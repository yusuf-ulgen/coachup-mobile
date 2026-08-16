import React, { useState, useEffect, useCallback } from 'react';
import { useFocusEffect } from '@react-navigation/native';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, TextInput, ActivityIndicator } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Colors } from '../../theme/colors';
import { MapPin, Clock, X, ArrowLeft, Calendar, Plus } from 'lucide-react-native';
import { DateTimePickerModal } from '../../components/DateTimePickerModal';
import { feedback } from '../../services/feedbackService';
import { supabase } from '../../services/supabaseClient';
import { AuthService } from '../../services/authService';
import { UserService } from '../../services/userService';
import { SmoothModal } from '../../components/motion/SmoothModal';

export const ReservationsScreen = ({ navigation }: any) => {
  const [loading, setLoading] = useState(true);
  const [areas, setAreas] = useState<any[]>([]);
  const [reservations, setReservations] = useState<any[]>([]);
  const [modalVisible, setModalVisible] = useState(false);
  const [selectedAreaId, setSelectedAreaId] = useState('');
  const [reservationNote, setReservationNote] = useState('');
  const [resDate, setResDate] = useState('');
  const [startTime, setStartTime] = useState('14:00');
  const [endTime, setEndTime] = useState('15:00');
  const [showDatePicker, setShowDatePicker] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useFocusEffect(
    useCallback(() => {
      loadData();
    }, [])
  );

  useEffect(() => {
    let channel: any = null;
    AuthService.getCurrentUser().then((user) => {
      if (!user) return;
      channel = supabase
        .channel(`reservations:user:${user.id}`)
        .on(
          'postgres_changes',
          {
            event: '*',
            schema: 'public',
            table: 'user_reservations',
            filter: `user_id=eq.${user.id}`,
          },
          () => {
            loadData();
          }
        )
        .subscribe();
    });

    return () => {
      if (channel) supabase.removeChannel(channel);
    };
  }, []);

  const loadData = async () => {
    setLoading(true);
    try {
      const user = await AuthService.getCurrentUser();
      if (!user) return;
      const profile = await UserService.fetchProfile(user.id);
      const gymId = await UserService.resolveActiveGymIdForContent(profile);

      // 1. gym_areas çek
      let areaQuery = supabase.from('gym_areas').select('*').eq('is_active', true);
      if (gymId && gymId !== 'staff') {
        areaQuery = areaQuery.eq('gym_id', gymId);
      }
      const { data: areaData } = await areaQuery;
      setAreas(areaData || []);
      if (areaData && areaData.length > 0) {
        setSelectedAreaId(areaData[0].id);
      }

      // 2. user_reservations çek
      const { data: resData, error: resErr } = await supabase
        .from('user_reservations')
        .select(`
          *,
          area:gym_areas(id, name, description)
        `)
        .eq('user_id', user.id)
        .order('reservation_date', { ascending: false });

      if (resErr) {
        console.error('Error fetching reservations:', resErr);
      }
      setReservations(resData || []);
    } catch (e) {
      console.error('Error in loadData:', e);
    } finally {
      setLoading(false);
    }
  };

  const handleMakeReservation = async () => {
    if (!resDate || !startTime || !endTime) {
      feedback.warning({ title: 'Hata', message: 'Lütfen tarih ve saat aralığı seçin.' });
      return;
    }

    setSubmitting(true);
    try {
      const user = await AuthService.getCurrentUser();
      if (!user) throw new Error('Kullanıcı oturumu bulunamadı.');

      const selectedArea = areas.find((a) => a.id === selectedAreaId);
      const gymId = selectedArea?.gym_id || null;

      const payload = {
        user_id: user.id,
        gym_id: gymId,
        area_id: selectedAreaId || null,
        reservation_date: resDate,
        start_time: startTime,
        end_time: endTime,
        notes: reservationNote.trim() || null,
        status: 'pending',
      };

      const { data, error } = await supabase
        .from('user_reservations')
        .insert(payload)
        .select(`
          *,
          area:gym_areas(id, name, description)
        `)
        .single();

      if (error) throw error;

      setReservations((prev) => [data, ...prev]);
      setModalVisible(false);
      setReservationNote('');
      setResDate('');
      feedback.success({ title: 'Başarılı', message: 'Rezervasyon talebiniz salon yönetimine iletildi.' });
    } catch (e: any) {
      console.error('Reservation error:', e);
      feedback.error({ title: 'Hata', message: e, fallbackMessage: 'Rezervasyon oluşturulamadı.' });
    } finally {
      setSubmitting(false);
    }
  };

  const handleCancelReservation = async (id: string) => {
    const confirmed = await feedback.destructive({
      title: 'Rezervasyonu İptal Et',
      message: 'Bu rezervasyonu iptal etmek istediğinize emin misiniz?',
      confirmText: 'İptal Et',
      cancelText: 'Vazgeç',
    });

    if (!confirmed) return;

    try {
      const { error } = await supabase
        .from('user_reservations')
        .update({ status: 'cancelled' })
        .eq('id', id);

      if (error) throw error;

      setReservations((prev) =>
        prev.map((r) => (r.id === id ? { ...r, status: 'cancelled' } : r))
      );
      feedback.success({ title: 'Bilgi', message: 'Rezervasyon iptal edildi.' });
    } catch (e: any) {
      feedback.error({ title: 'Hata', message: e, fallbackMessage: 'Rezervasyon iptal edilemedi.' });
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'approved':
        return { label: 'Onaylandı', color: '#10B981', bg: 'rgba(16, 185, 129, 0.15)' };
      case 'rejected':
        return { label: 'Reddedildi', color: '#EF4444', bg: 'rgba(239, 68, 68, 0.15)' };
      case 'cancelled':
        return { label: 'İptal Edildi', color: '#64748B', bg: 'rgba(100, 116, 139, 0.15)' };
      default:
        return { label: 'Onay Bekliyor', color: '#F59E0B', bg: 'rgba(245, 158, 11, 0.15)' };
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation?.goBack()} style={styles.backButton}>
          <ArrowLeft size={24} color={Colors.textDark} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Alan Rezervasyonları</Text>
        <TouchableOpacity onPress={() => setModalVisible(true)} style={styles.addBtn}>
          <Plus size={20} color="#FFFFFF" />
        </TouchableOpacity>
      </View>

      {loading ? (
        <View style={styles.centerBox}>
          <ActivityIndicator size="large" color={Colors.primary} />
        </View>
      ) : (
        <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
          {/* Rezervasyonlarım Listesi */}
          <Text style={styles.sectionTitle}>Mevcut Taleplerim & Rezervasyonlarım</Text>

          {reservations.length === 0 ? (
            <View style={styles.emptyState}>
              <Calendar size={48} color={Colors.textSecondaryDark} />
              <Text style={styles.emptyTitle}>Kayıtlı Rezervasyon Yok</Text>
              <Text style={styles.emptySubtitle}>
                Stüdyo, kort veya havuz gibi salon alanları için rezervasyon talebinde bulunabilirsiniz.
              </Text>
              <TouchableOpacity onPress={() => setModalVisible(true)} style={styles.createBtn}>
                <Text style={styles.createBtnText}>Yeni Rezervasyon Yap</Text>
              </TouchableOpacity>
            </View>
          ) : (
            reservations.map((item) => {
              const badge = getStatusBadge(item.status);
              const areaName = item.area?.name || item.area || 'Salon Alanı';

              return (
                <View key={item.id} style={styles.card}>
                  <View style={styles.cardHeader}>
                    <View style={{ flex: 1 }}>
                      <Text style={styles.areaTitle}>{areaName}</Text>
                      <Text style={styles.timeText}>
                        {item.reservation_date || item.date} • {item.start_time || item.time} - {item.end_time || ''}
                      </Text>
                    </View>
                    <View style={[styles.statusBadge, { backgroundColor: badge.bg }]}>
                      <Text style={[styles.statusText, { color: badge.color }]}>{badge.label}</Text>
                    </View>
                  </View>

                  {item.notes ? (
                    <Text style={styles.notesText}>Not: {item.notes}</Text>
                  ) : null}

                  {item.status === 'pending' || item.status === 'approved' ? (
                    <View style={styles.cardActions}>
                      <TouchableOpacity
                        onPress={() => handleCancelReservation(item.id)}
                        style={styles.cancelBtn}
                      >
                        <Text style={styles.cancelBtnText}>İptal Et</Text>
                      </TouchableOpacity>
                    </View>
                  ) : null}
                </View>
              );
            })
          )}
        </ScrollView>
      )}

      {/* Yeni Rezervasyon Modalı */}
      <SmoothModal visible={modalVisible} onClose={() => setModalVisible(false)}>
        <View style={styles.modalContent}>
          <Text style={styles.modalTitle}>Alan Rezervasyonu</Text>

          {/* Alan Seçimi */}
          <Text style={styles.inputLabel}>Rezervasyon Alanı</Text>
          {areas.length > 0 ? (
            <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginBottom: 4 }}>
              {areas.map((a) => (
                <TouchableOpacity
                  key={a.id}
                  onPress={() => setSelectedAreaId(a.id)}
                  style={[
                    styles.areaChip,
                    selectedAreaId === a.id && styles.areaChipActive,
                  ]}
                >
                  <Text
                    style={[
                      styles.areaChipText,
                      selectedAreaId === a.id && styles.areaChipTextActive,
                    ]}
                  >
                    {a.name}
                  </Text>
                </TouchableOpacity>
              ))}
            </ScrollView>
          ) : (
            <Text style={{ color: Colors.textSecondaryDark, fontSize: 13, marginBottom: 8 }}>
              Kayıtlı salon alanı bulunamadı.
            </Text>
          )}

          {/* Tarih Seçimi */}
          <Text style={styles.inputLabel}>Tarih</Text>
          <TouchableOpacity
            onPress={() => setShowDatePicker(true)}
            style={styles.dateSelector}
          >
            <Calendar size={18} color={Colors.primary} />
            <Text style={{ color: resDate ? Colors.textDark : Colors.textSecondaryDark }}>
              {resDate || 'Tarih Seçin (YYYY-AA-GG)'}
            </Text>
          </TouchableOpacity>

          {/* Saatler */}
          <View style={{ flexDirection: 'row', gap: 12 }}>
            <View style={{ flex: 1 }}>
              <Text style={styles.inputLabel}>Başlangıç</Text>
              <TextInput
                style={styles.modalInput}
                value={startTime}
                onChangeText={setStartTime}
                placeholder="14:00"
                placeholderTextColor={Colors.textSecondaryDark}
              />
            </View>
            <View style={{ flex: 1 }}>
              <Text style={styles.inputLabel}>Bitiş</Text>
              <TextInput
                style={styles.modalInput}
                value={endTime}
                onChangeText={setEndTime}
                placeholder="15:00"
                placeholderTextColor={Colors.textSecondaryDark}
              />
            </View>
          </View>

          {/* Notlar */}
          <Text style={styles.inputLabel}>Notlar (İsteğe Bağlı)</Text>
          <TextInput
            style={[styles.modalInput, { height: 60 }]}
            value={reservationNote}
            onChangeText={setReservationNote}
            placeholder="Ekstra ekipman talebi veya bilgi..."
            placeholderTextColor={Colors.textSecondaryDark}
            multiline
          />

          <View style={styles.modalButtons}>
            <TouchableOpacity
              onPress={() => setModalVisible(false)}
              style={styles.modalCancelBtn}
              disabled={submitting}
            >
              <Text style={styles.modalCancelText}>Vazgeç</Text>
            </TouchableOpacity>
            <TouchableOpacity
              onPress={handleMakeReservation}
              style={styles.modalSubmitBtn}
              disabled={submitting}
            >
              {submitting ? (
                <ActivityIndicator size="small" color="#FFFFFF" />
              ) : (
                <Text style={styles.modalSubmitText}>Talep Gönder</Text>
              )}
            </TouchableOpacity>
          </View>
        </View>
      </SmoothModal>

      <DateTimePickerModal
        visible={showDatePicker}
        mode="date"
        initialValue={resDate}
        onConfirm={(val) => {
          setResDate(val);
          setShowDatePicker(false);
        }}
        onCancel={() => setShowDatePicker(false)}
      />
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0F172A',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#1E293B',
  },
  backButton: {
    padding: 4,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.textDark,
  },
  addBtn: {
    backgroundColor: Colors.primary,
    padding: 8,
    borderRadius: 8,
  },
  content: {
    padding: 16,
    gap: 14,
  },
  centerBox: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  sectionTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: Colors.textDark,
  },
  card: {
    backgroundColor: '#1E293B',
    borderRadius: 14,
    padding: 16,
    borderWidth: 1,
    borderColor: '#334155',
    gap: 10,
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  areaTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.textDark,
  },
  timeText: {
    fontSize: 13,
    color: Colors.primary,
    marginTop: 2,
    fontWeight: '500',
  },
  statusBadge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
  },
  statusText: {
    fontSize: 12,
    fontWeight: '600',
  },
  notesText: {
    fontSize: 12,
    color: '#94A3B8',
    fontStyle: 'italic',
  },
  cardActions: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    borderTopWidth: 1,
    borderTopColor: '#334155',
    paddingTop: 8,
  },
  cancelBtn: {
    paddingVertical: 4,
    paddingHorizontal: 8,
  },
  cancelBtnText: {
    fontSize: 12,
    color: Colors.error,
    fontWeight: '600',
  },
  emptyState: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 60,
    gap: 12,
  },
  emptyTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.textDark,
  },
  emptySubtitle: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    textAlign: 'center',
    paddingHorizontal: 32,
    lineHeight: 18,
  },
  createBtn: {
    backgroundColor: Colors.primary,
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 8,
    marginTop: 8,
  },
  createBtnText: {
    color: '#FFFFFF',
    fontWeight: '600',
    fontSize: 14,
  },
  modalContent: {
    padding: 16,
    gap: 10,
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.textDark,
    marginBottom: 4,
  },
  inputLabel: {
    fontSize: 12,
    fontWeight: '600',
    color: Colors.textSecondaryDark,
    marginTop: 4,
  },
  areaChip: {
    backgroundColor: '#0F172A',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 8,
    marginRight: 8,
    borderWidth: 1,
    borderColor: '#334155',
  },
  areaChipActive: {
    borderColor: Colors.primary,
    backgroundColor: 'rgba(255, 96, 71, 0.15)',
  },
  areaChipText: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
  },
  areaChipTextActive: {
    color: Colors.primary,
    fontWeight: '600',
  },
  dateSelector: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    backgroundColor: '#0F172A',
    borderRadius: 8,
    padding: 12,
    borderWidth: 1,
    borderColor: '#334155',
  },
  modalInput: {
    backgroundColor: '#0F172A',
    borderRadius: 8,
    padding: 10,
    color: Colors.textDark,
    borderWidth: 1,
    borderColor: '#334155',
    fontSize: 13,
  },
  modalButtons: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 12,
  },
  modalCancelBtn: {
    flex: 1,
    paddingVertical: 12,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#334155',
    alignItems: 'center',
  },
  modalCancelText: {
    color: Colors.textSecondaryDark,
    fontWeight: '600',
  },
  modalSubmitBtn: {
    flex: 1,
    backgroundColor: Colors.primary,
    paddingVertical: 12,
    borderRadius: 8,
    alignItems: 'center',
  },
  modalSubmitText: {
    color: '#FFFFFF',
    fontWeight: '600',
  },
});
