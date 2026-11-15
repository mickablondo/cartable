ALTER TABLE sheet
    ADD COLUMN general_knowledge JSONB NOT NULL DEFAULT '[]'::jsonb;
