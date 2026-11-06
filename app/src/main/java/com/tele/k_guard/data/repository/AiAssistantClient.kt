package com.tele.k_guard.data.repository

import android.util.Log
import com.tele.k_guard.data.model.AiConfig
import com.tele.k_guard.data.model.GeminiKeyRotationManager
import com.tele.k_guard.data.model.LlmProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** One line of the conversation. */
data class AiTurn(val fromUser: Boolean, val text: String)

/** The model's safety filter held back the answer. */
class AiFilteredException : IOException("The answer was held back by the safety filter.")

/**
 * Talks to the language model behind the K-Guard Assistant: the self-hosted model when [AiConfig.selfHostedUrl]
 * is set (falling back to Gemini if it can't be reached), otherwise Gemini.
 */
class AiAssistantClient {

    /**
     * Streams the answer: each emission is the whole answer so far, so the UI can simply show the latest value.
     * Throws [AiFilteredException] if the safety filter blocked the answer, or IOException on network errors.
     */
    fun stream(systemPrompt: String, history: List<AiTurn>): Flow<String> = flow {
        val turns = history.takeLast(AiConfig.MAX_HISTORY_TURNS).dropWhile { !it.fromUser }
        require(turns.isNotEmpty()) { "Nothing to answer." }
        if (AiConfig.provider == LlmProviderType.SELF_HOSTED) {
            var emitted = false
            try {
                streamSelfHosted(systemPrompt, turns) { emitted = true; emit(it) }
                return@flow
            } catch (e: IOException) {
                if (emitted) throw e // part of an answer was already shown; don't mix in another model
                Log.w(TAG, "Self-hosted model failed (${e.message}); using Gemini instead")
            }
        }
        streamGemini(systemPrompt, turns)
    }.flowOn(Dispatchers.IO)

    // ------------------------------------------------------------------ Gemini
    private suspend fun FlowCollector<String>.streamGemini(system: String, turns: List<AiTurn>) {
        fun body(model: String) = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            put("contents", JSONArray().apply {
                turns.forEach { t ->
                    put(JSONObject().put("role", if (t.fromUser) "user" else "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", t.text))))
                }
            })
            put("generationConfig", JSONObject()
                .put("maxOutputTokens", AiConfig.MAX_REPLY_TOKENS)
                .put("temperature", 0.6)
                .apply { if (com.tele.k_guard.live.LiveScreenAnalyzer.supportsNoThinking(model)) put("thinkingConfig", JSONObject().put("thinkingBudget", 0)) })
            put("safetySettings", JSONArray().apply {
                listOf("HARASSMENT", "HATE_SPEECH", "SEXUALLY_EXPLICIT", "DANGEROUS_CONTENT").forEach {
                    put(JSONObject().put("category", "HARM_CATEGORY_$it").put("threshold", "BLOCK_MEDIUM_AND_ABOVE"))
                }
            })
        }.toString()

        var lastError: IOException? = null
        for (model in AiConfig.geminiModels) {
            var keysTried = 0
            while (keysTried < GeminiKeyRotationManager.keyCount) {
                val conn = open(
                    "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse",
                    body(model), mapOf("x-goog-api-key" to GeminiKeyRotationManager.getCurrentKey())
                )
                try {
                    val code = conn.responseCode
                    if (code in 200..299) {
                        readGeminiStream(conn, model)
                        return
                    }
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    when {
                        code == 429 || code == 403 || err.contains("RESOURCE_EXHAUSTED") -> {
                            lastError = IOException("Gemini $model HTTP $code")
                            GeminiKeyRotationManager.rotateKey(); keysTried++
                        }
                        code == 404 -> { lastError = IOException("Gemini model $model is not available"); keysTried = Int.MAX_VALUE }
                        else -> throw IOException("Gemini $model HTTP $code: ${errorMessage(err)}")
                    }
                } finally {
                    conn.disconnect()
                }
            }
        }
        throw lastError ?: IOException("Gemini could not be reached")
    }

    private suspend fun FlowCollector<String>.readGeminiStream(conn: HttpURLConnection, model: String) {
        val answer = StringBuilder()
        var filtered = false
        conn.inputStream.bufferedReader().useLines { lines ->
            for (line in lines) {
                currentCoroutineContext().ensureActive()
                if (!line.startsWith("data:")) continue
                val chunk = runCatching { JSONObject(line.removePrefix("data:").trim()) }.getOrNull() ?: continue
                if (chunk.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty().isNotBlank()) filtered = true
                val candidate = chunk.optJSONArray("candidates")?.optJSONObject(0) ?: continue
                if (candidate.optString("finishReason") in SAFETY_FINISH) filtered = true
                val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: continue
                var grew = false
                for (i in 0 until parts.length()) {
                    val t = parts.optJSONObject(i)?.optString("text").orEmpty()
                    if (t.isNotEmpty()) { answer.append(t); grew = true }
                }
                if (grew) emit(answer.toString())
            }
        }
        Log.d(TAG, "Answer from Gemini $model: ${answer.length} chars, filtered=$filtered")
        if (filtered && answer.isBlank()) throw AiFilteredException()
        if (answer.isBlank()) throw IOException("Gemini returned an empty answer")
    }

    // ------------------------------------------------------------------ self-hosted (OpenAI-compatible)
    private suspend fun streamSelfHosted(system: String, turns: List<AiTurn>, emit: suspend (String) -> Unit) {
        val base = AiConfig.selfHostedUrl.trimEnd('/')
        val url = when {
            base.endsWith("/chat/completions") -> base
            base.endsWith("/v1") -> "$base/chat/completions"
            else -> "$base/v1/chat/completions"
        }
        val body = JSONObject().apply {
            put("model", AiConfig.selfHostedModel)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", system))
                turns.forEach { put(JSONObject().put("role", if (it.fromUser) "user" else "assistant").put("content", it.text)) }
            })
            put("max_tokens", AiConfig.MAX_REPLY_TOKENS)
            put("temperature", 0.6)
            put("stream", true)
        }.toString()
        val headers = buildMap {
            put("ngrok-skip-browser-warning", "true") // ngrok's free plan otherwise returns an HTML warning page
            put("Accept", "text/event-stream")
            if (AiConfig.selfHostedKey.isNotBlank()) put("Authorization", "Bearer ${AiConfig.selfHostedKey}")
        }
        val conn = open(url, body, headers)
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IOException("Self-hosted model HTTP $code: ${conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty().take(200)}")
            }
            val answer = StringBuilder()
            val isStream = conn.contentType.orEmpty().contains("event-stream")
            if (!isStream) {
                // Some servers ignore "stream": true and answer in one piece.
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                answer.append(json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty())
                if (answer.isNotBlank()) emit(answer.toString().trim())
            } else {
                conn.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        currentCoroutineContext().ensureActive()
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        val delta = runCatching {
                            JSONObject(data).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content")
                        }.getOrNull().orEmpty()
                        if (delta.isNotEmpty()) { answer.append(delta); emit(answer.toString()) }
                    }
                }
            }
            if (answer.isBlank()) throw IOException("Self-hosted model returned an empty answer")
            Log.d(TAG, "Answer from self-hosted ${AiConfig.selfHostedModel}: ${answer.length} chars")
        } finally {
            conn.disconnect()
        }
    }

    // ------------------------------------------------------------------ helpers
    private fun open(url: String, body: String, headers: Map<String, String>): HttpURLConnection {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = AiConfig.TIMEOUT_MS
            readTimeout = AiConfig.TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        return conn
    }

    private fun errorMessage(raw: String) =
        runCatching { JSONObject(raw).optJSONObject("error")?.optString("message") }.getOrNull()?.take(200) ?: raw.take(200)

    private companion object {
        const val TAG = "KGuardAI"
        val SAFETY_FINISH = setOf("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII")
    }
}
