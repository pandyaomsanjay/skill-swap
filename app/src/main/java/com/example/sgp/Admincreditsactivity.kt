package com.example.sgp

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/**
 * One screen, five tabs (the dashboard itself is the sixth nav item):
 * History | Status | User Credits | Packages | PDF Reports
 * Open a specific tab with intent extra EXTRA_TAB.
 */
class AdminCreditsActivity : BaseActivity() {

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_HISTORY = 0
        const val TAB_STATUS = 1
        const val TAB_USERS = 2
        const val TAB_PACKAGES = 3
        const val TAB_REPORTS = 4
    }

    private lateinit var db: FirebaseFirestore

    private val navy = Color.parseColor("#1B3C53")
    private val steel = Color.parseColor("#456882")
    private val cream = Color.parseColor("#F9F3EF")
    private val chipBg = Color.parseColor("#F0E6DA")
    private val stroke = Color.parseColor("#D2C1B6")

    // data
    private val allTxns = mutableListOf<AdminCreditTxn>()
    private val shownTxns = mutableListOf<AdminCreditTxn>()
    private val packages = mutableListOf<AdminCreditPackage>()
    private var statusFilter: TxStatus? = null
    private var query = ""
    private var txnHandle: CreditListenerHandle? = null
    private var pkgListener: ListenerRegistration? = null

    // ui
    private lateinit var sections: List<View>
    private lateinit var progress: ProgressBar
    private val tabChips = mutableListOf<Pair<MaterialCardView, TextView>>()
    private val filterChips = mutableListOf<Triple<MaterialCardView, TextView, TxStatus?>>()
    private lateinit var txnAdapter: TxnAdapter
    private lateinit var pkgAdapter: PackageAdapter

    // user credits
    private var selUid = ""
    private var selEmail = ""
    private var selBalance = 0L

    private var pendingExport: (() -> Unit)? = null
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pendingExport?.invoke()
        else Toast.makeText(this, "Storage permission is required to export", Toast.LENGTH_SHORT).show()
        pendingExport = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_credits)

        val prefs = getSharedPreferences("SkillSwapPrefs", Context.MODE_PRIVATE)
        if (prefs.getString("user_type", "") != "admin") {
            Toast.makeText(this, "Unauthorized", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        db = Firebase.firestore

        progress = findViewById(R.id.progress)
        sections = listOf(
            findViewById(R.id.secHistory), findViewById(R.id.secStatus), findViewById(R.id.secUsers),
            findViewById(R.id.secPackages), findViewById(R.id.secReports)
        )
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        setupTabs()
        setupHistory()
        setupStatus()
        setupUsers()
        setupPackages()
        setupReports()

        showTab(intent.getIntExtra(EXTRA_TAB, TAB_HISTORY))
        startListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        txnHandle?.remove()
        pkgListener?.remove()
    }

    // ---------------------------------------------------------------- data

    private fun startListening() {
        progress.visibility = View.VISIBLE
        txnHandle = CreditRepo.listenTransactions(db,
            onUpdate = { list ->
                allTxns.clear(); allTxns.addAll(list)
                progress.visibility = View.GONE
                applyTxnFilters()
            },
            onError = { msg ->
                progress.visibility = View.GONE
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                applyTxnFilters()
            })

        pkgListener = db.collection("creditPackages").addSnapshotListener { snap, err ->
            if (err != null) {
                Toast.makeText(this, "Packages: ${err.message}", Toast.LENGTH_LONG).show(); return@addSnapshotListener
            }
            packages.clear()
            packages.addAll((snap?.documents ?: emptyList()).map { CreditRepo.parsePackage(it) }
                .sortedWith(compareBy({ it.sortOrder }, { it.price })))
            pkgAdapter.notifyDataSetChanged()
            findViewById<View>(R.id.tvEmptyPackages).visibility = if (packages.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    // ---------------------------------------------------------------- tabs

    private fun setupTabs() {
        val row = findViewById<LinearLayout>(R.id.llTabs)
        listOf("History", "Status", "User Credits", "Packages", "PDF Reports").forEachIndexed { i, label ->
            val (card, tv) = makeChip(label, 14) { showTab(i) }
            card.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
            row.addView(card)
            tabChips.add(card to tv)
        }
    }

    private fun showTab(index: Int) {
        sections.forEachIndexed { i, v -> v.visibility = if (i == index) View.VISIBLE else View.GONE }
        tabChips.forEachIndexed { i, (c, t) -> styleChip(c, t, i == index) }
    }

    private fun makeChip(label: String, hPad: Int, onClick: () -> Unit): Pair<MaterialCardView, TextView> {
        val tv = TextView(this).apply {
            text = label
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(dp(hPad), dp(10), dp(hPad), dp(10))
        }
        val card = MaterialCardView(this).apply {
            radius = dp(14).toFloat()
            cardElevation = 0f
            isClickable = true
            isFocusable = true
            addView(tv)
            setOnClickListener { onClick() }
        }
        styleChip(card, tv, false)
        return card to tv
    }

    private fun styleChip(card: MaterialCardView, tv: TextView, selected: Boolean) {
        if (selected) {
            card.setCardBackgroundColor(navy); card.strokeWidth = 0; tv.setTextColor(cream)
        } else {
            card.setCardBackgroundColor(chipBg); card.strokeWidth = dp(1); card.strokeColor = stroke; tv.setTextColor(steel)
        }
    }

    // ---------------------------------------------------------------- history

    private fun setupHistory() {
        val rv = findViewById<RecyclerView>(R.id.rvTxns)
        txnAdapter = TxnAdapter(shownTxns) { showTxnDetails(it) }
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = txnAdapter

        findViewById<EditText>(R.id.etSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                query = s?.toString()?.trim()?.lowercase(Locale.getDefault()) ?: ""
                applyTxnFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val row = findViewById<LinearLayout>(R.id.llFilters)
        listOf<Pair<String, TxStatus?>>(
            "All" to null, "Successful" to TxStatus.SUCCESS, "Failed" to TxStatus.FAILED, "Pending" to TxStatus.PENDING
        ).forEach { (label, st) ->
            val (card, tv) = makeChip(label, 12) { statusFilter = st; applyTxnFilters() }
            card.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
            row.addView(card)
            filterChips.add(Triple(card, tv, st))
        }
        applyTxnFilters()
    }

    private fun applyTxnFilters() {
        filterChips.forEach { (c, t, st) -> styleChip(c, t, st == statusFilter) }
        val filtered = allTxns.filter { tx ->
            (statusFilter == null || tx.status == statusFilter) &&
                    (query.isBlank() || tx.userEmail.lowercase(Locale.getDefault()).contains(query) ||
                            tx.id.lowercase(Locale.getDefault()).contains(query))
        }.sortedByDescending { it.timestamp }
        shownTxns.clear(); shownTxns.addAll(filtered)
        txnAdapter.notifyDataSetChanged()
        findViewById<View>(R.id.tvEmptyTxns).visibility = if (shownTxns.isEmpty()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.rvTxns).visibility = if (shownTxns.isEmpty()) View.GONE else View.VISIBLE

        val s = CreditStats.summarize(allTxns)
        findViewById<TextView>(R.id.tvStSuccess).text = s.successful.toString()
        findViewById<TextView>(R.id.tvStFailed).text = s.failed.toString()
        findViewById<TextView>(R.id.tvStPending).text = s.pending.toString()
    }

    // ---------------------------------------------------------------- status tab

    private fun setupStatus() {
        findViewById<View>(R.id.cardStSuccess).setOnClickListener { statusFilter = TxStatus.SUCCESS; applyTxnFilters(); showTab(TAB_HISTORY) }
        findViewById<View>(R.id.cardStFailed).setOnClickListener { statusFilter = TxStatus.FAILED; applyTxnFilters(); showTab(TAB_HISTORY) }
        findViewById<View>(R.id.cardStPending).setOnClickListener { statusFilter = TxStatus.PENDING; applyTxnFilters(); showTab(TAB_HISTORY) }
    }

    /** Read-only. Transactions can't be edited or deleted from the app (and Firestore rules block it too). */
    private fun showTxnDetails(tx: AdminCreditTxn) {
        val root = dialogCard()
        root.addView(TextView(this).apply {
            text = "Transaction Details"
            setTextColor(navy); textSize = 17f; setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(divider())

        fun row(label: String, value: String) {
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }
            }
            r.addView(TextView(this).apply {
                text = label; setTextColor(steel); textSize = 12.5f
                layoutParams = LinearLayout.LayoutParams(dp(112), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            r.addView(TextView(this).apply {
                text = value.ifBlank { "-" }; setTextColor(navy); textSize = 12.5f; setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            root.addView(r)
        }

        row("Transaction ID", tx.id)
        row("User email", tx.userEmail)
        row("Package", tx.packageName)
        row("Amount", CreditFmt.money(tx.price) + " (simulated)")
        row("Credits", "🪙 ${tx.credits}")
        row("Payment method", tx.method)
        row("Status", tx.status.label)
        row("Date & time", CreditFmt.dateTime(tx.timestamp))

        val shown = setOf("userId", "userEmail", "email", "packageId", "packageName", "credits", "creditsPurchased",
            "price", "priceInr", "amountPaid", "amountInr", "totalAmount", "paymentMethod", "status",
            "timestamp", "createdAt", "purchaseTimestamp")
        val extra = tx.raw.filterKeys { it !in shown }
        if (extra.isNotEmpty()) {
            root.addView(divider())
            root.addView(TextView(this).apply {
                text = "Other recorded fields"; setTextColor(steel); textSize = 11.5f; setPadding(0, 0, 0, dp(8))
            })
            extra.toSortedMap().forEach { (k, v) -> row(k, v.toString()) }
        }

        val dialog = AlertDialog.Builder(this).setView(android.widget.ScrollView(this).apply { addView(root) }).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        root.addView(pillButton("Close", navy, Color.WHITE).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            setOnClickListener { dialog.dismiss() }
        })
        dialog.show()
    }

    // ---------------------------------------------------------------- user credits

    private fun setupUsers() {
        findViewById<View>(R.id.btnFindUser).setOnClickListener { findUser() }
        findViewById<View>(R.id.btnAddCredits).setOnClickListener { adjust("add") }
        findViewById<View>(R.id.btnDeductCredits).setOnClickListener { adjust("deduct") }
    }

    private fun userMsg(text: String?) {
        val tv = findViewById<TextView>(R.id.tvUserMsg)
        tv.text = text ?: ""
        tv.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    private fun findUser() {
        val email = findViewById<EditText>(R.id.etUserEmail).text.toString().trim()
        if (email.isBlank()) { userMsg("Enter a user email"); return }
        userMsg(null)
        progress.visibility = View.VISIBLE
        db.collection("users").whereIn("email", listOf(email, email.lowercase(Locale.ROOT)).distinct()).limit(1).get()
            .addOnSuccessListener { snap ->
                progress.visibility = View.GONE
                val d = snap.documents.firstOrNull()
                if (d == null) {
                    findViewById<View>(R.id.cardUserResult).visibility = View.GONE
                    userMsg("No user found with that email"); return@addOnSuccessListener
                }
                selUid = d.id
                selEmail = d.getString("email") ?: email
                selBalance = d.getLong("credits") ?: 0L
                findViewById<TextView>(R.id.tvUserName).text = d.getString("name")?.ifBlank { "No name set" } ?: "No name set"
                findViewById<TextView>(R.id.tvUserEmail).text = selEmail
                renderBalance()
                findViewById<View>(R.id.cardUserResult).visibility = View.VISIBLE
            }
            .addOnFailureListener {
                progress.visibility = View.GONE
                userMsg("Search failed: ${it.message}")
            }
    }

    private fun renderBalance() {
        findViewById<TextView>(R.id.tvUserBalance).text = "🪙 $selBalance"
    }

    private fun adjust(direction: String) {
        if (selUid.isBlank()) { userMsg("Find a user first"); return }
        val amount = findViewById<EditText>(R.id.etAdjustAmount).text.toString().toIntOrNull()
        val reason = findViewById<EditText>(R.id.etAdjustReason).text.toString().trim()
        if (amount == null || amount <= 0 || amount > 100000) { userMsg("Enter credits between 1 and 100000"); return }
        if (reason.length < 5) { userMsg("Enter a reason (at least 5 characters)"); return }
        if (direction == "deduct" && amount > selBalance) { userMsg("Cannot deduct more than the current balance ($selBalance)"); return }

        val verb = if (direction == "add") "Add $amount bonus credits to" else "Deduct $amount credits from"
        confirm(if (direction == "add") "🪙" else "➖", if (direction == "add") "Add credits" else "Deduct credits",
            "$verb $selEmail?\n\nReason: $reason", if (direction == "add") "Add" else "Deduct",
            destructive = direction == "deduct") {
            progress.visibility = View.VISIBLE
            val body = JSONObject()
                .put("targetUid", selUid)
                .put("direction", direction)
                .put("amount", amount)
                .put("reason", reason)
                .put("requestId", UUID.randomUUID().toString())   // makes retries idempotent on the backend
            AdminCreditApi.call("adjust-credits", body) { res ->
                progress.visibility = View.GONE
                res.onSuccess {
                    selBalance = it.optLong("newBalance", selBalance)
                    renderBalance()
                    findViewById<EditText>(R.id.etAdjustAmount).text.clear()
                    findViewById<EditText>(R.id.etAdjustReason).text.clear()
                    userMsg("Done. Previous ${it.optLong("previousBalance")} → new ${it.optLong("newBalance")}")
                }.onFailure { userMsg("Failed: ${it.message}") }
            }
        }
    }

    // ---------------------------------------------------------------- packages

    private fun setupPackages() {
        val rv = findViewById<RecyclerView>(R.id.rvPackages)
        pkgAdapter = PackageAdapter(packages,
            onEdit = { showPackageDialog(it) },
            onToggle = { pkg, active -> savePackageActive(pkg, active) })
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = pkgAdapter
        findViewById<View>(R.id.btnAddPackage).setOnClickListener { showPackageDialog(null) }
    }

    private fun savePackageActive(pkg: AdminCreditPackage, active: Boolean) {
        progress.visibility = View.VISIBLE
        AdminCreditApi.call("set-package-active", JSONObject().put("id", pkg.id).put("active", active)) { res ->
            progress.visibility = View.GONE
            res.onFailure {
                Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_LONG).show()
                pkgAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun showPackageDialog(existing: AdminCreditPackage?) {
        val root = dialogCard()
        root.addView(TextView(this).apply {
            text = if (existing == null) "Add Package" else "Edit ${existing.name}"
            setTextColor(navy); textSize = 17f; setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(divider())

        fun field(hint: String, value: String, numeric: Boolean, enabled: Boolean = true): EditText {
            val et = EditText(this).apply {
                setHint(hint); setText(value); isEnabled = enabled
                setTextColor(navy); textSize = 14f
                inputType = if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat(); setColor(cream); setStroke(dp(1), stroke)
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }
            }
            root.addView(et)
            return et
        }

        val etName = field("Name (e.g. Starter)", existing?.name ?: "", false, enabled = existing == null)
        val etPrice = field("Price in ₹", existing?.price?.toString() ?: "", true)
        val etCredits = field("Credits", existing?.credits?.toString() ?: "", true)

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(pillButton("Cancel", Color.parseColor("#EAF1F5"), steel).apply {
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        buttons.addView(pillButton("Save", navy, Color.WHITE).apply {
            setOnClickListener {
                val name = etName.text.toString().trim()
                val price = etPrice.text.toString().toLongOrNull()
                val credits = etCredits.text.toString().toLongOrNull()
                if (name.length < 2 || price == null || price <= 0 || credits == null || credits <= 0) {
                    Toast.makeText(this@AdminCreditsActivity, "Enter a name, a price and credits greater than 0", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                dialog.dismiss()
                progress.visibility = View.VISIBLE
                val body = JSONObject().put("name", name).put("price", price).put("credits", credits)
                    .put("active", existing?.active ?: true)
                existing?.let { body.put("id", it.id) }
                AdminCreditApi.call("upsert-package", body) { res ->
                    progress.visibility = View.GONE
                    res.onSuccess { Toast.makeText(this@AdminCreditsActivity, "Package saved", Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(this@AdminCreditsActivity, "Failed: ${it.message}", Toast.LENGTH_LONG).show() }
                }
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(buttons)
        dialog.show()
    }

    // ---------------------------------------------------------------- PDF reports

    private fun setupReports() {
        findViewById<View>(R.id.btnPdfHistory).setOnClickListener {
            runWithStoragePermission { savePdf(CreditPdfExporter.exportHistory(this, allTxns)) }
        }
        findViewById<View>(R.id.btnPdfSummary).setOnClickListener {
            runWithStoragePermission { savePdf(CreditPdfExporter.exportSummary(this, allTxns)) }
        }
    }

    private fun savePdf(result: Result<String>) {
        result.onSuccess { Toast.makeText(this, "Saved to Downloads: $it", Toast.LENGTH_LONG).show() }
            .onFailure { Toast.makeText(this, "Export failed: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    private fun runWithStoragePermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { action(); return }
        val p = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED) action()
        else { pendingExport = action; storagePermissionLauncher.launch(p) }
    }

    // ---------------------------------------------------------------- dialog helpers (same look as the rest of admin)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun dialogCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(22), dp(20), dp(20))
        background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(Color.WHITE) }
    }

    private fun divider() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            .apply { topMargin = dp(12); bottomMargin = dp(12) }
        setBackgroundColor(Color.parseColor("#EAF1F5"))
    }

    private fun pillButton(text: String, bg: Int, fg: Int) = TextView(this).apply {
        this.text = text; setTextColor(fg); textSize = 14f; setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(12))
        background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(bg) }
        isClickable = true; isFocusable = true
    }

    private fun confirm(emoji: String, title: String, message: String, positive: String, destructive: Boolean, onYes: () -> Unit) {
        val root = dialogCard()
        root.addView(TextView(this).apply {
            text = emoji; textSize = 30f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        })
        root.addView(TextView(this).apply {
            text = title; setTextColor(navy); textSize = 17f; setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = message; setTextColor(steel); textSize = 13f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8); bottomMargin = dp(18) }
        })
        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(pillButton("Cancel", Color.parseColor("#EAF1F5"), steel).apply {
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        row.addView(pillButton(positive, if (destructive) Color.parseColor("#DC2626") else navy, Color.WHITE).apply {
            setOnClickListener { dialog.dismiss(); onYes() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row)
        dialog.show()
    }

    // ---------------------------------------------------------------- adapters

    private class TxnAdapter(
        private val items: List<AdminCreditTxn>,
        private val onClick: (AdminCreditTxn) -> Unit
    ) : RecyclerView.Adapter<TxnAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val id: TextView = v.findViewById(R.id.tvTxnId)
            val status: TextView = v.findViewById(R.id.tvStatus)
            val statusCard: MaterialCardView = v.findViewById(R.id.cardStatus)
            val email: TextView = v.findViewById(R.id.tvEmail)
            val pkg: TextView = v.findViewById(R.id.tvPackage)
            val meta: TextView = v.findViewById(R.id.tvMeta)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_admin_credit_transaction, parent, false))

        override fun onBindViewHolder(h: VH, position: Int) {
            val t = items[position]
            h.id.text = "#${t.id}"
            h.email.text = t.userEmail
            h.pkg.text = "${t.packageName} • ${CreditFmt.money(t.price)} • 🪙 ${t.credits}"
            h.meta.text = "${t.method} • ${CreditFmt.dateTime(t.timestamp)}"
            h.status.text = t.status.label
            val (bg, fg) = when (t.status) {
                TxStatus.SUCCESS -> Color.parseColor("#E3F5E9") to Color.parseColor("#2E9E63")
                TxStatus.FAILED -> Color.parseColor("#FDEAEA") to Color.parseColor("#DC2626")
                TxStatus.PENDING -> Color.parseColor("#FFF3E0") to Color.parseColor("#B8860B")
            }
            h.statusCard.setCardBackgroundColor(bg)
            h.status.setTextColor(fg)
            h.itemView.setOnClickListener { onClick(t) }
        }

        override fun getItemCount() = items.size
    }

    private class PackageAdapter(
        private val items: List<AdminCreditPackage>,
        private val onEdit: (AdminCreditPackage) -> Unit,
        private val onToggle: (AdminCreditPackage, Boolean) -> Unit
    ) : RecyclerView.Adapter<PackageAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.tvPkgName)
            val credits: TextView = v.findViewById(R.id.tvPkgCredits)
            val price: TextView = v.findViewById(R.id.tvPkgPrice)
            val edit: TextView = v.findViewById(R.id.tvPkgEdit)
            val sw: SwitchCompat = v.findViewById(R.id.swPkgActive)
            val state: TextView = v.findViewById(R.id.tvPkgActive)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_admin_credit_package, parent, false))

        override fun onBindViewHolder(h: VH, position: Int) {
            val p = items[position]
            h.name.text = p.name
            h.credits.text = "${p.credits} Credits"
            h.price.text = "₹${p.price}"
            h.sw.setOnCheckedChangeListener(null)
            h.sw.isChecked = p.active
            h.state.text = if (p.active) "Active" else "Inactive"
            h.state.setTextColor(Color.parseColor(if (p.active) "#2E9E63" else "#8FA6B8"))
            h.sw.setOnCheckedChangeListener { _, checked -> onToggle(p, checked) }
            h.edit.setOnClickListener { onEdit(p) }
        }

        override fun getItemCount() = items.size
    }
}