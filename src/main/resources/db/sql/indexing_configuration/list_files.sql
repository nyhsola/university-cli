SELECT
    configuration.id AS configurationId,
    document.id AS documentId,
    document.fileName,
    configuration.status
FROM indexing_configuration configuration
JOIN document ON document.id = configuration.documentId
ORDER BY configuration.id
