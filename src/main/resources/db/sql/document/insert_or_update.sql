INSERT INTO document(fileName, fileHash)
VALUES (?, ?)
ON CONFLICT(fileName) DO UPDATE SET fileHash = excluded.fileHash
