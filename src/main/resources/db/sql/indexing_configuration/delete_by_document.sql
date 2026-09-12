DELETE FROM indexing_configuration
WHERE documentId = (
    SELECT id
    FROM document
    WHERE fileName = ?
)
AND (? IS NULL OR hash = ?)
