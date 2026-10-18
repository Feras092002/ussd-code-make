# الجسر بين الصفحة والتطبيق يُستدعى من JavaScript بالاسم، فلا يجوز تغييره
-keepclassmembers class com.ferasfarra.ussdmaker.MainActivity$AndroidBridge {
    @android.webkit.JavascriptInterface <methods>;
}
