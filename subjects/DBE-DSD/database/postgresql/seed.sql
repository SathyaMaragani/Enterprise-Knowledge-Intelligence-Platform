-- Phase 1.1 PostgreSQL Database Seed Data
-- Fictional development data

-- 1. Users
INSERT INTO users (username, email, password_hash, full_name) VALUES
('admin_user', 'admin@example.com', 'placeholder_hash_1', 'Admin Istrator'),
('alice_mgr', 'alice@example.com', 'placeholder_hash_2', 'Alice Manager'),
('bob_eng', 'bob@example.com', 'placeholder_hash_3', 'Bob Engineer'),
('charlie_hr', 'charlie@example.com', 'placeholder_hash_4', 'Charlie HR'),
('dave_tmp', 'dave@example.com', 'placeholder_hash_5', 'Dave Temp');

-- 2. Roles
INSERT INTO roles (name, description) VALUES
('ADMIN', 'System Administrator with full access'),
('MANAGER', 'Manager with departmental access'),
('EMPLOYEE', 'Standard employee access');

-- 3. Permissions
INSERT INTO permissions (name, description) VALUES
('DOCUMENT_CREATE', 'Can create new documents'),
('DOCUMENT_READ', 'Can read documents'),
('DOCUMENT_UPDATE', 'Can update existing documents'),
('DOCUMENT_DELETE', 'Can delete documents'),
('USER_MANAGE', 'Can manage users and roles'),
('ROLE_MANAGE', 'Can create and modify roles');

-- 4. User Roles (Assign roles to users)
-- admin -> ADMIN
-- alice -> MANAGER
-- bob -> EMPLOYEE
-- charlie -> EMPLOYEE
-- dave -> EMPLOYEE
INSERT INTO user_roles (user_id, role_id) VALUES
((SELECT id FROM users WHERE username = 'admin_user'), (SELECT id FROM roles WHERE name = 'ADMIN')),
((SELECT id FROM users WHERE username = 'alice_mgr'), (SELECT id FROM roles WHERE name = 'MANAGER')),
((SELECT id FROM users WHERE username = 'bob_eng'), (SELECT id FROM roles WHERE name = 'EMPLOYEE')),
((SELECT id FROM users WHERE username = 'charlie_hr'), (SELECT id FROM roles WHERE name = 'EMPLOYEE')),
((SELECT id FROM users WHERE username = 'dave_tmp'), (SELECT id FROM roles WHERE name = 'EMPLOYEE'));

-- 5. Role Permissions
-- ADMIN -> ALL
-- MANAGER -> CREATE, READ, UPDATE
-- EMPLOYEE -> READ
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'ADMIN';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'MANAGER' AND p.name IN ('DOCUMENT_CREATE', 'DOCUMENT_READ', 'DOCUMENT_UPDATE');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'EMPLOYEE' AND p.name = 'DOCUMENT_READ';

-- 6. Categories
INSERT INTO categories (name, description) VALUES
('HR', 'Human Resources documents and policies'),
('Finance', 'Financial reports and budgets'),
('Technical', 'Engineering and architecture designs'),
('Research', 'R&D papers and findings'),
('Legal', 'Contracts and compliance docs'),
('Administration', 'General admin records');

-- 7. Tags
INSERT INTO tags (name) VALUES
('Policy'), ('Report'), ('Draft'), ('Final'), ('Confidential'),
('Public'), ('Architecture'), ('Q1'), ('Q2'), ('Compliance');

-- 8. Documents
INSERT INTO documents (title, description, category_id, owner_id, storage_reference, document_type, status) VALUES
('Employee Handbook 2026', 'Updated HR handbook', (SELECT id FROM categories WHERE name = 'HR'), (SELECT id FROM users WHERE username = 'charlie_hr'), 'mongo_ref_1001', 'PDF', 'INDEXED'),
('Q1 Financial Report', 'Q1 results', (SELECT id FROM categories WHERE name = 'Finance'), (SELECT id FROM users WHERE username = 'alice_mgr'), 'mongo_ref_1002', 'XLSX', 'INDEXED'),
('System Architecture v2', 'New microservices design', (SELECT id FROM categories WHERE name = 'Technical'), (SELECT id FROM users WHERE username = 'bob_eng'), 'mongo_ref_1003', 'MD', 'INDEXED'),
('AI Research Paper', 'Deep learning findings', (SELECT id FROM categories WHERE name = 'Research'), (SELECT id FROM users WHERE username = 'bob_eng'), 'mongo_ref_1004', 'PDF', 'PROCESSING'),
('Vendor Contract A', 'Software vendor agreement', (SELECT id FROM categories WHERE name = 'Legal'), (SELECT id FROM users WHERE username = 'admin_user'), 'mongo_ref_1005', 'PDF', 'UPLOADED'),
('Office Layout Plan', 'New floor plan', (SELECT id FROM categories WHERE name = 'Administration'), (SELECT id FROM users WHERE username = 'alice_mgr'), 'mongo_ref_1006', 'PDF', 'ARCHIVED'),
('Q2 Budget Draft', 'Draft for next quarter', (SELECT id FROM categories WHERE name = 'Finance'), (SELECT id FROM users WHERE username = 'alice_mgr'), 'mongo_ref_1007', 'XLSX', 'INDEXED'),
('Code Guidelines', 'Coding standards', (SELECT id FROM categories WHERE name = 'Technical'), (SELECT id FROM users WHERE username = 'bob_eng'), 'mongo_ref_1008', 'MD', 'INDEXED'),
('Leave Policy Update', 'Changes to leave policy', (SELECT id FROM categories WHERE name = 'HR'), (SELECT id FROM users WHERE username = 'charlie_hr'), 'mongo_ref_1009', 'PDF', 'FAILED'),
('NDA Template', 'Standard NDA', (SELECT id FROM categories WHERE name = 'Legal'), (SELECT id FROM users WHERE username = 'admin_user'), 'mongo_ref_1010', 'DOCX', 'INDEXED');

-- 9. Document Tags
INSERT INTO document_tags (document_id, tag_id) VALUES
(1, (SELECT id FROM tags WHERE name = 'Policy')),
(1, (SELECT id FROM tags WHERE name = 'Final')),
(2, (SELECT id FROM tags WHERE name = 'Report')),
(2, (SELECT id FROM tags WHERE name = 'Q1')),
(3, (SELECT id FROM tags WHERE name = 'Architecture')),
(3, (SELECT id FROM tags WHERE name = 'Draft')),
(4, (SELECT id FROM tags WHERE name = 'Confidential')),
(5, (SELECT id FROM tags WHERE name = 'Compliance')),
(6, (SELECT id FROM tags WHERE name = 'Public')),
(7, (SELECT id FROM tags WHERE name = 'Draft')),
(7, (SELECT id FROM tags WHERE name = 'Q2')),
(8, (SELECT id FROM tags WHERE name = 'Policy'));

-- 10. Document Versions
INSERT INTO document_versions (document_id, version_number, storage_reference, uploaded_by, change_summary) VALUES
(3, 1, 'mongo_ref_1003_v1', (SELECT id FROM users WHERE username = 'bob_eng'), 'Initial draft'),
(3, 2, 'mongo_ref_1003_v2', (SELECT id FROM users WHERE username = 'bob_eng'), 'Added API gateway details'),
(7, 1, 'mongo_ref_1007_v1', (SELECT id FROM users WHERE username = 'alice_mgr'), 'Initial Q2 numbers');

-- 11. Document Permissions (Custom document level overrides)
INSERT INTO document_permissions (document_id, user_id, permission_type) VALUES
(4, (SELECT id FROM users WHERE username = 'alice_mgr'), 'READ'),
(4, (SELECT id FROM users WHERE username = 'admin_user'), 'WRITE'),
(5, (SELECT id FROM users WHERE username = 'alice_mgr'), 'READ');

-- 12. Search History
INSERT INTO search_history (user_id, query_text, search_type, result_count) VALUES
((SELECT id FROM users WHERE username = 'alice_mgr'), 'financial report Q1', 'KEYWORD', 1),
((SELECT id FROM users WHERE username = 'bob_eng'), 'architecture diagram', 'SEMANTIC', 3),
((SELECT id FROM users WHERE username = 'charlie_hr'), 'leave policy', 'FUZZY', 5),
((SELECT id FROM users WHERE username = 'admin_user'), 'NDA', 'TEXTHACK', 10),
((SELECT id FROM users WHERE username = 'dave_tmp'), 'handbook', 'KEYWORD', 1);
