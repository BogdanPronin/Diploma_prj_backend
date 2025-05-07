package com.github.bogdanpronin.mail.controllers

import com.github.bogdanpronin.mail.controllers.dto.*
import com.github.bogdanpronin.mail.model.*
import com.github.bogdanpronin.mail.services.ImapService
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@RestController
@RequestMapping("/api/mail")
class EmailController(
    private val imapService: ImapService
) {
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

    @PostMapping("/move-to-folder")
    fun moveToTrash(
        @RequestHeader("Authorization") authHeader: String,
        @RequestBody request: MoveToFolderRequestDto
    ): ResponseEntity<Map<String, String>> {
        val accessToken = authHeader.removePrefix("Bearer ").trim()
        val result = imapService.moveToFolder(
            providerName = request.providerName,
            accessToken = accessToken,
            email = request.email,
            uid = request.uid,
            sourceFolder = request.sourceFolder,
            toFolder = request.toFolder,
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
        @RequestPart("bcc", required = false) bcc: String?,
        @RequestPart("cc", required = false) cc: String?,
        @RequestPart("subject") subject: String,
        @RequestPart("html") html: String,
        @RequestPart("providerName") providerName: String,
        @RequestPart("email") email: String,
        @RequestPart("inReplyTo", required = false) inReplyTo: String?,
        @RequestPart("references", required = false) references: String?,
        @RequestPart("attachments", required = false) attachments: List<MultipartFile>?
    ): ResponseEntity<Unit> {
        val accessToken = authHeader.removePrefix("Bearer ").trim()

        val result = imapService.sendEmail(
            SendEmailDto(
                providerName = providerName,
                accessToken = accessToken,
                to = to,
                subject = subject,
                html = html,
                attachments = attachments,
                email = email,
                bcc = bcc,
                cc = cc,
                inReplyTo = inReplyTo,
                references = references
            )
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

    @PostMapping("/download-attachment")
    fun downloadAttachment(
        @RequestBody request: DownloadAttachmentRequest,
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<ByteArrayResource> {
        val accessToken = authHeader.removePrefix("Bearer ").trim()

        val (fileContent, contentType) = imapService.downloadAttachment(
            request.providerName,
            accessToken,
            request.email,
            request.uid,
            request.folder,
            request.filename
        )

        val encodedFilename = URLEncoder.encode(request.filename, StandardCharsets.UTF_8.toString())
            .replace("+", "%20")
        val contentDisposition = "attachment; filename*=UTF-8''$encodedFilename"

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
            .body(ByteArrayResource(fileContent))
    }

    @PostMapping("/mark-read-batch")
    fun markEmailsAsRead(
        @RequestBody request: MarkReadRequest,
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<String> {
        val accessToken = authHeader.removePrefix("Bearer ").trim()
        val response = imapService.markEmailsAsRead(
            request.providerName,
            accessToken,
            request.email,
            request.uids,
            request.folderName
        )
        return ResponseEntity.ok(response)
    }

}

