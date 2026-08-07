import React, { useState } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView, Alert, SafeAreaView, Dimensions } from 'react-native';
import { Colors } from '../../theme/colors';
import { GYM_CONFIG } from '../../config/gym';
import { Users, Clock, MapPin, CheckCircle, UserCheck, AlertCircle } from 'lucide-react-native';
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
    status: 'none', // none, joined, waitlist
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

export default function GroupClassesScreen() {
  const [selectedDay, setSelectedDay] = useState(1);
  const [classes, setClasses] = useState(mockClasses);

  const handleBooking = async (item: any) => {
    try {
      // Supabase class_bookings simülasyonu
      const newStatus = item.status === 'none' ? (item.booked >= item.capacity ? 'waitlist' : 'joined') : 'none';
      
      /* Supabase kaydı (gerçek uygulamada)
      const { data, error } = await supabase
        .from('class_bookings')
        .insert([{ class_id: item.id, status: newStatus }]);
      if (error) throw error;
      */

      setClasses((prev) =>
        prev.map((c) =>
          c.id === item.id
            ? { ...c, status: newStatus, booked: newStatus === 'joined' ? c.booked + 1 : (item.status === 'joined' ? c.booked - 1 : c.booked) }
            : c
        )
      );

      Alert.alert('Başarılı', newStatus === 'none' ? 'İptal edildi.' : (newStatus === 'waitlist' ? 'Bekleme listesine alındınız.' : 'Derse katıldınız.'));
    } catch (error) {
      Alert.alert('Hata', 'Bir sorun oluştu.');
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.headerTitle}>Grup Dersleri</Text>
      
      {/* Haftalık Gün Seçici */}
      <View style={styles.daysWrapper}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.daysContainer}>
          {days.map((day) => (
            <TouchableOpacity
              key={day.id}
              style={[styles.dayItem, selectedDay === day.id && styles.selectedDayItem]}
              onPress={() => setSelectedDay(day.id)}
            >
              <Text style={[styles.dayName, selectedDay === day.id && styles.selectedDayText]}>{day.name}</Text>
              <Text style={[styles.dayDate, selectedDay === day.id && styles.selectedDayText]}>{day.date}</Text>
            </TouchableOpacity>
          ))}
        </ScrollView>
      </View>

      {/* Ders Listesi */}
      <ScrollView contentContainerStyle={styles.listContainer}>
        {classes.map((item) => {
          const isFull = item.booked >= item.capacity;
          return (
            <View key={item.id} style={styles.card}>
              <View style={styles.cardHeader}>
                <Text style={styles.className}>{item.name}</Text>
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
                  <UserCheck size={16} color={Colors.textSecondaryDark} />
                  <Text style={styles.infoText}>{item.instructor}</Text>
                </View>
                <View style={styles.infoRow}>
                  <Clock size={16} color={Colors.textSecondaryDark} />
                  <Text style={styles.infoText}>{item.time}</Text>
                </View>
                <View style={styles.infoRow}>
                  <MapPin size={16} color={Colors.textSecondaryDark} />
                  <Text style={styles.infoText}>{item.location}</Text>
                </View>
                <View style={styles.infoRow}>
                  <Users size={16} color={Colors.textSecondaryDark} />
                  <Text style={styles.infoText}>
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
  headerTitle: {
    fontSize: 24,
    fontWeight: 'bold',
    color: Colors.text,
    paddingHorizontal: 20,
    paddingTop: 20,
    paddingBottom: 10,
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
