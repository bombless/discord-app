package dev.bombless.discordapp

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

class ScriptRuntime(
    context: Context,
    private val repository: ScriptRepository,
    private val state: RuntimeState
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pageReady = false
    private var pendingScript: ActiveScript? = null

    @SuppressLint("SetJavaScriptEnabled")
    val webView: WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = false
        settings.allowFileAccess = true
        settings.allowContentAccess = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        webChromeClient = WebChromeClient()
        addJavascriptInterface(NativeHost(), "NativeHost")
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                pageReady = true
                val initial = pendingScript ?: repository.active()
                pendingScript = null
                applyNow(initial)
            }
        }
        loadUrl("file:///android_asset/runtime.html")
    }

    fun apply(script: ActiveScript) {
        mainHandler.post {
            pendingScript = script
            state.activeVersion.value = script.version
            state.versions.value = repository.listVersions()
            if (pageReady) {
                pendingScript = null
                applyNow(script)
            }
        }
    }

    fun dispatch(callbackId: Int, payload: Any?) {
        if (callbackId <= 0) return
        val literal = when (payload) {
            null -> "null"
            is String -> JSONObject.quote(payload)
            is Number, is Boolean -> payload.toString()
            else -> JSONObject.quote(payload.toString())
        }
        mainHandler.post {
            if (pageReady) webView.evaluateJavascript("window.__dispatchEvent($callbackId, $literal);", null)
        }
    }

    private fun applyNow(script: ActiveScript) {
        if (!pageReady) {
            pendingScript = script
            return
        }
        webView.evaluateJavascript(
            "window.__loadScript(${JSONObject.quote(script.source)}, ${script.version});",
            null
        )
    }

    fun close() {
        mainHandler.post {
            webView.stopLoading()
            webView.removeJavascriptInterface("NativeHost")
            webView.destroy()
        }
    }

    inner class NativeHost {
        @JavascriptInterface
        fun publishUi(json: String) {
            try {
                val parsed = UiNode.fromJson(JSONObject(json))
                mainHandler.post { state.tree.value = parsed }
            } catch (error: Exception) {
                log("Invalid UI tree: ${error.message}")
            }
        }

        @JavascriptInterface
        fun log(message: String) {
            mainHandler.post { state.appendLog(message) }
        }

        @JavascriptInterface
        fun onScriptLoaded(version: Int) {
            mainHandler.post {
                state.activeVersion.value = version
                state.statusMessage.value = "Script v$version loaded"
            }
        }
    }
}
