ALTER TABLE sheet
    ADD COLUMN sheet_type VARCHAR(64) NOT NULL DEFAULT 'GENERIC',
    ADD COLUMN schema_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN variant_data JSONB;

UPDATE sheet
SET variant_data = jsonb_build_object(
    'definitions', definitions,
    'landmarks', landmarks
);

ALTER TABLE sheet
    ALTER COLUMN variant_data SET DEFAULT '{"definitions":[],"landmarks":[]}'::jsonb,
    ALTER COLUMN variant_data SET NOT NULL;
