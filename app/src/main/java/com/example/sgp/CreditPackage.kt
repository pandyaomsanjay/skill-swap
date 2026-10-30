package com.example.sgp

import com.google.firebase.firestore.DocumentId

/**
 * Represents a purchasable credit package in the Credit Store.
 *
 * Stored in Firestore: creditPackages/{packageId}
 *
 * Architecture note: credit amounts and prices are intentionally NOT
 * hardcoded. They are read from Firestore so the product team can update
 * packages without a new APK release.
 */
data class CreditPackage(
    @DocumentId
    val packageId: String = "",
    val name: String = "",
    val priceInRupees: Int = 0,
    val credits: Int = 0,
    val active: Boolean = true,
    val displayOrder: Int = 0
) {
    /**
     * Resolves human-readable package title. If `name` is stored in Firestore,
     * uses it; otherwise formats the `packageId` prefix cleanly
     * (e.g. "starter_50" -> "Starter").
     */
    fun getDisplayName(): String {
        if (name.isNotBlank()) return name
        return when (packageId.lowercase()) {
            "starter_50" -> "Starter"
            "popular_100" -> "Popular"
            "value_200" -> "Value"
            "pro_500" -> "Pro"
            else -> packageId.split("_").firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "Package"
        }
    }
}
