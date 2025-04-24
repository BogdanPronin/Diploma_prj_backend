package com.github.bogdanpronin.mail.controllers

import com.github.bogdanpronin.mail.controllers.dto.DeleteForeverRequestDto
import com.github.bogdanpronin.mail.controllers.dto.MoveToTrashRequestDto
import com.github.bogdanpronin.mail.model.*
import com.github.bogdanpronin.mail.services.ImapService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/mail")
class EmailController(
    private val imapService: ImapService
) {
//    @PostMapping("/send", consumes = ["multipart/form-data"])
//    fun sendEmail(@ModelAttribute form: SendEmailForm): ResponseEntity<Any> {
//        emailService.sendEmail(form)
//        return ResponseEntity.ok(mapOf("message" to "Письмо с вложениями отправлено"))
//    }
    @GetMapping("/receive")
    fun getEmails(
        @RequestHeader("Authorization") authHeader: String,
        @RequestParam provider: String,
        @RequestParam email: String,
        @RequestParam(required = false, defaultValue = "INBOX") category: String,
        @RequestParam(required = false) beforeUid: Long?,
        @RequestParam(required = false, defaultValue = "10") limit: Int
    ): ResponseEntity<EmailResponseDto> {
        val accessToken = authHeader.removePrefix("Bearer ").trim()
        val result = imapService.readEmails(provider, accessToken, email, category, beforeUid, limit)
        return ResponseEntity.ok(result)
    }

    @PostMapping("/move-to-trash")
    fun moveToTrash(
        @RequestHeader("Authorization") authHeader: String,
        @RequestBody request: MoveToTrashRequestDto
    ): ResponseEntity<Map<String, String>> {
        val accessToken = authHeader.removePrefix("Bearer ").trim()
        val result = imapService.moveToTrash(
            providerName = request.providerName,
            accessToken = accessToken,
            email = request.email,
            uid = request.uid,
            sourceFolder = request.sourceFolder
        )
        return ResponseEntity.ok(mapOf("message" to result))
    }

    @PostMapping("/delete-forever")
    fun deleteForever(
        @RequestHeader("Authorization") authHeader: String,
        @RequestBody request: DeleteForeverRequestDto
    ): ResponseEntity<Map<String, String>> {
        val accessToken = authHeader.removePrefix("Bearer ").trim()
        val result = imapService.deleteForever(
            providerName = request.providerName,
            accessToken = accessToken,
            email = request.email,
            uid = request.uid,
            folderName = request.folderName
        )
        return ResponseEntity.ok(mapOf("message" to "Письмо перемещено в корзину"))

    }

    @PostMapping("/send", consumes = ["multipart/form-data"])
    fun sendEmail(
        @RequestHeader("Authorization") authHeader: String,
        @RequestPart("to") to: String,
        @RequestPart("subject") subject: String,
        @RequestPart("html") html: String,
        @RequestPart("providerName") providerName: String,
        @RequestPart("email") email: String, // Добавляем email
        @RequestPart("attachments", required = false) attachments: List<MultipartFile>?
    ): ResponseEntity<Unit> {
            val accessToken = authHeader.removePrefix("Bearer ").trim()

            val result = imapService.sendEmail(
                providerName = providerName,
                accessToken = accessToken,
                to = to,
                subject = subject,
                html = html,
                attachments = attachments,
                email = email
            )
            return ResponseEntity.ok(result)

    }

    @GetMapping("/emails-from-sender")
    fun getEmailsFromSender(
        @RequestParam providerName: String,
        @RequestParam email: String,
        @RequestParam sender: String,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestHeader("Authorization") authorization: String
    ): ResponseEntity<EmailResponseDto> {
        return try {
            val accessToken = authorization.removePrefix("Bearer ").trim()
            val response = imapService.getEmailsFromSender(providerName, accessToken, email, sender, limit)
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(EmailResponseDto(0, 0, emptyList()))
        }
    }

    @GetMapping("/emails-sent-to")
    fun getEmailsSentTo(
        @RequestParam providerName: String,
        @RequestParam email: String,
        @RequestParam recipient: String,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestHeader("Authorization") authorization: String
    ): ResponseEntity<EmailResponseDto> {
        return try {
            val accessToken = authorization.removePrefix("Bearer ").trim()
            val response = imapService.getEmailsSentTo(providerName, accessToken, email, recipient, limit)
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(EmailResponseDto(0, 0, emptyList()))
        }
    }

}

