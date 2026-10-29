package com.example.sgp

import android.util.Log
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

data class RecoveryRequest(
    val id: String,
    val requesterEmail: String,
    val otherEmail: String,
    val conversationId: String,
    val description: String,
    val screenshotUrl: String?,
    val consentAtMillis: Long?,
    val status: String,            // pending | active | ended | rejected
    val sessionExpiresMillis: Long?,
    val sessionEndedMillis: Long?,
    val endedBy: String?,
    val createdMillis: Long
) {
    val isOpen get() = status == "pending" || status == "active"
}

data class RecoveryMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long,
    val deleted: Boolean,
    val note: String?              // "deleted" | "undecryptable" | null
)

class RecoveryResult<T>(val value: T?, val error: String = "") {
    val ok get() = value != null
}

object RecoveryClient {
    private const val TAG = "RecoveryClient"

    private fun JSONObject.str(k: String): String? = if (has(k) && !isNull(k)) optString(k) else null
    private fun JSONObject.lng(k: String): Long? = if (has(k) && !isNull(k)) optLong(k) else null

    private fun parse(o: JSONObject) = RecoveryRequest(
        id = o.optString("id"),
        requesterEmail = o.optString("requesterEmail"),
        otherEmail = o.optString("otherEmail"),
        conversationId = o.optString("conversationId"),
        description = o.optString("description"),
        screenshotUrl = o.str("screenshotUrl")?.takeIf { it.isNotBlank() },
        consentAtMillis = o.lng("consentAtMillis"),
        status = o.optString("status"),
        sessionExpiresMillis = o.lng("sessionExpiresMillis"),
        sessionEndedMillis = o.lng("sessionEndedMillis"),
        endedBy = o.str("endedBy"),
        createdMillis = o.optLong("createdMillis")
    )

    private fun parseList(arr: JSONArray?): List<RecoveryRequest> {
        val out = ArrayList<RecoveryRequest>()
        if (arr != null) for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { out.add(parse(it)) }
        return out
    }

    private suspend fun post(route: String, payload: JSONObject = JSONObject()): GatewayResponse? = try {
        ChatGateway.call(route, payload)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "$route failed: ${e.javaClass.simpleName}")
        null
    }

    private fun <T> result(r: GatewayResponse?, onOk: (JSONObject) -> T): RecoveryResult<T> {
        if (r == null) return RecoveryResult(null, "network")
        val j = r.json
        if (!r.ok || j == null) return RecoveryResult(null, r.errorCode.ifBlank { "failed" })
        return RecoveryResult(onOk(j))
    }

    fun errorText(code: String): String = when (code) {
        "network" -> "Check your connection and try again."
        "already-open" -> "You already have an open request for this chat."
        "consent-required" -> "Consent is required."
        "access-ended" -> "Access to this chat has ended."
        "in-use" -> "Another admin has already opened this request."
        "permission-denied" -> "You don't have permission."
        else -> "Something went wrong. Please try again."
    }

    // ---------------- user ----------------
    suspend fun submit(conversationId: String, description: String, screenshotUrl: String?, consent: Boolean) =
        result(
            post(
                "recovery/submit",
                JSONObject().put("conversationId", conversationId).put("description", description)
                    .put("screenshotUrl", screenshotUrl ?: JSONObject.NULL).put("consent", consent)
            )
        ) { it.optString("id") }

    suspend fun mine(conversationId: String) =
        result(post("recovery/mine", JSONObject().put("conversationId", conversationId))) {
            parseList(it.optJSONArray("requests"))
        }

    suspend fun revoke(requestId: String): Boolean =
        post("recovery/revoke", JSONObject().put("requestId", requestId))?.ok == true

    // ---------------- admin ----------------
    suspend fun adminList(closed: Boolean) =
        result(post("recovery/list", JSONObject().put("closed", closed))) { parseList(it.optJSONArray("requests")) }

    suspend fun adminGet(requestId: String) =
        result(post("recovery/get", JSONObject().put("requestId", requestId))) { parse(it.getJSONObject("request")) }

    suspend fun adminStart(requestId: String) =
        result(post("recovery/start", JSONObject().put("requestId", requestId))) { parse(it.getJSONObject("request")) }

    suspend fun adminEnd(requestId: String, note: String, reject: Boolean = false): Boolean =
        post("recovery/end", JSONObject().put("requestId", requestId).put("note", note).put("reject", reject))?.ok == true

    /** Loads the WHOLE conversation (start to end) by following nextPageToken. */
    suspend fun adminMessages(requestId: String): RecoveryResult<List<RecoveryMessage>> {
        val all = ArrayList<RecoveryMessage>()
        var token: String? = null
        var pages = 0
        do {
            val payload = JSONObject().put("requestId", requestId)
            if (token != null) payload.put("pageToken", token)
            val r = post("recovery/messages", payload) ?: return RecoveryResult(null, "network")
            val j = r.json
            if (!r.ok || j == null) return RecoveryResult(null, r.errorCode.ifBlank { "failed" })
            val arr = j.optJSONArray("messages") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                all.add(
                    RecoveryMessage(
                        id = o.optString("id"),
                        senderId = o.optString("senderId"),
                        senderName = o.optString("senderName"),
                        text = o.optString("text"),
                        timestamp = o.optLong("timestamp"),
                        deleted = o.optBoolean("deleted", false),
                        note = o.str("note")
                    )
                )
            }
            token = j.str("nextPageToken")?.takeIf { it.isNotBlank() }
            pages++
        } while (token != null && pages < 100)
        return RecoveryResult(all.sortedBy { it.timestamp })
    }
}