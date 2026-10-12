package com.example.sgp

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed class ChatListItem {
    data class DateHeader(val label: String) : ChatListItem()
    data class MessageItem(val message: ChatMessage) : ChatListItem()
}

/** What the adapter should show for a message (decrypted, placeholder, or deleted notice). */
data class MessageDisplay(val text: String, val italic: Boolean = false)

class ChatActivity : BaseActivity() {

    companion object {
        private const val TAG = "ChatActivity"
        private const val NOTIFY_FUNCTION_URL =
            "https://ghrxltlstncjcizyyqfo.supabase.co/functions/v1/send-chat-notification"
        private const val DELETE_FOR_EVERYONE_WINDOW_MS = 60 * 60 * 1000L // 1 hour
        private const val MAX_MESSAGE_LENGTH = 2000 // keep in sync with policy.maxMessageLength
    }

    private lateinit var db: FirebaseFirestore
    private lateinit var recyclerView: RecyclerView
    private lateinit var etMessage: TextInputEditText
    private lateinit var tilMessage: TextInputLayout
    private lateinit var btnSend: ImageButton
    private lateinit var adapter: MessageAdapter

    private val messages = mutableListOf<ChatMessage>()
    private val chatItems = mutableListOf<ChatListItem>()
    private var messagesListener: ListenerRegistration? = null

    /** Messages shown immediately but not yet confirmed by the server (they now arrive via Cloud Function). */
    private val pendingOutgoing = LinkedHashMap<String, ChatMessage>()

    private lateinit var conversationId: String
    private lateinit var myEmail: String
    private lateinit var otherEmail: String
    private var otherName: String = ""
    private var skillId: String? = null
    private var skillTitle: String? = null
    private var myDisplayName: String = ""

    // ---------------------------------------------------------------------
    // ENCRYPTION + MODERATION STATE
    // ---------------------------------------------------------------------
    private var conversationKey: ByteArray? = null
    private var keyLoading = false

    private var moderationState = ModerationState()
    private var restrictionNoticeShown = false
    private val tickHandler = Handler(Looper.getMainLooper())
    private val tickRunnable = Runnable { updateRestrictionUi() }

    // ---------------------------------------------------------------------
    // THEME
    // ---------------------------------------------------------------------
    private lateinit var sharedPreferences: SharedPreferences
    private var isDarkModeEnabled = false

    private val lightScreenBg = Color.parseColor("#EAF1F5")
    private val lightHeaderBg = Color.parseColor("#1B3C53")
    private val lightHeaderSubtitle = Color.parseColor("#C9D6DE")
    private val lightInputBarBg = Color.parseColor("#EAF1F5")
    private val lightInputBoxBg = Color.parseColor("#FFFFFF")
    private val lightInputStroke = Color.parseColor("#D2C1B6")
    private val lightTextPrimary = Color.parseColor("#1B3C53")
    private val lightTextSecondary = Color.parseColor("#456882")
    private val lightSendBtnBg = Color.parseColor("#1B3C53")

    private val darkScreenBg = Color.parseColor("#0F1B24")
    private val darkHeaderBg = Color.parseColor("#0B1620")
    private val darkHeaderSubtitle = Color.parseColor("#8FA6B8")
    private val darkInputBarBg = Color.parseColor("#0F1B24")
    private val darkInputBoxBg = Color.parseColor("#16232E")
    private val darkInputStroke = Color.parseColor("#2A3B48")
    private val darkTextPrimary = Color.parseColor("#F5F7F8")
    private val darkTextSecondary = Color.parseColor("#8FA6B8")
    private val darkSendBtnBg = Color.parseColor("#24405A")

    private val headerTextColor = Color.parseColor("#F9F3EF")

    // Dialog-specific colors
    private val dialogDangerLight = Color.parseColor("#DC2626")
    private val dialogDangerDark = Color.parseColor("#B3261E")
    private val dialogDangerTextLight = Color.parseColor("#C0392B")
    private val dialogDangerTextDark = Color.parseColor("#FF6961")

    private val httpClient by lazy {
        HttpClient(Android) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    @Serializable
    private data class ChatNotificationRequest(
        val senderId: String,
        val senderName: String,
        val receiverId: String,
        val text: String,
        val conversationId: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        sharedPreferences = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        isDarkModeEnabled = sharedPreferences.getBoolean(SettingsActivity.KEY_DARK_MODE, false)

        val currentEmail = FirebaseAuth.getInstance().currentUser?.email
        if (currentEmail.isNullOrEmpty()) {
            Toast.makeText(this, "Please log in to chat", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        myEmail = currentEmail
        myDisplayName = myEmail

        val other = intent.getStringExtra("otherUserEmail")
        if (other.isNullOrEmpty()) {
            Toast.makeText(this, "Unable to open chat", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        otherEmail = other
        otherName = intent.getStringExtra("otherUserName") ?: otherEmail
        skillId = intent.getStringExtra("skillId")
        skillTitle = intent.getStringExtra("skillTitle")

        if (myEmail == otherEmail) {
            Toast.makeText(this, "You can't message yourself", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        conversationId = buildConversationId(myEmail, otherEmail, skillId)
        db = FirebaseFirestore.getInstance()

        val toolbar: Toolbar = findViewById(R.id.toolbarChat)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.navigationIcon?.setTint(Color.WHITE)

        val toolbarBasePaddingTop = toolbar.paddingTop
        val toolbarBasePaddingBottom = toolbar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, toolbarBasePaddingTop + bars.top, v.paddingRight, toolbarBasePaddingBottom)
            insets
        }

        val rootLayout = findViewById<View>(R.id.rootChatLayout)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, maxOf(systemBars.bottom, ime.bottom))
            insets
        }

        findViewById<TextView>(R.id.tvChatWithName).text = otherName
        findViewById<TextView>(R.id.tvChatContext).apply {
            if (!skillTitle.isNullOrEmpty()) {
                text = "About: $skillTitle"
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }

        recyclerView = findViewById(R.id.recyclerViewMessages)
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        recyclerView.layoutManager = layoutManager
        adapter = MessageAdapter(
            chatItems, myEmail, isDarkModeEnabled,
            { message -> resolveDisplay(message) },
            { message -> showMessageOptionsDialog(message) }
        )
        recyclerView.adapter = adapter

        etMessage = findViewById(R.id.etMessage)
        tilMessage = findViewById(R.id.tilMessageInput)
        btnSend = findViewById(R.id.btnSend)
        btnSend.setOnClickListener { sendMessage() }

        applyChatTheme(isDarkModeEnabled)

        loadMyDisplayName {
            ensureConversationExists()
        }
    }

    override fun onStart() {
        super.onStart()
        listenForMessages()
        refreshModerationStatus()
        loadConversationKey()
        markAsRead()
    }

    override fun onResume() {
        super.onResume()
        val darkNow = sharedPreferences.getBoolean(SettingsActivity.KEY_DARK_MODE, false)
        if (darkNow != isDarkModeEnabled) {
            isDarkModeEnabled = darkNow
            applyChatTheme(isDarkModeEnabled)
            adapter.setDarkMode(isDarkModeEnabled)
        }
    }

    override fun onStop() {
        super.onStop()
        messagesListener?.remove()
        messagesListener = null
        tickHandler.removeCallbacks(tickRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        httpClient.close()
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }

    // ---------------------------------------------------------------------
    // THEME APPLICATION
    // ---------------------------------------------------------------------
    private fun applyChatTheme(isDark: Boolean) {
        val screenBg = if (isDark) darkScreenBg else lightScreenBg
        val headerBg = if (isDark) darkHeaderBg else lightHeaderBg
        val headerSubtitle = if (isDark) darkHeaderSubtitle else lightHeaderSubtitle
        val inputBarBg = if (isDark) darkInputBarBg else lightInputBarBg
        val inputBoxBg = if (isDark) darkInputBoxBg else lightInputBoxBg
        val inputStroke = if (isDark) darkInputStroke else lightInputStroke
        val textPrimary = if (isDark) darkTextPrimary else lightTextPrimary
        val textSecondary = if (isDark) darkTextSecondary else lightTextSecondary
        val sendBg = if (isDark) darkSendBtnBg else lightSendBtnBg

        findViewById<View>(R.id.rootChatLayout)?.setBackgroundColor(screenBg)
        findViewById<Toolbar>(R.id.toolbarChat)?.setBackgroundColor(headerBg)
        findViewById<TextView>(R.id.tvChatWithName)?.setTextColor(headerTextColor)
        findViewById<TextView>(R.id.tvChatContext)?.setTextColor(headerSubtitle)

        recyclerView.setBackgroundColor(screenBg)
        findViewById<View>(R.id.layoutInputBar)?.setBackgroundColor(inputBarBg)

        findViewById<TextInputLayout>(R.id.tilMessageInput)?.apply {
            boxBackgroundColor = inputBoxBg
            boxStrokeColor = inputStroke
            setHelperTextColor(ColorStateList.valueOf(textSecondary))
        }
        etMessage.setTextColor(textPrimary)
        etMessage.setHintTextColor(textSecondary)

        btnSend.backgroundTintList = ColorStateList.valueOf(sendBg)
    }

    // ---------------------------------------------------------------------
    // DIALOG THEME HELPERS
    // ---------------------------------------------------------------------
    private fun currentDialogBg(): Int =
        if (isDarkModeEnabled) Color.parseColor("#16232E") else Color.parseColor("#FFFFFF")

    private fun currentTextPrimary(): Int = if (isDarkModeEnabled) darkTextPrimary else lightTextPrimary
    private fun currentTextSecondary(): Int = if (isDarkModeEnabled) darkTextSecondary else lightTextSecondary

    private fun currentSecondaryBtnBg(): Int =
        if (isDarkModeEnabled) Color.parseColor("#2A3B48") else Color.parseColor("#EAF1F5")

    private fun currentSecondaryBtnText(): Int = if (isDarkModeEnabled) darkTextPrimary else lightTextSecondary
    private fun currentDangerBg(): Int = if (isDarkModeEnabled) dialogDangerDark else dialogDangerLight
    private fun currentDangerText(): Int = if (isDarkModeEnabled) dialogDangerTextDark else dialogDangerTextLight
    private fun currentPrimaryBtnBg(): Int = if (isDarkModeEnabled) darkSendBtnBg else lightSendBtnBg

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun dialogCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(20))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(currentDialogBg())
            }
        }
    }

    private fun pillButton(text: String, bgColor: Int, textColor: Int): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(textColor)
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(bgColor)
            }
            isClickable = true
            isFocusable = true
        }
    }

    private fun matchWrap(bottom: Int = 0, top: Int = 0) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = bottom; topMargin = top }

    // ---------------------------------------------------------------------
    // MODERATION / WARNING DIALOGS (same visual language as the delete dialogs)
    // ---------------------------------------------------------------------
    private fun showInfoDialog(
        title: String,
        body: String,
        secondaryLabel: String? = null,
        onSecondary: (() -> Unit)? = null
    ) {
        val root = dialogCard()
        root.addView(TextView(this).apply {
            text = title
            setTextColor(currentTextPrimary())
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = matchWrap(bottom = dp(8))
        })
        root.addView(TextView(this).apply {
            text = body
            setTextColor(currentTextSecondary())
            textSize = 14f
            layoutParams = matchWrap(bottom = dp(18))
        })

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        root.addView(pillButton("OK", currentPrimaryBtnBg(), Color.WHITE).apply {
            setOnClickListener { dialog.dismiss() }
            layoutParams = matchWrap(bottom = dp(8))
        })
        if (secondaryLabel != null && onSecondary != null) {
            root.addView(pillButton(secondaryLabel, currentSecondaryBtnBg(), currentSecondaryBtnText()).apply {
                setOnClickListener {
                    dialog.dismiss()
                    onSecondary()
                }
            })
        }
        dialog.show()
    }

    /** Small form dialog (one optional text field) used for appeals and reports. */
    private fun showFormDialog(
        title: String,
        description: String,
        hint: String,
        confirmLabel: String,
        onConfirm: (String) -> Unit
    ) {
        val root = dialogCard()
        root.addView(TextView(this).apply {
            text = title
            setTextColor(currentTextPrimary())
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = matchWrap(bottom = dp(8))
        })
        root.addView(TextView(this).apply {
            text = description
            setTextColor(currentTextSecondary())
            textSize = 13f
            layoutParams = matchWrap(bottom = dp(12))
        })
        val input = EditText(this).apply {
            this.hint = hint
            setTextColor(currentTextPrimary())
            setHintTextColor(currentTextSecondary())
            filters = arrayOf(InputFilter.LengthFilter(500))
            minLines = 2
            maxLines = 4
            layoutParams = matchWrap(bottom = dp(16))
        }
        root.addView(input)

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(
            pillButton("Cancel", currentSecondaryBtnBg(), currentSecondaryBtnText()).apply {
                setOnClickListener { dialog.dismiss() }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
        )
        row.addView(
            pillButton(confirmLabel, currentPrimaryBtnBg(), Color.WHITE).apply {
                setOnClickListener {
                    dialog.dismiss()
                    onConfirm(input.text?.toString()?.trim().orEmpty())
                }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        root.addView(row)
        dialog.show()
    }

    private fun showViolationDialog(outcome: SendOutcome.Rejected, rejectedText: String) {
        val now = ModerationClient.now()
        val reason = "Detected: ${ModerationMessages.categoryLabel(outcome.category)}. This is an automatic check, " +
                "so it can be wrong. You can appeal."
        showInfoDialog(
            title = ModerationMessages.warningTitle(outcome.warningCount),
            body = ModerationMessages.warningBody(outcome.warningCount, outcome.blockedUntilMillis, now) + "\n\n" + reason,
            secondaryLabel = if (outcome.eventId.isNotEmpty()) "Appeal this decision" else null,
            onSecondary = { showAppealDialog(outcome.eventId, rejectedText) }
        )
    }

    private fun showAppealDialog(eventId: String, rejectedText: String) {
        showFormDialog(
            title = "Appeal this decision",
            description = "A reviewer will see your note and the message you tried to send. " +
                    "If we got it wrong, the warning will be removed.",
            hint = "Why does this message follow the guidelines? (optional)",
            confirmLabel = "Submit appeal"
        ) { note ->
            lifecycleScope.launch {
                val ok = ModerationClient.appeal(eventId, note, rejectedText)
                Toast.makeText(
                    this@ChatActivity,
                    if (ok) "Appeal submitted. Thank you." else "Couldn't submit the appeal. Please try again later.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showRestrictionDialog(state: ModerationState) {
        val until = state.blockedUntilMillis ?: return
        val fmt = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
        val started = state.blockStartedMillis?.let { "Started: ${fmt.format(Date(it))}\n" } ?: ""
        val reason = state.blockReason?.let { "Reason: $it\n" } ?: ""
        val remaining = ModerationMessages.formatRemaining(until - ModerationClient.now())
        showInfoDialog(
            title = "Chat sending restricted",
            body = ModerationMessages.BLOCKED_BODY + "\n\n" + reason + started +
                    "Ends: ${fmt.format(Date(until))}\nTime left: $remaining\n\n" +
                    "You can still read your conversations."
        )
    }

    private fun confirmReport(message: ChatMessage) {
        showFormDialog(
            title = "Report this message?",
            description = "Reviewers will see this message to decide if it breaks the community guidelines. " +
                    "The sender is not told who reported it.",
            hint = "What's wrong with it? (optional)",
            confirmLabel = "Report"
        ) { reason ->
            lifecycleScope.launch {
                val ok = ModerationClient.report(conversationId, message.id, reason)
                Toast.makeText(
                    this@ChatActivity,
                    if (ok) "Thanks. We'll review this message." else "Couldn't send the report. Please try again.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ---------------------------------------------------------------------
    // RESTRICTION UI (display only; the server is the authority)
    // ---------------------------------------------------------------------
    /** Asks the server for warnings/restriction (display only; the server enforces on every send). */
    private fun refreshModerationStatus() {
        lifecycleScope.launch {
            val state = ModerationClient.fetchStatus() ?: return@launch
            moderationState = state
            updateRestrictionUi()
            if (!restrictionNoticeShown && state.isBlockedAt(ModerationClient.now())) {
                restrictionNoticeShown = true
                showRestrictionDialog(state)
            }
        }
    }

    private fun updateRestrictionUi() {
        tickHandler.removeCallbacks(tickRunnable)
        val now = ModerationClient.now()
        val until = moderationState.blockedUntilMillis
        if (until != null && until > now) {
            etMessage.isEnabled = false
            btnSend.isEnabled = false
            btnSend.alpha = 0.5f
            tilMessage.isHelperTextEnabled = true
            tilMessage.helperText = "Sending restricted · ${ModerationMessages.formatRemaining(until - now)} left"
            tickHandler.postDelayed(tickRunnable, 1000)
        } else {
            etMessage.isEnabled = true
            btnSend.isEnabled = true
            btnSend.alpha = 1f
            val w = moderationState.warningCount
            tilMessage.isHelperTextEnabled = w > 0
            tilMessage.helperText = if (w > 0) "Warnings: $w of ${ModerationMessages.WARNINGS_BEFORE_BLOCK}" else null
        }
    }

    // ---------------------------------------------------------------------
    // DATA LOADING / SETUP
    // ---------------------------------------------------------------------
    private fun loadMyDisplayName(onDone: () -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrEmpty()) {
            onDone()
            return
        }
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val nameFromDb = doc.getString("name")?.takeIf { it.isNotBlank() }
                if (nameFromDb != null) {
                    myDisplayName = nameFromDb
                }
                onDone()
            }
            .addOnFailureListener {
                onDone()
            }
    }

    private fun ensureConversationExists() {
        val convoRef = db.collection("chats").document(conversationId)
        val sortedParticipants = listOf(myEmail, otherEmail).sorted()
        val data = hashMapOf(
            "id" to conversationId,
            "participants" to sortedParticipants,
            "participantNames" to mapOf(myEmail to myDisplayName, otherEmail to otherName),
            "skillId" to skillId,
            "skillTitle" to skillTitle
        )
        convoRef.set(data, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener { loadConversationKey() }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to open chat: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /** Fetches (or reads from the Keystore-wrapped cache) this conversation's decryption key. */
    private fun loadConversationKey() {
        if (keyLoading || conversationKey != null) return
        keyLoading = true
        lifecycleScope.launch {
            conversationKey = ChatKeys.provider(applicationContext).getKey(conversationId)
            keyLoading = false
            if (conversationKey != null) adapter.notifyDataSetChanged()
        }
    }

    private fun markAsRead() {
        db.collection("chats").document(conversationId)
            .update("unreadFor", FieldValue.arrayRemove(myEmail))
    }

    private fun listenForMessages() {
        messagesListener?.remove()

        messagesListener = db.collection("chats").document(conversationId)
            .collection("messages")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, error.message, Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val freshMessages = mutableListOf<ChatMessage>()
                    for (doc in snapshot.documents) {
                        try {
                            doc.toObject(ChatMessage::class.java)?.let { freshMessages.add(it) }
                        } catch (e: Exception) {
                            // One malformed document must never take the whole chat down.
                            Log.w(TAG, "skipping unreadable message document")
                        }
                    }

                    messages.clear()
                    messages.addAll(freshMessages)
                    // Server copy has arrived: drop the optimistic placeholder.
                    freshMessages.forEach { pendingOutgoing.remove(it.id) }

                    rebuildDisplayList()
                    adapter.notifyDataSetChanged()
                    if (chatItems.isNotEmpty()) {
                        recyclerView.scrollToPosition(chatItems.size - 1)
                    }
                    if (conversationKey == null) loadConversationKey()
                }
            }
    }

    // ---------------------------------------------------------------------
    // DECRYPTION FOR DISPLAY
    // ---------------------------------------------------------------------
    private fun resolveDisplay(message: ChatMessage): MessageDisplay {
        if (message.deleted) return MessageDisplay("This message was deleted", true)
        // Legacy (pre-encryption) messages and local optimistic messages are plaintext by construction.
        // Encrypted messages are identified ONLY by encVersion, never by looking at the text.
        if (!message.isEncrypted) return MessageDisplay(message.text)

        val aad = MessageCipher.messageAad(conversationId, message.id, message.senderId)
        return when (val r = MessageCipher.decrypt(conversationKey, message.encVersion, message.ciphertext, message.iv, aad)) {
            is DecryptResult.Success -> MessageDisplay(r.text)
            DecryptResult.MissingKey ->
                MessageDisplay(if (keyLoading) "🔒 Unlocking…" else "🔒 Message locked. Check your connection.", true)
            DecryptResult.Tampered, DecryptResult.Corrupt ->
                MessageDisplay("⚠️ This message could not be verified.", true)
            DecryptResult.Unsupported ->
                MessageDisplay("🔒 Update SkillSwap to read this message.", true)
        }
    }

    // ---------------------------------------------------------------------
    // SENDING (goes through the trusted sendMessage Cloud Function)
    // ---------------------------------------------------------------------
    private fun sendMessage() {
        val text = etMessage.text?.toString()?.trim().orEmpty()
        if (TextUtils.isEmpty(text)) return
        if (text.length > MAX_MESSAGE_LENGTH) {
            Toast.makeText(this, "Message is too long.", Toast.LENGTH_SHORT).show()
            return
        }
        // UX shortcut only. The server re-checks the restriction on every message.
        if (moderationState.isBlockedAt(ModerationClient.now())) {
            showRestrictionDialog(moderationState)
            return
        }

        // Client-generated id is the idempotency key for retries.
        val messageId = db.collection("chats").document(conversationId).collection("messages").document().id
        val clientTimestamp = System.currentTimeMillis()

        pendingOutgoing[messageId] = ChatMessage(
            id = messageId,
            senderId = myEmail,
            senderName = myDisplayName,
            receiverId = otherEmail,
            text = text,
            timestamp = clientTimestamp,
            serverTimestamp = null
        )
        etMessage.setText("")
        rebuildDisplayList()
        adapter.notifyDataSetChanged()
        if (chatItems.isNotEmpty()) recyclerView.scrollToPosition(chatItems.size - 1)

        lifecycleScope.launch {
            handleOutcome(messageId, text, ModerationClient.sendMessage(conversationId, messageId, text, clientTimestamp))
        }
    }

    private fun handleOutcome(messageId: String, text: String, outcome: SendOutcome) {
        when (outcome) {
            is SendOutcome.Sent -> {
                // Placeholder is removed when the real document arrives in the snapshot listener.
                if (!outcome.duplicate) sendPushNotification()
            }
            is SendOutcome.Rejected -> {
                dropPending(messageId)
                moderationState = moderationState.copy(
                    warningCount = outcome.warningCount,
                    blockedUntilMillis = outcome.blockedUntilMillis ?: moderationState.blockedUntilMillis
                )
                updateRestrictionUi()
                showViolationDialog(outcome, text)
            }
            is SendOutcome.Blocked -> {
                dropPending(messageId)
                restoreDraft(text)
                moderationState = ModerationState(
                    outcome.warningCount, outcome.blockedUntilMillis, outcome.blockStartedMillis, outcome.reason
                )
                updateRestrictionUi()
                showRestrictionDialog(moderationState)
            }
            is SendOutcome.Failed -> {
                dropPending(messageId)
                restoreDraft(text)
                Toast.makeText(this, outcome.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun dropPending(messageId: String) {
        if (pendingOutgoing.remove(messageId) != null) {
            rebuildDisplayList()
            adapter.notifyDataSetChanged()
        }
    }

    private fun restoreDraft(text: String) {
        if (etMessage.text.isNullOrEmpty()) {
            etMessage.setText(text)
            etMessage.setSelection(text.length)
        }
    }

    /** The push body is deliberately generic: message plaintext must not leave the app for third parties. */
    private fun sendPushNotification() {
        lifecycleScope.launch {
            try {
                val response = httpClient.post(NOTIFY_FUNCTION_URL) {
                    contentType(ContentType.Application.Json)
                    setBody(
                        ChatNotificationRequest(
                            senderId = myEmail,
                            senderName = myDisplayName,
                            receiverId = otherEmail,
                            text = "Sent you a message",
                            conversationId = conversationId
                        )
                    )
                }
                Log.d(TAG, "Push notification response: ${response.status}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send push notification: ${e.javaClass.simpleName}")
            }
        }
    }

    // ---------------- Ordering & Date Grouping ----------------

    private fun effectiveMillis(message: ChatMessage): Long {
        return message.serverTimestamp?.toDate()?.time ?: message.timestamp
    }

    private fun rebuildDisplayList() {
        chatItems.clear()
        var lastDateLabel: String? = null

        val sortedList = (messages + pendingOutgoing.values)
            .filter { !it.deletedFor.contains(myEmail) }
            .sortedWith(
                compareBy<ChatMessage> { effectiveMillis(it) }
                    .thenBy { it.timestamp }
            )

        sortedList.forEach { message ->
            val label = dateLabelFor(effectiveMillis(message))
            if (label != lastDateLabel) {
                chatItems.add(ChatListItem.DateHeader(label))
                lastDateLabel = label
            }
            chatItems.add(ChatListItem.MessageItem(message))
        }
    }

    private fun dateLabelFor(timestamp: Long): String {
        val messageCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val todayCal = Calendar.getInstance()
        val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

        fun isSameDay(a: Calendar, b: Calendar) =
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

        return when {
            isSameDay(messageCal, todayCal) -> "Today"
            isSameDay(messageCal, yesterdayCal) -> "Yesterday"
            else -> SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(timestamp))
        }
    }

    // ---------------- Message options (delete / report) ----------------

    private fun showMessageOptionsDialog(message: ChatMessage) {
        if (message.deleted) return
        if (pendingOutgoing.containsKey(message.id)) return // not on the server yet

        val isMine = message.senderId == myEmail
        val withinDeleteForEveryoneWindow =
            System.currentTimeMillis() - effectiveMillis(message) <= DELETE_FOR_EVERYONE_WINDOW_MS

        val root = dialogCard()

        root.addView(TextView(this).apply {
            text = "Message options"
            setTextColor(currentTextPrimary())
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = matchWrap(bottom = dp(16))
        })

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        root.addView(
            pillButton("Delete for me", currentSecondaryBtnBg(), currentSecondaryBtnText()).apply {
                setOnClickListener {
                    dialog.dismiss()
                    deleteForMe(message)
                }
                layoutParams = matchWrap(bottom = dp(8))
            }
        )

        if (isMine && withinDeleteForEveryoneWindow) {
            root.addView(
                pillButton("Delete for everyone", currentSecondaryBtnBg(), currentDangerText()).apply {
                    setOnClickListener {
                        dialog.dismiss()
                        confirmDeleteForEveryone(message)
                    }
                    layoutParams = matchWrap(bottom = dp(8))
                }
            )
        }

        if (!isMine) {
            root.addView(
                pillButton("Report message", currentSecondaryBtnBg(), currentDangerText()).apply {
                    setOnClickListener {
                        dialog.dismiss()
                        confirmReport(message)
                    }
                    layoutParams = matchWrap(bottom = dp(8))
                }
            )
        }

        root.addView(
            pillButton("Cancel", Color.TRANSPARENT, currentTextSecondary()).apply {
                setOnClickListener { dialog.dismiss() }
            }
        )

        dialog.show()
    }

    private fun confirmDeleteForEveryone(message: ChatMessage) {
        val root = dialogCard()

        root.addView(TextView(this).apply {
            text = "⚠️"
            textSize = 30f
            gravity = Gravity.CENTER
            layoutParams = matchWrap(bottom = dp(8))
        })

        root.addView(TextView(this).apply {
            text = "Delete for everyone?"
            setTextColor(currentTextPrimary())
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })

        root.addView(TextView(this).apply {
            text = "This message will be deleted for everyone in this chat."
            setTextColor(currentTextSecondary())
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = matchWrap(top = dp(8), bottom = dp(18))
        })

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val buttonRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val btnCancel = pillButton("Cancel", currentSecondaryBtnBg(), currentSecondaryBtnText()).apply {
            setOnClickListener { dialog.dismiss() }
        }
        val btnDelete = pillButton("Delete", currentDangerBg(), Color.WHITE).apply {
            setOnClickListener {
                dialog.dismiss()
                deleteForEveryone(message)
            }
        }

        buttonRow.addView(
            btnCancel,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
        )
        buttonRow.addView(
            btnDelete,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        root.addView(buttonRow)

        dialog.show()
    }

    private fun deleteForMe(message: ChatMessage) {
        db.collection("chats").document(conversationId)
            .collection("messages").document(message.id)
            .update("deletedFor", FieldValue.arrayUnion(myEmail))
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteForEveryone(message: ChatMessage) {
        // Wipes the ciphertext as well as the text. Firestore rules allow exactly these four fields.
        db.collection("chats").document(conversationId)
            .collection("messages").document(message.id)
            .update(
                mapOf(
                    "deleted" to true,
                    "text" to "This message was deleted",
                    "ciphertext" to "",
                    "iv" to ""
                )
            )
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ---------------- Adapter ----------------

    class MessageAdapter(
        private val items: List<ChatListItem>,
        private val myEmail: String,
        private var isDarkMode: Boolean,
        private val displayResolver: (ChatMessage) -> MessageDisplay,
        private val onMessageLongClick: (ChatMessage) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            const val VIEW_TYPE_DATE_HEADER = 0
            const val VIEW_TYPE_SENT = 1
            const val VIEW_TYPE_RECEIVED = 2
        }

        fun setDarkMode(dark: Boolean) {
            if (dark != isDarkMode) {
                isDarkMode = dark
                notifyDataSetChanged()
            }
        }

        inner class DateHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvDateHeader: TextView = itemView.findViewById(R.id.tvDateHeader)
        }

        inner class SentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val card: com.google.android.material.card.MaterialCardView = itemView.findViewById(R.id.cardSent)
            val tvText: TextView = itemView.findViewById(R.id.tvMessageText)
            val tvTime: TextView = itemView.findViewById(R.id.tvMessageTime)
        }

        inner class ReceivedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val card: com.google.android.material.card.MaterialCardView = itemView.findViewById(R.id.cardReceived)
            val tvText: TextView = itemView.findViewById(R.id.tvMessageText)
            val tvTime: TextView = itemView.findViewById(R.id.tvMessageTime)
            val tvSender: TextView = itemView.findViewById(R.id.tvSenderName)
        }

        override fun getItemViewType(position: Int): Int {
            return when (val item = items[position]) {
                is ChatListItem.DateHeader -> VIEW_TYPE_DATE_HEADER
                is ChatListItem.MessageItem ->
                    if (item.message.senderId == myEmail) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                VIEW_TYPE_DATE_HEADER ->
                    DateHeaderViewHolder(inflater.inflate(R.layout.item_chat_date_header, parent, false))
                VIEW_TYPE_SENT ->
                    SentViewHolder(inflater.inflate(R.layout.item_message_sent, parent, false))
                else ->
                    ReceivedViewHolder(inflater.inflate(R.layout.item_message_received, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is ChatListItem.DateHeader -> {
                    val h = holder as DateHeaderViewHolder
                    h.tvDateHeader.text = item.label
                    h.tvDateHeader.backgroundTintList = ColorStateList.valueOf(
                        if (isDarkMode) Color.parseColor("#2A3B48") else Color.parseColor("#1B3C53")
                    )
                    h.tvDateHeader.setTextColor(
                        if (isDarkMode) Color.parseColor("#C9D6DE") else Color.parseColor("#F9F3EF")
                    )
                }
                is ChatListItem.MessageItem -> {
                    val message = item.message
                    val displayTimestamp = message.serverTimestamp?.toDate()?.time ?: message.timestamp
                    val timeStr = android.text.format.DateFormat.format("h:mm a", displayTimestamp).toString()
                    val display = try {
                        displayResolver(message)
                    } catch (e: Exception) {
                        MessageDisplay("⚠️ This message could not be shown.", true)
                    }
                    val style = if (display.italic) Typeface.ITALIC else Typeface.NORMAL
                    val timeColor = if (isDarkMode) Color.parseColor("#8FA6B8") else Color.parseColor("#456882")

                    val longClickListener = View.OnLongClickListener {
                        if (!message.deleted) {
                            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onMessageLongClick(message)
                        }
                        true
                    }

                    when (holder) {
                        is SentViewHolder -> {
                            holder.card.setCardBackgroundColor(
                                if (isDarkMode) Color.parseColor("#24405A") else Color.parseColor("#1B3C53")
                            )
                            holder.tvText.setTextColor(
                                if (isDarkMode) Color.parseColor("#F5F7F8") else Color.parseColor("#F9F3EF")
                            )
                            holder.tvText.text = display.text
                            holder.tvText.setTypeface(holder.tvText.typeface, style)
                            holder.tvTime.text = timeStr
                            holder.tvTime.setTextColor(timeColor)
                            holder.itemView.setOnLongClickListener(longClickListener)
                        }
                        is ReceivedViewHolder -> {
                            holder.card.setCardBackgroundColor(
                                if (isDarkMode) Color.parseColor("#16232E") else Color.parseColor("#F9F3EF")
                            )
                            holder.card.strokeColor =
                                if (isDarkMode) Color.parseColor("#2A3B48") else Color.parseColor("#D2C1B6")
                            holder.tvText.setTextColor(
                                if (isDarkMode) Color.parseColor("#F5F7F8") else Color.parseColor("#1B3C53")
                            )
                            holder.tvText.text = display.text
                            holder.tvText.setTypeface(holder.tvText.typeface, style)
                            holder.tvTime.text = timeStr
                            holder.tvTime.setTextColor(timeColor)
                            holder.tvSender.text = message.senderName
                            holder.tvSender.setTextColor(timeColor)
                            holder.itemView.setOnLongClickListener(longClickListener)
                        }
                    }
                }
            }
        }

        override fun getItemCount() = items.size
    }
}