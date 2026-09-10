package com.example.sgp

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore

class CreateTradeActivity : BaseActivity() {

    private lateinit var db: FirebaseFirestore

    private lateinit var currentUserEmail: String
    private lateinit var currentUserName: String

    private lateinit var etYourSkill: TextInputEditText
    private lateinit var etPartnerEmail: TextInputEditText
    private lateinit var etPartnerSkill: TextInputEditText
    private lateinit var etMessage: TextInputEditText
    private lateinit var btnPropose: MaterialButton

    private lateinit var yourSkillLayout: TextInputLayout
    private lateinit var partnerEmailLayout: TextInputLayout
    private lateinit var partnerSkillLayout: TextInputLayout

    private var selectedYourSkill: Skill? = null
    private var selectedPartnerSkill: Skill? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_trade)

        db = Firebase.firestore

        val prefs = getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
        currentUserEmail = prefs.getString("user_email", "") ?: ""
        currentUserName = prefs.getString("user_name", "") ?: ""

        if (currentUserEmail.isEmpty()) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (currentUserName.isBlank()) currentUserName = currentUserEmail

        initViews()
        loadYourSkills()
    }

    private fun initViews() {
        yourSkillLayout = findViewById(R.id.yourSkillLayout)
        partnerEmailLayout = findViewById(R.id.partnerEmailLayout)
        partnerSkillLayout = findViewById(R.id.partnerSkillLayout)

        etYourSkill = findViewById(R.id.etYourSkill)
        etPartnerEmail = findViewById(R.id.etPartnerEmail)
        etPartnerSkill = findViewById(R.id.etPartnerSkill)
        etMessage = findViewById(R.id.etMessage)
        btnPropose = findViewById(R.id.btnPropose)

        findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }

        etYourSkill.setOnClickListener { showSkillPicker("your") }

        etPartnerEmail.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && etPartnerEmail.text?.isNotEmpty() == true) {
                loadPartnerSkills(etPartnerEmail.text.toString().trim())
            }
        }

        etPartnerSkill.setOnClickListener {
            val partnerEmail = etPartnerEmail.text.toString().trim()
            if (partnerEmail.isEmpty()) {
                partnerEmailLayout.error = "Enter partner's email first"
            } else {
                partnerEmailLayout.error = null
                showSkillPicker("partner", partnerEmail)
            }
        }

        btnPropose.setOnClickListener { proposeTrade() }
    }

    private fun loadYourSkills() {
        db.collection("skills")
            .whereEqualTo("userId", currentUserEmail)
            .get()
            .addOnSuccessListener { snapshot ->
                val skills = mutableListOf<Skill>()
                snapshot.documents.forEach { doc ->
                    doc.toObject(Skill::class.java)?.let { skills.add(it) }
                }
                if (skills.isEmpty()) {
                    etYourSkill.setText("No skills found. Please add a skill first.")
                    etYourSkill.isEnabled = false
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load skills", Toast.LENGTH_SHORT).show()
            }
    }

    // ============================ Themed skill picker ============================

    private fun showSkillPicker(type: String, partnerEmail: String = "") {
        val query = if (type == "your") {
            db.collection("skills").whereEqualTo("userId", currentUserEmail)
        } else {
            db.collection("skills").whereEqualTo("userId", partnerEmail)
        }

        query.get()
            .addOnSuccessListener { snapshot ->
                val skills = mutableListOf<Skill>()
                snapshot.documents.forEach { doc ->
                    doc.toObject(Skill::class.java)?.let { skills.add(it) }
                }
                if (skills.isEmpty()) {
                    Toast.makeText(
                        this@CreateTradeActivity,
                        "No skills available",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@addOnSuccessListener
                }

                showThemedSkillDialog(type, skills)
            }
            .addOnFailureListener {
                Toast.makeText(
                    this@CreateTradeActivity,
                    "Error loading skills",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun showThemedSkillDialog(type: String, skills: List<Skill>) {
        val root = dialogCard()
        root.addView(
            dialogTitle(
                if (type == "your") "Select your skill" else "Select partner's skill"
            )
        )
        root.addView(
            TextView(this).apply {
                text = "Pick one to include in this trade"
                setTextColor(Color.parseColor("#456882"))
                textSize = 13f
                setPadding(0, dp(4), 0, dp(4))
            }
        )
        root.addView(dividerLine())

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        skills.forEachIndexed { index, skill ->
            root.addView(skillRow(skill.title) {
                dialog.dismiss()
                if (type == "your") {
                    selectedYourSkill = skill
                    etYourSkill.setText(skill.title)
                    yourSkillLayout.error = null
                } else {
                    selectedPartnerSkill = skill
                    etPartnerSkill.setText(skill.title)
                    partnerSkillLayout.error = null
                }
            })
            if (index != skills.lastIndex) {
                root.addView(dividerLine())
            }
        }

        val btnCancel = pillButton(
            "Cancel",
            Color.parseColor("#EAF1F5"),
            Color.parseColor("#456882")
        ).apply {
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

    // ---- Themed dialog helpers (same style as ExploreActivity) ----

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

    private fun skillRow(text: String, onClick: () -> Unit): TextView {
        val outValue = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 15f
            setPadding(dp(4), dp(12), dp(4), dp(12))
            setBackgroundResource(outValue.resourceId)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    // ============================ End themed dialog helpers ============================

    private fun loadPartnerSkills(email: String) {
        db.collection("skills")
            .whereEqualTo("userId", email)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Toast.makeText(
                        this@CreateTradeActivity,
                        "Partner has no skills",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error checking partner skills", Toast.LENGTH_SHORT).show()
            }
    }

    private fun proposeTrade() {
        val yourSkillObj = selectedYourSkill
        val partnerSkillObj = selectedPartnerSkill
        val partnerEmail = etPartnerEmail.text.toString().trim()
        val message = etMessage.text.toString().trim()

        if (yourSkillObj == null) {
            yourSkillLayout.error = "Select the skill you're offering"
            return
        }
        if (partnerEmail.isEmpty()) {
            partnerEmailLayout.error = "Enter partner's email"
            return
        }
        if (partnerSkillObj == null) {
            partnerSkillLayout.error = "Select the skill you want"
            return
        }

        partnerEmailLayout.error = null

        db.collection("users").whereEqualTo("email", partnerEmail).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    partnerEmailLayout.error = "Partner not registered"
                    return@addOnSuccessListener
                }
                val partnerDoc = snapshot.documents[0]
                val partner = partnerDoc.toObject(Users::class.java)
                if (partner == null) {
                    partnerEmailLayout.error = "Partner not registered"
                    return@addOnSuccessListener
                }

                val tradeId = db.collection("trades").document().id
                val trade = Trade(
                    id = tradeId,
                    requesterId = currentUserEmail,
                    receiverId = partnerEmail,
                    requesterSkill = yourSkillObj.title,
                    receiverSkill = partnerSkillObj.title,
                    status = "pending",
                    requesterName = currentUserName,
                    receiverName = partner.name,
                    timestamp = System.currentTimeMillis(),
                    videoUrl = yourSkillObj.videoUrl ?: "",
                    isActive = true,
                    uploaderName = currentUserName,
                    skillOffered = yourSkillObj.title,
                    skillRequested = partnerSkillObj.title,
                    rating = 0f,
                    uploaderAvatar = R.drawable.ic_default_profile
                )

                db.collection("trades").document(tradeId).set(trade)
                    .addOnSuccessListener {
                        Toast.makeText(
                            this@CreateTradeActivity,
                            "Trade proposed!",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            this@CreateTradeActivity,
                            "Failed: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error finding partner", Toast.LENGTH_SHORT).show()
            }
    }
}