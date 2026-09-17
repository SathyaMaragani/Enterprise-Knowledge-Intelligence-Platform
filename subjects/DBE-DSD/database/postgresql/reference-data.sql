-- Reference data for a deployed environment: roles, permissions and categories.
-- Loaded after schema.sql. It contains no users and no documents; the first
-- administrator is created by the backend from EIP_BOOTSTRAPADMIN_* settings
-- (see docker/README.md). seed.sql, with its known test passwords, is for the
-- integration test stack only.
--
-- The values mirror seed.sql so deployed roles behave exactly as tested.
-- Idempotent, so it is safe to re-run against an existing database.

BEGIN;

INSERT INTO roles (name, description) VALUES
('ADMIN', 'System Administrator with full access'),
('MANAGER', 'Manager with departmental access'),
('EMPLOYEE', 'Standard employee access')
ON CONFLICT (name) DO NOTHING;

INSERT INTO permissions (name, description) VALUES
('DOCUMENT_CREATE', 'Can create new documents'),
('DOCUMENT_READ', 'Can read documents'),
('DOCUMENT_UPDATE', 'Can update existing documents'),
('DOCUMENT_DELETE', 'Can delete documents'),
('USER_MANAGE', 'Can manage users and roles'),
('ROLE_MANAGE', 'Can create and modify roles')
ON CONFLICT (name) DO NOTHING;

-- ADMIN -> all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;

-- MANAGER -> create, read, update
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'MANAGER' AND p.name IN ('DOCUMENT_CREATE', 'DOCUMENT_READ', 'DOCUMENT_UPDATE')
ON CONFLICT DO NOTHING;

-- EMPLOYEE -> read
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'EMPLOYEE' AND p.name = 'DOCUMENT_READ'
ON CONFLICT DO NOTHING;

INSERT INTO categories (name, description) VALUES
('HR', 'Human Resources documents and policies'),
('Finance', 'Financial reports and budgets'),
('Technical', 'Engineering and architecture designs'),
('Research', 'R&D papers and findings'),
('Legal', 'Contracts and compliance docs'),
('Administration', 'General admin records')
ON CONFLICT (name) DO NOTHING;

COMMIT;
