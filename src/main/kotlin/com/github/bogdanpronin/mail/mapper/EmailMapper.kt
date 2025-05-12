package com.github.bogdanpronin.mail.mapper

import com.github.bogdanpronin.mail.model.*
import jakarta.mail.*
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeUtility
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

    fun groupThreads(messages: List<EmailMessageDto>): List<EmailMessageDto> {
        // Создаем список с кэшированными датами (эпоха в миллисекундах)
        val messagesWithEpoch = messages.map { message ->
            message to (message.date?.let { Instant.parse(it).toEpochMilli() } ?: 0L)
        }

        // Сортируем все письма по дате (новые сверху)
        val sortedMessages = messagesWithEpoch.sortedByDescending { it.second }

        // Группируем по threadId
        val threadMap = sortedMessages.groupBy { (message, _) ->
            val messageId = message.messageId ?: message.uid.toString()
            if (message.references != null) {
                val refs = message.references.split("\\s+".toRegex()).filter { it.isNotBlank() }
                refs.firstOrNull() ?: messageId
            } else {
                messageId
            }
        }

        // Формируем результат: только последнее письмо каждой цепочки
        val result = threadMap.values.map { thread ->
            val latestMessage = thread.first().first // Первое письмо (самое новое)
            // Остальные письма в цепочке (от старого к новому)
            val threadMessages = thread.drop(1)
                .sortedBy { it.second } // Сортировка по эпохе (от старого к новому)
                .map { it.first }
            latestMessage.copy(threadMessages = threadMessages)
        }

        // Сортировка уже не нужна, так как исходный список отсортирован
        return result
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
                processMultipart(content, attachments)
            }
        } catch (e: Exception) {
            println("Error extracting attachments: ${e.message}")
        }
        return attachments
    }

    private fun processMultipart(multipart: Multipart, attachments: MutableList<EmailAttachmentDto>) {
        for (i in 0 until multipart.count) {
            val part = multipart.getBodyPart(i)
            when {
                part.isAttachment() -> {
                    val filename = part.fileName?.let { decodeFilename(it) } ?: "unnamed_${System.currentTimeMillis()}"
                    val mimeType = part.contentType.split(";")[0].trim()
                    val size = try { part.size } catch (e: Exception) { 0 }
                    attachments.add(
                        EmailAttachmentDto(
                            filename = filename,
                            mimeType = mimeType,
                            size = if (size >= 0) size else 0
                        )
                    )
                }
                part.content is Multipart -> {
                    processMultipart(part.content as Multipart, attachments)
                }
            }
        }
    }

    private fun Part.isAttachment(): Boolean {
        return (disposition?.equals(Part.ATTACHMENT, ignoreCase = true) == true ||
                disposition?.equals(Part.INLINE, ignoreCase = true) == true ||
                (fileName != null && !contentType.startsWith("text/")))
    }

    private fun decodeFilename(filename: String): String {
        return try {
            MimeUtility.decodeText(filename)
        } catch (e: Exception) {
            filename
        }
    }
}