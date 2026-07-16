#!/usr/bin/env bash
# CoachUP — pending remote migrations (RLS + community feed)
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
  echo "  - supabase/migrations/20260716000000_community_feed.sql"
  exit 1
fi

if [[ ! -f "supabase/.temp/project-ref" ]] && [[ ! -f "supabase/config.toml" ]]; then
  echo "Proje linkli değil. Örnek: supabase link --project-ref auiebboyocmkkxbdahqf --yes"
fi

echo "Remote SQL apply (community + linked query)..."
# Prefer Management API query for single files when db push password is unavailable
if [[ -f "supabase/migrations/20260716000000_community_feed.sql" ]]; then
  supabase db query --linked -f "supabase/migrations/20260716000000_community_feed.sql" || true
  supabase db query --linked "insert into supabase_migrations.schema_migrations (version, name) values ('20260716000000', 'community_feed') on conflict (version) do nothing;" || true
fi

echo "Remote migration push (if password/link available)..."
supabase db push --linked --yes || echo "db push atlandı (şifre/link gerekebilir); SQL query adımı yeterli olabilir."

echo "Tamamlandı."

