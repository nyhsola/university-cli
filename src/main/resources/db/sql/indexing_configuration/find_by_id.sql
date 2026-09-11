SELECT id, documentId, hash, status, chunkFile, createdAt
FROM indexing_configuration
WHERE id = ?
