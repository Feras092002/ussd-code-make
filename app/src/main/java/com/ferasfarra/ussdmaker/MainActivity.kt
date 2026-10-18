package com.ferasfarra.ussdmaker

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import java.io.File

/**
 * غلاف Android لصفحة "صانع كود USSD".
 *
 * - الصفحة مضمّنة داخل التطبيق (assets/index.html) وتعمل بدون إنترنت.
 * - تُقدَّم عبر WebViewAssetLoader على https://appassets.androidplatform.net
 *   حتى تعمل واجهات المتصفح التي تتطلب سياقاً آمناً (الحافظة، localStorage... إلخ).
 * - روابط tel: تُحوَّل إلى لوحة الاتصال (ACTION_DIAL) مع الكود جاهزاً، تماماً كما في المتصفح.
 * - اختيار ملف (استيراد النسخة الاحتياطية) وحفظ ملف (تصدير كبير) مدعومان.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null

    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingFileCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        pendingFileCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            textZoom = 100
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
        }

        webView.addJavascriptInterface(AndroidBridge(), "AndroidApp")

        webView.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                return when (url.scheme?.lowercase()) {
                    "tel" -> {
                        openDialer(url)
                        true
                    }
                    "http", "https" -> {
                        if (url.host == APP_HOST) {
                            false
                        } else {
                            openExternally(Intent(Intent.ACTION_VIEW, url))
                            true
                        }
                    }
                    "mailto", "sms", "whatsapp" -> {
                        openExternally(Intent(Intent.ACTION_VIEW, url))
                        true
                    }
                    "intent" -> {
                        runCatching {
                            openExternally(Intent.parseUri(url.toString(), Intent.URI_INTENT_SCHEME))
                        }
                        true
                    }
                    else -> true
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                pendingFileCallback?.onReceiveValue(null)
                pendingFileCallback = filePathCallback
                return try {
                    filePicker.launch("*/*")
                    true
                } catch (e: ActivityNotFoundException) {
                    pendingFileCallback = null
                    false
                }
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) webView.goBack() else finish()
        }

        if (savedInstanceState == null) {
            webView.loadUrl(START_URL)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    /** يفتح لوحة الاتصال والكود مكتوب فيها؛ المستخدم يضغط "اتصال" بنفسه كما في نسخة الويب. */
    private fun openDialer(telUri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_DIAL, telUri))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_dialer, Toast.LENGTH_LONG).show()
        }
    }

    private fun openExternally(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // لا يوجد تطبيق مناسب، نتجاهل بهدوء
        }
    }

    /** دوال يستدعيها JavaScript من الصفحة عبر window.AndroidApp */
    inner class AndroidBridge {

        @JavascriptInterface
        fun isApp(): Boolean = true

        @JavascriptInterface
        fun versionName(): String =
            runCatching { packageManager.getPackageInfo(packageName, 0).versionName ?: "" }.getOrDefault("")

        /** يحفظ ملف نصي (النسخة الاحتياطية الكبيرة) في مجلد التنزيلات ويعيد true عند النجاح. */
        @JavascriptInterface
        fun saveTextFile(fileName: String, content: String): Boolean {
            val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "ussd-history.backup" }
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                        put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: return false
                    contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                } else {
                    @Suppress("DEPRECATION")
                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    dir.mkdirs()
                    File(dir, safeName).writeText(content, Charsets.UTF_8)
                }
                runOnUiThread { Toast.makeText(this@MainActivity, R.string.file_saved, Toast.LENGTH_SHORT).show() }
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    companion object {
        private const val APP_HOST = "appassets.androidplatform.net"
        private const val START_URL = "https://$APP_HOST/assets/index.html"
    }
}
