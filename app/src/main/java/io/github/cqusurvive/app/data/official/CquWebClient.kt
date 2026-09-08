package io.github.cqusurvive.app.data.official

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import io.github.cqusurvive.app.BuildConfig
import kotlin.coroutines.resume

sealed interface WebAuthState {
    data object Idle : WebAuthState
    data object Loading : WebAuthState
    data object Authenticated : WebAuthState
    data class Failed(val message: String) : WebAuthState
}

data class WebResponse(val status: Int, val body: String)

/**
 * Runs official-service requests inside the school's authenticated WebView origin.
 * Passwords, MFA codes, cookies, and OAuth tokens are never extracted into Kotlin.
 */
class CquWebClient(private val context: android.content.Context) {
    private val allowedHosts = setOf("my.cqu.edu.cn", "sso.cqu.edu.cn")
    private val _authState = MutableStateFlow<WebAuthState>(WebAuthState.Idle)
    val authState: StateFlow<WebAuthState> = _authState.asStateFlow()

    private companion object {
        // WebView proxy overrides are process-wide. Sharing one loopback proxy lets the
        // dedicated login Activity hand its WebView session back to the main Activity
        // without racing another client's clearProxyOverride call.
        val scopedProxy by lazy { ScopedConnectProxy() }
        @Volatile var proxyConfigured = false
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var authProbe: Runnable? = null

    @SuppressLint("SetJavaScriptEnabled")
    val webView = WebView(context).apply {
        // ColorOS Autofill can repeatedly restore a WebView form value while an IME is
        // composing text, which resets the DOM selection and makes input appear reversed.
        // Authentication data deliberately stays in the official page, so excluding this
        // WebView from platform Autofill also keeps the existing credential boundary clear.
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        isFocusable = true
        isFocusableInTouchMode = true
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        if (android.os.Build.VERSION.SDK_INT >= 26) settings.safeBrowsingEnabled = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        WebView.setWebContentsDebuggingEnabled(false)
        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                cancelAuthProbe()
                _authState.value = WebAuthState.Loading
            }

            override fun onPageFinished(view: WebView, url: String?) {
                val uri = url?.let(Uri::parse)
                debugLog("page", uri, 0)
                if (uri?.host == "my.cqu.edu.cn" && uri.path?.startsWith("/workspace") == true) {
                    startAuthProbe()
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse,
            ) {
                debugLog("http", request.url, errorResponse.statusCode)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (BuildConfig.DEBUG) {
                    Log.w("ReCQU.Web", "network host=${request.url.host} path=${request.url.path} code=${error.errorCode}")
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                return request.url.scheme != "https" || request.url.host !in allowedHosts
            }
        }
    }

    private fun debugLog(event: String, uri: Uri?, status: Int) {
        if (BuildConfig.DEBUG && uri != null) {
            Log.d("ReCQU.Web", "$event host=${uri.host} path=${uri.path} status=$status")
        }
    }

    private fun startAuthProbe() {
        cancelAuthProbe()
        val probe = object : Runnable {
            private var attempts = 0

            override fun run() {
                val current = webView.url?.let(Uri::parse)
                if (current?.host != "my.cqu.edu.cn" || current.path?.startsWith("/workspace") != true) return

                val nextProbe = this
                webView.evaluateJavascript(
                    "Boolean(localStorage.getItem('cqu_edu_ACCESS_TOKEN'))",
                ) { value ->
                    if (authProbe !== nextProbe) return@evaluateJavascript
                    if (value == "true") {
                        authProbe = null
                        _authState.value = WebAuthState.Authenticated
                    } else if (++attempts < 240) {
                        mainHandler.postDelayed(nextProbe, 250)
                    }
                }
            }
        }
        authProbe = probe
        mainHandler.post(probe)
    }

    private fun cancelAuthProbe() {
        authProbe?.let(mainHandler::removeCallbacks)
        authProbe = null
    }

    fun startLogin() {
        _authState.value = WebAuthState.Loading
        if (proxyConfigured) {
            webView.loadUrl("https://my.cqu.edu.cn/workspace/home")
            return
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE_REVERSE_BYPASS)
        ) {
            _authState.value = WebAuthState.Failed("当前系统 WebView 不支持应用内校园域名解析")
            return
        }
        val config = ProxyConfig.Builder()
            .addProxyRule("http://127.0.0.1:${scopedProxy.port}")
            .addBypassRule("my.cqu.edu.cn")
            .addBypassRule("sso.cqu.edu.cn")
            .setReverseBypassEnabled(true)
            .build()
        ProxyController.getInstance().setProxyOverride(
            config,
            ContextCompat.getMainExecutor(context),
        ) {
            proxyConfigured = true
            webView.loadUrl("https://my.cqu.edu.cn/workspace/home")
        }
    }

    suspend fun fetch(path: String, method: String = "GET", jsonBody: String? = null): WebResponse {
        require(path.startsWith('/')) { "Only same-origin paths are allowed" }
        require(method in setOf("GET", "POST")) { "Unsupported method" }
        check(authState.value is WebAuthState.Authenticated) { "Not authenticated" }

        val requestId = "native_${UUID.randomUUID().toString().replace("-", "")}"
        val bodyStatement = jsonBody?.let {
            "options.headers['Content-Type']='application/json';options.body=${JSONObject.quote(it)};"
        }.orEmpty()
        val script = """
            (() => {
              window.__cquNativeResponses = window.__cquNativeResponses || {};
              let token = localStorage.getItem('cqu_edu_ACCESS_TOKEN') || '';
              try { const parsed = JSON.parse(token); if (typeof parsed === 'string') token = parsed; } catch (_) {}
              const options = {
                method: ${JSONObject.quote(method)},
                credentials: 'include',
                headers: { Authorization: /^Bearer\s/i.test(token) ? token : 'Bearer ' + token }
              };
              $bodyStatement
              fetch(${JSONObject.quote(path)}, options)
                .then(async response => {
                  window.__cquNativeResponses[${JSONObject.quote(requestId)}] = JSON.stringify({
                    status: response.status,
                    body: await response.text()
                  });
                })
                .catch(error => {
                  window.__cquNativeResponses[${JSONObject.quote(requestId)}] = JSON.stringify({
                    status: 0,
                    body: JSON.stringify({ error: error && error.name ? error.name : 'FetchError' })
                  });
                });
              return true;
            })();
        """.trimIndent()
        evaluate(script)

        repeat(150) {
            delay(100)
            val result = evaluate(
                "(() => { const r=window.__cquNativeResponses && window.__cquNativeResponses[${JSONObject.quote(requestId)}];" +
                    "if(r){delete window.__cquNativeResponses[${JSONObject.quote(requestId)}];return r;}return null; })();",
            )
            if (result != null) {
                val envelope = JSONObject(result)
                return WebResponse(envelope.getInt("status"), envelope.getString("body"))
            }
        }
        throw IllegalStateException("Official service request timed out")
    }

    fun logout() {
        webView.evaluateJavascript("localStorage.clear(); sessionStorage.clear();", null)
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        webView.clearCache(true)
        _authState.value = WebAuthState.Idle
        webView.loadUrl("about:blank")
    }

    fun destroy() {
        cancelAuthProbe()
        webView.stopLoading()
        webView.destroy()
    }

    private suspend fun evaluate(script: String): String? = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            webView.evaluateJavascript(script) { encoded ->
                if (!continuation.isActive) return@evaluateJavascript
                val decoded = when (encoded) {
                    null, "null", "undefined" -> null
                    else -> runCatching { JSONArray("[$encoded]").getString(0) }.getOrNull()
                }
                continuation.resume(decoded)
            }
        }
    }
}
