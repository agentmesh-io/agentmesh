-- V10 — M13.2: replace the V9 placeholder admin password hash with a real
-- BCrypt hash of "admin-change-me" (Spring's BCryptPasswordEncoder, cost 10).
--
-- Generated with:
--   htpasswd -bnBC 10 "" "admin-change-me" | tr -d ':\n'
--
-- Operators MUST rotate this default before exposing the platform.

UPDATE users
SET password_hash = '$2y$10$oVSWncORJxnqSheBZhIwA.ehrjbGnJq2zGbFIV5VgXmYCUUFkVCSu'
WHERE username = 'admin'
  AND tenant_id = '00000000-0000-0000-0000-000000000000'::uuid
  AND password_hash = '$2a$10$dxjvLMr4S8PGQMPvB.Qzn.tFVx1sUMzjs/TyV1N9WXQyWZsR9YyZi';

