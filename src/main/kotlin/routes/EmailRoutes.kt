package org.example.routes

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.serialization.Serializable
import com.mail.services.EmailService

fun Route.emailRoutes() {
    val emailService = EmailService()

    route("/send") {
        post {
            val request = call.receive<SendEmailRequest>()
            val result = emailService.sendEmail(request.to, request.subject, request.html)
            if (result.success) {
                call.respond(HttpStatusCode.OK, mapOf("message" to "Письмо отправлено"))
            } else {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to result.error))
            }
        }
    }

    route("/receive") {
        get {
            val category = call.request.queryParameters["category"] ?: "INBOX"
            val emails = emailService.fetchEmails(category)
            call.respond(HttpStatusCode.OK, emails)
        }
    }

    route("/mark-read") {
        post {
            val request = call.receive<MarkReadRequest>()
            val result = emailService.markAsRead(request.uids)
            if (result.success) {
                call.respond(HttpStatusCode.OK, mapOf("message" to "Письма помечены как прочитанные"))
            } else {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to result.error))
            }
        }
    }

    route("/move-to-trash") {
        post {
            val request = call.receive<MoveToTrashRequest>()
            val result = emailService.moveToTrash(request.uid)
            if (result.success) {
                call.respond(HttpStatusCode.OK, mapOf("message" to "Письмо перемещено в корзину"))
            } else {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to result.error))
            }
        }
    }
}

@Serializable
data class SendEmailRequest(val to: String, val subject: String, val html: String)

@Serializable
data class MarkReadRequest(val uids: List<Long>)

@Serializable
data class MoveToTrashRequest(val uid: Long)
