package com.github.bogdanpronin.mail.services
import com.github.bogdanpronin.mail.mapper.EmailMapper
import com.github.bogdanpronin.mail.model.EmailResponseDto
import com.github.bogdanpronin.mail.provider.EmailProvider
import jakarta.activation.DataHandler
import jakarta.activation.DataSource
import jakarta.mail.*
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.search.FromStringTerm
import jakarta.mail.search.RecipientStringTerm
import jakarta.mail.util.ByteArrayDataSource

import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.*

@Service
class ImapService(
    private val emailMapper: EmailMapper,
    private val providers: Map<String, EmailProvider>
) {

    fun readEmails(
        providerName: String,
        accessToken: String,
        email: String,
        category: String,
        beforeUid: Long?,
        limit: Int
    ): EmailResponseDto {
        val provider = providers[providerName] ?: throw IllegalArgumentException("Unknown provider: $providerName")

        val props = Properties().apply {
            put("mail.store.protocol", "imap")
            put("mail.imap.host", provider.getConfig().imap.host)
            put("mail.imap.port", provider.getConfig().imap.port)
            put("mail.imap.ssl.enable", provider.getConfig().imap.sslEnabled.toString())
            put("mail.imap.auth.mechanisms", provider.getConfig().imap.authMechanism)
        }
        val session = Session.getInstance(props)
        val store = provider.connect(session, email, accessToken)

        val folder = provider.getFolder(store, category)
        folder.open(Folder.READ_ONLY)

        val rootFolders = store.defaultFolder.list("*")

        val unreadCount = folder.unreadMessageCount
        val totalCount = folder.messageCount

        val messages = if (beforeUid != null) {
            folder.getMessagesByUID(1, beforeUid - 1).takeLast(limit).reversed()
        } else {
            folder.messages.takeLast(limit).reversed()
        }

        val parsedMessages = messages.map { msg ->
            emailMapper.mapMessage(msg, folder)
        }

        folder.close(false)
        store.close()

        return EmailResponseDto(
            totalMessages = totalCount,
            totalUnreadMessages = unreadCount,
            messages = parsedMessages
        )
    }

    fun moveToFolder(
        providerName: String,
        accessToken: String,
        email: String,
        uid: Long,
        sourceFolder: String,
        toFolder: String
    ): String {
        val provider = providers[providerName] ?: throw IllegalArgumentException("Unknown provider: $providerName")

        val props = Properties().apply {
            put("mail.store.protocol", "imap")
            put("mail.imap.host", provider.getConfig().imap.host)
            put("mail.imap.port", provider.getConfig().imap.port)
            put("mail.imap.ssl.enable", provider.getConfig().imap.sslEnabled.toString())
            put("mail.imap.auth.mechanisms", provider.getConfig().imap.authMechanism)
        }

        val session = Session.getInstance(props)
        val store = provider.connect(session, email, accessToken)

        try {
            val from = provider.getFolder(store, sourceFolder)
            val to = provider.getFolder(store, toFolder)

            from.open(Folder.READ_WRITE)
            to.open(Folder.READ_WRITE)

            val uidFolder = from as UIDFolder
            val message = uidFolder.getMessageByUID(uid)
                ?: throw MessagingException("Письмо с UID $uid не найдено")

            // Копируем письмо в целевую папку
            from.copyMessages(arrayOf(message), to)

            // Помечаем письмо как удалённое в исходной папке
            message.setFlag(Flags.Flag.DELETED, true)

            // Удаляем помеченные письма из исходной папки
            from.expunge()

            return "Письмо перемещено в папку $toFolder"
        } finally {
            store.close()
        }
    }




    fun deleteForever(
        providerName: String,
        accessToken: String,
        email: String,
        uid: Long,
        folderName: String
    ): String {
        val provider = providers[providerName] ?: throw IllegalArgumentException("Unknown provider: $providerName")

        val props = Properties().apply {
            put("mail.store.protocol", "imap")
            put("mail.imap.host", provider.getConfig().imap.host)
            put("mail.imap.port", provider.getConfig().imap.port)
            put("mail.imap.ssl.enable", provider.getConfig().imap.sslEnabled.toString())
            put("mail.imap.auth.mechanisms", provider.getConfig().imap.authMechanism)
        }

        val session = Session.getInstance(props)
        val store = provider.connect(session, email, accessToken)

        try {
            val folder = provider.getFolder(store, folderName)
            folder.open(Folder.READ_WRITE)

            val uidFolder = folder as UIDFolder
            val message = uidFolder.getMessageByUID(uid)
                ?: throw MessagingException("Письмо с UID $uid не найдено")

            message.setFlag(Flags.Flag.DELETED, true)
            folder.expunge()

            return "Письмо удалено навсегда"
        } finally {
            store.close()
        }
    }

    fun sendEmail(
        providerName: String,
        accessToken: String,
        to: String,
        subject: String,
        html: String,
        attachments: List<MultipartFile>?,
        email: String
    ) {
        val provider = providers[providerName] ?: throw IllegalArgumentException("Unknown provider: $providerName")
        val smtpConfig = provider.getConfig().smtp

        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")     // STARTTLS
            put("mail.smtp.ssl.enable", smtpConfig.sslEnabled)         // без SSL
            put("mail.smtp.host", smtpConfig.host)
            put("mail.smtp.port", smtpConfig.port)
            put("mail.smtp.auth.mechanisms",smtpConfig.authMechanism)
        }


        val session = Session.getInstance(props, null)
        val transport = session.getTransport("smtp")

        try {
            transport.connect(smtpConfig.host, email, accessToken)

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(email))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                setSubject(subject)

                val multipart = MimeMultipart()

                // HTML body part
                val htmlPart = MimeBodyPart()
                htmlPart.setContent(html, "text/html; charset=utf-8")
                multipart.addBodyPart(htmlPart)

                // Attachments
                attachments?.forEach { file ->
                    val attachmentPart = MimeBodyPart()
                    val ds: DataSource = ByteArrayDataSource(file.bytes, file.contentType ?: "application/octet-stream")
                    attachmentPart.dataHandler = DataHandler(ds)
                    attachmentPart.fileName = file.originalFilename
                    multipart.addBodyPart(attachmentPart)
                }

                setContent(multipart)
            }

            transport.sendMessage(message, message.allRecipients)
        } finally {
            transport.close()
        }
    }

    fun getEmailsFromSender(
        providerName: String,
        accessToken: String,
        email: String,
        senderEmail: String,
        limit: Int
    ): EmailResponseDto {
        val provider = providers[providerName] ?: throw IllegalArgumentException("Unknown provider: $providerName")

        val props = Properties().apply {
            put("mail.store.protocol", "imap")
            put("mail.imap.host", provider.getConfig().imap.host)
            put("mail.imap.port", provider.getConfig().imap.port)
            put("mail.imap.ssl.enable", provider.getConfig().imap.sslEnabled.toString())
            put("mail.imap.auth.mechanisms", provider.getConfig().imap.authMechanism)
        }
        val session = Session.getInstance(props)
        val store = provider.connect(session, email, accessToken)

        val folder = provider.getFolder(store, "INBOX")
        folder.open(Folder.READ_ONLY)

        try {
            val searchTerm = FromStringTerm(senderEmail)
            val messages = folder.search(searchTerm).takeLast(limit).reversed()
            val parsedMessages = messages.map { msg ->
                emailMapper.mapMessage(msg, folder)
            }

            return EmailResponseDto(
                totalMessages = parsedMessages.size,
                totalUnreadMessages = parsedMessages.count { !it.isRead },
                messages = parsedMessages
            )
        } finally {
            folder.close(false)
            store.close()
        }
    }

    fun getEmailsSentTo(
        providerName: String,
        accessToken: String,
        email: String,
        recipientEmail: String,
        limit: Int
    ): EmailResponseDto {
        val provider = providers[providerName] ?: throw IllegalArgumentException("Unknown provider: $providerName")

        val props = Properties().apply {
            put("mail.store.protocol", "imap")
            put("mail.imap.host", provider.getConfig().imap.host)
            put("mail.imap.port", provider.getConfig().imap.port)
            put("mail.imap.ssl.enable", provider.getConfig().imap.sslEnabled.toString())
            put("mail.imap.auth.mechanisms", provider.getConfig().imap.authMechanism)
        }
        val session = Session.getInstance(props)
        val store = provider.connect(session, email, accessToken)

        val folder = provider.getFolder(store, "SENT")
        folder.open(Folder.READ_ONLY)

        try {
            val searchTerm = RecipientStringTerm(Message.RecipientType.TO, recipientEmail)
            val messages = folder.search(searchTerm).takeLast(limit).reversed()
            val parsedMessages = messages.map { msg ->
                emailMapper.mapMessage(msg, folder)
            }

            return EmailResponseDto(
                totalMessages = parsedMessages.size,
                totalUnreadMessages = parsedMessages.count { !it.isRead },
                messages = parsedMessages
            )
        } finally {
            folder.close(false)
            store.close()
        }
    }

    fun downloadAttachment(
        providerName: String,
        accessToken: String,
        email: String,
        uid: Long,
        folderName: String,
        filename: String
    ): Pair<ByteArray, String> {
        val provider = providers[providerName] ?: throw IllegalArgumentException("Unknown provider: $providerName")

        val props = Properties().apply {
            put("mail.store.protocol", "imap")
            put("mail.imap.host", provider.getConfig().imap.host)
            put("mail.imap.port", provider.getConfig().imap.port)
            put("mail.imap.ssl.enable", provider.getConfig().imap.sslEnabled.toString())
            put("mail.imap.auth.mechanisms", provider.getConfig().imap.authMechanism)
        }

        val session = Session.getInstance(props)
        val store = provider.connect(session, email, accessToken)

        try {
            val folder = provider.getFolder(store, folderName)
            folder.open(Folder.READ_ONLY)

            val uidFolder = folder as UIDFolder
            val message = uidFolder.getMessageByUID(uid)
                ?: throw MessagingException("Письмо с UID $uid не найдено")

            val multipart = message.content as? Multipart
                ?: throw MessagingException("Письмо не содержит вложений")

            for (i in 0 until multipart.count) {
                val part = multipart.getBodyPart(i)
                if (Part.ATTACHMENT.equals(part.disposition, ignoreCase = true) && part.fileName == filename) {
                    val inputStream = part.inputStream
                    val byteArrayOutputStream = ByteArrayOutputStream()
                    inputStream.use { it.copyTo(byteArrayOutputStream) }
                    return Pair(byteArrayOutputStream.toByteArray(), part.contentType?.split(";")?.get(0) ?: "application/octet-stream")
                }
            }

            throw MessagingException("Вложение $filename не найдено в письме с UID $uid")
        } finally {
            store.close()
        }
    }

    fun markEmailsAsRead(
        providerName: String,
        accessToken: String,
        email: String,
        uids: List<Long>,
        folderName: String
    ): String {
        val provider = providers[providerName] ?: throw IllegalArgumentException("Unknown provider: $providerName")

        val props = Properties().apply {
            put("mail.store.protocol", "imap")
            put("mail.imap.host", provider.getConfig().imap.host)
            put("mail.imap.port", provider.getConfig().imap.port)
            put("mail.imap.ssl.enable", provider.getConfig().imap.sslEnabled.toString())
            put("mail.imap.auth.mechanisms", provider.getConfig().imap.authMechanism)
        }

        val session = Session.getInstance(props)
        val store = provider.connect(session, email, accessToken)

        try {
            val folder = provider.getFolder(store, folderName)
            folder.open(Folder.READ_WRITE)

            val uidFolder = folder as UIDFolder
            val messages = uids.mapNotNull { uid ->
                uidFolder.getMessageByUID(uid)
            }.toTypedArray()

            if (messages.isNotEmpty()) {
                // Устанавливаем флаг \Seen для указанных писем
                folder.setFlags(messages, Flags(Flags.Flag.SEEN), true)
            } else {
                throw MessagingException("Письма с указанными UID не найдены: $uids")
            }

            folder.close(false)
            return "Письма успешно помечены как прочитанные"
        } finally {
            store.close()
        }
    }

}
