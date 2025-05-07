package com.github.bogdanpronin.mail.services
import com.github.bogdanpronin.mail.controllers.dto.SendEmailDto
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
import jakarta.mail.search.*
import jakarta.mail.util.ByteArrayDataSource

import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.*
import kotlin.math.max

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

        val folder = provider.getFolder(store, category).apply { open(Folder.READ_ONLY) }
        val uidFolder = folder as UIDFolder

        // 3. Считаем общее и непрочитанное
        val totalCount = folder.messageCount
        val unreadCount = folder.unreadMessageCount

        // 4. Вычисляем границы по sequence numbers
        val endSeq = if (beforeUid != null) {
            // находим номер сообщения с этим UID и отнимаем 1
            uidFolder.getMessageByUID(beforeUid)?.messageNumber?.minus(1)
                ?: folder.messageCount
        } else {
            folder.messageCount
        }
        val startSeq = max(1, endSeq - limit + 1)

        // 5. Берём именно этот диапазон по номерам
        val initialMsgs: Array<Message> = folder.getMessages(startSeq, endSeq)

        // 6. Пакетно загружаем нужные поля
        val fetchProfile = FetchProfile().apply {
            add(FetchProfile.Item.ENVELOPE)
            add(FetchProfile.Item.FLAGS)
            add("Message-ID")
            add("References")
        }
        folder.fetch(initialMsgs, fetchProfile)

        // 7. Маппим и собираем уже загруженные Message-ID
        val mapped = initialMsgs.map { msg -> emailMapper.mapMessage(msg, folder) }
        val existingIds = mapped.map { it.messageId ?: it.uid.toString() }.toSet()

        // 8. Собираем новые References
        val refs = mapped
            .flatMap { it.references?.split("\\s+".toRegex()).orEmpty() }
            .filter { it.isNotBlank() && it !in existingIds }
            .distinct()

        // 9. Ищем все References одним OrTerm-запросом
        val additional = if (refs.isNotEmpty()) {
            val terms = refs.map { ref -> HeaderTerm("Message-ID", ref) as SearchTerm }
                .toTypedArray()
            val orTerm = OrTerm(terms)
            val found = folder.search(orTerm)
            folder.fetch(found, fetchProfile)
            found
                .map { msg -> emailMapper.mapMessage(msg, folder) }
                .filter { msgDto -> (msgDto.messageId ?: msgDto.uid.toString()) !in existingIds }
        } else {
            emptyList()
        }

        // 10. Объединяем, убираем дубликаты и группируем по цепочкам
        val all = (mapped + additional).distinctBy { it.uid }
        val grouped = emailMapper.groupThreads(all)

        // 11. Закрываем ресурсы
        folder.close(false)
        store.close()

        return EmailResponseDto(
            totalMessages       = totalCount,
            totalUnreadMessages = unreadCount,
            messages            = grouped
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
        var from: Folder? = null
        var to: Folder? = null

        try {
            from = provider.getFolder(store, sourceFolder)
            to = provider.getFolder(store, toFolder)

            from.open(Folder.READ_WRITE)
            to.open(Folder.READ_WRITE)

            val uidFolder = from as UIDFolder
            val message = uidFolder.getMessageByUID(uid)
                ?: throw MessagingException("Письмо с UID $uid не найдено в папке $sourceFolder")

            from.copyMessages(arrayOf(message), to)

            if (providerName.lowercase() == "custom") {
                if (toFolder == "TRASH") {
                    message.setFlag(Flags.Flag.DELETED, true)
                }
            }

            from.expunge()

            return "Письмо перемещено в папку $toFolder"
        } catch (e: MessagingException) {
           throw e
        } finally {
            from?.close(true)
            to?.close(true)
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

    fun sendEmail(dto: SendEmailDto) {
        val provider = providers[dto.providerName] ?: throw IllegalArgumentException("Unknown provider: ${dto.providerName}")
        val smtpConfig = provider.getConfig().smtp

        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.ssl.enable", smtpConfig.sslEnabled)
            put("mail.smtp.host", smtpConfig.host)
            put("mail.smtp.port", smtpConfig.port)
            put("mail.smtp.auth.mechanisms", smtpConfig.authMechanism)
        }

        val session = Session.getInstance(props, null)
        val transport = session.getTransport("smtp")

        try {
            transport.connect(smtpConfig.host, dto.email, dto.accessToken)

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(dto.email))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(dto.to))

                if (!dto.cc.isNullOrBlank()) {
                    setRecipients(Message.RecipientType.CC, InternetAddress.parse(dto.cc))
                }

                if (!dto.bcc.isNullOrBlank()) {
                    setRecipients(Message.RecipientType.BCC, InternetAddress.parse(dto.bcc))
                }

                setSubject(dto.subject, "UTF-8")
                if (!dto.inReplyTo.isNullOrBlank()) {
                    setHeader("In-Reply-To", dto.inReplyTo)
                }
                if (!dto.references.isNullOrBlank()) {
                    setHeader("References", dto.references)
                }

                val multipart = MimeMultipart()

                val htmlPart = MimeBodyPart()
                htmlPart.setContent(dto.html, "text/html; charset=utf-8")
                multipart.addBodyPart(htmlPart)

                dto.attachments?.forEach { file ->
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
            var parsedMessages = messages.map { msg ->
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
            var parsedMessages = messages.map { msg ->
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

    fun saveDraft(
        to: String?,
        bcc: String?,
        cc: String?,
        subject: String?,
        html: String?,
        providerName: String,
        email: String,
        accessToken: String,
        inReplyTo: String?,
        references: String?,
        attachments: List<MultipartFile>?
    ): Long {
        val provider = providers[providerName] ?: throw IllegalArgumentException("Unknown provider: $providerName")
        val imapConfig = provider.getConfig().imap

        val props = Properties().apply {
            put("mail.store.protocol", "imap")
            put("mail.imap.host", imapConfig.host)
            put("mail.imap.port", imapConfig.port)
            put("mail.imap.ssl.enable", imapConfig.sslEnabled.toString())
            put("mail.imap.auth.mechanisms", imapConfig.authMechanism)
        }

        val session = Session.getInstance(props)
        val store = provider.connect(session, email, accessToken)

        var draftsFolder: Folder? = null
        try {
            draftsFolder = provider.getFolder(store, "DRAFTS")
            draftsFolder.open(Folder.READ_WRITE)

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(email))
                if (!to.isNullOrBlank()) {
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                }
                if (!cc.isNullOrBlank()) {
                    setRecipients(Message.RecipientType.CC, InternetAddress.parse(cc))
                }
                if (!bcc.isNullOrBlank()) {
                    setRecipients(Message.RecipientType.BCC, InternetAddress.parse(bcc))
                }
                setSubject(subject, "UTF-8")
                if (!inReplyTo.isNullOrBlank()) {
                    setHeader("In-Reply-To", inReplyTo)
                }
                if (!references.isNullOrBlank()) {
                    setHeader("References", references)
                }

                val multipart = MimeMultipart()
                val htmlPart = MimeBodyPart()
                htmlPart.setContent(html, "text/html; charset=utf-8")
                multipart.addBodyPart(htmlPart)

                attachments?.forEach { file ->
                    val attachmentPart = MimeBodyPart()
                    val ds: DataSource = ByteArrayDataSource(file.bytes, file.contentType ?: "application/octet-stream")
                    attachmentPart.dataHandler = DataHandler(ds)
                    attachmentPart.fileName = file.originalFilename
                    multipart.addBodyPart(attachmentPart)
                }

                setContent(multipart)
                setFlag(Flags.Flag.DRAFT, true)
            }

            draftsFolder.appendMessages(arrayOf(message))

            val uidFolder = draftsFolder as UIDFolder
            val savedMessage = draftsFolder.messages.lastOrNull()
                ?: throw MessagingException("Не удалось сохранить черновик")
            val uid = uidFolder.getUID(savedMessage)

            return uid
        } finally {
            try {
                draftsFolder?.close(false)
            } catch (e: Exception) {
                println("Ошибка при закрытии папки DRAFTS: ${e.message}")
            }
            try {
                store.close()
            } catch (e: Exception) {
                println("Ошибка при закрытии хранилища: ${e.message}")
            }
        }
    }
}
