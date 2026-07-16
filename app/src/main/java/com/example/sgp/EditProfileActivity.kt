package com.example.sgp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore

class EditProfileActivity : BaseActivity() {

    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        db = Firebase.firestore

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