package com.project.tracking_system.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Вспомогательные методы для работы с поиском по ФИО покупателя.
 * <p>
 * Класс отвечает только за извлечение и подготовку токенов, что позволяет
 * повторно использовать логику в разных частях приложения и облегчает тестирование.
 * </p>
 */
public final class NameSearchUtils {

    /**
     * Регулярное выражение для выделения последовательностей букв в поисковом запросе.
     * Поддерживает кириллицу и латиницу благодаря использованию {@code \p{L}}.
     */
    private static final Pattern NAME_TOKEN_PATTERN = Pattern.compile("\\p{L}+");

    private NameSearchUtils() {
        // Утилитарный класс не предполагает создание экземпляров.
    }

    /**
     * Извлекает последовательности букв из пользовательского запроса.
     * <p>
     * Метод пропускает цифры и специальные символы, позволяя использовать его
     * как для поиска по ФИО, так и для комбинированных запросов, где встречаются
     * номера телефонов или трек-номера.
     * </p>
     *
     * @param query исходная строка поиска
     * @return неизменяемый список токенов в порядке появления, либо пустой список
     */
    public static List<String> extractNameTokens(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        Matcher matcher = NAME_TOKEN_PATTERN.matcher(query);
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return Collections.unmodifiableList(tokens);
    }

    /**
     * Возвращает токен по индексу или пустую строку, если токена нет.
     * <p>
     * Используется при формировании запросов к базе данных, где требуется
     * фиксированное количество параметров.
     * </p>
     *
     * @param tokens список токенов ФИО
     * @param index  индекс требуемого токена
     * @return токен в нижнем регистре либо пустая строка
     */
    public static String getTokenOrEmpty(List<String> tokens, int index) {
        if (tokens == null || index < 0 || index >= tokens.size()) {
            return "";
        }
        return tokens.get(index).toLowerCase();
    }
}

