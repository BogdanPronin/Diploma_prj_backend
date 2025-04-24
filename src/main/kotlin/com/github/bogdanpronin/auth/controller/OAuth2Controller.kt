package com.github.bogdanpronin.auth.controller

import com.github.bogdanpronin.auth.config.OAuth2Config
import com.github.bogdanpronin.auth.service.TokenService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.util.UriComponentsBuilder

@RestController
@RequestMapping("/auth")
class OAuth2Controller(
    private val config: OAuth2Config,
    private val tokenService: TokenService
) {

    @GetMapping("/login/{provider}")
    fun login(@PathVariable provider: String): ResponseEntity<Void> {
        println("dasd")
        val p = config.providers[provider] ?: error("Unknown provider")
        val uri = UriComponentsBuilder.fromHttpUrl(p.authUri)
            .queryParam("client_id", p.clientId)
            .queryParam("redirect_uri", p.redirectUri)
            .queryParam("response_type", "code")
            .queryParam("scope", p.scope)
            .queryParam("access_type", "offline")
            .queryParam("prompt", "consent")
            .build()
            .toUri()

        return ResponseEntity.status(302).location(uri).build()
    }


    @GetMapping("/callback/{provider}")
    fun callback(
        @PathVariable provider: String,
        @RequestParam code: String
    ): ResponseEntity<Void> {
        val p = config.providers[provider] ?: error("Unknown provider: $provider")
        val tokenResponse = tokenService.exchangeCodeForToken(provider, code, p)

        // Формируем URL для редиректа на фронтенд
        val redirectUri = UriComponentsBuilder
            .fromHttpUrl("http://localhost:3001/callback")
            .queryParam("accessToken", tokenResponse.accessToken)
            .queryParam("email", tokenResponse.email ?: error("Email not found"))
            .queryParam("provider", provider)
            .build()
            .toUri()

        return ResponseEntity.status(302).location(redirectUri).build()
    }

}
