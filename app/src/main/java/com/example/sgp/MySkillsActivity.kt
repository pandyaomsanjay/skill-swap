package com.example.sgp

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore

class MySkillsActivity : BaseActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var currentUserEmail: String
    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var skillsContainer: LinearLayout
    private lateinit var tvNoSkills: TextView

    private var isDarkModeEnabled = false

    // ---------------------------------------------------------------------
    // LIGHT THEME PALETTE
    // ---------------------------------------------------------------------
    private val lightScreenBg = Color.parseColor("#EAF1F5")
    private val lightCardBg = Color.parseColor("#FFFFFF")
    private val lightCardStroke = Color.parseColor("#D2C1B6")
    private val lightTextPrimary = Color.parseColor("#1B3C53")
    private val lightTextSecondary = Color.parseColor("#456882")
    private val lightChevronTint = Color.parseColor("#456882")
    private val lightHeaderBg = Color.parseColor("#1B3C53")

    // ---------------------------------------------------------------------
    // DARK THEME PALETTE
    // ---------------------------------------------------------------------
    private val darkScreenBg = Color.parseColor("#0F1B24")
    private val darkCardBg = Color.parseColor("#16232E")
    private val darkCardStroke = Color.parseColor("#2A3B48")
    private val darkTextPrimary = Color.parseColor("#F5F7F8")
    private val darkTextSecondary = Color.parseColor("#8FA6B8")
    private val darkChevronTint = Color.parseColor("#6E8598")
    private val darkHeaderBg = Color.parseColor("#0B1620")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_skills)

        db = FirebaseFirestore.getInstance()
        sharedPreferences = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
        isDarkModeEnabled = sharedPreferences.getBoolean(SettingsActivity.KEY_DARK_MODE, false)

        currentUserEmail = sharedPreferences.getString("user_email", "") ?: ""

        initViews()
        applyTheme(isDarkModeEnabled)
        loadUserSkills()
    }

    override fun onResume() {
        super.onResume()
        // Re-apply theme in case user toggled dark mode in Settings and came back
        isDarkModeEnabled = sharedPreferences.getBoolean(SettingsActivity.KEY_DARK_MODE, false)
        applyTheme(isDarkModeEnabled)
        loadUserSkills()
    }

    private fun initViews() {
        skillsContainer = findViewById(R.id.skillsContainer)
        tvNoSkills = findViewById(R.id.tvNoSkills)

        findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnAddNewSkill)?.setOnClickListener {
            startActivity(Intent(this, AddSkillActivity::class.java))
        }
    }

    // ---------------------------------------------------------------------
    // THEME ENGINE
    // ---------------------------------------------------------------------
    private fun applyTheme(isDark: Boolean) {
        isDarkModeEnabled = isDark

        val screenBg = if (isDark) darkScreenBg else lightScreenBg
        val cardBg = if (isDark) darkCardBg else lightCardBg
        val cardStroke = if (isDark) darkCardStroke else lightCardStroke
        val textPrimary = if (isDark) darkTextPrimary else lightTextPrimary
        val textSecondary = if (isDark) darkTextSecondary else lightTextSecondary
        val chevronTint = if (isDark) darkChevronTint else lightChevronTint
        val headerBg = if (isDark) darkHeaderBg else lightHeaderBg

        // Root background
        findViewById<View>(R.id.rootLayout)?.setBackgroundColor(screenBg)

        // Header
        findViewById<View>(R.id.headerBar)?.setBackgroundColor(headerBg)
        findViewById<TextView>(R.id.tvTitle)?.setTextColor(Color.parseColor("#F9F3EF"))
        findViewById<ImageView>(R.id.ivBackIcon)?.imageTintList = ColorStateList.valueOf(Color.parseColor("#F9F3EF"))
        findViewById<MaterialCardView>(R.id.btnBack)?.setCardBackgroundColor(
            if (isDark) Color.parseColor("#22323D") else Color.parseColor("#456882")
        )

        // Add button
        findViewById<MaterialButton>(R.id.btnAddNewSkill)?.apply {
            backgroundTintList = ColorStateList.valueOf(if (isDark) Color.parseColor("#22323D") else Color.parseColor("#1B3C53"))
            setTextColor(if (isDark) Color.parseColor("#F5F7F8") else Color.parseColor("#F9F3EF"))
        }

        tvNoSkills.setTextColor(textSecondary)

        // Re-theme already inflated cards
        for (i in 0 until skillsContainer.childCount) {
            styleSkillCardView(skillsContainer.getChildAt(i), isDark)
        }

        // System bars
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark
        window.statusBarColor = headerBg
        window.navigationBarColor = screenBg
    }

    // ---------------------------------------------------------------------
    // DATA LOADING
    // ---------------------------------------------------------------------
    private fun loadUserSkills() {
        if (currentUserEmail.isEmpty()) {
            tvNoSkills.visibility = View.VISIBLE
            tvNoSkills.text = "User not found."
            return
        }

        db.collection("skills")
            .whereEqualTo("userId", currentUserEmail)
            .get()
            .addOnSuccessListener { snapshot ->
                skillsContainer.removeAllViews()
                if (snapshot.isEmpty) {
                    tvNoSkills.visibility = View.VISIBLE
                    tvNoSkills.text = "No skills added yet. Tap 'Add New Skill' to get started."
                } else {
                    tvNoSkills.visibility = View.GONE
                    snapshot.documents.forEach { doc ->
                        doc.toObject(Skill::class.java)?.let { addSkillView(it) }
                    }
                }
            }
            .addOnFailureListener { e ->
                tvNoSkills.visibility = View.VISIBLE
                tvNoSkills.text = "Error loading skills: ${e.message}"
            }
    }

    private fun addSkillView(skill: Skill) {
        val view = layoutInflater.inflate(R.layout.item_profile_skill, skillsContainer, false)
        val ivThumbnail = view.findViewById<ImageView>(R.id.ivSkillThumbnail)
        val tvTitle = view.findViewById<TextView>(R.id.tvSkillTitle)
        val tvCredits = view.findViewById<TextView>(R.id.tvSkillCredits)
        val ivPlay = view.findViewById<ImageView>(R.id.ivPlayIcon)

        tvTitle.text = skill.title
        tvCredits.text = "${skill.credits} credits"

        val thumbnailUrl = if (skill.skillType == "playlist") {
            skill.thumbnailUrl
        } else {
            skill.videoUrl
        }

        if (!thumbnailUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(thumbnailUrl)
                .placeholder(R.drawable.baseline_videocam_24)
                .error(R.drawable.baseline_videocam_24)
                .into(ivThumbnail)
        } else {
            ivThumbnail.setImageResource(R.drawable.baseline_videocam_24)
        }

        styleSkillCardView(view, isDarkModeEnabled)

        val vertPad = dp(12)
        view.setPadding(vertPad, vertPad, vertPad, vertPad)
        (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            lp.bottomMargin = dp(14)
            view.layoutParams = lp
        } ?: run {
            view.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
        }
        view.elevation = dp(1).toFloat()
        view.clipToOutline = true

        val clickListener = View.OnClickListener { openSkill(skill) }
        view.setOnClickListener(clickListener)
        ivPlay.setOnClickListener(clickListener)

        skillsContainer.addView(view)
    }

    private fun styleSkillCardView(view: View, isDark: Boolean) {
        view.background = skillCardBackground(isDark)
        view.findViewById<TextView>(R.id.tvSkillTitle)?.setTextColor(
            if (isDark) darkTextPrimary else lightTextPrimary
        )
        view.findViewById<TextView>(R.id.tvSkillCredits)?.setTextColor(
            if (isDark) darkTextSecondary else lightTextSecondary
        )
        view.findViewById<ImageView>(R.id.ivPlayIcon)?.imageTintList = ColorStateList.valueOf(
            if (isDark) darkChevronTint else lightChevronTint
        )
    }

    private fun openSkill(skill: Skill) {
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
        } else {
            Toast.makeText(this, "No video available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun skillCardBackground(isDark: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(if (isDark) darkCardBg else Color.WHITE)
            setStroke(dp(1), if (isDark) darkCardStroke else lightCardStroke)
        }
    }
}