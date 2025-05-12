package com.github.bogdanpronin

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig : WebMvcConfigurer {
    private val allowedOrigins: Array<String> = System.getenv("ALLOWED_ORIGINS")
        ?.split(",")
        ?.map { it.trim() }
        ?.toTypedArray()
        ?: arrayOf()

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOrigins(*allowedOrigins)
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("Authorization", "Content-Type", "Accept")
            .allowCredentials(true)
            .maxAge(3600)
    }
}