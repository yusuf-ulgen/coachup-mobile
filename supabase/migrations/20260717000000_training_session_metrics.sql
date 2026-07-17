-- ============================================================================
-- Migration: training_session_metrics
-- Adds dedicated metric columns to training_sessions table.
--
-- Previously ALL workout data was encoded into the `notes` TEXT column as:
--   "builtin:running|duration_s=1800;distance_km=5.200"
-- This migration adds proper typed columns so metrics are queryable,
-- indexable, and no longer depend on string parsing.
--
-- All columns are NULLABLE:
--   • duration_seconds & distance_km: always written by the app
--   • avg/max_heart_rate & calories: only from Health Connect (smartwatch)
--     → NULL when no wearable is connected → UI shows "—"
--   • avg_pace & avg_speed: only for outdoor/GPS activities
--   • altitude_gain: only for GPS activities with altitude data
--   • perceived_effort: user's post-workout feeling (optional)
-- ============================================================================

-- 1) Core timing & distance (previously encoded in notes)
ALTER TABLE training_sessions
  ADD COLUMN IF NOT EXISTS duration_seconds INT;

ALTER TABLE training_sessions
  ADD COLUMN IF NOT EXISTS distance_km DOUBLE PRECISION;

-- 2) Heart rate — from Health Connect / smartwatch only
ALTER TABLE training_sessions
  ADD COLUMN IF NOT EXISTS avg_heart_rate INT;

ALTER TABLE training_sessions
  ADD COLUMN IF NOT EXISTS max_heart_rate INT;

-- 3) Calories — from Health Connect / smartwatch only (no fake estimates)
ALTER TABLE training_sessions
  ADD COLUMN IF NOT EXISTS calories INT;

-- 4) Pace & speed — outdoor/GPS activities
ALTER TABLE training_sessions
  ADD COLUMN IF NOT EXISTS avg_pace DOUBLE PRECISION;   -- min/km

ALTER TABLE training_sessions
  ADD COLUMN IF NOT EXISTS avg_speed DOUBLE PRECISION;  -- km/h

-- 5) Altitude gain — GPS activities with barometer/altitude data
ALTER TABLE training_sessions
  ADD COLUMN IF NOT EXISTS altitude_gain DOUBLE PRECISION;  -- metres

-- 6) Perceived effort — post-workout "Nasıl hissediyorsun?" response
--    Values: 'great' | 'good' | 'normal' | 'hard' | 'very_hard'
ALTER TABLE training_sessions
  ADD COLUMN IF NOT EXISTS perceived_effort TEXT;

-- ============================================================================
-- Indexes for common query patterns (dashboard stats, progress charts)
-- ============================================================================

-- Fast lookup: "last N completed sessions for a user"
CREATE INDEX IF NOT EXISTS idx_training_sessions_user_completed
  ON training_sessions (user_id, completed_at DESC)
  WHERE status = 'completed';

-- Fast aggregation: "total calories / distance this week for a user"
CREATE INDEX IF NOT EXISTS idx_training_sessions_user_calories
  ON training_sessions (user_id, completed_at)
  WHERE calories IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_training_sessions_user_distance
  ON training_sessions (user_id, completed_at)
  WHERE distance_km IS NOT NULL;
