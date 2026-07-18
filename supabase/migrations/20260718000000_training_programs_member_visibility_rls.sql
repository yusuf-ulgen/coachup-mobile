-- ============================================================
-- Antrenman programlarında üye bazlı görünürlük (RLS)
-- ============================================================
-- training_programs tablosuna RLS ekler:
--   • privacy = 'public'  → salonun tüm üyeleri görür
--   • privacy = 'private' → sadece visible_member_ids içinde
--                           userId'si olan üyeler görür
--   • Admin / koç → her zaman görür (gym_id eşleşmesi ile)
-- ============================================================

-- 1. Sütunlar henüz yoksa ekle (güvenli: IF NOT EXISTS)
ALTER TABLE training_programs
    ADD COLUMN IF NOT EXISTS privacy TEXT NOT NULL DEFAULT 'public'
        CHECK (privacy IN ('public','private')),
    ADD COLUMN IF NOT EXISTS visible_member_ids UUID[] NOT NULL DEFAULT '{}';

-- Mevcut programları varsayılan olarak 'public' yap
UPDATE training_programs
SET privacy = 'public'
WHERE privacy IS NULL;

-- 2. RLS aktif et
ALTER TABLE training_programs ENABLE ROW LEVEL SECURITY;

-- 3. Eski genel politikaları temizle (varsa)
DROP POLICY IF EXISTS "Anyone can view active programs"   ON training_programs;
DROP POLICY IF EXISTS "Admins can manage programs"        ON training_programs;

-- 4. Üye okuma politikası
--    Üye kendi salonunun public programlarını VEYA
--    visible_member_ids içinde kendi ID'si geçen private programları görebilir.
DROP POLICY IF EXISTS "Members can view visible gym programs" ON training_programs;
CREATE POLICY "Members can view visible gym programs"
  ON training_programs FOR SELECT
  USING (
    is_active = true
    AND (
      -- Herkese açık salon programı
      privacy = 'public'
      OR
      -- Sadece bu üyeye atanmış özel program
      auth.uid() = ANY (visible_member_ids)
    )
  );

-- 5. Admin / süper-admin tam erişim
DROP POLICY IF EXISTS "Admins full access to programs" ON training_programs;
CREATE POLICY "Admins full access to programs"
  ON training_programs FOR ALL
  USING (
    EXISTS (
      SELECT 1 FROM users
      WHERE users.id = auth.uid()
        AND users.role IN ('admin', 'super_admin')
    )
  );

-- 6. Koç: kendi salonundaki programları okuyabilir ve güncelleyebilir
DROP POLICY IF EXISTS "Coaches can view own gym programs" ON training_programs;
CREATE POLICY "Coaches can view own gym programs"
  ON training_programs FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM coaches c
      WHERE c.user_id = auth.uid()
        AND c.gym_id = training_programs.gym_id
        AND c.is_active = true
    )
  );

DROP POLICY IF EXISTS "Coaches can manage own gym programs" ON training_programs;
CREATE POLICY "Coaches can manage own gym programs"
  ON training_programs FOR ALL
  USING (
    EXISTS (
      SELECT 1 FROM coaches c
      WHERE c.user_id = auth.uid()
        AND c.gym_id = training_programs.gym_id
        AND c.is_active = true
    )
  );

-- 7. visible_member_ids için indeks (performans)
CREATE INDEX IF NOT EXISTS idx_training_programs_visible_member_ids
  ON training_programs USING GIN (visible_member_ids);

CREATE INDEX IF NOT EXISTS idx_training_programs_privacy
  ON training_programs (privacy);
