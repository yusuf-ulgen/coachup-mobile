#!/usr/bin/env bash
# CoachUP — Haziran 2025 migration'ları (survey, coach, training_sessions, events, bookings RLS)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if ! command -v supabase >/dev/null 2>&1; then
  echo "supabase CLI bulunamadı. Kurulum: https://supabase.com/docs/guides/cli"
  echo "Alternatif: Supabase Dashboard → SQL Editor'da şu dosyaları sırayla çalıştırın:"
  echo "  - supabase/migrations/20250625170000_survey_audience_rls.sql"
  echo "  - supabase/migrations/20250625180000_coach_audience_rls.sql"
  echo "  - supabase/migrations/20250625190000_training_sessions_member_rls.sql"
  echo "  - supabase/migrations/20250630120000_member_events_and_class_bookings_rls.sql"
  exit 1
fi

echo "Remote migration push..."
supabase db push

echo "Tamamlandı."
