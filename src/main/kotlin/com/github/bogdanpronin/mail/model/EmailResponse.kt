package com.github.bogdanpronin.mail.model
data class EmailResponseDto(
    val totalMessages: Int,
    val totalUnreadMessages: Int,
    val messages: List<EmailMessageDto>
)
data class EmailAttachmentDto(
    val filename: String,
    val mimeType: String,
    val size: Int
)
data class EmailMessageDto(
    val uid: Long,
    val subject: String?,
    val from: AddressObject?,
    val to: List<AddressObject?>?,
    val date: String?,
    val text: String?,
    val html: String?,
    val isRead: Boolean,
    val attachments: List<EmailAttachmentDto>,
    val messageId: String? = null,
    val references: String? = null,
    val children: List<EmailMessageDto>? = null
)

data class AddressObject(
    val value: List<EmailAddress>,
    val name: String,
    val address: String
)

data class EmailAddress(
    val address: String,
    val name: String?
)