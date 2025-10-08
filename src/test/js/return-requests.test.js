const { TextEncoder, TextDecoder } = require('util');

global.TextEncoder = TextEncoder;
global.TextDecoder = TextDecoder;

describe('return-requests table updates', () => {
    beforeEach(() => {
        jest.resetModules();
        document.body.innerHTML = `
            <table id="returnRequestsTable">
                <tbody>
                    <tr data-return-request-row data-track-id="44" data-request-id="7" data-exchange-requested="false">
                        <td>
                            <button type="button" data-return-track-number>Исходный трек</button>
                        </td>
                        <td>
                            <span data-return-comment>Комментарий отсутствует</span>
                            <span data-return-reverse>Обратный трек: —</span>
                            <span data-return-confirmation>Получение ещё не подтверждено</span>
                        </td>
                    </tr>
                </tbody>
            </table>
            <div id="returnRequestsEmptyState" class="d-none"></div>
        `;
        require('../../main/resources/static/js/return-requests.js');
    });

    afterEach(() => {
        document.body.innerHTML = '';
        if (global.window && global.window.returnRequests) {
            delete global.window.returnRequests;
        }
    });

    test('updates reverse track column from partial payload', () => {
        const row = document.querySelector('tr[data-return-request-row]');
        expect(row).not.toBeNull();
        const trackButton = row.querySelector('[data-return-track-number]');
        expect(trackButton?.textContent).toBe('Исходный трек');

        global.window.returnRequests.updateRow({
            parcelId: 44,
            requestId: 7,
            reverseTrackNumber: 'RR000111222BY'
        });

        const reverseSpan = row.querySelector('[data-return-reverse]');
        expect(reverseSpan?.textContent).toBe('Обратный трек: RR000111222BY');
        expect(trackButton?.textContent).toBe('Исходный трек');
    });

    test('updates comment cell when payload contains comment', () => {
        const row = document.querySelector('tr[data-return-request-row]');
        expect(row).not.toBeNull();
        const commentSpan = row.querySelector('[data-return-comment]');
        expect(commentSpan?.textContent).toBe('Комментарий отсутствует');

        global.window.returnRequests.updateRow({
            parcelId: 44,
            requestId: 7,
            comment: 'Обновлённый комментарий'
        });

        expect(commentSpan?.textContent).toBe('Обновлённый комментарий');
    });

    test('updates confirmation label when receipt confirmed', () => {
        const row = document.querySelector('tr[data-return-request-row]');
        expect(row).not.toBeNull();
        const confirmation = row.querySelector('[data-return-confirmation]');
        expect(confirmation?.textContent).toContain('не подтверждено');

        global.window.returnRequests.updateRow({
            parcelId: 44,
            requestId: 7,
            returnReceiptConfirmed: true,
            returnReceiptConfirmedAt: '2024-05-01T10:00:00Z',
            canConfirmReceipt: false
        });

        expect(confirmation?.textContent).toContain('2024-05-01T10:00:00Z');
        expect(confirmation?.classList.contains('text-success')).toBe(true);
    });
});
