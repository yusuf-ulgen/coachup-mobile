import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, TextInput } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Colors } from '../../theme/colors';
import { Info, MapPin, CheckCircle, Clock, XCircle, X, ArrowLeft, Calendar } from 'lucide-react-native';
import { DateTimePickerModal } from '../../components/DateTimePickerModal';
import { feedback } from '../../services/feedbackService';
import { supabase } from '../../services/supabaseClient';
import { AuthService } from '../../services/authService';
import { SmoothModal } from '../../components/motion/SmoothModal';

export const ReservationsScreen = ({ navigation }: any) => {
  const [reservations, setReservations] = useState<any[]>([]);
  const [modalVisible, setModalVisible] = useState(false);
  const [selectedArea, setSelectedArea] = useState('');
  const [reservationNote, setReservationNote] = useState('');
  const [resDate, setResDate] = useState('');
  const [resTime, setResTime] = useState('');
  const [showDatePicker, setShowDatePicker] = useState(false);
  const [showTimePicker, setShowTimePicker] = useState(false);

  const STORAGE_KEY = '@user_reservations_cache';

  const saveLocalReservations = async (list: any[]) => {
    try {
      await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(list));
    } catch (e) {
      console.error('Error saving reservations locally:', e);
    }
  };

  const loadReservations = async () => {
    let localData: any[] = [];
    try {
      const stored = await AsyncStorage.getItem(STORAGE_KEY);
      if (stored) localData = JSON.parse(stored);
    } catch (e) {
      console.error('Error loading local reservations:', e);
    }

    try {
      const user = await AuthService.getCurrentUser();
      if (user?.id) {
        const { data, error } = await supabase
          .from('user_reservations')
          .select('*')
          .eq('user_id', user.id)
          .order('created_at', { ascending: false });

        if (!error && data && data.length > 0) {
          const remoteIds = new Set(data.map((r) => r.id));
          const localOnly = localData.filter((lr) => !remoteIds.has(lr.id));
          const merged = [...data, ...localOnly];
          setReservations(merged);
          saveLocalReservations(merged);
          return;
        }
      }
    } catch (e) {
      console.error('Error fetching remote reservations:', e);
    }

    setReservations(localData);
  };

  useEffect(() => {
    loadReservations();
  }, []);

  const openReservationModal = (area: string) => {
    setSelectedArea(area);
    setModalVisible(true);
  };

  const handleMakeReservation = async () => {
    if (!resDate || !resTime) {
      feedback.warning({ title: 'Hata', message: 'Lütfen tarih ve saat aralığı seçin.' });
      return;
    }

    const user = await AuthService.getCurrentUser();
    const newRes = {
      id: 'res_' + Date.now(),
      user_id: user?.id || 'guest',
      area: selectedArea,
      date: resDate,
      time: resTime,
      notes: reservationNote,
      status: 'pending',
      created_at: new Date().toISOString(),
    };

    const updated = [newRes, ...reservations];
    setReservations(updated);
    await saveLocalReservations(updated);

    if (user?.id) {
      try {
        await supabase.from('user_reservations').insert([
          {
            user_id: user.id,
            area: selectedArea,
            date: resDate,
            time: resTime,
            notes: reservationNote,
            status: 'pending',
          },
        ]);
      } catch {}
    }

    setModalVisible(false);
    setReservationNote('');
    setResDate('');
    setResTime('');

    feedback.success({ title: 'Başarılı', message: `${selectedArea} alanına rezervasyon talebiniz alındı.` });
  };

  const handleCancelReservation = async (id: string) => {
    const confirmed = await feedback.destructive({
      title: 'Rezervasyonu İptal Et',
      message: 'Bu rezervasyonu iptal etmek istediğinize emin misiniz?',
      confirmText: 'İptal Et',
      cancelText: 'Vazgeç',
    });

    if (!confirmed) return;

    const updated = reservations.map((r) => (r.id === id ? { ...r, status: 'cancelled' } : r));
    setReservations(updated);
    await saveLocalReservations(updated);

    try {
      await supabase.from('user_reservations').update({ status: 'cancelled' }).eq('id', id);
    } catch {}

    feedback.toast('Rezervasyon iptal edildi.', 'info');
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.headerRow}>
        <TouchableOpacity onPress={() => navigation?.goBack()} style={styles.backBtn}>
          <ArrowLeft size={22} color={Colors.allWhite} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Rezervasyonlar</Text>
      </View>
      <ScrollView contentContainerStyle={{ padding: 16 }}>
      {/* Bilgilendirme kartı */}
      <View style={styles.infoCard}>
        <Info color={Colors.primary} size={20} />
        <Text style={styles.infoText}>Rezervasyonlarınızı ders veya alan kullanımından en az 2 saat öncesinde iptal etmeniz gerekmektedir.</Text>
      </View>

      {/* Rezerve Edilebilir Salon Alanları */}
      <Text style={styles.sectionTitle}>Rezerve Edilebilir Alanlar</Text>
      <View style={styles.areasGrid}>
        <TouchableOpacity style={styles.areaCard} onPress={() => openReservationModal('Stüdyo')}>
          <MapPin color={Colors.primary} size={24} />
          <Text style={styles.areaTitle}>Stüdyo</Text>
          <Text style={styles.areaCapacity}>Kapasite: 15</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.areaCard} onPress={() => openReservationModal('Havuz')}>
          <MapPin color={Colors.primary} size={24} />
          <Text style={styles.areaTitle}>Havuz</Text>
          <Text style={styles.areaCapacity}>Kapasite: 10</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.areaCard} onPress={() => openReservationModal('Sauna')}>
          <MapPin color={Colors.primary} size={24} />
          <Text style={styles.areaTitle}>Sauna</Text>
          <Text style={styles.areaCapacity}>Kapasite: 5</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.areaCard} onPress={() => openReservationModal('Kort')}>
          <MapPin color={Colors.primary} size={24} />
          <Text style={styles.areaTitle}>Kort</Text>
          <Text style={styles.areaCapacity}>Kapasite: 4</Text>
        </TouchableOpacity>
      </View>

      {/* Aktif Rezervasyonlarım */}
      <Text style={styles.sectionTitle}>Aktif Rezervasyonlarım</Text>
      <View style={styles.reservationList}>
        {reservations.length === 0 ? (
          <View style={{ padding: 20, alignItems: 'center' }}>
            <Text style={{ color: Colors.textSecondary, fontSize: 14 }}>Henüz aktif bir rezervasyonunuz bulunmamaktadır.</Text>
          </View>
        ) : (
          reservations.map((item) => (
            <View key={item.id} style={styles.reservationItem}>
              <View style={styles.resInfo}>
                <Text style={styles.resArea}>{item.area}</Text>
                <Text style={styles.resDate}>{item.date} - {item.time}</Text>
                {item.status === 'approved' && (
                  <View style={[styles.statusBadge, { backgroundColor: Colors.success + '20' }]}>
                    <CheckCircle color={Colors.success} size={14} />
                    <Text style={[styles.statusText, { color: Colors.success }]}>Onaylandı</Text>
                  </View>
                )}
                {item.status === 'pending' && (
                  <View style={[styles.statusBadge, { backgroundColor: Colors.warning + '20' }]}>
                    <Clock color={Colors.warning} size={14} />
                    <Text style={[styles.statusText, { color: Colors.warning }]}>Bekliyor</Text>
                  </View>
                )}
                {item.status === 'cancelled' && (
                  <View style={[styles.statusBadge, { backgroundColor: Colors.error + '20' }]}>
                    <XCircle color={Colors.error} size={14} />
                    <Text style={[styles.statusText, { color: Colors.error }]}>İptal</Text>
                  </View>
                )}
              </View>
              {item.status !== 'cancelled' && (
                <TouchableOpacity style={styles.cancelBtn} onPress={() => handleCancelReservation(item.id)}>
                  <Text style={styles.cancelBtnText}>İptal Et</Text>
                </TouchableOpacity>
              )}
            </View>
          ))
        )}
      </View>

      {/* Rezervasyon Yapma Dialogu */}
      <SmoothModal visible={modalVisible} onClose={() => setModalVisible(false)} variant="bottom-sheet">
        <View style={styles.modalContainer}>
          <View style={styles.modalHeader}>
            <Text style={styles.modalTitle}>Rezervasyon Yap</Text>
            <TouchableOpacity onPress={() => setModalVisible(false)}>
              <X color={Colors.text} size={24} />
            </TouchableOpacity>
          </View>
          <Text style={styles.modalSub}>Seçilen Alan: {selectedArea}</Text>
          
          <View style={styles.inputGroup}>
            <Text style={styles.inputLabel}>Tarih</Text>
            <TouchableOpacity 
              onPress={() => setShowDatePicker(true)}
              style={[styles.input, { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }]}
            >
              <Text style={{ color: resDate ? Colors.text : Colors.textSecondary }}>
                {resDate || 'Tarih Seçin (YYYY-MM-DD)'}
              </Text>
              <Calendar size={18} color={Colors.primary} />
            </TouchableOpacity>
          </View>
          
          <View style={styles.inputGroup}>
            <Text style={styles.inputLabel}>Saat Aralığı</Text>
            <TouchableOpacity 
              onPress={() => setShowTimePicker(true)}
              style={[styles.input, { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }]}
            >
              <Text style={{ color: resTime ? Colors.text : Colors.textSecondary }}>
                {resTime || 'Saat Seçin (Örn: 18:00 - 19:00)'}
              </Text>
              <Clock size={18} color={Colors.primary} />
            </TouchableOpacity>
          </View>
          
          <View style={styles.inputGroup}>
            <Text style={styles.inputLabel}>Not (Opsiyonel)</Text>
            <TextInput 
              style={[styles.input, styles.textArea]} 
              placeholder="Örn: Ekipman talebi..." 
              placeholderTextColor={Colors.textSecondary}
              multiline
              value={reservationNote}
              onChangeText={setReservationNote}
            />
          </View>

          <TouchableOpacity style={styles.submitBtn} onPress={handleMakeReservation}>
            <Text style={styles.submitBtnText}>Rezervasyonu Tamamla</Text>
          </TouchableOpacity>
        </View>
      </SmoothModal>

      {/* Date & Time Picker Modals */}
      <DateTimePickerModal
        visible={showDatePicker}
        mode="date"
        title="Rezervasyon Tarihi Seç"
        initialValue={resDate}
        onConfirm={(d) => {
          setResDate(d);
          setShowDatePicker(false);
        }}
        onCancel={() => setShowDatePicker(false)}
      />

      <DateTimePickerModal
        visible={showTimePicker}
        mode="time"
        title="Rezervasyon Saati Seç"
        initialValue={resTime}
        onConfirm={(t) => {
          setResTime(t);
          setShowTimePicker(false);
        }}
        onCancel={() => setShowTimePicker(false)}
      />
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Colors.backgroundDark },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingTop: 12,
    paddingBottom: 8,
  },
  backBtn: {
    padding: 8,
    marginRight: 8,
  },
  headerTitle: { fontSize: 24, fontWeight: 'bold', color: Colors.allWhite },
  infoCard: { flexDirection: 'row', backgroundColor: Colors.cardDark, padding: 12, borderRadius: 8, marginBottom: 20, alignItems: 'center' },
  infoText: { flex: 1, fontSize: 12, color: Colors.textSecondaryDark, marginLeft: 12 },
  sectionTitle: { fontSize: 18, fontWeight: '600', color: Colors.allWhite, marginBottom: 12, marginTop: 8 },
  areasGrid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', marginBottom: 20 },
  areaCard: { width: '48%', backgroundColor: Colors.cardDark, padding: 16, borderRadius: 12, alignItems: 'center', marginBottom: 12 },
  areaTitle: { fontSize: 16, fontWeight: 'bold', color: Colors.allWhite, marginTop: 8 },
  areaCapacity: { fontSize: 12, color: Colors.textSecondaryDark, marginTop: 4 },
  reservationList: { marginBottom: 30 },
  reservationItem: { flexDirection: 'row', backgroundColor: Colors.cardDark, padding: 16, borderRadius: 12, marginBottom: 12, alignItems: 'center', justifyContent: 'space-between' },
  resInfo: { flex: 1 },
  resArea: { fontSize: 16, fontWeight: 'bold', color: Colors.allWhite, marginBottom: 4 },
  resDate: { fontSize: 14, color: Colors.textSecondaryDark, marginBottom: 8 },
  statusBadge: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 8, paddingVertical: 4, borderRadius: 12, alignSelf: 'flex-start' },
  statusText: { fontSize: 12, fontWeight: '600', marginLeft: 4 },
  cancelBtn: { backgroundColor: Colors.error + '20', paddingHorizontal: 12, paddingVertical: 8, borderRadius: 8 },
  cancelBtnText: { color: Colors.error, fontSize: 12, fontWeight: 'bold' },
  modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'center', padding: 20 },
  modalContainer: { backgroundColor: Colors.cardDark, borderRadius: 16, padding: 20 },
  modalHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 },
  modalTitle: { fontSize: 20, fontWeight: 'bold', color: Colors.allWhite },
  modalSub: { fontSize: 16, color: Colors.primary, marginBottom: 20, fontWeight: '500' },
  inputGroup: { marginBottom: 16 },
  inputLabel: { fontSize: 14, color: Colors.textSecondaryDark, marginBottom: 8 },
  input: { backgroundColor: Colors.backgroundDark, borderWidth: 1, borderColor: Colors.borderDark, borderRadius: 8, padding: 12, color: Colors.allWhite },
  textArea: { minHeight: 80, textAlignVertical: 'top' },
  submitBtn: { backgroundColor: Colors.primary, padding: 16, borderRadius: 8, alignItems: 'center', marginTop: 8 },
  submitBtnText: { color: Colors.allWhite, fontSize: 16, fontWeight: 'bold' }
});
