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

        fetch("/app/progress/latest", {cache: "no-store"})
            .then(r => r.ok ? r.json() : null)
            .then(data => {
                if (data && data.total > 0) {
                    lastBatchId = data.batchId;
                    updateDisplay(data, container);
                }
            })
            .finally(() => connectSocket(userId, container));
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
                startTimer();
                updateProgressBar(data.completed, data.total);
                updateTrackingRow(data.trackingNumber, data.status);
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
            fetch(`/app/progress/${lastBatchId}`, {cache: "no-store"})
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
     * Также скрывает элементы при завершении.
     *
     * @param {{batchId:number,processed:number,total:number,elapsed:string}} data
     * @param {HTMLElement|null} container контейнер прогресс-бара
     */
    function updateDisplay(data, container) {
        if (!data || data.total === 0) return;
        if (container) {
            renderBar(container, data);
        } else {
            showProgressToast(data);
        }
        if (data.processed >= data.total) {
            hideDisplay(container);
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
     * Показывает уведомление в виде toast с информацией о прогрессе.
     * @param {{processed:number,total:number,elapsed:string}} data
     */
    function showProgressToast(data) {
        const container = document.getElementById("globalToastContainer");
        if (!container) return;
        const toastId = `progress-toast-${Date.now()}`;
        const message = `Обработано ${data.processed}/${data.total} | ${data.elapsed}`;
        container.insertAdjacentHTML("beforeend",
            `<div id="${toastId}" class="toast align-items-center text-bg-info border-0 mb-2" role="alert" aria-live="assertive" aria-atomic="true">
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
     * Запускает локальный таймер отображения прогресса.
     * Таймер перерисовывает прогресс каждую секунду.
     */
    function startTimer() {
        if (timerId) return;
        timerStart = Date.now();
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

    /** Скрывает прогресс-бар и прекращает опрос. */
    function hideDisplay(container) {
        stopPolling();
        if (container) {
            container.innerHTML = "";
            container.classList.add("d-none");
        }
    }
})();
