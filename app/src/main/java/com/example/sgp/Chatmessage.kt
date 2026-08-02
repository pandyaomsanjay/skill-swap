package com.example.sgp

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val receiverId: String = "",
    val text: String = "",
    val timestamp: Long = 0L
)

data class Conversation(
    val id: String = "",
    val participants: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val skillId: String? = null,
    val skillTitle: String? = null,
    val lastMessage: String = "",
    val lastTimestamp: Long = 0L,
    val lastSenderId: String = "",
    val unreadFor: List<String> = emptyList() // emails that have NOT read the latest message
)

fun buildConversationId(emailA: String, emailB: String, skillId: String? = null): String {
    fun sanitize(email: String) = email.trim().lowercase()
        .replace("@", "-at-")
        .replace(".", "-dot-")

    val usersPart = listOf(sanitize(emailA), sanitize(emailB)).sorted().joinToString("_")
    val skillPart = skillId?.trim().takeUnless { it.isNullOrEmpty() } ?: "general"
    return "${skillPart}_${usersPart}"
}