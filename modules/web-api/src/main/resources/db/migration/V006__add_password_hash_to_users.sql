-- Add password_hash column to users table for JWT/BCrypt authentication.
-- This column is required by the multi-user auth layer (T038).
-- The column is nullable so existing rows (e.g. OAuth-only users) are not broken.
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(72);
