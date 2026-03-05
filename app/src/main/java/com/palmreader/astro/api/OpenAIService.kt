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

    private const val BASE_URL = "https://api.openai.com/v1/chat/completions"
    private const val MODEL = "gpt-4o-mini"
    const val MODEL_VISION_FAST = "gpt-4o-mini"   // cheap: hand validation
    const val MODEL_VISION_FULL = "gpt-4o"         // full palm analysis
    private const val TIMEOUT_MS = 90_000L

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
        temperature: Float = 0.7f
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey: String? = BuildConfig.OPENAI_API_KEY

            if (apiKey.isNullOrBlank() || apiKey == "YOUR_API_KEY_HERE") {
                return@withContext ApiResult.Error(
                    "API key not configured. Add OPENAI_API_KEY to local.properties."
                )
            }

            var lastResult: ApiResult<String> = ApiResult.Error("Unknown error", -1)
            var backoffMs = 2_000L

            for (attempt in 0..2) {
                if (attempt > 0) {
                    Log.w("OpenAIService", "Retrying after ${backoffMs}ms (attempt $attempt)")
                    delay(backoffMs)
                    backoffMs *= 2
                }

                val result = withTimeoutOrNull(TIMEOUT_MS) {
                    try {
                        makeRequest(apiKey, systemPrompt, userMessage, temperature)
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
        temperature: Float = 0.3f
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey: String? = BuildConfig.OPENAI_API_KEY
            if (apiKey.isNullOrBlank() || apiKey == "YOUR_API_KEY_HERE") {
                return@withContext ApiResult.Error("API key not configured.")
            }
            withTimeoutOrNull(TIMEOUT_MS) {
                try {
                    makeVisionRequest(apiKey, systemPrompt, userMessage, imageBase64, model, temperature)
                } catch (e: IOException) {
                    ApiResult.Error("Network error: ${e.message}", -1)
                }
            } ?: ApiResult.Error("Vision request timed out.", 408)
        } catch (e: Exception) {
            Log.e("OpenAIService", "Vision API call failed", e)
            ApiResult.Error("Vision error: ${e.message}", -1)
        }
    }

    private fun makeVisionRequest(
        apiKey: String,
        systemPrompt: String,
        userMessage: String,
        imageBase64: String,
        model: String,
        temperature: Float
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
                    put("detail", "high")
                })
            })
        }
        val requestBody = JSONObject().apply {
            put("model", model)
            put("temperature", temperature.toDouble())
            put("max_tokens", 800)
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
        return executeHttpRequest(apiKey, requestBody)
    }

    private fun makeRequest(
        apiKey: String,
        systemPrompt: String,
        userMessage: String,
        temperature: Float
    ): ApiResult<String> {
        val requestBody = JSONObject().apply {
            put("model", MODEL)
            put("temperature", temperature.toDouble())
            put("max_tokens", 1500)
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

        return executeHttpRequest(apiKey, requestBody)
    }

    private fun executeHttpRequest(apiKey: String, requestBody: JSONObject): ApiResult<String> {
        val connection = (URL(BASE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            connectTimeout = 15_000
            readTimeout = 90_000
            doOutput = true
        }

        return try {
            connection.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode

            when {
                responseCode == 429 -> ApiResult.RateLimited
                responseCode !in 200..299 -> {
                    val errorBody = connection.errorStream?.let { stream ->
                        BufferedReader(InputStreamReader(stream)).use { it.readText() }
                    } ?: "Unknown error"
                    ApiResult.Error("API error ($responseCode): $errorBody", responseCode)
                }
                else -> {
                    val responseBody = BufferedReader(
                        InputStreamReader(connection.inputStream)
                    ).use { it.readText() }

                    val json = runCatching { JSONObject(responseBody) }.getOrElse { parseError ->
                        Log.e("OpenAIService", "Response parse error: ${parseError.message}")
                        return ApiResult.Error("Invalid AI response format.", responseCode)
                    }
                    val content = json
                        .optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content")
                        ?.trim()
                        .orEmpty()

                    if (content.isBlank()) {
                        ApiResult.Error("AI returned an empty response.", responseCode)
                    } else {
                        ApiResult.Success(content)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}
