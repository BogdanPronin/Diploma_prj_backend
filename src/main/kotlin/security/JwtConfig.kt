package org.example.security

import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.routing.*
import com.mail.routes.emailRoutes
import com.mail.routes.authRoutes

val secretKey = "yourSecretKey" // 🔐 Ключ для подписи JWT

fun main() {
    embeddedServer(Netty, port = 3000, module = Application::module).start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) { json() }
    install(CORS) { anyHost() }

    install(Authentication) {
        jwt("auth-jwt") {
            verifier(JwtConfig.verifier)
            validate { credential -> JwtConfig.validate(credential) }
        }
    }

    routing {
        authRoutes()
        authenticate("auth-jwt") {
            emailRoutes()
        }
    }
}
