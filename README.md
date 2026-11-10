# med-records-api

[![CI](https://github.com/VEYMK/med-records-api/actions/workflows/ci.yml/badge.svg)](https://github.com/VEYMK/med-records-api/actions/workflows/ci.yml)

REST API для медицинского центра: врачи и пациенты, запись на приём, роли и JWT-аутентификация.

Проект специально сделан как «портфолио-проект под Java-разработчика»: здесь есть Spring Web,
Spring Security + OAuth2 Resource Server, Spring Data JPA, PostgreSQL с осознанными индексами,
JUnit 5 + Mockito, интеграционные тесты на `@SpringBootTest`, многоэтапный Dockerfile и CI.

---

## Ключевые технические решения

| Решение | Почему так, а не иначе |
|---|---|
| **Spring Security + OAuth2 Resource Server, JWT (HS256)** | Стандартный способ аутентификации вместо самописных фильтров и сессий. Формат токена и проверка подписи совместимы с Keycloak/OIDC: переход на внешний провайдер — замена источника токенов, а не переписывание security-слоя. |
| **PostgreSQL, а не H2 в продакшене** | Нужны честные транзакции, `FOREIGN KEY`, `UNIQUE` и составные индексы. H2 используется только в тестах в режиме `MODE=PostgreSQL`, чтобы `mvn test` работал без Docker. |
| **`UNIQUE (doctor_id, appointment_date)`** | Защита от двойного бронирования на уровне БД. Даже при гонке двух запросов второй `INSERT` упадёт с `409`, а не создаст две записи. |
| **Optimistic locking (`@Version`)** | Защита от потерянного обновления при одновременной смене статуса приёма двумя врачами. |
| **Составные индексы `(doctor_id, appointment_date)` и `(patient_id, appointment_date)`** | Самые частые запросы — расписание врача и история пациента. Правило левого префикса даёт фильтрацию и сортировку одним индексом. |
| **RFC 7807 `ProblemDetail`** | Единый машиночитаемый формат ошибок вместо самодельных JSON-структур. |
| **Multi-stage Docker** | В финальный образ попадает только JRE и jar: меньше размер, меньше уязвимостей, нет Maven и исходников. |
| **GitHub Actions + GitLab CI** | Пайплайны запускают тесты до сборки образа: красные тесты не пропускают образ в registry. |
| **H2 вместо Testcontainers в тестах** | Компромисс ради скорости и запуска без Docker. Ограничение осознано; Testcontainers — первый пункт в списке улучшений. |

---

## 1. Стек

| Слой | Технологии |
|---|---|
| Язык | Java 21 |
| Framework | Spring Boot 3.4 |
| Web | Spring MVC, Bean Validation |
| Security | Spring Security, OAuth2 Resource Server, JWT (HS256), BCrypt |
| Persistence | Spring Data JPA, Hibernate, PostgreSQL 16 |
| Ошибки | RFC 7807 `ProblemDetail` |
| Тесты | JUnit 5, Mockito, MockMvc, H2 (в режиме PostgreSQL) |
| Сборка | Maven |
| Контейнеры | Docker (multi-stage), Docker Compose |
| CI | GitLab CI и GitHub Actions |

---

## 2. Быстрый старт

### Вариант A — Docker Compose (рекомендуется)

Нужен установленный Docker Desktop / Docker Engine. Запуск — одной командой:

```bash
docker compose up --build
```

`docker-compose.yml` содержит dev-значения по умолчанию для БД и JWT, поэтому `.env`
не обязателен. Для переопределения секретов скопируйте `.env.example` в `.env`:

```bash
cp .env.example .env   # опционально; в продакшене пароль и APP_JWT_SECRET нужно заменить
```

> Значения по умолчанию предназначены только для локального запуска. В продакшене
> задайте сильный `POSTGRES_PASSWORD` и `APP_JWT_SECRET` (минимум 32 байта) через
> переменные окружения или секреты CI/CD.

Поднимется два контейнера:

- `medrecords-postgres` — PostgreSQL 16;
- `medrecords-api` — приложение на `http://localhost:8080`.

Проверка:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

### Вариант B — локально

Нужны JDK 21, Maven 3.9+ и PostgreSQL 16.

```bash
createdb medrecords

export DB_URL=jdbc:postgresql://localhost:5432/medrecords
export DB_USERNAME=medrecords
export DB_PASSWORD=medrecords
export APP_JWT_SECRET="$(openssl rand -base64 48)"

mvn spring-boot:run
```

Windows PowerShell:

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/medrecords"
$env:DB_USERNAME="medrecords"
$env:DB_PASSWORD="medrecords"
$env:APP_JWT_SECRET="dev-only-secret-change-me-0123456789abcdef0123456789abcdef"
mvn spring-boot:run
```

### Переменные окружения

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `SERVER_PORT` | `8080` | Порт HTTP |
| `DB_URL` | `jdbc:postgresql://localhost:5432/medrecords` | JDBC URL |
| `DB_USERNAME` | `medrecords` | Пользователь БД |
| `DB_PASSWORD` | `medrecords` | Пароль БД |
| `JPA_DDL_AUTO` | `update` | Стратегия Hibernate DDL |
| `APP_JWT_SECRET` | dev-значение | Секрет HS256, **минимум 32 байта** |
| `APP_JWT_ISSUER` | `med-records-api` | `iss` в токене |
| `APP_JWT_TTL` | `PT1H` | Время жизни access-токена (ISO-8601) |

> В продакшене `JPA_DDL_AUTO` должен быть `validate`, а схема — управляться Flyway/Liquibase.
> Здесь `update` оставлен, чтобы проект запускался одной командой.

---

## 3. API

### Аутентификация

| Метод | Путь | Доступ | Описание |
|---|---|---|---|
| `POST` | `/api/auth/register` | все | Регистрация врача или пациента |
| `POST` | `/api/auth/login` | все | Логин, возвращает JWT |
| `GET` | `/api/auth/me` | любой токен | Текущий пользователь |

### Приёмы

| Метод | Путь | Доступ | Описание |
|---|---|---|---|
| `POST` | `/api/appointments` | `DOCTOR` | Создать приём для пациента |
| `GET` | `/api/appointments` | `DOCTOR`, `PATIENT` | Свои приёмы |
| `GET` | `/api/appointments/{id}` | участник приёма | Один приём |
| `PATCH` | `/api/appointments/{id}/status` | `DOCTOR` | `COMPLETED` / `CANCELLED` |
| `DELETE` | `/api/appointments/{id}` | `PATIENT` | Отменить свой приём |
| `GET` | `/api/users/doctors` | любой токен | Справочник врачей |

### Пример: регистрация → логин → создание приёма

```bash
# 1. Врач
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"doctor@example.com","password":"password123","fullName":"Dr. House","role":"DOCTOR"}'

# 2. Пациент
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"patient@example.com","password":"password123","fullName":"John Doe","role":"PATIENT"}'

# 3. Логин врача
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"doctor@example.com","password":"password123"}' | jq -r .accessToken)

# 4. Создание приёма (patientId возьмите из ответа регистрации)
curl -X POST http://localhost:8080/api/appointments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
        "patientId": "<UUID пациента>",
        "appointmentDate": "2030-01-15T10:00:00Z",
        "reason": "Annual checkup",
        "notes": "Fasting before the visit"
      }'
```

Готовые запросы также лежат в [`api-examples.http`](api-examples.http) — файл понимают
IntelliJ HTTP Client и расширение REST Client для VS Code.

---

## 4. Модель данных и индексы

### Таблицы

**`users`**

| Колонка | Тип | Комментарий |
|---|---|---|
| `id` | `uuid` | PK |
| `email` | `varchar(255)` | логин, уникальный индекс |
| `password_hash` | `varchar(100)` | BCrypt |
| `full_name` | `varchar(150)` | |
| `role` | `varchar(20)` | `DOCTOR` / `PATIENT` |
| `enabled` | `boolean` | блокировка пользователя |
| `created_at` | `timestamptz` | |

**`appointments`**

| Колонка | Тип | Комментарий |
|---|---|---|
| `id` | `uuid` | PK |
| `doctor_id` | `uuid` | FK → `users.id` |
| `patient_id` | `uuid` | FK → `users.id` |
| `appointment_date` | `timestamptz` | время приёма |
| `status` | `varchar(20)` | `SCHEDULED` / `COMPLETED` / `CANCELLED` |
| `reason` | `varchar(500)` | причина визита |
| `notes` | `varchar(2000)` | заметки врача |
| `version` | `bigint` | optimistic locking |
| `created_at`, `updated_at` | `timestamptz` | |

Связи: `appointments.doctor_id` и `appointments.patient_id` — оба FK на `users`.
Это уже не «одна таблица», а реальная реляционная модель с двумя связями.

### Почему PostgreSQL

1. **ACID и честные транзакции.** PostgreSQL даёт строгие гарантии атомарности,
   согласованности, изолированности и долговечности. Для записи на приём это критично:
   два человека не должны занять один слот.
2. **Ограничения на уровне БД.** `NOT NULL`, `FOREIGN KEY`, `UNIQUE`, `CHECK` — база сама
   защищает данные, даже если в приложении есть баг или гонка.
3. **Сильные индексы.** B-tree, составные, частичные (`WHERE`), GIN/JSONB, выраженные индексы.
   Можно проектировать доступ под конкретные запросы, а не «индексировать всё».
4. **Типы.** `timestamptz`, `uuid`, `numeric`, `jsonb` — без костылей.
5. **MVCC.** Читатели не блокируют писателей; для API с параллельными запросами это важно.
6. **Экосистема.** Hibernate/Spring Data JPA отлично поддерживают PostgreSQL, а сам PostgreSQL
   легко запускается в Docker и в managed-облаке.

### Как спроектированы индексы

Индексы объявлены прямо в сущностях (`@Table(indexes = ...)`), а не «где-то в SQL»:

```java
@Index(name = "idx_appointments_appointment_date", columnList = "appointment_date")
@Index(name = "idx_appointments_doctor_date", columnList = "doctor_id, appointment_date")
@Index(name = "idx_appointments_patient_date", columnList = "patient_id, appointment_date")
```

- **`idx_appointments_appointment_date`** — глобальный индекс по дате. Нужен для отчётов
  «все приёмы за период», где фильтр не привязан к врачу или пациенту.
- **`idx_appointments_doctor_date`** — составной индекс `(doctor_id, appointment_date)`.
  Самый частый запрос — «расписание врача по датам». В составном B-tree индексе работает
  **правило левого префикса**: сначала фильтруем по `doctor_id`, затем данные уже
  отсортированы по `appointment_date`, поэтому `ORDER BY` не требует отдельной сортировки.
- **`idx_appointments_patient_date`** — то же самое для истории пациента.
- **`uk_appointments_doctor_slot`** — `UNIQUE (doctor_id, appointment_date)`. Это не просто
  индекс, а бизнес-правило на уровне БД: один врач не может иметь два приёма в одно время.
  Даже если два запроса придут одновременно и оба пройдут проверку в приложении, второй
  `INSERT` упадёт с нарушением уникальности — приложение вернёт `409 Conflict`.
- **`idx_users_email`** (unique) — ускоряет логин и гарантирует уникальность email.
- **`idx_users_role`** — ускоряет `GET /api/users/doctors`.

Важно помнить: индексы ускоряют чтение, но замедляют запись и занимают место. Поэтому
индексируются конкретные пути доступа, а не каждая колонка.

---

## 5. Безопасность

### Схема

1. `POST /api/auth/register` — создаёт пользователя, пароль хешируется BCrypt.
2. `POST /api/auth/login` — Spring Security `AuthenticationManager` проверяет пароль,
   затем приложение выпускает JWT.
3. Клиент передаёт токен в заголовке `Authorization: Bearer <token>`.
4. Spring Security (OAuth2 Resource Server) проверяет подпись, `iss`, `exp` и достаёт роли
   из claim `roles`.
5. Методы защищены `@PreAuthorize("hasRole('DOCTOR')")` и проверками владения объектом
   в сервисном слое.

### Claims в токене

```json
{
  "iss": "med-records-api",
  "sub": "<user uuid>",
  "email": "doctor@example.com",
  "name": "Dr. House",
  "roles": ["DOCTOR"],
  "iat": 1893456000,
  "exp": 1893459600
}
```

### Коды ответов

| Ситуация | Код |
|---|---|
| Нет токена / токен просрочен / подпись неверна | `401 Unauthorized` |
| Токен валиден, но роли не хватает | `403 Forbidden` |
| Чужой приём (не участник) | `403 Forbidden` |
| Дубль email / занятый слот | `409 Conflict` |
| Ошибка валидации | `400 Bad Request` + `errors` в `ProblemDetail` |

### Симуляция Keycloak

Проект использует стандартный **OAuth2 Resource Server**: формат JWT, проверка подписи и
маппинг ролей такие же, как при работе с Keycloak. Отличие только в том, что токен выпускает
само приложение (HS256), а не внешний OIDC-провайдер. Это позволяет запускать проект без
инфраструктуры и при этом не «изобретать» нестандартную авторизацию.

---

## 6. Тесты

```bash
mvn test        # unit + integration
mvn verify      # то же + проверки жизненного цикла пакета
```

### Unit-тесты (JUnit 5 + Mockito)

- `AppointmentServiceTest` — создание приёма, запрет записи к не-пациенту, защита от
  двойного бронирования, права врача на смену статуса, отмена пациентом.
- `AuthServiceTest` — нормализация email, BCrypt-хеширование, дубликат email,
  неверный пароль, выпуск токена.

Сервисы тестируются изолированно: репозитории и `AuthenticationManager` замоканы, поэтому
тесты быстрые и не требуют БД.

### Интеграционные тесты (`@SpringBootTest`)

- `AuthFlowIntegrationTest` — регистрация → логин → доступ к `/api/auth/me`, `401` без токена,
  `401` при неверном пароле, `409` при дубликате, `400` при ошибках валидации.
- `AppointmentIntegrationTest` — полный жизненный цикл: врач создаёт приём, пациент видит его,
  пациент не может менять статус, врач завершает приём, дубль слота даёт `409`,
  пациент отменяет приём, чужой пациент получает `403`.

Интеграционные тесты поднимают реальный Spring-контекст (security-фильтры, JWT-decoder, JPA)
и ходят в API через `MockMvc`. В тестах используется H2 в режиме `MODE=PostgreSQL`, чтобы
`mvn test` работал без Docker. Для проверки именно PostgreSQL можно добавить Testcontainers
(см. раздел «Что можно улучшить»).

---

## 7. Docker: образ и контейнер

### Образ vs контейнер

- **Образ (image)** — неизменяемый шаблон: слои файловой системы + метаданные (команда запуска,
  порты, переменные). Образ нельзя «изменить во время работы».
- **Контейнер (container)** — запущенный экземпляр образа. У него есть тонкий записываемый слой
  поверх образа; всё, что приложение пишет в файловую систему контейнера, живёт только пока
  контейнер существует.

Один образ можно запустить в 10 контейнерах — они будут независимыми процессами с общим
шаблоном. Если удалить контейнер, его записываемый слой исчезнет; образ останется.

### Multi-stage сборка

`Dockerfile` состоит из двух стадий:

1. **`build`** — образ `maven:3.9.9-eclipse-temurin-21` (JDK + Maven). Скачивает зависимости,
   компилирует и собирает jar.
2. **`runtime`** — образ `eclipse-temurin:21-jre-alpine` (только JRE). Копирует **только jar**
   из первой стадии.

В финальный образ не попадают Maven, исходники и кэш зависимостей. Это даёт меньший размер,
меньше уязвимостей и быстрый старт. Приложение запускается от непривилегированного
пользователя `app`, есть `HEALTHCHECK`.

### Команды

```bash
# собрать образ
docker build -t med-records-api:local .

# запустить контейнер
docker run --rm -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/medrecords \
  -e DB_USERNAME=medrecords \
  -e DB_PASSWORD=medrecords \
  -e APP_JWT_SECRET=dev-only-secret-change-me-0123456789abcdef0123456789abcdef \
  med-records-api:local

# посмотреть образы и контейнеры
docker images
docker ps

# логи и shell внутри контейнера
docker logs -f <container>
docker exec -it <container> sh

# остановить и удалить
docker stop <container>
docker rm <container>
docker rmi med-records-api:local
```

`docker compose up --build` делает то же самое, но ещё и поднимает PostgreSQL, ждёт его
`healthcheck` и прокидывает переменные окружения. Данные БД сохраняются в named volume `pgdata`.

---

## 8. CI

### GitLab CI (`.gitlab-ci.yml`)

Три стадии:

1. **`test`** — `mvn verify`: unit- и интеграционные тесты. JUnit-отчёты публикуются как
   артефакты (`reports:junit`), поэтому результаты видны во вкладке Tests в GitLab.
2. **`package`** — сборка исполняемого jar, артефакт на неделю.
3. **`docker`** — сборка и пуш образа в GitLab Container Registry. Запускается только на
   default-ветке, использует Docker-in-Docker (`docker:27-dind`).

### GitHub Actions (`.github/workflows/ci.yml`)

Альтернативный pipeline: checkout → JDK 21 (Temurin) → `mvn verify` → выгрузка отчётов →
сборка jar → `docker build`. Запускается на каждый push и pull request.

Оба пайплайна запускают тесты **до** сборки образа: если тесты красные, образ не соберётся
и не попадёт в registry.

---

## 9. ACID и транзакции

- Все методы сервисов, меняющие данные, помечены `@Transactional`. При unchecked-исключении
  транзакция откатывается целиком.
- **Атомарность бронирования** обеспечивается не только проверкой в коде, но и уникальным
  ограничением `uk_appointments_doctor_slot`. Гонка двух запросов заканчивается `409`,
  а не двумя записями на одно время.
- **Optimistic locking** (`@Version`) защищает от «потерянного обновления»: если два врача
  одновременно меняют один приём, второй получит ошибку и сможет перечитать данные.
- PostgreSQL по умолчанию использует `READ COMMITTED`; для большинства CRUD-сценариев этого
  достаточно. Для сложных отчётов можно явно поднять уровень изоляции.

---

## 10. Структура проекта

```
med-records-api/
├── src/main/java/com/example/medrecords/
│   ├── config/SecurityConfig.java
│   ├── domain/            User, Role, Appointment, AppointmentStatus
│   ├── dto/               auth/*, appointment/*
│   ├── exception/         ApiException + наследники
│   ├── repository/        UserRepository, AppointmentRepository
│   ├── security/          JwtProperties, JwtTokenService
│   ├── service/           AuthService, AppointmentService, UserService
│   └── web/               AuthController, AppointmentController, UserController,
│                          GlobalExceptionHandler
├── src/main/resources/application.yml
├── src/test/java/...      unit- и интеграционные тесты
├── src/test/resources/application-test.yml
├── Dockerfile
├── docker-compose.yml
├── .gitlab-ci.yml
├── .github/workflows/ci.yml
├── api-examples.http
└── README.md
```

---

## 11. Переход на реальный Keycloak (опционально)

Проект уже использует OAuth2 Resource Server, поэтому переход сводится к замене источника
токенов:

1. Запустите Keycloak (например, `quay.io/keycloak/keycloak:26.0` с `start-dev`).
2. Создайте realm `medrecords`, клиент `med-records-api`, роли `DOCTOR` и `PATIENT`.
3. В `application.yml` укажите:
   ```yaml
   spring:
     security:
       oauth2:
         resourceserver:
           jwt:
             issuer-uri: http://localhost:8081/realms/medrecords
   ```
4. Удалите локальные beans `JwtDecoder`/`JwtEncoder` (или оставьте их под отдельным профилем
   `local`), чтобы Spring Security взял decoder из `issuer-uri` и JWKS.
5. Замените `JwtAuthenticationConverter`: у Keycloak роли лежат в `realm_access.roles`, а не в
   `roles`. Нужен кастомный конвертер, который читает `realm_access.roles` и добавляет префикс
   `ROLE_`.
6. Локальные `/api/auth/register` и `/api/auth/login` можно оставить для обратной совместимости
   или удалить — тогда пользователей и токены полностью ведёт Keycloak.

---

## 12. Что можно улучшить

- Flyway/Liquibase вместо `ddl-auto=update` и `validate` в продакшене.
- Testcontainers с реальным PostgreSQL в интеграционных тестах.
- Refresh-токены и rotation.
- Пагинация и фильтрация списков приёмов.
- OpenAPI/Swagger (`springdoc-openapi`).
- Rate limiting и аудит действий.
- Метрики (Micrometer/Prometheus) и трассировка.
