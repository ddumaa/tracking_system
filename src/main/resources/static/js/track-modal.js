(() => {
    'use strict';

    /** Текущий идентификатор интервала обратного отсчёта. */
    let refreshTimerId = null;

    /** Счётчик для генерации уникальных идентификаторов элементов формы. */
    let elementSequence = 0;

    /** Ключ хранилища для состояния сворачивания правой панели. */
    const SIDE_PANEL_COLLAPSE_KEY = 'trackModal.sidePanel.collapsed';

    /** Функция очистки обработчиков правой панели. */
    let disposeSidePanelInteractions = null;

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
     * Читает булево значение из localStorage с запасным вариантом.
     * Метод инкапсулирует работу с хранилищем, чтобы остальной код не зависел от реализации хранения (SRP).
     * @param {string} key ключ хранения
     * @param {boolean} fallback запасное значение
     * @returns {boolean} результирующее значение
     */
    function readBooleanFromStorage(key, fallback) {
        try {
            const storedValue = window.localStorage.getItem(key);
            if (storedValue === null) {
                return fallback;
            }
            return storedValue === 'true';
        } catch (error) {
            console.warn('Не удалось прочитать состояние панели', key, error);
            return fallback;
        }
    }

    /**
     * Сохраняет булево значение в localStorage.
     * Метод обрабатывает возможные ошибки записи, чтобы приложение продолжало работу даже при ограничениях хранилища.
     * @param {string} key ключ хранения
     * @param {boolean} value сохраняемое значение
     */
    function writeBooleanToStorage(key, value) {
        try {
            window.localStorage.setItem(key, value ? 'true' : 'false');
        } catch (error) {
            console.warn('Не удалось сохранить состояние панели', key, error);
        }
    }

    /**
     * Настраивает выезжающий блок с информацией об обмене и возврате.
     * Метод управляет состоянием панели и синхронизирует aria-атрибуты, соблюдая принцип SRP.
     * @param {Object} options параметры конфигурации
     * @param {HTMLElement} options.container основной контейнер модального окна
     * @param {HTMLElement} options.drawer панель, выезжающая поверх основного контента
     * @param {Array<HTMLElement>} options.toggleButtons коллекция кнопок управления панелью
     * @param {boolean} [options.drawerDisabled] начальный признак недоступности панели
     * @returns {Function} функция очистки обработчиков
     */
    function setupSidePanelInteractions({
        container,
        drawer,
        toggleButtons,
        drawerDisabled = false
    }) {
        const buttons = Array.isArray(toggleButtons)
            ? toggleButtons.filter((button) => button instanceof HTMLElement)
            : [];
        if (!container || !drawer || buttons.length === 0) {
            return () => {};
        }

        const drawerId = drawer.id || `trackModalDrawer${++elementSequence}`;
        drawer.id = drawerId;
        buttons.forEach((button) => {
            button.setAttribute('aria-controls', drawerId);
        });

        let isDrawerDisabled = Boolean(drawerDisabled)
            || buttons.every((button) => button.getAttribute('aria-disabled') === 'true');
        let isOpen = !isDrawerDisabled && !readBooleanFromStorage(SIDE_PANEL_COLLAPSE_KEY, false);

        /**
         * Обновляет aria-атрибуты кнопки, чтобы отражать текущее состояние панели.
         * Метод изолирует текстовые ресурсы, чтобы облегчить локализацию (OCP).
         * @param {boolean} open актуальное состояние панели
         */
        const updateToggleAria = (open) => {
            buttons.forEach((button) => {
                const toggleLabelCollapsed = button.dataset.toggleLabelCollapsed;
                const toggleLabelExpanded = button.dataset.toggleLabelExpanded;
                const disabledTitle = button.dataset.disabledTitle || 'Недоступно';
                const isButtonDisabled = button.getAttribute('aria-disabled') === 'true';
                if (!toggleLabelCollapsed || !toggleLabelExpanded) {
                    return;
                }
                if (isButtonDisabled) {
                    button.setAttribute('title', disabledTitle);
                    button.setAttribute('aria-label', `${toggleLabelCollapsed}. ${disabledTitle}`);
                } else {
                    const label = open ? toggleLabelExpanded : toggleLabelCollapsed;
                    button.setAttribute('aria-label', label);
                    button.setAttribute('title', label);
                }
            });
        };

        /**
         * Применяет визуальное состояние панели и синхронизирует хранилище.
         * Метод отвечает только за обновление DOM и не содержит побочных эффектов вне области ответственности.
         * @param {boolean} open целевое состояние панели
         */
        const applyState = (open) => {
            const shouldOpen = !isDrawerDisabled && Boolean(open);
            drawer.setAttribute('aria-hidden', shouldOpen ? 'false' : 'true');
            container.classList.toggle('track-modal-container--drawer-open', shouldOpen);
            buttons.forEach((button) => {
                const isButtonDisabled = button.getAttribute('aria-disabled') === 'true';
                const expandedValue = shouldOpen && !isButtonDisabled;
                button.setAttribute('aria-expanded', String(expandedValue));
            });
            updateToggleAria(shouldOpen);
        };

        applyState(isOpen);

        const focusFirstToggle = () => {
            const [firstToggle] = buttons;
            if (firstToggle && typeof firstToggle.focus === 'function') {
                firstToggle.focus();
            }
        };

        const handleToggleClick = (event) => {
            event.preventDefault();
            const target = event.currentTarget;
            const targetDisabled = target && target.getAttribute('aria-disabled') === 'true';
            if (isDrawerDisabled || targetDisabled) {
                return;
            }
            isOpen = !isOpen;
            applyState(isOpen);
            writeBooleanToStorage(SIDE_PANEL_COLLAPSE_KEY, !isOpen);
        };

        const handleKeydown = (event) => {
            if (event.key === 'Escape' && isOpen) {
                isOpen = false;
                applyState(isOpen);
                writeBooleanToStorage(SIDE_PANEL_COLLAPSE_KEY, true);
                if (drawer.contains(event.target)) {
                    focusFirstToggle();
                }
            }
        };

        buttons.forEach((button) => {
            button.addEventListener('click', handleToggleClick);
        });
        container.addEventListener('keydown', handleKeydown);

        return () => {
            buttons.forEach((button) => {
                button.removeEventListener('click', handleToggleClick);
            });
            container.removeEventListener('keydown', handleKeydown);
        };
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
        if (typeof onClick === 'function') {
            button.addEventListener('click', (event) => {
                event.preventDefault();
                onClick(button);
            });
        }
        return button;
    }

    /**
     * Создаёт встроенную кнопку управления панелью обмена и возврата.
     * Метод формирует вертикальный таб с понятными aria-атрибутами, чтобы UI оставался доступным (SRP).
     * @returns {HTMLButtonElement} кнопка переключения панели
     */
    function createDrawerControlButton() {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'track-modal-tab btn track-modal-drawer-toggle';
        button.dataset.toggleLabelCollapsed = 'Показать панель «Обмен/Возврат»';
        button.dataset.toggleLabelExpanded = 'Скрыть панель «Обмен/Возврат»';
        button.dataset.disabledTitle = 'Недоступно';
        button.setAttribute('aria-expanded', 'false');
        button.setAttribute('aria-disabled', 'false');
        button.setAttribute('aria-label', button.dataset.toggleLabelCollapsed);
        button.setAttribute('title', button.dataset.toggleLabelCollapsed);

        const label = document.createElement('span');
        label.className = 'track-modal-tab__label';
        label.setAttribute('aria-hidden', 'true');
        'Обмен/Возврат'.split('').forEach((symbol, index) => {
            const symbolElement = document.createElement('span');
            symbolElement.className = 'track-modal-tab__symbol';
            symbolElement.textContent = symbol;
            if (symbol === '/' && index !== 0) {
                symbolElement.classList.add('track-modal-tab__symbol--divider');
            }
            label.appendChild(symbolElement);
        });

        const srLabel = document.createElement('span');
        srLabel.className = 'visually-hidden';
        srLabel.textContent = 'Обмен/Возврат';

        button.append(label, srLabel);
        return button;
    }

    /**
     * Применяет состояние доступности к вертикальной кнопке панели.
     * Метод централизует управление aria-атрибутами, чтобы не дублировать код в рендеринге (SRP).
     * @param {HTMLButtonElement} button настраиваемая кнопка
     * @param {Object} options параметры состояния
     * @param {boolean} options.disabled признак недоступности панели
     * @param {string} [options.reason] текст тултипа для недоступной кнопки
     */
    function applyDrawerToggleAvailability(button, { disabled, reason } = {}) {
        if (!(button instanceof HTMLElement)) {
            return;
        }

        const collapsedLabel = button.dataset.toggleLabelCollapsed
            || 'Показать панель «Обмен/Возврат»';
        const disabledTitle = reason || button.dataset.disabledTitle || 'Недоступно';
        const isDisabled = Boolean(disabled);

        button.setAttribute('aria-disabled', String(isDisabled));
        button.tabIndex = isDisabled ? -1 : 0;
        if (isDisabled) {
            button.classList.add('track-modal-tab--disabled');
            button.setAttribute('title', disabledTitle);
            button.setAttribute('aria-label', `${collapsedLabel}. ${disabledTitle}`);
        } else {
            button.classList.remove('track-modal-tab--disabled');
            const title = button.dataset.toggleLabelCollapsed || collapsedLabel;
            button.setAttribute('title', title);
            button.setAttribute('aria-label', title);
        }
    }

    /**
     * Создаёт кнопку закрытия панели обмена и возврата.
     * Метод подготавливает визуальное оформление и ARIA-атрибуты, позволяя переиспользовать кнопку в любом контейнере.
     * @returns {HTMLButtonElement} кнопка закрытия панели
     */
    function createDrawerCloseButton() {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'btn btn-link btn-sm track-side-panel__close';
        button.textContent = 'Закрыть';
        button.setAttribute('aria-expanded', 'false');
        button.setAttribute('aria-disabled', 'false');
        button.setAttribute('aria-label', 'Скрыть панель «Обмен/Возврат»');
        button.setAttribute('title', 'Скрыть панель «Обмен/Возврат»');
        button.dataset.toggleLabelCollapsed = 'Показать панель «Обмен/Возврат»';
        button.dataset.toggleLabelExpanded = 'Скрыть панель «Обмен/Возврат»';
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
     * Определяет, относится ли заявка к обмену с учётом обратной совместимости.
     * Метод проверяет новые и старые флаги, чтобы не допустить ложных выводов.
     * @param {Object} returnRequest DTO заявки
     * @returns {boolean} {@code true}, если пользователь запросил обмен
     */
    function isExchangeRequest(returnRequest) {
        if (!returnRequest || typeof returnRequest !== 'object') {
            return false;
        }
        if (typeof returnRequest.isExchange === 'boolean') {
            return returnRequest.isExchange;
        }
        if (typeof returnRequest.exchangeRequested === 'boolean') {
            return returnRequest.exchangeRequested;
        }
        if (typeof returnRequest.exchangeApproved === 'boolean' && returnRequest.exchangeApproved) {
            return true;
        }
        return false;
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
        const payload = await sendReturnRequest(`/api/v1/tracks/${trackId}/returns/${requestId}/exchange`);
        const details = payload?.details ?? payload ?? null;
        invalidateLazyDataCache(trackId);
        renderTrackModal(details);
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

    async function handleCreateExchangeParcelAction(trackId, requestId, options = {}) {
        if (!trackId || !requestId) {
            return null;
        }
        const payload = await sendReturnRequest(`/api/v1/tracks/${trackId}/returns/${requestId}/exchange/parcel`);
        const details = payload?.details ?? null;
        const exchangeItem = payload?.exchange ?? null;
        invalidateLazyDataCache(trackId);
        renderTrackModal(details, { exchangeItem });
        if (details) {
            updateRowRequiresAction(details);
        }
        if (typeof window.returnRequests?.removeRowByIds === 'function') {
            window.returnRequests.removeRowByIds(trackId, requestId);
        }
        updateActionTabCounter();
        const successMessage = options.successMessage || 'Создана обменная посылка';
        const notificationType = options.notificationType || 'success';
        notifyUser(successMessage, notificationType);
        return details;
    }

    async function handleCloseWithoutExchange(trackId, requestId, options = {}) {
        if (!trackId || !requestId) {
            return null;
        }
        const payload = await sendReturnRequest(`/api/v1/tracks/${trackId}/returns/${requestId}/close`);
        invalidateLazyDataCache(trackId);
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
     * Подтверждает вручную получение возврата и обновляет интерфейсы.
     * @param {number} trackId идентификатор посылки
     * @param {number} requestId идентификатор заявки
     * @param {Object} options параметры уведомления
     */
    async function handleConfirmProcessingAction(trackId, requestId, options = {}) {
        if (!trackId || !requestId) {
            return null;
        }
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
        const message = options.successMessage || 'Обработка возврата подтверждена';
        const notificationType = options.notificationType || 'success';
        notifyUser(message, notificationType);
        return payload;
    }

    /**
     * Переводит обменную заявку обратно в режим возврата.
     * @param {number} trackId идентификатор посылки
     * @param {number} requestId идентификатор заявки
     * @param {Object} options параметры уведомления
     */
    async function handleReopenReturnAction(trackId, requestId, options = {}) {
        if (!trackId || !requestId) {
            return null;
        }
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
        return details;
    }

    /**
     * Отменяет обмен и закрывает заявку без отправки новой посылки.
     * @param {number} trackId идентификатор посылки
     * @param {number} requestId идентификатор заявки
     * @param {Object} options параметры уведомления
     */
    async function handleCancelExchangeAction(trackId, requestId, options = {}) {
        if (!trackId || !requestId) {
            return null;
        }
        const payload = await sendReturnRequest(`/api/v1/tracks/${trackId}/returns/${requestId}/cancel`);
        const details = payload?.details ?? payload ?? null;
        invalidateLazyDataCache(trackId);
        renderTrackModal(details);
        if (details) {
            updateRowRequiresAction(details);
        }
        const summary = payload?.actionRequired ?? null;
        if (summary && typeof window.returnRequests?.updateRow === 'function') {
            window.returnRequests.updateRow(summary);
        } else if (typeof window.returnRequests?.removeRowByIds === 'function') {
            window.returnRequests.removeRowByIds(trackId, requestId);
        }
        updateActionTabCounter();
        const message = options.successMessage || 'Обмен отменён и заявка закрыта';
        const notificationType = options.notificationType || 'warning';
        notifyUser(message, notificationType);
        return details;
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
    function renderTrackModal(data, options = {}) {
        clearRefreshTimer();

        if (typeof disposeSidePanelInteractions === 'function') {
            disposeSidePanelInteractions();
            disposeSidePanelInteractions = null;
        }

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

        const mainLayout = document.createElement('div');
        mainLayout.className = 'track-modal-main-layout';

        const mainWrapper = document.createElement('div');
        mainWrapper.className = 'track-modal-main-wrapper';

        const mainColumn = document.createElement('div');
        mainColumn.className = 'track-modal-main d-flex flex-column gap-3';
        mainWrapper.appendChild(mainColumn);
        mainLayout.appendChild(mainWrapper);

        const drawer = document.createElement('aside');
        drawer.className = 'track-modal-drawer';
        drawer.setAttribute('role', 'complementary');
        drawer.setAttribute('tabindex', '-1');
        drawer.setAttribute('aria-hidden', 'true');

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

        const inlineDrawerToggle = createDrawerControlButton();
        const toggleSlot = document.createElement('div');
        toggleSlot.className = 'track-modal-tab-slot';
        toggleSlot.appendChild(inlineDrawerToggle);
        mainLayout.appendChild(toggleSlot);

        const trackId = data?.id;
        const returnRequest = data?.returnRequest || null;
        const canRegisterReturn = Boolean(data?.canRegisterReturn);
        const shouldDisableDrawer = !returnRequest && !canRegisterReturn;
        applyDrawerToggleAvailability(inlineDrawerToggle, {
            disabled: shouldDisableDrawer,
            reason: 'Недоступно'
        });
        const exchangeContext = isExchangeRequest(returnRequest);
        const canStartExchange = Boolean(returnRequest?.canStartExchange);
        const canCreateExchangeParcel = Boolean(returnRequest?.canCreateExchangeParcel);

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

        mainColumn.appendChild(parcelCard.card);

        const returnCard = createCard('Обращение');
        if (returnRequest) {
            const statusSection = document.createElement('div');
            statusSection.className = 'd-flex flex-column gap-2';

            const statusHeading = document.createElement('div');
            statusHeading.className = 'fw-semibold';
            statusHeading.textContent = 'Статус обращения';
            statusSection.appendChild(statusHeading);

            const badgeRow = document.createElement('div');
            badgeRow.className = 'd-flex flex-wrap align-items-center gap-2';

            const typeBadge = document.createElement('span');
            typeBadge.className = exchangeContext
                ? 'badge rounded-pill bg-warning-subtle text-warning-emphasis'
                : 'badge rounded-pill bg-info-subtle text-info-emphasis';
            typeBadge.textContent = exchangeContext ? 'Обмен' : 'Возврат';
            typeBadge.setAttribute('aria-label', `Тип обращения: ${typeBadge.textContent}`);
            badgeRow.appendChild(typeBadge);

            const statusLabelText = firstNonEmpty(returnRequest.statusLabel, returnRequest.status, 'Статус не определён');
            const badgeClass = firstNonEmpty(returnRequest.statusBadgeClass);
            const statusBadge = document.createElement('span');
            statusBadge.className = `badge rounded-pill ${badgeClass || 'bg-secondary-subtle text-secondary-emphasis'}`;
            statusBadge.textContent = statusLabelText;
            statusBadge.setAttribute('aria-label', `Текущий статус обращения: ${statusLabelText}`);
            badgeRow.appendChild(statusBadge);

            statusSection.appendChild(badgeRow);
            returnCard.body.appendChild(statusSection);

            const actionsWrapper = document.createElement('div');
            actionsWrapper.className = 'd-flex flex-column gap-3 mt-3';

            const primaryStack = document.createElement('div');
            primaryStack.className = 'd-flex flex-column gap-2';
            primaryStack.dataset.returnPrimaryActions = 'true';

            const secondaryStack = document.createElement('div');
            secondaryStack.className = 'd-flex flex-column gap-2';
            secondaryStack.dataset.returnSecondaryActions = 'true';

            const appendAction = (stack, button) => {
                if (button) {
                    stack.appendChild(button);
                }
            };

            if (canStartExchange && trackId !== undefined && returnRequest.id !== undefined) {
                const startLabel = returnRequest.exchangeRequested
                    ? 'Создать обменную посылку'
                    : 'Перевести в обмен';
                const startHandler = returnRequest.exchangeRequested
                    ? () => handleCreateExchangeParcelAction(trackId, returnRequest.id, {
                        successMessage: 'Создана обменная посылка',
                        notificationType: 'success'
                    })
                    : () => handleApproveExchangeAction(trackId, returnRequest.id, {
                        successMessage: 'Заявка переведена в обмен',
                        notificationType: 'info'
                    });
                const startButton = createActionButton({
                    text: startLabel,
                    variant: 'primary',
                    ariaLabel: returnRequest.exchangeRequested
                        ? 'Создать обменную посылку для покупателя'
                        : 'Перевести заявку возврата в обмен',
                    onClick: (button) => runButtonAction(button, startHandler),
                    fullWidth: true
                });
                appendAction(primaryStack, startButton);
            }

            const allowDirectCreation = canCreateExchangeParcel
                && trackId !== undefined
                && returnRequest?.id !== undefined
                && !(canStartExchange && returnRequest.exchangeRequested);
            if (allowDirectCreation) {
                const createButton = createActionButton({
                    text: 'Создать обменную посылку',
                    variant: 'primary',
                    ariaLabel: 'Создать обменную посылку для покупателя',
                    onClick: (button) => runButtonAction(button,
                        () => handleCreateExchangeParcelAction(trackId, returnRequest.id, {
                            successMessage: 'Создана обменная посылка',
                            notificationType: 'success'
                        })),
                    fullWidth: true
                });
                appendAction(primaryStack, createButton);
            }

            if (returnRequest?.canCloseWithoutExchange && trackId !== undefined && returnRequest.id !== undefined) {
                const closeButtonText = exchangeContext ? 'Закрыть без обмена' : 'Принять возврат';
                const closeVariant = exchangeContext ? 'outline-secondary' : 'success';
                const closeOptions = exchangeContext
                    ? { successMessage: 'Заявка закрыта без обмена', notificationType: 'info' }
                    : { successMessage: 'Возврат принят', notificationType: 'success' };
                const closeButton = createActionButton({
                    text: closeButtonText,
                    variant: closeVariant,
                    ariaLabel: exchangeContext
                        ? 'Закрыть заявку без запуска обменной посылки'
                        : 'Подтвердить приём возврата',
                    onClick: (button) => runButtonAction(button,
                        () => handleCloseWithoutExchange(trackId, returnRequest.id, closeOptions)),
                    fullWidth: true
                });
                appendAction(exchangeContext ? secondaryStack : primaryStack, closeButton);
            }

            if (returnRequest?.canConfirmReceipt && trackId !== undefined && returnRequest.id !== undefined) {
                const confirmButton = createActionButton({
                    text: 'Подтвердить получение',
                    variant: 'outline-success',
                    ariaLabel: 'Подтвердить получение возврата без закрытия заявки',
                    onClick: (button) => runButtonAction(button,
                        () => handleConfirmProcessingAction(trackId, returnRequest.id, {
                            successMessage: 'Получение возврата подтверждено',
                            notificationType: 'success'
                        })),
                    fullWidth: true
                });
                appendAction(secondaryStack, confirmButton);
            }

            if (returnRequest?.canReopenAsReturn && trackId !== undefined && returnRequest.id !== undefined) {
                const reopenButton = createActionButton({
                    text: 'Перевести в возврат',
                    variant: 'outline-warning',
                    ariaLabel: 'Перевести обменную заявку обратно в возврат',
                    onClick: (button) => runButtonAction(button,
                        () => handleReopenReturnAction(trackId, returnRequest.id, {
                            successMessage: 'Заявка переведена в возврат',
                            notificationType: 'info'
                        })),
                    fullWidth: true
                });
                appendAction(secondaryStack, reopenButton);
            }

            if (returnRequest?.canCancelExchange && trackId !== undefined && returnRequest.id !== undefined) {
                const cancelButton = createActionButton({
                    text: 'Отменить обмен',
                    variant: 'outline-danger',
                    ariaLabel: 'Отменить обмен и закрыть заявку',
                    onClick: (button) => runButtonAction(button,
                        () => handleCancelExchangeAction(trackId, returnRequest.id, {
                            successMessage: 'Обмен отменён и заявка закрыта',
                            notificationType: 'warning'
                        })),
                    fullWidth: true
                });
                appendAction(secondaryStack, cancelButton);
            }

            if (primaryStack.childElementCount > 0) {
                actionsWrapper.appendChild(primaryStack);
            }
            if (secondaryStack.childElementCount > 0) {
                actionsWrapper.appendChild(secondaryStack);
            }

            const hintText = firstNonEmpty(
                returnRequest.hint,
                exchangeContext
                    ? 'Заявка оформлена как обмен. Новая посылка появится после подтверждения возврата.'
                    : 'Заявка оформлена как возврат. Выберите подходящее действие, чтобы завершить процесс.'
            );
            const detailsUrl = firstNonEmpty(returnRequest.detailsUrl, returnRequest.hintUrl, returnRequest.helpUrl);
            if (hintText) {
                const hintParagraph = document.createElement('p');
                hintParagraph.className = 'text-muted small mb-0';
                hintParagraph.textContent = hintText;
                const moreLink = document.createElement('a');
                moreLink.textContent = 'Подробнее';
                moreLink.className = 'ms-1';
                if (detailsUrl) {
                    moreLink.href = detailsUrl;
                    moreLink.target = '_blank';
                    moreLink.rel = 'noreferrer noopener';
                } else {
                    moreLink.href = '#';
                    moreLink.classList.add('disabled', 'pe-none');
                    moreLink.setAttribute('aria-disabled', 'true');
                }
                hintParagraph.append(' ');
                hintParagraph.appendChild(moreLink);
                actionsWrapper.appendChild(hintParagraph);
            }

            if (actionsWrapper.childElementCount > 0) {
                returnCard.body.appendChild(actionsWrapper);
            }

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

            appendDefinitionItem(infoList, 'Тип обращения', exchangeContext ? 'Обмен' : 'Возврат');
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

            const canEditReverseTrack = Boolean(
                returnRequest?.id !== undefined
                && data?.id !== undefined
                && (!returnRequest?.reverseTrackNumber || returnRequest?.requiresAction)
            );
            if (canEditReverseTrack) {
                const reverseForm = createReverseTrackForm(data.id, returnRequest);
                returnCard.body.appendChild(reverseForm);
            }
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
            const { card, body } = createCard('Жизненный цикл заказа');
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
            body.appendChild(lifecycleSection.container);
            return card;
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

        const sidePanel = document.createElement('div');
        sidePanel.className = 'track-side-panel d-flex flex-column gap-3';
        sidePanel.setAttribute('role', 'region');

        const sideHeader = document.createElement('div');
        sideHeader.className = 'track-side-panel__header d-flex align-items-center justify-content-between gap-2';

        const sideTitle = document.createElement('h6');
        sideTitle.id = 'trackSidePanelTitle';
        sideTitle.className = 'track-side-panel__title mb-0 text-uppercase text-muted small';
        sideTitle.textContent = 'Обращение и этапы';

        const sideCloseButton = createDrawerCloseButton();

        sideHeader.append(sideTitle, sideCloseButton);

        const sideContent = document.createElement('div');
        sideContent.className = 'track-side-panel__body d-flex flex-column gap-3';
        sideContent.appendChild(returnCard.card);
        if (lifecycleCard) {
            sideContent.appendChild(lifecycleCard);
        }

        sidePanel.append(sideHeader, sideContent);
        drawer.setAttribute('aria-labelledby', sideTitle.id);
        drawer.appendChild(sidePanel);

        container.appendChild(mainLayout);
        container.appendChild(drawer);

        disposeSidePanelInteractions = setupSidePanelInteractions({
            container,
            drawer,
            toggleButtons: [inlineDrawerToggle, sideCloseButton],
            drawerDisabled: shouldDisableDrawer
        });

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
        closeReturnRequest: (trackId, requestId, options) => handleCloseWithoutExchange(trackId, requestId, options),
        reopenReturnRequest: (trackId, requestId, options) => handleReopenReturnAction(trackId, requestId, options),
        cancelReturnExchange: (trackId, requestId, options) => handleCancelExchangeAction(trackId, requestId, options),
        confirmReturnProcessing: (trackId, requestId, options) => handleConfirmProcessingAction(trackId, requestId, options),
        updateReverseTrack: (trackId, requestId, reverseValue, comment) => handleReverseTrackUpdate(trackId, requestId, reverseValue, comment)
    };
})();
