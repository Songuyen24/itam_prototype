CREATE TABLE disposal_deadline_notices (
    transaction_id BIGINT NOT NULL REFERENCES transactions(transaction_id),
    group_id TEXT NOT NULL,
    due_date DATE NOT NULL,
    recipient TEXT NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (transaction_id,group_id,due_date,recipient)
);
