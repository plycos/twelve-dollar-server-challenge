package com.lycosp.health

import jakarta.enterprise.context.ApplicationScoped
import java.sql.SQLException
import javax.sql.DataSource

@ApplicationScoped
class HealthRepository(
    private val datasource: DataSource
) {
    private companion object {
        const val SQL_HEALTHCHECK = "SELECT 1"
    }

    fun check(): Boolean =
        try {
            datasource.connection.use { conn ->
                conn.prepareStatement(SQL_HEALTHCHECK).use { stmt ->
                    stmt.execute()
                    return true
                }
            }
        } catch (_: SQLException) {
            return false
        }
}
