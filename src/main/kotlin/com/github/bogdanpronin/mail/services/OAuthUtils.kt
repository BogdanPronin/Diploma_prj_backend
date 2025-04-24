package com.github.bogdanpronin.mail.services

import java.util.Base64

object OAuthUtils {
    fun generateXOAuth2Token(email: String, accessToken: String): String {
        val authString = "user=$email\u0001auth=Bearer $accessToken\u0001\u0001"
        return Base64.getEncoder().encodeToString(authString.toByteArray())
    }
}