package com.example.sgp

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

object GatewayConfig {
    /** Same project as your existing send-chat-notification function. */
    const val BASE_URL = "https://ghrxltlstncjcizyyqfo.supabase.co/functions/v1/chat-gateway"
}

class GatewayResponse(val httpStatus: Int, val json: JSONObject?) {
    val ok get() = httpStatus in 200..299
    val errorCode: String get() = json?.optString("error").orEmpty()
}

/** Thin HTTP client for the chat-gateway edge function. Sends the Firebase ID token; the server verifies it. */
object ChatGateway {
    private const val TAG = "ChatGateway"
    private val client by lazy { HttpClient(Android) }

    suspend fun call(route: String, payload: JSONObject = JSONObject()): GatewayResponse {
        val user = FirebaseAuth.getInstance().currentUser
            ?: return GatewayResponse(401, JSONObject().put("error", "unauthenticated"))
        val token = user.getIdToken(false).await().token
            ?: return GatewayResponse(401, JSONObject().put("error", "unauthenticated"))
        val res = client.post("${GatewayConfig.BASE_URL}/$route") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }
        val body = res.bodyAsText()
        val json = try { JSONObject(body) } catch (e: Exception) { null }
        Log.d(TAG, "$route -> ${res.status.value}") // status only; never bodies (they can contain keys/messages)
        return GatewayResponse(res.status.value, json)
    }
}

sealed class SendOutcome {
    data class Sent(val duplicate: Boolean) : SendOutcome()
    /** Message was NOT delivered: automatic detection flagged a potential violation. */
    data class Rejected(
        val eventId: String,
        val category: String,
        val warningCount: Int,
        val blocked: Boolean,
        val blockedUntilMillis: Long?
    ) : SendOutcome()
    data class Blocked(
        val warningCount: Int,
        val blockedUntilMillis: Long,
        val blockStartedMillis: Long?,
        val reason: String?
    ) : SendOutcome()
    data class Failed(val message: String, val retryable: Boolean) : SendOutcome()
}

/** Read-only mirror of the server's moderation row. Used for display; the server is the only enforcer. */
data class ModerationState(
    val warningCount: Int = 0,
    val blockedUntilMillis: Long? = null,
    val blockStartedMillis: Long? = null,
    val blockReason: String? = null
) {
    fun isBlockedAt(nowMillis: Long): Boolean = blockedUntilMillis != null && blockedUntilMillis > nowMillis
}

object ModerationClient {
    private const val TAG = "ModerationClient"

    /** serverNow - localNow, learned from server responses. Display-only clock correction. */
    @Volatile var serverClockOffsetMs: Long = 0L
        private set

    fun now(): Long = System.currentTimeMillis() + serverClockOffsetMs

    private fun JSONObject.longOrNull(k: String): Long? = if (has(k) && !isNull(k)) optLong(k) else null

    private fun learnClock(j: JSONObject) {
        j.longOrNull("serverNowMillis")?.let { serverClockOffsetMs = it - System.currentTimeMillis() }
    }

    /** Current warnings/restriction from the server, or null if it couldn't be fetched. */
    suspend fun fetchStatus(): ModerationState? = try {
        val r = ChatGateway.call("status")
        val j = r.json
        if (!r.ok || j == null) null else {
            learnClock(j)
            ModerationState(
                warningCount = j.optInt("warningCount", 0),
                blockedUntilMillis = j.longOrNull("blockedUntilMillis"),
                blockStartedMillis = j.longOrNull("blockStartedMillis"),
                blockReason = if (j.isNull("reason")) null else j.optString("reason")
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "status failed: ${e.javaClass.simpleName}")
        null
    }

    /**
     * The same [messageId] is reused on every retry. The server treats it as an idempotency key,
     * so a retry can never deliver twice or add a second warning.
     */
    suspend fun sendMessage(conversationId: String, messageId: String, text: String, clientTimestamp: Long): SendOutcome {
        val payload = JSONObject()
            .put("conversationId", conversationId)
            .put("messageId", messageId)
            .put("text", text)
            .put("clientTimestamp", clientTimestamp)
        var attempt = 0
        while (true) {
            try {
                val r = ChatGateway.call("send", payload)
                val transient = r.httpStatus == 502 || r.httpStatus == 503 || r.httpStatus == 504 || r.httpStatus == 500
                if (transient && attempt < 2) {
                    attempt++
                    delay(800L * attempt)
                    continue
                }
                if (!r.ok || r.json == null) return SendOutcome.Failed(userMessageFor(r), transient)
                return parse(r.json)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Network errors (offline, timeout, DNS ...)
                Log.w(TAG, "send failed: ${e.javaClass.simpleName}")
                if (attempt < 2) {
                    attempt++
                    delay(800L * attempt)
                    continue
                }
                return SendOutcome.Failed("Couldn't send. Check your connection and try again.", true)
            }
        }
    }

    suspend fun appeal(eventId: String, note: String, messageText: String): Boolean = callBoolean(
        "appeal", JSONObject().put("eventId", eventId).put("note", note).put("messageText", messageText)
    )

    suspend fun report(conversationId: String, messageId: String, reason: String): Boolean = callBoolean(
        "report", JSONObject().put("conversationId", conversationId).put("messageId", messageId).put("reason", reason)
    )

    private suspend fun callBoolean(route: String, payload: JSONObject): Boolean = try {
        ChatGateway.call(route, payload).ok
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "$route failed: ${e.javaClass.simpleName}")
        false
    }

    private fun parse(j: JSONObject): SendOutcome {
        learnClock(j)
        return when (j.optString("status")) {
            "sent" -> SendOutcome.Sent(j.optBoolean("duplicate", false))
            "rejected" -> SendOutcome.Rejected(
                eventId = j.optString("eventId"),
                category = j.optString("category"),
                warningCount = j.optInt("warningCount", 0),
                blocked = j.optBoolean("blocked", false),
                blockedUntilMillis = j.longOrNull("blockedUntilMillis")
            )
            "blocked" -> SendOutcome.Blocked(
                warningCount = j.optInt("warningCount", 0),
                blockedUntilMillis = j.longOrNull("blockedUntilMillis") ?: 0L,
                blockStartedMillis = j.longOrNull("blockStartedMillis"),
                reason = if (j.isNull("reason")) null else j.optString("reason")
            )
            else -> SendOutcome.Failed("Unexpected server response.", false)
        }
    }

    private fun userMessageFor(r: GatewayResponse): String = when (r.httpStatus) {
        401 -> "Please sign in again."
        403 -> "You don't have access to this conversation."
        400 -> r.json?.optString("message")?.takeIf { it.isNotBlank() } ?: "That message couldn't be sent."
        412 -> "This chat isn't ready yet. Please try again."
        else -> "Couldn't send. Check your connection and try again."
    }
}