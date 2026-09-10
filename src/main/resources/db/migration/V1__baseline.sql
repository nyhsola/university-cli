CREATE TABLE document (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    fileName TEXT NOT NULL,
    fileHash TEXT NOT NULL UNIQUE
);

CREATE TABLE indexing_configuration (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    documentId INTEGER NOT NULL,
    embeddingModel TEXT NOT NULL,
    strategy INTEGER NOT NULL,
    status INTEGER NOT NULL,
    parameters TEXT NOT NULL,
    hash TEXT NOT NULL UNIQUE,
    chunkFile TEXT,
    createdAt TEXT NOT NULL,
    FOREIGN KEY (documentId) REFERENCES document(id) ON DELETE CASCADE
);

CREATE INDEX idx_indexing_configuration_document_id
    ON indexing_configuration(documentId);

CREATE TABLE vector (
    id INTEGER NOT NULL,
    indexConfigurationId INTEGER NOT NULL,
    vector BLOB NOT NULL CHECK (
        typeof(vector) = 'blob'
        AND length(vector) > 0
        AND length(vector) % 4 = 0
    ),
    PRIMARY KEY (indexConfigurationId, id),
    FOREIGN KEY (indexConfigurationId) REFERENCES indexing_configuration(id) ON DELETE CASCADE
);

CREATE INDEX idx_vector_index_configuration_id
    ON vector(indexConfigurationId);
