(() => {
    'use strict';

    /** Текущий идентификатор интервала обратного отсчёта. */
    let refreshTimerId = null;

    /** Счётчик для генерации уникальных идентификаторов элементов формы. */
    let elementSequence = 0;

    /**
     * Останавливает активный таймер обновления.
     * Метод вызывается при повторном рендере и закрытии модального окна,
     * чтобы не выполнять лишние вычисления в фоне.
     */
    function clearRefreshTimer() {
        if (refreshTimerId !== null) {
            window.clearInterval(refreshTimerId);
            refreshTimerId = null;
        }
    }

    /**
     * Запускает таймер для разблокировки кнопки обновления.
     * Метод выводит пользовательское сообщение, если обратный отсчёт недоступен (OCP),
     * и делегирует обновление тултипа внешнему обработчику, сохраняя SRP.
     * @param {HTMLButtonElement} button кнопка «Обновить»
     * @param {HTMLElement} countdown элемент с текстом обратного отсчёта
     * @param {string|null} nextRefreshAt ISO-строка следующего обновления
     * @param {boolean} refreshAllowed признак немедленного обновления
     * @param {string|null} finalStatusMessage текст сообщения для финального статуса
     * @param {string|null} unavailableReason текст причины недоступности
     * @param {Function} [onTooltipChange] колбэк для синхронизации текста тултипа
     */
    function startRefreshTimer(
        button,
        countdown,
        nextRefreshAt,
        refreshAllowed,
        finalStatusMessage,
        unavailableReason,
        onTooltipChange
    ) {
        clearRefreshTimer();
        if (!button || !countdown) {
            return;
        }

        const defaultTooltipText = button.dataset.defaultTooltip
            || button.getAttribute('title')
            || '';

        /**
         * Обновляет состояние кнопки и подписи, соблюдая доступность.
         * Метод уведомляет внешний обработчик об изменении подсказки, чтобы таймер не зависел от DOM.
         * @param {Object} options параметры отображения
         * @param {string} options.text отображаемый текст рядом с кнопкой
         * @param {boolean} options.disabled нужно ли блокировать кнопку
         * @param {string|null} [options.tooltipText] актуальный текст тултипа
         */
        const applyState = ({ text, disabled, tooltipText }) => {
            button.disabled = disabled;
            button.setAttribute('aria-disabled', String(disabled));
            const normalizedText = text || '';
            countdown.textContent = normalizedText;
            const usesTooltip = Boolean(tooltipText) && normalizedText.length === 0;
            const shouldHide = usesTooltip || normalizedText.length === 0;
            countdown.classList.toggle('visually-hidden', shouldHide);
            countdown.setAttribute('aria-hidden', shouldHide ? 'true' : 'false');
            const tooltipValue = tooltipText || defaultTooltipText;
            if (typeof onTooltipChange === 'function') {
                onTooltipChange(tooltipValue, tooltipText);
            }
        };

        const showActiveState = () => applyState({ text: '', disabled: false, tooltipText: null });

        if (refreshAllowed) {
            showActiveState();
            return;
        }

        if (!nextRefreshAt) {
            const fallbackText = finalStatusMessage || unavailableReason || 'Можно выполнить через —';
            applyState({
                text: fallbackText,
                disabled: true,
                tooltipText: finalStatusMessage ? null : unavailableReason || null
            });
            return;
        }

        const target = Date.parse(nextRefreshAt);
        if (Number.isNaN(target)) {
            const fallbackText = finalStatusMessage || unavailableReason || 'Можно выполнить через —';
            applyState({
                text: fallbackText,
                disabled: true,
                tooltipText: finalStatusMessage ? null : unavailableReason || null
            });
            return;
        }

        const tick = () => {
            const diff = target - Date.now();
            if (diff <= 0) {
                showActiveState();
                clearRefreshTimer();
                return;
            }
            const totalSeconds = Math.ceil(diff / 1000);
            const hours = Math.floor(totalSeconds / 3600);
            const minutes = Math.floor((totalSeconds % 3600) / 60);
            const seconds = totalSeconds % 60;
            const formatted = [hours, minutes, seconds]
                .map((part) => String(part).padStart(2, '0'))
                .join(':');
            applyState({
                text: `Можно выполнить через ${formatted}`,
                disabled: true,
                tooltipText: null
            });
        };

        tick();
        refreshTimerId = window.setInterval(tick, 1000);
    }

    /**
     * Форматирует ISO-дату в часовой пояс пользователя.
     * Метод отвечает только за человеко-читаемое представление дат, соблюдая SRP.
     * @param {string} isoString ISO-строка даты
     * @param {string} timeZone идентификатор часового пояса
     * @returns {string} отформатированная дата или тире при ошибке
     */
    function formatDateTime(isoString, timeZone) {
        if (!isoString) {
            return '—';
        }
        try {
            const options = {
                year: 'numeric',
                month: '2-digit',
                day: '2-digit',
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit'
            };
            const formatter = new Intl.DateTimeFormat(undefined, {
                ...options,
                timeZone: timeZone || undefined
            });
            return formatter.format(new Date(isoString));
        } catch (error) {
            console.warn('Не удалось форматировать дату', isoString, error);
            return isoString;
        }
    }

    /**
     * Возвращает значение в формате datetime-local для указанной даты.
     * Метод обеспечивает совместимость с нативными контролами браузера.
     * @param {Date} date исходная дата
     * @returns {string} отформатированное значение или пустая строка
     */
    function formatDateTimeLocal(date) {
        if (!(date instanceof Date) || Number.isNaN(date.getTime())) {
            return '';
        }
        const offsetMinutes = date.getTimezoneOffset();
        const localTime = new Date(date.getTime() - offsetMinutes * 60000);
        return localTime.toISOString().slice(0, 16);
    }

    /**
     * Возвращает объект с CSRF-заголовком на основе глобальных переменных.
     * Метод инкапсулирует работу с CSRF, не привязывая модуль к app.js (ISP).
     * @returns {Object} карта заголовков или пустой объект
     */
    function buildCsrfHeaders() {
        const header = window.csrfHeader;
        const token = window.csrfToken;
        return header && token ? { [header]: token } : {};
    }

    /**
     * Создаёт уникальный идентификатор на основе заданного префикса.
     * Метод упрощает связывание label и элемента управления.
     * @param {string} prefix базовый префикс
     * @returns {string} уникальный идентификатор
     */
    function generateElementId(prefix) {
        elementSequence += 1;
        const safePrefix = prefix || 'control';
        return `${safePrefix}-${elementSequence}`;
    }

    /**
     * Создаёт кнопку действия с единым оформлением.
     * @param {Object} options параметры кнопки
     * @param {string} options.text отображаемый текст
     * @param {string} [options.variant] модификатор Bootstrap
     * @param {string} [options.ariaLabel] текст для screen reader
     * @param {Function} options.onClick обработчик клика
     * @returns {HTMLButtonElement} созданная кнопка
     */
    function createActionButton({ text, variant = 'outline-primary', ariaLabel, onClick }) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = `btn btn-${variant} btn-sm`;
        button.textContent = text;
        if (ariaLabel) {
            button.setAttribute('aria-label', ariaLabel);
        }
        if (typeof onClick === 'function') {
            button.addEventListener('click', (event) => {
                event.preventDefault();
                onClick(button);
            });
        }
        return button;
    }

    /**
     * Формирует подписанный контрол формы с необязательным описанием.
     * @param {string} labelText текст подписи
     * @param {HTMLElement} control элемент управления
     * @param {string} [description] дополнительная подсказка
     * @returns {HTMLElement} контейнер с оформлением Bootstrap
     */
    function createLabeledControl(labelText, control, description) {
        const wrapper = document.createElement('div');
        wrapper.className = 'mb-3';

        const label = document.createElement('label');
        label.className = 'form-label';
        label.textContent = labelText;
        if (control.id) {
            label.setAttribute('for', control.id);
        }

        wrapper.appendChild(label);
        wrapper.appendChild(control);

        if (description) {
            const hint = document.createElement('div');
            hint.className = 'form-text';
            hint.textContent = description;
            wrapper.appendChild(hint);
        }

        return wrapper;
    }

    /**
     * Добавляет элемент описательного списка в единообразном формате.
     * @param {HTMLElement} list контейнер <dl>
     * @param {string} term заголовок значения
     * @param {string} value отображаемое значение
     */
    function appendDefinitionItem(list, term, value) {
        if (!list) {
            return;
        }
        const title = document.createElement('dt');
        title.className = 'col-sm-5 col-lg-4';
        title.textContent = term;

        const definition = document.createElement('dd');
        definition.className = 'col-sm-7 col-lg-8';
        definition.textContent = value && value.length > 0 ? value : '—';

        list.append(title, definition);
    }

    /**
     * Отправляет запрос к REST-эндпоинтам управления заявками.
     * @param {string} url относительный URL запроса
     * @param {RequestInit} [options] дополнительные настройки fetch
     * @returns {Promise<Object>} десериализованный ответ контроллера
     */
    async function sendReturnRequest(url, options = {}) {
        const response = await fetch(url, {
            method: 'POST',
            headers: {
                Accept: 'application/json',
                ...buildCsrfHeaders(),
                ...(options.headers || {})
            },
            body: options.body ?? null
        });
        const contentType = response.headers.get('content-type') || '';
        const isJson = contentType.includes('application/json');
        const payload = isJson ? await response.json() : await response.text();
        if (!response.ok) {
            const message = (isJson && payload && typeof payload === 'object' && payload.message)
                ? payload.message
                : (typeof payload === 'string' ? payload : 'Операция недоступна');
            throw new Error(message);
        }
        return payload;
    }

    /**
     * Обновляет строку таблицы, чтобы отразить требуемые действия.
     * @param {Object} details DTO деталей трека
     */
    function updateRowRequiresAction(details) {
        if (!details || typeof details !== 'object' || details.id === undefined) {
            return;
        }
        const row = document.querySelector(`tr[data-track-id="${details.id}"]`);
        if (!row) {
            return;
        }
        const requiresAction = Boolean(details.requiresAction);
        row.dataset.requiresAction = requiresAction ? 'true' : 'false';
        const badgeSelector = '.badge.bg-warning-subtle';
        let badge = row.querySelector(badgeSelector);
        if (requiresAction) {
            if (!badge) {
                const container = row.querySelector('div.d-inline-flex');
                if (container) {
                    badge = document.createElement('span');
                    badge.className = 'badge rounded-pill bg-warning-subtle text-warning-emphasis';
                    badge.setAttribute('aria-label', 'По посылке требуется действие');
                    badge.textContent = 'Действие';
                    container.appendChild(badge);
                }
            }
        } else if (badge) {
            badge.remove();
        }
    }

    /**
     * Пересчитывает счётчик вкладки «Требуют действия» и применяет активный фильтр.
     */
    function updateActionTabCounter() {
        const badge = document.querySelector('#departuresActionTabs .badge');
        if (!badge) {
            return;
        }
        const rows = Array.from(document.querySelectorAll('.history-table tbody tr'));
        const count = rows.filter((row) => row.dataset.requiresAction === 'true').length;
        badge.textContent = String(count);
        badge.classList.toggle('visually-hidden', count === 0);

        if (typeof window.returnRequests?.refreshEmptyState === 'function') {
            window.returnRequests.refreshEmptyState();
        }
    }

    /**
     * Возвращает идемпотентный ключ для регистрации заявок.
     */
    function generateIdempotencyKey() {
        if (window.crypto && typeof window.crypto.randomUUID === 'function') {
            return window.crypto.randomUUID();
        }
        return `return-${Date.now()}-${Math.random().toString(16).slice(2)}`;
    }

    /**
     * Выполняет сетевое действие с управлением состоянием кнопки.
     * @param {HTMLButtonElement} button управляемая кнопка
     * @param {Function} action асинхронное действие
     */
    async function runButtonAction(button, action) {
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
            notifyUser(error?.message || 'Не удалось выполнить действие', 'danger');
        } finally {
            if (button && document.body.contains(button)) {
                button.disabled = false;
                button.setAttribute('aria-busy', 'false');
            }
        }
    }

    /**
     * Создаёт форму регистрации возврата и навешивает обработчики.
     * Метод инкапсулирует работу с DOM, соблюдая принцип единой ответственности.
     * @param {number} trackId идентификатор посылки
     * @returns {HTMLFormElement} форма отправки заявки
     */
    function createReturnRegistrationForm(trackId) {
        const form = document.createElement('form');
        form.className = 'd-flex flex-column gap-2';
        form.noValidate = true;

        const reasonInput = document.createElement('input');
        reasonInput.type = 'text';
        reasonInput.className = 'form-control';
        reasonInput.name = 'reason';
        reasonInput.required = true;
        reasonInput.maxLength = 255;
        reasonInput.id = generateElementId('return-reason');
        reasonInput.autocomplete = 'off';
        reasonInput.placeholder = 'Например, повреждение товара';

        const reasonControl = createLabeledControl(
            'Причина обращения',
            reasonInput,
            'Опишите причину возврата (до 255 символов).'
        );

        const commentInput = document.createElement('textarea');
        commentInput.className = 'form-control';
        commentInput.name = 'comment';
        commentInput.rows = 3;
        commentInput.maxLength = 2000;
        commentInput.id = generateElementId('return-comment');
        commentInput.placeholder = 'Дополнительная информация (необязательно)';

        const commentControl = createLabeledControl(
            'Комментарий',
            commentInput,
            'Необязательное поле, до 2000 символов.'
        );

        const requestedInput = document.createElement('input');
        requestedInput.type = 'datetime-local';
        requestedInput.className = 'form-control';
        requestedInput.name = 'requestedAt';
        requestedInput.required = true;
        requestedInput.step = 60;
        requestedInput.id = generateElementId('return-requested-at');
        const nowValue = formatDateTimeLocal(new Date());
        if (nowValue) {
            requestedInput.value = nowValue;
        }
        requestedInput.addEventListener('focus', () => {
            const limit = formatDateTimeLocal(new Date());
            if (limit) {
                requestedInput.max = limit;
            }
        });

        const requestedControl = createLabeledControl(
            'Дата обращения',
            requestedInput,
            'Укажите фактический момент запроса возврата.'
        );

        const reverseInput = document.createElement('input');
        reverseInput.type = 'text';
        reverseInput.className = 'form-control';
        reverseInput.name = 'reverseTrackNumber';
        reverseInput.maxLength = 64;
        reverseInput.id = generateElementId('return-reverse-track');
        reverseInput.placeholder = 'Например, LP123456789BY';
        reverseInput.autocomplete = 'off';

        const reverseControl = createLabeledControl(
            'Трек обратной отправки',
            reverseInput,
            'Необязательное поле, до 64 символов.'
        );

        const submitButton = document.createElement('button');
        submitButton.type = 'submit';
        submitButton.className = 'btn btn-warning align-self-start';
        submitButton.textContent = 'Отправить заявку';

        form.append(reasonControl, commentControl, requestedControl, reverseControl, submitButton);

        form.addEventListener('submit', (event) => {
            event.preventDefault();
            if (!form.reportValidity()) {
                return;
            }
            const formValues = {
                reason: reasonInput.value,
                comment: commentInput.value,
                requestedAt: requestedInput.value,
                reverseTrackNumber: reverseInput.value
            };
            runButtonAction(submitButton, () => handleRegisterReturnAction(trackId, formValues));
        });

        return form;
    }

    /**
     * Подготавливает тело запроса регистрации возврата.
     * @param {Object} formValues значения полей формы
     * @returns {Object} нормализованные данные для API
     */
    function buildReturnRegistrationPayload(formValues = {}) {
        const reason = (formValues.reason ?? '').trim();
        if (reason.length === 0) {
            throw new Error('Укажите причину возврата');
        }
        if (reason.length > 255) {
            throw new Error('Причина возврата не должна превышать 255 символов');
        }

        const commentRaw = (formValues.comment ?? '').trim();
        if (commentRaw.length > 2000) {
            throw new Error('Комментарий не должен превышать 2000 символов');
        }

        const requestedValue = formValues.requestedAt;
        if (!requestedValue) {
            throw new Error('Укажите дату обращения');
        }
        const requestedDate = new Date(requestedValue);
        if (Number.isNaN(requestedDate.getTime())) {
            throw new Error('Неверный формат даты обращения');
        }
        const now = new Date();
        if (requestedDate.getTime() - now.getTime() > 60000) {
            throw new Error('Дата обращения не может быть из будущего');
        }

        const reverseRaw = (formValues.reverseTrackNumber ?? '').trim();
        if (reverseRaw.length > 64) {
            throw new Error('Трек обратной отправки не должен превышать 64 символа');
        }

        return {
            idempotencyKey: generateIdempotencyKey(),
            reason,
            requestedAt: requestedDate.toISOString(),
            comment: commentRaw.length > 0 ? commentRaw : null,
            reverseTrackNumber: reverseRaw.length > 0 ? reverseRaw.toUpperCase() : null
        };
    }

    async function handleRegisterReturnAction(trackId, formValues) {
        if (!trackId) {
            return;
        }
        const requestBody = buildReturnRegistrationPayload(formValues);
        const payload = await sendReturnRequest(`/api/v1/tracks/${trackId}/returns`, {
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(requestBody)
        });
        renderTrackModal(payload);
        updateRowRequiresAction(payload);
        updateActionTabCounter();
        notifyUser('Заявка на возврат зарегистрирована', 'success');
    }

    async function handleApproveExchangeAction(trackId, requestId, options = {}) {
        if (!trackId || !requestId) {
            return null;
        }
        const payload = await sendReturnRequest(`/api/v1/tracks/${trackId}/returns/${requestId}/exchange`);
        const details = payload?.details ?? payload ?? null;
        const exchangeItem = payload?.exchange ?? null;
        renderTrackModal(details, { exchangeItem });
        if (details) {
            updateRowRequiresAction(details);
        }
        if (typeof window.returnRequests?.removeRowByIds === 'function') {
            window.returnRequests.removeRowByIds(trackId, requestId);
        }
        updateActionTabCounter();
        const successMessage = options.successMessage || 'Запущен обмен по заявке';
        const notificationType = options.notificationType || 'success';
        notifyUser(successMessage, notificationType);
        return details;
    }

    async function handleCloseWithoutExchange(trackId, requestId, options = {}) {
        if (!trackId || !requestId) {
            return null;
        }
        const payload = await sendReturnRequest(`/api/v1/tracks/${trackId}/returns/${requestId}/close`);
        renderTrackModal(payload);
        updateRowRequiresAction(payload);
        if (typeof window.returnRequests?.removeRowByIds === 'function') {
            window.returnRequests.removeRowByIds(trackId, requestId);
        }
        updateActionTabCounter();
        const message = options.successMessage || 'Заявка закрыта без обмена';
        const notificationType = options.notificationType || 'info';
        notifyUser(message, notificationType);
        return payload;
    }

    /**
     * Создаёт карточку модального окна с заголовком и телом.
     * Метод устраняет дублирование разметки и упрощает расширение модалки (OCP).
     * @param {string} title заголовок карточки
     * @returns {{card: HTMLElement, body: HTMLElement}} карточка и контейнер содержимого
     */
    function createCard(title) {
        const card = document.createElement('section');
        card.className = 'card shadow-sm border-0 rounded-4 mb-3';
        const body = document.createElement('div');
        body.className = 'card-body';
        if (title) {
            const heading = document.createElement('h6');
            heading.className = 'text-uppercase text-muted small mb-3';
            heading.textContent = title;
            body.appendChild(heading);
        }
        card.appendChild(body);
        return { card, body };
    }

    /**
     * Формирует навигацию по связанным трекам эпизода.
     * Метод создаёт доступную разметку с кнопками переключения, не полагаясь на Bootstrap.
     * @param {Array<Object>} chainData элементы цепочки
     * @param {Function} onSelect обработчик выбора другого трека
     * @returns {HTMLElement|null} контейнер навигации или {@code null}
     */
    function createChainNavigation(chainData, onSelect) {
        if (!Array.isArray(chainData) || chainData.length === 0) {
            return null;
        }

        const nav = document.createElement('nav');
        nav.className = 'mt-3';
        nav.setAttribute('aria-label', 'Связанные посылки');

        const list = document.createElement('div');
        list.className = 'd-flex flex-wrap gap-2';

        chainData.forEach((item) => {
            if (!item || typeof item !== 'object' || item.id === undefined) {
                return;
            }

            const button = document.createElement('button');
            button.type = 'button';
            button.className = item.current
                ? 'btn btn-primary btn-sm track-chain__item'
                : 'btn btn-outline-secondary btn-sm track-chain__item';
            button.dataset.trackId = String(item.id);

            const numberText = item.number ? item.number : 'Без номера';
            const visualText = item.exchange ? `${numberText} · обмен` : numberText;
            button.textContent = visualText;

            const ariaParts = [];
            ariaParts.push(item.number ? `Трек ${item.number}` : 'Трек без номера');
            if (item.exchange) {
                ariaParts.push('обмен');
            }
            if (item.current) {
                ariaParts.push('текущий');
            }
            button.setAttribute('aria-label', ariaParts.join(', '));

            if (item.current) {
                button.disabled = true;
                button.setAttribute('aria-current', 'true');
            } else if (typeof onSelect === 'function') {
                button.addEventListener('click', () => onSelect(item));
            }

            list.appendChild(button);
        });

        if (!list.hasChildNodes()) {
            return null;
        }

        nav.appendChild(list);
        return nav;
    }

    /**
     * Отрисовывает содержимое модального окна с деталями трека.
     * Метод собирает карточки интерфейса и обновляет заголовок без сетевых обращений (SRP).
     * @param {Object} data DTO с сервера
     */
    function renderTrackModal(data, options = {}) {
        clearRefreshTimer();

        const modal = document.getElementById('infoModal');
        const container = document.getElementById('trackModalContent')
            || modal?.querySelector('.modal-body');
        if (!container) {
            return;
        }

        const timeZone = data?.timeZone;
        const format = (value) => formatDateTime(value, timeZone);
        const history = Array.isArray(data?.history) ? data.history : [];

        container.replaceChildren();
        container.classList.remove('justify-content-center', 'align-items-center', 'text-muted');

        const exchangeItem = options?.exchangeItem || null;

        const layout = document.createElement('div');
        layout.className = 'd-flex flex-column gap-3 w-100';
        if (data?.id !== undefined) {
            layout.dataset.trackId = String(data.id);
        }

        const parcelCard = createCard('Трек');
        const parcelHeader = document.createElement('div');
        parcelHeader.className = 'd-flex flex-wrap justify-content-between align-items-start gap-3';

        const trackInfo = document.createElement('div');
        trackInfo.className = 'd-flex flex-column w-100 flex-grow-1';

        const trackTitleRow = document.createElement('div');
        trackTitleRow.className = 'd-flex align-items-center gap-2 justify-content-between w-100';

        const trackTitleColumn = document.createElement('div');
        trackTitleColumn.className = 'd-flex align-items-center flex-grow-1';

        const trackNumber = document.createElement('div');
        trackNumber.className = 'fs-3 fw-semibold';
        const trackText = data?.number ? data.number : 'Трек не указан';
        trackNumber.textContent = trackText;
        if (!data?.number) {
            trackNumber.classList.add('text-muted');
        }

        trackTitleColumn.appendChild(trackNumber);

        if (data?.exchange) {
            const exchangeBadge = document.createElement('span');
            exchangeBadge.className = 'badge rounded-pill bg-warning-subtle text-warning-emphasis ms-3';
            exchangeBadge.textContent = 'Обмен';
            exchangeBadge.setAttribute('aria-label', 'Посылка оформлена как обмен');
            trackTitleColumn.appendChild(exchangeBadge);
        }

        const trackActions = document.createElement('div');
        trackActions.className = 'd-flex justify-content-end flex-grow-1 gap-2';

        const trackId = data?.id;
        const returnRequest = data?.returnRequest;

        if (returnRequest?.canStartExchange && trackId !== undefined && returnRequest.id !== undefined) {
            const exchangeButton = createActionButton({
                text: 'Запустить обмен',
                variant: 'primary',
                ariaLabel: 'Запустить обменную отправку',
                onClick: (button) => runButtonAction(button,
                    () => handleApproveExchangeAction(trackId, returnRequest.id))
            });
            trackActions.appendChild(exchangeButton);
        }

        if (returnRequest?.canCloseWithoutExchange && trackId !== undefined && returnRequest.id !== undefined) {
            const closeButton = createActionButton({
                text: 'Закрыть без обмена',
                variant: 'outline-secondary',
                ariaLabel: 'Закрыть заявку без обмена',
                onClick: (button) => runButtonAction(button,
                    () => handleCloseWithoutExchange(trackId, returnRequest.id))
            });
            trackActions.appendChild(closeButton);
        }

        trackTitleRow.append(trackTitleColumn, trackActions);

        const serviceInfo = document.createElement('div');
        serviceInfo.className = 'text-muted small';
        serviceInfo.textContent = data?.deliveryService || 'Служба доставки не определена';

        trackInfo.append(trackTitleRow, serviceInfo);

        if (data?.episodeNumber !== undefined && data.episodeNumber !== null) {
            const episodeInfo = document.createElement('div');
            episodeInfo.className = 'text-muted small';
            episodeInfo.textContent = `Эпизод №${data.episodeNumber}`;
            episodeInfo.setAttribute('aria-label', `Номер эпизода: ${data.episodeNumber}`);
            trackInfo.appendChild(episodeInfo);
        }
        parcelHeader.appendChild(trackInfo);

        parcelCard.body.appendChild(parcelHeader);

        const exchangeCancellationMessage = returnRequest?.exchangeCancellationMessage;
        if (exchangeCancellationMessage) {
            const warning = document.createElement('div');
            warning.className = 'alert alert-warning d-flex align-items-center gap-2 mt-3 mb-0';
            warning.setAttribute('role', 'alert');
            warning.textContent = exchangeCancellationMessage;
            parcelCard.body.appendChild(warning);
        }

        const chainNav = createChainNavigation(
            Array.isArray(data?.chain) ? data.chain : [],
            (item) => {
                if (window.trackModal && typeof window.trackModal.loadModal === 'function') {
                    window.trackModal.loadModal(item.id);
                }
            }
        );
        if (chainNav) {
            parcelCard.body.appendChild(chainNav);
        }

        /**
         * Активирует Bootstrap-тултип для переданного элемента, если библиотека доступна.
         * Метод изолирует проверку наличия Bootstrap, чтобы переиспользовать её для разных кнопок (SRP).
         * @param {HTMLElement} element элемент, для которого нужно создать тултип
         */
        const activateTooltip = (element) => {
            if (!element || typeof bootstrap === 'undefined') {
                return;
            }
            const tooltipFactory = bootstrap.Tooltip;
            if (!tooltipFactory || typeof tooltipFactory.getOrCreateInstance !== 'function') {
                return;
            }
            tooltipFactory.getOrCreateInstance(element);
        };

        if (data?.canEditTrack && data?.id !== undefined) {
            const editButton = document.createElement('button');
            editButton.type = 'button';
            editButton.className = 'btn btn-outline-primary btn-sm d-inline-flex align-items-center justify-content-center';
            editButton.setAttribute('aria-label', 'Редактировать трек-номер');
            editButton.setAttribute('data-bs-toggle', 'tooltip');
            editButton.setAttribute('data-bs-placement', 'top');
            const EDIT_TOOLTIP_TEXT = 'Редактировать трек-номер';
            editButton.setAttribute('title', EDIT_TOOLTIP_TEXT);
            editButton.setAttribute('data-bs-original-title', EDIT_TOOLTIP_TEXT);

            const editIcon = document.createElement('i');
            editIcon.className = 'bi bi-pencil';
            editIcon.setAttribute('aria-hidden', 'true');

            const editText = document.createElement('span');
            editText.className = 'visually-hidden';
            editText.textContent = 'Редактировать трек-номер';

            editButton.append(editIcon, editText);
            editButton.addEventListener('click', () => {
                promptTrackNumber(data.id, data.number || '');
            });
            activateTooltip(editButton);
            trackActions.appendChild(editButton);
        }

        layout.appendChild(parcelCard.card);

        const returnCard = createCard('Возврат / обмен');
        if (returnRequest) {
            const infoList = document.createElement('dl');
            infoList.className = 'row g-2 mb-0';

            appendDefinitionItem(infoList, 'Статус', returnRequest.status || '—');
            appendDefinitionItem(infoList, 'Причина', returnRequest.reason || '—');
            appendDefinitionItem(infoList, 'Комментарий', returnRequest.comment || '—');
            appendDefinitionItem(infoList, 'Дата обращения', format(returnRequest.requestedAt));
            appendDefinitionItem(infoList, 'Дата регистрации', format(returnRequest.createdAt));
            appendDefinitionItem(infoList, 'Дата решения', format(returnRequest.decisionAt));
            appendDefinitionItem(infoList, 'Дата закрытия', format(returnRequest.closedAt));
            appendDefinitionItem(infoList, 'Трек обратной отправки', returnRequest.reverseTrackNumber || '—');

            returnCard.body.appendChild(infoList);

            if (exchangeItem && exchangeItem.id !== undefined) {
                const exchangeNotice = document.createElement('div');
                exchangeNotice.className = 'alert alert-info d-flex flex-wrap align-items-center justify-content-between gap-2 mt-3';

                const noticeText = document.createElement('div');
                noticeText.className = 'mb-0 flex-grow-1';
                const numberLabel = exchangeItem.number ? `трек ${exchangeItem.number}` : 'трек без номера';
                noticeText.textContent = `Создана обменная посылка, ${numberLabel}.`;

                const openButton = document.createElement('button');
                openButton.type = 'button';
                openButton.className = 'btn btn-outline-primary btn-sm ms-auto';
                openButton.textContent = 'Открыть';
                openButton.setAttribute('aria-label', 'Открыть обменную посылку');
                openButton.addEventListener('click', (event) => {
                    event.preventDefault();
                    if (window.trackModal && typeof window.trackModal.loadModal === 'function') {
                        window.trackModal.loadModal(exchangeItem.id);
                    }
                });

                exchangeNotice.append(noticeText, openButton);
                returnCard.body.appendChild(exchangeNotice);
            }
        } else if (data?.canRegisterReturn && trackId !== undefined) {
            const intro = document.createElement('p');
            intro.className = 'text-muted small';
            intro.textContent = 'Заполните форму, чтобы зарегистрировать заявку на возврат или обмен.';
            returnCard.body.appendChild(intro);

            const form = createReturnRegistrationForm(trackId);
            returnCard.body.appendChild(form);
        } else {
            const emptyState = document.createElement('p');
            emptyState.className = 'text-muted mb-0';
            emptyState.textContent = 'Заявка на возврат ещё не зарегистрирована.';
            returnCard.body.appendChild(emptyState);
        }

        layout.appendChild(returnCard.card);

        const statusCard = createCard('Текущий статус');
        const statusRow = document.createElement('div');
        statusRow.className = 'd-flex flex-wrap justify-content-between align-items-start gap-3';

        const statusColumn = document.createElement('div');
        statusColumn.className = 'd-flex flex-column gap-1 flex-grow-1';

        const statusValue = document.createElement('div');
        statusValue.className = 'fs-6 fw-semibold';
        statusValue.textContent = data?.systemStatus || 'Статус не определён';

        const statusTime = document.createElement('div');
        statusTime.className = 'text-muted small';
        const formattedUpdate = data?.lastUpdateAt ? format(data.lastUpdateAt) : '—';
        statusTime.textContent = formattedUpdate === '—' ? 'Дата обновления не определена' : formattedUpdate;

        statusColumn.append(statusValue, statusTime);

        const actionContainer = document.createElement('div');
        actionContainer.className = 'd-flex flex-column align-items-end text-end gap-2';

        const refreshButton = document.createElement('button');
        refreshButton.type = 'button';
        refreshButton.className = 'btn btn-primary js-track-refresh-btn';
        refreshButton.textContent = 'Обновить';
        refreshButton.dataset.loadingText = 'Обновляем…';
        refreshButton.setAttribute('aria-label', 'Обновить данные трека');
        refreshButton.setAttribute('aria-controls', 'trackModalContent');
        refreshButton.setAttribute('data-bs-toggle', 'tooltip');
        refreshButton.setAttribute('data-bs-placement', 'top');
        const REFRESH_TOOLTIP_DEFAULT = 'Нажмите, чтобы обновить трек';
        refreshButton.setAttribute('title', REFRESH_TOOLTIP_DEFAULT);
        refreshButton.dataset.defaultTooltip = REFRESH_TOOLTIP_DEFAULT;
        if (data?.id !== undefined) {
            refreshButton.dataset.trackId = String(data.id);
        }
        activateTooltip(refreshButton);

        const countdown = document.createElement('span');
        countdown.id = 'trackRefreshCountdown';
        countdown.className = 'text-muted small visually-hidden track-refresh-countdown';
        countdown.setAttribute('role', 'status');
        countdown.setAttribute('aria-live', 'polite');
        countdown.setAttribute('aria-hidden', 'true');

        actionContainer.append(refreshButton, countdown);
        statusRow.append(statusColumn, actionContainer);

        statusCard.body.append(statusRow);
        layout.appendChild(statusCard.card);

        const historyCard = createCard('История трека');
        if (history.length === 0) {
            const emptyHistory = document.createElement('p');
            emptyHistory.className = 'text-muted mb-0';
            emptyHistory.textContent = 'История пока пуста';
            historyCard.body.appendChild(emptyHistory);
        } else {
            const timeline = document.createElement('div');
            timeline.className = 'timeline';

            history.forEach((event, index) => {
                const item = document.createElement('div');
                item.className = 'timeline-item';
                if (index === 0) {
                    item.classList.add('timeline-item-current');
                }

                const marker = document.createElement('span');
                marker.className = 'timeline-marker';
                item.appendChild(marker);

                const dateEl = document.createElement('div');
                dateEl.className = 'timeline-date text-muted small';
                dateEl.textContent = format(event.timestamp);
                item.appendChild(dateEl);

                const statusEl = document.createElement('div');
                statusEl.className = 'timeline-status';
                statusEl.textContent = event.status || '—';
                item.appendChild(statusEl);

                if (event.details) {
                    const detailsEl = document.createElement('div');
                    detailsEl.className = 'timeline-details text-muted small';
                    detailsEl.textContent = event.details;
                    item.appendChild(detailsEl);
                }

                timeline.appendChild(item);
            });

            historyCard.body.appendChild(timeline);
        }
        layout.appendChild(historyCard.card);

        container.appendChild(layout);

        const nextRefreshAt = data?.nextRefreshAt || null;
        const isFinalStatus = data?.refreshAllowed === false && !data?.nextRefreshAt;
        const unavailableReason = isFinalStatus ? 'Обновление недоступно для финального статуса' : null;
        /**
         * Обновляет тултип и aria-атрибуты кнопки, чтобы отразить текущее состояние таймера.
         * Метод использует Bootstrap API для мгновенного обновления подсказки.
         * @param {string} tooltipText результирующий текст тултипа
         * @param {string|null} rawReason исходная причина блокировки
         * @returns {string} фактический текст тултипа
         */
        const handleTooltipChange = (tooltipText, rawReason) => {
            const actualTooltip = tooltipText || REFRESH_TOOLTIP_DEFAULT;
            refreshButton.setAttribute('title', actualTooltip);
            refreshButton.setAttribute('data-bs-original-title', actualTooltip);
            const ariaLabelText = rawReason || 'Обновить данные трека';
            refreshButton.setAttribute('aria-label', ariaLabelText);
            if (typeof bootstrap !== 'undefined'
                && bootstrap.Tooltip
                && typeof bootstrap.Tooltip.getOrCreateInstance === 'function'
            ) {
                const instance = bootstrap.Tooltip.getOrCreateInstance(refreshButton);
                if (typeof instance.update === 'function') {
                    instance.update();
                }
            }
            return actualTooltip;
        };

        startRefreshTimer(
            refreshButton,
            countdown,
            nextRefreshAt,
            Boolean(data?.refreshAllowed),
            isFinalStatus ? 'Обновление больше не требуется' : null,
            unavailableReason,
            handleTooltipChange
        );

        updateRowRequiresAction(data);
        updateActionTabCounter();
    }

    /**
     * Показывает модальное окно для ввода трек-номера.
     * Метод только настраивает форму и делегирует показ Bootstrap-модали (SRP).
     * @param {string} id идентификатор отправления
     */
    function promptTrackNumber(id, currentNumber) {
        const idInput = document.querySelector('#set-track-number-form input[name="id"]');
        if (idInput) {
            idInput.value = id;
        }

        const numberInput = document.getElementById('track-number-input');
        if (numberInput) {
            numberInput.value = currentNumber || '';
        }

        const modalEl = document.getElementById('trackNumberModal');
        if (modalEl) {
            const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
            modal.show();
        }
    }

    /**
     * Отправляет введённый трек-номер на сервер.
     * Метод отвечает за сетевой запрос и обновление строки таблицы, соблюдая SRP.
     * @param {SubmitEvent} event событие отправки формы
     */
    async function handleTrackNumberFormSubmit(event) {
        event.preventDefault();

        const form = event.target;
        const id = form.querySelector('input[name="id"]').value;
        const number = form.querySelector('input[name="number"]').value;
        const normalized = number.toUpperCase().trim();

        const submitButton = form.querySelector('button[type="submit"]');
        if (submitButton) {
            submitButton.disabled = true;
        }

        try {
            const response = await fetch(`/api/v1/tracks/${id}/number`, {
                method: 'PATCH',
                headers: {
                    'Content-Type': 'application/json',
                    Accept: 'application/json',
                    ...buildCsrfHeaders()
                },
                body: JSON.stringify({ number: normalized })
            });

            let payload = null;
            const contentType = response.headers.get('content-type') || '';
            if (contentType.includes('application/json')) {
                payload = await response.json();
            }
            if (!response.ok) {
                const message = payload?.message || 'Не удалось сохранить номер';
                throw new Error(message);
            }

            if (payload?.details) {
                renderTrackModal(payload.details);
            }
            if (payload?.summary) {
                updateTableRow(payload.summary);
            }
            notifyUser('Трек-номер обновлён', 'success');
        } catch (error) {
            notifyUser('Ошибка: ' + (error?.message || 'Не удалось сохранить номер'), 'danger');
        } finally {
            const modal = bootstrap.Modal.getInstance(document.getElementById('trackNumberModal'));
            modal?.hide();
            form.reset();
            if (submitButton) {
                submitButton.disabled = false;
            }
        }
    }

    /**
     * Обновляет строку таблицы после успешного редактирования номера.
     * @param {Object} summary DTO с обновлёнными данными
     */
    function updateTableRow(summary) {
        if (!summary || typeof summary !== 'object' || summary.id === undefined) {
            return;
        }
        const row = document.querySelector(`tr[data-track-id="${summary.id}"]`);
        if (!row) {
            return;
        }
        row.dataset.trackNumber = summary.number || '';
        row.dataset.requiresAction = summary.requiresAction ? 'true' : 'false';

        const numberButton = row.querySelector('button.parcel-number');
        if (numberButton) {
            numberButton.textContent = summary.number || '—';
            numberButton.dataset.itemnumber = summary.number || '';
            numberButton.dataset.trackId = summary.id;
            numberButton.classList.add('open-modal');
        }

        const iconContainer = row.querySelector('span.status-icon');
        if (iconContainer && typeof summary.iconHtml === 'string') {
            iconContainer.innerHTML = summary.iconHtml;
        }

        updateRowRequiresAction({ id: summary.id, requiresAction: Boolean(summary.requiresAction) });
        updateActionTabCounter();
    }

    /**
     * Загружает подробную информацию по треку и показывает модальное окно.
     * Метод инкапсулирует сетевой вызов и отображение, сохраняя единичную ответственность.
     * @param {string|number} trackId идентификатор отправления
     */
    function loadModal(trackId) {
        if (!trackId) return;

        if (typeof window.showLoading === 'function') {
            window.showLoading();
        }
        const content = document.getElementById('trackModalContent');
        if (content) {
            content.textContent = 'Загрузка данных...';
        }

        fetch(`/api/v1/tracks/${trackId}`)
            .then(response => {
                if (!response.ok) {
                    throw new Error('Не удалось получить данные трека');
                }
                return response.json();
            })
            .then(data => {
                renderTrackModal(data);
                const modal = bootstrap.Modal.getOrCreateInstance(document.getElementById('infoModal'));
                modal.show();
            })
            .catch((error) => {
                console.error(error);
                if (content) {
                    content.textContent = 'Не удалось загрузить данные трека.';
                }
                notifyUser('Ошибка при загрузке данных трека', 'danger');
            })
            .finally(() => {
                if (typeof window.hideLoading === 'function') {
                    window.hideLoading();
                }
            });
    }

    /**
     * Инициализирует обработчики модального окна при загрузке страницы.
     * Метод подготавливает форму к работе и выполняет принципы DI, не создавая глобальных зависимостей.
     */
    function initModalInteractions() {
        const trackNumberForm = document.getElementById('set-track-number-form');
        if (trackNumberForm) {
            trackNumberForm.addEventListener('submit', handleTrackNumberFormSubmit);
        }
        const infoModal = document.getElementById('infoModal');
        if (infoModal) {
            infoModal.addEventListener('hidden.bs.modal', clearRefreshTimer);
        }
    }

    document.addEventListener('DOMContentLoaded', initModalInteractions);

    window.trackModal = {
        loadModal,
        promptTrackNumber,
        render: renderTrackModal,
        approveReturnExchange: (trackId, requestId, options) => handleApproveExchangeAction(trackId, requestId, options),
        closeReturnRequest: (trackId, requestId, options) => handleCloseWithoutExchange(trackId, requestId, options)
    };
})();
