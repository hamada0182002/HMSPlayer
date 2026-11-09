# HMS Player

تطبيق IPTV (Xtream Codes) للموبايل وشاشات Android TV.

- Kotlin + Jetpack Compose + Media3 (ExoPlayer)
- أكتر من حساب Xtream (إضافة / تعديل / حذف / تبديل)
- تحكم باللمس أو بالريموت (أسهم + OK + أزرار الميديا) — من غير أرقام
- الموبايل بيفتح بالعرض دايماً
- مشغل: إعادة اتصال تلقائي، استكمال من آخر نقطة، الحلقة/القناة التالية، اختيار صوت/ترجمة، سرعة، حجم الصورة
- أنيميشن تنقل + ظهور متدرّج + تركيز متحرك للريموت

## أول مرة (مرة واحدة)
1. ارفع المشروع على GitHub في Repository **Public**.
2. افتح `app/src/main/java/com/hms/player/Updater.kt` وغيّر `GITHUB_REPO = "OWNER/REPO"` لاسم حسابك/اسم المستودع (مثال `ahmed/hms-player`).

## نشر إصدار / تحديث (يوصل لكل الناس)
```
git tag v1.0.0
git push --tags
```
GitHub Actions بيبني الـ APK وينشره في Releases. الرقم بعد `v` هو رقم الإصدار (v1.1.0 ← 1.1.0).
أي حد فاتح التطبيق بيظهرله تنبيه تحديث تلقائي، أو من **الإعدادات ← التحقق من التحديث**.
من غير Git: Actions ← Run workflow بيطلّع APK تجريبي بس (مش بيوصل للناس).

## بناء بـ Android Studio
افتح المجلد وسيب Gradle يخلّص Sync ← Build > Build APK(s).

## مفتاح التوقيع (سري — مش جوه المشروع)
المفتاح بيتخزن في **GitHub Secrets** مش في الكود. في المستودع: Settings ← Secrets and variables ← Actions ← New repository secret، وضيف 4 أسرار بالأسماء دي والقيم من ملف `GITHUB_SECRETS.txt`:
`KEYSTORE_B64` و `KEYSTORE_PASSWORD` و `KEY_ALIAS` و `KEY_PASSWORD`.
- **كل التحديثات لازم تتوقّع بنفس المفتاح** وإلا أندرويد يرفض التثبيت. احتفظ بنسخة من `hms-release.jks` في مكان آمن ومترفعوش على GitHub.

## تغيير اللوجو
`python3 design/make_logo.py`
