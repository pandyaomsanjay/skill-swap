package com.example.sgp

import android.content.Intent
import android.graphics.Outline
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

data class SimpleUser(
    val uid: String,
    val email: String,
    val name: String,
    val photoUrl: String = ""
)

class NewChatActivity : BaseActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: UserAdapter
    private lateinit var etSearch: TextInputEditText
    private lateinit var emptyState: View

    // Full unfiltered list fetched from Firestore
    private val allUsers = mutableListOf<SimpleUser>()

    // What the adapter is currently showing (filtered subset of allUsers)
    private val displayedUsers = mutableListOf<SimpleUser>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_chat)

        db = FirebaseFirestore.getInstance()

        val toolbar: Toolbar = findViewById(R.id.toolbarNewChat)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        emptyState = findViewById(R.id.emptyStateNewChat)

        recyclerView = findViewById(R.id.recyclerViewUsers)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )
        adapter = UserAdapter(displayedUsers) { user -> startChatWith(user) }
        recyclerView.adapter = adapter

        etSearch = findViewById(R.id.etSearchUsers)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterUsers(s?.toString().orEmpty())
            }
        })

        loadUsers()
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }

    private fun loadUsers() {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid
        val myEmail = FirebaseAuth.getInstance().currentUser?.email

        db.collection("users").get()
            .addOnSuccessListener { snapshot ->
                allUsers.clear()
                snapshot.documents.forEach { doc ->
                    if (doc.id == myUid) return@forEach // skip self
                    val email = doc.getString("email") ?: return@forEach
                    if (email == myEmail) return@forEach // skip self (belt and suspenders)
                    val name = doc.getString("name") ?: doc.getString("displayName") ?: email
                    val photoUrl = doc.getString("profileImage") ?: ""
                    allUsers.add(SimpleUser(doc.id, email, name, photoUrl))
                }
                filterUsers(etSearch.text?.toString().orEmpty())
            }
    }

    /** Filters allUsers by name or email (case-insensitive) into displayedUsers and refreshes the list. */
    private fun filterUsers(query: String) {
        val trimmed = query.trim()
        val filtered = if (trimmed.isEmpty()) {
            allUsers
        } else {
            allUsers.filter {
                it.name.contains(trimmed, ignoreCase = true) ||
                        it.email.contains(trimmed, ignoreCase = true)
            }
        }

        displayedUsers.clear()
        displayedUsers.addAll(filtered)
        adapter.notifyDataSetChanged()

        val showEmpty = displayedUsers.isEmpty()
        emptyState.visibility = if (showEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (showEmpty) View.GONE else View.VISIBLE
    }

    private fun startChatWith(user: SimpleUser) {
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("otherUserEmail", user.email)
        intent.putExtra("otherUserName", user.name)
        // no skillId/skillTitle -> general chat, not tied to any video
        startActivity(intent)
        finish()
    }

    class UserAdapter(
        private val items: List<SimpleUser>,
        private val onClick: (SimpleUser) -> Unit
    ) : RecyclerView.Adapter<UserAdapter.ViewHolder>() {

        // Turns any View into a circle by clipping its outline to an oval matching its bounds
        private val circleOutlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvName: TextView = itemView.findViewById(R.id.tvUserName)
            val tvEmail: TextView = itemView.findViewById(R.id.tvUserEmail)
            val tvInitial: TextView = itemView.findViewById(R.id.tvUserInitial)
            val ivAvatar: ImageView = itemView.findViewById(R.id.ivUserAvatar)
            val avatarContainer: FrameLayout = itemView.findViewById(R.id.avatarContainer)

            init {
                avatarContainer.outlineProvider = circleOutlineProvider
                avatarContainer.clipToOutline = true
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_user, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val user = items[position]
            holder.tvName.text = user.name
            holder.tvEmail.text = user.email
            holder.tvInitial.text = user.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

            if (user.photoUrl.isNotBlank()) {
                holder.ivAvatar.visibility = View.VISIBLE
                Glide.with(holder.ivAvatar.context)
                    .load(user.photoUrl)
                    .into(holder.ivAvatar)
            } else {
                holder.ivAvatar.visibility = View.GONE
            }

            holder.itemView.setOnClickListener { onClick(user) }
        }

        override fun getItemCount() = items.size
    }
}