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
import org.json.JSONArray
import org.json.JSONObject

object GatewayConfig {
    const val BASE_URL = "https://ghrxltlstncjcizyyqfo.supabase.co/functions/v1/chat-gateway"
}

class GatewayResponse(val httpStatus: Int, val json: JSONObject?) {
    val ok get() = httpStatus in 200..299
    val errorCode: String get() = json?.optString("error").orEmpty()
}

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
        Log.d(TAG, "$route -> ${res.status.value}")
        return GatewayResponse(res.status.value, json)
    }
}

sealed class SendOutcome {
    data class Sent(val duplicate: Boolean) : SendOutcome()

    data class Rejected(
        val eventId: String,
        val category: String,
        val warningCount: Int,
        val stageWarnings: Int,
        val banLevel: Int,
        val isPermanent: Boolean,
        val blocked: Boolean,
        val blockedUntilMillis: Long?
    ) : SendOutcome()

    data class Blocked(
        val warningCount: Int,
        val stageWarnings: Int,
        val banLevel: Int,
        val isPermanent: Boolean,
        val blockedUntilMillis: Long?,
        val blockStartedMillis: Long?,
        val reason: String?
    ) : SendOutcome()

    data class Failed(val message: String, val retryable: Boolean) : SendOutcome()
}

/** Read-only mirror of the server's moderation row. The server is the only enforcer. */
data class ModerationState(
    val warningCount: Int = 0,
    val stageWarnings: Int = 0,
    val banLevel: Int = 0,
    val isPermanent: Boolean = false,
    val blockedUntilMillis: Long? = null,
    val blockStartedMillis: Long? = null,
    val blockReason: String? = null
) {
    fun isBlockedAt(nowMillis: Long): Boolean =
        isPermanent || (blockedUntilMillis != null && blockedUntilMillis > nowMillis)

    /** UI stage number 1..3 (banLevel+1 clamped). */
    fun stage(): Int = (banLevel + 1).coerceIn(1, 3)
}

/** One entry in the admin "Banned users" list. */
data class BannedUser(
    val uid: String,
    val username: String,
    val email: String,
    val warningCount: Int,
    val stageWarnings: Int,
    val banLevel: Int,
    val isPermanent: Boolean,
    val blockedUntilMillis: Long?,
    val blockStartedMillis: Long?,
    val reason: String?
) {
    fun stage(): Int = (banLevel + 1).coerceIn(1, 3)
}

object ModerationClient {
    private const val TAG = "ModerationClient"

    @Volatile var serverClockOffsetMs: Long = 0L
        private set

    fun now(): Long = System.currentTimeMillis() + serverClockOffsetMs

    private fun JSONObject.longOrNull(k: String): Long? = if (has(k) && !isNull(k)) optLong(k) else null

    private fun learnClock(j: JSONObject) {
        j.longOrNull("serverNowMillis")?.let { serverClockOffsetMs = it - System.currentTimeMillis() }
    }

    suspend fun fetchStatus(): ModerationState? = try {
        val r = ChatGateway.call("status")
        val j = r.json
        if (!r.ok || j == null) null else {
            learnClock(j)
            ModerationState(
                warningCount = j.optInt("warningCount", 0),
                stageWarnings = j.optInt("stageWarnings", 0),
                banLevel = j.optInt("banLevel", 0),
                isPermanent = j.optBoolean("isPermanent", false),
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

    // ---------------- Admin API ----------------

    suspend fun fetchBannedUsers(includeTemporary: Boolean): List<BannedUser>? = try {
        val r = ChatGateway.call("admin/list-banned", JSONObject().put("includeTemporary", includeTemporary))
        val j = r.json
        if (!r.ok || j == null) null else {
            learnClock(j)
            val arr: JSONArray = j.optJSONArray("users") ?: JSONArray()
            val out = ArrayList<BannedUser>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(
                    BannedUser(
                        uid = o.optString("uid"),
                        username = o.optString("username"),
                        email = o.optString("email"),
                        warningCount = o.optInt("warningCount", 0),
                        stageWarnings = o.optInt("stageWarnings", 0),
                        banLevel = o.optInt("banLevel", 0),
                        isPermanent = o.optBoolean("isPermanent", false),
                        blockedUntilMillis = o.longOrNull("blockedUntilMillis"),
                        blockStartedMillis = o.longOrNull("blockStartedMillis"),
                        reason = if (o.isNull("reason")) null else o.optString("reason")
                    )
                )
            }
            out
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "admin/list-banned failed: ${e.javaClass.simpleName}")
        null
    }

    /** @return true on success (including idempotent repeat). */
    suspend fun unbanUser(uid: String, reason: String): Boolean = try {
        val r = ChatGateway.call("admin/unban", JSONObject().put("uid", uid).put("reason", reason))
        r.ok
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "admin/unban failed: ${e.javaClass.simpleName}")
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
                stageWarnings = j.optInt("stageWarnings", 0),
                banLevel = j.optInt("banLevel", 0),
                isPermanent = j.optBoolean("isPermanent", false),
                blocked = j.optBoolean("blocked", false),
                blockedUntilMillis = j.longOrNull("blockedUntilMillis")
            )
            "blocked" -> SendOutcome.Blocked(
                warningCount = j.optInt("warningCount", 0),
                stageWarnings = j.optInt("stageWarnings", 0),
                banLevel = j.optInt("banLevel", 0),
                isPermanent = j.optBoolean("isPermanent", false),
                blockedUntilMillis = j.longOrNull("blockedUntilMillis"),
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