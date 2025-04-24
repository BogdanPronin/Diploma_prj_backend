package com.github.bogdanpronin.auth.model

import java.time.Instant

data class OAuthToken(
    val accessToken: String,
    val refreshToken: String?,
    val accessTokenExpiresAt: Instant,
    val refreshTokenExpiresAt: Instant,
    val email: String?,
)
