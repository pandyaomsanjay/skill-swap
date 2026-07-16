package com.example.sgp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore

class CreateTradeActivity : BaseActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var currentUserEmail: String
    private lateinit var currentUserId: String
    private lateinit var currentUserName: String

    private lateinit var etYourSkill: TextInputEditText
    private lateinit var etPartnerEmail: TextInputEditText
    private lateinit var etPartnerSkill: TextInputEditText
    private lateinit var etMessage: TextInputEditText
    private lateinit var btnPropose: MaterialButton

    private var selectedYourSkill: Skill? = null
    private var selectedPartnerSkill: Skill? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_trade)

        db = Firebase.firestore

        val prefs = getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
        currentUserEmail = prefs.getString("user_email", "") ?: ""
        currentUserId = currentUserEmail // we'll store email as ID (could use UID but we'll keep email)
        currentUserName = prefs.getString("user_name", "") ?: ""

        if (currentUserEmail.isEmpty()) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        setupToolbar()
        loadYourSkills()
    }

    private fun initViews() {
        etYourSkill = findViewById(R.id.etYourSkill)
        etPartnerEmail = findViewById(R.id.etPartnerEmail)
        etPartnerSkill = findViewById(R.id.etPartnerSkill)
        etMessage = findViewById(R.id.etMessage)
        btnPropose = findViewById(R.id.btnPropose)

        etYourSkill.setOnClickListener {
            showSkillPicker("your")
        }

        etPartnerEmail.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && etPartnerEmail.text?.isNotEmpty() == true) {
                loadPartnerSkills(etPartnerEmail.text.toString())
            }
        }

        etPartnerSkill.setOnClickListener {
            val partnerEmail = etPartnerEmail.text.toString().trim()
            if (partnerEmail.isEmpty()) {
                Toast.makeText(this, "Enter partner's email first", Toast.LENGTH_SHORT).show()
            } else {
                showSkillPicker("partner", partnerEmail)
            }
        }

        btnPropose.setOnClickListener {
            proposeTrade()
        }
    }

    private fun setupToolbar() {
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
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
                    Toast.makeText(this@CreateTradeActivity, "No skills available", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                val skillTitles = skills.map { it.title }.toTypedArray()
                AlertDialog.Builder(this@CreateTradeActivity)
                    .setTitle("Select Skill")
                    .setItems(skillTitles) { _, which ->
                        val selected = skills[which]
                        if (type == "your") {
                            selectedYourSkill = selected
                            etYourSkill.setText(selected.title)
                        } else {
                            selectedPartnerSkill = selected
                            etPartnerSkill.setText(selected.title)
                        }
                    }
                    .show()
            }
            .addOnFailureListener {
                Toast.makeText(this@CreateTradeActivity, "Error loading skills", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadPartnerSkills(email: String) {
        db.collection("skills")
            .whereEqualTo("userId", email)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Toast.makeText(this@CreateTradeActivity, "Partner has no skills", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Select your skill", Toast.LENGTH_SHORT).show()
            return
        }
        if (partnerEmail.isEmpty()) {
            Toast.makeText(this, "Enter partner's email", Toast.LENGTH_SHORT).show()
            return
        }
        if (partnerSkillObj == null) {
            Toast.makeText(this, "Select partner's skill", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("users").whereEqualTo("email", partnerEmail).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Toast.makeText(this@CreateTradeActivity, "Partner not registered", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                val partnerDoc = snapshot.documents[0]
                val partner = partnerDoc.toObject(Users::class.java)
                if (partner == null) {
                    Toast.makeText(this@CreateTradeActivity, "Partner not registered", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(this@CreateTradeActivity, "Trade proposed!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this@CreateTradeActivity, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error finding partner", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}