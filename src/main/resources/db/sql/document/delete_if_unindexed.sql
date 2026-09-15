DELETE FROM document
WHERE fileName = ?
AND NOT EXISTS (
    SELECT 1
    FROM indexing_configuration
    WHERE documentId = document.id
)
