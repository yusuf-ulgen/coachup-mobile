import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  FlatList,
  ActivityIndicator,
  TextInput,
} from 'react-native';
import { feedback } from '../../services/feedbackService';
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
  Edit3,
  Trash2,
  Star,
  Activity,
  Droplets,
  Bike,
  Apple,
} from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { Header } from '../../components/Header';
import { SideMenu } from '../../components/SideMenu';
import { AuthService } from '../../services/authService';
import { ScheduleService, ScheduledProgram } from '../../services/scheduleService';
import { TrainingService, TrainingSession } from '../../services/trainingService';
import { GroupClassService, ClassBooking } from '../../services/groupClassService';
import { GoalService, UserGoal } from '../../services/goalService';
import { GymEventService, GymEvent, EventParticipant } from '../../services/gymEventService';
import { CustomAlert } from '../../components/CustomAlertModal';
import { supabase } from '../../services/supabaseClient';

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
  const [newEventColor, setNewEventColor] = useState('#2196F3');
  const [newEventIcon, setNewEventIcon] = useState('Calendar');
  const [newEventNotes, setNewEventNotes] = useState('');

  const [eventDays, setEventDays] = useState<Set<number>>(new Set());

  const [scheduledPrograms, setScheduledPrograms] = useState<ScheduledProgram[]>([]);
  const [completedSessions, setCompletedSessions] = useState<TrainingSession[]>([]);
  const [groupBookings, setGroupBookings] = useState<ClassBooking[]>([]);
  const [openClasses, setOpenClasses] = useState<any[]>([]);
  const [userGoals, setUserGoals] = useState<UserGoal[]>([]);
  const [userEvents, setUserEvents] = useState<any[]>([]);
  const [editingEventId, setEditingEventId] = useState<string | null>(null);
  const [gymEvents, setGymEvents] = useState<GymEvent[]>([]);
  const [eventParticipations, setEventParticipations] = useState<EventParticipant[]>([]);

  const year = currentDate.getFullYear();
  const month = currentDate.getMonth();
  const yearMonthPrefix = `${year}-${String(month + 1).padStart(2, '0')}`;

  const selectedDateStr = `${yearMonthPrefix}-${String(selectedDay).padStart(2, '0')}`;

  const loadMonthEventDays = useCallback(async () => {
    if (!userProfile?.id && !userProfile?.user_id) return;
    const userId = userProfile?.id || userProfile?.user_id;
    try {
      const [eventsRes, programsRes, goalsRes, classesRes, gymEventsRes] = await Promise.all([
        supabase
          .from('user_events')
          .select('event_date')
          .eq('user_id', userId)
          .gte('event_date', `${yearMonthPrefix}-01`)
          .lte('event_date', `${yearMonthPrefix}-31`),
        supabase
          .from('scheduled_programs')
          .select('scheduled_date')
          .eq('user_id', userId)
          .gte('scheduled_date', `${yearMonthPrefix}-01`)
          .lte('scheduled_date', `${yearMonthPrefix}-31`),
        supabase
          .from('user_goals')
          .select('target_date, created_at')
          .eq('user_id', userId),
        userProfile?.gym_id
          ? supabase
              .from('group_classes')
              .select('date_str')
              .eq('gym_id', userProfile.gym_id)
              .gte('date_str', `${yearMonthPrefix}-01`)
              .lte('date_str', `${yearMonthPrefix}-31`)
          : Promise.resolve({ data: [] }),
        supabase
          .from('gym_events')
          .select('event_date')
          .gte('event_date', `${yearMonthPrefix}-01`)
          .lte('event_date', `${yearMonthPrefix}-31`),
      ]);

      const daysSet = new Set<number>();
      eventsRes.data?.forEach((item: any) => {
        if (item.event_date) {
          const d = parseInt(item.event_date.split('-')[2], 10);
          if (!isNaN(d)) daysSet.add(d);
        }
      });
      programsRes.data?.forEach((item: any) => {
        if (item.scheduled_date) {
          const d = parseInt(item.scheduled_date.split('-')[2], 10);
          if (!isNaN(d)) daysSet.add(d);
        }
      });
      goalsRes.data?.forEach((item: any) => {
        const dateStr = item.target_date || item.created_at;
        if (dateStr && dateStr.startsWith(yearMonthPrefix)) {
          const d = parseInt(dateStr.split('-')[2], 10);
          if (!isNaN(d)) daysSet.add(d);
        }
      });
      classesRes.data?.forEach((item: any) => {
        if (item.date_str) {
          const d = parseInt(item.date_str.split('-')[2], 10);
          if (!isNaN(d)) daysSet.add(d);
        }
      });
      gymEventsRes.data?.forEach((item: any) => {
        if (item.event_date) {
          const d = parseInt(item.event_date.split('-')[2], 10);
          if (!isNaN(d)) daysSet.add(d);
        }
      });

      setEventDays(daysSet);
    } catch (e) {
      console.error('Error loading month event days:', e);
    }
  }, [userProfile, yearMonthPrefix]);

  useEffect(() => {
    loadMonthEventDays();
  }, [loadMonthEventDays]);

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
      const [programs, sessions, bookings, goals, eventsRes, gymEventsData, participationsData, openClassesData] =
        await Promise.all([
          ScheduleService.fetchScheduledPrograms(userId, selectedDateStr),
          TrainingService.fetchCompletedSessionsForDate(userId, selectedDateStr),
          GroupClassService.fetchBookingsForDate(userId, selectedDateStr),
          GoalService.fetchGoalsForDate(userId, selectedDateStr),
          supabase
            .from('user_events')
            .select('*')
            .eq('user_id', userId)
            .eq('event_date', selectedDateStr),
          GymEventService.fetchEventsForDate(selectedDateStr),
          GymEventService.fetchParticipationsForDate(userId, selectedDateStr),
          GroupClassService.fetchClassesForDate(userId, selectedDateStr),
        ]);

      setScheduledPrograms(programs);
      setCompletedSessions(sessions);
      setGroupBookings(bookings);
      setUserGoals(goals);
      setUserEvents(eventsRes.data || []);
      setGymEvents(gymEventsData);
      setEventParticipations(participationsData);
      setOpenClasses(openClassesData);
    } catch (e) {
      console.error('Error loading calendar content:', e);
      feedback.error({ title: 'Hata', message: e, fallbackMessage: 'İçerikler yüklenemedi.' });
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
    openClasses.length > 0 ||
    userGoals.length > 0 ||
    userEvents.length > 0 ||
    gymEvents.length > 0;

  const [menuVisible, setMenuVisible] = useState(false);

  return (
    <View style={styles.container}>
      <Header navigation={navigation} onOpenDrawer={() => setMenuVisible(true)} />
      <SideMenu visible={menuVisible} onClose={() => setMenuVisible(false)} navigation={navigation} />
      {navigation?.canGoBack && navigation.canGoBack() && (
        <View style={styles.header}>
          <Text style={styles.headerTitle}>Takvim</Text>
        </View>
      )}

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
                {eventDays.has(dayNum) && (
                  <View
                    style={[
                      styles.eventDot,
                      isSelected && { backgroundColor: Colors.allWhite },
                    ]}
                  />
                )}
              </TouchableOpacity>
            );
          })}
        </View>

        {/* Day Schedule Content Section */}
        <View style={styles.scheduleSection}>
          <View style={styles.sectionTitleRow}>
            <CalendarIcon size={20} color={Colors.primary} />
            <Text style={styles.sectionTitle}>
              {!userProfile?.gym_id ? 'Katıldığım Etkinlikler' : 'Günün Programı'}
            </Text>
          </View>

          {loading ? (
            <ActivityIndicator size="small" color={Colors.primary} style={{ marginTop: 24 }} />
          ) : !hasContent ? (
            <View style={styles.emptyCard}>
              <CalendarIcon size={36} color={Colors.textSecondaryDark} />
              <Text style={styles.emptyText}>Bu gün için program yok</Text>
            </View>
          ) : (
            <View style={styles.contentList}>
              {/* Goals */}
              {userGoals.length > 0 && (
                <View style={{ gap: 8, marginBottom: 8 }}>
                  <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 4 }}>
                    <Flag size={16} color="#FF9800" />
                    <Text style={{ fontSize: 16, fontWeight: '600', color: Colors.textDark }}>
                      Hedefler
                    </Text>
                  </View>
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
                </View>
              )}

              {/* Scheduled Programs */}
              {scheduledPrograms.map((prog) => {
                const progName = prog.program?.name?.toLowerCase() || '';
                let bgColor = Colors.cardDark; // default
                if (progName.includes('cardio') || progName.includes('kardiyo')) bgColor = '#F44336';
                else if (progName.includes('strength') || progName.includes('güç') || progName.includes('kuvvet')) bgColor = '#FF9800';
                else if (progName.includes('flexibility') || progName.includes('esneklik') || progName.includes('pilates') || progName.includes('yoga')) bgColor = '#9C27B0';
                else bgColor = Colors.primary;

                return (
                <View key={prog.id} style={styles.programCardRow}>
                  <View style={[styles.iconBox, { backgroundColor: `${bgColor}20` }]}>
                    <Dumbbell size={20} color={bgColor} />
                  </View>
                  <View style={[styles.purplePill, { backgroundColor: bgColor }]}>
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
                      <CalendarIcon size={14} color="rgba(255,255,255,0.7)" />
                      <Text style={styles.timeText}>
                        {prog.start_time} / {prog.end_time}
                      </Text>
                    </View>
                  </View>
                </View>
              )})}

              {/* Open Classes */}
              {openClasses.map((cls) => {
                const booking = groupBookings.find(b => b.class_id === cls.id);
                const isWaitlist = booking?.status === 'waitlist';
                const currentCount = cls.enrolled_count || cls.current_participants || 0;
                const isFull = cls.capacity && currentCount >= cls.capacity;

                // Past class calculation
                const now = new Date();
                const [endH, endM] = (cls.end_time || cls.start_time || '23:59').split(':');
                const classEndTime = new Date(`${selectedDateStr}T${String(endH).padStart(2, '0')}:${String(endM).padStart(2, '0')}:00`);
                const isPast = classEndTime < now;

                let buttonLabel = 'Derse Katıl';
                if (isPast) {
                  buttonLabel = 'Bitti';
                } else if (booking) {
                  buttonLabel = isWaitlist ? 'Kuyruktan Çık' : 'İptal Et';
                } else if (isFull) {
                  buttonLabel = 'Yedek Listesine Katıl';
                }

                return (
                  <View key={cls.id} style={styles.cardItem}>
                    <View style={styles.iconBox}>
                      <Users size={20} color={Colors.primary} />
                    </View>
                    <View style={{ flex: 1 }}>
                      <View style={{ flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', gap: 6 }}>
                        <Text style={styles.cardTitle}>{cls.name}</Text>
                        {isWaitlist && (
                          <View style={{ backgroundColor: '#FF9800', paddingHorizontal: 6, paddingVertical: 2, borderRadius: 4 }}>
                            <Text style={{ color: '#fff', fontSize: 10, fontWeight: 'bold' }}>Yedekte</Text>
                          </View>
                        )}
                        {!isWaitlist && isFull && (
                          <View style={{ backgroundColor: '#F44336', paddingHorizontal: 6, paddingVertical: 2, borderRadius: 4 }}>
                            <Text style={{ color: '#fff', fontSize: 10, fontWeight: 'bold' }}>Dolu</Text>
                          </View>
                        )}
                        {isPast && (
                          <View style={{ backgroundColor: 'rgba(255,255,255,0.1)', paddingHorizontal: 6, paddingVertical: 2, borderRadius: 4 }}>
                            <Text style={{ color: Colors.textSecondaryDark, fontSize: 10, fontWeight: 'bold' }}>Geçmiş</Text>
                          </View>
                        )}
                      </View>
                      <Text style={styles.cardSubtitle}>
                        {cls.instructor_name || 'Eğitmen'} · {cls.start_time} - {cls.end_time}
                      </Text>
                      {cls.capacity ? (
                        <Text style={[styles.cardSubtitle, { marginTop: 4, color: Colors.primary }]}>
                          Kapasite: {currentCount}/{cls.capacity}
                        </Text>
                      ) : null}
                    </View>
                    <TouchableOpacity
                      style={[
                        styles.joinButton,
                        booking && styles.joinedButton,
                        isPast && { backgroundColor: 'rgba(255,255,255,0.08)', opacity: 0.6 },
                      ]}
                      disabled={isPast}
                      onPress={async () => {
                        try {
                          const uid = userProfile?.id || userProfile?.user_id;
                          if (booking) {
                            await GroupClassService.cancelBooking(booking.id);
                            CustomAlert.show({
                              title: 'Bilgi',
                              message: 'Dersten ayrıldınız.',
                              type: 'info',
                            });
                          } else {
                            const status = isFull ? 'waitlist' : 'booked';
                            await GroupClassService.bookClass(uid, cls.id, selectedDateStr, status);
                            CustomAlert.show({
                              title: 'Başarılı 🎉',
                              message: isFull
                                ? 'Ders dolu olduğu için yedek listesine eklendiniz.'
                                : 'Derse kaydınız başarıyla oluşturuldu!',
                              type: 'success',
                            });
                          }
                          loadCalendarContent();
                        } catch (e: any) {
                          console.error('Error toggling class booking:', e);
                          CustomAlert.show({
                            title: 'Hata',
                            message: 'İşlem gerçekleştirilemedi: ' + (e?.message || e),
                            type: 'error',
                          });
                        }
                      }}
                      activeOpacity={0.8}
                    >
                      <Text
                        style={[
                          styles.joinButtonText,
                          booking && styles.joinedButtonText,
                          isPast && { color: Colors.textSecondaryDark },
                        ]}
                      >
                        {buttonLabel}
                      </Text>
                    </TouchableOpacity>
                  </View>
                );
              })}

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

              {/* User Events */}
              {userEvents.length > 0 && (
                <View style={{ gap: 8, marginTop: 8 }}>
                  <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
                    <CalendarIcon size={16} color={Colors.primary} />
                    <Text style={{ fontSize: 16, fontWeight: '600', color: Colors.textDark }}>
                      Etkinliklerim
                    </Text>
                  </View>
                  {userEvents.map((evt) => {
                    const EventIcon = evt.icon === 'Activity' ? Activity : evt.icon === 'Dumbbell' ? Dumbbell : evt.icon === 'Droplets' ? Droplets : evt.icon === 'Bike' ? Bike : evt.icon === 'Apple' ? Apple : CalendarIcon;
                    const eventColor = evt.color || Colors.primary;
                    return (
                    <View key={evt.id} style={styles.cardItem}>
                      <View style={[styles.iconBox, { backgroundColor: `${eventColor}1A` }]}>
                        <EventIcon size={20} color={eventColor} />
                      </View>
                      <View style={{ flex: 1 }}>
                        <Text style={styles.cardTitle}>{evt.title}</Text>
                        <Text style={styles.cardSubtitle}>
                          {evt.start_time} / {evt.end_time}
                        </Text>
                      </View>
                      <TouchableOpacity
                        onPress={() => {
                          setEditingEventId(evt.id);
                          setNewEventTitle(evt.title);
                          setNewEventStartTime(evt.start_time || '09:00');
                          setNewEventEndTime(evt.end_time || '10:00');
                          setNewEventColor(evt.color || '#2196F3');
                          setNewEventIcon(evt.icon || 'Calendar');
                          setNewEventNotes(evt.notes || '');
                          setShowAddEventModal(true);
                        }}
                        style={{ padding: 6 }}
                        activeOpacity={0.7}
                      >
                        <Edit3 size={18} color={Colors.textSecondaryDark} />
                      </TouchableOpacity>
                      <TouchableOpacity
                        onPress={async () => {
                          const confirmed = await feedback.destructive({
                            title: 'Etkinliği Sil',
                            message: 'Bu etkinliği silmek istediğinize emin misiniz?',
                            confirmText: 'Sil',
                            cancelText: 'Vazgeç',
                          });
                          if (!confirmed) return;

                          try {
                            await supabase.from('user_events').delete().eq('id', evt.id);
                            loadCalendarContent();
                            loadMonthEventDays();
                            feedback.toast('Etkinlik silindi.', 'info');
                          } catch (e) {
                            console.error('Error deleting event:', e);
                            feedback.error({ title: 'Hata', message: e, fallbackMessage: 'Etkinlik silinemedi.' });
                          }
                        }}
                        style={{ padding: 6 }}
                        activeOpacity={0.7}
                      >
                        <Trash2 size={18} color="#F44336" />
                      </TouchableOpacity>
                    </View>
                  )})}
                </View>
              )}
              {/* Gym Events */}
              {gymEvents.length > 0 && (
                <View style={{ gap: 8, marginTop: 8 }}>
                  <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
                    <Star size={16} color={Colors.primary} />
                    <Text style={{ fontSize: 16, fontWeight: '600', color: Colors.textDark }}>
                      Salon Etkinlikleri
                    </Text>
                  </View>
                  {gymEvents.map((evt) => {
                    const participation = eventParticipations.find((p) => p.event_id === evt.id);
                    const isJoined = Boolean(participation);
                    return (
                      <View key={evt.id} style={styles.cardItem}>
                        <View style={styles.iconBox}>
                          <Star size={20} color={Colors.primary} />
                        </View>
                        <View style={{ flex: 1 }}>
                          <Text style={styles.cardTitle}>{evt.title}</Text>
                          {evt.description ? (
                            <Text style={styles.cardSubtitle}>{evt.description}</Text>
                          ) : null}
                          <Text style={styles.cardSubtitle}>
                            {evt.start_time} / {evt.end_time}
                          </Text>
                        </View>
                        <TouchableOpacity
                          style={[styles.joinButton, isJoined && styles.joinedButton]}
                          onPress={async () => {
                            const uid = userProfile?.id || userProfile?.user_id;
                            if (!uid) return;
                            try {
                              if (isJoined && participation) {
                                await GymEventService.leaveEvent(participation.id);
                              } else {
                                await GymEventService.joinEvent(uid, evt.id);
                              }
                              loadCalendarContent();
                            } catch (e) {
                              console.error('Error toggling gym event participation:', e);
                            }
                          }}
                          activeOpacity={0.8}
                        >
                          <Text
                            style={[
                              styles.joinButtonText,
                              isJoined && styles.joinedButtonText,
                            ]}
                          >
                            {isJoined ? 'Ayrıl' : 'Katıl'}
                          </Text>
                        </TouchableOpacity>
                      </View>
                    );
                  })}
                </View>
              )}
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

      {/* Add / Edit User Event Modal */}
      {showAddEventModal && (
        <View style={styles.modalOverlay}>
          <View style={styles.modalCard}>
            <Text style={styles.modalTitle}>
              {editingEventId ? 'Etkinliği Düzenle' : `Etkinlik Ekle (${selectedDateStr})`}
            </Text>

            <TextInput
              style={styles.modalInput}
              placeholder="Etkinlik başlığı..."
              placeholderTextColor={Colors.textSecondaryDark}
              value={newEventTitle}
              onChangeText={setNewEventTitle}
            />

            <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: 12, marginTop: 12 }}>
              {['#2196F3', '#4CAF50', '#FF9800', '#9C27B0', '#F44336', '#FFEB3B'].map(color => (
                <TouchableOpacity
                  key={color}
                  style={[{ width: 32, height: 32, borderRadius: 16, backgroundColor: color }, newEventColor === color && { borderWidth: 2, borderColor: Colors.allWhite }]}
                  onPress={() => setNewEventColor(color)}
                />
              ))}
            </View>

            <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: 12 }}>
              {['Calendar', 'Activity', 'Dumbbell', 'Droplets', 'Bike', 'Apple'].map(icon => {
                const IconComp = icon === 'Activity' ? Activity : icon === 'Dumbbell' ? Dumbbell : icon === 'Droplets' ? Droplets : icon === 'Bike' ? Bike : icon === 'Apple' ? Apple : CalendarIcon;
                return (
                  <TouchableOpacity
                    key={icon}
                    style={[{ padding: 8, borderRadius: 8, backgroundColor: Colors.cardDark }, newEventIcon === icon && { backgroundColor: Colors.primary }]}
                    onPress={() => setNewEventIcon(icon)}
                  >
                    <IconComp size={20} color={newEventIcon === icon ? Colors.allWhite : Colors.textSecondaryDark} />
                  </TouchableOpacity>
                );
              })}
            </View>

            <TextInput
              style={[styles.modalInput, { height: 80, textAlignVertical: 'top' }]}
              placeholder="Notlar..."
              placeholderTextColor={Colors.textSecondaryDark}
              value={newEventNotes}
              onChangeText={setNewEventNotes}
              multiline
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
                onPress={() => {
                  setEditingEventId(null);
                  setNewEventTitle('');
                  setShowAddEventModal(false);
                }}
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
                    if (editingEventId) {
                      await supabase
                        .from('user_events')
                        .update({
                          title: newEventTitle.trim(),
                          start_time: newEventStartTime,
                          end_time: newEventEndTime,
                          color: newEventColor,
                          icon: newEventIcon,
                          notes: newEventNotes,
                        })
                        .eq('id', editingEventId);
                    } else {
                      await supabase.from('user_events').insert({
                        user_id: userId,
                        title: newEventTitle.trim(),
                        event_date: selectedDateStr,
                        start_time: newEventStartTime,
                        end_time: newEventEndTime,
                        color: newEventColor,
                        icon: newEventIcon,
                        notes: newEventNotes,
                      });
                    }
                    setEditingEventId(null);
                    setNewEventTitle('');
                    setShowAddEventModal(false);
                    loadCalendarContent();
                    loadMonthEventDays();
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
    backgroundColor: Colors.backgroundDark,
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
    fontSize: 12,
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
  eventDot: {
    width: 4,
    height: 4,
    borderRadius: 2,
    backgroundColor: Colors.primary,
    marginTop: 2,
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
  joinButton: {
    backgroundColor: Colors.primary,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 10,
  },
  joinedButton: {
    backgroundColor: 'rgba(255, 255, 255, 0.1)',
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  joinButtonText: {
    color: Colors.allWhite,
    fontWeight: '600',
    fontSize: 12,
  },
  joinedButtonText: {
    color: Colors.textSecondaryDark,
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
    right: 20,
    bottom: 20,
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: Colors.primary,
    justifyContent: 'center',
    alignItems: 'center',
    elevation: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 5,
    zIndex: 99,
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
