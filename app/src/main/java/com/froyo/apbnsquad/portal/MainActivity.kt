/*
 * Project Froyo — APBn Squad Message Portal
 * Created & Maintained by Fahad Al-Belal
 * Portfolio: https://fahadnway.qd.je
 */

package com.froyo.apbnsquad.portal

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.froyo.apbnsquad.portal.service.NotificationHelper
import com.froyo.apbnsquad.portal.ui.theme.MyApplicationTheme
import com.froyo.apbnsquad.portal.ui.theme.PortalBackground
import com.froyo.apbnsquad.portal.ui.theme.PortalBlue

/**
 * Main Activity for Project Froyo APBn Squad Message Portal.
 * Manages full-screen portal browsing, JavaScript bridge, and native notification routing.
 */
class MainActivity : ComponentActivity() {

    companion object {
        const val PORTAL_URL = "https://apbnsquad.qd.je"
        const val OFFLINE_URL = "file:///android_asset/offline.html"
        const val EXTRA_TARGET_URL = "extra_target_url"
        private const val TAG = "FroyoPortalActivity"
    }

    private var webView: WebView? = null
    private var pendingWebPermissionRequest: PermissionRequest? = null

    // Permission launcher for Android 13+ POST_NOTIFICATIONS
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                try {
                    pendingWebPermissionRequest?.grant(pendingWebPermissionRequest?.resources)
                } catch (e: Exception) {
                    Log.e(TAG, "Error granting web permission", e)
                }
                pendingWebPermissionRequest = null
            } else {
                try {
                    pendingWebPermissionRequest?.deny()
                } catch (e: Exception) {
                    Log.e(TAG, "Error denying web permission", e)
                }
                pendingWebPermissionRequest = null
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show system status bar (time, battery, system notification icons)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        try {
            requestNotificationPermissionIfNeeded()
        } catch (_: Exception) {
        }

        val targetUrl = intent?.getStringExtra(EXTRA_TARGET_URL) ?: PORTAL_URL

        setContent {
            MyApplicationTheme {
                PortalScreen(
                    initialUrl = targetUrl,
                    onRequestNotificationPermission = { req ->
                        pendingWebPermissionRequest = req
                        requestNotificationPermissionIfNeeded()
                    },
                    onRegisterWebView = { wv ->
                        webView = wv
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newTarget = intent.getStringExtra(EXTRA_TARGET_URL)
        if (!newTarget.isNullOrBlank() && webView != null) {
            webView?.loadUrl(newTarget)
        }
    }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                try {
                    pendingWebPermissionRequest?.grant(pendingWebPermissionRequest?.resources)
                } catch (_: Exception) {
                }
                pendingWebPermissionRequest = null
            }
        }
    }

    override fun onBackPressed() {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        try {
            webView?.let {
                it.stopLoading()
                it.webChromeClient = null
                it.webViewClient = WebViewClient()
                it.removeJavascriptInterface("Android")
                it.destroy()
            }
            webView = null
        } catch (_: Exception) {
        }
        super.onDestroy()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PortalScreen(
    initialUrl: String,
    onRequestNotificationPermission: (PermissionRequest) -> Unit,
    onRegisterWebView: (WebView) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    var progress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(PortalBackground)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        allowFileAccess = true
                        allowContentAccess = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        userAgentString = settings.userAgentString + " FahadAlBelalPortalApp/21.12.2008"
                    }

                    // JavaScript Bridge to connect the website's notifications to native Android
                    addJavascriptInterface(
                        WebAppInterface(
                            context = ctx,
                            onRetry = {
                                post {
                                    if (isOnline(ctx)) {
                                        loadUrl(MainActivity.PORTAL_URL)
                                    } else {
                                        loadUrl(MainActivity.OFFLINE_URL)
                                    }
                                }
                            },
                            onRequestNotification = {
                                post {
                                    activity?.requestNotificationPermissionIfNeeded()
                                }
                            },
                            onShowNotification = { title, message ->
                                post {
                                    NotificationHelper.showMessageNotification(
                                        context = ctx,
                                        title = title,
                                        body = message
                                    )
                                }
                            }
                        ),
                        "Android"
                    )

                    webViewClient = object : WebViewClient() {
                        private var hasError = false

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            hasError = false
                            isLoading = true
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                hasError = true
                                view?.loadUrl(MainActivity.OFFLINE_URL)
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            errorResponse: WebResourceResponse?
                        ) {
                            super.onReceivedHttpError(view, request, errorResponse)
                            if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 200) >= 400) {
                                if (!isOnline(ctx)) {
                                    hasError = true
                                    view?.loadUrl(MainActivity.OFFLINE_URL)
                                }
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                            if (!hasError && url != null && url != MainActivity.OFFLINE_URL) {
                                injectNotificationBridge(view)
                            }
                        }

                        override fun onRenderProcessGone(
                            view: WebView?,
                            detail: RenderProcessGoneDetail?
                        ): Boolean {
                            try {
                                view?.post {
                                    view.loadUrl(MainActivity.OFFLINE_URL)
                                }
                            } catch (e: Exception) {
                                Log.e("FroyoPortal", "Error recovering from renderer termination", e)
                            }
                            return true
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url?.toString() ?: return false
                            if (url.startsWith("https://apbnsquad.qd.je") ||
                                url.startsWith("http://apbnsquad.qd.je") ||
                                url.startsWith("file:///android_asset/")
                            ) {
                                return false
                            }
                            return try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                ctx.startActivity(intent)
                                true
                            } catch (_: Exception) {
                                false
                            }
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress / 100f
                            if (newProgress >= 100) {
                                isLoading = false
                            }
                        }

                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            return super.onConsoleMessage(consoleMessage)
                        }

                        override fun onPermissionRequest(request: PermissionRequest?) {
                            if (request == null) return
                            val resources = request.resources
                            val isNotificationReq = resources.any {
                                it.contains("notification", ignoreCase = true)
                            }
                            if (isNotificationReq) {
                                if (NotificationHelper.hasNotificationPermission(ctx)) {
                                    request.grant(resources)
                                } else {
                                    onRequestNotificationPermission(request)
                                }
                            } else {
                                request.grant(resources)
                            }
                        }
                    }

                    onRegisterWebView(this)

                    if (isOnline(ctx)) {
                        loadUrl(initialUrl)
                    } else {
                        loadUrl(MainActivity.OFFLINE_URL)
                    }
                }
            }
        )

        if (isLoading && progress < 1f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = PortalBlue,
                trackColor = Color.Transparent
            )
        }
    }
}

/**
 * Injects Fahad Al-Belal's real-time notification bridge into the Web client.
 */
private fun injectNotificationBridge(webView: WebView?) {
    val js = """
        (function() {
            if (window._apbnNotificationBridgeInjected) return;
            window._apbnNotificationBridgeInjected = true;

            function forwardToAndroid(title, options) {
                var body = "";
                if (options) {
                    if (typeof options === "string") {
                        body = options;
                    } else if (options.body) {
                        body = options.body;
                    }
                }
                var cleanTitle = title || "APBn Squad Message";
                if (window.Android && typeof window.Android.showNotification === 'function') {
                    window.Android.showNotification(cleanTitle, body);
                }
            }

            var OriginalNotification = window.Notification;
            window.Notification = function(title, options) {
                forwardToAndroid(title, options);
                if (OriginalNotification) {
                    try {
                        return new OriginalNotification(title, options);
                    } catch(e) {}
                }
                return {
                    onclick: null,
                    onclose: null,
                    onerror: null,
                    onshow: null,
                    close: function(){}
                };
            };
            window.Notification.permission = "granted";
            window.Notification.requestPermission = function(callback) {
                if (window.Android && typeof window.Android.requestNotificationPermission === 'function') {
                    window.Android.requestNotificationPermission();
                }
                var p = Promise.resolve("granted");
                if (callback) callback("granted");
                return p;
            };

            if (typeof ServiceWorkerRegistration !== "undefined" && ServiceWorkerRegistration.prototype.showNotification) {
                var origShowNotification = ServiceWorkerRegistration.prototype.showNotification;
                ServiceWorkerRegistration.prototype.showNotification = function(title, options) {
                    forwardToAndroid(title, options);
                    try {
                        return origShowNotification.apply(this, arguments);
                    } catch(e) {
                        return Promise.resolve();
                    }
                };
            }
        })();
    """.trimIndent()

    webView?.evaluateJavascript(js, null)
}

private fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return true
    val activeNetwork = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

/**
 * JavaScript interface exposed to the Web runtime as `window.Android`.
 */
class WebAppInterface(
    private val context: Context,
    private val onRetry: () -> Unit,
    private val onRequestNotification: () -> Unit,
    revealedTitle: String = "",
    private val onShowNotification: (String, String) -> Unit
) {
    @JavascriptInterface
    fun retryConnection() {
        onRetry()
    }

    @JavascriptInterface
    fun requestNotificationPermission() {
        onRequestNotification()
    }

    @JavascriptInterface
    fun showNotification(title: String, message: String) {
        onShowNotification(title, message)
    }
}
