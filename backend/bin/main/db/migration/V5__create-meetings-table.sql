CREATE TABLE meeting (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    grp_id          UUID NOT NULL REFERENCES grp(id) ON DELETE CASCADE,
    creator_id      UUID NOT NULL REFERENCES app_user(id),
    starts_at       TIMESTAMPTZ NOT NULL,
    ends_at         TIMESTAMPTZ NOT NULL,
    title           TEXT NOT NULL,
    description     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    -- Constraints
    CONSTRAINT chk_meeting_time_order CHECK (ends_at > starts_at),
    CONSTRAINT chk_meeting_title_length CHECK (char_length(title) BETWEEN 3 AND 100),
    CONSTRAINT chk_meeting_description_length CHECK (description IS NULL OR char_length(description) <= 2000)
);

-- Indexes
CREATE INDEX idx_meeting_grp ON meeting(grp_id, starts_at DESC);
CREATE INDEX idx_meeting_creator ON meeting(creator_id);

-- Comments
COMMENT ON TABLE meeting IS 'Scheduled meetings created by group owners';
COMMENT ON COLUMN meeting.starts_at IS 'Meeting start time in UTC';
COMMENT ON COLUMN meeting.ends_at IS 'Meeting end time in UTC';