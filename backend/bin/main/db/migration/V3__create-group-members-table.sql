CREATE TABLE grp_member (
    grp_id      UUID NOT NULL REFERENCES grp(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role        TEXT NOT NULL DEFAULT 'MEMBER',
    status      TEXT NOT NULL DEFAULT 'ACTIVE',
    joined_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    -- Composite primary key
    PRIMARY KEY (grp_id, user_id),
    
    -- Constraints
    CONSTRAINT chk_member_role CHECK (role IN ('OWNER', 'MEMBER')),
    CONSTRAINT chk_member_status CHECK (status IN ('ACTIVE', 'LEFT', 'BANNED'))
);

-- Indexes
CREATE INDEX idx_member_user ON grp_member(user_id);
CREATE INDEX idx_member_active ON grp_member(grp_id, user_id) WHERE status = 'ACTIVE';

-- Ensure exactly one owner per group (partial unique index)
CREATE UNIQUE INDEX idx_one_owner_per_group 
    ON grp_member(grp_id) 
    WHERE role = 'OWNER' AND status = 'ACTIVE';

-- Trigger: Auto-add owner as member when group is created
CREATE OR REPLACE FUNCTION add_owner_as_member()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO grp_member (grp_id, user_id, role, status)
    VALUES (NEW.id, NEW.owner_id, 'OWNER', 'ACTIVE')
    ON CONFLICT (grp_id, user_id) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_grp_add_owner
    AFTER INSERT ON grp
    FOR EACH ROW
    EXECUTE FUNCTION add_owner_as_member();

-- Comments
COMMENT ON TABLE grp_member IS 'Group membership with roles';
COMMENT ON COLUMN grp_member.role IS 'OWNER or MEMBER';
COMMENT ON COLUMN grp_member.status IS 'ACTIVE, LEFT (voluntary), or BANNED (by owner)';