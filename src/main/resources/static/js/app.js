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

// Обновляем кнопку при изменении чекбоксов
document.body.addEventListener("change", function (event) {
    if (event.target.classList.contains("selectCheckbox")) {
        updateApplyButtonState();
    }
});

document.getElementById("actionSelect")?.addEventListener("change", updateApplyButtonState);

function loadModal(itemNumber) {
    if (!itemNumber) return;

    fetch(`/departures/${itemNumber}`)
        .then(response => {
            if (!response.ok) {
                throw new Error('Ошибка при загрузке данных');
            }
            return response.text();
        })
        .then(data => {
            document.querySelector('#infoModal .modal-body').innerHTML = data;
            let modal = new bootstrap.Modal(document.getElementById('infoModal'));
            modal.show();
        })
        .catch(() => notifyUser('Ошибка при загрузке данных', "danger"));
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
                fetch('/profile/settings/use-custom-credentials', {
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

// Показать или скрыть поля
function toggleFieldsVisibility(checkbox, fieldsContainer) {
    if (checkbox.checked) {
        fieldsContainer.classList.remove('hidden');
    } else {
        fieldsContainer.classList.add('hidden');
    }
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

let stompClient = null;
let userId = document.getElementById("userId")?.value || ""; // Получаем userId из скрытого поля

function connectWebSocket() {
    debugLog("🚀 connectWebSocket() вызван!");

    stompClient = new StompJs.Client({
        //'wss://belivery.by/ws', 'ws://localhost:8080/ws',
        brokerURL: 'ws://localhost:8080/ws',
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

function reloadParcelTable() {
    debugLog("🔄 AJAX-запрос для обновления таблицы...");

    fetch("/departures", { method: "GET", cache: "no-store" })
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
 */
function enableTooltips(root = document) {
    root.querySelectorAll('[data-bs-toggle="tooltip"]').forEach(tooltipTriggerEl => {
        // Если tooltip уже инициализирован для элемента, пропускаем его
        if (bootstrap.Tooltip.getInstance(tooltipTriggerEl)) return;

        // Создаём новый tooltip
        const newTooltip = new bootstrap.Tooltip(tooltipTriggerEl, {
            trigger: 'manual', // Управляем вручную
            placement: 'top',
        });

        // === Наведение мыши (ПК) ===
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

        // === Клик (мобильные устройства) ===
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
    const response = await fetch('/profile/stores');
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
        row.innerHTML = `
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

    console.info("✅ Магазины успешно загружены и отрисованы.");
}

/**
 * Загружает магазины и формирует кнопки для очистки аналитики.
 */
async function loadAnalyticsButtons() {
    const response = await fetch('/profile/stores');
    if (!response.ok) return;

    const stores = await response.json();
    const container = document.getElementById('storeAnalyticsButtons');
    if (!container) return;

    container.innerHTML = '';

    stores.forEach(store => {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'btn btn-outline-secondary w-100 reset-store-analytics-btn mb-2 d-flex align-items-center';
        btn.dataset.storeId = store.id;
        btn.dataset.storeName = store.name;
        btn.setAttribute('data-bs-toggle', 'tooltip');
        btn.title = `Очистить аналитику магазина «${store.name}»`;
        btn.innerHTML = `<i class="bi bi-brush me-2"></i> Очистить аналитику — ${store.name}`;

        // ✅ обработчик клика на кнопку
        btn.addEventListener('click', () => {
            analyticsActionUrl = `/analytics/reset/store/${store.id}`;
            showResetModal(`Вы действительно хотите очистить аналитику магазина «${store.name}»?`);
        });

        container.appendChild(btn);
    });

    // повторно инициализируем Bootstrap tooltip (если используется)
    enableTooltips(container);
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

const baseUrl = "/profile/stores"; // Базовый URL для всех запросов

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
    row.innerHTML = `
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

    const response = await fetch("/profile/stores", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            [document.querySelector('meta[name="_csrf_header"]').content]: document.querySelector('meta[name="_csrf"]').content
        },
        body: JSON.stringify({ name: newStoreName })
    });

    if (response.ok) {
        loadStores(); // Обновляем список магазинов
        updateStoreLimit();
        loadAnalyticsButtons();
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
        const response = await fetch('/profile/stores/limit');
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
            const response = await fetch(`/profile/stores/default/${storeId}`, {
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

    if (!localStorage.getItem("cookiesAccepted")) {
        cookieModal.classList.add("show");
    }

    acceptButton.addEventListener("click", function () {
        localStorage.setItem("cookiesAccepted", "true");
        setCookie("cookie_consent", "accepted", 365);
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

    // Если кука нет - показываем окно
    if (!getCookie("cookie_consent")) {
        cookieModal.classList.add("show");
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
        analyticsActionUrl = "/analytics/reset/all";
        showResetModal("Вы уверены, что хотите удалить всю аналитику?");
    });

    document.body.addEventListener("click", function (event) {
        const btn = event.target.closest(".reset-store-analytics-btn");
        if (!btn) return;
        analyticsActionUrl = `/analytics/reset/store/${btn.dataset.storeId}`;
        showResetModal(`Очистить аналитику магазина \u00AB${btn.dataset.storeName}\u00BB?`);
    });

    /**
     * Отправления - модальное окно каждого трека с информацией
     */
    document.body.addEventListener("click", function (event) {
        if (event.target.closest(".open-modal")) {
            const button = event.target.closest(".open-modal");
            const itemNumber = button.getAttribute("data-itemnumber");
            loadModal(itemNumber);
        }
    });

    document.body.addEventListener("click", function (event) {
        if (event.target.closest(".btn-link")) {
            const button = event.target.closest(".btn-link");
            const itemNumber = button.getAttribute("data-itemnumber");
            loadModal(itemNumber);
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

    //установка активной вкладки в хедере
    const currentPath = window.location.pathname;
    document.querySelectorAll(".nav-link").forEach(link => {
        if (link.getAttribute("data-path") === currentPath) {
            link.classList.add("active");
        }
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

            // Перенаправляем пользователя на обновленный URL
            window.location.href = currentUrl.toString();
        });
    });

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

        fetch("/departures/track-update", {
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

    // Устанавливаем значения селекторов, если в URL были параметры
    if (currentStatus) statusFilterDropdown.value = currentStatus;
    if (currentStore && storeFilterDropdown) storeFilterDropdown.value = currentStore;

    /**
     * Функция применения фильтров.
     * - Считывает текущие выбранные значения в селекторах.
     * - Обновляет URL с новыми параметрами.
     * - Перезагружает страницу с обновленными фильтрами.
     */
    function applyFilters() {
        const selectedStatus = statusFilterDropdown.value;
        const selectedStore = storeFilterDropdown ? storeFilterDropdown.value : null;
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

        debugLog("✅ Фильтр применён: статус =", selectedStatus, "магазин =", selectedStore || "нет выбора");

        window.location.href = currentUrl.toString();
    }

    // Автоматическое применение фильтра при изменении значений в селекторах
    statusFilterDropdown.addEventListener("change", applyFilters);
    if (storeFilterDropdown) {
        storeFilterDropdown.addEventListener("change", applyFilters);
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

        fetch("/departures/delete-selected", {
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

        fetch("/departures/track-update", {
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