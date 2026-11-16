# AlertBackend

MQTTS-брокер для Android-клиента RedAlert. Брокер встроен в приложение (MQTTnet), для локального теста отправляет захардкоженные алерты.

## Запуск

```bash
dotnet run
```

- MQTTS-брокер: порт `8883`, TLS, логин и пароль — `appsettings.json` → `Mqtt`
- HTTP: порт `5080`

При первом запуске создаётся самоподписанный сертификат `certs/broker.pfx` и `certs/broker.crt`.
`broker.crt` нужно скопировать в клиент: `AlertMobile/app/src/main/res/raw/broker.crt`.
Если сертификат пересоздан (папка `certs` удалена), клиент нужно пересобрать с новым файлом.

## Отправить тестовый алерт

```powershell
Invoke-RestMethod -Method Post http://localhost:5080/api/alerts/test
```

Каждый вызов публикует следующий алерт из списка в `HardcodedAlertSource.cs`: авария → восстановление и т.д.

## Формат сообщения

Топик `infra/alerts`, QoS 1:

```json
{"id":"pg-01-down","severity":"critical","title":"DB down","body":"pg-01 не отвечает","host":"pg-01","ts":1760000000}
```

- `severity`: `critical` | `warning` | `info` | `resolved`
- `ts`: unix-время события в секундах
- одинаковый `id` у аварии и восстановления — клиент объединяет их в одну карточку

## Вебхук Zabbix

`POST /api/webhooks/zabbix`, заголовок `Authorization: Bearer <токен>`. Токен — `appsettings.json` → `Webhooks:ZabbixToken` или переменная окружения `Webhooks__ZabbixToken`. Пустой токен — все запросы получают 401.

Настройка на стороне Zabbix: [zabbix/README.md](zabbix/README.md).

```powershell
Invoke-RestMethod -Method Post http://localhost:5080/api/webhooks/zabbix -Headers @{Authorization="Bearer change-me"} -ContentType application/json -Body '{"eventId":"1","eventValue":"1","severity":"5","name":"pg-01 is unavailable","host":"pg-01"}'
```
