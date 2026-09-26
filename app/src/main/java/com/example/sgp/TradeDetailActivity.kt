package com.example.sgp

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Full detail + action surface for one trade.
 * Works for both sides of the transaction; actions are gated by role + state.
 */
class TradeDetailActivity : BaseActivity() {

    private lateinit var listener: ListenerRegistration
    private var currentTrade: Trade? = null
    private var myEmail: String = ""
    private var isRequester: Boolean = false

    private val db = FirebaseFirestore.getInstance()

    // Views
    private lateinit var tvStatus: TextView
    private lateinit var btnAccept: MaterialButton
    private lateinit var btnCounter: MaterialButton
    private lateinit var btnReject: MaterialButton
    private lateinit var btnCancel: MaterialButton
    private lateinit var btnComplete: MaterialButton
    private lateinit var btnAcceptCounter: MaterialButton

    private lateinit var ivRequesterAvatar: ImageView
    private lateinit var tvRequesterName: TextView
    private lateinit var tvRequesterRating: TextView

    private lateinit var ivReceiverAvatar: ImageView
    private lateinit var tvReceiverName: TextView
    private lateinit var tvReceiverRating: TextView

    private lateinit var cardMeetingDetails: View
    private lateinit var tvMeetingPlatform: TextView
    private lateinit var tvMeetingLink: TextView

    // ═════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═════════════════════════════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trade_detail)

        myEmail = FirebaseAuth.getInstance().currentUser?.email.orEmpty()
        if (myEmail.isBlank()) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val tradeId = intent.getStringExtra(EXTRA_TRADE_ID).orEmpty()
        if (tradeId.isBlank()) {
            Toast.makeText(this, "Invalid trade", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        bindViews()
        setupToolbar()
        setupActions()

        listener = TradeManager.listenToTrade(
            tradeId = tradeId,
            onUpdate = { trade ->
                if (trade == null) {
                    Toast.makeText(
                        this@TradeDetailActivity,
                        TradeValidator.ERR_UNAVAILABLE,
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                    return@listenToTrade
                }
                currentTrade = trade
                isRequester = trade.isRequester(myEmail)
                bind(trade)
                loadParticipantProfile(trade.requesterId, isRequester = true)
                loadParticipantProfile(trade.receiverId, isRequester = false)
                bindMeetingDetails(trade)
            },
            onError = { e ->
                Toast.makeText(
                    this,
                    TradeValidator.mapException(e),
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    private fun bindViews() {
        tvStatus = findViewById(R.id.tvTradeStatus)

        btnAccept = findViewById(R.id.btnAcceptTrade)
        btnCounter = findViewById(R.id.btnCounterSchedule)
        btnReject = findViewById(R.id.btnRejectTrade)
        btnCancel = findViewById(R.id.btnCancelTrade)
        btnComplete = findViewById(R.id.btnCompleteTrade)
        btnAcceptCounter = findViewById(R.id.btnAcceptCounterSchedule)

        ivRequesterAvatar = findViewById(R.id.ivRequesterAvatar)
        tvRequesterName = findViewById(R.id.tvRequesterName)
        tvRequesterRating = findViewById(R.id.tvRequesterRating)

        ivReceiverAvatar = findViewById(R.id.ivReceiverAvatar)
        tvReceiverName = findViewById(R.id.tvReceiverName)
        tvReceiverRating = findViewById(R.id.tvReceiverRating)

        cardMeetingDetails = findViewById(R.id.cardMeetingDetails)
        tvMeetingPlatform = findViewById(R.id.tvMeetingPlatform)
        tvMeetingLink = findViewById(R.id.tvMeetingLink)
    }

    private fun setupToolbar() {
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { finish() }
    }

    private fun setupActions() {
        btnAccept.setOnClickListener { confirmAccept() }
        btnCounter.setOnClickListener { openCounterScheduleDialog() }
        btnReject.setOnClickListener { confirmReject() }
        btnCancel.setOnClickListener { confirmCancel() }
        btnComplete.setOnClickListener { confirmComplete() }
        btnAcceptCounter.setOnClickListener { confirmAcceptCounter() }
    }

    // ═════════════════════════════════════════════════════════════
    // PROFILE LOADING
    // ═════════════════════════════════════════════════════════════

    private fun loadParticipantProfile(email: String, isRequester: Boolean) {
        if (email.isBlank()) return

        db.collection("users").whereEqualTo("email", email).limit(1).get()
            .addOnSuccessListener { snap ->
                val user = snap.documents.firstOrNull()?.toObject(Users::class.java)
                    ?: return@addOnSuccessListener

                val iv = if (isRequester) ivRequesterAvatar else ivReceiverAvatar
                val tvName = if (isRequester) tvRequesterName else tvReceiverName
                val tvRating = if (isRequester) tvRequesterRating else tvReceiverRating

                tvName.text = user.name.ifBlank { email }
                tvRating.text = String.format(Locale.getDefault(), "⭐ %.1f", user.rating)

                if (user.profileImage.isNotBlank()) {
                    Glide.with(this)
                        .load(user.profileImage)
                        .circleCrop()
                        .placeholder(R.drawable.baseline_account_circle_24)
                        .error(R.drawable.baseline_account_circle_24)
                        .into(iv)
                } else {
                    iv.setImageResource(R.drawable.baseline_account_circle_24)
                }
            }
    }

    // ═════════════════════════════════════════════════════════════
    // BIND TRADE DATA
    // ═════════════════════════════════════════════════════════════

    private fun bind(trade: Trade) {
        // ── Resources ──
        findViewById<TextView>(R.id.tvRequestedTitle).text =
            trade.requestedResourceTitle.ifBlank { "—" }
        findViewById<TextView>(R.id.tvRequestedType).text =
            "${trade.requestedResourceTypeEnum.emoji} ${trade.requestedResourceTypeEnum.label}"

        findViewById<TextView>(R.id.tvOfferedTitle).text =
            trade.offeredResourceTitle.ifBlank { "—" }
        findViewById<TextView>(R.id.tvOfferedType).text =
            "${trade.offeredResourceTypeEnum.emoji} ${trade.offeredResourceTypeEnum.label}"

        // ── Message ──
        findViewById<TextView>(R.id.tvMessage).text =
            trade.message.ifBlank { "—" }

        // ── Schedules ──
        findViewById<TextView>(R.id.tvProposedSchedule).text = format(trade.proposedSchedule)

        findViewById<TextView>(R.id.tvCounterSchedule).apply {
            text = format(trade.counterSchedule)
            visibility = if (trade.counterSchedule > 0L) View.VISIBLE else View.GONE
        }
        findViewById<View>(R.id.cardCounter).visibility =
            if (trade.counterSchedule > 0L) View.VISIBLE else View.GONE

        findViewById<TextView>(R.id.tvFinalSchedule).apply {
            text = format(trade.finalizedSchedule)
            visibility = if (trade.finalizedSchedule > 0L) View.VISIBLE else View.GONE
        }
        findViewById<View>(R.id.cardFinal).visibility =
            if (trade.finalizedSchedule > 0L) View.VISIBLE else View.GONE

        // ── Status pill ──
        tvStatus.text = trade.statusEnum.name.replace('_', ' ')

        // ── Actions visibility ──
        applyActionVisibility(trade)
    }

    /**
     * The single source of truth for which action buttons are visible.
     * Never allows an invalid action for the current state/role.
     */
    private fun applyActionVisibility(trade: Trade) {
        resetActions()
        val s = trade.statusEnum

        when {
            // ─── Receiver, awaiting their decision ───
            s == TradeStatus.PENDING && !isRequester -> {
                btnAccept.visibility = View.VISIBLE
                btnCounter.visibility = View.VISIBLE
                btnReject.visibility = View.VISIBLE
            }

            // ─── Requester, receiver has countered ───
            s == TradeStatus.COUNTER_PROPOSED && isRequester -> {
                btnAcceptCounter.visibility = View.VISIBLE
                btnCancel.visibility = View.VISIBLE
            }

            // ─── Receiver, they've already countered, can still cancel ───
            s == TradeStatus.COUNTER_PROPOSED && !isRequester -> {
                btnCancel.visibility = View.VISIBLE
            }

            // ─── Requester, still pending ───
            s == TradeStatus.PENDING && isRequester -> {
                btnCancel.visibility = View.VISIBLE
            }

            // ─── Schedule finalized — either party can complete or cancel ───
            s == TradeStatus.SCHEDULE_FINALIZED || s == TradeStatus.ACCEPTED -> {
                btnComplete.visibility = View.VISIBLE
                btnCancel.visibility = View.VISIBLE
            }

            // ─── COMPLETED, CANCELLED, REJECTED, EXPIRED → no actions ───
            else -> { /* terminal — all buttons hidden by resetActions() */ }
        }
    }

    private fun resetActions() {
        listOf(btnAccept, btnCounter, btnReject, btnCancel, btnComplete, btnAcceptCounter)
            .forEach { it.visibility = View.GONE }
    }

    // ═════════════════════════════════════════════════════════════
    // MEETING DETAILS (live session only, after finalization)
    // ═════════════════════════════════════════════════════════════

    private fun bindMeetingDetails(trade: Trade) {
        val isLiveSession = trade.requestedResourceTypeEnum == TradeResourceType.LIVE_SESSION ||
                trade.offeredResourceTypeEnum == TradeResourceType.LIVE_SESSION

        if (!isLiveSession || !trade.shouldExposeMeetingDetails) {
            cardMeetingDetails.visibility = View.GONE
            return
        }

        val info = trade.liveSessionInfoTyped
        cardMeetingDetails.visibility = View.VISIBLE
        tvMeetingPlatform.text = buildString {
            append(info.platform.ifBlank { "TBD" })
            append(" • ")
            append(info.mode.replaceFirstChar { it.uppercase() })
            if (info.durationMinutes > 0) {
                append(" • ")
                append("${info.durationMinutes} min")
            }
        }
        tvMeetingLink.text = when {
            info.meetingLink.isNotBlank() -> info.meetingLink
            info.physicalLocation.isNotBlank() -> info.physicalLocation
            else -> "Details will be shared soon"
        }
    }

    // ═════════════════════════════════════════════════════════════
    // ACCEPT
    // ═════════════════════════════════════════════════════════════

    private fun confirmAccept() {
        val trade = currentTrade ?: return
        AlertDialog.Builder(this)
            .setTitle("Accept trade?")
            .setMessage("Finalize the schedule at ${format(trade.proposedSchedule)}?")
            .setPositiveButton("Accept") { _, _ -> performAccept(trade) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performAccept(trade: Trade) {
        setActionsEnabled(false)
        lifecycleScope.launch {
            val result = TradeManager.acceptTrade(trade.id, myEmail)
            setActionsEnabled(true)

            result.fold(
                onSuccess = {
                    // Schedule the reminders now that the schedule is finalized
                    TradeManager.scheduleRemindersAfterFinalize(applicationContext, trade.id)
                    Toast.makeText(
                        this@TradeDetailActivity,
                        "Trade accepted",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onFailure = { e ->
                    val friendly = TradeValidator.mapException(e)
                    android.util.Log.e("TradeDetailActivity", "Accept failed", e)
                    Toast.makeText(this@TradeDetailActivity, friendly, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    // ═════════════════════════════════════════════════════════════
    // ACCEPT COUNTER
    // ═════════════════════════════════════════════════════════════

    private fun confirmAcceptCounter() {
        val trade = currentTrade ?: return
        AlertDialog.Builder(this)
            .setTitle("Accept counter-schedule?")
            .setMessage("Finalize at ${format(trade.counterSchedule)}?")
            .setPositiveButton("Accept") { _, _ -> performAcceptCounter(trade) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performAcceptCounter(trade: Trade) {
        setActionsEnabled(false)
        lifecycleScope.launch {
            val result = TradeManager.acceptCounterSchedule(trade.id, myEmail)
            setActionsEnabled(true)

            result.fold(
                onSuccess = {
                    TradeManager.scheduleRemindersAfterFinalize(applicationContext, trade.id)
                    Toast.makeText(
                        this@TradeDetailActivity,
                        "Schedule finalized",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onFailure = { e ->
                    val friendly = TradeValidator.mapException(e)
                    android.util.Log.e("TradeDetailActivity", "Accept counter failed", e)
                    Toast.makeText(this@TradeDetailActivity, friendly, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    // ═════════════════════════════════════════════════════════════
    // REJECT
    // ═════════════════════════════════════════════════════════════

    private fun confirmReject() {
        val trade = currentTrade ?: return
        AlertDialog.Builder(this)
            .setTitle("Reject trade?")
            .setMessage("The requester will be notified.")
            .setPositiveButton("Reject") { _, _ -> performReject(trade) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performReject(trade: Trade) {
        setActionsEnabled(false)
        lifecycleScope.launch {
            val result = TradeManager.rejectTrade(trade.id, myEmail)
            setActionsEnabled(true)

            result.fold(
                onSuccess = {
                    TradeManager.cancelReminders(applicationContext, trade.id)
                    Toast.makeText(
                        this@TradeDetailActivity,
                        "Trade rejected",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onFailure = { e ->
                    Toast.makeText(
                        this@TradeDetailActivity,
                        TradeValidator.mapException(e),
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    // ═════════════════════════════════════════════════════════════
    // CANCEL
    // ═════════════════════════════════════════════════════════════

    private fun confirmCancel() {
        val trade = currentTrade ?: return
        val input = TextInputEditText(this).apply {
            hint = "Reason (optional)"
            setPadding(40, 30, 40, 30)
        }
        AlertDialog.Builder(this)
            .setTitle("Cancel trade?")
            .setView(input)
            .setPositiveButton("Cancel trade") { _, _ ->
                performCancel(trade, input.text?.toString().orEmpty())
            }
            .setNegativeButton("Keep", null)
            .show()
    }

    private fun performCancel(trade: Trade, reason: String) {
        setActionsEnabled(false)
        lifecycleScope.launch {
            val result = TradeManager.cancelTrade(trade.id, myEmail, reason)
            setActionsEnabled(true)

            result.fold(
                onSuccess = {
                    TradeManager.cancelReminders(applicationContext, trade.id)
                    Toast.makeText(
                        this@TradeDetailActivity,
                        "Trade cancelled",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onFailure = { e ->
                    Toast.makeText(
                        this@TradeDetailActivity,
                        TradeValidator.mapException(e),
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    // ═════════════════════════════════════════════════════════════
    // COMPLETE
    // ═════════════════════════════════════════════════════════════

    private fun confirmComplete() {
        val trade = currentTrade ?: return
        AlertDialog.Builder(this)
            .setTitle("Mark trade as completed?")
            .setMessage("This will finalize the trade for both users and increment your completed-trade count.")
            .setPositiveButton("Complete") { _, _ -> performComplete(trade) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performComplete(trade: Trade) {
        setActionsEnabled(false)
        lifecycleScope.launch {
            val result = TradeManager.completeTrade(trade.id, myEmail)
            setActionsEnabled(true)

            result.fold(
                onSuccess = {
                    Toast.makeText(
                        this@TradeDetailActivity,
                        "Trade completed 🎉",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onFailure = { e ->
                    Toast.makeText(
                        this@TradeDetailActivity,
                        TradeValidator.mapException(e),
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    // ═════════════════════════════════════════════════════════════
    // COUNTER SCHEDULE
    // ═════════════════════════════════════════════════════════════

    private fun openCounterScheduleDialog() {
        val trade = currentTrade ?: return

        if (trade.statusEnum != TradeStatus.PENDING) {
            Toast.makeText(this, TradeValidator.ERR_INVALID_STATE, Toast.LENGTH_SHORT).show()
            return
        }
        if (trade.counterSchedule > 0L) {
            Toast.makeText(this, TradeValidator.ERR_COUNTER_ALREADY, Toast.LENGTH_SHORT).show()
            return
        }

        val cal = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, y, m, d ->
                val picked = Calendar.getInstance().apply {
                    set(Calendar.YEAR, y)
                    set(Calendar.MONTH, m)
                    set(Calendar.DAY_OF_MONTH, d)
                }
                TimePickerDialog(
                    this,
                    { _, h, min ->
                        picked.set(Calendar.HOUR_OF_DAY, h)
                        picked.set(Calendar.MINUTE, min)
                        picked.set(Calendar.SECOND, 0)
                        picked.set(Calendar.MILLISECOND, 0)

                        if (picked.timeInMillis <= System.currentTimeMillis()) {
                            Toast.makeText(
                                this,
                                TradeValidator.ERR_PAST_TIME,
                                Toast.LENGTH_SHORT
                            ).show()
                            return@TimePickerDialog
                        }
                        showCounterMessageDialog(picked.timeInMillis)
                    },
                    picked.get(Calendar.HOUR_OF_DAY),
                    picked.get(Calendar.MINUTE),
                    false
                ).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = System.currentTimeMillis() - 1000
        }.show()
    }

    private fun showCounterMessageDialog(newSchedule: Long) {
        val trade = currentTrade ?: return

        val input = TextInputEditText(this).apply {
            hint = "Optional message to the requester"
            setText(
                "I'm not available at the proposed time. Can we meet at this time instead?"
            )
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(this)
            .setTitle("Send counter-schedule\n${format(newSchedule)}")
            .setView(input)
            .setPositiveButton("Send") { _, _ ->
                performCounterSchedule(trade, newSchedule, input.text?.toString().orEmpty())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performCounterSchedule(trade: Trade, newSchedule: Long, message: String) {
        setActionsEnabled(false)
        lifecycleScope.launch {
            val result = TradeManager.counterSchedule(
                tradeId = trade.id,
                newSchedule = newSchedule,
                optionalMessage = message,
                currentUserEmail = myEmail
            )
            setActionsEnabled(true)

            result.fold(
                onSuccess = {
                    Toast.makeText(
                        this@TradeDetailActivity,
                        "Counter-schedule sent",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onFailure = { e ->
                    Toast.makeText(
                        this@TradeDetailActivity,
                        TradeValidator.mapException(e),
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    // ═════════════════════════════════════════════════════════════
    // HELPERS
    // ═════════════════════════════════════════════════════════════

    /** Disable all action buttons during an in-flight network operation. */
    private fun setActionsEnabled(enabled: Boolean) {
        listOf(btnAccept, btnCounter, btnReject, btnCancel, btnComplete, btnAcceptCounter)
            .forEach { it.isEnabled = enabled }
    }

    private fun format(millis: Long): String =
        if (millis <= 0L) "—"
        else SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(millis))

    override fun onDestroy() {
        super.onDestroy()
        if (::listener.isInitialized) listener.remove()
    }

    // ═════════════════════════════════════════════════════════════
    // COMPANION
    // ═════════════════════════════════════════════════════════════

    companion object {
        private const val EXTRA_TRADE_ID = "tradeId"

        fun start(context: Context, tradeId: String) {
            context.startActivity(
                Intent(context, TradeDetailActivity::class.java).apply {
                    putExtra(EXTRA_TRADE_ID, tradeId)
                }
            )
        }
    }
}