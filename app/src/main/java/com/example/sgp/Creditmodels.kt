package com.example.sgp

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Prefixed "Admin" so they never clash with the models used by CreditStoreActivity.

enum class TxStatus(val label: String) {
    SUCCESS("Successful"), FAILED("Failed"), PENDING("Pending")
}

data class AdminCreditPackage(
    val id: String,
    val name: String,
    val price: Long,
    val credits: Long,
    val active: Boolean,
    val sortOrder: Long
)

data class AdminCreditTxn(
    val id: String,
    val userId: String,
    val userEmail: String,
    val packageName: String,
    val price: Double,
    val credits: Int,
    val method: String,
    val status: TxStatus,
    val timestamp: Long,
    val raw: Map<String, Any?>
)

object CreditFmt {
    fun money(v: Double): String =
        "₹" + if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.getDefault(), "%.2f", v)

    fun dateTime(ts: Long): String =
        if (ts <= 0) "N/A" else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ts))

    fun date(ts: Long): String =
        if (ts <= 0) "N/A" else SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(ts))
}

data class CreditSummary(
    val total: Int,
    val successful: Int,
    val failed: Int,
    val pending: Int,
    val creditsPurchased: Long,   // successful purchases only
    val amount: Double            // successful purchases only (simulated)
)

object CreditStats {
    fun summarize(list: List<AdminCreditTxn>): CreditSummary {
        val ok = list.filter { it.status == TxStatus.SUCCESS }
        return CreditSummary(
            total = list.size,
            successful = ok.size,
            failed = list.count { it.status == TxStatus.FAILED },
            pending = list.count { it.status == TxStatus.PENDING },
            creditsPurchased = ok.sumOf { it.credits.toLong() },
            amount = ok.sumOf { it.price }
        )
    }
}

class CreditListenerHandle {
    var reg: ListenerRegistration? = null
    @Volatile var removed = false
    fun remove() { removed = true; reg?.remove() }
}

object CreditRepo {

    private fun DocumentSnapshot.num(vararg keys: String): Double? {
        for (k in keys) {
            when (val v = get(k)) {
                is Number -> return v.toDouble()
                is String -> v.toDoubleOrNull()?.let { return it }
            }
        }
        return null
    }

    fun parsePackage(d: DocumentSnapshot) = AdminCreditPackage(
        id = d.id,
        name = d.getString("name") ?: d.id,
        price = (d.num("price") ?: 0.0).toLong(),
        credits = (d.num("credits") ?: 0.0).toLong(),
        active = d.getBoolean("active") ?: true,
        sortOrder = (d.num("sortOrder") ?: 0.0).toLong()
    )

    fun normalizeStatus(s: String?): TxStatus = when (s?.trim()?.lowercase(Locale.ROOT)) {
        null, "", "success", "successful", "completed", "complete", "paid", "succeeded" -> TxStatus.SUCCESS
        "failed", "failure", "cancelled", "canceled", "error", "declined" -> TxStatus.FAILED
        else -> TxStatus.PENDING
    }

    fun parseTxn(
        d: DocumentSnapshot,
        pkgs: Map<String, AdminCreditPackage>,
        uidToEmail: Map<String, String>
    ): AdminCreditTxn {
        val userId = d.getString("userId") ?: ""
        val email = d.getString("userEmail") ?: d.getString("email")
        ?: if (userId.contains("@")) userId else (uidToEmail[userId] ?: userId)

        val pkgKey = d.getString("packageId") ?: d.getString("packageName") ?: ""
        val pkg = pkgs[pkgKey] ?: pkgs.values.firstOrNull { it.name.equals(pkgKey, true) }
        val pkgName = d.getString("packageName") ?: pkg?.name
        ?: pkgKey.replaceFirstChar { it.uppercase() }.ifBlank { "Credit" }

        val credits = (d.num("credits", "creditsPurchased") ?: pkg?.credits?.toDouble() ?: d.num("amount") ?: 0.0).toInt()
        val price = d.num("price", "priceInr", "amountPaid", "amountInr", "totalAmount") ?: pkg?.price?.toDouble() ?: 0.0

        val ts = when (val t = d.get("timestamp") ?: d.get("createdAt") ?: d.get("purchaseTimestamp")) {
            is Timestamp -> t.toDate().time
            is Number -> t.toLong()
            else -> 0L
        }

        return AdminCreditTxn(
            id = d.id,
            userId = userId,
            userEmail = email,
            packageName = pkgName,
            price = price,
            credits = credits,
            method = (d.getString("paymentMethod") ?: "Simulated").replaceFirstChar { it.uppercase() },
            status = normalizeStatus(d.getString("status")),
            timestamp = ts,
            raw = d.data ?: emptyMap()
        )
    }

    /**
     * Live list of every demoTransactions document (admin only; enforced by Firestore rules).
     * Packages and users are fetched once so price/credits/email can be resolved.
     */
    fun listenTransactions(
        db: FirebaseFirestore,
        onUpdate: (List<AdminCreditTxn>) -> Unit,
        onError: (String) -> Unit
    ): CreditListenerHandle {
        val handle = CreditListenerHandle()
        db.collection("creditPackages").get().addOnCompleteListener { pk ->
            val pkgs = if (pk.isSuccessful)
                pk.result.documents.map { parsePackage(it) }.associateBy { it.id } else emptyMap()
            db.collection("users").get().addOnCompleteListener { us ->
                val map = if (us.isSuccessful)
                    us.result.documents.mapNotNull { u -> u.getString("email")?.let { u.id to it } }.toMap()
                else emptyMap()
                if (handle.removed) return@addOnCompleteListener
                handle.reg = db.collection("demoTransactions").addSnapshotListener { snap, err ->
                    if (err != null) { onError(err.message ?: "Failed to load transactions"); return@addSnapshotListener }
                    onUpdate((snap?.documents ?: emptyList()).map { parseTxn(it, pkgs, map) })
                }
            }
        }
        return handle
    }
}