package com.github.bogdanpronin.mail.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "email.providers")
data class EmailProviderProperties(
    val google: ProviderConfig
)

data class ProviderConfig(
    val imap: MailConfig,
    val smtp: MailConfig,
    val folderMappings: Map<String, String>
)

data class MailConfig(
    val host: String,
    val port: String,
    val sslEnabled: Boolean,
    val authMechanism: String
)