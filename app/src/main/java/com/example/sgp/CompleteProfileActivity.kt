package com.example.sgp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.io.IOException
import java.util.*

class CompleteProfileActivity : BaseActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var storageRef: StorageReference
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // UI
    private lateinit var stepIndicator: LinearLayout // dots
    private lateinit var profileImage: ImageView
    private lateinit var btnChangePhoto: ImageButton
    private lateinit var etName: TextInputEditText
    private lateinit var etLocation: TextInputEditText
    private lateinit var btnAutoDetect: Button
    private lateinit var locationValidationIcon: ImageView
    private lateinit var btnSave: MaterialButton

    private var selectedImageUri: Uri? = null
    private var currentLocationLatLng: Pair<Double, Double>? = null
    private var isLocationValid = false

    // Intent extras
    private var email = ""
    private var nameFromIntent = ""
    private var password = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_complete_profile)

        db = FirebaseFirestore.getInstance()
        storageRef = FirebaseStorage.getInstance().reference
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Get extras
        email = intent.getStringExtra("email") ?: ""
        nameFromIntent = intent.getStringExtra("name") ?: ""
        password = intent.getStringExtra("password") ?: ""

        bindViews()
        setupListeners()

        // Pre-fill name if provided from Google
        if (nameFromIntent.isNotEmpty()) {
            etName.setText(nameFromIntent)
        }
    }

    private fun bindViews() {
        stepIndicator = findViewById(R.id.stepIndicator)
        profileImage = findViewById(R.id.profileImage)
        btnChangePhoto = findViewById(R.id.btnChangePhoto)
        etName = findViewById(R.id.etName)
        etLocation = findViewById(R.id.etLocation)
        btnAutoDetect = findViewById(R.id.btnAutoDetect)
        locationValidationIcon = findViewById(R.id.locationValidationIcon)
        btnSave = findViewById(R.id.btnSave)

        // Set up step dots (3 steps, second active? We'll just show visual)
        // We'll just have a static indicator for step 3.
    }

    private fun setupListeners() {
        btnChangePhoto.setOnClickListener {
            showImagePickerDialog()
        }

        btnAutoDetect.setOnClickListener {
            getCurrentLocation()
        }

        btnSave.setOnClickListener {
            if (validateInputs()) {
                saveProfile()
            }
        }

        // Validate location on text change (with debounce)
        etLocation.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                validateLocation(s.toString())
            }
        })
    }

    private fun showImagePickerDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        AlertDialog.Builder(this)
            .setTitle("Profile Picture")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> cameraLauncher.launch(null)
                    1 -> galleryLauncher.launch("image/*")
                }
            }
            .show()
    }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                selectedImageUri = it
                profileImage.setImageURI(it)
            }
        }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            bitmap?.let {
                val path = MediaStore.Images.Media.insertImage(
                    contentResolver,
                    it,
                    "ProfileImage_${System.currentTimeMillis()}",
                    null
                )
                val uri = Uri.parse(path)
                selectedImageUri = uri
                profileImage.setImageURI(uri)
            }
        }

    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                val geocoder = Geocoder(this, Locale.getDefault())
                try {
                    val addresses = geocoder.getFromLocation(it.latitude, it.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0].getAddressLine(0)
                        etLocation.setText(address)
                        currentLocationLatLng = Pair(it.latitude, it.longitude)
                        validateLocation(address)
                    }
                } catch (e: IOException) {
                    Toast.makeText(this, "Geocoder error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Location fetch failed: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun validateLocation(locationText: String) {
        if (locationText.isBlank()) {
            locationValidationIcon.visibility = View.GONE
            isLocationValid = false
            return
        }
        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocationName(locationText, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                currentLocationLatLng = Pair(addr.latitude, addr.longitude)
                locationValidationIcon.visibility = View.VISIBLE
                locationValidationIcon.setImageResource(R.drawable.ic_check_circle_green)
                isLocationValid = true
                // Optionally update EditText with formatted address
            } else {
                locationValidationIcon.visibility = View.VISIBLE
                locationValidationIcon.setImageResource(R.drawable.ic_error_red)
                isLocationValid = false
                Toast.makeText(this, "Please enter a valid location", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            locationValidationIcon.visibility = View.GONE
            isLocationValid = false
        }
    }

    private fun validateInputs(): Boolean {
        val name = etName.text.toString().trim()
        if (name.isEmpty()) {
            etName.error = "Full name is required"
            return false
        }
        if (!isLocationValid) {
            Toast.makeText(this, "Please enter a valid location", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun saveProfile() {
        btnSave.isEnabled = false
        btnSave.text = "Saving..."

        val name = etName.text.toString().trim()
        val location = etLocation.text.toString().trim()
        val lat = currentLocationLatLng?.first ?: 0.0
        val lng = currentLocationLatLng?.second ?: 0.0

        // Upload profile picture if selected
        val uploadTask = if (selectedImageUri != null) {
            val ref = storageRef.child("profile_images/${email}_${System.currentTimeMillis()}.jpg")
            ref.putFile(selectedImageUri!!)
                .continueWithTask { task ->
                    if (!task.isSuccessful) {
                        task.exception?.let { throw it }
                    }
                    ref.downloadUrl
                }
        } else {
            // No image, return null
            null
        }

        if (uploadTask != null) {
            uploadTask.addOnSuccessListener { downloadUri ->
                saveUserToFirestore(name, location, lat, lng, downloadUri.toString())
            }.addOnFailureListener { e ->
                Toast.makeText(this, "Image upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                btnSave.isEnabled = true
                btnSave.text = "Save"
            }
        } else {
            saveUserToFirestore(name, location, lat, lng, null)
        }
    }

    private fun saveUserToFirestore(name: String, location: String, lat: Double, lng: Double, profileImageUrl: String?) {
        val user = Users(
            name = name,
            email = email,
            location = location,
            latitude = lat,
            longitude = lng,
            profileImage = profileImageUrl ?: "",
            isLocationVerified = true,
            loginProvider = if (password.isEmpty()) "google" else "email",
            createdAt = System.currentTimeMillis(),
            // other fields default
            phone = "",
            password = "",
            rating = 0.0,
            completedTrades = 0,
            credits = 1250,
            userType = "standard",
            joinedDate = System.currentTimeMillis()
        )

        // Save using email as document ID or use UID? We'll use email as unique.
        db.collection("users").document(email).set(user)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile saved!", Toast.LENGTH_SHORT).show()
                // Save session
                val prefs = getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
                with(prefs.edit()) {
                    putString("user_name", name)
                    putString("user_email", email)
                    putInt("user_points", 1250)
                    putInt("user_total_trades", 0)
                    putFloat("user_rating", 0f)
                    putInt("user_total_skills", 0)
                    apply()
                }
                // Navigate to Home
                startActivity(Intent(this, Home::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                btnSave.isEnabled = true
                btnSave.text = "Save"
            }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation()
        } else {
            Toast.makeText(this, "Location permission required for auto‑detect", Toast.LENGTH_SHORT).show()
        }
    }
}