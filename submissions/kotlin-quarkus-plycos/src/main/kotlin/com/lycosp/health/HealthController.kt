package com.lycosp.health

import io.smallrye.common.annotation.RunOnVirtualThread
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.jboss.resteasy.reactive.RestResponse
import org.jboss.resteasy.reactive.RestResponse.Status.SERVICE_UNAVAILABLE

@ApplicationScoped
@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
class HealthController(
    private val service: HealthService
) {

    @GET
    fun check(): RestResponse<HealthDTO> {
        val health = service.check()
        val healthy = health.status == "ok"
        return if (healthy) RestResponse.ok(health)
        else RestResponse.status(SERVICE_UNAVAILABLE, health)
    }
}
