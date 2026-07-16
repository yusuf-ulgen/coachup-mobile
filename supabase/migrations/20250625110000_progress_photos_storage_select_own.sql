-- Run if you already applied 20250625100000_progress_photos_storage.sql
-- Storage DELETE requires SELECT on own objects for authenticated users.

DROP POLICY IF EXISTS "progress_photos_storage_select_own" ON storage.objects;
CREATE POLICY "progress_photos_storage_select_own"
  ON storage.objects FOR SELECT TO authenticated
  USING (
    bucket_id = 'progress-photos'
    AND (storage.foldername(name))[1] = auth.uid()::text
  );
