(() => {
    'use strict';

    /**
     * Удаляет строку заявки из таблицы по идентификаторам посылки и заявки.
     * @param {string|number} trackId идентификатор посылки
     * @param {string|number} requestId идентификатор заявки
     */
    function removeRowByIds(trackId, requestId) {
        if (!trackId) {
            return;
        }
        const selector = `tr[data-return-request-row][data-track-id="${trackId}"]`;
        const rows = Array.from(document.querySelectorAll(selector));
        rows.forEach((row) => {
            if (requestId && String(row.dataset.requestId) !== String(requestId)) {
                return;
            }
            row.remove();
        });
        refreshEmptyState();
    }

    /**
     * Обновляет состояние пустого блока и скрывает его при наличии строк.
     */
    function refreshEmptyState() {
        const table = document.getElementById('returnRequestsTable');
        const emptyState = document.getElementById('returnRequestsEmptyState');
        if (!emptyState) {
            return;
        }
        const hasRows = Boolean(table?.querySelector('tbody tr[data-return-request-row]'));
        if (table) {
            const container = table.closest('.table-responsive')?.parentElement;
            if (container instanceof HTMLElement) {
                container.classList.toggle('d-none', !hasRows);
            }
        }
        emptyState.classList.toggle('d-none', hasRows);
        emptyState.classList.toggle('visually-hidden', hasRows);
        emptyState.setAttribute('aria-hidden', hasRows ? 'true' : 'false');
    }

    /**
     * Выполняет действие кнопки с управлением состоянием и обработкой ошибок.
     * @param {HTMLButtonElement} button кнопка, инициировавшая действие
     * @param {Function} action асинхронная операция
     */
    async function executeAction(button, action) {
        if (typeof action !== 'function') {
            return;
        }
        if (button) {
            button.disabled = true;
            button.setAttribute('aria-busy', 'true');
        }
        try {
            await action();
        } catch (error) {
            if (typeof window.notifyUser === 'function') {
                window.notifyUser(error?.message || 'Не удалось выполнить действие', 'danger');
            } else {
                console.error(error);
            }
        } finally {
            if (button && document.body.contains(button)) {
                button.disabled = false;
                button.setAttribute('aria-busy', 'false');
            }
        }
    }

    /**
     * Возвращает карту обработчиков действий, привязанных к REST-эндпоинтам.
     */
    function getActionExecutors() {
        return {
            exchange(trackId, requestId, options = {}) {
                const fn = window.trackModal?.approveReturnExchange;
                if (typeof fn !== 'function') {
                    return Promise.reject(new Error('Создание обменной посылки недоступно'));
                }
                return fn(trackId, requestId, options);
            },
            close(trackId, requestId, options = {}) {
                const fn = window.trackModal?.closeReturnRequest;
                if (typeof fn !== 'function') {
                    return Promise.reject(new Error('Закрытие заявки недоступно'));
                }
                return fn(trackId, requestId, options);
            }
        };
    }

    document.addEventListener('DOMContentLoaded', () => {
        const table = document.getElementById('returnRequestsTable');
        if (!table) {
            refreshEmptyState();
            return;
        }

        const executors = getActionExecutors();

        table.addEventListener('click', (event) => {
            const button = event.target.closest('.js-return-request-exchange, .js-return-request-close');
            if (!button) {
                return;
            }
            const row = button.closest('tr[data-return-request-row]');
            if (!row) {
                return;
            }
            const { trackId, requestId, exchangeRequested } = row.dataset;
            if (!trackId || !requestId) {
                return;
            }

            let actionKey = 'exchange';
            if (button.classList.contains('js-return-request-close')) {
                actionKey = 'close';
            }

            const executor = executors[actionKey];
            if (typeof executor !== 'function') {
                return;
            }
            const isExchangeRequested = exchangeRequested === 'true';
            const actionOptions = {};
            if (actionKey === 'exchange') {
                actionOptions.successMessage = isExchangeRequested
                    ? 'Создана обменная посылка'
                    : 'Заявка переведена в обмен';
                actionOptions.notificationType = isExchangeRequested ? 'success' : 'info';
            } else if (actionKey === 'close') {
                actionOptions.successMessage = isExchangeRequested
                    ? 'Заявка закрыта без обмена'
                    : 'Возврат принят';
                actionOptions.notificationType = isExchangeRequested ? 'info' : 'success';
            }
            executeAction(button, () => executor(trackId, requestId, actionOptions));
        });

        refreshEmptyState();
    });

    window.returnRequests = {
        removeRowByIds,
        refreshEmptyState
    };
})();
