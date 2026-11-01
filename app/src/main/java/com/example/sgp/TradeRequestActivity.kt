package com.example.sgp

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Reached from a demo video ("Trade / Swap" button) OR from Explore/Playlist.
 *
 * Flow:
 *   1. Show requested resource summary
 *   2. Let the requester pick WHAT they offer:
 *        • POINTS   — enter a number of points (validated against balance)
 *        • MATERIAL — pick one of their own existing resources
 *   3. Let the requester pick a preferred date/time
 *   4. Optional message
 *   5. Send -> TradeManager.createTrade()
 *
 * Backward compatible: defaults to MATERIAL (the pre-existing behaviour), so
 * a user who never touches the toggle sees the same flow they always have.
 */
class TradeRequestActivity : BaseActivity() {

    // ─── Extras ───
    private var requestedResourceId = ""
    private var requestedResourceTitle = ""
    private var requestedResourceType = TradeResourceType.OTHER
    private var receiverId = ""
    private var receiverName = ""

    // ─── Selected offered resource (material path) ───
    private var selectedOfferedResourceId = ""
    private var selectedOfferedResourceTitle = ""
    private var selectedOfferedResourceType = TradeResourceType.OTHER

    // ─── NEW: exchange method state ───
    private var selectedExchangeMethod: ExchangeMethod = ExchangeMethod.MATERIAL
    private var currentBalance: Int = 0

    // ─── Selected schedule ───
    private var selectedScheduleMillis = 0L

    // ─── Views ───
    private lateinit var tvRequestedTitle: TextView
    private lateinit var tvRequestedType: TextView
    private lateinit var tvReceiverName: TextView
    private lateinit var ivRequestedThumb: android.widget.ImageView

    // Exchange-method toggle + sections (NEW)
    private lateinit var toggleExchangeMethod: MaterialButtonToggleGroup
    private lateinit var btnOfferPoints: MaterialButton
    private lateinit var btnOfferMaterial: MaterialButton
    private lateinit var pointsSection: View
    private lateinit var materialSection: View
    private lateinit var tvAvailableBalance: TextView
    private lateinit var tilOfferPoints: TextInputLayout
    private lateinit var etOfferPoints: TextInputEditText

    // Material picker (existing views, now inside materialSection)
    private lateinit var btnSelectOffered: MaterialButton
    private lateinit var tvSelectedOffered: TextView

    // Schedule
    private lateinit var tvScheduleValue: TextView
    private lateinit var btnPickSchedule: MaterialButton

    // Message / send
    private lateinit var etMessage: TextInputEditText
    private lateinit var btnSend: MaterialButton
    private lateinit var progressBar: ProgressBar

    private val db = FirebaseFirestore.getInstance()

    // ═════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═════════════════════════════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trade_request)

        // ── Read extras ──
        requestedResourceId = intent.getStringExtra(EXTRA_REQUESTED_ID).orEmpty()
        requestedResourceTitle = intent.getStringExtra(EXTRA_REQUESTED_TITLE).orEmpty()
        requestedResourceType = TradeResourceType.fromString(
            intent.getStringExtra(EXTRA_REQUESTED_TYPE)
        )
        receiverId = intent.getStringExtra(EXTRA_RECEIVER_ID).orEmpty()
        receiverName = intent.getStringExtra(EXTRA_RECEIVER_NAME).orEmpty()

        // ── Auth checks ──
        val myEmail = FirebaseAuth.getInstance().currentUser?.email.orEmpty()
        if (myEmail.isBlank()) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (myEmail.equals(receiverId, ignoreCase = true)) {
            Toast.makeText(this, TradeValidator.ERR_SELF_TRADE, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (requestedResourceId.isBlank() || receiverId.isBlank()) {
            Toast.makeText(this, "Invalid trade request", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // ── Bind views ──
        bindViews()
        setupToolbar()
        bindRequestedResource()

        // ── NEW: wire the exchange-method toggle BEFORE anything else that
        //   depends on visibility, so Points/Material sections start in a
        //   consistent state.
        setupExchangeMethodToggle()
        loadUserBalance()

        // ── Material picker, schedule, message + send ──
        setupSchedulePicker()
        setupSendButton(myEmail)
    }

    private fun bindViews() {
        // Requested resource summary
        tvRequestedTitle = findViewById(R.id.tvRequestedTitle)
        tvRequestedType = findViewById(R.id.tvRequestedType)
        tvReceiverName = findViewById(R.id.tvReceiverName)
        ivRequestedThumb = findViewById(R.id.ivRequestedThumb)

        // Exchange-method toggle + sections (NEW)
        toggleExchangeMethod = findViewById(R.id.toggleExchangeMethod)
        btnOfferPoints = findViewById(R.id.btnOfferPoints)
        btnOfferMaterial = findViewById(R.id.btnOfferMaterial)
        pointsSection = findViewById(R.id.pointsSection)
        materialSection = findViewById(R.id.materialSection)
        tvAvailableBalance = findViewById(R.id.tvAvailableBalance)
        tilOfferPoints = findViewById(R.id.tilOfferPoints)
        etOfferPoints = findViewById(R.id.etOfferPoints)

        // Material picker
        btnSelectOffered = findViewById(R.id.btnSelectOfferedResource)
        tvSelectedOffered = findViewById(R.id.tvSelectedOfferedResource)

        // Schedule
        tvScheduleValue = findViewById(R.id.tvScheduleValue)
        btnPickSchedule = findViewById(R.id.btnPickSchedule)

        // Message / send
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSendTradeRequest)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupToolbar() {
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { finish() }
    }

    // ═════════════════════════════════════════════════════════════
    // REQUESTED RESOURCE SUMMARY  (unchanged behaviour)
    // ═════════════════════════════════════════════════════════════

    private fun bindRequestedResource() {
        tvRequestedTitle.text = requestedResourceTitle.ifBlank { "Resource" }
        tvRequestedType.text = "${requestedResourceType.emoji} ${requestedResourceType.label}"
        tvReceiverName.text = receiverName.ifBlank { receiverId }

        if (requestedResourceId.isNotBlank()) {
            db.collection("skills").document(requestedResourceId).get()
                .addOnSuccessListener { doc ->
                    val url = doc.getString("thumbnailUrl")
                        ?: doc.getString("videoUrl")
                        ?: doc.getString("demoVideoUrl")
                    if (!url.isNullOrBlank()) {
                        Glide.with(this)
                            .load(url)
                            .placeholder(R.drawable.baseline_videocam_24)
                            .error(R.drawable.baseline_videocam_24)
                            .into(ivRequestedThumb)
                    } else {
                        ivRequestedThumb.setImageResource(R.drawable.baseline_videocam_24)
                    }
                }
                .addOnFailureListener {
                    ivRequestedThumb.setImageResource(R.drawable.baseline_videocam_24)
                }
        }
    }

    // ═════════════════════════════════════════════════════════════
    // EXCHANGE METHOD TOGGLE  (NEW)
    // ═════════════════════════════════════════════════════════════

    private fun setupExchangeMethodToggle() {
        toggleExchangeMethod.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            if (checkedId == R.id.btnOfferPoints) {
                selectedExchangeMethod = ExchangeMethod.POINTS
                pointsSection.visibility = View.VISIBLE
                materialSection.visibility = View.GONE
            } else {
                selectedExchangeMethod = ExchangeMethod.MATERIAL
                pointsSection.visibility = View.GONE
                materialSection.visibility = View.VISIBLE
            }
        }

        // Default to MATERIAL, matching the pre-existing flow so nothing
        // changes for users who don't need points.
        toggleExchangeMethod.check(R.id.btnOfferMaterial)

        // Clear any stale error when the user edits the points field
        etOfferPoints.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (tilOfferPoints.error != null) tilOfferPoints.error = null
            }
        })
    }

    /**
     * Loads the requester's current points balance so the UI can show it and
     * do a client-side sanity check before the network call. The authoritative
     * balance check still happens server-side in TradeManager.createTrade().
     */
    private fun loadUserBalance() {
        val email = FirebaseAuth.getInstance().currentUser?.email ?: return
        tvAvailableBalance.text = "Available: loading…"
        db.collection("users").whereEqualTo("email", email).limit(1).get()
            .addOnSuccessListener { snap ->
                currentBalance = snap.documents.firstOrNull()
                    ?.getLong("credits")?.toInt() ?: 0
                tvAvailableBalance.text = "Available: $currentBalance points"
            }
            .addOnFailureListener {
                tvAvailableBalance.text = "Available: —"
            }
    }

    // ═════════════════════════════════════════════════════════════
    // MATERIAL PICKER + SCHEDULE  (existing behaviour, consolidated)
    // ═════════════════════════════════════════════════════════════

    private fun setupSchedulePicker() {
        // Material picker — consolidated from the old openResourceSelector()
        // (which was dead code) into this single setup method.
        btnSelectOffered.setOnClickListener {
            SelectResourceActivity.start(this, SelectResourceActivity.REQ_SELECT)
        }

        // Schedule picker
        tvScheduleValue.setOnClickListener { showDatePicker() }
        btnPickSchedule.setOnClickListener { showDatePicker() }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SelectResourceActivity.REQ_SELECT && resultCode == RESULT_OK) {
            selectedOfferedResourceId =
                data?.getStringExtra(SelectResourceActivity.EXTRA_RESOURCE_ID).orEmpty()
            selectedOfferedResourceTitle =
                data?.getStringExtra(SelectResourceActivity.EXTRA_RESOURCE_TITLE).orEmpty()
            selectedOfferedResourceType = TradeResourceType.fromString(
                data?.getStringExtra(SelectResourceActivity.EXTRA_RESOURCE_TYPE)
            )

            if (selectedOfferedResourceId.isNotBlank()) {
                tvSelectedOffered.text =
                    "${selectedOfferedResourceType.emoji} $selectedOfferedResourceTitle"
            } else {
                tvSelectedOffered.text = "No resource selected"
            }
        }
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, y, m, d ->
                val picked = Calendar.getInstance().apply {
                    set(Calendar.YEAR, y)
                    set(Calendar.MONTH, m)
                    set(Calendar.DAY_OF_MONTH, d)
                }
                showTimePicker(picked)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = System.currentTimeMillis() - 1000
        }.show()
    }

    private fun showTimePicker(dateCal: Calendar) {
        TimePickerDialog(
            this,
            { _, h, min ->
                dateCal.set(Calendar.HOUR_OF_DAY, h)
                dateCal.set(Calendar.MINUTE, min)
                dateCal.set(Calendar.SECOND, 0)
                dateCal.set(Calendar.MILLISECOND, 0)

                if (dateCal.timeInMillis <= System.currentTimeMillis()) {
                    Toast.makeText(
                        this,
                        TradeValidator.ERR_PAST_TIME,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@TimePickerDialog
                }
                selectedScheduleMillis = dateCal.timeInMillis
                tvScheduleValue.text = formatSchedule(selectedScheduleMillis)
            },
            dateCal.get(Calendar.HOUR_OF_DAY),
            dateCal.get(Calendar.MINUTE),
            false
        ).show()
    }

    // ═════════════════════════════════════════════════════════════
    // SEND  (extended to support POINTS or MATERIAL)
    // ═════════════════════════════════════════════════════════════

    private fun setupSendButton(myEmail: String) {
        btnSend.setOnClickListener {
            // ── 1. Exchange-method specific pre-checks (UI feedback) ──
            val offeredPoints: Int = when (selectedExchangeMethod) {
                ExchangeMethod.POINTS -> {
                    val raw = etOfferPoints.text?.toString()?.trim().orEmpty()
                    val parsed = raw.toIntOrNull() ?: 0
                    if (parsed <= 0) {
                        tilOfferPoints.error = "Enter a positive amount"
                        etOfferPoints.requestFocus()
                        return@setOnClickListener
                    }
                    if (currentBalance > 0 && parsed > currentBalance) {
                        tilOfferPoints.error = "You only have $currentBalance points"
                        etOfferPoints.requestFocus()
                        return@setOnClickListener
                    }
                    parsed
                }
                ExchangeMethod.MATERIAL -> 0
            }

            if (selectedExchangeMethod == ExchangeMethod.MATERIAL &&
                selectedOfferedResourceId.isBlank()
            ) {
                Toast.makeText(this, TradeValidator.ERR_NO_RESOURCE, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ── 2. Build the trade payload ──
            val message = etMessage.text?.toString()?.trim().orEmpty()
            val myName = getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
                .getString("user_name", "").orEmpty()

            val trade = Trade(
                requesterId = myEmail,
                requesterName = myName,
                receiverId = receiverId,
                receiverName = receiverName,
                requestedResourceId = requestedResourceId,
                requestedResourceType = requestedResourceType.name,
                requestedResourceTitle = requestedResourceTitle,

                // NEW: exchange side
                exchangeMethod = selectedExchangeMethod.value,
                offeredPoints = offeredPoints,
                offeredResourceId = if (selectedExchangeMethod == ExchangeMethod.MATERIAL)
                    selectedOfferedResourceId else "",
                offeredResourceType = if (selectedExchangeMethod == ExchangeMethod.MATERIAL)
                    selectedOfferedResourceType.name else TradeResourceType.OTHER.name,
                offeredResourceTitle = if (selectedExchangeMethod == ExchangeMethod.MATERIAL)
                    selectedOfferedResourceTitle else "",

                message = message,
                status = TradeStatus.PENDING.value,
                proposedSchedule = selectedScheduleMillis
            )

            // ── 3. Client-side pre-flight validation ──
            val preflightError = TradeValidator.validateNewTrade(trade)
            if (preflightError != null) {
                Toast.makeText(this, preflightError, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // ── 4. Disable UI and dispatch ──
            setLoading(true)

            lifecycleScope.launch {
                val result = TradeManager.createTrade(trade)
                setLoading(false)

                result.fold(
                    onSuccess = {
                        Toast.makeText(
                            this@TradeRequestActivity,
                            "Trade request sent!",
                            Toast.LENGTH_SHORT
                        ).show()
                        setResult(RESULT_OK)
                        finish()
                    },
                    onFailure = { e ->
                        // Never surface raw Firestore errors to the user.
                        val friendly = TradeValidator.mapException(e)
                        android.util.Log.e(
                            "TradeRequestActivity",
                            "Trade create failed: ${e.message}",
                            e
                        )
                        Toast.makeText(
                            this@TradeRequestActivity,
                            friendly,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnSend.isEnabled = !loading
        btnSelectOffered.isEnabled = !loading
        btnPickSchedule.isEnabled = !loading
        etMessage.isEnabled = !loading
        toggleExchangeMethod.isEnabled = !loading
        etOfferPoints.isEnabled = !loading
    }

    private fun formatSchedule(millis: Long): String =
        SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(millis))

    // ═════════════════════════════════════════════════════════════
    // COMPANION
    // ═════════════════════════════════════════════════════════════

    companion object {
        private const val EXTRA_REQUESTED_ID = "requestedResourceId"
        private const val EXTRA_REQUESTED_TITLE = "requestedResourceTitle"
        private const val EXTRA_REQUESTED_TYPE = "requestedResourceType"
        private const val EXTRA_RECEIVER_ID = "receiverId"
        private const val EXTRA_RECEIVER_NAME = "receiverName"

        /**
         * Launch helper used by VideoPlayerActivity, PlaylistActivity, etc.
         * Pre-fills the "requested" side of the trade.
         */
        fun start(
            context: Context,
            requestedResourceId: String,
            requestedResourceTitle: String,
            requestedResourceType: TradeResourceType,
            receiverId: String,
            receiverName: String
        ) {
            context.startActivity(
                Intent(context, TradeRequestActivity::class.java).apply {
                    putExtra(EXTRA_REQUESTED_ID, requestedResourceId)
                    putExtra(EXTRA_REQUESTED_TITLE, requestedResourceTitle)
                    putExtra(EXTRA_REQUESTED_TYPE, requestedResourceType.name)
                    putExtra(EXTRA_RECEIVER_ID, receiverId)
                    putExtra(EXTRA_RECEIVER_NAME, receiverName)
                }
            )
        }
    }
}