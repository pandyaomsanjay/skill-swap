package com.example.sgp

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore

class EditProfileActivity : BaseActivity() {

    private lateinit var db: FirebaseFirestore

    private val navyDark = Color.parseColor("#1B3C53")
    private val navyMed = Color.parseColor("#456882")
    private val cream = Color.parseColor("#F9F3EF")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        db = Firebase.firestore

        styleHeader()

        var currentUserEmail = intent.getStringExtra("email")
        if (currentUserEmail.isNullOrEmpty()) {
            val prefs = getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
            currentUserEmail = prefs.getString("user_email", "")
        }

        if (currentUserEmail.isNullOrEmpty()) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews(currentUserEmail)
    }

    /**
     * Paints the header navy with rounded-bottom corners (matching Profile's
     * header card) and gives the pencil icon its own soft translucent circle,
     * both done in code so no new shape/drawable resources are needed beyond
     * the vector icon itself.
     */
    private fun styleHeader() {
        val header = findViewById<LinearLayout>(R.id.headerCard)
        header.background = GradientDrawable().apply {
            setColor(navyDark)
            cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat())
        }
        header.elevation = dp(4).toFloat()

        val icon = findViewById<ImageView>(R.id.ivEditIcon)
        icon.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#22FFFFFF"))
            setStroke(dp(1), Color.parseColor("#40FFFFFF"))
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun initializeViews(email: String) {
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnSave = findViewById<Button>(R.id.btnSave)

        val nameInput = findViewById<TextInputLayout>(R.id.name)
        val phoneInput = findViewById<TextInputLayout>(R.id.phone)
        val locationInput = findViewById<TextInputLayout>(R.id.location)
        val skillsTeachInput = findViewById<TextInputLayout>(R.id.skillsTeach)
        val skillsLearnInput = findViewById<TextInputLayout>(R.id.skillsLearn)

        loadCurrentData(email, nameInput, phoneInput, locationInput, skillsTeachInput, skillsLearnInput)

        btnBack.setOnClickListener {
            finish()
        }

        // Subtle press-down/release scale animation so tapping Save feels
        // tactile instead of a flat instant color-change.
        btnSave.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }
            }
            false // let the click listener still fire
        }

        btnSave.setOnClickListener {
            val name = nameInput.editText?.text.toString()
            val phone = phoneInput.editText?.text.toString()
            val location = locationInput.editText?.text.toString()
            val skillsTeach = skillsTeachInput.editText?.text.toString()
            val skillsLearn = skillsLearnInput.editText?.text.toString()

            updateProfile(email, name, phone, location, skillsTeach, skillsLearn)
        }
    }

    private fun loadCurrentData(
        email: String,
        nameInput: TextInputLayout,
        phoneInput: TextInputLayout,
        locationInput: TextInputLayout,
        skillsTeachInput: TextInputLayout,
        skillsLearnInput: TextInputLayout
    ) {
        db.collection("users").whereEqualTo("email", email).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val user = snapshot.documents[0].toObject(Users::class.java)
                    user?.let {
                        nameInput.editText?.setText(it.name)
                        phoneInput.editText?.setText(it.phone)
                        locationInput.editText?.setText(it.location)
                        skillsTeachInput.editText?.setText(it.skillsTeach)
                        skillsLearnInput.editText?.setText(it.skillsLearn)
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateProfile(
        email: String,
        name: String,
        phone: String,
        location: String,
        skillsTeach: String,
        skillsLearn: String
    ) {
        if (name.isEmpty() || phone.isEmpty() || location.isEmpty()) {
            Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("users").whereEqualTo("email", email).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val docId = snapshot.documents[0].id
                    val updates = hashMapOf<String, Any>(
                        "name" to name,
                        "phone" to phone,
                        "location" to location,
                        "skillsTeach" to skillsTeach,
                        "skillsLearn" to skillsLearn
                    )
                    db.collection("users").document(docId).update(updates)
                        .addOnSuccessListener {
                            val prefs = getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
                            prefs.edit().apply {
                                putString("user_name", name)
                                putString("user_location", location)
                                apply()
                            }
                            Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to update profile: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "User not found: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}