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
        require('../../main/resources/static/js/track-modal.js');
        global.window.returnRequests = {
            updateRow: jest.fn(),
            removeRowByIds: jest.fn(),
            refreshEmptyState: jest.fn()
        };
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

        global.window.trackModal.render(data);

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

        global.window.trackModal.render(data);

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
                reverseTrackNumber: null,
                requiresAction: true,
                exchangeApproved: false,
                exchangeRequested: false,
                canStartExchange: true,
                canCreateExchangeParcel: false,
                canCloseWithoutExchange: true,
                canReopenAsReturn: false,
                canCancelExchange: true,
                cancelExchangeUnavailableReason: null,
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
                    code: 'CUSTOMER_RETURN',
                    title: 'Возврат от покупателя',
                    actor: 'Покупатель',
                    description: '...',
                    state: 'IN_PROGRESS',
                    occurredAt: '2024-01-05T12:00:00Z',
                    trackNumber: null,
                    trackContext: 'Обратный трек'
                },
                {
                    code: 'MERCHANT_ACCEPT_RETURN',
                    title: 'Приём возврата магазином',
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

        global.window.trackModal.render(data);

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
        expect(lifecycleText).toContain('Возврат от покупателя');
        expect(lifecycleText).toContain('Приём возврата магазином');

        const confirmBtn = Array.from(document.querySelectorAll('button'))
            .find((btn) => btn.getAttribute('aria-label') === 'Подтвердить получение обратной посылки и зафиксировать закрытие обращения');
        expect(confirmBtn).toBeDefined();
        expect(confirmBtn?.textContent).toContain('Принять обратную посылку');

        const closeButton = Array.from(document.querySelectorAll('button'))
            .find((btn) => btn.textContent?.includes('Закрыть обращение'));
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
                reverseTrackNumber: null,
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

        global.window.trackModal.render(data);

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
        expect(texts).toContain('Закрыть обращение');
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
                reverseTrackNumber: null,
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
                reverseTrackNumber: null,
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

        global.window.trackModal.render(initialData);

        const actionCard = Array.from(document.querySelectorAll('section.card'))
            .find((card) => card.querySelector('h6')?.textContent === 'Обращение');
        const closeButton = Array.from(actionCard?.querySelectorAll('button') || [])
            .find((btn) => btn.textContent === 'Закрыть обращение');
        expect(closeButton).toBeDefined();
        closeButton?.click();

        await Promise.resolve();
        await Promise.resolve();
        await Promise.resolve();

        expect(global.fetch).toHaveBeenCalledWith(
            '/api/v1/tracks/31/returns/81/close',
            expect.objectContaining({ method: 'POST' })
        );
        expect(global.notifyUser).toHaveBeenCalledWith('Обращение закрыто без результата', 'warning');

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
                reverseTrackNumber: null,
                requiresAction: false,
                exchangeApproved: false,
                exchangeRequested: false,
                canStartExchange: false,
                canCreateExchangeParcel: false,
                canCloseWithoutExchange: false,
                canReopenAsReturn: false,
                canCancelExchange: false,
                cancelExchangeUnavailableReason: null,
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

        global.window.trackModal.render(data);

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
                reverseTrackNumber: null,
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

        global.window.trackModal.render(data);

        const actionCard = Array.from(document.querySelectorAll('section.card'))
            .find((card) => card.querySelector('h6')?.textContent === 'Обращение');
        expect(actionCard).toBeDefined();

        const reopenButton = Array.from(actionCard?.querySelectorAll('button') || [])
            .find((btn) => btn.textContent?.trim() === 'Перевести в возврат');
        expect(reopenButton).toBeDefined();
        expect(reopenButton?.disabled).toBe(false);
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
                reverseTrackNumber: null,
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

        global.window.trackModal.render(data);

        const actionCard = Array.from(document.querySelectorAll('section.card'))
            .find((card) => card.querySelector('h6')?.textContent === 'Обращение');
        expect(actionCard).toBeDefined();

        const reopenButton = Array.from(actionCard?.querySelectorAll('button') || [])
            .find((btn) => btn.textContent?.trim() === 'Перевести в возврат');
        expect(reopenButton).toBeDefined();
        expect(reopenButton?.disabled).toBe(false);
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
                reverseTrackNumber: null,
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

        global.window.trackModal.render(data);

        const actionCard = Array.from(document.querySelectorAll('section.card'))
            .find((card) => card.querySelector('h6')?.textContent === 'Обращение');
        expect(actionCard).toBeDefined();

        const reopenButton = Array.from(actionCard?.querySelectorAll('button') || [])
            .find((btn) => btn.textContent?.trim() === 'Перевести в возврат');
        expect(reopenButton).toBeUndefined();
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
                reverseTrackNumber: null,
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

        global.window.trackModal.render(data);

        const actionCard = Array.from(document.querySelectorAll('section.card'))
            .find((card) => card.querySelector('h6')?.textContent === 'Обращение');
        expect(actionCard).toBeDefined();

        const confirmButton = Array.from(actionCard?.querySelectorAll('button') || [])
            .find((btn) => btn.getAttribute('aria-label') === 'Подтвердить получение обратной посылки');
        expect(confirmButton).toBeUndefined();

        const closeButton = Array.from(actionCard?.querySelectorAll('button') || [])
            .find((btn) => btn.textContent === 'Закрыть обращение');
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
                    code: 'CUSTOMER_RETURN',
                    title: 'Возврат от покупателя',
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

        global.window.trackModal.render(data);

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
                reverseTrackNumber: 'RR123456789BY',
                requiresAction: false,
                exchangeApproved: false,
                exchangeRequested: false,
                canStartExchange: true,
                canCreateExchangeParcel: false,
                canCloseWithoutExchange: true,
                canReopenAsReturn: false,
                canCancelExchange: false,
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
                reverseTrackNumber: null,
                requiresAction: true,
                exchangeApproved: false,
                exchangeRequested: false,
                canStartExchange: true,
                canCreateExchangeParcel: false,
                canCloseWithoutExchange: true,
                canReopenAsReturn: false,
                canCancelExchange: false,
                hint: 'Укажите обратный трек для ускорения обработки.',
                warnings: [],
                detailsUrl: 'https://example.com/returns'
            },
            canRegisterReturn: false,
            lifecycle: [],
            requiresAction: true
        };

        global.window.trackModal.render(initialData);

        const headers = { get: jest.fn(() => 'application/json') };
        global.fetch.mockResolvedValueOnce({
            ok: true,
            headers,
            json: () => Promise.resolve(updatedDetails)
        });

        const form = document.querySelector('form[data-reverse-track-form]');
        expect(form).not.toBeNull();
        const input = form.querySelector('input[name="reverseTrackNumber"]');
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

        expect(global.fetch).toHaveBeenCalledWith(
            '/api/v1/tracks/12/returns/5/reverse-track',
            expect.objectContaining({
                method: 'PATCH',
                body: JSON.stringify({
                    reverseTrackNumber: 'RR123456789BY',
                    comment: 'Обновлённый комментарий'
                })
            })
        );
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
            reverseTrackNumber: 'RR123456789BY',
            comment: 'Обновлённый комментарий'
        }));
    });

    test('confirms return receipt via API and updates table row', async () => {
        setupDom();

        const payload = {
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
                reverseTrackNumber: null,
                requiresAction: true,
                exchangeApproved: false,
                exchangeRequested: false,
                canStartExchange: false,
                canCloseWithoutExchange: true,
                canReopenAsReturn: false,
                canCancelExchange: false,
                cancelExchangeUnavailableReason: null,
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
        };

        global.fetch.mockResolvedValueOnce({
            ok: true,
            headers: { get: () => 'application/json' },
            json: () => Promise.resolve(payload)
        });

        await global.window.trackModal.confirmReturnProcessing(14, 6, {});

        expect(global.fetch).toHaveBeenCalledWith(
            '/api/v1/tracks/14/returns/6/confirm-processing',
            expect.objectContaining({ method: 'POST' })
        );
        expect(global.window.returnRequests.updateRow).toHaveBeenCalledWith(expect.objectContaining({
            parcelId: 14,
            requestId: 6,
            returnReceiptConfirmed: true,
            returnReceiptConfirmedAt: '2024-03-01T09:00:00Z',
            canConfirmReceipt: false
        }));
        expect(global.notifyUser).toHaveBeenCalledWith('Обратная посылка принята', 'success');
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

        global.window.trackModal.render(data);

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

        global.window.trackModal.render(data);

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
                reverseTrackNumber: null,
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

        global.window.trackModal.render(data);

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

        expect(global.fetch).toHaveBeenCalledWith(
            '/api/v1/tracks/12/returns/5/exchange',
            expect.objectContaining({ method: 'POST' })
        );
        expect(global.notifyUser).toHaveBeenCalledWith('Заявка переведена в обмен', 'info');
    });

    test('creates exchange parcel via dedicated action', async () => {
        setupDom();

        const headers = { get: jest.fn(() => 'application/json') };
        const responsePayload = {
            details: { returnRequest: { id: 6, state: 'REGISTERED_EXCHANGE' } },
            exchange: { id: 101, number: '' },
            state: 'REGISTERED_EXCHANGE'
        };
        global.fetch.mockImplementation((url) => {
            if (String(url).includes('/exchange/parcel')) {
                return Promise.resolve({ ok: true, headers, json: () => Promise.resolve(responsePayload) });
            }
            if (String(url).includes('/details')) {
                return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
            }
            return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
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
                reverseTrackNumber: null,
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

        global.window.trackModal.render(data);

        const card = Array.from(document.querySelectorAll('section.card'))
            .find((item) => item.querySelector('h6')?.textContent === 'Обращение');
        const convertButton = Array.from(card?.querySelectorAll('button') || [])
            .find((btn) => btn.getAttribute('aria-label') === 'Создать обменную посылку и указать трек номер');
        expect(convertButton).toBeDefined();
        convertButton?.click();

        await Promise.resolve();
        await Promise.resolve();
        await Promise.resolve();

        expect(global.fetch).toHaveBeenCalledWith(
            '/api/v1/tracks/14/returns/6/exchange/parcel',
            expect.objectContaining({ method: 'POST' })
        );
        expect(global.notifyUser).toHaveBeenCalledWith('Обменное отправление создано', 'info');
        expect(global.window.trackModal.promptTrackNumber).toBeInstanceOf(Function);
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
                reverseTrackNumber: null,
                requiresAction: true,
                exchangeApproved: true,
                exchangeRequested: true,
                canStartExchange: false,
                canCreateExchangeParcel: false,
                canCloseWithoutExchange: false,
                canReopenAsReturn: true,
                canCancelExchange: true,
                returnReceiptConfirmed: true,
                returnReceiptConfirmedAt: '2024-02-16T12:00:00Z',
                canConfirmReceipt: false,
                hint: 'Обменная посылка готова к отправке.',
                warnings: ['Проверьте состав вложения.'],
                detailsUrl: 'https://example.com/returns',
                cancelExchangeUnavailableReason: null
            },
            canRegisterReturn: false,
            lifecycle: [],
            requiresAction: true
        };

        global.window.trackModal.render(data);

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
                reverseTrackNumber: null,
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

        global.window.trackModal.render(data);

        const returnCard = Array.from(document.querySelectorAll('section.card'))
            .find((card) => card.querySelector('h6')?.textContent === 'Обращение');
        const badges = Array.from(returnCard?.querySelectorAll('.badge.rounded-pill') || []);
        expect(badges[0]?.textContent).toBe('Обмен');
        expect(badges[1]?.textContent).toBe('Возврат');

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
                reverseTrackNumber: null,
                requiresAction: true,
                exchangeApproved: false,
                exchangeRequested: false,
                canStartExchange: true,
                canCreateExchangeParcel: false,
                canCloseWithoutExchange: true,
                canReopenAsReturn: false,
                canCancelExchange: false,
                cancelExchangeUnavailableReason: null,
                returnReceiptConfirmed: false,
                returnReceiptConfirmedAt: null,
                canConfirmReceipt: true
            },
            canRegisterReturn: false,
            lifecycle: [],
            requiresAction: true
        };

        global.window.trackModal.render(data);

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
                reverseTrackNumber: 'RR123456',
                requiresAction: false,
                exchangeApproved: false,
                exchangeRequested: false,
                canStartExchange: false,
                canCreateExchangeParcel: false,
                canCloseWithoutExchange: false,
                canReopenAsReturn: false,
                canCancelExchange: false,
                cancelExchangeUnavailableReason: null,
                returnReceiptConfirmed: true,
                returnReceiptConfirmedAt: '2024-01-06T11:00:00Z',
                canConfirmReceipt: false
            },
            canRegisterReturn: false,
            lifecycle: [],
            requiresAction: false
        };

        global.window.trackModal.render(data);

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
                    code: 'CUSTOMER_RETURN',
                    title: 'Возврат от покупателя',
                    actor: 'Покупатель',
                    description: '...',
                    state: 'PLANNED',
                    occurredAt: null,
                    trackNumber: null,
                    trackContext: null
                },
                {
                    code: 'MERCHANT_ACCEPT_RETURN',
                    title: 'Приём возврата магазином',
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

        global.window.trackModal.render(data);

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

        const reverseTrackInput = document.querySelector('form input[name="reverseTrackNumber"]');
        expect(reverseTrackInput).not.toBeNull();
    });
});
