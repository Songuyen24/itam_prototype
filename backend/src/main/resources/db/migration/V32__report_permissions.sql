CREATE TABLE report_permissions (
 user_id BIGINT NOT NULL REFERENCES users(user_id),
 report_group VARCHAR(20) NOT NULL CHECK (report_group IN ('ASSETS','TRANSACTIONS','ALLOCATIONS')),
 can_view BOOLEAN NOT NULL DEFAULT FALSE,
 can_export BOOLEAN NOT NULL DEFAULT FALSE,
 PRIMARY KEY (user_id, report_group),
 CHECK (NOT can_export OR can_view)
);
