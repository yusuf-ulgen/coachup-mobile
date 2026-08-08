import React, { useState } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView, Dimensions } from 'react-native';
import { feedback } from '../../services/feedbackService';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Colors } from '../../theme/colors';
import { useTheme } from '../../theme/ThemeContext';
import { Users, Clock, MapPin, CheckCircle, UserCheck, AlertCircle, ArrowLeft } from 'lucide-react-native';
import { supabase } from '../../services/supabaseClient';

// Mock veriler
const mockClasses = [
  {
    id: '1',
    name: 'Pilates',
    instructor: 'Ayşe Yılmaz',
    time: '10:00 - 11:00',
    location: 'Stüdyo 1',
    capacity: 10,
    booked: 8,
    status: 'none',
  },
  {
    id: '2',
    name: 'Yoga',
    instructor: 'Fatma Şahin',
    time: '12:00 - 13:00',
    location: 'Stüdyo 2',
    capacity: 15,
    booked: 15,
    status: 'none',
  },
  {
    id: '3',
    name: 'Zumba',
    instructor: 'Ahmet Demir',
    time: '18:00 - 19:00',
    location: 'Ana Salon',
    capacity: 20,
    booked: 19,
    status: 'joined',
  },
];

const days = [
  { id: 1, name: 'Pzt', date: '10' },
  { id: 2, name: 'Sal', date: '11' },
  { id: 3, name: 'Çar', date: '12' },
  { id: 4, name: 'Per', date: '13' },
  { id: 5, name: 'Cum', date: '14' },
  { id: 6, name: 'Cmt', date: '15' },
  { id: 7, name: 'Paz', date: '16' },
];

export default function GroupClassesScreen({ navigation }: any) {
  const { colors } = useTheme();
  const [selectedDay, setSelectedDay] = useState(1);
  const [classes, setClasses] = useState(mockClasses);

  const handleBooking = async (item: any) => {
    try {
      const newStatus = item.status === 'none' ? (item.booked >= item.capacity ? 'waitlist' : 'joined') : 'none';

      setClasses((prev) =>
        prev.map((c) =>
          c.id === item.id
            ? { ...c, status: newStatus, booked: newStatus === 'joined' ? c.booked + 1 : (item.status === 'joined' ? c.booked - 1 : c.booked) }
            : c
        )
      );

      const msg = newStatus === 'none' ? 'İptal edildi.' : (newStatus === 'waitlist' ? 'Bekleme listesine alındınız.' : 'Derse katıldınız.');
      feedback.toast(msg, newStatus === 'none' ? 'info' : 'success');
    } catch (error) {
      feedback.error({ title: 'Hata', message: error, fallbackMessage: 'Bir sorun oluştu.' });
    }
  };

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: colors.bg }]}>
      <View style={styles.headerRow}>
        <TouchableOpacity onPress={() => navigation?.goBack()} style={styles.backBtn}>
          <ArrowLeft size={22} color={colors.textPrimary} />
        </TouchableOpacity>
        <Text style={[styles.headerTitle, { color: colors.textPrimary }]}>Grup Dersleri</Text>
      </View>
      
      {/* Haftalık Gün Seçici */}
      <View style={[styles.daysWrapper, { borderBottomColor: colors.border }]}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.daysContainer}>
          {days.map((day) => (
            <TouchableOpacity
              key={day.id}
              style={[styles.dayItem, { backgroundColor: colors.cardBg }, selectedDay === day.id && styles.selectedDayItem]}
              onPress={() => setSelectedDay(day.id)}
            >
              <Text style={[styles.dayName, { color: colors.textSecondary }, selectedDay === day.id && styles.selectedDayText]}>{day.name}</Text>
              <Text style={[styles.dayDate, { color: colors.textPrimary }, selectedDay === day.id && styles.selectedDayText]}>{day.date}</Text>
            </TouchableOpacity>
          ))}
        </ScrollView>
      </View>

      {/* Ders Listesi */}
      <ScrollView contentContainerStyle={styles.listContainer}>
        {classes.map((item) => {
          const isFull = item.booked >= item.capacity;
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
                {item.status === 'none' && isFull && (
                  <View style={[styles.badge, { backgroundColor: Colors.error }]}>
                    <Text style={styles.badgeText}>Dolu</Text>
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
                  item.status !== 'none' ? styles.leaveButton : (isFull ? styles.waitlistButton : styles.joinButton),
                ]}
                onPress={() => handleBooking(item)}
              >
                <Text style={styles.actionButtonText}>
                  {item.status !== 'none'
                    ? 'Ayrıl'
                    : isFull
                    ? 'Bekleme Listesine Gir'
                    : 'Katıl'}
                </Text>
              </TouchableOpacity>
            </View>
          );
        })}
      </ScrollView>
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
