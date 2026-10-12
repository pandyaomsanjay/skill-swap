package com.example.sgp

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.ServerTimestamp

/**
 * Message document. Messages written before encryption existed have encVersion == 0 and keep
 * their plaintext in [text]. New messages have encVersion >= 1, an empty [text], and the
 * content in [ciphertext]/[iv] (AES-256-GCM, written by the sendMessage Cloud Function).
 * No key material ever lives in this object.
 */
data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val receiverId: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    @ServerTimestamp
    val serverTimestamp: Timestamp? = null,
    val deleted: Boolean = false,
    val deletedFor: List<String> = emptyList(),
    // --- encryption metadata (all default to "legacy plaintext") ---
    val ciphertext: String = "",
    val iv: String = "",
    val encVersion: Int = 0,
    val keyVersion: Int = 0
) {
    @get:Exclude
    val isEncrypted: Boolean get() = encVersion > 0
}

data class Conversation(
    val id: String = "",
    val participants: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val skillId: String? = null,
    val skillTitle: String? = null,
    val lastMessage: String = "",
    val lastTimestamp: Long = 0L,
    val lastSenderId: String = "",
    val unreadFor: List<String> = emptyList(),
    // --- encrypted preview (0 == legacy plaintext in lastMessage) ---
    val lastMessageCt: String = "",
    val lastMessageIv: String = "",
    val lastEncVersion: Int = 0,
    val lastKeyVersion: Int = 0
)

fun buildConversationId(emailA: String, emailB: String, skillId: String? = null): String {
    fun sanitize(email: String) = email.trim().lowercase()
        .replace("@", "-at-")
        .replace(".", "-dot-")

    val usersPart = listOf(sanitize(emailA), sanitize(emailB)).sorted().joinToString("_")
    val skillPart = skillId?.trim().takeUnless { it.isNullOrEmpty() } ?: "general"
    return "${skillPart}_${usersPart}"
}