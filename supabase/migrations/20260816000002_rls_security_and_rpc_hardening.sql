-- ══════════════════════════════════════════════════════════════════════════════
-- COACHUP CANONICAL SECURITY, RLS TENANCY & RPC HARDENING MIGRATION
-- ══════════════════════════════════════════════════════════════════════════════

-- 1. NOTIFICATIONS TABLE TYPE CONSTRAINT EXPANSION
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
      AND t.relname = 'notifications'
      AND c.contype = 'c'
      AND pg_get_constraintdef(c.oid) ILIKE '%type%'
  LOOP
    EXECUTE format('ALTER TABLE public.notifications DROP CONSTRAINT %I', r.conname);
  END LOOP;

  ALTER TABLE public.notifications
    ADD CONSTRAINT notifications_type_check
    CHECK (type IN (
      'system', 'general', 'membership', 'appointment', 'reservation',
      'event', 'class', 'survey', 'payment', 'campaign', 'alert', 'warning'
    ));
EXCEPTION
  WHEN others THEN
    RAISE NOTICE 'notifications type constraint error: %', SQLERRM;
END $$;

-- 2. SURVEYS & RESPONSES CANONICAL JSONB SCHEMA & COMPATIBILITY
ALTER TABLE public.surveys ADD COLUMN IF NOT EXISTS questions jsonb DEFAULT '[]'::jsonb;
ALTER TABLE public.survey_responses ADD COLUMN IF NOT EXISTS answers jsonb DEFAULT '{}'::jsonb;

-- Backfill legacy single question to questions jsonb if questions is empty or null
UPDATE public.surveys
SET questions = jsonb_build_array(
  jsonb_build_object(
    'id', 'q_1',
    'question', COALESCE(question, title, 'Memnuniyetinizi değerlendirin'),
    'type', 'rating'
  )
)
WHERE (questions IS NULL OR jsonb_array_length(questions) = 0) AND (question IS NOT NULL OR title IS NOT NULL);

-- Ensure unique response per survey + user
DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'uq_survey_responses_survey_user'
  ) THEN
    ALTER TABLE public.survey_responses
      ADD CONSTRAINT uq_survey_responses_survey_user
      UNIQUE (survey_id, user_id);
  END IF;
EXCEPTION
  WHEN others THEN
    RAISE NOTICE 'survey_responses unique constraint note: %', SQLERRM;
END $$;

-- 3. RLS PERMISSIVE POLICIES REMOVAL & REAL TENANT ISOLATION

-- Helper function: is_super_admin
CREATE OR REPLACE FUNCTION public.is_super_admin()
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
  SELECT COALESCE(
    (SELECT is_admin FROM public.users WHERE id = auth.uid()),
    false
  );
$$;

-- Helper function: is_manager_for_gym
CREATE OR REPLACE FUNCTION public.is_manager_for_gym(p_gym_id uuid)
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
  SELECT COALESCE(
    (SELECT (is_admin = true OR (is_gym_manager = true AND managed_gym_id = p_gym_id))
     FROM public.users WHERE id = auth.uid()),
    false
  );
$$;

-- 3.1 Appointments RLS
DROP POLICY IF EXISTS "srf_appointments" ON public.appointments;
DROP POLICY IF EXISTS "appointments_user_isolation" ON public.appointments;
DROP POLICY IF EXISTS "appointments_manager_access" ON public.appointments;

CREATE POLICY "appointments_user_isolation" ON public.appointments
  FOR ALL
  TO authenticated
  USING (
    user_id = auth.uid()
    OR public.is_manager_for_gym(gym_id)
    OR public.is_super_admin()
  )
  WITH CHECK (
    user_id = auth.uid()
    OR public.is_manager_for_gym(gym_id)
    OR public.is_super_admin()
  );

-- 3.2 User Reservations RLS
DROP POLICY IF EXISTS "srf_user_reservations" ON public.user_reservations;
DROP POLICY IF EXISTS "user_reservations_isolation" ON public.user_reservations;

CREATE POLICY "user_reservations_isolation" ON public.user_reservations
  FOR ALL
  TO authenticated
  USING (
    user_id = auth.uid()
    OR public.is_manager_for_gym(gym_id)
    OR public.is_super_admin()
  )
  WITH CHECK (
    user_id = auth.uid()
    OR public.is_manager_for_gym(gym_id)
    OR public.is_super_admin()
  );

-- 3.3 Coach Messages RLS
DROP POLICY IF EXISTS "srf_coach_messages" ON public.coach_messages;
DROP POLICY IF EXISTS "coach_messages_isolation" ON public.coach_messages;

CREATE POLICY "coach_messages_isolation" ON public.coach_messages
  FOR ALL
  TO authenticated
  USING (
    user_id = auth.uid()
    OR public.is_super_admin()
    OR EXISTS (
      SELECT 1 FROM public.coaches c
      WHERE c.id = coach_messages.coach_id
        AND public.is_manager_for_gym(c.gym_id)
    )
  )
  WITH CHECK (
    user_id = auth.uid()
    OR public.is_super_admin()
    OR EXISTS (
      SELECT 1 FROM public.coaches c
      WHERE c.id = coach_messages.coach_id
        AND public.is_manager_for_gym(c.gym_id)
    )
  );

-- 3.4 Nutrition & Body Measurements RLS
DROP POLICY IF EXISTS "srf_nutrition_foods" ON public.nutrition_foods;
CREATE POLICY "nutrition_foods_authenticated" ON public.nutrition_foods
  FOR SELECT TO authenticated USING (true);
CREATE POLICY "nutrition_foods_manager_modify" ON public.nutrition_foods
  FOR ALL TO authenticated
  USING (public.is_super_admin() OR EXISTS (
    SELECT 1 FROM public.nutrition_meals nm
    JOIN public.nutrition_plans np ON np.id = nm.plan_id
    WHERE nm.id = nutrition_foods.meal_id AND public.is_manager_for_gym(np.gym_id)
  ))
  WITH CHECK (public.is_super_admin() OR EXISTS (
    SELECT 1 FROM public.nutrition_meals nm
    JOIN public.nutrition_plans np ON np.id = nm.plan_id
    WHERE nm.id = nutrition_foods.meal_id AND public.is_manager_for_gym(np.gym_id)
  ));

ALTER TABLE public.body_measurements ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "body_measurements_user_isolation" ON public.body_measurements;
CREATE POLICY "body_measurements_user_isolation" ON public.body_measurements
  FOR ALL TO authenticated
  USING (
    user_id = auth.uid()
    OR (gym_id IS NOT NULL AND public.is_manager_for_gym(gym_id))
    OR public.is_super_admin()
  )
  WITH CHECK (
    user_id = auth.uid()
    OR (gym_id IS NOT NULL AND public.is_manager_for_gym(gym_id))
    OR public.is_super_admin()
  );

ALTER TABLE public.progress_photos ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "progress_photos_user_isolation" ON public.progress_photos;
CREATE POLICY "progress_photos_user_isolation" ON public.progress_photos
  FOR ALL TO authenticated
  USING (
    user_id = auth.uid()
    OR (gym_id IS NOT NULL AND public.is_manager_for_gym(gym_id))
    OR public.is_super_admin()
  )
  WITH CHECK (
    user_id = auth.uid()
    OR (gym_id IS NOT NULL AND public.is_manager_for_gym(gym_id))
    OR public.is_super_admin()
  );

-- 3.5 Notifications RLS
ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "notifications_user_isolation" ON public.notifications;
CREATE POLICY "notifications_user_isolation" ON public.notifications
  FOR ALL TO authenticated
  USING (user_id = auth.uid() OR public.is_super_admin())
  WITH CHECK (user_id = auth.uid() OR public.is_super_admin());

-- 3.6 User Assigned Programs RLS
ALTER TABLE public.user_assigned_programs ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "user_assigned_programs_isolation" ON public.user_assigned_programs;
CREATE POLICY "user_assigned_programs_isolation" ON public.user_assigned_programs
  FOR ALL TO authenticated
  USING (
    user_id = auth.uid()
    OR (gym_id IS NOT NULL AND public.is_manager_for_gym(gym_id))
    OR public.is_super_admin()
  )
  WITH CHECK (
    user_id = auth.uid()
    OR (gym_id IS NOT NULL AND public.is_manager_for_gym(gym_id))
    OR public.is_super_admin()
  );

-- 3.7 QR Entries RLS
ALTER TABLE public.qr_entries ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "qr_entries_isolation" ON public.qr_entries;
CREATE POLICY "qr_entries_isolation" ON public.qr_entries
  FOR ALL TO authenticated
  USING (
    user_id = auth.uid()
    OR (gym_id IS NOT NULL AND public.is_manager_for_gym(gym_id))
    OR public.is_super_admin()
  )
  WITH CHECK (
    user_id = auth.uid()
    OR (gym_id IS NOT NULL AND public.is_manager_for_gym(gym_id))
    OR public.is_super_admin()
  );

-- 3.8 Surveys & Survey Responses RLS
ALTER TABLE public.surveys ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "surveys_select" ON public.surveys;
DROP POLICY IF EXISTS "surveys_modify" ON public.surveys;
CREATE POLICY "surveys_select" ON public.surveys
  FOR SELECT TO authenticated
  USING (status = 'active' OR public.is_manager_for_gym(gym_id) OR public.is_super_admin());
CREATE POLICY "surveys_modify" ON public.surveys
  FOR ALL TO authenticated
  USING (public.is_manager_for_gym(gym_id) OR public.is_super_admin())
  WITH CHECK (public.is_manager_for_gym(gym_id) OR public.is_super_admin());

ALTER TABLE public.survey_responses ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "survey_responses_isolation" ON public.survey_responses;
CREATE POLICY "survey_responses_isolation" ON public.survey_responses
  FOR ALL TO authenticated
  USING (
    user_id = auth.uid()
    OR EXISTS (SELECT 1 FROM public.surveys s WHERE s.id = survey_responses.survey_id AND public.is_manager_for_gym(s.gym_id))
    OR public.is_super_admin()
  )
  WITH CHECK (user_id = auth.uid() OR public.is_super_admin());


-- ══════════════════════════════════════════════════════════════════════════════
-- 4. HARDENED ATOMIC RPC FUNCTIONS (SECURITY DEFINER + auth.uid() enforcement)
-- ══════════════════════════════════════════════════════════════════════════════

-- 4.1 ATOMIC BOOK GROUP CLASS (Scoped by Date & Capacity Locked)
CREATE OR REPLACE FUNCTION public.atomic_book_group_class(
  p_user_id uuid,
  p_class_id uuid,
  p_booking_date date DEFAULT CURRENT_DATE
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_caller_id uuid;
  v_effective_user_id uuid;
  v_capacity integer;
  v_active_count integer;
  v_existing_booking record;
  v_new_id uuid;
  v_status text;
  v_gym_id uuid;
BEGIN
  v_caller_id := auth.uid();
  IF v_caller_id IS NULL THEN
    RETURN jsonb_build_object('success', false, 'error', 'Yetkisiz erişim: Oturum açmanız gerekmektedir.');
  END IF;

  -- Validate user parameter
  IF p_user_id IS NOT NULL AND p_user_id <> v_caller_id THEN
    -- Only admin / gym manager can book on behalf of another user
    IF NOT (public.is_super_admin()) THEN
      RETURN jsonb_build_object('success', false, 'error', 'Başka bir kullanıcı adına işlem yapamazsınız.');
    END IF;
    v_effective_user_id := p_user_id;
  ELSE
    v_effective_user_id := v_caller_id;
  END IF;

  -- Lock group class row to serialize concurrent capacity decisions
  SELECT capacity, gym_id INTO v_capacity, v_gym_id
  FROM public.group_classes
  WHERE id = p_class_id
  FOR UPDATE;

  IF v_capacity IS NULL THEN
    RETURN jsonb_build_object('success', false, 'error', 'Ders bulunamadı.');
  END IF;

  -- Check existing booking for this user on the SPECIFIC booking date
  SELECT id, status INTO v_existing_booking
  FROM public.class_bookings
  WHERE class_id = p_class_id
    AND user_id = v_effective_user_id
    AND (booking_date = p_booking_date OR (booking_date IS NULL AND p_booking_date = CURRENT_DATE));

  IF v_existing_booking.id IS NOT NULL AND v_existing_booking.status IN ('booked', 'confirmed', 'waiting') THEN
    RETURN jsonb_build_object(
      'success', false,
      'error', 'Bu ders için seçilen tarihte zaten aktif kaydınız bulunmaktadır.',
      'booking_id', v_existing_booking.id,
      'status', v_existing_booking.status
    );
  END IF;

  -- Count active bookings for the specified date
  SELECT COUNT(*) INTO v_active_count
  FROM public.class_bookings
  WHERE class_id = p_class_id
    AND status IN ('booked', 'confirmed')
    AND (booking_date = p_booking_date OR (booking_date IS NULL AND p_booking_date = CURRENT_DATE));

  IF v_active_count < v_capacity THEN
    v_status := 'booked';
  ELSE
    v_status := 'waiting';
  END IF;

  IF v_existing_booking.id IS NOT NULL THEN
    UPDATE public.class_bookings
    SET status = v_status,
        booking_date = p_booking_date,
        created_at = now()
    WHERE id = v_existing_booking.id
    RETURNING id INTO v_new_id;
  ELSE
    INSERT INTO public.class_bookings (id, class_id, user_id, status, booking_date, created_at)
    VALUES (gen_random_uuid(), p_class_id, v_effective_user_id, v_status, p_booking_date, now())
    RETURNING id INTO v_new_id;
  END IF;

  RETURN jsonb_build_object(
    'success', true,
    'booking_id', v_new_id,
    'status', v_status,
    'message', CASE WHEN v_status = 'booked' THEN 'Ders kaydınız başarıyla oluşturuldu.' ELSE 'Ders dolu olduğu için bekleme listesine alındınız.' END
  );
END;
$$;

-- 4.2 ATOMIC CANCEL GROUP CLASS & AUTO-PROMOTE
CREATE OR REPLACE FUNCTION public.atomic_cancel_group_class(
  p_user_id uuid,
  p_booking_id uuid
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_caller_id uuid;
  v_booking record;
  v_next_waiting record;
BEGIN
  v_caller_id := auth.uid();
  IF v_caller_id IS NULL THEN
    RETURN jsonb_build_object('success', false, 'error', 'Yetkisiz erişim.');
  END IF;

  SELECT * INTO v_booking
  FROM public.class_bookings
  WHERE id = p_booking_id;

  IF v_booking.id IS NULL THEN
    RETURN jsonb_build_object('success', false, 'error', 'Rezervasyon bulunamadı.');
  END IF;

  -- Validate ownership
  IF v_booking.user_id <> v_caller_id AND NOT public.is_super_admin() THEN
    RETURN jsonb_build_object('success', false, 'error', 'Bu rezervasyonu iptal etme yetkiniz yok.');
  END IF;

  UPDATE public.class_bookings
  SET status = 'cancelled'
  WHERE id = p_booking_id;

  -- If was booked, promote the oldest waiting for the same class & date
  IF v_booking.status IN ('booked', 'confirmed') THEN
    SELECT id, user_id INTO v_next_waiting
    FROM public.class_bookings
    WHERE class_id = v_booking.class_id
      AND (booking_date = v_booking.booking_date OR (booking_date IS NULL AND v_booking.booking_date IS NULL))
      AND status = 'waiting'
    ORDER BY created_at ASC
    LIMIT 1;

    IF v_next_waiting.id IS NOT NULL THEN
      UPDATE public.class_bookings
      SET status = 'booked'
      WHERE id = v_next_waiting.id;

      INSERT INTO public.notifications (id, user_id, title, message, type, is_read, created_at)
      VALUES (
        gen_random_uuid(),
        v_next_waiting.user_id,
        'Ders Kaydınız Onaylandı!',
        'Bekleme listesinde olduğunuz grup dersinde yer açıldı ve kaydınız onaylandı.',
        'class',
        false,
        now()
      );
    END IF;
  END IF;

  RETURN jsonb_build_object('success', true, 'message', 'Rezervasyon iptal edildi.');
END;
$$;

-- 4.3 ATOMIC JOIN EVENT
CREATE OR REPLACE FUNCTION public.atomic_join_event(
  p_user_id uuid,
  p_event_id uuid
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_caller_id uuid;
  v_effective_user_id uuid;
  v_capacity integer;
  v_active_count integer;
  v_existing record;
  v_new_id uuid;
  v_status text;
BEGIN
  v_caller_id := auth.uid();
  IF v_caller_id IS NULL THEN
    RETURN jsonb_build_object('success', false, 'error', 'Yetkisiz erişim.');
  END IF;

  IF p_user_id IS NOT NULL AND p_user_id <> v_caller_id AND NOT public.is_super_admin() THEN
    RETURN jsonb_build_object('success', false, 'error', 'Başka kullanıcı adına işlem yapamazsınız.');
  END IF;

  v_effective_user_id := COALESCE(p_user_id, v_caller_id);

  SELECT capacity INTO v_capacity
  FROM public.gym_events
  WHERE id = p_event_id
  FOR UPDATE;

  IF v_capacity IS NULL THEN
    RETURN jsonb_build_object('success', false, 'error', 'Etkinlik bulunamadı.');
  END IF;

  SELECT id, status INTO v_existing
  FROM public.event_participants
  WHERE event_id = p_event_id AND user_id = v_effective_user_id;

  IF v_existing.id IS NOT NULL AND v_existing.status IN ('registered', 'waiting') THEN
    RETURN jsonb_build_object('success', false, 'error', 'Bu etkinliğe zaten kaydınız bulunmaktadır.', 'status', v_existing.status);
  END IF;

  SELECT COUNT(*) INTO v_active_count
  FROM public.event_participants
  WHERE event_id = p_event_id AND status = 'registered';

  IF v_active_count < v_capacity THEN
    v_status := 'registered';
  ELSE
    v_status := 'waiting';
  END IF;

  IF v_existing.id IS NOT NULL THEN
    UPDATE public.event_participants
    SET status = v_status, registered_at = now()
    WHERE id = v_existing.id
    RETURNING id INTO v_new_id;
  ELSE
    INSERT INTO public.event_participants (id, event_id, user_id, status, registered_at)
    VALUES (gen_random_uuid(), p_event_id, v_effective_user_id, v_status, now())
    RETURNING id INTO v_new_id;
  END IF;

  RETURN jsonb_build_object('success', true, 'status', v_status, 'participant_id', v_new_id);
END;
$$;

-- 4.4 ATOMIC LEAVE EVENT
CREATE OR REPLACE FUNCTION public.atomic_leave_event(
  p_user_id uuid,
  p_participant_id uuid
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_caller_id uuid;
  v_event_id uuid;
  v_old_status text;
  v_user_id uuid;
  v_next_waiting record;
BEGIN
  v_caller_id := auth.uid();
  IF v_caller_id IS NULL THEN
    RETURN jsonb_build_object('success', false, 'error', 'Yetkisiz erişim.');
  END IF;

  SELECT event_id, status, user_id INTO v_event_id, v_old_status, v_user_id
  FROM public.event_participants
  WHERE id = p_participant_id;

  IF v_event_id IS NULL THEN
    RETURN jsonb_build_object('success', false, 'error', 'Katılım kaydı bulunamadı.');
  END IF;

  IF v_user_id <> v_caller_id AND NOT public.is_super_admin() THEN
    RETURN jsonb_build_object('success', false, 'error', 'İptal yetkiniz bulunmamaktadır.');
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

      INSERT INTO public.notifications (id, user_id, title, message, type, is_read, created_at)
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

  RETURN jsonb_build_object('success', true, 'message', 'Etkinlik kaydı iptal edildi.');
END;
$$;

GRANT EXECUTE ON FUNCTION public.atomic_book_group_class TO authenticated;
GRANT EXECUTE ON FUNCTION public.atomic_cancel_group_class TO authenticated;
GRANT EXECUTE ON FUNCTION public.atomic_join_event TO authenticated;
GRANT EXECUTE ON FUNCTION public.atomic_leave_event TO authenticated;
