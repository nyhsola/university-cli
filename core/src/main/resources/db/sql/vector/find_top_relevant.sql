SELECT
    id,
    indexConfigurationId,
    vector,
    vec_distance_cosine(vector, ?) AS distance
FROM vector
WHERE indexConfigurationId = ?
ORDER BY distance
LIMIT ?
