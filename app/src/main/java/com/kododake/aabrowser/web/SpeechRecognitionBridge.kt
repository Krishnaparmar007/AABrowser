package com.kododake.aabrowser.web

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference

class SpeechRecognitionBridge(
    webView: WebView,
    private val onNeedPermission: () -> Unit
) : RecognitionListener {

    private val webViewRef = WeakReference(webView)
    private var speechRecognizer: SpeechRecognizer? = null
    private var pendingLang: String? = null

    @JavascriptInterface
    fun startRecognition(lang: String) {
        val webView = webViewRef.get() ?: return
        val context = webView.context

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingLang = lang
            webView.post { onNeedPermission() }
            return
        }

        webView.post { startListening(lang) }
    }

    @JavascriptInterface
    fun stopRecognition() {
        val webView = webViewRef.get() ?: return
        webView.post {
            speechRecognizer?.stopListening()
        }
    }

    @JavascriptInterface
    fun abortRecognition() {
        val webView = webViewRef.get() ?: return
        webView.post {
            speechRecognizer?.cancel()
            stopInternal()
            dispatchSimple("end")
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
        const val JS_INTERFACE_NAME = "_SpeechBridge"

        val POLYFILL_JS = """
            (function(){
                if(window.__sr_polyfill) return;
                window.__sr_polyfill = true;
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
                    try { _SpeechBridge.startRecognition(this.lang || ''); } catch(e) {}
                };
                SR.prototype.stop = function() {
                    try { _SpeechBridge.stopRecognition(); } catch(e) {}
                };
                SR.prototype.abort = function() {
                    try { _SpeechBridge.abortRecognition(); } catch(e) {}
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
