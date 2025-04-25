package com.github.bogdanpronin.mail.provider

import com.github.bogdanpronin.mail.config.MailConfig
import com.github.bogdanpronin.mail.config.ProviderConfig
import jakarta.mail.Folder
import jakarta.mail.Session
import jakarta.mail.Store
import org.eclipse.angus.mail.imap.IMAPFolder
import org.eclipse.angus.mail.imap.IMAPStore

abstract class RootEmailProvider(
    private val config: ProviderConfig
) : EmailProvider {
    override fun getConfig(): ProviderConfig = config

    override fun getSmtpConfig(): MailConfig = config.smtp

    override fun connect(session: Session, email: String, accessToken: String): Store {
        val store = session.getStore("imap") as IMAPStore
        store.connect(config.imap.host, email, accessToken)
        return store
    }

    override fun getFolder(store: Store, category: String): IMAPFolder {
        val folderName = detectFolders(store)[category] ?: category
        return store.getFolder(folderName) as IMAPFolder
    }

    private val folderKeywords = mapOf(
        "INBOX" to listOf("inbox", "входящие", "boîte de réception", "posta in arrivo"),
        "SENT" to listOf("sent", "отправленные", "enviados", "inviati"),
        "DRAFTS" to listOf("drafts", "черновики", "bozze", "brouillons"),
        "TRASH" to listOf("trash", "корзина", "cestino", "corbeille"),
        "SPAM" to listOf("spam", "спам", "junk", "courrier indésirable"),
        "ARCHIVE" to listOf("archive", "архив", "archivio", "archives")
    )

    private fun detectFolders(store: Store): Map<String, String> {
        val folders = store.defaultFolder.list("*")
        val mapping = mutableMapOf<String, String>()
        for ((logicalName, keywords) in folderKeywords) {
            val match = folders.find { folder ->
                keywords.any { keyword ->
                    folder.fullName.lowercase().contains(keyword)
                }
            }
            if (match != null) {
                mapping[logicalName] = match.fullName
            }
        }
        return mapping
    }
}