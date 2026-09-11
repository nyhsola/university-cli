INSERT INTO document(fileName, fileHash)
VALUES (?, ?)
ON CONFLICT(fileHash) DO UPDATE SET fileName = excluded.fileName
