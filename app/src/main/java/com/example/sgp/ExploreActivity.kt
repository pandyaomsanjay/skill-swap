package com.example.sgp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore

class ExploreActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SkillAdapter
    private val allSkills = mutableListOf<Skill>()
    private val displayedSkills = mutableListOf<Skill>()
    private lateinit var db: FirebaseFirestore
    private var listener: ListenerRegistration? = null

    // Five report reasons suited to a skill-swap marketplace: content quality,
    // trust/scam risk, behavior, and IP concerns cover the realistic categories
    // an admin would need to triage on this kind of app.
    private val reportReasons = arrayOf(
        "Inappropriate or Offensive Content",
        "Spam or Misleading Information",
        "Fake Skill or Scam",
        "Harassment or Abusive Behavior",
        "Copyright or Intellectual Property Violation"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_explore)

        db = Firebase.firestore

        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = SkillAdapter(
            displayedSkills,
            onItemClick = { skill ->
                if (skill.skillType == "single" && !skill.videoUrl.isNullOrEmpty()) {
                    playVideo(skill.videoUrl!!)
                } else if (skill.skillType == "playlist") {
                    val intent = Intent(this, PlaylistActivity::class.java)
                    intent.putExtra("skillId", skill.id)
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "No video available", Toast.LENGTH_SHORT).show()
                }
            },
            onReportClick = { skill -> showReportDialog(skill) }
        )
        recyclerView.adapter = adapter

        val etSearch = findViewById<TextInputEditText>(R.id.etSearch)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterSkills(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        setupCategoryChips()
        loadSkills()

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.selectedItemId = R.id.nav_explore
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, Home::class.java))
                    finish()
                    true
                }
                R.id.nav_explore -> true
                R.id.nav_add_skill -> {
                    startActivity(Intent(this, AddSkillActivity::class.java))
                    true
                }
                R.id.nav_trades -> {
                    startActivity(Intent(this, MyTradesActivity::class.java))
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, Profile::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun setupCategoryChips() {
        val chipAll = findViewById<com.google.android.material.chip.Chip>(R.id.chipAll)
        val chipProgramming = findViewById<com.google.android.material.chip.Chip>(R.id.chipProgramming)
        val chipDesign = findViewById<com.google.android.material.chip.Chip>(R.id.chipDesign)
        val chipMusic = findViewById<com.google.android.material.chip.Chip>(R.id.chipMusic)
        val chipLanguage = findViewById<com.google.android.material.chip.Chip>(R.id.chipLanguage)

        val clickListener = { chip: com.google.android.material.chip.Chip, category: String ->
            chip.setOnClickListener {
                filterByCategory(category)
                resetChipColors()
                chip.isChecked = true
            }
        }

        clickListener(chipAll, "All")
        clickListener(chipProgramming, "Programming")
        clickListener(chipDesign, "Design")
        clickListener(chipMusic, "Music")
        clickListener(chipLanguage, "Language")
    }

    private fun resetChipColors() {
        val chips = listOf(
            findViewById<com.google.android.material.chip.Chip>(R.id.chipAll),
            findViewById(R.id.chipProgramming),
            findViewById(R.id.chipDesign),
            findViewById(R.id.chipMusic),
            findViewById(R.id.chipLanguage)
        )
        chips.forEach { it.isChecked = false }
    }

    private fun filterByCategory(category: String) {
        displayedSkills.clear()
        if (category == "All") {
            displayedSkills.addAll(allSkills)
        } else {
            displayedSkills.addAll(allSkills.filter { it.category == category })
        }
        adapter.notifyDataSetChanged()
    }

    private fun loadSkills() {
        listener = db.collection("skills")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this@ExploreActivity, error.message, Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                allSkills.clear()
                snapshot?.documents?.forEach { doc ->
                    doc.toObject(Skill::class.java)?.let { allSkills.add(it) }
                }
                displayedSkills.clear()
                displayedSkills.addAll(allSkills)
                adapter.notifyDataSetChanged()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
    }

    private fun filterSkills(query: String) {
        displayedSkills.clear()
        if (query.isEmpty()) {
            displayedSkills.addAll(allSkills)
        } else {
            displayedSkills.addAll(allSkills.filter {
                it.title.contains(query, true) ||
                        it.category.contains(query, true) ||
                        it.description.contains(query, true)
            })
        }
        adapter.notifyDataSetChanged()
    }

    private fun playVideo(videoUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
            intent.setDataAndType(Uri.parse(videoUrl), "video/*")
            startActivity(Intent.createChooser(intent, "Play video with"))
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot play video", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }

    // ---------------- Report skill / user ----------------

    private fun showReportDialog(skill: Skill) {
        // IMPORTANT: your Firestore rule for reports/{reportId} requires
        // request.resource.data.reporterId == request.auth.token.email —
        // so this MUST be the signed-in user's email, not their uid, or the
        // write will be rejected with PERMISSION_DENIED.
        val reporterEmail = FirebaseAuth.getInstance().currentUser?.email
        if (reporterEmail.isNullOrEmpty()) {
            Toast.makeText(this, "Please log in to report", Toast.LENGTH_SHORT).show()
            return
        }
        // NOTE: per your skills/{skillId} rule, Skill.userId is also stored
        // as the poster's email (request.resource.data.userId == currentUserEmail()),
        // so comparing it against reporterEmail here is correct.
        if (reporterEmail == skill.userId) {
            Toast.makeText(this, "You can't report your own skill", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Report \"${skill.title}\"")
            .setItems(reportReasons) { _, which ->
                submitReport(skill, reportReasons[which], reporterEmail)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun submitReport(skill: Skill, reason: String, reporterEmail: String) {
        val docRef = db.collection("reports").document()
        val report = Report(
            id = docRef.id,
            reporterId = reporterEmail,
            reportedUserId = skill.userId, // also an email, per skills rule
            reason = reason,
            description = "Reported skill: \"${skill.title}\" (Skill ID: ${skill.id})",
            status = "pending",
            timestamp = System.currentTimeMillis()
        )
        docRef.set(report)
            .addOnSuccessListener {
                Toast.makeText(this, "Report submitted. Our team will review it.", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to submit report: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    inner class SkillAdapter(
        private val skills: List<Skill>,
        private val onItemClick: (Skill) -> Unit,
        private val onReportClick: (Skill) -> Unit
    ) : RecyclerView.Adapter<SkillAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val ivThumbnail: ImageView = itemView.findViewById(R.id.ivThumbnail)
            val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
            val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
            val tvUser: TextView = itemView.findViewById(R.id.tvUser)
            val tvCredits: TextView = itemView.findViewById(R.id.tvCredits)
            val btnReport: View = itemView.findViewById(R.id.btnReport)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_explore_skill, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val skill = skills[position]
            holder.tvTitle.text = skill.title
            holder.tvCategory.text = skill.category
            holder.tvUser.text = "By: ${skill.userName}"
            holder.tvCredits.text = "${skill.credits} credits"

            if (!skill.videoUrl.isNullOrEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(skill.videoUrl)
                    .placeholder(R.drawable.baseline_videocam_24)
                    .error(R.drawable.baseline_videocam_24)
                    .into(holder.ivThumbnail)
            } else {
                holder.ivThumbnail.setImageResource(R.drawable.baseline_videocam_24)
            }

            holder.itemView.setOnClickListener { onItemClick(skill) }
            holder.btnReport.setOnClickListener { onReportClick(skill) }
        }

        override fun getItemCount() = skills.size
    }
}