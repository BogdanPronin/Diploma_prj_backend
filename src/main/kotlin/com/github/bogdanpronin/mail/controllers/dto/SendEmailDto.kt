package com.github.bogdanpronin.mail.controllers.dto

import org.springframework.web.multipart.MultipartFile

data class SendEmailDto(
    val to: String,
    val cc: String? = null,
    val bcc: String? = null,
    val subject: String,
    val html: String,
    val providerName: String,
    val email: String,
    val accessToken: String,
    val inReplyTo: String? = null,
    val references: String? = null,
    val attachments: List<MultipartFile>? = null
)