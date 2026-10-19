package com.lycosp.post

import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.InternalServerErrorException
import jakarta.ws.rs.NotFoundException
import org.sqlite.SQLiteErrorCode
import org.sqlite.SQLiteException
import com.lycosp.post.dao.PostDAO
import java.sql.ResultSet
import java.sql.SQLException
import javax.sql.DataSource

@ApplicationScoped
class PostRepository(
    private val datasource: DataSource
) {

    private companion object {
        const val SQL_FEED_BASE = """
            SELECT
                p.id,
                p.body,
                p.created_at,
                u.username,
                (SELECT COUNT(*) FROM likes l WHERE l.post_id = p.id) AS like_count
            FROM posts p
            JOIN users u ON u.id = p.user_id
        """

        const val SQL_FEED = """
            $SQL_FEED_BASE
            ORDER BY
                p.created_at DESC,
                p.id DESC
            LIMIT 20
        """

        const val SQL_GET_POST = """
            $SQL_FEED_BASE
            WHERE p.id = ?
        """

        const val SQL_CREATE_POST = """
            INSERT INTO posts (user_id, body)
            VALUES (?, ?)
            RETURNING id, created_at
        """

        const val SQL_LIKE_POST = """
            INSERT INTO likes (user_id, post_id)
            VALUES (?, ?)
            ON CONFLICT (user_id, post_id) DO NOTHING
        """

        private fun postFromResult(rs: ResultSet): PostDAO {
            return PostDAO(
                id = rs.getInt(1),
                body = rs.getString(2),
                createdAt = rs.getString(3),
                author = rs.getString(4),
                likes = rs.getInt(5)
            )
        }
    }

    fun feed(): List<PostDAO> {
        try {
            datasource.connection.use { conn ->
                conn.prepareStatement(SQL_FEED).executeQuery().use { rs ->
                    val posts = ArrayList<PostDAO>()
                    while (rs.next()) {
                        posts.add(postFromResult(rs))
                    }
                    return posts
                }
            }
        } catch (e: SQLException) {
            throw InternalServerErrorException(e)
        }
    }

    fun get(postId: Int): PostDAO? {
        try {
            datasource.connection.use { conn ->
                conn.prepareStatement(SQL_GET_POST).apply {
                    setInt(1, postId)
                    executeQuery().use { rs ->
                        return if (rs.next())
                            postFromResult(rs)
                        else null
                    }
                }
            }
        } catch (e: SQLException) {
            throw InternalServerErrorException(e)
        }
    }

    fun create(userId: Int, username: String, body: String): PostDAO {
        try {
            datasource.connection.use { conn ->
                conn.prepareStatement(SQL_CREATE_POST).apply {
                    setInt(1, userId)
                    setString(2, body)
                    executeQuery().use { rs ->
                        return if (rs.next())
                            PostDAO(
                                id = rs.getInt(1),
                                body = body,
                                createdAt = rs.getString(2),
                                author = username,
                                likes = 0
                            )
                        else throw InternalServerErrorException()
                    }
                }
            }
        } catch (e: SQLException) {
            throw InternalServerErrorException(e)
        }
    }

    /**
     * Creates a new like record in the `likes` table.
     *
     * @param userId The id of the user liking the post.
     * @param postId The id of the post to like.
     * @return True if a new like record was created.
     *         False if no new like record was created (already liked).
     */
    fun like(userId: Int, postId: Int): Boolean {
        try {
            datasource.connection.use { conn ->
                conn.prepareStatement(SQL_LIKE_POST).apply {
                    setInt(1, userId)
                    setInt(2, postId)
                    return executeUpdate() == 1
                }
            }
        } catch (e: SQLException) {
            if (isForeignKeyViolation(e)) throw NotFoundException(e)
            throw InternalServerErrorException(e)
        }
    }

    private fun isForeignKeyViolation(e: SQLException): Boolean =
        (e as SQLiteException).resultCode == SQLiteErrorCode.SQLITE_CONSTRAINT_FOREIGNKEY
}
