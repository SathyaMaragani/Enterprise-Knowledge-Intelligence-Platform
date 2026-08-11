-- Phase 1.1 PostgreSQL Database Reporting Queries

-- 9. Count documents by category.
SELECT c.name AS category_name, COUNT(d.id) AS document_count
FROM categories c
LEFT JOIN documents d ON c.id = d.category_id
GROUP BY c.id, c.name
ORDER BY document_count DESC;

-- 10. Count documents by status.
SELECT status, COUNT(id) AS document_count
FROM documents
GROUP BY status
ORDER BY document_count DESC;

-- 11. Find most frequently searched queries.
SELECT query_text, COUNT(*) AS search_count, SUM(result_count) AS total_results
FROM search_history
GROUP BY query_text
ORDER BY search_count DESC
LIMIT 10;

-- 12. Find recently uploaded documents.
SELECT id, title, created_at
FROM documents
WHERE status = 'UPLOADED'
ORDER BY created_at DESC
LIMIT 5;
