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
    private const val TIMEOUT_MS = 60_000L

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

        val connection = (URL(BASE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            connectTimeout = 15_000
            readTimeout = 60_000
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

                    val content = runCatching {
                        val json = JSONObject(responseBody)
                        json.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                            .trim()
                    }.getOrElse { parseError ->
                        Log.e("OpenAIService", "Response parse error: ${parseError.message}")
                        return ApiResult.Error("Invalid AI response format.", responseCode)
                    }

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
