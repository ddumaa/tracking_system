$(document).ready(function () {
    // Инициализация вкладок
    initializeTabs();

    // Логика вкладки Европочты
    function initializeTabs() {
        // Вкладка "Европочта"
        $("#evropost-settings-link").click(function (event) {
            event.preventDefault();

            $.ajax({
                url: '/profile/settings?tab=evropost',
                method: 'GET',
                success: function (response) {
                    $("#v-pills-evropost").html(response);
                    attachEvropostFormHandler();
                },
                error: function () {
                    alert('Ошибка загрузки формы настроек Европочты.');
                }
            });
        });

        // Вкладка "Пароль"
        $("#password-settings-link").click(function (event) {
            event.preventDefault();

            $.ajax({
                url: '/profile/settings?tab=password',
                method: 'GET',
                success: function (response) {
                    $("#v-pills-profile").html(response);
                    attachPasswordFormHandler();
                },
                error: function () {
                    alert('Ошибка загрузки формы изменения пароля.');
                }
            });
        });
    }

    // Привязка обработчика для формы изменения пароля
    function attachPasswordFormHandler() {
        $("#password-settings-form").on("submit", function (event) {
            event.preventDefault();

            $.ajax({
                url: $(this).attr("action"),
                method: $(this).attr("method"),
                data: $(this).serialize(),
                success: function (response) {
                    $("#v-pills-profile").html(response);
                    attachPasswordFormHandler(); // Повторная привязка
                },
                error: function () {
                    alert('Ошибка при изменении пароля. Пожалуйста, попробуйте позже.');
                }
            });
        });
    }

    // Привязка обработчика для формы Европочты
    function attachEvropostFormHandler() {
        $("#evropost-settings-form").on("submit", function (event) {
            event.preventDefault();

            $.ajax({
                url: $(this).attr("action"),
                method: $(this).attr("method"),
                data: $(this).serialize(),
                success: function (response) {
                    $("#v-pills-evropost").html(response);
                    attachEvropostFormHandler(); // Повторная привязка
                    initializeCustomCredentialsCheckbox(); // Инициализация логики чекбокса
                },
                error: function () {
                    alert('Ошибка при сохранении данных Европочты. Пожалуйста, попробуйте позже.');
                }
            });
        });

        initializeCustomCredentialsCheckbox(); // Привязываем логику чекбокса
    }

    // Логика для чекбокса "Использовать пользовательские креды"
    function initializeCustomCredentialsCheckbox() {
        const checkbox = $("#useCustomCredentials");
        const fieldsContainer = $("#custom-credentials-fields");

        // Инициализируем видимость полей при загрузке страницы
        toggleFieldsVisibility(checkbox, fieldsContainer);

        // Привязываем обработчик изменения состояния
        checkbox.off("change").on("change", function () {
            const isChecked = checkbox.is(":checked");
            console.log("Checkbox state changed to:", isChecked);

            // Отправляем состояние чекбокса на сервер
            const csrfToken = $('meta[name="_csrf"]').attr('content');
            const csrfHeader = $('meta[name="_csrf_header"]').attr('content');

            $.ajax({
                url: '/profile/settings/use-custom-credentials',
                type: 'POST',
                data: { useCustomCredentials: isChecked },
                beforeSend: function (xhr) {
                    xhr.setRequestHeader(csrfHeader, csrfToken);
                },
                success: function () {
                    console.log("Чекбокс успешно обновлён.");
                    // Обновляем видимость полей после успешного ответа
                    toggleFieldsVisibility(checkbox, fieldsContainer);
                },
                error: function (xhr) {
                    console.error("Failed to update checkbox state:", xhr.responseText);
                    alert("Ошибка при обновлении чекбокса. Пожалуйста, попробуйте позже.");
                }
            });
        });
    }

    // Логика для управления видимостью полей
    function toggleFieldsVisibility(checkbox, fieldsContainer) {
        if (checkbox.is(":checked")) {
            fieldsContainer.show();
        } else {
            fieldsContainer.hide();
        }
    }

    // Логика показа/скрытия пароля
    $(document).on("click", ".toggle-password", function () {
        const targetId = $(this).data("target");
        const input = $("#" + targetId);

        if (!input.length) {
            console.error(`Поле с ID "${targetId}" не найдено.`);
            return;
        }

        if (input.attr("type") === "password") {
            input.attr("type", "text");
            $(this).text("Скрыть");
        } else {
            input.attr("type", "password");
            $(this).text("Показать");
        }
    });

});