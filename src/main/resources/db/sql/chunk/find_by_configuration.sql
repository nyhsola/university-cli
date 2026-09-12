SELECT chunkId, content
FROM chunk
WHERE indexConfigurationId = ?
ORDER BY chunkId
