import React, { useState } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, Modal, TextInput } from 'react-native';
import { Colors } from '../../theme/colors';
import { Info, MapPin, CheckCircle, Clock, XCircle, X } from 'lucide-react-native';

export const ReservationsScreen = () => {
  const [modalVisible, setModalVisible] = useState(false);
  const [selectedArea, setSelectedArea] = useState('');
  const [reservationNote, setReservationNote] = useState('');

  const openReservationModal = (area: string) => {
    setSelectedArea(area);
    setModalVisible(true);
  };

  const handleMakeReservation = () => {
    // Supabase reservations kaydı yapılacak
    setModalVisible(false);
    setReservationNote('');
  };

  return (
    <ScrollView style={styles.container}>
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
        <View style={styles.reservationItem}>
          <View style={styles.resInfo}>
            <Text style={styles.resArea}>Stüdyo (Pilates)</Text>
            <Text style={styles.resDate}>15.09.2026 - 18:00</Text>
            <View style={[styles.statusBadge, { backgroundColor: Colors.success + '20' }]}>
              <CheckCircle color={Colors.success} size={14} />
              <Text style={[styles.statusText, { color: Colors.success }]}>Onaylandı</Text>
            </View>
          </View>
          <TouchableOpacity style={styles.cancelBtn}>
            <Text style={styles.cancelBtnText}>İptal Et</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.reservationItem}>
          <View style={styles.resInfo}>
            <Text style={styles.resArea}>Tenis Kortu</Text>
            <Text style={styles.resDate}>16.09.2026 - 19:00</Text>
            <View style={[styles.statusBadge, { backgroundColor: Colors.warning + '20' }]}>
              <Clock color={Colors.warning} size={14} />
              <Text style={[styles.statusText, { color: Colors.warning }]}>Bekliyor</Text>
            </View>
          </View>
          <TouchableOpacity style={styles.cancelBtn}>
            <Text style={styles.cancelBtnText}>İptal Et</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.reservationItem}>
          <View style={styles.resInfo}>
            <Text style={styles.resArea}>Sauna</Text>
            <Text style={styles.resDate}>14.09.2026 - 20:00</Text>
            <View style={[styles.statusBadge, { backgroundColor: Colors.error + '20' }]}>
              <XCircle color={Colors.error} size={14} />
              <Text style={[styles.statusText, { color: Colors.error }]}>İptal</Text>
            </View>
          </View>
        </View>
      </View>

      {/* Rezervasyon Yapma Dialogu */}
      <Modal visible={modalVisible} transparent animationType="slide">
        <View style={styles.modalOverlay}>
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
              <TextInput style={styles.input} placeholder="15.09.2026" placeholderTextColor={Colors.textSecondary} />
            </View>
            
            <View style={styles.inputGroup}>
              <Text style={styles.inputLabel}>Saat Aralığı</Text>
              <TextInput style={styles.input} placeholder="18:00 - 19:00" placeholderTextColor={Colors.textSecondary} />
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
        </View>
      </Modal>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Colors.background, padding: 16 },
  headerTitle: { fontSize: 24, fontWeight: 'bold', color: Colors.text, marginBottom: 16 },
  infoCard: { flexDirection: 'row', backgroundColor: Colors.surface, padding: 12, borderRadius: 8, marginBottom: 20, alignItems: 'center' },
  infoText: { flex: 1, fontSize: 12, color: Colors.textSecondary, marginLeft: 12 },
  sectionTitle: { fontSize: 18, fontWeight: '600', color: Colors.text, marginBottom: 12, marginTop: 8 },
  areasGrid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', marginBottom: 20 },
  areaCard: { width: '48%', backgroundColor: Colors.surface, padding: 16, borderRadius: 12, alignItems: 'center', marginBottom: 12 },
  areaTitle: { fontSize: 16, fontWeight: 'bold', color: Colors.text, marginTop: 8 },
  areaCapacity: { fontSize: 12, color: Colors.textSecondary, marginTop: 4 },
  reservationList: { marginBottom: 30 },
  reservationItem: { flexDirection: 'row', backgroundColor: Colors.surface, padding: 16, borderRadius: 12, marginBottom: 12, alignItems: 'center', justifyContent: 'space-between' },
  resInfo: { flex: 1 },
  resArea: { fontSize: 16, fontWeight: 'bold', color: Colors.text, marginBottom: 4 },
  resDate: { fontSize: 14, color: Colors.textSecondary, marginBottom: 8 },
  statusBadge: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 8, paddingVertical: 4, borderRadius: 12, alignSelf: 'flex-start' },
  statusText: { fontSize: 12, fontWeight: '600', marginLeft: 4 },
  cancelBtn: { backgroundColor: Colors.error + '20', paddingHorizontal: 12, paddingVertical: 8, borderRadius: 8 },
  cancelBtnText: { color: Colors.error, fontSize: 12, fontWeight: 'bold' },
  modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'center', padding: 20 },
  modalContainer: { backgroundColor: Colors.surface, borderRadius: 16, padding: 20 },
  modalHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 },
  modalTitle: { fontSize: 20, fontWeight: 'bold', color: Colors.text },
  modalSub: { fontSize: 16, color: Colors.primary, marginBottom: 20, fontWeight: '500' },
  inputGroup: { marginBottom: 16 },
  inputLabel: { fontSize: 14, color: Colors.textSecondary, marginBottom: 8 },
  input: { backgroundColor: Colors.background, borderWidth: 1, borderColor: Colors.border, borderRadius: 8, padding: 12, color: Colors.text },
  textArea: { minHeight: 80, textAlignVertical: 'top' },
  submitBtn: { backgroundColor: Colors.primary, padding: 16, borderRadius: 8, alignItems: 'center', marginTop: 8 },
  submitBtnText: { color: Colors.white, fontSize: 16, fontWeight: 'bold' }
});
