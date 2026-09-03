# دفتر کار و نقشه‌راه پروژه SMS Center Android Gateway

آخرین به‌روزرسانی: 2026-09-02
وضعیت کل: MVP نرم‌افزاری build/test شده؛ آمادهٔ نصب آزمایشی، تأیید دستگاه واقعی باقی است.

## ۱. روش استفاده

- [x] فقط وقتی ثبت می‌شود که کار انجام و آزمون آن تأیید شده باشد.
- [ ] یعنی کار ناقص یا آزمایش‌نشده است.
- صرف وجود کد به معنی حل‌شدن مشکل نیست.
- پس از هر بخش، نتیجه، آزمون‌ها و فایل‌های تغییرکرده در گزارش پیشرفت ثبت می‌شود.

## ۲. هدف نهایی

ساخت برنامه مستقل اندرویدی جایگزین SMSGate که:

- Inbox و Sent را مستقیماً از Android SMS Provider بخواند.
- حذف واقعی پیام در گوشی را تشخیص دهد و به پنل منتقل کند.
- پیام حذف‌شده را پس از Sync یا Restart برنگرداند.
- پیام جدید همان شماره را با پیام حذف‌شده اشتباه نگیرد.
- پیام را با SIM انتخاب‌شده ارسال کند.
- وضعیت‌های Pending، Processing، Sent، Delivered، Failed، Cancelled و Expired را مدیریت کند.
- در نبود شبکه، Queue و Eventها را محلی نگه دارد و بعداً همگام کند.
- با پنل Flask فعلی از طریق API و Webhook امن کار کند.
- روی فارسی، RTL، تک‌سیم و دوسیم پایدار باشد.
- Secret، شماره و متن پیام را در Git یا لاگ Release افشا نکند.

### معیار موفقیت نهایی

- [ ] اجرای پایدار حداقل ۷۲ ساعت روی گوشی واقعی
- [ ] دریافت، ارسال و Delivery واقعی
- [ ] انعکاس حذف گوشی در پنل
- [ ] عدم بازگشت پیام حذف‌شده پس از Restart
- [ ] عدم Duplicate یا فقدان پیام در قطعی شبکه
- [ ] APK Release امضاشده
- [ ] تأیید امنیت و حریم خصوصی

## ۳. وضعیت فعلی

- [x] ریپوزیتوری دریافت و ساختار آن بررسی شد.
- [x] Kotlin، Compose، Room، Retrofit و Foreground Service شناسایی شدند.
- [x] کد اولیه خواندن مستقیم Android SMS Provider شناسایی شد.
- [x] Queue، Receiver، Webhook و Tombstone اولیه شناسایی شدند.
- [ ] Build موفق از Clone تازه
- [ ] اجرای موفق تست‌های موجود
- [ ] نصب APK فعلی روی گوشی
- [ ] ثبت رفتار فعلی روی نسخه‌های هدف Android

تیک‌های بالا فقط بررسی وضعیت موجود هستند، نه تأیید عملیاتی قابلیت‌ها.

## ۴. محدوده

### باید انجام شود

- پایدارسازی Android، Room، Queue و Background Service
- قرارداد ثابت Android و Flask
- تشخیص ایجاد، تغییر و حذف SMS
- Event Outbox و Retry پایدار
- تک‌سیم و دوسیم
- امنیت API، Webhook، Token و Log
- UI فارسی و RTL برای تنظیم و عیب‌یابی
- تست Unit، Integration، UI و دستگاه واقعی
- APK و مستندات نصب و نگهداری

### نباید انجام شود

- وابستگی نسخه نهایی به SMSGate
- تغییر بدون قرارداد هم‌زمان در Backend و Android
- حذف SMS گوشی بدون تصمیم صریح و مجوز لازم
- ارسال واقعی در Test Mode
- ذخیره Secret یا متن کامل پیام در Source و Log
- Firebase، Gemini یا Cloud بدون نیاز قطعی
- Refactor تزئینی قبل از حل هسته Gateway
- AI، گزارش تجاری و مخاطبان پیشرفته قبل از MVP
- انتشار Store قبل از بررسی سیاست مجوز SMS

## ۵. محدودیت‌ها و ریسک‌ها

### Android

- مجوزهای SMS حساس‌اند و برخی عملیات ممکن است Default SMS Role بخواهند.
- Battery Optimization می‌تواند سرویس را متوقف کند.
- Dual SIM و Delivery Report بین گوشی‌ها متفاوت است.
- ContentObserver نوع تغییر را اعلام نمی‌کند؛ Snapshot Comparison لازم است.
- شناسه SMS به‌تنهایی هویت دائمی پیام نیست.

### مخابرات و شبکه

- Delivery Report همیشه ارائه نمی‌شود.
- SMS چندبخشی برای هر Part نتیجه جدا دارد.
- ارسال پرتعداد ممکن است محدود شود.
- گوشی همیشه داخل شبکه محلی نیست.
- Webhook شکست‌خورده باید در Outbox بماند.
- اختلاف ساعت نباید HMAC و Schedule را خراب کند.
- HTTP بدون TLS فقط برای توسعه محلی است.

### مشکلات مشخص نسخه فعلی

- [ ] حذف محدودیت Sync هفت روز اخیر
- [ ] جایگزینی body.hashCode با SHA-256 پایدار
- [ ] جداسازی simSlot از messageType
- [ ] Outbox و Retry برای Webhook
- [ ] خاموش‌کردن BODY Log در Release
- [ ] اصلاح ساعات کاری عبوری از نیمه‌شب
- [ ] سیاست Retention برای Tombstone
- [ ] تکمیل Inbox در UI
- [ ] تغییر Package عمومی به نام نهایی
- [ ] حذف یا توجیه AI/Firebase بلااستفاده
- [ ] Migration امن به‌جای Destructive Migration

## ۶. تصمیم‌های معماری

- Source of Truth: Android SMS Provider
- Local Storage: Room
- UI: Compose + Material 3 + RTL
- Background: Foreground Service و در صورت نیاز WorkManager
- Network: Retrofit/OkHttp روی HTTPS
- Auth: Device Token قابل تعویض و بدون Hardcode
- Webhook: HMAC-SHA256
- Idempotency: Request ID و Event ID پایدار
- Time Storage: UTC Epoch؛ نمایش شمسی در پنل

### تصمیم‌های باز

- [ ] قرارداد API v1
- [ ] نیاز به Default SMS App
- [ ] Retention پیام، Log و Tombstone
- [ ] Minimum Android Version
- [ ] اتصال امن خارج از LAN

## ۷. نقشه‌راه اجرایی

### مرحله ۰ — خط مبنا و Build

- [ ] ثبت نسخه JDK، SDK و Gradle
- [ ] Build نسخه Debug
- [ ] اجرای Unit و Instrumented Test
- [ ] ساخت و نصب APK Debug
- [ ] دسته‌بندی Error و Warning
- [ ] ایجاد README برای Build و Test
- [ ] بررسی Gitignore، Secret و Keystore

معیار پذیرش:

- [ ] Clone تازه طبق README بدون حدس اضافی Build و تست شود.

### مرحله ۱ — قرارداد محصول و API

- [ ] نهایی‌کردن قابلیت‌های MVP
- [ ] مستندسازی Queue، Status، Sync و Health Endpointها
- [ ] تعریف Eventهای Received، Sent، Delivered، Failed و Deleted
- [ ] تعریف Device ID، Message ID، Request ID و Event ID
- [ ] تعریف Timestamp، Timezone و ترتیب Eventها
- [ ] تطبیق HMAC با Backend
- [ ] تعریف Idempotency و Error Response
- [ ] ثبت نمونه JSON

معیار پذیرش:

- [ ] API v1 برای پیاده‌سازی هر دو سمت کافی باشد.

### مرحله ۲ — مدل داده و Migration

- [ ] مدل Message با Direction، Type، Subscription ID و Source ID
- [ ] Fingerprint استاندارد SHA-256
- [ ] Tombstone با Reason، Source، Time و Expiry
- [ ] Event Outbox
- [ ] Send Attempt برای Partها و Retry
- [ ] Room Migration و Migration Test
- [ ] حذف Destructive Migration از Release
- [ ] تعیین Retention

معیار پذیرش:

- [ ] ارتقای DB بدون حذف Queue، Settings و History انجام شود.

### مرحله ۳ — مجوزها

- [ ] صفحه وضعیت Permission
- [ ] درخواست مرحله‌ای SMS، Phone State و Notification
- [ ] مدیریت رد مجوز و Do Not Ask Again
- [ ] تعیین تکلیف Default SMS Role
- [ ] راهنمای Battery Optimization
- [ ] Disabled State برای قابلیت فاقد مجوز

معیار پذیرش:

- [ ] نصب تمیز، رد و اعطای مجدد مجوز بدون Crash باشد.

### مرحله ۴ — موتور خواندن و Sync

- [ ] خواندن جداگانه Inbox و Sent
- [ ] خواندن Subscription ID
- [ ] Full Scan صفحه‌بندی‌شده و خارج UI Thread
- [ ] Incremental Sync با Watermark و Overlap
- [ ] مقاومت در برابر تغییر Timezone
- [ ] Canonicalization پیام Multipart و Unicode
- [ ] مدیریت Cursor و Provider Error

معیار پذیرش:

- [ ] Sync متوالی بدون Duplicate و فقدان پیام باشد.

### مرحله ۵ — حذف واقعی

- [ ] تشخیص حذف Inbox
- [ ] تشخیص حذف Sent
- [ ] تشخیص حذف کامل Conversation
- [ ] آزمایش Trash
- [ ] ثبت Tombstone قبل از حذف Snapshot
- [ ] ارسال Deleted Event از Outbox
- [ ] عدم بازگشت بعد از Restart
- [ ] پذیرش پیام جدید همان شماره
- [ ] تشخیص حذف پیام قدیمی‌تر از هفت روز

معیار پذیرش:

- [ ] سناریوی چهار پیام، حذف گفتگو، پاک‌کردن آرشیو پنل و Sync مجدد بدون بازگشت موفق باشد.

### مرحله ۶ — دریافت

- [ ] Idempotency بین SMS_RECEIVED و Sync
- [ ] تبدیل Multipart به یک پیام منطقی
- [ ] Normalize شماره بدون خراب‌کردن Sender خدماتی
- [ ] ذخیره Received Event در Outbox
- [ ] ارسال بعدی در نبود شبکه
- [ ] آزمایش Reboot و Force Stop

معیار پذیرش:

- [ ] هر SMS واقعی دقیقاً یک بار در پنل ثبت شود.

### مرحله ۷ — ارسال و Delivery

- [ ] ارسال Single و Multipart
- [ ] تجمیع نتیجه Partها
- [ ] State Machine قطعی
- [ ] Retry فقط برای خطای قابل Retry
- [ ] Exponential Backoff و Max Attempt
- [ ] جلوگیری از ارسال دوباره بعد از Crash
- [ ] تضمین عدم ارسال واقعی در Test Mode
- [ ] Normalize شماره ایران، صفر اول و +98

معیار پذیرش:

- [ ] موفق، Airplane Mode، No Service، Invalid Number و Long SMS نتیجه قابل پیش‌بینی داشته باشند.

### مرحله ۸ — چند سیم‌کارت

- [ ] خواندن Active Subscription
- [ ] نمایش Carrier، Slot و Subscription ID
- [ ] Default SIM و انتخاب صریح
- [ ] مدیریت تعویض یا حذف SIM
- [ ] ثبت SIM پیام ورودی
- [ ] تست گوشی دوسیم

معیار پذیرش:

- [ ] ارسال واقعی از هر SIM انتخاب‌شده تأیید شود.

### مرحله ۹ — Queue و محدودیت‌ها

- [ ] Schedule با UTC
- [ ] TTL و Expiry
- [ ] Rate Limit براساس زمان واقعی ارسال
- [ ] Working Hours عادی و عبور از نیمه‌شب
- [ ] Pause، Resume و Cancel
- [ ] Duplicate Request ID بدون ارسال تکراری
- [ ] بازیابی Queue بعد از Reboot

معیار پذیرش:

- [ ] توقف برنامه وسط Queue بدون Duplicate یا فقدان مدیریت شود.

### مرحله ۱۰ — اتصال Flask

- [ ] پیاده‌سازی API v1 یا Adapter
- [ ] Queue Fetch و Acknowledge
- [ ] اعمال Received و Deleted
- [ ] اعمال Sent و Delivered
- [ ] نمایش Health و Last Seen
- [ ] گزارش Version Mismatch
- [ ] حفظ تاریخ UTC در DB و شمسی در UI
- [ ] حفظ Excel و صفر اول شماره

معیار پذیرش:

- [ ] جریان End-to-End دریافت، پاسخ، ارسال، تحویل و حذف موفق باشد.

### مرحله ۱۱ — امنیت

- [ ] حذف Secret از Source و APK
- [ ] Encrypted Storage برای Token
- [ ] HMAC با Timestamp و Replay Protection
- [ ] Constant-Time Signature Compare
- [ ] خاموش‌کردن BODY Log در Release
- [ ] Redact شماره و متن
- [ ] Cleartext فقط در Development
- [ ] محدودکردن Backup داده حساس
- [ ] بازبینی Exported Component
- [ ] Dependency و Static Security Scan

معیار پذیرش:

- [ ] Secret، متن پیام و Endpoint ناامن عملیاتی پیدا نشود.

### مرحله ۱۲ — پایداری Background

- [ ] تطبیق Foreground Service با Android جدید
- [ ] Reboot Receiver در صورت نیاز
- [ ] تصمیم و اجرای WorkManager
- [ ] Backoff شبکه
- [ ] Wake Lock حداقلی
- [ ] نمایش Service State، Last Sync و Last Error
- [ ] تست Doze، Battery Saver و Network Change
- [ ] تست پایداری ۷۲ ساعته

معیار پذیرش:

- [ ] توقف توضیح‌ناپذیر یا مصرف غیرعادی باتری وجود نداشته باشد.

### مرحله ۱۳ — UI فارسی

- [ ] Onboarding و Permission Flow
- [ ] Dashboard وضعیت Gateway، Network، Panel، SIM و Sync
- [ ] Inbox، Sent و Queue با Search و Filter
- [ ] Status Color، Icon و Label
- [ ] Loading، Empty، Error، Disabled و Offline State
- [ ] RTL، فونت، عدد و تاریخ
- [ ] جداسازی Advanced Settings
- [ ] Export امن Diagnostic Log
- [ ] Accessibility و Contrast

معیار پذیرش:

- [ ] کاربر بدون ابزار توسعه بتواند نصب، تنظیم و عیب‌یابی اولیه کند.

### مرحله ۱۴ — آزمون جامع

- [ ] Unit Test برای Fingerprint، Time، Rate Limit و State Machine
- [ ] Room Migration Test
- [ ] Mock Server Test
- [ ] Instrumented Permission و SMS Provider Test
- [ ] RTL UI و Screenshot Test
- [ ] تست شماره ایران، Unicode و Multipart
- [ ] تست Delete، Duplicate، Retry و Reboot
- [ ] تست تک‌سیم و دوسیم واقعی
- [ ] Flask Regression Test

معیار پذیرش:

- [ ] تست‌های اجباری سبز و ریسک باقی‌مانده مستند باشد.

### مرحله ۱۵ — Release و Pilot

- [ ] Application ID و نام نهایی
- [ ] Versioning و Changelog
- [ ] Keystore امن خارج Git
- [ ] APK Release امضاشده
- [ ] راهنمای Install، Permission، Battery و Rollback
- [ ] Backup و پاک‌سازی امن
- [ ] Pilot تک‌سیم
- [ ] Pilot دوسیم
- [ ] رفع مشکل Pilot و Regression مجدد

معیار پذیرش:

- [ ] Release برای بهره‌برداری محدود تأیید شود.

### مرحله ۱۶ — تحویل و نگهداری

- [ ] مستند Architecture و Data Flow
- [ ] مستند API و Webhook
- [ ] Runbook خطاها
- [ ] سیاست Backup، Retention و Delete
- [ ] برنامه Update SDK و Dependency
- [ ] Health و Monitoring
- [ ] Backlog بعد از MVP

معیار پذیرش:

- [ ] فرد دیگری بتواند Build، نصب، پشتیبانی و Upgrade کند.

## ۸. ترتیب قطعی اجرا

1. مرحله ۰: Build و Baseline
2. مرحله ۱: API Contract
3. مرحله ۲: Data Model و Migration
4. مرحله ۳: Permission
5. مرحله ۴ و ۵: Sync و حذف واقعی
6. مرحله ۶ تا ۹: Receive، Send، SIM و Queue
7. مرحله ۱۰: Flask Integration
8. مرحله ۱۱ و ۱۲: Security و Reliability
9. مرحله ۱۳: UI/UX
10. مرحله ۱۴: QA
11. مرحله ۱۵ و ۱۶: Release و Handover

تا پایان مرحله ۵ اولویت اصلی اثبات حل «بازگشت پیام حذف‌شده» است.

## ۹. MVP سریع

### داخل MVP

- [ ] Build تکرارپذیر
- [ ] Permission صحیح
- [ ] خواندن Inbox و Sent
- [ ] حذف واقعی و Tombstone پایدار
- [ ] دریافت بدون Duplicate
- [ ] ارسال Single و Multipart
- [ ] انتخاب SIM
- [ ] Queue و Retry پایه
- [ ] اتصال امن پنل
- [ ] Dashboard و Settings ضروری
- [ ] تست واقعی و APK آزمایشی

### خارج MVP

- Store عمومی
- گزارش تجاری پیشرفته
- مخاطبان کامل
- AI و Auto Reply
- تعداد زیاد Device
- پنل سازمانی چندکاربره
- ظاهر بسیار پیشرفته قبل از تثبیت هسته

## ۱۰. قالب گزارش پیشرفت

- تاریخ
- مرحله و عنوان
- هدف
- فایل‌های تغییرکرده
- آزمون‌های انجام‌شده
- نتیجه
- مشکلات باقی‌مانده
- تصمیم معماری
- وضعیت: انجام شد یا انجام نشد
- گام بعدی

## ۱۱. گزارش پیشرفت

### رکورد ۰۰۱ — ایجاد دفتر کار

- تاریخ: 2026-09-01
- مرحله: برنامه‌ریزی کل پروژه
- هدف: نقشه‌راه واحد و جلوگیری از کار پراکنده
- فایل: PROJECT_ROADMAP.md
- نتیجه: هدف، محدودیت، محدوده، مراحل و معیار پذیرش ثبت شد.
- مشکلات باقی‌مانده: مراحل اجرایی ۰ تا ۱۶
- وضعیت: انجام شد
- گام بعدی: مرحله ۰

## ۱۲. گام فعال

مرحله ۰ — تثبیت Baseline و Build

- [ ] بررسی Java، Gradle و Android SDK
- [ ] اولین Build بدون تغییر منطق
- [ ] اجرای Testهای فعلی
- [ ] ثبت خطاها و تبدیل آن‌ها به چک‌لیست اصلاح

### رکورد ۰۰۲ — بررسی محیط Build

- تاریخ: 2026-09-01
- مرحله: ۰ — تثبیت Baseline و Build
- نتیجه: Java 21 نصب است؛ Clone فعلی Gradle Wrapper ندارد و Gradle سراسری نیز در محیط پیدا نشد.
- وضعیت: Build محلی فعلاً انجام نشد.
- نوع مانع: محیطی؛ نصب Android Studio یا Android SDK/Gradle کامل در این سیستم در دسترس نیست.
- تصمیم: Build اولیه در Google AI Studio انجام شود؛ بعداً برای Build تکرارپذیر، Gradle Wrapper یا GitHub Actions بررسی شود.
- فایل‌های منطق برنامه: بدون تغییر.
- گام بعدی: واردکردن پروژه در Google AI Studio و اجرای Build/Preview.

## ۱۳. تصمیم محیط توسعه و توقف موقت

تصمیم قطعی ثبت‌شده:

- [x] GitHub مرجع اصلی کد و نسخه‌ها باشد.
- [x] توسعه با Worktree یا Branchهای جدا انجام شود.
- [x] ایجنت‌ها از روند plan -> test -> implement -> review -> verify -> document پیروی کنند.
- [x] Google AI Studio برای Build، Preview و نصب آزمایشی استفاده شود.
- [x] تست نهایی روی گوشی واقعی انجام شود.
- [x] Android Studio به‌دلیل نبود فضای کافی اجباری نباشد.
- [x] سایت‌های ناشناس ساخت APK استفاده نشوند.
- [ ] در صورت نیاز، GitHub Actions برای Build تکرارپذیر APK راه‌اندازی شود.

### برنامه آماده برای شروع دور بعد

1. بررسی وضعیت Clone و پاک‌بودن تغییرات ناخواسته
2. اجرای Build اولیه در Google AI Studio
3. ثبت خطاهای Build در همین دفتر
4. اصلاح فقط خطاهای Build، بدون تغییر قابلیت‌ها
5. ساخت APK آزمایشی و نصب روی گوشی
6. آزمایش مجوزها، دریافت SMS و حذف پیام
7. فعال‌کردن کار ایجنت‌ها در Worktreeهای جدا
8. شروع مرحله ۲ و ۳ پس از موفقیت Baseline

### نقطه توقف فعلی

- پروژه برای شروع مرحله ۰ آماده است.
- هیچ ایجنت پیاده‌سازی فعالی در حال اجرا نیست.
- هیچ تغییر Backend، API، Database یا Gateway در این توقف انجام نشده است.
- ادامه کار باید از «Build اولیه در Google AI Studio» شروع شود.

## ۱۳. استفاده از ECC

وضعیت: راهنمای پروژه‌ای اضافه شد؛ نصب ECC در سطح APK انجام نمی‌شود.

- [x] روند plan -> test -> implement -> review -> verify -> document ثبت شد.
- [x] نقش‌های تخصصی Android، Sync، Sending، Integration، Security، QA و Release تعریف شدند.
- [x] قانون Worktree یا Branch جدا برای جلوگیری از تداخل ایجنت‌ها ثبت شد.
- [x] قانون عدم تغییر بدون مجوز در Backend، API، Database و Gateway ثبت شد.
- [x] الزام ثبت نتیجه در همین دفتر کار ثبت شد.
- [ ] در صورت نیاز، نصب Native ECC برای Codex به‌صورت جداگانه و فقط یک‌بار انجام شود.

تصمیم: ECC ابزار مدیریت توسعه است، نه Dependency برنامه. APK نباید برای اجرا به ECC وابسته باشد.

### رکورد ۰۰۳ — اصلاح همگام‌سازی حذف P0

- تاریخ: 2026-09-02
- مرحله: ۴ و ۵ — Sync و حذف واقعی
- هدف: جلوگیری از resurrection، تشخیص حذف پیام‌های قدیمی و حذف کامل مکالمه بدون حذف SMS گوشی
- فایل‌های تغییرکرده: `app/src/main/java/com/example/data/repository/SmsRepository.kt`، `app/src/main/java/com/example/data/repository/SmsSyncPlanner.kt`، `app/src/main/java/com/example/data/local/AppDatabase.kt`، `app/src/test/java/com/example/data/repository/SmsSyncPlannerTest.kt`
- نتیجه: query به snapshot کامل تغییر کرد؛ cursor ناقص/نامعتبر هیچ حذف یا درج جدیدی اعمال نمی‌کند؛ tombstone پیش از درج بررسی می‌شود؛ حذف فقط از cache محلی انجام می‌شود.
- آزمون‌های اضافه‌شده: رفت‌وبرگشت چهار پیام، بازگشت با شناسه Provider جدید، حذف کامل مکالمه شامل پیام قدیمی، و snapshot ناقص.
- آزمون‌های اجراشده: `git diff --check` موفق؛ Gradle test/build اجرا نشد چون clone فاقد `gradlew` و محیط فاقد Gradle سراسری است.
- مشکلات باقی‌مانده: اجرای build/test باید در محیط دارای Android SDK و Gradle انجام شود؛ تست گوشی واقعی هنوز انجام نشده است.
- تصمیم معماری: تشخیص حذف فقط بر اساس snapshot کامل Provider مجاز است؛ API و Backend بدون تغییر باقی ماندند؛ fallback مهاجرت مخرب حذف شد.
- وضعیت: پیاده‌سازی انجام شد؛ تأیید build/test محیطی باقی‌مانده است.
- گام بعدی: اجرای `:app:testDebugUnitTest` و build در محیط CI یا Android Studio، سپس تست Provider روی گوشی واقعی.
### رکورد ۰۰۳ — UI یکپارچه نسخه اولیه و navigation

- تاریخ: 2026-09-02
- مرحله: ۱۳ — UI فارسی
- هدف: navigation یکپارچه Compose برای داشبورد، Inbox، Sent، Queue، Contacts، Gateway و Settings با حفظ Send و Logs.
- فایل‌های تغییرکرده: app/src/main/java/com/example/MainActivity.kt
- نتیجه: drawer navigation، RTL فارسی، وضعیت اتصال Gateway، حالت تست، مجوز، loading در همگام‌سازی، empty/disabled/error، جست‌وجو و فیلتر صف، status label/color و Settings wiring اضافه شد.
- محدودیت صریح: SyncedSms در قرارداد فعلی جهت پیام را ندارد؛ Inbox/Sent موقتاً بر اساس convention فعلی type=1/2 (در فیلد simSlot) نمایش داده می‌شوند. Contacts به‌دلیل نبود data source فقط TODO شفاف دارد.
- تست: :app:testDebugUnitTest اجرا شد اما baseline پیش از compile به‌دلیل resolve نشدن com.android.application:9.1.1 متوقف شد.
- وضعیت: UI انجام شد؛ Build و smoke test به‌دلیل blocker محیط/Dependency تأیید نشد.
- گام بعدی: رفع نسخه یا دسترسی Android Gradle Plugin، سپس اجرای build و screenshot smoke روی شاخه UI.


### رکورد ۰۰۴ — ادغام MVP P0 و کنترل انتشار

- تاریخ: 2026-09-02
- شاخه: `codex/mvp-integration`، پایه: `codex/initial-roadmap` در commit `0c9b90366b2118238ea4e936ce88fb5b398c9038`
- منابع ادغام‌شده: sync در `c3cbff9d35872f6b8a016195342a71138e13fc14` و UI در `b8b6ee2870b889ece0fc3db13428992685aadcc1`.
- نتیجه: تغییرات snapshot کامل، tombstone، حذف فقط از cache محلی، تست planner و navigation فارسی Compose با حفظ هر دو رکورد قبلی ادغام شد.
- حل تعارض: فقط `PROJECT_ROADMAP.md` تعارض متنی داشت؛ رکوردهای sync و UI هر دو حفظ شدند. هیچ endpoint، API، SMS Provider، service، receiver یا منطق کسب‌وکار حذف نشد.
- اصلاحات integrator: چهار interpolation ناقص در `MainActivity.kt` اصلاح شد؛ reference تعریف‌نشدهٔ `tombstones` در تست planner به tombstone مورد انتظار سناریو اصلاح شد.
- بررسی‌ها: `git diff --check` و اسکن markerهای merge موفق است؛ گزارش امنیتیِ بدون فایل یا Diff وارد کد نشد.
- Build/test: اجرا نشد؛ clone فاقد `gradlew.bat` است و Gradle سراسری نیز نصب نیست. Java 21 و `ANDROID_SDK=C:\Android\sdk` موجود است، اما بدون Gradle امکان resolve dependency یا اجرای `:app:compileDebugKotlin` و `:app:testDebugUnitTest` وجود ندارد.
- گام بعدی انتشار: اجرای build و unit test در CI یا Android Studio دارای Gradle/Android SDK، سپس push همین شاخه و تست Provider روی گوشی واقعی.

### رکورد ۰۰۵ — اصلاحات امنیتی P0 و CI انتشار

- تاریخ: 2026-09-02
- شاخه: `codex/mvp-integration`، ادامه از commit `45134c7596e7555a7d2619028458c2c94d626bdb`.
- لاگ امن: سطح HTTP logging از `BODY` به `NONE` تغییر کرد؛ متن پیام، شماره، آدرس و stack trace از لاگ‌های پایدار/Logcat حذف شد.
- foreground service: permission `FOREGROUND_SERVICE_DATA_SYNC`، attribute `foregroundServiceType="dataSync"` و typed `ServiceCompat.startForeground` اضافه شد.
- backup امن: `allowBackup=false` شد و دیتابیس Room در cloud backup و device transfer با rules صریحاً مستثنا شد.
- محدوده حفظ شد: endpointها، API contract، SMS Provider، entity/schema/version دیتابیس و منطق کسب‌وکار تغییر نکردند.
- CI: فایل `.github/workflows/android-ci.yml` اضافه شد؛ Gradle `9.3.1`، Java `17`، Android SDK `36.1` و Build Tools `36.0.0` را آماده و `:app:testDebugUnitTest` و `:app:assembleDebug` را اجرا می‌کند.
- بررسی‌ها: XML structural validation، scan لاگ حساس، scan حذف `BODY`/`printStackTrace` و `git diff --check` موفق شدند.
- Build محلی: قابل اجرا نبود؛ `gradlew.bat` و Gradle سراسری در محیط موجود نیستند. نتیجهٔ واقعی build باید از GitHub Actions/Android Studio ثبت شود.
- بازبینی امنیتی مستقل: سه blocker P0 شناسایی شد و هر سه در working tree اصلاح شدند؛ هیچ فایل امنیتی حدسی یا گزارشِ بدون Diff وارد نشد.

### رکورد ۰۰۶ — هماهنگ‌سازی تست Robolectric

- تاریخ: 2026-09-02
- فایل تغییرکرده: `app/src/test/java/com/example/ExampleRobolectricTest.kt`.
- نتیجه: assertion نام نمونهٔ `My Application` به نام واقعی `SMS Center` تغییر کرد؛ تست حذف یا غیرفعال نشد.
- manifest: `FOREGROUND_SERVICE_DATA_SYNC` و `foregroundServiceType="dataSync"` از commit امنیتی قبلی موجود و تأیید شدند؛ تغییر منطق SMS/API انجام نشد.
- بررسی ساختاری: ۶ تست با annotation `@Test` شناسایی شد؛ assertion نام برنامه و تنظیمات foreground معتبر هستند؛ `git diff --check` موفق است.
- Build/test: `:app:testDebugUnitTest` و `:app:assembleDebug` قابل اجرا نبودند چون `gradlew.bat` و Gradle سراسری در محیط وجود ندارند. workflow CI کم‌ریسک در commit قبلی اضافه شده است.
- انتشار: commit بعدی محلی ثبت می‌شود اما تا سبزشدن هر دو task، push جدید انجام نمی‌شود.
### رکورد ۰۰۷ — MVP یکپارچه، Build و Verification

- تاریخ: 2026-09-02
- شاخه: `codex/mvp-integration`؛ پایهٔ public branch در commit `ca1c463d434f3cc3e89f18484159dd0737e3b424`.
- Android: خواندن مستقیم Inbox/Sent، تفکیک direction، SIM از subscription، fingerprint SHA-256، tombstone، queue، ارسال single/multipart، delivery callback، LAN health و webhook HMAC پیاده‌سازی شد.
- UI: داشبورد فارسی RTL، navigation stack و Back، Inbox/Sent/Conversation، Send، Queue/Logs، Contacts محلی و Call Log واقعی با search/filter و ACTION_DIAL اضافه شد.
- Call Log: Room migration نسخهٔ ۲، outbox/retry، sync batch idempotent و API فیلترشونده در `backend/app.py` اضافه شد؛ نام مخاطب فقط local است و upload نمی‌شود.
- Build: `:app:compileDebugKotlin` و `:app:assembleDebug` با Gradle 9.3.1، JDK 17 و SDK 36 موفق شدند؛ debug از signing پیش‌فرض Android استفاده می‌کند.
- Android tests: `:app:testDebugUnitTest` موفق، 17 تست.
- Backend tests: `python -m pytest backend/tests -q` موفق، 5 تست.
- تست‌های نمایشی Greeting و فایل screenshot حذف شدند؛ هیچ تستی برای پنهان‌کردن failure غیرفعال نشد.
- محدودیت‌های تأییدنشده: گوشی واقعی، Default SMS Role، Dual-SIM واقعی، Delivery واقعی، Doze/Battery و اجرای ۷۲ ساعته.
- وضعیت: MVP نرم‌افزاری انجام شد؛ آمادهٔ نصب آزمایشی، نه تأیید production.
- گام بعدی: نصب APK و اجرای سناریوهای Permission، دریافت/ارسال، حذف/Restart، Dual-SIM و Call Log روی دستگاه واقعی.

### رکورد ۰۰۸ — معماری Phone-as-Server

- تاریخ: 2026-09-02
- شاخه: `codex/phone-server-architecture`، پایه از آخرین `main` با commit `84dee27`.
- هدف: قرارگرفتن HTTP Server واقعی داخل Android و اتصال کلاینت وب/رایانه به IP گوشی، بدون حذف backend Flask.
- Android: `PhoneHttpServer` داخل `SmsGatewayService` با bind روی `0.0.0.0`، پورت قابل تنظیم، foreground lifecycle، restart/stop، خطای پورت، کشف IPهای LAN و API key تصادفی constant-time اضافه شد.
- API گوشی: `/api/health`، `/api/status`، `/api/sms/read`، `/api/sms/send`، `/api/sms/sync`، `/api/call-logs` و `/api/call-logs/sync`؛ ثبت SMS از API با `requestId` idempotent به صف فعلی وصل است و محدودیت‌های test mode، working hours، rate limit و permission حفظ شده‌اند.
- داده و UI: migration Room نسخهٔ ۳ برای `phoneServerPort` و `phoneServerApiKey`، نمایش endpoint در وضعیت Gateway، و تنظیمات کلید/پورت اضافه شد. `serverUrl` و polling Flask حذف نشدند و فقط optional legacy هستند.
- مستندات: `docs/PHONE_SERVER_API.md` اضافه شد؛ هیچ secret یا متن پیام در source/log ثبت نشده است.
- تست اضافه‌شده: `PhoneServerSecurityTest` برای Bearer/X-API-Key، رد credential نادرست و تولید کلید.
- `git diff --check`: موفق.
- Build/test واقعی: `testDebugUnitTest` و `assembleDebug` ابتدا با cache/offline و سپس هر دو با دسترسی آنلاین و `--refresh-dependencies` قبل از compile به‌علت resolve نشدن `com.android.tools.build:gradle:8.13.0` از Google/Maven/Plugin Portal متوقف شدند؛ هیچ خطای واقعی Kotlin از مرحلهٔ compile حاصل نشد و هیچ build سبزی ادعا نمی‌شود.
- محدودیت باقی‌مانده: تست listener روی دستگاه واقعی و تست build پس از فراهم‌شدن artifactهای AGP باید انجام شود. Push انجام نشده است.

### رکورد ۰۰۹ — اصلاحات QA برای lifecycle، concurrency و امنیت شبکه

- تاریخ: 2026-09-02
- شاخه: `codex/phone-server-architecture`، ادامهٔ commit `6d4ea261`.
- lifecycle: در Android 15+، boot receiver به‌جای start مستقیم data-sync FGS، JobScheduler persisted را فعال می‌کند؛ JobService تلاش کنترل‌شده برای startForegroundService انجام می‌دهد و در صورت رد OS، sync محدود و امن را ادامه می‌دهد. در نسخه‌های قبل، FGS مستقیماً start می‌شود.
- صف: `requestDedupeKey` nullable با migration non-destructive، get-or-insert تراکنشی، Mutex در Repository و `claimPending` اتمیک اضافه شد؛ duplicateهای قدیمی حذف نمی‌شوند و dispatch هم‌زمان یک SMS دوباره ارسال نمی‌کند.
- امنیت HTTP: Bearer scheme دقیق، CORS با origin دقیق و Allow-Methods صریح، LAN-only پیش‌فرض، هشدار HTTP، و نگهداری API key با AES/GCM در Android Keystore اضافه شد.
- شبکه و API: IPهای Wi-Fi/Ethernet اولویت دارند؛ onLost وقتی listener زنده است Offline جعلی نمی‌سازد؛ `POST /api/call-logs/sync` provider و outbox محلی را واقعاً refresh می‌کند.
- تست‌های اضافه‌شده: policy lifecycle Android 15، CORS/scheme و تست concurrent Room برای get-or-insert و claim.
- نتایج: `git diff --check` موفق و `python -m pytest backend/tests -q` برابر `5 passed`؛ `assembleDebug` و `testDebugUnitTest` آنلاین با `--refresh-dependencies` قبل از compile به‌علت resolve نشدن `com.android.tools.build:gradle:8.13.0` متوقف شدند. هیچ build سبزی ادعا نمی‌شود.

### رکورد ۰۱۰ — اصلاح صحت status API

- تاریخ: 2026-09-02
- `GET /api/status` اکنون `running`، `port`، `addresses` و `error` را مستقیماً از `PhoneServerStatusStore` می‌خواند؛ در حالت starting/stopped/failed دیگر وضعیت فعال جعلی گزارش نمی‌شود.
- تست `PhoneServerStatusTest` انتقال وضعیت‌های starting، running، failed و stopped را پوشش می‌دهد.
