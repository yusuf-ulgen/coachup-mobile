-- Community Comments and Polls Migration
-- Run against shared Supabase project used by CoachUP mobile + web.

-- ---------------------------------------------------------------------------
-- Comments
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS community_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
    author_id UUID NOT NULL,
    content TEXT NOT NULL CONSTRAINT comment_content_check CHECK (length(trim(content)) > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS community_comments_post_idx ON community_comments(post_id);

-- ---------------------------------------------------------------------------
-- Polls
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS community_polls (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
    question TEXT NOT NULL CONSTRAINT poll_question_check CHECK (length(trim(question)) > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(post_id)
);

CREATE TABLE IF NOT EXISTS community_poll_options (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    poll_id UUID NOT NULL REFERENCES community_polls(id) ON DELETE CASCADE,
    option_text TEXT NOT NULL CONSTRAINT option_text_check CHECK (length(trim(option_text)) > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS community_poll_options_poll_idx ON community_poll_options(poll_id);

CREATE TABLE IF NOT EXISTS community_poll_votes (
    poll_id UUID NOT NULL REFERENCES community_polls(id) ON DELETE CASCADE,
    option_id UUID NOT NULL REFERENCES community_poll_options(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (poll_id, user_id)
);

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------
ALTER TABLE community_comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE community_polls ENABLE ROW LEVEL SECURITY;
ALTER TABLE community_poll_options ENABLE ROW LEVEL SECURITY;
ALTER TABLE community_poll_votes ENABLE ROW LEVEL SECURITY;

-- Comments Policies
DROP POLICY IF EXISTS community_comments_select ON community_comments;
CREATE POLICY community_comments_select ON community_comments
    FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS community_comments_insert ON community_comments;
CREATE POLICY community_comments_insert ON community_comments
    FOR INSERT TO authenticated WITH CHECK (author_id = auth.uid());

DROP POLICY IF EXISTS community_comments_delete ON community_comments;
CREATE POLICY community_comments_delete ON community_comments
    FOR DELETE TO authenticated USING (author_id = auth.uid());

-- Polls Policies
DROP POLICY IF EXISTS community_polls_select ON community_polls;
CREATE POLICY community_polls_select ON community_polls
    FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS community_polls_insert ON community_polls;
CREATE POLICY community_polls_insert ON community_polls
    FOR INSERT TO authenticated WITH CHECK (
        EXISTS (
            SELECT 1 FROM community_posts p
            WHERE p.id = post_id AND p.author_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS community_polls_delete ON community_polls;
CREATE POLICY community_polls_delete ON community_polls
    FOR DELETE TO authenticated USING (
        EXISTS (
            SELECT 1 FROM community_posts p
            WHERE p.id = post_id AND p.author_id = auth.uid()
        )
    );

-- Poll Options Policies
DROP POLICY IF EXISTS community_poll_options_select ON community_poll_options;
CREATE POLICY community_poll_options_select ON community_poll_options
    FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS community_poll_options_insert ON community_poll_options;
CREATE POLICY community_poll_options_insert ON community_poll_options
    FOR INSERT TO authenticated WITH CHECK (true);

DROP POLICY IF EXISTS community_poll_options_delete ON community_poll_options;
CREATE POLICY community_poll_options_delete ON community_poll_options
    FOR DELETE TO authenticated USING (true);

-- Poll Votes Policies
DROP POLICY IF EXISTS community_poll_votes_select ON community_poll_votes;
CREATE POLICY community_poll_votes_select ON community_poll_votes
    FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS community_poll_votes_insert ON community_poll_votes;
CREATE POLICY community_poll_votes_insert ON community_poll_votes
    FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS community_poll_votes_delete ON community_poll_votes;
CREATE POLICY community_poll_votes_delete ON community_poll_votes
    FOR DELETE TO authenticated USING (user_id = auth.uid());
