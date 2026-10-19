package com.lycosp.health

import jakarta.enterprise.context.ApplicationScoped
import java.lang.management.ManagementFactory

@ApplicationScoped
class HealthService(
    private val repository: HealthRepository
) {
    private val runtime = ManagementFactory.getRuntimeMXBean()

    fun check(): HealthDTO {
        val healthy = repository.check()
        return if (healthy) HealthDTO(
            status = "ok",
            db = "ok",
            uptime = runtime.uptime / 1_000
        )
        else HealthDTO(
            status = "degraded",
            db = "unreachable",
            error = "internal server error"
        )
    }
}
