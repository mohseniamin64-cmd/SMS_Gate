# Phone HTTP Server API

When Gateway is enabled, the Android foreground service binds an HTTP server to
`0.0.0.0:<phoneServerPort>`. Use one of the IP addresses shown in the Gateway
screen from a computer on the same Wi-Fi/LAN. The Flask backend remains an
optional legacy panel and is not required for direct phone access.

All application endpoints require either `Authorization: Bearer <phoneServerApiKey>`
or `X-API-Key: <phoneServerApiKey>`. The Authorization scheme must be exactly
Bearer. The key is generated locally, encrypted with Android Keystore, and is
never written to logs or returned by the API.

The transport is HTTP. LAN-only mode is enabled by default and rejects clients
outside local/loopback address ranges. Use a trusted Wi-Fi/LAN; do not expose
the port to the public internet. Browser clients must set one exact allowed
Origin in the Android settings; `*` is not accepted.

On Android 15 and newer, `BOOT_COMPLETED` schedules a persisted JobScheduler
fallback because Android restricts direct data-sync foreground-service starts
from boot. The job attempts to start the foreground service and records a safe
bounded sync if the OS rejects that start; opening the app restores the long-
lived HTTP listener.

## Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/health` | Authenticated liveness and endpoint information |
| GET | `/api/status` | Device id, IP addresses, port and pending SMS count |
| GET | `/api/sms/read?limit=100&offset=0` | Read the local SMS snapshot |
| POST | `/api/sms/send` | Queue and process an SMS |
| POST | `/api/sms/sync` | Refresh the Android SMS provider snapshot |
| GET | `/api/call-logs` | Read local call logs (without contact names) |
| POST | `/api/call-logs/sync` | Refresh the Android provider and local upload outbox |

Send payload:

```json
{"to":"+15551234567","body":"Hello","simSlot":-1,"requestId":"optional-id"}
```

`requestId` is idempotent: repeating it returns the original queued request.
The queue uses a transactional get-or-insert path plus an atomic pending-row
claim, so concurrent requests/workers cannot dispatch the same new request
twice. Existing duplicate legacy rows are preserved during migration.
The normal local queue limits, working hours, test mode and SMS permissions are
still applied before Android sends a message.

`POST /api/call-logs/sync` does not upload data to Flask. It refreshes the
device provider and outbox; legacy Flask upload remains an optional background
operation when its panel URL is configured.
