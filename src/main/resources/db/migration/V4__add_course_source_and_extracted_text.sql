ALTER TABLE course
    ADD COLUMN source_path TEXT NOT NULL,
    ADD COLUMN extracted_text TEXT NOT NULL;
