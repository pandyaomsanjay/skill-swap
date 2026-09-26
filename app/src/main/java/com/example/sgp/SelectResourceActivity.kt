package com.example.sgp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SelectResourceActivity : BaseActivity() {

    companion object {
        private const val TAG = "SelectResourceActivity"

        const val REQ_SELECT = 9001
        const val EXTRA_RESOURCE_ID = "resourceId"
        const val EXTRA_RESOURCE_TITLE = "resourceTitle"
        const val EXTRA_RESOURCE_TYPE = "resourceType"
        const val EXTRA_RESOURCE_URL = "resourceUrl"

        fun start(activity: Activity, requestCode: Int) {
            activity.startActivityForResult(
                Intent(activity, SelectResourceActivity::class.java),
                requestCode
            )
        }
    }

    private lateinit var adapter: ResourcePickerAdapter
    private val items = mutableListOf<Skill>()

    private lateinit var rv: RecyclerView
    private lateinit var emptyState: View
    private lateinit var loading: View
    private lateinit var errorState: View
    private lateinit var tvErrorMessage: TextView
    private lateinit var btnRetry: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_resource)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener {
                setResult(RESULT_CANCELED)
                finish()
            }

        loading = findViewById(R.id.loadingIndicator)
        emptyState = findViewById(R.id.emptyState)
        errorState = findViewById(R.id.errorState)
        tvErrorMessage = findViewById(R.id.tvErrorMessage)
        btnRetry = findViewById(R.id.btnRetry)

        rv = findViewById(R.id.rvResources)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = ResourcePickerAdapter(items) { chosen ->
            setResult(RESULT_OK, Intent().apply {
                putExtra(EXTRA_RESOURCE_ID, chosen.id)
                putExtra(EXTRA_RESOURCE_TITLE, chosen.title)
                putExtra(EXTRA_RESOURCE_TYPE, resourceTypeFor(chosen).name)
                putExtra(EXTRA_RESOURCE_URL, chosen.thumbnailUrl ?: chosen.videoUrl ?: "")
            })
            finish()
        }
        rv.adapter = adapter

        btnRetry.setOnClickListener { loadMyResources() }

        loadMyResources()
    }

    private fun resourceTypeFor(skill: Skill): TradeResourceType = when {
        skill.skillType == "playlist" -> TradeResourceType.COURSE
        !skill.videoUrl.isNullOrBlank() -> TradeResourceType.VIDEO
        else -> TradeResourceType.OTHER
    }

    private fun showState(state: String) {
        loading.visibility     = if (state == "loading") View.VISIBLE else View.GONE
        rv.visibility          = if (state == "content") View.VISIBLE else View.GONE
        emptyState.visibility  = if (state == "empty")   View.VISIBLE else View.GONE
        errorState.visibility  = if (state == "error")   View.VISIBLE else View.GONE
    }

    private fun loadMyResources() {
        val email = FirebaseAuth.getInstance().currentUser?.email
        if (email.isNullOrBlank()) {
            Log.e(TAG, "No signed-in user — cannot load resources")
            finish()
            return
        }

        showState("loading")

        lifecycleScope.launch {
            try {
                // ── IMPORTANT: NO orderBy() here. Sorting is done client-side
                // to avoid needing a Firestore composite index on
                // (userId ASC, timestamp DESC). Mirrors the query used in
                // Profile.loadUserSkills(), which is known to work.
                val snapshot = FirebaseFirestore.getInstance()
                    .collection("skills")
                    .whereEqualTo("userId", email)
                    .get()
                    .await()

                Log.d(TAG, "Loaded ${snapshot.size()} skill docs for $email")

                items.clear()
                snapshot.documents.forEach { d ->
                    d.toObject(Skill::class.java)?.let {
                        it.id = d.id
                        items.add(it)
                    }
                }

                // Client-side sort: newest first
                items.sortByDescending { it.timestamp }

                adapter.notifyDataSetChanged()

                when {
                    items.isEmpty() -> showState("empty")
                    else -> showState("content")
                }
            } catch (e: Exception) {
                // ── Log the REAL error, but show a friendly message ──
                Log.e(TAG, "Failed to load resources for $email", e)
                tvErrorMessage.text = buildString {
                    append("Couldn't load your resources.\n\n")
                    append(e.localizedMessage ?: e.message ?: "Unknown error")
                }
                showState("error")
            }
        }
    }

    // ═════════════════════════════════════════════════════════════
    // Adapter
    // ═════════════════════════════════════════════════════════════

    class ResourcePickerAdapter(
        private val list: List<Skill>,
        private val onSelect: (Skill) -> Unit
    ) : RecyclerView.Adapter<ResourcePickerAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val ivThumb: ImageView = v.findViewById(R.id.ivResourceThumb)
            val tvTitle: TextView = v.findViewById(R.id.tvResourceTitle)
            val tvType: TextView = v.findViewById(R.id.tvResourceType)
            val tvDesc: TextView = v.findViewById(R.id.tvResourceDescription)
            val tvDuration: TextView = v.findViewById(R.id.tvResourceDuration)
            val tvAvailability: TextView = v.findViewById(R.id.tvResourceAvailability)
            val btnSelect: View = v.findViewById(R.id.btnSelectResource)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_selectable_resource, parent, false)
        )

        override fun onBindViewHolder(h: VH, position: Int) {
            val s = list[position]
            val type = when {
                s.skillType == "playlist" -> TradeResourceType.COURSE
                !s.videoUrl.isNullOrBlank() -> TradeResourceType.VIDEO
                else -> TradeResourceType.OTHER
            }

            h.tvTitle.text = s.title
            h.tvType.text = "${type.emoji} ${type.label}"
            h.tvDesc.text = s.description.ifBlank { "No description" }
            h.tvDuration.text = s.duration.ifBlank { "—" }
            h.tvAvailability.text = "✅ Available"

            val url = s.thumbnailUrl ?: s.videoUrl
            if (!url.isNullOrBlank()) {
                Glide.with(h.itemView.context)
                    .load(url)
                    .placeholder(R.drawable.baseline_videocam_24)
                    .error(R.drawable.baseline_videocam_24)
                    .into(h.ivThumb)
            } else {
                h.ivThumb.setImageResource(R.drawable.baseline_videocam_24)
            }

            h.btnSelect.setOnClickListener { onSelect(s) }
            h.itemView.setOnClickListener { onSelect(s) }
        }

        override fun getItemCount() = list.size
    }
}