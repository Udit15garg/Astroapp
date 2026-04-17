package com.palmreader.astro.api

import com.palmreader.astro.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * OpenAI API integration layer using HttpURLConnection (no Retrofit dependency needed).
 * In production, this should be proxied through a backend server to protect the API key.
 */
object OpenAIService {

    private const val OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions"
    private const val MODEL = "gpt-4o-mini"
    const val MODEL_VISION_FAST = "gpt-4o-mini"          // fast strict hand validation
    const val MODEL_VISION_FULL = "gpt-4o"               // standard full palm analysis (~15s)
    const val MODEL_VISION_DEEP = "gpt-5.3-chat-latest"  // deep palm analysis (~45s, richer)
    const val MODEL_PALM_QA = "gpt-4o-mini"              // palm follow-up answers
    const val MODEL_PALM_PREMIUM = "gpt-5.3-chat-latest" // richer extraction / synthesis / q&a
    private const val TIMEOUT_MS = 90_000L

    private data class RequestTransport(
        val endpoint: String,
        val authHeader: String?
    )

    sealed class ApiResult<out T> {
        data class Success<T>(val data: T) : ApiResult<T>()
        data class Error(val message: String, val code: Int = -1) : ApiResult<Nothing>()
        data object Loading : ApiResult<Nothing>()
        data object RateLimited : ApiResult<Nothing>()
    }

    /**
     * Send a chat completion request to OpenAI.
     * @param systemPrompt The system-level instruction for the model
     * @param userMessage The user's input/question
     * @param temperature Creativity level (0.0 = deterministic, 1.0 = creative)
     * @return ApiResult containing the response text or error details
     */
    suspend fun chatCompletion(
        systemPrompt: String,
        userMessage: String,
        model: String = MODEL,
        temperature: Float = 0.7f,
        maxOutputTokens: Int = 1500,
        timeoutMs: Long = TIMEOUT_MS,
        maxRetries: Int = 2
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        try {
            val transport = resolveTransport() ?: return@withContext ApiResult.Error(
                "AI backend not configured. Set OPENAI_PROXY_URL (preferred) or OPENAI_API_KEY for local testing."
            )

            var lastResult: ApiResult<String> = ApiResult.Error("Unknown error", -1)
            var backoffMs = 2_000L
            val retries = maxRetries.coerceIn(0, 2)

            for (attempt in 0..retries) {
                if (attempt > 0) {
                    Log.w("OpenAIService", "Retrying after ${backoffMs}ms (attempt $attempt)")
                    delay(backoffMs)
                    backoffMs *= 2
                }

                val result = withTimeoutOrNull(timeoutMs) {
                    try {
                        makeRequest(
                            transport = transport,
                            systemPrompt = systemPrompt,
                            userMessage = userMessage,
                            model = model,
                            temperature = temperature,
                            maxOutputTokens = maxOutputTokens
                        )
                    } catch (e: IOException) {
                        Log.w("OpenAIService", "Network IO error on attempt $attempt: ${e.message}")
                        ApiResult.Error("Network error: ${e.message}", -1)
                    }
                } ?: ApiResult.Error("Request timed out. Please try again.", 408)

                lastResult = result

                when {
                    result is ApiResult.Success -> return@withContext result
                    result is ApiResult.RateLimited -> return@withContext result
                    result is ApiResult.Error && result.code in 400..499 -> return@withContext result
                    // 5xx server errors, timeout (-408), network error (-1) → retry
                }
            }

            val fallbackModel = fallbackModelForEmpty(model)
            if (fallbackModel != null && isEmptyResponseError(lastResult)) {
                Log.w("OpenAIService", "Primary model '$model' returned empty output. Retrying with '$fallbackModel'.")
                return@withContext withTimeoutOrNull(timeoutMs) {
                    try {
                        makeRequest(
                            transport = transport,
                            systemPrompt = systemPrompt,
                            userMessage = userMessage,
                            model = fallbackModel,
                            temperature = temperature,
                            maxOutputTokens = maxOutputTokens
                        )
                    } catch (e: IOException) {
                        ApiResult.Error("Network error: ${e.message}", -1)
                    }
                } ?: ApiResult.Error("Request timed out. Please try again.", 408)
            }

            lastResult
        } catch (e: Exception) {
            Log.e("OpenAIService", "API call failed: ${e::class.simpleName}: ${e.message}", e)
            val reason = e.message?.takeIf { it.isNotBlank() } ?: "unknown reason"
            ApiResult.Error("Network error: $reason", -1)
        }
    }

    /**
     * Vision chat completion — sends an image alongside text to a vision-capable model.
     * @param imageBase64 Base64-encoded JPEG/PNG image data (without data URI prefix)
     * @param model Use MODEL_VISION_FAST for quick validation, MODEL_VISION_FULL for analysis
     */
    suspend fun visionChatCompletion(
        systemPrompt: String,
        userMessage: String,
        imageBase64: String,
        model: String = MODEL_VISION_FAST,
        temperature: Float = 0.3f,
        imageDetail: String = "high",
        maxOutputTokens: Int = 900,
        timeoutMs: Long = TIMEOUT_MS,
        maxRetries: Int = 1
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        try {
            val transport = resolveTransport() ?: return@withContext ApiResult.Error(
                "AI backend not configured. Set OPENAI_PROXY_URL (preferred) or OPENAI_API_KEY for local testing."
            )
            var lastResult: ApiResult<String> = ApiResult.Error("Unknown vision error", -1)
            var backoffMs = 1_500L
            val retries = maxRetries.coerceIn(0, 2)
            for (attempt in 0..retries) {
                if (attempt > 0) {
                    Log.w("OpenAIService", "Retrying vision after ${backoffMs}ms (attempt $attempt)")
                    delay(backoffMs)
                    backoffMs *= 2
                }
                val result = withTimeoutOrNull(timeoutMs) {
                    try {
                        makeVisionRequest(
                            transport = transport,
                            systemPrompt = systemPrompt,
                            userMessage = userMessage,
                            imageBase64 = imageBase64,
                            model = model,
                            temperature = temperature,
                            imageDetail = imageDetail,
                            maxOutputTokens = maxOutputTokens
                        )
                    } catch (e: IOException) {
                        ApiResult.Error("Network error: ${e.message}", -1)
                    }
                } ?: ApiResult.Error("Vision request timed out.", 408)

                lastResult = result
                when {
                    result is ApiResult.Success -> return@withContext result
                    result is ApiResult.RateLimited -> return@withContext result
                    result is ApiResult.Error && result.code in 400..499 -> return@withContext result
                    // retry on network/timeouts/empty response (code=200 error)
                }
            }

            val fallbackModel = fallbackModelForEmpty(model)
            if (fallbackModel != null && isEmptyResponseError(lastResult)) {
                Log.w("OpenAIService", "Primary vision model '$model' returned empty output. Retrying with '$fallbackModel'.")
                return@withContext withTimeoutOrNull(timeoutMs) {
                    try {
                        makeVisionRequest(
                            transport = transport,
                            systemPrompt = systemPrompt,
                            userMessage = userMessage,
                            imageBase64 = imageBase64,
                            model = fallbackModel,
                            temperature = temperature,
                            imageDetail = imageDetail,
                            maxOutputTokens = maxOutputTokens
                        )
                    } catch (e: IOException) {
                        ApiResult.Error("Network error: ${e.message}", -1)
                    }
                } ?: ApiResult.Error("Vision request timed out.", 408)
            }

            lastResult
        } catch (e: Exception) {
            Log.e("OpenAIService", "Vision API call failed", e)
            ApiResult.Error("Vision error: ${e.message}", -1)
        }
    }

    suspend fun multiImageVisionChatCompletion(
        systemPrompt: String,
        userMessage: String,
        imageBase64List: List<String>,
        model: String = MODEL_VISION_FULL,
        temperature: Float = 0.3f,
        imageDetail: String = "high",
        maxOutputTokens: Int = 1400,
        timeoutMs: Long = TIMEOUT_MS,
        maxRetries: Int = 1
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        try {
            val transport = resolveTransport() ?: return@withContext ApiResult.Error(
                "AI backend not configured. Set OPENAI_PROXY_URL (preferred) or OPENAI_API_KEY for local testing."
            )
            if (imageBase64List.isEmpty()) {
                return@withContext ApiResult.Error("No images were supplied for vision analysis.")
            }
            val readTimeoutMs = (timeoutMs + 8_000L).coerceAtLeast(22_000L).toInt()
            var lastResult: ApiResult<String> = ApiResult.Error("Unknown multi-image vision error", -1)
            var backoffMs = 1_500L
            val retries = maxRetries.coerceIn(0, 2)
            for (attempt in 0..retries) {
                if (attempt > 0) {
                    Log.w("OpenAIService", "Retrying multi-image vision after ${backoffMs}ms (attempt $attempt)")
                    delay(backoffMs)
                    backoffMs *= 2
                }
                val result = withTimeoutOrNull(timeoutMs) {
                    try {
                        makeVisionRequest(
                            transport = transport,
                            systemPrompt = systemPrompt,
                            userMessage = userMessage,
                            imageBase64List = imageBase64List,
                            model = model,
                            temperature = temperature,
                            imageDetail = imageDetail,
                            maxOutputTokens = maxOutputTokens,
                            readTimeoutMs = readTimeoutMs
                        )
                    } catch (e: IOException) {
                        ApiResult.Error("Network error: ${e.message}", -1)
                    }
                } ?: ApiResult.Error("Vision request timed out.", 408)

                lastResult = result
                when {
                    result is ApiResult.Success -> return@withContext result
                    result is ApiResult.RateLimited -> return@withContext result
                    result is ApiResult.Error && result.code in 400..499 -> return@withContext result
                }
            }

            val fallbackModel = fallbackModelForEmpty(model)
            if (fallbackModel != null && isEmptyResponseError(lastResult)) {
                return@withContext withTimeoutOrNull(timeoutMs) {
                    try {
                        makeVisionRequest(
                            transport = transport,
                            systemPrompt = systemPrompt,
                            userMessage = userMessage,
                            imageBase64List = imageBase64List,
                            model = fallbackModel,
                            temperature = temperature,
                            imageDetail = imageDetail,
                            maxOutputTokens = maxOutputTokens,
                            readTimeoutMs = readTimeoutMs
                        )
                    } catch (e: IOException) {
                        ApiResult.Error("Network error: ${e.message}", -1)
                    }
                } ?: ApiResult.Error("Vision request timed out.", 408)
            }

            lastResult
        } catch (e: Exception) {
            Log.e("OpenAIService", "Multi-image vision API call failed", e)
            ApiResult.Error("Vision error: ${e.message}", -1)
        }
    }

    private fun resolveTransport(): RequestTransport? {
        val proxyUrl = BuildConfig.OPENAI_PROXY_URL.trim()
        if (proxyUrl.isNotBlank()) {
            val proxyToken = BuildConfig.OPENAI_PROXY_TOKEN.trim()
            val auth = if (proxyToken.isBlank()) null else "Bearer $proxyToken"
            return RequestTransport(endpoint = proxyUrl, authHeader = auth)
        }

        val apiKey = BuildConfig.OPENAI_API_KEY
        val keyConfigured = !apiKey.isNullOrBlank() && apiKey != "YOUR_API_KEY_HERE"
        val directAllowed = BuildConfig.DEBUG || BuildConfig.ALLOW_DIRECT_OPENAI
        if (!directAllowed || !keyConfigured) return null
        return RequestTransport(
            endpoint = OPENAI_CHAT_URL,
            authHeader = "Bearer $apiKey"
        )
    }

    private fun makeVisionRequest(
        transport: RequestTransport,
        systemPrompt: String,
        userMessage: String,
        imageBase64: String,
        model: String,
        temperature: Float,
        imageDetail: String,
        maxOutputTokens: Int
    ): ApiResult<String> {
        val userContent = JSONArray().apply {
            put(JSONObject().apply {
                put("type", "text")
                put("text", userMessage)
            })
            put(JSONObject().apply {
                put("type", "image_url")
                put("image_url", JSONObject().apply {
                    put("url", "data:image/jpeg;base64,$imageBase64")
                    put("detail", imageDetail)
                })
            })
        }
        val requestBody = JSONObject().apply {
            put("model", model)
            putTemperatureIfSupported(this, model, temperature)
            putTokenLimit(this, model, maxOutputTokens.coerceIn(64, 4000))
            putReasoningEffortIfSupported(this, model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userContent)
                })
            })
        }
        return executeHttpRequest(transport, requestBody)
    }

    private fun makeVisionRequest(
        transport: RequestTransport,
        systemPrompt: String,
        userMessage: String,
        imageBase64List: List<String>,
        model: String,
        temperature: Float,
        imageDetail: String,
        maxOutputTokens: Int,
        readTimeoutMs: Int = 22_000
    ): ApiResult<String> {
        val userContent = JSONArray().apply {
            put(JSONObject().apply {
                put("type", "text")
                put("text", userMessage)
            })
            imageBase64List.forEach { imageBase64 ->
                put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", "data:image/jpeg;base64,$imageBase64")
                        put("detail", imageDetail)
                    })
                })
            }
        }
        val requestBody = JSONObject().apply {
            put("model", model)
            putTemperatureIfSupported(this, model, temperature)
            putTokenLimit(this, model, maxOutputTokens.coerceIn(64, 4000))
            putReasoningEffortIfSupported(this, model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userContent)
                })
            })
        }
        return executeHttpRequest(transport, requestBody, readTimeoutMs)
    }

    private fun makeRequest(
        transport: RequestTransport,
        systemPrompt: String,
        userMessage: String,
        model: String,
        temperature: Float,
        maxOutputTokens: Int
    ): ApiResult<String> {
        val requestBody = JSONObject().apply {
            put("model", model)
            putTemperatureIfSupported(this, model, temperature)
            putTokenLimit(this, model, maxOutputTokens.coerceIn(64, 4000))
            putReasoningEffortIfSupported(this, model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userMessage)
                })
            })
        }

        return executeHttpRequest(transport, requestBody)
    }

    private fun putTokenLimit(body: JSONObject, model: String, limit: Int) {
        if (model.startsWith("gpt-5")) {
            body.put("max_completion_tokens", limit)
        } else {
            body.put("max_tokens", limit)
        }
    }

    private fun putTemperatureIfSupported(body: JSONObject, model: String, temperature: Float) {
        // gpt-5 family currently only supports default temperature; omit explicit parameter.
        if (model.startsWith("gpt-5")) return
        body.put("temperature", temperature.toDouble())
    }

    private fun putReasoningEffortIfSupported(body: JSONObject, model: String) {
        if (!model.startsWith("gpt-5")) return
        // Encourage concise structured output for parser reliability.
        body.put("reasoning_effort", "low")
    }

    private fun executeHttpRequest(
        transport: RequestTransport,
        requestBody: JSONObject,
        readTimeoutMs: Int = 22_000
    ): ApiResult<String> {
        val startedAt = System.currentTimeMillis()
        val connection = (URL(transport.endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            transport.authHeader?.let { setRequestProperty("Authorization", it) }
            connectTimeout = 12_000
            readTimeout = readTimeoutMs
            doOutput = true
        }

        return try {
            connection.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            val elapsed = System.currentTimeMillis() - startedAt
            Log.d(
                "OpenAIService",
                "POST ${transport.endpoint} model=${requestBody.optString("model")} status=$responseCode elapsedMs=$elapsed"
            )

            when {
                responseCode == 429 -> ApiResult.RateLimited
                responseCode !in 200..299 -> {
                    val errorBody = connection.errorStream?.let { stream ->
                        BufferedReader(InputStreamReader(stream)).use { it.readText() }
                    } ?: "Unknown error"
                    val detail = extractApiErrorMessage(errorBody)
                    ApiResult.Error("API error ($responseCode) at ${transport.endpoint}: $detail", responseCode)
                }
                else -> {
                    val responseBody = BufferedReader(
                        InputStreamReader(connection.inputStream)
                    ).use { it.readText() }

                    val json = runCatching { JSONObject(responseBody) }.getOrElse { parseError ->
                        Log.e("OpenAIService", "Response parse error: ${parseError.message}")
                        return ApiResult.Error("Invalid AI response format.", responseCode)
                    }
                    val choice = json.optJSONArray("choices")?.optJSONObject(0)
                    val message = choice?.optJSONObject("message")
                    val finishReason = choice?.optString("finish_reason").orEmpty()
                    val content = extractAssistantContent(message)
                    val refusal = message?.optString("refusal").orEmpty().trim()

                    val finalText = when {
                        content.isNotBlank() -> content
                        refusal.isNotBlank() -> refusal
                        else -> ""
                    }

                    if (finalText.isBlank()) {
                        val hint = if (finishReason.isNotBlank()) " (finish_reason=$finishReason)" else ""
                        ApiResult.Error("AI returned an empty response$hint.", responseCode)
                    } else {
                        ApiResult.Success(finalText)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun extractAssistantContent(message: JSONObject?): String {
        if (message == null) return ""
        val contentAny = message.opt("content")
        return when (contentAny) {
            is String -> contentAny.trim()
            is JSONArray -> {
                buildString {
                    for (i in 0 until contentAny.length()) {
                        val part = contentAny.opt(i)
                        when (part) {
                            is JSONObject -> {
                                val text = part.optString("text").trim()
                                if (text.isNotBlank()) {
                                    if (isNotEmpty()) append('\n')
                                    append(text)
                                }
                            }
                            is String -> {
                                val text = part.trim()
                                if (text.isNotBlank()) {
                                    if (isNotEmpty()) append('\n')
                                    append(text)
                                }
                            }
                        }
                    }
                }.trim()
            }
            else -> ""
        }
    }

    private fun extractApiErrorMessage(raw: String): String {
        val parsed = runCatching {
            JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")
        return parsed.ifBlank { raw.take(300) }
    }

    private fun isEmptyResponseError(result: ApiResult<String>): Boolean {
        return result is ApiResult.Error &&
            result.code == 200 &&
            result.message.contains("empty response", ignoreCase = true)
    }

    private fun fallbackModelForEmpty(primaryModel: String): String? {
        return when {
            primaryModel == "gpt-4o-mini" -> "gpt-4o"
            primaryModel == "gpt-4o" -> "gpt-4o-mini"
            primaryModel.startsWith("gpt-5") -> "gpt-4o-mini"
            else -> null
        }
    }
}
