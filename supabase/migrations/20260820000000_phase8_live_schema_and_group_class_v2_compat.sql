-- ══════════════════════════════════════════════════════════════════════════════
-- Phase 8: Live Schema Alignment & Group Class V2 Compatibility
-- ══════════════════════════════════════════════════════════════════════════════

-- 1. ADDITIVE COLUMNS (Idempotent schema alignment)

-- user_memberships canonical columns
ALTER TABLE public.user_memberships 
  ADD COLUMN IF NOT EXISTS plan_id uuid REFERENCES public.membership_plans(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS is_active boolean DEFAULT true,
  ADD COLUMN IF NOT EXISTS auto_renew boolean DEFAULT false,
  ADD COLUMN IF NOT EXISTS payment_status text DEFAULT 'paid',
  ADD COLUMN IF NOT EXISTS total_price numeric,
  ADD COLUMN IF NOT EXISTS notes text,
  ADD COLUMN IF NOT EXISTS coach_id uuid REFERENCES public.coaches(id) ON DELETE SET NULL;

-- group_classes canonical columns
ALTER TABLE public.group_classes 
  ADD COLUMN IF NOT EXISTS day_of_week integer,
  ADD COLUMN IF NOT EXISTS is_active boolean DEFAULT true,
  ADD COLUMN IF NOT EXISTS class_type text,
  ADD COLUMN IF NOT EXISTS instructor_name text;

-- class_bookings canonical occurrence column & index
ALTER TABLE public.class_bookings 
  ADD COLUMN IF NOT EXISTS booking_date date;

CREATE INDEX IF NOT EXISTS idx_class_bookings_occurrence 
  ON public.class_bookings(class_id, booking_date, status);

-- users canonical profile, address & account-synced settings columns
ALTER TABLE public.users 
  ADD COLUMN IF NOT EXISTS height numeric,
  ADD COLUMN IF NOT EXISTS weight numeric,
  ADD COLUMN IF NOT EXISTS is_individual boolean DEFAULT false,
  ADD COLUMN IF NOT EXISTS address_title text,
  ADD COLUMN IF NOT EXISTS city text,
  ADD COLUMN IF NOT EXISTS district text,
  ADD COLUMN IF NOT EXISTS neighborhood text,
  ADD COLUMN IF NOT EXISTS street text,
  ADD COLUMN IF NOT EXISTS building_no text,
  ADD COLUMN IF NOT EXISTS door_no text,
  ADD COLUMN IF NOT EXISTS postal_code text,
  ADD COLUMN IF NOT EXISTS default_screen text,
  ADD COLUMN IF NOT EXISTS notifications_enabled boolean DEFAULT true,
  ADD COLUMN IF NOT EXISTS biometrics_enabled boolean DEFAULT false,
  ADD COLUMN IF NOT EXISTS weight_unit text DEFAULT 'kg',
  ADD COLUMN IF NOT EXISTS theme_mode text DEFAULT 'dark',
  ADD COLUMN IF NOT EXISTS profile_image_url text;

-- 2. HELPER FUNCTIONS (Preserve/ensure super_admin and manager helpers)
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

-- 3. FINALIZED LIVE-COMPATIBLE GROUP CLASS V2 ATOMIC RPCS
-- Uses user_memberships.is_active (NOT status)
-- Uses group_classes.day_of_week (NOT date_str)
-- Preserves hierarchical lock order: group_classes -> class_bookings -> waiter

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
  SELECT id, status INTO v_existing_booking
  FROM public.class_bookings
  WHERE class_id = p_class_id
    AND user_id = v_effective_user_id
    AND booking_date = p_booking_date
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
BEGIN
  v_caller_id := auth.uid();
  IF v_caller_id IS NULL THEN
    RETURN jsonb_build_object('success', false, 'error', 'Yetkisiz erişim.');
  END IF;

  IF p_booking_id IS NULL THEN
    RETURN jsonb_build_object('success', false, 'error', 'Rezervasyon kimliği zorunludur.');
  END IF;

  -- 1. Unlocked lookup solely to resolve class_id
  SELECT id, class_id INTO v_initial_booking
  FROM public.class_bookings
  WHERE id = p_booking_id;

  IF v_initial_booking.id IS NULL THEN
    RETURN jsonb_build_object('success', false, 'error', 'Rezervasyon bulunamadı.');
  END IF;

  -- 2. Lock parent group_classes row FIRST (Hierarchical lock boundary: class first)
  SELECT gym_id INTO v_gym_id
  FROM public.group_classes
  WHERE id = v_initial_booking.class_id
  FOR UPDATE;

  -- 3. Re-read and lock target booking row SECOND (class -> booking)
  SELECT id, user_id, class_id, status, booking_date
  INTO v_booking
  FROM public.class_bookings
  WHERE id = p_booking_id
  FOR UPDATE;

  IF v_booking.id IS NULL THEN
    RETURN jsonb_build_object('success', false, 'error', 'Rezervasyon bulunamadı.');
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
    SELECT id, user_id INTO v_next_waiting
    FROM public.class_bookings
    WHERE class_id = v_booking.class_id
      AND booking_date = v_booking.booking_date
      AND status IN ('waiting', 'waitlist')
    ORDER BY created_at ASC
    LIMIT 1
    FOR UPDATE;

    IF v_next_waiting.id IS NOT NULL THEN
      UPDATE public.class_bookings
      SET status = 'booked'
      WHERE id = v_next_waiting.id;

      v_promoted_id := v_next_waiting.id;

      -- Insert notification only upon actual promotion
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
