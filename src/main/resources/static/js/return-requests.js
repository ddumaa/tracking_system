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
     * Обновляет содержимое строки таблицы на основе свежего DTO заявки.
     * @param {Object} summary данные заявки
     */
    function updateRowFromSummary(summary) {
        if (!summary || summary.parcelId === undefined || summary.requestId === undefined) {
            return;
        }
        const selector = `tr[data-return-request-row][data-track-id="${summary.parcelId}"][data-request-id="${summary.requestId}"]`;
        const row = document.querySelector(selector);
        if (!row) {
            return;
        }
        if ('exchangeRequested' in summary) {
            row.dataset.exchangeRequested = summary.exchangeRequested ? 'true' : 'false';
        }
        if ('requestId' in summary) {
            row.dataset.requestId = String(summary.requestId);
        }

        const trackButton = row.querySelector('[data-return-track-number]');
        if (trackButton && 'trackNumber' in summary) {
            trackButton.textContent = summary.trackNumber || 'Трек не указан';
            trackButton.dataset.trackId = String(summary.parcelId);
            trackButton.dataset.itemnumber = summary.trackNumber || '';
        }

        const storeSpan = row.querySelector('[data-return-store]');
        if (storeSpan && 'storeName' in summary) {
            storeSpan.textContent = summary.storeName || 'Магазин не указан';
        }

        const statusSpan = row.querySelector('[data-return-parcel-status]');
        if (statusSpan && 'parcelStatus' in summary) {
            statusSpan.textContent = summary.parcelStatus || 'Статус не определён';
        }

        const requestStatus = row.querySelector('[data-return-status-label]');
        if (requestStatus && 'statusLabel' in summary) {
            requestStatus.textContent = summary.statusLabel || 'Статус не определён';
        }

        const requestedSpan = row.querySelector('[data-return-requested]');
        if (requestedSpan && 'requestedAt' in summary) {
            const requestedText = summary.requestedAt ? `Обращение: ${summary.requestedAt}` : 'Обращение: —';
            requestedSpan.textContent = requestedText;
        }

        const createdSpan = row.querySelector('[data-return-created]');
        if (createdSpan && 'createdAt' in summary) {
            const createdText = summary.createdAt ? `Регистрация: ${summary.createdAt}` : 'Регистрация: —';
            createdSpan.textContent = createdText;
        }

        const reasonSpan = row.querySelector('[data-return-reason]');
        if (reasonSpan && 'reason' in summary) {
            reasonSpan.textContent = summary.reason || 'Причина не указана';
        }

        const commentSpan = row.querySelector('[data-return-comment]');
        if (commentSpan && 'comment' in summary) {
            commentSpan.textContent = summary.comment || 'Комментарий отсутствует';
        }

        const reverseSpan = row.querySelector('[data-return-reverse]');
        if (reverseSpan && 'reverseTrackNumber' in summary) {
            const reverseText = summary.reverseTrackNumber
                ? `Обратный трек: ${summary.reverseTrackNumber}`
                : 'Обратный трек: —';
            reverseSpan.textContent = reverseText;
        }

        const confirmationSpan = row.querySelector('[data-return-confirmation]');
        if (confirmationSpan && ('returnReceiptConfirmed' in summary || 'returnReceiptConfirmedAt' in summary)) {
            const confirmed = Boolean(summary.returnReceiptConfirmed);
            const confirmationText = confirmed
                ? `Получение подтверждено: ${summary.returnReceiptConfirmedAt || '—'}`
                : 'Получение ещё не подтверждено';
            confirmationSpan.textContent = confirmationText;
            confirmationSpan.classList.toggle('text-success', confirmed);
            confirmationSpan.classList.toggle('text-muted', !confirmed);
        }

        const warningBox = row.querySelector('[data-return-cancel-warning]');
        if (warningBox && 'cancelExchangeUnavailableReason' in summary) {
            const showWarning = Boolean(summary.cancelExchangeUnavailableReason);
            warningBox.classList.toggle('d-none', !showWarning);
            warningBox.setAttribute('aria-hidden', showWarning ? 'false' : 'true');
            const warningText = warningBox.querySelector('[data-return-cancel-warning-text]');
            if (warningText) {
                warningText.textContent = summary.cancelExchangeUnavailableReason || '';
            }
        }

        const exchangeButton = row.querySelector('.js-return-request-exchange');
        if (exchangeButton && 'canStartExchange' in summary) {
            const canExchange = Boolean(summary.canStartExchange);
            exchangeButton.classList.toggle('d-none', !canExchange);
            exchangeButton.setAttribute('aria-hidden', canExchange ? 'false' : 'true');
            exchangeButton.disabled = !canExchange;
            if (canExchange) {
                const exchangeText = summary.exchangeRequested
                    ? 'Создать обменную посылку'
                    : 'Перевести в обмен';
                exchangeButton.textContent = exchangeText;
                exchangeButton.dataset.actionLabel = exchangeText;
            }
        }

        const closeButton = row.querySelector('.js-return-request-close');
        if (closeButton && ('canCloseWithoutExchange' in summary || 'exchangeRequested' in summary)) {
            const canClose = Boolean(summary.canCloseWithoutExchange);
            closeButton.classList.toggle('d-none', !canClose);
            closeButton.setAttribute('aria-hidden', canClose ? 'false' : 'true');
            closeButton.disabled = !canClose;
            if (canClose) {
                const closeText = summary.exchangeRequested ? 'Закрыть без обмена' : 'Принять возврат';
                closeButton.textContent = closeText;
                closeButton.dataset.actionLabel = closeText;
                closeButton.classList.toggle('btn-outline-secondary', Boolean(summary.exchangeRequested));
                closeButton.classList.toggle('btn-success', !summary.exchangeRequested);
            }
        }

        const reopenButton = row.querySelector('.js-return-request-reopen');
        if (reopenButton && 'canReopenAsReturn' in summary) {
            const canReopen = Boolean(summary.canReopenAsReturn);
            reopenButton.classList.toggle('d-none', !canReopen);
            reopenButton.setAttribute('aria-hidden', canReopen ? 'false' : 'true');
            reopenButton.disabled = !canReopen;
        }

        const cancelButton = row.querySelector('.js-return-request-cancel');
        if (cancelButton && 'canCancelExchange' in summary) {
            const canCancel = Boolean(summary.canCancelExchange);
            cancelButton.classList.toggle('d-none', !canCancel);
            cancelButton.setAttribute('aria-hidden', canCancel ? 'false' : 'true');
            cancelButton.disabled = !canCancel;
        }

        const confirmButton = row.querySelector('.js-return-request-confirm');
        if (confirmButton && ('canConfirmReceipt' in summary || 'returnReceiptConfirmed' in summary)) {
            const canConfirm = Boolean(summary.canConfirmReceipt);
            confirmButton.classList.toggle('d-none', !canConfirm);
            confirmButton.setAttribute('aria-hidden', canConfirm ? 'false' : 'true');
            confirmButton.disabled = !canConfirm;
        }

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
            },
            reopen(trackId, requestId, options = {}) {
                const fn = window.trackModal?.reopenReturnRequest;
                if (typeof fn !== 'function') {
                    return Promise.reject(new Error('Перевод в возврат недоступен'));
                }
                return fn(trackId, requestId, options);
            },
            cancel(trackId, requestId, options = {}) {
                const fn = window.trackModal?.cancelReturnExchange;
                if (typeof fn !== 'function') {
                    return Promise.reject(new Error('Отмена обмена недоступна'));
                }
                return fn(trackId, requestId, options);
            },
            confirm(trackId, requestId, options = {}) {
                const fn = window.trackModal?.confirmReturnProcessing;
                if (typeof fn !== 'function') {
                    return Promise.reject(new Error('Подтверждение обработки возврата недоступно'));
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
            const button = event.target.closest('.js-return-request-exchange, .js-return-request-close, .js-return-request-reopen, .js-return-request-cancel, .js-return-request-confirm');
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
            } else if (button.classList.contains('js-return-request-reopen')) {
                actionKey = 'reopen';
            } else if (button.classList.contains('js-return-request-cancel')) {
                actionKey = 'cancel';
            } else if (button.classList.contains('js-return-request-confirm')) {
                actionKey = 'confirm';
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
            } else if (actionKey === 'reopen') {
                actionOptions.successMessage = 'Заявка переведена в возврат';
                actionOptions.notificationType = 'info';
            } else if (actionKey === 'cancel') {
                actionOptions.successMessage = 'Обмен отменён и заявка закрыта';
                actionOptions.notificationType = 'warning';
            } else if (actionKey === 'confirm') {
                actionOptions.successMessage = 'Обработка возврата подтверждена';
                actionOptions.notificationType = 'success';
            }
            executeAction(button, () => executor(trackId, requestId, actionOptions));
        });

        refreshEmptyState();
    });

    window.returnRequests = {
        removeRowByIds,
        refreshEmptyState,
        updateRow: updateRowFromSummary
    };
})();
