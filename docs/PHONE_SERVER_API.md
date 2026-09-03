# Phone HTTP Server API

When Gateway is enabled, the Android foreground service binds an HTTP server to
`0.0.0.0:<phoneServerPort>`. Use one of the IP addresses shown in the Gateway
screen from a computer on the same Wi-Fi/LAN. The Flask backend remains an
optional legacy panel and is not required for direct phone access.

All endpoints require either `Authorization: Bearer <phoneServerApiKey>` or
`X-API-Key: <phoneServerApiKey>`. The key is generated locally and is never
written to logs or returned by the API.

## Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/health` | Authenticated liveness and endpoint information |
| GET | `/api/status` | Device id, IP addresses, port and pending SMS count |
| GET | `/api/sms/read?limit=100&offset=0` | Read the local SMS snapshot |
| POST | `/api/sms/send` | Queue and process an SMS |
| POST | `/api/sms/sync` | Refresh the Android SMS provider snapshot |
| GET | `/api/call-logs` | Read local call logs (without contact names) |
| POST | `/api/call-logs/sync` | Refresh/read local call logs |

Send payload:

```json
{"to":"+15551234567","body":"Hello","simSlot":-1,"requestId":"optional-id"}
```

`requestId` is idempotent: repeating it returns the original queued request.
The normal local queue limits, working hours, test mode and SMS permissions are
still applied before Android sends a message.
