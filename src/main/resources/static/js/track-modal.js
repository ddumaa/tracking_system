(() => {
    'use strict';

    /** Текущий идентификатор интервала обратного отсчёта. */
    let refreshTimerId = null;

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
     * Запускает таймер для разблокировки кнопки обновления.
     * @param {HTMLButtonElement} button кнопка «Обновить»
     * @param {HTMLElement} countdown элемент с текстом обратного отсчёта
     * @param {string|null} nextRefreshAt ISO-строка следующего обновления
     * @param {boolean} refreshAllowed признак немедленного обновления
     */
    function startRefreshTimer(button, countdown, nextRefreshAt, refreshAllowed) {
        clearRefreshTimer();
        if (!button || !countdown) {
            return;
        }

        /**
         * Обновляет состояние кнопки и подписи, соблюдая доступность.
         * @param {string} text отображаемый текст рядом с кнопкой
         * @param {boolean} disabled нужно ли блокировать кнопку
         */
        const applyState = (text, disabled) => {
            button.disabled = disabled;
            button.setAttribute('aria-disabled', String(disabled));
            countdown.textContent = text;
            countdown.classList.toggle('visually-hidden', text.length === 0);
            countdown.setAttribute('aria-hidden', text.length === 0 ? 'true' : 'false');
        };

        const showActiveState = () => applyState('', false);

        if (!nextRefreshAt || refreshAllowed) {
            if (refreshAllowed) {
                showActiveState();
            } else {
                applyState('Обновление недоступно', true);
            }
            return;
        }

        const target = Date.parse(nextRefreshAt);
        if (Number.isNaN(target)) {
            showActiveState();
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
            applyState(`Можно выполнить через ${formatted}`, true);
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

        const headerNumber = modal?.querySelector('#trackModalNumber');
        if (headerNumber) {
            headerNumber.textContent = data?.number || '—';
        }

        const headerService = modal?.querySelector('#trackModalService');
        if (headerService) {
            headerService.textContent = data?.deliveryService || 'Служба доставки не определена';
        }

        const editButton = modal?.querySelector('#trackModalEditButton');
        if (editButton) {
            const canEdit = Boolean(data?.canEditTrack);
            editButton.classList.toggle('d-none', !canEdit);
            editButton.setAttribute('aria-hidden', canEdit ? 'false' : 'true');
            if (canEdit && data?.id !== undefined) {
                editButton.dataset.trackId = String(data.id);
                editButton.dataset.currentNumber = data?.number || '';
                editButton.onclick = () => {
                    promptTrackNumber(data.id, data.number || '');
                };
            } else {
                editButton.removeAttribute('data-track-id');
                editButton.removeAttribute('data-current-number');
                editButton.onclick = null;
            }
        }

        const timeZone = data?.timeZone;
        const format = (value) => formatDateTime(value, timeZone);
        const history = Array.isArray(data?.history) ? data.history : [];

        container.replaceChildren();

        const layout = document.createElement('div');
        layout.className = 'd-flex flex-column gap-3';
        if (data?.id !== undefined) {
            layout.dataset.trackId = String(data.id);
        }

        const parcelCard = createCard('Данные о посылке');
        const detailsList = document.createElement('dl');
        detailsList.className = 'row mb-0 g-2 small';

        const idLabel = document.createElement('dt');
        idLabel.className = 'col-sm-4 text-muted';
        idLabel.textContent = 'ID посылки';
        const idValue = document.createElement('dd');
        idValue.className = 'col-sm-8';
        idValue.textContent = data?.id !== undefined ? String(data.id) : '—';

        const numberLabel = document.createElement('dt');
        numberLabel.className = 'col-sm-4 text-muted';
        numberLabel.textContent = 'Трек-номер';
        const numberValue = document.createElement('dd');
        numberValue.className = 'col-sm-8';
        numberValue.textContent = data?.number || '—';

        const serviceLabel = document.createElement('dt');
        serviceLabel.className = 'col-sm-4 text-muted';
        serviceLabel.textContent = 'Служба доставки';
        const serviceValue = document.createElement('dd');
        serviceValue.className = 'col-sm-8';
        serviceValue.textContent = data?.deliveryService || 'Не указана';

        detailsList.append(idLabel, idValue, numberLabel, numberValue, serviceLabel, serviceValue);
        parcelCard.body.appendChild(detailsList);
        layout.appendChild(parcelCard.card);

        const refreshCard = createCard('Обновление');
        const refreshSection = document.createElement('div');
        refreshSection.className = 'd-flex flex-wrap align-items-center gap-3';

        const refreshButton = document.createElement('button');
        refreshButton.type = 'button';
        refreshButton.className = 'btn btn-primary js-track-refresh-btn';
        refreshButton.textContent = 'Обновить';
        refreshButton.dataset.loadingText = 'Обновляем…';
        refreshButton.setAttribute('aria-label', 'Обновить данные трека');
        refreshButton.setAttribute('aria-controls', 'trackModalContent');
        refreshButton.setAttribute('data-bs-toggle', 'tooltip');
        refreshButton.setAttribute('data-bs-placement', 'top');
        refreshButton.setAttribute('title', 'Нажмите, чтобы обновить трек');
        if (data?.id !== undefined) {
            refreshButton.dataset.trackId = String(data.id);
        }

        const countdown = document.createElement('span');
        countdown.id = 'trackRefreshCountdown';
        countdown.className = 'text-muted small visually-hidden';
        countdown.setAttribute('role', 'status');
        countdown.setAttribute('aria-live', 'polite');
        countdown.setAttribute('aria-hidden', 'true');

        refreshSection.append(refreshButton, countdown);
        refreshCard.body.appendChild(refreshSection);
        layout.appendChild(refreshCard.card);

        const statusCard = createCard('Текущий статус');
        if (data?.currentStatus) {
            const statusValue = document.createElement('div');
            statusValue.className = 'fs-6 fw-semibold';
            statusValue.textContent = data.currentStatus.status || '—';

            const statusTime = document.createElement('div');
            statusTime.className = 'text-muted small';
            statusTime.textContent = format(data.currentStatus.timestamp);

            statusCard.body.append(statusValue, statusTime);
        } else {
            const noStatus = document.createElement('div');
            noStatus.className = 'text-muted';
            noStatus.textContent = 'Статус ещё не определён';
            statusCard.body.appendChild(noStatus);
        }
        layout.appendChild(statusCard.card);

        const historyCard = createCard('История трека');
        if (history.length === 0) {
            const emptyHistory = document.createElement('p');
            emptyHistory.className = 'text-muted mb-0';
            emptyHistory.textContent = 'История пока пуста';
            historyCard.body.appendChild(emptyHistory);
        } else {
            const timeline = document.createElement('div');
            timeline.className = 'timeline';

            history.forEach((event, index) => {
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
                statusEl.textContent = event.status || '—';
                item.appendChild(statusEl);

                if (event.details) {
                    const detailsEl = document.createElement('div');
                    detailsEl.className = 'timeline-details text-muted small';
                    detailsEl.textContent = event.details;
                    item.appendChild(detailsEl);
                }

                timeline.appendChild(item);
            });

            historyCard.body.appendChild(timeline);
        }
        layout.appendChild(historyCard.card);

        container.appendChild(layout);

        startRefreshTimer(refreshButton, countdown, data?.nextRefreshAt || null, Boolean(data?.refreshAllowed));

        if (typeof bootstrap !== 'undefined' && bootstrap.Tooltip && typeof bootstrap.Tooltip.getOrCreateInstance === 'function') {
            bootstrap.Tooltip.getOrCreateInstance(refreshButton);
        }
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
        const infoModal = document.getElementById('infoModal');
        if (infoModal) {
            infoModal.addEventListener('hidden.bs.modal', clearRefreshTimer);
        }
    }

    document.addEventListener('DOMContentLoaded', initModalInteractions);

    window.trackModal = {
        loadModal,
        promptTrackNumber,
        render: renderTrackModal
    };
})();
