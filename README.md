# Smart SMS CRM V4.5 — بازطراحی کاربردی UI/UX

پروژه اندروید Kotlin / Jetpack Compose برای Samsung Galaxy S25. V4.5 بر پایه V4.4 است؛ دو فایل حساس `SendService.kt` و `Store.kt` بدون تغییر حفظ شده‌اند.

**بخوانید:** `V4_5_CHANGES_FA.md`، `START_HERE_V4_5_FA.md` و `TEST_V4_5_FA.md`.

## ساخت

GitHub Actions از `.github/workflows/android-apk.yml` استفاده می‌کند. اجرای آزمایش `:app:testDebugUnitTest` و ساخت `:app:assembleDebug` فقط پس از دانلود و آپلود در GitHub تأیید می‌شوند.

شناسه نصب: `ir.smartsmscrm.campaignv45`؛ از شناسه V4.4 مستقل است. هیچ ارسال خودکاری هنگام بازکردن برنامه شروع نمی‌شود.
