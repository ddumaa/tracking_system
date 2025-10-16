(() => {
    'use strict';

    /** Текущий идентификатор интервала обратного отсчёта. */
    let refreshTimerId = null;

    /** Счётчик для генерации уникальных идентификаторов элементов формы. */
    let elementSequence = 0;

    /** Перечень предустановленных причин возврата/обмена. */
    const RETURN_REASON_OPTIONS = [
        { value: 'Не подошло', label: 'Не подошло' },
        { value: 'Брак', label: 'Брак' },
        { value: 'Не понравилось', label: 'Не понравилось' },
        { value: 'Другое', label: 'Другое' }
    ];

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
     * Проверяет, нужно ли автоматически раскрывать историю эпизода.
     * Метод выступает фасадом для анализа DTO, чтобы логика принятия решения
     * была сосредоточена в одном месте и удовлетворяла принципу SRP.
     * @param {Object} data DTO выбранного трека
     * @returns {boolean} {@code true}, если историю стоит раскрыть сразу
     */
    function shouldAutoExpandEpisodeHistory(data) {
        if (!data || typeof data !== 'object') {
            return false;
        }

        const request = data.returnRequest || null;
        if (request && typeof request === 'object') {
            if (request.requiresAction) {
                return true;
            }
            if (!request.closedAt) {
                return true;
            }
        }

        if (data.requiresAction) {
            return true;
        }

        return false;
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
     * Формирует идемпотентный ключ для регистрации заявки.
     * Метод использует доступный API браузера и гарантирует уникальность (SRP).
     * @returns {string} уникальный идентификатор запроса
     */
    function generateIdempotencyKey() {
        if (window.crypto && typeof window.crypto.randomUUID === 'function') {
            return window.crypto.randomUUID();
        }
        if (window.crypto && typeof window.crypto.getRandomValues === 'function') {
            const buffer = new Uint32Array(4);
            window.crypto.getRandomValues(buffer);
            return Array.from(buffer, (value) => value.toString(16).padStart(8, '0')).join('-');
        }
        const timestamp = Date.now().toString(16);
        const random = Math.random().toString(16).slice(2, 10);
        return `return-${timestamp}-${random}`;
    }

    /**
     * Создаёт радио-контрол для выбора типа заявки.
     * Метод изолирует генерацию элемента, чтобы облегчить поддержку (SRP/OCP).
     * @param {string} name имя группы радио-кнопок
     * @param {string} value значение радио-кнопки
     * @param {string} labelText подпись элемента
     * @param {boolean} checked выбран ли элемент по умолчанию
     * @returns {HTMLElement} контейнер с радио-кнопкой и подписью
     */
    function buildRadioOption(name, value, labelText, checked) {
        const wrapper = document.createElement('div');
        wrapper.className = 'form-check form-check-inline';

        const id = generateElementId(name);

        const input = document.createElement('input');
        input.type = 'radio';
        input.className = 'form-check-input';
        input.name = name;
        input.id = id;
        input.value = value;
        if (checked) {
            input.checked = true;
        }

        const label = document.createElement('label');
        label.className = 'form-check-label';
        label.htmlFor = id;
        label.textContent = labelText;

        wrapper.append(input, label);
        return wrapper;
    }

    /**
     * Подготавливает карточку для создания заявки на возврат/обмен.
     * Метод возвращает готовую структуру карточки либо {@code null}, если оформление недоступно.
     * @param {Object} details DTO с подробностями трека
     * @returns {{card: HTMLElement, body: HTMLElement, heading: HTMLElement|null}|null} карточка или {@code null}
     */
    function createReturnRequestCard(details) {
        if (!details || typeof details !== 'object') {
            return null;
        }
        if (!details.canRegisterReturn || details.id === undefined) {
            return null;
        }

        const { card, body } = createCard('Возврат или обмен');

        const hint = document.createElement('p');
        hint.className = 'text-muted small';
        hint.textContent = 'Оформите заявку, чтобы зафиксировать обращение покупателя.';
        body.appendChild(hint);

        const form = document.createElement('form');
        form.className = 'd-flex flex-column gap-3';
        form.setAttribute('novalidate', 'novalidate');

        const typeFieldset = document.createElement('fieldset');
        typeFieldset.className = 'd-flex flex-column gap-2';
        const typeLegend = document.createElement('legend');
        typeLegend.className = 'form-label fw-semibold mb-0';
        typeLegend.textContent = 'Тип заявки';
        typeFieldset.appendChild(typeLegend);

        const typeOptions = document.createElement('div');
        typeOptions.className = 'd-flex flex-wrap gap-3';
        typeOptions.append(
            buildRadioOption('returnRequestType', 'return', 'Возврат', true),
            buildRadioOption('returnRequestType', 'exchange', 'Обмен', false)
        );
        typeFieldset.appendChild(typeOptions);
        form.appendChild(typeFieldset);

        const reasonGroup = document.createElement('div');
        reasonGroup.className = 'd-flex flex-column gap-2';
        const reasonId = generateElementId('return-reason');
        const reasonLabel = document.createElement('label');
        reasonLabel.className = 'form-label fw-semibold mb-0';
        reasonLabel.htmlFor = reasonId;
        reasonLabel.textContent = 'Причина обращения';
        const reasonSelect = document.createElement('select');
        reasonSelect.className = 'form-select';
        reasonSelect.id = reasonId;
        reasonSelect.required = true;
        reasonSelect.name = 'returnReason';
        const placeholderOption = document.createElement('option');
        placeholderOption.value = '';
        placeholderOption.textContent = 'Выберите причину';
        placeholderOption.disabled = true;
        placeholderOption.selected = true;
        reasonSelect.appendChild(placeholderOption);
        RETURN_REASON_OPTIONS.forEach((option) => {
            const opt = document.createElement('option');
            opt.value = option.value;
            opt.textContent = option.label;
            reasonSelect.appendChild(opt);
        });
        const reasonFeedback = document.createElement('div');
        reasonFeedback.className = 'invalid-feedback';
        reasonFeedback.textContent = 'Пожалуйста, выберите причину обращения.';
        reasonSelect.addEventListener('input', () => {
            reasonSelect.classList.remove('is-invalid');
        });
        reasonGroup.append(reasonLabel, reasonSelect, reasonFeedback);
        form.appendChild(reasonGroup);

        const reverseGroup = document.createElement('div');
        reverseGroup.className = 'd-flex flex-column gap-2';
        const reverseId = generateElementId('return-reverse');
        const reverseLabel = document.createElement('label');
        reverseLabel.className = 'form-label fw-semibold mb-0';
        reverseLabel.htmlFor = reverseId;
        reverseLabel.textContent = 'Трек обратной посылки (необязательно)';
        const reverseInput = document.createElement('input');
        reverseInput.type = 'text';
        reverseInput.className = 'form-control';
        reverseInput.id = reverseId;
        reverseInput.name = 'returnReverseTrack';
        reverseInput.maxLength = 64;
        reverseInput.placeholder = 'Например, BY1234567890';
        reverseGroup.append(reverseLabel, reverseInput);
        form.appendChild(reverseGroup);

        const commentGroup = document.createElement('div');
        commentGroup.className = 'd-flex flex-column gap-2';
        const commentId = generateElementId('return-comment');
        const commentLabel = document.createElement('label');
        commentLabel.className = 'form-label fw-semibold mb-0';
        commentLabel.htmlFor = commentId;
        commentLabel.textContent = 'Комментарий (необязательно)';
        const commentInput = document.createElement('textarea');
        commentInput.className = 'form-control';
        commentInput.id = commentId;
        commentInput.name = 'returnComment';
        commentInput.rows = 3;
        commentInput.maxLength = 2000;
        commentGroup.append(commentLabel, commentInput);
        form.appendChild(commentGroup);

        const submitButton = document.createElement('button');
        submitButton.type = 'submit';
        submitButton.className = 'btn btn-primary';
        submitButton.textContent = 'Создать заявку';
        submitButton.dataset.defaultText = submitButton.textContent;

        form.appendChild(submitButton);

        form.addEventListener('submit', (event) => {
            event.preventDefault();
            submitReturnRequest({
                trackId: details.id,
                form,
                typeFieldName: 'returnRequestType',
                reasonSelect,
                reverseInput,
                commentInput,
                submitButton
            });
        });

        body.appendChild(form);
        return { card, body, heading: null };
    }

    /**
     * Отправляет запрос на создание заявки и обрабатывает ответ.
     * Метод разделяет этапы валидации, сетевого вызова и обработки UI (SRP).
     * @param {Object} options набор параметров формы
     */
    function submitReturnRequest(options) {
        const {
            trackId,
            form,
            typeFieldName,
            reasonSelect,
            reverseInput,
            commentInput,
            submitButton
        } = options || {};

        if (!trackId || !form || !reasonSelect || !submitButton) {
            return;
        }

        const reasonValue = (reasonSelect.value || '').trim();
        if (!reasonValue) {
            reasonSelect.classList.add('is-invalid');
            notifyUser('Выберите причину обращения перед отправкой заявки', 'warning');
            reasonSelect.focus();
            return;
        }

        const typeValue = form.elements[typeFieldName]?.value || 'return';
        const isExchange = typeValue === 'exchange';
        const reverseValue = (reverseInput?.value || '').trim();
        const commentValue = (commentInput?.value || '').trim();

        const payload = {
            idempotencyKey: generateIdempotencyKey(),
            reason: reasonValue,
            requestedAt: new Date().toISOString(),
            comment: commentValue.length > 0 ? commentValue : null,
            reverseTrackNumber: reverseValue.length > 0 ? reverseValue : null,
            isExchange
        };

        const headers = {
            'Content-Type': 'application/json',
            Accept: 'application/json',
            ...buildCsrfHeaders()
        };

        const originalText = submitButton.textContent;
        submitButton.disabled = true;
        submitButton.setAttribute('aria-disabled', 'true');
        submitButton.textContent = 'Создаём…';

        fetch(`/api/v1/tracks/${trackId}/returns`, {
            method: 'POST',
            headers,
            body: JSON.stringify(payload)
        })
            .then(async (response) => {
                const contentType = response.headers.get('content-type') || '';
                let bodyPayload = null;
                if (contentType.includes('application/json')) {
                    bodyPayload = await response.json();
                }
                if (!response.ok) {
                    const message = bodyPayload?.message || 'Не удалось создать заявку';
                    throw new Error(message);
                }
                return bodyPayload;
            })
            .then((details) => {
                if (details) {
                    renderTrackModal(details);
                }
                notifyUser(isExchange ? 'Заявка на обмен создана' : 'Заявка на возврат создана', 'success');
                if (typeof window.returnRequests?.refreshEmptyState === 'function') {
                    window.returnRequests.refreshEmptyState();
                }
            })
            .catch((error) => {
                const message = error?.message || 'Не удалось создать заявку';
                notifyUser(`Ошибка: ${message}`, 'danger');
            })
            .finally(() => {
                submitButton.disabled = false;
                submitButton.setAttribute('aria-disabled', 'false');
                submitButton.textContent = originalText;
            });
    }

    /**
     * Добавляет элемент описательного списка в единообразном формате.
     * @param {HTMLElement} list контейнер <dl>
     * @param {string} term заголовок значения
     * @param {string} value отображаемое значение
     * @returns {{title: HTMLElement, definition: HTMLElement}|null} ссылки на добавленные узлы или {@code null}
     */
    function appendDefinitionItem(list, term, value) {
        if (!list) {
            return null;
        }
        const title = document.createElement('dt');
        title.className = 'col-sm-5 col-lg-4';
        title.textContent = term;

        const definition = document.createElement('dd');
        definition.className = 'col-sm-7 col-lg-8';
        definition.textContent = value && value.length > 0 ? value : '—';

        list.append(title, definition);
        return { title, definition };
    }

    /**
     * Отправляет запрос к REST-эндпоинтам деталей трека.
     * Метод инкапсулирует работу с CSRF и десериализацию, чтобы вызывающий код соблюдал SRP.
     * @param {string} url относительный URL запроса
     * @param {RequestInit} [options] дополнительные настройки fetch
     * @returns {Promise<Object>} десериализованный ответ контроллера
     */
    async function sendTrackRequest(url, options = {}) {
        const method = options.method || 'POST';
        const headers = {
            Accept: 'application/json',
            ...buildCsrfHeaders(),
            ...(options.headers || {})
        };
        const response = await fetch(url, {
            method,
            headers,
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
     * Кэширует результаты ленивых запросов истории и жизненного цикла.
     * Используем {@link Map}, чтобы соблюдать SRP и изолировать хранение состояния (принцип единой ответственности).
     */
    const lazyDataCache = {
        history: new Map(),
        lifecycle: new Map()
    };

    /**
     * Сбрасывает кэш ленивых данных для конкретного трека.
     * Метод отделяет управление состоянием от бизнес-логики действий (SRP) и
     * упрощает повторное использование при любых обновлениях данных.
     * @param {number|string} trackId идентификатор трека, чьи данные нужно сбросить
     */
    function invalidateLazyDataCache(trackId) {
        if (trackId === undefined || trackId === null) {
            return;
        }

        const candidates = new Set();
        candidates.add(trackId);

        if (typeof trackId === 'string') {
            const numericId = Number(trackId);
            if (Number.isFinite(numericId)) {
                candidates.add(numericId);
            }
        }

        if (typeof trackId === 'number' && Number.isFinite(trackId)) {
            candidates.add(String(trackId));
        }

        candidates.forEach((key) => {
            lazyDataCache.history.delete(key);
            lazyDataCache.lifecycle.delete(key);
        });
    }

    /**
     * Загружает историю событий трека ровно один раз.
     * Функция выполняет запрос к REST-контроллеру и кэширует промис, чтобы переиспользовать результат (LSP и SRP).
     * @param {number} trackId идентификатор трека
     * @returns {Promise<Array<Object>>} массив событий истории
     */
    function loadHistoryOnce(trackId) {
        if (trackId === undefined || trackId === null) {
            return Promise.reject(new Error('История недоступна для неопределённого трека'));
        }
        if (lazyDataCache.history.has(trackId)) {
            return lazyDataCache.history.get(trackId);
        }
        const request = sendTrackRequest(`/api/v1/tracks/${trackId}/history`, { method: 'GET' })
            .then((payload) => {
                const historyArray = Array.isArray(payload)
                    ? payload
                    : (Array.isArray(payload?.history) ? payload.history : []);
                return historyArray;
            })
            .catch((error) => {
                lazyDataCache.history.delete(trackId);
                throw error;
            });
        lazyDataCache.history.set(trackId, request);
        return request;
    }

    /**
     * Загружает этапы жизненного цикла заказа один раз и кэширует ответ.
     * Метод скрывает детали API и переиспользует инфраструктуру fetch, соблюдая принцип инверсии зависимостей.
     * @param {number} trackId идентификатор трека
     * @returns {Promise<Array<Object>>} массив этапов жизненного цикла
     */
    function loadLifecycleOnce(trackId) {
        if (trackId === undefined || trackId === null) {
            return Promise.reject(new Error('Жизненный цикл недоступен для неопределённого трека'));
        }
        if (lazyDataCache.lifecycle.has(trackId)) {
            return lazyDataCache.lifecycle.get(trackId);
        }
        const request = sendTrackRequest(`/api/v1/tracks/${trackId}/lifecycle`, { method: 'GET' })
            .then((payload) => {
                const lifecycleArray = Array.isArray(payload)
                    ? payload
                    : (Array.isArray(payload?.lifecycle) ? payload.lifecycle : []);
                return lifecycleArray;
            })
            .catch((error) => {
                lazyDataCache.lifecycle.delete(trackId);
                throw error;
            });
        lazyDataCache.lifecycle.set(trackId, request);
        return request;
    }

    /**
     * Генерирует скелетон-заглушку для ленивых секций.
     * Метод изолирует создание декоративных элементов, чтобы их можно было переиспользовать (SRP).
     * @param {number} lines количество строк скелетона
     * @returns {HTMLElement} контейнер со скелетоном
     */
    function createSkeletonPlaceholder(lines = 3) {
        const wrapper = document.createElement('div');
        wrapper.className = 'placeholder-glow track-lazy-section__placeholder';
        for (let index = 0; index < lines; index += 1) {
            const span = document.createElement('span');
            span.className = 'placeholder col-12';
            if (index === lines - 1) {
                span.classList.add('w-75');
            }
            span.classList.add('mb-2');
            wrapper.appendChild(span);
        }
        return wrapper;
    }

    let lazySectionIdCounter = 0;

    /**
     * Создаёт секцию с ленивой загрузкой контента и управлением состоянием.
     * Функция инкапсулирует работу с ошибками, повторными попытками и скелетоном, упрощая переиспользование (SRP + OCP).
     * @param {Object} options параметры секции
     * @param {string} options.buttonLabel текст кнопки в закрытом состоянии
     * @param {string} [options.expandedLabel] текст кнопки после раскрытия
     * @param {string} [options.emptyStateText] сообщение об отсутствии данных
     * @param {() => Promise<HTMLElement|null>} options.loadContent асинхронный загрузчик содержимого
     * @param {boolean} [options.initiallyExpanded] нужно ли раскрыть блок сразу
     * @returns {{container: HTMLElement, expand: Function, collapse: Function}} управляющий объект секции
     */
    function createLazySection({
        buttonLabel,
        expandedLabel,
        emptyStateText,
        loadContent,
        initiallyExpanded = false
    }) {
        const container = document.createElement('div');
        container.className = 'track-lazy-section d-flex flex-column gap-3';

        const sectionId = `trackLazySection-${lazySectionIdCounter += 1}`;

        const toggleButton = document.createElement('button');
        toggleButton.type = 'button';
        toggleButton.className = 'btn btn-outline-primary align-self-start';
        toggleButton.textContent = buttonLabel;
        toggleButton.setAttribute('aria-controls', sectionId);
        toggleButton.setAttribute('aria-expanded', 'false');

        const contentWrapper = document.createElement('div');
        contentWrapper.id = sectionId;
        contentWrapper.className = 'track-lazy-section__content d-none';

        const skeleton = createSkeletonPlaceholder();
        const resultContainer = document.createElement('div');
        resultContainer.className = 'track-lazy-section__result d-none';

        const errorContainer = document.createElement('div');
        errorContainer.className = 'alert alert-danger d-none track-lazy-section__error';
        errorContainer.setAttribute('role', 'status');

        const errorText = document.createElement('p');
        errorText.className = 'mb-2';
        errorContainer.appendChild(errorText);

        const retryButton = document.createElement('button');
        retryButton.type = 'button';
        retryButton.className = 'btn btn-outline-danger btn-sm';
        retryButton.textContent = 'Повторить';
        errorContainer.appendChild(retryButton);

        contentWrapper.append(skeleton, errorContainer, resultContainer);
        container.append(toggleButton, contentWrapper);

        let isExpanded = false;
        let isLoaded = false;
        let isLoading = false;

        const handleSuccess = (element) => {
            const contentElement = element ?? (() => {
                if (!emptyStateText) {
                    return null;
                }
                const emptyParagraph = document.createElement('p');
                emptyParagraph.className = 'text-muted mb-0';
                emptyParagraph.textContent = emptyStateText;
                return emptyParagraph;
            })();

            resultContainer.replaceChildren();
            if (contentElement) {
                resultContainer.appendChild(contentElement);
            }
            skeleton.classList.add('d-none');
            errorContainer.classList.add('d-none');
            resultContainer.classList.remove('d-none');
            isLoaded = true;
        };

        const handleError = (error) => {
            const message = error?.message || 'Не удалось загрузить данные';
            skeleton.classList.add('d-none');
            resultContainer.classList.add('d-none');
            errorText.textContent = message;
            errorContainer.classList.remove('d-none');
            notifyUser(message, 'danger');
            isLoaded = false;
        };

        const requestContent = async () => {
            if (typeof loadContent !== 'function') {
                handleError(new Error('Загрузчик секции не настроен'));
                return;
            }
            isLoading = true;
            skeleton.classList.remove('d-none');
            resultContainer.classList.add('d-none');
            errorContainer.classList.add('d-none');
            try {
                const element = await loadContent();
                handleSuccess(element);
            } catch (error) {
                handleError(error);
            } finally {
                isLoading = false;
            }
        };

        const expandSection = () => {
            if (isExpanded) {
                return;
            }
            isExpanded = true;
            toggleButton.setAttribute('aria-expanded', 'true');
            toggleButton.textContent = expandedLabel || buttonLabel;
            contentWrapper.classList.remove('d-none');
            if (!isLoaded && !isLoading) {
                void requestContent();
            }
        };

        const collapseSection = () => {
            if (!isExpanded) {
                return;
            }
            isExpanded = false;
            toggleButton.setAttribute('aria-expanded', 'false');
            toggleButton.textContent = buttonLabel;
            contentWrapper.classList.add('d-none');
        };

        toggleButton.addEventListener('click', () => {
            if (isExpanded) {
                collapseSection();
            } else {
                expandSection();
            }
        });

        retryButton.addEventListener('click', () => {
            skeleton.classList.remove('d-none');
            errorContainer.classList.add('d-none');
            void requestContent();
        });

        if (initiallyExpanded) {
            expandSection();
        }

        return {
            container,
            expand: expandSection,
            collapse: collapseSection
        };
    }

    /**
     * Формирует таймлайн истории трека на основе событий.
     * Метод отвечает только за визуализацию событий, что облегчает тестирование и расширение.
     * @param {Array<Object>} history события истории
     * @param {Function} format функция форматирования даты
     * @returns {HTMLElement|null} таймлайн или {@code null}
     */
    function createHistoryTimeline(history, format) {
        if (!Array.isArray(history) || history.length === 0) {
            return null;
        }
        const timeline = document.createElement('div');
        timeline.className = 'timeline';
        history.forEach((event, index) => {
            if (!event || typeof event !== 'object') {
                return;
            }
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
            statusEl.textContent = event?.status || '—';
            item.appendChild(statusEl);

            if (event?.details) {
                const detailsEl = document.createElement('div');
                detailsEl.className = 'timeline-details text-muted small';
                detailsEl.textContent = event.details;
                item.appendChild(detailsEl);
            }

            timeline.appendChild(item);
        });
        return timeline;
    }

    /**
     * Создаёт упорядоченный список этапов жизненного цикла заказа.
     * Выделение логики визуализации в отдельную функцию следует принципу SRP.
     * @param {Array<Object>} stages этапы жизненного цикла
     * @param {Function} formatDate функция форматирования дат
     * @returns {HTMLElement|null} список этапов или {@code null}
     */
    function buildLifecycleList(stages, formatDate) {
        if (!Array.isArray(stages) || stages.length === 0) {
            return null;
        }

        const normalizedStages = stages.filter((stage) => stage && typeof stage === 'object');
        if (normalizedStages.length === 0) {
            return null;
        }

        const hasOnlyOutboundStage = normalizedStages.length === 1
            && normalizedStages[0].code === 'OUTBOUND';
        if (hasOnlyOutboundStage) {
            return null;
        }

        const list = document.createElement('ol');
        list.className = 'list-unstyled d-flex flex-column gap-3 mb-0';
        list.setAttribute('role', 'list');

        normalizedStages.forEach((stage, index) => {
            const item = document.createElement('li');
            item.className = 'd-flex flex-column flex-lg-row gap-2 gap-lg-3 align-items-lg-center';
            item.setAttribute('role', 'listitem');
            item.dataset.stageCode = stage.code || `stage-${index}`;

            const badge = document.createElement('span');
            badge.className = 'badge rounded-pill';
            const state = stage.state || 'PLANNED';
            switch (state) {
                case 'COMPLETED':
                    badge.classList.add('bg-success-subtle', 'text-success-emphasis');
                    badge.textContent = 'Завершено';
                    break;
                case 'IN_PROGRESS':
                    badge.classList.add('bg-primary-subtle', 'text-primary-emphasis');
                    badge.textContent = 'В процессе';
                    break;
                default:
                    badge.classList.add('bg-secondary-subtle', 'text-secondary-emphasis');
                    badge.textContent = 'Ожидает';
                    break;
            }
            item.appendChild(badge);

            const title = document.createElement('div');
            title.className = 'fw-semibold flex-grow-1';
            title.textContent = stage.title || 'Этап';
            item.appendChild(title);

            if (stage.description) {
                const description = document.createElement('div');
                description.className = 'text-muted small flex-grow-1';
                description.textContent = stage.description;
                item.appendChild(description);
            }

            if (stage.startedAt || stage.finishedAt) {
                const dates = document.createElement('div');
                dates.className = 'text-muted small text-lg-end';
                const started = stage.startedAt ? formatDate(stage.startedAt) : null;
                const finished = stage.finishedAt ? formatDate(stage.finishedAt) : null;
                const dateParts = [];
                if (started) {
                    dateParts.push(`Начало: ${started}`);
                }
                if (finished) {
                    dateParts.push(`Завершение: ${finished}`);
                }
                dates.textContent = dateParts.join(' · ');
                item.appendChild(dates);
            }

            list.appendChild(item);
        });

        return list;
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
     * Создаёт карточку модального окна с заголовком и телом.
     * Метод устраняет дублирование разметки и упрощает расширение модалки (OCP).
     * @param {string} title заголовок карточки
     * @param {Object} [options] дополнительные настройки разметки
     * @param {string|null} [options.headingId] идентификатор заголовка для aria-связей
     * @returns {{card: HTMLElement, body: HTMLElement, heading: HTMLElement|null}} карточка, контейнер содержимого и заголовок
     */
    function createCard(title, options = {}) {
        const card = document.createElement('section');
        card.className = 'card shadow-sm border-0 rounded-4 mb-3';
        const body = document.createElement('div');
        body.className = 'card-body';
        const { headingId = null } = options;
        let heading = null;
        if (title) {
            heading = document.createElement('h6');
            heading.className = 'text-uppercase text-muted small mb-3';
            heading.textContent = title;
            if (headingId) {
                heading.id = headingId;
            }
            body.appendChild(heading);
        }
        card.appendChild(body);
        if (headingId) {
            card.setAttribute('aria-labelledby', headingId);
        }
        return { card, body, heading };
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
            const isExchange = Boolean(item.exchange);
            const isReturn = Boolean(item.returnShipment);
            const postfix = isExchange ? ' · обмен' : (isReturn ? ' · возврат' : '');
            const visualText = `${numberText}${postfix}`;
            button.textContent = visualText;

            const ariaParts = [];
            ariaParts.push(item.number ? `Трек ${item.number}` : 'Трек без номера');
            if (isExchange) {
                ariaParts.push('обмен');
            }
            if (isReturn) {
                ariaParts.push('возвратная посылка');
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
    function renderTrackModal(data) {
        clearRefreshTimer();

        const modal = document.getElementById('infoModal');
        const container = document.getElementById('trackModalContent')
            || modal?.querySelector('.modal-body');
        if (!container) {
            return;
        }

        const timeZone = data?.timeZone;
        const format = (value) => formatDateTime(value, timeZone);
        container.replaceChildren();
        container.classList.remove('justify-content-center', 'align-items-center', 'text-muted', 'track-modal-placeholder');
        container.classList.add('track-modal-container');

        if (data?.id !== undefined) {
            container.dataset.trackId = String(data.id);
        } else {
            delete container.dataset.trackId;
        }

        const mainColumn = document.createElement('section');
        mainColumn.className = 'track-modal-main d-flex flex-column gap-3';
        mainColumn.setAttribute('role', 'region');
        mainColumn.setAttribute('aria-label', 'Сведения о треке');

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

        if (trackActions.childElementCount === 0 && trackActions.parentElement === trackTitleRow) {
            trackTitleRow.removeChild(trackActions);
        }

        const parcelHeading = parcelCard.card.querySelector('h6');
        if (parcelHeading) {
            const regionTitleId = 'trackMainRegionTitle';
            parcelHeading.id = regionTitleId;
            mainColumn.setAttribute('aria-labelledby', regionTitleId);
            mainColumn.removeAttribute('aria-label');
        }

        mainColumn.appendChild(parcelCard.card);

        const lifecycleCard = (() => {
            const lifecycle = createCard('Жизненный цикл заказа', { headingId: generateElementId('track-lifecycle-title') });
            const lifecycleSection = createLazySection({
                buttonLabel: 'Жизненный цикл заказа',
                expandedLabel: 'Скрыть этапы',
                emptyStateText: 'Этапы пока недоступны',
                loadContent: async () => {
                    if (trackId === undefined || trackId === null) {
                        const message = document.createElement('p');
                        message.className = 'text-muted mb-0';
                        message.textContent = 'Жизненный цикл доступен только для сохранённых треков.';
                        return message;
                    }
                    const stages = await loadLifecycleOnce(trackId);
                    return buildLifecycleList(stages, format);
                }
            });
            lifecycle.body.appendChild(lifecycleSection.container);
            return lifecycle;
        })();

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
        mainColumn.appendChild(statusCard.card);

        const historyCard = createCard('История трека');
        const historySection = createLazySection({
            buttonLabel: 'Показать историю',
            expandedLabel: 'Скрыть историю',
            emptyStateText: 'История пока пуста',
            loadContent: async () => {
                if (trackId === undefined || trackId === null) {
                    const message = document.createElement('p');
                    message.className = 'text-muted mb-0';
                    message.textContent = 'История доступна только для сохранённых треков.';
                    return message;
                }
                const history = await loadHistoryOnce(trackId);
                return createHistoryTimeline(history, format);
            },
            initiallyExpanded: shouldAutoExpandEpisodeHistory(data)
        });
        historyCard.body.appendChild(historySection.container);
        mainColumn.appendChild(historyCard.card);

        container.appendChild(mainColumn);

        const returnCard = createReturnRequestCard(data);
        const sideCards = [lifecycleCard, returnCard]
            .filter((cardInfo) => Boolean(cardInfo));

        if (sideCards.length > 0) {
            /**
             * Создаёт правую колонку модального окна и наполняет её карточками.
             * Метод формирует единый контейнер, чтобы сетка корректно выравнивала
             * вспомогательные блоки и их позиция не менялась при изменении контента.
             * @returns {HTMLElement} обёртка с карточками правой части
             */
            const createSideColumn = () => {
                const sideColumn = document.createElement('aside');
                sideColumn.className = 'track-modal-side d-flex flex-column gap-3';
                sideColumn.setAttribute('aria-label', 'Дополнительные сведения о треке');
                sideCards.forEach((cardInfo) => {
                    if (cardInfo?.card instanceof HTMLElement) {
                        sideColumn.appendChild(cardInfo.card);
                    }
                });
                return sideColumn;
            };

            container.appendChild(createSideColumn());
        }

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
                invalidateLazyDataCache(id);
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
                invalidateLazyDataCache(data?.id ?? trackId);
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
        invalidateLazySections: (trackId) => invalidateLazyDataCache(trackId)
    };
})();
