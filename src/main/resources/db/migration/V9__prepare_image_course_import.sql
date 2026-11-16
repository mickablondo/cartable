ALTER TABLE course
    ADD COLUMN requires_image_reading BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT course_status_check CHECK (status IN (
        'IMPORTED',
        'TEXT_EXTRACTION_IN_PROGRESS',
        'TEXT_TO_REVIEW',
        'VALIDATED',
        'INDEXED',
        'FAILED'
    ));

CREATE TABLE course_page (
    course_id UUID NOT NULL REFERENCES course (id) ON DELETE CASCADE,
    page_number INTEGER NOT NULL CHECK (page_number > 0),
    file_name TEXT NOT NULL,
    source_path TEXT NOT NULL,
    media_type VARCHAR(32) NOT NULL CHECK (media_type IN ('image/jpeg', 'image/png', 'image/webp')),
    PRIMARY KEY (course_id, page_number)
);
