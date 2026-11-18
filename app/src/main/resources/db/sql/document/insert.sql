INSERT INTO document(fileName)
VALUES (?)
ON CONFLICT(fileName) DO NOTHING
