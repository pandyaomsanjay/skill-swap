package com.example.sgp

/**
 * What a trade participant is giving in exchange for the other side.
 * Used both for User A's original offer and User B's counter offer.
 *
 * BACKWARD COMPAT: any Firestore document written before this feature
 * existed will decode to MATERIAL because fromString() returns MATERIAL
 * for empty strings and unknown values.
 */
enum class ExchangeMethod(val value: String) {
    POINTS("POINTS"),
    MATERIAL("MATERIAL");

    companion object {
        fun fromString(value: String?): ExchangeMethod {
            if (value.isNullOrBlank()) return MATERIAL
            return entries.firstOrNull {
                it.value.equals(value, ignoreCase = true)
            } ?: MATERIAL
        }
    }
}