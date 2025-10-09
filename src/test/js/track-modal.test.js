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
            exchange: false,
            chain: [
                { id: 1, number: 'AB123456789BY', exchange: false, current: true }
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

    test('skips lifecycle card for outbound-only stage list', () => {
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
            exchange: false,
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
        expect(lifecycleCard).toBeUndefined();
    });

    test('renders exchange chain with clickable original parcel', () => {
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
            exchange: true,
            chain: [
                { id: 11, number: 'RB987654321CN', exchange: true, current: true },
                { id: 10, number: 'RB111222333CN', exchange: false, current: false }
            ],
            returnRequest: { id: 5, status: 'Зарегистрирована', decisionAt: null, closedAt: null,
                requiresAction: true, exchangeApproved: false, exchangeRequested: true, canStartExchange: true, canCreateExchangeParcel: false, canCloseWithoutExchange: true,
                cancelExchangeUnavailableReason: null, canConfirmReceipt: true, returnReceiptConfirmed: false, returnReceiptConfirmedAt: null },
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

        global.window.trackModal.render(data);

        const buttons = document.querySelectorAll('button.track-chain__item');
        expect(buttons).toHaveLength(2);
        expect(buttons[0].disabled).toBe(true);
        expect(buttons[0].textContent).toContain('обмен');
        buttons[1].click();
        expect(loadSpy).toHaveBeenCalledWith(10);

        const returnCard = Array.from(document.querySelectorAll('section.card'))
            .find((card) => card.querySelector('h6')?.textContent === 'Возврат / обмен');
        expect(returnCard).toBeDefined();
        const definitions = returnCard?.querySelector('dl');
        expect(definitions?.textContent).toContain('Тип обращения');
        const typeHint = returnCard?.querySelector('p.text-muted.small');
        expect(typeHint?.textContent).toContain('Заявка оформлена как обмен');
        expect(returnCard?.textContent).toContain('Подтверждение получения');

        const lifecycleCard = Array.from(document.querySelectorAll('section.card'))
            .find((card) => card.querySelector('h6')?.textContent === 'Жизненный цикл заказа');
        expect(lifecycleCard?.textContent).toContain('Исходная посылка');
        expect(lifecycleCard?.textContent).toContain('RB987654321CN');
        expect(lifecycleCard?.textContent).toContain('Обратный трек');
        expect(lifecycleCard?.textContent).toContain('трек не указан');

        const confirmBtn = Array.from(document.querySelectorAll('button')).find((btn) => btn.textContent === 'Подтвердить получение');
        expect(confirmBtn).toBeDefined();
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
            exchange: false,
            chain: [],
            returnRequest: {
                id: 5,
                status: 'Зарегистрирована',
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
                canConfirmReceipt: true
            },
            canRegisterReturn: false,
            lifecycle: [],
            requiresAction: false
        };

        const headers = { get: jest.fn(() => 'application/json') };
        global.fetch.mockResolvedValueOnce({
            ok: true,
            headers,
            json: () => Promise.resolve(updatedDetails)
        });

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
            exchange: false,
            chain: [],
            returnRequest: {
                id: 5,
                status: 'Зарегистрирована',
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
                canCancelExchange: false
            },
            canRegisterReturn: false,
            lifecycle: [],
            requiresAction: true
        };

        global.window.trackModal.render(initialData);

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
            exchange: false,
            chain: [],
            returnRequest: {
                id: 6,
                status: 'Зарегистрирована',
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
                canConfirmReceipt: false
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

        expect(global.window.returnRequests.updateRow).toHaveBeenCalledWith(expect.objectContaining({
            parcelId: 14,
            requestId: 6,
            returnReceiptConfirmed: true,
            returnReceiptConfirmedAt: '2024-03-01T09:00:00Z',
            canConfirmReceipt: false
        }));
        expect(global.notifyUser).toHaveBeenCalledWith('Обработка возврата подтверждена', 'success');
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
            exchange: false,
            chain: [
                { id: 5, number: 'BY555555555BY', exchange: false, current: true }
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

    test('omits lifecycle card when only outbound stage is provided', () => {
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
            exchange: false,
            chain: [
                { id: 9, number: 'BY000000000BY', exchange: false, current: true }
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
        expect(lifecycleCard).toBeUndefined();
        const lifecycleList = document.querySelector('ol[role="list"]');
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
            exchange: false,
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
            exchange: false,
            chain: [],
            returnRequest: {
                id: 5,
                status: 'Зарегистрирована',
                requiresAction: true,
                exchangeApproved: false,
                exchangeRequested: true,
                canStartExchange: true,
                canCreateExchangeParcel: false,
                canCloseWithoutExchange: true,
                canReopenAsReturn: false,
                canCancelExchange: false,
                returnReceiptConfirmed: false,
                returnReceiptConfirmedAt: null,
                canConfirmReceipt: true
            },
            canRegisterReturn: false,
            lifecycle: [],
            requiresAction: true
        };

        global.window.trackModal.render(data);

        const approveButton = Array.from(document.querySelectorAll('button'))
            .find((btn) => btn.textContent === 'Перевести в обмен');
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
            details: {
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
                exchange: false,
                chain: [],
                returnRequest: null,
                canRegisterReturn: false,
                lifecycle: [],
                requiresAction: false
            },
            exchange: { id: 44, number: 'EX123', exchange: true, current: false }
        };
        global.fetch.mockResolvedValueOnce({ ok: true, headers, json: () => Promise.resolve(responsePayload) });

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
            exchange: false,
            chain: [],
            returnRequest: {
                id: 6,
                status: 'Обмен одобрен',
                requiresAction: true,
                exchangeApproved: true,
                exchangeRequested: true,
                canStartExchange: false,
                canCreateExchangeParcel: true,
                canCloseWithoutExchange: false,
                canReopenAsReturn: true,
                canCancelExchange: false,
                returnReceiptConfirmed: true,
                returnReceiptConfirmedAt: '2024-02-01T10:00:00Z',
                canConfirmReceipt: false
            },
            canRegisterReturn: false,
            lifecycle: [],
            requiresAction: true
        };

        global.window.trackModal.render(data);

        const createButton = Array.from(document.querySelectorAll('button'))
            .find((btn) => btn.textContent === 'Создать обменную посылку');
        expect(createButton).toBeDefined();
        createButton?.click();

        await Promise.resolve();
        await Promise.resolve();
        await Promise.resolve();

        expect(global.fetch).toHaveBeenCalledWith(
            '/api/v1/tracks/14/returns/6/exchange/parcel',
            expect.objectContaining({ method: 'POST' })
        );
        expect(global.notifyUser).toHaveBeenCalledWith('Создана обменная посылка', 'success');
    });

    test('shows register button when return can be created', () => {
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
            exchange: false,
            chain: [
                { id: 7, number: 'RR123', exchange: false, current: true }
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

        global.window.trackModal.render(data);

        const lifecycleHeading = Array.from(document.querySelectorAll('section.card h6'))
            .find((heading) => heading.textContent.includes('Жизненный цикл заказа'));
        expect(lifecycleHeading).toBeDefined();

        const button = document.querySelector('form button[type="submit"]');
        expect(button).not.toBeNull();

        const stages = document.querySelectorAll('ol[role="list"] li');
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
