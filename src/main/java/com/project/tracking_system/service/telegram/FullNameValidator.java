package com.project.tracking_system.service.telegram;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Проверяет корректность ввода ФИО покупателя и нормализует его представление.
 * <p>Валидатор обеспечивает единые правила обработки строк, поступающих из Telegram,</p>
 * <p>чтобы последующие сервисы получали уже очищенное и нормализованное значение.</p>
 */
@Component
public class FullNameValidator {

    /** Минимально допустимая длина ФИО после нормализации. */
    static final int MIN_LENGTH = 2;
    /** Максимально допустимая длина ФИО после нормализации. */
    static final int MAX_LENGTH = 100;
    /** Минимально допустимая длина каждой части ФИО. */
    private static final int MIN_WORD_PART_LENGTH = 2;

    /** Пример корректного ФИО для подсказок пользователю. */
    private static final String FULL_NAME_EXAMPLE = "Иванов Иван Иванович";

    /** Разрешённый набор символов: буквы, пробелы, дефисы и апострофы. */
    private static final Pattern ALLOWED_SYMBOLS = Pattern.compile("^[\\p{L}\\s'\\-]+$");
    /** Паттерн для схлопывания последовательных пробелов. */
    private static final Pattern COLLAPSE_SPACES = Pattern.compile("\\s+");
    /** Паттерн для удаления пробелов вокруг дефисов. */
    private static final Pattern TRIM_HYPHEN_SPACES = Pattern.compile("\\s*-\\s*");
    /** Паттерн для удаления пробелов вокруг апострофов. */
    private static final Pattern TRIM_APOSTROPHE_SPACES = Pattern.compile("\\s*'\\s*");

    /** Слова, которые используются для подтверждения и не должны сохраняться как ФИО. */
    private static final Set<String> CONFIRMATION_WORDS = Set.of(
            "да",
            "верно",
            "ок",
            "окей",
            "ага",
            "yes",
            "y"
    );

    /**
     * Проверяет введённое ФИО и возвращает результат с признаком валидности и нормализованным значением.
     *
     * @param rawFullName исходная строка из Telegram
     * @return результат проверки с ошибкой или нормализованным значением
     */
    public FullNameValidationResult validate(String rawFullName) {
        if (rawFullName == null) {
            return FullNameValidationResult.invalid(
                    FullNameValidationError.EMPTY,
                    "⚠️ Пожалуйста, укажите полное ФИО, например: " + FULL_NAME_EXAMPLE + "."
            );
        }

        String trimmed = rawFullName.strip();
        if (trimmed.isEmpty()) {
            return FullNameValidationResult.invalid(
                    FullNameValidationError.EMPTY,
                    "⚠️ Пожалуйста, укажите полное ФИО, например: " + FULL_NAME_EXAMPLE + "."
            );
        }

        String normalizedWhitespace = normalizeWhitespace(trimmed);

        if (normalizedWhitespace.length() < MIN_LENGTH) {
            return FullNameValidationResult.invalid(
                    FullNameValidationError.TOO_SHORT,
                    "⚠️ ФИО должно содержать не менее 2 символов."
            );
        }

        if (normalizedWhitespace.length() > MAX_LENGTH) {
            return FullNameValidationResult.invalid(
                    FullNameValidationError.TOO_LONG,
                    "⚠️ ФИО должно содержать не более 100 символов."
            );
        }

        if (isConfirmationPhrase(normalizedWhitespace)) {
            return FullNameValidationResult.invalid(
                    FullNameValidationError.CONFIRMATION_PHRASE,
                    "ℹ️ Для подтверждения текущего ФИО воспользуйтесь кнопкой или укажите его полностью."
            );
        }

        if (!ALLOWED_SYMBOLS.matcher(normalizedWhitespace).matches()) {
            return FullNameValidationResult.invalid(
                    FullNameValidationError.INVALID_SYMBOLS,
                    "⚠️ ФИО может содержать только буквы, пробелы, дефисы и апострофы."
            );
        }

        WordStructure wordStructure = analyzeWordStructure(normalizedWhitespace);

        if (wordStructure.hasTooShortPart()) {
            return FullNameValidationResult.invalid(
                    FullNameValidationError.WORD_PART_TOO_SHORT,
                    "⚠️ Пожалуйста, укажите имя и фамилию полностью, без инициалов. Например: "
                            + FULL_NAME_EXAMPLE + "."
            );
        }

        if (!hasAtLeastTwoWords(wordStructure)) {
            return FullNameValidationResult.invalid(
                    FullNameValidationError.NAME_AND_SURNAME_REQUIRED,
                    "⚠️ Для корректной обработки укажите как минимум имя и фамилию полностью, без сокращений. Например: "
                            + FULL_NAME_EXAMPLE + "."
            );
        }

        String normalizedCase = normalizeCase(normalizedWhitespace);
        return FullNameValidationResult.valid(normalizedCase);
    }

    /**
     * Проверяет, относится ли введённая строка к подтверждающим словам.
     *
     * @param candidate строка из Telegram
     * @return {@code true}, если строка соответствует слову подтверждения
     */
    public boolean isConfirmationPhrase(String candidate) {
        if (candidate == null) {
            return false;
        }
        String normalized = candidate.strip().toLowerCase(Locale.ROOT);
        return CONFIRMATION_WORDS.contains(normalized);
    }

    /**
     * Нормализует пробелы вокруг символов и схлопывает последовательности пробелов.
     *
     * @param value исходное значение ФИО
     * @return строка с одним пробелом между словами и без пробелов вокруг дефисов/апострофов
     */
    private String normalizeWhitespace(String value) {
        String collapsed = COLLAPSE_SPACES.matcher(value).replaceAll(" ");
        String trimmedHyphen = TRIM_HYPHEN_SPACES.matcher(collapsed).replaceAll("-");
        return TRIM_APOSTROPHE_SPACES.matcher(trimmedHyphen).replaceAll("'").strip();
    }

    /**
     * Проверяет, содержит ли строка минимум два слова, разделённых пробелами или дефисами.
     *
     * @param value строка с нормализованными пробелами
     * @return {@code true}, если найдено как минимум два непустых слова
     */
    private boolean hasAtLeastTwoWords(String value) {
        return hasAtLeastTwoWords(analyzeWordStructure(value));
    }

    /**
     * Проверяет наличие минимум двух слов на основе ранее рассчитанной структуры.
     *
     * @param structure структура ФИО после анализа
     * @return {@code true}, если слова присутствуют и не содержат коротких частей
     */
    private boolean hasAtLeastTwoWords(WordStructure structure) {
        return !structure.hasTooShortPart() && structure.hasAtLeastTwoWords();
    }

    /**
     * Проводит структурный анализ ФИО: считает слова и проверяет длину их частей.
     *
     * @param value строка с нормализованными пробелами
     * @return агрегированная информация о структуре ФИО
     */
    private WordStructure analyzeWordStructure(String value) {
        String[] words = value.split(" ");
        int wordCount = 0;
        boolean hasTooShortPart = false;

        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }

            if (!isWordLongEnough(word)) {
                hasTooShortPart = true;
            }

            wordCount++;
        }

        return new WordStructure(wordCount, hasTooShortPart);
    }

    /**
     * Проверяет, что каждая часть слова (между дефисами или апострофами) имеет достаточную длину.
     *
     * @param word отдельное слово ФИО
     * @return {@code true}, если все части содержат минимум два символа
     */
    private boolean isWordLongEnough(String word) {
        String[] parts = word.split("[-']");
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            int length = part.codePointCount(0, part.length());
            if (length < MIN_WORD_PART_LENGTH) {
                return false;
            }
        }
        return true;
    }

    /**
     * Агрегирует результаты структурной проверки ФИО.
     *
     * @param wordCount      количество слов в строке
     * @param hasTooShortPart флаг наличия слишком коротких частей
     */
    private record WordStructure(int wordCount, boolean hasTooShortPart) {

        /**
         * Сообщает о наличии частей, которые короче допустимой длины.
         *
         * @return {@code true}, если в словах присутствуют короткие части
         */
        boolean hasTooShortPart() {
            return hasTooShortPart;
        }

        /**
         * Указывает, что в ФИО присутствует минимум имя и фамилия.
         *
         * @return {@code true}, если найдено не менее двух слов
         */
        boolean hasAtLeastTwoWords() {
            return wordCount >= 2;
        }
    }

    /**
     * Приводит каждое слово к «титульному» регистру, учитывая разделители.
     * <p>После обработки первая буква каждого сегмента становится заглавной, остальные — строчными.</p>
     *
     * @param value строка с нормализованными пробелами
     * @return строка с единым стилем написания ФИО
     */
    private String normalizeCase(String value) {
        StringBuilder builder = new StringBuilder();
        boolean capitalizeNext = true;
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            int charCount = Character.charCount(codePoint);

            if (Character.isLetter(codePoint)) {
                if (capitalizeNext) {
                    builder.appendCodePoint(Character.toTitleCase(codePoint));
                } else {
                    builder.appendCodePoint(Character.toLowerCase(codePoint));
                }
                capitalizeNext = false;
            } else {
                builder.appendCodePoint(codePoint);
                capitalizeNext = codePoint == ' ' || codePoint == '-' || codePoint == '\'';
            }

            i += charCount;
        }
        return builder.toString();
    }

    /**
     * Результат проверки ФИО с нормализованным значением или описанием ошибки.
     *
     * @param valid                признак успешной валидации
     * @param normalizedFullName   нормализованное ФИО
     * @param error                тип обнаруженной ошибки
     * @param message              текст подсказки для пользователя
     */
    public record FullNameValidationResult(
            boolean valid,
            String normalizedFullName,
            FullNameValidationError error,
            String message
    ) {

        /**
         * Создаёт успешный результат с нормализованным ФИО.
         *
         * @param normalizedFullName нормализованное ФИО
         * @return успешный результат проверки
         */
        public static FullNameValidationResult valid(String normalizedFullName) {
            return new FullNameValidationResult(true, normalizedFullName, FullNameValidationError.NONE, null);
        }

        /**
         * Создаёт результат с ошибкой и подсказкой для пользователя.
         *
         * @param error   тип ошибки валидации
         * @param message текст подсказки
         * @return неуспешный результат проверки
         */
        public static FullNameValidationResult invalid(FullNameValidationError error, String message) {
            return new FullNameValidationResult(false, null, error, message);
        }
    }

    /**
     * Перечень возможных ошибок валидации ФИО.
     */
    public enum FullNameValidationError {
        /** Ошибок нет, ФИО корректно. */
        NONE,
        /** ФИО не указано или состоит только из пробелов. */
        EMPTY,
        /** ФИО короче минимально допустимой длины. */
        TOO_SHORT,
        /** ФИО превышает максимально допустимую длину. */
        TOO_LONG,
        /** ФИО содержит недопустимые символы. */
        INVALID_SYMBOLS,
        /** ФИО совпало с подтверждающей фразой. */
        CONFIRMATION_PHRASE,
        /** Требуются как минимум имя и фамилия. */
        NAME_AND_SURNAME_REQUIRED,
        /** Встречены слишком короткие части слова (инициалы). */
        WORD_PART_TOO_SHORT
    }
}
