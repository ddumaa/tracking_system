(function (global) {
    'use strict';

    /**
     * Нормализует трек-номер: приводит к верхнему регистру и удаляет пробелы.
     * @param {string} number исходный трек-номер
     * @returns {string} нормализованный трек-номер
     */
    function normalize(number) {
        return (number || '').toUpperCase().trim();
    }

    /**
     * Определяет почтовую службу по формату трек-номера.
     * @param {string} number исходный трек-номер
     * @returns {string|null} BELPOST, EVROPOST или null, если формат не распознан
     */
    function detectService(number) {
        const value = normalize(number);
        if (/^(PC|BV|BP|PE)\d{9}BY$/.test(value)) {
            return 'BELPOST';
        }
        if (/^BY\d{12}$/.test(value)) {
            return 'EVROPOST';
        }
        return null;
    }

    /**
     * Проверяет корректность трек-номера и возвращает результат валидации.
     * @param {string} number трек-номер для проверки
     * @returns {{valid: boolean, service: string|null, message: string}} результат проверки
     */
    function validate(number) {
        const value = normalize(number);
        if (!value) {
            return { valid: false, service: null, message: 'Номер обязателен' };
        }
        const service = detectService(value);
        if (!service) {
            return { valid: false, service: null, message: 'Неверный формат номера' };
        }
        return { valid: true, service, message: '' };
    }

    // Экспортируем функции в глобальную область видимости
    global.trackValidator = { normalize, detectService, validate };
})(window);
