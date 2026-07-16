-- Mobil uygulama: üyeler kendi antrenman oturumlarını oluşturabilsin.
-- Kapsamlı admin/salon RLS politikaları üye INSERT'ini engelliyordu.

ALTER TABLE training_sessions
    ALTER COLUMN program_id DROP NOT NULL;

DO $$ BEGIN
    ALTER TABLE training_sessions ALTER COLUMN gym_id DROP NOT NULL;
EXCEPTION
    WHEN undefined_column THEN NULL;
END $$;

-- Yerleşik aktivite programları (FK için)
INSERT INTO training_programs (
    id, name, category, difficulty, duration, calories_burn, exercise_count, is_active
)
VALUES
    ('b0000000-0000-4000-8000-000000000000', 'Aktivite', 'custom', 'intermediate', 60, 0, 0, true),
    ('b0000001-0000-4000-8000-000000000001', 'Fitness', 'fitness', 'intermediate', 60, 0, 0, true),
    ('b0000002-0000-4000-8000-000000000002', 'Koşu', 'running', 'intermediate', 60, 0, 0, true),
    ('b0000003-0000-4000-8000-000000000003', 'Yürüyüş', 'walking', 'intermediate', 60, 0, 0, true),
    ('b0000004-0000-4000-8000-000000000004', 'Bisiklet', 'cycling', 'intermediate', 60, 0, 0, true),
    ('b0000005-0000-4000-8000-000000000005', 'Yüzme', 'swimming', 'intermediate', 60, 0, 0, true),
    ('b0000006-0000-4000-8000-000000000006', 'Dövüş Sporları', 'custom', 'intermediate', 60, 0, 0, true),
    ('b0000007-0000-4000-8000-000000000007', 'Yoga', 'yoga', 'intermediate', 60, 0, 0, true),
    ('b0000008-0000-4000-8000-000000000008', 'Pilates', 'pilates', 'intermediate', 60, 0, 0, true),
    ('b0000009-0000-4000-8000-000000000009', 'CrossFit', 'custom', 'intermediate', 60, 0, 0, true),
    ('b000000a-0000-4000-8000-00000000000a', 'Functional Fitness', 'custom', 'intermediate', 60, 0, 0, true),
    ('b000000b-0000-4000-8000-00000000000b', 'Hyrox', 'custom', 'intermediate', 60, 0, 0, true),
    ('b000000c-0000-4000-8000-00000000000c', 'Özel Aktivite', 'custom', 'intermediate', 60, 0, 0, true)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    category = EXCLUDED.category,
    duration = EXCLUDED.duration,
    calories_burn = EXCLUDED.calories_burn,
    exercise_count = EXCLUDED.exercise_count,
    is_active = true;

-- Üye: kendi oturumları
DROP POLICY IF EXISTS "members_manage_own_training_sessions" ON training_sessions;
CREATE POLICY "members_manage_own_training_sessions"
    ON training_sessions
    FOR ALL
    TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

-- Eski politika adları (varsa)
DROP POLICY IF EXISTS "Users can view own sessions" ON training_sessions;
DROP POLICY IF EXISTS "Users can manage own sessions" ON training_sessions;

-- Aktif programları okuyabilsin (FK / liste için)
DROP POLICY IF EXISTS "members_read_active_training_programs" ON training_programs;
CREATE POLICY "members_read_active_training_programs"
    ON training_programs
    FOR SELECT
    TO authenticated
    USING (is_active = true);

-- workout_sets: oturum sahibi yazabilsin
DROP POLICY IF EXISTS "members_manage_own_workout_sets" ON workout_sets;
CREATE POLICY "members_manage_own_workout_sets"
    ON workout_sets
    FOR ALL
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM training_sessions ts
            WHERE ts.id = workout_sets.session_id
              AND ts.user_id = auth.uid()
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM training_sessions ts
            WHERE ts.id = workout_sets.session_id
              AND ts.user_id = auth.uid()
        )
    );
