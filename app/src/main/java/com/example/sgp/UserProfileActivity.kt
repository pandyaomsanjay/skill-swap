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
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
    private lateinit var ivAvatar: ImageView
    private lateinit var tvName: TextView
    private lateinit var tvHeadline: TextView
    private lateinit var tvStats: TextView
    private lateinit var tvAbout: TextView
    private lateinit var aboutSection: View
    private lateinit var recyclerUserSkills: RecyclerView
    private lateinit var tvNoSkills: TextView
    private lateinit var btnMessage: View
    private lateinit var btnBack: View
    private lateinit var btnMore: View
    private lateinit var progressBar: ProgressBar

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        profileUserId = intent.getStringExtra("userId") ?: ""
        profileUserName = intent.getStringExtra("userName") ?: "User"

        bindViews()
        setupClickListeners()
        setupSkillsList()

        loadCurrentUser()
        loadUserProfile()
        loadUserSkills()
    }

    private fun bindViews() {
        ivAvatar = findViewById(R.id.ivAvatar)
        tvName = findViewById(R.id.tvName)
        tvHeadline = findViewById(R.id.tvHeadline)
        tvStats = findViewById(R.id.tvStats)
        tvAbout = findViewById(R.id.tvAbout)
        aboutSection = findViewById(R.id.aboutSection)
        recyclerUserSkills = findViewById(R.id.recyclerUserSkills)
        tvNoSkills = findViewById(R.id.tvNoSkills)
        btnMessage = findViewById(R.id.btnMessage)
        btnBack = findViewById(R.id.btnBack)
        btnMore = findViewById(R.id.btnReportUser)
        progressBar = findViewById(R.id.progressBar)

        tvLocation = findViewById(R.id.tvLocation)
        tvOnlineStatus = findViewById(R.id.tvOnlineStatus)
        llSkillsLearn = findViewById(R.id.llSkillsLearn)
        llLocation = findViewById(R.id.llLocation)
        tvSkillsLearn = findViewById(R.id.tvSkillsLearn)

        // No `bio` field on Users — About section has nothing to bind, keep it hidden.
        aboutSection.visibility = View.GONE
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }
        btnMessage.setOnClickListener { messageUser() }
        btnMore.setOnClickListener { showUserStatsDialog() }
    }

    private fun setupSkillsList() {
        recyclerUserSkills.layoutManager = LinearLayoutManager(this)
        skillsAdapter = UserSkillsAdapter(userSkills) { skill ->
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

        // Name
        tvName.text = user.name.ifBlank { profileUserName }

        // Headline - what they teach, plus a verified badge if applicable
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

        // Location
        if (user.location.isNotBlank()) {
            llLocation.visibility = View.VISIBLE
            tvLocation.text = "📍 ${user.location}"
        } else {
            llLocation.visibility = View.GONE
        }

        // Skills they want to learn
        if (user.skillsLearn.isNotBlank()) {
            llSkillsLearn.visibility = View.VISIBLE
            tvSkillsLearn.text = "💡 Wants to learn: ${user.skillsLearn}"
        } else {
            llSkillsLearn.visibility = View.GONE
        }

        // No presence field on Users yet — default to Offline until you add one.
        tvOnlineStatus.text = "⚪ Offline"
        tvOnlineStatus.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))

        // Avatar — prefer a remote profileImage URL, fall back to a local path if present
        val imageSource: Any? = when {
            user.profileImage.isNotBlank() -> user.profileImage
            !user.profileImagePath.isNullOrBlank() -> user.profileImagePath
            else -> null
        }
        if (imageSource != null) {
            // Real photo: fill the circle edge-to-edge.
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

        val btnOk = pillButton("OK", Color.parseColor("#1B3C53"), Color.parseColor("#F9F3EF")).apply {
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

    /** A single "Label   Value" row for the stats dialog, navy-on-cream themed. */
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
            setTextColor(Color.parseColor("#456882"))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(dp(130), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        row.addView(TextView(this).apply {
            text = value
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        parent.addView(row)
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

    private fun dividerLine(): View {
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
        private val onClick: (Skill) -> Unit
    ) : RecyclerView.Adapter<UserSkillsAdapter.VH>() {

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
            holder.tvTitle.text = skill.title
            holder.tvMeta.text = "${skill.category} · ${skill.credits} credits"
            holder.itemView.setOnClickListener { onClick(skill) }
        }

        override fun getItemCount() = skills.size
    }
}