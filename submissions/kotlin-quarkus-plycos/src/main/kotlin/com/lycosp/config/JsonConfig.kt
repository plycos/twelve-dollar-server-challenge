package com.lycosp.config

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton
import kotlinx.serialization.json.Json

@ApplicationScoped
class JsonConfig {

    @Singleton
    @Produces
    fun json(): Json = Json {
        ignoreUnknownKeys = true
    }
}
