package com.example.sgp

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import java.text.SimpleDateFormat
import java.util.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

class UserProfileActivity : BaseActivity() {

    private val db = Firebase.firestore

    // Views
    private lateinit var rootLayout: View
    private lateinit var ivAvatar: ImageView
    private lateinit var tvName: TextView
    private lateinit var tvHeadline: TextView
    private lateinit var tvStats: TextView
    private lateinit var tvAbout: TextView
    private lateinit var tvAboutTitle: TextView
    private lateinit var aboutSection: View
    private lateinit var recyclerUserSkills: RecyclerView
    private lateinit var tvNoSkills: TextView
    private lateinit var btnMessage: com.google.android.material.card.MaterialCardView
    private lateinit var btnBack: View
    private lateinit var btnMore: com.google.android.material.card.MaterialCardView
    private lateinit var ivReportUserIcon: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var dividerGap: View
    private lateinit var tvSkillsOfferedTitle: TextView

    // Additional details
    private lateinit var tvLocation: TextView
    private lateinit var tvOnlineStatus: TextView
    private lateinit var llSkillsLearn: LinearLayout
    private lateinit var llLocation: LinearLayout
    private lateinit var tvSkillsLearn: TextView

    private val userSkills = mutableListOf<Skill>()
    private lateinit var skillsAdapter: UserSkillsAdapter

    private var profileUserId: String = ""
    private var profileUserName: String = ""
    private var currentUser: Users? = null
    private var viewedUser: Users? = null

    private lateinit var sharedPreferences: SharedPreferences
    private var isDarkModeEnabled = false

    companion object {
        private const val PREFS_NAME = "SkillSwapPrefs"
        private const val KEY_DARK_MODE = "dark_mode"
    }

    // ---- Light palette ----
    private val lightScreenBg = Color.parseColor("#EAF1F5")
    private val lightCardStroke = Color.parseColor("#D2C1B6")
    private val lightTextPrimary = Color.parseColor("#1B3C53")
    private val lightTextSecondary = Color.parseColor("#456882")
    private val lightIconBgNeutral = Color.parseColor("#F9F3EF")
    private val lightBtnMessageBg = Color.parseColor("#1B3C53")

    // ---- Dark palette ----
    private val darkScreenBg = Color.parseColor("#0F1B24")
    private val darkCardStroke = Color.parseColor("#2A3B48")
    private val darkTextPrimary = Color.parseColor("#F5F7F8")
    private val darkTextSecondary = Color.parseColor("#8FA6B8")
    private val darkIconBgNeutral = Color.parseColor("#26343F")
    private val darkBtnMessageBg = Color.parseColor("#0B1620")

    // ---------- Report User ----------
    private val reportReasons = arrayOf(
        "Inappropriate or Offensive Content",
        "Spam or Misleading Information",
        "Fake Skill or Scam",
        "Harassment or Abusive Behavior",
        "Copyright or Intellectual Property Violation"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        profileUserId = intent.getStringExtra("userId") ?: ""
        profileUserName = intent.getStringExtra("userName") ?: "User"

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isDarkModeEnabled = sharedPreferences.getBoolean(KEY_DARK_MODE, false)

        bindViews()
        setupClickListeners()
        setupSkillsList()
        applyTheme(isDarkModeEnabled)

        loadCurrentUser()
        loadUserProfile()
        loadUserSkills()
    }

    override fun onResume() {
        super.onResume()
        val storedDarkMode = sharedPreferences.getBoolean(KEY_DARK_MODE, false)
        if (storedDarkMode != isDarkModeEnabled) {
            applyTheme(storedDarkMode)
        }
    }

    private fun bindViews() {
        rootLayout = findViewById(R.id.rootLayout)
        ivAvatar = findViewById(R.id.ivAvatar)
        tvName = findViewById(R.id.tvName)
        tvHeadline = findViewById(R.id.tvHeadline)
        tvStats = findViewById(R.id.tvStats)
        tvAbout = findViewById(R.id.tvAbout)
        tvAboutTitle = findViewById(R.id.tvAboutTitle)
        aboutSection = findViewById(R.id.aboutSection)
        recyclerUserSkills = findViewById(R.id.recyclerUserSkills)
        tvNoSkills = findViewById(R.id.tvNoSkills)
        btnMessage = findViewById(R.id.btnMessage)
        btnBack = findViewById(R.id.btnBack)
        btnMore = findViewById(R.id.btnReportUser)
        ivReportUserIcon = findViewById(R.id.ivReportUserIcon)
        progressBar = findViewById(R.id.progressBar)
        dividerGap = findViewById(R.id.dividerGap)
        tvSkillsOfferedTitle = findViewById(R.id.tvSkillsOfferedTitle)

        tvLocation = findViewById(R.id.tvLocation)
        tvOnlineStatus = findViewById(R.id.tvOnlineStatus)
        llSkillsLearn = findViewById(R.id.llSkillsLearn)
        llLocation = findViewById(R.id.llLocation)
        tvSkillsLearn = findViewById(R.id.tvSkillsLearn)

        aboutSection.visibility = View.GONE
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }
        btnMessage.setOnClickListener { messageUser() }
        btnMore.setOnClickListener { showReportUserDialog() }
        tvStats.setOnClickListener { showUserStatsDialog() }
    }

    // ---------------------------------------------------------------------
    // THEME ENGINE
    // ---------------------------------------------------------------------
    private fun applyTheme(isDark: Boolean) {
        isDarkModeEnabled = isDark

        val screenBg = if (isDark) darkScreenBg else lightScreenBg
        val textPrimary = if (isDark) darkTextPrimary else lightTextPrimary
        val textSecondary = if (isDark) darkTextSecondary else lightTextSecondary
        val iconBgNeutral = if (isDark) darkIconBgNeutral else lightIconBgNeutral
        val btnMessageBg = if (isDark) darkBtnMessageBg else lightBtnMessageBg

        rootLayout.setBackgroundColor(screenBg)
        dividerGap.setBackgroundColor(screenBg)

        tvName.setTextColor(textPrimary)
        tvHeadline.setTextColor(textSecondary)
        tvLocation.setTextColor(textSecondary)
        tvSkillsLearn.setTextColor(textSecondary)
        tvAboutTitle.setTextColor(textPrimary)
        tvAbout.setTextColor(textSecondary)
        tvSkillsOfferedTitle.setTextColor(textPrimary)
        tvNoSkills.setTextColor(textSecondary)

        btnMessage.setCardBackgroundColor(btnMessageBg)
        btnMore.setCardBackgroundColor(iconBgNeutral)
        ivReportUserIcon.imageTintList = ColorStateList.valueOf(textSecondary)

        if (::skillsAdapter.isInitialized) {
            skillsAdapter.isDarkMode = isDark
            skillsAdapter.notifyDataSetChanged()
        }
    }

    private fun setupSkillsList() {
        recyclerUserSkills.layoutManager = LinearLayoutManager(this)
        skillsAdapter = UserSkillsAdapter(userSkills, isDarkModeEnabled) { skill ->
            openSkillVideo(skill)
        }
        recyclerUserSkills.adapter = skillsAdapter
    }

    private fun loadCurrentUser() {
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email
        if (currentUserEmail.isNullOrEmpty()) return

        db.collection("users")
            .whereEqualTo("email", currentUserEmail)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    currentUser = snapshot.documents[0].toObject(Users::class.java)
                }
            }
    }

    private fun loadUserProfile() {
        if (profileUserId.isEmpty()) {
            progressBar.visibility = View.GONE
            return
        }

        progressBar.visibility = View.VISIBLE

        db.collection("users")
            .whereEqualTo("email", profileUserId)
            .addSnapshotListener { snapshot, error ->
                progressBar.visibility = View.GONE

                if (error != null || snapshot == null || snapshot.isEmpty) {
                    Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                val doc = snapshot.documents[0]

                try {
                    val user = doc.toObject(Users::class.java)
                    if (user != null) {
                        bindUser(user)
                    } else {
                        loadUserProfileManually(doc)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    loadUserProfileManually(doc)
                }
            }
    }

    private fun loadUserProfileManually(doc: com.google.firebase.firestore.DocumentSnapshot) {
        try {
            val user = Users(
                name = doc.getString("name") ?: profileUserName,
                email = doc.getString("email") ?: profileUserId,
                phone = doc.getString("phone") ?: "",
                location = doc.getString("location") ?: "",
                latitude = doc.getDouble("latitude") ?: 0.0,
                longitude = doc.getDouble("longitude") ?: 0.0,
                rating = doc.getDouble("rating") ?: 0.0,
                completedTrades = doc.getLong("completedTrades")?.toInt() ?: 0,
                profileImage = doc.getString("profileImage") ?: "",
                userType = doc.getString("userType") ?: "standard",
                joinedDate = doc.getLong("joinedDate") ?: System.currentTimeMillis(),
                skillsTeach = doc.getString("skillsTeach") ?: "",
                skillsLearn = doc.getString("skillsLearn") ?: "",
                credits = doc.getLong("credits")?.toInt() ?: 0,
                isLocationVerified = doc.getBoolean("isLocationVerified") ?: false,
                loginProvider = doc.getString("loginProvider") ?: "",
                createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                language = doc.getString("language") ?: "",
                profileImagePath = doc.getString("profileImagePath"),
                followersCount = doc.getLong("followersCount")?.toInt() ?: 0,
                followingCount = doc.getLong("followingCount")?.toInt() ?: 0
            )
            bindUser(user)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindUser(user: Users) {
        viewedUser = user

        tvName.text = user.name.ifBlank { profileUserName }

        val headline = buildString {
            if (user.skillsTeach.isNotBlank()) {
                append("Teaches: ${user.skillsTeach}")
            } else {
                append("Skill swapper on SkillSwap")
            }
            if (user.isLocationVerified) {
                append(" ✅ Verified")
            }
        }
        tvHeadline.text = headline

        if (user.location.isNotBlank()) {
            llLocation.visibility = View.VISIBLE
            tvLocation.text = "📍 ${user.location}"
        } else {
            llLocation.visibility = View.GONE
        }

        if (user.skillsLearn.isNotBlank()) {
            llSkillsLearn.visibility = View.VISIBLE
            tvSkillsLearn.text = "💡 Wants to learn: ${user.skillsLearn}"
        } else {
            llSkillsLearn.visibility = View.GONE
        }

        tvOnlineStatus.text = "⚪ Offline"
        tvOnlineStatus.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))

        val imageSource: Any? = when {
            user.profileImage.isNotBlank() -> user.profileImage
            !user.profileImagePath.isNullOrBlank() -> user.profileImagePath
            else -> null
        }
        if (imageSource != null) {
            ivAvatar.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            val params = ivAvatar.layoutParams
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            ivAvatar.layoutParams = params

            ivAvatar.imageTintList = null

            Glide.with(this)
                .load(imageSource)
                .circleCrop()
                .placeholder(R.drawable.baseline_account_circle_24)
                .error(R.drawable.baseline_account_circle_24)
                .into(ivAvatar)
        } else {
            ivAvatar.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            val params = ivAvatar.layoutParams
            val size = (56 * resources.displayMetrics.density).toInt()
            params.width = size
            params.height = size
            ivAvatar.layoutParams = params

            ivAvatar.imageTintList = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#F9F3EF")
            )
            ivAvatar.setImageResource(R.drawable.baseline_account_circle_24)
        }

        updateStatsLine()
    }

    private fun updateStatsLine() {
        tvStats.text = "${userSkills.size} skill${if (userSkills.size == 1) "" else "s"} shared"
    }

    private fun loadUserSkills() {
        if (profileUserId.isEmpty()) return

        db.collection("skills")
            .whereEqualTo("userId", profileUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                userSkills.clear()
                snapshot.documents.forEach { doc ->
                    val skill = doc.toObject(Skill::class.java)
                    if (skill != null) {
                        skill.id = doc.id
                        userSkills.add(skill)
                    }
                }
                userSkills.sortBy { it.title }

                skillsAdapter.notifyDataSetChanged()
                updateStatsLine()
                tvNoSkills.visibility = if (userSkills.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    private fun showUserStatsDialog() {
        val user = viewedUser
        if (user == null) {
            Toast.makeText(this, "Profile still loading, try again", Toast.LENGTH_SHORT).show()
            return
        }

        val userTypeText = when (user.userType.lowercase()) {
            "premium" -> "👑 Premium Member"
            "verified" -> "✅ Verified Member"
            else -> "👤 Standard Member"
        }

        val tradeEmoji = when {
            user.completedTrades >= 100 -> "🏆"
            user.completedTrades >= 50 -> "🌟"
            user.completedTrades >= 10 -> "🔥"
            else -> "🔄"
        }

        val dateFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())
        val memberSince = "📅 Joined ${dateFormat.format(Date(user.joinedDate))}"

        val root = dialogCard()

        root.addView(dialogTitle("User Statistics"))
        root.addView(dividerLine())

        addStatRow(root, "Skills Offered", "${userSkills.size}")
        addStatRow(root, "Member Type", userTypeText)
        addStatRow(root, "Credits", "⭐ ${user.credits} credits")
        addStatRow(root, "Trades Completed", "$tradeEmoji ${user.completedTrades} trades completed")
        addStatRow(root, "Rating", String.format(Locale.getDefault(), "%.1f", user.rating))
        addStatRow(root, "Member Since", memberSince, isLast = true)

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnOk = pillButton("OK", currentTextPrimary(), Color.parseColor("#F9F3EF")).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(18)
                gravity = Gravity.END
            }
            setPadding(dp(28), dp(10), dp(28), dp(10))
            setOnClickListener { dialog.dismiss() }
        }
        root.addView(btnOk)

        dialog.show()
    }

    private fun addStatRow(parent: LinearLayout, label: String, value: String, isLast: Boolean = false) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = if (isLast) 0 else dp(10)
            }
        }
        row.addView(TextView(this).apply {
            text = label
            setTextColor(currentTextSecondary())
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(dp(130), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        row.addView(TextView(this).apply {
            text = value
            setTextColor(currentTextPrimary())
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        parent.addView(row)
    }

    // ---------- Report User ----------

    private fun showReportUserDialog() {
        val reporterEmail = FirebaseAuth.getInstance().currentUser?.email
        if (reporterEmail.isNullOrEmpty()) {
            Toast.makeText(this, "Please log in to report", Toast.LENGTH_SHORT).show()
            return
        }
        if (reporterEmail == profileUserId) {
            Toast.makeText(this, "You can't report your own profile", Toast.LENGTH_SHORT).show()
            return
        }

        val root = dialogCard()
        root.addView(dialogTitle("Report $profileUserName"))
        root.addView(TextView(this).apply {
            text = "Help us understand what's wrong"
            setTextColor(currentTextSecondary())
            textSize = 13f
            setPadding(0, dp(4), 0, dp(4))
        })
        root.addView(dividerLine())

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        reportReasons.forEachIndexed { index, reason ->
            root.addView(reportReasonRow(reason) {
                dialog.dismiss()
                submitUserReport(reason, reporterEmail)
            })
            if (index != reportReasons.lastIndex) {
                root.addView(dividerLine())
            }
        }

        val btnCancel = pillButton("Cancel", currentSecondaryBtnBg(), currentSecondaryBtnText()).apply {
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
        root.addView(btnCancel)

        dialog.show()
    }

    private fun submitUserReport(reason: String, reporterEmail: String) {
        val docRef = db.collection("reports").document()
        val report = Report(
            id = docRef.id,
            reporterId = reporterEmail,
            reportedUserId = profileUserId,
            skillId = "",
            reason = reason,
            description = "Reported user profile: $profileUserName ($profileUserId)",
            status = "pending",
            timestamp = System.currentTimeMillis(),
            type = "user"
        )
        docRef.set(report)
            .addOnSuccessListener {
                Toast.makeText(this, "Report submitted. Our team will review it.", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to submit report: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun reportReasonRow(text: String, onClick: () -> Unit): TextView {
        val outValue = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        return TextView(this).apply {
            this.text = text
            setTextColor(currentTextPrimary())
            textSize = 15f
            setPadding(dp(4), dp(8), dp(4), dp(8))
            setBackgroundResource(outValue.resourceId)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun currentDialogBg(): Int =
        if (isDarkModeEnabled) Color.parseColor("#16232E") else Color.WHITE

    private fun currentTextPrimary(): Int =
        if (isDarkModeEnabled) darkTextPrimary else lightTextPrimary

    private fun currentTextSecondary(): Int =
        if (isDarkModeEnabled) darkTextSecondary else lightTextSecondary

    private fun currentSecondaryBtnBg(): Int =
        if (isDarkModeEnabled) darkCardStroke else Color.parseColor("#EAF1F5")

    private fun currentSecondaryBtnText(): Int =
        if (isDarkModeEnabled) darkTextPrimary else lightTextSecondary

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

    private fun dialogTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(currentTextPrimary())
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
        }
    }

    private fun dividerLine(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                topMargin = dp(12)
                bottomMargin = dp(12)
            }
            setBackgroundColor(if (isDarkModeEnabled) darkCardStroke else Color.parseColor("#EAF1F5"))
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

    private fun messageUser() {
        val myEmail = FirebaseAuth.getInstance().currentUser?.email
        if (myEmail.isNullOrEmpty()) {
            Toast.makeText(this, "Please log in to chat", Toast.LENGTH_SHORT).show()
            return
        }
        if (myEmail == profileUserId) {
            Toast.makeText(this, "This is your own profile", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("otherUserEmail", profileUserId)
        intent.putExtra("otherUserName", profileUserName)
        startActivity(intent)
    }

    // ---------- Open Skill Video ----------

    private fun openSkillVideo(skill: Skill) {
        if (skill.skillType == "single" && !skill.videoUrl.isNullOrEmpty()) {
            val intent = Intent(this, VideoPlayerActivity::class.java)
            intent.putExtra("videoUrl", skill.videoUrl)
            intent.putExtra("skillId", skill.id)
            intent.putExtra("skillTitle", skill.title)
            intent.putExtra("skillCategory", skill.category)
            intent.putExtra("skillUserId", skill.userId)
            intent.putExtra("skillUserName", skill.userName)
            intent.putExtra("skillCredits", skill.credits)
            intent.putExtra("skillDescription", skill.description)
            startActivity(intent)
        } else if (skill.skillType == "playlist") {
            val intent = Intent(this, PlaylistActivity::class.java)
            intent.putExtra("skillId", skill.id)
            startActivity(intent)
        }
    }

    // ---------- Adapter ----------

    class UserSkillsAdapter(
        private val skills: List<Skill>,
        var isDarkMode: Boolean,
        private val onClick: (Skill) -> Unit
    ) : RecyclerView.Adapter<UserSkillsAdapter.VH>() {

        private val lightTextPrimary = Color.parseColor("#1B3C53")
        private val lightTextSecondary = Color.parseColor("#456882")
        private val darkTextPrimary = Color.parseColor("#F5F7F8")
        private val darkTextSecondary = Color.parseColor("#8FA6B8")

        // Card bg + stroke per theme — matching the palette used across the app
        private val lightCardBg = Color.parseColor("#F9F3EF")
        private val lightCardStroke = Color.parseColor("#D2C1B6")
        private val darkCardBg = Color.parseColor("#16232E")
        private val darkCardStroke = Color.parseColor("#2A3B48")

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvRowTitle)
            val tvMeta: TextView = view.findViewById(R.id.tvRowMeta)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_user_skill_row, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val skill = skills[position]

            val textPrimary   = if (isDarkMode) darkTextPrimary   else lightTextPrimary
            val textSecondary = if (isDarkMode) darkTextSecondary else lightTextSecondary
            val cardBg        = if (isDarkMode) darkCardBg        else lightCardBg
            val cardStroke    = if (isDarkMode) darkCardStroke    else lightCardStroke

            // Content
            holder.tvTitle.text = skill.title
            holder.tvMeta.text = "${skill.category} · ${skill.credits} credits"

            // Text colors
            holder.tvTitle.setTextColor(textPrimary)
            holder.tvMeta.setTextColor(textSecondary)

            // Card background + stroke — this is the fix for the white card in
            // dark mode. The root of item_user_skill_row.xml is a
            // MaterialCardView, so we can cast and set its colors directly.
            (holder.itemView as? com.google.android.material.card.MaterialCardView)?.apply {
                setCardBackgroundColor(cardBg)
                strokeColor = cardStroke
            }

            holder.itemView.setOnClickListener { onClick(skill) }
        }

        override fun getItemCount() = skills.size
    }
}