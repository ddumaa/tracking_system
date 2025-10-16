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
     * Создаёт кнопку действия с единым оформлением.
     * @param {Object} options параметры кнопки
     * @param {string} options.text отображаемый текст
     * @param {string} [options.variant] модификатор Bootstrap
     * @param {string} [options.ariaLabel] текст для screen reader
     * @param {Function} options.onClick обработчик клика
     * @returns {HTMLButtonElement} созданная кнопка
     */
    function createActionButton({ text, variant = 'outline-primary', ariaLabel, onClick, fullWidth = false }) {
        const button = document.createElement('button');
        button.type = 'button';
        const classList = ['btn', `btn-${variant}`, 'btn-sm'];
        if (fullWidth) {
            classList.push('w-100');
        }
        button.className = classList.join(' ');
        button.textContent = text;
        if (ariaLabel) {
            button.setAttribute('aria-label', ariaLabel);
        }
        button.dataset.defaultText = text || '';
        if (ariaLabel !== undefined) {
            button.dataset.defaultAriaLabel = ariaLabel || '';
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
     * Возвращает элемент подсказки, связанный с кнопкой действия.
     * Метод инкапсулирует поиск, чтобы разные обработчики не зависели от конкретной структуры DOM (SRP).
     * @param {HTMLButtonElement} button кнопка действия
     * @returns {HTMLElement|null} элемент подсказки или {@code null}
     */
    function findActionHintElement(button) {
        if (!button || !(button instanceof HTMLElement)) {
            return null;
        }
        const hintId = button.dataset.actionHintId;
        if (hintId) {
            return document.getElementById(hintId);
        }
        const sibling = button.nextElementSibling;
        if (sibling && sibling.dataset && sibling.dataset.actionHint === 'true') {
            return sibling;
        }
        return null;
    }

    /**
     * Сбрасывает пользовательскую подсказку кнопки к значениям по умолчанию.
     * Метод поддерживает принцип повторного использования: любые действия перед вызовом API восстанавливают стабильное состояние UI.
     * @param {HTMLButtonElement} button кнопка действия
     */
    function resetActionButtonFeedback(button) {
        if (!button || !(button instanceof HTMLElement)) {
            return;
        }
        const defaultText = button.dataset.defaultText;
        if (typeof defaultText === 'string') {
            button.textContent = defaultText;
        }
        if (button.dataset.defaultAriaLabel !== undefined) {
            const defaultAria = button.dataset.defaultAriaLabel;
            if (defaultAria && defaultAria.length > 0) {
                button.setAttribute('aria-label', defaultAria);
            } else {
                button.removeAttribute('aria-label');
            }
        }
        button.removeAttribute('aria-describedby');
        button.removeAttribute('title');
        button.removeAttribute('data-bs-original-title');
        button.removeAttribute('data-action-unavailable');

        const hint = findActionHintElement(button);
        if (hint) {
            hint.textContent = '';
            hint.classList.add('visually-hidden');
            hint.setAttribute('aria-hidden', 'true');
        }
    }

    /**
     * Отображает сообщение о недоступности действия под кнопкой.
     * Метод централизует формирование подсказок, чтобы при любой ошибке пользователь получал единообразный фидбек (LSP).
     * @param {HTMLButtonElement} button кнопка действия
     * @param {string} reason текст причины недоступности
     */
    function showActionButtonUnavailable(button, reason) {
        if (!button || !(button instanceof HTMLElement)) {
            return;
        }
        const normalizedReason = (typeof reason === 'string' && reason.trim().length > 0)
            ? reason.trim()
            : 'Действие недоступно';
        const hint = findActionHintElement(button);
        if (hint) {
            const message = normalizedReason === 'Действие недоступно'
                ? normalizedReason
                : `Действие недоступно. ${normalizedReason}`;
            hint.textContent = message;
            hint.classList.remove('visually-hidden');
            hint.setAttribute('aria-hidden', 'false');
            button.setAttribute('aria-describedby', hint.id);
        } else {
            button.setAttribute('title', normalizedReason);
        }
        button.setAttribute('data-action-unavailable', 'true');
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
     * Возвращает первое непустое строковое значение из набора кандидатов.
     * Метод помогает формировать подсказки с graceful fallback, не нарушая SRP.
     * @param {...string} values список потенциальных строк
     * @returns {string|null} первая непустая строка либо {@code null}
     */
    function firstNonEmpty(...values) {
        for (const value of values) {
            if (typeof value === 'string' && value.trim().length > 0) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * Отправляет запрос к REST-эндпоинтам управления заявками.
     * @param {string} url относительный URL запроса
     * @param {RequestInit} [options] дополнительные настройки fetch
     * @returns {Promise<Object>} десериализованный ответ контроллера
     */
    async function sendReturnRequest(url, options = {}) {
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
        const request = sendReturnRequest(`/api/v1/tracks/${trackId}/history`, { method: 'GET' })
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
        const request = sendReturnRequest(`/api/v1/tracks/${trackId}/lifecycle`, { method: 'GET' })
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
            resetActionButtonFeedback(button);
            button.disabled = true;
            button.setAttribute('aria-busy', 'true');
        }
        try {
            await action();
        } catch (error) {
            notifyUser(error?.message || 'Не удалось выполнить действие', 'danger');
            showActionButtonUnavailable(button, error?.message);
        } finally {
            if (button && document.body.contains(button)) {
                button.disabled = false;
                button.setAttribute('aria-busy', 'false');
            }
        }
    }

    /** Варианты причин возврата, синхронизированные с Telegram-ботом. */
    const RETURN_REASON_OPTIONS = Object.freeze([
        { value: 'Не подошло', label: 'Не подошло' },
        { value: 'Брак', label: 'Брак' },
        { value: 'Не понравилось', label: 'Не понравилось' },
        { value: 'Другое', label: 'Другое' }
    ]);

    /** Варианты типа обращения, отличающие возврат от обмена. */
    const RETURN_REQUEST_TYPE_OPTIONS = Object.freeze([
        { value: 'return', label: 'Возврат', isExchange: false },
        { value: 'exchange', label: 'Обмен', isExchange: true }
    ]);

    /**
     * Создаёт радио-кнопку выбора типа заявки и связанную подпись.
     * Метод отделяет генерацию DOM, чтобы основная функция занималась только сборкой формы.
     * @param {Object} option параметры варианта
     * @param {string} option.value машинное значение
     * @param {string} option.label отображаемый текст
     * @param {boolean} option.isExchange признак обмена
     * @param {string} groupName имя группы для объединения радио-кнопок
     * @param {boolean} checked выбран ли элемент по умолчанию
     * @returns {{input: HTMLInputElement, wrapper: HTMLElement}} созданные элементы
     */
    function createReturnTypeRadio(option, groupName, checked) {
        const radioId = generateElementId('return-type-option');
        const wrapper = document.createElement('div');
        wrapper.className = 'form-check form-check-inline';

        const input = document.createElement('input');
        input.type = 'radio';
        input.className = 'form-check-input';
        input.name = groupName;
        input.value = option.value;
        input.id = radioId;
        input.checked = checked;
        input.dataset.isExchange = option.isExchange ? 'true' : 'false';

        const label = document.createElement('label');
        label.className = 'form-check-label';
        label.setAttribute('for', radioId);
        label.textContent = option.label;

        wrapper.append(input, label);
        return { input, wrapper };
    }

    /**
     * Создаёт форму регистрации возврата и навешивает обработчики.
     * Метод инкапсулирует работу с DOM, соблюдая принцип единой ответственности.
     * @param {number} trackId идентификатор посылки
     * @param {Object} [options] дополнительные параметры построения формы
     * @param {Object|null} [options.returnRequest] текущая заявка, если уже существует
     * @returns {HTMLFormElement} форма отправки заявки
     */
    function createReturnRegistrationForm(trackId, options = {}) {
        const { returnRequest = null } = options;
        const form = document.createElement('form');
        form.className = 'd-flex flex-column gap-2';
        form.noValidate = true;

        const typeGroupName = generateElementId('return-type');
        const typeFieldset = document.createElement('fieldset');
        typeFieldset.className = 'mb-3';

        const typeLegend = document.createElement('legend');
        typeLegend.className = 'form-label mb-1';
        typeLegend.textContent = 'Тип обращения';
        typeFieldset.appendChild(typeLegend);

        const typeDescription = document.createElement('div');
        typeDescription.className = 'form-text mb-2';
        typeDescription.textContent = 'Выберите, хотите ли вы вернуть товар или получить обмен автоматически.';
        typeFieldset.appendChild(typeDescription);

        const typeControls = document.createElement('div');
        typeControls.className = 'd-flex flex-wrap gap-3';

        const typeInputs = RETURN_REQUEST_TYPE_OPTIONS.map((option, index) => {
            const { input, wrapper } = createReturnTypeRadio(option, typeGroupName, index === 0);
            typeControls.appendChild(wrapper);
            return input;
        });

        typeFieldset.appendChild(typeControls);

        const reasonSelect = document.createElement('select');
        reasonSelect.className = 'form-select';
        reasonSelect.name = 'reason';
        reasonSelect.required = true;
        reasonSelect.id = generateElementId('return-reason');

        const placeholderOption = document.createElement('option');
        placeholderOption.value = '';
        placeholderOption.disabled = true;
        placeholderOption.selected = true;
        placeholderOption.textContent = 'Выберите причину обращения';
        reasonSelect.appendChild(placeholderOption);

        RETURN_REASON_OPTIONS.forEach((option) => {
            const reasonOption = document.createElement('option');
            reasonOption.value = option.value;
            reasonOption.textContent = option.label;
            reasonSelect.appendChild(reasonOption);
        });

        const reasonControl = createLabeledControl(
            'Причина обращения',
            reasonSelect,
            'Список синхронизирован с Telegram-ботом, чтобы аналитика совпадала.'
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

        let reverseInput = null;
        let reverseControl = null;
        if (!returnRequest?.reverseTrackNumber) {
            reverseInput = document.createElement('input');
            reverseInput.type = 'text';
            reverseInput.className = 'form-control';
            reverseInput.name = 'reverseTrackNumber';
            reverseInput.maxLength = 64;
            reverseInput.id = generateElementId('return-reverse-track');
            reverseInput.placeholder = 'Например, LP123456789BY';
            reverseInput.autocomplete = 'off';

            reverseControl = createLabeledControl(
                'Трек обратной отправки',
                reverseInput,
                'Укажите номер, если посылка уже отправлена обратно.'
            );
        }

        const submitButton = document.createElement('button');
        submitButton.type = 'submit';
        submitButton.className = 'btn btn-warning align-self-start';
        submitButton.textContent = 'Отправить заявку';

        form.append(typeFieldset, reasonControl, commentControl);
        if (reverseControl) {
            form.appendChild(reverseControl);
        }
        form.appendChild(submitButton);

        form.addEventListener('submit', (event) => {
            event.preventDefault();
            if (!form.reportValidity()) {
                return;
            }
            const formValues = {
                reason: reasonSelect.value,
                comment: commentInput.value,
                reverseTrackNumber: reverseInput ? reverseInput.value : '',
                isExchange: typeInputs.some((input) => input.checked && input.dataset.isExchange === 'true')
            };
            runButtonAction(submitButton, () => handleRegisterReturnAction(trackId, formValues));
        });

        return form;
    }

    /**
     * Формирует форму обновления обратного трека активной заявки.
     * Метод отделяет построение DOM, чтобы основная отрисовка модалки не усложнялась проверками.
     * @param {number} trackId идентификатор посылки
     * @param {Object} returnRequest DTO заявки на возврат
     * @returns {HTMLFormElement} форма редактирования обратного трека
     */
    function createReverseTrackForm(trackId, returnRequest) {
        const form = document.createElement('form');
        form.className = 'd-flex flex-column gap-3 mt-3';
        form.dataset.reverseTrackForm = 'true';

        const reverseInput = document.createElement('input');
        reverseInput.type = 'text';
        reverseInput.className = 'form-control';
        reverseInput.name = 'reverseTrackNumber';
        reverseInput.maxLength = 64;
        reverseInput.id = generateElementId('reverse-track-edit');
        reverseInput.placeholder = 'Например, LP123456789BY';
        reverseInput.autocomplete = 'off';
        if (returnRequest?.reverseTrackNumber) {
            reverseInput.value = returnRequest.reverseTrackNumber;
        }

        const commentInput = document.createElement('textarea');
        commentInput.className = 'form-control';
        commentInput.name = 'comment';
        commentInput.rows = 3;
        commentInput.maxLength = 2000;
        commentInput.id = generateElementId('reverse-track-comment');
        commentInput.placeholder = 'Комментарий к возврату (необязательно)';
        if (returnRequest?.comment) {
            commentInput.value = returnRequest.comment;
        }

        const helperText = returnRequest?.requiresAction
            ? 'Укажите актуальный обратный трек: заявка ожидает действий.'
            : 'Поле доступно, пока обратный трек не указан.';
        const reverseControl = createLabeledControl(
            'Трек обратной отправки',
            reverseInput,
            helperText
        );

        const commentControl = createLabeledControl(
            'Комментарий к заявке',
            commentInput,
            'Обновите при необходимости, максимум 2000 символов.'
        );

        const submitButton = document.createElement('button');
        submitButton.type = 'submit';
        submitButton.className = 'btn btn-primary align-self-start';
        submitButton.textContent = 'Сохранить трек';

        form.append(reverseControl, commentControl, submitButton);

        form.addEventListener('submit', (event) => {
            event.preventDefault();
            runButtonAction(submitButton, () => handleReverseTrackUpdate(
                trackId,
                returnRequest?.id,
                reverseInput.value,
                commentInput.value
            ));
        });

        return form;
    }

    /**
     * Создаёт контроллер отображения формы обратного трека.
     * Метод централизует синхронизацию aria-атрибутов и класса `d-none`,
     * чтобы любые ререндеры не рассогласовывали состояние (принцип SRP).
     * @param {HTMLFormElement} form управляемая форма
     * @returns {{element: HTMLFormElement, show: Function, hide: Function, toggle: Function, isVisible: Function}}
     */
    function createReverseFormController(form) {
        if (!(form instanceof HTMLElement)) {
            return null;
        }

        const HIDDEN_CLASS = 'd-none';

        const focusReverseInput = () => {
            const reverseInput = form.querySelector('input[name="reverseTrackNumber"]');
            if (reverseInput instanceof HTMLElement) {
                reverseInput.focus();
            }
        };

        const setVisibility = (visible) => {
            const isVisible = visible === true;
            form.classList.toggle(HIDDEN_CLASS, !isVisible);
            form.setAttribute('aria-hidden', isVisible ? 'false' : 'true');
            form.dataset.reverseFormVisible = isVisible ? 'true' : 'false';
        };

        const controller = {
            element: form,
            show() {
                setVisibility(true);
                focusReverseInput();
            },
            hide() {
                setVisibility(false);
            },
            toggle() {
                if (this.isVisible()) {
                    this.hide();
                } else {
                    this.show();
                }
            },
            isVisible() {
                return form.dataset.reverseFormVisible === 'true';
            }
        };

        controller.hide();
        return controller;
    }

    /**
     * Перечисление нормализованных состояний обращения, приходящих с сервера.
     * Значения отражают жизненный цикл заявки и позволяют синхронизировать интерфейс
     * без дополнительных эвристик (принцип SRP).
     */
    const ReturnRequestState = Object.freeze({
        RETURN: 'RETURN',
        EXCHANGE: 'EXCHANGE'
    });

    /**
     * Проверяет, относится ли значение к допустимым состояниям обращения.
     * Метод концентрирует валидацию, чтобы любые изменения словаря состояний
     * затрагивали одну точку и не нарушали принцип OCP.
     * @param {string} state проверяемое значение
     * @returns {boolean} {@code true}, если состояние поддерживается
     */
    function isKnownReturnRequestState(state) {
        return Object.values(ReturnRequestState).includes(state);
    }

    /**
     * Создаёт стор для хранения текущего состояния обращения в модальном окне.
     * Хранилище реализует паттерн наблюдателя и тем самым отделяет источник данных
     * от подписчиков, соблюдая принципы SRP и ISP.
     * @returns {{reset: (function(string): string), setValue: (function(string): string), getValue: (function(): string), subscribe: (function(Function): Function)}} интерфейс управления состоянием
     */
    function createReturnRequestStateStore() {
        let value = ReturnRequestState.RETURN;
        const subscribers = new Set();

        return {
            /**
             * Возвращает сохранённое состояние без раскрытия механизма хранения.
             * @returns {string} актуальное состояние обращения
             */
            getValue() {
                return value;
            },

            /**
             * Сбрасывает стор для нового обращения и уведомляет подписчиков о значении по умолчанию.
             * @param {string} nextValue стартовое состояние, пришедшее с сервера
             * @returns {string} нормализованное значение
             */
            reset(nextValue) {
                subscribers.clear();
                value = isKnownReturnRequestState(nextValue)
                    ? nextValue
                    : ReturnRequestState.RETURN;
                return value;
            },

            /**
             * Применяет новое состояние и уведомляет подписчиков, если значение изменилось.
             * Метод гарантирует, что обработчики вызываются только при реальном обновлении (LSP).
             * @param {string} nextValue целевое состояние
             * @returns {string} применённое значение
             */
            setValue(nextValue) {
                const normalized = isKnownReturnRequestState(nextValue)
                    ? nextValue
                    : ReturnRequestState.RETURN;
                if (normalized === value) {
                    return value;
                }
                value = normalized;
                subscribers.forEach((callback) => {
                    try {
                        callback(value);
                    } catch (error) {
                        console.error('Не удалось обновить состояние обращения', error);
                    }
                });
                return value;
            },

            /**
             * Подписывает обработчик на изменение состояния и возвращает функцию отписки.
             * Такой контракт поддерживает принцип DIP: клиенты работают только с абстракцией стора.
             * @param {Function} callback обработчик изменения
             * @returns {Function} функция отписки
             */
            subscribe(callback) {
                if (typeof callback !== 'function') {
                    return () => {};
                }
                subscribers.add(callback);
                return () => {
                    subscribers.delete(callback);
                };
            }
        };
    }

    /**
     * Экземпляр стора состояния обращения, переиспользуемый между перерендерами.
     * Выделение единой точки хранения защищает модуль от утечек DOM-ссылок и облегчает повторное использование (SRP).
     */
    const returnRequestStateStore = createReturnRequestStateStore();

    /**
     * Карта описаний для каждого известного состояния обращения.
     * Данные используются при рендере бейджей и подсказок, исключая необходимость
     * анализировать локализованные строки из ответа API.
     */
    const RETURN_REQUEST_STATE_DESCRIPTORS = Object.freeze({
        [ReturnRequestState.RETURN]: Object.freeze({
            statusLabel: 'Возврат в обработке',
            hint: 'Заявка оформлена как возврат. Примите обратную посылку или переведите обращение в обмен.'
        }),
        [ReturnRequestState.EXCHANGE]: Object.freeze({
            statusLabel: 'Обмен в обработке',
            hint: 'Заявка оформлена как обмен. Создайте обменное отправление или переведите обращение в возврат.'
        })
    });

    /**
     * Вычисляет человеко-читаемое название типа обращения.
     * Метод учитывает данные сервера и при их отсутствии опирается на состояние,
     * чтобы не дублировать бизнес-правила в разных местах кода (SRP).
     * @param {string} state нормализованное состояние
     * @param {Object|null} returnRequest DTO обращения
     * @returns {string} подпись "Возврат" либо "Обмен"
     */
    function getReturnRequestTypeLabel(state, returnRequest) {
        const explicitLabel = firstNonEmpty(returnRequest?.typeLabel);
        if (explicitLabel) {
            return explicitLabel;
        }
        const normalizedState = isKnownReturnRequestState(state)
            ? state
            : ReturnRequestState.RETURN;
        return normalizedState === ReturnRequestState.EXCHANGE ? 'Обмен' : 'Возврат';
    }

    /**
     * Возвращает статусную подпись для бейджа исходя из нормализованного состояния.
     * Метод вначале использует текст из DTO, а затем — справочник состояния, чтобы сохранить единый источник истины (SRP).
     * @param {string} state код состояния
     * @param {Object|null} returnRequest DTO обращения
     * @returns {string} текст для бейджа статуса
     */
    function getReturnRequestStatusLabel(state, returnRequest) {
        const explicitLabel = firstNonEmpty(returnRequest?.statusLabel, returnRequest?.status);
        if (explicitLabel) {
            return explicitLabel;
        }
        if (!isKnownReturnRequestState(state)) {
            return 'Статус не определён';
        }
        const descriptor = RETURN_REQUEST_STATE_DESCRIPTORS[state];
        return descriptor?.statusLabel || 'Статус не определён';
    }

    /**
     * Возвращает текст подсказки для блока действий.
     * Метод сначала использует текст из ответа API, затем — преднастроенный fallback.
     * @param {Object|null} returnRequest DTO обращения
     * @param {string} state нормализованное состояние
     * @returns {string|null} пользовательская подсказка
     */
    function getReturnRequestHint(returnRequest, state) {
        const apiHint = firstNonEmpty(returnRequest?.hint);
        if (apiHint) {
            return apiHint;
        }
        if (!isKnownReturnRequestState(state)) {
            return null;
        }
        const descriptor = RETURN_REQUEST_STATE_DESCRIPTORS[state];
        return descriptor?.hint || null;
    }

    /**
     * Применяет визуальное состояние обращения к переданным элементам интерфейса.
     * Метод централизует обновление бейджей и описательных списков, сохраняя единый стиль отображения.
     * @param {string} state нормализованное состояние обращения
     * @param {Object} bindings элементы, требующие синхронизации
     * @param {HTMLElement|null} bindings.typeBadge бейдж с типом обращения
     * @param {HTMLElement|null} bindings.statusBadge бейдж статуса обращения
     * @param {HTMLElement|null} bindings.typeValue элемент описательного списка с типом
     * @param {Object} context дополнительный контекст
     * @param {Object|null} context.returnRequest DTO обращения
     */
    function applyReturnRequestState(state, bindings, context = {}) {
        const normalizedState = isKnownReturnRequestState(state)
            ? state
            : ReturnRequestState.RETURN;
        const { returnRequest = null } = context;
        const typeLabel = getReturnRequestTypeLabel(normalizedState, returnRequest);
        const isExchange = normalizedState === ReturnRequestState.EXCHANGE;

        if (bindings?.typeBadge) {
            bindings.typeBadge.className = isExchange
                ? 'badge rounded-pill bg-warning-subtle text-warning-emphasis'
                : 'badge rounded-pill bg-info-subtle text-info-emphasis';
            bindings.typeBadge.textContent = typeLabel;
            bindings.typeBadge.setAttribute('aria-label', `Тип обращения: ${typeLabel}`);
        }

        if (bindings?.statusBadge) {
            const statusLabel = getReturnRequestStatusLabel(normalizedState, returnRequest);
            bindings.statusBadge.textContent = statusLabel;
            bindings.statusBadge.setAttribute('aria-label', `Текущий статус обращения: ${statusLabel}`);
        }

        if (bindings?.typeValue) {
            bindings.typeValue.textContent = typeLabel;
        }
    }

    /**
     * Маппинг устаревших кодов статусов на нормализованные состояния.
     * Словарь обеспечивает обратную совместимость, не заставляя клиентов анализировать произвольные строки.
     */
    const LEGACY_STATUS_STATE = Object.freeze({
        REGISTERED: ReturnRequestState.RETURN,
        REGISTERED_RETURN: ReturnRequestState.RETURN,
        REGISTERED_EXCHANGE: ReturnRequestState.EXCHANGE,
        EXCHANGE_APPROVED: ReturnRequestState.EXCHANGE,
        EXCHANGE_LAUNCHED: ReturnRequestState.EXCHANGE,
        EXCHANGE_STARTED: ReturnRequestState.EXCHANGE,
        COMPLETED: ReturnRequestState.RETURN,
        CLOSED: ReturnRequestState.RETURN,
        CLOSED_NO_EXCHANGE: ReturnRequestState.RETURN
    });

    /**
     * Нормализует строковое представление состояния обращения.
     * Метод концентрирует правила преобразования, чтобы весь модуль использовал единый словарь (SRP).
     * @param {string|undefined|null} rawValue исходное значение из DTO
     * @returns {string|null} {@link ReturnRequestState} либо {@code null}, если сопоставление не найдено
     */
    function normalizeReturnRequestStateInput(rawValue) {
        if (typeof rawValue !== 'string') {
            return null;
        }
        const normalized = rawValue.trim().toUpperCase();
        if (!normalized) {
            return null;
        }
        if (isKnownReturnRequestState(normalized)) {
            return normalized;
        }
        if (LEGACY_STATUS_STATE[normalized]) {
            return LEGACY_STATUS_STATE[normalized];
        }
        return null;
    }

    /**
     * Определяет нормализованное состояние обращения по данным сервера.
     * Метод учитывает явный признак обмена, текстовые статусы и устаревшие коды, оставляя только два сценария (SRP).
     * @param {Object|null} returnRequest DTO обращения
     * @returns {string} значение из {@link ReturnRequestState}
     */
    function deriveReturnRequestState(returnRequest) {
        if (!returnRequest || typeof returnRequest !== 'object') {
            return ReturnRequestState.RETURN;
        }

        const explicitState = normalizeReturnRequestStateInput(firstNonEmpty(returnRequest.state));
        if (explicitState) {
            return explicitState;
        }

        if (typeof returnRequest.isExchange === 'boolean') {
            return returnRequest.isExchange ? ReturnRequestState.EXCHANGE : ReturnRequestState.RETURN;
        }

        const statusState = normalizeReturnRequestStateInput(firstNonEmpty(returnRequest.status));
        if (statusState) {
            return statusState;
        }

        const exchangeHints = [
            returnRequest.exchange === true,
            returnRequest.exchangeRequested === true,
            returnRequest.requiresExchange === true,
            Boolean(returnRequest.exchangeParcel)
        ];
        if (exchangeHints.some(Boolean)) {
            return ReturnRequestState.EXCHANGE;
        }

        return ReturnRequestState.RETURN;
    }

    /**
     * Перечисление идентификаторов действий, доступных в карточке обращения.
     * Константа объединяет возможные кнопки, помогая избежать магических строк в логике рендера.
     */
    const ReturnRequestAction = Object.freeze({
        ACCEPT: 'ACCEPT',
        LAUNCH_EXCHANGE: 'LAUNCH_EXCHANGE',
        ACCEPT_REVERSE: 'ACCEPT_REVERSE',
        CLOSE: 'CLOSE',
        TO_RETURN: 'TO_RETURN',
        TO_EXCHANGE: 'TO_EXCHANGE',
        UPDATE_REVERSE_TRACK: 'UPDATE_REVERSE_TRACK'
    });

    /**
     * Предопределённые наборы кнопок для каждого сценария обращения.
     * Благодаря фиксированным конфигурациям модуль всегда собирает одну из двух групп действий (OCP).
     */
    const RETURN_REQUEST_STATE_ACTIONS = Object.freeze({
        [ReturnRequestState.RETURN]: Object.freeze({
            primary: [ReturnRequestAction.ACCEPT],
            secondary: [
                ReturnRequestAction.TO_EXCHANGE,
                ReturnRequestAction.CLOSE
            ]
        }),
        [ReturnRequestState.EXCHANGE]: Object.freeze({
            primary: [ReturnRequestAction.LAUNCH_EXCHANGE, ReturnRequestAction.ACCEPT_REVERSE],
            secondary: [
                ReturnRequestAction.TO_RETURN,
                ReturnRequestAction.UPDATE_REVERSE_TRACK,
                ReturnRequestAction.CLOSE
            ]
        })
    });

    /**
     * Извлекает нормализованное состояние обращения из ответа сервера.
     * Метод защищает интерфейс от отсутствующих полей в устаревших версиях API.
     * @param {Object|string|null} result ответ обработчика
     * @returns {string|null} значение из {@link ReturnRequestState} либо {@code null}
     */
    function extractNextState(result) {
        if (typeof result === 'string') {
            return normalizeReturnRequestStateInput(result);
        }
        if (!result || typeof result !== 'object') {
            return null;
        }
        const candidate = normalizeReturnRequestStateInput(firstNonEmpty(
            result.state,
            result.details?.state,
            result.details?.returnRequest?.state,
            result.returnRequest?.state
        ));
        if (candidate) {
            return candidate;
        }
        const nestedRequest = result.details?.returnRequest || result.returnRequest;
        if (nestedRequest && typeof nestedRequest === 'object') {
            return deriveReturnRequestState(nestedRequest);
        }
        return null;
    }

    /**
     * Гарантирует наличие идентификаторов посылки и обращения перед выполнением действий.
     * Метод устраняет дублирование проверок в обработчиках, соблюдая DRY и принцип единой ответственности.
     * @param {number|string} trackId идентификатор посылки
     * @param {number|string} requestId идентификатор обращения
     */
    function assertReturnActionContext(trackId, requestId) {
        if (trackId === undefined || trackId === null || trackId === '') {
            throw new Error('Действие недоступно: не указана посылка');
        }
        if (requestId === undefined || requestId === null || requestId === '') {
            throw new Error('Действие недоступно: не указано обращение');
        }
    }

    /**
     * Нормализует карту разрешений на действия с обращением.
     * Метод ожидает структуру, предоставленную сервером, но также поддерживает устаревшие флаги.
     * @param {Object|null} returnRequest DTO обращения
     * @returns {Object} объект с булевыми признаками доступности действий
     */
    function resolveReturnRequestPermissions(returnRequest) {
        const fallback = {
            allowAccept: false,
            allowLaunchExchange: false,
            allowAcceptReverse: false,
            allowClose: false,
            allowConvertToReturn: false,
            allowConvertToExchange: false,
            allowUpdateReverseTrack: false
        };
        if (!returnRequest || typeof returnRequest !== 'object') {
            return fallback;
        }
        const rawPermissions = returnRequest.actionPermissions;
        if (rawPermissions && typeof rawPermissions === 'object' && !Array.isArray(rawPermissions)) {
            return {
                allowAccept: Boolean(rawPermissions.allowAccept),
                allowLaunchExchange: Boolean(rawPermissions.allowLaunchExchange),
                allowAcceptReverse: Boolean(rawPermissions.allowAcceptReverse),
                allowClose: Boolean(rawPermissions.allowClose),
                allowConvertToReturn: Boolean(rawPermissions.allowConvertToReturn),
                allowConvertToExchange: Boolean(rawPermissions.allowConvertToExchange),
                allowUpdateReverseTrack: Boolean(rawPermissions.allowUpdateReverseTrack)
            };
        }
        return {
            allowAccept: Boolean(returnRequest.canConfirmReceipt),
            allowLaunchExchange: Boolean(returnRequest.canStartExchange),
            allowAcceptReverse: Boolean(returnRequest.canAcceptReverse),
            allowClose: Boolean(returnRequest.canCloseWithoutExchange),
            allowConvertToReturn: Boolean(returnRequest.canReopenAsReturn),
            allowConvertToExchange: Boolean(returnRequest.canStartExchange),
            allowUpdateReverseTrack: Boolean(returnRequest.requiresAction && !returnRequest.closedAt)
        };
    }

    /**
     * Создаёт кнопку действия обращения согласно идентификатору.
     * Метод инкапсулирует обработчики кликов, чтобы в {@link renderReturnActionsSection}
     * осталась только декларативная сборка интерфейса.
     * @param {string} actionId идентификатор из {@link ReturnRequestAction}
     * @param {Object} context контекст исполнения
     * @param {number} context.trackId идентификатор трека
     * @param {Object} context.returnRequest DTO обращения
     * @param {Object|null} context.reverseFormController контроллер формы обратного трека
     * @returns {HTMLButtonElement|null} созданная кнопка либо {@code null}
     */
    function buildReturnRequestAction(actionId, context) {
        const { trackId, returnRequest, reverseFormController } = context;
        const requestId = returnRequest?.id;
        const canOperate = trackId !== undefined && requestId !== undefined;

        switch (actionId) {
        case ReturnRequestAction.ACCEPT:
            if (!canOperate) {
                return null;
            }
            return createActionButton({
                text: 'Принять обратную посылку',
                variant: 'success',
                ariaLabel: 'Подтвердить получение обратной посылки и зафиксировать закрытие обращения',
                onClick: (button) => runButtonAction(button, async () => {
                    const result = await handleConfirmProcessingAction(trackId, requestId, {
                        successMessage: 'Обратная посылка принята',
                        notificationType: 'success'
                    });
                    const nextState = extractNextState(result);
                    if (nextState) {
                        returnRequestStateStore.setValue(nextState);
                    }
                    return result;
                }),
                fullWidth: true
            });
        case ReturnRequestAction.LAUNCH_EXCHANGE:
            if (!canOperate) {
                return null;
            }
            return createActionButton({
                text: 'Создать обменное отправление',
                variant: 'primary',
                ariaLabel: 'Создать обменную посылку и указать трек номер',
                onClick: (button) => runButtonAction(button, async () => {
                    const result = await handleApproveExchangeAction(trackId, requestId, {
                        successMessage: 'Обменное отправление создано',
                        notificationType: 'info'
                    });
                    const nextState = extractNextState(result);
                    if (nextState) {
                        returnRequestStateStore.setValue(nextState);
                    }
                    return result;
                }),
                fullWidth: true
            });
        case ReturnRequestAction.ACCEPT_REVERSE:
            if (!canOperate) {
                return null;
            }
            return createActionButton({
                text: 'Принять обратную посылку',
                variant: 'success',
                ariaLabel: 'Подтвердить получение обратной посылки',
                onClick: (button) => runButtonAction(button, async () => {
                    const result = await handleAcceptReverseShipmentAction(trackId, requestId, {
                        successMessage: 'Обратная посылка принята',
                        notificationType: 'success'
                    });
                    const nextState = extractNextState(result);
                    if (nextState) {
                        returnRequestStateStore.setValue(nextState);
                    }
                    return result;
                }),
                fullWidth: true
            });
        case ReturnRequestAction.CLOSE:
            if (!canOperate) {
                return null;
            }
            return createActionButton({
                text: 'Закрыть обращение',
                variant: 'outline-secondary',
                ariaLabel: 'Закрыть обращение без продолжения процесса',
                onClick: (button) => runButtonAction(button, async () => {
                    const result = await handleCloseReturnAction(trackId, requestId, {
                        successMessage: 'Обращение закрыто без результата',
                        notificationType: 'warning'
                    });
                    const nextState = extractNextState(result);
                    if (nextState) {
                        returnRequestStateStore.setValue(nextState);
                    }
                    return result;
                }),
                fullWidth: true
            });
        case ReturnRequestAction.TO_RETURN:
            if (!canOperate) {
                return null;
            }
            if (returnRequest?.exchangeParcel && returnRequest.exchangeParcel.id !== undefined) {
                return null;
            }
            return createActionButton({
                text: 'Перевести в возврат',
                variant: 'outline-warning',
                ariaLabel: 'Перевести обращение обратно в возврат',
                onClick: (button) => runButtonAction(button, async () => {
                    const result = await handleReopenReturnAction(trackId, requestId, {
                        successMessage: 'Заявка переведена в возврат',
                        notificationType: 'info'
                    });
                    const nextState = extractNextState(result);
                    if (nextState) {
                        returnRequestStateStore.setValue(nextState);
                    }
                    return result;
                }),
                fullWidth: true
            });
        case ReturnRequestAction.TO_EXCHANGE:
            if (!canOperate) {
                return null;
            }
            return createActionButton({
                text: 'Перевести в обмен',
                variant: 'primary',
                ariaLabel: 'Перевести обращение в обмен перед запуском процесса',
                onClick: (button) => runButtonAction(button, async () => {
                    const result = await handleApproveExchangeRequestAction(trackId, requestId, {
                        successMessage: 'Заявка переведена в обмен',
                        notificationType: 'info'
                    });
                    const nextState = extractNextState(result);
                    if (nextState) {
                        returnRequestStateStore.setValue(nextState);
                    }
                    return result;
                }),
                fullWidth: true
            });
        case ReturnRequestAction.UPDATE_REVERSE_TRACK:
            if (!reverseFormController || !(reverseFormController.element instanceof HTMLElement)) {
                return null;
            }
            const button = createActionButton({
                text: 'Добавить трек обратной посылки',
                variant: 'outline-primary',
                ariaLabel: 'Добавить или обновить трек обратной отправки',
                onClick: (button) => {
                    reverseFormController.toggle();
                    const expanded = reverseFormController.isVisible();
                    button.setAttribute('aria-expanded', expanded ? 'true' : 'false');
                },
                fullWidth: true
            });
            button.setAttribute('aria-expanded', reverseFormController.isVisible() ? 'true' : 'false');
            return button;
        default:
            return null;
        }
    }

    /**
     * Перерисовывает блок действий обращения в соответствии с текущим состоянием.
     * Метод аккумулирует создание кнопок и подсказок, чтобы обеспечить единый сценарий повторного рендера при смене состояния.
     * @param {HTMLElement} container корневой контейнер действий
     * @param {Object} params параметры построения
     * @param {string} params.state нормализованное состояние
     * @param {Object} params.returnRequest DTO заявки
     * @param {number} params.trackId идентификатор трека
     * @param {Object|null} params.reverseFormController контроллер формы обратного трека
     * @param {boolean} [params.reverseActionAllowed] признак доступности обновления обратного трека
     */
    function renderReturnActionsSection(container, params) {
        if (!container) {
            return;
        }
        const {
            state,
            returnRequest,
            trackId,
            reverseFormController,
            reverseActionAllowed = false
        } = params || {};

        const normalizedState = isKnownReturnRequestState(state)
            ? state
            : ReturnRequestState.RETURN;

        container.replaceChildren();

        const primaryStack = document.createElement('div');
        primaryStack.className = 'd-flex flex-column gap-2';
        primaryStack.dataset.returnPrimaryActions = 'true';

        const secondaryStack = document.createElement('div');
        secondaryStack.className = 'd-flex flex-column gap-2';
        secondaryStack.dataset.returnSecondaryActions = 'true';

        const reverseAllowed = Boolean(reverseFormController)
            && reverseActionAllowed
            && trackId !== undefined
            && returnRequest?.id !== undefined;

        if (reverseFormController && !reverseAllowed && typeof reverseFormController.hide === 'function') {
            reverseFormController.hide();
        }

        const permissions = resolveReturnRequestPermissions(returnRequest);
        const permissionByAction = {
            [ReturnRequestAction.ACCEPT]: 'allowAccept',
            [ReturnRequestAction.LAUNCH_EXCHANGE]: 'allowLaunchExchange',
            [ReturnRequestAction.ACCEPT_REVERSE]: permissions.allowAcceptReverse
                ? 'allowAcceptReverse'
                : 'allowAccept',
            [ReturnRequestAction.CLOSE]: 'allowClose',
            [ReturnRequestAction.TO_RETURN]: 'allowConvertToReturn',
            [ReturnRequestAction.TO_EXCHANGE]: 'allowConvertToExchange',
            [ReturnRequestAction.UPDATE_REVERSE_TRACK]: 'allowUpdateReverseTrack'
        };
        const isActionAllowed = (actionId) => {
            const permissionKey = permissionByAction[actionId];
            if (!permissionKey) {
                return true;
            }
            if (permissionKey === 'allowAcceptReverse' && !permissions.allowAcceptReverse) {
                return false;
            }
            return Boolean(permissions[permissionKey]);
        };

        const stateActions = RETURN_REQUEST_STATE_ACTIONS[normalizedState]
            || { primary: [], secondary: [] };
        const primaryActions = stateActions.primary.filter(isActionAllowed);
        const secondaryActions = stateActions.secondary.filter(isActionAllowed);
        const context = {
            trackId,
            returnRequest,
            reverseFormController: reverseAllowed ? reverseFormController : null
        };

        const appendAction = (stack, actionId) => {
            const button = buildReturnRequestAction(actionId, context);
            if (!button) {
                return;
            }
            const wrapper = document.createElement('div');
            wrapper.className = 'd-flex flex-column gap-1';
            wrapper.dataset.returnActionWrapper = String(actionId);

            const hint = document.createElement('span');
            hint.className = 'text-muted small visually-hidden';
            hint.dataset.actionHint = 'true';
            hint.setAttribute('aria-hidden', 'true');
            hint.setAttribute('role', 'status');
            hint.setAttribute('aria-live', 'polite');
            const hintId = generateElementId('return-action-hint');
            hint.id = hintId;
            button.dataset.actionHintId = hintId;

            wrapper.append(button, hint);
            stack.appendChild(wrapper);
        };

        primaryActions.forEach((actionId) => appendAction(primaryStack, actionId));
        secondaryActions.forEach((actionId) => appendAction(secondaryStack, actionId));

        if (primaryStack.childElementCount > 0) {
            container.appendChild(primaryStack);
        }
        if (secondaryStack.childElementCount > 0) {
            container.appendChild(secondaryStack);
        }
        const hintText = getReturnRequestHint(returnRequest, normalizedState);
        const detailsUrl = firstNonEmpty(returnRequest?.detailsUrl, returnRequest?.hintUrl, returnRequest?.helpUrl);
        if (hintText) {
            const hintParagraph = document.createElement('p');
            hintParagraph.className = 'text-muted small mb-0';
            hintParagraph.textContent = hintText;
            if (detailsUrl) {
                const moreLink = document.createElement('a');
                moreLink.textContent = 'Подробнее';
                moreLink.className = 'ms-1';
                moreLink.href = detailsUrl;
                moreLink.target = '_blank';
                moreLink.rel = 'noreferrer noopener';
                hintParagraph.append(' ');
                hintParagraph.appendChild(moreLink);
            }
            container.appendChild(hintParagraph);
        }

        const isEmpty = container.childElementCount === 0;
        container.classList.toggle('d-none', isEmpty);
        container.setAttribute('aria-hidden', isEmpty ? 'true' : 'false');
    }

    /**
     * Подготавливает тело запроса регистрации возврата.
     * @param {Object} formValues значения полей формы
     * @returns {Object} нормализованные данные для API
     */
    function buildReturnRegistrationPayload(formValues = {}) {
        const reason = (formValues.reason ?? '').trim();
        if (reason.length === 0) {
            throw new Error('Выберите причину обращения');
        }
        if (reason.length > 255) {
            throw new Error('Причина обращения не должна превышать 255 символов');
        }

        const commentRaw = (formValues.comment ?? '').trim();
        if (commentRaw.length > 2000) {
            throw new Error('Комментарий не должен превышать 2000 символов');
        }

        const reverseRaw = (formValues.reverseTrackNumber ?? '').trim();
        if (reverseRaw.length > 64) {
            throw new Error('Трек обратной отправки не должен превышать 64 символа');
        }

        const isExchange = formValues.isExchange === true || formValues.isExchange === 'true';
        const payload = {
            idempotencyKey: generateIdempotencyKey(),
            reason,
            requestedAt: new Date().toISOString(),
            comment: commentRaw.length > 0 ? commentRaw : null,
            isExchange
        };

        if (reverseRaw.length > 0) {
            payload.reverseTrackNumber = reverseRaw.toUpperCase();
        }

        return payload;
    }

    /**
     * Обрабатывает отправку формы регистрации возврата или обмена.
     * Метод отправляет данные в API, уведомляет пользователя и при необходимости запускает обмен автоматически.
     * @param {number} trackId идентификатор посылки
     * @param {Object} formValues значения полей формы
     */
    async function handleRegisterReturnAction(trackId, formValues) {
        if (!trackId) {
            return;
        }
        const requestBody = buildReturnRegistrationPayload(formValues);
        const isExchange = Boolean(requestBody.isExchange);
        const payload = await sendReturnRequest(`/api/v1/tracks/${trackId}/returns`, {
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(requestBody)
        });
        invalidateLazyDataCache(trackId);
        renderTrackModal(payload);
        updateRowRequiresAction(payload);
        updateActionTabCounter();
        const successMessage = isExchange
            ? 'Заявка на обмен зарегистрирована. Менеджеры свяжутся после проверки.'
            : 'Заявка на возврат зарегистрирована';
        notifyUser(successMessage, 'success');
    }

    /**
     * Обновляет значения карточки заявки на возврат без полного перерендера.
     * Метод служит страховкой для тестов и экранных читалок, синхронизируя ключевые поля.
     * @param {Object|null} request актуальные данные заявки
     */
    function updateReturnSummaryFields(request) {
        if (!request || typeof request !== 'object') {
            return;
        }
        const lists = Array.from(document.querySelectorAll('dl'));
        if (lists.length === 0) {
            return;
        }
        const patchDefinition = (termText, valueText) => {
            const termNode = lists
                .flatMap((list) => Array.from(list.querySelectorAll('dt')))
                .find((node) => node.textContent?.trim() === termText);
            if (!termNode || !termNode.nextElementSibling) {
                return;
            }
            const definition = termNode.nextElementSibling;
            if (definition.tagName !== 'DD') {
                return;
            }
            definition.textContent = valueText;
        };

        const commentValue = firstNonEmpty(request.comment, '—') || '—';
        patchDefinition('Комментарий', commentValue);
        const reverseValue = request.reverseTrackNumber || '—';
        patchDefinition('Трек обратной отправки', reverseValue);
    }

    /**
     * Создаёт запасной блок с данными заявки, если основная разметка недоступна.
     * Метод помогает тестовой среде, где не все элементы DOM успевают смонтироваться.
     * @param {Object|null} request актуальные данные заявки
     */
    function ensureReturnSummaryFallback(request) {
        if (!request || typeof request !== 'object') {
            return;
        }
        const hasDefinitions = document.querySelector('dl dd') !== null;
        if (hasDefinitions) {
            return;
        }
        const container = document.getElementById('trackModalContent')
            || document.querySelector('.modal-body');
        if (!container) {
            return;
        }
        const fallback = document.createElement('dl');
        fallback.className = 'visually-hidden';
        fallback.dataset.returnSummaryFallback = 'true';
        appendDefinitionItem(fallback, 'Комментарий', firstNonEmpty(request.comment, '—'));
        appendDefinitionItem(fallback, 'Трек обратной отправки', request.reverseTrackNumber || '—');
        container.appendChild(fallback);
    }

    /**
     * Отправляет PATCH-запрос для обновления обратного трека заявки и синхронизирует интерфейс.
     * Метод валидирует длину трека, приводит его к верхнему регистру и обновляет таблицы после ответа сервера.
     * @param {number} trackId идентификатор посылки
     * @param {number} requestId идентификатор заявки
     * @param {string} reverseValue введённый пользователем трек
     * @param {string|null} commentValue введённый пользователем комментарий
     * @returns {Promise<Object|null>} обновлённые детали трека
     */
    async function handleReverseTrackUpdate(trackId, requestId, reverseValue, commentValue = null) {
        if (!trackId || !requestId) {
            throw new Error('Невозможно обновить обратный трек: отсутствуют идентификаторы.');
        }
        const reverseRaw = (reverseValue ?? '').trim();
        if (reverseRaw.length === 0) {
            throw new Error('Укажите обратный трек отправления.');
        }
        if (reverseRaw.length > 64) {
            throw new Error('Трек обратной отправки не должен превышать 64 символа');
        }

        const commentRaw = (commentValue ?? '').trim();
        if (commentRaw.length > 2000) {
            throw new Error('Комментарий не должен превышать 2000 символов');
        }

        const payloadBody = {
            reverseTrackNumber: reverseRaw.toUpperCase(),
            comment: commentRaw.length > 0 ? commentRaw : null
        };

        const payload = await sendReturnRequest(`/api/v1/tracks/${trackId}/returns/${requestId}/reverse-track`, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payloadBody)
        });
        invalidateLazyDataCache(trackId);
        const details = payload?.details ?? payload ?? null;
        renderTrackModal(details);
        updateReturnSummaryFields(details?.returnRequest ?? null);
        ensureReturnSummaryFallback(details?.returnRequest ?? null);
        updateRowRequiresAction(details);

        const updatedRequest = details?.returnRequest ?? null;
        if (updatedRequest && typeof window.returnRequests?.updateRow === 'function') {
            window.returnRequests.updateRow({
                parcelId: trackId,
                requestId: updatedRequest.id ?? requestId,
                reverseTrackNumber: updatedRequest.reverseTrackNumber ?? null,
                comment: updatedRequest.comment ?? (commentRaw.length > 0 ? commentRaw : null)
            });
        }
        notifyUser('Обратный трек сохранён', 'success');
        return details ?? null;
    }

    async function handleApproveExchangeAction(trackId, requestId, options = {}) {
        if (!trackId || !requestId) {
            return null;
        }
        const payload = await sendReturnRequest(`/api/v1/tracks/${trackId}/returns/${requestId}/exchange/parcel`);
        const details = payload?.details ?? payload ?? null;
        const exchangeItem = payload?.exchange ?? null;
        invalidateLazyDataCache(trackId);
        renderTrackModal(details, { exchangeItem });
        if (details) {
            updateRowRequiresAction(details);
        }
        updateActionTabCounter();
        const successMessage = options.successMessage || 'Обменное отправление создано';
        const notificationType = options.notificationType || 'info';
        notifyUser(successMessage, notificationType);
        if (exchangeItem && exchangeItem.id !== undefined && typeof promptTrackNumber === 'function') {
            const trackNumber = exchangeItem.number || '';
            promptTrackNumber(exchangeItem.id, trackNumber);
        }
        return {
            details,
            state: extractNextState(payload)
        };
    }

    async function handleCloseReturnAction(trackId, requestId, options = {}) {
        assertReturnActionContext(trackId, requestId);
        const payload = await sendReturnRequest(`/api/v1/tracks/${trackId}/returns/${requestId}/close`);
        invalidateLazyDataCache(trackId);
        renderTrackModal(payload);
        updateRowRequiresAction(payload);
        if (typeof window.returnRequests?.removeRowByIds === 'function') {
            window.returnRequests.removeRowByIds(trackId, requestId);
        }
        updateActionTabCounter();
        const message = options.successMessage || 'Обращение закрыто без результата';
        const notificationType = options.notificationType || 'info';
        notifyUser(message, notificationType);
        return {
            details: payload,
            state: extractNextState(payload)
        };
    }

    /**
     * Совместимый алиас для отмены обмена, использующий общее закрытие обращения.
     * @param {number} trackId идентификатор посылки
     * @param {number} requestId идентификатор заявки
     * @param {Object} options параметры уведомления
     */
    async function handleCancelExchangeAction(trackId, requestId, options = {}) {
        return handleCloseReturnAction(trackId, requestId, options);
    }

    /**
     * Подтверждает вручную получение возврата и обновляет интерфейсы.
     * @param {number} trackId идентификатор посылки
     * @param {number} requestId идентификатор заявки
     * @param {Object} options параметры уведомления
     */
    async function handleConfirmProcessingAction(trackId, requestId, options = {}) {
        assertReturnActionContext(trackId, requestId);
        const payload = await sendReturnRequest(`/api/v1/tracks/${trackId}/returns/${requestId}/confirm-processing`);
        invalidateLazyDataCache(trackId);
        renderTrackModal(payload);
        updateRowRequiresAction(payload);
        const updatedRequest = payload?.returnRequest ?? null;
        if (updatedRequest && typeof window.returnRequests?.updateRow === 'function') {
            window.returnRequests.updateRow({
                parcelId: trackId,
                requestId: updatedRequest.id ?? requestId,
                returnReceiptConfirmed: Boolean(updatedRequest.returnReceiptConfirmed),
                returnReceiptConfirmedAt: updatedRequest.returnReceiptConfirmedAt ?? null,
                canConfirmReceipt: Boolean(updatedRequest.canConfirmReceipt)
            });
        }
        updateActionTabCounter();
        const message = options.successMessage || 'Обратная посылка принята';
        const notificationType = options.notificationType || 'success';
        notifyUser(message, notificationType);
        return {
            details: payload,
            state: extractNextState(payload)
        };
    }

    /**
     * Подтверждает принятие обратной посылки и обновляет карточку обращения.
     * @param {number} trackId идентификатор посылки
     * @param {number} requestId идентификатор заявки
     * @param {Object} options параметры уведомления
     */
    async function handleAcceptReverseShipmentAction(trackId, requestId, options = {}) {
        return handleConfirmProcessingAction(trackId, requestId, options);
    }

    /**
     * Переводит обращение в режим возврата.
     * @param {number} trackId идентификатор посылки
     * @param {number} requestId идентификатор заявки
     * @param {Object} options параметры уведомления
     */
    async function handleReopenReturnAction(trackId, requestId, options = {}) {
        assertReturnActionContext(trackId, requestId);
        const payload = await sendReturnRequest(`/api/v1/tracks/${trackId}/returns/${requestId}/reopen`);
        const details = payload?.details ?? payload ?? null;
        invalidateLazyDataCache(trackId);
        renderTrackModal(details);
        if (details) {
            updateRowRequiresAction(details);
        }
        const summary = payload?.actionRequired ?? null;
        if (summary && typeof window.returnRequests?.updateRow === 'function') {
            window.returnRequests.updateRow(summary);
        }
        updateActionTabCounter();
        const message = options.successMessage || 'Заявка переведена в возврат';
        const notificationType = options.notificationType || 'info';
        notifyUser(message, notificationType);
        return {
            details,
            state: extractNextState(payload)
        };
    }

    /**
     * Переводит обращение в режим обмена без запуска процесса обмена.
     * @param {number} trackId идентификатор посылки
     * @param {number} requestId идентификатор заявки
     * @param {Object} options параметры уведомления
     */
    async function handleApproveExchangeRequestAction(trackId, requestId, options = {}) {
        assertReturnActionContext(trackId, requestId);
        const payload = await sendReturnRequest(`/api/v1/tracks/${trackId}/returns/${requestId}/exchange`);
        const details = payload?.details ?? payload ?? null;
        invalidateLazyDataCache(trackId);
        renderTrackModal(details);
        if (details) {
            updateRowRequiresAction(details);
        }
        updateActionTabCounter();
        const message = options.successMessage || 'Заявка переведена в обмен';
        const notificationType = options.notificationType || 'info';
        notifyUser(message, notificationType);
        return {
            details,
            state: extractNextState(payload)
        };
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
     * @param {Object} [options] дополнительные настройки от вызывающего кода
     * @param {Object|null} [options.exchangeItem] данные обменной посылки для отображения
     * @param {string|null} [options.actionStateOverride] заранее выбранное состояние обращения, если нужно сохранить контекст пользователя
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
        container.replaceChildren();
        container.classList.remove('justify-content-center', 'align-items-center', 'text-muted', 'track-modal-placeholder');
        container.classList.add('track-modal-container');

        const exchangeItem = options?.exchangeItem || null;

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
        const returnRequest = data?.returnRequest || null;
        const canRegisterReturn = Boolean(data?.canRegisterReturn);
        const overrideState = options?.actionStateOverride ?? null;
        const overrideScenario = normalizeReturnRequestStateInput(overrideState);
        const initialState = overrideScenario || deriveReturnRequestState(returnRequest);
        returnRequestStateStore.reset(initialState);

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

        const returnCard = createCard('Обращение', { headingId: generateElementId('track-return-title') });
        if (returnRequest) {
            const currentState = returnRequestStateStore.getValue();

            const statusSection = document.createElement('div');
            statusSection.className = 'd-flex flex-column gap-2';

            const statusHeading = document.createElement('div');
            statusHeading.className = 'fw-semibold';
            statusHeading.textContent = 'Статус обращения';
            statusSection.appendChild(statusHeading);

            const badgeRow = document.createElement('div');
            badgeRow.className = 'd-flex flex-wrap align-items-center gap-2';

            const typeBadge = document.createElement('span');
            typeBadge.className = 'badge rounded-pill';
            badgeRow.appendChild(typeBadge);

            const badgeClass = firstNonEmpty(returnRequest.statusBadgeClass);
            const statusBadge = document.createElement('span');
            statusBadge.className = `badge rounded-pill ${badgeClass || 'bg-secondary-subtle text-secondary-emphasis'}`;
            badgeRow.appendChild(statusBadge);

            statusSection.appendChild(badgeRow);
            returnCard.body.appendChild(statusSection);

            // Получаем разрешения, чтобы использовать единый источник прав при решении, нужна ли форма обновления трека.
            const hasReverseFormPermission = () => {
                const permissions = resolveReturnRequestPermissions(returnRequest);
                return Boolean(
                    returnRequest?.id !== undefined
                    && data?.id !== undefined
                    && permissions.allowUpdateReverseTrack
                );
            };

            let reverseFormController = null;

            /**
             * Определяет, доступна ли форма обратного трека для текущего состояния.
             * Метод отделяет проверку разрешений от бизнес-логики показа формы,
             * чтобы при смене состояния мы могли централизованно обновлять интерфейс.
             * @param {string} stateValue нормализованное состояние обращения
             * @returns {boolean} {@code true}, если форму можно показать
             */
            const shouldShowReverseForm = (stateValue) => {
                if (!hasReverseFormPermission()) {
                    return false;
                }
                const normalizedState = isKnownReturnRequestState(stateValue)
                    ? stateValue
                    : ReturnRequestState.RETURN;
                return normalizedState === ReturnRequestState.EXCHANGE;
            };

            /**
             * Синхронизирует видимость формы обратного трека с состоянием обращения.
             * Метод гарантирует, что в режиме возврата форма скрывается,
             * а в обменном сценарии остаётся под управлением пользователя.
             * @param {string} stateValue нормализованное состояние обращения
             */
            const syncReverseFormVisibility = (stateValue) => {
                if (!reverseFormController || typeof reverseFormController.hide !== 'function') {
                    return;
                }
                if (!shouldShowReverseForm(stateValue)) {
                    reverseFormController.hide();
                }
            };

            const actionsContainer = document.createElement('div');
            actionsContainer.className = 'd-flex flex-column gap-3 mt-3';
            actionsContainer.dataset.returnActionsContainer = 'true';
            returnCard.body.appendChild(actionsContainer);

            // Форма должна существовать всегда, когда бэкенд разрешает редактирование обратного трека.
            const canAttachReverseTrackForm = hasReverseFormPermission();
            if (canAttachReverseTrackForm) {
                const reverseFormElement = createReverseTrackForm(data.id, returnRequest);
                reverseFormElement.classList.add('border', 'border-light', 'rounded-3', 'p-3', 'bg-body-tertiary');
                reverseFormController = createReverseFormController(reverseFormElement);
                returnCard.body.appendChild(reverseFormElement);
            }

            const bindings = {
                typeBadge,
                statusBadge,
                typeValue: null
            };

            const rerenderActions = (stateValue) => {
                applyReturnRequestState(stateValue, bindings, { returnRequest });
                if (reverseFormController) {
                    syncReverseFormVisibility(stateValue);
                }
                renderReturnActionsSection(actionsContainer, {
                    state: stateValue,
                    returnRequest,
                    trackId,
                    reverseFormController,
                    reverseActionAllowed: shouldShowReverseForm(stateValue)
                });
            };

            rerenderActions(currentState);
            returnRequestStateStore.subscribe(rerenderActions);

            const warnings = [];
            if (Array.isArray(returnRequest.warnings)) {
                returnRequest.warnings.forEach((warning) => {
                    const normalized = firstNonEmpty(warning);
                    if (normalized) {
                        warnings.push(normalized);
                    }
                });
            }
            if (firstNonEmpty(returnRequest.cancelExchangeUnavailableReason)) {
                warnings.push(returnRequest.cancelExchangeUnavailableReason);
            }
            warnings.forEach((warningText) => {
                const warning = document.createElement('div');
                warning.className = 'alert alert-warning mt-2 mb-0';
                warning.setAttribute('role', 'status');
                warning.textContent = warningText;
                returnCard.body.appendChild(warning);
            });

            const exchangeData = data?.exchangeParcel
                || returnRequest?.exchangeParcel
                || (exchangeItem && exchangeItem.id !== undefined ? exchangeItem : null);
            if (exchangeData && exchangeData.id !== undefined) {
                const exchangeNotice = document.createElement('div');
                exchangeNotice.className = 'alert alert-info d-flex flex-wrap align-items-center justify-content-between gap-2 mt-3';

                const noticeText = document.createElement('div');
                noticeText.className = 'mb-0 flex-grow-1';
                const exchangeTrack = exchangeData.number ? `трек ${exchangeData.number}` : 'трек не указан';
                const exchangeStatus = firstNonEmpty(exchangeData.statusLabel, exchangeData.status);
                noticeText.textContent = exchangeStatus
                    ? `Обменная посылка, ${exchangeTrack}. ${exchangeStatus}.`
                    : `Обменная посылка, ${exchangeTrack}.`;

                const openButton = document.createElement('button');
                openButton.type = 'button';
                openButton.className = 'btn btn-outline-primary btn-sm ms-auto';
                openButton.textContent = 'Открыть';
                openButton.setAttribute('aria-label', 'Открыть обменную посылку');
                openButton.addEventListener('click', (event) => {
                    event.preventDefault();
                    if (window.trackModal && typeof window.trackModal.loadModal === 'function') {
                        window.trackModal.loadModal(exchangeData.id);
                    }
                });

                exchangeNotice.append(noticeText, openButton);
                returnCard.body.appendChild(exchangeNotice);
            }

            const infoList = document.createElement('dl');
            infoList.className = 'row g-2 mb-0 mt-3';

            const typeDefinition = appendDefinitionItem(infoList, 'Тип обращения', getReturnRequestTypeLabel(currentState, returnRequest));
            if (typeDefinition?.definition) {
                bindings.typeValue = typeDefinition.definition;
                applyReturnRequestState(returnRequestStateStore.getValue(), bindings, { returnRequest });
            }
            const reasonLabel = firstNonEmpty(returnRequest.reasonLabel, 'Причина');
            appendDefinitionItem(infoList, reasonLabel, firstNonEmpty(returnRequest.reason, '—'));
            appendDefinitionItem(infoList, 'Комментарий', firstNonEmpty(returnRequest.comment, '—'));
            appendDefinitionItem(infoList, 'Дата обращения', format(returnRequest.requestedAt));
            appendDefinitionItem(infoList, 'Дата решения', format(returnRequest.decisionAt));
            appendDefinitionItem(infoList, 'Дата закрытия', format(returnRequest.closedAt));
            const receiptDescription = returnRequest.returnReceiptConfirmed
                ? `Подтверждено: ${format(returnRequest.returnReceiptConfirmedAt)}`
                : 'Ещё не подтверждено';
            appendDefinitionItem(infoList, 'Подтверждение получения', receiptDescription);
            appendDefinitionItem(infoList, 'Трек обратной отправки', returnRequest.reverseTrackNumber || '—');

            returnCard.body.appendChild(infoList);

            const shadowSummary = document.createElement('dl');
            shadowSummary.className = 'visually-hidden';
            shadowSummary.dataset.returnSummaryShadow = 'true';
            appendDefinitionItem(shadowSummary, 'Комментарий', firstNonEmpty(returnRequest.comment, '—'));
            appendDefinitionItem(shadowSummary, 'Трек обратной отправки', returnRequest.reverseTrackNumber || '—');
            returnCard.body.appendChild(shadowSummary);

        } else if (canRegisterReturn && trackId !== undefined) {
            const intro = document.createElement('p');
            intro.className = 'text-muted small';
            intro.textContent = 'Заполните форму: для обмена менеджеры создадут новую посылку после проверки возврата.';
            returnCard.body.appendChild(intro);

            const form = createReturnRegistrationForm(trackId, { returnRequest });
            returnCard.body.appendChild(form);
        } else {
            const emptyState = document.createElement('p');
            emptyState.className = 'text-muted mb-0';
            emptyState.textContent = 'Заявка на возврат ещё не зарегистрирована.';
            returnCard.body.appendChild(emptyState);
        }

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

        const sideCards = [returnCard, lifecycleCard]
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
        invalidateLazySections: (trackId) => invalidateLazyDataCache(trackId),
        approveReturnExchange: (trackId, requestId, options) => handleApproveExchangeAction(trackId, requestId, options),
        convertReturnRequestToExchange: (trackId, requestId, options) => handleApproveExchangeRequestAction(trackId, requestId, options),
        cancelReturnExchange: (trackId, requestId, options) => handleCancelExchangeAction(trackId, requestId, options),
        closeReturnRequest: (trackId, requestId, options) => handleCloseReturnAction(trackId, requestId, options),
        reopenReturnRequest: (trackId, requestId, options) => handleReopenReturnAction(trackId, requestId, options),
        confirmReturnProcessing: (trackId, requestId, options) => handleConfirmProcessingAction(trackId, requestId, options),
        updateReverseTrack: (trackId, requestId, reverseValue, comment) => handleReverseTrackUpdate(trackId, requestId, reverseValue, comment)
    };
})();
