-- Yerleşik aktiviteler (Koşu, Fitness vb.) program_id olmadan kaydedilebilsin
ALTER TABLE training_sessions
    ALTER COLUMN program_id DROP NOT NULL;

-- Varsayılan aktivite programları (duration / exercise_count zorunlu)
INSERT INTO training_programs (
    id, name, category, difficulty, duration, calories_burn, exercise_count, is_active
)
VALUES
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
