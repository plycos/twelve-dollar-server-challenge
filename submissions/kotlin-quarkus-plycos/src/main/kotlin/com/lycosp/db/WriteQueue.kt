package com.lycosp.db

import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import io.agroal.api.AgroalDataSource
import io.quarkus.agroal.DataSource
import java.sql.Connection
import java.sql.PreparedStatement
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.LinkedBlockingQueue

@ApplicationScoped
class WriteQueue(
    @param:DataSource("writer") private val dataSource: AgroalDataSource
) {
    class WriteContext(private val conn: Connection) {
        private val statements = HashMap<String, PreparedStatement>()

        fun statement(sql: String): PreparedStatement = statements.getOrPut(sql) { conn.prepareStatement(sql) }

        internal fun close() {
            statements.values.forEach { runCatching { it.close() } }
            runCatching { conn.close() }
        }
    }

    private class Task<T>(val op: (WriteContext) -> T) {
        val future = CompletableFuture<T>()
        var result: Any? = null
        var error: Throwable? = null

        fun run(ctx: WriteContext) {
            try {
                result = op(ctx)
            } catch (e: Exception) {
                error = e
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun finish() {
            val e = error
            if (e != null) future.completeExceptionally(e) else future.complete(result as T)
        }
    }

    private companion object {
        const val MAX_BATCH = 512
    }

    private val queue = LinkedBlockingQueue<Task<*>>()

    @Volatile
    private var running = true
    private lateinit var thread: Thread

    fun onStart(@Observes event: StartupEvent) {
        thread = Thread(::run, "sqlite-writer").apply {
            isDaemon = true
            start()
        }
    }

    fun onStop(@Observes event: ShutdownEvent) {
        running = false
        thread.interrupt()
    }

    fun <T> submit(op: (WriteContext) -> T): T {
        val task = Task(op)
        queue.put(task)
        try {
            return task.future.get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }

    private fun openConnection(): Connection = dataSource.connection.apply { autoCommit = false }

    private fun run() {
        val conn = openConnection()
        val ctx = WriteContext(conn)
        val batch = ArrayList<Task<*>>(MAX_BATCH)
        try {
            while (running) {
                batch.add(queue.take())
                queue.drainTo(batch, MAX_BATCH - 1)
                try {
                    for (task in batch) task.run(ctx)
                    conn.commit()
                    for (task in batch) task.finish()
                } catch (e: Exception) {
                    runCatching { conn.rollback() }
                    for (task in batch) task.future.completeExceptionally(e)
                } finally {
                    batch.clear()
                }
            }
        } catch (_: InterruptedException) {
            // shutting down
        } finally {
            queue.forEach { it.future.completeExceptionally(IllegalStateException("writer stopped")) }
            ctx.close()
        }
    }
}
