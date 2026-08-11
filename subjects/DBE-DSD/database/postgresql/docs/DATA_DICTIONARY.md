# PostgreSQL Data Dictionary

This document details the schema design for Phase 1.1 of the Enterprise Knowledge Intelligence Platform.

## 1. users
Stores authentication and basic profile data for platform users.
| Column | Type | Nullable | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | SERIAL | No | Nextval | PK | Primary Key |
| username | VARCHAR(50) | No | | UNIQUE | Unique username for login |
| email | VARCHAR(255) | No | | UNIQUE | Unique email address |
| password_hash | VARCHAR(255) | No | | | Bcrypt/Argon2 hashed password |
| full_name | VARCHAR(255) | No | | | User's full display name |
| is_active | BOOLEAN | No | TRUE | | Whether the account is active |
| created_at | TIMESTAMP | No | CURRENT_TIMESTAMP | | Account creation time |
| updated_at | TIMESTAMP | No | CURRENT_TIMESTAMP | | Last update time |

## 2. roles
Defines coarse-grained roles within the system.
| Column | Type | Nullable | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | SERIAL | No | Nextval | PK | Primary Key |
| name | VARCHAR(50) | No | | UNIQUE | Role name (e.g., ADMIN, MANAGER) |
| description | TEXT | Yes | | | Description of the role |
| created_at | TIMESTAMP | No | CURRENT_TIMESTAMP | | Role creation time |

## 3. permissions
Defines fine-grained actions users can perform.
| Column | Type | Nullable | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | SERIAL | No | Nextval | PK | Primary Key |
| name | VARCHAR(100) | No | | UNIQUE | Permission string (e.g., DOCUMENT_CREATE) |
| description | TEXT | Yes | | | Description of the permission |

## 4. user_roles
Junction table mapping users to multiple roles (Many-to-Many).
| Column | Type | Nullable | Default | Constraints | Description |
|---|---|---|---|---|---|
| user_id | INTEGER | No | | PK, FK | References `users(id) ON DELETE CASCADE` |
| role_id | INTEGER | No | | PK, FK | References `roles(id) ON DELETE CASCADE` |

## 5. role_permissions
Junction table mapping roles to multiple permissions (Many-to-Many).
| Column | Type | Nullable | Default | Constraints | Description |
|---|---|---|---|---|---|
| role_id | INTEGER | No | | PK, FK | References `roles(id) ON DELETE CASCADE` |
| permission_id | INTEGER | No | | PK, FK | References `permissions(id) ON DELETE CASCADE` |

## 6. categories
Organizational categories for documents.
| Column | Type | Nullable | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | SERIAL | No | Nextval | PK | Primary Key |
| name | VARCHAR(100) | No | | UNIQUE | Category name (e.g., HR, Finance) |
| description | TEXT | Yes | | | Category description |
| created_at | TIMESTAMP | No | CURRENT_TIMESTAMP | | Category creation time |

## 7. documents
Metadata for documents (Binary content stored in MongoDB).
| Column | Type | Nullable | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | SERIAL | No | Nextval | PK | Primary Key |
| title | VARCHAR(255) | No | | | Title of the document |
| description | TEXT | Yes | | | Brief description |
| category_id | INTEGER | Yes | | FK | References `categories(id) ON DELETE SET NULL` |
| owner_id | INTEGER | Yes | | FK | References `users(id) ON DELETE SET NULL` |
| storage_reference | VARCHAR(255) | No | | | Identifier for fetching file from MongoDB |
| document_type | VARCHAR(50) | No | | | MIME type or extension (e.g., PDF) |
| status | VARCHAR(50) | No | | CHECK | In: UPLOADED, PROCESSING, INDEXED, FAILED, ARCHIVED |
| created_at | TIMESTAMP | No | CURRENT_TIMESTAMP | | Upload time |
| updated_at | TIMESTAMP | No | CURRENT_TIMESTAMP | | Last metadata update time |

## 8. tags
Arbitrary text tags that can be applied to documents.
| Column | Type | Nullable | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | SERIAL | No | Nextval | PK | Primary Key |
| name | VARCHAR(50) | No | | UNIQUE | Tag name |

## 9. document_tags
Junction table mapping documents to tags (Many-to-Many).
| Column | Type | Nullable | Default | Constraints | Description |
|---|---|---|---|---|---|
| document_id | INTEGER | No | | PK, FK | References `documents(id) ON DELETE CASCADE` |
| tag_id | INTEGER | No | | PK, FK | References `tags(id) ON DELETE CASCADE` |

## 10. document_versions
Tracks versions of a single document to allow rollback and history.
| Column | Type | Nullable | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | SERIAL | No | Nextval | PK | Primary Key |
| document_id | INTEGER | No | | FK, UNIQUE(doc, ver) | References `documents(id) ON DELETE CASCADE` |
| version_number | INTEGER | No | | UNIQUE(doc, ver) | The version sequence (1, 2, 3) |
| storage_reference | VARCHAR(255) | No | | | Storage ref for this specific version |
| uploaded_by | INTEGER | Yes | | FK | References `users(id) ON DELETE SET NULL` |
| change_summary | TEXT | Yes | | | What changed in this version |
| created_at | TIMESTAMP | No | CURRENT_TIMESTAMP | | Version upload time |

## 11. document_permissions
Document-level overrides allowing specific users to access specific documents.
| Column | Type | Nullable | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | SERIAL | No | Nextval | PK | Primary Key |
| document_id | INTEGER | No | | FK | References `documents(id) ON DELETE CASCADE` |
| user_id | INTEGER | No | | FK | References `users(id) ON DELETE CASCADE` |
| permission_type | VARCHAR(50) | No | | CHECK | In: READ, WRITE, DELETE |
| granted_at | TIMESTAMP | No | CURRENT_TIMESTAMP | | Time access was granted |

*(Note: There is a UNIQUE constraint on document_id, user_id, permission_type)*

## 12. search_history
Audit trail of user searches for analytics and ML pipeline feeding.
| Column | Type | Nullable | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | SERIAL | No | Nextval | PK | Primary Key |
| user_id | INTEGER | No | | FK | References `users(id) ON DELETE CASCADE` |
| query_text | TEXT | No | | | The search string entered |
| search_type | VARCHAR(50) | No | | CHECK | In: KEYWORD, FUZZY, SEMANTIC, TEXTHACK |
| result_count | INTEGER | No | 0 | | Number of results returned |
| created_at | TIMESTAMP | No | CURRENT_TIMESTAMP | | Time of search |
