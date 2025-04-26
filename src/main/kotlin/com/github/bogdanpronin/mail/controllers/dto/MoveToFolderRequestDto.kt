package com.github.bogdanpronin.mail.controllers.dto


data class MoveToFolderRequestDto(
    val providerName: String,
    val email: String,
    val uid: Long,
    val sourceFolder: String,
    val toFolder: String,
)