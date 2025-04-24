package com.github.bogdanpronin.mail.provider.impl

import com.github.bogdanpronin.mail.config.ProviderConfig
import com.github.bogdanpronin.mail.provider.RootEmailProvider
import org.springframework.stereotype.Component


class GoogleEmailProvider(config: ProviderConfig) : RootEmailProvider(config)