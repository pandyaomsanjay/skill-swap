package com.example.sgp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder   // built-in Android SDK class that converts coordinates to a readable address and back
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient //Google Play Services library that fetches the device's raw GPS coordinates
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import java.io.IOException
import java.util.*

class CompleteProfileActivity : BaseActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var storageRef: StorageReference
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var stepIndicator: LinearLayout // dots
    private lateinit var profileImage: ImageView
    private lateinit var btnChangePhoto: ImageButton
    private lateinit var etName: TextInputEditText
    private lateinit var etLocation: TextInputEditText
    private lateinit var etLanguage: AutoCompleteTextView
    private lateinit var btnAutoDetect: ImageButton
    private lateinit var locationValidationIcon: ImageView
    private lateinit var btnSave: MaterialButton
    private lateinit var nameLayout: TextInputLayout
    private lateinit var locationLayout: TextInputLayout
    private lateinit var languageLayout: TextInputLayout

    // Activity Result Launchers
    private lateinit var galleryLauncher: ActivityResultLauncher<String>
    private lateinit var cameraLauncher: ActivityResultLauncher<Void?>

    private var selectedImageUri: Uri? = null
    private var currentLocationLatLng: Pair<Double, Double>? = null
    private var isLocationValid = false
    private var locationValidationJob: Job? = null

    // Intent extras
    private var email = ""
    private var nameFromIntent = ""
    private var password = ""
    private var isGoogle = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_complete_profile)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        storageRef = FirebaseStorage.getInstance().reference
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Get extras
        email = intent.getStringExtra("email") ?: ""
        nameFromIntent = intent.getStringExtra("name") ?: ""
        password = intent.getStringExtra("password") ?: ""
        isGoogle = intent.getBooleanExtra("isGoogle", false)

        // The OTP -> CompleteProfile chain doesn't currently forward a "name" extra,
        // so for Google sign-ins, recover the display name Createaccount.kt stashed in TempPrefs
        if (nameFromIntent.isEmpty() && isGoogle) {
            val tempPrefs = getSharedPreferences("TempPrefs", MODE_PRIVATE)
            nameFromIntent = tempPrefs.getString("google_name", "") ?: ""
            tempPrefs.edit().clear().apply()
        }

        // Initialize activity result launchers
        initializeLaunchers()

        bindViews()
        setupListeners()

        // Pre-fill name if provided from Google
        if (nameFromIntent.isNotEmpty()) {
            etName.setText(nameFromIntent)
        }
    }

    private fun initializeLaunchers() {
        // Gallery launcher
        galleryLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                selectedImageUri = it
                profileImage.setImageURI(it)
            }
        }

        // Camera launcher
        cameraLauncher = registerForActivityResult(
            ActivityResultContracts.TakePicturePreview()
        ) { bitmap ->
            bitmap?.let {
                val path = MediaStore.Images.Media.insertImage(
                    contentResolver,
                    it,
                    "ProfileImage_${System.currentTimeMillis()}",
                    null
                )
                path?.let { imagePath ->
                    val uri = Uri.parse(imagePath)
                    selectedImageUri = uri
                    profileImage.setImageURI(uri)
                }
            }
        }
    }

    private fun bindViews() {
        stepIndicator = findViewById(R.id.stepIndicator)
        profileImage = findViewById(R.id.profileImage)
        btnChangePhoto = findViewById(R.id.btnChangePhoto)
        etName = findViewById(R.id.etName)
        etLocation = findViewById(R.id.etLocation)
        etLanguage = findViewById(R.id.etLanguage)
        btnAutoDetect = findViewById(R.id.btnAutoDetect)
        locationValidationIcon = findViewById(R.id.locationValidationIcon)
        btnSave = findViewById(R.id.btnSave)
        nameLayout = findViewById(R.id.nameLayout)
        locationLayout = findViewById(R.id.locationLayout)
        languageLayout = findViewById(R.id.languageLayout)

        val languages = arrayOf("English", "Spanish", "French", "German", "Hindi", "Arabic", "Chinese", "Japanese")
        val languageAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, languages)
        etLanguage.setAdapter(languageAdapter)
        btnSave.isEnabled = false // locked until location is verified valid

        // Apply styling to match OTP page
        nameLayout.boxBackgroundColor = ContextCompat.getColor(this, R.color.surface)
        locationLayout.boxBackgroundColor = ContextCompat.getColor(this, R.color.surface)
        languageLayout.boxBackgroundColor = ContextCompat.getColor(this, R.color.surface)
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

        // Validate location on text change — debounced, off the main thread
        etLocation.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val text = s.toString()
                locationValidationJob?.cancel()
                if (text.isBlank()) {
                    locationValidationIcon.visibility = View.GONE
                    isLocationValid = false
                    btnSave.isEnabled = false
                    return
                }
                // Lock Save immediately while we (re)validate — don't let a stale valid state slip through
                isLocationValid = false
                btnSave.isEnabled = false
                locationValidationIcon.visibility = View.GONE
                locationValidationJob = lifecycleScope.launch {
                    delay(600) // debounce: wait for typing to pause before hitting the network
                    validateLocation(text)
                }
            }
        })
    }

    private fun showImagePickerDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        AlertDialog.Builder(this)
            .setTitle("Profile Picture")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // Launch camera
                        cameraLauncher.launch(null)
                    }
                    1 -> {
                        // Launch gallery
                        galleryLauncher.launch("image/*")
                    }
                }
            }
            .show()
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
                lifecycleScope.launch {
                    try {
                        val addresses = withContext(Dispatchers.IO) {
                            val geocoder = Geocoder(this@CompleteProfileActivity, Locale.getDefault())
                            geocoder.getFromLocation(it.latitude, it.longitude, 1)
                        }
                        if (!addresses.isNullOrEmpty()) {
                            val address = addresses[0].getAddressLine(0)
                            etLocation.setText(address) // triggers the debounced TextWatcher validation
                        }
                    } catch (e: IOException) {
                        Toast.makeText(this@CompleteProfileActivity, "Geocoder error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Location fetch failed: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun validateLocation(locationText: String) {
        if (locationText.isBlank()) {
            locationValidationIcon.visibility = View.GONE
            isLocationValid = false
            btnSave.isEnabled = false
            return
        }
        try {
            val addresses = withContext(Dispatchers.IO) {
                val geocoder = Geocoder(this@CompleteProfileActivity, Locale.getDefault())
                geocoder.getFromLocationName(locationText, 1)
            }
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                currentLocationLatLng = Pair(addr.latitude, addr.longitude)
                locationValidationIcon.visibility = View.VISIBLE
                locationValidationIcon.setImageResource(R.drawable.ic_check_circle_green)
                isLocationValid = true
                btnSave.isEnabled = true
                Toast.makeText(this@CompleteProfileActivity, "✓ Location verified", Toast.LENGTH_SHORT).show()
            } else {
                locationValidationIcon.visibility = View.VISIBLE
                locationValidationIcon.setImageResource(R.drawable.ic_error_red)
                isLocationValid = false
                btnSave.isEnabled = false
                Toast.makeText(this@CompleteProfileActivity, "Please enter a valid location", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            // Network/geocoder unavailable — treat as unverified, keep Save locked
            locationValidationIcon.visibility = View.VISIBLE
            locationValidationIcon.setImageResource(R.drawable.ic_error_red)
            isLocationValid = false
            btnSave.isEnabled = false
            Toast.makeText(this@CompleteProfileActivity, "Couldn't verify location — check your connection", Toast.LENGTH_SHORT).show()
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
        val language = etLanguage.text.toString().trim()
        val lat = currentLocationLatLng?.first ?: 0.0
        val lng = currentLocationLatLng?.second ?: 0.0
        val userId = auth.currentUser?.uid ?: email

        lifecycleScope.launch {
            try {
                var imageUrl: String? = null
                var imagePath: String? = null

                if (selectedImageUri != null) {
                    val (url, path) = SupabaseImageUploader.uploadProfileImage(
                        context = this@CompleteProfileActivity,
                        imageUri = selectedImageUri!!,
                        userId = userId,
                        oldImagePath = null // brand-new profile, nothing to delete yet
                    )
                    imageUrl = url
                    imagePath = path
                }

                saveUserToFirestore(name, location, language, lat, lng, imageUrl, imagePath)
            } catch (e: Exception) {
                Toast.makeText(this@CompleteProfileActivity, "Image upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                btnSave.isEnabled = true
                btnSave.text = "Save Profile"
            }
        }
    }

    private fun saveUserToFirestore(
        name: String,
        location: String,
        language: String,
        lat: Double,
        lng: Double,
        profileImageUrl: String?,
        profileImagePath: String?
    ) {
        val user = Users(
            name = name,
            email = email,
            location = location,
            latitude = lat,
            longitude = lng,
            profileImage = profileImageUrl ?: "",
            profileImagePath = profileImagePath,
            isLocationVerified = true,
            loginProvider = if (isGoogle) "google" else "email",
            createdAt = System.currentTimeMillis(),
            phone = "",
            rating = 0.0,
            completedTrades = 0,
            credits = 1250,
            userType = "standard",
            joinedDate = System.currentTimeMillis(),
            language = language
        )

        val docId = auth.currentUser?.uid ?: email
        db.collection("users").document(docId).set(user)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile saved successfully!", Toast.LENGTH_SHORT).show()
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
                startActivity(Intent(this, Home::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                btnSave.isEnabled = true
                btnSave.text = "Save Profile"
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