package com.example.sgp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import java.util.Locale

enum class SkillCategoryTab {
    ALL, TECHNOLOGY, ARTS, SPORTS, HOME, EDUCATION, LIFESTYLE
}

class ExploreActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: View
    private lateinit var etSearch: EditText
    private lateinit var adapter: SkillAdapter
    private lateinit var db: FirebaseFirestore
    private var listener: ListenerRegistration? = null

    private val allSkills = mutableListOf<Skill>()
    private val displayedSkills = mutableListOf<Skill>()

    private var currentTab = SkillCategoryTab.ALL
    private var currentQuery = ""

    private lateinit var btnChatInbox: View
    private lateinit var unreadBadge: View
    private var chatsListener: ListenerRegistration? = null

    private lateinit var tabAll: MaterialCardView
    private lateinit var tabTechnology: MaterialCardView
    private lateinit var tabArts: MaterialCardView
    private lateinit var tabSports: MaterialCardView
    private lateinit var tabHome: MaterialCardView
    private lateinit var tabEducation: MaterialCardView
    private lateinit var tabLifestyle: MaterialCardView

    private lateinit var tvTabAll: TextView
    private lateinit var tvTabTechnology: TextView
    private lateinit var tvTabArts: TextView
    private lateinit var tvTabSports: TextView
    private lateinit var tvTabHome: TextView
    private lateinit var tvTabEducation: TextView
    private lateinit var tvTabLifestyle: TextView

    // Selected segment vs. unselected text color inside the single rounded pill.
    // Unselected segments use a transparent card background so the outer
    // pill's own background color shows through.
    private val selectedTabBg = Color.parseColor("#F9F3EF")
    private val selectedTabText = Color.parseColor("#1B3C53")
    private val unselectedTabText = Color.parseColor("#D2C1B6")

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

        bindViews()
        setupChatInbox()
        setupTabs()
        setupSearch()
        setupBottomNav()

        loadSkills()
    }

    // ---------- Binding ----------

    private fun bindViews() {
        recyclerView = findViewById(R.id.recyclerView)
        emptyState = findViewById(R.id.emptyState)
        etSearch = findViewById(R.id.etSearch)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = SkillAdapter(
            displayedSkills,
            onItemClick = { skill -> openSkill(skill) },
            onReportClick = { skill -> showReportDialog(skill) },
            onChatClick = { skill -> openChat(skill) }
        )
        recyclerView.adapter = adapter

        btnChatInbox = findViewById(R.id.btnChatInbox)
        unreadBadge = findViewById(R.id.unreadBadge)

        tabAll = findViewById(R.id.tabAll)
        tabTechnology = findViewById(R.id.tabTechnology)
        tabArts = findViewById(R.id.tabArts)
        tabSports = findViewById(R.id.tabSports)
        tabHome = findViewById(R.id.tabHome)
        tabEducation = findViewById(R.id.tabEducation)
        tabLifestyle = findViewById(R.id.tabLifestyle)

        tvTabAll = findViewById(R.id.tvTabAll)
        tvTabTechnology = findViewById(R.id.tvTabTechnology)
        tvTabArts = findViewById(R.id.tvTabArts)
        tvTabSports = findViewById(R.id.tvTabSports)
        tvTabHome = findViewById(R.id.tvTabHome)
        tvTabEducation = findViewById(R.id.tvTabEducation)
        tvTabLifestyle = findViewById(R.id.tvTabLifestyle)
    }

    private fun setupChatInbox() {
        btnChatInbox.setOnClickListener {
            startActivity(Intent(this, ChatListActivity::class.java))
        }
        listenForUnreadChats()
    }

    private fun setupBottomNav() {
        BottomNavHelper.setup(this, BottomNavItem.EXPLORE)
    }

    // ---------- Chat inbox / unread badge ----------

    private fun listenForUnreadChats() {
        val myEmail = FirebaseAuth.getInstance().currentUser?.email ?: return
        chatsListener = db.collection("chats")
            .whereArrayContains("participants", myEmail)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ExploreActivity", "Unread chats listener error", error)
                    return@addSnapshotListener
                }
                try {
                    val hasUnread = snapshot?.documents?.any { doc ->
                        val unreadFor = doc.get("unreadFor") as? List<*>
                        unreadFor?.contains(myEmail) == true
                    } ?: false
                    unreadBadge.visibility = if (hasUnread) View.VISIBLE else View.GONE
                } catch (e: Exception) {
                    // Don't let one malformed chat doc take down the page.
                    Log.e("ExploreActivity", "Failed processing unread chats", e)
                }
            }
    }

    // ---------- Tabs ----------

    private fun setupTabs() {
        tabAll.setOnClickListener { selectTab(SkillCategoryTab.ALL) }
        tabTechnology.setOnClickListener { selectTab(SkillCategoryTab.TECHNOLOGY) }
        tabArts.setOnClickListener { selectTab(SkillCategoryTab.ARTS) }
        tabSports.setOnClickListener { selectTab(SkillCategoryTab.SPORTS) }
        tabHome.setOnClickListener { selectTab(SkillCategoryTab.HOME) }
        tabEducation.setOnClickListener { selectTab(SkillCategoryTab.EDUCATION) }
        tabLifestyle.setOnClickListener { selectTab(SkillCategoryTab.LIFESTYLE) }
        selectTab(SkillCategoryTab.ALL)
    }

    // Segmented-pill style: the outer MaterialCardView (in XML) supplies the
    // pill's background color. Here we just move the "selected" highlight
    // between segments — no per-segment stroke/border anymore.
    private fun selectTab(tab: SkillCategoryTab) {
        currentTab = tab
        val tabs = listOf(
            tabAll to tvTabAll,
            tabTechnology to tvTabTechnology,
            tabArts to tvTabArts,
            tabSports to tvTabSports,
            tabHome to tvTabHome,
            tabEducation to tvTabEducation,
            tabLifestyle to tvTabLifestyle
        )
        val selectedIndex = when (tab) {
            SkillCategoryTab.ALL -> 0
            SkillCategoryTab.TECHNOLOGY -> 1
            SkillCategoryTab.ARTS -> 2
            SkillCategoryTab.SPORTS -> 3
            SkillCategoryTab.HOME -> 4
            SkillCategoryTab.EDUCATION -> 5
            SkillCategoryTab.LIFESTYLE -> 6
        }
        tabs.forEachIndexed { index, (card, text) ->
            if (index == selectedIndex) {
                card.setCardBackgroundColor(selectedTabBg)
                text.setTextColor(selectedTabText)
            } else {
                card.setCardBackgroundColor(Color.TRANSPARENT)
                text.setTextColor(unselectedTabText)
            }
        }
        applyFilters()
    }

    // ---------- Search ----------

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentQuery = s?.toString()?.trim()?.lowercase(Locale.getDefault()) ?: ""
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    // ---------- Filtering ----------

    private fun applyFilters() {
        val categoryFiltered = allSkills.filter { skill ->
            when (currentTab) {
                SkillCategoryTab.ALL -> true
                SkillCategoryTab.TECHNOLOGY -> skill.category.equals("Technology", ignoreCase = true)
                SkillCategoryTab.ARTS -> skill.category.equals("Arts", ignoreCase = true)
                SkillCategoryTab.SPORTS -> skill.category.equals("Sports", ignoreCase = true)
                SkillCategoryTab.HOME -> skill.category.equals("Home", ignoreCase = true)
                SkillCategoryTab.EDUCATION -> skill.category.equals("Education", ignoreCase = true)
                SkillCategoryTab.LIFESTYLE -> skill.category.equals("Lifestyle", ignoreCase = true)
            }
        }

        val fullyFiltered = if (currentQuery.isBlank()) {
            categoryFiltered
        } else {
            categoryFiltered.filter {
                it.title.lowercase(Locale.getDefault()).contains(currentQuery) ||
                        it.category.lowercase(Locale.getDefault()).contains(currentQuery) ||
                        it.description.lowercase(Locale.getDefault()).contains(currentQuery)
            }
        }

        displayedSkills.clear()
        displayedSkills.addAll(fullyFiltered)
        adapter.notifyDataSetChanged()
        emptyState.visibility = if (displayedSkills.isEmpty()) View.VISIBLE else View.GONE
    }

    // ---------- Data loading ----------

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
                        // A single malformed document (bad type, missing field, etc.)
                        // used to crash the whole page via an uncaught RuntimeException
                        // from toObject(). Now we skip just that doc and log it.
                        Log.e("ExploreActivity", "Skipping malformed skill doc: ${doc.id}", e)
                    }
                }
                applyFilters()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
        chatsListener?.remove()
    }

    // ---------- Actions ----------

    private fun openSkill(skill: Skill) {
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

    private fun showReportDialog(skill: Skill) {
        val reporterEmail = FirebaseAuth.getInstance().currentUser?.email
        if (reporterEmail.isNullOrEmpty()) {
            Toast.makeText(this, "Please log in to report", Toast.LENGTH_SHORT).show()
            return
        }
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
            reportedUserId = skill.userId,
            skillId = skill.id,
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

    private fun openChat(skill: Skill) {
        val myEmail = FirebaseAuth.getInstance().currentUser?.email
        if (myEmail.isNullOrEmpty()) {
            Toast.makeText(this, "Please log in to chat", Toast.LENGTH_SHORT).show()
            return
        }
        if (myEmail == skill.userId) {
            Toast.makeText(this, "You can't message yourself about your own skill", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("otherUserEmail", skill.userId)
        intent.putExtra("otherUserName", skill.userName)
        intent.putExtra("skillId", skill.id)
        intent.putExtra("skillTitle", skill.title)
        startActivity(intent)
    }

    // ---------- Adapter ----------

    class SkillAdapter(
        private val skills: List<Skill>,
        private val onItemClick: (Skill) -> Unit,
        private val onReportClick: (Skill) -> Unit,
        private val onChatClick: (Skill) -> Unit
    ) : RecyclerView.Adapter<SkillAdapter.ViewHolder>() {

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val ivThumbnail: ImageView = itemView.findViewById(R.id.ivThumbnail)
            val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
            val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
            val tvUser: TextView = itemView.findViewById(R.id.tvUser)
            val tvCredits: TextView = itemView.findViewById(R.id.tvCredits)
            val btnReport: View = itemView.findViewById(R.id.btnReport)
            val btnChat: View = itemView.findViewById(R.id.btnChat)
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
            holder.tvUser.text = "by ${skill.userName.uppercase()}"
            holder.tvCredits.text = "${skill.credits} swaps"

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
            holder.btnChat.setOnClickListener { onChatClick(skill) }
        }

        override fun getItemCount() = skills.size
    }
}