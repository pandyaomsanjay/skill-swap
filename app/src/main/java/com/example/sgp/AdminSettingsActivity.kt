package com.example.sgp

import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminSettingsActivity : BaseActivity() {

    private lateinit var db: FirebaseFirestore

    private var pendingExport: (() -> Unit)? = null
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingExport?.invoke()
        } else {
            Toast.makeText(this, "Storage permission is required to export", Toast.LENGTH_SHORT).show()
        }
        pendingExport = null
    }

    // Same dark palette used by AdminReportsActivity's bottom sheets, so all admin
    // screens feel consistent.
    private val sheetBg = Color.parseColor("#16263A")
    private val sheetDivider = Color.parseColor("#28405A")
    private val sheetPrimaryText = Color.parseColor("#F5EDE4")
    private val sheetSecondaryText = Color.parseColor("#9FB3C8")

    private fun runWithStoragePermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            action()
            return
        }
        val permission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingExport = action
            storagePermissionLauncher.launch(permission)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_settings)

        db = Firebase.firestore

        // FIX: top-left is now a back button that returns to the Admin
        // Dashboard, replacing the old static settings-gear icon that had
        // no click handler.
        findViewById<View>(R.id.btnBack).setOnClickListener {
            val intent = Intent(this, AdminDashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            overridePendingTransition(R.anim.nav_enter, R.anim.nav_exit)
            finish()
        }

        bindProfile()
        bindGeneralSection()
        bindSecuritySection()
        bindDataExportSection()
        bindLogoutSection()
    }

    private fun bindProfile() {
        val tvAdminName = findViewById<TextView>(R.id.tvAdminName)
        val tvAdminEmail = findViewById<TextView>(R.id.tvAdminEmail)

        val user = FirebaseAuth.getInstance().currentUser
        tvAdminName.text = user?.displayName?.takeIf { it.isNotBlank() } ?: "Admin"
        tvAdminEmail.text = user?.email ?: "admin@skillswap.com"
    }

    private fun bindGeneralSection() {
        findViewById<MaterialCardView>(R.id.cardManageSkills).setOnClickListener {
            startActivity(Intent(this, AdminSkillsActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardSendNotification).setOnClickListener {
            showSendNotificationPicker()
        }
    }

    private fun bindSecuritySection() {
        findViewById<MaterialCardView>(R.id.cardAdminManagement).setOnClickListener {
            showAdminManagementDialog()
        }

        val cardChangePassword = findViewById<MaterialCardView>(R.id.cardChangePassword)
        val isEmailPasswordAdmin = FirebaseAuth.getInstance().currentUser
            ?.providerData
            ?.any { it.providerId == EmailAuthProvider.PROVIDER_ID } == true

        if (isEmailPasswordAdmin) {
            cardChangePassword.visibility = View.VISIBLE
            cardChangePassword.setOnClickListener { showChangePasswordDialog() }
        } else {
            // Google-signed-in admins manage their password through Google, not here.
            cardChangePassword.visibility = View.GONE
        }
    }

    private fun bindDataExportSection() {
        // "Export Reports (PDF)" now opens a picker: Reports Summary or Users List,
        // matching the same choice available on the Manage Reports screen.
        findViewById<MaterialCardView>(R.id.cardExportReportsPdf).setOnClickListener {
            showReportsExportOptionsDialog()
        }
        findViewById<MaterialCardView>(R.id.cardExportFeedbackPdf).setOnClickListener {
            runWithStoragePermission { exportFeedbackToPdf() }
        }
    }

    private fun bindLogoutSection() {
        findViewById<MaterialCardView>(R.id.cardLogout).setOnClickListener {
            confirmLogout()
        }
    }

    private fun confirmLogout() {
        val root = dialogCard()

        root.addView(TextView(this).apply {
            text = "🚪"
            textSize = 30f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        })
        root.addView(TextView(this).apply {
            text = "Log Out"
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Are you sure you want to log out?"
            setTextColor(Color.parseColor("#456882"))
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
                bottomMargin = dp(18)
            }
        })

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val buttonRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnCancel = pillButton("Cancel", Color.parseColor("#EAF1F5"), Color.parseColor("#456882")).apply {
            setOnClickListener { dialog.dismiss() }
        }
        val btnLogout = pillButton("Log Out", Color.parseColor("#DC2626"), Color.WHITE).apply {
            setOnClickListener {
                dialog.dismiss()
                FirebaseAuth.getInstance().signOut()
                getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE).edit().clear().apply()
                val intent = Intent(this@AdminSettingsActivity, Login::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            }
        }
        buttonRow.addView(btnCancel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        buttonRow.addView(btnLogout, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(buttonRow)

        dialog.show()
    }


    // ---------------- Send Notification: multi-select users, then title + message ----------------

    private fun showSendNotificationPicker() {
        val root = dialogCard()
        root.addView(dialogTitle("Send Notification"))
        root.addView(dialogDivider())

        val searchInput = EditText(this).apply {
            hint = "Search by name or email"
            setHintTextColor(Color.parseColor("#9AA7B0"))
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 14f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#EAF1F5"))
                setStroke(dp(1), Color.parseColor("#D2C1B6"))
            }
        }
        root.addView(
            searchInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        )

        val resultsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(260))
            addView(resultsContainer)
        }
        root.addView(scroll)

        // Selected-count + Next button, shown once at least one user is selected.
        val selectedBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }
        val tvSelectedCount = TextView(this).apply {
            setTextColor(Color.parseColor("#456882"))
            textSize = 12.5f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnNext = pillButton("Next", Color.parseColor("#1B3C53"), Color.WHITE).apply {
            layoutParams = LinearLayout.LayoutParams(dp(90), LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(dp(18), dp(10), dp(18), dp(10))
        }
        selectedBar.addView(tvSelectedCount)
        selectedBar.addView(btnNext)
        root.addView(selectedBar)

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        var cachedUsers: List<User> = emptyList()
        // Keyed by uid (falls back to email if uid is blank) so re-renders keep selection state.
        val selectedUsers = LinkedHashMap<String, User>()

        fun userKey(u: User) = u.uid.ifBlank { u.email }

        fun updateSelectedBar() {
            if (selectedUsers.isEmpty()) {
                selectedBar.visibility = View.GONE
            } else {
                selectedBar.visibility = View.VISIBLE
                tvSelectedCount.text = "${selectedUsers.size} user${if (selectedUsers.size == 1) "" else "s"} selected"
            }
        }

        fun renderResults(query: String) {
            resultsContainer.removeAllViews()
            val q = query.trim().lowercase(Locale.getDefault())
            val matches = cachedUsers.filter {
                q.isEmpty() ||
                        it.name.lowercase(Locale.getDefault()).contains(q) ||
                        it.email.lowercase(Locale.getDefault()).contains(q)
            }.take(30)

            if (matches.isEmpty()) {
                resultsContainer.addView(TextView(this).apply {
                    text = if (cachedUsers.isEmpty()) "Loading users…" else "No users found"
                    setTextColor(Color.parseColor("#9AA7B0"))
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setPadding(0, dp(20), 0, dp(20))
                })
                return
            }

            matches.forEach { user ->
                val key = userKey(user)
                resultsContainer.addView(
                    buildUserPickRow(user, isSelected = selectedUsers.containsKey(key)) {
                        if (selectedUsers.containsKey(key)) {
                            selectedUsers.remove(key)
                        } else {
                            selectedUsers[key] = user
                        }
                        renderResults(searchInput.text?.toString() ?: "")
                        updateSelectedBar()
                    }
                )
            }
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderResults(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnNext.setOnClickListener {
            if (selectedUsers.isEmpty()) return@setOnClickListener
            dialog.dismiss()
            showComposeNotificationDialog(selectedUsers.values.toList())
        }

        renderResults("")
        dialog.show()

        db.collection("users").get()
            .addOnSuccessListener { snapshot ->
                cachedUsers = snapshot.documents.mapNotNull { it.toObject(User::class.java) }
                renderResults(searchInput.text?.toString() ?: "")
            }
            .addOnFailureListener {
                resultsContainer.removeAllViews()
                resultsContainer.addView(TextView(this).apply {
                    text = "Failed to load users"
                    setTextColor(Color.parseColor("#DC2626"))
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setPadding(0, dp(20), 0, dp(20))
                })
            }
    }

    private fun buildUserPickRow(user: User, isSelected: Boolean, onToggle: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(dp(4), dp(10), dp(4), dp(10))
            val outValue = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            setOnClickListener { onToggle() }
        }

        val initial = user.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        row.addView(MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            radius = dp(18).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.parseColor("#EAE1DA"))
            addView(TextView(this@AdminSettingsActivity).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                gravity = Gravity.CENTER
                text = initial
                setTextColor(Color.parseColor("#1B3C53"))
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
            })
        })

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(10)
            }
        }
        textCol.addView(TextView(this).apply {
            text = user.name.ifBlank { "No name set" }
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 13.5f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        textCol.addView(TextView(this).apply {
            text = user.email
            setTextColor(Color.parseColor("#456882"))
            textSize = 11.5f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        row.addView(textCol)

        // Selection indicator (checkbox-style circle)
        row.addView(MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginStart = dp(8) }
            radius = dp(12).toFloat()
            cardElevation = 0f
            if (isSelected) {
                setCardBackgroundColor(Color.parseColor("#1B3C53"))
                strokeWidth = 0
            } else {
                setCardBackgroundColor(Color.parseColor("#FFFFFF"))
                strokeWidth = dp(1)
                strokeColor = Color.parseColor("#D2C1B6")
            }
            addView(TextView(this@AdminSettingsActivity).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                gravity = Gravity.CENTER
                text = if (isSelected) "✓" else ""
                setTextColor(Color.WHITE)
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
            })
        })

        return row
    }

    private fun showComposeNotificationDialog(users: List<User>) {
        val root = dialogCard()
        val heading = if (users.size == 1)
            "Notify ${users[0].name.ifBlank { users[0].email }}"
        else
            "Notify ${users.size} users"
        root.addView(dialogTitle(heading))

        val titleInput = EditText(this).apply {
            hint = "Notification title"
            setHintTextColor(Color.parseColor("#9AA7B0"))
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 14f
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#EAF1F5"))
                setStroke(dp(1), Color.parseColor("#D2C1B6"))
            }
        }
        root.addView(
            titleInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(14)
                bottomMargin = dp(10)
            }
        )

        val messageInput = EditText(this).apply {
            hint = "Write your message"
            setHintTextColor(Color.parseColor("#9AA7B0"))
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 14f
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#EAF1F5"))
                setStroke(dp(1), Color.parseColor("#D2C1B6"))
            }
        }
        root.addView(
            messageInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(18) }
        )

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val buttonRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnCancel = pillButton("Cancel", Color.parseColor("#EAF1F5"), Color.parseColor("#456882")).apply {
            setOnClickListener { dialog.dismiss() }
        }
        // Call site — inside showComposeNotificationDialog's btnSend click listener
        val btnSend = pillButton("Send", Color.parseColor("#1B3C53"), Color.WHITE).apply {
            setOnClickListener {
                val title = titleInput.text.toString().trim()
                val message = messageInput.text.toString().trim()
                if (title.isEmpty()) {
                    Toast.makeText(this@AdminSettingsActivity, "Title can't be empty", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (message.isEmpty()) {
                    Toast.makeText(this@AdminSettingsActivity, "Message can't be empty", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                dialog.dismiss()
                sendPushNotification(users, title, message)
            }
        }
        buttonRow.addView(
            btnCancel,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
        )
        buttonRow.addView(btnSend, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(buttonRow)

        dialog.show()
    }

    // FIX: signature takes (users: List<User>, title: String, message: String) —
// must match the 3-arg call above exactly. If your file still has an older
// version with (user: User, message: String) or (users: List<User>, message: String),
// that mismatch is the red underline.
    private fun sendPushNotification(users: List<User>, title: String, message: String) {
        val usersWithoutToken = users.count { it.fcmToken.isBlank() }
        if (usersWithoutToken > 0) {
            Toast.makeText(
                this,
                "$usersWithoutToken user(s) have no device token on file",
                Toast.LENGTH_SHORT
            ).show()
        }

        val targetUids = users.map { it.uid }.filter { it.isNotBlank() }
        val userEmails = users.map { it.email }.filter { it.isNotBlank() }

        val notification = hashMapOf(
            "userIds" to userEmails,
            "targetUids" to targetUids,
            "title" to title,
            "message" to message,
            "timestamp" to System.currentTimeMillis(),
            "sentByAdmin" to true,
            "recipientCount" to users.size
        )

        db.collection("notifications").add(notification)
            .addOnSuccessListener {
                Toast.makeText(
                    this,
                    "Notification sent to ${users.size} user(s)",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to send notification: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }


    private fun showAdminManagementDialog() {
        val root = dialogCard()
        root.addView(dialogTitle("Admin Management"))
        root.addView(dialogDivider())

        val listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listContainer.addView(TextView(this).apply {
            text = "Loading admins…"
            setTextColor(Color.parseColor("#9AA7B0"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(20))
        })

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(320))
            addView(listContainer)
        }
        root.addView(scroll)

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnClose = pillButton("Close", Color.parseColor("#1B3C53"), Color.WHITE).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(16)
                gravity = Gravity.END
            }
            setPadding(dp(28), dp(10), dp(28), dp(10))
            setOnClickListener { dialog.dismiss() }
        }
        root.addView(btnClose)

        dialog.show()

        db.collection("users").whereEqualTo("userType", "admin").get()
            .addOnSuccessListener { snapshot ->
                listContainer.removeAllViews()
                val admins = snapshot.documents.mapNotNull { it.toObject(User::class.java) }
                if (admins.isEmpty()) {
                    listContainer.addView(TextView(this).apply {
                        text = "No admin accounts found"
                        setTextColor(Color.parseColor("#9AA7B0"))
                        textSize = 13f
                        gravity = Gravity.CENTER
                        setPadding(0, dp(20), 0, dp(20))
                    })
                } else {
                    admins.forEachIndexed { index, admin ->
                        listContainer.addView(buildAdminInfoBlock(admin))
                        if (index != admins.lastIndex) listContainer.addView(dialogDivider())
                    }
                }
            }
            .addOnFailureListener {
                listContainer.removeAllViews()
                listContainer.addView(TextView(this).apply {
                    text = "Failed to load admins"
                    setTextColor(Color.parseColor("#DC2626"))
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setPadding(0, dp(20), 0, dp(20))
                })
            }
    }

    private fun buildAdminInfoBlock(admin: User): View {
        val block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        block.addView(TextView(this).apply {
            text = admin.name.ifBlank { "No name set" }
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 14.5f
            setTypeface(typeface, Typeface.BOLD)
        })
        block.addView(TextView(this).apply {
            text = admin.email
            setTextColor(Color.parseColor("#456882"))
            textSize = 12.5f
            setPadding(0, dp(2), 0, 0)
        })
        if (admin.phone.isNotBlank()) {
            block.addView(TextView(this).apply {
                text = "📱 ${admin.phone}"
                setTextColor(Color.parseColor("#9AA7B0"))
                textSize = 11.5f
                setPadding(0, dp(4), 0, 0)
            })
        }
        if (admin.joinedDate > 0) {
            block.addView(TextView(this).apply {
                text = "Joined ${SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(admin.joinedDate))}"
                setTextColor(Color.parseColor("#9AA7B0"))
                textSize = 11.5f
                setPadding(0, dp(2), 0, 0)
            })
        }
        return block
    }


    private fun showChangePasswordDialog() {
        val root = dialogCard()
        root.addView(dialogTitle("Change Password"))

        val currentPasswordInput = passwordField("Current password")
        val newPasswordInput = passwordField("New password")
        val confirmPasswordInput = passwordField("Confirm new password")

        listOf(currentPasswordInput, newPasswordInput, confirmPasswordInput).forEachIndexed { index, field ->
            root.addView(
                field,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = if (index == 0) dp(14) else dp(10)
                }
            )
        }

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(18) }
        }
        val btnCancel = pillButton("Cancel", Color.parseColor("#EAF1F5"), Color.parseColor("#456882")).apply {
            setOnClickListener { dialog.dismiss() }
        }
        val btnUpdate = pillButton("Update", Color.parseColor("#1B3C53"), Color.WHITE).apply {
            setOnClickListener {
                val current = currentPasswordInput.text.toString()
                val newPass = newPasswordInput.text.toString()
                val confirm = confirmPasswordInput.text.toString()

                if (current.isBlank() || newPass.isBlank() || confirm.isBlank()) {
                    Toast.makeText(this@AdminSettingsActivity, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (newPass.length < 6) {
                    Toast.makeText(
                        this@AdminSettingsActivity,
                        "New password must be at least 6 characters",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                if (newPass != confirm) {
                    Toast.makeText(this@AdminSettingsActivity, "New passwords don't match", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val user = FirebaseAuth.getInstance().currentUser
                val email = user?.email
                if (user == null || email.isNullOrBlank()) {
                    Toast.makeText(
                        this@AdminSettingsActivity,
                        "No signed-in email/password admin found",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                val credential = EmailAuthProvider.getCredential(email, current)
                user.reauthenticate(credential)
                    .addOnSuccessListener {
                        user.updatePassword(newPass)
                            .addOnSuccessListener {
                                dialog.dismiss()
                                Toast.makeText(this@AdminSettingsActivity, "Password updated", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(
                                    this@AdminSettingsActivity,
                                    "Failed to update password: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this@AdminSettingsActivity, "Current password is incorrect", Toast.LENGTH_SHORT).show()
                    }
            }
        }
        buttonRow.addView(
            btnCancel,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
        )
        buttonRow.addView(btnUpdate, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(buttonRow)

        dialog.show()
    }

    private fun passwordField(hintText: String): EditText {
        return EditText(this).apply {
            hint = hintText
            setHintTextColor(Color.parseColor("#9AA7B0"))
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#EAF1F5"))
                setStroke(dp(1), Color.parseColor("#D2C1B6"))
            }
        }
    }

    // ---------------- Export picker: Reports Summary vs Users List ----------------

    private fun showReportsExportOptionsDialog() {
        val dialog = BottomSheetDialog(this, R.style.DarkBottomSheetDialog)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(12))
            background = GradientDrawable().apply {
                val r = dp(20).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
                setColor(sheetBg)
            }
        }

        // ---- Header ----
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        header.addView(TextView(this).apply {
            text = "⬇️"
            textSize = 18f
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_status_trade_accepted)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        })
        header.addView(TextView(this).apply {
            text = "Export PDF"
            setTextColor(sheetPrimaryText)
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(14)
            }
        })
        root.addView(header)

        // ---- Divider ----
        root.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            setBackgroundColor(sheetDivider)
        })

        fun addRow(emoji: String, label: String, subtitle: String, action: () -> Unit) {
            val outValue = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                setPadding(dp(20), dp(14), dp(20), dp(14))
                setBackgroundResource(outValue.resourceId)
                setOnClickListener {
                    dialog.dismiss()
                    action()
                }
            }
            row.addView(TextView(this).apply {
                text = emoji
                textSize = 18f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(28), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(14) }
            }
            textCol.addView(TextView(this).apply {
                text = label
                textSize = 15f
                setTextColor(sheetPrimaryText)
            })
            textCol.addView(TextView(this).apply {
                text = subtitle
                textSize = 12f
                setTextColor(sheetSecondaryText)
                setPadding(0, dp(2), 0, 0)
            })
            row.addView(textCol)
            root.addView(row)
        }

        addRow("🚩", "Reports Summary", "Export all reports with status & reason") {
            runWithStoragePermission { exportReportsSummaryToPdf() }
        }
        addRow("👥", "Users List", "Export all registered users & their skills") {
            runWithStoragePermission { exportUsersToPdf() }
        }

        dialog.setContentView(root)
        dialog.show()
    }

    // ---------------- Export 1: Reports Summary PDF ----------------

    private fun exportReportsSummaryToPdf() {
        db.collection("reports").get()
            .addOnSuccessListener { reportsSnapshot ->
                if (reportsSnapshot.isEmpty) {
                    Toast.makeText(this, "No reports to export", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val reports = reportsSnapshot.documents.mapNotNull { doc ->
                    doc.toObject(Report::class.java)?.let { r ->
                        if (r.id.isBlank()) r.copy(id = doc.id) else r
                    }
                }

                // reports store reportedUserId as an email, while users/{uid} docs are
                // keyed by uid, so build an email -> Users map to resolve display names.
                db.collection("users").get()
                    .addOnSuccessListener { usersSnapshot ->
                        val emailToUser = mutableMapOf<String, Users>()
                        usersSnapshot.documents.forEach { doc ->
                            val user = doc.toObject(Users::class.java) ?: return@forEach
                            if (user.email.isNotBlank()) emailToUser[user.email] = user
                        }
                        generateReportsSummaryPdf(reports, emailToUser)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to load users: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load reports: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun generateReportsSummaryPdf(reports: List<Report>, emailToUser: Map<String, Users>) {
        val fileName = "SkillSwap_Reports.pdf"
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        val pageWidth = 842
        val pageHeight = 595
        val leftMargin = 24f
        val bottomMargin = pageHeight - 30f
        val rowHeight = 22f
        val headerRowHeight = 24f
        val bannerHeight = 70f

        val headers = listOf("Report ID", "User Name", "Reason", "Status", "Date")
        val tableWidth = pageWidth - (leftMargin * 2)
        val columnWeights = listOf(0.16f, 0.2f, 0.34f, 0.12f, 0.18f)
        val columnWidths = columnWeights.map { it * tableWidth }

        val brandColor = Color.parseColor("#1B3C53")
        val brandColorDark = Color.parseColor("#102838")

        val bannerBgPaint = Paint().apply { color = brandColor; style = Paint.Style.FILL }
        val bannerAccentPaint = Paint().apply { color = brandColorDark; style = Paint.Style.FILL }
        val titlePaint = Paint().apply {
            textSize = 22f; isFakeBoldText = true
            color = Color.parseColor("#F9F3EF"); textAlign = Paint.Align.CENTER
        }
        val subtitlePaint = Paint().apply {
            textSize = 11f; color = Color.parseColor("#CBD8E1"); textAlign = Paint.Align.CENTER
        }
        val headerPaint = Paint().apply {
            textSize = 10f; isFakeBoldText = true; color = Color.parseColor("#F9F3EF")
        }
        val headerBgPaint = Paint().apply { color = brandColor; style = Paint.Style.FILL }
        val bodyPaint = Paint().apply { textSize = 9f; color = Color.parseColor("#1B3C53") }
        val borderPaint = Paint().apply {
            color = Color.parseColor("#CCCCCC"); style = Paint.Style.STROKE; strokeWidth = 0.7f
        }
        val altRowBgPaint = Paint().apply { color = Color.parseColor("#EAF1F5"); style = Paint.Style.FILL }
        val footerPaint = Paint().apply {
            textSize = 8f; color = Color.parseColor("#9CA3AF"); textAlign = Paint.Align.CENTER
        }

        val pdfDocument = PdfDocument()
        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y: Float

        fun drawBanner() {
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), bannerHeight, bannerBgPaint)
            canvas.drawRect(0f, bannerHeight, pageWidth.toFloat(), bannerHeight + 4f, bannerAccentPaint)
            val centerX = pageWidth / 2f
            canvas.drawText("SkillSwap — Reports Summary", centerX, bannerHeight / 2f, titlePaint)
            canvas.drawText("Generated: ${dateFormat.format(Date())}", centerX, bannerHeight / 2f + 20f, subtitlePaint)
        }

        fun drawTableHeader(startY: Float): Float {
            var x = leftMargin
            var localY = startY
            canvas.drawRect(leftMargin, localY, leftMargin + tableWidth, localY + headerRowHeight, headerBgPaint)
            headers.forEachIndexed { i, header ->
                canvas.drawText(header, x + 4f, localY + headerRowHeight - 7f, headerPaint)
                canvas.drawRect(x, localY, x + columnWidths[i], localY + headerRowHeight, borderPaint)
                x += columnWidths[i]
            }
            return localY + headerRowHeight
        }

        fun drawFooter() {
            canvas.drawText("Page $pageNumber", pageWidth / 2f, pageHeight - 12f, footerPaint)
        }

        fun truncate(text: String, maxWidth: Float, paint: Paint): String {
            if (paint.measureText(text) <= maxWidth) return text
            var end = text.length
            while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
            return text.substring(0, end) + "…"
        }

        fun newPage() {
            drawFooter()
            pdfDocument.finishPage(page)
            pageNumber++
            page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            drawBanner()
            y = drawTableHeader(bannerHeight + 24f)
        }

        drawBanner()
        y = drawTableHeader(bannerHeight + 24f)

        val sortedReports = reports.sortedByDescending { it.timestamp }
        sortedReports.forEachIndexed { rowIndex, report ->
            if (y + rowHeight > bottomMargin) newPage()

            val userName = emailToUser[report.reportedUserId]?.name ?: report.reportedUserId
            val rowValues = listOf(
                report.id,
                userName,
                report.reason,
                report.status,
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(report.timestamp))
            )

            if (rowIndex % 2 == 1) {
                canvas.drawRect(leftMargin, y, leftMargin + tableWidth, y + rowHeight, altRowBgPaint)
            }

            var x = leftMargin
            rowValues.forEachIndexed { i, value ->
                val cellPadding = 4f
                val maxTextWidth = columnWidths[i] - (cellPadding * 2)
                val displayText = truncate(value, maxTextWidth, bodyPaint)
                canvas.drawText(displayText, x + cellPadding, y + rowHeight - 7f, bodyPaint)
                canvas.drawRect(x, y, x + columnWidths[i], y + rowHeight, borderPaint)
                x += columnWidths[i]
            }
            y += rowHeight
        }

        drawFooter()
        pdfDocument.finishPage(page)

        try {
            val outputStream = openDownloadsOutputStream(fileName, "application/pdf")
            outputStream?.use { pdfDocument.writeTo(it) }
            Toast.makeText(this, "Saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            pdfDocument.close()
        }
    }

    // ---------------- Export 2: Users List PDF ----------------

    private fun exportUsersToPdf() {
        db.collection("users").get()
            .addOnSuccessListener { usersSnapshot ->
                if (usersSnapshot.isEmpty) {
                    Toast.makeText(this, "No users to export", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                db.collection("trades").get()
                    .addOnSuccessListener { tradesSnapshot ->
                        val completedCountByEmail = mutableMapOf<String, Int>()
                        tradesSnapshot.documents.forEach { doc ->
                            val status = doc.getString("status") ?: ""
                            if (!status.equals("completed", ignoreCase = true)) return@forEach
                            val requesterId = doc.getString("requesterId")
                            val receiverId = doc.getString("receiverId")
                            requesterId?.let { completedCountByEmail[it] = (completedCountByEmail[it] ?: 0) + 1 }
                            receiverId?.let { completedCountByEmail[it] = (completedCountByEmail[it] ?: 0) + 1 }
                        }

                        val users = usersSnapshot.documents.mapNotNull { it.toObject(Users::class.java) }
                        generateUsersPdf(users, completedCountByEmail)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to load trades: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load users: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun generateUsersPdf(users: List<Users>, completedCountByEmail: Map<String, Int>) {
        val fileName = "SkillSwap_Users.pdf"
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        val pageWidth = 842
        val pageHeight = 595
        val leftMargin = 24f
        val bottomMargin = pageHeight - 30f
        val rowHeight = 22f
        val headerRowHeight = 24f
        val bannerHeight = 70f

        val headers = listOf(
            "Name", "Email", "Phone", "Skill I Can Teach",
            "Skill I Want To Learn", "Trades", "Review"
        )
        val tableWidth = pageWidth - (leftMargin * 2)
        val columnWeights = listOf(0.13f, 0.22f, 0.11f, 0.19f, 0.19f, 0.08f, 0.08f)
        val columnWidths = columnWeights.map { it * tableWidth }

        val brandColor = Color.parseColor("#1B3C53")
        val brandColorDark = Color.parseColor("#102838")

        val bannerBgPaint = Paint().apply { color = brandColor; style = Paint.Style.FILL }
        val bannerAccentPaint = Paint().apply { color = brandColorDark; style = Paint.Style.FILL }
        val titlePaint = Paint().apply {
            textSize = 22f
            isFakeBoldText = true
            color = Color.parseColor("#F9F3EF")
            textAlign = Paint.Align.CENTER
        }
        val subtitlePaint = Paint().apply {
            textSize = 11f
            color = Color.parseColor("#CBD8E1")
            textAlign = Paint.Align.CENTER
        }
        val headerPaint = Paint().apply {
            textSize = 10f
            isFakeBoldText = true
            color = Color.parseColor("#F9F3EF")
        }
        val headerBgPaint = Paint().apply { color = brandColor; style = Paint.Style.FILL }
        val bodyPaint = Paint().apply { textSize = 9f; color = Color.parseColor("#1B3C53") }
        val borderPaint = Paint().apply {
            color = Color.parseColor("#CCCCCC")
            style = Paint.Style.STROKE
            strokeWidth = 0.7f
        }
        val altRowBgPaint = Paint().apply {
            color = Color.parseColor("#EAF1F5")
            style = Paint.Style.FILL
        }
        val footerPaint = Paint().apply {
            textSize = 8f
            color = Color.parseColor("#9CA3AF")
            textAlign = Paint.Align.CENTER
        }

        val pdfDocument = PdfDocument()
        var pageNumber = 1
        var page = pdfDocument.startPage(
            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        )
        var canvas = page.canvas
        var y: Float

        fun blankIfEmpty(value: String): String = if (value.isBlank()) "" else value
        fun blankIfZeroInt(value: Int): String = if (value == 0) "" else value.toString()
        fun blankIfZeroDouble(value: Double): String =
            if (value == 0.0) "" else String.format(Locale.getDefault(), "%.1f", value)

        fun drawBanner() {
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), bannerHeight, bannerBgPaint)
            canvas.drawRect(0f, bannerHeight, pageWidth.toFloat(), bannerHeight + 4f, bannerAccentPaint)
            val centerX = pageWidth / 2f
            canvas.drawText("SkillSwap — User Report", centerX, bannerHeight / 2f, titlePaint)
            canvas.drawText(
                "Generated: ${dateFormat.format(Date())}",
                centerX, bannerHeight / 2f + 20f, subtitlePaint
            )
        }

        fun drawTableHeader(startY: Float): Float {
            var x = leftMargin
            var localY = startY
            canvas.drawRect(leftMargin, localY, leftMargin + tableWidth, localY + headerRowHeight, headerBgPaint)
            headers.forEachIndexed { i, header ->
                canvas.drawText(header, x + 4f, localY + headerRowHeight - 7f, headerPaint)
                canvas.drawRect(x, localY, x + columnWidths[i], localY + headerRowHeight, borderPaint)
                x += columnWidths[i]
            }
            return localY + headerRowHeight
        }

        fun drawFooter() {
            canvas.drawText("Page $pageNumber", pageWidth / 2f, pageHeight - 12f, footerPaint)
        }

        fun truncate(text: String, maxWidth: Float, paint: Paint): String {
            if (paint.measureText(text) <= maxWidth) return text
            var end = text.length
            while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
            return text.substring(0, end) + "…"
        }

        fun newPage() {
            drawFooter()
            pdfDocument.finishPage(page)
            pageNumber++
            page = pdfDocument.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            )
            canvas = page.canvas
            drawBanner()
            y = drawTableHeader(bannerHeight + 24f)
        }

        drawBanner()
        y = drawTableHeader(bannerHeight + 24f)

        users.forEachIndexed { rowIndex, user ->
            if (y + rowHeight > bottomMargin) newPage()

            val realCompletedCount = completedCountByEmail[user.email] ?: 0
            val rowValues = listOf(
                blankIfEmpty(user.name),
                blankIfEmpty(user.email),
                blankIfEmpty(user.phone),
                blankIfEmpty(user.skillsTeach),
                blankIfEmpty(user.skillsLearn),
                blankIfZeroInt(realCompletedCount),
                blankIfZeroDouble(user.rating)
            )

            if (rowIndex % 2 == 1) {
                canvas.drawRect(leftMargin, y, leftMargin + tableWidth, y + rowHeight, altRowBgPaint)
            }

            var x = leftMargin
            rowValues.forEachIndexed { i, value ->
                val cellPadding = 4f
                val maxTextWidth = columnWidths[i] - (cellPadding * 2)
                val displayText = truncate(value, maxTextWidth, bodyPaint)
                canvas.drawText(displayText, x + cellPadding, y + rowHeight - 7f, bodyPaint)
                canvas.drawRect(x, y, x + columnWidths[i], y + rowHeight, borderPaint)
                x += columnWidths[i]
            }
            y += rowHeight
        }

        drawFooter()
        pdfDocument.finishPage(page)

        try {
            val outputStream = openDownloadsOutputStream(fileName, "application/pdf")
            outputStream?.use { pdfDocument.writeTo(it) }
            Toast.makeText(this, "Saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            pdfDocument.close()
        }
    }


    private fun exportFeedbackToPdf() {
        db.collection("feedback").get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Toast.makeText(this, "No feedback to export", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val feedbackDocs = snapshot.documents.mapNotNull {
                    it.toObject(Feedback::class.java)?.copy(id = it.id)
                }
                val fileName = "SkillSwap_Feedback.pdf"
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

                val pageWidth = 842
                val pageHeight = 595
                val leftMargin = 24f
                val bottomMargin = pageHeight - 30f
                val rowHeight = 22f
                val headerRowHeight = 24f
                val bannerHeight = 70f

                val headers = listOf("User", "Title", "Category", "Rating", "Status", "Date")
                val tableWidth = pageWidth - (leftMargin * 2)
                val columnWeights = listOf(0.18f, 0.28f, 0.16f, 0.1f, 0.14f, 0.14f)
                val columnWidths = columnWeights.map { it * tableWidth }

                val navy = Color.parseColor("#1B3C53")
                val navyDark = Color.parseColor("#102838")

                val bannerBgPaint = Paint().apply { color = navy; style = Paint.Style.FILL }
                val bannerAccentPaint = Paint().apply { color = navyDark; style = Paint.Style.FILL }
                val titlePaint = Paint().apply {
                    textSize = 22f; isFakeBoldText = true
                    color = Color.parseColor("#F9F3EF"); textAlign = Paint.Align.CENTER
                }
                val subtitlePaint = Paint().apply {
                    textSize = 11f
                    color = Color.parseColor("#CBD8E1")
                    textAlign = Paint.Align.CENTER
                }
                val headerPaint = Paint().apply {
                    textSize = 10f; isFakeBoldText = true; color = Color.parseColor("#F9F3EF")
                }
                val headerBgPaint = Paint().apply { color = navy; style = Paint.Style.FILL }
                val bodyPaint = Paint().apply { textSize = 9f; color = Color.parseColor("#1B3C53") }
                val borderPaint = Paint().apply {
                    color = Color.parseColor("#CCCCCC")
                    style = Paint.Style.STROKE; strokeWidth = 0.7f
                }
                val altRowBgPaint = Paint().apply {
                    color = Color.parseColor("#EAF1F5"); style = Paint.Style.FILL
                }
                val footerPaint = Paint().apply {
                    textSize = 8f
                    color = Color.parseColor("#9CA3AF")
                    textAlign = Paint.Align.CENTER
                }

                val pdfDocument = PdfDocument()
                var pageNumber = 1
                var page = pdfDocument.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                )
                var canvas = page.canvas
                var y: Float

                fun drawBanner() {
                    canvas.drawRect(0f, 0f, pageWidth.toFloat(), bannerHeight, bannerBgPaint)
                    canvas.drawRect(0f, bannerHeight, pageWidth.toFloat(), bannerHeight + 4f, bannerAccentPaint)
                    val centerX = pageWidth / 2f
                    canvas.drawText("SkillSwap — Feedback Report", centerX, bannerHeight / 2f, titlePaint)
                    canvas.drawText(
                        "Generated: ${dateFormat.format(Date())}",
                        centerX, bannerHeight / 2f + 20f, subtitlePaint
                    )
                }

                fun drawTableHeader(startY: Float): Float {
                    var x = leftMargin
                    var localY = startY
                    canvas.drawRect(leftMargin, localY, leftMargin + tableWidth, localY + headerRowHeight, headerBgPaint)
                    headers.forEachIndexed { i, header ->
                        canvas.drawText(header, x + 4f, localY + headerRowHeight - 7f, headerPaint)
                        canvas.drawRect(x, localY, x + columnWidths[i], localY + headerRowHeight, borderPaint)
                        x += columnWidths[i]
                    }
                    return localY + headerRowHeight
                }

                fun drawFooter() {
                    canvas.drawText("Page $pageNumber", pageWidth / 2f, pageHeight - 12f, footerPaint)
                }

                fun truncate(text: String, maxWidth: Float, paint: Paint): String {
                    if (paint.measureText(text) <= maxWidth) return text
                    var end = text.length
                    while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
                    return text.substring(0, end) + "…"
                }

                fun newPage() {
                    drawFooter()
                    pdfDocument.finishPage(page)
                    pageNumber++
                    page = pdfDocument.startPage(
                        PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    )
                    canvas = page.canvas
                    drawBanner()
                    y = drawTableHeader(bannerHeight + 24f)
                }

                drawBanner()
                y = drawTableHeader(bannerHeight + 24f)

                feedbackDocs.forEachIndexed { rowIndex, fb ->
                    if (y + rowHeight > bottomMargin) newPage()

                    val rowValues = listOf(
                        fb.userName.ifBlank { "—" },
                        fb.title.ifBlank { "—" },
                        fb.category.ifBlank { "—" },
                        if (fb.rating > 0) "${fb.rating}/5" else "—",
                        fb.status.ifBlank { "new" },
                        if (fb.timestamp > 0) dateFormat.format(Date(fb.timestamp)) else "—"
                    )

                    if (rowIndex % 2 == 1) {
                        canvas.drawRect(leftMargin, y, leftMargin + tableWidth, y + rowHeight, altRowBgPaint)
                    }

                    var x = leftMargin
                    rowValues.forEachIndexed { i, value ->
                        val cellPadding = 4f
                        val maxTextWidth = columnWidths[i] - (cellPadding * 2)
                        val displayText = truncate(value, maxTextWidth, bodyPaint)
                        canvas.drawText(displayText, x + cellPadding, y + rowHeight - 7f, bodyPaint)
                        canvas.drawRect(x, y, x + columnWidths[i], y + rowHeight, borderPaint)
                        x += columnWidths[i]
                    }
                    y += rowHeight
                }

                drawFooter()
                pdfDocument.finishPage(page)

                try {
                    val outputStream = openDownloadsOutputStream(fileName, "application/pdf")
                    outputStream?.use { pdfDocument.writeTo(it) }
                    Toast.makeText(this, "Saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    pdfDocument.close()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load feedback: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun openDownloadsOutputStream(fileName: String, mimeType: String): OutputStream? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: return null
            contentResolver.openOutputStream(uri)
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = java.io.File(downloadsDir, fileName)
            java.io.FileOutputStream(file)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun dialogCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(20))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.WHITE)
            }
        }
    }

    private fun dialogTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
        }
    }

    private fun dialogDivider(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                topMargin = dp(12)
                bottomMargin = dp(12)
            }
            setBackgroundColor(Color.parseColor("#EAF1F5"))
        }
    }

    private fun pillButton(text: String, bgColor: Int, textColor: Int): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(textColor)
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(bgColor)
            }
            isClickable = true
            isFocusable = true
        }
    }
}