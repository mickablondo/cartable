CREATE TABLE reference_document (
    id UUID PRIMARY KEY,
    source_url TEXT NOT NULL UNIQUE,
    source_file TEXT NOT NULL,
    title TEXT NOT NULL,
    retrieved_at DATE NOT NULL,
    license TEXT NOT NULL,
    subject VARCHAR(32) NOT NULL CHECK (subject IN (
        'MATHEMATICS',
        'FRENCH',
        'HISTORY_GEOGRAPHY',
        'PHYSICS_CHEMISTRY',
        'LIFE_AND_EARTH_SCIENCES',
        'TECHNOLOGY'
    )),
    publication_date DATE NOT NULL,
    content_hash CHAR(64) NOT NULL,
    indexed_chunks INTEGER NOT NULL CHECK (indexed_chunks > 0),
    status VARCHAR(32) NOT NULL
);
