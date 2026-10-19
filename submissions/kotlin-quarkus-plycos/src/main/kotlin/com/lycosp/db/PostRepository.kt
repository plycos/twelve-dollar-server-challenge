package com.lycosp.db

import com.lycosp.Post
import jakarta.enterprise.context.ApplicationScoped
import org.sqlite.SQLiteConnection
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource

@ApplicationScoped
class PostRepository(
    private val readers: DataSource,
    private val writer: WriteQueue
) {
    data class CreatedPost(val id: Int, val createdAt: String)

    private companion object {
        const val SQL_POST_BASE =
            "SELECT p.id, p.body, p.created_at, u.username, (SELECT COUNT(*) FROM likes l WHERE l.post_id = p.id) AS like_count FROM posts p JOIN users u ON u.id = p.user_id"
        const val SQL_FEED = "$SQL_POST_BASE ORDER BY p.created_at DESC, p.id DESC LIMIT 20"
        const val SQL_GET_POST = "$SQL_POST_BASE WHERE p.id = ?"
        const val SQL_CREATE_POST = "INSERT INTO posts (user_id, body) VALUES (?, ?) RETURNING id, created_at"
        const val SQL_LIKE_POST =
            "INSERT INTO likes (user_id, post_id) VALUES (?, ?) ON CONFLICT (user_id, post_id) DO NOTHING"


        @JvmStatic
        private fun postFromResultSet(rs: ResultSet): Post = Post(
            id = rs.getInt(1),
            body = rs.getString(2),
            createdAt = rs.getString(3),
            author = rs.getString(4),
            likeCount = rs.getInt(5)
        )
    }

    private val readerStatements = ConcurrentHashMap<SQLiteConnection, HashMap<String, PreparedStatement>>()

    private fun Connection.statement(sql: String): PreparedStatement {
        val physical = unwrap(SQLiteConnection::class.java)
        val cache = readerStatements.computeIfAbsent(physical) { HashMap() }
        val cached = cache[sql]
        if (cached != null && !cached.isClosed) return cached
        return physical.prepareStatement(sql).also { cache[sql] = it }
    }

    fun feed(): List<Post> = readers.connection.use { conn ->
        conn.statement(SQL_FEED).executeQuery().use { rs ->
            val posts = ArrayList<Post>(20)
            while (rs.next()) posts.add(postFromResultSet(rs))
            posts
        }
    }

    fun getPost(id: Int): Post? = readers.connection.use { conn ->
        val stmt = conn.statement(SQL_GET_POST)
        stmt.setInt(1, id)
        stmt.executeQuery().use { rs -> if (rs.next()) postFromResultSet(rs) else null }
    }

    fun createPost(userId: Int, body: String): CreatedPost = writer.submit { ctx ->
        val stmt = ctx.statement(SQL_CREATE_POST)
        stmt.setInt(1, userId)
        stmt.setString(2, body)
        stmt.executeQuery().use { rs ->
            if (!rs.next()) throw SQLException("insert returned no row")
            CreatedPost(rs.getInt(1), rs.getString(2))
        }
    }

    fun like(userId: Int, postId: Int): Int = writer.submit { ctx ->
        val stmt = ctx.statement(SQL_LIKE_POST)
        stmt.setInt(1, userId)
        stmt.setInt(2, postId)
        stmt.executeUpdate()
    }
}
