-- ============================================================================
-- Migration: backfill_session_metrics
-- Parses the legacy encoded notes string and backfills the newly added
-- duration_seconds and distance_km columns for all historical sessions.
-- ============================================================================

-- Backfill duration_seconds using regex extraction from notes
UPDATE training_sessions
SET duration_seconds = (substring(notes from 'duration_s=([0-9]+)'))::int
WHERE status = 'completed'
  AND duration_seconds IS NULL
  AND notes IS NOT NULL
  AND notes ~ 'duration_s=';

-- Backfill distance_km using regex extraction from notes
UPDATE training_sessions
SET distance_km = (substring(notes from 'distance_km=([0-9.]+)'))::double precision
WHERE status = 'completed'
  AND distance_km IS NULL
  AND notes IS NOT NULL
  AND notes ~ 'distance_km=';
