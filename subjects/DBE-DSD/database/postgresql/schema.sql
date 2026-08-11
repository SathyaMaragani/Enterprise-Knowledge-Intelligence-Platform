-- Enterprise Knowledge Intelligence Platform
-- Phase 1.1 PostgreSQL Database Schema

-- Drop tables if they exist to allow clean recreation during development
DROP TABLE IF EXISTS search_history CASCADE;
DROP TABLE IF EXISTS document_permissions CASCADE;
DROP TABLE IF EXISTS document_versions CASCADE;
DROP TABLE IF EXISTS document_tags CASCADE;
DROP TABLE IF EXISTS tags CASCADE;
DROP TABLE IF EXISTS documents CASCADE;
DROP TABLE IF EXISTS categories CASCADE;
DROP TABLE IF EXISTS role_permissions CASCADE;
DROP TABLE IF EXISTS user_roles CASCADE;
DROP TABLE IF EXISTS permissions CASCADE;
DROP TABLE IF EXISTS roles CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- 1. users
CREATE TABLE users (
    id SERIAL,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email)
);

-- 2. roles
CREATE TABLE roles (
    id SERIAL,
    name VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uq_roles_name UNIQUE (name)
);

-- 3. permissions
CREATE TABLE permissions (
    id SERIAL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    
    CONSTRAINT pk_permissions PRIMARY KEY (id),
    CONSTRAINT uq_permissions_name UNIQUE (name)
);

-- 4. user_roles
CREATE TABLE user_roles (
    user_id INTEGER NOT NULL,
    role_id INTEGER NOT NULL,
    
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

-- 5. role_permissions
CREATE TABLE role_permissions (
    role_id INTEGER NOT NULL,
    permission_id INTEGER NOT NULL,
    
    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);

-- 6. categories
CREATE TABLE categories (
    id SERIAL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT uq_categories_name UNIQUE (name)
);

-- 7. documents
CREATE TABLE documents (
    id SERIAL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    category_id INTEGER,
    owner_id INTEGER,
    storage_reference VARCHAR(255) NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT pk_documents PRIMARY KEY (id),
    CONSTRAINT fk_documents_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL,
    CONSTRAINT fk_documents_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT chk_documents_status CHECK (status IN ('UPLOADED', 'PROCESSING', 'INDEXED', 'FAILED', 'ARCHIVED'))
);

-- 8. tags
CREATE TABLE tags (
    id SERIAL,
    name VARCHAR(50) NOT NULL,
    
    CONSTRAINT pk_tags PRIMARY KEY (id),
    CONSTRAINT uq_tags_name UNIQUE (name)
);

-- 9. document_tags
CREATE TABLE document_tags (
    document_id INTEGER NOT NULL,
    tag_id INTEGER NOT NULL,
    
    CONSTRAINT pk_document_tags PRIMARY KEY (document_id, tag_id),
    CONSTRAINT fk_document_tags_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    CONSTRAINT fk_document_tags_tag FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE
);

-- 10. document_versions
CREATE TABLE document_versions (
    id SERIAL,
    document_id INTEGER NOT NULL,
    version_number INTEGER NOT NULL,
    storage_reference VARCHAR(255) NOT NULL,
    uploaded_by INTEGER,
    change_summary TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT pk_document_versions PRIMARY KEY (id),
    CONSTRAINT fk_document_versions_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    CONSTRAINT fk_document_versions_uploader FOREIGN KEY (uploaded_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT uq_document_versions_doc_ver UNIQUE (document_id, version_number)
);

-- 11. document_permissions
CREATE TABLE document_permissions (
    id SERIAL,
    document_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    permission_type VARCHAR(50) NOT NULL,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT pk_document_permissions PRIMARY KEY (id),
    CONSTRAINT fk_document_permissions_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    CONSTRAINT fk_document_permissions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_document_permissions_type CHECK (permission_type IN ('READ', 'WRITE', 'DELETE')),
    CONSTRAINT uq_document_permissions_doc_user_type UNIQUE (document_id, user_id, permission_type)
);

-- 12. search_history
CREATE TABLE search_history (
    id SERIAL,
    user_id INTEGER NOT NULL,
    query_text TEXT NOT NULL,
    search_type VARCHAR(50) NOT NULL,
    result_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT pk_search_history PRIMARY KEY (id),
    CONSTRAINT fk_search_history_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_search_history_type CHECK (search_type IN ('KEYWORD', 'FUZZY', 'SEMANTIC', 'TEXTHACK'))
);

-- Indexes for performance
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_documents_owner ON documents(owner_id);
CREATE INDEX idx_documents_category ON documents(category_id);
CREATE INDEX idx_documents_status ON documents(status);
CREATE INDEX idx_document_versions_doc ON document_versions(document_id);
CREATE INDEX idx_document_permissions_doc ON document_permissions(document_id);
CREATE INDEX idx_document_permissions_user ON document_permissions(user_id);
CREATE INDEX idx_search_history_user ON search_history(user_id);
CREATE INDEX idx_search_history_created ON search_history(created_at);
