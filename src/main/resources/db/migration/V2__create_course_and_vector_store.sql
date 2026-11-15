CREATE TABLE course (
    id UUID PRIMARY KEY,
    file_name TEXT NOT NULL,
    subject VARCHAR(32) NOT NULL CHECK (subject IN (
        'MATHEMATICS',
        'FRENCH',
        'HISTORY_GEOGRAPHY',
        'PHYSICS_CHEMISTRY',
        'LIFE_AND_EARTH_SCIENCES',
        'TECHNOLOGY'
    )),
    imported_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL
);

CREATE TABLE vector_store (
    id UUID PRIMARY KEY,
    content TEXT,
    metadata JSON,
    embedding vector(1024)
);

CREATE INDEX vector_store_embedding_idx
    ON vector_store USING HNSW (embedding vector_cosine_ops);
