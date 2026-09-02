package com.example.sgp

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import java.util.Locale

/**
 * Admin-only screen listing every uploaded skill video across every user
 * (not scoped to one uploader). Reuses the Skill model and Explore's
 * navy/cream dialog styling. From here the admin can:
 *  - open a video (View)
 *  - jump to the uploader's profile (Profile)
 *  - delete just that video/skill (Delete Video) — this removes it from
 *    Firestore, so it disappears from Explore and the uploader's own
 *    profile in the same stroke, since both read from the same "skills"
 *    collection.
 *  - remove the uploader's account entirely (Remove User) — deletes their
 *    "users" doc and every skill they've posted. NOTE: this cannot delete
 *    their Firebase Auth sign-in credentials from the client; that needs a
 *    Cloud Function with the Admin SDK (a client app can't delete another
 *    user's auth account). Flagged with a TODO below.
 */
class AdminVideosActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: View
    private lateinit var etSearch: EditText
    private lateinit var adapter: AdminVideoAdapter
    private val db: FirebaseFirestore by lazy { Firebase.firestore }
    private var listener: ListenerRegistration? = null

    private val allSkills = mutableListOf<Skill>()
    private val displayedSkills = mutableListOf<Skill>()
    private var currentQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_videos)

        recyclerView = findViewById(R.id.recyclerView)
        emptyState = findViewById(R.id.emptyState)
        etSearch = findViewById(R.id.etSearch)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AdminVideoAdapter(
            displayedSkills,
            onViewClick = { skill -> openVideo(skill) },
            onProfileClick = { skill -> openProfile(skill) },
            onDeleteVideoClick = { skill -> confirmDeleteVideo(skill) },
            onRemoveUserClick = { skill -> confirmRemoveUser(skill) }
        )
        recyclerView.adapter = adapter

        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentQuery = s?.toString()?.trim()?.lowercase(Locale.getDefault()) ?: ""
                applyFilter()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        loadSkills()
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
    }

    // ---------- Data ----------

    private fun loadSkills() {
        listener = db.collection("skills")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Load failed: ${error.message}", Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }
                allSkills.clear()
                snapshot?.documents?.forEach { doc ->
                    try {
                        doc.toObject(Skill::class.java)?.let { allSkills.add(it) }
                    } catch (e: Exception) {
                        // Skip a single malformed doc instead of crashing the whole list.
                    }
                }
                applyFilter()
            }
    }

    private fun applyFilter() {
        val filtered = if (currentQuery.isBlank()) {
            allSkills
        } else {
            allSkills.filter {
                it.title.lowercase(Locale.getDefault()).contains(currentQuery) ||
                        it.userName.lowercase(Locale.getDefault()).contains(currentQuery) ||
                        it.userId.lowercase(Locale.getDefault()).contains(currentQuery) ||
                        it.category.lowercase(Locale.getDefault()).contains(currentQuery)
            }
        }
        displayedSkills.clear()
        displayedSkills.addAll(filtered)
        adapter.notifyDataSetChanged()
        emptyState.visibility = if (displayedSkills.isEmpty()) View.VISIBLE else View.GONE
    }

    // ---------- Actions ----------

    private fun openVideo(skill: Skill) {
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

    private fun openProfile(skill: Skill) {
        val intent = Intent(this, UserProfileActivity::class.java)
        intent.putExtra("userId", skill.userId)
        intent.putExtra("userName", skill.userName)
        startActivity(intent)
    }

    // ---------- Delete video (themed confirm dialog) ----------

    private fun confirmDeleteVideo(skill: Skill) {
        val root = dialogCard()
        root.addView(dialogTitle("Delete \"${skill.title}\"?"))
        root.addView(TextView(this).apply {
            text = "This removes the video for everyone — it will disappear from Explore and from ${skill.userName}'s profile too. This can't be undone."
            setTextColor(Color.parseColor("#456882"))
            textSize = 13f
            setPadding(0, dp(8), 0, dp(4))
        })

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(18) }
        }
        row.addView(pillButton("Cancel", Color.parseColor("#EAF1F5"), Color.parseColor("#456882")).apply {
            setPadding(dp(24), dp(10), dp(24), dp(10))
            setOnClickListener { dialog.dismiss() }
        })
        row.addView(pillButton("Delete", Color.parseColor("#C0392B"), Color.parseColor("#F9F3EF")).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(10) }
            setPadding(dp(24), dp(10), dp(24), dp(10))
            setOnClickListener {
                dialog.dismiss()
                deleteVideo(skill)
            }
        })
        root.addView(row)

        dialog.show()
    }

    private fun deleteVideo(skill: Skill) {
        if (skill.id.isEmpty()) {
            Toast.makeText(this, "Couldn't identify this video", Toast.LENGTH_SHORT).show()
            return
        }
        db.collection("skills").document(skill.id).delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Video deleted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_LONG).show()
            }
        // NOTE: if videoUrl points at Firebase Storage, also delete the file there,
        // e.g. Firebase.storage.getReferenceFromUrl(skill.videoUrl).delete()
    }

    // ---------- Remove user (themed confirm dialog) ----------

    private fun confirmRemoveUser(skill: Skill) {
        val root = dialogCard()
        root.addView(dialogTitle("Remove ${skill.userName}?"))
        root.addView(TextView(this).apply {
            text = "This deletes their account record and every skill video they've posted (${skill.userId}). This can't be undone."
            setTextColor(Color.parseColor("#456882"))
            textSize = 13f
            setPadding(0, dp(8), 0, dp(4))
        })

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(18) }
        }
        row.addView(pillButton("Cancel", Color.parseColor("#EAF1F5"), Color.parseColor("#456882")).apply {
            setPadding(dp(24), dp(10), dp(24), dp(10))
            setOnClickListener { dialog.dismiss() }
        })
        row.addView(pillButton("Remove", Color.parseColor("#C0392B"), Color.parseColor("#F9F3EF")).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(10) }
            setPadding(dp(24), dp(10), dp(24), dp(10))
            setOnClickListener {
                dialog.dismiss()
                removeUser(skill)
            }
        })
        root.addView(row)

        dialog.show()
    }

    private fun removeUser(skill: Skill) {
        val userEmail = skill.userId
        if (userEmail.isEmpty()) {
            Toast.makeText(this, "Couldn't identify this user", Toast.LENGTH_SHORT).show()
            return
        }

        // 1) Delete every skill this user has posted.
        db.collection("skills").whereEqualTo("userId", userEmail).get()
            .addOnSuccessListener { snapshot ->
                val batch = db.batch()
                snapshot.documents.forEach { batch.delete(it.reference) }
                batch.commit()
                    .addOnSuccessListener {
                        // 2) Delete their user profile doc. Matches the doc by
                        // an "email" field — adjust if your users are keyed by
                        // document ID instead.
                        db.collection("users").whereEqualTo("email", userEmail).get()
                            .addOnSuccessListener { userSnapshot ->
                                val userBatch = db.batch()
                                userSnapshot.documents.forEach { userBatch.delete(it.reference) }
                                userBatch.commit()
                                    .addOnSuccessListener {
                                        Toast.makeText(
                                            this,
                                            "$userEmail and their videos were removed",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        // TODO: deleting their Firebase Auth sign-in
                                        // account requires the Admin SDK — call a
                                        // Cloud Function here (e.g. via Functions
                                        // callable) to fully revoke their login.
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(this, "Removed videos, but failed to remove user doc: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                            }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to remove videos: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to look up user's videos: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // ---------- Themed-dialog helpers (same navy/cream card + pill buttons as Explore) ----------

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

    private fun pillButton(text: String, bgColor: Int, textColor: Int): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(textColor)
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(bgColor)
            }
            isClickable = true
            isFocusable = true
        }
    }

    // ---------- Adapter ----------

    class AdminVideoAdapter(
        private val skills: List<Skill>,
        private val onViewClick: (Skill) -> Unit,
        private val onProfileClick: (Skill) -> Unit,
        private val onDeleteVideoClick: (Skill) -> Unit,
        private val onRemoveUserClick: (Skill) -> Unit
    ) : RecyclerView.Adapter<AdminVideoAdapter.ViewHolder>() {

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val ivThumbnail: ImageView = itemView.findViewById(R.id.ivThumbnail)
            val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
            val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
            val tvUser: TextView = itemView.findViewById(R.id.tvUser)
            val tvCredits: TextView = itemView.findViewById(R.id.tvCredits)
            val btnView: View = itemView.findViewById(R.id.btnView)
            val btnProfile: View = itemView.findViewById(R.id.btnProfile)
            val btnDeleteVideo: View = itemView.findViewById(R.id.btnDeleteVideo)
            val btnRemoveUser: View = itemView.findViewById(R.id.btnRemoveUser)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_admin_video, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val skill = skills[position]
            holder.tvTitle.text = skill.title
            holder.tvCategory.text = skill.category
            holder.tvUser.text = "By ${skill.userName}  •  ${skill.userId}"
            holder.tvCredits.text = "${skill.credits} Credits"

            if (!skill.videoUrl.isNullOrEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(skill.videoUrl)
                    .placeholder(R.drawable.baseline_videocam_24)
                    .error(R.drawable.baseline_videocam_24)
                    .into(holder.ivThumbnail)
            } else {
                holder.ivThumbnail.setImageResource(R.drawable.baseline_videocam_24)
            }

            holder.btnView.setOnClickListener { onViewClick(skill) }
            holder.btnProfile.setOnClickListener { onProfileClick(skill) }
            holder.btnDeleteVideo.setOnClickListener { onDeleteVideoClick(skill) }
            holder.btnRemoveUser.setOnClickListener { onRemoveUserClick(skill) }
        }

        override fun getItemCount() = skills.size
    }
}