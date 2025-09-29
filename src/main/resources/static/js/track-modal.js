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

        container.replaceChildren();

        const wrapper = document.createElement('div');
        wrapper.className = 'w-100';
        if (data?.id !== undefined) {
            wrapper.dataset.trackId = String(data.id);
        }

        const headerBlock = document.createElement('div');
        headerBlock.className = 'd-flex align-items-start gap-3 mb-3';

        const infoBlock = document.createElement('div');

        const trackLabel = document.createElement('div');
        trackLabel.className = 'text-muted small';
        trackLabel.textContent = 'Трек-номер';
        infoBlock.appendChild(trackLabel);

        const trackValue = document.createElement('div');
        trackValue.className = 'fs-5 fw-semibold';
        trackValue.textContent = data?.number || '—';
        infoBlock.appendChild(trackValue);

        const deliveryValue = document.createElement('div');
        deliveryValue.className = 'text-muted';
        deliveryValue.textContent = data?.deliveryService || 'Служба доставки не определена';
        infoBlock.appendChild(deliveryValue);

        headerBlock.appendChild(infoBlock);

        let editButton;
        if (data?.canEditTrack) {
            editButton = document.createElement('button');
            editButton.type = 'button';
            editButton.className = 'btn btn-outline-primary btn-sm ms-auto';
            editButton.id = 'trackModalEditBtn';
            editButton.textContent = 'Редактировать номер';
            if (data?.id !== undefined) {
                editButton.dataset.trackId = String(data.id);
            }
            headerBlock.appendChild(editButton);
        }

        wrapper.appendChild(headerBlock);

        if (data?.currentStatus) {
            const currentStatusBlock = document.createElement('div');
            currentStatusBlock.className = 'mb-3';

            const currentLabel = document.createElement('div');
            currentLabel.className = 'text-muted small';
            currentLabel.textContent = 'Текущий статус';
            currentStatusBlock.appendChild(currentLabel);

            const currentValue = document.createElement('div');
            currentValue.className = 'fw-semibold';
            currentValue.textContent = data.currentStatus.status || '—';
            currentStatusBlock.appendChild(currentValue);

            const currentTime = document.createElement('div');
            currentTime.className = 'text-muted';
            currentTime.textContent = format(data.currentStatus.timestamp);
            currentStatusBlock.appendChild(currentTime);

            wrapper.appendChild(currentStatusBlock);
        }

        const refreshBlock = document.createElement('div');
        refreshBlock.classList.add('alert', 'mb-3');
        refreshBlock.setAttribute('role', 'alert');

        if (data?.refreshAllowed) {
            refreshBlock.classList.add('alert-success');
            refreshBlock.textContent = 'Обновление доступно — выполните его из таблицы отправлений.';
        } else if (data?.nextRefreshAt) {
            refreshBlock.classList.add('alert-warning');
            refreshBlock.textContent = `Повторное обновление будет доступно после ${format(data.nextRefreshAt)}.`;
        } else {
            refreshBlock.classList.add('alert-secondary');
            refreshBlock.textContent = 'Обновление недоступно для текущего статуса.';
        }

        wrapper.appendChild(refreshBlock);

        const tableWrapper = document.createElement('div');
        tableWrapper.className = 'table-responsive';

        const table = document.createElement('table');
        table.className = 'table table-striped';

        const thead = document.createElement('thead');
        const headRow = document.createElement('tr');

        const dateHeader = document.createElement('th');
        dateHeader.textContent = 'Дата';
        headRow.appendChild(dateHeader);

        const statusHeader = document.createElement('th');
        statusHeader.textContent = 'Статус';
        headRow.appendChild(statusHeader);

        thead.appendChild(headRow);
        table.appendChild(thead);

        const tbody = document.createElement('tbody');

        if (history.length === 0) {
            const emptyRow = document.createElement('tr');
            const emptyCell = document.createElement('td');
            emptyCell.colSpan = 2;
            emptyCell.className = 'text-center text-muted';
            emptyCell.textContent = 'История отсутствует';
            emptyRow.appendChild(emptyCell);
            tbody.appendChild(emptyRow);
        } else {
            history.forEach(event => {
                const row = document.createElement('tr');

                const dateCell = document.createElement('td');
                dateCell.textContent = format(event.timestamp);
                row.appendChild(dateCell);

                const statusCell = document.createElement('td');
                statusCell.textContent = event.status || '—';
                row.appendChild(statusCell);

                tbody.appendChild(row);
            });
        }

        table.appendChild(tbody);
        tableWrapper.appendChild(table);
        wrapper.appendChild(tableWrapper);

        container.appendChild(wrapper);

        if (editButton && data?.id) {
            editButton.addEventListener('click', () => {
                promptTrackNumber(data.id);
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
