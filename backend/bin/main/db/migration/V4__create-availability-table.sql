CREATE TABLE availability (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    grp_id      UUID NOT NULL REFERENCES grp(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    starts_at   TIMESTAMPTZ NOT NULL,
    ends_at     TIMESTAMPTZ NOT NULL,
    note        TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    -- Constraints
    CONSTRAINT chk_avail_time_order CHECK (ends_at > starts_at),
    CONSTRAINT chk_avail_min_duration CHECK (ends_at - starts_at >= INTERVAL '5 minutes'),
    CONSTRAINT chk_avail_max_duration CHECK (ends_at - starts_at <= INTERVAL '48 hours'),
    CONSTRAINT chk_avail_note_length CHECK (note IS NULL OR char_length(note) <= 200)
);

-- Critical index for heatmap queries
CREATE INDEX idx_avail_grp_time ON availability(grp_id, starts_at, ends_at);
CREATE INDEX idx_avail_user ON availability(user_id);
CREATE INDEX idx_avail_created ON availability(created_at DESC);

-- Trigger: Increment group version on any availability change (for cache invalidation)
CREATE OR REPLACE FUNCTION increment_group_version()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE grp 
    SET version = version + 1 
    WHERE id = COALESCE(NEW.grp_id, OLD.grp_id);
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_avail_insert_version
    AFTER INSERT ON availability
    FOR EACH ROW
    EXECUTE FUNCTION increment_group_version();

CREATE TRIGGER trg_avail_update_version
    AFTER UPDATE ON availability
    FOR EACH ROW
    EXECUTE FUNCTION increment_group_version();

CREATE TRIGGER trg_avail_delete_version
    AFTER DELETE ON availability
    FOR EACH ROW
    EXECUTE FUNCTION increment_group_version();

-- Comments
COMMENT ON TABLE availability IS 'Time slots when users are available for meetings';
COMMENT ON COLUMN availability.starts_at IS 'Start time in UTC (always stored as UTC)';
COMMENT ON COLUMN availability.ends_at IS 'End time in UTC (always stored as UTC)';
COMMENT ON COLUMN availability.note IS 'Optional note like "Preferred" or "Maybe"';