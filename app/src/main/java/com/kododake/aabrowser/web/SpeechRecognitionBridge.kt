package com.kododake.aabrowser.web

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.webkit.WebView
import androidx.webkit.WebMessageCompat
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.ref.WeakReference

class SpeechRecognitionBridge(
    webView: WebView,
    private val onRequestMicrophoneAccess: (String?) -> Unit
) : RecognitionListener {

    private enum class BridgeCommand {
        START,
        STOP,
        ABORT
    }

    private val webViewRef = WeakReference(webView)
    private var speechRecognizer: SpeechRecognizer? = null
    private var pendingLang: String? = null

    fun handleWebMessage(
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        currentPageUrl: String?
    ) {
        if (!isTrustedCaller(sourceOrigin, isMainFrame, currentPageUrl)) return

        val payload = message.data ?: return
        val command = parseCommand(payload) ?: return
        when (command.first) {
            BridgeCommand.START -> {
                pendingLang = command.second.orEmpty()
                webViewRef.get()?.post { onRequestMicrophoneAccess(sourceOrigin.toString()) }
            }

            BridgeCommand.STOP -> {
                webViewRef.get()?.post { speechRecognizer?.stopListening() }
            }

            BridgeCommand.ABORT -> {
                webViewRef.get()?.post {
                    speechRecognizer?.cancel()
                    stopInternal()
                    dispatchSimple("end")
                }
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        val lang = pendingLang
        pendingLang = null
        if (granted && lang != null) {
            startListening(lang)
        } else {
            dispatchError("not-allowed")
            dispatchSimple("end")
        }
    }

    fun destroy() {
        stopInternal()
    }

    fun hasPendingPermissionRequest(): Boolean = pendingLang != null

    private fun startListening(lang: String) {
        val webView = webViewRef.get() ?: return
        val context = webView.context

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            dispatchError("service-not-allowed")
            dispatchSimple("end")
            return
        }

        stopInternal()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).also { sr ->
            sr.setRecognitionListener(this)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                if (lang.isNotBlank()) putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            }
            sr.startListening(intent)
        }
    }

    private fun stopInternal() {
        speechRecognizer?.apply {
            try { stopListening() } catch (_: Exception) {}
            try { destroy() } catch (_: Exception) {}
        }
        speechRecognizer = null
    }

    private fun isTrustedCaller(sourceOrigin: Uri, isMainFrame: Boolean, currentPageUrl: String?): Boolean {
        if (!isMainFrame) return false
        val currentPageOrigin = currentPageUrl
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?.takeIf { it.scheme in setOf("http", "https") && !it.host.isNullOrBlank() }
            ?: return false
        return normalizedOrigin(sourceOrigin) == normalizedOrigin(currentPageOrigin)
    }

    private fun normalizedOrigin(uri: Uri): String {
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        val port = when {
            uri.port != -1 -> uri.port
            scheme == "https" -> 443
            scheme == "http" -> 80
            else -> -1
        }
        return "$scheme://$host:$port"
    }

    private fun parseCommand(payload: String): Pair<BridgeCommand, String?>? {
        return try {
            val json = JSONObject(payload)
            val type = json.optString("type").lowercase()
            when (type) {
                "start" -> BridgeCommand.START to json.optString("lang")
                "stop" -> BridgeCommand.STOP to null
                "abort" -> BridgeCommand.ABORT to null
                else -> null
            }
        } catch (_: JSONException) {
            null
        }
    }

    private fun dispatchSimple(eventType: String) {
        val webView = webViewRef.get() ?: return
        webView.post {
            webView.evaluateJavascript(
                "window.__sr_event&&window.__sr_event('$eventType')", null
            )
        }
    }

    private fun dispatchError(errorCode: String) {
        val webView = webViewRef.get() ?: return
        webView.post {
            webView.evaluateJavascript(
                "window.__sr_event&&window.__sr_event('error','$errorCode')", null
            )
        }
    }

    private fun dispatchResults(
        matches: List<String>,
        confidences: FloatArray?,
        isFinal: Boolean
    ) {
        val webView = webViewRef.get() ?: return
        val alts = JSONArray()
        matches.forEachIndexed { i, text ->
            alts.put(JSONObject().apply {
                put("transcript", text)
                put("confidence", (confidences?.getOrNull(i) ?: 0.9f).toDouble())
            })
        }
        val payload = JSONObject().apply {
            put("a", alts)
            put("f", isFinal)
        }.toString()
        val escaped = payload
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        webView.post {
            webView.evaluateJavascript(
                "window.__sr_event&&window.__sr_event('result','$escaped')", null
            )
        }
    }

    // RecognitionListener

    override fun onReadyForSpeech(params: Bundle?) {
        dispatchSimple("start")
        dispatchSimple("audiostart")
    }

    override fun onBeginningOfSpeech() {
        dispatchSimple("speechstart")
    }

    override fun onEndOfSpeech() {
        dispatchSimple("speechend")
        dispatchSimple("audioend")
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
        if (matches.isNullOrEmpty()) {
            dispatchError("no-speech")
            dispatchSimple("end")
            return
        }
        dispatchResults(matches, confidences, isFinal = true)
        dispatchSimple("end")
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches =
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (matches.isNullOrEmpty()) return
        dispatchResults(matches, null, isFinal = false)
    }

    override fun onError(error: Int) {
        val code = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no-speech"
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "not-allowed"
            SpeechRecognizer.ERROR_AUDIO -> "audio-capture"
            else -> "aborted"
        }
        dispatchError(code)
        dispatchSimple("end")
    }

    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    companion object {
        const val BRIDGE_OBJECT_NAME = "_SpeechBridgeChannel"

        val POLYFILL_JS = """
            (function(){
                if(window.__sr_polyfill) return;
                window.__sr_polyfill = true;
                var bridge = window.${BRIDGE_OBJECT_NAME};
                var active = null;
                window.__sr_event = function(type, data) {
                    if(!active) return;
                    var r = active;
                    if(type === 'result') {
                        try {
                            var d = JSON.parse(data);
                            var alts = d.a;
                            var result = {isFinal: d.f, length: alts.length};
                            for(var i = 0; i < alts.length; i++) result[i] = alts[i];
                            var results = {length: 1, 0: result};
                            var evt = {resultIndex: 0, results: results};
                            if(r.onresult) r.onresult(evt);
                        } catch(e) {}
                    } else if(type === 'error') {
                        if(r.onerror) r.onerror({error: data});
                    } else {
                        var handler = r['on' + type];
                        if(handler) {
                            try { handler(new Event(type)); } catch(e) { handler({}); }
                        }
                    }
                    if(type === 'end') active = null;
                };
                function postToNative(payload) {
                    if(!bridge || typeof bridge.postMessage !== 'function') {
                        window.__sr_event('error', 'service-not-allowed');
                        window.__sr_event('end');
                        return false;
                    }
                    try {
                        bridge.postMessage(JSON.stringify(payload));
                        return true;
                    } catch(e) {
                        window.__sr_event('error', 'service-not-allowed');
                        window.__sr_event('end');
                        return false;
                    }
                }
                function SR() {
                    this.lang = '';
                    this.continuous = false;
                    this.interimResults = false;
                    this.maxAlternatives = 1;
                    this.onresult = null;
                    this.onerror = null;
                    this.onstart = null;
                    this.onend = null;
                    this.onspeechstart = null;
                    this.onspeechend = null;
                    this.onaudiostart = null;
                    this.onaudioend = null;
                    this.onnomatch = null;
                }
                SR.prototype.start = function() {
                    active = this;
                    postToNative({type: 'start', lang: this.lang || ''});
                };
                SR.prototype.stop = function() {
                    postToNative({type: 'stop'});
                };
                SR.prototype.abort = function() {
                    postToNative({type: 'abort'});
                };
                SR.prototype.addEventListener = function(type, fn) {
                    this['on' + type] = fn;
                };
                SR.prototype.removeEventListener = function(type, fn) {
                    if(this['on' + type] === fn) this['on' + type] = null;
                };
                window.SpeechRecognition = SR;
                window.webkitSpeechRecognition = SR;
            })();
        """.trimIndent()
    }
}
