import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  FlatList,
  ActivityIndicator,
  Image,
} from 'react-native';
import {
  Menu,
  Bell,
  Flame,
  Dumbbell,
  Calendar as CalendarIcon,
  CheckCircle,
  Flag,
  Clock,
  Users,
} from 'lucide-react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Colors } from '../../theme/colors';
import { GYM_CONFIG } from '../../config/gym';
import { AuthService } from '../../services/authService';
import { SideMenu } from '../../components/SideMenu';
import { ScheduleService, ScheduledProgram } from '../../services/scheduleService';
import { TrainingService, TrainingSession } from '../../services/trainingService';
import { GroupClassService, ClassBooking } from '../../services/groupClassService';
import { GoalService, UserGoal } from '../../services/goalService';
import { UserService } from '../../services/userService';
import { NotificationService } from '../../services/notificationService';
import { supabase } from '../../services/supabaseClient';

import { HomeTabState } from '../../navigation/HomeTabState';

interface HomeScreenProps {
  navigation: any;
}

interface WeekDay {
  name: string;
  dateString: string;
  dayOfMonth: number;
  isToday: boolean;
}

const buildWeekDays = (): WeekDay[] => {
  const days: WeekDay[] = [];
  const today = new Date();
  const todayStr = today.toISOString().split('T')[0];
  const dayNames = ['Pzt', 'Sal', 'Çar', 'Per', 'Cum', 'Cts', 'Paz'];

  for (let i = -30; i < 30; i++) {
    const d = new Date();
    d.setDate(today.getDate() + i);
    const dateStr = d.toISOString().split('T')[0];
    const nameIndex = (d.getDay() + 6) % 7;
    days.push({
      name: dayNames[nameIndex],
      dateString: dateStr,
      dayOfMonth: d.getDate(),
      isToday: dateStr === todayStr,
    });
  }
  return days;
};

export const HomeScreen: React.FC<HomeScreenProps> = ({ navigation }) => {
  const insets = useSafeAreaInsets();
  const flatListRef = React.useRef<FlatList>(null);

  const [menuVisible, setMenuVisible] = useState(false);
  const [userProfile, setUserProfile] = useState<any>(null);
  const [selectedDate, setSelectedDateState] = useState<string>(
    HomeTabState.selectedDate || new Date().toISOString().split('T')[0]
  );
  // Okunmamış bildirim sayısı
  const [unreadNotifCount, setUnreadNotifCount] = useState(0);

  const setSelectedDate = (dateStr: string) => {
    HomeTabState.selectedDate = dateStr;
    setSelectedDateState(dateStr);
  };
  const [weekDays, setWeekDays] = useState<WeekDay[]>(() => buildWeekDays());
  const [loading, setLoading] = useState(false);

  // Live Supabase Data State
  const [scheduledPrograms, setScheduledPrograms] = useState<ScheduledProgram[]>([]);
  const [completedSessions, setCompletedSessions] = useState<TrainingSession[]>([]);
  const [groupBookings, setGroupBookings] = useState<ClassBooking[]>([]);
  const [userGoals, setUserGoals] = useState<UserGoal[]>([]);

  const [availableMemberships, setAvailableMemberships] = useState<any[]>([]);
  const [showMembershipPicker, setShowMembershipPicker] = useState(false);

  useEffect(() => {
    // Auto-scroll to today in day selector strip
    const todayIndex = weekDays.findIndex((w) => w.isToday);
    if (todayIndex >= 0 && flatListRef.current) {
      setTimeout(() => {
        try {
          flatListRef.current?.scrollToIndex({
            index: Math.max(0, todayIndex - 2),
            animated: false,
          });
        } catch (e) {
          // Ignore layout scroll errors
        }
      }, 150);
    }
  }, []);

  useEffect(() => {
    // Fetch Profile
    const loadProfile = async () => {
      try {
        const profile = await AuthService.getCurrentProfile();
        setUserProfile(profile);
        if (profile) {
          const uid = profile.id || profile.user_id;
          const memberships = await UserService.fetchAvailableMemberships(uid);
          setAvailableMemberships(memberships);
          if (memberships.length > 1 && !profile.gym_id) {
            setShowMembershipPicker(true);
          }
          if (profile.default_screen && !HomeTabState.hasAppliedDefaultTab) {
            HomeTabState.hasAppliedDefaultTab = true;
            const tabMap: Record<string, string> = {
              calendar: 'CalendarTab',
              training: 'TrainingTab',
              community: 'CommunityTab',
              qr: 'QRTab',
            };
            const targetTab = tabMap[profile.default_screen];
            if (targetTab) {
              setTimeout(() => {
                try {
                  navigation?.navigate(targetTab);
                } catch (err) {}
              }, 100);
            }
          }
        }
      } catch (e) {
        console.error('Error fetching profile:', e);
      }
    };
    loadProfile();

    // Okunmamış bildirim sayısını çek
    const loadUnreadCount = async () => {
      try {
        const user = await AuthService.getCurrentUser();
        if (user?.id) {
          const { count } = await supabase
            .from('notifications')
            .select('*', { count: 'exact', head: true })
            .eq('user_id', user.id)
            .eq('is_read', false);
          setUnreadNotifCount(count || 0);
        }
      } catch (e) {
        // Bildirim sayısı hatası sessizce geç
      }
    };
    loadUnreadCount();
  }, []);

  // Fetch Live Supabase Data on date or user change
  const fetchLiveData = useCallback(async () => {
    if (!userProfile?.id && !userProfile?.user_id) return;
    const userId = userProfile?.id || userProfile?.user_id;
    const isIndividualUser = !userProfile?.gym_id;

    setLoading(true);
    try {
      const [programs, sessions, bookings, goals] = await Promise.all([
        ScheduleService.fetchScheduledPrograms(userId, selectedDate),
        TrainingService.fetchCompletedSessionsForDate(userId, selectedDate),
        isIndividualUser
          ? Promise.resolve([])
          : GroupClassService.fetchBookingsForDate(userId, selectedDate),
        GoalService.fetchGoalsForDate(userId, selectedDate),
      ]);

      setScheduledPrograms(programs);
      setCompletedSessions(sessions);
      setGroupBookings(bookings);
      setUserGoals(goals);
    } catch (e) {
      console.error('Error loading Supabase live data:', e);
    } finally {
      setLoading(false);
    }
  }, [userProfile, selectedDate]);

  useEffect(() => {
    fetchLiveData();
  }, [fetchLiveData]);

  const streakCount = userProfile?.current_streak || 0;
  const hasContent =
    scheduledPrograms.length > 0 ||
    completedSessions.length > 0 ||
    groupBookings.length > 0 ||
    userGoals.length > 0;

  return (
    <View style={styles.container}>
      {/* Top Navigation Bar */}
      <View style={[styles.topHeader, { paddingTop: Math.max(12, insets.top + 6) }]}>
        <TouchableOpacity
          style={styles.circleIconButton}
          onPress={() => setMenuVisible(true)}
          activeOpacity={0.8}
        >
          <Menu size={20} color={Colors.textDark} />
        </TouchableOpacity>

        <Image
          source={GYM_CONFIG.LOGIN_LOGO}
          style={styles.headerLogo}
          resizeMode="contain"
        />

        {/* Bildirim Butonu + Badge */}
        <TouchableOpacity
          style={styles.circleIconButton}
          onPress={() => navigation.navigate('Notifications')}
          activeOpacity={0.8}
        >
          <Bell size={20} color={Colors.textDark} />
          {unreadNotifCount > 0 && (
            <View style={styles.notifBadge}>
              <Text style={styles.notifBadgeText}>
                {unreadNotifCount > 9 ? '9+' : unreadNotifCount}
              </Text>
            </View>
          )}
        </TouchableOpacity>
      </View>

      <ScrollView
        style={styles.scrollContainer}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        {/* Welcome Section */}
        <View style={styles.welcomeSection}>
          <View style={{ flex: 1 }}>
            {userProfile?.name ? (
              <>
                <Text style={styles.welcomeTitle}>
                  Merhaba, {userProfile.name}!
                </Text>
                <Text style={styles.welcomeSubtitle}>Hadi Başlayalım!</Text>
              </>
            ) : (
              <>
                <Text style={styles.welcomeTitleBig}>Hadi</Text>
                <Text style={styles.welcomeTitleBig}>Başlayalım!</Text>
              </>
            )}
          </View>

          {/* Streak Counter */}
          <TouchableOpacity
            style={styles.streakContainer}
            onPress={() => navigation.navigate('Streak')}
            activeOpacity={0.7}
          >
            <Flame
              size={24}
              color={
                streakCount >= 30
                  ? '#E91E63'
                  : streakCount >= 14
                  ? '#FF5722'
                  : streakCount >= 7
                  ? '#FF9800'
                  : streakCount > 0
                  ? '#FFC107'
                  : '#9E9E9E'
              }
            />
            <Text style={styles.streakCount}>{streakCount}</Text>
            <Text style={styles.streakArrow}>›</Text>
          </TouchableOpacity>
        </View>

        {/* Day Selector Strip */}
        <View style={styles.daySelectorWrapper}>
          <FlatList
            ref={flatListRef}
            horizontal
            data={weekDays}
            keyExtractor={(item) => item.dateString}
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={styles.daySelectorList}
            initialScrollIndex={28}
            onScrollToIndexFailed={(info) => {
              setTimeout(() => {
                try {
                  flatListRef.current?.scrollToIndex({
                    index: Math.max(0, info.index),
                    animated: false,
                  });
                } catch (e) {}
              }, 150);
            }}
            getItemLayout={(_, index) => ({
              length: 64,
              offset: 64 * index,
              index,
            })}
            renderItem={({ item }) => {
              const isSelected = item.dateString === selectedDate;
              return (
                <TouchableOpacity
                  style={[
                    styles.dayBox,
                    isSelected && styles.dayBoxSelected,
                  ]}
                  onPress={() => setSelectedDate(item.dateString)}
                  activeOpacity={0.8}
                >
                  <Text
                    style={[
                      styles.dayName,
                      isSelected && styles.dayTextSelected,
                    ]}
                  >
                    {item.name}
                  </Text>
                  <Text
                    style={[
                      styles.dayNumber,
                      isSelected && styles.dayTextSelected,
                    ]}
                  >
                    {item.dayOfMonth}
                  </Text>
                </TouchableOpacity>
              );
            }}
          />
        </View>

        {/* Daily Program Section */}
        <View style={styles.programSection}>
          <View style={styles.sectionHeader}>
            <Dumbbell size={20} color={Colors.primary} />
            <Text style={styles.sectionTitle}>Günün Programı</Text>
          </View>

          {loading ? (
            <ActivityIndicator
              size="large"
              color={Colors.primary}
              style={{ marginTop: 24 }}
            />
          ) : !hasContent ? (
            <View style={styles.emptyProgramCard}>
              <CalendarIcon size={40} color={Colors.textSecondaryDark} />
              <Text style={styles.emptyProgramText}>
                Bu gün için program bulunmuyor
              </Text>
            </View>
          ) : (
            <View style={styles.programList}>
              {/* User Goals */}
              {userGoals.map((goal) => (
                <View key={goal.id} style={styles.goalCard}>
                  <View style={styles.goalIconBox}>
                    <Flag size={20} color="#FF9800" />
                  </View>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.cardTitle}>{goal.title}</Text>
                    <Text style={styles.cardSubtitle}>
                      Hedef · {goal.progress_percentage}% tamamlandı
                    </Text>
                  </View>
                </View>
              ))}

              {/* Scheduled Programs */}
              {scheduledPrograms.map((prog) => (
                <View key={prog.id} style={styles.programCard}>
                  <View style={styles.programIconBox}>
                    <Dumbbell size={20} color={Colors.primary} />
                  </View>
                  <View style={styles.purplePill}>
                    <View style={{ flex: 1 }}>
                      <Text style={styles.purplePillTitle}>
                        {prog.program?.name || 'Antrenman Programı'}
                      </Text>
                      <Text style={styles.purplePillSubtitle}>
                        {prog.coach?.name
                          ? `${prog.coach.name} ${prog.coach.surname || ''}`
                          : 'Koç Atanmadı'}
                      </Text>
                    </View>
                    <View style={styles.timeRow}>
                      <CalendarIcon size={14} color="rgba(255,255,255,0.7)" />
                      <Text style={styles.timeText}>
                        {prog.start_time} / {prog.end_time}
                      </Text>
                    </View>
                  </View>
                </View>
              ))}

              {/* Tamamlanan Oturumlar */}
              {completedSessions.map((session: any) => {
                const completionTime = session.completed_at
                  ? (() => {
                      try {
                        const d = new Date(session.completed_at);
                        return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
                      } catch {
                        return null;
                      }
                    })()
                  : null;
                const durationText =
                  session.started_at && session.completed_at
                    ? (() => {
                        try {
                          const mins = Math.floor(
                            (new Date(session.completed_at).getTime() -
                              new Date(session.started_at).getTime()) /
                              60000
                          );
                          return mins > 0 ? `${mins} dk` : null;
                        } catch {
                          return null;
                        }
                      })()
                    : null;

                const categoryEmojis: Record<string, string> = {
                  running: '🏃',
                  walking: '🚶',
                  cycling: '🚴',
                  swimming: '🏊',
                  crossfit: '🏋️',
                  hiit: '⚡',
                  yoga: '🧘',
                  custom: '💪',
                };
                const emoji =
                  categoryEmojis[session.category] ||
                  session.emoji ||
                  session.program?.emoji;

                return (
                  <View key={session.id} style={styles.completedCard}>
                    <View style={styles.completedIconBox}>
                      {emoji ? (
                        <Text style={{ fontSize: 24 }}>{emoji}</Text>
                      ) : (
                        <Dumbbell size={22} color={Colors.primary} />
                      )}
                    </View>
                    <View style={{ flex: 1 }}>
                      <Text style={styles.cardTitle}>
                        {session.program?.name || session.title || 'Antrenman Oturumu'}
                      </Text>
                      <View style={styles.statusRow}>
                        <CheckCircle size={14} color="#4CAF50" />
                        <Text style={styles.completedStatusText}>Tamamlandı</Text>
                      </View>
                    </View>
                    {(completionTime || durationText) && (
                      <View style={{ alignItems: 'flex-end', gap: 2 }}>
                        {completionTime && (
                          <Text style={styles.completionTimeText}>{completionTime}</Text>
                        )}
                        {durationText && (
                          <Text style={styles.durationText}>{durationText}</Text>
                        )}
                      </View>
                    )}
                  </View>
                );
              })}

              {/* Grup Dersi Kartları (GroupClassProgramCard) */}
              {groupBookings.map((booking: ClassBooking) => {
                const groupClass = booking.group_class || (booking as any).class;
                const statusStr = booking.status?.toLowerCase() || '';
                const isWaitlist = statusStr === 'waitlist' || statusStr === 'waiting';
                const isConfirmed = statusStr === 'confirmed' || statusStr === 'booked';

                const startTimeStr = groupClass?.start_time ? groupClass.start_time.substring(0, 5) : null;
                const endTimeStr = groupClass?.end_time ? groupClass.end_time.substring(0, 5) : null;
                const timeDisplay = startTimeStr ? (endTimeStr ? `${startTimeStr} - ${endTimeStr}` : startTimeStr) : null;

                return (
                  <View key={booking.id} style={styles.groupClassCard}>
                    <View style={styles.groupClassIconBox}>
                      <Users size={20} color="#9C27B0" />
                    </View>
                    <View style={{ flex: 1 }}>
                      <Text style={styles.cardTitle}>
                        {groupClass?.name || 'Grup Dersi'}
                      </Text>
                      <Text style={styles.cardSubtitle}>
                        {groupClass?.instructor_name
                          ? `Eğitmen: ${groupClass.instructor_name}`
                          : 'Grup Eğitimi'}
                      </Text>
                      {timeDisplay && (
                        <View style={styles.statusRow}>
                          <Clock size={12} color={Colors.textSecondaryDark} />
                          <Text style={[styles.cardSubtitle, { marginTop: 0, marginLeft: 4 }]}>
                            {timeDisplay}
                          </Text>
                        </View>
                      )}
                    </View>
                    <View
                      style={[
                        styles.bookingStatusBadge,
                        isWaitlist && styles.bookingStatusWaitlist,
                      ]}
                    >
                      <Text style={styles.bookingStatusText}>
                        {isConfirmed
                          ? 'Katılıyorum'
                          : isWaitlist
                          ? 'Yedekte'
                          : booking.status || 'Katılıyorum'}
                      </Text>
                    </View>
                  </View>
                );
              })}
            </View>
          )}
        </View>
      </ScrollView>

      {/* Side Drawer Modal */}
      <SideMenu
        visible={menuVisible}
        onClose={() => setMenuVisible(false)}
        userProfile={userProfile}
        onNavigate={(route) => navigation.navigate(route)}
      />

      {/* Membership Picker Modal */}
      {showMembershipPicker && (
        <View style={styles.modalOverlay}>
          <View style={styles.modalCard}>
            <Text style={styles.modalTitle}>Üyelik Seçin</Text>
            {availableMemberships.map((m: any) => (
              <TouchableOpacity
                key={m.id}
                style={styles.membershipOption}
                onPress={async () => {
                  const uid = userProfile?.id || userProfile?.user_id;
                  const gymId = m.gym?.id || m.plan?.gym_id;
                  const gymName = m.gym?.name || m.plan?.name || 'Salon';
                  if (uid && gymId) {
                    await UserService.selectMembership(uid, gymId, gymName);
                    setShowMembershipPicker(false);
                    const updated = await AuthService.getCurrentProfile();
                    setUserProfile(updated);
                  }
                }}
              >
                <Text style={{ fontSize: 16, fontWeight: '600', color: Colors.textDark }}>
                  {m.plan?.name || 'Üyelik'}
                </Text>
                {m.gym?.name && (
                  <Text style={{ fontSize: 13, color: Colors.textSecondaryDark, marginTop: 2 }}>
                    {m.gym.name}
                  </Text>
                )}
              </TouchableOpacity>
            ))}
          </View>
        </View>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.backgroundDark,
  },
  topHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingTop: 50,
    paddingBottom: 12,
    backgroundColor: 'transparent',
  },
  circleIconButton: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: Colors.cardDark,
    justifyContent: 'center',
    alignItems: 'center',
    position: 'relative', // Badge için
  },
  headerLogo: {
    height: 32,
    width: 120,
  },
  scrollContainer: {
    flex: 1,
  },
  scrollContent: {
    paddingBottom: 100,
  },
  welcomeSection: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    marginTop: 16,
    marginBottom: 20,
  },
  welcomeTitle: {
    fontSize: 28,
    fontWeight: '700',
    color: Colors.textDark,
  },
  welcomeTitleBig: {
    fontSize: 32,
    fontWeight: '700',
    color: Colors.textDark,
  },
  welcomeSubtitle: {
    fontSize: 24,
    fontWeight: '500',
    color: Colors.textSecondaryDark,
    marginTop: 2,
  },
  streakContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingLeft: 8,
  },
  streakCount: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.textDark,
    marginLeft: 6,
  },
  streakArrow: {
    fontSize: 20,
    color: Colors.primary,
    marginLeft: 4,
    fontWeight: '600',
  },
  daySelectorWrapper: {
    marginVertical: 12,
  },
  daySelectorList: {
    paddingHorizontal: 20,
    gap: 8,
  },
  dayBox: {
    width: 56,
    height: 58,
    borderRadius: 12,
    backgroundColor: Colors.cardDark,
    justifyContent: 'center',
    alignItems: 'center',
  },
  dayBoxSelected: {
    borderWidth: 2,
    borderColor: Colors.primary,
  },
  dayName: {
    fontSize: 12,
    fontWeight: '500',
    color: Colors.textSecondaryDark,
  },
  dayNumber: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.textDark,
    marginTop: 2,
  },
  dayTextSelected: {
    color: Colors.primary,
  },
  programSection: {
    paddingHorizontal: 20,
    marginTop: 24,
  },
  sectionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 16,
  },
  sectionTitle: {
    fontSize: 20,
    fontWeight: '600',
    color: Colors.textDark,
    marginLeft: 8,
  },
  emptyProgramCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 32,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyProgramText: {
    fontSize: 14,
    color: Colors.textSecondaryDark,
    marginTop: 12,
    textAlign: 'center',
  },
  programList: {
    gap: 12,
  },
  goalCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 14,
    gap: 12,
  },
  goalIconBox: {
    width: 44,
    height: 44,
    borderRadius: 12,
    backgroundColor: 'rgba(255, 152, 0, 0.12)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  cardTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: Colors.textDark,
  },
  cardSubtitle: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    marginTop: 2,
  },
  programCard: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  programIconBox: {
    width: 50,
    height: 50,
    borderRadius: 14,
    backgroundColor: 'rgba(255, 96, 71, 0.1)',
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  purplePill: {
    flex: 1,
    backgroundColor: Colors.purple100,
    borderRadius: 16,
    paddingHorizontal: 16,
    paddingVertical: 14,
    flexDirection: 'row',
    alignItems: 'center',
  },
  purplePillTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#FFFFFF',
  },
  purplePillSubtitle: {
    fontSize: 13,
    color: 'rgba(255,255,255,0.7)',
    marginTop: 2,
  },
  timeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  timeText: {
    fontSize: 13,
    color: 'rgba(255,255,255,0.9)',
    fontWeight: '500',
  },
  completedCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 14,
    gap: 12,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
  },
  completionTimeText: {
    fontSize: 13,
    fontWeight: '500',
    color: Colors.textDark,
  },
  durationText: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
  },
  completedIconBox: {
    width: 50,
    height: 50,
    borderRadius: 14,
    backgroundColor: 'rgba(255, 96, 71, 0.1)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  statusRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    marginTop: 2,
  },
  completedStatusText: {
    fontSize: 12,
    color: '#4CAF50',
    fontWeight: '500',
  },
  modalOverlay: {
    ...StyleSheet.absoluteFill,
    backgroundColor: 'rgba(0,0,0,0.65)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
    zIndex: 999,
  },
  modalCard: {
    width: '100%',
    backgroundColor: Colors.cardDark,
    borderRadius: 24,
    padding: 24,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.textDark,
    marginBottom: 16,
  },
  membershipOption: {
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: Colors.borderDark,
  },
  // Grup Dersi Kartı Stilleri
  groupClassCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 14,
    gap: 12,
    borderLeftWidth: 3,
    borderLeftColor: '#9C27B0',
  },
  groupClassIconBox: {
    width: 44,
    height: 44,
    borderRadius: 12,
    backgroundColor: 'rgba(156, 39, 176, 0.12)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  bookingStatusBadge: {
    backgroundColor: 'rgba(76, 175, 80, 0.15)',
    borderRadius: 8,
    paddingHorizontal: 8,
    paddingVertical: 4,
  },
  bookingStatusWaitlist: {
    backgroundColor: 'rgba(255, 152, 0, 0.15)',
  },
  bookingStatusText: {
    fontSize: 11,
    fontWeight: '600',
    color: '#4CAF50',
  },
  // Bildirim Badge Stilleri
  notifBadge: {
    position: 'absolute',
    top: 4,
    right: 4,
    backgroundColor: Colors.primary,
    borderRadius: 8,
    minWidth: 16,
    height: 16,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 3,
  },
  notifBadgeText: {
    color: Colors.allWhite,
    fontSize: 10,
    fontWeight: '700',
  },
});

