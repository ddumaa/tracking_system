// Глобальный режим отладки. Если уже определён, используем существующее
// значение, иначе по умолчанию false
window.DEBUG_MODE = window.DEBUG_MODE || false;
function debugLog(...args) { if (window.DEBUG_MODE) console.log(...args); }

/** =====================
 *  ГЛОБАЛЬНЫЕ ФУНКЦИИ
 * ===================== */

function updateDeleteButtonState() {
    const hasChecked = document.querySelectorAll(".selectCheckbox:checked").length > 0;
    document.getElementById("applyActionBtn").disabled = !hasChecked;
}

function updateApplyButtonState() {
    const applyBtn = document.getElementById("applyActionBtn");
    if (!applyBtn) return; // Если кнопки нет, просто выходим

    const selectedCheckboxes = document.querySelectorAll(".selectCheckbox:checked").length;
    const selectedAction = document.getElementById("actionSelect")?.value || ""; // Проверяем существование actionSelect

    // Кнопка `disabled`, если не выбраны чекбоксы или не выбрано действие
    applyBtn.disabled = !(selectedCheckboxes > 0 && selectedAction);
}

function toggleAllCheckboxes(checked) {
    document.querySelectorAll(".selectCheckbox").forEach(checkbox => {
        checkbox.checked = checked;
    });
    updateApplyButtonState();
}

function showLoading() {
    document.body.classList.add('loading');
    document.getElementById('loadingOverlay')?.classList.remove('hidden');
}

function hideLoading() {
    document.body.classList.remove('loading');
    document.getElementById('loadingOverlay')?.classList.add('hidden');
}

/**
 * Копирует текст в буфер обмена и показывает уведомление о результате.
 * @param {string} text - копируемый текст
 */
function copyToClipboard(text) {
    navigator.clipboard.writeText(text)
        .then(() => notifyUser('Ссылка скопирована в буфер обмена', 'success'))
        .catch(() => notifyUser('Не удалось скопировать ссылку', 'danger'));
}

/**
 * Инициализирует кнопку копирования ссылки на Telegram-бота.
 * Находит кнопку по ID, читает URL из атрибута data-link
 * и регистрирует обработчик, вызывающий {@link copyToClipboard}.
 */
function initTelegramLinkCopy() {
    const copyBtn = document.getElementById('telegramLinkCopyBtn');
    if (!copyBtn) return;

    const link = copyBtn.dataset.link;
    if (!link) return;

    copyBtn.addEventListener('click', () => copyToClipboard(link));
}

/**
 * Устанавливает активную вкладку профиля во всех меню.
 * @param {string} href - Идентификатор вкладки (href вида '#v-pills-home').
 */
function setActiveProfileTab(href) {
    document.querySelectorAll('.profile-tab-menu a').forEach(link => {
        link.classList.toggle('active', link.getAttribute('href') === href);
    });
}

/**
 * Инициализирует переключение сортировки по дате.
 * Нажатие на кнопку с ID 'sortDateBtn' меняет параметр 'sortOrder',
 * выполняет запрос по обновлённому URL и заменяет тело таблицы без
 * полной перезагрузки страницы.
 */
function initSortDateToggle() {
    const sortBtn = document.getElementById('sortDateBtn');
    if (!sortBtn) return;

    sortBtn.addEventListener('click', function (event) {
        event.preventDefault();

        const url = new URL(window.location.href);
        const currentOrder = url.searchParams.get('sortOrder');
        const newOrder = currentOrder === 'asc' ? 'desc' : 'asc';
        url.searchParams.set('sortOrder', newOrder);

        fetch(url.toString(), { method: 'GET', cache: 'no-store' })
            .then(response => {
                if (!response.ok) {
                    throw new Error('Ошибка загрузки данных');
                }
                return response.text();
            })
            .then(html => {
                const parser = new DOMParser();
                const doc = parser.parseFromString(html, 'text/html');
                const newTableBody = doc.querySelector('tbody')?.innerHTML || '';

                if (newTableBody) {
                    const currentTbody = document.querySelector('tbody');
                    if (currentTbody) {
                        currentTbody.innerHTML = newTableBody;
                    }
                }

                // Обновляем иконку сортировки, чтобы отразить текущее состояние
                const newIcon = doc.querySelector('#sortDateBtn i');
                const currentIcon = sortBtn.querySelector('i');
                if (newIcon && currentIcon) {
                    currentIcon.className = newIcon.className;
                }

                // Сохраняем новый URL в истории
                window.history.replaceState({}, '', url);
                debugLog('✅ Таблица отсортирована!');
            })
            .catch(error => {
                console.error('❌ Ошибка загрузки отсортированных данных!', error);
            });
    });
}

// Обновляем кнопку при изменении чекбоксов
document.body.addEventListener("change", function (event) {
    if (event.target.classList.contains("selectCheckbox")) {
        updateApplyButtonState();
    }
});

document.getElementById("actionSelect")?.addEventListener("change", updateApplyButtonState);

/**
 * Загружает детали по конкретной отправке и показывает модальное окно.
 *
 * @param {string} itemNumber - номер отправления
 *
 * Алгоритм работы:
 * 1. Проверяем, что получен номер отправления.
 * 2. Показываем индикатор загрузки, чтобы пользователь видел процесс.
 * 3. Выполняем запрос к серверу и получаем HTML с деталями.
 * 4. Подставляем полученные данные в тело модального окна.
 * 5. Отображаем модальное окно и в блоке finally скрываем индикатор.
 *
 * Функция отвечает только за загрузку и отображение окна,
 * сохраняя единую ответственность согласно принципам SOLID.
 */
function loadModal(itemNumber) {
    if (!itemNumber) return;

    showLoading(); // показываем индикатор для операций с одной посылкой

    fetch(`/app/departures/${itemNumber}`)
        .then(response => {
            if (!response.ok) {
                throw new Error('Ошибка при загрузке данных');
            }
            return response.text();
        })
        .then(data => {
            document.querySelector('#infoModal .modal-body').innerHTML = data;
            const modal = new bootstrap.Modal(document.getElementById('infoModal'));
            modal.show();
        })
        .catch(() => notifyUser('Ошибка при загрузке данных', "danger"))
        .finally(() => hideLoading()); // скрываем индикатор в любом случае
}

/**
 * Загружает данные покупателя, связанного с посылкой, и показывает их в модале.
 *
 * @param {string} trackId - идентификатор трека
 *
 * Шаги выполнения:
 * 1. Проверяем наличие trackId.
 * 2. Отправляем запрос на сервер и получаем HTML с информацией о покупателе.
 * 3. Вставляем полученные данные в тело модального окна.
 * 4. Показываем модальное окно и инициализируем связанные формы.
 *
 * Единая ответственность функции — получение и отображение информации
 * о покупателе.
 */
function loadCustomerInfo(trackId) {
    if (!trackId) return;
    fetch(`/app/customers/parcel/${trackId}`)
        .then(response => {
            if (!response.ok) {
                throw new Error('Ошибка при загрузке данных');
            }
            return response.text();
        })
        .then(data => {
            document.querySelector('#customerModal .modal-body').innerHTML = data;
            let modal = new bootstrap.Modal(document.getElementById('customerModal'));
            modal.show();
            // После загрузки контента назначаем обработчики форм
            initAssignCustomerFormHandler();
            initEditCustomerPhoneFormHandler();
            initPhoneEditToggle();
        })
        .catch(() => notifyUser('Ошибка при загрузке данных', 'danger'));
}

// Общая функция для отправки формы через AJAX
function ajaxSubmitForm(formId, containerId, afterLoadCallbacks = []) {
    const form = document.getElementById(formId);
    if (!form) return;

    form.addEventListener('submit', function (event) {
        event.preventDefault();

        fetch(form.action, {
            method: form.method,
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                [document.querySelector('meta[name="_csrf_header"]').content]: document.querySelector('meta[name="_csrf"]').content
            },
            body: new URLSearchParams(new FormData(form))
        })
            .then(response => response.text())
            .then(html => {
                const container = document.getElementById(containerId);
                container.innerHTML = html;

                // Переинициализируем обработчики после замены HTML-кода вкладки
                afterLoadCallbacks.forEach(callback => callback());
            })
            .catch(() => alert("Ошибка сети."));
    });
}

// Инициализация формы изменения пароля
function initPasswordFormHandler() {
    ajaxSubmitForm('password-settings-form', 'password-content', [initPasswordFormHandler]);
}

// Инициализация формы Европочты
function initEvropostFormHandler() {
    ajaxSubmitForm('evropost-settings-form', 'evropost-content', [
        initEvropostFormHandler,
        initializeCustomCredentialsCheckbox
    ]);
}

// Инициализация логики для чекбокса "Использовать пользовательские креды"
function initializeCustomCredentialsCheckbox() {
    const checkbox = document.getElementById("useCustomCredentials");
    const fieldsContainer = document.getElementById("custom-credentials-fields");

    if (checkbox && fieldsContainer) {
        // Первоначальная инициализация состояния формы
        toggleFieldsVisibility(checkbox, fieldsContainer);

        let debounceTimer;

        checkbox.addEventListener('change', function () {
            clearTimeout(debounceTimer);
            debounceTimer = setTimeout(() => {
                fetch('/app/profile/settings/use-custom-credentials', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded',
                        [document.querySelector('meta[name="_csrf_header"]').content]: document.querySelector('meta[name="_csrf"]').content
                    },
                    body: new URLSearchParams({ useCustomCredentials: checkbox.checked })
                })
                    .then(response => {
                        if (response.ok) {
                            toggleFieldsVisibility(checkbox, fieldsContainer);
                        } else {
                            alert("Ошибка при обновлении чекбокса.");
                        }
                    })
                    .catch(() => {
                        alert("Ошибка сети при обновлении чекбокса.");
                    });
            }, 300);
        });
    }
}

// Инициализация переключателя автообновления треков
function initAutoUpdateToggle() {
    const checkbox = document.getElementById("autoUpdateToggle");
    if (!checkbox) return;
    // При недоступном тарифе переключатель заблокирован
    if (checkbox.disabled) {
        checkbox.checked = false; // визуально показываем выключенное состояние
        return;
    }

    let debounceTimer;
    checkbox.addEventListener('change', function () {
        clearTimeout(debounceTimer);
        debounceTimer = setTimeout(() => {
            fetch('/app/profile/settings/auto-update', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    [document.querySelector('meta[name="_csrf_header"]').content]: document.querySelector('meta[name="_csrf"]').content
                },
                body: new URLSearchParams({ enabled: checkbox.checked })
            }).then(response => {
                if (!response.ok) {
                    alert('Ошибка при обновлении настройки.');
                }
            }).catch(() => {
                alert('Ошибка сети при обновлении настройки.');
            });
        }, 300);
    });
}

// Инициализация переключателя отображения кнопки массового обновления
function initBulkButtonToggle() {
    const checkbox = document.getElementById("showBulkUpdateButton");
    if (!checkbox) return;

    // Форма может быть отключена на бесплатном тарифе
    if (checkbox.disabled) {
        checkbox.checked = false; // состояние всегда выключено
        return;
    }
    let debounceTimer;
    checkbox.addEventListener('change', function () {
        clearTimeout(debounceTimer);
        debounceTimer = setTimeout(() => {
            fetch('/app/profile/settings/bulk-button', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    [document.querySelector('meta[name="_csrf_header"]').content]: document.querySelector('meta[name="_csrf"]').content
                },
                body: new URLSearchParams({ show: checkbox.checked })
            }).then(response => {
                if (!response.ok) {
                    alert('Ошибка при обновлении настройки.');
                }
            }).catch(() => {
                alert('Ошибка сети при обновлении настройки.');
            });
        }, 300);
    });
}

// Инициализация глобального переключателя Telegram-уведомлений
function initTelegramNotificationsToggle() {
    const checkbox = document.getElementById('telegramNotificationsToggle');
    if (!checkbox) return;

    const updateFormState = () => {
        document.querySelectorAll('.telegram-settings-form').forEach(form => {
            // При недоступном тарифе блокируем формы и сбрасываем чекбоксы
            if (checkbox.disabled) {
                form.querySelectorAll('input, select, button').forEach(el => {
                    el.disabled = true;
                    if (el.type === 'checkbox' || el.type === 'radio') {
                        el.checked = false;
                    }
                });
            } else {
                const enableCb = form.querySelector('input[name="enabled"]');
                const remindersCb = form.querySelector('input[name="remindersEnabled"]');
                if (enableCb) enableCb.disabled = !checkbox.checked;
                if (remindersCb) remindersCb.disabled = !checkbox.checked;
            }
        });
    };

    updateFormState();

    if (!checkbox.disabled) {
        let debounceTimer;
        checkbox.addEventListener('change', function () {
            updateFormState();
            clearTimeout(debounceTimer);
            debounceTimer = setTimeout(() => {
                fetch('/app/profile/settings/telegram-notifications', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded',
                        [document.querySelector('meta[name="_csrf_header"]').content]: document.querySelector('meta[name="_csrf"]').content
                    },
                    body: new URLSearchParams({ enabled: checkbox.checked })
                }).then(response => {
                    if (!response.ok) {
                        alert('Ошибка при обновлении настройки.');
                    }
                }).catch(() => {
                    alert('Ошибка сети при обновлении настройки.');
                });
            }, 300);
        });
    }
}

// Инициализация переключателя для ввода телефона
function initializePhoneToggle() {
    const toggle = document.getElementById("togglePhone");
    const phoneField = document.getElementById("phoneField");

    if (toggle && phoneField) {
        // Первичное состояние
        toggleFieldsVisibility(toggle, phoneField);

        // Единый обработчик для переключения
        const handler = () => toggleFieldsVisibility(toggle, phoneField);
        toggle.addEventListener('change', handler);
    }
}

// Инициализация формы привязки покупателя к посылке
function initAssignCustomerFormHandler() {
    ajaxSubmitForm('assign-customer-form', 'customerInfoContainer', [initAssignCustomerFormHandler]);
}

/**
 * Инициализирует отправку формы изменения телефона покупателя.
 * После успешного запроса перечитывает данные покупателя и
 * повторно настраивает обработчики формы и кнопки редактирования.
 */
function initEditCustomerPhoneFormHandler() {
    const reloadCallback = () => {
        const idInput = document.querySelector('#edit-phone-form input[name="trackId"]');
        if (idInput) loadCustomerInfo(idInput.value);
    };
    ajaxSubmitForm('edit-phone-form', 'customerInfoContainer', [
        reloadCallback,
        initEditCustomerPhoneFormHandler,
        initAssignCustomerFormHandler,
        initPhoneEditToggle
    ]);
}

/**
 * Назначает обработчик кнопке редактирования телефона,
 * который показывает или скрывает форму ввода номера.
 */
function initPhoneEditToggle() {
    const editBtn = document.getElementById('editPhoneBtn');
    const form = document.getElementById('edit-phone-form');

    if (editBtn && form && !editBtn.dataset.initialized) {
        editBtn.dataset.initialized = 'true';
        editBtn.addEventListener('click', () => form.classList.toggle('hidden'));
    }
}

// Инициализация форм настроек Telegram
function initTelegramForms() {
    const tgToggle = document.getElementById('telegramNotificationsToggle');
    const telegramUnavailable = tgToggle && tgToggle.disabled;

    document.querySelectorAll('.telegram-settings-form').forEach(form => {
        if (form.dataset.initialized) return;
        form.dataset.initialized = 'true';

        if (telegramUnavailable) {
            // Отправка настроек запрещена на базовом тарифе
            const saveBtn = form.querySelector('button[type="submit"]');
            if (saveBtn) saveBtn.disabled = true;
            return;
        }

        form.addEventListener('submit', async function (event) {
            event.preventDefault();

            const formData = new FormData(form);
            const csrfToken = form.querySelector('input[name="_csrf"]')?.value || '';

            try {
                const response = await fetch(form.action, {
                    method: 'POST',
                    headers: { 'X-CSRF-TOKEN': csrfToken },
                    body: formData
                });

                if (response.ok) {
                    // Уведомление придёт через WebSocket
                } else {
                    const errorText = await response.text();
                    // Показываем ошибку непосредственно в форме
                    showInlineNotification(form, errorText || 'Ошибка при сохранении', 'danger');
                }
            } catch (e) {
                // В случае сетевой ошибки также выводим сообщение в форме
                showInlineNotification(form, 'Ошибка сети при сохранении', 'danger');
            }
        });
    });
}

// Показать или скрыть поля
function slideDown(element, duration = 200) {
    element.classList.remove('hidden');
    element.style.removeProperty('display');
    let height = element.scrollHeight;
    element.style.overflow = 'hidden';
    element.style.maxHeight = '0';
    element.offsetHeight; // принудительный reflow
    element.style.transition = `max-height ${duration}ms ease`;
    element.style.maxHeight = height + 'px';
    setTimeout(() => {
        element.style.removeProperty('max-height');
        element.style.removeProperty('overflow');
        element.style.removeProperty('transition');
    }, duration);
}

function slideUp(element, duration = 200) {
    element.style.overflow = 'hidden';
    element.style.maxHeight = element.scrollHeight + 'px';
    element.offsetHeight; // принудительный reflow
    element.style.transition = `max-height ${duration}ms ease`;
    element.style.maxHeight = '0';
    setTimeout(() => {
        element.classList.add('hidden');
        element.style.removeProperty('max-height');
        element.style.removeProperty('overflow');
        element.style.removeProperty('transition');
    }, duration);
}

function toggleFieldsVisibility(checkbox, fieldsContainer) {
    if (!fieldsContainer) return;
    if (checkbox.checked) {
        slideDown(fieldsContainer);
    } else {
        slideUp(fieldsContainer);
    }
}

// --- Работа с состоянием блоков настроек Telegram
const TG_COLLAPSED_KEY = "collapsedTgStores";

function getCollapsedTgStores() {
    return JSON.parse(localStorage.getItem(TG_COLLAPSED_KEY)) || [];
}

function saveCollapsedTgStores(ids) {
    localStorage.setItem(TG_COLLAPSED_KEY, JSON.stringify(ids));
}

function initTelegramToggle() {
    const container = document.getElementById('telegram-management');
    if (!container) return;

    const collapsedStored = getCollapsedTgStores();

    collapsedStored.forEach(storeId => {
        const content = container.querySelector(`.tg-settings-content[data-store-id="${storeId}"]`);
        const btn = container.querySelector(`.toggle-tg-btn[data-store-id="${storeId}"]`);
        if (content && btn) {
            content.classList.remove('expanded');
            content.classList.add('collapsed');
            const icon = btn.querySelector('i');
            icon?.classList.remove('bi-chevron-up');
            icon?.classList.add('bi-chevron-down');
        }
    });

    container.querySelectorAll('.toggle-tg-btn').forEach(btn => {
        if (btn.dataset.toggleInit) return;
        btn.dataset.toggleInit = 'true';

        const handler = (e) => {
            e.preventDefault();
            const storeId = btn.getAttribute('data-store-id');
            const content = container.querySelector(`.tg-settings-content[data-store-id="${storeId}"]`);
            const icon = btn.querySelector('i');

            if (!content) return;

            const collapsed = content.classList.toggle('collapsed');
            content.classList.toggle('expanded', !collapsed);

            icon?.classList.toggle('bi-chevron-down', collapsed);
            icon?.classList.toggle('bi-chevron-up', !collapsed);

            debugLog(`⚙️ Telegram блок магазина ${storeId} toggled. collapsed=${collapsed}. event=${e.type}`);

            let ids = getCollapsedTgStores();
            if (collapsed) {
                if (!ids.includes(storeId)) ids.push(storeId);
            } else {
                ids = ids.filter(id => id !== storeId);
            }
            saveCollapsedTgStores(ids);
        };

        btn.addEventListener('click', handler);
        btn.addEventListener('touchstart', handler);
    });
}

// Инициализация зависимых полей Telegram
function initTelegramReminderBlocks() {
    document.querySelectorAll('.telegram-settings-form').forEach(form => {
        const enabledCb = form.querySelector('input[name="enabled"]');
        const remindersBlock = form.querySelector('.reminders-container');
        const remindersCb = form.querySelector('input[name="remindersEnabled"]');
        const reminderFields = form.querySelector('.reminder-fields');

        if (!enabledCb) return;

        const updateVisibility = () => {
            if (remindersBlock) toggleFieldsVisibility(enabledCb, remindersBlock);
            if (reminderFields && remindersCb) {
                if (enabledCb.checked) {
                    toggleFieldsVisibility(remindersCb, reminderFields);
                } else {
                    reminderFields.classList.add('hidden');
                }
            }
        };

        // Первоначальное состояние
        updateVisibility();

        enabledCb.addEventListener('change', updateVisibility);
        remindersCb?.addEventListener('change', () => {
            if (reminderFields) toggleFieldsVisibility(remindersCb, reminderFields);
        });
    });
}

// Инициализация блока пользовательских шаблонов
function initTelegramTemplateBlocks() {
    document.querySelectorAll('.telegram-settings-form').forEach(form => {
        const radios = form.querySelectorAll('input[name="useCustomTemplates"]');
        const fields = form.querySelector('.custom-template-fields');
        if (!radios.length || !fields) return;

        const getSelectedMode = () => {
            const checked = form.querySelector('input[name="useCustomTemplates"]:checked');
            return checked ? checked.value : 'system';
        };

        const update = () => {
            const custom = getSelectedMode() === 'custom';
            fields.querySelectorAll('textarea').forEach(t => {
                t.disabled = !custom;
            });
            fields.classList.toggle('bg-light', !custom);
            fields.classList.toggle('text-muted', !custom);
        };

        update();

        radios.forEach(r => r.addEventListener('change', update));
    });
}


let lastPage = window.location.pathname; // Запоминаем текущую страницу при загрузке
let isInitialLoad = true;

document.addEventListener("visibilitychange", function () {
    if (isInitialLoad) {
        isInitialLoad = false;
        debugLog("Страница только что загрузилась, состояние: " + document.visibilityState);
        return;
    }

    if (document.hidden) {
        debugLog("🔴 Пользователь ушёл со страницы");
        lastPage = window.location.pathname;
    } else {
        debugLog("🟢 Пользователь вернулся на страницу");
        lastPage = window.location.pathname;
    }
});

// Определяем, есть ли уже открытое модальное окно
function isModalOpen() {
    return document.querySelector(".modal.show") !== null;
}

// Функция выбора уведомления
function notifyUser(message, type = "info") {
    setTimeout(() => { // ⏳ Даем 100мс на закрытие модалки
        if (document.hidden || window.location.pathname !== lastPage || isModalOpen()) {
            debugLog("📢 Показываем toast, так как пользователь сменил страницу или уже в модальном окне");
            showToast(message, type);
        } else {
            debugLog("✅ Показываем alert, так как пользователь остаётся на странице");
            showAlert(message, type);
        }
    }, 100); // 🔥 100мс - небольшая задержка
}

// Уведомления
function showAlert(message, type) {
    // Находим контейнер уведомлений на странице
    const notificationContainer = document.querySelector('#storeNotificationContainer')
        || document.querySelector('#evropostNotificationContainer')
        || document.querySelector('#notificationContainer');

    if (!notificationContainer) {
        console.warn("❌ Не найден контейнер для уведомлений!");
        return;
    }

    let existingAlert = document.querySelector(".notification"); // Берём только первый найденный alert

    // ❌ Игнорируем "Обновление запущено...", так как оно временное
    if (message.includes("Обновление запущено")) {
        debugLog("⚠ Пропущено уведомление:", message);
        return;
    }

    // Проверяем, есть ли уже уведомление с таким же текстом
    if (existingAlert) {
        let currentMessage = existingAlert.querySelector("span.alert-text")?.textContent || "";
        if (currentMessage === message) {
            debugLog("⚠ Повторное уведомление проигнорировано:", message);
            return;
        }
        existingAlert.remove(); // Удаляем старое уведомление перед добавлением нового
    }

    // Создаём HTML уведомления
    const alertHtml = `
    <div class="alert alert-${type} alert-dismissible fade show notification" role="alert">
        <i class="bi ${type === 'success' ? 'bi-check-circle-fill' : 'bi-exclamation-triangle-fill'} me-2"></i>
        <span class="alert-text">${message}</span>
        <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Закрыть"></button>
    </div>`;

    notificationContainer.insertAdjacentHTML("afterbegin", alertHtml);

    // Убираем уведомление через 5 секунд
    setTimeout(() => {
        let notification = document.querySelector(".notification");
        if (notification) {
            notification.style.transition = "opacity 0.5s";
            notification.style.opacity = "0";
            setTimeout(() => notification.remove(), 500);
        }
    }, 5000);
}

// Функция для показа Toast (если пользователь ушёл или уже в модальном окне)
function showToast(message, type = "info") {
    let toastContainer = document.getElementById("globalToastContainer");
    if (!toastContainer) {
        console.warn("❌ Не найден контейнер для тостов!");
        return;
    }

    let toastId = "toast-" + new Date().getTime();
    let toastHtml = `
        <div id="${toastId}" class="toast align-items-center text-bg-${type} border-0 mb-2" role="alert" aria-live="assertive" aria-atomic="true">
          <div class="d-flex">
            <div class="toast-body">${message}</div>
            <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>
          </div>
        </div>
    `;

    toastContainer.insertAdjacentHTML("beforeend", toastHtml);
    let toastElement = document.getElementById(toastId);
    let toast = new bootstrap.Toast(toastElement, { delay: 5000 });
    toast.show();

    toastElement.addEventListener("hidden.bs.toast", () => {
        toastElement.remove();
    });
}

/**
 * Показывает встроенное уведомление в форме.
 * Предыдущее уведомление удаляется, чтобы не дублировать сообщения.
 * @param {HTMLFormElement} form - форма, в которую вставляется уведомление
 * @param {string} message - текст уведомления
 * @param {string} type - тип Bootstrap-алерта (success, danger, warning, ...)
 */
function showInlineNotification(form, message, type = 'danger') {
    if (!form) return;

    // Если в форме уже есть уведомление, удаляем его
    const existing = form.querySelector('.inline-notification');
    if (existing) existing.remove();

    // Создаём элемент уведомления
    const alertDiv = document.createElement('div');
    alertDiv.className = `alert alert-${type} alert-dismissible fade show inline-notification mb-2`;
    alertDiv.role = 'alert';
    alertDiv.innerHTML = `
        <span class="alert-text">${message}</span>
        <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Закрыть"></button>`;

    // Добавляем уведомление в начало формы
    form.prepend(alertDiv);
}

let stompClient = null;
let userId = document.getElementById("userId")?.value || ""; // Получаем userId из скрытого поля

function connectWebSocket() {
    debugLog("🚀 connectWebSocket() вызван!");

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.host;
    stompClient = new StompJs.Client({
        brokerURL: `${protocol}//${host}/ws`,
        reconnectDelay: 1000,
        heartbeatIncoming: 0,
        heartbeatOutgoing: 0,
        debug: function (str) {
            debugLog('STOMP Debug: ', str);
        }
    });

    stompClient.onConnect = function (frame) {
        debugLog('✅ WebSocket подключен: ' + frame);

        let destination = '/topic/status/' + userId;
        debugLog("📡 Подписываемся на " + destination);

        if (stompClient.connected) {
            stompClient.subscribe(destination, function (message) {
                let response = JSON.parse(message.body);
                debugLog("📡 WebSocket сообщение: ", response);

                debugLog("⚠️ DEBUG: success=", response.success, "message=", response.message);

                notifyUser(response.message, response.success ? "success" : "warning");

                let applyActionBtn = document.getElementById("applyActionBtn");
                if (applyActionBtn) {
                    applyActionBtn.disabled = false;
                    applyActionBtn.innerHTML = "Применить";
                }

                let refreshAllBtn = document.getElementById("refreshAllBtn");
                if (refreshAllBtn) {
                    refreshAllBtn.disabled = false;
                    refreshAllBtn.innerHTML = '<i class="bi bi-arrow-repeat"></i>';
                }

                // 🔥 Загружаем обновлённые данные из БД
                if (response.success && response.message.startsWith("Обновление завершено")) {
                    reloadParcelTable();
                }
            });
        } else {
            console.error("❌ STOMP не подключен! Повторная попытка подписки через 2 сек...");
            setTimeout(() => {
                connectWebSocket();
            }, 2000);
        }
    };

    stompClient.onStompError = function (frame) {
        console.error('❌ STOMP ошибка: ', frame);
        notifyUser("Ошибка WebSocket: " + frame.headers['message'], "danger");
    };

    debugLog("🔄 WebSocket активация отправлена...");
    stompClient.activate();
}

/**
 * Загружает актуальные данные таблицы.
 * Запрос выполняется по текущему URL, чтобы сохранить все параметры
 * фильтрации и сортировки.
 */
function reloadParcelTable() {
    debugLog("🔄 AJAX-запрос для обновления таблицы...");

    const url = new URL(window.location.href);

    fetch(url.toString(), { method: "GET", cache: "no-store" })
        .then(response => {
            if (!response.ok) {
                throw new Error("Ошибка загрузки данных");
            }
            return response.text();
        })
        .then(html => {
            let parser = new DOMParser();
            let doc = parser.parseFromString(html, "text/html");
            let newTableBody = doc.querySelector("tbody")?.innerHTML || "";

            if (newTableBody) {
                let currentTbody = document.querySelector("tbody");
                if (currentTbody) {
                    currentTbody.innerHTML = newTableBody;
                    debugLog("✅ Таблица обновлена!");
                }
            }
        })
        .catch(error => {
            console.error("❌ Ошибка загрузки обновлённых данных!", error);
        });
}

let activeTooltip = null; // Храним текущий tooltip

/**
 * Функция для создания и управления tooltips
 * Работает корректно как на ПК, так и на мобильных устройствах
 */
function enableTooltips(root = document) {
    const supportsHover = window.matchMedia('(hover: hover)').matches; // Проверяем поддержку наведения

    root.querySelectorAll('[data-bs-toggle="tooltip"]').forEach(tooltipTriggerEl => {
        // Если tooltip уже инициализирован для элемента, пропускаем его
        if (bootstrap.Tooltip.getInstance(tooltipTriggerEl)) return;

        // Создаём новый tooltip
        const newTooltip = new bootstrap.Tooltip(tooltipTriggerEl, {
            trigger: 'manual', // Управляем вручную
            placement: 'top',
        });

        // === Наведение мыши (ПК) ===
        if (supportsHover) {
            tooltipTriggerEl.addEventListener("mouseenter", function () {
                if (activeTooltip && activeTooltip !== newTooltip) {
                    activeTooltip.hide();
                }
                newTooltip.show();
                activeTooltip = newTooltip;
            });

            // === Уход курсора (ПК) ===
            tooltipTriggerEl.addEventListener("mouseleave", function () {
                newTooltip.hide();
                if (activeTooltip === newTooltip) {
                    activeTooltip = null;
                }
            });
        }

        // === Клик/Тап для мобильных устройств ===
        tooltipTriggerEl.addEventListener("click", function (e) {
            // Останавливаем всплытие, чтобы глобальный обработчик клика не сработал сразу
            e.stopPropagation();
            if (activeTooltip === newTooltip) {
                newTooltip.hide();
                activeTooltip = null;
            } else {
                if (activeTooltip) {
                    activeTooltip.hide();
                }
                newTooltip.show();
                activeTooltip = newTooltip;
            }
        });
    });
}

// Глобальный обработчик для клика вне tooltip (применяется один раз)
document.addEventListener("click", function (event) {
    if (activeTooltip) {
        // Если клик произошёл вне активного элемента с tooltip
        const tooltipEl = activeTooltip._element;
        if (tooltipEl && !tooltipEl.contains(event.target)) {
            activeTooltip.hide();
            activeTooltip = null;
        }
    }
}, true);

let storeToDelete = null;
let analyticsActionUrl = null;

/**
 * Загружает магазины пользователя и обновляет таблицу
 */
async function loadStores() {
    const response = await fetch('/app/profile/stores');
    if (!response.ok) {
        console.error("Ошибка загрузки магазинов:", await response.text());
        return;
    }

    const stores = await response.json();
    const tableBody = document.getElementById('storeTableBody');

    if (!tableBody) {
        console.warn("⚠️ Таблица 'storeTableBody' не найдена в DOM!");
        return;
    }

    tableBody.innerHTML = "";

    stores.forEach(store => {
        const row = document.createElement("tr");
        // Первый столбец содержит идентификатор магазина и выравнивается по центру
        row.innerHTML = `
            <td class="text-center store-id">${store.id}</td>
            <td class="d-flex align-items-center">
                <input type="radio" name="defaultStore"
                       class="default-store-radio me-2"
                       data-store-id="${store.id}"
                       ${store.default ? "checked" : ""}
                       data-bs-toggle="tooltip"
                       title="Магазин по умолчанию">
                <input type="text" class="form-control store-name-input" value="${store.name}" id="store-name-${store.id}" disabled>
            </td>
            <td>
                <button class="btn btn-sm btn-outline-primary edit-store-btn" data-store-id="${store.id}">
                    <i class="bi bi-pencil"></i>
                </button>
                <button class="btn btn-sm btn-outline-success save-store-btn d-none" data-store-id="${store.id}">
                    <i class="bi bi-check"></i>
                </button>
                <button class="btn btn-sm btn-outline-danger delete-store-btn" data-store-id="${store.id}">
                    <i class="bi bi-trash"></i>
                </button>
            </td>
        `;
        tableBody.appendChild(row);
    });

    // Инициализируем tooltips после загрузки магазинов
    enableTooltips();

    // --- Инициализируем состояние блоков Telegram
    initTelegramToggle();

    console.info("✅ Магазины успешно загружены и отрисованы.");
}

/**
 * Загружает магазины и формирует кнопки для очистки аналитики.
 */
async function loadAnalyticsButtons() {
    const response = await fetch('/app/profile/stores');
    if (!response.ok) return;

    const stores = await response.json();
    const container = document.getElementById('storeAnalyticsButtons');
    if (!container) return;

    container.innerHTML = '';

    stores.forEach(store => {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'btn btn-outline-secondary btn-sm reset-store-analytics-btn d-inline-flex align-items-center';
        btn.dataset.storeId = store.id;
        btn.dataset.storeName = store.name;
        btn.setAttribute('data-bs-toggle', 'tooltip');
        btn.title = `Очистить аналитику магазина «${store.name}»`;
        btn.innerHTML = `<i class="bi bi-brush me-2"></i> Очистить аналитику — ${store.name}`;

        btn.addEventListener('click', () => {
            analyticsActionUrl = `/app/analytics/reset/store/${store.id}`;
            showResetModal(`Вы действительно хотите очистить аналитику магазина «${store.name}»?`);
        });

        container.appendChild(btn);
    });

    enableTooltips(container);
}


/**
 * Формирует DOM-блок настроек Telegram для магазина
 */
async function renderTelegramBlock(storeId) {
    const response = await fetch(`/app/profile/stores/${storeId}/telegram-block`);
    if (!response.ok) return null;

    const html = await response.text();
    const wrapper = document.createElement('div');
    wrapper.innerHTML = html.trim();
    return wrapper.firstElementChild;
}

/**
 * Загружает настройки Telegram и добавляет блок на страницу
 */
async function appendTelegramBlock(store) {
    const storeId = typeof store === 'object' ? store.id : store;

    const block = await renderTelegramBlock(storeId);
    if (!block) return;
    document.getElementById('telegram-management').appendChild(block);

    // Инициализируем подсказки в добавленном блоке
    enableTooltips(block);

    // --- Инициализируем формы и collapse
    initTelegramForms();
    initTelegramToggle();
    initTelegramReminderBlocks();
    initTelegramTemplateBlocks();
    initTelegramNotificationsToggle();
}

/**
 * Включает/выключает редактирование для магазина
 */
function toggleEditStore(storeId) {
    const inputField = document.getElementById(`store-name-${storeId}`);
    const editBtn = document.querySelector(`.edit-store-btn[data-store-id="${storeId}"]`);
    const saveBtn = document.querySelector(`.save-store-btn[data-store-id="${storeId}"]`);
    const deleteBtn = document.querySelector(`.delete-store-btn[data-store-id="${storeId}"], .cancel-edit-store-btn[data-store-id="${storeId}"]`);

    if (inputField.disabled) {
        // Включаем редактирование
        inputField.disabled = false;
        inputField.focus();

        editBtn.classList.add('d-none');
        saveBtn.classList.remove('d-none');

        // Меняем "Удалить" на "Отменить"
        deleteBtn.classList.remove("delete-store-btn");
        deleteBtn.classList.add("cancel-edit-store-btn");
        deleteBtn.innerHTML = `<i class="bi bi-x"></i>`;
    } else {
        // Выключаем редактирование
        inputField.disabled = true;

        editBtn.classList.remove('d-none');
        saveBtn.classList.add('d-none');

        // Возвращаем кнопку "Удалить"
        deleteBtn.classList.remove("cancel-edit-store-btn");
        deleteBtn.classList.add("delete-store-btn");
        deleteBtn.innerHTML = `<i class="bi bi-trash"></i>`;
    }
}

const baseUrl = "/app/profile/stores"; // Базовый URL для всех запросов

/**
 * Сохраняет обновленное название магазина
 */
async function saveStore(storeId) {
    const inputField = document.getElementById(`store-name-${storeId}`);
    const newName = inputField.value.trim();

    if (!newName) {
        alert("Название не может быть пустым!");
        return;
    }

    const response = await fetch(`${baseUrl}/${storeId}`, { // ✅ Правильный путь
        method: "PUT",
        headers: {
            "Content-Type": "application/json",
            [document.querySelector('meta[name="_csrf_header"]').content]: document.querySelector('meta[name="_csrf"]').content
        },
        body: JSON.stringify({ name: newName })
    });

    if (response.ok) {
        loadStores();
        loadAnalyticsButtons();
    } else {
        alert("Ошибка обновления: " + await response.text());
    }
}

/**
 * Добавляет новую строку для магазина
 */
function addNewStore() {
    const tableBody = document.getElementById("storeTableBody");
    const tempId = `new-${Date.now()}`; // Уникальный ID для нового магазина

    const row = document.createElement("tr");
    // Строка новой записи с центровкой ID для согласованности с таблицей
    row.innerHTML = `
        <td class="text-center store-id">—</td>
        <td>
            <input type="text" class="form-control store-name-input" id="store-name-${tempId}" placeholder="Введите название">
        </td>
        <td>
            <button type="button" class="btn btn-sm btn-outline-success save-new-store-btn" data-store-id="${tempId}">
                <i class="bi bi-check"></i>
            </button>
            <button type="button" class="btn btn-sm btn-outline-danger remove-new-store-btn" data-store-id="${tempId}">
                <i class="bi bi-x"></i>
            </button>
        </td>
    `;

    tableBody.appendChild(row);

    // Фокусируемся на поле ввода
    document.getElementById(`store-name-${tempId}`).focus();
}

/**
 * Удаляет строку, если пользователь передумал добавлять магазин
 */
function removeNewStoreRow(button) {
    button.closest("tr").remove();
}

/**
 * Сохраняет новый магазин
 */
async function saveNewStore(event) {
    event.preventDefault(); // ❗ Предотвращаем стандартное поведение

    const button = event.target.closest(".save-new-store-btn");
    if (!button) return;

    const storeId = button.dataset.storeId;
    const inputField = document.getElementById(`store-name-${storeId}`);
    const newStoreName = inputField?.value.trim();

    if (!newStoreName) {
        alert("Название не может быть пустым!");
        return;
    }

    const response = await fetch("/app/profile/stores", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            [document.querySelector('meta[name="_csrf_header"]').content]: document.querySelector('meta[name="_csrf"]').content
        },
        body: JSON.stringify({ name: newStoreName })
    });

    if (response.ok) {
        const newStore = await response.json();
        loadStores(); // Обновляем список магазинов
        updateStoreLimit();
        loadAnalyticsButtons();
        appendTelegramBlock(newStore);
    } else {
        console.warn("Ошибка при создании магазина: ", await response.text());
        return;
    }
}

/**
 * Открывает модальное окно подтверждения удаления
 */
function confirmDeleteStore(storeId) {
    storeToDelete = storeId;
    new bootstrap.Modal(document.getElementById('deleteStoreModal')).show();
}

/**
 * Удаляет магазин после подтверждения
 */
async function deleteStore() {
    if (!storeToDelete) return;

    const response = await fetch(`${baseUrl}/${storeToDelete}`, { // ✅ Правильный путь
        method: "DELETE",
        headers: {
            [document.querySelector('meta[name="_csrf_header"]').content]: document.querySelector('meta[name="_csrf"]').content
        }
    });

    if (response.ok) {
        loadStores();
        updateStoreLimit();
        loadAnalyticsButtons();
        const storeElement = document.querySelector(`#store-block-${storeToDelete}`);
        if (storeElement) {
            storeElement.remove();
        }

        // Очищаем сохранённый ID из localStorage
        let collapsed = getCollapsedTgStores();
        collapsed = collapsed.filter(id => id !== String(storeToDelete));
        saveCollapsedTgStores(collapsed);
    } else {
        alert("Ошибка при удалении: " + await response.text());
    }

    storeToDelete = null;
    bootstrap.Modal.getInstance(document.getElementById('deleteStoreModal')).hide();
}


/**
 * Обновляет отображение лимита магазинов
 */
async function updateStoreLimit() {
    try {
        const response = await fetch('/app/profile/stores/limit');
        if (!response.ok) {
            console.error("Ошибка при получении лимита магазинов:", response.status);
            return;
        }

        const newLimit = await response.text();
        const storeLimitElement = document.getElementById("store-limit");

        if (storeLimitElement) {
            storeLimitElement.textContent = newLimit;
        } else {
            console.warn("Элемент #store-limit не найден, невозможно обновить лимит магазинов.");
        }
    } catch (error) {
        console.error("Ошибка при обновлении лимита магазинов:", error);
    }
}

function showResetModal(message) {
    const modalEl = document.getElementById('resetAnalyticsModal');
    const msgEl = document.getElementById('resetAnalyticsMessage');
    const confirmBtn = document.getElementById('confirmResetAnalytics');
    if (!modalEl || !msgEl || !confirmBtn) return;
    msgEl.textContent = message;
    const modal = new bootstrap.Modal(modalEl);
    confirmBtn.onclick = async function () {
        if (!analyticsActionUrl) return;
        try {
            const response = await fetch(analyticsActionUrl, { method: 'POST', headers: { [window.csrfHeader]: window.csrfToken } });
            if (response.ok) {
                notifyUser('Аналитика успешно удалена.', 'success');
                loadAnalyticsButtons();
            } else {
                notifyUser('Ошибка при удалении аналитики', 'danger');
            }
        } catch (e) {
            notifyUser('Ошибка сети', 'danger');
        }
        analyticsActionUrl = null;
        modal.hide();
    };
    modal.show();
}

/**
 * Обработчик выбора магазина по умолчанию (с проверкой наличия элемента)
 */
const storeTableBody = document.getElementById("storeTableBody");
if (storeTableBody) {
    storeTableBody.addEventListener("change", async function (event) {
        const radio = event.target.closest(".default-store-radio");
        if (!radio) return;

        const storeId = radio.dataset.storeId;

        try {
            const response = await fetch(`/app/profile/stores/default/${storeId}`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    [document.querySelector('meta[name="_csrf_header"]').content]: document.querySelector('meta[name="_csrf"]').content
                }
            });

            if (!response.ok) {
                const errorText = await response.text();
                notifyUser(`❌ Ошибка: ${errorText}`, "danger");
                return;
            }

            //notifyUser(`Магазин по умолчанию обновлён!`, "success");

            // Перезагружаем магазины, чтобы обновить состояние
            loadStores();
        } catch (error) {
            console.error("❌ Ошибка при установке магазина по умолчанию:", error);
            notifyUser("❌ Ошибка соединения с сервером", "danger");
        }
    });
}

document.addEventListener("DOMContentLoaded", function () {
    debugLog("DOM полностью загружен");

    hideLoading();

    document.querySelector('form[action="/"]')?.addEventListener('submit', showLoading);
    document.querySelector('form[action="/app/upload"]')?.addEventListener('submit', showLoading);
    // форма проверки одной посылки также должна показывать индикатор загрузки
    document.querySelector('form[action="/app"]')?.addEventListener('submit', showLoading);

    // === Добавляем CSRF-токен ===
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content || "";
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || "";
    window.csrfToken = csrfToken;
    window.csrfHeader = csrfHeader;

    // === WebSocket ===
    connectWebSocket();

    document.getElementById("updateAllForm")?.addEventListener("submit", function (event) {
        event.preventDefault();
        sendUpdateRequest(null);
    });

    // === Всплывающие подсказки (tooltips) ===
    enableTooltips();

    // Кнопка копирования ссылки на Telegram-бота
    initTelegramLinkCopy();

    /// Авто-скрытие уведомлений
    setTimeout(() => {
        document.querySelectorAll(".alert").forEach(alert => {
            alert.style.transition = "opacity 0.5s";
            alert.style.opacity = "0";
            setTimeout(() => alert.remove(), 500); // Удаляем после завершения анимации
        });
    }, 10000);

    // мобильный хедер
    const burgerMenu = document.getElementById('burgerMenu');
    const mobileNav = document.getElementById('mobileNav');

    if (burgerMenu && mobileNav) {
        burgerMenu.addEventListener('click', function () {
            mobileNav.classList.toggle('active');
        });
    }

    /**
     * КУКИ
     */
    const cookieModal = document.getElementById("cookieConsentModal");
    const acceptButton = document.getElementById("acceptCookies");
    const declineButton = document.getElementById("declineCookies");

    if (!localStorage.getItem("cookiesAccepted")) {
        setTimeout(() => cookieModal.classList.add("show"), 800);
    }

    acceptButton.addEventListener("click", function () {
        localStorage.setItem("cookiesAccepted", "true");
        setCookie("cookie_consent", "accepted", 365);
        cookieModal.classList.remove("show");
    });

    declineButton.addEventListener("click", function () {
        localStorage.setItem("cookiesAccepted", "false");
        setCookie("cookie_consent", "declined", 365);
        cookieModal.classList.remove("show");
    });

    function setCookie(name, value, days) {
        let expires = "";
        if (days) {
            let date = new Date();
            date.setTime(date.getTime() + days * 24 * 60 * 60 * 1000);
            expires = "; expires=" + date.toUTCString();
        }
        document.cookie = name + "=" + value + "; Path=/; Secure; SameSite=None" + expires;
    }

    function getCookie(name) {
        let nameEQ = name + "=";
        let ca = document.cookie.split(';');
        for (let i = 0; i < ca.length; i++) {
            let c = ca[i].trim();
            if (c.startsWith(nameEQ)) {
                return c.substring(nameEQ.length);
            }
        }
        return null;
    }

    // Если кука нет - показываем окно с задержкой
    if (!getCookie("cookie_consent")) {
        setTimeout(() => cookieModal.classList.add("show"), 800);
    }

    /**
     * Трекинг - Добавление трека - Выбор магазина для трека
     */
    // Выбор магазина при добавлении трека
    const storeSelectDropdown = document.getElementById("storeSelect");
    if (storeSelectDropdown) {
        debugLog('Найден селект с магазинами, количество опций:', storeSelectDropdown.options.length);
        if (storeSelectDropdown.options.length > 1) {
            storeSelectDropdown.classList.remove("d-none");
        }
    } else {
        console.warn('Элемент storeSelect не найден.');
    }

    /**
     * Профиль - работа с магазинами (добавление, удаление) динамичные кнопки
     */
    // Инициализация логики форм
    initPasswordFormHandler();
    initEvropostFormHandler();
    initializeCustomCredentialsCheckbox();
    initAutoUpdateToggle();
    initBulkButtonToggle();
    initializePhoneToggle();
    initAssignCustomerFormHandler();
    initEditCustomerPhoneFormHandler();
    initPhoneEditToggle();
    initTelegramForms();
    initTelegramToggle();
    initTelegramReminderBlocks();
    initTelegramTemplateBlocks();
    initTelegramNotificationsToggle();

    // Назначаем обработчик кнопки "Добавить магазин" - с проверкой на наличие
    const addStoreBtn = document.getElementById("addStoreBtn");
    if (addStoreBtn) {
        addStoreBtn.addEventListener("click", addNewStore);
    }
    // Делегируем обработку кликов внутри таблицы
    const storeTableBody = document.getElementById("storeTableBody");
    if (storeTableBody) {

        // Загружаем лимит магазинов
        updateStoreLimit();

        // Загружаем список магазинов
        loadStores();
        loadAnalyticsButtons();

        storeTableBody.addEventListener("click", function (event) {
            event.preventDefault(); // ❗ ОТМЕНЯЕМ ПЕРЕЗАГРУЗКУ СТРАНИЦЫ

            const button = event.target.closest("button");
            if (!button) return;

            const storeId = button.dataset.storeId;

            if (button.classList.contains("edit-store-btn")) {
                toggleEditStore(storeId);
            }
            if (button.classList.contains("save-store-btn")) {
                saveStore(storeId);
            }
            if (button.classList.contains("delete-store-btn")) {
                confirmDeleteStore(storeId);
            }
            if (button.classList.contains("cancel-edit-store-btn")) {
                toggleEditStore(storeId);
            }
            if (button.classList.contains("save-new-store-btn")) {
                saveNewStore(event);
            }
            if (button.classList.contains("remove-new-store-btn")) {
                removeNewStoreRow(button);
            }
        });
    }

    // Проверяем наличие элемента перед добавлением обработчика - удаление магазина
    const confirmDeleteBtn = document.getElementById("confirmDeleteStore");
    if (confirmDeleteBtn) {
        confirmDeleteBtn.addEventListener("click", deleteStore);
    }

    // === Управление аналитикой ===
    document.getElementById("resetAllAnalyticsBtn")?.addEventListener("click", () => {
        analyticsActionUrl = "/app/analytics/reset/all";
        showResetModal("Вы уверены, что хотите удалить всю аналитику?");
    });

    document.body.addEventListener("click", function (event) {
        const btn = event.target.closest(".reset-store-analytics-btn");
        if (!btn) return;
        analyticsActionUrl = `/app/analytics/reset/store/${btn.dataset.storeId}`;
        showResetModal(`Очистить аналитику магазина \u00AB${btn.dataset.storeName}\u00BB?`);
    });

    /**
     * Отправления - модальное окно каждого трека с информацией
     */
    document.body.addEventListener("click", function (event) {
        const target = event.target;

        const openModalButton = target.closest(".open-modal, .btn-link");
        if (openModalButton) {
            const itemNumber = openModalButton.getAttribute("data-itemnumber");
            if (itemNumber) loadModal(itemNumber);
            console.log('loadModal called:', itemNumber)
            return;
        }

        const customerIcon = target.closest(".customer-icon");
        if (customerIcon) {
            const trackId = customerIcon.getAttribute("data-trackid");
            if (trackId) loadCustomerInfo(trackId);
            return;
        }
    });

    // Проверяем, есть ли модальное окно на странице
    let modalElement = document.getElementById('infoModal');
    if (modalElement) {
        modalElement.addEventListener('hidden.bs.modal', function () {
            let backdrop = document.querySelector('.modal-backdrop');
            if (backdrop) {
                backdrop.remove(); // Удаляем затемнение вручную
            }
            document.body.classList.remove('modal-open'); // Убираем класс, если остался
            document.body.style.overflow = ''; // Восстанавливаем прокрутку
        });
    }

    let customerModalElement = document.getElementById('customerModal');
    if (customerModalElement) {
        customerModalElement.addEventListener('hidden.bs.modal', function () {
            let backdrop = document.querySelector('.modal-backdrop');
            if (backdrop) {
                backdrop.remove();
            }
            document.body.classList.remove('modal-open');
            document.body.style.overflow = '';
        });
    }

    //установка активной вкладки в хедере
    const currentPath = window.location.pathname;
    document.querySelectorAll(".nav-link").forEach(link => {
        if (link.getAttribute("data-path") === currentPath) {
            link.classList.add("active");
        }
    });

    // Запоминание активной вкладки и анимация при переключении
    const tabKey = "profileActiveTab";
    const tabLinks = document.querySelectorAll('.profile-tab-menu a');
    const savedTab = localStorage.getItem(tabKey);
    if (savedTab) {
        const links = document.querySelectorAll(`.profile-tab-menu a[href="${savedTab}"]`);
        if (links.length > 0) {
            bootstrap.Tab.getOrCreateInstance(links[0]).show();
            setActiveProfileTab(savedTab);
        }
    }
    tabLinks.forEach(link => {
        link.addEventListener('shown.bs.tab', e => {
            const href = e.target.getAttribute('href');
            setActiveProfileTab(href);
            localStorage.setItem(tabKey, href);
            const pane = document.querySelector(href);
            if (pane) {
                pane.classList.add('animate__animated', 'animate__fadeIn');
                pane.addEventListener('animationend', () => {
                    pane.classList.remove('animate__animated', 'animate__fadeIn');
                }, { once: true });
            }
        });
    });

    // Логика показа/скрытия пароля
    document.querySelectorAll(".toggle-password").forEach(button => {
        button.addEventListener("click", function () {
            const targetId = this.getAttribute("data-target");
            const input = document.getElementById(targetId);
            const icon = this.querySelector("i");

            if (!input || !icon) return;

            const isPassword = input.type === "password";
            input.type = isPassword ? "text" : "password";
            icon.classList.toggle("bi-eye");
            icon.classList.toggle("bi-eye-slash");
        });
    });

    // Закрытие Offcanvas при выборе пункта меню
    const sidebar = document.getElementById("settingsSidebar");

    if (sidebar) {
        sidebar.querySelectorAll(".nav-link").forEach(link => {
            link.addEventListener("click", function () {
                const offcanvasInstance = bootstrap.Offcanvas.getInstance(sidebar);
                if (offcanvasInstance) {
                    offcanvasInstance.hide();
                    setTimeout(() => {
                        const backdrop = document.querySelector(".offcanvas-backdrop");
                        if (backdrop) backdrop.remove();
                    }, 300);
                }
            });
        });
    }

    // === Обработчик выбора количества элементов ===
    document.querySelectorAll(".size-btn").forEach(button => {
        button.addEventListener("click", function () {
            // Убираем класс "active" у всех кнопок
            document.querySelectorAll(".size-btn").forEach(btn => btn.classList.remove("active"));

            // Добавляем "active" только на нажатую кнопку
            this.classList.add("active");

            // Получаем размер из атрибута data-size
            const size = this.getAttribute("data-size");

            // Обновляем URL, меняя параметр "size"
            const currentUrl = new URL(window.location.href);
            currentUrl.searchParams.set("size", size);
            if (searchInput && searchInput.value.trim()) {
                currentUrl.searchParams.set("query", searchInput.value.trim());
            }

            // Перенаправляем пользователя на обновленный URL

            window.location.href = currentUrl.toString();
        });
    });

    // === Сортировка по дате ===
    // Инициализируем обработчик для переключения параметра sortOrder
    initSortDateToggle();

    const selectAllCheckbox = document.getElementById("selectAllCheckbox");

    // Обработчик клика: включает/выключает все чекбоксы
    selectAllCheckbox?.addEventListener("click", function () {
        toggleAllCheckboxes(this.checked);
    });

    // Обработчик изменений: если чекбоксы выбраны/сняты вручную
    document.body.addEventListener("change", function (event) {
        if (event.target.id === "selectAllCheckbox") {
            toggleAllCheckboxes(event.target.checked);
        }
    });

    document.body.addEventListener("change", function (event) {
        if (event.target.classList.contains("selectCheckbox")) {
            const allCheckboxes = document.querySelectorAll(".selectCheckbox");
            const checkedCheckboxes = document.querySelectorAll(".selectCheckbox:checked");
            const selectAllCheckbox = document.getElementById("selectAllCheckbox");

            selectAllCheckbox.checked = allCheckboxes.length > 0 && checkedCheckboxes.length === allCheckboxes.length;
            updateApplyButtonState();
        }
    });

    // === Обработчик кнопки "Применить" ===
    document.getElementById("applyActionBtn")?.addEventListener("click", function () {
        const selectedNumbers = Array.from(document.querySelectorAll(".selectCheckbox:checked"))
            .map(checkbox => checkbox.value);

        const selectedAction = document.getElementById("actionSelect").value;
        const applyBtn = document.getElementById("applyActionBtn");

        if (selectedNumbers.length === 0) {
            notifyUser("Выберите хотя бы одну посылку.", "warning");
            return;
        }

        if (!selectedAction) {
            notifyUser("Выберите действие перед нажатием кнопки.", "warning");
            return;
        }

        applyBtn.disabled = true;
        applyBtn.innerHTML = '<i class="bi bi-arrow-repeat spin"></i> Выполняется...';

        if (selectedAction === "delete") {
            sendDeleteRequest(selectedNumbers, applyBtn);
        } else if (selectedAction === "update") {
            sendUpdateRequest(selectedNumbers, applyBtn);
        }
    });

    updateApplyButtonState();

    // === Обработчик кнопки "Обновить всё" ===
    document.getElementById("refreshAllBtn")?.addEventListener("click", function () {
        const refreshBtn = this;
        refreshBtn.disabled = true;
        refreshBtn.innerHTML = '<i class="bi bi-arrow-repeat spin"></i>';

        fetch("/app/departures/track-update", {
            method: "POST",
            headers: {
                [csrfHeader]: csrfToken // CSRF-токен
            }
        })
            .then(response => {
                if (!response.ok) {
                    return response.text().then(text => { throw new Error(text); });
                }
                debugLog("✅ AJAX-запрос для обновления всех треков отправлен. Ждём WebSocket...");
            })
            .catch(error => {
                notifyUser("Ошибка при обновлении: " + error.message, "danger");
                refreshBtn.disabled = false;
                refreshBtn.innerHTML = '<i class="bi bi-arrow-repeat"></i>';
            });
    });

    // Получаем элементы фильтров: статус и магазин
    const statusFilterDropdown  = document.getElementById("status");
    const storeFilterDropdown = document.getElementById("storeId");
    const searchInput = document.getElementById("search");
    const searchBtn = document.getElementById("searchBtn");

    // Проверяем, существует ли фильтр по статусу (если нет - выходим)
    if (!statusFilterDropdown) return;

    // Если фильтра магазинов нет (потому что 1 магазин), отключаем работу с ним
    if (!storeFilterDropdown) {
        console.warn("ℹ️ Фильтр по магазинам скрыт, но фильтр по статусу работает.");
    } else {
        // Если магазин 1, скрываем фильтр
        if (storeFilterDropdown.options.length <= 2) {
            storeFilterDropdown.closest(".filter-group").classList.add("d-none");
        }
    }

    // Восстанавливаем значения фильтров из URL (чтобы при обновлении страницы они оставались)
    const currentUrl = new URL(window.location.href);
    const currentStatus = currentUrl.searchParams.get("status");
    const currentStore = currentUrl.searchParams.get("storeId");
    const currentQuery = currentUrl.searchParams.get("query");

    // Устанавливаем значения селекторов, если в URL были параметры
    if (currentStatus) statusFilterDropdown.value = currentStatus;
    if (currentStore && storeFilterDropdown) storeFilterDropdown.value = currentStore;
    if (currentQuery && searchInput) searchInput.value = currentQuery;

    /**
     * Функция применения фильтров.
     * - Считывает текущие выбранные значения в селекторах.
     * - Обновляет URL с новыми параметрами.
     * - Перезагружает страницу с обновленными фильтрами.
     */
    function applyFilters() {
        const selectedStatus = statusFilterDropdown.value;
        const selectedStore = storeFilterDropdown ? storeFilterDropdown.value : null;
        const query = searchInput ? searchInput.value.trim() : "";
        const currentUrl = new URL(window.location.href);

        if (selectedStatus) {
            currentUrl.searchParams.set("status", selectedStatus);
        } else {
            currentUrl.searchParams.delete("status");
        }

        if (selectedStore) {
            currentUrl.searchParams.set("storeId", selectedStore);
        } else {
            currentUrl.searchParams.delete("storeId");
        }

        if (query) {
            currentUrl.searchParams.set("query", query);
        } else {
            currentUrl.searchParams.delete("query");
        }

        debugLog("✅ Фильтр применён: статус =", selectedStatus, "магазин =", selectedStore || "нет выбора", "query=", query);

        window.location.href = currentUrl.toString();
    }

    // Автоматическое применение фильтра при изменении значений в селекторах
    statusFilterDropdown.addEventListener("change", applyFilters);
    if (storeFilterDropdown) {
        storeFilterDropdown.addEventListener("change", applyFilters);
    }

    if (searchBtn && searchInput) {
        searchBtn.addEventListener("click", applyFilters);
        searchInput.addEventListener("keypress", function (e) {
            if (e.key === "Enter") {
                e.preventDefault();
                applyFilters();
            }
        });
    }

    document.body.addEventListener("change", function (event) {
        if (event.target.classList.contains("selectCheckbox")) {
            updateDeleteButtonState();
        }
    });

    // === Функция отправки запроса на удаление ===
    function sendDeleteRequest(selectedNumbers, applyBtn) {
        applyBtn.disabled = true;
        applyBtn.innerHTML = "Удаление...";

        const formData = new URLSearchParams();
        selectedNumbers.forEach(number => formData.append("selectedNumbers", number));

        fetch("/app/departures/delete-selected", {
            method: "POST",
            headers: {
                [csrfHeader]: csrfToken // CSRF-токен
            },
            body: formData
        })
            .then(response => {
                if (!response.ok) {
                    return response.text().then(text => { throw new Error(text); });
                }
                notifyUser("Выбранные посылки успешно удалены.", "success");

                const checkedCheckboxes = Array.from(document.querySelectorAll(".selectCheckbox:checked"));
                const rowsToRemove = checkedCheckboxes
                    .map(cb => cb.closest("tr"))
                    .filter(row => row);

                // Анимация исчезновения удалённых строк
                rowsToRemove.forEach(row => {
                    row.style.transition = "opacity 0.5s";
                    row.style.opacity = "0";
                });

                // Удаляем строки и очищаем чекбоксы после завершения анимации
                setTimeout(() => {
                    rowsToRemove.forEach(row => row.remove());
                    clearAllCheckboxes();
                }, 500);
            })
            .catch(error => {
                notifyUser("Ошибка при удалении: " + error.message, "danger");
            })
            .finally(() => {
                applyBtn.disabled = false;
                applyBtn.innerHTML = "Применить";
            });
    }

    function sendUpdateRequest(selectedNumbers, applyBtn) {
        applyBtn.disabled = true;
        applyBtn.innerHTML = '<i class="bi bi-arrow-repeat spin"></i> Обновление...';

        const formData = new URLSearchParams();
        selectedNumbers.forEach(number => formData.append("selectedNumbers", number));

        fetch("/app/departures/track-update", {
            method: "POST",
            headers: {
                [csrfHeader]: csrfToken // CSRF-токен
            },
            body: formData
        })
            .then(response => {
                if (!response.ok) {
                    return response.text().then(text => { throw new Error(text); });
                }
                debugLog("✅ AJAX-запрос отправлен. Ждём уведомления через WebSocket...");

                clearAllCheckboxes();

            })
            .catch(error => {
                notifyUser("Ошибка при обновлении: " + error.message, "danger");
            })
            .finally(() => {
                applyBtn.disabled = false;
                applyBtn.innerHTML = "Применить";
            });
    }

    function clearAllCheckboxes() {
        document.querySelectorAll(".selectCheckbox, #selectAllCheckbox").forEach(checkbox => {
            checkbox.checked = false;
        });

        setTimeout(updateApplyButtonState, 0); // Гарантированно обновляем кнопку после очистки чекбоксов
    }

});
