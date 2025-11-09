(() => {
    'use strict';

    /**
     * Нормализует action-code к нижнему регистру и убирает пробелы.
     * @param {string} value исходный код
     * @returns {string} нормализованное значение или пустая строка
     */
    function normalizeActionCode(value) {
        if (typeof value !== 'string') {
            return '';
        }
        return value.trim().toLowerCase();
    }

    /**
     * Формирует множество доступных action-code из возможных источников summary.
     * Метод инкапсулирует детали структуры DTO, соблюдая принцип SRP.
     * @param {Object} summary агрегированная информация о заявке
     * @returns {Set<string>} множество нормализованных кодов
     */
    function collectActionCodes(summary) {
        if (!summary || typeof summary !== 'object') {
            return new Set();
        }
        const sources = [];
        if (Array.isArray(summary.actionCodes)) {
            sources.push(summary.actionCodes);
        }
        if (Array.isArray(summary.actions)) {
            sources.push(summary.actions);
        }
        const available = summary.availableActions;
        if (available && typeof available === 'object') {
            if (Array.isArray(available.actions)) {
                sources.push(available.actions);
            }
            if (Array.isArray(available.actionCodes)) {
                sources.push(available.actionCodes);
            }
        }
        const flattened = sources.flatMap((list) => Array.isArray(list) ? list : []);
        const seen = new Set();
        const codes = flattened
            .map((value) => normalizeActionCode(value))
            .filter((value) => value.length > 0)
            .filter((value) => {
                if (seen.has(value)) {
                    return false;
                }
                seen.add(value);
                return true;
            });
        return new Set(codes);
    }

    /**
     * Проверяет, содержится ли указанный action-code в множестве доступных действий.
     * Метод учитывает только актуальные идентификаторы, так как устаревшие коды удалены.
     * @param {Set<string>} codeSet множество доступных действий
     * @param {string} code искомое действие
     * @returns {boolean} {@code true}, если действие доступно
     */
    function hasActionFromSet(codeSet, code) {
        if (!(codeSet instanceof Set) || codeSet.size === 0) {
            return false;
        }
        const normalized = normalizeActionCode(code);
        if (!normalized) {
            return false;
        }
        return codeSet.has(normalized);
    }

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

        const derivePermissions = () => ({
            allowConfirmReceipt: false,
            allowConvertToExchange: false,
            allowCloseRequest: false,
            allowUpdateReverseTrack: false,
            allowConvertToReturn: false
        });

        const permissions = summary.actionPermissions
            ? summary.actionPermissions
            : derivePermissions(summary);
        const actionCodeSet = collectActionCodes(summary);
        const hasModernActions = actionCodeSet.size > 0;

        const fallbackAllowConfirmReceipt = Boolean(permissions.allowConfirmReceipt)
            || (summary.canConfirmReceipt !== undefined ? Boolean(summary.canConfirmReceipt) : false);
        const fallbackAllowConvertToExchange = Boolean(permissions.allowConvertToExchange);
        const fallbackAllowCloseRequest = Boolean(permissions.allowCloseRequest);
        const fallbackAllowUpdateReverseTrack = Boolean(permissions.allowUpdateReverseTrack)
            || (summary.canUpdateReverseTrack !== undefined ? Boolean(summary.canUpdateReverseTrack) : false);
        const fallbackAllowConvertToReturn = Boolean(permissions.allowConvertToReturn);

        const allowConfirmReceipt = hasModernActions
            ? hasActionFromSet(actionCodeSet, 'mark_inbound_picked_up')
            : fallbackAllowConfirmReceipt;
        const allowConvertToExchange = hasModernActions
            ? hasActionFromSet(actionCodeSet, 'set_mode_exchange')
            : fallbackAllowConvertToExchange;
        const allowCloseRequest = hasModernActions
            ? hasActionFromSet(actionCodeSet, 'close_request')
            : fallbackAllowCloseRequest;
        const allowUpdateReverseTrack = hasModernActions
            ? hasActionFromSet(actionCodeSet, 'update_reverse_track')
            : fallbackAllowUpdateReverseTrack;
        const allowConvertToReturn = hasModernActions
            ? hasActionFromSet(actionCodeSet, 'set_mode_return')
            : fallbackAllowConvertToReturn;
        const statusRaw = typeof summary.stage === 'string' ? summary.stage.toUpperCase() : '';
        const isExchangeStatus = statusRaw.includes('EXCHANGE');

        const syncButton = (button, visible, label) => {
            if (!button) {
                return;
            }
            button.classList.toggle('d-none', !visible);
            button.setAttribute('aria-hidden', visible ? 'false' : 'true');
            button.disabled = !visible;
            if (visible && label) {
                button.textContent = label;
                button.dataset.actionLabel = label;
            }
        };

        const confirmReturnButton = row.querySelector('.js-return-request-confirm-return');
        syncButton(confirmReturnButton, allowConfirmReceipt && !isExchangeStatus, 'Принять возврат');

        const toExchangeButton = row.querySelector('.js-return-request-to-exchange');
        syncButton(toExchangeButton, allowConvertToExchange, 'Перевести в обмен');

        const closeButton = row.querySelector('.js-return-request-close');
        syncButton(closeButton, allowCloseRequest, 'Закрыть обращение');

        const addReverseButton = row.querySelector('.js-return-request-add-reverse');
        syncButton(addReverseButton, allowUpdateReverseTrack, 'Добавить трек обратной посылки');

        const toReturnButton = row.querySelector('.js-return-request-to-return');
        syncButton(toReturnButton, allowConvertToReturn, 'Перевести в возврат');

        const confirmReverseButton = row.querySelector('.js-return-request-confirm-reverse');
        syncButton(confirmReverseButton, allowConfirmReceipt && isExchangeStatus, 'Принять обратную посылку');

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
            toExchange(trackId, requestId, options = {}) {
                const fn = window.trackModal?.convertReturnRequestToExchange;
                if (typeof fn !== 'function') {
                    return Promise.reject(new Error('Перевод заявки в обмен недоступен'));
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
            toReturn(trackId, requestId, options = {}) {
                const fn = window.trackModal?.reopenReturnRequest;
                if (typeof fn !== 'function') {
                    return Promise.reject(new Error('Перевод в возврат недоступен'));
                }
                return fn(trackId, requestId, options);
            },
            confirm(trackId, requestId, options = {}) {
                const fn = window.trackModal?.confirmReturnProcessing;
                if (typeof fn !== 'function') {
                    return Promise.reject(new Error('Подтверждение обработки возврата недоступно'));
                }
                return fn(trackId, requestId, options);
            },
            reverse(trackId, requestId, reverseValue, comment = null) {
                const fn = window.trackModal?.updateReverseTrack;
                if (typeof fn !== 'function') {
                    return Promise.reject(new Error('Обновление обратного трека недоступно'));
                }
                return fn(trackId, requestId, reverseValue, comment);
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
            const button = event.target.closest('.js-return-request-confirm-return, .js-return-request-to-exchange, .js-return-request-close, .js-return-request-add-reverse, .js-return-request-to-return, .js-return-request-confirm-reverse');
            if (!button) {
                return;
            }
            const row = button.closest('tr[data-return-request-row]');
            if (!row) {
                return;
            }
            const { trackId, requestId } = row.dataset;
            if (!trackId || !requestId) {
                return;
            }

            if (button.classList.contains('js-return-request-add-reverse')) {
                const reversePrompt = window.prompt('Укажите трек обратной посылки');
                if (!reversePrompt) {
                    return;
                }
                const reverseValue = reversePrompt.trim();
                if (reverseValue.length === 0) {
                    if (typeof window.notifyUser === 'function') {
                        window.notifyUser('Трек обратной посылки не может быть пустым', 'warning');
                    }
                    return;
                }
                const commentPrompt = window.prompt('Комментарий к обращению (необязательно)');
                const commentValue = commentPrompt ? commentPrompt.trim() : '';
                executeAction(button, () => executors.reverse(
                    trackId,
                    requestId,
                    reverseValue,
                    commentValue.length > 0 ? commentValue : null
                ));
                return;
            }

            let actionKey = '';
            let actionOptions = {};
            if (button.classList.contains('js-return-request-confirm-return')) {
                actionKey = 'confirm';
                actionOptions = {
                    successMessage: 'Возврат подтверждён',
                    notificationType: 'success'
                };
            } else if (button.classList.contains('js-return-request-to-exchange')) {
                actionKey = 'toExchange';
                actionOptions = {
                    successMessage: 'Заявка переведена в обмен',
                    notificationType: 'info'
                };
            } else if (button.classList.contains('js-return-request-close')) {
                actionKey = 'close';
                actionOptions = {
                    successMessage: 'Заявка закрыта',
                    notificationType: 'warning'
                };
            } else if (button.classList.contains('js-return-request-to-return')) {
                actionKey = 'toReturn';
                actionOptions = {
                    successMessage: 'Заявка переведена в возврат',
                    notificationType: 'info'
                };
            } else if (button.classList.contains('js-return-request-confirm-reverse')) {
                actionKey = 'confirm';
                actionOptions = {
                    successMessage: 'Получение обратной посылки подтверждено',
                    notificationType: 'success'
                };
            }

            const executor = executors[actionKey];
            if (typeof executor !== 'function') {
                return;
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
