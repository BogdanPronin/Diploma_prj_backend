package com.github.bogdanpronin.mail.provider

import com.github.bogdanpronin.mail.config.MailConfig
import com.github.bogdanpronin.mail.config.ProviderConfig
import jakarta.mail.Folder
import jakarta.mail.Session
import jakarta.mail.Store
import org.eclipse.angus.mail.imap.IMAPFolder

interface EmailProvider {
    fun getConfig(): ProviderConfig
    fun getSmtpConfig(): MailConfig // Добавляем метод для SMTP
    fun connect(session: Session, email: String, accessToken: String): Store
    fun getFolder(store: Store, category: String): IMAPFolder
}