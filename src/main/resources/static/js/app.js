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
    $.ajax({
        type: 'GET',
        url: `/departures/${itemNumber}`,
        success: (data) => {
            $('#infoModal .modal-body').html(data);
            $('#infoModal').modal('show');
        },
        error: () => showAlert('Ошибка при загрузке данных', "danger")
    });
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

$(document).ready(function () {

    // === Добавляем CSRF-токен ===
    const csrfToken = $('meta[name="_csrf"]').attr('content');
    const csrfHeader = $('meta[name="_csrf_header"]').attr('content');

    let stompClient = null;
    let userId = $("#userId").val(); // Получаем userId из скрытого поля

    function connectWebSocket() {
        console.log("🚀 connectWebSocket() вызван!");

        stompClient = new StompJs.Client({
            brokerURL: 'ws://belivery.by/ws',
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

                    showAlert(response.message, response.success ? "success" : "warning");

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
            showAlert("Ошибка WebSocket: " + frame.headers['message'], "danger");
        };

        console.log("🔄 WebSocket активация отправлена...");
        stompClient.activate();
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
            existingAlert.remove(); // Удаляем старое, если пришло новое
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
        }, 5000);
    }


    $(document).ready(function () {
        connectWebSocket();
    });

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

    document.querySelectorAll(".open-modal").forEach(button => {
        button.addEventListener("click", function () {
            const itemNumber = this.getAttribute("data-itemnumber");
            loadModal(itemNumber);
        });
    });

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
    $(document).on("click", ".toggle-password", function () {
        const targetId = $(this).data("target");
        const input = $("#" + targetId);
        const icon = $(this).find("i");

        if (!input.length || !icon.length) return;

        const isPassword = input.attr("type") === "password";
        input.attr("type", isPassword ? "text" : "password");
        icon.toggleClass("bi-eye bi-eye-slash");
    });

    // Закрытие Offcanvas при выборе пункта меню
    $(document).on("click", "#settingsSidebar .nav-link", function () {
        const sidebar = $("#settingsSidebar");
        const offcanvasInstance = bootstrap.Offcanvas.getInstance(sidebar[0]);
        if (offcanvasInstance) {
            offcanvasInstance.hide();
            setTimeout(() => $(".offcanvas-backdrop").remove(), 300);
        }
    });

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
            showAlert("Выберите хотя бы одну посылку.", "warning");
            return;
        }

        if (!selectedAction) {
            showAlert("Выберите действие перед нажатием кнопки.", "warning");
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
                showAlert("Ошибка при обновлении: " + xhr.responseText, "danger");
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

    $(document).on("click", ".btn-link", function () {
        const itemNumber = $(this).data("itemnumber");
        loadModal(itemNumber);
    });

    $(document).on("change", ".selectCheckbox", updateDeleteButtonState);

    // === Обработчик выбора количества элементов ===
    $(".size-btn").on("click", function () {
        const size = $(this).data("size");
        const currentUrl = new URL(window.location.href);
        currentUrl.searchParams.set("size", size);
        window.location.href = currentUrl.toString();
    });

    // === Функция отправки запроса на удаление ===
    function sendDeleteRequest(selectedNumbers, applyBtn) {
        $.ajax({
            url: "/departures/delete-selected",
            type: "POST",
            data: { selectedNumbers: selectedNumbers },
            beforeSend: (xhr) => xhr.setRequestHeader(csrfHeader, csrfToken),
            success: function () {
                showAlert("Выбранные посылки успешно удалены.", "success");
                $(".selectCheckbox:checked").closest("tr").fadeOut(500, function () { $(this).remove(); });

                // ✅ Возвращаем кнопку в нормальное состояние
                applyBtn.prop("disabled", false).html("Применить");
            },
            error: (xhr) => {
                showAlert("Ошибка при удалении: " + xhr.responseText, "danger");
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
                showAlert("Ошибка при обновлении: " + xhr.responseText, "danger");
                applyBtn.prop("disabled", false).html("Применить");
            }
        });
    }

});