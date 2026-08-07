import React, { useState, useEffect, useMemo } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  ActivityIndicator,
  FlatList,
  Modal,
} from 'react-native';
import {
  ArrowLeft,
  Flame,
  ChevronLeft,
  ChevronRight,
  ChevronDown,
  Award,
  Dumbbell,
  CheckCircle2,
  Calendar as CalendarIcon,
} from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { AuthService } from '../../services/authService';
import { UserService } from '../../services/userService';
import { supabase } from '../../services/supabaseClient';

interface PersonalRecordsScreenProps {
  navigation?: any;
}

const DAY_NAMES = ['Pzt', 'Sal', 'Çar', 'Per', 'Cum', 'Cts', 'Pz'];
const MONTH_NAMES = [
  'Ocak',
  'Şubat',
  'Mart',
  'Nisan',
  'Mayıs',
  'Haziran',
  'Temmuz',
  'Ağustos',
  'Eylül',
  'Ekim',
  'Kasım',
  'Aralık',
];

export const PersonalRecordsScreen: React.FC<PersonalRecordsScreenProps> = ({ navigation }) => {
  const [loading, setLoading] = useState(true);
  const [selectedTab, setSelectedTab] = useState<number>(0); // 0: Antrenmanlar, 1: Kişisel Rekorlar
  const [streakCount, setStreakCount] = useState<number>(1);
  const [currentDate, setCurrentDate] = useState<Date>(new Date());
  const [selectedDayStr, setSelectedDayStr] = useState<string | null>(null);

  // Data states
  const [records, setRecords] = useState<any[]>([]);
  const [workoutSessions, setWorkoutSessions] = useState<any[]>([]);
  const [recordAttempts, setRecordAttempts] = useState<any[]>([]);

  // Filters
  const [workoutFilter, setWorkoutFilter] = useState<string>('Bu hafta');
  const [recordFilter, setRecordFilter] = useState<string>('Tüm zamanlar');
  const [showWorkoutFilterDropdown, setShowWorkoutFilterDropdown] = useState(false);
  const [showRecordFilterDropdown, setShowRecordFilterDropdown] = useState(false);

  // Expanded items
  const [expandedRecordIds, setExpandedRecordIds] = useState<Set<string>>(new Set());

  useEffect(() => {
    loadAllData();
  }, []);

  const loadAllData = async () => {
    setLoading(true);
    try {
      const user = await AuthService.getCurrentUser();
      if (!user) {
        setLoading(false);
        return;
      }

      // Fetch user profile for streak
      const profile = await UserService.fetchProfile(user.id);
      if (profile?.current_streak) {
        setStreakCount(profile.current_streak);
      }

      // Fetch PRs
      const { data: prData } = await supabase
        .from('personal_records')
        .select('*, exercise:exercises(name)')
        .eq('user_id', user.id)
        .order('record_date', { ascending: false });

      setRecords(prData || []);

      // Fetch completed workout sessions
      const { data: sessionData } = await supabase
        .from('training_sessions')
        .select('*, program:training_programs(name)')
        .eq('user_id', user.id)
        .not('completed_at', 'is', null)
        .order('completed_at', { ascending: false });

      setWorkoutSessions(sessionData || []);

      // Fetch completed record attempts
      const { data: attemptData } = await supabase
        .from('record_attempts')
        .select('*')
        .eq('user_id', user.id)
        .eq('status', 'completed')
        .order('completed_at', { ascending: false });

      setRecordAttempts(attemptData || []);
    } catch (e) {
      console.error('Error loading activity history:', e);
    } finally {
      setLoading(false);
    }
  };

  // Activity days map (YYYY-MM-DD set)
  const activityDaysSet = useMemo(() => {
    const set = new Set<string>();
    records.forEach((r) => {
      if (r.record_date) set.add(r.record_date.slice(0, 10));
    });
    workoutSessions.forEach((s) => {
      const d = s.completed_at || s.created_at;
      if (d) set.add(d.slice(0, 10));
    });
    recordAttempts.forEach((a) => {
      const d = a.completed_at || a.created_at;
      if (d) set.add(d.slice(0, 10));
    });
    return set;
  }, [records, workoutSessions, recordAttempts]);

  // Calendar calculations
  const year = currentDate.getFullYear();
  const month = currentDate.getMonth();

  const calendarDays = useMemo(() => {
    const firstDayOfMonth = new Date(year, month, 1);
    const lastDayOfMonth = new Date(year, month + 1, 0);

    // Monday-based day index: Sunday (0) -> 6, Monday (1) -> 0
    let startDay = firstDayOfMonth.getDay() - 1;
    if (startDay === -1) startDay = 6;

    const days: Array<{ day: number; dateStr: string; isCurrentMonth: boolean }> = [];

    // Days from previous month for alignment
    const prevMonthLastDay = new Date(year, month, 0).getDate();
    for (let i = startDay - 1; i >= 0; i--) {
      const dayNum = prevMonthLastDay - i;
      const prevDate = new Date(year, month - 1, dayNum);
      const dateStr = prevDate.toISOString().slice(0, 10);
      days.push({ day: dayNum, dateStr, isCurrentMonth: false });
    }

    // Days of current month
    for (let i = 1; i <= lastDayOfMonth.getDate(); i++) {
      const mStr = String(month + 1).padStart(2, '0');
      const dStr = String(i).padStart(2, '0');
      const dateStr = `${year}-${mStr}-${dStr}`;
      days.push({ day: i, dateStr, isCurrentMonth: true });
    }

    return days;
  }, [year, month]);

  const handlePrevMonth = () => {
    setCurrentDate(new Date(year, month - 1, 1));
  };

  const handleNextMonth = () => {
    setCurrentDate(new Date(year, month + 1, 1));
  };

  // Filtered workouts
  const filteredWorkouts = useMemo(() => {
    let list = workoutSessions;
    if (selectedDayStr) {
      return list.filter((s) => (s.completed_at || s.created_at)?.slice(0, 10) === selectedDayStr);
    }
    const today = new Date();
    if (workoutFilter === 'Bu hafta') {
      const monday = new Date(today);
      const day = monday.getDay();
      const diff = monday.getDate() - day + (day === 0 ? -6 : 1);
      monday.setDate(diff);
      monday.setHours(0, 0, 0, 0);
      return list.filter((s) => new Date(s.completed_at || s.created_at) >= monday);
    } else if (workoutFilter === 'Bu ay') {
      return list.filter((s) => {
        const d = new Date(s.completed_at || s.created_at);
        return d.getMonth() === today.getMonth() && d.getFullYear() === today.getFullYear();
      });
    }
    return list;
  }, [workoutSessions, workoutFilter, selectedDayStr]);

  // Filtered records
  const filteredRecords = useMemo(() => {
    let list = records;
    if (selectedDayStr) {
      return list.filter((r) => r.record_date?.slice(0, 10) === selectedDayStr);
    }
    const today = new Date();
    if (recordFilter === 'Bu hafta') {
      const monday = new Date(today);
      const day = monday.getDay();
      const diff = monday.getDate() - day + (day === 0 ? -6 : 1);
      monday.setDate(diff);
      monday.setHours(0, 0, 0, 0);
      return list.filter((r) => new Date(r.record_date) >= monday);
    } else if (recordFilter === 'Bu ay') {
      return list.filter((r) => {
        const d = new Date(r.record_date);
        return d.getMonth() === today.getMonth() && d.getFullYear() === today.getFullYear();
      });
    }
    return list;
  }, [records, recordFilter, selectedDayStr]);

  const toggleExpandRecord = (id: string) => {
    const next = new Set(expandedRecordIds);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    setExpandedRecordIds(next);
  };

  return (
    <View style={styles.container}>
      {/* ── Header ────────────────────────────────────────────────────────── */}
      <View style={styles.headerRow}>
        <TouchableOpacity onPress={() => navigation?.goBack()} style={styles.backBtn} activeOpacity={0.8}>
          <ArrowLeft size={20} color={Colors.textDark} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Aktivite Geçmişi</Text>
        <TouchableOpacity
          style={styles.rekorHeaderBtn}
          onPress={() => navigation?.navigate('RecordAttemptSetup')}
          activeOpacity={0.8}
        >
          <Flame size={15} color={Colors.allWhite} />
          <Text style={styles.rekorHeaderBtnText}>Rekor Denemesi</Text>
        </TouchableOpacity>
      </View>

      {/* ── Tabs (Antrenmanlar | Kişisel Rekorlar) ───────────────────────── */}
      <View style={styles.tabsRow}>
        <TouchableOpacity
          style={[styles.tabBtn, selectedTab === 0 && styles.tabBtnActive]}
          onPress={() => setSelectedTab(0)}
          activeOpacity={0.8}
        >
          <Text style={[styles.tabText, selectedTab === 0 && styles.tabTextActive]}>Antrenmanlar</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.tabBtn, selectedTab === 1 && styles.tabBtnActive]}
          onPress={() => setSelectedTab(1)}
          activeOpacity={0.8}
        >
          <Text style={[styles.tabText, selectedTab === 1 && styles.tabTextActive]}>Kişisel Rekorlar</Text>
        </TouchableOpacity>
      </View>

      {loading ? (
        <View style={styles.loadingBox}>
          <ActivityIndicator size="large" color={Colors.primary} />
        </View>
      ) : (
        <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
          {/* ── Streak Section ───────────────────────────────────────────── */}
          <View style={styles.streakBox}>
            <Flame size={32} color={Colors.primary} />
            <Text style={styles.streakNumber}>{streakCount}</Text>
            <Text style={styles.streakLabel}>Günlük Streak</Text>
          </View>

          {/* ── Calendar View ────────────────────────────────────────────── */}
          <View style={styles.calendarContainer}>
            {/* Month Header */}
            <View style={styles.monthHeader}>
              <TouchableOpacity onPress={handlePrevMonth} style={styles.monthNavBtn}>
                <ChevronLeft size={20} color={Colors.textDark} />
              </TouchableOpacity>
              <Text style={styles.monthTitle}>
                {MONTH_NAMES[month]} {year}
              </Text>
              <TouchableOpacity onPress={handleNextMonth} style={styles.monthNavBtn}>
                <ChevronRight size={20} color={Colors.textDark} />
              </TouchableOpacity>
            </View>

            {/* Days of week */}
            <View style={styles.weekDaysRow}>
              {DAY_NAMES.map((name) => (
                <Text key={name} style={styles.weekDayText}>
                  {name}
                </Text>
              ))}
            </View>

            {/* Calendar Days Grid */}
            <View style={styles.daysGrid}>
              {calendarDays.map((item, idx) => {
                const hasActivity = activityDaysSet.has(item.dateStr);
                const isSelected = selectedDayStr === item.dateStr;
                const isToday = new Date().toISOString().slice(0, 10) === item.dateStr;

                return (
                  <TouchableOpacity
                    key={idx}
                    style={[
                      styles.dayCell,
                      isSelected && styles.dayCellSelected,
                      !item.isCurrentMonth && { opacity: 0.25 },
                    ]}
                    disabled={!item.isCurrentMonth}
                    onPress={() => {
                      if (isSelected) setSelectedDayStr(null);
                      else setSelectedDayStr(item.dateStr);
                    }}
                    activeOpacity={0.7}
                  >
                    <Text
                      style={[
                        styles.dayNumberText,
                        isSelected && styles.dayNumberTextSelected,
                        isToday && !isSelected && { color: Colors.primary, fontWeight: '700' },
                      ]}
                    >
                      {item.day}
                    </Text>
                    {hasActivity && <View style={[styles.activityDot, isSelected && { backgroundColor: Colors.allWhite }]} />}
                  </TouchableOpacity>
                );
              })}
            </View>
          </View>

          {/* ── Selected Day Indicator Bar ───────────────────────────────── */}
          {selectedDayStr && (
            <View style={styles.selectedDayBar}>
              <Text style={styles.selectedDayText}>Seçili Tarih: {selectedDayStr}</Text>
              <TouchableOpacity onPress={() => setSelectedDayStr(null)}>
                <Text style={styles.clearFilterText}>Filtreyi Temizle</Text>
              </TouchableOpacity>
            </View>
          )}

          {/* ── Tab 0: Antrenmanlar ──────────────────────────────────────── */}
          {selectedTab === 0 && (
            <View style={styles.sectionContainer}>
              <View style={styles.sectionHeaderRow}>
                <Text style={styles.sectionTitle}>Antrenmanlar</Text>
                {!selectedDayStr && (
                  <TouchableOpacity
                    style={styles.dropdownBtn}
                    onPress={() => setShowWorkoutFilterDropdown(!showWorkoutFilterDropdown)}
                    activeOpacity={0.8}
                  >
                    <Text style={styles.dropdownBtnText}>{workoutFilter}</Text>
                    <ChevronDown size={14} color={Colors.textSecondaryDark} />
                  </TouchableOpacity>
                )}
              </View>

              {/* Dropdown Menu */}
              {showWorkoutFilterDropdown && !selectedDayStr && (
                <View style={styles.dropdownMenu}>
                  {['Bu hafta', 'Bu ay', 'Tüm zamanlar'].map((option) => (
                    <TouchableOpacity
                      key={option}
                      style={styles.dropdownMenuItem}
                      onPress={() => {
                        setWorkoutFilter(option);
                        setShowWorkoutFilterDropdown(false);
                      }}
                    >
                      <Text
                        style={[
                          styles.dropdownMenuText,
                          workoutFilter === option && { color: Colors.primary, fontWeight: '700' },
                        ]}
                      >
                        {option}
                      </Text>
                    </TouchableOpacity>
                  ))}
                </View>
              )}

              {filteredWorkouts.length === 0 ? (
                <Text style={styles.emptyText}>Bu dönemde tamamlanan antrenman yok.</Text>
              ) : (
                filteredWorkouts.map((item) => (
                  <View key={item.id} style={styles.activityCard}>
                    <View style={styles.activityIconCircle}>
                      <Dumbbell size={20} color={Colors.primary} />
                    </View>
                    <View style={{ flex: 1 }}>
                      <Text style={styles.activityCardTitle}>
                        {item.program?.name || item.title || 'Antrenman'}
                      </Text>
                      <Text style={styles.activityCardSub}>
                        {item.completed_at ? item.completed_at.slice(0, 10) : 'Tamamlandı'}
                      </Text>
                    </View>
                    <CheckCircle2 size={20} color={Colors.success} />
                  </View>
                ))
              )}
            </View>
          )}

          {/* ── Tab 1: Kişisel Rekorlar ─────────────────────────────────── */}
          {selectedTab === 1 && (
            <View style={styles.sectionContainer}>
              <View style={styles.sectionHeaderRow}>
                <Text style={styles.sectionTitle}>Kişisel Rekorlar</Text>
                {!selectedDayStr && (
                  <TouchableOpacity
                    style={styles.dropdownBtn}
                    onPress={() => setShowRecordFilterDropdown(!showRecordFilterDropdown)}
                    activeOpacity={0.8}
                  >
                    <Text style={styles.dropdownBtnText}>{recordFilter}</Text>
                    <ChevronDown size={14} color={Colors.textSecondaryDark} />
                  </TouchableOpacity>
                )}
              </View>

              {/* Dropdown Menu */}
              {showRecordFilterDropdown && !selectedDayStr && (
                <View style={styles.dropdownMenu}>
                  {['Tüm zamanlar', 'Bu hafta', 'Bu ay'].map((option) => (
                    <TouchableOpacity
                      key={option}
                      style={styles.dropdownMenuItem}
                      onPress={() => {
                        setRecordFilter(option);
                        setShowRecordFilterDropdown(false);
                      }}
                    >
                      <Text
                        style={[
                          styles.dropdownMenuText,
                          recordFilter === option && { color: Colors.primary, fontWeight: '700' },
                        ]}
                      >
                        {option}
                      </Text>
                    </TouchableOpacity>
                  ))}
                </View>
              )}

              {filteredRecords.length === 0 ? (
                <View style={styles.emptyBox}>
                  <Award size={40} color={Colors.textSecondaryDark} />
                  <Text style={styles.emptyText}>Henüz kayıtlı kişisel rekor bulunmuyor.</Text>
                  <TouchableOpacity
                    style={styles.createPrBtn}
                    onPress={() => navigation?.navigate('RecordAttemptSetup')}
                  >
                    <Text style={styles.createPrBtnText}>Rekor Denemesi Başlat</Text>
                  </TouchableOpacity>
                </View>
              ) : (
                filteredRecords.map((item) => {
                  const isExpanded = expandedRecordIds.has(item.id);
                  return (
                    <TouchableOpacity
                      key={item.id}
                      style={styles.prCard}
                      onPress={() => toggleExpandRecord(item.id)}
                      activeOpacity={0.9}
                    >
                      <View style={styles.prCardHeader}>
                        <View style={styles.prIconCircle}>
                          <Award size={20} color="#FF9800" />
                        </View>
                        <View style={{ flex: 1 }}>
                          <Text style={styles.prCardTitle}>
                            {item.exercise?.name || item.exercise_name || 'Rekor'}
                          </Text>
                          <Text style={styles.prCardSub}>
                            {item.record_date ? item.record_date.slice(0, 10) : ''}
                          </Text>
                        </View>
                        <Text style={styles.prCardValue}>
                          {item.weight_kg ? `${item.weight_kg} kg` : `${item.reps || 1} tekrar`}
                        </Text>
                      </View>

                      {isExpanded && (
                        <View style={styles.prCardDetail}>
                          <Text style={styles.prDetailText}>
                            Tekrar: {item.reps || 1} · Ağırlık: {item.weight_kg || 0} kg
                          </Text>
                          {item.notes && <Text style={styles.prNotesText}>Not: {item.notes}</Text>}
                        </View>
                      )}
                    </TouchableOpacity>
                  );
                })
              )}
            </View>
          )}
        </ScrollView>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.backgroundDark,
  },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingTop: 50,
    paddingBottom: 16,
    gap: 12,
  },
  backBtn: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: Colors.cardDark,
    justifyContent: 'center',
    alignItems: 'center',
  },
  headerTitle: {
    flex: 1,
    fontSize: 22,
    fontWeight: '700',
    color: Colors.textDark,
  },
  rekorHeaderBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: Colors.primary,
    paddingHorizontal: 14,
    paddingVertical: 9,
    borderRadius: 100,
    elevation: 3,
  },
  rekorHeaderBtnText: {
    color: Colors.allWhite,
    fontSize: 13,
    fontWeight: '600',
  },
  tabsRow: {
    flexDirection: 'row',
    paddingHorizontal: 20,
    borderBottomWidth: 1,
    borderBottomColor: Colors.borderDark,
  },
  tabBtn: {
    flex: 1,
    paddingVertical: 12,
    alignItems: 'center',
    borderBottomWidth: 2,
    borderBottomColor: 'transparent',
  },
  tabBtnActive: {
    borderBottomColor: Colors.primary,
  },
  tabText: {
    fontSize: 15,
    fontWeight: '500',
    color: Colors.textSecondaryDark,
  },
  tabTextActive: {
    color: Colors.primary,
    fontWeight: '700',
  },
  loadingBox: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  scrollContent: {
    paddingHorizontal: 20,
    paddingBottom: 40,
  },
  // Streak
  streakBox: {
    alignItems: 'center',
    paddingVertical: 20,
    gap: 4,
  },
  streakNumber: {
    fontSize: 48,
    fontWeight: '800',
    color: Colors.textDark,
  },
  streakLabel: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
  },
  // Calendar
  calendarContainer: {
    backgroundColor: Colors.cardDark,
    borderRadius: 20,
    padding: 16,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  monthHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 16,
  },
  monthNavBtn: {
    padding: 8,
  },
  monthTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.textDark,
  },
  weekDaysRow: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    marginBottom: 12,
  },
  weekDayText: {
    width: 36,
    textAlign: 'center',
    fontSize: 12,
    color: Colors.textSecondaryDark,
    fontWeight: '600',
  },
  daysGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
  },
  dayCell: {
    width: '14.28%',
    height: 44,
    justifyContent: 'center',
    alignItems: 'center',
    borderRadius: 22,
    marginVertical: 2,
    position: 'relative',
  },
  dayCellSelected: {
    backgroundColor: Colors.primary,
  },
  dayNumberText: {
    fontSize: 14,
    color: Colors.textDark,
    fontWeight: '500',
  },
  dayNumberTextSelected: {
    color: Colors.allWhite,
    fontWeight: '700',
  },
  activityDot: {
    width: 4,
    height: 4,
    borderRadius: 2,
    backgroundColor: Colors.primary,
    position: 'absolute',
    bottom: 6,
  },
  selectedDayBar: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: 'rgba(255,96,71,0.12)',
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 12,
    marginBottom: 16,
  },
  selectedDayText: {
    color: Colors.primary,
    fontWeight: '600',
    fontSize: 13,
  },
  clearFilterText: {
    color: Colors.textSecondaryDark,
    fontSize: 12,
    textDecorationLine: 'underline',
  },
  // Sections
  sectionContainer: {
    marginBottom: 20,
  },
  sectionHeaderRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.textDark,
  },
  dropdownBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: Colors.cardDark,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 100,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  dropdownBtnText: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
    fontWeight: '500',
  },
  dropdownMenu: {
    backgroundColor: Colors.cardDark,
    borderRadius: 14,
    padding: 8,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  dropdownMenuItem: {
    paddingVertical: 10,
    paddingHorizontal: 12,
  },
  dropdownMenuText: {
    fontSize: 13,
    color: Colors.textDark,
  },
  emptyText: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    marginVertical: 12,
  },
  activityCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 14,
    marginBottom: 10,
    gap: 12,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  activityIconCircle: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: 'rgba(255,96,71,0.12)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  activityCardTitle: {
    fontSize: 15,
    fontWeight: '600',
    color: Colors.textDark,
  },
  activityCardSub: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
    marginTop: 2,
  },
  emptyBox: {
    alignItems: 'center',
    paddingVertical: 24,
    gap: 10,
  },
  createPrBtn: {
    backgroundColor: Colors.primary,
    borderRadius: 100,
    paddingHorizontal: 20,
    paddingVertical: 10,
    marginTop: 8,
  },
  createPrBtnText: {
    color: Colors.allWhite,
    fontWeight: '600',
    fontSize: 13,
  },
  prCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 14,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  prCardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  prIconCircle: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: 'rgba(255,152,0,0.12)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  prCardTitle: {
    fontSize: 15,
    fontWeight: '600',
    color: Colors.textDark,
  },
  prCardSub: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
    marginTop: 2,
  },
  prCardValue: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.primary,
  },
  prCardDetail: {
    marginTop: 10,
    paddingTop: 10,
    borderTopWidth: 1,
    borderTopColor: Colors.borderDark,
  },
  prDetailText: {
    fontSize: 12,
    color: Colors.textDark,
  },
  prNotesText: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
    marginTop: 4,
  },
});
