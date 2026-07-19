package com.example.sgp

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminReportsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ReportAdapter
    private val reportList = mutableListOf<Report>()
    private lateinit var db: FirebaseFirestore
    private var listener: ListenerRegistration? = null

    private var pendingExport: (() -> Unit)? = null

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingExport?.invoke()
        } else {
            Toast.makeText(this, "Storage permission is required to export", Toast.LENGTH_SHORT).show()
        }
        pendingExport = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_reports)

        val prefs = getSharedPreferences("SkillSwapPrefs", Context.MODE_PRIVATE)
        if (prefs.getString("user_type", "") != "admin") {
            Toast.makeText(this, "Unauthorized", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        db = Firebase.firestore

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.inflateMenu(R.menu.menu_admin_reports)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_export_csv -> {
                    runWithStoragePermission { exportReportsToCsv() }
                    true
                }
                R.id.action_export_pdf -> {
                    runWithStoragePermission { exportReportsToPdf() }
                    true
                }
                else -> false
            }
        }

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ReportAdapter(reportList) { report ->
            showReportOptionsDialog(report)
        }
        recyclerView.adapter = adapter

        loadReports()
    }

    private fun loadReports() {
        listener = db.collection("reports")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this@AdminReportsActivity, error.message, Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                reportList.clear()
                snapshot?.documents?.forEach { doc ->
                    val report = doc.toObject(Report::class.java)
                    if (report != null) {
                        reportList.add(report)
                    }
                }
                adapter.notifyDataSetChanged()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
    }

    private fun showReportOptionsDialog(report: Report) {
        val options = arrayOf("View Details", "Mark as Resolved", "Dismiss Report", "Delete Report")
        AlertDialog.Builder(this)
            .setTitle("Manage Report")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewReportDetails(report)
                    1 -> updateReportStatus(report, "resolved")
                    2 -> updateReportStatus(report, "dismissed")
                    3 -> deleteReport(report)
                }
            }
            .show()
    }

    private fun viewReportDetails(report: Report) {
        val message = """
            Reporter ID: ${report.reporterId}
            Reported User ID: ${report.reportedUserId}
            Reason: ${report.reason}
            Description: ${report.description}
            Status: ${report.status}
            Date: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(report.timestamp))}
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("Report Details")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun updateReportStatus(report: Report, status: String) {
        db.collection("reports").document(report.id)
            .update("status", status)
            .addOnSuccessListener {
                Toast.makeText(this, "Report status updated to $status", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteReport(report: Report) {
        AlertDialog.Builder(this)
            .setTitle("Delete Report")
            .setMessage("Are you sure you want to delete this report?")
            .setPositiveButton("Delete") { _, _ ->
                db.collection("reports").document(report.id).delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Report deleted", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun runWithStoragePermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            action()
            return
        }
        val permission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingExport = action
            storagePermissionLauncher.launch(permission)
        }
    }

    private fun exportReportsToCsv() {
        db.collection("users").get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Toast.makeText(this, "No users to export", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val fileName = "SkillSwap_Users_${System.currentTimeMillis()}.csv"
                val csvBuilder = StringBuilder()
                csvBuilder.append("Name,Email,Phone Number,Skill I Can Teach,Skill I Want To Learn,Trades,Review\n")

                snapshot.documents.forEach { doc ->
                    val user = doc.toObject(Users::class.java) ?: return@forEach
                    csvBuilder.append(csvEscape(blankIfEmpty(user.name))).append(",")
                    csvBuilder.append(csvEscape(blankIfEmpty(user.email))).append(",")
                    csvBuilder.append(csvEscape(blankIfEmpty(user.phone))).append(",")
                    csvBuilder.append(csvEscape(blankIfEmpty(user.skillsTeach))).append(",")
                    csvBuilder.append(csvEscape(blankIfEmpty(user.skillsLearn))).append(",")
                    csvBuilder.append(csvEscape(blankIfZeroInt(user.completedTrades))).append(",")
                    csvBuilder.append(csvEscape(blankIfZeroDouble(user.rating))).append("\n")
                }

                try {
                    val outputStream = openDownloadsOutputStream(fileName, "text/csv")
                    outputStream?.use { it.write(csvBuilder.toString().toByteArray()) }
                    Toast.makeText(this, "Saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load users: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun blankIfEmpty(value: String): String = if (value.isBlank()) "" else value
    private fun blankIfZeroInt(value: Int): String = if (value == 0) "" else value.toString()
    private fun blankIfZeroDouble(value: Double): String = if (value == 0.0) "" else String.format(Locale.getDefault(), "%.1f", value)

    private fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.contains(",") || escaped.contains("\n")) "\"$escaped\"" else escaped
    }

    // ─── PDF Export (Users) — Styled Table with Centered Banner ──
    private fun exportReportsToPdf() {
        db.collection("users").get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Toast.makeText(this, "No users to export", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val users = snapshot.documents.mapNotNull { it.toObject(Users::class.java) }
                val fileName = "SkillSwap_Users_${System.currentTimeMillis()}.pdf"
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

                // ── Page geometry (A4 landscape) ───────────────────
                val pageWidth = 842
                val pageHeight = 595
                val leftMargin = 24f
                val bottomMargin = pageHeight - 30f
                val rowHeight = 22f
                val headerRowHeight = 24f
                val bannerHeight = 70f

                val headers = listOf(
                    "Name", "Email", "Phone", "Skill I Can Teach",
                    "Skill I Want To Learn", "Trades", "Review"
                )
                val tableWidth = pageWidth - (leftMargin * 2)
                val columnWeights = listOf(0.13f, 0.22f, 0.11f, 0.19f, 0.19f, 0.08f, 0.08f)
                val columnWidths = columnWeights.map { it * tableWidth }

                val brandColor = android.graphics.Color.parseColor("#1B5EC8")
                val brandColorDark = android.graphics.Color.parseColor("#123E85")

                val bannerBgPaint = Paint().apply { color = brandColor; style = Paint.Style.FILL }
                val bannerAccentPaint = Paint().apply { color = brandColorDark; style = Paint.Style.FILL }
                val titlePaint = Paint().apply {
                    textSize = 22f
                    isFakeBoldText = true
                    color = android.graphics.Color.WHITE
                    textAlign = Paint.Align.CENTER
                }
                val subtitlePaint = Paint().apply {
                    textSize = 11f
                    color = android.graphics.Color.parseColor("#D6E4FA")
                    textAlign = Paint.Align.CENTER
                }
                val headerPaint = Paint().apply {
                    textSize = 10f
                    isFakeBoldText = true
                    color = android.graphics.Color.WHITE
                }
                val headerBgPaint = Paint().apply { color = brandColor; style = Paint.Style.FILL }
                val bodyPaint = Paint().apply { textSize = 9f; color = android.graphics.Color.parseColor("#1A1A2E") }
                val borderPaint = Paint().apply {
                    color = android.graphics.Color.parseColor("#CCCCCC")
                    style = Paint.Style.STROKE
                    strokeWidth = 0.7f
                }
                val altRowBgPaint = Paint().apply {
                    color = android.graphics.Color.parseColor("#F5F7FA")
                    style = Paint.Style.FILL
                }
                val footerPaint = Paint().apply {
                    textSize = 8f
                    color = android.graphics.Color.parseColor("#9CA3AF")
                    textAlign = Paint.Align.CENTER
                }

                val pdfDocument = PdfDocument()
                var pageNumber = 1
                var page = pdfDocument.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                )
                var canvas = page.canvas
                var y: Float

                fun drawBanner() {
                    // Full-width colored banner with a thin darker accent strip beneath it
                    canvas.drawRect(0f, 0f, pageWidth.toFloat(), bannerHeight, bannerBgPaint)
                    canvas.drawRect(0f, bannerHeight, pageWidth.toFloat(), bannerHeight + 4f, bannerAccentPaint)

                    val centerX = pageWidth / 2f
                    canvas.drawText("SkillSwap — User Report", centerX, bannerHeight / 2f, titlePaint)
                    canvas.drawText(
                        "Generated: ${dateFormat.format(Date())}",
                        centerX,
                        bannerHeight / 2f + 20f,
                        subtitlePaint
                    )
                }

                fun drawTableHeader(startY: Float): Float {
                    var x = leftMargin
                    var localY = startY
                    canvas.drawRect(leftMargin, localY, leftMargin + tableWidth, localY + headerRowHeight, headerBgPaint)
                    headers.forEachIndexed { i, header ->
                        canvas.drawText(header, x + 4f, localY + headerRowHeight - 7f, headerPaint)
                        canvas.drawRect(x, localY, x + columnWidths[i], localY + headerRowHeight, borderPaint)
                        x += columnWidths[i]
                    }
                    localY += headerRowHeight
                    return localY
                }

                fun drawFooter() {
                    canvas.drawText("Page $pageNumber", pageWidth / 2f, pageHeight - 12f, footerPaint)
                }

                fun truncate(text: String, maxWidth: Float, paint: Paint): String {
                    if (paint.measureText(text) <= maxWidth) return text
                    var end = text.length
                    while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
                        end--
                    }
                    return text.substring(0, end) + "…"
                }

                fun newPage() {
                    drawFooter()
                    pdfDocument.finishPage(page)
                    pageNumber++
                    page = pdfDocument.startPage(
                        PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    )
                    canvas = page.canvas
                    drawBanner()
                    y = drawTableHeader(bannerHeight + 24f)
                }

                drawBanner()
                y = drawTableHeader(bannerHeight + 24f)

                users.forEachIndexed { rowIndex, user ->
                    if (y + rowHeight > bottomMargin) {
                        newPage()
                    }

                    val rowValues = listOf(
                        blankIfEmpty(user.name),
                        blankIfEmpty(user.email),
                        blankIfEmpty(user.phone),
                        blankIfEmpty(user.skillsTeach),
                        blankIfEmpty(user.skillsLearn),
                        blankIfZeroInt(user.completedTrades),
                        blankIfZeroDouble(user.rating)
                    )

                    if (rowIndex % 2 == 1) {
                        canvas.drawRect(leftMargin, y, leftMargin + tableWidth, y + rowHeight, altRowBgPaint)
                    }

                    var x = leftMargin
                    rowValues.forEachIndexed { i, value ->
                        val cellPadding = 4f
                        val maxTextWidth = columnWidths[i] - (cellPadding * 2)
                        val displayText = truncate(value, maxTextWidth, bodyPaint)
                        canvas.drawText(displayText, x + cellPadding, y + rowHeight - 7f, bodyPaint)
                        canvas.drawRect(x, y, x + columnWidths[i], y + rowHeight, borderPaint)
                        x += columnWidths[i]
                    }

                    y += rowHeight
                }

                drawFooter()
                pdfDocument.finishPage(page)

                try {
                    val outputStream = openDownloadsOutputStream(fileName, "application/pdf")
                    outputStream?.use { pdfDocument.writeTo(it) }
                    Toast.makeText(this, "Saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    pdfDocument.close()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load users: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun openDownloadsOutputStream(fileName: String, mimeType: String): OutputStream? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: return null
            contentResolver.openOutputStream(uri)
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = java.io.File(downloadsDir, fileName)
            java.io.FileOutputStream(file)
        }
    }

    class ReportAdapter(
        private val reports: List<Report>,
        private val onItemClick: (Report) -> Unit
    ) : RecyclerView.Adapter<ReportAdapter.ViewHolder>() {

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvReporter: TextView = itemView.findViewById(R.id.tvReporter)
            val tvReason: TextView = itemView.findViewById(R.id.tvReason)
            val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_admin_report, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val report = reports[position]
            holder.tvReporter.text = "Reporter: ${report.reporterId}"
            holder.tvReason.text = report.reason
            holder.tvStatus.text = report.status
            holder.tvStatus.setTextColor(
                when (report.status) {
                    "pending" -> android.graphics.Color.parseColor("#FF9800")
                    "resolved" -> android.graphics.Color.parseColor("#4CAF50")
                    "dismissed" -> android.graphics.Color.parseColor("#F44336")
                    else -> android.graphics.Color.parseColor("#757575")
                }
            )
            holder.itemView.setOnClickListener { onItemClick(report) }
        }

        override fun getItemCount() = reports.size
    }
}