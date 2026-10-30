package com.example.sgp

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

data class BanInfo(
    val level: Int, val permanent: Boolean, val startedMillis: Long?,
    val expiresMillis: Long?, val endedMillis: Long?, val endedReason: String?
)

data class AppealInfo(
    val id: String, val status: String, val note: String, val decisionReason: String?,
    val submittedMillis: Long?, val decidedMillis: Long?, val notifyStatus: String?
)

data class WarningRecord(
    val eventId: String, val createdMillis: Long, val eventStatus: String,
    val category: String, val ruleId: String, val matchedText: String?,
    val spans: List<Pair<Int, Int>>, val originalMessage: String?, val evidenceAvailable: Boolean,
    val warningNumber: Int?, val stage: Int?, val stageWarningNo: Int?,
    val triggeredBan: Boolean, val ban: BanInfo?, val appeal: AppealInfo?
)

data class AppealListItem(
    val id: String, val eventId: String, val uid: String, val username: String, val email: String,
    val note: String, val status: String, val submittedMillis: Long?, val decisionReason: String?,
    val decidedMillis: Long?, val notifyStatus: String?, val category: String, val evidenceAvailable: Boolean
)

data class AppealDetail(
    val appeal: AppealInfo?, val eventId: String?, val uid: String, val username: String, val email: String,
    val moderation: ModerationState, val history: List<WarningRecord>
)

data class UserAppeal(
    val id: String, val status: String, val category: String, val note: String,
    val submittedMillis: Long?, val decisionReason: String?, val decidedMillis: Long?
)

data class UserAppealsResult(val appeals: List<UserAppeal>, val moderation: ModerationState)

object AppealClient {
    private const val TAG = "AppealClient"

    private fun JSONObject.str(k: String): String? = if (has(k) && !isNull(k)) optString(k) else null
    private fun JSONObject.lng(k: String): Long? = if (has(k) && !isNull(k)) optLong(k) else null
    private fun JSONObject.intOrNull(k: String): Int? = if (has(k) && !isNull(k)) optInt(k) else null

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
        "already-decided" -> "This appeal was already decided differently."
        "event-already-resolved" -> "This warning was already resolved elsewhere."
        "reason-required" -> "Enter a decision reason."
        "permission-denied" -> "You don't have permission."
        else -> "Error: $code"
    }

    fun categoryLabel(category: String): String = ModerationMessages.categoryLabel(category)

    // ---------------- parsing ----------------
    private fun parseAppeal(o: JSONObject) = AppealInfo(
        id = o.optString("id"), status = o.optString("status"), note = o.optString("note"),
        decisionReason = o.str("decisionReason"), submittedMillis = o.lng("submittedMillis"),
        decidedMillis = o.lng("decidedMillis"), notifyStatus = o.str("notifyStatus")
    )

    private fun parseWarning(o: JSONObject): WarningRecord {
        val spans = ArrayList<Pair<Int, Int>>()
        val arr = o.optJSONArray("spans") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val p = arr.optJSONArray(i) ?: continue
            if (p.length() == 2) spans.add(p.optInt(0) to p.optInt(1))
        }
        val ban = o.optJSONObject("ban")?.let {
            BanInfo(
                it.optInt("level"), it.optBoolean("permanent"), it.lng("startedMillis"),
                it.lng("expiresMillis"), it.lng("endedMillis"), it.str("endedReason")
            )
        }
        return WarningRecord(
            eventId = o.optString("eventId"), createdMillis = o.optLong("createdMillis"),
            eventStatus = o.optString("eventStatus"), category = o.optString("category"),
            ruleId = o.optString("ruleId"), matchedText = o.str("matchedText"), spans = spans,
            originalMessage = o.str("originalMessage"), evidenceAvailable = o.optBoolean("evidenceAvailable", false),
            warningNumber = o.intOrNull("warningNumber"), stage = o.intOrNull("stage"),
            stageWarningNo = o.intOrNull("stageWarningNo"), triggeredBan = o.optBoolean("triggeredBan", false),
            ban = ban, appeal = o.optJSONObject("appeal")?.let { parseAppeal(it) }
        )
    }

    private fun parseModeration(j: JSONObject?) = ModerationState(
        warningCount = j?.optInt("warningCount", 0) ?: 0,
        stageWarnings = j?.optInt("stageWarnings", 0) ?: 0,
        banLevel = j?.optInt("banLevel", 0) ?: 0,
        isPermanent = j?.optBoolean("isPermanent", false) ?: false,
        blockedUntilMillis = j?.lng("blockedUntilMillis"),
        blockStartedMillis = j?.lng("blockStartedMillis"),
        blockReason = j?.str("reason")
    )

    private fun parseDetail(j: JSONObject): AppealDetail {
        val u = j.optJSONObject("user")
        val hist = ArrayList<WarningRecord>()
        val arr = j.optJSONArray("history") ?: JSONArray()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { hist.add(parseWarning(it)) }
        val ap = j.optJSONObject("appeal")
        return AppealDetail(
            appeal = ap?.let { parseAppeal(it) }, eventId = ap?.str("eventId"),
            uid = u?.optString("uid").orEmpty(), username = u?.optString("username").orEmpty(),
            email = u?.optString("email").orEmpty(),
            moderation = parseModeration(j.optJSONObject("moderation")), history = hist
        )
    }

    // ---------------- admin ----------------
    suspend fun adminList(decided: Boolean) =
        result(post("admin/appeals", JSONObject().put("decided", decided))) { j ->
            val out = ArrayList<AppealListItem>()
            val arr = j.optJSONArray("appeals") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(
                    AppealListItem(
                        id = o.optString("id"), eventId = o.optString("eventId"), uid = o.optString("uid"),
                        username = o.optString("username"), email = o.optString("email"), note = o.optString("note"),
                        status = o.optString("status"), submittedMillis = o.lng("submittedMillis"),
                        decisionReason = o.str("decisionReason"), decidedMillis = o.lng("decidedMillis"),
                        notifyStatus = o.str("notifyStatus"), category = o.optString("category"),
                        evidenceAvailable = o.optBoolean("evidenceAvailable", false)
                    )
                )
            }
            out as List<AppealListItem>
        }

    suspend fun adminDetail(appealId: String) =
        result(post("admin/appeal-detail", JSONObject().put("appealId", appealId))) { parseDetail(it) }

    suspend fun adminUserWarnings(uid: String) =
        result(post("admin/user-warnings", JSONObject().put("uid", uid))) { parseDetail(it) }

    /** @return notifyStatus ("sent", "failed", "skipped", ...) on success. */
    suspend fun adminDecide(appealId: String, approve: Boolean, reason: String) =
        result(
            post(
                "admin/appeal-decide",
                JSONObject().put("appealId", appealId).put("decision", if (approve) "approve" else "reject").put("reason", reason)
            )
        ) { it.optString("notifyStatus", "") }

    suspend fun adminResend(appealId: String) =
        result(post("admin/appeal-resend", JSONObject().put("appealId", appealId))) { it.optString("notifyStatus", "") }

    // ---------------- user ----------------
    suspend fun userMine() = result(post("appeal/mine")) { j ->
        val out = ArrayList<UserAppeal>()
        val arr = j.optJSONArray("appeals") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                UserAppeal(
                    id = o.optString("id"), status = o.optString("status"), category = o.optString("category"),
                    note = o.optString("note"), submittedMillis = o.lng("submittedMillis"),
                    decisionReason = o.str("decisionReason"), decidedMillis = o.lng("decidedMillis")
                )
            )
        }
        UserAppealsResult(out, parseModeration(j.optJSONObject("moderation")))
    }

    // ---------------- helpers ----------------
    fun highlight(text: String, spans: List<Pair<Int, Int>>): CharSequence {
        val s = SpannableString(text)
        for ((a, b) in spans) {
            if (a in 0 until b && b <= text.length) {
                s.setSpan(BackgroundColorSpan(Color.parseColor("#F6C9C4")), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                s.setSpan(ForegroundColorSpan(Color.parseColor("#9B1C1C")), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                s.setSpan(StyleSpan(Typeface.BOLD), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return s
    }
}