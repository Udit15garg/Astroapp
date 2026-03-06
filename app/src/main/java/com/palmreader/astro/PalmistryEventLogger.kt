package com.palmreader.astro

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PalmistryEventLogger {
    private const val TAG = "PalmistryEvent"
    private const val FILE_NAME = "palmistry_events.log"

    fun log(context: Context, event: String, details: Map<String, Any?> = emptyMap()) {
        val timestamp = System.currentTimeMillis()
        val payload = JSONObject().apply {
            put("ts", timestamp)
            put("time", isoTime(timestamp))
            put("event", event)
            details.forEach { (k, v) -> put(k, v?.toString().orEmpty()) }
        }
        val line = payload.toString()
        Log.d(TAG, line)
        runCatching {
            logFile(context).appendText(line + "\n")
        }.onFailure { e ->
            Log.w(TAG, "Failed to persist palmistry log: ${e.message}")
        }
    }

    fun logPath(context: Context): String = logFile(context).absolutePath

    private fun logFile(context: Context): File {
        return File(context.cacheDir, FILE_NAME).also { file ->
            if (!file.exists()) file.createNewFile()
        }
    }

    private fun isoTime(ms: Long): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date(ms))
    }
}
