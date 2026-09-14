CREATE TABLE seller_email_notification_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_user_id UUID NOT NULL,
    inquiry_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_seller_email_notification_logs_seller_user_id FOREIGN KEY (seller_user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_seller_email_notification_logs_inquiry_id FOREIGN KEY (inquiry_id) REFERENCES inquiries (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uk_seller_email_notification_logs_inquiry_type
    ON seller_email_notification_logs (inquiry_id, type);

CREATE INDEX ix_seller_email_notification_logs_seller_user_id_sent_at
    ON seller_email_notification_logs (seller_user_id, sent_at DESC);
