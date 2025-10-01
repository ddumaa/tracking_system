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

        const timeZone = data?.timeZone;
        const format = (value) => formatDateTime(value, timeZone);
        const history = Array.isArray(data?.history) ? data.history : [];

        container.replaceChildren();
        container.classList.remove('justify-content-center', 'align-items-center', 'text-muted');

        const layout = document.createElement('div');
        layout.className = 'd-flex flex-column gap-3 w-100';
        if (data?.id !== undefined) {
            layout.dataset.trackId = String(data.id);
        }

        const parcelCard = createCard('Данные о посылке');
        const parcelHeader = document.createElement('div');
        parcelHeader.className = 'd-flex flex-wrap justify-content-between align-items-start gap-3';

        const trackInfo = document.createElement('div');
        trackInfo.className = 'd-flex flex-column';

        const trackNumber = document.createElement('div');
        trackNumber.className = 'fs-3 fw-semibold';
        const trackText = data?.number ? data.number : 'Трек не указан';
        trackNumber.textContent = trackText;
        if (!data?.number) {
            trackNumber.classList.add('text-muted');
        }

        const serviceInfo = document.createElement('div');
        serviceInfo.className = 'text-muted small';
        serviceInfo.textContent = data?.deliveryService || 'Служба доставки не определена';

        trackInfo.append(trackNumber, serviceInfo);
        parcelHeader.appendChild(trackInfo);

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
            editButton.className = 'btn btn-outline-primary btn-sm align-self-start d-inline-flex align-items-center justify-content-center';
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
            parcelHeader.appendChild(editButton);
        }

        parcelCard.body.appendChild(parcelHeader);
        layout.appendChild(parcelCard.card);

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
