SELECT id, documentId, hash, status, chunkFile, createdAt
FROM indexing_configuration
WHERE status = ?
ORDER BY id
