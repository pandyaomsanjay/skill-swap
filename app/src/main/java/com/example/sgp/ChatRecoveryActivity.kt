package com.example.sgp

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatRecoveryActivity : BaseActivity() {

    private lateinit var conversationId: String
    private var screenshotUri: Uri? = null

    private lateinit var cardStatus: MaterialCardView
    private lateinit var cardForm: MaterialCardView
    private lateinit var tvStatus: TextView
    private lateinit var tvIntro: TextView
    private lateinit var btnRevoke: MaterialButton
    private lateinit var btnPick: MaterialButton
    private lateinit var btnSubmit: MaterialButton
    private lateinit var etDescription: TextInputEditText
    private lateinit var cbConsent: MaterialCheckBox
    private lateinit var ivScreenshot: ImageView
    private lateinit var progressBar: ProgressBar

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            screenshotUri = uri
            Glide.with(this).load(uri).into(ivScreenshot)
            ivScreenshot.visibility = View.VISIBLE
            btnPick.setText(R.string.recovery_change_screenshot)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_recovery)

        val cid = intent.getStringExtra("conversationId")
        if (cid.isNullOrEmpty() || FirebaseAuth.getInstance().currentUser == null) {
            finish(); return
        }
        conversationId = cid

        cardStatus = findViewById(R.id.cardStatus)
        cardForm = findViewById(R.id.cardForm)
        tvStatus = findViewById(R.id.tvStatus)
        tvIntro = findViewById(R.id.tvIntro)
        btnRevoke = findViewById(R.id.btnRevoke)
        btnPick = findViewById(R.id.btnPickScreenshot)
        btnSubmit = findViewById(R.id.btnSubmit)
        etDescription = findViewById(R.id.etDescription)
        cbConsent = findViewById(R.id.cbConsent)
        ivScreenshot = findViewById(R.id.ivScreenshot)
        progressBar = findViewById(R.id.progressBar)

        findViewById<View>(R.id.ivBack).setOnClickListener { finish() }
        btnPick.setOnClickListener { pickImage.launch("image/*") }
        btnSubmit.setOnClickListener { onSubmitClicked() }

        applyTheme()
        loadStatus()
    }

    private fun applyTheme() {
        val dark = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(SettingsActivity.KEY_DARK_MODE, false)
        val bg = Color.parseColor(if (dark) "#0F1B24" else "#EAF1F5")
        val card = Color.parseColor(if (dark) "#16232E" else "#FFFFFF")
        val stroke = Color.parseColor(if (dark) "#2A3B48" else "#D2C1B6")
        val text = Color.parseColor(if (dark) "#F5F7F8" else "#1B3C53")
        val sub = Color.parseColor(if (dark) "#8FA6B8" else "#456882")
        findViewById<View>(R.id.rootRecovery).setBackgroundColor(bg)
        for (c in listOf(cardStatus, cardForm)) {
            c.setCardBackgroundColor(card)
            c.strokeColor = stroke
        }
        tvIntro.setTextColor(sub)
        tvStatus.setTextColor(text)
        etDescription.setTextColor(text)
        etDescription.setHintTextColor(sub)
        cbConsent.setTextColor(text)
    }

    private fun setBusy(busy: Boolean) {
        progressBar.visibility = if (busy) View.VISIBLE else View.GONE
        btnSubmit.isEnabled = !busy
        btnRevoke.isEnabled = !busy
    }

    private fun loadStatus() {
        lifecycleScope.launch {
            setBusy(true)
            val open = RecoveryClient.mine(conversationId).value?.firstOrNull { it.isOpen }
            setBusy(false)
            if (open == null) {
                cardStatus.visibility = View.GONE
                cardForm.visibility = View.VISIBLE
            } else {
                showStatus(open)
            }
        }
    }

    private fun showStatus(r: RecoveryRequest) {
        cardForm.visibility = View.GONE
        cardStatus.visibility = View.VISIBLE
        val fmt = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
        tvStatus.text = if (r.status == "active") {
            getString(R.string.recovery_status_active) +
                    (r.sessionExpiresMillis?.let { "\nAccess ends by: ${fmt.format(Date(it))}" } ?: "")
        } else {
            getString(R.string.recovery_status_pending)
        }
        btnRevoke.setOnClickListener {
            lifecycleScope.launch {
                setBusy(true)
                val ok = RecoveryClient.revoke(r.id)
                setBusy(false)
                Toast.makeText(
                    this@ChatRecoveryActivity,
                    if (ok) getString(R.string.recovery_revoked) else getString(R.string.recovery_failed),
                    Toast.LENGTH_LONG
                ).show()
                if (ok) loadStatus()
            }
        }
    }

    private fun onSubmitClicked() {
        val desc = etDescription.text?.toString()?.trim().orEmpty()
        if (desc.isEmpty() || !cbConsent.isChecked) {
            Toast.makeText(this, R.string.recovery_need_details, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.recovery_confirm_title)
            .setMessage(R.string.recovery_confirm_body)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.recovery_allow) { _, _ -> doSubmit(desc) }
            .show()
    }

    private fun doSubmit(desc: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        lifecycleScope.launch {
            setBusy(true)
            var shotUrl: String? = null
            val uri = screenshotUri
            if (uri != null) {
                try {
                    shotUrl = SupabaseImageUploader.uploadRecoveryScreenshot(applicationContext, uri, uid)
                } catch (e: Exception) {
                    setBusy(false)
                    Toast.makeText(this@ChatRecoveryActivity, e.message ?: getString(R.string.recovery_failed), Toast.LENGTH_LONG).show()
                    return@launch
                }
            }
            val res = RecoveryClient.submit(conversationId, desc, shotUrl, cbConsent.isChecked)
            setBusy(false)
            if (res.ok) {
                Toast.makeText(this@ChatRecoveryActivity, R.string.recovery_submitted, Toast.LENGTH_LONG).show()
                loadStatus()
            } else {
                Toast.makeText(this@ChatRecoveryActivity, RecoveryClient.errorText(res.error), Toast.LENGTH_LONG).show()
            }
        }
    }
}