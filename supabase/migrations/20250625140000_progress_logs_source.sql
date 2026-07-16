-- Ölçüm kaynağı: üye (self), koç (coach), admin (admin)
ALTER TABLE user_progress_logs
    ADD COLUMN IF NOT EXISTS recorded_by_name text,
    ADD COLUMN IF NOT EXISTS source text DEFAULT 'coach';

COMMENT ON COLUMN user_progress_logs.source IS 'self = üye kendi ekledi, coach = koç, admin = panel';
