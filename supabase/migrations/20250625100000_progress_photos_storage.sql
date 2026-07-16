-- Progress photos: storage bucket + RLS (run in Supabase SQL Editor if not using CLI)

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'progress-photos',
  'progress-photos',
  true,
  5242880,
  ARRAY['image/jpeg', 'image/png', 'image/webp', 'image/jpg']
)
ON CONFLICT (id) DO UPDATE SET
  public = EXCLUDED.public,
  file_size_limit = EXCLUDED.file_size_limit,
  allowed_mime_types = EXCLUDED.allowed_mime_types;

-- Allow before/after photo types (admin panel + mobile app)
ALTER TABLE public.progress_photos
  DROP CONSTRAINT IF EXISTS progress_photos_photo_type_check;

ALTER TABLE public.progress_photos
  ADD CONSTRAINT progress_photos_photo_type_check
  CHECK (photo_type IN ('front', 'back', 'side_left', 'side_right', 'before', 'after'));

ALTER TABLE public.progress_photos ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "progress_photos_select_own" ON public.progress_photos;
CREATE POLICY "progress_photos_select_own"
  ON public.progress_photos FOR SELECT TO authenticated
  USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "progress_photos_insert_own" ON public.progress_photos;
CREATE POLICY "progress_photos_insert_own"
  ON public.progress_photos FOR INSERT TO authenticated
  WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "progress_photos_delete_own" ON public.progress_photos;
CREATE POLICY "progress_photos_delete_own"
  ON public.progress_photos FOR DELETE TO authenticated
  USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "progress_photos_storage_insert" ON storage.objects;
CREATE POLICY "progress_photos_storage_insert"
  ON storage.objects FOR INSERT TO authenticated
  WITH CHECK (
    bucket_id = 'progress-photos'
    AND (storage.foldername(name))[1] = auth.uid()::text
  );

DROP POLICY IF EXISTS "progress_photos_storage_select" ON storage.objects;
CREATE POLICY "progress_photos_storage_select"
  ON storage.objects FOR SELECT TO public
  USING (bucket_id = 'progress-photos');

-- Required for authenticated DELETE on storage.objects (must be able to SELECT own rows)
DROP POLICY IF EXISTS "progress_photos_storage_select_own" ON storage.objects;
CREATE POLICY "progress_photos_storage_select_own"
  ON storage.objects FOR SELECT TO authenticated
  USING (
    bucket_id = 'progress-photos'
    AND (storage.foldername(name))[1] = auth.uid()::text
  );

DROP POLICY IF EXISTS "progress_photos_storage_update" ON storage.objects;
CREATE POLICY "progress_photos_storage_update"
  ON storage.objects FOR UPDATE TO authenticated
  USING (
    bucket_id = 'progress-photos'
    AND (storage.foldername(name))[1] = auth.uid()::text
  );

DROP POLICY IF EXISTS "progress_photos_storage_delete" ON storage.objects;
CREATE POLICY "progress_photos_storage_delete"
  ON storage.objects FOR DELETE TO authenticated
  USING (
    bucket_id = 'progress-photos'
    AND (storage.foldername(name))[1] = auth.uid()::text
  );
