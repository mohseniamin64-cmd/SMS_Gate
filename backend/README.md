# SMS Center Call Log API

این ماژول فقط ذخیره و همگام‌سازی Call Log را اضافه می‌کند و مسیرهای قدیمی SMS پنل را جایگزین نمی‌کند. مسیرهای legacy مانند `GET /api/messages/pending` و `POST /api/messages/status` باید در همان پنل Flask موجود باقی بمانند.

## اجرا

Python 3.10 یا جدیدتر لازم است. کلید API را در محیط تنظیم کنید:

```powershell
$env:SMS_CENTER_API_KEY = "change-me"
python -m flask --app backend.app run --host 0.0.0.0 --port 5000
```

پایگاه‌دادهٔ پیش‌فرض `backend/call_logs.db` است. برای تست یا استقرار جداگانه می‌توان مسیر آن را با `CALL_LOG_DB` تغییر داد.

تمام مسیرهای این ماژول به Header زیر نیاز دارند:

```text
Authorization: Bearer <SMS_CENTER_API_KEY>
```

## مسیرها

- `GET /api/health` — بررسی سلامت و احراز هویت
- `POST /api/call-logs/sync` — ثبت batch حداکثر ۵۰۰ تماس به‌صورت idempotent
- `GET /api/call-logs?deviceId=...&type=INCOMING|OUTGOING|MISSED` — فهرست صفحه‌بندی‌شدهٔ تماس‌های یک دستگاه

`contactName` عمداً در جدول ذخیره نمی‌شود و اگر از دستگاه ارسال شود باید `null` یا خالی باشد؛ نام مخاطب فقط روی خود Android باقی می‌ماند.

برای اجرای تست:

```powershell
python -m pytest backend/tests -q
```

در Debug Android، آدرس `IP:port` برای توسعهٔ LAN با HTTP مجاز است. پیکربندی Release cleartext را مسدود می‌کند و باید از HTTPS استفاده شود.
