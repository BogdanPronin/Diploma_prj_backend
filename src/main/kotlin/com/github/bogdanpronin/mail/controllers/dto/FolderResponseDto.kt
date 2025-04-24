package com.github.bogdanpronin.mail.controllers.dto

data class FolderResponseDto(
    val folders: List<String>
)

data class FolderDto(
    val name: String, // Имя папки (например, "INBOX", "SENT")
    val unreadCount: Int // Количество непрочитанных сообщений
)