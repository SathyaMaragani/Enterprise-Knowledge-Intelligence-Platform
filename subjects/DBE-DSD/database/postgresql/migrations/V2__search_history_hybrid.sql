-- V2: record hybrid searches.
-- Search history is written for every first-page text search, and HYBRID
-- (keyword and semantic fused) is the default search mode, which V1's CHECK
-- did not allow. schema.sql already includes this change for new databases.

ALTER TABLE search_history DROP CONSTRAINT chk_search_history_type;
ALTER TABLE search_history ADD CONSTRAINT chk_search_history_type
    CHECK (search_type IN ('HYBRID', 'KEYWORD', 'FUZZY', 'SEMANTIC', 'TEXTHACK'));
