import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  FlatList,
  ActivityIndicator,
  TextInput,
  Image,
  ScrollView,
  Animated,
  Alert,
} from 'react-native';
import {
  Heart,
  MessageSquare,
  Plus,
  Image as ImageIcon,
  BarChart2,
  X,
  Send,
  Trash2,
  Lock,
  AlertTriangle,
  CheckCircle2,
} from 'lucide-react-native';
import * as ImagePicker from 'expo-image-picker';
import { Colors } from '../../theme/colors';
import { Header } from '../../components/Header';
import { SideMenu } from '../../components/SideMenu';
import { AuthService } from '../../services/authService';
import {
  CommunityService,
  CommunityPost,
  CommunityComment,
} from '../../services/communityService';
import { UserService } from '../../services/userService';

// ── Types ────────────────────────────────────────────────────────────────────

type ScopeTab = 'general' | 'gym';

interface CommunityGroup {
  id: string;
  name: string;
  scope: string;
}

interface CommunityScreenProps {
  navigation?: any;
}

// ── Helpers ──────────────────────────────────────────────────────────────────

function formatPostTime(iso: string): string {
  try {
    const normalized = iso.replace(' ', 'T');
    const withZ =
      normalized.endsWith('Z') || normalized.includes('+') ? normalized : normalized + 'Z';
    const dt = new Date(withZ);
    return dt.toLocaleDateString('tr-TR', {
      day: 'numeric',
      month: 'short',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return iso.slice(0, 16);
  }
}

// ── Snackbar component ───────────────────────────────────────────────────────

const Snackbar: React.FC<{ message: string | null; onHide: () => void }> = ({
  message,
  onHide,
}) => {
  const opacity = React.useRef(new Animated.Value(0)).current;

  useEffect(() => {
    if (message) {
      Animated.sequence([
        Animated.timing(opacity, { toValue: 1, duration: 200, useNativeDriver: true }),
        Animated.delay(2000),
        Animated.timing(opacity, { toValue: 0, duration: 300, useNativeDriver: true }),
      ]).start(() => onHide());
    }
  }, [message]);

  if (!message) return null;

  return (
    <Animated.View style={[styles.snackbar, { opacity }]}>
      <Text style={styles.snackbarText}>{message}</Text>
    </Animated.View>
  );
};

// ── Locked State ─────────────────────────────────────────────────────────────

const LockedState: React.FC<{ message: string }> = ({ message }) => (
  <View style={styles.lockedBox}>
    <Lock size={56} color={Colors.textSecondaryDark} style={{ opacity: 0.3 }} />
    <Text style={styles.lockedText}>{message}</Text>
  </View>
);

// ── Empty Feed State ─────────────────────────────────────────────────────────

const EmptyFeedState: React.FC<{ onCreatePress: () => void }> = ({ onCreatePress }) => (
  <View style={styles.emptyBox}>
    <Text style={styles.emptyEmoji}>💬</Text>
    <Text style={styles.emptyTitle}>Henüz paylaşım yok</Text>
    <Text style={styles.emptySubtitle}>İlk yazıyı veya görseli sen paylaş.</Text>
    <TouchableOpacity style={styles.emptyCreateBtn} onPress={onCreatePress} activeOpacity={0.8}>
      <Text style={styles.emptyCreateBtnText}>Paylaşım Yap</Text>
    </TouchableOpacity>
  </View>
);

// ── Post Card ─────────────────────────────────────────────────────────────────

interface PostCardProps {
  item: CommunityPost;
  isMine: boolean;
  currentUserId?: string;
  onLike: () => void;
  onDelete: () => void;
  onVote: (pollId: string, optionId: string) => void;
  onCommentAction: () => void;
}

const PostCard: React.FC<PostCardProps> = ({
  item,
  isMine,
  currentUserId,
  onLike,
  onDelete,
  onVote,
  onCommentAction,
}) => {
  const [commentsExpanded, setCommentsExpanded] = useState(false);
  const [comments, setComments] = useState<CommunityComment[]>([]);
  const [isCommentsLoading, setIsCommentsLoading] = useState(false);
  const [newCommentText, setNewCommentText] = useState('');
  const [isSendingComment, setIsSendingComment] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);

  const loadComments = useCallback(async () => {
    setIsCommentsLoading(true);
    try {
      const data = await CommunityService.fetchComments(item.id);
      setComments(data);
    } catch {
      setComments([]);
    } finally {
      setIsCommentsLoading(false);
    }
  }, [item.id]);

  const handleToggleComments = () => {
    if (!commentsExpanded) {
      setCommentsExpanded(true);
      loadComments();
    } else {
      setCommentsExpanded(false);
    }
  };

  const handleSendComment = async () => {
    if (!newCommentText.trim() || !currentUserId || isSendingComment) return;
    setIsSendingComment(true);
    try {
      await CommunityService.createComment(item.id, currentUserId, newCommentText.trim());
      setNewCommentText('');
      await loadComments();
      onCommentAction();
    } catch (e: any) {
      Alert.alert('Hata', 'Yorum gönderilemedi: ' + (e.message || 'Bilinmeyen hata'));
    } finally {
      setIsSendingComment(false);
    }
  };

  const handleDeleteComment = async (commentId: string) => {
    if (!currentUserId) return;
    try {
      await CommunityService.deleteComment(commentId, currentUserId);
      await loadComments();
      onCommentAction();
    } catch {}
  };

  const hasVoted = !!item.poll?.my_vote_option_id;

  return (
    <View style={styles.postCard}>
      {/* Header */}
      <View style={styles.postHeader}>
        {item.author_avatar_url ? (
          <Image source={{ uri: item.author_avatar_url }} style={styles.avatarImage} />
        ) : (
          <View style={styles.avatarPlaceholder}>
            <Text style={styles.avatarText}>
              {(item.author_name || 'Ü')[0].toUpperCase()}
            </Text>
          </View>
        )}
        <View style={{ flex: 1 }}>
          <Text style={styles.authorName}>{item.author_name || 'Üye'}</Text>
          <Text style={styles.timeText}>{formatPostTime(item.created_at)}</Text>
        </View>
        {isMine && (
          <TouchableOpacity
            style={styles.deletePostHeaderBtn}
            onPress={() => setShowDeleteConfirm(true)}
            activeOpacity={0.7}
          >
            <Trash2 size={18} color={Colors.error} />
          </TouchableOpacity>
        )}
      </View>

      {/* Delete confirmation */}
      {showDeleteConfirm && (
        <View style={styles.deleteConfirmBox}>
          <AlertTriangle size={18} color={Colors.error} />
          <Text style={styles.deleteConfirmText}>Paylaşımı silmek istiyor musunuz?</Text>
          <View style={styles.deleteConfirmActions}>
            <TouchableOpacity onPress={() => setShowDeleteConfirm(false)}>
              <Text style={styles.deleteConfirmCancel}>Vazgeç</Text>
            </TouchableOpacity>
            <TouchableOpacity
              onPress={() => {
                setShowDeleteConfirm(false);
                onDelete();
              }}
            >
              <Text style={styles.deleteConfirmOk}>Sil</Text>
            </TouchableOpacity>
          </View>
        </View>
      )}

      {/* Content */}
      {item.content && item.content.trim() ? (
        <Text style={styles.postContent}>{item.content}</Text>
      ) : null}

      {/* Poll */}
      {item.poll && (
        <View style={styles.pollCard}>
          <Text style={styles.pollQuestionTitle}>Anket: {item.poll.question}</Text>
          <View style={styles.pollOptionsContainer}>
            {item.poll.options.map((opt) => {
              const isMyVote = opt.id === item.poll!.my_vote_option_id;
              if (hasVoted) {
                // Show progress bar result
                return (
                  <View
                    key={opt.id}
                    style={[
                      styles.pollResultRow,
                      isMyVote && styles.pollResultRowVoted,
                    ]}
                  >
                    <View
                      style={[
                        styles.pollProgressBg,
                        { width: `${Math.max(opt.percentage, 2)}%` },
                        isMyVote && styles.pollProgressBgVoted,
                      ]}
                    />
                    <View style={styles.pollOptionContentRow}>
                      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
                        <Text
                          style={[
                            styles.pollOptionText,
                            isMyVote && styles.pollOptionTextVoted,
                          ]}
                        >
                          {opt.option_text}
                        </Text>
                        {isMyVote && (
                          <CheckCircle2 size={16} color={Colors.primary} />
                        )}
                      </View>
                      <Text style={[styles.pollPctText, isMyVote && { color: Colors.primary }]}>
                        %{opt.percentage}
                      </Text>
                    </View>
                  </View>
                );
              }
              // Voteable button
              return (
                <TouchableOpacity
                  key={opt.id}
                  style={styles.pollOptionBtn}
                  onPress={() => onVote(item.poll!.id, opt.id)}
                  activeOpacity={0.8}
                >
                  <Text style={styles.pollOptionText}>{opt.option_text}</Text>
                </TouchableOpacity>
              );
            })}
          </View>
          {(item.poll.total_votes || 0) > 0 && (
            <Text style={styles.pollTotalVotesText}>
              Toplam {item.poll.total_votes} oy
            </Text>
          )}
        </View>
      )}

      {/* Image */}
      {item.image_url ? (
        <Image source={{ uri: item.image_url }} style={styles.postImage} resizeMode="cover" />
      ) : null}

      {/* Footer actions */}
      <View style={styles.postFooter}>
        <TouchableOpacity style={styles.actionBtn} onPress={onLike}>
          <Heart
            size={18}
            color={item.liked_by_me ? Colors.primary : Colors.textSecondaryDark}
            fill={item.liked_by_me ? Colors.primary : 'transparent'}
          />
          <Text style={[styles.actionText, item.liked_by_me && { color: Colors.primary }]}>
            {item.likes_count > 0 ? `${item.likes_count}` : 'Beğen'}
          </Text>
        </TouchableOpacity>

        <TouchableOpacity style={styles.actionBtn} onPress={handleToggleComments}>
          <MessageSquare
            size={18}
            color={commentsExpanded ? Colors.primary : Colors.textSecondaryDark}
          />
          <Text
            style={[
              styles.actionText,
              commentsExpanded && { color: Colors.primary },
            ]}
          >
            {item.comments_count > 0 ? `${item.comments_count} Yorum` : 'Yorum Yap'}
          </Text>
        </TouchableOpacity>
      </View>

      {/* Comments */}
      {commentsExpanded && (
        <View style={styles.commentsSection}>
          <View style={styles.commentsDivider} />

          {isCommentsLoading ? (
            <ActivityIndicator
              size="small"
              color={Colors.primary}
              style={{ marginVertical: 10 }}
            />
          ) : comments.length === 0 ? (
            <Text style={styles.noCommentsText}>
              Henüz yorum yok. İlk yorumu sen yap!
            </Text>
          ) : (
            comments.map((c) => (
              <View key={c.id} style={styles.commentRow}>
                {c.author_avatar_url ? (
                  <Image source={{ uri: c.author_avatar_url }} style={styles.commentAvatarImage} />
                ) : (
                  <View style={styles.commentAvatarPlaceholder}>
                    <Text style={styles.commentAvatarText}>
                      {(c.author_name || 'Ü')[0].toUpperCase()}
                    </Text>
                  </View>
                )}
                <View style={{ flex: 1 }}>
                  <Text style={styles.commentAuthor}>{c.author_name}</Text>
                  <Text style={styles.commentContent}>{c.content}</Text>
                </View>
                {(c.author_id === currentUserId) && (
                  <TouchableOpacity onPress={() => handleDeleteComment(c.id)}>
                    <X size={14} color={Colors.textSecondaryDark} />
                  </TouchableOpacity>
                )}
              </View>
            ))
          )}

          {/* New comment input */}
          <View style={styles.commentInputRow}>
            <TextInput
              style={styles.commentInput}
              placeholder="Yorum yaz..."
              placeholderTextColor={Colors.textSecondaryDark}
              value={newCommentText}
              onChangeText={setNewCommentText}
            />
            <TouchableOpacity
              style={[
                styles.commentSendBtn,
                !newCommentText.trim() && { backgroundColor: Colors.borderDark },
              ]}
              onPress={handleSendComment}
              disabled={!newCommentText.trim() || isSendingComment}
            >
              {isSendingComment ? (
                <ActivityIndicator size="small" color={Colors.allWhite} />
              ) : (
                <Send size={16} color={Colors.allWhite} />
              )}
            </TouchableOpacity>
          </View>
        </View>
      )}
    </View>
  );
};

// ── Main Screen ──────────────────────────────────────────────────────────────

export const CommunityScreen: React.FC<CommunityScreenProps> = ({ navigation }) => {
  const [menuVisible, setMenuVisible] = useState(false);
  const [selectedTab, setSelectedTab] = useState<ScopeTab>('general');
  const [selectedGroupId, setSelectedGroupId] = useState<string | null>(null);

  const [posts, setPosts] = useState<CommunityPost[]>([]);
  const [groups, setGroups] = useState<CommunityGroup[]>([]);
  const [loading, setLoading] = useState(true);
  const [canAccess, setCanAccess] = useState(true);
  const [accessMessage, setAccessMessage] = useState<string | null>(null);

  const [userProfile, setUserProfile] = useState<any>(null);
  const [snackbar, setSnackbar] = useState<string | null>(null);

  // Composer state
  const [showComposer, setShowComposer] = useState(false);
  const [postText, setPostText] = useState('');
  const [imageUri, setImageUri] = useState<string | null>(null);
  const [showPollCreator, setShowPollCreator] = useState(false);
  const [pollQuestion, setPollQuestion] = useState('');
  const [pollOptions, setPollOptions] = useState<string[]>(['', '']);
  const [isSending, setIsSending] = useState(false);

  // ── Load profile ──────────────────────────────────────────────────────────

  useEffect(() => {
    const loadProfile = async () => {
      try {
        const profile = await AuthService.getCurrentProfile();
        setUserProfile(profile);
      } catch {}
    };
    loadProfile();
  }, []);

  // ── Reset group on tab switch ─────────────────────────────────────────────

  useEffect(() => {
    setSelectedGroupId(null);
  }, [selectedTab]);

  // ── Load feed ─────────────────────────────────────────────────────────────

  const loadFeed = useCallback(async () => {
    if (!userProfile) return;

    const userId = userProfile.id || userProfile.user_id;
    
    setLoading(true);
    try {
      const resolvedGymId = await UserService.resolveActiveGymIdForContent(userProfile);

      if (selectedTab === 'gym') {
        const allowed = resolvedGymId ? await CommunityService.canAccessGymFeed(userId, resolvedGymId) : false;
        setCanAccess(allowed);
        if (!resolvedGymId) {
          setAccessMessage('Aktif salon üyeliğin yok. Salon topluluğuna sadece üyeler erişebilir.');
          setPosts([]);
          setGroups([]);
        } else if (!allowed) {
          setAccessMessage('Salon topluluğu kapalı veya üyeliğin aktif değil.');
          setPosts([]);
          setGroups([]);
        } else {
          setAccessMessage(null);
          const groupsData = await CommunityService.fetchGroups('gym', resolvedGymId);
          setGroups(groupsData);

          // Group access check
          if (selectedGroupId) {
            const isStaff =
              userProfile.role === 'admin' ||
              userProfile.role === 'gym_manager' ||
              userProfile.is_admin === true ||
              userProfile.is_gym_manager === true;
            const isMember =
              isStaff || (await CommunityService.isGroupMember(selectedGroupId, userId));
            if (!isMember) {
              setCanAccess(false);
              setAccessMessage(
                'Bu özel gruba erişim yetkiniz yok. Sadece işletmenin eklediği üyeler görebilir.'
              );
              setPosts([]);
              return;
            }
          }

          const data = await CommunityService.fetchFeed(userId, 'gym', resolvedGymId, selectedGroupId ?? undefined);
          setPosts(data);
        }
      } else {
        const allowed = await CommunityService.canAccessGeneralFeed();
        setCanAccess(allowed);
        if (!allowed) {
          setAccessMessage('Genel topluluk şu an kapalı.');
          setPosts([]);
          setGroups([]);
        } else {
          setAccessMessage(null);
          const groupsData = await CommunityService.fetchGroups('general');
          setGroups(groupsData);
          const data = await CommunityService.fetchFeed(userId, 'general', undefined, selectedGroupId ?? undefined);
          setPosts(data);
        }
      }
    } catch {
      setCanAccess(false);
      setAccessMessage('Topluluk verisi yüklenemedi.');
      setPosts([]);
      setGroups([]);
    } finally {
      setLoading(false);
    }
  }, [selectedTab, selectedGroupId, userProfile]);

  useEffect(() => {
    loadFeed();
  }, [loadFeed]);

  // ── Post actions ──────────────────────────────────────────────────────────

  const handleLike = async (postId: string) => {
    const userId = userProfile?.id || userProfile?.user_id;
    if (!userId) return;

    // Optimistic update
    setPosts((prev) =>
      prev.map((p) => {
        if (p.id !== postId) return p;
        const newLiked = !p.liked_by_me;
        return {
          ...p,
          liked_by_me: newLiked,
          likes_count: newLiked ? p.likes_count + 1 : Math.max(0, p.likes_count - 1),
        };
      })
    );

    try {
      await CommunityService.toggleLike(postId, userId);
    } catch {
      loadFeed();
    }
  };

  const handleDelete = async (postId: string) => {
    const userId = userProfile?.id || userProfile?.user_id;
    if (!userId) return;
    try {
      await CommunityService.deletePost(postId, userId);
      loadFeed();
      setSnackbar('Gönderi silindi');
    } catch {
      setSnackbar('Silinemedi');
    }
  };

  const handleVote = async (pollId: string, optionId: string) => {
    const userId = userProfile?.id || userProfile?.user_id;
    if (!userId) return;
    try {
      await CommunityService.votePoll(pollId, optionId, userId);
      loadFeed();
    } catch {
      setSnackbar('Oy kullanılamadı');
    }
  };

  // ── Image picker ──────────────────────────────────────────────────────────

  const handlePickImage = async () => {
    try {
      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: ['images'],
        quality: 0.8,
      });
      if (!result.canceled && result.assets?.[0]?.uri) {
        setImageUri(result.assets[0].uri);
      }
    } catch {}
  };

  // ── Poll helpers ──────────────────────────────────────────────────────────

  const handleAddPollOption = () => {
    if (pollOptions.length < 4) setPollOptions([...pollOptions, '']);
  };

  const handleRemovePollOption = (index: number) => {
    if (pollOptions.length > 2) {
      const next = [...pollOptions];
      next.splice(index, 1);
      setPollOptions(next);
    }
  };

  const handleUpdatePollOption = (index: number, text: string) => {
    const next = [...pollOptions];
    next[index] = text;
    setPollOptions(next);
  };

  // ── Create post ───────────────────────────────────────────────────────────

  const handleCreatePost = async () => {
    const userId = userProfile?.id || userProfile?.user_id;
    if (!userId) return;

    const canSubmit = showPollCreator
      ? pollQuestion.trim() && pollOptions.filter((o) => o.trim()).length >= 2
      : postText.trim() || imageUri;

    if (!canSubmit) return;

    setIsSending(true);
    try {
      let uploadedUrl: string | undefined;
      if (imageUri) {
        uploadedUrl = await CommunityService.uploadImage(imageUri, userId);
      }

      const resolvedGymId = await UserService.resolveActiveGymIdForContent(userProfile);
      const createdPost = await CommunityService.createPost(
        userId,
        postText.trim() || (showPollCreator ? pollQuestion.trim() : ' '),
        selectedTab,
        selectedTab === 'gym' ? resolvedGymId || undefined : undefined,
        uploadedUrl,
        selectedGroupId ?? undefined
      );

      if (showPollCreator && pollQuestion.trim() && createdPost?.id) {
        const validOpts = pollOptions.filter((o) => o.trim().length > 0);
        if (validOpts.length >= 2) {
          await CommunityService.createPoll(createdPost.id, pollQuestion.trim(), validOpts);
        }
      }

      // Reset composer
      setPostText('');
      setImageUri(null);
      setShowPollCreator(false);
      setPollQuestion('');
      setPollOptions(['', '']);
      setShowComposer(false);
      loadFeed();
      setSnackbar('Paylaşıldı');
    } catch (e: any) {
      setSnackbar(e?.message || 'Paylaşım başarısız');
    } finally {
      setIsSending(false);
    }
  };

  const resetComposer = () => {
    setShowComposer(false);
    setPostText('');
    setImageUri(null);
    setShowPollCreator(false);
    setPollQuestion('');
    setPollOptions(['', '']);
  };

  const currentUserId = userProfile?.id || userProfile?.user_id;

  // ── canSubmitComposer ─────────────────────────────────────────────────────
  const canSubmitComposer = showPollCreator
    ? pollQuestion.trim().length > 0 &&
      pollOptions.filter((o) => o.trim().length > 0).length >= 2
    : postText.trim().length > 0 || !!imageUri;

  // ── Render ────────────────────────────────────────────────────────────────

  return (
    <View style={styles.container}>
      <Header navigation={navigation} onOpenDrawer={() => setMenuVisible(true)} />
      <SideMenu visible={menuVisible} onClose={() => setMenuVisible(false)} navigation={navigation} />

      {/* Scope Tab (Genel | Salon) */}
      <View style={styles.tabContainer}>
        {(['general', 'gym'] as ScopeTab[]).map((tab) => {
          const label = tab === 'general' ? 'Genel' : 'Salon';
          const isSelected = selectedTab === tab;
          return (
            <TouchableOpacity
              key={tab}
              style={[styles.tabButton, isSelected && styles.tabButtonSelected]}
              onPress={() => setSelectedTab(tab)}
              activeOpacity={0.8}
            >
              <Text style={[styles.tabText, isSelected && styles.tabTextSelected]}>
                {label}
              </Text>
            </TouchableOpacity>
          );
        })}
      </View>

      <Text style={styles.subScopeTitle}>
        {selectedTab === 'gym' ? 'Salon Topluluğu' : 'Genel Topluluk'}
      </Text>

      {/* Group Chips */}
      {groups.length > 0 && canAccess && (
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.groupChipsRow}
        >
          <TouchableOpacity
            style={[styles.groupChip, selectedGroupId === null && styles.groupChipSelected]}
            onPress={() => setSelectedGroupId(null)}
            activeOpacity={0.8}
          >
            <Text
              style={[
                styles.groupChipText,
                selectedGroupId === null && styles.groupChipTextSelected,
              ]}
            >
              Tümü
            </Text>
          </TouchableOpacity>
          {groups.map((g) => (
            <TouchableOpacity
              key={g.id}
              style={[styles.groupChip, selectedGroupId === g.id && styles.groupChipSelected]}
              onPress={() => setSelectedGroupId(g.id)}
              activeOpacity={0.8}
            >
              <Text
                style={[
                  styles.groupChipText,
                  selectedGroupId === g.id && styles.groupChipTextSelected,
                ]}
              >
                {g.name}
              </Text>
            </TouchableOpacity>
          ))}
        </ScrollView>
      )}

      {/* Main Content */}
      {loading ? (
        <View style={styles.loadingBox}>
          <ActivityIndicator size="large" color={Colors.primary} />
        </View>
      ) : !canAccess ? (
        <LockedState message={accessMessage || 'Erişim yok'} />
      ) : posts.length === 0 ? (
        <EmptyFeedState onCreatePress={() => setShowComposer(true)} />
      ) : (
        <FlatList
          data={posts}
          keyExtractor={(p) => p.id}
          contentContainerStyle={styles.listContent}
          renderItem={({ item }) => (
            <PostCard
              item={item}
              isMine={item.author_id === currentUserId}
              currentUserId={currentUserId}
              onLike={() => handleLike(item.id)}
              onDelete={() => handleDelete(item.id)}
              onVote={handleVote}
              onCommentAction={loadFeed}
            />
          )}
          ListFooterComponent={<View style={{ height: 88 }} />}
        />
      )}

      {/* FAB */}
      {canAccess && !loading && (
        <TouchableOpacity
          style={styles.fab}
          onPress={() => setShowComposer(true)}
          activeOpacity={0.85}
        >
          <Plus size={24} color={Colors.allWhite} />
        </TouchableOpacity>
      )}

      {/* Snackbar */}
      <Snackbar message={snackbar} onHide={() => setSnackbar(null)} />

      {/* Composer Overlay */}
      {showComposer && (
        <View style={styles.composerOverlay}>
          <View style={styles.composerBox}>
            <View style={styles.composerHeader}>
              <Text style={styles.composerTitle}>Yeni Paylaşım</Text>
              <TouchableOpacity onPress={resetComposer} disabled={isSending}>
                <X size={20} color={Colors.textSecondaryDark} />
              </TouchableOpacity>
            </View>

            <ScrollView style={{ maxHeight: 380 }} showsVerticalScrollIndicator={false}>
              <TextInput
                style={styles.composerInput}
                placeholder="Ne düşünüyorsun?"
                placeholderTextColor={Colors.textSecondaryDark}
                multiline
                value={postText}
                onChangeText={setPostText}
              />

              {/* Triggers row */}
              <View style={styles.triggersRow}>
                <TouchableOpacity
                  style={[styles.triggerBtn, imageUri && styles.triggerBtnActive]}
                  onPress={handlePickImage}
                  disabled={showPollCreator || isSending}
                >
                  <ImageIcon
                    size={20}
                    color={imageUri ? Colors.primary : Colors.textSecondaryDark}
                  />
                  <Text
                    style={[styles.triggerBtnText, imageUri && { color: Colors.primary }]}
                  >
                    Fotoğraf
                  </Text>
                </TouchableOpacity>

                <TouchableOpacity
                  style={[styles.triggerBtn, showPollCreator && styles.triggerBtnActive]}
                  onPress={() => setShowPollCreator(!showPollCreator)}
                  disabled={!!imageUri || isSending}
                >
                  <BarChart2
                    size={20}
                    color={showPollCreator ? Colors.primary : Colors.textSecondaryDark}
                  />
                  <Text
                    style={[
                      styles.triggerBtnText,
                      showPollCreator && { color: Colors.primary },
                    ]}
                  >
                    Anket
                  </Text>
                </TouchableOpacity>
              </View>

              {/* Image preview */}
              {imageUri && (
                <View style={styles.imagePreviewWrapper}>
                  <Image source={{ uri: imageUri }} style={styles.previewImage} />
                  <TouchableOpacity
                    style={styles.removeImageBtn}
                    onPress={() => setImageUri(null)}
                  >
                    <X size={16} color={Colors.allWhite} />
                  </TouchableOpacity>
                </View>
              )}

              {/* Poll creator */}
              {showPollCreator && (
                <View style={styles.composerPollCard}>
                  <Text style={styles.pollCardTitle}>Anket Detayları</Text>
                  <TextInput
                    style={styles.pollQuestionInput}
                    placeholder="Bir soru sor..."
                    placeholderTextColor={Colors.textSecondaryDark}
                    value={pollQuestion}
                    onChangeText={setPollQuestion}
                  />
                  {pollOptions.map((opt, idx) => (
                    <View key={idx} style={styles.pollOptionRow}>
                      <TextInput
                        style={styles.pollOptionInput}
                        placeholder={`Seçenek ${idx + 1}`}
                        placeholderTextColor={Colors.textSecondaryDark}
                        value={opt}
                        onChangeText={(text) => handleUpdatePollOption(idx, text)}
                      />
                      {pollOptions.length > 2 && (
                        <TouchableOpacity onPress={() => handleRemovePollOption(idx)}>
                          <X size={18} color={Colors.error} />
                        </TouchableOpacity>
                      )}
                    </View>
                  ))}
                  {pollOptions.length < 4 && (
                    <TouchableOpacity
                      style={styles.addOptionBtn}
                      onPress={handleAddPollOption}
                    >
                      <Plus size={16} color={Colors.primary} />
                      <Text style={styles.addOptionText}>Seçenek Ekle</Text>
                    </TouchableOpacity>
                  )}
                </View>
              )}
            </ScrollView>

            {/* Composer actions */}
            <View style={styles.composerActions}>
              <TouchableOpacity
                style={styles.cancelBtn}
                onPress={resetComposer}
                disabled={isSending}
              >
                <Text style={styles.cancelBtnText}>İptal</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.sendBtn, !canSubmitComposer && { opacity: 0.6 }]}
                onPress={handleCreatePost}
                disabled={isSending || !canSubmitComposer}
              >
                {isSending ? (
                  <ActivityIndicator size="small" color={Colors.allWhite} />
                ) : (
                  <Text style={styles.sendBtnText}>Paylaş</Text>
                )}
              </TouchableOpacity>
            </View>
          </View>
        </View>
      )}
    </View>
  );
};

// ── Styles ────────────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Colors.backgroundDark },
  // Tabs
  tabContainer: {
    flexDirection: 'row',
    marginHorizontal: 20,
    marginTop: 10,
    backgroundColor: Colors.cardDark,
    borderRadius: 100,
    padding: 4,
  },
  tabButton: { flex: 1, paddingVertical: 10, borderRadius: 100, alignItems: 'center' },
  tabButtonSelected: { backgroundColor: Colors.primary },
  tabText: { fontSize: 14, fontWeight: '500', color: Colors.textSecondaryDark },
  tabTextSelected: { color: Colors.allWhite, fontWeight: '600' },
  subScopeTitle: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    paddingHorizontal: 20,
    marginVertical: 10,
  },
  // Groups
  groupChipsRow: {
    paddingHorizontal: 20,
    paddingBottom: 10,
    gap: 8,
    flexDirection: 'row',
  },
  groupChip: {
    borderRadius: 100,
    paddingHorizontal: 14,
    paddingVertical: 8,
    backgroundColor: Colors.cardDark,
    borderWidth: 1,
    borderColor: 'rgba(250,249,248,0.08)',
  },
  groupChipSelected: {
    backgroundColor: 'rgba(255,96,71,0.15)',
    borderColor: Colors.primary,
  },
  groupChipText: { fontSize: 13, fontWeight: '500', color: 'rgba(250,249,248,0.7)' },
  groupChipTextSelected: { color: Colors.primary, fontWeight: '600' },
  // Feed
  loadingBox: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  listContent: { paddingHorizontal: 20 },
  // Locked
  lockedBox: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 32,
    gap: 12,
  },
  lockedText: {
    fontSize: 14,
    color: 'rgba(250,249,248,0.55)',
    lineHeight: 20,
    textAlign: 'center',
  },
  // Empty
  emptyBox: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 32 },
  emptyEmoji: { fontSize: 40, marginBottom: 12 },
  emptyTitle: { fontSize: 16, fontWeight: '600', color: Colors.textDark },
  emptySubtitle: { fontSize: 13, color: Colors.textSecondaryDark, marginTop: 6, marginBottom: 16 },
  emptyCreateBtn: {
    backgroundColor: Colors.primary,
    borderRadius: 100,
    paddingHorizontal: 24,
    paddingVertical: 12,
  },
  emptyCreateBtnText: { color: Colors.allWhite, fontWeight: '600' },
  // Post card
  postCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 20,
    padding: 14,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: 'rgba(250,249,248,0.05)',
  },
  postHeader: { flexDirection: 'row', alignItems: 'center', marginBottom: 12 },
  avatarImage: { width: 40, height: 40, borderRadius: 20, marginRight: 10 },
  avatarPlaceholder: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: 'rgba(255,96,71,0.15)',
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 10,
  },
  avatarText: { fontWeight: '700', color: Colors.primary },
  authorName: { fontSize: 14, fontWeight: '600', color: Colors.textDark },
  timeText: { fontSize: 11, color: 'rgba(250,249,248,0.45)', marginTop: 1 },
  deletePostHeaderBtn: {
    padding: 6,
    borderRadius: 8,
    backgroundColor: 'rgba(176,0,32,0.1)',
  },
  // Delete confirm inline
  deleteConfirmBox: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    backgroundColor: 'rgba(176,0,32,0.1)',
    borderRadius: 10,
    padding: 10,
    marginBottom: 10,
    flexWrap: 'wrap',
  },
  deleteConfirmText: { flex: 1, fontSize: 13, color: Colors.textDark },
  deleteConfirmActions: { flexDirection: 'row', gap: 12 },
  deleteConfirmCancel: { color: Colors.textSecondaryDark, fontWeight: '600', fontSize: 13 },
  deleteConfirmOk: { color: Colors.error, fontWeight: '700', fontSize: 13 },
  postContent: { fontSize: 14, color: Colors.textDark, lineHeight: 20, marginBottom: 12 },
  postImage: { width: '100%', height: 200, borderRadius: 14, marginBottom: 12 },
  // Poll display
  pollCard: {
    backgroundColor: Colors.backgroundDark,
    borderRadius: 12,
    padding: 12,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  pollQuestionTitle: {
    fontSize: 14,
    fontWeight: '700',
    color: Colors.textDark,
    marginBottom: 12,
  },
  pollOptionsContainer: { gap: 8 },
  pollResultRow: {
    position: 'relative',
    height: 44,
    backgroundColor: 'rgba(250,249,248,0.05)',
    borderRadius: 8,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: 'transparent',
    justifyContent: 'center',
  },
  pollResultRowVoted: { borderColor: Colors.primary },
  pollProgressBg: {
    position: 'absolute',
    top: 0,
    bottom: 0,
    left: 0,
    backgroundColor: 'rgba(250,249,248,0.06)',
  },
  pollProgressBgVoted: { backgroundColor: 'rgba(255,96,71,0.22)' },
  pollOptionContentRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 12,
  },
  pollOptionBtn: {
    height: 42,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: Colors.borderDark,
    justifyContent: 'center',
    paddingHorizontal: 12,
  },
  pollOptionText: { fontSize: 13, fontWeight: '500', color: Colors.textDark },
  pollOptionTextVoted: { color: Colors.primary, fontWeight: '700' },
  pollPctText: { fontSize: 12, fontWeight: '700', color: Colors.textSecondaryDark },
  pollTotalVotesText: {
    fontSize: 11,
    color: Colors.textSecondaryDark,
    marginTop: 8,
    textAlign: 'right',
  },
  // Footer
  postFooter: {
    flexDirection: 'row',
    gap: 24,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: Colors.borderDark,
  },
  actionBtn: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  actionText: { color: Colors.textSecondaryDark, fontSize: 13, fontWeight: '600' },
  // Comments
  commentsSection: { marginTop: 12 },
  commentsDivider: { height: 1, backgroundColor: 'rgba(68,68,68,0.35)', marginBottom: 10 },
  noCommentsText: { fontSize: 12, color: Colors.textSecondaryDark, marginVertical: 6 },
  commentRow: { flexDirection: 'row', alignItems: 'flex-start', marginBottom: 10, gap: 8 },
  commentAvatarImage: { width: 28, height: 28, borderRadius: 14 },
  commentAvatarPlaceholder: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: 'rgba(255,96,71,0.1)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  commentAvatarText: { fontSize: 11, fontWeight: '700', color: Colors.primary },
  commentAuthor: { fontSize: 12, fontWeight: '700', color: Colors.textDark },
  commentContent: { fontSize: 13, color: Colors.textDark, lineHeight: 16, marginTop: 1 },
  commentInputRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginTop: 8,
  },
  commentInput: {
    flex: 1,
    backgroundColor: Colors.backgroundDark,
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 8,
    color: Colors.textDark,
    fontSize: 13,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  commentSendBtn: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: Colors.primary,
    justifyContent: 'center',
    alignItems: 'center',
  },
  // FAB
  fab: {
    position: 'absolute',
    right: 24,
    bottom: 90,
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: Colors.primary,
    justifyContent: 'center',
    alignItems: 'center',
    elevation: 6,
  },
  // Snackbar
  snackbar: {
    position: 'absolute',
    bottom: 80,
    left: 20,
    right: 20,
    backgroundColor: Colors.cardDark,
    borderRadius: 12,
    padding: 14,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: Colors.borderDark,
    elevation: 8,
  },
  snackbarText: { color: Colors.textDark, fontSize: 14 },
  // Composer
  composerOverlay: {
    ...StyleSheet.absoluteFill,
    backgroundColor: 'rgba(0,0,0,0.6)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  composerBox: {
    width: '100%',
    backgroundColor: Colors.cardDark,
    borderRadius: 24,
    padding: 20,
  },
  composerHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 14,
  },
  composerTitle: { fontSize: 18, fontWeight: '700', color: Colors.textDark },
  composerInput: {
    height: 90,
    backgroundColor: Colors.backgroundDark,
    borderRadius: 14,
    padding: 12,
    color: Colors.textDark,
    textAlignVertical: 'top',
    fontSize: 15,
    marginBottom: 12,
  },
  triggersRow: { flexDirection: 'row', gap: 12, marginBottom: 12 },
  triggerBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: Colors.backgroundDark,
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 100,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  triggerBtnActive: {
    borderColor: Colors.primary,
    backgroundColor: 'rgba(255,96,71,0.1)',
  },
  triggerBtnText: { fontSize: 13, fontWeight: '600', color: Colors.textSecondaryDark },
  imagePreviewWrapper: { position: 'relative', marginBottom: 12 },
  previewImage: { width: '100%', height: 140, borderRadius: 12 },
  removeImageBtn: {
    position: 'absolute',
    top: 8,
    right: 8,
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: 'rgba(0,0,0,0.6)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  composerPollCard: {
    backgroundColor: Colors.backgroundDark,
    borderRadius: 16,
    padding: 14,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  pollCardTitle: { fontSize: 13, fontWeight: '700', color: Colors.primary, marginBottom: 8 },
  pollQuestionInput: {
    backgroundColor: Colors.cardDark,
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 8,
    color: Colors.textDark,
    fontSize: 14,
    marginBottom: 10,
  },
  pollOptionRow: { flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 8 },
  pollOptionInput: {
    flex: 1,
    backgroundColor: Colors.cardDark,
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 8,
    color: Colors.textDark,
    fontSize: 13,
  },
  addOptionBtn: { flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 4 },
  addOptionText: { color: Colors.primary, fontSize: 13, fontWeight: '600' },
  composerActions: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: 12,
    marginTop: 14,
  },
  cancelBtn: { paddingHorizontal: 16, paddingVertical: 10 },
  cancelBtnText: { color: Colors.textSecondaryDark, fontWeight: '600' },
  sendBtn: {
    backgroundColor: Colors.primary,
    borderRadius: 12,
    paddingHorizontal: 22,
    paddingVertical: 10,
  },
  sendBtnText: { color: Colors.allWhite, fontWeight: '700' },
});
