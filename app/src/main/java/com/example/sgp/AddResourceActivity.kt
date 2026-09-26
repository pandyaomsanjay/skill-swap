package com.example.sgp

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class AddResourceActivity : BaseActivity() {

    private companion object {
        const val TAG = "AddResourceActivity"
        const val MAX_FILE_SIZE_BYTES = 25L * 1024 * 1024   // 25 MB
        const val STORAGE_BUCKET = "media"
        const val COLLECTION = "skills"
    }

    private lateinit var db: FirebaseFirestore

    // Views
    private lateinit var actvResourceType: MaterialAutoCompleteTextView
    private lateinit var etTitle: TextInputEditText
    private lateinit var actvCategory: MaterialAutoCompleteTextView
    private lateinit var etDescription: TextInputEditText
    private lateinit var etDuration: TextInputEditText
    private lateinit var etCredits: TextInputEditText

    private lateinit var cardFilePicker: MaterialCardView
    private lateinit var tvFileName: TextView
    private lateinit var tvFileSize: TextView
    private lateinit var btnPickFile: MaterialButton
    private lateinit var btnClearFile: ImageButton

    private lateinit var cardTextNotes: MaterialCardView
    private lateinit var etTextContent: TextInputEditText

    private lateinit var cardLiveSession: MaterialCardView
    private lateinit var actvPlatform: MaterialAutoCompleteTextView
    private lateinit var actvMode: MaterialAutoCompleteTextView
    private lateinit var etDurationMin: TextInputEditText
    private lateinit var etMeetingLink: TextInputEditText
    private lateinit var etLocation: TextInputEditText

    private lateinit var btnPublish: MaterialButton
    private lateinit var progressBar: ProgressBar

    // Picked file
    private var pickedFileUri: Uri? = null
    private var pickedFileName: String = ""
    private var pickedFileSize: Long = 0L
    private var pickedFileMime: String = ""

    // Launchers
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { handleFilePicked(it) } }

    // Resource types & categories (kept as simple string lists)
    private val resourceTypes = listOf(
        "📹 Video",
        "📄 Notes",
        "📑 PDF",
        "📃 Document",
        "🎓 Course",
        "👨‍🏫 Live Teaching",
        "📚 Other"
    )
    private val resourceTypeKeys = listOf(
        "VIDEO", "NOTES", "PDF", "DOCUMENT", "COURSE", "LIVE_SESSION", "OTHER"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_resource)

        db = Firebase.firestore

        bindViews()
        setupToolbar()
        setupDropdowns()
        setupTypeWatcher()
        setupListeners()
    }

    private fun bindViews() {
        actvResourceType = findViewById(R.id.actvResourceType)
        etTitle = findViewById(R.id.etTitle)
        actvCategory = findViewById(R.id.actvCategory)
        etDescription = findViewById(R.id.etDescription)
        etDuration = findViewById(R.id.etDuration)
        etCredits = findViewById(R.id.etCredits)

        cardFilePicker = findViewById(R.id.cardFilePicker)
        tvFileName = findViewById(R.id.tvFileName)
        tvFileSize = findViewById(R.id.tvFileSize)
        btnPickFile = findViewById(R.id.btnPickFile)
        btnClearFile = findViewById(R.id.btnClearFile)

        cardTextNotes = findViewById(R.id.cardTextNotes)
        etTextContent = findViewById(R.id.etTextContent)

        cardLiveSession = findViewById(R.id.cardLiveSession)
        actvPlatform = findViewById(R.id.actvPlatform)
        actvMode = findViewById(R.id.actvMode)
        etDurationMin = findViewById(R.id.etDurationMin)
        etMeetingLink = findViewById(R.id.etMeetingLink)
        etLocation = findViewById(R.id.etLocation)

        btnPublish = findViewById(R.id.btnPublishResource)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupToolbar() {
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { finish() }
    }

    private fun setupDropdowns() {
        // Resource type
        val typeAdapter = ArrayAdapter(
            this, android.R.layout.simple_list_item_1, resourceTypes
        )
        actvResourceType.setAdapter(typeAdapter)
        actvResourceType.isFocusable = false
        actvResourceType.isClickable = true

        // Category (reuse the existing list)
        val categories = resources.getStringArray(R.array.skill_categories)
        val catAdapter = ArrayAdapter(
            this, android.R.layout.simple_list_item_1, categories.toList()
        )
        actvCategory.setAdapter(catAdapter)
        actvCategory.isFocusable = false
        actvCategory.isClickable = true

        // Platform
        val platforms = listOf("Google Meet", "Zoom", "Microsoft Teams", "In-person", "Other")
        actvPlatform.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, platforms))
        actvPlatform.isFocusable = false
        actvPlatform.isClickable = true

        // Mode
        val modes = listOf("Online", "Offline")
        actvMode.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, modes))
        actvMode.isFocusable = false
        actvMode.isClickable = true

        // Default selection
        actvResourceType.setText(resourceTypes[1], false) // NOTES by default
        actvMode.setText("Online", false)
    }

    /** Shows / hides the file / notes / live-session cards depending on the chosen type. */
    private fun setupTypeWatcher() {
        actvResourceType.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                applyTypeVisibility(currentTypeKey())
            }
        })
        applyTypeVisibility(currentTypeKey())
    }

    private fun currentTypeKey(): String {
        val idx = resourceTypes.indexOf(actvResourceType.text.toString())
        return if (idx in resourceTypeKeys.indices) resourceTypeKeys[idx] else "OTHER"
    }

    private fun applyTypeVisibility(typeKey: String) {
        // File picker: PDF, DOCUMENT, NOTES, COURSE, OTHER
        val showFile = typeKey in listOf("PDF", "DOCUMENT", "NOTES", "COURSE", "OTHER")
        cardFilePicker.visibility = if (showFile) View.VISIBLE else View.GONE

        // Text notes: NOTES only
        cardTextNotes.visibility = if (typeKey == "NOTES") View.VISIBLE else View.GONE

        // Live session fields: LIVE_SESSION only
        cardLiveSession.visibility = if (typeKey == "LIVE_SESSION") View.VISIBLE else View.GONE

        // Duration field is more relevant for videos/courses/live; hide for PDFs
        val showDuration = typeKey in listOf("VIDEO", "COURSE", "LIVE_SESSION")
        findViewById<View>(R.id.tilDuration).visibility =
            if (showDuration) View.VISIBLE else View.GONE
    }

    private fun setupListeners() {
        btnPickFile.setOnClickListener {
            // Restrict mime types per resource type
            val mime = when (currentTypeKey()) {
                "PDF" -> "application/pdf"
                "DOCUMENT" -> "*/*"
                else -> "*/*"
            }
            filePickerLauncher.launch(mime)
        }

        btnClearFile.setOnClickListener {
            pickedFileUri = null
            pickedFileName = ""
            pickedFileSize = 0L
            pickedFileMime = ""
            tvFileName.text = "No file selected"
            tvFileSize.text = ""
            btnClearFile.visibility = View.GONE
        }

        btnPublish.setOnClickListener { publish() }
    }

    // ═════════════════════════════════════════════════════════════
    // FILE PICK
    // ═════════════════════════════════════════════════════════════

    private fun handleFilePicked(uri: Uri) {
        val resolver = contentResolver
        var name = uri.lastPathSegment ?: "file"
        var size = 0L
        var mime = resolver.getType(uri) ?: "application/octet-stream"

        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
            }
        }

        if (size > MAX_FILE_SIZE_BYTES) {
            Toast.makeText(
                this,
                "File too large (max ${MAX_FILE_SIZE_BYTES / (1024 * 1024)} MB)",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        pickedFileUri = uri
        pickedFileName = name
        pickedFileSize = size
        pickedFileMime = mime

        tvFileName.text = name
        tvFileSize.text = "${size / 1024} KB"
        btnClearFile.visibility = View.VISIBLE
    }

    // ═════════════════════════════════════════════════════════════
    // PUBLISH
    // ═════════════════════════════════════════════════════════════

    private fun publish() {
        val prefs = getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
        val email = prefs.getString("user_email", "").orEmpty()
        val name = prefs.getString("user_name", "").orEmpty()
        if (email.isBlank()) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            return
        }

        val typeKey = currentTypeKey()
        val title = etTitle.text?.toString()?.trim().orEmpty()
        val category = actvCategory.text?.toString()?.trim().orEmpty()
        val description = etDescription.text?.toString()?.trim().orEmpty()
        val duration = etDuration.text?.toString()?.trim().orEmpty()
        val credits = etCredits.text?.toString()?.trim()?.toIntOrNull() ?: 0

        // ── Validation ──
        if (title.isEmpty()) {
            etTitle.error = "Title is required"; return
        }
        if (category.isEmpty()) {
            actvCategory.error = "Pick a category"; return
        }
        if (description.isEmpty()) {
            etDescription.error = "Description is required"; return
        }
        if (typeKey == "LIVE_SESSION") {
            val platform = actvPlatform.text?.toString().orEmpty()
            val mode = actvMode.text?.toString().orEmpty()
            if (platform.isBlank()) {
                actvPlatform.error = "Pick a platform"; return
            }
            if (mode.isBlank()) {
                actvMode.error = "Pick online or offline"; return
            }
            if (mode == "Offline" && etLocation.text?.toString().isNullOrBlank()) {
                etLocation.error = "Location required for offline sessions"; return
            }
        } else if (typeKey == "NOTES") {
            // Notes: require either text content or an uploaded file
            val hasText = !etTextContent.text?.toString().isNullOrBlank()
            val hasFile = pickedFileUri != null
            if (!hasText && !hasFile) {
                Toast.makeText(this, "Add text or upload a file", Toast.LENGTH_SHORT).show()
                return
            }
        } else {
            // PDF / DOCUMENT / COURSE / OTHER require a file
            if (pickedFileUri == null) {
                Toast.makeText(this, "Please upload a file", Toast.LENGTH_SHORT).show()
                return
            }
        }

        setLoading(true)

        lifecycleScope.launch {
            try {
                // ── 1. Upload file (if any) ──
                var resourceUrl: String? = null
                if (pickedFileUri != null) {
                    resourceUrl = uploadFileToSupabase(pickedFileUri!!, pickedFileName)
                    if (resourceUrl == null) {
                        setLoading(false)
                        Toast.makeText(
                            this@AddResourceActivity,
                            "File upload failed. Please try again.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }
                }

                // ── 2. Build Skill doc ──
                val docRef = db.collection(COLLECTION).document()
                val skill = Skill(
                    id = docRef.id,
                    userId = email,
                    userName = name.ifBlank { email },
                    title = title,
                    description = description,
                    category = category,
                    duration = duration,
                    credits = credits,
                    timestamp = System.currentTimeMillis(),
                    videoUrl = null,
                    skillType = "resource",             // marks it as a non-video resource
                    videos = null,
                    thumbnailUrl = null,
                    demoVideoUrl = null,
                    videoCount = 0,
                    totalDuration = duration,
                    resourceType = typeKey,
                    resourceUrl = resourceUrl,
                    textContent = etTextContent.text?.toString()?.trim()?.takeIf { it.isNotBlank() },
                    fileSize = pickedFileSize,
                    mimeType = pickedMimeFor(typeKey),
                    liveSessionPlatform = actvPlatform.text?.toString()?.trim()?.takeIf { typeKey == "LIVE_SESSION" && it.isNotBlank() },
                    liveSessionMode = actvMode.text?.toString()?.trim()?.lowercase()?.takeIf { typeKey == "LIVE_SESSION" && it.isNotBlank() },
                    liveSessionDurationMin = etDurationMin.text?.toString()?.toIntOrNull() ?: 0,
                    liveSessionMeetingLink = etMeetingLink.text?.toString()?.trim()?.takeIf { typeKey == "LIVE_SESSION" && it.isNotBlank() },
                    liveSessionLocation = etLocation.text?.toString()?.trim()?.takeIf { typeKey == "LIVE_SESSION" && it.isNotBlank() }
                )

                // ── 3. Save ──
                docRef.set(skill)
                    .addOnSuccessListener {
                        setLoading(false)
                        Toast.makeText(
                            this@AddResourceActivity,
                            "Resource published!",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        setLoading(false)
                        Log.e(TAG, "Firestore write failed", e)
                        Toast.makeText(
                            this@AddResourceActivity,
                            "Save failed: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            } catch (e: Exception) {
                setLoading(false)
                Log.e(TAG, "Publish failed", e)
                Toast.makeText(
                    this@AddResourceActivity,
                    "Upload error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun pickedMimeFor(typeKey: String): String? = when {
        typeKey == "PDF" -> "application/pdf"
        pickedFileMime.isNotBlank() -> pickedFileMime
        else -> null
    }

    private suspend fun uploadFileToSupabase(uri: Uri, originalName: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val bucket = SupabaseClient.client.storage.from(STORAGE_BUCKET)
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@withContext null

                if (bytes.size > MAX_FILE_SIZE_BYTES) return@withContext null

                val ext = originalName.substringAfterLast('.', "")
                val safeExt = if (ext.isNotBlank()) ".$ext" else ""
                val path = "resources/${UUID.randomUUID()}$safeExt"

                bucket.upload(path = path, data = bytes) { upsert = true }

                "https://ghrxltlstncjcizyyqfo.supabase.co/storage/v1/object/public/$STORAGE_BUCKET/$path"
            } catch (e: Exception) {
                Log.e(TAG, "Supabase upload failed", e)
                null
            }
        }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnPublish.isEnabled = !loading
    }
}