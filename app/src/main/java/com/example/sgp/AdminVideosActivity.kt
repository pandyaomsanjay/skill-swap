package com.example.sgp

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.tabs.TabLayout
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import java.util.Locale

/**
 * Admin-only screen listing every uploaded skill video/playlist across every
 * user (not scoped to one uploader). Reuses the Skill model and Explore's
 * navy/cream dialog styling. From here the admin can:
 *  - filter by type via tabs (All / Videos / Playlists)
 *  - open a video (View)
 *  - jump to the uploader's profile (Profile)
 *  - for a playlist, manage its individual videos (delete one video out of
 *    the playlist without deleting the whole playlist)
 *  - delete a single video, or an entire playlist and every video inside
 *    it (Delete) — this removes it from Firestore *and* from Supabase
 *    Storage, so it disappears from Explore and the uploader's own profile
 *    in the same stroke.
 *  - remove the uploader's account entirely (Remove User) — deletes their
 *    "users" doc, every skill (video or playlist) they've posted, and every
 *    associated file in Supabase Storage (including every video embedded in
 *    any of their playlists). NOTE: this cannot delete their Firebase Auth
 *    sign-in credentials from the client; that needs a Cloud Function with
 *    the Admin SDK (a client app can't delete another user's auth account).
 *    Flagged with a TODO below.
 */
class AdminVideosActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: View
    private lateinit var etSearch: EditText
    private lateinit var tabLayout: TabLayout
    private lateinit var adapter: AdminVideoAdapter
    private val db: FirebaseFirestore by lazy { Firebase.firestore }
    private var listener: ListenerRegistration? = null

    private val allSkills = mutableListOf<Skill>()
    private val displayedSkills = mutableListOf<Skill>()
    private var currentQuery = ""

    // "all" | "single" | "playlist" — matches Skill.skillType for the latter two.
    private var currentTypeFilter = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_videos)

        recyclerView = findViewById(R.id.recyclerView)
        emptyState = findViewById(R.id.emptyState)
        etSearch = findViewById(R.id.etSearch)
        tabLayout = findViewById(R.id.tabLayout)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AdminVideoAdapter(
            displayedSkills,
            onViewClick = { skill -> openVideo(skill) },
            onProfileClick = { skill -> openProfile(skill) },
            onDeleteVideoClick = { skill -> confirmDeleteVideo(skill) },
            onRemoveUserClick = { skill -> confirmRemoveUser(skill) },
            onManagePlaylistClick = { skill -> showManagePlaylistDialog(skill) }
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

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTypeFilter = when (tab.position) {
                    1 -> "single"
                    2 -> "playlist"
                    else -> "all"
                }
                applyFilter()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
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
        val typeMatched = when (currentTypeFilter) {
            "single" -> allSkills.filter { it.skillType == "single" }
            "playlist" -> allSkills.filter { it.skillType == "playlist" }
            else -> allSkills
        }
        val filtered = if (currentQuery.isBlank()) {
            typeMatched
        } else {
            typeMatched.filter {
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

    /** Every Supabase Storage URL referenced by a skill doc (video, thumbnail,
     *  demo clip, and — for playlists — every embedded video's URL). */
    private fun collectStorageUrls(skill: Skill): List<String?> {
        val urls = mutableListOf<String?>()
        urls.add(skill.videoUrl)
        urls.add(skill.thumbnailUrl)
        urls.add(skill.demoVideoUrl)
        skill.videos?.forEach { video -> urls.add(video.videoUrl) }
        return urls
    }

    // ---------- Delete video / playlist (themed confirm dialog) ----------

    private fun confirmDeleteVideo(skill: Skill) {
        val isPlaylist = skill.skillType == "playlist"
        val videoCount = skill.videos?.size ?: 0

        val root = dialogCard()
        root.addView(dialogTitle(if (isPlaylist) "Delete playlist \"${skill.title}\"?" else "Delete \"${skill.title}\"?"))
        root.addView(TextView(this).apply {
            text = if (isPlaylist) {
                "This removes the whole playlist and all $videoCount video(s) in it — for everyone. It will disappear from Explore and from ${skill.userName}'s profile, and every video file will be deleted from storage. This can't be undone."
            } else {
                "This removes the video for everyone — it will disappear from Explore and from ${skill.userName}'s profile too, and the file will be deleted from storage. This can't be undone."
            }
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
        val isPlaylist = skill.skillType == "playlist"
        val storageUrls = collectStorageUrls(skill)

        db.collection("skills").document(skill.id).delete()
            .addOnSuccessListener {
                SupabaseStorageHelper.deleteFiles(storageUrls) { deletedCount, failedCount ->
                    runOnUiThread {
                        val label = if (isPlaylist) "Playlist" else "Video"
                        val msg = if (failedCount == 0) {
                            "$label deleted ($deletedCount file(s) removed from storage)"
                        } else {
                            "$label deleted, but $failedCount storage file(s) failed to remove — check Supabase manually"
                        }
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // ---------- Manage individual videos inside a playlist ----------

    private fun showManagePlaylistDialog(skill: Skill) {
        val videos = skill.videos.orEmpty()
        if (videos.isEmpty()) {
            Toast.makeText(this, "This playlist has no videos", Toast.LENGTH_SHORT).show()
            return
        }

        val outer = dialogCard()
        outer.addView(dialogTitle("Manage \"${skill.title}\""))
        outer.addView(TextView(this).apply {
            text = "Delete individual videos from this playlist. This removes each file from Supabase Storage too. Deleting the last video does not remove the playlist itself — use \"Delete Playlist\" for that."
            setTextColor(Color.parseColor("#456882"))
            textSize = 12.5f
            setPadding(0, dp(6), 0, dp(10))
        })

        val listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(320)
            )
            addView(listContainer)
        }
        outer.addView(scroll)

        val dialog = AlertDialog.Builder(this).setView(outer).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        videos.forEach { video ->
            listContainer.addView(playlistVideoRow(skill, video, listContainer))
        }

        val btnClose = pillButton("Close", Color.parseColor("#EAF1F5"), Color.parseColor("#456882")).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14); gravity = Gravity.END }
            setPadding(dp(24), dp(10), dp(24), dp(10))
            setOnClickListener { dialog.dismiss() }
        }
        outer.addView(btnClose)

        dialog.show()
    }

    private fun playlistVideoRow(skill: Skill, video: PlaylistVideo, container: LinearLayout): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        row.addView(TextView(this).apply {
            text = video.title.ifBlank { "Untitled video" }
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 13.5f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(pillButton("Delete", Color.parseColor("#C0392B"), Color.parseColor("#F9F3EF")).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(10) }
            setPadding(dp(16), dp(6), dp(16), dp(6))
            textSize = 12f
            setOnClickListener {
                deletePlaylistVideo(skill, video) { success ->
                    if (success) container.removeView(row)
                }
            }
        })
        return row
    }

    private fun deletePlaylistVideo(skill: Skill, video: PlaylistVideo, onDone: (Boolean) -> Unit) {
        val remaining = skill.videos.orEmpty().filterNot { it.id == video.id }
        db.collection("skills").document(skill.id)
            .update(mapOf("videos" to remaining, "videoCount" to remaining.size))
            .addOnSuccessListener {
                SupabaseStorageHelper.deleteFile(video.videoUrl) { success ->
                    runOnUiThread {
                        if (!success) {
                            Toast.makeText(this, "Removed from playlist, but the storage file failed to delete", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "Video removed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                onDone(true)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to remove video: ${e.message}", Toast.LENGTH_LONG).show()
                onDone(false)
            }
    }

    // ---------- Remove user (themed confirm dialog) ----------

    private fun confirmRemoveUser(skill: Skill) {
        val root = dialogCard()
        root.addView(dialogTitle("Remove ${skill.userName}?"))
        root.addView(TextView(this).apply {
            text = "This deletes their account record and every video and playlist they've posted (${skill.userId}), including every file in Supabase Storage. This can't be undone."
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

        // 1) Look up every skill (video or playlist) this user has posted so we
        //    can also clean up Supabase Storage — including every video
        //    embedded inside any of their playlists.
        db.collection("skills").whereEqualTo("userId", userEmail).get()
            .addOnSuccessListener { snapshot ->
                val userSkills = snapshot.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(Skill::class.java)
                    } catch (e: Exception) {
                        null
                    }
                }
                val storageUrls = mutableListOf<String?>()
                userSkills.forEach { storageUrls.addAll(collectStorageUrls(it)) }

                // 2) Delete every skill doc.
                val batch = db.batch()
                snapshot.documents.forEach { batch.delete(it.reference) }
                batch.commit()
                    .addOnSuccessListener {
                        // 3) Delete their user profile doc. Matches the doc by
                        // an "email" field — adjust if your users are keyed by
                        // document ID instead.
                        db.collection("users").whereEqualTo("email", userEmail).get()
                            .addOnSuccessListener { userSnapshot ->
                                val userBatch = db.batch()
                                userSnapshot.documents.forEach { userBatch.delete(it.reference) }
                                userBatch.commit()
                                    .addOnSuccessListener {
                                        // 4) Clean up every video/thumbnail/demo
                                        // file (including nested playlist
                                        // videos) from Supabase Storage.
                                        SupabaseStorageHelper.deleteFiles(storageUrls) { deletedCount, failedCount ->
                                            runOnUiThread {
                                                val msg = if (failedCount == 0) {
                                                    "$userEmail and their ${userSkills.size} video/playlist item(s) were removed ($deletedCount file(s) deleted from storage)"
                                                } else {
                                                    "$userEmail removed; $failedCount storage file(s) failed to delete — check Supabase manually"
                                                }
                                                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                                            }
                                        }
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
        private val onRemoveUserClick: (Skill) -> Unit,
        private val onManagePlaylistClick: (Skill) -> Unit
    ) : RecyclerView.Adapter<AdminVideoAdapter.ViewHolder>() {

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val ivThumbnail: ImageView = itemView.findViewById(R.id.ivThumbnail)
            val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
            val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
            val tvUser: TextView = itemView.findViewById(R.id.tvUser)
            val tvCredits: TextView = itemView.findViewById(R.id.tvCredits)
            val btnView: TextView = itemView.findViewById(R.id.btnView)
            val btnProfile: View = itemView.findViewById(R.id.btnProfile)
            val btnDeleteVideo: TextView = itemView.findViewById(R.id.btnDeleteVideo)
            val btnRemoveUser: View = itemView.findViewById(R.id.btnRemoveUser)
            val btnManagePlaylist: View = itemView.findViewById(R.id.btnManagePlaylist)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_admin_video, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val skill = skills[position]
            val isPlaylist = skill.skillType == "playlist"

            holder.tvTitle.text = skill.title
            holder.tvCategory.text = if (isPlaylist) "${skill.category} • Playlist" else skill.category
            holder.tvUser.text = "By ${skill.userName}  •  ${skill.userId}"
            holder.tvCredits.text = "${skill.credits} Credits"

            val thumbUrl = if (isPlaylist) skill.thumbnailUrl else skill.videoUrl
            if (!thumbUrl.isNullOrEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(thumbUrl)
                    .placeholder(R.drawable.baseline_videocam_24)
                    .error(R.drawable.baseline_videocam_24)
                    .into(holder.ivThumbnail)
            } else {
                holder.ivThumbnail.setImageResource(R.drawable.baseline_videocam_24)
            }

            holder.btnView.text = if (isPlaylist) "▶ Open" else "▶ View"
            holder.btnDeleteVideo.text = if (isPlaylist) "🗑 Playlist" else "🗑 Video"

            holder.btnManagePlaylist.visibility = if (isPlaylist) View.VISIBLE else View.GONE

            holder.btnView.setOnClickListener { onViewClick(skill) }
            holder.btnProfile.setOnClickListener { onProfileClick(skill) }
            holder.btnDeleteVideo.setOnClickListener { onDeleteVideoClick(skill) }
            holder.btnRemoveUser.setOnClickListener { onRemoveUserClick(skill) }
            holder.btnManagePlaylist.setOnClickListener { onManagePlaylistClick(skill) }
        }

        override fun getItemCount() = skills.size
    }
}