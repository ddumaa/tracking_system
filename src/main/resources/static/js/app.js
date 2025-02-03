/** =====================
 *  ГЛОБАЛЬНЫЕ ФУНКЦИИ
 * ===================== */

function showAlert(message, type) {
    console.log("Показываем уведомление:", message, type);

    const alertHtml = `
        <div class="alert alert-${type} alert-dismissible fade show notification" role="alert">
            <i class="bi ${type === 'success' ? 'bi-check-circle-fill' : 'bi-exclamation-triangle-fill'} me-2"></i>
            ${message}
            <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Закрыть"></button>
        </div>`;

    $(".d-flex.flex-wrap.align-items-center.gap-3.justify-content-between").before(alertHtml);

    setTimeout(() => {
        $(".notification").fadeOut("slow", function () {
            $(this).remove();
        });
    }, 5000);
}

function updateApplyButtonState() {
    $("#applyActionBtn").prop("disabled", $(".selectCheckbox:checked").length === 0);
}

function toggleAllCheckboxes(checked) {
    $(".selectCheckbox").prop("checked", checked);
    updateApplyButtonState();
}

function updateDeleteButtonState() {
    const hasChecked = $(".selectCheckbox:checked").length > 0;
    $("#applyActionBtn").prop("disabled", !hasChecked);
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

// === Проверка статуса обновления ===
function checkUpdateStatus(applyBtn) {
    let attempts = 0;
    const maxAttempts = 20;

    function pollStatus() {
        $.ajax({
            url: "/departures/update-status",
            type: "GET",
            success: function (response) {
                if (response.completed) {
                    showAlert("Обновление завершено!", "success");
                    applyBtn.prop("disabled", false).html("Применить");
                    setTimeout(() => location.reload(), 2000);
                } else if (attempts < maxAttempts) {
                    attempts++;
                    setTimeout(pollStatus, 3000);
                } else {
                    showAlert("Обновление выполняется в фоне, данные обновятся автоматически.", "info");
                    applyBtn.prop("disabled", false).html("Применить");
                    setTimeout(() => location.reload(), 60000);
                }
            },
            error: function () {
                showAlert("Ошибка проверки статуса обновления.", "danger");
                applyBtn.prop("disabled", false).html("Применить");
            }
        });
    }

    pollStatus();
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

    // Инициализация всплывающих подсказок (работает и для динамических элементов)
    $("body").tooltip({ selector: '[data-bs-toggle="tooltip"]' });

    /// Авто-скрытие уведомлений
    setTimeout(() => { $(".alert").fadeOut("slow"); }, 5000);

    // Инициализация логики форм
    attachPasswordFormHandler();
    attachEvropostFormHandler();
    initializeCustomCredentialsCheckbox();

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
            showAlert("Выберите хотя бы одну посылку.", "danger");
            return;
        }

        if (!selectedAction) {
            showAlert("Выберите действие.", "danger");
            return;
        }

        if (selectedAction === "delete") {
            sendDeleteRequest(selectedNumbers);
        } else if (selectedAction === "update") {
            sendUpdateRequest(selectedNumbers);
        }
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
    function sendDeleteRequest(selectedNumbers) {
        $.ajax({
            url: "/departures/delete-selected",
            type: "POST",
            data: { selectedNumbers: selectedNumbers },
            beforeSend: (xhr) => xhr.setRequestHeader(csrfHeader, csrfToken),
            success: function () {
                showAlert("Выбранные посылки успешно удалены.", "success");
                $(".selectCheckbox:checked").closest("tr").fadeOut(500, function () { $(this).remove(); });
            },
            error: (xhr) => showAlert("Ошибка при удалении: " + xhr.responseText, "danger")
        });
    }

    function sendUpdateRequest(selectedNumbers) {
        showAlert("Выбранные посылки обновляются. Это может занять время...", "info");
        const applyBtn = $("#applyActionBtn");
        applyBtn.prop("disabled", true).html('<i class="bi bi-arrow-repeat spin"></i> Обновление...');

        $.ajax({
            url: "/departures/track-update",
            type: "POST",
            data: { selectedNumbers: selectedNumbers },
            beforeSend: (xhr) => xhr.setRequestHeader(csrfHeader, csrfToken),
            success: function () {
                checkUpdateStatus(applyBtn);
            },
            error: function (xhr) {
                showAlert("Ошибка при обновлении: " + xhr.responseText, "danger");
                applyBtn.prop("disabled", false).html("Применить");
            }
        });
    }

});