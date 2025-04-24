package com.github.bogdanpronin.mail.model

import org.springframework.web.multipart.MultipartFile

data class SendEmailForm(
    val to: String,
    val subject: String,
    val html: String,
    val attachments: List<MultipartFile>? = null
)
