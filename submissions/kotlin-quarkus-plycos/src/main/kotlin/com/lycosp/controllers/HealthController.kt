package com.lycosp.controllers

import com.lycosp.HealthCheck
import com.lycosp.controllers.utils.ResponseFactory
import io.smallrye.common.annotation.RunOnVirtualThread
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.lang.management.ManagementFactory
import java.sql.SQLException
import javax.sql.DataSource

@ApplicationScoped
@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
class HealthController(
    private val dataSource: DataSource
) {
    private companion object {
        const val SQL_HEALTHCHECK = "SELECT 1"
    }

    @GET
    fun healthcheck(): Response {
        return try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(SQL_HEALTHCHECK).execute()
            }
            ResponseFactory.ok(
                HealthCheck(
                    status = "ok",
                    db = "ok",
                    uptimeS = ManagementFactory.getRuntimeMXBean().uptime / 1000
                )
            )
        } catch (_: Exception) {
            Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(
                    HealthCheck(
                        status = "degraded",
                        db = "unreachable",
                        error = "internal server error"
                    )
                )
                .build()
        }
    }
}
