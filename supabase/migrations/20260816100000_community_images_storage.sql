-- Community images: storage bucket + RLS policies
-- Fixes `Bucket not found` error when uploading images on community posts.

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'community-images',
  'community-images',
  true,
  10485760,
  ARRAY['image/jpeg', 'image/png', 'image/webp', 'image/jpg', 'image/gif']
)
ON CONFLICT (id) DO UPDATE SET
  public = EXCLUDED.public,
  file_size_limit = EXCLUDED.file_size_limit,
  allowed_mime_types = EXCLUDED.allowed_mime_types;

DROP POLICY IF EXISTS "community_images_storage_insert" ON storage.objects;
CREATE POLICY "community_images_storage_insert"
  ON storage.objects FOR INSERT TO authenticated
  WITH CHECK (
    bucket_id = 'community-images'
    AND (storage.foldername(name))[1] = auth.uid()::text
  );

DROP POLICY IF EXISTS "community_images_storage_select" ON storage.objects;
CREATE POLICY "community_images_storage_select"
  ON storage.objects FOR SELECT TO public
  USING (bucket_id = 'community-images');

DROP POLICY IF EXISTS "community_images_storage_update" ON storage.objects;
CREATE POLICY "community_images_storage_update"
  ON storage.objects FOR UPDATE TO authenticated
  USING (
    bucket_id = 'community-images'
    AND (storage.foldername(name))[1] = auth.uid()::text
  );

DROP POLICY IF EXISTS "community_images_storage_delete" ON storage.objects;
CREATE POLICY "community_images_storage_delete"
  ON storage.objects FOR DELETE TO authenticated
  USING (
    bucket_id = 'community-images'
    AND (storage.foldername(name))[1] = auth.uid()::text
  );
