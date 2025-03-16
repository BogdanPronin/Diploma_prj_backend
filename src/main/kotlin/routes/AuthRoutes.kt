package org.example.routes

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import com.mail.security.JwtConfig
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

fun Route.authRoutes() {
    route("/login") {
        post {
            val request = call.receive<AuthRequest>()
            if (request.email == "test@mail.ru" && request.password == "password") {
                val token = JwtConfig.generateToken(request.email)
                call.respond(HttpStatusCode.OK, mapOf("token" to token))
            } else {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Неверные данные"))
            }
        }
    }
}

@Serializable
data class AuthRequest(val email: String, val password: String)
