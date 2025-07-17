# tracking_system
# Parcel tracking system
Belpost and Evropost

## Миграция статистики

Начиная с версии `V10` в таблицах статистики добавлены уникальные ограничения.
Перед применением ограничений выполняется агрегация дубликатов, поэтому на существующих инсталляциях достаточно запустить стандартные Flyway-миграции.
Скрипт `V10__deduplicate_statistics_and_add_constraints.sql` удалит повторяющиеся записи и создаст уникальные ключи.


## Компиляция CSS

Для генерации стилей используется Sass, исходный SCSS размещён в каталоге `src/main/resources/assets/scss`.

- `npm run build:css` — выполняет разовую компиляцию файла `main.scss` и создаёт `src/main/resources/static/css/style.css`.
- `npm run watch:css` — отслеживает изменения SCSS и автоматически обновляет тот же CSS-файл.

Скомпилированный `style.css` располагается в `src/main/resources/static/css` и подключается приложением при запуске.

## Конфигурация ChromeDriver

Для запуска веб-драйвера в файле `application.properties` используется свойство
`webdriver.chrome.driver`, которое определяет путь к исполняемому файлу
ChromeDriver. В контейнере по умолчанию применяется путь
`/usr/local/bin/chromedriver`, задаваемый в `Dockerfile`. При локальном запуске
приложения значение можно изменить, передав параметр JVM:

```bash
java -jar app.jar --webdriver.chrome.driver=/path/to/chromedriver
```

## Автообновление треков

Функция автообновления использует лимит `maxTrackUpdates` тарифного плана. Количество обновлений в сутки не может превышать этот показатель.

## Версия приложения

Актуальная версия задаётся в `application.properties` свойством `application.version`.
Получить её можно через REST-эндпоинт `/app/version`, который возвращает JSON
следующего вида:

```json
{ "version": "0.0.1-SNAPSHOT" }
```
