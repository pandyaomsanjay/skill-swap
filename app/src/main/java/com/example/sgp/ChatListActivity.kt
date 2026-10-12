package com.example.sgp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ChatListActivity : BaseActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ConversationAdapter
    private val conversations = mutableListOf<Conversation>()
    private var listener: ListenerRegistration? = null
    private lateinit var myEmail: String

    // ---------------------------------------------------------------------
    // THEME
    // ---------------------------------------------------------------------
    private lateinit var sharedPreferences: SharedPreferences
    private var isDarkModeEnabled = false

    private val lightScreenBg = Color.parseColor("#EAF1F5")
    private val lightHeaderBg = Color.parseColor("#1B3C53")
    private val lightTextSecondary = Color.parseColor("#456882")

    private val darkScreenBg = Color.parseColor("#0F1B24")
    private val darkHeaderBg = Color.parseColor("#0B1620")
    private val darkTextSecondary = Color.parseColor("#8FA6B8")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_list)

        sharedPreferences = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        isDarkModeEnabled = sharedPreferences.getBoolean(SettingsActivity.KEY_DARK_MODE, false)

        val email = FirebaseAuth.getInstance().currentUser?.email
        if (email.isNullOrEmpty()) { finish(); return }
        myEmail = email

        db = FirebaseFirestore.getInstance()

        val toolbar: Toolbar = findViewById(R.id.toolbarChatList)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        toolbar.navigationIcon?.let {
            it.setTint(Color.WHITE)
        }

        val toolbarBasePaddingTop = toolbar.paddingTop
        val toolbarBasePaddingBottom = toolbar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, toolbarBasePaddingTop + bars.top, v.paddingRight, toolbarBasePaddingBottom)
            insets
        }

        recyclerView = findViewById(R.id.recyclerViewChatList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ConversationAdapter(
            conversations, myEmail, db, isDarkModeEnabled,
            lifecycleScope, ChatKeys.provider(applicationContext)
        ) { convo -> openConversation(convo) }
        recyclerView.adapter = adapter

        val recyclerBasePaddingBottom = recyclerView.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, recyclerBasePaddingBottom + bars.bottom)
            insets
        }

        applyListTheme(isDarkModeEnabled)

        listenForConversations()
    }

    override fun onResume() {
        super.onResume()
        val darkNow = sharedPreferences.getBoolean(SettingsActivity.KEY_DARK_MODE, false)
        if (darkNow != isDarkModeEnabled) {
            isDarkModeEnabled = darkNow
            applyListTheme(isDarkModeEnabled)
            adapter.setDarkMode(isDarkModeEnabled)
        }
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }

    // ---------------------------------------------------------------------
    // THEME APPLICATION
    // ---------------------------------------------------------------------
    private fun applyListTheme(isDark: Boolean) {
        val screenBg = if (isDark) darkScreenBg else lightScreenBg
        val headerBg = if (isDark) darkHeaderBg else lightHeaderBg
        val textSecondary = if (isDark) darkTextSecondary else lightTextSecondary

        findViewById<View>(R.id.rootChatListLayout)?.setBackgroundColor(screenBg)
        findViewById<Toolbar>(R.id.toolbarChatList)?.setBackgroundColor(headerBg)
        findViewById<TextView>(R.id.tvChatListTitle)?.setTextColor(Color.parseColor("#F9F3EF"))
        findViewById<TextView>(R.id.tvChatListSubtitle)?.setTextColor(
            if (isDark) Color.parseColor("#8FA6B8") else Color.parseColor("#C9D6DE")
        )
        recyclerView.setBackgroundColor(screenBg)

        findViewById<ImageView>(R.id.ivEmptyStateIcon)?.imageTintList =
            android.content.res.ColorStateList.valueOf(
                if (isDark) Color.parseColor("#3FA7D6") else Color.parseColor("#1B3C53")
            )
        findViewById<TextView>(R.id.tvEmptyStateText)?.setTextColor(textSecondary)
    }

    private fun listenForConversations() {
        listener = db.collection("chats")
            .whereArrayContains("participants", myEmail)
            .orderBy("lastTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                conversations.clear()
                snapshot?.documents?.forEach { doc ->
                    try {
                        doc.toObject(Conversation::class.java)?.let {
                            conversations.add(it.copy(id = doc.id))
                        }
                    } catch (e: Exception) {
                        // A malformed conversation document must not crash the list.
                    }
                }
                adapter.notifyDataSetChanged()

                val emptyState = findViewById<View>(R.id.layoutEmptyState)
                emptyState?.visibility = if (conversations.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    private fun openConversation(convo: Conversation) {
        val otherEmail = convo.participants.firstOrNull { it != myEmail } ?: return
        val otherName = convo.participantNames[otherEmail] ?: otherEmail
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("otherUserEmail", otherEmail)
        intent.putExtra("otherUserName", otherName)
        intent.putExtra("skillId", convo.skillId)
        intent.putExtra("skillTitle", convo.skillTitle)
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
    }

    class ConversationAdapter(
        private val items: List<Conversation>,
        private val myEmail: String,
        private val db: FirebaseFirestore,
        private var isDarkMode: Boolean,
        private val scope: CoroutineScope,
        private val keys: ConversationKeyProvider,
        private val onClick: (Conversation) -> Unit
    ) : RecyclerView.Adapter<ConversationAdapter.ViewHolder>() {

        private val photoCache = HashMap<String, String>()

        // Key fetches that are running, and a cool-down after failures (offline) so rebinding doesn't hammer the backend.
        private val keyFetchInFlight = HashSet<String>()
        private val keyFetchFailedAt = HashMap<String, Long>()

        fun setDarkMode(dark: Boolean) {
            if (dark != isDarkMode) {
                isDarkMode = dark
                notifyDataSetChanged()
            }
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val cardRoot: com.google.android.material.card.MaterialCardView =
                itemView.findViewById(R.id.cardConversationRoot)
            val cardAvatarBg: com.google.android.material.card.MaterialCardView =
                itemView.findViewById(R.id.cardAvatarBg)
            val tvName: TextView = itemView.findViewById(R.id.tvConvoName)
            val tvLastMessage: TextView = itemView.findViewById(R.id.tvConvoLastMessage)
            val tvTime: TextView = itemView.findViewById(R.id.tvConvoTime)
            val tvInitial: TextView = itemView.findViewById(R.id.tvConvoInitial)
            val ivAvatar: ImageView = itemView.findViewById(R.id.ivConvoAvatar)
            val cardSkillTag: com.google.android.material.card.MaterialCardView =
                itemView.findViewById(R.id.cardSkillTag)
            val tvSkillTag: TextView = itemView.findViewById(R.id.tvSkillTag)
            val viewUnreadDot: View = itemView.findViewById(R.id.viewUnreadDot)
            val viewOnlineDot: View = itemView.findViewById(R.id.viewOnlineDot)
            val ivChevron: ImageView = itemView.findViewById(R.id.ivConvoChevron)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_conversation, parent, false)
            return ViewHolder(view)
        }

        /**
         * Preview text for the list. Never throws. Never shows ciphertext. If the key isn't cached yet it shows a
         * neutral placeholder, fetches the key in the background, and refreshes just that row.
         */
        private fun previewText(convo: Conversation): String {
            if (convo.lastEncVersion == 0) return convo.lastMessage // legacy plaintext preview
            if (convo.lastEncVersion != ENC_VERSION_AES_GCM_V1) return "🔒 Update SkillSwap to read this message"

            val version = convo.lastKeyVersion.coerceAtLeast(1)
            val key = try {
                keys.peek(convo.id, version)
            } catch (e: Exception) {
                null
            }
            if (key == null) {
                prefetchKey(convo, version)
                return "🔒 Encrypted message"
            }
            return when (
                val r = MessageCipher.decrypt(
                    key, convo.lastEncVersion, convo.lastMessageCt, convo.lastMessageIv,
                    MessageCipher.previewAad(convo.id, convo.lastTimestamp)
                )
            ) {
                is DecryptResult.Success -> r.text
                else -> "🔒 Message unavailable"
            }
        }

        private fun prefetchKey(convo: Conversation, version: Int) {
            val failedAt = keyFetchFailedAt[convo.id]
            if (failedAt != null && SystemClock.elapsedRealtime() - failedAt < 30_000) return
            if (!keyFetchInFlight.add(convo.id)) return
            scope.launch {
                val key = keys.getKey(convo.id, version)
                keyFetchInFlight.remove(convo.id)
                if (key == null) {
                    keyFetchFailedAt[convo.id] = SystemClock.elapsedRealtime()
                } else {
                    val idx = items.indexOfFirst { it.id == convo.id }
                    if (idx >= 0) notifyItemChanged(idx)
                }
            }
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val convo = items[position]
            val otherEmail = convo.participants.firstOrNull { it != myEmail } ?: ""
            val otherName = convo.participantNames[otherEmail] ?: otherEmail
            val isUnread = convo.unreadFor.contains(myEmail)

            val cardBg = if (isDarkMode) Color.parseColor("#16232E") else Color.parseColor("#FFFFFF")
            val cardStroke = if (isDarkMode) Color.parseColor("#2A3B48") else Color.parseColor("#D2C1B6")
            val textPrimary = if (isDarkMode) Color.parseColor("#F5F7F8") else Color.parseColor("#1B3C53")
            val textSecondary = if (isDarkMode) Color.parseColor("#8FA6B8") else Color.parseColor("#456882")
            val chevronTint = if (isDarkMode) Color.parseColor("#6E8598") else Color.parseColor("#456882")
            val skillTagBg = if (isDarkMode) Color.parseColor("#26343F") else Color.parseColor("#EAE1DA")
            val skillTagText = if (isDarkMode) Color.parseColor("#C9D6DE") else Color.parseColor("#1B3C53")
            val avatarBg = if (isDarkMode) Color.parseColor("#24405A") else Color.parseColor("#1B3C53")
            val unreadDotColor = if (isDarkMode) Color.parseColor("#3FA7D6") else Color.parseColor("#1B3C53")

            holder.cardRoot.setCardBackgroundColor(cardBg)
            holder.cardRoot.strokeColor = cardStroke
            holder.cardAvatarBg.setCardBackgroundColor(avatarBg)
            holder.ivChevron.imageTintList = android.content.res.ColorStateList.valueOf(chevronTint)
            holder.viewUnreadDot.let {
                if (it is com.google.android.material.card.MaterialCardView) {
                    it.setCardBackgroundColor(unreadDotColor)
                }
            }

            holder.tvName.text = otherName
            holder.tvName.setTextColor(textPrimary)
            holder.tvLastMessage.text = previewText(convo)
            holder.tvLastMessage.setTextColor(textSecondary)
            holder.tvTime.setTextColor(textSecondary)
            holder.tvTime.text = if (convo.lastTimestamp > 0)
                android.text.format.DateFormat.format("MMM d, h:mm a", convo.lastTimestamp).toString()
            else ""

            holder.tvName.setTypeface(null, if (isUnread) Typeface.BOLD else Typeface.NORMAL)
            holder.tvLastMessage.setTypeface(null, if (isUnread) Typeface.BOLD else Typeface.NORMAL)
            holder.viewUnreadDot.visibility = if (isUnread) View.VISIBLE else View.GONE

            val skillTitle = convo.skillTitle
            if (!skillTitle.isNullOrBlank()) {
                holder.tvSkillTag.text = skillTitle
                holder.tvSkillTag.setTextColor(skillTagText)
                holder.cardSkillTag.setCardBackgroundColor(skillTagBg)
                holder.cardSkillTag.visibility = View.VISIBLE
            } else {
                holder.cardSkillTag.visibility = View.GONE
            }

            holder.viewOnlineDot.visibility = View.GONE
            holder.tvInitial.text = otherName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            holder.ivAvatar.visibility = View.GONE

            bindAvatar(holder, otherEmail)

            holder.itemView.setOnClickListener { onClick(convo) }
        }

        private fun bindAvatar(holder: ViewHolder, otherEmail: String) {
            if (otherEmail.isBlank()) return

            val cached = photoCache[otherEmail]
            if (cached != null) {
                showAvatar(holder, cached)
                return
            }

            db.collection("users")
                .whereEqualTo("email", otherEmail)
                .limit(1)
                .get()
                .addOnSuccessListener { snapshot ->
                    val photoUrl = snapshot.documents.firstOrNull()?.getString("profileImage") ?: ""
                    photoCache[otherEmail] = photoUrl
                    if (holder.bindingAdapterPosition != RecyclerView.NO_POSITION &&
                        items.getOrNull(holder.bindingAdapterPosition)?.participants?.contains(otherEmail) == true
                    ) {
                        showAvatar(holder, photoUrl)
                    }
                }
        }

        private fun showAvatar(holder: ViewHolder, photoUrl: String) {
            if (photoUrl.isBlank()) {
                holder.ivAvatar.visibility = View.GONE
                return
            }
            holder.ivAvatar.visibility = View.VISIBLE
            Glide.with(holder.ivAvatar.context)
                .load(photoUrl)
                .circleCrop()
                .into(holder.ivAvatar)
        }

        override fun getItemCount() = items.size
    }
}