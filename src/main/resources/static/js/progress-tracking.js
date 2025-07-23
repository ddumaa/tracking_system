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

    document.addEventListener("DOMContentLoaded", initProgressTracking);

    /**
     * Точка входа: инициализируем соединение и отображение прогресса.
     */
    function initProgressTracking() {
        const userId = document.getElementById("userId")?.value;
        if (!userId) {
            return; // Пользователь не авторизован
        }
        const container = document.getElementById("progressContainer");
        connectSocket(userId, container);
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

    /** Скрывает прогресс-бар и прекращает опрос. */
    function hideDisplay(container) {
        stopPolling();
        if (container) {
            container.innerHTML = "";
            container.classList.add("d-none");
        }
    }
})();
