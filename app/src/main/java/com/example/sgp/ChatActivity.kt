package com.example.sgp

import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
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

class ChatActivity : BaseActivity() {

    companion object {
        private const val TAG = "ChatActivity"

        // Your deployed Supabase Edge Function URL
        private const val NOTIFY_FUNCTION_URL =
            "https://ghrxltlstncjcizyyqfo.supabase.co/functions/v1/send-chat-notification"
    }

    private lateinit var db: FirebaseFirestore
    private lateinit var recyclerView: RecyclerView
    private lateinit var etMessage: TextInputEditText
    private lateinit var btnSend: ImageButton
    private lateinit var adapter: MessageAdapter

    private val messages = mutableListOf<ChatMessage>()
    private var messagesListener: ListenerRegistration? = null

    private lateinit var conversationId: String
    private lateinit var myEmail: String
    private lateinit var otherEmail: String
    private var otherName: String = ""
    private var skillId: String? = null
    private var skillTitle: String? = null

    // My display name pulled from the "users" collection (falls back to email until it loads / if missing)
    private var myDisplayName: String = ""

    // Ktor client reused for the lifetime of this activity, used only to call
    // our Supabase Edge Function that relays push notifications to OneSignal.
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

        val currentEmail = FirebaseAuth.getInstance().currentUser?.email
        if (currentEmail.isNullOrEmpty()) {
            Toast.makeText(this, "Please log in to chat", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        myEmail = currentEmail
        myDisplayName = myEmail // fallback until Firestore lookup finishes

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

        toolbar.navigationIcon?.setTint(android.graphics.Color.WHITE)

        // Apply status bar inset on top of the toolbar's existing padding, since
        // BaseActivity now draws edge-to-edge.
        val toolbarBasePaddingTop = toolbar.paddingTop
        val toolbarBasePaddingBottom = toolbar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, toolbarBasePaddingTop + bars.top, v.paddingRight, toolbarBasePaddingBottom)
            insets
        }

        // Keyboard-covers-input fix: with edge-to-edge on, the system no longer
        // auto-resizes the layout for the IME or the nav bar — push the whole
        // screen up by whichever inset (keyboard or nav bar) is currently larger.
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
        adapter = MessageAdapter(messages, myEmail)
        recyclerView.adapter = adapter

        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        btnSend.setOnClickListener { sendMessage() }

        loadMyDisplayName {
            ensureConversationExists()
        }
    }

    // Listener is attached every time the screen becomes visible, and torn down
    // the moment it's not — this is what guarantees it's always live while open,
    // and never silently stale from a leftover onCreate-only registration.
    override fun onStart() {
        super.onStart()
        listenForMessages()
        markAsRead()
    }

    override fun onStop() {
        super.onStop()
        messagesListener?.remove()
        messagesListener = null
    }

    override fun onDestroy() {
        super.onDestroy()
        httpClient.close()
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }

    /**
     * Looks up my real display name from the "users" collection (doc ID = auth uid).
     */
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
                onDone() // keep the email fallback already set
            }
    }

    /**
     * Creates the parent conversation doc if it doesn't exist yet, without clobbering existing data.
     *
     * IMPORTANT: participants must be stored in a consistent (sorted) order regardless of who
     * opens the chat first — otherwise the Firestore rule's exact-array-equality check on update
     * (`request.resource.data.participants == resource.data.participants`) fails for whichever
     * person opens the chat second, since myEmail/otherEmail swap positions between the two users.
     * That mismatch was causing "PERMISSION_DENIED: Missing or insufficient permissions" for the receiver.
     */
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
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to open chat: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /** Clears the unread flag for me when I open this conversation. */
    private fun markAsRead() {
        db.collection("chats").document(conversationId)
            .update("unreadFor", FieldValue.arrayRemove(myEmail))
    }

    private fun listenForMessages() {
        // Guard against double-attaching if onStart somehow fires twice without an onStop between
        messagesListener?.remove()

        messagesListener = db.collection("chats").document(conversationId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, error.message, Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                messages.clear()
                snapshot?.documents?.forEach { doc ->
                    doc.toObject(ChatMessage::class.java)?.let { messages.add(it) }
                }
                adapter.notifyDataSetChanged()
                if (messages.isNotEmpty()) {
                    recyclerView.scrollToPosition(messages.size - 1)
                }
            }
    }

    private fun sendMessage() {
        val text = etMessage.text?.toString()?.trim().orEmpty()
        if (TextUtils.isEmpty(text)) return

        val messagesRef = db.collection("chats").document(conversationId).collection("messages")
        val docRef = messagesRef.document()
        val message = ChatMessage(
            id = docRef.id,
            senderId = myEmail,
            senderName = myDisplayName,
            receiverId = otherEmail,
            text = text,
            timestamp = System.currentTimeMillis()
        )

        etMessage.setText("")

        // Optimistic UI: show the bubble instantly instead of waiting on the
        // Firestore round trip. The listener's next snapshot will include this
        // same message (same id) and cleanly rebuild the list, so there's no
        // duplicate — this just removes the visible lag on the sender's screen.
        messages.add(message)
        adapter.notifyItemInserted(messages.size - 1)
        recyclerView.scrollToPosition(messages.size - 1)

        docRef.set(message)
            .addOnSuccessListener {
                db.collection("chats").document(conversationId)
                    .set(
                        mapOf(
                            "lastMessage" to text,
                            "lastTimestamp" to message.timestamp,
                            "lastSenderId" to myEmail,
                            "unreadFor" to listOf(otherEmail)
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                sendPushNotification(text)
            }
            .addOnFailureListener { e ->
                // Roll back the optimistic bubble if the write genuinely failed
                val idx = messages.indexOfFirst { it.id == message.id }
                if (idx != -1) {
                    messages.removeAt(idx)
                    adapter.notifyItemRemoved(idx)
                }
                Toast.makeText(this, "Failed to send: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Calls our Supabase Edge Function, which relays a push notification to the
     * recipient via OneSignal. Fire-and-forget: a failure here should never block
     * or roll back the chat message itself, so we only log on error.
     */
    private fun sendPushNotification(text: String) {
        lifecycleScope.launch {
            try {
                val response = httpClient.post(NOTIFY_FUNCTION_URL) {
                    contentType(ContentType.Application.Json)
                    setBody(
                        ChatNotificationRequest(
                            senderId = myEmail,
                            senderName = myDisplayName,
                            receiverId = otherEmail,
                            text = text,
                            conversationId = conversationId
                        )
                    )
                }
                Log.d(TAG, "Push notification response: ${response.status}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send push notification: ${e.message}", e)
            }
        }
    }

    // ---------------- Adapter ----------------

    class MessageAdapter(
        private val messages: List<ChatMessage>,
        private val myEmail: String
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            const val VIEW_TYPE_SENT = 1
            const val VIEW_TYPE_RECEIVED = 2
        }

        inner class SentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvText: TextView = itemView.findViewById(R.id.tvMessageText)
            val tvTime: TextView = itemView.findViewById(R.id.tvMessageTime)
        }

        inner class ReceivedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvText: TextView = itemView.findViewById(R.id.tvMessageText)
            val tvTime: TextView = itemView.findViewById(R.id.tvMessageTime)
            val tvSender: TextView = itemView.findViewById(R.id.tvSenderName)
        }

        override fun getItemViewType(position: Int): Int {
            return if (messages[position].senderId == myEmail) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == VIEW_TYPE_SENT) {
                SentViewHolder(inflater.inflate(R.layout.item_message_sent, parent, false))
            } else {
                ReceivedViewHolder(inflater.inflate(R.layout.item_message_received, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val message = messages[position]
            val timeStr = android.text.format.DateFormat.format("h:mm a", message.timestamp).toString()
            when (holder) {
                is SentViewHolder -> {
                    holder.tvText.text = message.text
                    holder.tvTime.text = timeStr
                }
                is ReceivedViewHolder -> {
                    holder.tvText.text = message.text
                    holder.tvTime.text = timeStr
                    holder.tvSender.text = message.senderName
                }
            }
        }

        override fun getItemCount() = messages.size
    }
}