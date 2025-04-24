package com.github.bogdanpronin.mail.controllers.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class MoveToTrashRequestDto(
    val providerName: String,
    val email: String,
    val uid: Long,
    val sourceFolder: String
)