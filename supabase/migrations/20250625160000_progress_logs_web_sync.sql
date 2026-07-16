-- Web panel (log_date, arms, legs) + mobil (measured_at, source) uyumu

ALTER TABLE public.user_progress_logs
    ADD COLUMN IF NOT EXISTS log_date date,
    ADD COLUMN IF NOT EXISTS arms numeric,
    ADD COLUMN IF NOT EXISTS legs numeric,
    ADD COLUMN IF NOT EXISTS recorded_by_name text,
    ADD COLUMN IF NOT EXISTS source text DEFAULT 'coach';

UPDATE public.user_progress_logs
SET log_date = (measured_at AT TIME ZONE 'UTC')::date
WHERE log_date IS NULL AND measured_at IS NOT NULL;

COMMENT ON COLUMN public.user_progress_logs.source IS 'self = üye kendi ekledi, coach = koç, admin = panel';
