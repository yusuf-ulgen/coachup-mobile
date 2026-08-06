import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  FlatList,
  ActivityIndicator,
} from 'react-native';
import {
  ChevronLeft,
  ChevronRight,
  Calendar as CalendarIcon,
  Plus,
  Clock,
  Dumbbell,
  Users,
  Flag,
  CheckCircle,
} from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { Header } from '../../components/Header';
import { SideMenu } from '../../components/SideMenu';
import { AuthService } from '../../services/authService';
import { ScheduleService, ScheduledProgram } from '../../services/scheduleService';
import { TrainingService, TrainingSession } from '../../services/trainingService';
import { GroupClassService, ClassBooking } from '../../services/groupClassService';
import { GoalService, UserGoal } from '../../services/goalService';
import { supabase } from '../../services/supabaseClient';
import { TextInput } from 'react-native';

interface CalendarScreenProps {
  navigation?: any;
}

const WEEKDAYS = ['Pzt', 'Sal', 'Çar', 'Per', 'Cum', 'Cts', 'Paz'];
const MONTHS = [
  'Ocak', 'Şubat', 'Mart', 'Nisan', 'Mayıs', 'Haziran',
  'Temmuz', 'Ağustos', 'Eylül', 'Ekim', 'Kasım', 'Aralık'
];

export const CalendarScreen: React.FC<CalendarScreenProps> = ({ navigation }) => {
  const [currentDate, setCurrentDate] = useState<Date>(new Date());
  const [selectedDay, setSelectedDay] = useState<number>(new Date().getDate());
  const [userProfile, setUserProfile] = useState<any>(null);
  const [loading, setLoading] = useState(false);

  // Add Event Modal State
  const [showAddEventModal, setShowAddEventModal] = useState(false);
  const [newEventTitle, setNewEventTitle] = useState('');
  const [newEventStartTime, setNewEventStartTime] = useState('09:00');
  const [newEventEndTime, setNewEventEndTime] = useState('10:00');

  const [scheduledPrograms, setScheduledPrograms] = useState<ScheduledProgram[]>([]);
  const [completedSessions, setCompletedSessions] = useState<TrainingSession[]>([]);
  const [groupBookings, setGroupBookings] = useState<ClassBooking[]>([]);
  const [userGoals, setUserGoals] = useState<UserGoal[]>([]);

  const year = currentDate.getFullYear();
  const month = currentDate.getMonth();

  const selectedDateStr = `${year}-${String(month + 1).padStart(2, '0')}-${String(selectedDay).padStart(2, '0')}`;

  useEffect(() => {
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

  const loadCalendarContent = useCallback(async () => {
    if (!userProfile?.id && !userProfile?.user_id) return;
    const userId = userProfile?.id || userProfile?.user_id;

    setLoading(true);
    try {
      const [programs, sessions, bookings, goals] = await Promise.all([
        ScheduleService.fetchScheduledPrograms(userId, selectedDateStr),
        TrainingService.fetchCompletedSessionsForDate(userId, selectedDateStr),
        GroupClassService.fetchBookingsForDate(userId, selectedDateStr),
        GoalService.fetchGoalsForUser(userId),
      ]);

      setScheduledPrograms(programs);
      setCompletedSessions(sessions);
      setGroupBookings(bookings);
      setUserGoals(goals);
    } catch (e) {
      console.error('Error loading calendar content:', e);
    } finally {
      setLoading(false);
    }
  }, [userProfile, selectedDateStr]);

  useEffect(() => {
    loadCalendarContent();
  }, [loadCalendarContent]);

  // Calendar Grid Days Calculation
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const firstDayIndex = (new Date(year, month, 1).getDay() + 6) % 7; // Monday start

  const calendarCells = [];
  for (let i = 0; i < firstDayIndex; i++) {
    calendarCells.push(null);
  }
  for (let d = 1; d <= daysInMonth; d++) {
    calendarCells.push(d);
  }

  const handlePrevMonth = () => {
    setCurrentDate(new Date(year, month - 1, 1));
    setSelectedDay(1);
  };

  const handleNextMonth = () => {
    setCurrentDate(new Date(year, month + 1, 1));
    setSelectedDay(1);
  };

  const hasContent =
    scheduledPrograms.length > 0 ||
    completedSessions.length > 0 ||
    groupBookings.length > 0 ||
    userGoals.length > 0;

  const [menuVisible, setMenuVisible] = useState(false);

  return (
    <View style={styles.container}>
      <Header navigation={navigation} onOpenDrawer={() => setMenuVisible(true)} />
      <SideMenu visible={menuVisible} onClose={() => setMenuVisible(false)} navigation={navigation} />
      <View style={styles.header}>
        <Text style={styles.headerTitle}>Takvim</Text>
      </View>

      <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
        {/* Month Header Navigation */}
        <View style={styles.monthHeader}>
          <TouchableOpacity onPress={handlePrevMonth} style={styles.monthArrow}>
            <ChevronLeft size={24} color={Colors.textDark} />
          </TouchableOpacity>

          <Text style={styles.monthTitle}>
            {MONTHS[month]} {year}
          </Text>

          <TouchableOpacity onPress={handleNextMonth} style={styles.monthArrow}>
            <ChevronRight size={24} color={Colors.textDark} />
          </TouchableOpacity>
        </View>

        {/* Weekday Labels */}
        <View style={styles.weekdaysRow}>
          {WEEKDAYS.map((day) => (
            <Text key={day} style={styles.weekdayText}>
              {day}
            </Text>
          ))}
        </View>

        {/* Month Grid */}
        <View style={styles.gridContainer}>
          {calendarCells.map((dayNum, index) => {
            if (dayNum === null) {
              return <View key={`empty-${index}`} style={styles.dayCellEmpty} />;
            }

            const isSelected = dayNum === selectedDay;
            const isToday =
              dayNum === new Date().getDate() &&
              month === new Date().getMonth() &&
              year === new Date().getFullYear();

            return (
              <TouchableOpacity
                key={`day-${dayNum}`}
                style={[
                  styles.dayCell,
                  isSelected && styles.dayCellSelected,
                  isToday && !isSelected && styles.dayCellToday,
                ]}
                onPress={() => setSelectedDay(dayNum)}
                activeOpacity={0.8}
              >
                <Text
                  style={[
                    styles.dayCellText,
                    isSelected && styles.dayCellTextSelected,
                    isToday && !isSelected && styles.dayCellTextToday,
                  ]}
                >
                  {dayNum}
                </Text>
              </TouchableOpacity>
            );
          })}
        </View>

        {/* Day Schedule Content Section */}
        <View style={styles.scheduleSection}>
          <View style={styles.sectionTitleRow}>
            <CalendarIcon size={20} color={Colors.primary} />
            <Text style={styles.sectionTitle}>Günün Programı</Text>
          </View>

          {loading ? (
            <ActivityIndicator size="large" color={Colors.primary} style={{ marginTop: 24 }} />
          ) : !hasContent ? (
            <View style={styles.emptyCard}>
              <CalendarIcon size={36} color={Colors.textSecondaryDark} />
              <Text style={styles.emptyText}>Bu gün için program yok</Text>
            </View>
          ) : (
            <View style={styles.contentList}>
              {/* Goals */}
              {userGoals.map((goal) => (
                <View key={goal.id} style={styles.cardItem}>
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
                <View key={prog.id} style={styles.programCardRow}>
                  <View style={styles.iconBox}>
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
                          : ''}
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

              {/* Group Classes */}
              {groupBookings.map((booking) => (
                <View key={booking.id} style={styles.cardItem}>
                  <View style={styles.iconBox}>
                    <Users size={20} color={Colors.primary} />
                  </View>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.cardTitle}>
                      {booking.group_class?.name || 'Grup Dersi'}
                    </Text>
                    <Text style={styles.cardSubtitle}>
                      {booking.group_class?.instructor_name || 'Eğitmen'}
                    </Text>
                  </View>
                </View>
              ))}

              {/* Completed Sessions */}
              {completedSessions.map((session) => (
                <View key={session.id} style={styles.cardItem}>
                  <View style={styles.iconBox}>
                    <Dumbbell size={22} color={Colors.primary} />
                  </View>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.cardTitle}>
                      {session.program?.name || 'Tamamlanan Oturum'}
                    </Text>
                    <View style={styles.statusRow}>
                      <CheckCircle size={14} color="#4CAF50" />
                      <Text style={styles.statusText}>Tamamlandı</Text>
                    </View>
                  </View>
                </View>
              ))}
            </View>
          )}
        </View>
      </ScrollView>

      {/* Floating Action Button */}
      <TouchableOpacity
        style={styles.fab}
        activeOpacity={0.85}
        onPress={() => setShowAddEventModal(true)}
      >
        <Plus size={24} color={Colors.allWhite} />
      </TouchableOpacity>

      {/* Add User Event Modal */}
      {showAddEventModal && (
        <View style={styles.modalOverlay}>
          <View style={styles.modalCard}>
            <Text style={styles.modalTitle}>Etkinlik Ekle ({selectedDateStr})</Text>

            <TextInput
              style={styles.modalInput}
              placeholder="Etkinlik başlığı..."
              placeholderTextColor={Colors.textSecondaryDark}
              value={newEventTitle}
              onChangeText={setNewEventTitle}
            />

            <View style={styles.timeInputsRow}>
              <View style={{ flex: 1 }}>
                <Text style={styles.timeInputLabel}>Başlangıç</Text>
                <TextInput
                  style={styles.timeInput}
                  placeholder="09:00"
                  placeholderTextColor={Colors.textSecondaryDark}
                  value={newEventStartTime}
                  onChangeText={setNewEventStartTime}
                />
              </View>

              <View style={{ flex: 1 }}>
                <Text style={styles.timeInputLabel}>Bitiş</Text>
                <TextInput
                  style={styles.timeInput}
                  placeholder="10:00"
                  placeholderTextColor={Colors.textSecondaryDark}
                  value={newEventEndTime}
                  onChangeText={setNewEventEndTime}
                />
              </View>
            </View>

            <View style={styles.modalActions}>
              <TouchableOpacity
                style={styles.modalCancelBtn}
                onPress={() => setShowAddEventModal(false)}
              >
                <Text style={styles.modalCancelText}>İptal</Text>
              </TouchableOpacity>

              <TouchableOpacity
                style={styles.modalSaveBtn}
                onPress={async () => {
                  if (!newEventTitle.trim()) return;
                  const userId = userProfile?.id || userProfile?.user_id;
                  if (!userId) return;

                  try {
                    await supabase.from('user_events').insert({
                      user_id: userId,
                      title: newEventTitle.trim(),
                      event_date: selectedDateStr,
                      start_time: newEventStartTime,
                      end_time: newEventEndTime,
                    });
                    setNewEventTitle('');
                    setShowAddEventModal(false);
                    loadCalendarContent();
                  } catch (e) {
                    console.error('Error saving user event:', e);
                  }
                }}
              >
                <Text style={styles.modalSaveText}>Kaydet</Text>
              </TouchableOpacity>
            </View>
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
  header: {
    paddingHorizontal: 20,
    paddingTop: 50,
    paddingBottom: 16,
    backgroundColor: Colors.cardDark,
  },
  headerTitle: {
    fontSize: 22,
    fontWeight: '700',
    color: Colors.textDark,
  },
  scrollContent: {
    paddingBottom: 100,
  },
  monthHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    marginVertical: 16,
  },
  monthArrow: {
    padding: 8,
  },
  monthTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: Colors.textDark,
  },
  weekdaysRow: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    paddingHorizontal: 16,
    marginBottom: 8,
  },
  weekdayText: {
    flex: 1,
    textAlign: 'center',
    fontSize: 13,
    fontWeight: '600',
    color: Colors.textSecondaryDark,
  },
  gridContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    paddingHorizontal: 16,
  },
  dayCellEmpty: {
    width: '14.28%',
    height: 48,
  },
  dayCell: {
    width: '14.28%',
    height: 48,
    justifyContent: 'center',
    alignItems: 'center',
    borderRadius: 12,
  },
  dayCellSelected: {
    backgroundColor: Colors.primary,
  },
  dayCellToday: {
    borderWidth: 1.5,
    borderColor: Colors.primary,
  },
  dayCellText: {
    fontSize: 15,
    fontWeight: '600',
    color: Colors.textDark,
  },
  dayCellTextSelected: {
    color: Colors.allWhite,
  },
  dayCellTextToday: {
    color: Colors.primary,
  },
  scheduleSection: {
    paddingHorizontal: 20,
    marginTop: 24,
  },
  sectionTitleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 16,
    gap: 8,
  },
  sectionTitle: {
    fontSize: 20,
    fontWeight: '600',
    color: Colors.textDark,
  },
  emptyCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 32,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyText: {
    fontSize: 14,
    color: Colors.textSecondaryDark,
    marginTop: 12,
  },
  contentList: {
    gap: 12,
  },
  cardItem: {
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
  iconBox: {
    width: 50,
    height: 50,
    borderRadius: 14,
    backgroundColor: 'rgba(255, 96, 71, 0.1)',
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
  programCardRow: {
    flexDirection: 'row',
    alignItems: 'center',
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
  statusRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    marginTop: 2,
  },
  statusText: {
    fontSize: 12,
    color: '#4CAF50',
    fontWeight: '500',
  },
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
  modalOverlay: {
    ...StyleSheet.absoluteFill,
    backgroundColor: 'rgba(0,0,0,0.65)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
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
  modalInput: {
    backgroundColor: Colors.backgroundDark,
    borderRadius: 14,
    paddingHorizontal: 14,
    paddingVertical: 12,
    color: Colors.textDark,
    fontSize: 15,
    marginBottom: 16,
  },
  timeInputsRow: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 20,
  },
  timeInputLabel: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
    marginBottom: 4,
  },
  timeInput: {
    backgroundColor: Colors.backgroundDark,
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 10,
    color: Colors.textDark,
    fontSize: 14,
    textAlign: 'center',
  },
  modalActions: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: 12,
  },
  modalCancelBtn: {
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  modalCancelText: {
    color: Colors.textSecondaryDark,
    fontWeight: '600',
  },
  modalSaveBtn: {
    backgroundColor: Colors.primary,
    borderRadius: 12,
    paddingHorizontal: 22,
    paddingVertical: 10,
  },
  modalSaveText: {
    color: Colors.allWhite,
    fontWeight: '700',
  },
});
