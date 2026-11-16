# Media type «IT Аварии»

## 1. Создать media type

Alerts --> Media types  --> Create media type:

- Name: `IT Аварии`
- Type: `Webhook`
- Parameters:

| Name | Value |
|---|---|
| `URL` | `http://<адрес бэкенда>:5080/api/webhooks/zabbix` |
| `Token` | токен (выдаём вместе с адресом) |
| `EventId` | `{EVENT.ID}` |
| `EventValue` | `{EVENT.VALUE}` |
| `EventUpdateStatus` | `{EVENT.UPDATE.STATUS}` |
| `Severity` | `{EVENT.NSEVERITY}` |
| `Name` | `{EVENT.NAME}` |
| `Host` | `{HOST.NAME}` |
| `Message` | `{ALERT.MESSAGE}` |

- Script: содержимое `media-type.js`
- Timeout: `10s`

Вкладка Message templates: добавить `Problem` и `Problem recovery` (текст из них уходит в `Message`).

## Что получает бэкенд

```json
{"eventId":"12345","eventValue":"1","severity":"4","name":"pg-01 is unavailable","host":"pg-01","message":"..."}
```

- `eventValue`: `1` — проблема, `0` — восстановление (у восстановления тот же `eventId`)
- `severity`: 4–5 (High, Disaster) → авария, 2–3 (Warning, Average) → предупреждение, 0–1 → инфо
