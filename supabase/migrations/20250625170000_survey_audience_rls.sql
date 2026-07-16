-- Anketler: bireysel (platform) ve salon kapsamları birbirinden ayrılır.
-- gym_id IS NULL  → süper admin / platform anketleri (bireysel kullanıcılar)
-- gym_id NOT NULL → salon anketleri (yalnızca ilgili salon üyeleri)

CREATE OR REPLACE FUNCTION public.user_survey_gym_ids(uid uuid)
RETURNS SETOF uuid
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT u.gym_id
  FROM users u
  WHERE u.id = uid AND u.gym_id IS NOT NULL
  UNION
  SELECT mp.gym_id
  FROM user_memberships um
  JOIN membership_plans mp ON mp.id = um.plan_id
  WHERE um.user_id = uid
    AND um.is_active = true
    AND mp.gym_id IS NOT NULL;
$$;

CREATE OR REPLACE FUNCTION public.is_individual_app_user(uid uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1 FROM users u
    WHERE u.id = uid AND lower(coalesce(u.role, '')) = 'individual'
  )
  OR (
    NOT EXISTS (SELECT 1 FROM public.user_survey_gym_ids(uid) AS gym_id)
    AND EXISTS (
      SELECT 1 FROM users u
      WHERE u.id = uid AND u.gym_id IS NULL
    )
  );
$$;

DROP POLICY IF EXISTS "Users can view active surveys for their gym" ON surveys;

CREATE POLICY "Users can view platform surveys"
  ON surveys FOR SELECT
  USING (
    status = 'active'
    AND end_date >= CURRENT_DATE
    AND gym_id IS NULL
    AND public.is_individual_app_user(auth.uid())
  );

CREATE POLICY "Users can view their gym surveys"
  ON surveys FOR SELECT
  USING (
    status = 'active'
    AND end_date >= CURRENT_DATE
    AND gym_id IS NOT NULL
    AND gym_id IN (SELECT public.user_survey_gym_ids(auth.uid()))
  );
