CREATE TABLE grp (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id            UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    title               TEXT NOT NULL,
    description         TEXT,
    tz_id               TEXT NOT NULL DEFAULT 'Europe/Moscow',
    is_locked           BOOLEAN NOT NULL DEFAULT FALSE,
    show_participants   BOOLEAN NOT NULL DEFAULT FALSE,
    version             INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    -- Constraints
    CONSTRAINT chk_grp_title_length CHECK (char_length(title) BETWEEN 3 AND 100),
    CONSTRAINT chk_grp_description_length CHECK (description IS NULL OR char_length(description) <= 1000)
);

-- Indexes
CREATE INDEX idx_grp_owner ON grp(owner_id);
CREATE INDEX idx_grp_created ON grp(created_at DESC);

-- Trigger for updated_at
CREATE TRIGGER trg_grp_updated_at
    BEFORE UPDATE ON grp
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Comments
COMMENT ON TABLE grp IS 'Groups for coordinating meetings (named grp to avoid SQL reserved word)';
COMMENT ON COLUMN grp.version IS 'Incremented on any change for cache invalidation';
COMMENT ON COLUMN grp.is_locked IS 'When true, only owner can modify availability slots';
COMMENT ON COLUMN grp.show_participants IS 'When true, participant names are visible in heatmap';