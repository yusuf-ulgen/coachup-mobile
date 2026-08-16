-- ══════════════════════════════════════════════════════════════════════════════
-- CANONICAL SCHEMA UNIFICATION & ATOMIC RPCS MIGRATION
-- ══════════════════════════════════════════════════════════════════════════════

-- 1. GYM AREAS (Reservation Resources) RLS & Defaults
CREATE TABLE IF NOT EXISTS public.gym_areas (
  id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
  gym_id uuid REFERENCES public.gyms(id) ON DELETE CASCADE,
  name text NOT NULL,
  capacity integer DEFAULT 10,
  is_active boolean DEFAULT true,
  description text,
  created_at timestamptz DEFAULT now()
);

ALTER TABLE public.gym_areas ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'gym_areas' AND policyname = 'srf_gym_areas') THEN
    CREATE POLICY "srf_gym_areas" ON public.gym_areas FOR ALL USING (true) WITH CHECK (true);
  END IF;
END $$;

-- 2. USER RESERVATIONS (Canonical Reservation Model)
CREATE TABLE IF NOT EXISTS public.user_reservations (
  id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
  gym_id uuid REFERENCES public.gyms(id) ON DELETE CASCADE,
  area_id uuid REFERENCES public.gym_areas(id) ON DELETE CASCADE,
  user_id uuid REFERENCES public.users(id) ON DELETE CASCADE,
  reservation_date date NOT NULL,
  start_time time NOT NULL,
  end_time time NOT NULL,
  notes text,
  status text DEFAULT 'pending',
  created_at timestamptz DEFAULT now()
);

DO $$
DECLARE
  r record;
BEGIN
  FOR r IN
    SELECT c.conname
    FROM pg_constraint c
    JOIN pg_class t ON c.conrelid = t.oid
    JOIN pg_namespace n ON t.relnamespace = n.oid
    WHERE n.nspname = 'public'
      AND t.relname = 'user_reservations'
      AND c.contype = 'c'
      AND pg_get_constraintdef(c.oid) ILIKE '%status%'
  LOOP
    EXECUTE format('ALTER TABLE public.user_reservations DROP CONSTRAINT %I', r.conname);
  END LOOP;

  ALTER TABLE public.user_reservations
    ADD CONSTRAINT user_reservations_status_check
    CHECK (status IN ('pending', 'approved', 'rejected', 'cancelled'));
EXCEPTION
  WHEN others THEN
    RAISE NOTICE 'user_reservations status check error: %', SQLERRM;
END $$;

ALTER TABLE public.user_reservations ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'user_reservations' AND policyname = 'srf_user_reservations') THEN
    CREATE POLICY "srf_user_reservations" ON public.user_reservations FOR ALL USING (true) WITH CHECK (true);
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_user_reservations_gym_date ON public.user_reservations(gym_id, reservation_date);
CREATE INDEX IF NOT EXISTS idx_user_reservations_user ON public.user_reservations(user_id);

-- 3. COACH MESSAGES (Safe Migration)
CREATE TABLE IF NOT EXISTS public.coach_messages (
  id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
  coach_id uuid REFERENCES public.coaches(id) ON DELETE CASCADE,
  user_id uuid REFERENCES public.users(id) ON DELETE CASCADE,
  message text NOT NULL,
  is_read boolean DEFAULT false,
  created_at timestamptz DEFAULT now()
);

ALTER TABLE public.coach_messages ADD COLUMN IF NOT EXISTS sender_type text;
ALTER TABLE public.coach_messages ADD COLUMN IF NOT EXISTS created_at timestamptz DEFAULT now();

DO $$
DECLARE
  r record;
BEGIN
  -- Backfill any existing message without sender_type if any
  UPDATE public.coach_messages SET sender_type = 'user' WHERE sender_type IS NULL;

  FOR r IN
    SELECT c.conname
    FROM pg_constraint c
    JOIN pg_class t ON c.conrelid = t.oid
    JOIN pg_namespace n ON t.relnamespace = n.oid
    WHERE n.nspname = 'public'
      AND t.relname = 'coach_messages'
      AND c.contype = 'c'
      AND pg_get_constraintdef(c.oid) ILIKE '%sender_type%'
  LOOP
    EXECUTE format('ALTER TABLE public.coach_messages DROP CONSTRAINT %I', r.conname);
  END LOOP;

  ALTER TABLE public.coach_messages
    ADD CONSTRAINT coach_messages_sender_type_check
    CHECK (sender_type IN ('user', 'coach'));
EXCEPTION
  WHEN others THEN
    RAISE NOTICE 'coach_messages constraint error: %', SQLERRM;
END $$;

ALTER TABLE public.coach_messages ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'coach_messages' AND policyname = 'srf_coach_messages') THEN
    CREATE POLICY "srf_coach_messages" ON public.coach_messages FOR ALL USING (true) WITH CHECK (true);
  END IF;
END $$;

-- 4. NUTRITION FOODS & USER NUTRITION PLANS ENHANCEMENT
CREATE TABLE IF NOT EXISTS public.nutrition_foods (
  id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
  meal_id uuid REFERENCES public.nutrition_meals(id) ON DELETE CASCADE,
  name text NOT NULL,
  portion text,
  calories integer DEFAULT 0,
  protein numeric DEFAULT 0,
  carbs numeric DEFAULT 0,
  fat numeric DEFAULT 0,
  order_index integer DEFAULT 0,
  created_at timestamptz DEFAULT now()
);

ALTER TABLE public.nutrition_foods ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'nutrition_foods' AND policyname = 'srf_nutrition_foods') THEN
    CREATE POLICY "srf_nutrition_foods" ON public.nutrition_foods FOR ALL USING (true) WITH CHECK (true);
  END IF;
END $$;

ALTER TABLE public.user_nutrition_plans ADD COLUMN IF NOT EXISTS status text DEFAULT 'active';
ALTER TABLE public.user_nutrition_plans ADD COLUMN IF NOT EXISTS gym_id uuid REFERENCES public.gyms(id) ON DELETE SET NULL;

-- 5. EVENT PARTICIPANTS STATUS SAFE BACKFILL & CONSTRAINT
DO $$
DECLARE
  r record;
BEGIN
  -- Safe backfill
  UPDATE public.event_participants SET status = 'registered' WHERE status = 'confirmed';

  FOR r IN
    SELECT c.conname
    FROM pg_constraint c
    JOIN pg_class t ON c.conrelid = t.oid
    JOIN pg_namespace n ON t.relnamespace = n.oid
    WHERE n.nspname = 'public'
      AND t.relname = 'event_participants'
      AND c.contype = 'c'
      AND pg_get_constraintdef(c.oid) ILIKE '%status%'
  LOOP
    EXECUTE format('ALTER TABLE public.event_participants DROP CONSTRAINT %I', r.conname);
  END LOOP;

  ALTER TABLE public.event_participants
    ADD CONSTRAINT event_participants_status_check
    CHECK (status IN ('registered', 'waiting', 'cancelled', 'attended'));
EXCEPTION
  WHEN others THEN
    RAISE NOTICE 'event_participants status check error: %', SQLERRM;
END $$;

-- 6. APPOINTMENTS RLS POLICIES
ALTER TABLE public.appointments ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'appointments' AND policyname = 'srf_appointments') THEN
    CREATE POLICY "srf_appointments" ON public.appointments FOR ALL USING (true) WITH CHECK (true);
  END IF;
END $$;

-- 7. ATOMIC BOOK GROUP CLASS RPC
CREATE OR REPLACE FUNCTION public.atomic_book_group_class(
  p_user_id uuid,
  p_class_id uuid,
  p_booking_date date DEFAULT CURRENT_DATE
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  v_capacity integer;
  v_active_count integer;
  v_existing_booking record;
  v_new_id uuid;
  v_status text;
BEGIN
  -- 1. Check existing booking for user
  SELECT id, status INTO v_existing_booking
  FROM public.class_bookings
  WHERE class_id = p_class_id AND user_id = p_user_id;

  IF v_existing_booking.id IS NOT NULL AND v_existing_booking.status IN ('booked', 'confirmed', 'waiting') THEN
    RETURN jsonb_build_object('success', false, 'error', 'Bu derse zaten aktif kaydınız bulunmaktadır.', 'booking_id', v_existing_booking.id, 'status', v_existing_booking.status);
  END IF;

  -- 2. Get class capacity
  SELECT capacity INTO v_capacity
  FROM public.group_classes
  WHERE id = p_class_id;

  IF v_capacity IS NULL THEN
    RETURN jsonb_build_object('success', false, 'error', 'Ders bulunamadı.');
  END IF;

  -- 3. Count active participants
  SELECT count(*) INTO v_active_count
  FROM public.class_bookings
  WHERE class_id = p_class_id AND status IN ('booked', 'confirmed');

  IF v_active_count < v_capacity THEN
    v_status := 'booked';
  ELSE
    v_status := 'waiting';
  END IF;

  v_new_id := gen_random_uuid();

  -- 4. If previous cancelled booking existed, reactivate it, otherwise insert new
  IF v_existing_booking.id IS NOT NULL THEN
    UPDATE public.class_bookings
    SET status = v_status, created_at = now()
    WHERE id = v_existing_booking.id;
    v_new_id := v_existing_booking.id;
  ELSE
    INSERT INTO public.class_bookings(id, class_id, user_id, status, created_at)
    VALUES (v_new_id, p_class_id, p_user_id, v_status, now());
  END IF;

  RETURN jsonb_build_object('success', true, 'booking_id', v_new_id, 'status', v_status, 'is_waiting', (v_status = 'waiting'));
END;
$$;

-- 8. ATOMIC CANCEL GROUP CLASS RPC
CREATE OR REPLACE FUNCTION public.atomic_cancel_group_class(
  p_user_id uuid,
  p_booking_id uuid
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  v_class_id uuid;
  v_old_status text;
  v_next_waiting record;
BEGIN
  SELECT class_id, status INTO v_class_id, v_old_status
  FROM public.class_bookings
  WHERE id = p_booking_id AND user_id = p_user_id;

  IF v_class_id IS NULL THEN
    RETURN jsonb_build_object('success', false, 'error', 'Rezervasyon bulunamadı veya yetkiniz yok.');
  END IF;

  -- Update booking to cancelled
  UPDATE public.class_bookings
  SET status = 'cancelled'
  WHERE id = p_booking_id;

  -- If the cancelled one was occupying a spot, promote the earliest waiting participant
  IF v_old_status IN ('booked', 'confirmed') THEN
    SELECT id, user_id INTO v_next_waiting
    FROM public.class_bookings
    WHERE class_id = v_class_id AND status = 'waiting'
    ORDER BY created_at ASC
    LIMIT 1;

    IF v_next_waiting.id IS NOT NULL THEN
      UPDATE public.class_bookings
      SET status = 'booked'
      WHERE id = v_next_waiting.id;

      -- Create notification for promoted user
      INSERT INTO public.notifications(id, user_id, title, message, type, is_read, created_at)
      VALUES (
        gen_random_uuid(),
        v_next_waiting.user_id,
        'Yedek Sıranız Geldi!',
        'Grup dersindeki yer boşaldı ve kaydınız onaylandı.',
        'class_booking',
        false,
        now()
      );
    END IF;
  END IF;

  RETURN jsonb_build_object('success', true);
END;
$$;

-- 9. ATOMIC JOIN EVENT RPC
CREATE OR REPLACE FUNCTION public.atomic_join_event(
  p_user_id uuid,
  p_event_id uuid
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  v_capacity integer;
  v_active_count integer;
  v_existing record;
  v_new_id uuid;
  v_status text;
BEGIN
  -- 1. Check existing
  SELECT id, status INTO v_existing
  FROM public.event_participants
  WHERE event_id = p_event_id AND user_id = p_user_id;

  IF v_existing.id IS NOT NULL AND v_existing.status IN ('registered', 'waiting') THEN
    RETURN jsonb_build_object('success', false, 'error', 'Bu etkinliğe zaten kayıtlısınız.', 'participant_id', v_existing.id, 'status', v_existing.status);
  END IF;

  -- 2. Check capacity
  SELECT capacity INTO v_capacity
  FROM public.gym_events
  WHERE id = p_event_id;

  IF v_capacity IS NOT NULL AND v_capacity > 0 THEN
    SELECT count(*) INTO v_active_count
    FROM public.event_participants
    WHERE event_id = p_event_id AND status = 'registered';

    IF v_active_count >= v_capacity THEN
      v_status := 'waiting';
    ELSE
      v_status := 'registered';
    END IF;
  ELSE
    v_status := 'registered';
  END IF;

  v_new_id := gen_random_uuid();

  IF v_existing.id IS NOT NULL THEN
    UPDATE public.event_participants
    SET status = v_status, registered_at = now()
    WHERE id = v_existing.id;
    v_new_id := v_existing.id;
  ELSE
    INSERT INTO public.event_participants(id, event_id, user_id, status, registered_at, created_at)
    VALUES (v_new_id, p_event_id, p_user_id, v_status, now(), now());
  END IF;

  RETURN jsonb_build_object('success', true, 'participant_id', v_new_id, 'status', v_status, 'is_waiting', (v_status = 'waiting'));
END;
$$;

-- 10. ATOMIC LEAVE EVENT RPC
CREATE OR REPLACE FUNCTION public.atomic_leave_event(
  p_user_id uuid,
  p_participant_id uuid
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  v_event_id uuid;
  v_old_status text;
  v_next_waiting record;
BEGIN
  SELECT event_id, status INTO v_event_id, v_old_status
  FROM public.event_participants
  WHERE id = p_participant_id AND user_id = p_user_id;

  IF v_event_id IS NULL THEN
    RETURN jsonb_build_object('success', false, 'error', 'Katılım kaydı bulunamadı.');
  END IF;

  UPDATE public.event_participants
  SET status = 'cancelled'
  WHERE id = p_participant_id;

  IF v_old_status = 'registered' THEN
    SELECT id, user_id INTO v_next_waiting
    FROM public.event_participants
    WHERE event_id = v_event_id AND status = 'waiting'
    ORDER BY registered_at ASC
    LIMIT 1;

    IF v_next_waiting.id IS NOT NULL THEN
      UPDATE public.event_participants
      SET status = 'registered'
      WHERE id = v_next_waiting.id;

      INSERT INTO public.notifications(id, user_id, title, message, type, is_read, created_at)
      VALUES (
        gen_random_uuid(),
        v_next_waiting.user_id,
        'Etkinlik Kaydınız Onaylandı!',
        'Etkinlikte yer açıldı ve kaydınız onaylandı.',
        'event',
        false,
        now()
      );
    END IF;
  END IF;

  RETURN jsonb_build_object('success', true);
END;
$$;
