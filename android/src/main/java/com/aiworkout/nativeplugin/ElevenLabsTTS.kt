package com.demo.aiworlout

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.IOException

class ElevenLabsTTS(private val apiKey: String) {

    private val client = OkHttpClient()

    fun speak(text: String) {
        val json = JSONObject().apply {
            put("text", text)
            put("voice_settings", JSONObject().apply {
                put("stability", 0.4)
                put("similarity_boost", 0.7)
            })
        }

        val body = RequestBody.create(
            "application/json".toMediaType(),
            json.toString()
        )

        val request = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/EXAVITQu4vr4xnSDxMaL")
            .addHeader("xi-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                // stream audio → AudioTrack (advanced)
            }
        })
    }
}
