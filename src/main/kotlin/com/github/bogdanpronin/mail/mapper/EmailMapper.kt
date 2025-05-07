package com.github.bogdanpronin.mail.mapper

import com.github.bogdanpronin.mail.model.*
import jakarta.mail.*
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Component
class EmailMapper {

    private val isoFormatter = DateTimeFormatter.ISO_INSTANT

    fun mapMessage(message: Message, folder: Folder): EmailMessageDto {
        val mime = message as MimeMessage
        val uid = (folder as UIDFolder).getUID(mime)

        return EmailMessageDto(
            uid = uid,
            subject = mime.subject,
            from = mapAddress(mime.from?.firstOrNull()),
            to = mime.allRecipients?.map { mapAddress(it) },
            date = mime.sentDate?.let { date ->
                Instant.ofEpochMilli(date.time)
                    .atZone(ZoneOffset.UTC)
                    .format(isoFormatter)
            },
            text = extractText(mime),
            html = extractHtml(mime),
            isRead = mime.flags.contains(Flags.Flag.SEEN),
            attachments = extractAttachments(mime),
            messageId = mime.getHeader("Message-ID")?.firstOrNull(),
            references = mime.getHeader("References")?.firstOrNull()
        )
    }

    fun fillChildren(messages: List<EmailMessageDto>): List<EmailMessageDto> {
        // Карта для быстрого доступа к письмам
        val messageMap = messages.associateBy { it.messageId ?: it.uid.toString() }
        // Карта для хранения детей
        val childrenMap = mutableMapOf<String, MutableList<EmailMessageDto>>()

        // Определяем родительско-дочерние связи
        messages.forEach { message ->
            val messageId = message.messageId ?: message.uid.toString()
            if (message.references != null) {
                val refs = message.references.split("\\s+".toRegex()).filter { it.isNotBlank() }
                val parentId = refs.lastOrNull() // Последний Message-ID как родитель
                if (parentId != null && parentId != messageId && messageMap.containsKey(parentId)) {
                    if (!childrenMap.containsKey(parentId)) {
                        childrenMap[parentId] = mutableListOf()
                    }
                    childrenMap[parentId]!!.add(message)
                }
            }
        }

        // Формируем иерархию
        val result = messages.map { message ->
            val messageId = message.messageId ?: message.uid.toString()
            val children = childrenMap[messageId]?.sortedByDescending {
                it.date?.let { date -> Instant.parse(date).toEpochMilli() }
            }
            message.copy(children = children)
        }

        // Возвращаем только корневые письма
        return result
            .filter { message ->
                val messageId = message.messageId ?: message.uid.toString()
                childrenMap.values.none { children -> children.any { it.messageId == messageId || it.uid.toString() == messageId } }
            }
            .sortedByDescending { it.date?.let { date -> Instant.parse(date).toEpochMilli() } }
    }

    private fun mapAddress(addr: Address?): AddressObject? {
        if (addr == null) return null

        val internetAddress = addr as? InternetAddress ?: return null
        val emailAddress = EmailAddress(
            address = internetAddress.address,
            name = internetAddress.personal
        )

        return AddressObject(
            value = listOf(emailAddress),
            name = internetAddress.personal ?: internetAddress.address,
            address = internetAddress.address
        )
    }

    private fun extractText(message: MimeMessage): String? {
        return try {
            when (val content = message.content) {
                is String -> if (message.contentType.contains("text/plain")) content else null
                is Multipart -> extractTextFromMultipart(content)
                else -> null
            }
        } catch (e: Exception) {
            println("Error extracting text: ${e.message}")
            null
        }
    }

    private fun extractTextFromMultipart(multipart: Multipart): String? {
        for (i in 0 until multipart.count) {
            val part = multipart.getBodyPart(i)
            when {
                part.contentType.startsWith("text/plain", ignoreCase = true) -> {
                    return part.content.toString()
                }
                part.content is Multipart -> {
                    val nestedText = extractTextFromMultipart(part.content as Multipart)
                    if (nestedText != null) return nestedText
                }
            }
        }
        return null
    }

    private fun extractHtml(message: MimeMessage): String? {
        return try {
            when (val content = message.content) {
                is String -> if (message.contentType.contains("text/html")) content else null
                is Multipart -> extractHtmlFromMultipart(content)
                else -> null
            }
        } catch (e: Exception) {
            println("Error extracting HTML: ${e.message}")
            null
        }
    }

    private fun extractHtmlFromMultipart(multipart: Multipart): String? {
        for (i in 0 until multipart.count) {
            val part = multipart.getBodyPart(i)
            when {
                part.contentType.startsWith("text/html", ignoreCase = true) -> {
                    return part.content.toString()
                }
                part.content is Multipart -> {
                    val nestedHtml = extractHtmlFromMultipart(part.content as Multipart)
                    if (nestedHtml != null) return nestedHtml
                }
            }
        }
        return null
    }

    private fun extractAttachments(message: MimeMessage): List<EmailAttachmentDto> {
        val attachments = mutableListOf<EmailAttachmentDto>()
        try {
            val content = message.content
            if (content is Multipart) {
                for (i in 0 until content.count) {
                    val part = content.getBodyPart(i)
                    if (part.disposition == Part.ATTACHMENT) {
                        attachments.add(
                            EmailAttachmentDto(
                                filename = part.fileName ?: "unnamed",
                                mimeType = part.contentType,
                                size = part.size
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        return attachments
    }
}