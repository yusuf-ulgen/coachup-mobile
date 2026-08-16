import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView, ActivityIndicator } from 'react-native';
import { feedback } from '../../services/feedbackService';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Colors } from '../../theme/colors';
import { useTheme } from '../../theme/ThemeContext';
import { Users, Clock, MapPin, CheckCircle, UserCheck, AlertCircle, ArrowLeft } from 'lucide-react-native';
import { supabase } from '../../services/supabaseClient';
import { GroupClassService } from '../../services/groupClassService';
import { AuthService } from '../../services/authService';
import AsyncStorage from '@react-native-async-storage/async-storage';

const DAY_NAMES = ['Paz', 'Pzt', 'Sal', 'Çar', 'Per', 'Cum', 'Cmt'];

// Day-specific realistic schedule fallbacks if DB table returns no records for a specific gym
const SCHEDULE_TEMPLATES: { [key: number]: any[] } = {
  1: [ // Pazartesi
    { id: 'pzt_1', name: 'Sabah Pilates', instructor: 'Ayşe Yılmaz', time: '09:00 - 10:00', location: 'Stüdyo 1', capacity: 12, booked: 8, status: 'none' },
    { id: 'pzt_2', name: 'Spinning Cardio', instructor: 'Caner Erkin', time: '18:30 - 19:30', location: 'Spinning Salonu', capacity: 15, booked: 14, status: 'none' },
    { id: 'pzt_3', name: 'Crossfit WOD', instructor: 'Mehmet Kaya', time: '20:00 - 21:00', location: 'Ana Fit Alan', capacity: 10, booked: 10, status: 'none' },
  ],
  2: [ // Salı
    { id: 'sal_1', name: 'Power Yoga', instructor: 'Fatma Şahin', time: '10:00 - 11:00', location: 'Stüdyo 2', capacity: 15, booked: 7, status: 'none' },
    { id: 'sal_2', name: 'Kick Boks', instructor: 'Burak Öz', time: '17:00 - 18:00', location: 'Dövüş Alanı', capacity: 12, booked: 11, status: 'none' },
    { id: 'sal_3', name: 'Zumba Dance', instructor: 'Selin Yılmaz', time: '19:00 - 20:00', location: 'Ana Salon', capacity: 20, booked: 18, status: 'none' },
  ],
  3: [ // Çarşamba
    { id: 'car_1', name: 'TRX Süspansiyon', instructor: 'Deniz Akın', time: '09:30 - 10:30', location: 'Stüdyo 1', capacity: 10, booked: 6, status: 'none' },
    { id: 'car_2', name: 'HIIT Cardio', instructor: 'Ayşe Yılmaz', time: '18:00 - 19:00', location: 'Ana Fit Alan', capacity: 16, booked: 16, status: 'none' },
    { id: 'car_3', name: 'Body Pump', instructor: 'Caner Erkin', time: '19:30 - 20:30', location: 'Stüdyo 2', capacity: 14, booked: 9, status: 'none' },
  ],
  4: [ // Perşembe
    { id: 'per_1', name: 'Mat Pilates', instructor: 'Fatma Şahin', time: '11:00 - 12:00', location: 'Stüdyo 1', capacity: 12, booked: 9, status: 'none' },
    { id: 'per_2', name: 'Boks Teknikleri', instructor: 'Burak Öz', time: '18:00 - 19:00', location: 'Dövüş Alanı', capacity: 10, booked: 8, status: 'none' },
    { id: 'per_3', name: 'Core & Abs Express', instructor: 'Mehmet Kaya', time: '19:15 - 20:00', location: 'Stüdyo 2', capacity: 15, booked: 12, status: 'none' },
  ],
  5: [ // Cuma
    { id: 'cum_1', name: 'Güneş Yogası', instructor: 'Fatma Şahin', time: '08:30 - 09:30', location: 'Stüdyo 2', capacity: 12, booked: 5, status: 'none' },
    { id: 'cum_2', name: 'Fonksiyonel Antrenman', instructor: 'Deniz Akın', time: '17:30 - 18:30', location: 'Ana Fit Alan', capacity: 15, booked: 13, status: 'none' },
    { id: 'cum_3', name: 'Cuma Zumba Partisi', instructor: 'Selin Yılmaz', time: '19:00 - 20:00', location: 'Ana Salon', capacity: 25, booked: 21, status: 'none' },
  ],
  6: [ // Cumartesi
    { id: 'cmt_1', name: 'Haftasonu Crossfit', instructor: 'Mehmet Kaya', time: '11:00 - 12:30', location: 'Ana Fit Alan', capacity: 12, booked: 11, status: 'none' },
    { id: 'cmt_2', name: 'Esneme & Mobilite', instructor: 'Ayşe Yılmaz', time: '13:00 - 14:00', location: 'Stüdyo 1', capacity: 15, booked: 8, status: 'none' },
    { id: 'cmt_3', name: 'Dinamik Bisiklet', instructor: 'Caner Erkin', time: '15:00 - 16:00', location: 'Spinning Salonu', capacity: 12, booked: 10, status: 'none' },
  ],
  0: [ // Pazar
    { id: 'paz_1', name: 'Pazar Dinlenme Yogası', instructor: 'Fatma Şahin', time: '10:30 - 11:30', location: 'Stüdyo 2', capacity: 15, booked: 9, status: 'none' },
    { id: 'paz_2', name: 'Açık Hava Koşu Grubu', instructor: 'Deniz Akın', time: '16:00 - 17:30', location: 'Dış Alan / Park', capacity: 20, booked: 14, status: 'none' },
  ],
};

export default function GroupClassesScreen({ navigation }: any) {
  const { colors } = useTheme();
  const [days, setDays] = useState<any[]>([]);
  const [selectedDayId, setSelectedDayId] = useState<number>(0);
  const [selectedDateStr, setSelectedDateStr] = useState<string>('');
  const [classes, setClasses] = useState<any[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [userBookings, setUserBookings] = useState<{ [classId: string]: string }>({});

  const scrollViewRef = React.useRef<ScrollView>(null);

  // Generate dynamic 15 days: 7 days past (-7), Today (0), 7 days future (+7)
  useEffect(() => {
    const today = new Date();
    const generatedDays: any[] = [];
    let todayIndex = 7; // Index 7 is Today

    for (let offset = -7; offset <= 7; offset++) {
      const d = new Date(today);
      d.setDate(today.getDate() + offset);

      const dateStr = d.toISOString().split('T')[0];
      const isToday = offset === 0;

      const idx = offset + 7;
      if (isToday) todayIndex = idx;

      generatedDays.push({
        id: idx,
        fullDate: dateStr,
        name: isToday ? 'Bugün' : DAY_NAMES[d.getDay()],
        dateNumber: d.getDate().toString(),
        dayOfWeek: d.getDay(),
        isToday,
      });
    }

    setDays(generatedDays);
    setSelectedDayId(todayIndex);
    setSelectedDateStr(generatedDays[todayIndex]?.fullDate || today.toISOString().split('T')[0]);

    // Scroll to center Today (each pill width is 60 + 10 margin = 70px)
    setTimeout(() => {
      scrollViewRef.current?.scrollTo({ x: Math.max(0, todayIndex * 70 - 100), animated: true });
    }, 200);
  }, []);

  // Load user bookings from local storage
  const BOOKINGS_KEY = '@group_class_user_bookings';
  const loadLocalBookings = async () => {
    try {
      const stored = await AsyncStorage.getItem(BOOKINGS_KEY);
      return stored ? JSON.parse(stored) : {};
    } catch {
      return {};
    }
  };

  const saveLocalBookings = async (bookingsMap: { [key: string]: string }) => {
    try {
      await AsyncStorage.setItem(BOOKINGS_KEY, JSON.stringify(bookingsMap));
    } catch (e) {
      console.error('Error saving local bookings:', e);
    }
  };

  // Load classes whenever selected date changes
  useEffect(() => {
    if (!selectedDateStr) return;

    const loadDataForDate = async () => {
      setLoading(true);
      const bookingsMap = await loadLocalBookings();
      setUserBookings(bookingsMap);

      const activeDayObj = days.find((d) => d.fullDate === selectedDateStr);
      const dayOfWeekIndex = activeDayObj ? activeDayObj.dayOfWeek : new Date(selectedDateStr).getDay();

      try {
        const user = await AuthService.getCurrentUser();
        if (user?.id) {
          const remoteClasses = await GroupClassService.fetchClassesForDate(user.id, selectedDateStr);
          const remoteBookings = await GroupClassService.fetchBookingsForDate(user.id, selectedDateStr);

          if (remoteClasses && remoteClasses.length > 0) {
            const bookedClassIds = new Set(remoteBookings.map((b) => b.class_id));
            const formatted = remoteClasses.map((c: any) => ({
              id: c.id,
              name: c.name,
              instructor: c.instructor_name || 'Eğitmen',
              time: `${c.start_time || '10:00'} - ${c.end_time || '11:00'}`,
              location: c.location || 'Ana Salon',
              capacity: c.capacity || 15,
              booked: c.booked_count || 5,
              status: bookedClassIds.has(c.id) ? 'joined' : (bookingsMap[c.id] || 'none'),
            }));

            setClasses(formatted);
            setLoading(false);
            return;
          }
        }
      } catch (e) {
        console.error('Remote class fetch error:', e);
      }

      // Fallback schedule based on day of week
      const template = SCHEDULE_TEMPLATES[dayOfWeekIndex] || SCHEDULE_TEMPLATES[1];
      const withStatus = template.map((c) => ({
        ...c,
        status: bookingsMap[`${selectedDateStr}_${c.id}`] || 'none',
      }));

      setClasses(withStatus);
      setLoading(false);
    };

    loadDataForDate();
  }, [selectedDateStr, days]);

  const handleDaySelect = (dayItem: any) => {
    setSelectedDayId(dayItem.id);
    setSelectedDateStr(dayItem.fullDate);
  };

  const handleBooking = async (item: any) => {
    try {
      const isFull = item.booked >= item.capacity;
      const currentStatus = item.status || 'none';
      const newStatus = currentStatus === 'none' ? (isFull ? 'waitlist' : 'joined') : 'none';

      const bookingKey = `${selectedDateStr}_${item.id}`;
      const updatedBookings = { ...userBookings, [bookingKey]: newStatus };
      if (newStatus === 'none') {
        delete updatedBookings[bookingKey];
      }

      setUserBookings(updatedBookings);
      await saveLocalBookings(updatedBookings);

      setClasses((prev) =>
        prev.map((c) =>
          c.id === item.id
            ? {
                ...c,
                status: newStatus,
                booked: newStatus === 'joined' ? c.booked + 1 : (currentStatus === 'joined' ? c.booked - 1 : c.booked),
              }
            : c
        )
      );

      // Attempt remote booking if user logged in
      const user = await AuthService.getCurrentUser();
      if (user?.id) {
        if (newStatus !== 'none') {
          await GroupClassService.bookClass(user.id, item.id, selectedDateStr).catch(() => {});
        }
      }

      const msg = newStatus === 'none' ? 'Dersten ayrıldınız.' : (newStatus === 'waitlist' ? 'Bekleme listesine alındınız.' : 'Derse başarıyla katıldınız.');
      feedback.toast(msg, newStatus === 'none' ? 'info' : 'success');
    } catch (error) {
      feedback.error({ title: 'Hata', message: error, fallbackMessage: 'Bir sorun oluştu.' });
    }
  };

  const checkIsClassPassed = (dateStr: string, timeStr: string): boolean => {
    const now = new Date();
    const todayStr = now.toISOString().split('T')[0];

    if (dateStr < todayStr) return true;
    if (dateStr > todayStr) return false;

    if (!timeStr) return false;
    const parts = timeStr.split('-').map((s) => s.trim());
    const targetTimePart = parts.length > 1 ? parts[1] : parts[0];
    const timeTokens = targetTimePart.split(':');
    if (timeTokens.length < 2) return false;

    const endHour = parseInt(timeTokens[0], 10);
    const endMin = parseInt(timeTokens[1], 10);

    if (isNaN(endHour)) return false;

    const currentHour = now.getHours();
    const currentMin = now.getMinutes();

    if (currentHour > endHour) return true;
    if (currentHour === endHour && currentMin >= endMin) return true;

    return false;
  };

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: colors.bg }]}>
      <View style={styles.headerRow}>
        <TouchableOpacity onPress={() => navigation?.goBack()} style={styles.backBtn}>
          <ArrowLeft size={22} color={colors.textPrimary} />
        </TouchableOpacity>
        <Text style={[styles.headerTitle, { color: colors.textPrimary }]}>Grup Dersleri</Text>
      </View>
      
      {/* Haftalık Dinamik Gün Seçici */}
      <View style={[styles.daysWrapper, { borderBottomColor: colors.border }]}>
        <ScrollView ref={scrollViewRef} horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.daysContainer}>
          {days.map((day) => (
            <TouchableOpacity
              key={day.id}
              style={[
                styles.dayItem, 
                { backgroundColor: colors.cardBg }, 
                selectedDayId === day.id && styles.selectedDayItem
              ]}
              onPress={() => handleDaySelect(day)}
            >
              <Text style={[styles.dayName, { color: colors.textSecondary }, selectedDayId === day.id && styles.selectedDayText]}>
                {day.name}
              </Text>
              <Text style={[styles.dayDate, { color: colors.textPrimary }, selectedDayId === day.id && styles.selectedDayText]}>
                {day.dateNumber}
              </Text>
            </TouchableOpacity>
          ))}
        </ScrollView>
      </View>

      {/* Ders Listesi */}
      {loading ? (
        <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
          <ActivityIndicator size="large" color={Colors.primary} />
        </View>
      ) : (
        <ScrollView contentContainerStyle={styles.listContainer}>
          {classes.length === 0 ? (
            <View style={{ padding: 40, alignItems: 'center' }}>
              <Text style={{ color: colors.textSecondary, fontSize: 16 }}>Bu gün için kayıtlı grup dersi bulunmamaktadır.</Text>
            </View>
          ) : (
            classes.map((item) => {
              const isFull = item.booked >= item.capacity;
              const isPassed = checkIsClassPassed(selectedDateStr, item.time);
              return (
                <View key={item.id} style={[styles.card, { backgroundColor: colors.cardBg, borderColor: colors.border }]}>
                  <View style={styles.cardHeader}>
                    <Text style={[styles.className, { color: colors.textPrimary }]}>{item.name}</Text>
                    {item.status === 'joined' && (
                      <View style={[styles.badge, { backgroundColor: Colors.success }]}>
                        <Text style={styles.badgeText}>Katıldın</Text>
                      </View>
                    )}
                    {item.status === 'waitlist' && (
                      <View style={[styles.badge, { backgroundColor: Colors.warning }]}>
                        <Text style={styles.badgeText}>Yedekte</Text>
                      </View>
                    )}
                    {item.status === 'none' && isFull && !isPassed && (
                      <View style={[styles.badge, { backgroundColor: Colors.error }]}>
                        <Text style={styles.badgeText}>Dolu</Text>
                      </View>
                    )}
                    {isPassed && item.status === 'none' && (
                      <View style={[styles.badge, { backgroundColor: '#666' }]}>
                        <Text style={styles.badgeText}>Tamamlandı</Text>
                      </View>
                    )}
                  </View>

                  <View style={styles.cardBody}>
                    <View style={styles.infoRow}>
                      <UserCheck size={16} color={colors.textSecondary} />
                      <Text style={[styles.infoText, { color: colors.textSecondary }]}>{item.instructor}</Text>
                    </View>
                    <View style={styles.infoRow}>
                      <Clock size={16} color={colors.textSecondary} />
                      <Text style={[styles.infoText, { color: colors.textSecondary }]}>{item.time}</Text>
                    </View>
                    <View style={styles.infoRow}>
                      <MapPin size={16} color={colors.textSecondary} />
                      <Text style={[styles.infoText, { color: colors.textSecondary }]}>{item.location}</Text>
                    </View>
                    <View style={styles.infoRow}>
                      <Users size={16} color={colors.textSecondary} />
                      <Text style={[styles.infoText, { color: colors.textSecondary }]}>
                        {item.booked}/{item.capacity}
                      </Text>
                    </View>
                  </View>

                  <TouchableOpacity
                    style={[
                      styles.actionButton,
                      isPassed
                        ? { backgroundColor: '#555', opacity: 0.6 }
                        : item.status !== 'none'
                        ? styles.leaveButton
                        : isFull
                        ? styles.waitlistButton
                        : styles.joinButton,
                    ]}
                    disabled={isPassed}
                    onPress={() => !isPassed && handleBooking(item)}
                  >
                    <Text style={styles.actionButtonText}>
                      {isPassed
                        ? 'Etkinlik Bitmiştir'
                        : item.status !== 'none'
                        ? 'Ayrıl'
                        : isFull
                        ? 'Bekleme Listesine Gir'
                        : 'Katıl'}
                    </Text>
                  </TouchableOpacity>
                </View>
              );
            })
          )}
        </ScrollView>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.background,
  },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingTop: 16,
    paddingBottom: 8,
  },
  backBtn: {
    padding: 8,
    marginRight: 8,
  },
  headerTitle: {
    fontSize: 24,
    fontWeight: 'bold',
    color: Colors.text,
  },
  daysWrapper: {
    height: 80,
    borderBottomWidth: 1,
    borderBottomColor: Colors.border,
  },
  daysContainer: {
    paddingHorizontal: 15,
    alignItems: 'center',
  },
  dayItem: {
    width: 60,
    height: 65,
    justifyContent: 'center',
    alignItems: 'center',
    borderRadius: 15,
    marginHorizontal: 5,
    backgroundColor: Colors.surface,
  },
  selectedDayItem: {
    backgroundColor: Colors.primary,
  },
  dayName: {
    fontSize: 14,
    color: Colors.textLight,
    marginBottom: 4,
  },
  dayDate: {
    fontSize: 16,
    fontWeight: 'bold',
    color: Colors.text,
  },
  selectedDayText: {
    color: '#fff',
  },
  listContainer: {
    padding: 20,
  },
  card: {
    backgroundColor: Colors.surface,
    borderRadius: 16,
    padding: 16,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: Colors.border,
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  className: {
    fontSize: 18,
    fontWeight: 'bold',
    color: Colors.text,
  },
  badge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
  },
  badgeText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: 'bold',
  },
  cardBody: {
    marginBottom: 16,
  },
  infoRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  infoText: {
    marginLeft: 8,
    fontSize: 14,
    color: Colors.textLight,
  },
  actionButton: {
    paddingVertical: 12,
    borderRadius: 8,
    alignItems: 'center',
  },
  joinButton: {
    backgroundColor: Colors.primary,
  },
  leaveButton: {
    backgroundColor: Colors.error,
  },
  waitlistButton: {
    backgroundColor: Colors.warning,
  },
  actionButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: 'bold',
  },
});
