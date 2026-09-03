# Phone HTTPS Server API (Phone-as-Server)

When Gateway is enabled, the Android foreground service binds an HTTPS server to
`0.0.0.0:<phoneServerPort>` (default port: `3030`). Access the server from a computer or
client on the same Wi-Fi/LAN over HTTPS:
`https://<PHONE-IP>:3030`. The Flask backend remains an
optional legacy panel and is not required for direct phone access.

## TLS Security & Certificate Fingerprint

- The server uses genuine TLS via `SSLServerSocket`.
- A 2048-bit RSA key pair and self-signed X.509 certificate are generated and maintained in `AndroidKeyStore`.
- The private key is never written to Room, logs, or API responses.
- The SHA-256 fingerprint of the certificate is displayed in the app's Gateway screen and returned in the `/api/status` endpoint for clients to verify.

## Authentication & API Key

All endpoints require either:
- `Authorization: Bearer <phoneServerApiKey>`
- `X-API-Key: <phoneServerApiKey>`

Verification is performed in constant time using `MessageDigest.isEqual`.
The API key is encrypted using AES/GCM and stored in `AndroidKeyStore`. It is never stored in plaintext in the database or logs. Users can view the key in the app via a one-time 60-second reveal or copy it securely (marked sensitive on Android 13+).

## Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/health` | Authenticated liveness and endpoint information |
| GET | `/api/status` | Device id, transport (`https`), certificate fingerprint, IP addresses, port and pending SMS count |
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

## Android 15 Lifecycle

On Android 15+ (API 35+), `BOOT_COMPLETED` background execution is handled by `JobScheduler` (`SmsGatewayJobService`). Foreground service execution rejections and transient sync errors are retried with exponential backoff (`JobRetryPolicy`).

