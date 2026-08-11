-- Phase 1.1 PostgreSQL Database Common Queries

-- 1. List all documents with category and owner.
SELECT d.id, d.title, c.name AS category_name, u.username AS owner_name, d.status
FROM documents d
LEFT JOIN categories c ON d.category_id = c.id
LEFT JOIN users u ON d.owner_id = u.id
ORDER BY d.created_at DESC;

-- 2. Find documents belonging to a category (e.g., 'HR').
SELECT d.id, d.title, d.status
FROM documents d
JOIN categories c ON d.category_id = c.id
WHERE c.name = 'HR';

-- 3. Find documents by tag (e.g., 'Draft').
SELECT d.id, d.title, t.name AS tag_name
FROM documents d
JOIN document_tags dt ON d.id = dt.document_id
JOIN tags t ON dt.tag_id = t.id
WHERE t.name = 'Draft';

-- 4. List all roles of a user (e.g., 'alice_mgr').
SELECT r.name AS role_name, r.description
FROM roles r
JOIN user_roles ur ON r.id = ur.role_id
JOIN users u ON ur.user_id = u.id
WHERE u.username = 'alice_mgr';

-- 5. List permissions of a user through roles (e.g., 'alice_mgr').
SELECT DISTINCT p.name AS permission_name
FROM permissions p
JOIN role_permissions rp ON p.id = rp.permission_id
JOIN user_roles ur ON rp.role_id = ur.role_id
JOIN users u ON ur.user_id = u.id
WHERE u.username = 'alice_mgr';

-- 6. Find document versions for a specific document (e.g., doc ID 3).
SELECT v.version_number, v.change_summary, v.created_at, u.username AS uploaded_by
FROM document_versions v
JOIN users u ON v.uploaded_by = u.id
WHERE v.document_id = 3
ORDER BY v.version_number DESC;

-- 7. Find documents a particular user can access (e.g., via document_permissions or owner).
-- Assuming checking for 'alice_mgr'
SELECT d.id, d.title, 'OWNER' AS access_reason
FROM documents d
JOIN users u ON d.owner_id = u.id
WHERE u.username = 'alice_mgr'
UNION
SELECT d.id, d.title, dp.permission_type AS access_reason
FROM documents d
JOIN document_permissions dp ON d.id = dp.document_id
JOIN users u ON dp.user_id = u.id
WHERE u.username = 'alice_mgr';

-- 8. Search history for a user (e.g., 'admin_user').
SELECT query_text, search_type, result_count, created_at
FROM search_history
WHERE user_id = (SELECT id FROM users WHERE username = 'admin_user')
ORDER BY created_at DESC;
