-- 检查 REPLICA IDENTITY（修正版）
SELECT
    t.table_name,
    c.relreplident,
    CASE c.relreplident
        WHEN 'd' THEN 'Default'
        WHEN 'n' THEN 'Nothing'
        WHEN 'f' THEN 'Full'
        WHEN 'i' THEN 'Index'
        ELSE 'Unknown'
    END AS replica_identity
FROM information_schema.tables t
JOIN pg_class c ON c.relname = t.table_name
WHERE t.table_schema = 'public'
  AND t.table_name IN ('part', 'document', 'document_master', 'folder', 'part_master', 'product', 'contains_link', 'iteraite_link')
ORDER BY t.table_name;