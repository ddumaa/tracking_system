(function() {
    "use strict";

    /**
     * Клиент STOMP для получения уведомлений по WebSocket.
     * @type {StompJs.Client|null}
     */
    let stompClient = null;

    /**
     * Идентификатор таймера опроса REST контроллера.
     * @type {number|null}
     */
    let pollingTimer = null;

    /**
     * Последний известный batchId. Используется при падении WebSocket
     * и переходе на опрос REST контроллера.
     * @type {number|null}
     */
    let lastBatchId = null;

    /**
     * Начальная отметка времени для локального таймера.
     * @type {number|null}
     */
    let timerStart = null;

    /**
     * Идентификатор интервала обновления таймера.
     * @type {number|null}
     */
    let timerId = null;

    /**
     * Последние значения прогресса.
     * Хранятся для перерисовки бара при тике таймера.
     */
    let lastCompleted = 0;
    let lastTotal = 0;
    /**
     * Контейнер текущего прогресс-бара.
     * Хранится чтобы таймер мог обновлять его без WebSocket сообщений.
     * @type {HTMLElement|null}
     */
    let progressContainer = null;
    /**
     * DOM-элемент всплывающего окна с прогрессом.
     * @type {HTMLElement|null}
     */
    let progressPopup = null;

    document.addEventListener("DOMContentLoaded", initProgressTracking);

    /**
     * Точка входа: инициализируем соединение и отображение прогресса.
     * Запрашиваем актуальный прогресс через REST до подключения к WebSocket,
     * чтобы отобразить его сразу после загрузки страницы.
     */
    function initProgressTracking() {
        const userId = document.getElementById("userId")?.value;
        if (!userId) {
            return; // Пользователь не авторизован
        }
        const container = document.getElementById("progressContainer");
        progressPopup = document.getElementById("progressPopup");
        attachResultsCloseHandler();
        attachUnloadHandler();

        fetch("/app/progress/latest", {cache: "no-store"})
            .then(r => r.ok ? r.json() : null)
            .then(data => {
                if (data && data.total > 0) {
                    lastBatchId = data.batchId;
                    updateDisplay(data, container);
                }
            })
            .finally(() => connectSocket(userId, container));

        // Загружаем сохранённые результаты последней партии
        fetch("/app/results/latest", {cache: "no-store"})
            .then(r => r.ok ? r.json() : [])
            .then(list => list.forEach(item => updateTrackingRow(item.trackingNumber, item.status)));
    }

    /**
     * Подключается к WebSocket и подписывается на канал прогресса.
     * В случае ошибки запускает периодический опрос REST.
     *
     * @param {string} userId идентификатор пользователя
     * @param {HTMLElement|null} container блок для прогресс-бара
     */
    function connectSocket(userId, container) {
        const protocol = window.location.protocol === "https:" ? "wss" : "ws";
        stompClient = new StompJs.Client({
            brokerURL: `${protocol}://${window.location.host}/ws`,
            reconnectDelay: 0
        });

        stompClient.onConnect = () => {
            stompClient.subscribe(`/topic/progress/${userId}`, message => {
                const data = JSON.parse(message.body);
                lastBatchId = data.batchId;
                updateDisplay(data, container);
            });

            // Подписка на события обработки трека Белпочты
            stompClient.subscribe(`/topic/belpost/track-processed/${userId}`, message => {
                progressContainer = container;
                const data = JSON.parse(message.body);
                // Progress updates come with elapsed time, timer is started in updateDisplay
                updateProgressBar(data.completed, data.total);
                updateTrackingRow(data.trackingNumber, data.status);
            });

            // Подписка на событие завершения партии треков Белпочты
            stompClient.subscribe(`/topic/belpost/batch-finished/${userId}`, () => {
                handleBatchFinished(container);
            });
        };

        const fallback = () => startPolling(container);
        stompClient.onWebSocketError = fallback;
        stompClient.onStompError = fallback;

        stompClient.activate();
    }

    /**
     * Запускает циклический опрос REST контроллера.
     * Используется, если WebSocket недоступен.
     *
     * @param {HTMLElement|null} container блок для прогресс-бара
     */
    function startPolling(container) {
        if (pollingTimer || !lastBatchId) {
            return; // Уже запущено или неизвестен batchId
        }
        pollingTimer = setInterval(() => {
            // Используем новый эндпоинт для получения прогресса
            fetch(`/app/progress/latest`, {cache: "no-store"})
                .then(r => r.ok ? r.json() : Promise.reject(r.status))
                .then(data => {
                    updateDisplay(data, container);
                    if (data.processed >= data.total) {
                        stopPolling();
                    }
                })
                .catch(err => console.error("Progress polling error", err));
        }, 5000);
    }

    /** Останавливает опрос REST контроллера. */
    function stopPolling() {
        if (pollingTimer) {
            clearInterval(pollingTimer);
            pollingTimer = null;
        }
    }

    /**
     * Обновляет отображение прогресса: прогресс-бар или toast.
     * Также скрывает элементы при завершении и синхронизирует локальный таймер.
     *
     * @param {{batchId:number,processed:number,total:number,elapsed:string}} data данные прогресса
     * @param {HTMLElement|null} container контейнер прогресс-бара
     */
    function updateDisplay(data, container) {
        if (!data || data.total === 0) return;
        // Первое сообщение задаёт базовую точку времени для локального таймера
        if (timerStart === null && typeof data.elapsed === "string") {
            timerStart = Date.now() - parseElapsed(data.elapsed);
        }
        // Запускаем таймер при первом сообщении о прогрессе
        if (timerId === null && typeof data.elapsed === "string") {
            startTimer(parseElapsed(data.elapsed));
        }
        if (data.processed >= data.total) {
            // Если обработка завершена, показываем итоговое уведомление
            handleBatchFinished(container);
            return;
        }

        if (container) {
            renderBar(container, data);
        } else {
            renderPopup(data);
        }
    }

    /**
     * Создаёт или обновляет полоску прогресса.
     *
     * @param {HTMLElement} container блок для прогресс-бара
     * @param {{processed:number,total:number,elapsed:string}} data данные прогресса
     */
    function renderBar(container, data) {
        container.classList.remove("d-none");
        let bar = container.querySelector(".progress-bar");
        let info = container.querySelector(".progress-info");
        if (!bar) {
            container.innerHTML =
                `<div class="progress my-3">
                     <div class="progress-bar" role="progressbar" aria-valuemin="0" aria-valuemax="${data.total}"></div>
                 </div>
                 <div class="progress-info small text-center"></div>`;
            bar = container.querySelector(".progress-bar");
            info = container.querySelector(".progress-info");
        }
        const percent = Math.floor(data.processed / data.total * 100);
        bar.style.width = percent + "%";
        bar.setAttribute("aria-valuenow", String(data.processed));
        info.textContent = `Обработано ${data.processed} из ${data.total} | ${data.elapsed}`;
    }

    /**
     * Отображает прогресс во всплывающем окне, не создавая новые toast.
     * @param {{processed:number,total:number,elapsed:string}} data данные прогресса
     */
    function renderPopup(data) {
        if (!progressPopup) return;
        progressPopup.classList.remove("d-none");

        const percent = Math.floor(data.processed / data.total * 100);
        progressPopup.innerHTML =
            `<div class="progress">
                 <div class="progress-bar" role="progressbar" aria-valuemin="0" aria-valuemax="${data.total}" style="width: ${percent}%" aria-valuenow="${data.processed}"></div>
             </div>
             <div class="progress-info small text-center mt-1">Обработано ${data.processed} из ${data.total} | ${data.elapsed}</div>`;
    }

    /**
     * Показывает универсальный toast с произвольным текстом.
     * @param {string} message текст уведомления
     * @param {string} [bgClass="text-bg-info"] CSS-класс для цвета
     */
    function showToast(message, bgClass = "text-bg-info") {
        const container = document.getElementById("globalToastContainer");
        if (!container) return;
        const toastId = `toast-${Date.now()}`;
        container.insertAdjacentHTML("beforeend",
            `<div id="${toastId}" class="toast align-items-center ${bgClass} border-0 mb-2" role="alert" aria-live="assertive" aria-atomic="true">
                <div class="d-flex">
                    <div class="toast-body">${message}</div>
                    <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>
                </div>
             </div>`);
        const toastEl = document.getElementById(toastId);
        const toast = new bootstrap.Toast(toastEl, {delay: 5000});
        toast.show();
        toastEl.addEventListener("hidden.bs.toast", () => toastEl.remove());
    }

    /**
     * Показывает уведомление в виде toast с информацией о прогрессе.
     * @param {{processed:number,total:number,elapsed:string}} data
     */
    function showProgressToast(data) {
        const message = `Обработано ${data.processed}/${data.total} | ${data.elapsed}`;
        showToast(message, "text-bg-info");
    }

    /** Показывает сообщение о завершении обработки всех треков. */
    function showBatchFinishedToast() {
        showToast("Все треки обработаны", "text-bg-success");
    }

    /**
     * Обрабатывает окончание партии: уведомляет пользователя и скрывает прогресс.
     * @param {HTMLElement|null} container контейнер прогресс-бара
     */
    function handleBatchFinished(container) {
        // Останавливаем локальный таймер и уведомляем пользователя
        stopTimer();
        showBatchFinishedToast();
        hideDisplay(container);
        hidePopup();
    }

    /**
     * Запускает локальный таймер отображения прогресса.
     * Таймер перерисовывает прогресс каждую секунду.
     *
     * @param {number} [offsetMs=0] сколько миллисекунд прошло с начала обработки
     */
    function startTimer(offsetMs = 0) {
        if (timerId) return;
        // Если старт не был синхронизирован ранее, вычисляем его на основе смещения
        if (timerStart === null) {
            timerStart = Date.now() - offsetMs;
        }
        timerId = setInterval(() => updateProgressBar(lastCompleted, lastTotal), 1000);
    }

    /**
     * Останавливает таймер прогресса.
     */
    function stopTimer() {
        if (timerId) {
            clearInterval(timerId);
            timerId = null;
        }
    }

    /**
     * Обновляет визуальный прогресс-бар текущими значениями.
     * Также выводит прошедшее время с момента запуска таймера.
     *
     * @param {number} completed сколько элементов обработано
     * @param {number} total общее количество элементов
     */
    function updateProgressBar(completed, total) {
        if (!progressContainer) return;
        lastCompleted = completed;
        lastTotal = total;

        progressContainer.classList.remove("d-none");
        let bar = progressContainer.querySelector(".progress-bar");
        let info = progressContainer.querySelector(".progress-info");
        if (!bar) {
            progressContainer.innerHTML =
                `<div class="progress my-3">
                     <div class="progress-bar" role="progressbar" aria-valuemin="0" aria-valuemax="${total}"></div>
                 </div>
                 <div class="progress-info small text-center"></div>`;
            bar = progressContainer.querySelector(".progress-bar");
            info = progressContainer.querySelector(".progress-info");
        }

        const percent = total > 0 ? Math.floor(completed / total * 100) : 0;
        bar.style.width = percent + "%";
        bar.setAttribute("aria-valuenow", String(completed));
        const elapsed = timerStart ? formatElapsed(Date.now() - timerStart) : "0:00";
        info.textContent = `Обработано ${completed} из ${total} | ${elapsed}`;

        if (completed >= total && total > 0) {
            stopTimer();
        }
    }

    /**
     * Обновляет или создаёт строку в таблице результатов трекинга.
     * Таблица создаётся динамически при первом обращении.
     *
     * @param {string} trackNumber номер трек-отправления
     * @param {string} statusText текст статуса
     */
    function updateTrackingRow(trackNumber, statusText) {
        const table = ensureResultsTable();
        if (!table) return;

        // При добавлении результатов показываем контейнер
        document.getElementById("tracking-results-container")?.classList.remove("d-none");

        let row = table.querySelector(`tr[data-track-number="${trackNumber}"]`);
        if (!row) {
            row = table.insertRow(-1);
            row.setAttribute("data-track-number", trackNumber);
            row.insertCell(0).textContent = trackNumber;
            row.insertCell(1).textContent = statusText;
        } else {
            row.cells[1].textContent = statusText;
        }
    }

    /**
     * Гарантирует наличие таблицы результатов на странице и показывает её.
     * Если таблицы нет, она создаётся вместе с контейнером.
     *
     * @returns {HTMLTableElement|null} найденная или созданная таблица
     */
    function ensureResultsTable() {
        const container = document.getElementById("tracking-results-container");
        if (!container) return null;

        container.classList.remove("d-none");
        let table = container.querySelector("#tracking-results-table");
        if (!table) {
            container.innerHTML =
                `<div class="table-responsive">
                     <table id="tracking-results-table" class="table table-striped">
                         <thead>
                         <tr><th>Номер посылки</th><th>Статус</th></tr>
                         </thead>
                         <tbody id="tracking-results-body"></tbody>
                     </table>
                 </div>`;
            table = container.querySelector("#tracking-results-table");
        }
        return table;
    }

    /**
     * Форматирует прошедшее время в mm:ss.
     * @param {number} ms миллисекунды
     * @returns {string} строка вида "m:ss"
     */
    function formatElapsed(ms) {
        const seconds = Math.floor(ms / 1000);
        const minutes = Math.floor(seconds / 60);
        const secs = seconds % 60;
        return `${minutes}:${secs.toString().padStart(2, "0")}`;
    }

    /**
     * Преобразует строку времени вида "m:ss" в миллисекунды.
     * Используется для синхронизации локального таймера с серверным.
     *
     * @param {string} text время в формате m:ss
     * @returns {number} количество миллисекунд
     */
    function parseElapsed(text) {
        const parts = text.split(":");
        const minutes = parseInt(parts[0], 10) || 0;
        const seconds = parseInt(parts[1], 10) || 0;
        return (minutes * 60 + seconds) * 1000;
    }

    /**
     * Скрывает прогресс-бар и сбрасывает локальные таймеры/опросы.
     * Вызывается после завершения обработки.
     */
    function hideDisplay(container) {
        stopPolling();
        stopTimer();
        if (container) {
            container.innerHTML = "";
            container.classList.add("d-none");
        }
    }

    /**
     * Скрывает всплывающий блок прогресса и очищает его содержимое.
     */
    function hidePopup() {
        if (!progressPopup) return;
        progressPopup.innerHTML = "";
        progressPopup.classList.add("d-none");
    }

    /**
     * Назначает обработчик кнопки закрытия блока результатов трекинга.
     * При нажатии очищает таблицу и скрывает контейнер.
     */
    function attachResultsCloseHandler() {
        const container = document.getElementById("tracking-results-container");
        if (!container) return;

        const closeBtn = container.querySelector("#tracking-results-close");
        if (!closeBtn) return;

        closeBtn.addEventListener("click", () => {
            const tbody = container.querySelector("#tracking-results-body");
            if (tbody) {
                tbody.innerHTML = "";
            }
            container.classList.add("d-none");
            fetch("/app/results/clear", {method: "POST"});
        });
    }

    /**
     * Отправляет запрос на очистку кэша результатов при уходе со страницы.
     */
    function attachUnloadHandler() {
        window.addEventListener("beforeunload", () => {
            navigator.sendBeacon("/app/results/clear");
        });
    }
})();
