-- V12: Add ON DELETE CASCADE/SET NULL for user FKs missing cascade behavior.
-- Required so bulk DELETE on app_user (guest cleanup) doesn't hit FK violations.

-- meeting.creator_id: delete meetings when creator is deleted
ALTER TABLE meeting
    DROP CONSTRAINT IF EXISTS meeting_creator_id_fkey,
    ADD CONSTRAINT meeting_creator_id_fkey
        FOREIGN KEY (creator_id) REFERENCES app_user(id) ON DELETE CASCADE;

-- invite.created_by: delete invites when creator is deleted
ALTER TABLE invite
    DROP CONSTRAINT IF EXISTS invite_created_by_fkey,
    ADD CONSTRAINT invite_created_by_fkey
        FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE CASCADE;

-- report.reporter_id: delete reports when reporter is deleted
ALTER TABLE report
    DROP CONSTRAINT IF EXISTS report_reporter_id_fkey,
    ADD CONSTRAINT report_reporter_id_fkey
        FOREIGN KEY (reporter_id) REFERENCES app_user(id) ON DELETE CASCADE;

-- report.reviewed_by: nullable reviewer — null out when reviewer is deleted
ALTER TABLE report
    DROP CONSTRAINT IF EXISTS report_reviewed_by_fkey,
    ADD CONSTRAINT report_reviewed_by_fkey
        FOREIGN KEY (reviewed_by) REFERENCES app_user(id) ON DELETE SET NULL;
