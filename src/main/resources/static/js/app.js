/** =====================
 *  ГЛОБАЛЬНЫЕ ФУНКЦИИ
 * ===================== */

function updateDeleteButtonState() {
    const hasChecked = document.querySelectorAll(".selectCheckbox:checked").length > 0;
    document.getElementById("applyActionBtn").disabled = !hasChecked;
}

function updateApplyButtonState() {
    $("#applyActionBtn").prop("disabled", $(".selectCheckbox:checked").length === 0);
}

function toggleAllCheckboxes(checked) {
    $(".selectCheckbox").prop("checked", checked);
    updateApplyButtonState();
}

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

// Привязка обработчика для формы изменения пароля
function attachPasswordFormHandler() {
    $("#password-settings-form").off("submit").on("submit", function (event) {
        event.preventDefault();

        $.ajax({
            url: $(this).attr("action"),
            method: $(this).attr("method"),
            data: $(this).serialize(),
            success: function (response) {
                $("#v-pills-profile").replaceWith(response).addClass("show active");
                attachPasswordFormHandler();
            },
            error: function () {
                alert('Ошибка при изменении пароля.');
            }
        });
    });
}

// Привязка обработчика для формы Европочты
function attachEvropostFormHandler() {
    $("#evropost-settings-form").off("submit").on("submit", function (event) {
        event.preventDefault();

        $.ajax({
            url: $(this).attr("action"),
            method: $(this).attr("method"),
            data: $(this).serialize(),
            success: function (response) {
                $("#v-pills-evropost").replaceWith(response).addClass("show active");
                attachEvropostFormHandler();
                initializeCustomCredentialsCheckbox();
            },
            error: function () {
                alert('Ошибка при сохранении данных Европочты.');
            }
        });
    });
}

// Инициализация логики для чекбокса "Использовать пользовательские креды"
function initializeCustomCredentialsCheckbox() {
    const checkbox = $("#useCustomCredentials");
    const fieldsContainer = $("#custom-credentials-fields");

    if (checkbox.length && fieldsContainer.length) {
        toggleFieldsVisibility(checkbox, fieldsContainer);

        let debounceTimer;
        checkbox.off("change").on("change", function () {
            clearTimeout(debounceTimer);
            debounceTimer = setTimeout(() => {
                $.ajax({
                    url: '/profile/settings/use-custom-credentials',
                    type: 'POST',
                    data: { useCustomCredentials: checkbox.is(":checked") },
                    beforeSend: function (xhr) {
                        xhr.setRequestHeader(
                            $('meta[name="_csrf_header"]').attr('content'),
                            $('meta[name="_csrf"]').attr('content')
                        );
                    },
                    success: function () {
                        toggleFieldsVisibility(checkbox, fieldsContainer);
                    },
                    error: function () {
                        alert("Ошибка при обновлении чекбокса.");
                    }
                });
            }, 300);
        });
    }
}

// Функция управления видимостью полей
function toggleFieldsVisibility(checkbox, fieldsContainer) {
    fieldsContainer.toggle(checkbox.is(":checked"));
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
    let existingAlert = $(".notification");

    // ❌ Игнорируем "Обновление запущено...", так как оно временное
    if (message.includes("Обновление запущено")) {
        console.log("⚠ Пропущено уведомление:", message);
        return;
    }

    if (existingAlert.length > 0) {
        let currentMessage = existingAlert.find("span.alert-text").text();
        if (currentMessage === message) {
            console.log("⚠ Повторное уведомление проигнорировано:", message);
            return;
        }
        existingAlert.remove(); // Удаляем старое
    }

    const alertHtml = `
    <div class="alert alert-${type} alert-dismissible fade show notification" role="alert">
        <i class="bi ${type === 'success' ? 'bi-check-circle-fill' : 'bi-exclamation-triangle-fill'} me-2"></i>
        <span class="alert-text">${message}</span>
        <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Закрыть"></button>
    </div>`;

    $(".history-header").before(alertHtml);

    setTimeout(() => {
        $(".notification").fadeOut("slow", function () {
            $(this).remove();
        });
    }, 10000);
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
    let toast = new bootstrap.Toast(toastElement, { delay: 10000 });
    toast.show();

    toastElement.addEventListener("hidden.bs.toast", () => {
        toastElement.remove();
    });
}

let stompClient = null;
let userId = $("#userId").val(); // Получаем userId из скрытого поля

function connectWebSocket() {
    console.log("🚀 connectWebSocket() вызван!");

    stompClient = new StompJs.Client({
        //'wss://belivery.by/ws', 'ws://localhost:8080/ws',
        brokerURL: 'ws://localhost:8080/ws',
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

                $("#applyActionBtn").prop("disabled", false).html("Применить");

                $("#refreshAllBtn").prop("disabled", false).html('<i class="bi bi-arrow-repeat"></i>');

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
    $.ajax({
        url: "/departures",
        type: "GET",
        cache: false,
        success: function (html) {
            let newTableBody = $(html).find("tbody").html();
            console.log("📊 Получены новые данные:", newTableBody);
            $("tbody").html(newTableBody);
            console.log("✅ Таблица обновлена!");
        },
        error: function () {
            console.error("❌ Ошибка загрузки обновлённых данных!");
        }
    });
}

$(document).ready(function () {

    // === Добавляем CSRF-токен ===
    const csrfToken = $('meta[name="_csrf"]').attr('content');
    const csrfHeader = $('meta[name="_csrf_header"]').attr('content');

    connectWebSocket();

    $("#updateAllForm").on("submit", function (event) {
        event.preventDefault();
        sendUpdateRequest(null);
    });

    // Инициализация всплывающих подсказок (работает и для динамических элементов)
    $("body").tooltip({ selector: '[data-bs-toggle="tooltip"]' });

    /// Авто-скрытие уведомлений
    setTimeout(() => { $(".alert").fadeOut("slow"); }, 5000);

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
    attachPasswordFormHandler();
    attachEvropostFormHandler();
    initializeCustomCredentialsCheckbox();

    document.getElementById("selectAllCheckbox")?.addEventListener("click", function () {
        toggleAllCheckboxes(this);
    });

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

    document.querySelectorAll(".selectCheckbox").forEach(checkbox => {
        checkbox.addEventListener("change", updateDeleteButtonState);
    });

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

    $(".size-btn").on("click", function () {
        $(".size-btn").removeClass("active");
        $(this).addClass("active");

        const size = $(this).data("size");
        const currentUrl = new URL(window.location.href);
        currentUrl.searchParams.set("size", size);
        window.location.href = currentUrl.toString();
    });

    // Переключение всех чекбоксов при выборе верхнего чекбокса
    $(document).on("change", "#selectAllCheckbox", function () {
        toggleAllCheckboxes(this.checked);
    });

    $(document).on("change", ".selectCheckbox", function () {
        const allChecked = $(".selectCheckbox:checked").length === $(".selectCheckbox").length;
        $("#selectAllCheckbox").prop("checked", allChecked);
        updateApplyButtonState();
    });

    // === Обработчик кнопки "Применить" ===
    $("#applyActionBtn").on("click", function () {
        const selectedNumbers = $(".selectCheckbox:checked").map(function () { return this.value; }).get();
        const selectedAction = $("#actionSelect").val();

        if (selectedNumbers.length === 0) {
            notifyUser("Выберите хотя бы одну посылку.", "warning");
            return;
        }

        if (!selectedAction) {
            notifyUser("Выберите действие перед нажатием кнопки.", "warning");
            return;
        }

        const applyBtn = $("#applyActionBtn");
        applyBtn.prop("disabled", true).html('<i class="bi bi-arrow-repeat spin"></i> Выполняется...');

        if (selectedAction === "delete") {
            sendDeleteRequest(selectedNumbers, applyBtn);
        } else if (selectedAction === "update") {
            sendUpdateRequest(selectedNumbers, applyBtn);
        }
    });

    // === Обработчик кнопки "Обновить всё" ===
    $("#refreshAllBtn").on("click", function () {
        const refreshBtn = $(this);
        refreshBtn.prop("disabled", true).html('<i class="bi bi-arrow-repeat spin"></i>');

        $.ajax({
            url: "/departures/track-update",
            type: "POST",
            data: {},
            beforeSend: function (xhr) {
                xhr.setRequestHeader(csrfHeader, csrfToken);
            },
            success: function () {
                console.log("✅ AJAX-запрос для обновления всех треков отправлен. Ждём WebSocket...");
            },
            error: function (xhr) {
                notifyUser("Ошибка при обновлении: " + xhr.responseText, "danger");
                refreshBtn.prop("disabled", false).html('<i class="bi bi-arrow-repeat"></i>');
            }
        });
    });

    // === Фильтр по статусу ===
    $("#filterActionBtn").on("click", function () {
        const selectedStatus = $("#status").val();
        const currentUrl = new URL(window.location.href);

        if (selectedStatus) {
            currentUrl.searchParams.set("status", selectedStatus);
        } else {
            currentUrl.searchParams.delete("status");
        }

        window.location.href = currentUrl.toString();
    });

    $(document).on("change", ".selectCheckbox", updateDeleteButtonState);

    // === Обработчик выбора количества элементов ===
    // $(".size-btn").on("click", function () {
    //     const size = $(this).data("size");
    //     const currentUrl = new URL(window.location.href);
    //     currentUrl.searchParams.set("size", size);
    //     window.location.href = currentUrl.toString();
    // });

    // === Функция отправки запроса на удаление ===
    function sendDeleteRequest(selectedNumbers, applyBtn) {
        $.ajax({
            url: "/departures/delete-selected",
            type: "POST",
            data: { selectedNumbers: selectedNumbers },
            beforeSend: (xhr) => xhr.setRequestHeader(csrfHeader, csrfToken),
            success: function () {
                notifyUser("Выбранные посылки успешно удалены.", "success");
                $(".selectCheckbox:checked").closest("tr").fadeOut(500, function () { $(this).remove(); });

                // ✅ Возвращаем кнопку в нормальное состояние
                applyBtn.prop("disabled", false).html("Применить");
            },
            error: (xhr) => {
                notifyUser("Ошибка при удалении: " + xhr.responseText, "danger");
                applyBtn.prop("disabled", false).html("Применить");
            }
        });
    }

    function sendUpdateRequest(selectedNumbers, applyBtn) {
        applyBtn.prop("disabled", true).html('<i class="bi bi-arrow-repeat spin"></i> Обновление...');

        $.ajax({
            url: "/departures/track-update",
            type: "POST",
            data: { selectedNumbers: selectedNumbers },
            beforeSend: function (xhr) {
                xhr.setRequestHeader(csrfHeader, csrfToken);
            },
            success: function () {
                console.log("✅ AJAX-запрос отправлен. Ждём уведомления через WebSocket...");
                // Кнопка вернётся после получения уведомления через сокет
            },
            error: function (xhr) {
                notifyUser("Ошибка при обновлении: " + xhr.responseText, "danger");
                applyBtn.prop("disabled", false).html("Применить");
            }
        });
    }

});