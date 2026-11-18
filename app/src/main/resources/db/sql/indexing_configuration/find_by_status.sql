SELECT id, documentId, hash, documentHash, status, createdAt
FROM indexing_configuration
WHERE status = ?
ORDER BY id
