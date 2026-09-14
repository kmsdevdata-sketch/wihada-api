ALTER TABLE inquiries
    ADD COLUMN current_order_form_submission_id UUID;

WITH latest_submission AS (
    SELECT DISTINCT ON (inquiry_id)
        inquiry_id,
        id
    FROM order_form_submissions
    ORDER BY inquiry_id, submitted_at DESC, id DESC
)
UPDATE inquiries inquiry
SET current_order_form_submission_id = latest_submission.id
FROM latest_submission
WHERE inquiry.id = latest_submission.inquiry_id;

ALTER TABLE inquiries
    ADD CONSTRAINT fk_inquiries_current_order_form_submission_id
        FOREIGN KEY (current_order_form_submission_id)
        REFERENCES order_form_submissions (id)
        ON DELETE SET NULL;

CREATE INDEX ix_inquiries_current_order_form_submission_id
    ON inquiries (current_order_form_submission_id);
