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
        const available = summary.availableActions;
        if (available && typeof available === 'object') {
            if (Array.isArray(available.actions)) {
                sources.push(available.actions);
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
     * @param {string|Array<string>} code искомое действие или набор кодов
     * @returns {boolean} {@code true}, если действие доступно
     */
    function hasActionFromSet(codeSet, code) {
        if (!(codeSet instanceof Set) || codeSet.size === 0) {
            return false;
        }
        const candidates = Array.isArray(code) ? code : [code];
        return candidates.some((candidate) => {
            const normalized = normalizeActionCode(candidate);
            return normalized ? codeSet.has(normalized) : false;
        });
    }

    /**
     * Возвращает нормализованный обратный трек из актуального DTO.
     * @param {Object} summary агрегированная информация о заявке
     * @returns {string|null} нормализованное значение трека или {@code null}
     */
    function resolveReverseTrack(summary) {
        if (!summary || typeof summary !== 'object') {
            return null;
        }
        const value = summary.reverseTrack;
        if (typeof value !== 'string') {
            return value ?? null;
        }
        const trimmed = value.trim();
        return trimmed.length > 0 ? trimmed : null;
    }

    /**
     * Извлекает пользовательскую подпись для действия из DTO доступных операций.
     * Метод применяет принцип OCP, позволяя расширять список действий без изменения
     * вызывающего кода.
     * @param {Object|null} availableActions объект доступных действий
     * @param {string} propertyName базовое имя свойства
     * @param {string} fallback запасной текст
     * @returns {string} локализованная подпись кнопки
     */
    function resolveActionLabel(availableActions, propertyName, fallback) {
        if (!availableActions || typeof availableActions !== 'object') {
            return fallback;
        }
        const key = `${propertyName}Label`;
        const raw = availableActions[key];
        if (typeof raw !== 'string') {
            return fallback;
        }
        const trimmed = raw.trim();
        return trimmed.length > 0 ? trimmed : fallback;
    }

    /**
     * Извлекает aria-label для действия, позволяя бэкенду управлять доступностью
     * без изменения фронтенда.
     * @param {Object|null} availableActions объект доступных действий
     * @param {string} propertyName базовое имя свойства
     * @param {string} fallback запасной текст aria-атрибута
     * @returns {string} локализованный aria-label
     */
    function resolveActionAriaLabel(availableActions, propertyName, fallback) {
        if (!availableActions || typeof availableActions !== 'object') {
            return fallback;
        }
        const key = `${propertyName}AriaLabel`;
        const raw = availableActions[key];
        if (typeof raw !== 'string') {
            return fallback;
        }
        const trimmed = raw.trim();
        return trimmed.length > 0 ? trimmed : fallback;
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
        row.dataset.exchangeRequested = summary.exchangeRequested ? 'true' : 'false';
        row.dataset.requestId = String(summary.requestId);

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
        if (reverseSpan && 'reverseTrack' in summary) {
            const reverseValue = resolveReverseTrack(summary);
            const reverseText = reverseValue
                ? `Обратный трек: ${reverseValue}`
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

        const actionCodeSet = collectActionCodes(summary);
        const allowConfirmReceipt = hasActionFromSet(actionCodeSet, 'mark_inbound_picked_up');
        const allowConvertToExchange = hasActionFromSet(actionCodeSet, 'set_mode_exchange');
        const allowCloseRequest = hasActionFromSet(actionCodeSet, 'close_request');
        const allowUpdateReverseTrack = hasActionFromSet(actionCodeSet, 'update_reverse_track');
        const allowConvertToReturn = hasActionFromSet(actionCodeSet, 'set_mode_return');
        const statusRaw = typeof summary.stage === 'string' ? summary.stage.toUpperCase() : '';
        const isExchangeStatus = statusRaw.includes('EXCHANGE');

        const availableActions = summary.availableActions && typeof summary.availableActions === 'object'
            ? summary.availableActions
            : null;

        const syncButton = (button, { visible, label, ariaLabel }) => {
            if (!button) {
                return;
            }
            const isVisible = Boolean(visible);
            button.classList.toggle('d-none', !isVisible);
            button.setAttribute('aria-hidden', isVisible ? 'false' : 'true');
            button.disabled = !isVisible;
            button.setAttribute('aria-disabled', isVisible ? 'false' : 'true');
            if (isVisible && label) {
                button.textContent = label;
                button.dataset.actionLabel = label;
            }
            if (isVisible && ariaLabel) {
                button.setAttribute('aria-label', ariaLabel);
            } else if (!isVisible) {
                button.removeAttribute('aria-label');
            }
        };

        const confirmReturnButton = row.querySelector('.js-return-request-confirm-return');
        syncButton(confirmReturnButton, {
            visible: allowConfirmReceipt && !isExchangeStatus,
            label: 'Принять возврат'
        });

        const toExchangeButton = row.querySelector('.js-return-request-to-exchange');
        syncButton(toExchangeButton, {
            visible: allowConvertToExchange,
            label: 'Перевести в обмен'
        });

        const closeButton = row.querySelector('.js-return-request-close');
        syncButton(closeButton, {
            visible: allowCloseRequest,
            label: 'Закрыть обращение'
        });

        const addReverseButton = row.querySelector('.js-return-request-add-reverse');
        syncButton(addReverseButton, {
            visible: allowUpdateReverseTrack,
            label: 'Добавить трек обратной посылки'
        });

        const toReturnButton = row.querySelector('.js-return-request-to-return');
        syncButton(toReturnButton, {
            visible: allowConvertToReturn,
            label: 'Перевести в возврат'
        });

        const confirmReverseButton = row.querySelector('.js-return-request-confirm-reverse');
        syncButton(confirmReverseButton, {
            visible: allowConfirmReceipt && isExchangeStatus,
            label: 'Принять обратную посылку'
        });

        const allowMarkOutboundSent = hasActionFromSet(actionCodeSet, 'mark_outbound_sent');
        const allowMarkInboundArrived = hasActionFromSet(actionCodeSet, 'mark_inbound_arrived');
        const allowRegisterExchange = hasActionFromSet(actionCodeSet, 'register_exchange_parcel');
        const allowMarkExchangeSent = hasActionFromSet(actionCodeSet, 'mark_exchange_sent');
        const allowMarkExchangeDelivered = hasActionFromSet(actionCodeSet, 'mark_exchange_delivered');

        const outboundButton = row.querySelector('.js-return-request-mark-outbound');
        syncButton(outboundButton, {
            visible: allowMarkOutboundSent,
            label: resolveActionLabel(availableActions, 'markOutboundSent', 'Отправка возврата'),
            ariaLabel: resolveActionAriaLabel(
                availableActions,
                'markOutboundSent',
                'Отметить отправку возвратной посылки магазином'
            )
        });

        const inboundButton = row.querySelector('.js-return-request-mark-inbound');
        syncButton(inboundButton, {
            visible: allowMarkInboundArrived,
            label: resolveActionLabel(availableActions, 'markInboundArrived', 'Возврат на складе'),
            ariaLabel: resolveActionAriaLabel(
                availableActions,
                'markInboundArrived',
                'Отметить прибытие возвратной посылки на склад'
            )
        });

        const registerExchangeButton = row.querySelector('.js-return-request-register-exchange');
        syncButton(registerExchangeButton, {
            visible: allowRegisterExchange,
            label: resolveActionLabel(availableActions, 'registerExchangeParcel', 'Запустить обмен'),
            ariaLabel: resolveActionAriaLabel(
                availableActions,
                'registerExchangeParcel',
                'Запустить обмен по обращению'
            )
        });

        const exchangeSentButton = row.querySelector('.js-return-request-mark-exchange-sent');
        syncButton(exchangeSentButton, {
            visible: allowMarkExchangeSent,
            label: resolveActionLabel(availableActions, 'markExchangeSent', 'Отправка обмена'),
            ariaLabel: resolveActionAriaLabel(
                availableActions,
                'markExchangeSent',
                'Отметить отправку обменной посылки'
            )
        });

        const exchangeDeliveredButton = row.querySelector('.js-return-request-mark-exchange-delivered');
        syncButton(exchangeDeliveredButton, {
            visible: allowMarkExchangeDelivered,
            label: resolveActionLabel(availableActions, 'markExchangeDelivered', 'Доставка обмена'),
            ariaLabel: resolveActionAriaLabel(
                availableActions,
                'markExchangeDelivered',
                'Отметить доставку обменной посылки клиенту'
            )
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
            markOutboundSent(trackId, requestId, options = {}) {
                const fn = window.trackModal?.markReturnOutboundSent;
                if (typeof fn !== 'function') {
                    return Promise.reject(new Error('Отметка отправки возврата недоступна'));
                }
                return fn(trackId, requestId, options);
            },
            markInboundArrived(trackId, requestId, options = {}) {
                const fn = window.trackModal?.markReturnInboundArrived;
                if (typeof fn !== 'function') {
                    return Promise.reject(new Error('Отметка прибытия возврата недоступна'));
                }
                return fn(trackId, requestId, options);
            },
            toExchange(trackId, requestId, options = {}) {
                const fn = window.trackModal?.convertReturnRequestToExchange;
                if (typeof fn !== 'function') {
                    return Promise.reject(new Error('Перевод заявки в обмен недоступен'));
                }
                return fn(trackId, requestId, options);
            },
            registerExchangeParcel(trackId, requestId, options = {}) {
                const fn = window.trackModal?.createExchangeParcel
                    || window.trackModal?.launchExchange;
                if (typeof fn !== 'function') {
                    return Promise.reject(new Error('Запуск обмена недоступен'));
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
            markExchangeSent(trackId, requestId, options = {}) {
                const fn = window.trackModal?.markExchangeShipmentSent;
                if (typeof fn !== 'function') {
                    return Promise.reject(new Error('Отметка отправки обмена недоступна'));
                }
                return fn(trackId, requestId, options);
            },
            markExchangeDelivered(trackId, requestId, options = {}) {
                const fn = window.trackModal?.markExchangeShipmentDelivered;
                if (typeof fn !== 'function') {
                    return Promise.reject(new Error('Отметка доставки обмена недоступна'));
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
            const button = event.target.closest([
                '.js-return-request-confirm-return',
                '.js-return-request-to-exchange',
                '.js-return-request-close',
                '.js-return-request-add-reverse',
                '.js-return-request-to-return',
                '.js-return-request-confirm-reverse',
                '.js-return-request-mark-outbound',
                '.js-return-request-mark-inbound',
                '.js-return-request-register-exchange',
                '.js-return-request-mark-exchange-sent',
                '.js-return-request-mark-exchange-delivered'
            ].join(', '));
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
            } else if (button.classList.contains('js-return-request-mark-outbound')) {
                actionKey = 'markOutboundSent';
                actionOptions = {
                    successMessage: 'Отправка возвратной посылки отмечена',
                    notificationType: 'info'
                };
            } else if (button.classList.contains('js-return-request-mark-inbound')) {
                actionKey = 'markInboundArrived';
                actionOptions = {
                    successMessage: 'Прибытие возвратной посылки отмечено',
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
            } else if (button.classList.contains('js-return-request-register-exchange')) {
                actionKey = 'registerExchangeParcel';
                actionOptions = {
                    successMessage: 'Обмен запущен',
                    notificationType: 'info'
                };
            } else if (button.classList.contains('js-return-request-mark-exchange-sent')) {
                actionKey = 'markExchangeSent';
                actionOptions = {
                    successMessage: 'Отправка обменной посылки отмечена',
                    notificationType: 'info'
                };
            } else if (button.classList.contains('js-return-request-mark-exchange-delivered')) {
                actionKey = 'markExchangeDelivered';
                actionOptions = {
                    successMessage: 'Доставка обменной посылки отмечена',
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
