-- Fix training_programs privacy constraint to include 'members' option
-- The admin panel uses 'members' to assign programs to specific members
-- Run this in Supabase SQL Editor

-- 1. Update the constraint to allow 'members' as privacy value
ALTER TABLE training_programs
    DROP CONSTRAINT IF EXISTS training_programs_privacy_check;

ALTER TABLE training_programs
    ADD CONSTRAINT training_programs_privacy_check
    CHECK (privacy IN ('public', 'private', 'members'));

-- 2. Drop and recreate the member reading policy to handle all 3 privacy types
DROP POLICY IF EXISTS "Members can view visible gym programs" ON training_programs;
CREATE POLICY "Members can view visible gym programs"
  ON training_programs FOR SELECT
  USING (
    is_active = true
    AND (
      -- Public program - all gym members can see
      privacy = 'public'
      OR
      -- Members-only: assigned to specific members via visible_member_ids
      (privacy = 'members' AND auth.uid() = ANY (visible_member_ids))
      OR
      -- Private program assigned to specific member
      (privacy = 'private' AND auth.uid() = ANY (visible_member_ids))
    )
  );
