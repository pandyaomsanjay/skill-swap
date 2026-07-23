package com.example.sgp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

class HelpSupportActivity : BaseActivity() {

    private data class Faq(val question: String, val answer: String)

    private val db = FirebaseFirestore.getInstance()

    private val faqs = listOf(
        Faq(
            "How do I start a trade?",
            "• Go to Explore and pick a skill you're interested in\n" +
                    "• Open the user's profile\n" +
                    "• Tap \"Request Trade\"\n" +
                    "• They'll get a notification to accept or decline"
        ),
        Faq(
            "What are credits and how do I earn them?",
            "• Credits are SkillSwap's internal currency for skill exchanges\n" +
                    "• You earn credits by teaching or offering a skill to another user\n" +
                    "• They have no monetary value"
        ),
        Faq(
            "Is SkillSwap free to use?",
            "• Yes, SkillSwap is completely free\n" +
                    "• There are no subscription fees\n" +
                    "• The app runs entirely on the credit-based trade system"
        ),
        Faq(
            "How do I report a user?",
            "• Open the user's profile\n" +
                    "• Tap the three-dot menu in the top right\n" +
                    "• Select \"Report User\"\n" +
                    "• Our team reviews every report within 48 hours"
        ),
        Faq(
            "How do I delete my account?",
            "• Go to Settings > Edit Profile > Delete Account\n" +
                    "• Or email us directly\n" +
                    "• We'll remove your data within 7 days"
        ),
        Faq(
            "How do I cancel or decline a trade request?",
            "• Open Notifications or your Trades tab\n" +
                    "• Find the pending trade request\n" +
                    "• Tap \"Decline\" to reject it, or \"Cancel\" if you sent it\n" +
                    "• The other user will be notified automatically"
        ),
        Faq(
            "I forgot my password. How do I reset it?",
            "• Tap \"Forgot Password?\" on the login screen\n" +
                    "• Enter the email linked to your account\n" +
                    "• Check your inbox for a reset link\n" +
                    "• Follow the link to set a new password"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help_support)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        buildFaqList()
        setupAskQuestionBox()

        findViewById<View>(R.id.layoutEmailSupport).setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:skillswap23@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "SkillSwap Support Request")
            }
            startActivity(Intent.createChooser(intent, "Contact Support"))
        }
    }

    private fun buildFaqList() {
        val container = findViewById<LinearLayout>(R.id.layoutFaqContainer)
        val inflater = LayoutInflater.from(this)

        faqs.forEach { faq ->
            val row = inflater.inflate(R.layout.item_faq, container, false)

            val tvQuestion = row.findViewById<TextView>(R.id.tvQuestion)
            val tvAnswer = row.findViewById<TextView>(R.id.tvAnswer)
            val ivChevron = row.findViewById<ImageView>(R.id.ivChevron)
            val header = row.findViewById<View>(R.id.layoutQuestion)

            tvQuestion.text = faq.question
            tvAnswer.text = faq.answer

            header.setOnClickListener {
                val expanded = tvAnswer.visibility == View.VISIBLE
                tvAnswer.visibility = if (expanded) View.GONE else View.VISIBLE
                ivChevron.rotation = if (expanded) 90f else 270f
            }

            container.addView(row)
        }
    }

    private fun setupAskQuestionBox() {
        val etQuestion = findViewById<EditText>(R.id.etUserQuestion)
        val btnSubmit = findViewById<Button>(R.id.btnSubmitQuestion)

        btnSubmit.setOnClickListener {
            val questionText = etQuestion.text.toString().trim()

            if (questionText.isEmpty()) {
                Toast.makeText(this, "Please type your question first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                Toast.makeText(this, "Please log in to submit a question", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val questionMap = hashMapOf<String, Any>(
                "question" to questionText,
                "status" to "pending",
                "createdAt" to System.currentTimeMillis()
            )

            btnSubmit.isEnabled = false

            db.collection("users").document(currentUser.uid)
                .update("supportQuestions", com.google.firebase.firestore.FieldValue.arrayUnion(questionMap))
                .addOnSuccessListener {
                    btnSubmit.isEnabled = true
                    etQuestion.text.clear()
                    Toast.makeText(this, "Your question has been submitted!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    btnSubmit.isEnabled = true
                    Toast.makeText(this, "Failed to submit: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}