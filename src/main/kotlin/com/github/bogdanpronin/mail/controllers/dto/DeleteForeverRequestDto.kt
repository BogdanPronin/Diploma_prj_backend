package com.github.bogdanpronin.mail.controllers.dto

data class DeleteForeverRequestDto(
    val providerName: String,
    val email: String,
    val uid: Long,
    val folderName: String
)