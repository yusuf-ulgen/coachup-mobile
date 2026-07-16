-- Community / Topluluk feed, groups, settings
-- Run against shared Supabase project used by CoachUP mobile + web.

-- ---------------------------------------------------------------------------
-- Global / gym feature toggles
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS community_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scope TEXT NOT NULL CHECK (scope IN ('global', 'gym')),
    gym_id UUID NULL,
    general_enabled BOOLEAN NOT NULL DEFAULT true,
    gym_feed_enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS community_settings_global_uidx
    ON community_settings ((scope))
    WHERE scope = 'global';

CREATE UNIQUE INDEX IF NOT EXISTS community_settings_gym_uidx
    ON community_settings (gym_id)
    WHERE scope = 'gym' AND gym_id IS NOT NULL;

INSERT INTO community_settings (scope, gym_id, general_enabled, gym_feed_enabled)
SELECT 'global', NULL, true, true
WHERE NOT EXISTS (SELECT 1 FROM community_settings WHERE scope = 'global');

-- ---------------------------------------------------------------------------
-- Subgroups (under general app community or a gym community)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS community_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scope TEXT NOT NULL CHECK (scope IN ('gym', 'general')),
    gym_id UUID NULL,
    name TEXT NOT NULL,
    description TEXT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_by UUID NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS community_groups_scope_gym_idx
    ON community_groups (scope, gym_id, is_active);

CREATE TABLE IF NOT EXISTS community_group_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES community_groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    role TEXT NOT NULL DEFAULT 'member',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (group_id, user_id)
);

CREATE INDEX IF NOT EXISTS community_group_members_user_idx
    ON community_group_members (user_id);

-- ---------------------------------------------------------------------------
-- Posts
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS community_posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id UUID NOT NULL,
    scope TEXT NOT NULL CHECK (scope IN ('gym', 'general', 'group')),
    gym_id UUID NULL,
    group_id UUID NULL REFERENCES community_groups(id) ON DELETE SET NULL,
    content TEXT NULL,
    image_url TEXT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT community_posts_has_body CHECK (
        (content IS NOT NULL AND length(trim(content)) > 0)
        OR (image_url IS NOT NULL AND length(trim(image_url)) > 0)
    )
);

CREATE INDEX IF NOT EXISTS community_posts_feed_idx
    ON community_posts (scope, gym_id, group_id, created_at DESC)
    WHERE is_active = true;

CREATE TABLE IF NOT EXISTS community_likes (
    post_id UUID NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id)
);

-- ---------------------------------------------------------------------------
-- Storage bucket for post images
-- ---------------------------------------------------------------------------
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'community-images',
    'community-images',
    true,
    5242880,
    ARRAY['image/jpeg', 'image/png', 'image/webp']
)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------
ALTER TABLE community_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE community_groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE community_group_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE community_posts ENABLE ROW LEVEL SECURITY;
ALTER TABLE community_likes ENABLE ROW LEVEL SECURITY;

-- Settings: authenticated read; only admins write (app-layer also checks role)
DROP POLICY IF EXISTS community_settings_select ON community_settings;
CREATE POLICY community_settings_select ON community_settings
    FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS community_settings_all_admin ON community_settings;
CREATE POLICY community_settings_all_admin ON community_settings
    FOR ALL TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM users u
            WHERE u.id = auth.uid() AND u.role IN ('admin', 'gym_manager')
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM users u
            WHERE u.id = auth.uid() AND u.role IN ('admin', 'gym_manager')
        )
    );

-- Groups: readable by authenticated; manage by admin/gym_manager
DROP POLICY IF EXISTS community_groups_select ON community_groups;
CREATE POLICY community_groups_select ON community_groups
    FOR SELECT TO authenticated USING (is_active = true OR true);

DROP POLICY IF EXISTS community_groups_write ON community_groups;
CREATE POLICY community_groups_write ON community_groups
    FOR ALL TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM users u
            WHERE u.id = auth.uid() AND u.role IN ('admin', 'gym_manager')
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM users u
            WHERE u.id = auth.uid() AND u.role IN ('admin', 'gym_manager')
        )
    );

-- Group members
DROP POLICY IF EXISTS community_group_members_select ON community_group_members;
CREATE POLICY community_group_members_select ON community_group_members
    FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS community_group_members_write ON community_group_members;
CREATE POLICY community_group_members_write ON community_group_members
    FOR ALL TO authenticated
    USING (
        user_id = auth.uid()
        OR EXISTS (
            SELECT 1 FROM users u
            WHERE u.id = auth.uid() AND u.role IN ('admin', 'gym_manager')
        )
    )
    WITH CHECK (
        user_id = auth.uid()
        OR EXISTS (
            SELECT 1 FROM users u
            WHERE u.id = auth.uid() AND u.role IN ('admin', 'gym_manager')
        )
    );

-- Posts: authenticated can read active; authors can insert/update own
DROP POLICY IF EXISTS community_posts_select ON community_posts;
CREATE POLICY community_posts_select ON community_posts
    FOR SELECT TO authenticated USING (is_active = true OR author_id = auth.uid());

DROP POLICY IF EXISTS community_posts_insert ON community_posts;
CREATE POLICY community_posts_insert ON community_posts
    FOR INSERT TO authenticated
    WITH CHECK (author_id = auth.uid());

DROP POLICY IF EXISTS community_posts_update ON community_posts;
CREATE POLICY community_posts_update ON community_posts
    FOR UPDATE TO authenticated
    USING (
        author_id = auth.uid()
        OR EXISTS (
            SELECT 1 FROM users u
            WHERE u.id = auth.uid() AND u.role IN ('admin', 'gym_manager')
        )
    );

DROP POLICY IF EXISTS community_posts_delete ON community_posts;
CREATE POLICY community_posts_delete ON community_posts
    FOR DELETE TO authenticated
    USING (
        author_id = auth.uid()
        OR EXISTS (
            SELECT 1 FROM users u
            WHERE u.id = auth.uid() AND u.role IN ('admin', 'gym_manager')
        )
    );

-- Likes
DROP POLICY IF EXISTS community_likes_select ON community_likes;
CREATE POLICY community_likes_select ON community_likes
    FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS community_likes_write ON community_likes;
CREATE POLICY community_likes_write ON community_likes
    FOR ALL TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

-- Storage policies for community-images
DROP POLICY IF EXISTS community_images_public_read ON storage.objects;
CREATE POLICY community_images_public_read ON storage.objects
    FOR SELECT TO public
    USING (bucket_id = 'community-images');

DROP POLICY IF EXISTS community_images_auth_insert ON storage.objects;
CREATE POLICY community_images_auth_insert ON storage.objects
    FOR INSERT TO authenticated
    WITH CHECK (bucket_id = 'community-images' AND (storage.foldername(name))[1] = auth.uid()::text);

DROP POLICY IF EXISTS community_images_auth_delete ON storage.objects;
CREATE POLICY community_images_auth_delete ON storage.objects
    FOR DELETE TO authenticated
    USING (bucket_id = 'community-images' AND (storage.foldername(name))[1] = auth.uid()::text);
