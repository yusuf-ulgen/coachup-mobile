-- Koçlar: salon üyeleri yalnızca kendi salonlarının koçlarını görür.
-- Bireysel kullanıcılar yalnızca süper adminin atadığı koçları görür.

CREATE TABLE IF NOT EXISTS user_coach_assignments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    coach_id UUID NOT NULL REFERENCES coaches(id) ON DELETE CASCADE,
    assigned_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (user_id, coach_id)
);

CREATE INDEX IF NOT EXISTS idx_user_coach_assignments_user_id ON user_coach_assignments(user_id);
CREATE INDEX IF NOT EXISTS idx_user_coach_assignments_coach_id ON user_coach_assignments(coach_id);

ALTER TABLE user_coach_assignments ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can view own coach assignments" ON user_coach_assignments;
CREATE POLICY "Users can view own coach assignments"
  ON user_coach_assignments FOR SELECT
  USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "Anyone can view active coaches" ON coaches;

CREATE POLICY "Users can view their gym coaches"
  ON coaches FOR SELECT
  USING (
    is_active = true
    AND gym_id IS NOT NULL
    AND NOT public.is_individual_app_user(auth.uid())
    AND gym_id IN (SELECT public.user_survey_gym_ids(auth.uid()))
  );

CREATE POLICY "Individuals can view assigned coaches"
  ON coaches FOR SELECT
  USING (
    is_active = true
    AND public.is_individual_app_user(auth.uid())
    AND id IN (
      SELECT coach_id FROM user_coach_assignments
      WHERE user_id = auth.uid()
    )
  );
