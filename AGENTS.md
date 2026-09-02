# SMS Center Project Workflow

این فایل لایه مدیریت توسعه پروژه است و جزو منطق APK، API، دیتابیس یا Gateway نیست.

## روند اجباری هر کار

1. ابتدا هدف، محدوده، فایل‌های مجاز و معیار پذیرش را مشخص کن.
2. قبل از تغییر، کد و تست‌های مرتبط را بخوان.
3. برای تغییرهای حساس ابتدا تست شکست‌خورده یا سناریوی بازتولید بساز.
4. کوچک‌ترین تغییر لازم را انجام بده.
5. Build و تست‌های مرتبط را اجرا کن.
6. تغییرات را از نظر امنیت، Regression و مرزهای پروژه Review کن.
7. نتیجه، فایل‌ها، تست‌ها و مشکلات باقی‌مانده را در PROJECT_ROADMAP.md ثبت کن.

## تقسیم نقش ایجنت‌ها

- Architecture: قراردادها، مدل داده و مرز لایه‌ها
- Android Core: SMS Provider، Permission و Lifecycle
- Sync and Deletion: Snapshot، Fingerprint و Tombstone
- Sending: Queue، SIM، Retry، Schedule و Delivery
- Integration: API، Webhook و Flask
- Security: Secret، HMAC، Log و Permission Review
- QA: Unit، Integration، UI و دستگاه واقعی
- Release: Build، Signing، Documentation و Pilot
- Integrator: ادغام نهایی، Regression و به‌روزرسانی دفتر کار

## قوانین هماهنگی

- ایجنت‌ها نباید هم‌زمان یک فایل مشترک را تغییر دهند.
- هر تغییر باید در Worktree یا Branch جدا انجام شود.
- ادغام فقط پس از Build، تست و Review انجام شود.
- تغییر Backend، API، Database یا Gateway بدون ثبت دلیل و تأیید معیار پذیرش ممنوع است.
- تغییر تزئینی UI نباید رفتار فعلی را تغییر دهد.
- Secret، متن کامل SMS، API Key و اطلاعات شخصی نباید در Source یا Log ثبت شود.
- خطای Build یا تست نباید با حذف تست یا خاموش‌کردن کنترل کیفیت پنهان شود.
- نیاز به گوشی، مجوز Android یا بررسی انسانی باید به‌عنوان Blocker ثبت شود.

## ترتیب اجرای کار

plan -> test -> implement -> review -> verify -> document

## تعریف پایان کار

یک کار فقط وقتی تمام است که:

- معیار پذیرش آن تأیید شده باشد.
- تست مرتبط اجرا شده باشد.
- Regression مهم بررسی شده باشد.
- فایل‌ها و تصمیم‌های معماری ثبت شده باشند.
- چک‌لیست همان بخش در PROJECT_ROADMAP.md تیک خورده باشد.

