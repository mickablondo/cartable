ALTER TABLE sheet DROP CONSTRAINT sheet_status_check;

ALTER TABLE sheet
    ADD CONSTRAINT sheet_status_check CHECK (status IN ('TO_VERIFY', 'VERIFIED'));
