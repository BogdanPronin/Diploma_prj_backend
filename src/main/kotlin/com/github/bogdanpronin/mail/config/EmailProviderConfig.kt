package com.github.bogdanpronin.mail.config

import com.github.bogdanpronin.mail.provider.impl.CustomEmailProvider
import com.github.bogdanpronin.mail.provider.impl.GoogleEmailProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class EmailProviderConfig {
    @Bean
    fun google(properties: EmailProviderProperties): GoogleEmailProvider {
        return GoogleEmailProvider(properties.google)
    }

    @Bean
    fun custom(properties: EmailProviderProperties): CustomEmailProvider {
        return CustomEmailProvider(properties.custom)
    }
}