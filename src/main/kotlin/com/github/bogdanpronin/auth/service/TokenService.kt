package com.github.bogdanpronin.auth.service

import com.github.bogdanpronin.auth.config.OAuth2Config
import com.github.bogdanpronin.auth.model.OAuthToken
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import java.time.Instant

@Service
class TokenService {
    private val restTemplate = RestTemplate()

    fun exchangeCodeForToken(provider: String, code: String, config: OAuth2Config.Provider): OAuthToken {
        val body = mapOf(
            "code" to code,
            "client_id" to config.clientId,
            "client_secret" to config.clientSecret,
            "redirect_uri" to config.redirectUri,
            "grant_type" to "authorization_code"
        )
        val response = restTemplate.postForEntity(
            config.tokenUri,
            body,
            Map::class.java
        )

        val json = response.body ?: error("No response from token endpoint")
        val accessToken = json["access_token"] as String
        val refreshToken = json["refresh_token"] as String?
        val accessTokenExpiresIn = (json["expires_in"] as Number).toLong()
        val refreshTokenTokenExpiresIn = (json["refresh_token_expires_in"] as Number).toLong()

        val accessExpiresAt = Instant.now().plusSeconds(accessTokenExpiresIn)

        val refreshTokenExpiresAt = Instant.now().plusSeconds(refreshTokenTokenExpiresIn)

        val email = getEmailFromToken(accessToken)

        return OAuthToken(
            accessToken,
            refreshToken,
            accessExpiresAt,
            refreshTokenExpiresAt,
            email
        )
    }


    private fun getEmailFromToken(accessToken: String): String? {
        try {
            val headers = HttpHeaders().apply {
                setBearerAuth(accessToken)
            }
            val entity = HttpEntity<String>(headers)
            val response = restTemplate.exchange(
                "https://www.googleapis.com/oauth2/v3/userinfo",
                HttpMethod.GET,
                entity,
                Map::class.java
            )
            return response.body?.get("email") as? String
        } catch (e: Exception) {
            // Логируем ошибку, но не прерываем выполнение
            println("Failed to retrieve email from token: ${e.message}")
            return null // Возвращаем null, если email не удалось получить
        }
    }

//    fun refreshAccessToken(provider: String, config: OAuth2Config.Provider, userId: String): String {
//        val refreshToken = refreshTokenStorage[userId] ?: error("No refresh token stored for user")
//        val body = mapOf(
//            "client_id" to config.clientId,
//            "client_secret" to config.clientSecret,
//            "refresh_token" to refreshToken,
//            "grant_type" to "refresh_token"
//        )
//        val response = restTemplate.postForEntity(
//            config.tokenUri,
//            body,
//            Map::class.java
//        )
//        val json = response.body ?: error("No response")
//        return json["access_token"] as String
//    }
}
