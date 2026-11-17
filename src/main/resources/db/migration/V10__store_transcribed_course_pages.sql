ALTER TABLE course
    ADD COLUMN failure_message TEXT;

ALTER TABLE course_page
    ADD COLUMN extracted_text TEXT,
    ADD COLUMN rendered_from_pdf BOOLEAN NOT NULL DEFAULT FALSE;
