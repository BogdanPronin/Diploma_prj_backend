package com.github.bogdanpronin.mail.controllers.dto

data class MarkReadRequest(
    val providerName: String,
    val email: String,
    val uids: List<Long>,
    val folderName: String
)