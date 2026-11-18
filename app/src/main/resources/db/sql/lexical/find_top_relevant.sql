SELECT
    chunk.chunkId,
    chunk.indexConfigurationId,
    chunk_fts.rank AS bm25Score
FROM chunk_fts
JOIN chunk ON chunk.id = chunk_fts.rowid
WHERE chunk_fts MATCH ?
  AND chunk.indexConfigurationId = ?
ORDER BY chunk_fts.rank
LIMIT ?
