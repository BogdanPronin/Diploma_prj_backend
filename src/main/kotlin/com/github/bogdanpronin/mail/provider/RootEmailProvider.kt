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
        val folderName = config.folderMappings[category] ?: throw IllegalArgumentException("Invalid folder: $category")
        return store.getFolder(folderName) as IMAPFolder
    }
}