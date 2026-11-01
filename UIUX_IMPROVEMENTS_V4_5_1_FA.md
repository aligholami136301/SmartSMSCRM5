# بهبودهای UI/UX اعمال‌شده (V4.5.1 UI)

تغییرات فقط روی لایه رابط کاربری اعمال شده و منطق ارسال (`SendService`) و دیتابیس (`Store`) دست نخورده باقی مانده است.

## کامپوننت‌های مشترک (Common.kt)
- `StatusPillWithIcon` با آیکون وضعیت
- `LiveBadge` برای نمایش ارسال زنده
- `ProgressSection` با درصد و نوار پیشرفت بهتر
- `EmptyView` غنی‌شده با آیکون و دکمه اقدام
- `FilterChipRow` برای فیلترهای افقی
- `ExpandableSection` با انیمیشن
- توابع `stateColor` و `stateIcon` برای رنگ و آیکون یکدست وضعیت‌ها

## صفحه خانه (HomePage)
- کارت کمپین فعال برجسته‌تر با LiveBadge
- ProgressSection واضح با درصد
- دکمه اصلی بزرگ‌تر («مشاهده زنده» / «بازکردن و ادامه»)
- نمایش سایر کمپین‌های فعال
- Empty State بهتر وقتی کمپین فعالی نیست
- بخش خلاصه امروز و دسترسی سریع

## صفحه کمپین‌ها (CampaignsPage)
- کارت‌های لیست با ProgressSection و StatusPillWithIcon
- Empty State با دکمه «ساخت کمپین جدید»
- هدر جزئیات کمپین با LiveBadge هنگام ارسال
- ProgressSection یکدست در جزئیات
- Empty State بهتر برای کمپین یافت‌نشده

## صفحه مخاطبین (ListsPage)
- Empty Stateهای بهتر با آیکون و در بخش همه مخاطبین دکمه ورود از گوشی

## صفحه قالب‌ها (TemplatesPage)
- Empty State راهنما با دکمه ساخت
- مرتب‌سازی واضح‌تر
- پیش‌نمایش دو خطی و منوی عملیات

## صفحه گزارش‌ها (ReportsPage)
- فیلتر وضعیت با Chip (همه / فعال / تکمیل‌شده / سایر)
- کارت‌های گزارش با ProgressSection و StatusPillWithIcon
- Empty State بهتر

## نکات
- رنگ‌بندی و RTL حفظ شده است.
- هیچ ارسال خودکاری اضافه نشده.
- برای تست نهایی، پروژه را در GitHub Actions بسازید.
