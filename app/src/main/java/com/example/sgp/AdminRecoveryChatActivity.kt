package com.example.sgp

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed class RecoveryItem {
    data class Header(val label: String) : RecoveryItem()
    data class Msg(val m: RecoveryMessage) : RecoveryItem()
}

class AdminRecoveryChatActivity : BaseActivity() {

    private lateinit var requestId: String
    private var request: RecoveryRequest? = null
    private val items = mutableListOf<RecoveryItem>()
    private lateinit var adapter: MsgAdapter

    private lateinit var btnEndChat: View
    private lateinit var layoutPending: View
    private lateinit var tvRequester: TextView
    private lateinit var tvDescription: TextView
    private lateinit var tvMeta: TextView
    private lateinit var ivScreenshot: ImageView
    private lateinit var tvLocked: TextView
    private lateinit var progressBar: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Private conversation: block screenshots / screen recording of this screen.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_admin_recovery_chat)

        val prefs = getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
        if (prefs.getString("user_type", "") != "admin") {
            Toast.makeText(this, "Unauthorized", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        val id = intent.getStringExtra("requestId")
        if (id.isNullOrEmpty()) { finish(); return }
        requestId = id

        btnEndChat = findViewById(R.id.btnEndChat)
        layoutPending = findViewById(R.id.layoutPendingActions)
        tvRequester = findViewById(R.id.tvRequester)
        tvDescription = findViewById(R.id.tvDescription)
        tvMeta = findViewById(R.id.tvMeta)
        ivScreenshot = findViewById(R.id.ivScreenshot)
        tvLocked = findViewById(R.id.tvLocked)
        progressBar = findViewById(R.id.progressBar)

        val rv = findViewById<RecyclerView>(R.id.recyclerViewMessages)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = MsgAdapter(items) { request?.requesterEmail.orEmpty() }
        rv.adapter = adapter

        findViewById<View>(R.id.ivBack).setOnClickListener { finish() }
        btnEndChat.setOnClickListener { confirmEnd() }
        findViewById<View>(R.id.btnStart).setOnClickListener { startSession() }
        findViewById<View>(R.id.btnReject).setOnClickListener { confirmReject() }
        ivScreenshot.setOnClickListener {
            request?.screenshotUrl?.let { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
        }

        loadRequest()

        // If the user revokes (or the 24h limit passes) while this screen is open, lock it.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(20_000)
                    recheck()
                }
            }
        }
    }

    private fun loadRequest() {
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            val res = RecoveryClient.adminGet(requestId)
            val r = res.value
            if (r == null) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@AdminRecoveryChatActivity, RecoveryClient.errorText(res.error), Toast.LENGTH_LONG).show()
                finish(); return@launch
            }
            applyRequest(r)
            if (r.status == "active") loadMessages()
            progressBar.visibility = View.GONE
        }
    }

    private fun applyRequest(r: RecoveryRequest) {
        request = r
        val fmt = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
        tvRequester.text = "${r.requesterEmail}  ↔  ${r.otherEmail}"
        tvDescription.text = r.description
        tvMeta.text = (r.consentAtMillis?.let { "User consent: ${fmt.format(Date(it))}" } ?: "") +
                (r.sessionExpiresMillis?.takeIf { r.status == "active" }
                    ?.let { "\nAccess auto-expires: ${fmt.format(Date(it))}" } ?: "")
        if (r.screenshotUrl != null) {
            ivScreenshot.visibility = View.VISIBLE
            Glide.with(this).load(r.screenshotUrl).into(ivScreenshot)
        } else ivScreenshot.visibility = View.GONE

        layoutPending.visibility = if (r.status == "pending") View.VISIBLE else View.GONE
        btnEndChat.visibility = if (r.status == "active") View.VISIBLE else View.GONE
        if (r.status != "active") lockUi(r.status)
    }

    private fun lockUi(status: String) {
        items.clear()
        adapter.notifyDataSetChanged()
        tvLocked.visibility = View.VISIBLE
        tvLocked.text = when (status) {
            "pending" -> "The user consented to temporary access.\nTap “Start review” to open this chat."
            "rejected" -> "This request was rejected."
            else -> "Access ended. This conversation is no longer available."
        }
    }

    private fun startSession() {
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            val res = RecoveryClient.adminStart(requestId)
            val r = res.value
            if (r == null) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@AdminRecoveryChatActivity, RecoveryClient.errorText(res.error), Toast.LENGTH_LONG).show()
                return@launch
            }
            applyRequest(r)
            loadMessages()
            progressBar.visibility = View.GONE
        }
    }

    private suspend fun loadMessages() {
        progressBar.visibility = View.VISIBLE
        val res = RecoveryClient.adminMessages(requestId)
        progressBar.visibility = View.GONE
        val list = res.value
        if (list == null) {
            if (res.error == "access-ended") recheck()
            else Toast.makeText(this, RecoveryClient.errorText(res.error), Toast.LENGTH_LONG).show()
            return
        }
        tvLocked.visibility = View.GONE
        items.clear()
        var lastLabel: String? = null
        val dayFmt = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
        for (m in list) {
            val label = dayFmt.format(Date(m.timestamp))
            if (label != lastLabel) { items.add(RecoveryItem.Header(label)); lastLabel = label }
            items.add(RecoveryItem.Msg(m))
        }
        adapter.notifyDataSetChanged()
        if (list.isEmpty()) {
            tvLocked.visibility = View.VISIBLE
            tvLocked.text = "This conversation has no messages."
        }
    }

    private suspend fun recheck() {
        val r = RecoveryClient.adminGet(requestId).value ?: return
        val wasActive = request?.status == "active"
        applyRequest(r)
        if (wasActive && r.status != "active") {
            Toast.makeText(this, "Access ended.", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmEnd() {
        val note = EditText(this).apply {
            hint = "Resolution note (optional)"
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle("End chat access?")
            .setMessage("Your access to this conversation will be revoked immediately.")
            .setView(note)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("End Chat") { _, _ ->
                lifecycleScope.launch {
                    val ok = RecoveryClient.adminEnd(requestId, note.text.toString().trim())
                    if (ok) {
                        items.clear(); adapter.notifyDataSetChanged()
                        Toast.makeText(this@AdminRecoveryChatActivity, "Access ended", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@AdminRecoveryChatActivity, "Couldn't end the session. Try again.", Toast.LENGTH_LONG).show()
                    }
                }
            }.show()
    }

    private fun confirmReject() {
        AlertDialog.Builder(this)
            .setTitle("Reject request?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Reject") { _, _ ->
                lifecycleScope.launch {
                    if (RecoveryClient.adminEnd(requestId, "", reject = true)) finish()
                    else Toast.makeText(this@AdminRecoveryChatActivity, "Failed. Try again.", Toast.LENGTH_LONG).show()
                }
            }.show()
    }

    private class MsgAdapter(
        private val items: List<RecoveryItem>,
        private val requesterEmail: () -> String
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private class HeaderVH(v: View) : RecyclerView.ViewHolder(v) {
            val tv: TextView = v.findViewById(R.id.tvDateHeader)
        }

        private class MsgVH(v: View) : RecyclerView.ViewHolder(v) {
            val tvText: TextView = v.findViewById(R.id.tvMessageText)
            val tvTime: TextView = v.findViewById(R.id.tvMessageTime)
            val tvSender: TextView = v.findViewById(R.id.tvSenderName)
        }

        override fun getItemViewType(position: Int) = if (items[position] is RecoveryItem.Header) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inf = LayoutInflater.from(parent.context)
            return if (viewType == 0) HeaderVH(inf.inflate(R.layout.item_chat_date_header, parent, false))
            else MsgVH(inf.inflate(R.layout.item_message_received, parent, false))
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val it = items[position]) {
                is RecoveryItem.Header -> (holder as HeaderVH).tv.text = it.label
                is RecoveryItem.Msg -> {
                    val h = holder as MsgVH
                    val m = it.m
                    h.tvSender.text = m.senderName.ifBlank { m.senderId } +
                            if (m.senderId == requesterEmail()) " · requester" else ""
                    h.tvTime.text = android.text.format.DateFormat.format("h:mm a", m.timestamp).toString()
                    val (text, italic) = when {
                        m.deleted -> "This message was deleted" to true
                        m.note == "undecryptable" -> "⚠️ Could not decrypt this message" to true
                        else -> m.text to false
                    }
                    h.tvText.text = text
                    h.tvText.setTypeface(null, if (italic) Typeface.ITALIC else Typeface.NORMAL)
                    h.itemView.setOnLongClickListener(null)
                }
            }
        }

        override fun getItemCount() = items.size
    }
}