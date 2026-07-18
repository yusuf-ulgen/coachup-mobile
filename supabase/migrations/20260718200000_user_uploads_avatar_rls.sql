-- user-uploads storage bucket: RLS policies for avatar uploads
-- Run this in Supabase SQL Editor

-- Ensure the user-uploads bucket exists and is public
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'user-uploads',
  'user-uploads',
  true,
  5242880,
  ARRAY['image/jpeg', 'image/png', 'image/webp', 'image/jpg']
)
ON CONFLICT (id) DO UPDATE SET
  public = EXCLUDED.public,
  file_size_limit = EXCLUDED.file_size_limit,
  allowed_mime_types = EXCLUDED.allowed_mime_types;

-- Allow public SELECT (read) on user-uploads bucket
DROP POLICY IF EXISTS "user_uploads_public_read" ON storage.objects;
CREATE POLICY "user_uploads_public_read"
  ON storage.objects FOR SELECT TO public
  USING (bucket_id = 'user-uploads');

-- Allow authenticated users to INSERT into avatars/ folder
DROP POLICY IF EXISTS "user_uploads_avatar_insert" ON storage.objects;
CREATE POLICY "user_uploads_avatar_insert"
  ON storage.objects FOR INSERT TO authenticated
  WITH CHECK (
    bucket_id = 'user-uploads'
    AND (storage.foldername(name))[1] = 'avatars'
  );

-- Allow authenticated users to UPDATE their own avatars
DROP POLICY IF EXISTS "user_uploads_avatar_update" ON storage.objects;
CREATE POLICY "user_uploads_avatar_update"
  ON storage.objects FOR UPDATE TO authenticated
  USING (
    bucket_id = 'user-uploads'
    AND (storage.foldername(name))[1] = 'avatars'
  );

-- Allow authenticated users to DELETE their own avatars
DROP POLICY IF EXISTS "user_uploads_avatar_delete" ON storage.objects;
CREATE POLICY "user_uploads_avatar_delete"
  ON storage.objects FOR DELETE TO authenticated
  USING (
    bucket_id = 'user-uploads'
    AND (storage.foldername(name))[1] = 'avatars'
  );
