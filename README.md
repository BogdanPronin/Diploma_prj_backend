# Diploma Project Backend

Бэкенд для работы с почтовыми сервисами, реализованный на Kotlin и Spring. Обеспечивает получение, отправку и группировку писем, интеграцию с iRedMail, Gmail, Яндекс.Почтой и чат-формат.

## Требования

- **Docker** и **Docker Compose**
- **PostgreSQL**: 13 или выше (через iRedMail)
- Доступ к iRedMail и учетным данным для Gmail/Яндекс.Почты

## Установка и развертывание

Бэкенд развертывается через Docker Compose вместе с другими сервисами.

1. **Клонируйте репозитории**:
   ```bash
   mkdir mail-client && cd mail-client
   git clone https://github.com/BogdanPronin/SMTP_server_back.git smtpauth
   git clone https://github.com/BogdanPronin/Diploma_prj_backend.git backend
   git clone https://github.com/BogdanPronin/Diploma_Prj_Front.git front
   ```

2. **Настройте docker-compose.yml**:
   Используйте файл, описанный в README для `SMTP_server_back`. Бэкенд настроен на порт `8080`.

3. **Запустите сервисы**:
   ```bash
   docker-compose up -d
   ```

4. **Проверьте доступность**:
   - API: `http://<your-ip>:8080`
   - Пример эндпоинта: `GET /api/mail/inbox`

## Тестирование

- Используйте Postman для проверки:
  - `GET /api/mail/inbox` — входящие письма.
  - `POST /api/mail/send` — отправка письма.
  - `GET /api/mail/thread/<threadId>` — чат-формат.

## Примечания

- Настройте учетные данные для Gmail/Яндекс.Почты (OAuth или пароли приложений).
- Откройте порты 8080, 587 (SMTP), 143 (IMAP).
- Убедитесь, что iRedMail работает корректно.
