const { TextEncoder, TextDecoder } = require('util');

global.TextEncoder = TextEncoder;
global.TextDecoder = TextDecoder;

describe('track-modal render', () => {
    /**
     * Подготавливает DOM и загружает модуль перед каждым тестом.
     */
    function setupDom() {
        jest.resetModules();
        const markup = '<div id="infoModal"><div class="modal-body"><div id="trackModalContent"></div></div></div>';
        document.body.innerHTML = markup;
        global.bootstrap = {
            Tooltip: {
                getOrCreateInstance: jest.fn(() => ({ update: jest.fn() }))
            },
            Modal: {
                getOrCreateInstance: jest.fn(() => ({ show: jest.fn() }))
            }
        };
        global.notifyUser = jest.fn();
        global.promptTrackNumber = jest.fn();
        global.window.matchMedia = jest.fn(() => ({
            matches: false,
            addEventListener: jest.fn(),
            removeEventListener: jest.fn(),
            addListener: jest.fn(),
            removeListener: jest.fn()
        }));
        const defaultHeaders = { get: jest.fn(() => 'application/json') };
        global.fetch = jest.fn(() => Promise.resolve({ ok: true, headers: defaultHeaders, json: () => Promise.resolve({}) }));
        global.crypto = { randomUUID: jest.fn(() => 'test-uuid') };
        if (global.window) {
            global.window.crypto = global.crypto;
        }
        require('../../main/resources/static/js/track-modal.js');
        global.window.returnRequests = {
            updateRow: jest.fn(),
            removeRowByIds: jest.fn(),
            refreshEmptyState: jest.fn()
        };
    }

    /**
     * Преобразует тестовые данные старого формата в новую структуру, ожидаемую модулем.
     * @param {Object} details исходные данные теста
     * @returns {Object} нормализованный объект
     */
    function normalizeDetails(details) {
        if (!details || typeof details !== 'object') {
            return details;
        }
        const normalized = { ...details };
        if ('returnRequest' in normalized) {
            normalized.returnRequest = normalizeReturnRequest(normalized.returnRequest);
        }
        return normalized;
    }

    /**
     * Карта синонимов для кодов действий, чтобы извлекать причины недоступности из разных DTO.
     */
    const ACTION_REASON_KEYS = Object.freeze({
        setModeExchange: ['setModeExchange', 'set_mode_exchange', 'SET_MODE_EXCHANGE'],
        setModeReturn: ['setModeReturn', 'set_mode_return', 'SET_MODE_RETURN'],
        registerExchangeParcel: ['registerExchangeParcel', 'register_exchange_parcel', 'REGISTER_EXCHANGE_PARCEL'],
        markOutboundSent: ['markOutboundSent', 'mark_outbound_sent', 'MARK_OUTBOUND_SENT'],
        markInboundArrived: ['markInboundArrived', 'mark_inbound_arrived', 'MARK_INBOUND_ARRIVED'],
        markInboundPickedUp: ['markInboundPickedUp', 'mark_inbound_picked_up', 'MARK_INBOUND_PICKED_UP'],
        markExchangeSent: ['markExchangeSent', 'mark_exchange_sent', 'MARK_EXCHANGE_SENT'],
        markExchangeDelivered: ['markExchangeDelivered', 'mark_exchange_delivered', 'MARK_EXCHANGE_DELIVERED'],
        closeRequest: ['closeRequest', 'close_request', 'CLOSE_REQUEST'],
        updateReverseTrack: ['updateReverseTrack', 'update_reverse_track', 'UPDATE_REVERSE_TRACK']
    });

    const ACTION_KEY_TO_CODE = Object.freeze({
        setModeExchange: 'SET_MODE_EXCHANGE',
        setModeReturn: 'SET_MODE_RETURN',
        registerExchangeParcel: 'REGISTER_EXCHANGE_PARCEL',
        markOutboundSent: 'MARK_OUTBOUND_SENT',
        markInboundArrived: 'MARK_INBOUND_ARRIVED',
        markInboundPickedUp: 'MARK_INBOUND_PICKED_UP',
        markExchangeSent: 'MARK_EXCHANGE_SENT',
        markExchangeDelivered: 'MARK_EXCHANGE_DELIVERED',
        closeRequest: 'CLOSE_REQUEST',
        updateReverseTrack: 'UPDATE_REVERSE_TRACK'
    });

    /**
     * Преобразует карту кодов в набор строк верхнего регистра.
     * @param {Array<string>} codes исходные значения
     * @returns {Set<string>} множество нормализованных кодов
     */
    function toUpperCaseSet(codes) {
        return new Set((codes || []).filter(Boolean).map((code) => String(code).toUpperCase()));
    }

    /**
     * Извлекает строку причины недоступности из источника с учётом вариантов именования.
     * @param {Object} source объект-источник
     * @param {Array<string>} variants возможные ключи
     * @returns {string|null} нормализованный текст или {@code null}
     */
    function pickReasonFromSource(source, variants) {
        if (!source || typeof source !== 'object') {
            return null;
        }
        for (const variant of variants) {
            const value = source[variant];
            if (typeof value === 'string') {
                const trimmed = value.trim();
                if (trimmed.length > 0) {
                    return trimmed;
                }
            }
        }
        return null;
    }

    /**
     * Обогащает заявку вложенными объектами (state/actions/timestamps) при отсутствии новых полей.
     * @param {Object|null} request тестовая заявка
     * @returns {Object|null} нормализованная заявка
     */
    function normalizeReturnRequest(request) {
        if (!request || typeof request !== 'object') {
            return request;
        }
        const stage = request.stage || request.state || 'NEW';
        const stageUpper = typeof stage === 'string' ? stage.toUpperCase() : '';
        const mode = request.mode
            || (stageUpper.includes('EXCHANGE')
                ? 'EXCHANGE'
                : (request.exchangeApproved || request.exchangeRequested ? 'EXCHANGE' : 'RETURN'));
        const state = {
            mode,
            stage,
            manualStageOverride: Boolean(request.manualStageOverride),
            manualTrackOverride: Boolean(request.manualTrackOverride),
            exchangeRequested: Boolean(request.exchangeRequested),
            exchangeApproved: Boolean(request.exchangeApproved),
            exchangeShipmentDispatched: Boolean(request.exchangeShipmentDispatched),
            exchangeTrack: request.exchangeTrack || null,
            returnReceiptConfirmed: Boolean(request.returnReceiptConfirmed),
            ...request.state
        };
        const legacy = request.actionPermissions || {};
        const mapLegacy = (key) => Boolean(legacy?.[key]);
        const rawActions = request.availableActions || {};
        const normalizedCodes = Array.isArray(rawActions.actions)
            ? rawActions.actions
            : Array.isArray(rawActions.actionCodes)
                ? rawActions.actionCodes
                : (Array.isArray(request.actionCodes) ? request.actionCodes : []);
        const codesSet = toUpperCaseSet(normalizedCodes);
        const hasAction = (code) => codesSet.has(String(code).toUpperCase());
        const reasonSources = [
            rawActions,
            rawActions?.unavailableReasons,
            rawActions?.reasons,
            request?.unavailableReasons,
            request?.actionUnavailableReasons,
            request?.actionPermissionsReasons
        ];
        const reasonVariants = (key) => ACTION_REASON_KEYS[key] || [key];
        const readReason = (key) => {
            const directKey = `${key}UnavailableReason`;
            const directSources = [request, rawActions];
            for (const source of directSources) {
                const value = source?.[directKey];
                if (typeof value === 'string') {
                    const trimmed = value.trim();
                    if (trimmed.length > 0) {
                        return trimmed;
                    }
                }
            }
            const variants = reasonVariants(key);
            for (const source of reasonSources) {
                const candidate = pickReasonFromSource(source, variants);
                if (candidate) {
                    return candidate;
                }
            }
            return null;
        };
        const availableActions = {
            ...rawActions,
            setModeExchange: Boolean((request.canSetModeExchange
                ?? request.canStartExchange
                ?? rawActions.setModeExchange
                ?? rawActions.startExchange
                ?? (mapLegacy('allowConvertToExchange') && mapLegacy('allowLaunchExchange')))
                || hasAction('SET_MODE_EXCHANGE')),
            setModeReturn: Boolean((request.canSetModeReturn
                ?? request.canReopenAsReturn
                ?? rawActions.setModeReturn
                ?? rawActions.reopenAsReturn
                ?? mapLegacy('allowConvertToReturn'))
                || hasAction('SET_MODE_RETURN')),
            registerExchangeParcel: Boolean((request.canRegisterExchangeParcel
                ?? rawActions.registerExchangeParcel
                ?? rawActions.createExchangeParcel
                ?? mapLegacy('allowLaunchExchange'))
                || hasAction('REGISTER_EXCHANGE_PARCEL')),
            closeRequest: Boolean((request.canCloseRequest
                ?? request.canCloseWithoutExchange
                ?? rawActions.closeRequest
                ?? rawActions.closeWithoutExchange
                ?? mapLegacy('allowClose'))
                || hasAction('CLOSE_REQUEST')),
            updateReverseTrack: Boolean((request.canUpdateReverseTrack
                ?? rawActions.updateReverseTrack
                ?? mapLegacy('allowUpdate'))
                || hasAction('UPDATE_REVERSE_TRACK')),
            markOutboundSent: Boolean(request.canMarkOutboundSent
                ?? rawActions.markOutboundSent
                ?? hasAction('MARK_OUTBOUND_SENT')),
            markInboundArrived: Boolean(request.canMarkInboundArrived
                ?? rawActions.markInboundArrived
                ?? hasAction('MARK_INBOUND_ARRIVED')),
            markInboundPickedUp: Boolean((request.canMarkInboundPickedUp
                ?? rawActions.markInboundPickedUp
                ?? rawActions.confirmReceipt
                ?? mapLegacy('allowAcceptReverse')
                ?? mapLegacy('allowAccept'))
                || hasAction('MARK_INBOUND_PICKED_UP')),
            markExchangeSent: Boolean(request.canMarkExchangeSent
                ?? rawActions.markExchangeSent
                ?? hasAction('MARK_EXCHANGE_SENT')),
            markExchangeDelivered: Boolean(request.canMarkExchangeDelivered
                ?? rawActions.markExchangeDelivered
                ?? hasAction('MARK_EXCHANGE_DELIVERED')),
            actions: normalizedCodes,
            actionCodes: normalizedCodes
        };
        Object.entries(ACTION_KEY_TO_CODE).forEach(([actionKey, code]) => {
            if (availableActions[actionKey]) {
                codesSet.add(String(code).toUpperCase());
            }
        });
        const codesArray = Array.from(codesSet);
        availableActions.actions = codesArray;
        availableActions.actionCodes = codesArray;

        Object.keys(ACTION_REASON_KEYS).forEach((actionKey) => {
            const reason = readReason(actionKey);
            if (reason) {
                availableActions[`${actionKey}UnavailableReason`] = reason;
            }
        });
        const timestamps = {
            requestedAt: request.requestedAt || null,
            createdAt: request.createdAt || null,
            decisionAt: request.decisionAt || null,
            closedAt: request.closedAt || null,
            stageStartedAt: request.stageStartedAt || null,
            stageUpdatedAt: request.stageUpdatedAt || null,
            exchangeTrackAssignedAt: request.exchangeTrackAssignedAt || null,
            returnReceiptConfirmedAt: request.returnReceiptConfirmedAt || null,
            ...request.timestamps
        };
        const reverseTrack = request.reverseTrack ?? null;
        return {
            ...request,
            reverseTrack,
            state,
            availableActions,
            timestamps
        };
    }


    /**
     * Хелпер для рендеринга модалки с учётом нормализации данных.
     * @param {Object} details исходные данные
     */
    function renderModal(details) {
        global.window.trackModal.render(normalizeDetails(details));
    }

    /**
     * Выполняет последовательное ожидание микрозадач, чтобы тесты учитывали асинхронные обновления DOM.
     * @param {number} iterations количество циклов ожидания
     */
    async function flushAsyncQueue(iterations = 4) {
        for (let index = 0; index < iterations; index += 1) {
            // eslint-disable-next-line no-await-in-loop
            await Promise.resolve();
        }
    }

    /**
     * Отключает обменные действия в переданных деталях, чтобы принудительно отрисовать режим возврата.
     * @param {Object} details исходные данные трека
     */
    function disableExchangeLaunch(details) {
        const available = details?.returnRequest?.availableActions;
        if (!available || typeof available !== 'object') {
            return;
        }
        available.registerExchangeParcel = false;
        const stripCode = (code) => String(code).toUpperCase() !== 'REGISTER_EXCHANGE_PARCEL';
        if (Array.isArray(available.actions)) {
            available.actions = available.actions.filter(stripCode);
        }
        if (Array.isArray(available.actionCodes)) {
            available.actionCodes = available.actionCodes.filter(stripCode);
        }
    }

    /**
     * Десериализует тело POST-запроса команды и возвращает DTO.
     * @param {Array} call параметры вызова mock-функции fetch
     * @returns {Object} десериализованное тело запроса
     */
    function readCommandRequestDto(call) {
        expect(call).toBeDefined();
        const [, init] = call || [];
        expect(init).toBeDefined();
        const { body } = init || {};
        expect(typeof body).toBe('string');
        return JSON.parse(body);
    }

    /**
     * Проверяет, что строка соответствует ISO-формату UTC.
     * @param {string} value проверяемое значение
     */
    function expectIsoTimestamp(value) {
        expect(typeof value).toBe('string');
        expect(value).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/);
    }

    /**
     * Проверяет корректность идемпотентного ключа, не привязываясь к конкретному значению.
     * @param {string} value проверяемое значение
     */
    function expectValidIdempotencyKey(value) {
        expect(typeof value).toBe('string');
        expect(value.length).toBeGreaterThan(0);
    }

    /**
     * Фабрика тестовых DTO возвратов с полной матрицей действий.
     * Используется для подготовки консистентных данных в стиле паттерна Builder (OOP + SRP).
     */
    class ReturnRequestTestFixtureFactory {
        /**
         * Создаёт DTO трека с заявкой и полным набором action-кодов.
         * @param {Object} [options] параметры генерации
         * @param {'RETURN'|'EXCHANGE'} [options.mode='RETURN'] целевой режим заявки
         * @param {Object} [options.overrides] переопределения для тонкой настройки
         * @returns {Object} сконструированные детали трека
         */
        static createDetailsWithAllActions(options = {}) {
            const { mode = 'RETURN', overrides = {} } = options;
            const trackId = overrides.id ?? (mode === 'RETURN' ? 101 : 202);
            const requestId = overrides.returnRequest?.id ?? (mode === 'RETURN' ? 501 : 502);
            const stage = overrides.returnRequest?.stage || (mode === 'EXCHANGE' ? 'REGISTERED_EXCHANGE' : 'REGISTERED_RETURN');
            const actionCodes = [
                'SET_MODE_RETURN',
                'SET_MODE_EXCHANGE',
                'REGISTER_EXCHANGE_PARCEL',
                'MARK_OUTBOUND_SENT',
                'MARK_INBOUND_ARRIVED',
                'MARK_INBOUND_PICKED_UP',
                'MARK_EXCHANGE_SENT',
                'MARK_EXCHANGE_DELIVERED',
                'CLOSE_REQUEST',
                'UPDATE_REVERSE_TRACK'
            ];
            const availableActions = {
                actions: actionCodes,
                actionCodes,
                setModeExchange: true,
                setModeReturn: true,
                registerExchangeParcel: true,
                markOutboundSent: true,
                markInboundArrived: true,
                markInboundPickedUp: true,
                markExchangeSent: true,
                markExchangeDelivered: true,
                closeRequest: true,
                updateReverseTrack: true
            };
            const requestBase = {
                id: requestId,
                stage,
                status: 'REGISTERED',
                statusLabel: 'Зарегистрирована',
                mode,
                state: {
                    mode,
                    stage,
                    exchangeRequested: mode === 'EXCHANGE',
                    exchangeApproved: mode === 'EXCHANGE'
                },
                availableActions,
                requestedAt: '2024-03-01T10:00:00Z',
                decisionAt: null,
                closedAt: null,
                reverseTrack: null,
                returnReceiptConfirmed: false,
                returnReceiptConfirmedAt: null,
                exchangeRequested: mode === 'EXCHANGE',
                exchangeApproved: mode === 'EXCHANGE',
                actionPermissions: {},
                requiresAction: true
            };
            const request = {
                ...requestBase,
                ...(overrides.returnRequest || {})
            };
            const detailsBase = {
                id: trackId,
                number: overrides.number || (mode === 'RETURN' ? 'RET123456BY' : 'EXC987654BY'),
                deliveryService: 'Belpost',
                systemStatus: 'В обработке',
                history: [],
                refreshAllowed: true,
                nextRefreshAt: null,
                canEditTrack: true,
                timeZone: 'UTC',
                episodeNumber: 77,
                exchange: mode === 'EXCHANGE',
                returnShipment: mode !== 'EXCHANGE',
                chain: [
                    {
                        id: trackId,
                        number: overrides.number || (mode === 'RETURN' ? 'RET123456BY' : 'EXC987654BY'),
                        exchange: mode === 'EXCHANGE',
                        returnShipment: mode !== 'EXCHANGE',
                        current: true
                    }
                ],
                returnRequest: request,
                canRegisterReturn: true,
                lifecycle: [],
                requiresAction: true
            };
            return {
                ...detailsBase,
                ...overrides,
                returnRequest: request
            };
        }
    }

    /**
     * Драйвер для юнит-тестов действий модалки, инкапсулирующий настройку моков и работу с DOM.
     * Следует паттерну Facade, упрощая читаемость тестов.
     */
    class ReturnRequestActionTestDriver {
        /**
         * @param {Object} details исходные детали трека
         */
        constructor(details) {
            this.details = details;
            this.trackId = details?.id ?? null;
            this.requestId = details?.returnRequest?.id ?? null;
        }

        /**
         * Подготавливает моки {@link fetch} для последовательности «команда → обновление деталей».
         * @param {Object} [options] параметры поведения
         * @param {Object} [options.requestDto] ответ POST-команды
         * @param {Object} [options.refreshedDetails] DTO после обновления
         */
        mockSuccessfulCommandFlow(options = {}) {
            const { requestDto = { id: 1, status: 'QUEUED' }, refreshedDetails = null } = options;
            const headers = { get: jest.fn(() => 'application/json') };
            const trackResponse = refreshedDetails || this.details;
            if (typeof global.fetch?.mockClear === 'function') {
                global.fetch.mockClear();
            }
            global.fetch.mockImplementation((url, init = {}) => {
                const stringUrl = String(url);
                if (stringUrl.includes(`/api/v1/returns/${this.requestId}/commands`)) {
                    return Promise.resolve({ ok: true, headers, json: () => Promise.resolve(requestDto) });
                }
                if (stringUrl.includes(`/api/v1/tracks/${this.trackId}`)) {
                    return Promise.resolve({ ok: true, headers, json: () => Promise.resolve(trackResponse) });
                }
                return Promise.resolve({ ok: true, headers, json: () => Promise.resolve({}) });
            });
        }

        /**
         * Рендерит модалку на основе текущих деталей.
         */
        render() {
            renderModal(this.details);
        }

        /**
         * Находит кнопку по тексту и инициирует клик.
         * @param {string} label отображаемый текст кнопки
         */
        async clickAction(label) {
            const actionCard = Array.from(document.querySelectorAll('section.card'))
                .find((card) => card.querySelector('h6')?.textContent === 'Обращение');
            const button = Array.from(actionCard?.querySelectorAll('button') || [])
                .find((btn) => btn.textContent?.trim() === label);
            expect(button).toBeDefined();
            button?.click();
            await flushAsyncQueue();
        }

        /**
         * Возвращает параметры вызова fetch для команды.
         * @returns {Array|undefined} найденный вызов mock-функции
         */
        getCommandFetchCall() {
            return global.fetch.mock.calls.find((call) => String(call[0]).includes(`/api/v1/returns/${this.requestId}/commands`));
        }
    }

    afterEach(() => {
        jest.clearAllMocks();
        document.body.innerHTML = '';
        delete global.bootstrap;
        delete global.notifyUser;
        delete global.promptTrackNumber;
        delete global.fetch;
        delete global.crypto;
        if (global.window && global.window.matchMedia) {
            delete global.window.matchMedia;
        }
        if (global.window && global.window.returnRequests) {
            delete global.window.returnRequests;
        }
    });

    test('renders episode number and single chain item for base parcel', () => {
        setupDom();
        const containerBefore = global.document.getElementById('trackModalContent');
        expect(containerBefore).not.toBeNull();
        const data = {
            id: 1,
            number: 'AB123456789BY',
            deliveryService: 'Belpost',
            systemStatus: 'В пути',
            history: [],
            refreshAllowed: true,
            nextRefreshAt: null,
            canEditTrack: false,
            timeZone: 'UTC',
            episodeNumber: 42,
            exchange: false, returnShipment: false,
            chain: [
                { id: 1, number: 'AB123456789BY', exchange: false, returnShipment: false, current: true }
            ],
            returnRequest: null,
            canRegisterReturn: false,
            lifecycle: [],
            requiresAction: false
        };

        renderModal(data);

        const content = global.document.getElementById('trackModalContent')
            || global.document.querySelector('.modal-body');
        expect(content).not.toBeNull();
        expect(content.textContent).toContain('Эпизод №42');
        const chainButtons = content.querySelectorAll('button.track-chain__item');
        expect(chainButtons).toHaveLength(1);
        expect(chainButtons[0].disabled).toBe(true);
        expect(chainButtons[0].textContent).toContain('AB123456789BY');
    });

    test('shows lifecycle placeholder for outbound-only stage list', async () => {
        setupDom();
        const data = {
            id: 2,
            number: 'CD987654321BY',
            deliveryService: 'Belpost',
            systemStatus: 'В пути',
            history: [],
            refreshAllowed: true,
            nextRefreshAt: null,
            canEditTrack: false,
            timeZone: 'UTC',
            episodeNumber: null,
            exchange: false, returnShipment: false,
            chain: [],
            returnRequest: null,
            canRegisterReturn: false,
            lifecycle: [
                {
                    code: 'OUTBOUND',
                    title: 'Отправление магазина',
                    actor: 'Магазин',
                    description: 'Магазин отправил посылку.',
                    state: 'COMPLETED',
                    occurredAt: '2024-01-01T10:00:00Z',
                    trackNumber: 'CD987654321BY',
                    trackContext: 'Исходная посылка'
                }
            ],
            requiresAction: false
        };

        renderModal(data);

        const lifecycleCard = Array.from(document.querySelectorAll('section.card'))
            .find((card) => card.querySelector('h6')?.textContent === 'Жизненный цикл заказа');
        expect(lifecycleCard).toBeDefined();
        const toggle = lifecycleCard?.querySelector('button');
        toggle?.click();
        await Promise.resolve();
        await Promise.resolve();
        await new Promise((resolve) => setTimeout(resolve, 0));
        const placeholderText = lifecycleCard?.querySelector('.track-lazy-section__result p')?.textContent || '';
        expect(placeholderText).toContain('Этапы пока недоступны');
    });

    test('renders exchange chain with clickable original parcel', async () => {
        setupDom();
        const loadSpy = jest.spyOn(global.window.trackModal, 'loadModal').mockImplementation(() => {});
        const data = {
            id: 11,
            number: 'RB987654321CN',
            deliveryService: 'China Post',
            systemStatus: 'В пути',
            history: [],
            refreshAllowed: true,
            nextRefreshAt: null,
            canEditTrack: true,
            timeZone: 'UTC',
            episodeNumber: 77,
            exchange: true, returnShipment: false,
            chain: [
                { id: 11, number: 'RB987654321CN', exchange: true, returnShipment: false, current: true },
                { id: 10, number: 'RB111222333CN', exchange: false, returnShipment: false, current: false }
            ],
            returnRequest: {
                id: 5,
                state: 'REGISTERED_RETURN',
status: 'Зарегистрирована',
                statusLabel: 'Зарегистрирована',
                statusBadgeClass: 'bg-warning-subtle text-warning-emphasis',
                reasonLabel: 'Причина',
                reason: 'Размер не подошёл',
                comment: 'Свяжитесь со мной',
                requestedAt: '2024-01-05T12:00:00Z',
                decisionAt: null,
                closedAt: null,
                reverseTrack: null,
                requiresAction: true,
                exchangeApproved: false,
                exchangeRequested: false,
                canStartExchange: true,
                canCreateExchangeParcel: false,
                canCloseWithoutExchange: true,
                canReopenAsReturn: false,
                canConfirmReceipt: true,
                returnReceiptConfirmed: false,
                returnReceiptConfirmedAt: null,
                hint: 'Подтвердите возврат, чтобы оформить обмен.',
                warnings: ['Свяжитесь с покупателем перед отправкой.'],
                detailsUrl: 'https://example.com/returns'
            },
            canRegisterReturn: false,
            lifecycle: [
                {
                    code: 'OUTBOUND',
                    title: 'Отправление магазина',
                    actor: 'Магазин',
                    description: '...',
                    state: 'COMPLETED',
                    occurredAt: '2024-01-01T10:00:00Z',
                    trackNumber: 'RB987654321CN',
                    trackContext: 'Исходная посылка'
                },
                {
                    code: 'NEW',
                    title: 'Заявка зарегистрирована',
                    actor: 'Покупатель',
                    description: '...',
                    state: 'COMPLETED',
                    occurredAt: '2024-01-05T11:30:00Z',
                    trackNumber: null,
                    trackContext: null
                },
                {
                    code: 'OUTBOUND_SENT',
                    title: 'Возврат отправлен',
                    actor: 'Покупатель',
                    description: '...',
                    state: 'IN_PROGRESS',
                    occurredAt: '2024-01-05T12:00:00Z',
                    trackNumber: null,
                    trackContext: 'Обратный трек'
                },
                {
                    code: 'INBOUND_ARRIVED',
                    title: 'Возврат прибыл',
                    actor: 'Логистика',
                    description: '...',
                    state: 'PLANNED',
                    occurredAt: null,
                    trackNumber: null,
                    trackContext: null
                },
                {
                    code: 'INBOUND_PICKED_UP',
                    title: 'Возврат обработан',
                    actor: 'Магазин',
                    description: '...',
                    state: 'PLANNED',
                    occurredAt: null,
                    trackNumber: null,
                    trackContext: null
                }
            ],
            requiresAction: true
        };

        const headers = { get: jest.fn(() => 'application/json') };
        global.fetch.mockImplementation((url) => {
            if (String(url).includes('/lifecycle')) {
                return Promise.resolve({
                    ok: true,
                    headers,
                    json: () => Promise.resolve({ lifecycle: data.lifecycle })
                });
            }
            if (String(url).includes('/history')) {
                return Promise.resolve({
                    ok: true,
                    headers,
                    json: () => Promise.resolve({ history: [] })
                });
            }
            return Promise.resolve({ ok: true, headers, json: () => Promise.resolve({}) });
        });

        renderModal(data);

        const buttons = document.querySelectorAll('button.track-chain__item');
        expect(buttons).toHaveLength(2);
        expect(buttons[0].disabled).toBe(true);
        expect(buttons[0].textContent).toContain('обмен');
        buttons[1].click();
        expect(loadSpy).toHaveBeenCalledWith(10);

        const returnCard = Array.from(document.querySelectorAll('section.card'))
            .find((card) => card.querySelector('h6')?.textContent === 'Обращение');
        expect(returnCard).toBeDefined();
        const definitions = returnCard?.querySelector('dl');
        expect(definitions?.textContent).toContain('Тип обращения');
        const hint = returnCard?.querySelector('p.text-muted.small');
        expect(hint?.textContent).toContain('Подтвердите возврат');
        const warningBanner = returnCard?.querySelector('.alert.alert-warning');
        expect(warningBanner?.textContent).toContain('Свяжитесь с покупателем');
        expect(returnCard?.textContent).toContain('Подтверждение получения');

        const lifecycleCard = Array.from(document.querySelectorAll('section.card'))
            .find((card) => card.querySelector('h6')?.textContent === 'Жизненный цикл заказа');
        expect(lifecycleCard).toBeDefined();
        const toggleLifecycle = lifecycleCard?.querySelector('button');
        toggleLifecycle?.click();
        await Promise.resolve();
        await Promise.resolve();
        await new Promise((resolve) => setTimeout(resolve, 0));
        const lifecycleItems = Array.from(lifecycleCard?.querySelectorAll('ol[role="list"] li') || []);
        const lifecycleText = lifecycleItems.map((item) => item.textContent?.trim() || '').join(' ');
        expect(lifecycleItems.length).toBeGreaterThanOrEqual(2);
        expect(lifecycleText).toContain('Отправление магазина');
        expect(lifecycleText).toContain('Возврат отправлен');
        expect(lifecycleText).toContain('Возврат обработан');

        const confirmBtn = Array.from(document.querySelectorAll('button'))
            .find((btn) => btn.getAttribute('aria-label') === 'Подтвердить получение возврата и завершить обращение');
        expect(confirmBtn).toBeDefined();
        expect(confirmBtn?.textContent).toContain('Принять возврат');

        const closeButton = Array.from(document.querySelectorAll('button'))
            .find((btn) => {
                const text = btn.textContent || '';
                return text.includes('Отменить обмен') || text.includes('Закрыть обращение');
            });
        expect(closeButton).toBeDefined();
    });

    test('shows receipt confirmation alongside exchange actions when confirmation allowed', () => {
        setupDom();

        const data = {
            id: 31,
            number: 'BY2024001',
            deliveryService: 'Belpost',
            systemStatus: 'Вручена',
            history: [],
            refreshAllowed: true,
            nextRefreshAt: null,
            canEditTrack: false,
            timeZone: 'UTC',
            episodeNumber: 21,
            exchange: false, returnShipment: false,
            chain: [],
            returnRequest: {
                id: 81,
                state: 'EXCHANGE_LAUNCHED',
                status: 'EXCHANGE_STARTED',
                statusLabel: 'Обмен в работе',
                statusBadgeClass: 'bg-info-subtle text-info-emphasis',
                reasonLabel: 'Причина',
                reason: 'Не подошёл размер',
                comment: 'Покупатель ждёт замену',
                requestedAt: '2024-02-10T12:00:00Z',
                decisionAt: null,
                closedAt: null,
                reverseTrack: null,
                requiresAction: true,
                actionPermissions: {
                    allowAccept: false,
                    allowLaunchExchange: false,
                    allowAcceptReverse: true,
                    allowClose: true,
                    allowConvertToReturn: true,
                    allowConvertToExchange: false,
                    allowUpdateReverseTrack: true
                },
                returnReceiptConfirmed: false,
                returnReceiptConfirmedAt: null,
                hint: 'Можно подтвердить поступление и продолжить обмен.',
                warnings: [],
                detailsUrl: 'https://example.com/returns'
            },
            canRegisterReturn: false,
            lifecycle: [],
            requiresAction: true
        };

        renderModal(data);

        const actionCard = Array.from(document.querySelectorAll('section.card'))
            .find((card) => card.querySelector('h6')?.textContent === 'Обращение');
        expect(actionCard).toBeDefined();

        const buttons = Array.from(actionCard?.querySelectorAll('button') || []);
        const texts = buttons.map((btn) => btn.textContent?.trim());

        const confirmButton = buttons
            .find((btn) => btn.getAttribute('aria-label') === 'Подтвердить получение обратной посылки');
        expect(confirmButton).toBeDefined();

        expect(texts).toContain('Перевести в возврат');
        expect(texts).toContain('Добавить трек обратной посылки');
        const hasCancelAction = texts.some((label) => label === 'Отменить обмен' || label === 'Закрыть обращение');
        expect(hasCancelAction).toBe(true);
    });

    test('sends close request for exchange when cancel action triggered', async () => {
        setupDom();

        const headers = { get: jest.fn(() => 'application/json') };
        const updatedDetails = {
            id: 31,
            number: 'BY2024001',
            deliveryService: 'Belpost',
            systemStatus: 'Вручена',
            history: [],
            refreshAllowed: true,
            nextRefreshAt: null,
            canEditTrack: false,
            timeZone: 'UTC',
            episodeNumber: 21,
            exchange: false,
            returnShipment: false,
            chain: [],
            returnRequest: {
                id: 81,
                state: 'REGISTERED_RETURN',
                status: 'REGISTERED',
                statusLabel: 'Возврат',
                statusBadgeClass: 'bg-info-subtle text-info-emphasis',
                reasonLabel: 'Причина',
                reason: 'Не подошёл размер',
                comment: 'Покупатель ждёт замену',
                requestedAt: '2024-02-10T12:00:00Z',
                decisionAt: null,
                closedAt: null,
                reverseTrack: null,
                requiresAction: true,
                actionPermissions: {
                    allowAccept: false,
                    allowLaunchExchange: false,
                    allowAcceptReverse: false,
                    allowClose: true,
                    allowConvertToReturn: false,
                    allowConvertToExchange: true,
                    allowUpdateReverseTrack: false
                },
                returnReceiptConfirmed: false,
                returnReceiptConfirmedAt: null,
                hint: 'Можно подтвердить поступление и продолжить обмен.',
                warnings: [],
                detailsUrl: 'https://example.com/returns'
            },
            canRegisterReturn: false,
            lifecycle: [],
            requiresAction: true
        };
        const payload = { details: updatedDetails, actionRequired: { parcelId: 31, requestId: 81, requiresAction: true } };
        global.fetch.mockImplementation((url) => {
            if (String(url).includes('/close')) {
                return Promise.resolve({ ok: true, headers, json: () => Promise.resolve(payload) });
            }
            return Promise.resolve({ ok: true, headers, json: () => Promise.resolve({}) });
        });

        const initialData = {
            id: 31,
            number: 'BY2024001',
            deliveryService: 'Belpost',
            systemStatus: 'Вручена',
            history: [],
            refreshAllowed: true,
            nextRefreshAt: null,
            canEditTrack: false,
            timeZone: 'UTC',
            episodeNumber: 21,
            exchange: false,
            returnShipment: false,
            chain: [],
            returnRequest: {
                id: 81,
                state: 'EXCHANGE_LAUNCHED',
                status: 'EXCHANGE_STARTED',
                statusLabel: 'Обмен в работе',
                statusBadgeClass: 'bg-info-subtle text-info-emphasis',
                reasonLabel: 'Причина',
                reason: 'Не подошёл размер',
                comment: 'Покупатель ждёт замену',
                requestedAt: '2024-02-10T12:00:00Z',
                decisionAt: null,
                closedAt: null,
                reverseTrack: null,
                requiresAction: true,
                actionPermissions: {
                    allowAccept: false,
                    allowLaunchExchange: false,
                    allowAcceptReverse: true,
                    allowClose: true,
                    allowConvertToReturn: true,
                    allowConvertToExchange: false,
                    allowUpdateReverseTrack: true
                },
                returnReceiptConfirmed: false,
                returnReceiptConfirmedAt: null,
                hint: 'Можно подтвердить поступление и продолжить обмен.',
                warnings: [],
                detailsUrl: 'https://example.com/returns'
            },
            canRegisterReturn: false,
            lifecycle: [],
            requiresAction: true
        };

        renderModal(initialData);

        const actionCard = Array.from(document.querySelectorAll('section.card'))
            .find((card) => card.querySelector('h6')?.textContent === 'Обращение');
        const closeButton = Array.from(actionCard?.querySelectorAll('button') || [])
            .find((btn) => {
                const text = btn.textContent || '';
                return text === 'Отменить обмен' || text === 'Закрыть обращение';
            });
        expect(closeButton).toBeDefined();
        closeButton?.click();

        await Promise.resolve();
        await Promise.resolve();
        await Promise.resolve();
        await new Promise((resolve) => setTimeout(resolve, 0));

        const requestCall = global.fetch.mock.calls.find((call) => String(call[0]).includes('/api/v1/returns/81/commands'));
        const requestDto = readCommandRequestDto(requestCall);
        expectValidIdempotencyKey(requestDto.idempotencyKey);
        expect(requestDto.action).toBe('CLOSE_REQUEST');
        expect(requestDto.payload).toEqual({});
        expect(global.notifyUser).toHaveBeenCalledWith('Обращение закрыто', 'warning');

        const rerenderedCard = Array.from(document.querySelectorAll('section.card'))
            .find((card) => card.querySelector('h6')?.textContent === 'Обращение');
        const rerenderButtons = Array.from(rerenderedCard?.querySelectorAll('button') || [])
            .map((btn) => btn.textContent?.trim());
        expect(rerenderButtons).not.toContain('Перевести в обмен');
    });

    test('does not render return details link without url', () => {
        setupDom();

        const expectedHint = 'Детали обращения доступны в личном кабинете.';
        const data = {
            id: 401,
            number: 'BY2024005',
            deliveryService: 'Belpost',
            systemStatus: 'В обработке',
            history: [],
            refreshAllowed: true,
            nextRefreshAt: null,
            canEditTrack: false,
            timeZone: 'UTC',
            episodeNumber: null,
            exchange: false,
            returnShipment: false,
            chain: [],
            returnRequest: {
                id: 901,
                state: 'REGISTERED_RETURN',
status: 'Зарегистрирована',
                statusLabel: 'Зарегистрирована',
                statusBadgeClass: 'bg-info-subtle text-info-emphasis',
                reasonLabel: 'Причина',
                reason: 'Покупатель сообщил о проблеме',
                comment: null,
                requestedAt: '2024-02-15T12:00:00Z',
                decisionAt: null,
                closedAt: null,
                reverseTrack: null,
                requiresAction: false,
                exchangeApproved: false,
                exchangeRequested: false,
                canStartExchange: false,
                canCreateExchangeParcel: false,
                canCloseWithoutExchange: false,
                canReopenAsReturn: false,
                canConfirmReceipt: false,
                returnReceiptConfirmed: false,
                returnReceiptConfirmedAt: null,
                hint: expectedHint,
                warnings: [],
                detailsUrl: '',
                hintUrl: '',
                helpUrl: ''
            },
            canRegisterReturn: false,
            lifecycle: [],
            requiresAction: false
        };

        renderModal(data);

        const actionCard = Array.from(document.querySelectorAll('section.card'))
            .find((card) => card.querySelector('h6')?.textContent === 'Обращение');
        expect(actionCard).toBeDefined();

        const hintParagraph = actionCard?.querySelector('p.text-muted.small.mb-0');
        expect(hintParagraph).toBeDefined();
        expect(hintParagraph?.textContent?.trim()).toBe(expectedHint);
        expect(hintParagraph?.querySelector('a')).toBeNull();
    });

    test('shows reopen action for requested exchange without explicit flag', () => {
        setupDom();

        const data = {
            id: 101,
            number: 'BY2024003',
            deliveryService: 'Belpost',
            systemStatus: 'Ожидает подтверждения',
            history: [],
            refreshAllowed: true,
            nextRefreshAt: null,
            canEditTrack: false,
            timeZone: 'UTC',
            episodeNumber: 23,
            exchange: false, returnShipment: false,
            chain: [],
            returnRequest: {
                id: 91,
                state: 'REGISTERED_EXCHANGE',
                status: 'REGISTERED',
                statusLabel: 'Обмен запрошен',
                statusBadgeClass: 'bg-warning-subtle text-warning-emphasis',
                reasonLabel: 'Причина',
                reason: 'Не подошёл цвет',
                comment: 'Покупатель просит обмен',
                requestedAt: '2024-02-12T12:00:00Z',
                decisionAt: null,
                closedAt: null,
                reverseTrack: null,
                requiresAction: true,
                returnReceiptConfirmed: false,
                returnReceiptConfirmedAt: null,
                actionPermissions: {
                    allowAccept: false,
                    allowLaunchExchange: true,
                    allowAcceptReverse: false,
                    allowClose: false,
                    allowConvertToReturn: true,
                    allowConvertToExchange: false,
                    allowUpdateReverseTrack: false
                },
                hint: 'Покупатель ожидает решение по обмену.',
                warnings: [],
                detailsUrl: null
            },
            canRegisterReturn: false,
            lifecycle: [],
            requiresAction: true
        };

        renderModal(data);

        const actionCard = Array.from(document.querySelectorAll('section.card'))
            .find((card) => card.querySelector('h6')?.textContent === 'Обращение');
        expect(actionCard).toBeDefined();

        const reopenButton = Array.from(actionCard?.querySelectorAll('button') || [])
            .find((btn) => btn.textContent?.trim() === 'Перевести в возврат');
        expect(reopenButton).toBeDefined();
        expect(reopenButton?.classList.contains('d-none')).toBe(false);
        expect(reopenButton?.getAttribute('aria-hidden')).toBe('false');
        expect(reopenButton?.getAttribute('aria-label'))
            .toBe('Перевести обращение обратно в возврат');
    });

    test('shows reopen action for approved exchange without explicit flag', () => {
        setupDom();

        const data = {
            id: 102,
            number: 'BY2024004',
            deliveryService: 'Belpost',
            systemStatus: 'Обмен в работе',
            history: [],
            refreshAllowed: true,
            nextRefreshAt: null,
            canEditTrack: false,
            timeZone: 'UTC',
            episodeNumber: 24,
            exchange: false, returnShipment: false,
            chain: [],
            returnRequest: {
                id: 92,
                state: 'REGISTERED_EXCHANGE',
                status: 'REGISTERED',
                statusLabel: 'Обмен согласован',
                statusBadgeClass: 'bg-warning-subtle text-warning-emphasis',
                reasonLabel: 'Причина',
                reason: 'Не подошёл размер',
                comment: 'Магазин готовится отправить замену',
                requestedAt: '2024-02-13T12:00:00Z',
                decisionAt: null,
                closedAt: null,
                reverseTrack: null,
                requiresAction: true,
                returnReceiptConfirmed: false,
                returnReceiptConfirmedAt: null,
                actionPermissions: {
                    allowAccept: false,
                    allowLaunchExchange: true,
                    allowAcceptReverse: false,
                    allowClose: false,
                    allowConvertToReturn: true,
                    allowConvertToExchange: false,
                    allowUpdateReverseTrack: false
                },
                hint: 'Уточните у покупателя детали обмена.',
                warnings: [],
                detailsUrl: null
            },
            canRegisterReturn: false,
            lifecycle: [],
            requiresAction: true
        };

        renderModal(data);

        const actionCard = Array.from(document.querySelectorAll('section.card'))
            .find((card) => card.querySelector('h6')?.textContent === 'Обращение');
        expect(actionCard).toBeDefined();

        const reopenButton = Array.from(actionCard?.querySelectorAll('button') || [])
            .find((btn) => btn.textContent?.trim() === 'Перевести в возврат');
        expect(reopenButton).toBeDefined();
        expect(reopenButton?.classList.contains('d-none')).toBe(false);
        expect(reopenButton?.getAttribute('aria-hidden')).toBe('false');
        expect(reopenButton?.getAttribute('aria-label'))
            .toBe('Перевести обращение обратно в возврат');
    });

    test('shows reopen action when permissions deny conversion but обменные признаки присутствуют', () => {
        setupDom();

        const data = {
            id: 103,
            number: 'BY2024005',
            deliveryService: 'Belpost',
            systemStatus: 'Обмен в работе',
            history: [],
            refreshAllowed: true,
            nextRefreshAt: null,
            canEditTrack: false,
            timeZone: 'UTC',
            episodeNumber: 25,
            exchange: false, returnShipment: false,
            chain: [],
            returnRequest: {
                id: 93,
                state: 'REGISTERED_EXCHANGE',
                status: 'REGISTERED',
                statusLabel: 'Обмен запрошен',
                statusBadgeClass: 'bg-warning-subtle text-warning-emphasis',
                reasonLabel: 'Причина',
                reason: 'Не подошёл фасон',
                comment: 'Ожидание решения по обмену',
                requestedAt: '2024-02-14T12:00:00Z',
                decisionAt: null,
                closedAt: null,
                reverseTrack: null,
                requiresAction: true,
                returnReceiptConfirmed: false,
                returnReceiptConfirmedAt: null,
                hint: 'Уточните у покупателя детали обмена.',
                warnings: [],
                detailsUrl: null,
                actionPermissions: {
                    allowAccept: false,
                    allowLaunchExchange: true,
                    allowAcceptReverse: false,
                    allowClose: false,
                    allowConvertToReturn: false,
                    allowConvertToExchange: false,
                    allowUpdateReverseTrack: false
                }
            },
            canRegisterReturn: false,
            lifecycle: [],
            requiresAction: true
        };

        renderModal(data);

        const actionCard = Array.from(document.querySelectorAll('section.card'))
            .find((card) => card.querySelector('h6')?.textContent === 'Обращение');
        expect(actionCard).toBeDefined();

        const reopenButton = Array.from(actionCard?.querySelectorAll('button') || [])
            .find((btn) => btn.textContent?.trim() === 'Перевести в возврат');
        expect(reopenButton).toBeDefined();
        expect(reopenButton?.classList.contains('d-none')).toBe(true);
        expect(reopenButton?.getAttribute('aria-hidden')).toBe('true');
    });

    test('hides receipt confirmation when closing without exchange is possible', () => {
        setupDom();

        const data = {
            id: 32,
            number: 'BY2024002',
            deliveryService: 'Belpost',
            systemStatus: 'Вручена',
            history: [],
            refreshAllowed: true,
            nextRefreshAt: null,
            canEditTrack: false,
            timeZone: 'UTC',
            episodeNumber: 22,
            exchange: false, returnShipment: false,
            chain: [],
            returnRequest: {
                id: 82,
                state: 'EXCHANGE_LAUNCHED',
                status: 'EXCHANGE_STARTED',
                statusLabel: 'Обмен согласован',
                statusBadgeClass: 'bg-info-subtle text-info-emphasis',
                reasonLabel: 'Причина',
                reason: 'Замена по размеру',
                comment: 'Магазин готов отправить обменную посылку',
                requestedAt: '2024-02-11T12:00:00Z',
                decisionAt: null,
                closedAt: null,
                reverseTrack: null,
                requiresAction: true,
                returnReceiptConfirmed: false,
                returnReceiptConfirmedAt: null,
                actionPermissions: {
                    allowAccept: false,
                    allowLaunchExchange: false,
                    allowAcceptReverse: false,
                    allowClose: true,
                    allowConvertToReturn: false,
                    allowConvertToExchange: false,
                    allowUpdateReverseTrack: false
                },
                hint: 'Можно закрыть заявку без обмена, если товара нет.',
                warnings: [],
                detailsUrl: 'https://example.com/returns'
            },
            canRegisterReturn: false,
            lifecycle: [],
            requiresAction: true
        };

        renderModal(data);

        const actionCard = Array.from(document.querySelectorAll('section.card'))
            .find((card) => card.querySelector('h6')?.textContent === 'Обращение');
        expect(actionCard).toBeDefined();

        const confirmButton = Array.from(actionCard?.querySelectorAll('button') || [])
            .find((btn) => btn.getAttribute('aria-label') === 'Подтвердить получение обратной посылки');
        expect(confirmButton).toBeDefined();
        expect(confirmButton?.classList.contains('d-none')).toBe(true);
        expect(confirmButton?.getAttribute('aria-hidden')).toBe('true');

        const closeButton = Array.from(actionCard?.querySelectorAll('button') || [])
            .find((btn) => {
                const text = btn.textContent || '';
                return text === 'Отменить обмен' || text === 'Закрыть обращение';
            });
        expect(closeButton).toBeDefined();
    });

    test('marks return shipment in chain label and aria text', () => {
        setupDom();
        const data = {
            id: 21,
            number: 'RR123456789BY',
            deliveryService: 'Belpost',
            systemStatus: 'Возвращается',
            history: [],
            refreshAllowed: false,
            nextRefreshAt: null,
            canEditTrack: false,
            timeZone: 'UTC',
            episodeNumber: 13,
            exchange: false, returnShipment: true,
            chain: [
                { id: 21, number: 'RR123456789BY', exchange: false, returnShipment: true, current: true }
            ],
            returnRequest: null,
            canRegisterReturn: false,
            lifecycle: [
                {
                    code: 'OUTBOUND',
                    title: 'Отправление магазина',
                    actor: 'Магазин',
                    description: 'Магазин отправил посылку.',
                    state: 'COMPLETED',
                    occurredAt: '2024-01-01T10:00:00Z',
                    trackNumber: 'RR123456789BY',
                    trackContext: 'Исходная посылка'
                },
                {
                    code: 'OUTBOUND_SENT',
                    title: 'Возврат отправлен',
                    actor: 'Покупатель',
                    description: '...',
                    state: 'COMPLETED',
                    occurredAt: '2024-01-05T12:00:00Z',
                    trackNumber: 'RR123456789BY',
                    trackContext: 'Обратный трек'
                }
            ],
            requiresAction: false
        };

        renderModal(data);

        const button = document.querySelector('button.track-chain__item');
        expect(button).not.toBeNull();
        expect(button?.textContent).toContain('· возврат');
        expect(button?.getAttribute('aria-label')).toContain('возвратная посылка');
    });

    test('submits reverse track form and rerenders modal', async () => {
        setupDom();

        const updatedDetails = {
            id: 12,
            number: 'BY123456789BY',
            deliveryService: 'Belpost',
            systemStatus: 'Вручена',
            history: [],
            refreshAllowed: true,
            nextRefreshAt: null,
            canEditTrack: false,
            timeZone: 'UTC',
            episodeNumber: 5,
            exchange: false, returnShipment: false,
            chain: [],
            returnRequest: {
                id: 5,
                state: 'REGISTERED_RETURN',
status: 'Зарегистрирована',
                statusLabel: 'Зарегистрирована',
                statusBadgeClass: 'bg-warning-subtle text-warning-emphasis',
                reasonLabel: 'Причина',
                reason: 'Размер не подошёл',
                comment: 'Обновлённый комментарий',
                requestedAt: '2024-02-02T10:00:00Z',
                decisionAt: null,
                closedAt: null,
                reverseTrack: 'RR123456789BY',
                requiresAction: false,
                exchangeApproved: false,
                exchangeRequested: false,
                canStartExchange: true,
                canCreateExchangeParcel: false,
                canCloseWithoutExchange: true,
                canReopenAsReturn: false,
                returnReceiptConfirmed: false,
                returnReceiptConfirmedAt: null,
                canConfirmReceipt: true,
                hint: 'Возврат подтверждён, проверьте трек.',
                warnings: [],
                detailsUrl: 'https://example.com/returns'
            },
            canRegisterReturn: false,
            lifecycle: [],
            requiresAction: false
        };

        const initialData = {
            id: 12,
            number: 'BY123456789BY',
            deliveryService: 'Belpost',
            systemStatus: 'Вручена',
            history: [],
            refreshAllowed: true,
            nextRefreshAt: null,
            canEditTrack: false,
            timeZone: 'UTC',
            episodeNumber: 5,
            exchange: false, returnShipment: false,
            chain: [],
            returnRequest: {
                id: 5,
                state: 'REGISTERED_RETURN',
status: 'Зарегистрирована',
                statusLabel: 'Зарегистрирована',
                statusBadgeClass: 'bg-warning-subtle text-warning-emphasis',
                reasonLabel: 'Причина',
                reason: 'Размер не подошёл',
                comment: 'Свяжитесь со мной',
                requestedAt: '2024-02-02T10:00:00Z',
                decisionAt: null,
                closedAt: null,
                reverseTrack: null,
                requiresAction: true,
                exchangeApproved: false,
                exchangeRequested: false,
                canStartExchange: true,
                canCreateExchangeParcel: false,
                canCloseWithoutExchange: true,
                canReopenAsReturn: false,
                hint: 'Укажите обратный трек для ускорения обработки.',
                warnings: [],
                detailsUrl: 'https://example.com/returns'
            },
            canRegisterReturn: false,
            lifecycle: [],
            requiresAction: true
        };

        renderModal(initialData);

        const headers = { get: jest.fn(() => 'application/json') };
        global.fetch
            .mockResolvedValueOnce({
                ok: true,
                headers,
                json: () => Promise.resolve({ details: updatedDetails })
            })
            .mockResolvedValueOnce({
                ok: true,
                headers,
                json: () => Promise.resolve(updatedDetails)
            });

        const form = document.querySelector('form[data-reverse-track-form]');
        expect(form).not.toBeNull();
        const input = form.querySelector('input[name="reverseTrack"]');
        expect(input).not.toBeNull();
        input.value = ' rr123456789by ';

        const commentField = form.querySelector('textarea[name="comment"]');
        expect(commentField).not.toBeNull();
        expect(commentField.value).toBe('Свяжитесь со мной');
        commentField.value = '  Обновлённый комментарий  ';

        form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

        await Promise.resolve();
        await Promise.resolve();
        await new Promise((resolve) => setTimeout(resolve, 0));

        const requestCall = global.fetch.mock.calls.find((call) => String(call[0]).includes('/api/v1/returns/5/commands'));
        const requestDto = readCommandRequestDto(requestCall);
        expectValidIdempotencyKey(requestDto.idempotencyKey);
        expect(requestDto.action).toBe('UPDATE_REVERSE_TRACK');
        expect(requestDto.payload).toEqual({
            reverseTrack: 'RR123456789BY',
            comment: 'Обновлённый комментарий'
        });
        expect(global.notifyUser).toHaveBeenCalledWith('Обратный трек сохранён', 'success');
        const reverseInfo = Array.from(document.querySelectorAll('dl dd'))
            .find((node) => node.textContent?.includes('RR123456789BY'));
        expect(reverseInfo).toBeDefined();
        const commentInfo = Array.from(document.querySelectorAll('dl dd'))
            .find((node) => node.textContent?.includes('Обновлённый комментарий'));
        expect(commentInfo).toBeDefined();
        expect(global.window.returnRequests.updateRow).toHaveBeenCalledWith(expect.objectContaining({
            parcelId: 12,
            requestId: 5,
            reverseTrack: 'RR123456789BY',
            comment: 'Обновлённый комментарий'
        }));
    });

    test('confirms return receipt via API and updates table row', async () => {
        setupDom();

        const payload = normalizeDetails({
            id: 14,
            number: 'BY000',
            deliveryService: 'Belpost',
            systemStatus: 'В пути',
            history: [],
            refreshAllowed: true,
            nextRefreshAt: null,
            canEditTrack: false,
            timeZone: 'UTC',
            episodeNumber: 3,
            exchange: false, returnShipment: false,
            chain: [],
            returnRequest: {
                id: 6,
                state: 'REGISTERED_RETURN',
status: 'Зарегистрирована',
                statusLabel: 'Зарегистрирована',
                statusBadgeClass: 'bg-success-subtle text-success-emphasis',
                reasonLabel: 'Причина',
                reason: 'Брак',
                comment: null,
                requestedAt: '2024-02-02T10:00:00Z',
                decisionAt: null,
                closedAt: null,
                reverseTrack: null,
                requiresAction: true,
                exchangeApproved: false,
                exchangeRequested: false,
                canStartExchange: false,
                canCloseWithoutExchange: true,
                canReopenAsReturn: false,
                returnReceiptConfirmed: true,
                returnReceiptConfirmedAt: '2024-03-01T09:00:00Z',
                canConfirmReceipt: false,
                hint: 'Покупатель уже подтвердил возврат.',
                warnings: [],
                detailsUrl: 'https://example.com/returns'
            },
            canRegisterReturn: false,
            lifecycle: [],
            requiresAction: false
        });

        global.fetch
            .mockResolvedValueOnce({
                ok: true,
                headers: { get: () => 'application/json' },
                json: () => Promise.resolve(payload)
            })
            .mockResolvedValueOnce({
                ok: true,
                headers: { get: () => 'application/json' },
                json: () => Promise.resolve(payload)
            });

        await global.window.trackModal.confirmReturnProcessing(14, 6, {});

        expect(global.window.returnRequests.updateRow).toHaveBeenCalledWith(expect.objectContaining({
            parcelId: 14,
            requestId: 6,
            returnReceiptConfirmed: true,
            returnReceiptConfirmedAt: '2024-03-01T09:00:00Z'
        }));
        expect(global.notifyUser).toHaveBeenCalledWith('Возврат подтверждён', 'success');
    });

    test('renders return without exchange as single current item', () => {
        setupDom();
        const data = {
            id: 5,
            number: 'BY555555555BY',
            deliveryService: 'Belpost',
            systemStatus: 'Возвращена',
            history: [],
            refreshAllowed: false,
            nextRefreshAt: null,
            canEditTrack: false,
            timeZone: 'UTC',
            episodeNumber: 101,
            exchange: false, returnShipment: false,
            chain: [
                { id: 5, number: 'BY555555555BY', exchange: false, returnShipment: false, current: true }
            ],
            returnRequest: null,
            canRegisterReturn: false,
            lifecycle: [],
            requiresAction: false
        };

        renderModal(data);

        const buttons = document.querySelectorAll('button.track-chain__item');
        expect(buttons).toHaveLength(1);
        expect(buttons[0].textContent).not.toContain('обмен');
        expect(buttons[0].getAttribute('aria-current')).toBe('true');
    });

    test('keeps lifecycle card collapsed when only outbound stage is provided', async () => {
        setupDom();
        const data = {
            id: 9,
            number: 'BY000000000BY',
            deliveryService: 'Belpost',
            systemStatus: 'Подготовка',
            history: [],
            refreshAllowed: true,
            nextRefreshAt: null,
            canEditTrack: true,
            timeZone: 'UTC',
            episodeNumber: 202,
            exchange: false, returnShipment: false,
            chain: [
                { id: 9, number: 'BY000000000BY', exchange: false, returnShipment: false, current: true }
            ],
            returnRequest: null,
            canRegisterReturn: true,
            lifecycle: [
                {
                    code: 'OUTBOUND',
                    title: 'Отправление магазина',
                    actor: 'Магазин',
                    description: '...',
                    state: 'IN_PROGRESS',
                    occurredAt: null,
                    trackNumber: 'BY000000000BY',
                    trackContext: 'Исходная посылка'
                }
            ],
            requiresAction: false
        };

        renderModal(data);

        const lifecycleCard = Array.from(document.querySelectorAll('section.card'))
            .find((card) => card.querySelector('h6')?.textContent === 'Жизненный цикл заказа');
        expect(lifecycleCard).toBeDefined();
        const toggle = lifecycleCard?.querySelector('button');
        toggle?.click();
        await Promise.resolve();
        await Promise.resolve();
        const lifecycleList = lifecycleCard?.querySelector('ol[role="list"]');
        expect(lifecycleList).toBeNull();
    });

    test('approves exchange via action button', async () => {
        setupDom();
        const headers = { get: jest.fn(() => 'application/json') };
        const responsePayload = {
            id: 12,
            number: 'BY123',
            deliveryService: null,
            systemStatus: 'Вручена',
            history: [],
            refreshAllowed: true,
            nextRefreshAt: null,
            canEditTrack: false,
            timeZone: 'UTC',
            episodeNumber: 5,
            exchange: false, returnShipment: false,
            chain: [],
            returnRequest: null,
            canRegisterReturn: false,
            lifecycle: [],
            requiresAction: false
        };
        global.fetch.mockResolvedValueOnce({ ok: true, headers, json: () => Promise.resolve(responsePayload) });

        const data = {
            id: 12,
            number: 'BY123',
            deliveryService: null,
            systemStatus: 'Вручена',
            history: [],
            refreshAllowed: true,
            nextRefreshAt: null,
            canEditTrack: false,
            timeZone: 'UTC',
            episodeNumber: 5,
            exchange: false, returnShipment: false,
            chain: [],
            returnRequest: {
                id: 5,
                state: 'REGISTERED_RETURN',
                status: 'REGISTERED',
                statusLabel: 'Зарегистрирована',
                statusBadgeClass: 'bg-warning-subtle text-warning-emphasis',
                reasonLabel: 'Причина',
                reason: 'Размер не подошёл',
                comment: 'Требуется обмен',
                requestedAt: '2024-02-10T09:00:00Z',
                decisionAt: null,
                closedAt: null,
                reverseTrack: null,
                requiresAction: true,
                actionPermissions: {
                    allowAccept: false,
                    allowLaunchExchange: false,
                    allowAcceptReverse: false,
                    allowClose: false,
                    allowConvertToReturn: false,
                    allowConvertToExchange: true,
                    allowUpdateReverseTrack: false
                },
                returnReceiptConfirmed: false,
                returnReceiptConfirmedAt: null,
                hint: 'Переведите заявку, чтобы запустить обмен.',
                warnings: [],
                detailsUrl: 'https://example.com/returns'
            },
            canRegisterReturn: false,
            lifecycle: [],
            requiresAction: true
        };

        renderModal(data);

        const actionCard = Array.from(document.querySelectorAll('section.card'))
            .find((card) => card.querySelector('h6')?.textContent === 'Обращение');
        const actionButtons = Array.from(actionCard?.querySelectorAll('button') || [])
            .map((btn) => btn.textContent?.trim());
        expect(actionButtons).toContain('Перевести в обмен');
        const approveButton = Array.from(actionCard?.querySelectorAll('button') || [])
            .find((btn) => btn.textContent?.trim() === 'Перевести в обмен');
        expect(approveButton).toBeDefined();
        approveButton?.click();

        await Promise.resolve();
        await Promise.resolve();
        await Promise.resolve();
        await new Promise((resolve) => setTimeout(resolve, 0));

        const requestCall = global.fetch.mock.calls.find((call) => String(call[0]).includes('/api/v1/returns/5/commands'));
        const requestDto = readCommandRequestDto(requestCall);
        expectValidIdempotencyKey(requestDto.idempotencyKey);
        expect(requestDto.action).toBe('SET_MODE_EXCHANGE');
        expect(requestDto.payload).toEqual({});
        expect(global.notifyUser).toHaveBeenCalledWith('Заявка переведена в обмен', 'info');
    });

    test('starts exchange via dedicated action', async () => {
        setupDom();

        const headers = { get: jest.fn(() => 'application/json') };
        const responsePayload = { id: 6, state: { stage: 'REGISTERED_EXCHANGE' } };
        global.fetch.mockImplementation((url) => {
            if (String(url).includes('/api/v1/returns/6/commands')) {
                return Promise.resolve({ ok: true, headers, json: () => Promise.resolve(responsePayload) });
            }
            if (String(url).includes('/api/v1/tracks/14')) {
                return Promise.resolve({ ok: true, headers, json: () => Promise.resolve({ returnRequest: responsePayload }) });
            }
            return Promise.resolve({ ok: true, headers, json: () => Promise.resolve({}) });
        });

        const data = {
            id: 14,
            number: 'BY555',
            deliveryService: null,
            systemStatus: 'Вручена',
            history: [],
            refreshAllowed: true,
            nextRefreshAt: null,
            canEditTrack: false,
            timeZone: 'UTC',
            episodeNumber: 8,
            exchange: false, returnShipment: false,
            chain: [],
            returnRequest: {
                id: 6,
                state: 'REGISTERED_EXCHANGE',
                status: 'REGISTERED',
                statusLabel: 'Зарегистрирована',
                statusBadgeClass: 'bg-info-subtle text-info-emphasis',
                reasonLabel: 'Причина',
                reason: 'Брак',
                comment: 'Согласован обмен',
                requestedAt: '2024-02-11T11:00:00Z',
                decisionAt: '2024-02-12T11:00:00Z',
                closedAt: null,
                reverseTrack: null,
                requiresAction: true,
                actionPermissions: {
                    allowAccept: false,
                    allowLaunchExchange: true,
                    allowAcceptReverse: false,
                    allowClose: false,
                    allowConvertToReturn: true,
                    allowConvertToExchange: false,
                    allowUpdateReverseTrack: false
                },
                returnReceiptConfirmed: true,
                returnReceiptConfirmedAt: '2024-02-01T10:00:00Z',
                hint: 'Создайте обменную посылку, чтобы завершить процесс.',
                warnings: [],
                detailsUrl: 'https://example.com/returns'
            },
            canRegisterReturn: false,
            lifecycle: [],
            requiresAction: true
        };

        renderModal(data);

        global.fetch.mockClear();
        global.fetch.mockResolvedValueOnce({
            ok: true,
            headers: { get: () => 'application/json' },
            json: () => Promise.resolve({
                details: normalizeDetails(data),
                actionRequired: null
            })
        });

        const card = Array.from(document.querySelectorAll('section.card'))
            .find((item) => item.querySelector('h6')?.textContent === 'Обращение');
        const convertButton = Array.from(card?.querySelectorAll('button') || [])
            .find((btn) => btn.getAttribute('aria-label') === 'Запустить обмен по обращению');
        expect(convertButton).toBeDefined();
        convertButton?.click();

        await Promise.resolve();
        await Promise.resolve();
        await Promise.resolve();
        await new Promise((resolve) => setTimeout(resolve, 0));

        const requestCall = global.fetch.mock.calls.find((call) => String(call[0]).includes('/api/v1/returns/6/commands'));
        const requestDto = readCommandRequestDto(requestCall);
        expectValidIdempotencyKey(requestDto.idempotencyKey);
        expect(requestDto.action).toBe('REGISTER_EXCHANGE_PARCEL');
        expectIsoTimestamp(requestDto.payload.stageMoment);
        expect(global.notifyUser).toHaveBeenCalledWith('Обмен запущен', 'info');
    });

    test('reopens exchange into return mode via dedicated button', async () => {
        setupDom();

        const exchangeDetails = ReturnRequestTestFixtureFactory.createDetailsWithAllActions({ mode: 'EXCHANGE' });
        const refreshedDetails = ReturnRequestTestFixtureFactory.createDetailsWithAllActions({
            mode: 'RETURN',
            overrides: {
                id: exchangeDetails.id,
                number: exchangeDetails.number,
                returnRequest: {
                    id: exchangeDetails.returnRequest.id,
                    stage: 'REGISTERED_RETURN',
                    state: {
                        ...exchangeDetails.returnRequest.state,
                        mode: 'RETURN',
                        stage: 'REGISTERED_RETURN'
                    }
                }
            }
        });
        const driver = new ReturnRequestActionTestDriver(exchangeDetails);
        driver.mockSuccessfulCommandFlow({
            requestDto: { id: 15, status: 'QUEUED' },
            refreshedDetails
        });

        driver.render();
        await flushAsyncQueue();
        await driver.clickAction('Перевести в возврат');

        const commandCall = driver.getCommandFetchCall();
        expect(commandCall).toBeDefined();
        expect(commandCall?.[1]).toEqual(expect.objectContaining({ method: 'POST' }));
        const requestDto = readCommandRequestDto(commandCall);
        expectValidIdempotencyKey(requestDto.idempotencyKey);
        expect(requestDto.action).toBe('SET_MODE_RETURN');
        expect(requestDto.payload).toEqual({});
        expect(global.notifyUser).toHaveBeenCalledWith('Заявка переведена в возврат', 'info');
    });

    test('launches exchange via register action button with full DTO', async () => {
        setupDom();

        const returnDetails = ReturnRequestTestFixtureFactory.createDetailsWithAllActions();
        const refreshedDetails = ReturnRequestTestFixtureFactory.createDetailsWithAllActions({
            mode: 'EXCHANGE',
            overrides: {
                id: returnDetails.id,
                number: returnDetails.number,
                returnRequest: {
                    id: returnDetails.returnRequest.id,
                    stage: 'REGISTERED_EXCHANGE',
                    state: {
                        ...returnDetails.returnRequest.state,
                        mode: 'EXCHANGE',
                        stage: 'REGISTERED_EXCHANGE'
                    }
                }
            }
        });
        const driver = new ReturnRequestActionTestDriver(returnDetails);
        driver.mockSuccessfulCommandFlow({
            requestDto: { id: 16, status: 'QUEUED' },
            refreshedDetails
        });

        driver.render();
        await flushAsyncQueue();
        await driver.clickAction('Запустить обмен');

        const commandCall = driver.getCommandFetchCall();
        expect(commandCall?.[1]).toEqual(expect.objectContaining({ method: 'POST' }));
        const requestDto = readCommandRequestDto(commandCall);
        expectValidIdempotencyKey(requestDto.idempotencyKey);
        expect(requestDto.action).toBe('REGISTER_EXCHANGE_PARCEL');
        expectIsoTimestamp(requestDto.payload.stageMoment);
        expect(global.notifyUser).toHaveBeenCalledWith('Обмен запущен', 'info');
    });

    test('marks outbound parcel as sent via return action button', async () => {
        setupDom();

        const returnDetails = ReturnRequestTestFixtureFactory.createDetailsWithAllActions();
        disableExchangeLaunch(returnDetails);
        const refreshedDetails = ReturnRequestTestFixtureFactory.createDetailsWithAllActions({
            mode: 'RETURN',
            overrides: {
                id: returnDetails.id,
                number: returnDetails.number,
                returnRequest: {
                    id: returnDetails.returnRequest.id,
                    stage: 'OUTBOUND_SENT',
                    state: {
                        ...returnDetails.returnRequest.state,
                        stage: 'OUTBOUND_SENT'
                    }
                }
            }
        });
        disableExchangeLaunch(refreshedDetails);
        const driver = new ReturnRequestActionTestDriver(returnDetails);
        driver.mockSuccessfulCommandFlow({
            requestDto: { id: 17, status: 'QUEUED' },
            refreshedDetails
        });

        driver.render();
        await flushAsyncQueue();
        await driver.clickAction('Отправка возврата');

        const commandCall = driver.getCommandFetchCall();
        const requestDto = readCommandRequestDto(commandCall);
        expectValidIdempotencyKey(requestDto.idempotencyKey);
        expect(requestDto.action).toBe('MARK_OUTBOUND_SENT');
        expectIsoTimestamp(requestDto.payload.stageMoment);
        expect(global.notifyUser).toHaveBeenCalledWith('Отправка возвратной посылки отмечена', 'info');
    });

    test('marks inbound arrival via return action button', async () => {
        setupDom();

        const returnDetails = ReturnRequestTestFixtureFactory.createDetailsWithAllActions();
        disableExchangeLaunch(returnDetails);
        const refreshedDetails = ReturnRequestTestFixtureFactory.createDetailsWithAllActions({
            mode: 'RETURN',
            overrides: {
                id: returnDetails.id,
                number: returnDetails.number,
                returnRequest: {
                    id: returnDetails.returnRequest.id,
                    stage: 'INBOUND_ARRIVED',
                    state: {
                        ...returnDetails.returnRequest.state,
                        stage: 'INBOUND_ARRIVED'
                    }
                }
            }
        });
        disableExchangeLaunch(refreshedDetails);
        const driver = new ReturnRequestActionTestDriver(returnDetails);
        driver.mockSuccessfulCommandFlow({
            requestDto: { id: 18, status: 'QUEUED' },
            refreshedDetails
        });

        driver.render();
        await flushAsyncQueue();
        await driver.clickAction('Возврат на складе');

        const commandCall = driver.getCommandFetchCall();
        const requestDto = readCommandRequestDto(commandCall);
        expectValidIdempotencyKey(requestDto.idempotencyKey);
        expect(requestDto.action).toBe('MARK_INBOUND_ARRIVED');
        expectIsoTimestamp(requestDto.payload.stageMoment);
        expect(global.notifyUser).toHaveBeenCalledWith('Прибытие возвратной посылки отмечено', 'info');
    });

    test('confirms return via dedicated button with new payload', async () => {
        setupDom();

        const returnDetails = ReturnRequestTestFixtureFactory.createDetailsWithAllActions();
        disableExchangeLaunch(returnDetails);
        const refreshedDetails = ReturnRequestTestFixtureFactory.createDetailsWithAllActions({
            mode: 'RETURN',
            overrides: {
                id: returnDetails.id,
                number: returnDetails.number,
                returnRequest: {
                    id: returnDetails.returnRequest.id,
                    stage: 'INBOUND_PICKED_UP',
                    state: {
                        ...returnDetails.returnRequest.state,
                        stage: 'INBOUND_PICKED_UP'
                    },
                    returnReceiptConfirmed: true,
                    returnReceiptConfirmedAt: '2024-03-05T12:00:00Z'
                }
            }
        });
        disableExchangeLaunch(refreshedDetails);
        const driver = new ReturnRequestActionTestDriver(returnDetails);
        driver.mockSuccessfulCommandFlow({
            requestDto: { id: 19, status: 'QUEUED' },
            refreshedDetails
        });

        driver.render();
        await flushAsyncQueue();
        await driver.clickAction('Принять возврат');

        const commandCall = driver.getCommandFetchCall();
        const requestDto = readCommandRequestDto(commandCall);
        expectValidIdempotencyKey(requestDto.idempotencyKey);
        expect(requestDto.action).toBe('MARK_INBOUND_PICKED_UP');
        expectIsoTimestamp(requestDto.payload.stageMoment);
        expect(global.notifyUser).toHaveBeenCalledWith('Возврат подтверждён', 'success');
    });

    test('marks exchange shipment as sent via dedicated button', async () => {
        setupDom();

        const exchangeDetails = ReturnRequestTestFixtureFactory.createDetailsWithAllActions({ mode: 'EXCHANGE' });
        const refreshedDetails = ReturnRequestTestFixtureFactory.createDetailsWithAllActions({
            mode: 'EXCHANGE',
            overrides: {
                id: exchangeDetails.id,
                number: exchangeDetails.number,
                returnRequest: {
                    id: exchangeDetails.returnRequest.id,
                    stage: 'EXCHANGE_SENT',
                    state: {
                        ...exchangeDetails.returnRequest.state,
                        stage: 'EXCHANGE_SENT'
                    }
                }
            }
        });
        const driver = new ReturnRequestActionTestDriver(exchangeDetails);
        driver.mockSuccessfulCommandFlow({
            requestDto: { id: 20, status: 'QUEUED' },
            refreshedDetails
        });

        driver.render();
        await flushAsyncQueue();
        await driver.clickAction('Отправка обмена');

        const commandCall = driver.getCommandFetchCall();
        const requestDto = readCommandRequestDto(commandCall);
        expectValidIdempotencyKey(requestDto.idempotencyKey);
        expect(requestDto.action).toBe('MARK_EXCHANGE_SENT');
        expectIsoTimestamp(requestDto.payload.stageMoment);
        expect(global.notifyUser).toHaveBeenCalledWith('Отправка обменной посылки отмечена', 'info');
    });

    test('marks exchange delivery via dedicated button', async () => {
        setupDom();

        const exchangeDetails = ReturnRequestTestFixtureFactory.createDetailsWithAllActions({ mode: 'EXCHANGE' });
        const refreshedDetails = ReturnRequestTestFixtureFactory.createDetailsWithAllActions({
            mode: 'EXCHANGE',
            overrides: {
                id: exchangeDetails.id,
                number: exchangeDetails.number,
                returnRequest: {
                    id: exchangeDetails.returnRequest.id,
                    stage: 'EXCHANGE_DELIVERED',
                    state: {
                        ...exchangeDetails.returnRequest.state,
                        stage: 'EXCHANGE_DELIVERED'
                    }
                }
            }
        });
        const driver = new ReturnRequestActionTestDriver(exchangeDetails);
        driver.mockSuccessfulCommandFlow({
            requestDto: { id: 21, status: 'QUEUED' },
            refreshedDetails
        });

        driver.render();
        await flushAsyncQueue();
        await driver.clickAction('Доставка обмена');

        const commandCall = driver.getCommandFetchCall();
        const requestDto = readCommandRequestDto(commandCall);
        expectValidIdempotencyKey(requestDto.idempotencyKey);
        expect(requestDto.action).toBe('MARK_EXCHANGE_DELIVERED');
        expectIsoTimestamp(requestDto.payload.stageMoment);
        expect(global.notifyUser).toHaveBeenCalledWith('Доставка обменной посылки отмечена', 'success');
    });

    test('shows exchange parcel widget with open CTA', () => {
        setupDom();
        const loadSpy = jest.spyOn(global.window.trackModal, 'loadModal').mockImplementation(() => {});
        const data = {
            id: 18,
            number: 'BY777',
            deliveryService: 'Belpost',
            systemStatus: 'Вручена',
            history: [],
            refreshAllowed: true,
            nextRefreshAt: null,
            canEditTrack: false,
            timeZone: 'UTC',
            episodeNumber: 9,
            exchange: false, returnShipment: false,
            chain: [],
            exchangeParcel: { id: 77, number: 'EX777', statusLabel: 'В пути' },
            returnRequest: {
                id: 9,
                state: 'REGISTERED_RETURN',
status: 'Обмен запускается',
                statusLabel: 'Обмен запускается',
                statusBadgeClass: 'bg-info-subtle text-info-emphasis',
                reasonLabel: 'Причина',
                reason: 'Не подошёл размер',
                comment: 'Попросили обмен',
                requestedAt: '2024-02-15T12:00:00Z',
                decisionAt: null,
                closedAt: null,
                reverseTrack: null,
                requiresAction: true,
                exchangeApproved: true,
                exchangeRequested: true,
                canStartExchange: false,
                canCreateExchangeParcel: false,
                canCloseWithoutExchange: false,
                canReopenAsReturn: true,
                returnReceiptConfirmed: true,
                returnReceiptConfirmedAt: '2024-02-16T12:00:00Z',
                canConfirmReceipt: false,
                hint: 'Обменная посылка готова к отправке.',
                warnings: ['Проверьте состав вложения.'],
                detailsUrl: 'https://example.com/returns'
            },
            canRegisterReturn: false,
            lifecycle: [],
            requiresAction: true
        };

        renderModal(data);

        const card = Array.from(document.querySelectorAll('section.card'))
            .find((item) => item.querySelector('h6')?.textContent === 'Обращение');
        const exchangeAlert = card?.querySelector('.alert.alert-info');
        expect(exchangeAlert?.textContent).toContain('EX777');
        expect(exchangeAlert?.textContent).toContain('В пути');
        const openButton = exchangeAlert?.querySelector('button');
        expect(openButton).toBeDefined();
        openButton?.click();
        expect(loadSpy).toHaveBeenCalledWith(77);
    });

    test('forces exchange labels when flags indicate exchange mode', () => {
        setupDom();
        const data = {
            id: 31,
            number: 'BY999',
            deliveryService: 'Belpost',
            systemStatus: 'В пути',
            history: [],
            refreshAllowed: true,
            nextRefreshAt: null,
            canEditTrack: false,
            timeZone: 'UTC',
            episodeNumber: 10,
            exchange: false,
            returnShipment: false,
            chain: [
                { id: 31, number: 'BY999', exchange: false, returnShipment: false, current: true }
            ],
            returnRequest: {
                id: 11,
                state: 'REGISTERED_EXCHANGE',
                status: 'REGISTERED',
                statusLabel: 'Возврат',
                statusBadgeClass: 'bg-info-subtle text-info-emphasis',
                reasonLabel: 'Причина',
                reason: 'Не подошёл размер',
                comment: null,
                requestedAt: '2024-02-20T10:00:00Z',
                decisionAt: null,
                closedAt: null,
                reverseTrack: null,
                requiresAction: true,
                actionPermissions: {
                    allowAccept: false,
                    allowLaunchExchange: true,
                    allowAcceptReverse: false,
                    allowClose: false,
                    allowConvertToReturn: false,
                    allowConvertToExchange: false,
                    allowUpdateReverseTrack: false
                },
                returnReceiptConfirmed: false,
                returnReceiptConfirmedAt: null,
                hint: null,
                warnings: []
            },
            canRegisterReturn: false,
            lifecycle: [],
            requiresAction: true
        };

        renderModal(data);

        const returnCard = Array.from(document.querySelectorAll('section.card'))
            .find((card) => card.querySelector('h6')?.textContent === 'Обращение');
        const badges = Array.from(returnCard?.querySelectorAll('.badge.rounded-pill') || []);
        expect(badges[0]?.textContent).toBe('Обмен');
        expect(badges[1]?.textContent).toBe('Обмен зарегистрирован');

        const definitions = returnCard?.querySelector('dl');
        expect(definitions?.textContent).toContain('Тип обращения');
        expect(definitions?.textContent).toContain('Обмен');
    });

    test('auto expands history for active return request', async () => {
        setupDom();
        const data = {
            id: 21,
            number: 'BY111',
            deliveryService: 'Belpost',
            systemStatus: 'В пути',
            history: [],
            refreshAllowed: true,
            nextRefreshAt: null,
            canEditTrack: false,
            timeZone: 'UTC',
            episodeNumber: 33,
            exchange: false, returnShipment: false,
            chain: [
                { id: 21, number: 'BY111', exchange: false, returnShipment: false, current: true }
            ],
            returnRequest: {
                id: 3,
                state: 'REGISTERED_RETURN',
status: 'Зарегистрирована',
                reason: 'Размер не подошёл',
                comment: null,
                requestedAt: '2024-02-01T10:00:00Z',
                decisionAt: null,
                closedAt: null,
                reverseTrack: null,
                requiresAction: true,
                exchangeApproved: false,
                exchangeRequested: false,
                canStartExchange: true,
                canCreateExchangeParcel: false,
                canCloseWithoutExchange: true,
                canReopenAsReturn: false,
                returnReceiptConfirmed: false,
                returnReceiptConfirmedAt: null,
                canConfirmReceipt: true
            },
            canRegisterReturn: false,
            lifecycle: [],
            requiresAction: true
        };

        renderModal(data);

        await Promise.resolve();

        const historyCard = Array.from(document.querySelectorAll('section.card'))
            .find((card) => card.querySelector('h6')?.textContent === 'История трека');
        const toggle = historyCard?.querySelector('button');
        const content = historyCard?.querySelector('.track-lazy-section__content');
        expect(toggle?.getAttribute('aria-expanded')).toBe('true');
        expect(content?.classList.contains('d-none')).toBe(false);
    });

    test('keeps history collapsed when there is no active appeal', async () => {
        setupDom();
        const data = {
            id: 22,
            number: 'BY222',
            deliveryService: 'Belpost',
            systemStatus: 'Доставлена',
            history: [],
            refreshAllowed: true,
            nextRefreshAt: null,
            canEditTrack: false,
            timeZone: 'UTC',
            episodeNumber: 34,
            exchange: false, returnShipment: false,
            chain: [
                { id: 22, number: 'BY222', exchange: false, returnShipment: false, current: true }
            ],
            returnRequest: {
                id: 4,
                state: 'REGISTERED_RETURN',
status: 'Закрыта',
                reason: 'Не подошло',
                comment: 'Возврат завершён',
                requestedAt: '2024-01-01T10:00:00Z',
                decisionAt: '2024-01-05T10:00:00Z',
                closedAt: '2024-01-06T10:00:00Z',
                reverseTrack: 'RR123456',
                requiresAction: false,
                exchangeApproved: false,
                exchangeRequested: false,
                canStartExchange: false,
                canCreateExchangeParcel: false,
                canCloseWithoutExchange: false,
                canReopenAsReturn: false,
                returnReceiptConfirmed: true,
                returnReceiptConfirmedAt: '2024-01-06T11:00:00Z',
                canConfirmReceipt: false
            },
            canRegisterReturn: false,
            lifecycle: [],
            requiresAction: false
        };

        renderModal(data);

        await Promise.resolve();

        const historyCard = Array.from(document.querySelectorAll('section.card'))
            .find((card) => card.querySelector('h6')?.textContent === 'История трека');
        const toggle = historyCard?.querySelector('button');
        const content = historyCard?.querySelector('.track-lazy-section__content');
        expect(toggle?.getAttribute('aria-expanded')).toBe('false');
        expect(content?.classList.contains('d-none')).toBe(true);
    });

    test('shows register button when return can be created', async () => {
        setupDom();
        const data = {
            id: 7,
            number: 'RR123',
            deliveryService: 'Belpost',
            systemStatus: 'Вручена',
            history: [],
            refreshAllowed: false,
            nextRefreshAt: null,
            canEditTrack: false,
            timeZone: 'UTC',
            episodeNumber: 12,
            exchange: false, returnShipment: false,
            chain: [
                { id: 7, number: 'RR123', exchange: false, returnShipment: false, current: true }
            ],
            returnRequest: null,
            canRegisterReturn: true,
            lifecycle: [
                {
                    code: 'OUTBOUND',
                    title: 'Отправление магазина',
                    actor: 'Магазин',
                    description: '...',
                    state: 'COMPLETED',
                    occurredAt: '2024-01-01T09:00:00Z',
                    trackNumber: 'RR123',
                    trackContext: 'Исходная посылка'
                },
                {
                    code: 'NEW',
                    title: 'Заявка зарегистрирована',
                    actor: 'Покупатель',
                    description: '...',
                    state: 'PLANNED',
                    occurredAt: null,
                    trackNumber: null,
                    trackContext: null
                },
                {
                    code: 'OUTBOUND_SENT',
                    title: 'Возврат отправлен',
                    actor: 'Покупатель',
                    description: '...',
                    state: 'PLANNED',
                    occurredAt: null,
                    trackNumber: null,
                    trackContext: null
                },
                {
                    code: 'INBOUND_ARRIVED',
                    title: 'Возврат прибыл',
                    actor: 'Логистика',
                    description: '...',
                    state: 'PLANNED',
                    occurredAt: null,
                    trackNumber: null,
                    trackContext: null
                },
                {
                    code: 'INBOUND_PICKED_UP',
                    title: 'Возврат обработан',
                    actor: 'Магазин',
                    description: '...',
                    state: 'PLANNED',
                    occurredAt: null,
                    trackNumber: null,
                    trackContext: null
                }
            ],
            requiresAction: false
        };

        const headers = { get: jest.fn(() => 'application/json') };
        global.fetch.mockImplementation((url) => {
            if (String(url).includes('/lifecycle')) {
                return Promise.resolve({
                    ok: true,
                    headers,
                    json: () => Promise.resolve({ lifecycle: data.lifecycle })
                });
            }
            return Promise.resolve({ ok: true, headers, json: () => Promise.resolve({ history: [] }) });
        });

        renderModal(data);

        const lifecycleHeading = Array.from(document.querySelectorAll('section.card h6'))
            .find((heading) => heading.textContent.includes('Жизненный цикл заказа'));
        expect(lifecycleHeading).toBeDefined();

        const lifecycleCard = lifecycleHeading?.closest('section.card');
        const toggleLifecycle = lifecycleCard?.querySelector('button');
        toggleLifecycle?.click();
        await Promise.resolve();
        await Promise.resolve();
        await new Promise((resolve) => setTimeout(resolve, 0));

        const button = document.querySelector('form button[type="submit"]');
        expect(button).not.toBeNull();

        const stages = lifecycleCard?.querySelectorAll('ol[role="list"] li') || [];
        expect(stages.length).toBeGreaterThanOrEqual(3);
        expect(button?.textContent).toContain('Отправить заявку');

        const radios = document.querySelectorAll('form input[type="radio"][name^="return-type"]');
        expect(radios).toHaveLength(2);
        const radioLabels = Array.from(document.querySelectorAll('form label.form-check-label'))
            .map((label) => label.textContent);
        expect(radioLabels).toEqual(expect.arrayContaining(['Возврат', 'Обмен']));

        const reasonSelect = document.querySelector('form select[name="reason"]');
        expect(reasonSelect).not.toBeNull();
        const reasonOptions = Array.from(reasonSelect?.options || []).map((option) => option.textContent);
        expect(reasonOptions).toEqual(expect.arrayContaining(['Не подошло', 'Брак', 'Не понравилось', 'Другое']));

        const reverseTrackInput = document.querySelector('form input[name="reverseTrack"]');
        expect(reverseTrackInput).not.toBeNull();
    });
});
