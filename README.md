# tracking_system
# Parcel tracking system
Belpost and Evropost

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
{ "version": "0.7.1" }
```

## Очередь Белпочты

Для трек-номеров службы Белпочты используется отдельная очередь `BelPostTrackQueueService`.
Она обеспечивает последовательную обработку пачек треков и отправляет события через WebSocket.

- **Планирование.** Метод `processQueue()` помечен `@Scheduled(fixedDelay = 15000)`,
  поэтому каждые 15 секунд из очереди берётся один трек.
  При ошибке Selenium обработка приостанавливается на минуту.
- **Параметры.** Задержку и период можно изменить в коде сервиса,
  либо переопределить аннотацию при необходимости.

### Примеры событий WebSocket

```json
// belpost/batch-started/{userId}
{ "batchId": 1, "totalCount": 5 }

// belpost/track-processed/{userId}
{ "batchId": 1, "trackNumber": "RR123", "processed": 1, "success": 1, "failed": 0 }

// belpost/batch-finished/{userId}
{ "batchId": 1, "processed": 5, "success": 4, "failed": 1 }
```

После загрузки файла с треками пользователь может следить за прогрессом
в веб-интерфейсе: уведомления по WebSocket отображаются в правом верхнем углу
страницы и обновляют таблицу по мере завершения обработки.

## Тестирование

Для запуска модульных и интеграционных тестов выполните:

```bash
mvn test
```

Тесты используют встроенный брокер WebSocket и не требуют дополнительной настройки.

## Content Security Policy

Встроенные обработчики событий постепенно заменяются внешними скриптами для соблюдения CSP. Кнопка копирования ссылки на Telegram-бота теперь инициализируется функцией `initTelegramLinkCopy()` в `static/js/app.js`.
