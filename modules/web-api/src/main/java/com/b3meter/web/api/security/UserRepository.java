/*
 * Copyright 2024-2026 b3meter Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.b3meter.web.api.security;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory repository for {@link UserEntity} and refresh-token records.
 *
 * <p>Data is held in memory for the lifetime of the application process.
 * This removes the need for any external database while keeping the auth API functional.
 */
@Repository
public class UserRepository {

    private final ConcurrentHashMap<String, UserEntity> usersById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UserEntity> usersByUsername = new ConcurrentHashMap<>();

    /** Maps token_hash -> RefreshTokenEntry(userId, expiresAt). */
    private final ConcurrentHashMap<String, RefreshTokenEntry> refreshTokens = new ConcurrentHashMap<>();

    /**
     * Finds a user by their unique username.
     *
     * @param username the username to look up
     * @return the user entity, or {@link Optional#empty()} if not found
     */
    public Optional<UserEntity> findByUsername(String username) {
        return Optional.ofNullable(usersByUsername.get(username));
    }

    /**
     * Finds a user by their primary-key identifier.
     *
     * @param id the user UUID
     * @return the user entity, or {@link Optional#empty()} if not found
     */
    public Optional<UserEntity> findById(String id) {
        return Optional.ofNullable(usersById.get(id));
    }

    /**
     * Persists a new user record.
     *
     * @param user the user entity to insert
     */
    public void save(UserEntity user) {
        UserEntity toStore = new UserEntity(
                user.id(),
                user.username(),
                user.email(),
                user.role(),
                user.passwordHash(),
                user.createdAt() != null ? user.createdAt() : Instant.now()
        );
        usersById.put(toStore.id(), toStore);
        usersByUsername.put(toStore.username(), toStore);
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
        refreshTokens.put(tokenHash, new RefreshTokenEntry(userId, expiresAt));
    }

    /**
     * Looks up the user that owns a given (non-expired) refresh token.
     *
     * @param tokenHash SHA-256 hex digest of the raw refresh token
     * @return the owning user's UUID, or {@link Optional#empty()} if the token
     *         is not found or has expired
     */
    public Optional<String> findUserIdByRefreshTokenHash(String tokenHash) {
        RefreshTokenEntry entry = refreshTokens.get(tokenHash);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(entry.userId());
    }

    /**
     * Deletes a specific refresh token (used on logout).
     *
     * @param tokenHash SHA-256 hex digest of the raw token to invalidate
     */
    public void deleteRefreshToken(String tokenHash) {
        refreshTokens.remove(tokenHash);
    }

    /**
     * Deletes all refresh tokens belonging to a user (full logout from all devices).
     *
     * @param userId the user's UUID
     */
    public void deleteAllRefreshTokensForUser(String userId) {
        refreshTokens.entrySet().removeIf(e -> userId.equals(e.getValue().userId()));
    }

    private record RefreshTokenEntry(String userId, Instant expiresAt) {}
}
