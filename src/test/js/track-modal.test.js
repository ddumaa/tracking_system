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
        global.fetch = jest.fn(() => Promise.resolve({ ok: true, json: () => Promise.resolve({}) }));
        global.crypto = { randomUUID: jest.fn(() => 'test-uuid') };
        require('../../main/resources/static/js/track-modal.js');
    }

    afterEach(() => {
        jest.clearAllMocks();
        document.body.innerHTML = '';
        delete global.bootstrap;
        delete global.notifyUser;
        delete global.promptTrackNumber;
        delete global.fetch;
        delete global.crypto;
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
                requiresAction: true, exchangeApproved: false, canStartExchange: true, canCloseWithoutExchange: true,
                cancelExchangeUnavailableReason: null },
            canRegisterReturn: false,
            requiresAction: true
        };

        global.window.trackModal.render(data);

        const buttons = document.querySelectorAll('button.track-chain__item');
        expect(buttons).toHaveLength(2);
        expect(buttons[0].disabled).toBe(true);
        expect(buttons[0].textContent).toContain('обмен');
        buttons[1].click();
        expect(loadSpy).toHaveBeenCalledWith(10);
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
            requiresAction: false
        };

        global.window.trackModal.render(data);

        const buttons = document.querySelectorAll('button.track-chain__item');
        expect(buttons).toHaveLength(1);
        expect(buttons[0].textContent).not.toContain('обмен');
        expect(buttons[0].getAttribute('aria-current')).toBe('true');
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
            requiresAction: false
        };

        global.window.trackModal.render(data);

        const button = document.querySelector('form button[type="submit"]');
        expect(button).not.toBeNull();
        expect(button?.textContent).toContain('Отправить заявку');
    });
});
