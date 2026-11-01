# اصلاح خطای کامپایل Kotlin در V4.2

مسیر فایل اصلاح‌شده:
`app/src/main/java/ir/smartsmscrm/v4/ui/ToolsPage.kt`

علت خطای نحوی: در خط کادر رمز پشتیبان، فراخوانی `OutlinedTextField` یک پرانتز بسته کم داشت.

**قبل (اشتباه):**
```kotlin
visualTransformation=PasswordVisualTransformation()
```

**بعد (درست):**
```kotlin
visualTransformation=PasswordVisualTransformation())
```

در نسخه اصلاحی این پرانتز افزوده شده و پروژه کامل همراه پوشه `.github` موجود است.

نصب برنامه قبلی را حذف نکنید. اولین اجرا را پس از موفق‌شدن GitHub Actions تست کنید.

**محدودیت بررسی:** به دلیل نبود Android SDK/Gradle در این محیط، ساخت کامل APK انجام نشده؛ بررسی نحوی Kotlin بدون کتابخانه‌های اندروید انجام شده است.
