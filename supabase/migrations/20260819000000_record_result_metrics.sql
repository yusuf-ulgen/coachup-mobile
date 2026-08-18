-- ══════════════════════════════════════════════════════════════════════════════
-- ADDITIVE TYPED RESULT METRICS FOR CANONICAL RECORD PERSISTENCE
-- ══════════════════════════════════════════════════════════════════════════════

-- 1. Extend personal_records with typed non-weight metrics
ALTER TABLE public.personal_records
  ADD COLUMN IF NOT EXISTS result_type TEXT,
  ADD COLUMN IF NOT EXISTS elapsed_seconds INTEGER,
  ADD COLUMN IF NOT EXISTS distance_km NUMERIC(8,4),
  ADD COLUMN IF NOT EXISTS target_calories INTEGER,
  ADD COLUMN IF NOT EXISTS rounds INTEGER;

-- 2. Extend record_attempts with typed non-weight metrics
ALTER TABLE public.record_attempts
  ADD COLUMN IF NOT EXISTS result_type TEXT,
  ADD COLUMN IF NOT EXISTS elapsed_seconds INTEGER,
  ADD COLUMN IF NOT EXISTS distance_km NUMERIC(8,4),
  ADD COLUMN IF NOT EXISTS target_calories INTEGER,
  ADD COLUMN IF NOT EXISTS rounds INTEGER;

-- 3. Extend record_attempt_sets with typed non-weight metrics
ALTER TABLE public.record_attempt_sets
  ADD COLUMN IF NOT EXISTS result_type TEXT,
  ADD COLUMN IF NOT EXISTS elapsed_seconds INTEGER,
  ADD COLUMN IF NOT EXISTS distance_km NUMERIC(8,4),
  ADD COLUMN IF NOT EXISTS target_calories INTEGER,
  ADD COLUMN IF NOT EXISTS rounds INTEGER;
