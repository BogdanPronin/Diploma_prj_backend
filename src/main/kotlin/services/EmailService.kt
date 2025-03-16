package org.example.services

import kotlinx.serialization.Serializable
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import com.sun.mail.imap.IMAPStore
import com.sun.mail.imap.IMAPFolder
import java.util.*

class EmailService {
    private val emailUser = System.getenv("MAIL_USER") ?: "your@mail.ru"
    private val emailPass = System.getenv("MAIL_PASS") ?: "yourpassword"

    fun sendEmail(to: String, subject: String, html: String): EmailResponse {
        return try {
            val props = Properties().apply {
                put("mail.smtp.host", "smtp.mail.ru")
                put("mail.smtp.port", "465")
                put("mail.smtp.auth", "true")
                put("mail.smtp.ssl.enable", "true")
            }

            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication() = PasswordAuthentication(emailUser, emailPass)
            })

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(emailUser))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                setSubject(subject)
                setContent(html, "text/html; charset=utf-8")
            }

            Transport.send(message)
            EmailResponse(success = true)
        } catch (e: Exception) {
            EmailResponse(success = false, error = e.message)
        }
    }

    fun fetchEmails(category: String): List<Email> {
        val store = connectImap()
        val folder = store.getFolder(category) as IMAPFolder
        folder.open(Folder.READ_ONLY)

        val messages = folder.messages.takeLast(10).map { msg ->
            Email(
                uid = msg.messageNumber.toLong(),
                subject = msg.subject ?: "<Без темы>",
                from = (msg.from[0] as InternetAddress).address,
                date = msg.sentDate.toString(),
                text = msg.content.toString()
            )
        }

        folder.close(false)
        store.close()

        return messages
    }

    fun markAsRead(uids: List<Long>): EmailResponse {
        val store = connectImap()
        val folder = store.getFolder("INBOX") as IMAPFolder
        folder.open(Folder.READ_WRITE)

        uids.forEach { uid ->
            val msg = folder.getMessage(uid.toInt())
            msg.setFlag(Flags.Flag.SEEN, true)
        }

        folder.close(true)
        store.close()

        return EmailResponse(success = true)
    }

    fun moveToTrash(uid: Long): EmailResponse {
        val store = connectImap()
        val inbox = store.getFolder("INBOX") as IMAPFolder
        val trash = store.getFolder("Корзина") as IMAPFolder

        inbox.open(Folder.READ_WRITE)
        inbox.moveMessages(arrayOf(inbox.getMessage(uid.toInt())), trash)

        inbox.close(true)
        store.close()

        return EmailResponse(success = true)
    }

    private fun connectImap(): IMAPStore {
        val props = Properties().apply {
            put("mail.imap.host", "imap.mail.ru")
            put("mail.imap.port", "993")
            put("mail.imap.ssl.enable", "true")
        }
        val session = Session.getInstance(props)
        return session.getStore("imap") as IMAPStore
        .apply { connect(emailUser, emailPass) }
    }
}

@Serializable
data class Email(val uid: Long, val subject: String, val from: String, val date: String, val text: String)

@Serializable
data class EmailResponse(val success: Boolean, val error: String? = null)
