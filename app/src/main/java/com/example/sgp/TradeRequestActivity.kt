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
import com.google.android.material.textfield.TextInputEditText
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
 *   2. Let the requester pick which of their own resources to offer
 *   3. Let the requester pick a preferred date/time
 *   4. Optional message
 *   5. Send -> TradeManager.createTrade()
 */
class TradeRequestActivity : BaseActivity() {

    // ─── Extras ───
    private var requestedResourceId = ""
    private var requestedResourceTitle = ""
    private var requestedResourceType = TradeResourceType.OTHER
    private var receiverId = ""
    private var receiverName = ""

    // ─── Selected offered resource ───
    private var selectedOfferedResourceId = ""
    private var selectedOfferedResourceTitle = ""
    private var selectedOfferedResourceType = TradeResourceType.OTHER

    // ─── Selected schedule ───
    private var selectedScheduleMillis = 0L

    // ─── Views ───
    private lateinit var tvRequestedTitle: TextView
    private lateinit var tvRequestedType: TextView
    private lateinit var tvReceiverName: TextView
    private lateinit var ivRequestedThumb: android.widget.ImageView

    private lateinit var btnSelectOffered: MaterialButton
    private lateinit var tvSelectedOffered: TextView

    private lateinit var tvScheduleValue: TextView
    private lateinit var btnPickSchedule: MaterialButton

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
        setupSchedulePicker()
        setupSendButton(myEmail)
    }

    private fun bindViews() {
        tvRequestedTitle = findViewById(R.id.tvRequestedTitle)
        tvRequestedType = findViewById(R.id.tvRequestedType)
        tvReceiverName = findViewById(R.id.tvReceiverName)
        ivRequestedThumb = findViewById(R.id.ivRequestedThumb)

        btnSelectOffered = findViewById(R.id.btnSelectOfferedResource)
        tvSelectedOffered = findViewById(R.id.tvSelectedOfferedResource)

        tvScheduleValue = findViewById(R.id.tvScheduleValue)
        btnPickSchedule = findViewById(R.id.btnPickSchedule)

        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSendTradeRequest)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupToolbar() {
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { finish() }
    }

    // ═════════════════════════════════════════════════════════════
    // REQUESTED RESOURCE SUMMARY
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
    // OFFERED RESOURCE PICKER
    // ═════════════════════════════════════════════════════════════

    private fun openResourceSelector() {
        btnSelectOffered.setOnClickListener {
            SelectResourceActivity.start(this, SelectResourceActivity.REQ_SELECT)
        }
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

    // ═════════════════════════════════════════════════════════════
    // SCHEDULE PICKER
    // ═════════════════════════════════════════════════════════════

    private fun setupSchedulePicker() {
        btnSelectOffered.setOnClickListener {
            SelectResourceActivity.start(this, SelectResourceActivity.REQ_SELECT)
        }
        tvScheduleValue.setOnClickListener { showDatePicker() }
        btnPickSchedule.setOnClickListener { showDatePicker() }
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
    // SEND
    // ═════════════════════════════════════════════════════════════

    private fun setupSendButton(myEmail: String) {
        btnSend.setOnClickListener {
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
                offeredResourceId = selectedOfferedResourceId,
                offeredResourceType = selectedOfferedResourceType.name,
                offeredResourceTitle = selectedOfferedResourceTitle,
                message = message,
                status = TradeStatus.PENDING.value,
                proposedSchedule = selectedScheduleMillis
            )

            // ── Client-side pre-flight validation ──
            val preflightError = TradeValidator.validateNewTrade(trade)
            if (preflightError != null) {
                Toast.makeText(this, preflightError, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // ── Disable UI and dispatch ──
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
                        // ── Never surface raw Firestore errors to the user ──
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