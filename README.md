# tracking_system
# Parcel tracking system
Belpost and Evropost

## Миграция статистики

Начиная с версии `V10` в таблицах статистики добавлены уникальные ограничения.
Перед применением ограничений выполняется агрегация дубликатов, поэтому на существующих инсталляциях достаточно запустить стандартные Flyway-миграции.
Скрипт `V10__deduplicate_statistics_and_add_constraints.sql` удалит повторяющиеся записи и создаст уникальные ключи.

## Конфигурация OpenCV

В файле `application.properties` добавлено свойство `opencv.lib.path`, которое определяет путь к нативной библиотеке OpenCV. По умолчанию используется `/usr/lib/jni/libopencv_java4100.so`.

## Конфигурация Tesseract

Для работы OCR-сервиса в `application.properties` необходимо указать путь к каталогу с данными Tesseract через свойство `tesseract.datapath`. По умолчанию используется `/usr/local/share/tessdata`.

## Компиляция CSS

Для генерации стилей используется Sass, исходный SCSS размещён в каталоге `src/main/resources/assets/scss`.

- `npm run build:css` — выполняет разовую компиляцию файла `main.scss` и создаёт `src/main/resources/static/css/style.css`.
- `npm run watch:css` — отслеживает изменения SCSS и автоматически обновляет тот же CSS-файл.

Скомпилированный `style.css` располагается в `src/main/resources/static/css` и подключается приложением при запуске.

## Автообновление треков

Функция автообновления использует лимит `maxTrackUpdates` тарифного плана. Количество обновлений в сутки не может превышать этот показатель.
