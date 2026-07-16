package com.example.sgp

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore

class NotificationsActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NotificationAdapter
    private val notificationList = mutableListOf<Notification>()
    private lateinit var db: FirebaseFirestore
    private lateinit var currentUserId: String
    private var listener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val prefs = getSharedPreferences("SkillSwapPrefs", Context.MODE_PRIVATE)
        currentUserId = prefs.getString("user_email", "") ?: ""

        db = Firebase.firestore

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = NotificationAdapter(notificationList) { notif ->
            Toast.makeText(this, notif.message, Toast.LENGTH_SHORT).show()
        }
        recyclerView.adapter = adapter

        loadNotifications()
    }

    private fun loadNotifications() {
        listener = db.collection("notifications")
            .whereEqualTo("userId", currentUserId)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this@NotificationsActivity, error.message, Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                notificationList.clear()
                snapshot?.documents?.forEach { doc ->
                    doc.toObject(Notification::class.java)?.let { notificationList.add(it) }
                }
                adapter.notifyDataSetChanged()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}

class NotificationAdapter(
    private val notifications: List<Notification>,
    private val onItemClick: (Notification) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val notif = notifications[position]
        holder.tvMessage.text = notif.message
        holder.tvTime.text = android.text.format.DateUtils.getRelativeTimeSpanString(notif.timestamp)
        holder.itemView.setOnClickListener { onItemClick(notif) }
    }

    override fun getItemCount() = notifications.size
}