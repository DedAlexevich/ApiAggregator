# API Aggregator

Консольное приложение для первого этапа курсовой работы по ООП. Оно последовательно опрашивает выбранные REST API, приводит ответы к общей модели и сохраняет их в JSON или CSV. Отдельная команда выводит все сохранённые записи или записи одного источника.

## Сборка и первый запуск

Понадобится **JDK 25 или новее**. Текущий `Main` объявляет `static void main(String[] args)` с пакетным доступом. Такой метод поддерживается стандартным запуском с Java 25. Для сборки используется Gradle Wrapper из проекта.

Выполните из корня проекта в Linux или WSL:

```bash
java -version
./gradlew build
./gradlew run --args='help'
```

Для первого запроса можно использовать Open-Meteo Marine без ключа:

```bash
./gradlew run --args='-sources meteo -format json -mode overwrite run'
./gradlew run --args='-format json read'
```

Чтобы запускать приложение напрямую после сборки, выполните `./gradlew installDist`. Скрипт запуска появится в `build/install/ApiAggregator/bin/ApiAggregator`. Передавайте ему те же аргументы без обёртки `--args`:

```bash
./build/install/ApiAggregator/bin/ApiAggregator -sources meteo -format json -mode overwrite run
```

## Источники данных

| Имя в `-sources` | API и адрес запроса                                                                                                        | Какие данные извлекаются                                        |
|------------------|----------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------|
| `meteo`          | [Open-Meteo Marine](https://open-meteo.com/en/docs/marine-weather-api), `GET https://marine-api.open-meteo.com/v1/marine`  | Координаты и почасовой прогноз из `hourly`                      |
| `auto`           | [Auto.dev Vehicle Listings](https://docs.auto.dev/v2/reference/searchVehicleListings), `GET https://api.auto.dev/listings` | Объявления из массива `data`, включая VIN, марку, модель и цену |
| `exchange`       | [ExchangeRatesAPI](https://exchangeratesapi.io/documentation/), `GET https://api.exchangeratesapi.io/v1/latest`            | Валютные пары, дата и курсы из объекта `rates`                  |

Для Auto.dev и ExchangeRatesAPI создайте файл `.env` в корне проекта. Запишите по одному ключу на строку в формате `имя=значение`, без кавычек:

```dotenv
AUTO_DEV_API_KEY=your_auto_dev_key
EXCHANGE_RATES_API_KEY=your_exchange_rates_key
```

Приложение читает ключ только для выбранного источника. Auto.dev получает его в заголовке `Authorization: Bearer …`, ExchangeRatesAPI — в query-параметре `access_key`. Передавайте ключи через `.env`. Параметры из `-config` выводятся в журнал. Файл `.env` указан в `.gitignore`.

### Query-параметры источников

При запуске передайте параметры через `-config источник.параметр=значение`. Префикс до первой точки выбирает источник. Например, из `auto.vehicle.make=Toyota` приложение формирует query-параметр `vehicle.make=Toyota`. Имена и значения кодируются для URL автоматически.

| Источник | Query-параметр | Что задаёт | Пример значения |
| --- | --- | --- | --- |
| `meteo` | `latitude` | Широта точки прогноза в десятичных градусах | `43.3` |
| `meteo` | `longitude` | Долгота точки прогноза в десятичных градусах | `5.4` |
| `meteo` | `hourly` | Почасовые показатели через запятую. Адаптер извлекает высоту волн (`wave_height`), период (`wave_period`), направление (`wave_direction`) и температуру поверхности моря (`sea_surface_temperature`) | `wave_height,wave_period` |
| `meteo` | `forecast_days` | Число дней прогноза | `2` |
| `auto` | `vehicle.make` | Марка автомобиля для поиска объявлений | `Toyota` |
| `auto` | `limit` | Максимальное число объявлений в ответе | `5` |
| `exchange` | `symbols` | Коды валют, для которых нужны курсы, через запятую | `USD,GBP,JPY` |
| `exchange` | `access_key` | Ключ доступа. Адаптер подставляет его из `.env` и отклоняет передачу через `-config` | Из `EXCHANGE_RATES_API_KEY` |

Примеры аргументов приложения для IntelliJ IDEA:

```text
-sources meteo -format json -mode overwrite -config meteo.latitude=43.3 meteo.longitude=5.4 meteo.hourly=wave_height,wave_period meteo.forecast_days=2 run
-sources auto -format json -mode overwrite -config auto.vehicle.make=Toyota auto.limit=5 run
-sources exchange -format json -mode overwrite -config exchange.symbols=USD,GBP,JPY run
```

Таблица описывает параметры, используемые в проекте. Через `-config` можно передать и другие query-параметры из документации соответствующего API. Для `meteo` выбирайте хотя бы один из четырёх перечисленных показателей `hourly`: адаптер ожидает его в почасовом прогнозе.

## Команды и параметры

Последний аргумент задаёт действие. `run` опрашивает API, `read` читает файл, `help` выводит справку. Запуск без аргументов также показывает справку.

| Опция | Команда | Назначение |
| --- | --- | --- |
| `-sources meteo,auto,exchange` | `run` | Обязательный список источников через запятую. Порядок имён задаёт порядок опроса. |
| `-format json` или `-format csv` | `run`, `read` | Формат записи или чтения. Обязателен для `run`; для `read` по умолчанию `json`. |
| `-mode overwrite` или `-mode append` | `run` | `overwrite` создаёт файл или заменяет содержимое. `append` добавляет данные, сохраняя предыдущие записи. Параметр обязателен для `run`. |
| `-file путь` | `run`, `read` | Путь к файлу. По умолчанию `data.json` или `data.csv` в зависимости от `-format`. |
| `-config источник.параметр=значение ...` | `run` | Переопределяет указанные параметры API для текущего запуска. Параметры невыбранных источников вызывают ошибку. |
| `-source "имя в файле"` | `read` | Оставляет записи с указанным значением поля `source`. Без фильтра выводятся все записи. |

### Опрос и сохранение в JSON

```bash
./gradlew run --args='-sources meteo,auto,exchange -format json -mode overwrite -file data.json run'
```

### Запись и дозапись CSV

Первый запуск опрашивает три источника и записывает успешные результаты. Второй дописывает в файл результаты нового опроса `meteo` и `auto` с переопределёнными параметрами:

```bash
./gradlew run --args='-sources meteo,auto,exchange -format csv -mode overwrite -file data.csv run'
./gradlew run --args='-sources meteo,auto -format csv -mode append -file data.csv -config meteo.hourly=wave_height,wave_period auto.limit=3 run'
```

### Чтение результатов

```bash
# Весь JSON
./gradlew run --args='-format json -file data.json read'

# Весь CSV
./gradlew run --args='-format csv -file data.csv read'

# Только результаты морского прогноза
./gradlew run --args='-format csv -file data.csv -source meteo read'
```

Сохранённые имена для `-source` — `meteo`, `auto` и `exchange`. Фильтр сравнивает имя целиком с учётом регистра. Для чтения сохранённых данных ключи API не нужны.

## Формат данных

Результат одного успешного опроса хранится в `DataRecord`. Запись содержит имя источника `source`, время получения `fetchedAt`, параметры запроса `parameters` и список `items`. У каждого `DataItem` есть идентификатор `id` и карта атрибутов `attributes`. Конструкторы моделей создают неизменяемые копии коллекций через `Map.copyOf` и `List.copyOf`.

JSON хранит массив записей `DataRecord`. При дозаписи хранилище читает существующий массив и сохраняет его вместе с новыми записями. CSV использует колонки `source,fetchedAt,parameters,items`. Одна строка соответствует одной записи `DataRecord`. Вложенные `parameters` и `items` хранятся как JSON внутри ячеек. При дозаписи CSV новые строки добавляются в конец файла без повторного заголовка.

## Ошибки и журнал

При сбое одного источника агрегатор продолжает опрашивать остальные и сохраняет успешные результаты. Если все выбранные источники завершились с ошибкой, файл не перезаписывается.

Приложение пишет в `logs/application.log` параметры запуска и источников, статусы и длительность HTTP-запросов, результаты чтения и записи и сведения об ошибках. Адрес HTTP-запроса записывается без query-строки.

| Код завершения приложения | Значение |
| --- | --- |
| `0` | Команда выполнена успешно. |
| `1` | Ошибка аргументов или выполнения команды, например ошибка чтения файла. |
| `2` | Один или несколько выбранных источников завершились с ошибкой. Приложение сохраняет успешные результаты, если они есть. |
| `130` | Выполнение прервано. |

При запуске через `./gradlew run` ненулевой код приложения отображается как ошибка задачи Gradle. Консоль и журнал содержат результат опроса и имена источников, завершившихся с ошибкой.

## Структура проекта

| Пакет | Ответственность |
| --- | --- |
| `cli` | `CommandParser` проверяет аргументы, `CommandLine` выполняет выбранную команду и печатает результат. |
| `aggregator` | Последовательный опрос источников, сохранение успешных результатов и фильтрация при чтении. |
| `source` | Интерфейс `IDataSource` и адаптеры трёх API. Каждый адаптер преобразует свой ответ в общую модель. |
| `net` | Построение URL с query-параметрами и отправка HTTP-запросов через `java.net.http.HttpClient`. |
| `data` | Модели `DataRecord` и `DataItem`. |
| `storage` | Интерфейс `IDataStorage` и реализации для JSON и CSV. |
| `utils` | Загрузка ключей из `.env`. |

`Main` собирает зависимости и задаёт демонстрационные параметры. Новый источник реализует `IDataSource` и подключается в реестре `Main` без изменения логики опроса. Для JSON используется Jackson, для CSV — Apache Commons CSV, для логирования — SLF4J и Logback.
