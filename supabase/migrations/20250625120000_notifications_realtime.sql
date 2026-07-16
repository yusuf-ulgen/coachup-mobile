-- Enable Supabase Realtime for the notifications table so INSERT events
-- reach the Android app while it is open or in background.
-- Run in Supabase Dashboard → SQL Editor if push-in-app is not working.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime'
          AND schemaname = 'public'
          AND tablename = 'notifications'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.notifications;
    END IF;
END $$;
