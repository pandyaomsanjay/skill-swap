package com.example.sgp

import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Calls the `admin-credits` Supabase Edge Function. The Firebase ID token is sent as the
 * Bearer token; the function verifies it and checks admin status server-side.
 * No Supabase service-role key is ever on the device.
 *
 * Deploy with:  supabase functions deploy admin-credits --no-verify-jwt
 * (the token is a Firebase token, so Supabase's own JWT check must be off; the function does its own.)
 */
object AdminCreditApi {

    // TODO: use the same project URL your existing credit-purchase functions already use.
    // Base URL only; the route is appended: .../admin-credits/adjust-credits
    // Inside AdminCreditApi.kt
    const val FUNCTION_URL = "https://ghrxltlstncjcizyyqfo.supabase.co/functions/v1/admin-credits"

    private val main = Handler(Looper.getMainLooper())

    fun call(action: String, payload: JSONObject, onDone: (Result<JSONObject>) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onDone(Result.failure(Exception("Not signed in with Firebase. Please log in again.")))
            return
        }
        user.getIdToken(false).addOnCompleteListener { t ->
            if (!t.isSuccessful) {
                onDone(Result.failure(Exception("Could not get auth token")))
                return@addOnCompleteListener
            }
            val token = t.result?.token ?: ""
            Thread {
                val result = runCatching {
                    val body = payload.toString()
                    val conn = (URL("$FUNCTION_URL/$action").openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 15000
                        readTimeout = 20000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Authorization", "Bearer $token")
                    }
                    conn.outputStream.use { it.write(body.toByteArray()) }
                    val code = conn.responseCode
                    val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                        ?.bufferedReader()?.use { it.readText() } ?: ""
                    val json = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
                    if (code !in 200..299) throw Exception(json.optString("message").ifBlank { json.optString("error", "Request failed ($code)") })
                    json
                }
                main.post { onDone(result) }
            }.start()
        }
    }
}