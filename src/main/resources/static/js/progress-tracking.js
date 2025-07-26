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
     * Последнее отображённое состояние прогресса.
     * Используется таймером для обновления интерфейса.
     * @type {{batchId:number|null, processed:number, total:number, elapsed:string}}
     */
    const currentProgress = {
        batchId: null,
        processed: 0,
        total: 0,
        elapsed: "0:00"
    };
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
    /**
     * Флаг окончания обработки текущей партии.
     * Используется чтобы не показывать уведомление несколько раз.
     * @type {boolean}
     */
    let batchFinished = false;

    /**
     * Сводные данные о прогрессе по каждой партии.
     * Ключом является batchId, значение хранит
     * { total, processed, timerStart, container, finished }.
     * @type {Object.<number,{total:number,processed:number,timerStart:number|null,container:HTMLElement|null,finished:boolean}>}
     */
    const batchProgress = {};

    /**
     * Сохраняет текущее состояние прогресса, если поступили новые данные.
     * Обновление происходит только при новом batchId либо увеличении processed.
     *
     * @param {number} batchId идентификатор партии
     * @param {{processed:number,total:number,elapsed:string}} data данные прогресса
     */
    function storeCurrentProgress(batchId, data) {
        if (batchId !== currentProgress.batchId || data.processed > currentProgress.processed) {
            currentProgress.batchId = batchId;
            currentProgress.processed = data.processed;
            currentProgress.total = data.total;
            currentProgress.elapsed = data.elapsed;
        }
    }

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
                // При получении сообщения другой партии сбрасываем флаг окончания
                if (data.batchId !== lastBatchId) {
                    batchFinished = false;
                }
                lastBatchId = data.batchId;
                updateDisplay(data, container);
            });

            // Подписка на события обработки трека Белпочты
            // Глобальный прогресс обновляется через канал /topic/progress,
            // поэтому здесь обновляем только информацию по конкретному треку
            stompClient.subscribe(`/topic/belpost/track-processed/${userId}`, message => {
                progressContainer = container;
                const data = JSON.parse(message.body);
                updateTrackingRow(data.trackingNumber, data.status);
            });

            // Подписка на событие завершения партии треков Белпочты.
            // Итог обработки теперь вычисляется в updateDisplay по
            // суммарным значениям processed и total, поэтому здесь
            // оставляем пустой обработчик события.
            stompClient.subscribe(`/topic/belpost/batch-finished/${userId}`, () => {
                // завершение обрабатывается после агрегации прогресса
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
     * Агрегирует полученные данные и инициирует их отображение.
     * <p>
     * Метод обновляет запись в {@code batchProgress}, синхронизирует локальные
     * таймеры и сохраняет актуальное состояние в {@code currentProgress}. В
     * зависимости от наличия DOM-контейнера отображение выполняется либо через
     * {@link renderBar}, либо во всплывающем окне {@link renderPopup}. Когда
     * количество обработанных треков достигает общего значения, запись о партии
     * удаляется и вызывается обработчик завершения.
     * </p>
     *
     * @param {{batchId:number,processed:number,total:number,elapsed:string}} data данные прогресса
     * @param {HTMLElement|null} container контейнер прогресс-бара
     */
    function updateDisplay(data, container) {
        if (!data || data.total === 0) return;

        // Получаем или создаём агрегированный прогресс по batchId
        if (!batchProgress[data.batchId]) {
            batchProgress[data.batchId] = {
                total: data.total,
                processed: 0,
                timerStart: null,
                container: container || null,
                finished: false
            };
        }

        const entry = batchProgress[data.batchId];

        // Сохраняем максимальное известное общее количество
        entry.total = Math.max(entry.total, data.total);
        // Значение processed может приходить как инкрементальное, так и полное.
        // Чтобы избежать откатов, берём максимум между текущим и пришедшим
        // значением. Если от сервера пришло меньшее число, просто игнорируем
        // обновление. Также ограничиваем processed, чтобы он не превышал total.
        if (data.processed > entry.processed) {
            entry.processed = Math.min(data.processed, entry.total);
        }

        // Сохраняем контейнер и стартовую точку времени при первом сообщении
        if (!entry.container && container) {
            entry.container = container;
        }
        if (entry.timerStart === null && typeof data.elapsed === "string") {
            entry.timerStart = Date.now() - parseElapsed(data.elapsed);
        }

        // Обновляем контейнер и таймеры для корректной работы
        progressContainer = entry.container || progressContainer;
        if (timerStart === null && entry.timerStart !== null) {
            timerStart = entry.timerStart;
        }
        if (timerId === null && entry.timerStart !== null) {
            startTimer(Date.now() - entry.timerStart);
        }

        // Ограничиваем отображаемое значение, чтобы исключить переполнение
        // прогресс-бара при рассинхроне данных
        const displayData = {
            processed: Math.min(entry.processed, entry.total),
            total: entry.total,
            elapsed: entry.timerStart ? formatElapsed(Date.now() - entry.timerStart) : data.elapsed
        };

        storeCurrentProgress(data.batchId, displayData);

        if (entry.container) {
            renderBar(entry.container, displayData);
        } else {
            renderPopup(displayData);
        }

        // Завершение обработки определяется здесь, когда обработано
        // столько же или больше треков, сколько было запланировано
        if (!entry.finished && entry.processed >= entry.total) {
            entry.finished = true;
            handleBatchFinished(entry.container);
            delete batchProgress[data.batchId];
        }
    }

    /**
     * Создаёт или обновляет DOM-элементы полоски прогресса.
     * <p>
     * Функция отвечает исключительно за визуальное представление: при
     * отсутствии элементов внутри контейнера они создаются, затем
     * обновляются атрибуты и текстовое значение в соответствии с полученными
     * данными.
     * </p>
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
        const percent = progressPercent(data.processed, data.total);
        bar.style.width = percent + "%";
        bar.setAttribute("aria-valuenow", String(data.processed));
        info.textContent = `Обработано ${data.processed} из ${data.total} | ${data.elapsed}`;
    }

    /**
     * Обновляет содержимое всплывающего блока с прогрессом.
     * <p>
     * Отрисовывает HTML разметку внутри уже существующего {@code progressPopup}
     * и отображает его без создания дополнительных уведомлений. Данные
     * используются только для визуального представления и не изменяют
     * состояние модуля.
     * </p>
     *
     * @param {{processed:number,total:number,elapsed:string}} data данные прогресса
     */
    function renderPopup(data) {
        if (!progressPopup) return;

        // Прогресс отображаем внутри всплывающего блока без создания toast
        progressPopup.classList.remove("d-none");

        const percent = progressPercent(data.processed, data.total);
        progressPopup.innerHTML =
            `<div class="progress-text">
                 <span>Обработано ${data.processed} из ${data.total}</span>
                 <span class="progress-time">| ${data.elapsed}</span>
             </div>
             <div class="progress-track">
                 <div class="progress-bar" style="width: ${percent}%;"></div>
             </div>`;
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
     * Вызывается один раз из updateDisplay после агрегирования прогресса.
     *
     * @param {HTMLElement|null} container контейнер прогресс-бара
     */
    function handleBatchFinished(container) {
        // Если уведомление уже показано, повторно не выполняем действия
        if (batchFinished) {
            return;
        }
        batchFinished = true;
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
        timerId = setInterval(updateProgressDisplay, 1000);
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
     * Обновляет визуальный прогресс-бар на основе текущего состояния.
     * Используется локальным таймером для отрисовки времени.
     */
    function updateProgressDisplay() {
        if (progressContainer) {
            progressContainer.classList.remove("d-none");
            let bar = progressContainer.querySelector(".progress-bar");
            let info = progressContainer.querySelector(".progress-info");
            if (!bar) {
                progressContainer.innerHTML =
                    `<div class="progress my-3">
                         <div class="progress-bar" role="progressbar" aria-valuemin="0" aria-valuemax="${currentProgress.total}"></div>
                     </div>
                     <div class="progress-info small text-center"></div>`;
                bar = progressContainer.querySelector(".progress-bar");
                info = progressContainer.querySelector(".progress-info");
            }

            const percent = progressPercent(currentProgress.processed, currentProgress.total);
            bar.style.width = percent + "%";
            bar.setAttribute("aria-valuenow", String(currentProgress.processed));
            currentProgress.elapsed = timerStart ? formatElapsed(Date.now() - timerStart) : "0:00";
            info.textContent = `Обработано ${currentProgress.processed} из ${currentProgress.total} | ${currentProgress.elapsed}`;
        } else if (progressPopup) {
            currentProgress.elapsed = timerStart ? formatElapsed(Date.now() - timerStart) : "0:00";
            renderPopup(currentProgress);
        }

        if (currentProgress.processed >= currentProgress.total && currentProgress.total > 0) {
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
     * Рассчитывает процент выполнения на основе обработанных элементов.
     * Используется в разных частях модуля для отрисовки прогресс-бара.
     *
     * @param {number} processed сколько элементов обработано
     * @param {number} total общее количество элементов
     * @returns {number} значение процента от 0 до 100
     */
    function progressPercent(processed, total) {
        if (total <= 0) return 0;
        const clamped = Math.min(processed, total);
        return Math.floor(clamped / total * 100);
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
            // Отправляем запрос на очистку сохранённых результатов трекинга
            // Используем глобально заданные CSRF-заголовок и токен из app.js
            fetch("/app/results/clear", {
                method: "POST",
                headers: { [window.csrfHeader]: window.csrfToken }
            });
        });
    }

    /**
     * Отправляет запрос на очистку кэша результатов при уходе со страницы.
     */
    function attachUnloadHandler() {
        window.addEventListener("beforeunload", () => {
            // Используем fetch с keepalive, чтобы гарантировать отправку запроса
            // даже при закрытии страницы
            fetch("/app/results/clear", {
                method: "POST",
                headers: { [window.csrfHeader]: window.csrfToken },
                keepalive: true
            });
        });
    }
})();
