-- Phase 1.1 PostgreSQL Database Schema Tests
-- Run these after schema.sql and seed.sql to verify constraints

-- 1. Test UNIQUE constraint on user email (Should Fail)
BEGIN;
INSERT INTO users (username, email, password_hash, full_name) VALUES ('test_fail', 'admin@example.com', 'hash', 'Test Fail');
ROLLBACK;

-- 2. Test CHECK constraint on document status (Should Fail)
BEGIN;
INSERT INTO documents (title, storage_reference, document_type, status) VALUES ('Bad Status Doc', 'ref', 'PDF', 'UNKNOWN_STATUS');
ROLLBACK;

-- 3. Test FOREIGN KEY constraints (Should Fail - non-existent user)
BEGIN;
INSERT INTO user_roles (user_id, role_id) VALUES (999, 1);
ROLLBACK;

-- 4. Test ON DELETE CASCADE for users -> search_history (Should pass and delete search history)
BEGIN;
DELETE FROM users WHERE username = 'dave_tmp';
SELECT * FROM search_history WHERE user_id = (SELECT id FROM users WHERE username = 'dave_tmp'); -- Should return 0 rows
ROLLBACK;

-- 5. Test ON DELETE SET NULL for users -> documents (Should pass and set owner_id to NULL)
BEGIN;
DELETE FROM users WHERE username = 'charlie_hr';
SELECT owner_id FROM documents WHERE title = 'Employee Handbook 2026'; -- Should return NULL
ROLLBACK;

-- 6. Test UNIQUE constraint on document_versions (Should Fail)
BEGIN;
INSERT INTO document_versions (document_id, version_number, storage_reference) VALUES (3, 1, 'duplicate');
ROLLBACK;

-- 7. Test CHECK constraint on search type (Should Fail)
BEGIN;
INSERT INTO search_history (user_id, query_text, search_type) VALUES (1, 'test', 'INVALID');
ROLLBACK;
