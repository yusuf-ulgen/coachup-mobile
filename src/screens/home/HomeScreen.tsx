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
} from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { GYM_CONFIG } from '../../config/gym';
import { AuthService } from '../../services/authService';
import { SideMenu } from '../../components/SideMenu';
import { ScheduleService, ScheduledProgram } from '../../services/scheduleService';
import { TrainingService, TrainingSession } from '../../services/trainingService';
import { GroupClassService, ClassBooking } from '../../services/groupClassService';
import { GoalService, UserGoal } from '../../services/goalService';

interface HomeScreenProps {
  navigation: any;
}

interface WeekDay {
  name: string;
  dateString: string;
  dayOfMonth: number;
  isToday: boolean;
}

export const HomeScreen: React.FC<HomeScreenProps> = ({ navigation }) => {
  const [menuVisible, setMenuVisible] = useState(false);
  const [userProfile, setUserProfile] = useState<any>(null);
  const [selectedDate, setSelectedDate] = useState<string>(
    new Date().toISOString().split('T')[0]
  );
  const [weekDays, setWeekDays] = useState<WeekDay[]>([]);
  const [loading, setLoading] = useState(false);

  // Live Supabase Data State
  const [scheduledPrograms, setScheduledPrograms] = useState<ScheduledProgram[]>([]);
  const [completedSessions, setCompletedSessions] = useState<TrainingSession[]>([]);
  const [groupBookings, setGroupBookings] = useState<ClassBooking[]>([]);
  const [userGoals, setUserGoals] = useState<UserGoal[]>([]);

  useEffect(() => {
    // Generate 60 days range (30 days past + today + 29 days future)
    const days: WeekDay[] = [];
    const today = new Date();
    const todayStr = today.toISOString().split('T')[0];
    const dayNames = ['Paz', 'Pzt', 'Sal', 'Çar', 'Per', 'Cm', 'Cts'];

    for (let i = -30; i < 30; i++) {
      const d = new Date();
      d.setDate(today.getDate() + i);
      const dateStr = d.toISOString().split('T')[0];
      days.push({
        name: dayNames[d.getDay()],
        dateString: dateStr,
        dayOfMonth: d.getDate(),
        isToday: dateStr === todayStr,
      });
    }
    setWeekDays(days);

    // Fetch Profile
    const loadProfile = async () => {
      try {
        const profile = await AuthService.getCurrentProfile();
        setUserProfile(profile);
      } catch (e) {
        console.error('Error fetching profile:', e);
      }
    };
    loadProfile();
  }, []);

  // Fetch Live Supabase Data on date or user change
  const fetchLiveData = useCallback(async () => {
    if (!userProfile?.id && !userProfile?.user_id) return;
    const userId = userProfile?.id || userProfile?.user_id;

    setLoading(true);
    try {
      const [programs, sessions, bookings, goals] = await Promise.all([
        ScheduleService.fetchScheduledPrograms(userId, selectedDate),
        TrainingService.fetchCompletedSessionsForDate(userId, selectedDate),
        GroupClassService.fetchBookingsForDate(userId, selectedDate),
        GoalService.fetchGoalsForUser(userId),
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
      <View style={styles.topHeader}>
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

        <TouchableOpacity
          style={styles.circleIconButton}
          onPress={() => navigation.navigate('Notifications')}
          activeOpacity={0.8}
        >
          <Bell size={20} color={Colors.textDark} />
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
            <Text style={styles.welcomeTitle}>
              Merhaba, {userProfile?.name || 'Sporcu'}!
            </Text>
            <Text style={styles.welcomeSubtitle}>Hadi Başlayalım!</Text>
          </View>

          {/* Streak Counter */}
          <TouchableOpacity
            style={styles.streakContainer}
            onPress={() => navigation.navigate('Streak')}
            activeOpacity={0.7}
          >
            <Flame size={24} color={Colors.primary} />
            <Text style={styles.streakCount}>{streakCount}</Text>
            <Text style={styles.streakArrow}>›</Text>
          </TouchableOpacity>
        </View>

        {/* Day Selector Strip */}
        <View style={styles.daySelectorWrapper}>
          <FlatList
            horizontal
            data={weekDays}
            keyExtractor={(item) => item.dateString}
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={styles.daySelectorList}
            initialScrollIndex={30}
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
                Bu gün için henüz program bulunmuyor
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
                      <Clock size={14} color="rgba(255,255,255,0.7)" />
                      <Text style={styles.timeText}>
                        {prog.start_time} - {prog.end_time}
                      </Text>
                    </View>
                  </View>
                </View>
              ))}

              {/* Completed Sessions */}
              {completedSessions.map((session) => (
                <View key={session.id} style={styles.completedCard}>
                  <View style={styles.completedIconBox}>
                    <Dumbbell size={22} color={Colors.primary} />
                  </View>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.cardTitle}>
                      {session.program?.name || 'Antrenman Oturumu'}
                    </Text>
                    <View style={styles.statusRow}>
                      <CheckCircle size={14} color="#4CAF50" />
                      <Text style={styles.completedStatusText}>Tamamlandı</Text>
                    </View>
                  </View>
                </View>
              ))}
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
    backgroundColor: Colors.backgroundDark,
  },
  circleIconButton: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: Colors.cardDark,
    justifyContent: 'center',
    alignItems: 'center',
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
    fontSize: 26,
    fontWeight: '700',
    color: Colors.textDark,
  },
  welcomeSubtitle: {
    fontSize: 22,
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
});
