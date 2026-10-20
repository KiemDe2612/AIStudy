package com.aistudy.aistudy.ask

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object GeminiClient {

    private const val TAG = "GeminiClient"
    private const val MODEL = "gemini-2.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    fun ask(apiKey: String, prompt: String): String {
        val endpoint = "$BASE_URL/$MODEL:generateContent?key=$apiKey"
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection

        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.connectTimeout = 30000
            conn.readTimeout = 60000
            conn.doOutput = true

            val textPart = JSONObject().put("text", prompt)
            val partsArray = JSONArray().put(textPart)
            val contentObj = JSONObject().put("parts", partsArray)
            val contentsArray = JSONArray().put(contentObj)
            val requestBody = JSONObject().put("contents", contentsArray).toString()

            conn.outputStream.use { os ->
                val input = requestBody.toByteArray(StandardCharsets.UTF_8)
                os.write(input, 0, input.size)
            }

            val code = conn.responseCode
            Log.d(TAG, "response httpCode=$code")

            if (code in 200..299) {
                val responseBody = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use(BufferedReader::readText)
                Log.d(TAG, "response preview: ${responseBody.take(200)}")

                val rootJson = JSONObject(responseBody)
                val candidates = rootJson.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    throw Exception("No candidates returned by Gemini")
                }
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.getJSONObject("content")
                val parts = content.getJSONArray("parts")
                if (parts.length() == 0) {
                    throw Exception("No content parts in Gemini candidate")
                }
                return parts.getJSONObject(0).getString("text")
            } else {
                val errorBody = conn.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use(BufferedReader::readText) ?: ""
                Log.e(TAG, "request failed httpCode=$code error=$errorBody")
                throw Exception("HTTP $code: $errorBody")
            }
        } finally {
            conn.disconnect()
        }
    }
}
