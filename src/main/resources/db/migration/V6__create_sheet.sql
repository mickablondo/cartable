CREATE TABLE sheet (
    id UUID PRIMARY KEY,
    subject VARCHAR(32) NOT NULL CHECK (subject IN (
        'MATHEMATICS',
        'FRENCH',
        'HISTORY_GEOGRAPHY',
        'PHYSICS_CHEMISTRY',
        'LIFE_AND_EARTH_SCIENCES',
        'TECHNOLOGY'
    )),
    theme TEXT NOT NULL,
    key_concepts JSONB NOT NULL,
    definitions JSONB NOT NULL,
    landmarks JSONB NOT NULL,
    limitations JSONB NOT NULL,
    sources JSONB NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'TO_VERIFY' CHECK (status = 'TO_VERIFY'),
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
