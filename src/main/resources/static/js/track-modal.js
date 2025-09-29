(() => {
    'use strict';

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
     * Отрисовывает содержимое модального окна с деталями трека.
     * Метод отвечает только за манипуляцию DOM и не выполняет сетевые запросы (SRP).
     * @param {Object} data DTO с сервера
     */
    function renderTrackModal(data) {
        const container = document.getElementById('trackModalContent')
            || document.querySelector('#infoModal .modal-body');
        if (!container) {
            return;
        }

        const timeZone = data?.timeZone;
        const format = (value) => formatDateTime(value, timeZone);
        const history = Array.isArray(data?.history) ? data.history : [];
        const historyRows = history.length > 0
            ? history.map(event => `
                <tr>
                    <td>${format(event.timestamp)}</td>
                    <td>${event.status || '—'}</td>
                </tr>
            `).join('')
            : '<tr><td colspan="2" class="text-center text-muted">История отсутствует</td></tr>';

        const currentStatusBlock = data?.currentStatus ? `
            <div class="mb-3">
                <div class="text-muted small">Текущий статус</div>
                <div class="fw-semibold">${data.currentStatus.status}</div>
                <div class="text-muted">${format(data.currentStatus.timestamp)}</div>
            </div>
        ` : '';

        let refreshBlock = '';
        if (data?.refreshAllowed) {
            refreshBlock = '<div class="alert alert-success mb-3" role="alert">Обновление доступно — выполните его из таблицы отправлений.</div>';
        } else if (data?.nextRefreshAt) {
            refreshBlock = `<div class="alert alert-warning mb-3" role="alert">Повторное обновление будет доступно после ${format(data.nextRefreshAt)}.</div>`;
        } else {
            refreshBlock = '<div class="alert alert-secondary mb-3" role="alert">Обновление недоступно для текущего статуса.</div>';
        }

        const deliveryService = data?.deliveryService || 'Служба доставки не определена';
        const trackNumber = data?.number || '—';
        const editButton = data?.canEditTrack
            ? `<button type="button" class="btn btn-outline-primary btn-sm ms-auto" id="trackModalEditBtn" data-track-id="${data.id ?? ''}">Редактировать номер</button>`
            : '';

        container.innerHTML = `
            <div class="w-100" data-track-id="${data?.id ?? ''}">
                <div class="d-flex align-items-start gap-3 mb-3">
                    <div>
                        <div class="text-muted small">Трек-номер</div>
                        <div class="fs-5 fw-semibold">${trackNumber}</div>
                        <div class="text-muted">${deliveryService}</div>
                    </div>
                    ${editButton}
                </div>
                ${currentStatusBlock}
                ${refreshBlock}
                <div class="table-responsive">
                    <table class="table table-striped">
                        <thead>
                            <tr><th>Дата</th><th>Статус</th></tr>
                        </thead>
                        <tbody>${historyRows}</tbody>
                    </table>
                </div>
            </div>
        `;

        if (data?.canEditTrack) {
            container.querySelector('#trackModalEditBtn')?.addEventListener('click', () => {
                if (data?.id) {
                    promptTrackNumber(data.id);
                }
            });
        }
    }

    /**
     * Показывает модальное окно для ввода трек-номера.
     * Метод только настраивает форму и делегирует показ Bootstrap-модали (SRP).
     * @param {string} id идентификатор отправления
     */
    function promptTrackNumber(id) {
        const idInput = document.querySelector('#set-track-number-form input[name="id"]');
        if (idInput) {
            idInput.value = id;
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
    function handleTrackNumberFormSubmit(event) {
        event.preventDefault();

        const form = event.target;
        const id = form.querySelector('input[name="id"]').value;
        const number = form.querySelector('input[name="number"]').value;
        const normalized = number.toUpperCase().trim();

        fetch('/app/departures/set-number', {
            method: 'POST',
            headers: {
                ...buildCsrfHeaders(),
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: new URLSearchParams({ id, number: normalized })
        })
            .then(response => {
                if (!response.ok) {
                    throw new Error('Не удалось сохранить номер');
                }

                const row = document.querySelector(`tr[data-track-id="${id}"]`);
                if (row) {
                    const btn = row.querySelector('button.parcel-number');
                    if (btn) {
                        btn.textContent = normalized;
                        btn.classList.add('open-modal');
                        btn.dataset.itemnumber = normalized;
                        btn.dataset.trackId = id;
                    }
                    row.dataset.trackNumber = normalized;
                    notifyUser('Трек-номер добавлен', 'success');
                } else {
                    window.location.reload();
                }
            })
            .catch(error => notifyUser('Ошибка: ' + error.message, 'danger'))
            .finally(() => {
                const modal = bootstrap.Modal.getInstance(document.getElementById('trackNumberModal'));
                modal?.hide();
                form.reset();
            });
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
    }

    document.addEventListener('DOMContentLoaded', initModalInteractions);

    window.trackModal = {
        loadModal,
        promptTrackNumber
    };
})();
