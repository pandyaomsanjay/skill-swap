package com.example.sgp

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
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
 *
 * Theming follows the same pattern as ExploreActivity / CreateTradeActivity /
 * MyTradesActivity: a boolean flag in SharedPreferences ("dark_mode"),
 * applied by hand in applyTheme(), re-checked in onResume().
 *
 * Points / Material upgrade:
 *   • Original offer card shows points or material
 *   • Counter offer card appears once the receiver has countered
 *   • Final agreement card appears once both sides have agreed
 *   • Counter dialog supports either points or material (plus optional
 *     schedule change), all via TradeManager.counterTrade()
 *
 * FIX (this version):
 *   The material picker used inside the counter dialog is now a proper
 *   class-level ActivityResultLauncher, registered during activity
 *   construction. Previously it was registered inside a click handler,
 *   which throws IllegalStateException at runtime. A nullable callback
 *   field carries the result back into the dialog that launched it.
 */
class TradeDetailActivity : BaseActivity() {

    // ─────────────────────────────────────────────────────────────
    // CLASS-LEVEL LAUNCHERS
    // ─────────────────────────────────────────────────────────────
    /**
     * Material picker for the counter dialog. The callback is only
     * invoked if a dialog is currently waiting for the result.
     */
    private var pendingCounterMaterialCallback: ((id: String, type: String, title: String) -> Unit)? = null

    private val counterMaterialPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val cb = pendingCounterMaterialCallback
        pendingCounterMaterialCallback = null
        if (cb != null && result.resultCode == RESULT_OK && result.data != null) {
            val data = result.data!!
            cb(
                data.getStringExtra(SelectResourceActivity.EXTRA_RESOURCE_ID).orEmpty(),
                data.getStringExtra(SelectResourceActivity.EXTRA_RESOURCE_TYPE).orEmpty(),
                data.getStringExtra(SelectResourceActivity.EXTRA_RESOURCE_TITLE).orEmpty()
            )
        }
    }

    // ─────────────────────────────────────────────────────────────
    // STATE
    // ─────────────────────────────────────────────────────────────
    private lateinit var listener: ListenerRegistration
    private var currentTrade: Trade? = null
    private var myEmail: String = ""
    private var isRequester: Boolean = false

    private val db = FirebaseFirestore.getInstance()

    private lateinit var sharedPreferences: SharedPreferences
    private var isDarkModeEnabled = false

    // Header
    private lateinit var rootLayout: View
    private lateinit var headerBar: View
    private lateinit var btnBack: MaterialCardView
    private lateinit var ivBackIcon: ImageView
    private lateinit var tvTradeTitle: TextView

    // Cards
    private lateinit var cardStatusPill: MaterialCardView
    private lateinit var cardParticipants: MaterialCardView
    private lateinit var cardResources: MaterialCardView
    private lateinit var cardOriginalOffer: MaterialCardView
    private lateinit var cardCounterOffer: MaterialCardView
    private lateinit var cardFinalAgreement: MaterialCardView
    private lateinit var cardScheduleCard: MaterialCardView
    private lateinit var cardMessageCard: MaterialCardView
    private lateinit var dividerResources: View
    private lateinit var tvSwapArrow: TextView

    // Section labels
    private lateinit var tvLabelOffer: TextView
    private lateinit var tvLabelReceive: TextView
    private lateinit var tvLabelSchedule: TextView
    private lateinit var tvLabelMeeting: TextView
    private lateinit var tvLabelMessage: TextView
    private lateinit var tvOriginalOfferLabel: TextView
    private lateinit var tvOriginalOfferValue: TextView
    private lateinit var tvCounterOfferTitle: TextView
    private lateinit var tvCounterOfferValue: TextView
    private lateinit var tvCounterOfferMessage: TextView
    private lateinit var tvFinalAgreementValue: TextView

    // Action buttons
    private lateinit var tvStatus: TextView
    private lateinit var btnAccept: MaterialButton
    private lateinit var btnCounter: MaterialButton
    private lateinit var btnReject: MaterialButton
    private lateinit var btnCancel: MaterialButton
    private lateinit var btnComplete: MaterialButton
    private lateinit var btnAcceptCounter: MaterialButton

    // Participants
    private lateinit var ivRequesterAvatar: ImageView
    private lateinit var tvRequesterName: TextView
    private lateinit var tvRequesterRating: TextView
    private lateinit var ivReceiverAvatar: ImageView
    private lateinit var tvReceiverName: TextView
    private lateinit var tvReceiverRating: TextView

    // Meeting details
    private lateinit var cardMeetingDetails: View
    private lateinit var tvMeetingPlatform: TextView
    private lateinit var tvMeetingLink: TextView

    // ─────────────────────────────────────────────────────────────
    // PALETTE
    // ─────────────────────────────────────────────────────────────
    private val lightScreenBg = Color.parseColor("#EAF1F5")
    private val lightCardBg = Color.parseColor("#FFFFFF")
    private val lightCardStroke = Color.parseColor("#D2C1B6")
    private val lightTextPrimary = Color.parseColor("#1B3C53")
    private val lightTextSecondary = Color.parseColor("#456882")
    private val lightHeaderBg = Color.parseColor("#1B3C53")
    private val lightBackButtonBg = Color.parseColor("#456882")
    private val lightStatusPillBg = Color.parseColor("#F9F3EF")
    private val lightDivider = Color.parseColor("#EAF1F5")

    private val darkScreenBg = Color.parseColor("#0F1B24")
    private val darkCardBg = Color.parseColor("#16232E")
    private val darkCardStroke = Color.parseColor("#2A3B48")
    private val darkTextPrimary = Color.parseColor("#F5F7F8")
    private val darkTextSecondary = Color.parseColor("#8FA6B8")
    private val darkHeaderBg = Color.parseColor("#0B1620")
    private val darkBackButtonBg = Color.parseColor("#22323D")
    private val darkStatusPillBg = Color.parseColor("#22323D")
    private val darkDivider = Color.parseColor("#2A3B48")

    private val headerTitleColor = Color.parseColor("#F9F3EF")

    private val destructive = Color.parseColor("#DC2626")
    private val acceptGreen = Color.parseColor("#2E7D32")
    private val primaryBlue = Color.parseColor("#456882")
    private val counterPurple = Color.parseColor("#7C3AED")

    // ═════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═════════════════════════════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trade_detail)

        sharedPreferences = getSharedPreferences(
            SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE
        )
        isDarkModeEnabled = sharedPreferences.getBoolean(
            SettingsActivity.KEY_DARK_MODE, false
        )

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
        setupHeader()
        setupActions()
        applyTheme(isDarkModeEnabled)

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

    override fun onResume() {
        super.onResume()
        val savedDark = sharedPreferences.getBoolean(
            SettingsActivity.KEY_DARK_MODE, false
        )
        if (savedDark != isDarkModeEnabled) applyTheme(savedDark)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyTheme(isDarkModeEnabled)
    }

    // ─────────────────────────────────────────────────────────────
    // BIND VIEWS
    // ─────────────────────────────────────────────────────────────
    private fun bindViews() {
        rootLayout = findViewById(R.id.rootLayout)
        headerBar = findViewById(R.id.headerBar)
        btnBack = findViewById(R.id.btnBack)
        ivBackIcon = findViewById(R.id.ivBackIcon)
        tvTradeTitle = findViewById(R.id.tvTradeTitle)

        cardStatusPill = findViewById(R.id.cardStatusPill)
        cardParticipants = findViewById(R.id.cardParticipants)
        cardResources = findViewById(R.id.cardResources)
        cardOriginalOffer = findViewById(R.id.cardOriginalOffer)
        cardCounterOffer = findViewById(R.id.cardCounterOffer)
        cardFinalAgreement = findViewById(R.id.cardFinalAgreement)
        cardScheduleCard = findViewById(R.id.cardSchedule)
        cardMessageCard = findViewById(R.id.cardMessage)
        dividerResources = findViewById(R.id.dividerResources)
        tvSwapArrow = findViewById(R.id.tvSwapArrow)

        tvLabelOffer = findViewById(R.id.tvLabelOffer)
        tvLabelReceive = findViewById(R.id.tvLabelReceive)
        tvLabelSchedule = findViewById(R.id.tvLabelSchedule)
        tvLabelMeeting = findViewById(R.id.tvLabelMeeting)
        tvLabelMessage = findViewById(R.id.tvLabelMessage)
        tvOriginalOfferLabel = findViewById(R.id.tvOriginalOfferLabel)
        tvOriginalOfferValue = findViewById(R.id.tvOriginalOfferValue)
        tvCounterOfferTitle = findViewById(R.id.tvCounterOfferTitle)
        tvCounterOfferValue = findViewById(R.id.tvCounterOfferValue)
        tvCounterOfferMessage = findViewById(R.id.tvCounterOfferMessage)
        tvFinalAgreementValue = findViewById(R.id.tvFinalAgreementValue)

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

    private fun setupHeader() {
        btnBack.setOnClickListener { finish() }
    }

    private fun setupActions() {
        btnAccept.setOnClickListener { confirmAccept() }
        btnCounter.setOnClickListener { openCounterTradeDialog() }
        btnReject.setOnClickListener { confirmReject() }
        btnCancel.setOnClickListener { confirmCancel() }
        btnComplete.setOnClickListener { confirmComplete() }
        btnAcceptCounter.setOnClickListener { confirmAcceptCounter() }
    }

    // ─────────────────────────────────────────────────────────────
    // THEME
    // ─────────────────────────────────────────────────────────────
    private fun applyTheme(isDark: Boolean) {
        isDarkModeEnabled = isDark

        val screenBg = if (isDark) darkScreenBg else lightScreenBg
        val cardBg = if (isDark) darkCardBg else lightCardBg
        val cardStroke = if (isDark) darkCardStroke else lightCardStroke
        val textPrimary = if (isDark) darkTextPrimary else lightTextPrimary
        val textSecondary = if (isDark) darkTextSecondary else lightTextSecondary
        val headerBg = if (isDark) darkHeaderBg else lightHeaderBg
        val backButtonBg = if (isDark) darkBackButtonBg else lightBackButtonBg
        val statusPillBg = if (isDark) darkStatusPillBg else lightStatusPillBg
        val divider = if (isDark) darkDivider else lightDivider

        rootLayout.setBackgroundColor(screenBg)
        headerBar.setBackgroundColor(headerBg)
        btnBack.setCardBackgroundColor(backButtonBg)
        ivBackIcon.imageTintList = ColorStateList.valueOf(headerTitleColor)
        tvTradeTitle.setTextColor(headerTitleColor)

        cardStatusPill.setCardBackgroundColor(statusPillBg)
        tvStatus.setTextColor(textPrimary)

        listOf(
            cardParticipants, cardResources, cardOriginalOffer,
            cardCounterOffer, cardScheduleCard, cardMessageCard
        ).forEach {
            it.setCardBackgroundColor(cardBg)
            it.strokeColor = cardStroke
        }

        // Final agreement keeps its green tint in light mode, softened for dark
        if (isDark) {
            cardFinalAgreement.setCardBackgroundColor(Color.parseColor("#163B34"))
            cardFinalAgreement.strokeColor = Color.parseColor("#2D8577")
            tvFinalAgreementValue.setTextColor(textPrimary)
        } else {
            cardFinalAgreement.setCardBackgroundColor(Color.parseColor("#E4F1E9"))
            cardFinalAgreement.strokeColor = Color.parseColor("#34C77B")
            tvFinalAgreementValue.setTextColor(lightTextPrimary)
        }

        (cardMeetingDetails as? MaterialCardView)?.apply {
            setCardBackgroundColor(cardBg)
            strokeColor = cardStroke
        }

        tvSwapArrow.setTextColor(textPrimary)
        tvRequesterName.setTextColor(textPrimary)
        tvRequesterRating.setTextColor(textSecondary)
        tvReceiverName.setTextColor(textPrimary)
        tvReceiverRating.setTextColor(textSecondary)

        tvLabelOffer.setTextColor(textSecondary)
        tvLabelReceive.setTextColor(textSecondary)
        dividerResources.setBackgroundColor(divider)
        findViewById<TextView>(R.id.tvOfferedTitle).setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvOfferedType).setTextColor(textSecondary)
        findViewById<TextView>(R.id.tvRequestedTitle).setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvRequestedType).setTextColor(textSecondary)

        tvOriginalOfferLabel.setTextColor(textSecondary)
        tvOriginalOfferValue.setTextColor(textPrimary)

        tvCounterOfferValue.setTextColor(textPrimary)
        tvCounterOfferMessage.setTextColor(textSecondary)

        tvLabelSchedule.setTextColor(textSecondary)
        findViewById<TextView>(R.id.tvProposedSchedule).setTextColor(textPrimary)

        tvLabelMeeting.setTextColor(textSecondary)
        tvMeetingPlatform.setTextColor(textPrimary)
        tvMeetingLink.setTextColor(textSecondary)

        tvLabelMessage.setTextColor(textSecondary)
        findViewById<TextView>(R.id.tvMessage).setTextColor(textPrimary)

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark
        window.statusBarColor = headerBg
        window.navigationBarColor = screenBg
    }

    // ─────────────────────────────────────────────────────────────
    // PROFILE LOADING
    // ─────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────
    // BIND TRADE DATA
    // ─────────────────────────────────────────────────────────────
    private fun bind(trade: Trade) {

        // Resources card — for POINTS trades, show points in the "offered" slot
        if (trade.isPointsExchange) {
            findViewById<TextView>(R.id.tvOfferedTitle).text =
                "${trade.offeredPoints} points"
            findViewById<TextView>(R.id.tvOfferedType).text = "💰 Points"
        } else {
            findViewById<TextView>(R.id.tvOfferedTitle).text =
                trade.offeredResourceTitle.ifBlank { "—" }
            findViewById<TextView>(R.id.tvOfferedType).text =
                "${trade.offeredResourceTypeEnum.emoji} ${trade.offeredResourceTypeEnum.label}"
        }

        findViewById<TextView>(R.id.tvRequestedTitle).text =
            trade.requestedResourceTitle.ifBlank { "—" }
        findViewById<TextView>(R.id.tvRequestedType).text =
            "${trade.requestedResourceTypeEnum.emoji} ${trade.requestedResourceTypeEnum.label}"

        findViewById<TextView>(R.id.tvMessage).text =
            trade.message.ifBlank { "—" }

        // Schedules
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

        // Original offer card
        tvOriginalOfferLabel.text =
            if (trade.isPointsExchange) "Points Offered" else "Resource Offered"
        tvOriginalOfferValue.text = trade.offerDisplay()

        // Counter offer card
        if (trade.hasCounterOffer) {
            cardCounterOffer.visibility = View.VISIBLE
            tvCounterOfferTitle.text = if (trade.isCounterPoints)
                "Counter: Points Requested from the other side"
            else
                "Counter: Material Requested from the other side"
            tvCounterOfferValue.text = trade.counterDisplay()
            tvCounterOfferMessage.text = trade.counterMessage.ifBlank { "—" }
        } else {
            cardCounterOffer.visibility = View.GONE
        }

        // Final agreement card
        if (trade.isFinalized) {
            cardFinalAgreement.visibility = View.VISIBLE
            tvFinalAgreementValue.text = trade.finalDisplay()
        } else {
            cardFinalAgreement.visibility = View.GONE
        }

        // Status pill
        tvStatus.text = trade.statusEnum.name.replace('_', ' ')

        // Actions
        applyActionVisibility(trade)
    }

    private fun applyActionVisibility(trade: Trade) {
        resetActions()
        val s = trade.statusEnum

        when {
            s == TradeStatus.PENDING && !isRequester -> {
                btnAccept.visibility = View.VISIBLE
                btnCounter.visibility = View.VISIBLE
                btnReject.visibility = View.VISIBLE
            }
            s == TradeStatus.COUNTER_PROPOSED && isRequester -> {
                btnAcceptCounter.visibility = View.VISIBLE
                btnCancel.visibility = View.VISIBLE
            }
            s == TradeStatus.COUNTER_PROPOSED && !isRequester -> {
                btnCancel.visibility = View.VISIBLE
            }
            s == TradeStatus.PENDING && isRequester -> {
                btnCancel.visibility = View.VISIBLE
            }
            s == TradeStatus.SCHEDULE_FINALIZED || s == TradeStatus.ACCEPTED -> {
                btnComplete.visibility = View.VISIBLE
                btnCancel.visibility = View.VISIBLE
            }
            else -> { /* terminal — all buttons hidden */ }
        }
    }

    private fun resetActions() {
        listOf(btnAccept, btnCounter, btnReject, btnCancel, btnComplete, btnAcceptCounter)
            .forEach { it.visibility = View.GONE }
    }

    // ─────────────────────────────────────────────────────────────
    // MEETING DETAILS
    // ─────────────────────────────────────────────────────────────
    private fun bindMeetingDetails(trade: Trade) {
        val isLiveSession =
            trade.requestedResourceTypeEnum == TradeResourceType.LIVE_SESSION ||
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
    // DIALOG HELPERS
    // ═════════════════════════════════════════════════════════════

    private fun currentDialogBg(): Int = if (isDarkModeEnabled) darkCardBg else Color.WHITE
    private fun currentTextPrimary(): Int = if (isDarkModeEnabled) darkTextPrimary else lightTextPrimary
    private fun currentTextSecondary(): Int = if (isDarkModeEnabled) darkTextSecondary else lightTextSecondary
    private fun currentSecondaryBtnBg(): Int = if (isDarkModeEnabled) darkCardStroke else lightScreenBg
    private fun currentDividerColor(): Int = if (isDarkModeEnabled) darkDivider else lightDivider

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun dialogCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(22), dp(20), dp(20))
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(currentDialogBg())
        }
    }

    private fun dialogTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(currentTextPrimary())
        textSize = 17f
        setTypeface(typeface, Typeface.BOLD)
    }

    private fun dialogDivider(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
        ).apply {
            topMargin = dp(12)
            bottomMargin = dp(12)
        }
        setBackgroundColor(currentDividerColor())
    }

    private fun pillButton(text: String, bgColor: Int, textColor: Int): TextView = TextView(this).apply {
        this.text = text
        setTextColor(textColor)
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(0, dp(12), 0, dp(12))
        background = GradientDrawable().apply {
            cornerRadius = dp(24).toFloat()
            setColor(bgColor)
        }
        isClickable = true
        isFocusable = true
    }

    private fun dialogInputField(hint: String, initialText: String = ""): EditText {
        return EditText(this).apply {
            this.hint = hint
            if (initialText.isNotEmpty()) setText(initialText)
            setTextColor(currentTextPrimary())
            setHintTextColor(currentTextSecondary())
            textSize = 14f
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(
                    if (isDarkModeEnabled) Color.parseColor("#1E2C38")
                    else Color.parseColor("#F4F8FA")
                )
                setStroke(dp(1), if (isDarkModeEnabled) darkCardStroke else lightCardStroke)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
                bottomMargin = dp(16)
            }
        }
    }

    private fun showCustomConfirmDialog(
        title: String,
        message: String,
        positiveText: String,
        isDestructive: Boolean = false,
        onConfirm: () -> Unit
    ) {
        val root = dialogCard()
        root.addView(dialogTitle(title))
        if (message.isNotEmpty()) {
            root.addView(TextView(this).apply {
                text = message
                setTextColor(currentTextSecondary())
                textSize = 14f
                setPadding(0, dp(10), 0, dp(10))
            })
        }
        root.addView(dialogDivider())

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val buttonRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnCancel = pillButton("Cancel", currentSecondaryBtnBg(), currentTextPrimary()).apply {
            setOnClickListener { dialog.dismiss() }
        }
        val confirmBg = if (isDestructive) destructive else acceptGreen
        val btnConfirm = pillButton(positiveText, confirmBg, Color.WHITE).apply {
            setOnClickListener {
                dialog.dismiss()
                onConfirm()
            }
        }
        buttonRow.addView(
            btnCancel,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
        )
        buttonRow.addView(btnConfirm, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(buttonRow)

        dialog.show()
    }

    // ═════════════════════════════════════════════════════════════
    // ACCEPT (original offer)
    // ═════════════════════════════════════════════════════════════

    private fun confirmAccept() {
        val trade = currentTrade ?: return
        showCustomConfirmDialog(
            title = "Accept trade?",
            message = "Finalize the exchange (${trade.offerDisplay()}) at ${format(trade.proposedSchedule)}?",
            positiveText = "Accept",
            isDestructive = false,
            onConfirm = { performAccept(trade) }
        )
    }

    private fun performAccept(trade: Trade) {
        setActionsEnabled(false)
        lifecycleScope.launch {
            val result = TradeManager.acceptTrade(trade.id, myEmail)
            setActionsEnabled(true)
            result.fold(
                onSuccess = {
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
        showCustomConfirmDialog(
            title = "Accept counter offer?",
            message = "Finalize the exchange at ${format(trade.effectiveSchedule)}?",
            positiveText = "Accept",
            isDestructive = false,
            onConfirm = { performAcceptCounter(trade) }
        )
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
        showCustomConfirmDialog(
            title = "Reject trade?",
            message = "The requester will be notified.",
            positiveText = "Reject",
            isDestructive = true,
            onConfirm = { performReject(trade) }
        )
    }

    private fun performReject(trade: Trade) {
        setActionsEnabled(false)
        lifecycleScope.launch {
            val result = TradeManager.rejectTrade(trade.id, myEmail)
            setActionsEnabled(true)
            result.fold(
                onSuccess = {
                    TradeManager.cancelReminders(applicationContext, trade.id)
                    Toast.makeText(this@TradeDetailActivity, "Trade rejected", Toast.LENGTH_SHORT).show()
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

        val root = dialogCard()
        root.addView(dialogTitle("Cancel trade?"))

        val input = dialogInputField("Reason (optional)")
        root.addView(input)
        root.addView(dialogDivider())

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val buttonRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnKeep = pillButton("Keep", currentSecondaryBtnBg(), currentTextPrimary()).apply {
            setOnClickListener { dialog.dismiss() }
        }
        val btnCancelTrade = pillButton("Cancel trade", destructive, Color.WHITE).apply {
            setOnClickListener {
                dialog.dismiss()
                performCancel(trade, input.text?.toString().orEmpty())
            }
        }
        buttonRow.addView(
            btnKeep,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
        )
        buttonRow.addView(btnCancelTrade, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(buttonRow)

        dialog.show()
    }

    private fun performCancel(trade: Trade, reason: String) {
        setActionsEnabled(false)
        lifecycleScope.launch {
            val result = TradeManager.cancelTrade(trade.id, myEmail, reason)
            setActionsEnabled(true)
            result.fold(
                onSuccess = {
                    TradeManager.cancelReminders(applicationContext, trade.id)
                    Toast.makeText(this@TradeDetailActivity, "Trade cancelled", Toast.LENGTH_SHORT).show()
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
    // COMPLETE (triggers point transfer via TradeManager)
    // ═════════════════════════════════════════════════════════════

    private fun confirmComplete() {
        val trade = currentTrade ?: return
        val message = if (trade.agreedPoints > 0)
            "This will transfer ${trade.agreedPoints} points to your partner, mark the trade completed, and bump both users' completed-trade counts."
        else
            "This will finalize the trade for both users and increment your completed-trade count."
        showCustomConfirmDialog(
            title = "Mark trade as completed?",
            message = message,
            positiveText = "Complete",
            isDestructive = false,
            onConfirm = { performComplete(trade) }
        )
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
    // COUNTER TRADE  (points OR material + optional schedule)
    // ═════════════════════════════════════════════════════════════

    private fun openCounterTradeDialog() {
        val trade = currentTrade ?: return
        if (trade.statusEnum != TradeStatus.PENDING) {
            Toast.makeText(this, TradeValidator.ERR_INVALID_STATE, Toast.LENGTH_SHORT).show()
            return
        }
        if (trade.hasCounterOffer) {
            Toast.makeText(this, TradeValidator.ERR_COUNTER_ALREADY, Toast.LENGTH_SHORT).show()
            return
        }

        val root = dialogCard()
        root.addView(dialogTitle("Counter Offer"))

        // Original offer summary
        root.addView(TextView(this).apply {
            text = "Original: ${trade.offerDisplay()}"
            setTextColor(currentTextSecondary())
            textSize = 12.5f
            setPadding(0, dp(6), 0, dp(10))
        })

        // Local dialog state (captured by the material launcher callback)
        var selectedMethod = ExchangeMethod.MATERIAL
        var pickedSchedule = 0L
        var counterReqId = ""
        var counterReqType = ""
        var counterReqTitle = ""
        val counterOffId = trade.requestedResourceId
        val counterOffType = trade.requestedResourceType
        val counterOffTitle = trade.requestedResourceTitle

        // ── Toggle ──
        val toggle = MaterialButtonToggleGroup(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
            isSingleSelection = true
            isSelectionRequired = true
        }
        val btnPoints = MaterialButton(this, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Points"; isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnMaterial = MaterialButton(this, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Material"; isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        toggle.addView(btnPoints)
        toggle.addView(btnMaterial)
        root.addView(toggle)

        // ── Points input ──
        val pointsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
        val tvCounterHint = TextView(this).apply {
            text = "Requested points from ${trade.requesterName.ifBlank { "the requester" }}"
            setTextColor(currentTextSecondary())
            textSize = 11.5f
            setPadding(0, 0, 0, dp(6))
        }
        val etPoints = dialogInputField("Points you want")
        etPoints.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        pointsContainer.addView(tvCounterHint)
        pointsContainer.addView(etPoints)
        root.addView(pointsContainer)

        // ── Material picker ──
        val materialContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.VISIBLE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
        val tvMaterialSummary = TextView(this).apply {
            text = "No resource selected"
            setTextColor(currentTextSecondary())
            textSize = 12.5f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(
                    if (isDarkModeEnabled) Color.parseColor("#1E2C38")
                    else Color.parseColor("#F4F8FA")
                )
                setStroke(dp(1), if (isDarkModeEnabled) darkCardStroke else lightCardStroke)
            }
        }
        val btnPickMaterial = MaterialButton(this).apply {
            text = "Pick resource you want"
            isAllCaps = false
            setPadding(0, dp(10), 0, dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        materialContainer.addView(tvMaterialSummary)
        materialContainer.addView(btnPickMaterial)
        root.addView(materialContainer)

        // ── Schedule (optional override) ──
        val tvSchedule = TextView(this).apply {
            text = "Keep original: ${format(trade.proposedSchedule)}"
            setTextColor(currentTextPrimary())
            textSize = 12.5f
            setPadding(0, dp(8), 0, dp(4))
        }
        val btnChangeSchedule = TextView(this).apply {
            text = "Change schedule"
            setTextColor(currentTextPrimary())
            textSize = 12.5f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(tvSchedule)
        root.addView(btnChangeSchedule)

        // ── Message ──
        val etMessage = dialogInputField(
            "Optional counter message",
            "I'd like to propose a different exchange."
        )
        etMessage.minLines = 2
        etMessage.maxLines = 4
        root.addView(etMessage)

        root.addView(dialogDivider())

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Clear the callback holder when the dialog closes so a stale
        // activity result can't hijack a later dialog.
        dialog.setOnDismissListener {
            pendingCounterMaterialCallback = null
        }

        // ── Toggle behaviour ──
        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            if (checkedId == btnPoints.id) {
                selectedMethod = ExchangeMethod.POINTS
                pointsContainer.visibility = View.VISIBLE
                materialContainer.visibility = View.GONE
            } else {
                selectedMethod = ExchangeMethod.MATERIAL
                pointsContainer.visibility = View.GONE
                materialContainer.visibility = View.VISIBLE
            }
        }
        toggle.check(btnMaterial.id)

        // ── Material picker: uses the class-level launcher via a callback ──
        btnPickMaterial.setOnClickListener {
            pendingCounterMaterialCallback = { id, type, title ->
                counterReqId = id
                counterReqType = type
                counterReqTitle = title
                tvMaterialSummary.text = "Request: $title"
            }
            counterMaterialPickerLauncher.launch(
                Intent(this, SelectResourceActivity::class.java)
            )
        }

        // ── Schedule picker ──
        btnChangeSchedule.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this,
                { _, y, m, d ->
                    val picked = Calendar.getInstance().apply {
                        set(Calendar.YEAR, y); set(Calendar.MONTH, m); set(Calendar.DAY_OF_MONTH, d)
                    }
                    TimePickerDialog(this,
                        { _, h, min ->
                            picked.set(Calendar.HOUR_OF_DAY, h)
                            picked.set(Calendar.MINUTE, min)
                            picked.set(Calendar.SECOND, 0)
                            picked.set(Calendar.MILLISECOND, 0)
                            if (picked.timeInMillis > System.currentTimeMillis()) {
                                pickedSchedule = picked.timeInMillis
                                tvSchedule.text = "New: ${format(pickedSchedule)}"
                            } else {
                                Toast.makeText(
                                    this,
                                    TradeValidator.ERR_PAST_TIME,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        picked.get(Calendar.HOUR_OF_DAY),
                        picked.get(Calendar.MINUTE),
                        false
                    ).show()
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // ── Action row ──
        val buttonRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttonRow.addView(
            pillButton("Cancel", currentSecondaryBtnBg(), currentTextPrimary()).apply {
                setOnClickListener { dialog.dismiss() }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
        )
        buttonRow.addView(
            pillButton("Send Counter", counterPurple, Color.WHITE).apply {
                setOnClickListener {
                    val err = TradeValidator.validateCounterOffer(
                        selectedMethod.value,
                        etPoints.text?.toString()?.toIntOrNull() ?: 0,
                        counterReqId,
                        counterOffId
                    )
                    if (err != null) {
                        Toast.makeText(this@TradeDetailActivity, err, Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    dialog.dismiss()
                    performCounterTrade(
                        trade = trade,
                        method = selectedMethod,
                        points = etPoints.text?.toString()?.toIntOrNull() ?: 0,
                        reqId = counterReqId,
                        reqType = counterReqType,
                        reqTitle = counterReqTitle,
                        offId = counterOffId,
                        offType = counterOffType,
                        offTitle = counterOffTitle,
                        newSchedule = pickedSchedule,
                        message = etMessage.text?.toString()?.trim().orEmpty()
                    )
                }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        root.addView(buttonRow)

        dialog.show()
    }

    private fun performCounterTrade(
        trade: Trade,
        method: ExchangeMethod,
        points: Int,
        reqId: String,
        reqType: String,
        reqTitle: String,
        offId: String,
        offType: String,
        offTitle: String,
        newSchedule: Long,
        message: String
    ) {
        setActionsEnabled(false)
        lifecycleScope.launch {
            val result = TradeManager.counterTrade(
                tradeId = trade.id,
                currentUserEmail = myEmail,
                newSchedule = newSchedule,
                counterExchangeMethod = method.value,
                counterPoints = points,
                counterRequestedResourceId = reqId,
                counterRequestedResourceType = reqType,
                counterRequestedResourceTitle = reqTitle,
                counterOfferedResourceId = offId,
                counterOfferedResourceType = offType,
                counterOfferedResourceTitle = offTitle,
                message = message
            )
            setActionsEnabled(true)
            result.fold(
                onSuccess = {
                    Toast.makeText(
                        this@TradeDetailActivity,
                        "Counter offer sent",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onFailure = { e ->
                    android.util.Log.e("TradeDetailActivity", "Counter failed", e)
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

    private fun setActionsEnabled(enabled: Boolean) {
        listOf(btnAccept, btnCounter, btnReject, btnCancel, btnComplete, btnAcceptCounter)
            .forEach { it.isEnabled = enabled }
    }

    private fun format(millis: Long): String =
        if (millis <= 0L) "—"
        else SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(millis))

    override fun onDestroy() {
        super.onDestroy()
        pendingCounterMaterialCallback = null
        if (::listener.isInitialized) listener.remove()
    }

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