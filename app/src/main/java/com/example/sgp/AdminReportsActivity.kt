package com.example.sgp

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration

class AdminReportsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ReportAdapter
    private val reportList = mutableListOf<Report>()
    private lateinit var db: FirebaseFirestore
    private var listener: ListenerRegistration? = null

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
            Date: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(report.timestamp))}
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