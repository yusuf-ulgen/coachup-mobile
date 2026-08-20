-- ══════════════════════════════════════════════════════════════════════════════
-- Phase 8.5: Final Schema Compatibility Correction & Hardening
-- ══════════════════════════════════════════════════════════════════════════════

-- 1. SAFE LEGACY is_active RECONCILIATION
-- If legacy `status` exists on user_memberships, reconcile inactive/active states
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'user_memberships'
      AND column_name = 'status'
  ) THEN
    -- Legacy non-active status -> force is_active = false
    UPDATE public.user_memberships
    SET is_active = false
    WHERE status IS NOT NULL
      AND LOWER(TRIM(status)) IN ('cancelled', 'canceled', 'expired', 'frozen', 'pending', 'inactive');

    -- Legacy active status + is_active is NULL -> is_active = true
    UPDATE public.user_memberships
    SET is_active = true
    WHERE status IS NOT NULL
      AND LOWER(TRIM(status)) = 'active'
      AND is_active IS NULL;

    -- Legacy active status + is_active is already false: PRESERVED (do not overwrite intentional newer false)
  END IF;
END $$;

-- 2. SAFE PLAN ID BACKFILL
-- If both membership_plan_id and plan_id exist, backfill null plan_id from membership_plan_id
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'user_memberships'
      AND column_name = 'membership_plan_id'
  ) AND EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'user_memberships'
      AND column_name = 'plan_id'
  ) THEN
    UPDATE public.user_memberships
    SET plan_id = membership_plan_id
    WHERE plan_id IS NULL
      AND membership_plan_id IS NOT NULL;
  END IF;
END $$;

-- 3. HELPER FUNCTIONS
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

-- 4. FINALIZED LIVE-COMPATIBLE GROUP CLASS V2 RPCS
CREATE OR REPLACE FUNCTION public.atomic_book_group_class_v2(
  p_user_id uuid,
  p_class_id uuid,
  p_booking_date date
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
  v_is_active boolean;
  v_day_of_week integer;
  v_target_dow integer;
  v_membership_id uuid;
BEGIN
  v_caller_id := auth.uid();
  IF v_caller_id IS NULL THEN
    RETURN jsonb_build_object('success', false, 'error', 'Yetkisiz erişim: Oturum açmanız gerekmektedir.');
  END IF;

  IF p_class_id IS NULL OR p_booking_date IS NULL THEN
    RETURN jsonb_build_object('success', false, 'error', 'Ders kimliği ve rezervasyon tarihi zorunludur.');
  END IF;

  -- 1. Lock group_classes row FIRST (Hierarchical lock boundary)
  SELECT capacity, gym_id, is_active, day_of_week
  INTO v_capacity, v_gym_id, v_is_active, v_day_of_week
  FROM public.group_classes
  WHERE id = p_class_id
  FOR UPDATE;

  IF v_capacity IS NULL THEN
    RETURN jsonb_build_object('success', false, 'error', 'Ders bulunamadı.');
  END IF;

  IF v_gym_id IS NULL THEN
    RETURN jsonb_build_object('success', false, 'error', 'Dersin salon bilgisi bulunamadı.');
  END IF;

  IF v_is_active = false THEN
    RETURN jsonb_build_object('success', false, 'error', 'Bu ders aktif değildir.');
  END IF;

  -- 2. Validate caller authorization
  IF p_user_id IS NOT NULL AND p_user_id <> v_caller_id THEN
    -- Caller is acting on behalf of another member: must be super admin OR manager for this exact class's gym
    IF NOT (public.is_super_admin() OR public.is_manager_for_gym(v_gym_id)) THEN
      RETURN jsonb_build_object('success', false, 'error', 'Bu spor salonu veya ders için üye adına işlem yapma yetkiniz yok.');
    END IF;
    v_effective_user_id := p_user_id;
  ELSE
    v_effective_user_id := v_caller_id;
  END IF;

  -- 3. Validate target member has active membership covering this gym and occurrence date
  IF NOT (public.is_super_admin()) THEN
    SELECT id INTO v_membership_id
    FROM public.user_memberships
    WHERE user_id = v_effective_user_id
      AND gym_id = v_gym_id
      AND is_active = true
      AND (start_date IS NULL OR start_date <= p_booking_date)
      AND (end_date IS NULL OR end_date >= p_booking_date)
    LIMIT 1;

    IF v_membership_id IS NULL THEN
      RETURN jsonb_build_object(
        'success', false,
        'error', 'Üyenin bu dersin salonu ve seçilen tarihi kapsayan aktif bir üyeliği bulunmamaktadır.'
      );
    END IF;
  END IF;

  -- 4. Validate date corresponds to class occurrence schedule
  IF v_day_of_week IS NOT NULL THEN
    v_target_dow := EXTRACT(DOW FROM p_booking_date)::integer;
    IF v_day_of_week <> v_target_dow THEN
      RETURN jsonb_build_object('success', false, 'error', 'Seçilen tarih dersin haftalık günüyle uyuşmuyor.');
    END IF;
  ELSE
    RETURN jsonb_build_object('success', false, 'error', 'Ders için geçerli bir gün planı bulunmamaktadır.');
  END IF;

  -- 5. Lock existing booking row SECOND (consistent hierarchy: class -> booking)
  -- Deterministic selection handles any historical duplicates safely
  SELECT id, status INTO v_existing_booking
  FROM public.class_bookings
  WHERE class_id = p_class_id
    AND user_id = v_effective_user_id
    AND booking_date = p_booking_date
  ORDER BY created_at DESC NULLS LAST, id
  LIMIT 1
  FOR UPDATE;

  IF v_existing_booking.id IS NOT NULL AND v_existing_booking.status IN ('booked', 'confirmed', 'waiting', 'waitlist') THEN
    RETURN jsonb_build_object(
      'success', true,
      'booking_id', v_existing_booking.id,
      'status', CASE WHEN v_existing_booking.status IN ('waiting', 'waitlist') THEN 'waiting' ELSE 'booked' END,
      'is_waiting', v_existing_booking.status IN ('waiting', 'waitlist'),
      'booking_date', p_booking_date,
      'message', 'Bu ders için seçilen tarihte zaten kaydınız bulunmaktadır.'
    );
  END IF;

  -- 6. Count active bookings for the specified occurrence date
  SELECT COUNT(*) INTO v_active_count
  FROM public.class_bookings
  WHERE class_id = p_class_id
    AND booking_date = p_booking_date
    AND status IN ('booked', 'confirmed');

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
    'is_waiting', (v_status = 'waiting'),
    'booking_date', p_booking_date,
    'message', CASE WHEN v_status = 'booked' THEN 'Ders kaydınız başarıyla oluşturuldu.' ELSE 'Ders dolu olduğu için bekleme listesine alındınız.' END
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.atomic_cancel_group_class_v2(
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
  v_initial_booking record;
  v_booking record;
  v_next_waiting record;
  v_promoted_id uuid := NULL;
  v_gym_id uuid;
  v_capacity integer;
BEGIN
  v_caller_id := auth.uid();
  IF v_caller_id IS NULL THEN
    RETURN jsonb_build_object('success', false, 'error', 'Yetkisiz erişim.');
  END IF;

  IF p_booking_id IS NULL THEN
    RETURN jsonb_build_object('success', false, 'error', 'Rezervasyon kimliği zorunludur.');
  END IF;

  -- 1. Unlocked lookup solely to discover class_id
  SELECT id, class_id INTO v_initial_booking
  FROM public.class_bookings
  WHERE id = p_booking_id;

  IF v_initial_booking.id IS NULL THEN
    RETURN jsonb_build_object('success', false, 'error', 'Rezervasyon bulunamadı.');
  END IF;

  -- 2. Lock parent group_classes row FIRST (Hierarchical lock boundary: class first)
  SELECT capacity, gym_id INTO v_capacity, v_gym_id
  FROM public.group_classes
  WHERE id = v_initial_booking.class_id
  FOR UPDATE;

  -- Check parent class existence
  IF v_capacity IS NULL THEN
    RETURN jsonb_build_object('success', false, 'error', 'Ders bulunamadı.');
  END IF;

  -- 3. Re-read and lock target booking row SECOND (class -> booking)
  SELECT id, user_id, class_id, status, booking_date
  INTO v_booking
  FROM public.class_bookings
  WHERE id = p_booking_id
  FOR UPDATE;

  IF v_booking.id IS NULL THEN
    RETURN jsonb_build_object('success', false, 'error', 'Rezervasyon bulunamadı.');
  END IF;

  -- Revalidate class_id has not changed between initial discovery and locked re-read
  IF v_booking.class_id IS DISTINCT FROM v_initial_booking.class_id THEN
    RETURN jsonb_build_object('success', false, 'error', 'Rezervasyon dersi değişti, işlem iptal edildi.');
  END IF;

  -- 4. Revalidate p_user_id if provided
  IF p_user_id IS NOT NULL AND p_user_id <> v_booking.user_id THEN
    RETURN jsonb_build_object('success', false, 'error', 'Rezervasyon belirtilen üyeye ait değil.');
  END IF;

  -- 5. Revalidate caller authorization: member self, super admin, or gym manager for this class's gym
  IF NOT (
    v_booking.user_id = v_caller_id
    OR public.is_super_admin()
    OR (v_gym_id IS NOT NULL AND public.is_manager_for_gym(v_gym_id))
  ) THEN
    RETURN jsonb_build_object('success', false, 'error', 'Bu rezervasyonu iptal etme yetkiniz yok.');
  END IF;

  -- 6. Check if already cancelled (idempotent double-cancel protection)
  IF v_booking.status = 'cancelled' THEN
    RETURN jsonb_build_object(
      'success', true,
      'booking_id', p_booking_id,
      'booking_date', v_booking.booking_date,
      'message', 'Rezervasyon zaten iptal edilmiş.'
    );
  END IF;

  -- 7. Update status to cancelled (DO NOT DELETE)
  UPDATE public.class_bookings
  SET status = 'cancelled'
  WHERE id = p_booking_id;

  -- 8. If was booked/confirmed, lock and promote the oldest waiting waiter THIRD (class -> booking -> waiter)
  IF v_booking.status IN ('booked', 'confirmed') AND v_booking.booking_date IS NOT NULL AND v_booking.class_id IS NOT NULL THEN
    SELECT id, user_id, status INTO v_next_waiting
    FROM public.class_bookings
    WHERE class_id = v_booking.class_id
      AND booking_date = v_booking.booking_date
      AND status IN ('waiting', 'waitlist')
    ORDER BY created_at ASC NULLS LAST, id ASC
    LIMIT 1
    FOR UPDATE;

    -- Update only if still waiting/waitlist
    IF v_next_waiting.id IS NOT NULL AND v_next_waiting.status IN ('waiting', 'waitlist') THEN
      UPDATE public.class_bookings
      SET status = 'booked'
      WHERE id = v_next_waiting.id;

      v_promoted_id := v_next_waiting.id;

      -- Best-effort notification: failure must NOT roll back valid cancellation or promotion
      BEGIN
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
      EXCEPTION
        WHEN others THEN
          RAISE NOTICE 'Notification insert skipped on error: %', SQLERRM;
      END;
    END IF;
  END IF;

  RETURN jsonb_build_object(
    'success', true,
    'booking_id', p_booking_id,
    'promoted_booking_id', v_promoted_id,
    'booking_date', v_booking.booking_date,
    'message', 'Rezervasyon iptal edildi.'
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.atomic_book_group_class_v2 TO authenticated;
GRANT EXECUTE ON FUNCTION public.atomic_cancel_group_class_v2 TO authenticated;
