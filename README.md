# صانع كود USSD — نسخة Android

مشروع Android Studio كامل يغلّف صفحة `index.html` داخل تطبيق مستقل يعمل بدون إنترنت.
الصفحة نفسها موجودة في `app/src/main/assets/index.html`؛ أي تعديل لاحق على نسخة الويب
انسخه إلى هذا المسار ثم أعد البناء.

## الطريقة 1: بدون تثبيت أي شيء (GitHub Actions)

1. أنشئ مستودعاً جديداً على GitHub وارفع محتويات هذا المجلد إليه (كل الملفات بما فيها `.github`).
2. افتح تبويب **Actions** في المستودع؛ سيبدأ البناء تلقائياً ويستغرق 3–5 دقائق.
3. من صفحة التشغيل الناجح، حمّل الملف `ussd-code-maker-debug-apk` (ملف zip بداخله `app-debug.apk`).
4. انقل الـ APK إلى الهاتف وثبّته (فعّل "تثبيت من مصادر غير معروفة" إن طُلب).

## الطريقة 2: Android Studio

1. افتح المجلد في Android Studio (Hedgehog أو أحدث) واتركه يزامن Gradle. إن سألك عن Gradle wrapper اختر
   إنشاءه تلقائياً (المشروع يحتاج Gradle 8.11 أو أحدث).
2. من القائمة: **Build ▸ Build Bundle(s) / APK(s) ▸ Build APK(s)**.
3. الملف الناتج في `app/build/outputs/apk/debug/app-debug.apk`.

## الطريقة 3: سطر الأوامر (مع Android SDK وGradle 8.11+ مثبّتين)

```bash
gradle assembleDebug
```

## نسخة موقّعة للنشر (اختياري)

لرفع التطبيق على Google Play أو توزيعه كإصدار رسمي:

```bash
keytool -genkeypair -v -keystore release.jks -alias ussd -keyalg RSA -keysize 2048 -validity 10000
```

ثم في `app/build.gradle.kts` أضف داخل `android { }`:

```kotlin
signingConfigs {
    create("release") {
        storeFile = file("../release.jks")
        storePassword = "كلمة_سر_المخزن"
        keyAlias = "ussd"
        keyPassword = "كلمة_سر_المفتاح"
    }
}
```

وداخل `buildTypes { release { ... } }` أضف `signingConfig = signingConfigs.getByName("release")`،
ثم `gradle assembleRelease`.

## ملاحظات

- الخط: ضع `Tajawal-Regular.ttf` في `app/src/main/assets/` ليُستخدم نفس خط نسخة الويب؛ بدونه يُستخدم خط النظام.
- لا يطلب التطبيق أي صلاحية. فتح لوحة الاتصال بالكود يتم عبر `ACTION_DIAL` والمستخدم يضغط "اتصال" بنفسه.
- كلمة مرور جوال باي لا تُحفظ في أي مكان، كما في نسخة الويب.
- لرفع رقم الإصدار: عدّل `versionCode` و`versionName` في `app/build.gradle.kts`.
