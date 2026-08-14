package com.example.sgp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class ChatListActivity : BaseActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ConversationAdapter
    private val conversations = mutableListOf<Conversation>()
    private var listener: ListenerRegistration? = null
    private lateinit var myEmail: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_list)

        val email = FirebaseAuth.getInstance().currentUser?.email
        if (email.isNullOrEmpty()) { finish(); return }
        myEmail = email

        db = FirebaseFirestore.getInstance()

        val toolbar: Toolbar = findViewById(R.id.toolbarChatList) // or toolbarChat
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        toolbar.navigationIcon?.let {
            it.setTint(android.graphics.Color.WHITE)
        }

        recyclerView = findViewById(R.id.recyclerViewChatList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        // No DividerItemDecoration here — item_conversation.xml rows are now
        // MaterialCardViews with their own margin + stroke, so a divider line
        // would just double up against the card border.
        adapter = ConversationAdapter(conversations, myEmail, db) { convo -> openConversation(convo) }
        recyclerView.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fabNewChat).setOnClickListener {
            startActivity(Intent(this, NewChatActivity::class.java))
        }

        listenForConversations()
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }

    private fun listenForConversations() {
        listener = db.collection("chats")
            .whereArrayContains("participants", myEmail)
            .orderBy("lastTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                conversations.clear()
                snapshot?.documents?.forEach { doc ->
                    doc.toObject(Conversation::class.java)?.let {
                        conversations.add(it.copy(id = doc.id))
                    }
                }
                adapter.notifyDataSetChanged()
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
        private val onClick: (Conversation) -> Unit
    ) : RecyclerView.Adapter<ConversationAdapter.ViewHolder>() {

        // Cache email -> photoUrl so we don't re-fetch on every rebind
        private val photoCache = HashMap<String, String>()

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvName: TextView = itemView.findViewById(R.id.tvConvoName)
            val tvLastMessage: TextView = itemView.findViewById(R.id.tvConvoLastMessage)
            val tvTime: TextView = itemView.findViewById(R.id.tvConvoTime)
            val tvInitial: TextView = itemView.findViewById(R.id.tvConvoInitial)
            val ivAvatar: ImageView = itemView.findViewById(R.id.ivConvoAvatar)
            val cardSkillTag: View = itemView.findViewById(R.id.cardSkillTag)
            val tvSkillTag: TextView = itemView.findViewById(R.id.tvSkillTag)
            val viewUnreadDot: View = itemView.findViewById(R.id.viewUnreadDot)
            val viewOnlineDot: View = itemView.findViewById(R.id.viewOnlineDot)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_conversation, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val convo = items[position]
            val otherEmail = convo.participants.firstOrNull { it != myEmail } ?: ""
            val otherName = convo.participantNames[otherEmail] ?: otherEmail
            val isUnread = convo.unreadFor.contains(myEmail)

            holder.tvName.text = otherName
            holder.tvLastMessage.text = convo.lastMessage
            holder.tvTime.text = if (convo.lastTimestamp > 0)
                android.text.format.DateFormat.format("MMM d, h:mm a", convo.lastTimestamp).toString()
            else ""

            holder.tvName.setTypeface(null, if (isUnread) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            holder.tvLastMessage.setTypeface(null, if (isUnread) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)

            // Unread dot next to the last message, instead of relying on bold text alone
            holder.viewUnreadDot.visibility = if (isUnread) View.VISIBLE else View.GONE

            // Skill tag chip: only show when this conversation is tied to a skill
            val skillTitle = convo.skillTitle
            if (!skillTitle.isNullOrBlank()) {
                holder.tvSkillTag.text = skillTitle
                holder.cardSkillTag.visibility = View.VISIBLE
            } else {
                holder.cardSkillTag.visibility = View.GONE
            }

            // Online dot: left hidden for now since Conversation/user docs don't
            // track presence yet. Wire this up if you add an isOnline field.
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
                    // Guard against recycled ViewHolder now showing a different item
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