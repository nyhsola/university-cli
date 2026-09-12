CREATE TABLE document (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    fileName TEXT NOT NULL UNIQUE,
    fileHash TEXT NOT NULL
);

CREATE TABLE indexing_configuration (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    documentId INTEGER NOT NULL,
    hash TEXT NOT NULL,
    documentHash TEXT NOT NULL,
    status INTEGER NOT NULL,
    createdAt TEXT NOT NULL,
    UNIQUE (documentId, hash),
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

CREATE TABLE chunk (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    indexConfigurationId INTEGER NOT NULL,
    chunkId INTEGER NOT NULL,
    content TEXT NOT NULL,
    UNIQUE (indexConfigurationId, chunkId),
    FOREIGN KEY (indexConfigurationId) REFERENCES indexing_configuration(id) ON DELETE CASCADE
);

CREATE INDEX idx_chunk_index_configuration_id
    ON chunk(indexConfigurationId);

CREATE VIRTUAL TABLE chunk_fts USING fts5(
    content,
    content = 'chunk',
    content_rowid = 'id',
    tokenize = 'unicode61'
);

CREATE TRIGGER chunk_after_insert AFTER INSERT ON chunk BEGIN
    INSERT INTO chunk_fts(rowid, content)
    VALUES (new.id, new.content);
END;

CREATE TRIGGER chunk_after_delete AFTER DELETE ON chunk BEGIN
    INSERT INTO chunk_fts(chunk_fts, rowid, content)
    VALUES ('delete', old.id, old.content);
END;

CREATE TRIGGER chunk_after_update AFTER UPDATE ON chunk BEGIN
    INSERT INTO chunk_fts(chunk_fts, rowid, content)
    VALUES ('delete', old.id, old.content);
    INSERT INTO chunk_fts(rowid, content)
    VALUES (new.id, new.content);
END;
