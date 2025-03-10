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

document.addEventListener("visibilitychange", function () {
    if (document.hidden) {
        console.log("🔴 Пользователь ушёл со страницы");
        lastPage = window.location.pathname; // Фиксируем страницу
    } else {
        console.log("🟢 Пользователь вернулся на страницу");
        lastPage = window.location.pathname; // Фиксируем новую страницу
    }
});

// Определяем, есть ли уже открытое модальное окно
function isModalOpen() {
    return document.querySelector(".modal.show") !== null;
}

// Функция выбора уведомления
function notifyUser(message, type = "info") {
    if (document.hidden || window.location.pathname !== lastPage || isModalOpen()) {
        console.log("📢 Показываем toast, так как пользователь сменил страницу или уже в модальном окне");
        showToast(message, type);
    } else {
        console.log("✅ Показываем alert, так как пользователь остаётся на странице");
        showAlert(message, type);
    }
}

// Уведомления
function showAlert(message, type) {
    let existingAlert = document.querySelector(".notification"); // Берём только первый найденный alert

    // ❌ Игнорируем "Обновление запущено...", так как оно временное
    if (message.includes("Обновление запущено")) {
        console.log("⚠ Пропущено уведомление:", message);
        return;
    }

    // Проверяем, есть ли уже уведомление с таким же текстом
    if (existingAlert) {
        let currentMessage = existingAlert.querySelector("span.alert-text")?.textContent || "";
        if (currentMessage === message) {
            console.log("⚠ Повторное уведомление проигнорировано:", message);
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
    console.log("🚀 connectWebSocket() вызван!");

    stompClient = new StompJs.Client({
        //'wss://belivery.by/ws', 'ws://localhost:8080/ws',
        brokerURL: 'wss://belivery.by/ws',
        reconnectDelay: 1000,
        heartbeatIncoming: 2000,
        heartbeatOutgoing: 2000,
        debug: function (str) {
            console.log('STOMP Debug: ', str);
        }
    });

    stompClient.onConnect = function (frame) {
        console.log('✅ WebSocket подключен: ' + frame);

        let destination = '/topic/status/' + userId;
        console.log("📡 Подписываемся на " + destination);

        if (stompClient.connected) {
            stompClient.subscribe(destination, function (message) {
                let response = JSON.parse(message.body);
                console.log("📡 WebSocket сообщение: ", response);

                console.log("⚠️ DEBUG: success=", response.success, "message=", response.message);

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

    console.log("🔄 WebSocket активация отправлена...");
    stompClient.activate();
}

function reloadParcelTable() {
    console.log("🔄 AJAX-запрос для обновления таблицы...");

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
                    console.log("✅ Таблица обновлена!");
                }
            }
        })
        .catch(error => {
            console.error("❌ Ошибка загрузки обновлённых данных!", error);
        });
}

function enableTooltips() {
    const tooltipTriggerList = document.querySelectorAll('[data-bs-toggle="tooltip"]');
    tooltipTriggerList.forEach(tooltipTriggerEl => {
        new bootstrap.Tooltip(tooltipTriggerEl);
    });
}

// Повторная инициализация Tooltips при динамическом изменении страницы
document.addEventListener("mouseover", function (event) {
    if (event.target.matches('[data-bs-toggle="tooltip"]')) {
        enableTooltips();
    }
});


document.addEventListener("DOMContentLoaded", function () {

    // === Добавляем CSRF-токен ===
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content || "";
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || "";

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

    // Инициализация логики форм
    initPasswordFormHandler();
    initEvropostFormHandler();
    initializeCustomCredentialsCheckbox();

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
                console.log("✅ AJAX-запрос для обновления всех треков отправлен. Ждём WebSocket...");
            })
            .catch(error => {
                notifyUser("Ошибка при обновлении: " + error.message, "danger");
                refreshBtn.disabled = false;
                refreshBtn.innerHTML = '<i class="bi bi-arrow-repeat"></i>';
            });
    });

    // === Статус ===
    const statusSelect = document.getElementById("status");

    // Восстанавливаем сохранённый статус при загрузке страницы
    const urlParams = new URLSearchParams(window.location.search);
    const currentStatus = urlParams.get("status");

    if (currentStatus) {
        statusSelect.value = currentStatus; // Устанавливаем значение из URL
    }

    // === Фильтр по статусу ===
    document.getElementById("filterActionBtn")?.addEventListener("click", function () {
        const selectedStatus = statusSelect.value;
        const currentUrl = new URL(window.location.href);

        if (selectedStatus) {
            currentUrl.searchParams.set("status", selectedStatus);
        } else {
            currentUrl.searchParams.delete("status");
        }

        window.location.href = currentUrl.toString();
    });

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

                clearAllCheckboxes();

                // Анимация исчезновения удалённых строк
                document.querySelectorAll(".selectCheckbox:checked").forEach(checkbox => {
                    const row = checkbox.closest("tr");
                    if (row) {
                        row.style.transition = "opacity 0.5s";
                        row.style.opacity = "0";
                        setTimeout(() => row.remove(), 500);
                    }
                });

                // ✅ Возвращаем кнопку в нормальное состояние
                applyBtn.disabled = false;
                applyBtn.innerHTML = "Применить";
            })
            .catch(error => {
                notifyUser("Ошибка при удалении: " + error.message, "danger");
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
                console.log("✅ AJAX-запрос отправлен. Ждём уведомления через WebSocket...");

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