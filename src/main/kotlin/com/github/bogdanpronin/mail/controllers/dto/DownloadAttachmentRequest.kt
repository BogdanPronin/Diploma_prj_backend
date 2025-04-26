package com.github.bogdanpronin.mail.controllers.dto

data class DownloadAttachmentRequest(
    val uid: Long,
    val filename: String,
    val folder: String,
    val email: String,
    val providerName: String
)