package com.jmeternext.web.api.security;

import java.time.Instant;

/**
 * Immutable record representing a row in the {@code users} table.
 *
 * <p>The {@code passwordHash} field stores a BCrypt-hashed password. It is
 * intentionally absent from serialised representations (DTOs) and is only ever
 * used internally within the authentication layer.
 */
public record UserEntity(
        String id,
        String username,
        String email,
        String role,
        String passwordHash,
        Instant createdAt
) {}
