package com.example.sgp

import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed class ChatListItem {
    data class DateHeader(val label: String) : ChatListItem()
    data class MessageItem(val message: ChatMessage) : ChatListItem()
}

class ChatActivity : BaseActivity() {

    companion object {
        private const val TAG = "ChatActivity"
        private const val NOTIFY_FUNCTION_URL =
            "https://ghrxltlstncjcizyyqfo.supabase.co/functions/v1/send-chat-notification"
        private const val DELETE_FOR_EVERYONE_WINDOW_MS = 60 * 60 * 1000L // 1 hour
    }

    private lateinit var db: FirebaseFirestore
    private lateinit var recyclerView: RecyclerView
    private lateinit var etMessage: TextInputEditText
    private lateinit var btnSend: ImageButton
    private lateinit var adapter: MessageAdapter

    private val messages = mutableListOf<ChatMessage>()
    private val chatItems = mutableListOf<ChatListItem>()
    private var messagesListener: ListenerRegistration? = null

    private lateinit var conversationId: String
    private lateinit var myEmail: String
    private lateinit var otherEmail: String
    private var otherName: String = ""
    private var skillId: String? = null
    private var skillTitle: String? = null
    private var myDisplayName: String = ""

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
        toolbar.navigationIcon?.setTint(android.graphics.Color.WHITE)

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
        adapter = MessageAdapter(chatItems, myEmail) { message -> showMessageOptionsDialog(message) }
        recyclerView.adapter = adapter

        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        btnSend.setOnClickListener { sendMessage() }

        loadMyDisplayName {
            ensureConversationExists()
        }
    }

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
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to open chat: ${e.message}", Toast.LENGTH_SHORT).show()
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
                        doc.toObject(ChatMessage::class.java)?.let { freshMessages.add(it) }
                    }

                    // Replace list and sort completely by authoritative time
                    messages.clear()
                    messages.addAll(freshMessages)

                    rebuildDisplayList()
                    adapter.notifyDataSetChanged()
                    if (chatItems.isNotEmpty()) {
                        recyclerView.scrollToPosition(chatItems.size - 1)
                    }
                }
            }
    }

    private fun sendMessage() {
        val text = etMessage.text?.toString()?.trim().orEmpty()
        if (TextUtils.isEmpty(text)) return

        val messagesRef = db.collection("chats").document(conversationId).collection("messages")
        val docRef = messagesRef.document()
        val clientTimestamp = System.currentTimeMillis()

        val optimisticMessage = ChatMessage(
            id = docRef.id,
            senderId = myEmail,
            senderName = myDisplayName,
            receiverId = otherEmail,
            text = text,
            timestamp = clientTimestamp,
            serverTimestamp = null
        )

        etMessage.setText("")

        // Optimistic display insertion
        messages.add(optimisticMessage)
        rebuildDisplayList()
        adapter.notifyDataSetChanged()
        if (chatItems.isNotEmpty()) {
            recyclerView.scrollToPosition(chatItems.size - 1)
        }

        val messageData = hashMapOf(
            "id" to docRef.id,
            "senderId" to myEmail,
            "senderName" to myDisplayName,
            "receiverId" to otherEmail,
            "text" to text,
            "timestamp" to clientTimestamp,
            "serverTimestamp" to FieldValue.serverTimestamp()
        )

        docRef.set(messageData)
            .addOnSuccessListener {
                db.collection("chats").document(conversationId)
                    .set(
                        mapOf(
                            "lastMessage" to text,
                            "lastTimestamp" to clientTimestamp,
                            "lastSenderId" to myEmail,
                            "unreadFor" to listOf(otherEmail)
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                sendPushNotification(text)
            }
            .addOnFailureListener { e ->
                val idx = messages.indexOfFirst { it.id == optimisticMessage.id }
                if (idx != -1) {
                    messages.removeAt(idx)
                    rebuildDisplayList()
                    adapter.notifyDataSetChanged()
                }
                Toast.makeText(this, "Failed to send: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

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

    // ---------------- Ordering & Date Grouping ----------------

    private fun effectiveMillis(message: ChatMessage): Long {
        return message.serverTimestamp?.toDate()?.time ?: message.timestamp
    }

    private fun rebuildDisplayList() {
        chatItems.clear()
        var lastDateLabel: String? = null

        // Sort messages strictly by effective time, falling back to client timestamp
        val sortedList = messages
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

    // ---------------- Delete Message (WhatsApp-style) ----------------

    private fun showMessageOptionsDialog(message: ChatMessage) {
        if (message.deleted) return

        val isMine = message.senderId == myEmail
        val withinDeleteForEveryoneWindow =
            System.currentTimeMillis() - effectiveMillis(message) <= DELETE_FOR_EVERYONE_WINDOW_MS

        val options = mutableListOf("Delete for me")
        if (isMine && withinDeleteForEveryoneWindow) {
            options.add("Delete for everyone")
        }

        AlertDialog.Builder(this)
            .setTitle("Delete message?")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Delete for me" -> deleteForMe(message)
                    "Delete for everyone" -> confirmDeleteForEveryone(message)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteForEveryone(message: ChatMessage) {
        AlertDialog.Builder(this)
            .setTitle("Delete for everyone?")
            .setMessage("This message will be deleted for everyone in this chat.")
            .setPositiveButton("Delete") { _, _ -> deleteForEveryone(message) }
            .setNegativeButton("Cancel", null)
            .show()
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
        db.collection("chats").document(conversationId)
            .collection("messages").document(message.id)
            .update(
                mapOf(
                    "deleted" to true,
                    "text" to "This message was deleted"
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
        private val onMessageLongClick: (ChatMessage) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            const val VIEW_TYPE_DATE_HEADER = 0
            const val VIEW_TYPE_SENT = 1
            const val VIEW_TYPE_RECEIVED = 2
        }

        inner class DateHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvDateHeader: TextView = itemView.findViewById(R.id.tvDateHeader)
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
                    (holder as DateHeaderViewHolder).tvDateHeader.text = item.label
                }
                is ChatListItem.MessageItem -> {
                    val message = item.message
                    val displayTimestamp = message.serverTimestamp?.toDate()?.time ?: message.timestamp
                    val timeStr = android.text.format.DateFormat.format("h:mm a", displayTimestamp).toString()
                    val displayText = if (message.deleted) "This message was deleted" else message.text
                    val style = if (message.deleted) Typeface.ITALIC else Typeface.NORMAL

                    val longClickListener = View.OnLongClickListener {
                        if (!message.deleted) {
                            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onMessageLongClick(message)
                        }
                        true
                    }

                    when (holder) {
                        is SentViewHolder -> {
                            holder.tvText.text = displayText
                            holder.tvText.setTypeface(holder.tvText.typeface, style)
                            holder.tvTime.text = timeStr
                            holder.itemView.setOnLongClickListener(longClickListener)
                        }
                        is ReceivedViewHolder -> {
                            holder.tvText.text = displayText
                            holder.tvText.setTypeface(holder.tvText.typeface, style)
                            holder.tvTime.text = timeStr
                            holder.tvSender.text = message.senderName
                            holder.itemView.setOnLongClickListener(longClickListener)
                        }
                    }
                }
            }
        }

        override fun getItemCount() = items.size
    }
}