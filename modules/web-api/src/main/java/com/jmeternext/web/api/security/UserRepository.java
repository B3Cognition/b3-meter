package com.jmeternext.web.api.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JDBC repository for {@link UserEntity} and refresh-token records.
 *
 * <p>The {@code users} table schema is created by
 * {@code V004__create_users.sql}. The {@code password_hash} column is added
 * by {@code V006__add_password_hash.sql} so that the migration history stays
 * backward-compatible with existing test databases.
 */
@Repository
public class UserRepository {

    private static final String SELECT_BY_USERNAME = """
            SELECT id, username, email, role, password_hash, created_at
            FROM users
            WHERE username = ?
            """;

    private static final String SELECT_BY_ID = """
            SELECT id, username, email, role, password_hash, created_at
            FROM users
            WHERE id = ?
            """;

    private static final String INSERT_USER = """
            INSERT INTO users (id, username, email, role, password_hash, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_REFRESH_TOKEN = """
            INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at, created_at)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String SELECT_REFRESH_TOKEN = """
            SELECT user_id FROM refresh_tokens
            WHERE token_hash = ? AND expires_at > CURRENT_TIMESTAMP
            """;

    private static final String DELETE_REFRESH_TOKEN = """
            DELETE FROM refresh_tokens WHERE token_hash = ?
            """;

    private static final String DELETE_ALL_REFRESH_TOKENS_FOR_USER = """
            DELETE FROM refresh_tokens WHERE user_id = ?
            """;

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Finds a user by their unique username.
     *
     * @param username the username to look up
     * @return the user entity, or {@link Optional#empty()} if not found
     */
    public Optional<UserEntity> findByUsername(String username) {
        List<UserEntity> rows = jdbc.query(SELECT_BY_USERNAME, USER_ROW_MAPPER, username);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Finds a user by their primary-key identifier.
     *
     * @param id the user UUID
     * @return the user entity, or {@link Optional#empty()} if not found
     */
    public Optional<UserEntity> findById(String id) {
        List<UserEntity> rows = jdbc.query(SELECT_BY_ID, USER_ROW_MAPPER, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Persists a new user record.
     *
     * @param user the user entity to insert
     */
    public void save(UserEntity user) {
        Instant createdAt = user.createdAt() != null ? user.createdAt() : Instant.now();
        jdbc.update(INSERT_USER,
                user.id(),
                user.username(),
                user.email(),
                user.role(),
                user.passwordHash(),
                Timestamp.from(createdAt));
    }

    /**
     * Stores a hashed refresh token linked to a user.
     *
     * @param id        the refresh-token primary key (UUID)
     * @param userId    the owning user's UUID
     * @param tokenHash SHA-256 hex digest of the raw refresh token
     * @param expiresAt expiry timestamp
     */
    public void saveRefreshToken(String id, String userId, String tokenHash, Instant expiresAt) {
        jdbc.update(INSERT_REFRESH_TOKEN,
                id, userId, tokenHash, Timestamp.from(expiresAt), Timestamp.from(Instant.now()));
    }

    /**
     * Looks up the user that owns a given (non-expired) refresh token.
     *
     * @param tokenHash SHA-256 hex digest of the raw refresh token
     * @return the owning user's UUID, or {@link Optional#empty()} if the token
     *         is not found or has expired
     */
    public Optional<String> findUserIdByRefreshTokenHash(String tokenHash) {
        List<String> rows = jdbc.query(SELECT_REFRESH_TOKEN,
                (rs, n) -> rs.getString("user_id"), tokenHash);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Deletes a specific refresh token (used on logout).
     *
     * @param tokenHash SHA-256 hex digest of the raw token to invalidate
     */
    public void deleteRefreshToken(String tokenHash) {
        jdbc.update(DELETE_REFRESH_TOKEN, tokenHash);
    }

    /**
     * Deletes all refresh tokens belonging to a user (full logout from all devices).
     *
     * @param userId the user's UUID
     */
    public void deleteAllRefreshTokensForUser(String userId) {
        jdbc.update(DELETE_ALL_REFRESH_TOKENS_FOR_USER, userId);
    }

    // -------------------------------------------------------------------------
    // Row mapper
    // -------------------------------------------------------------------------

    private static final RowMapper<UserEntity> USER_ROW_MAPPER = new RowMapper<>() {
        @Override
        public UserEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp createdAt = rs.getTimestamp("created_at");
            return new UserEntity(
                    rs.getString("id"),
                    rs.getString("username"),
                    rs.getString("email"),
                    rs.getString("role"),
                    rs.getString("password_hash"),
                    createdAt != null ? createdAt.toInstant() : null
            );
        }
    };
}
