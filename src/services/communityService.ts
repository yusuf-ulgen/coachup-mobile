import { supabase } from './supabaseClient';
import { UserService } from './userService';

export interface CommunityComment {
  id: string;
  post_id: string;
  author_id: string;
  author_name: string;
  author_avatar_url?: string;
  content: string;
  created_at: string;
}

export interface PollOptionUi {
  id: string;
  poll_id: string;
  option_text: string;
  vote_count: number;
  percentage: number;
}

export interface PollUi {
  id: string;
  post_id: string;
  question: string;
  options: PollOptionUi[];
  my_vote_option_id?: string;
  total_votes: number;
}

export interface CommunityPost {
  id: string;
  author_id: string;
  author_name: string;
  author_avatar_url?: string;
  scope: string;
  gym_id?: string;
  group_id?: string;
  content: string;
  image_url?: string;
  likes_count: number;
  comments_count: number;
  liked_by_me: boolean;
  poll?: PollUi;
  created_at: string;
}

export const CommunityService = {
  async fetchFeed(userId?: string, scope: string = 'general', gymId?: string, groupId?: string): Promise<CommunityPost[]> {
    try {
      let query = supabase
        .from('community_posts')
        .select(`
          *,
          author:users!author_id(id, name, surname, email, avatar_url, profile_image_url)
        `)
        .eq('is_active', true)
        .order('created_at', { ascending: false });

      if (scope === 'gym' && gymId) {
        query = query.eq('scope', 'gym').eq('gym_id', gymId);
      } else {
        query = query.eq('scope', 'general');
      }

      if (groupId) {
        query = query.eq('group_id', groupId);
      }

      let { data: rawPosts, error } = await query;

      if (error || !rawPosts) {
        let fallbackQuery = supabase
          .from('community_posts')
          .select('*')
          .eq('is_active', true)
          .order('created_at', { ascending: false });

        if (scope === 'gym' && gymId) {
          fallbackQuery = fallbackQuery.eq('scope', 'gym').eq('gym_id', gymId);
        } else {
          fallbackQuery = fallbackQuery.eq('scope', 'general');
        }

        if (groupId) {
          fallbackQuery = fallbackQuery.eq('group_id', groupId);
        }

        const { data: fallbackPosts } = await fallbackQuery;
        rawPosts = fallbackPosts || [];
      }

      if (!rawPosts || rawPosts.length === 0) return [];

      const postIds = rawPosts.map((p) => p.id);
      const authorIds = Array.from(new Set(rawPosts.map((p) => p.author_id).filter(Boolean)));

      // Fetch authors map if needed
      let authorsMap: Record<string, any> = {};
      if (authorIds.length > 0) {
        const { data: authors } = await supabase
          .from('users')
          .select('id, name, surname, email, avatar_url, profile_image_url')
          .in('id', authorIds);

        if (authors) {
          authors.forEach((a) => {
            authorsMap[a.id] = a;
          });
        }
      }

      // Fetch likes
      const { data: allLikes } = await supabase
        .from('community_likes')
        .select('post_id, user_id')
        .in('post_id', postIds);

      const likesByPost: Record<string, number> = {};
      const likedByMeSet = new Set<string>();
      (allLikes || []).forEach((like) => {
        likesByPost[like.post_id] = (likesByPost[like.post_id] || 0) + 1;
        if (userId && like.user_id === userId) {
          likedByMeSet.add(like.post_id);
        }
      });

      // Fetch comment counts
      const { data: allComments } = await supabase
        .from('community_comments')
        .select('post_id')
        .in('post_id', postIds);

      const commentsByPost: Record<string, number> = {};
      (allComments || []).forEach((c) => {
        commentsByPost[c.post_id] = (commentsByPost[c.post_id] || 0) + 1;
      });

      // Fetch Polls
      const { data: rawPolls } = await supabase
        .from('community_polls')
        .select('*')
        .in('post_id', postIds);

      let pollsByPost: Record<string, PollUi> = {};

      if (rawPolls && rawPolls.length > 0) {
        const pollIds = rawPolls.map((p) => p.id);

        const { data: rawOptions } = await supabase
          .from('community_poll_options')
          .select('*')
          .in('poll_id', pollIds);

        const { data: rawVotes } = await supabase
          .from('community_poll_votes')
          .select('*')
          .in('poll_id', pollIds);

        rawPolls.forEach((poll) => {
          const pollOpts = (rawOptions || []).filter((o) => o.poll_id === poll.id);
          const pollVotes = (rawVotes || []).filter((v) => v.poll_id === poll.id);
          const totalVotes = pollVotes.length;

          const voteCountsByOpt: Record<string, number> = {};
          let myVoteOptId: string | undefined = undefined;

          pollVotes.forEach((v) => {
            voteCountsByOpt[v.option_id] = (voteCountsByOpt[v.option_id] || 0) + 1;
            if (userId && v.user_id === userId) {
              myVoteOptId = v.option_id;
            }
          });

          const optionsUi: PollOptionUi[] = pollOpts.map((opt) => {
            const count = voteCountsByOpt[opt.id] || 0;
            const pct = totalVotes === 0 ? 0 : Math.round((count / totalVotes) * 100);
            return {
              id: opt.id,
              poll_id: opt.poll_id,
              option_text: opt.option_text,
              vote_count: count,
              percentage: pct,
            };
          });

          pollsByPost[poll.post_id] = {
            id: poll.id,
            post_id: poll.post_id,
            question: poll.question,
            options: optionsUi,
            my_vote_option_id: myVoteOptId,
            total_votes: totalVotes,
          };
        });
      }

      return rawPosts.map((post: any) => {
        const author = post.author || authorsMap[post.author_id];
        const name = author
          ? `${author.name || ''} ${author.surname || ''}`.trim() || author.email || 'Üye'
          : 'Üye';
        return {
          id: post.id,
          author_id: post.author_id,
          author_name: name,
          author_avatar_url: author?.profile_image_url || author?.avatar_url,
          scope: post.scope || 'general',
          gym_id: post.gym_id,
          group_id: post.group_id,
          content: post.content || '',
          image_url: post.image_url,
          likes_count: likesByPost[post.id] || 0,
          comments_count: commentsByPost[post.id] || 0,
          liked_by_me: likedByMeSet.has(post.id),
          poll: pollsByPost[post.id],
          created_at: post.created_at,
        };
      });
    } catch (e) {
      console.error('Error in CommunityService.fetchFeed:', e);
      return [];
    }
  },

  async toggleLike(postId: string, userId: string) {
    const { data: existing } = await supabase
      .from('community_likes')
      .select('*')
      .eq('post_id', postId)
      .eq('user_id', userId);

    if (existing && existing.length > 0) {
      await supabase
        .from('community_likes')
        .delete()
        .eq('post_id', postId)
        .eq('user_id', userId);
    } else {
      await supabase.from('community_likes').insert({
        post_id: postId,
        user_id: userId,
      });
    }
  },

  async votePoll(pollId: string, optionId: string, userId: string) {
    await supabase.from('community_poll_votes').delete().match({
      poll_id: pollId,
      user_id: userId,
    });

    await supabase.from('community_poll_votes').insert({
      poll_id: pollId,
      option_id: optionId,
      user_id: userId,
    });
  },

  async uploadImage(uri: string, userId: string): Promise<string> {
    const filename = `${userId}/${Date.now()}.jpg`;

    // Supabase JS client does NOT support FormData for storage uploads.
    // We must upload as ArrayBuffer. Fetch the local file URI and convert directly.
    const response = await fetch(uri);
    const arrayBuffer = await response.arrayBuffer();

    const { data, error } = await supabase.storage
      .from('community-images')
      .upload(filename, arrayBuffer, {
        contentType: 'image/jpeg',
        upsert: true,
      });

    if (error) {
      throw new Error(`Resim yükleme başarısız: ${error.message}`);
    }

    const { data: publicUrlData } = supabase.storage
      .from('community-images')
      .getPublicUrl(filename);

    if (!publicUrlData?.publicUrl) {
      throw new Error('Resim URL adresi alınamadı.');
    }

    return publicUrlData.publicUrl;
  },

  async createPost(
    authorId: string,
    content: string,
    scope: string = 'general',
    gymId?: string,
    imageUrl?: string,
    groupId?: string
  ): Promise<any> {
    // Verify active membership before creating post
    const { data: userProfile } = await supabase
      .from('users')
      .select('*')
      .eq('id', authorId)
      .single();

    const isMember = await UserService.hasActiveMembership(userProfile);
    if (!isMember) {
      throw new Error('Paylaşım yapabilmek için aktif bir salon üyeliğinizin olması gerekmektedir.');
    }

    // Ensure content satisfies constraint community_posts_has_body
    let finalContent = content.trim();
    if (!finalContent && !imageUrl) {
      finalContent = ' '; // Satisfy constraint
    }

    const { data, error } = await supabase
      .from('community_posts')
      .insert({
        author_id: authorId,
        content: finalContent || ' ',
        image_url: imageUrl || null,
        scope: scope,
        gym_id: scope === 'gym' ? gymId : null,
        group_id: groupId || null,
        is_active: true,
      })
      .select()
      .single();

    if (error) throw error;
    return data;
  },

  async createPoll(postId: string, question: string, options: string[]) {
    const { data: poll, error: pollError } = await supabase
      .from('community_polls')
      .insert({
        post_id: postId,
        question: question.trim(),
      })
      .select()
      .single();

    if (pollError || !poll) throw pollError;

    const optionInserts = options
      .filter((o) => o.trim().length > 0)
      .map((opt) => ({
        poll_id: poll.id,
        option_text: opt.trim(),
      }));

    const { error: optError } = await supabase
      .from('community_poll_options')
      .insert(optionInserts);

    if (optError) throw optError;
  },

  async deletePost(postId: string, authorId: string) {
    const { error } = await supabase
      .from('community_posts')
      .update({ is_active: false })
      .eq('id', postId)
      .eq('author_id', authorId);

    if (error) {
      // Fallback to delete if soft delete column isn't supported
      await supabase.from('community_posts').delete().eq('id', postId).eq('author_id', authorId);
    }
  },

  async fetchComments(postId: string): Promise<CommunityComment[]> {
    try {
      const { data, error } = await supabase
        .from('community_comments')
        .select(`
          *,
          author:users!author_id(id, name, surname, email, avatar_url, profile_image_url)
        `)
        .eq('post_id', postId)
        .order('created_at', { ascending: true });

      if (error || !data) {
        const { data: fallback } = await supabase
          .from('community_comments')
          .select('*')
          .eq('post_id', postId)
          .order('created_at', { ascending: true });

        if (!fallback) return [];

        const authorIds = Array.from(new Set(fallback.map((c) => c.author_id)));
        let authorsMap: Record<string, any> = {};
        if (authorIds.length > 0) {
          const { data: authors } = await supabase
            .from('users')
            .select('id, name, surname, email, avatar_url, profile_image_url')
            .in('id', authorIds);
          (authors || []).forEach((a) => {
            authorsMap[a.id] = a;
          });
        }

        return fallback.map((c) => {
          const author = authorsMap[c.author_id];
          const name = author
            ? `${author.name || ''} ${author.surname || ''}`.trim() || author.email || 'Üye'
            : 'Üye';
          return {
            id: c.id,
            post_id: c.post_id,
            author_id: c.author_id,
            author_name: name,
            author_avatar_url: author?.profile_image_url || author?.avatar_url,
            content: c.content,
            created_at: c.created_at,
          };
        });
      }

      return data.map((c: any) => {
        const author = c.author;
        const name = author
          ? `${author.name || ''} ${author.surname || ''}`.trim() || author.email || 'Üye'
          : 'Üye';
        return {
          id: c.id,
          post_id: c.post_id,
          author_id: c.author_id,
          author_name: name,
          author_avatar_url: author?.profile_image_url || author?.avatar_url,
          content: c.content,
          created_at: c.created_at,
        };
      });
    } catch (e) {
      console.error('Error fetching comments:', e);
      return [];
    }
  },

  async createComment(postId: string, authorId: string, content: string) {
    const { data, error } = await supabase
      .from('community_comments')
      .insert({
        post_id: postId,
        author_id: authorId,
        content: content.trim(),
      })
      .select()
      .single();

    if (error) throw error;
    return data;
  },

  async deleteComment(commentId: string, authorId: string) {
    const { error } = await supabase
      .from('community_comments')
      .delete()
      .eq('id', commentId)
      .eq('author_id', authorId);

    if (error) throw error;
  },

  async fetchGlobalSettings(): Promise<{ general_enabled: boolean; gym_feed_enabled: boolean }> {
    try {
      const { data } = await supabase
        .from('community_settings')
        .select('*')
        .eq('scope', 'global')
        .limit(1);

      if (data && data.length > 0) {
        return {
          general_enabled: data[0].general_enabled ?? true,
          gym_feed_enabled: data[0].gym_feed_enabled ?? true,
        };
      }
      return { general_enabled: true, gym_feed_enabled: true };
    } catch {
      return { general_enabled: true, gym_feed_enabled: true };
    }
  },

  async fetchGymSettings(gymId: string): Promise<{ general_enabled: boolean; gym_feed_enabled: boolean }> {
    try {
      const { data } = await supabase
        .from('community_settings')
        .select('*')
        .eq('scope', 'gym')
        .eq('gym_id', gymId)
        .limit(1);

      if (data && data.length > 0) {
        return {
          general_enabled: data[0].general_enabled ?? true,
          gym_feed_enabled: data[0].gym_feed_enabled ?? true,
        };
      }
      return { general_enabled: true, gym_feed_enabled: true };
    } catch {
      return { general_enabled: true, gym_feed_enabled: true };
    }
  },

  async canAccessGymFeed(userId: string, gymId?: string): Promise<boolean> {
    if (!gymId) return false;

    // 1. Global toggle check
    const globalSettings = await this.fetchGlobalSettings();
    if (!globalSettings.gym_feed_enabled) return false;

    // 2. Gym-specific toggle check
    const gymSettings = await this.fetchGymSettings(gymId);
    if (!gymSettings.gym_feed_enabled) return false;

    // 3. Staff check (admin / gym_manager)
    try {
      const { data: profile } = await supabase
        .from('users')
        .select('role, is_admin, is_gym_manager')
        .eq('id', userId)
        .single();

      if (
        profile?.role === 'admin' ||
        profile?.role === 'gym_manager' ||
        profile?.is_admin === true ||
        profile?.is_gym_manager === true
      ) {
        return true;
      }
    } catch {}

    // 4. User active membership check
    try {
      const { data: memberships } = await supabase
        .from('user_memberships')
        .select('*, plan:membership_plans(*)')
        .eq('user_id', userId)
        .eq('is_active', true);

      if (!memberships || memberships.length === 0) return false;

      const activeMem = memberships.find((m: any) => {
        const notExpired = !m.end_date || new Date(m.end_date) > new Date();
        const planGym = m.plan?.gym_id || m.gym_id;
        const gymMatches = !planGym || planGym === gymId;
        return m.is_active && notExpired && gymMatches;
      });

      return !!activeMem;
    } catch {
      return false;
    }
  },

  async canAccessGeneralFeed(): Promise<boolean> {
    try {
      const settings = await this.fetchGlobalSettings();
      return settings.general_enabled;
    } catch {
      return true;
    }
  },

  async fetchGroups(scope: string, gymId?: string): Promise<{ id: string; name: string; scope: string; gym_id?: string }[]> {
    try {
      let query = supabase
        .from('community_groups')
        .select('id, name, scope, gym_id')
        .eq('scope', scope)
        .eq('is_active', true)
        .order('name', { ascending: true });

      if (scope === 'gym' && gymId) {
        query = query.eq('gym_id', gymId);
      } else if (scope === 'general') {
        query = query.is('gym_id', null);
      }

      const { data, error } = await query;
      if (error || !data) return [];
      return data;
    } catch {
      return [];
    }
  },

  async createGroup(
    scope: string,
    name: string,
    description?: string,
    gymId?: string,
    createdBy?: string
  ) {
    const { data, error } = await supabase
      .from('community_groups')
      .insert({
        scope,
        name: name.trim(),
        description: description?.trim() || null,
        gym_id: scope === 'gym' ? gymId : null,
        created_by: createdBy || null,
        is_active: true,
      })
      .select()
      .single();

    if (error) throw error;
    return data;
  },

  async deactivateGroup(groupId: string) {
    const { error } = await supabase
      .from('community_groups')
      .update({ is_active: false })
      .eq('id', groupId);

    if (error) throw error;
  },

  async addGroupMember(groupId: string, userId: string, role: string = 'member') {
    const { data, error } = await supabase
      .from('community_group_members')
      .insert({
        group_id: groupId,
        user_id: userId,
        role,
      })
      .select()
      .single();

    if (error) throw error;
    return data;
  },

  async removeGroupMember(groupId: string, userId: string) {
    const { error } = await supabase
      .from('community_group_members')
      .delete()
      .eq('group_id', groupId)
      .eq('user_id', userId);

    if (error) throw error;
  },

  async isGroupMember(groupId: string, userId: string): Promise<boolean> {
    try {
      const { data } = await supabase
        .from('community_group_members')
        .select('id')
        .eq('group_id', groupId)
        .eq('user_id', userId)
        .limit(1);
      return !!(data && data.length > 0);
    } catch {
      return false;
    }
  },
};
