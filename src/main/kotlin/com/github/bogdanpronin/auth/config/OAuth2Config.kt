package com.github.bogdanpronin.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "oauth2")
class OAuth2Config {
    val providers: MutableMap<String, Provider> = mutableMapOf()

    class Provider {
        lateinit var clientId: String
        lateinit var clientSecret: String
        lateinit var redirectUri: String
        lateinit var authUri: String
        lateinit var tokenUri: String
        lateinit var scope: String
    }
}
