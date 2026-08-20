import React, { useState, useEffect, useCallback } from 'react';
import { useFocusEffect } from '@react-navigation/native';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  TextInput,
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import { Colors } from '../../theme/colors';
import { feedback } from '../../services/feedbackService';
import { AuthService } from '../../services/authService';
import { supabase } from '../../services/supabaseClient';
import { ScreenContainer } from '../../components/ScreenContainer';
import { ArrowLeft, Calendar, Clock, User, CheckCircle2, XCircle, AlertCircle, Plus } from 'lucide-react-native';
import { DateTimePickerModal } from '../../components/DateTimePickerModal';

import { UserService } from '../../services/userService';

interface AppointmentItem {
  id: string;
  user_id: string;
  coach_id?: string;
  gym_id?: string;
  appointment_date: string;
  start_time: string;
  end_time: string;
  service_type: string;
  status: 'pending' | 'confirmed' | 'completed' | 'cancelled';
  notes?: string;
  created_at: string;
  coach?: {
    id: string;
    name: string;
    surname?: string;
    specialty?: string;
  };
}

const SERVICE_TYPES = [
  { label: 'Bireysel Antrenman (PT)', value: 'pt' },
  { label: 'Beslenme Danışmanlığı', value: 'nutrition' },
  { label: 'Vücut Analizi & Ölçüm', value: 'assessment' },
  { label: 'Genel Danışmanlık', value: 'consultation' },
];

export const AppointmentsScreen: React.FC<{ navigation?: any; route?: any }> = ({ navigation, route }) => {
  const [appointments, setAppointments] = useState<AppointmentItem[]>([]);
  const [coaches, setCoaches] = useState<any[]>([]);
  const [activeGymId, setActiveGymId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  // Form State
  const [showModal, setShowModal] = useState(false);
  const [selectedCoachId, setSelectedCoachId] = useState<string>('');
  const [serviceType, setServiceType] = useState<string>('pt');
  const [appointmentDate, setAppointmentDate] = useState<string>('');
  const [startTime, setStartTime] = useState<string>('10:00');
  const [endTime, setEndTime] = useState<string>('11:00');
  const [notes, setNotes] = useState<string>('');
  const [showDatePicker, setShowDatePicker] = useState(false);
  const [showTimePicker, setShowTimePicker] = useState<'start' | 'end' | 'slot' | null>(null);

  const initialCoachHandledRef = React.useRef(false);

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
        .channel(`appointments:user:${user.id}`)
        .on(
          'postgres_changes',
          {
            event: '*',
            schema: 'public',
            table: 'appointments',
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

      // 1. Resolve member's active gym
      const profile = await UserService.fetchProfile(user.id);
      const resolvedGymId = await UserService.resolveActiveGymIdForContent(profile);
      setActiveGymId(resolvedGymId);

      // 2. Load Appointments with relation fallback
      let finalAppointments: AppointmentItem[] = [];
      const { data: appData, error: appErr } = await supabase
        .from('appointments')
        .select(`
          *,
          coach:coaches(id, name, surname, specialty)
        `)
        .eq('user_id', user.id)
        .order('appointment_date', { ascending: false });

      if (appErr) {
        console.warn('Embedded coach select warning on appointments, falling back to base query:', appErr);
        const { data: rawData, error: rawErr } = await supabase
          .from('appointments')
          .select('*')
          .eq('user_id', user.id)
          .order('appointment_date', { ascending: false });

        if (rawErr) throw rawErr;
        finalAppointments = rawData || [];
      } else {
        finalAppointments = appData || [];
      }
      setAppointments(finalAppointments);

      // 3. Load Active Gym Scoped Coaches
      if (resolvedGymId && resolvedGymId !== 'staff') {
        const { data: coachData, error: coachErr } = await supabase
          .from('coaches')
          .select('id, name, surname, specialty, gym_id')
          .eq('gym_id', resolvedGymId)
          .eq('is_active', true);

        if (!coachErr && coachData) {
          setCoaches(coachData);

          // Check incoming route coachId
          const routeCoachId = route?.params?.coachId;
          if (routeCoachId && !initialCoachHandledRef.current) {
            const matchingCoach = coachData.find((c: any) => c.id === routeCoachId);
            if (matchingCoach) {
              setSelectedCoachId(matchingCoach.id);
              setShowModal(true);
            } else {
              feedback.toast('Seçilen koç aktif spor salonunuza ait değil.', 'warning');
              if (coachData.length > 0) {
                setSelectedCoachId(coachData[0].id);
              }
            }
            initialCoachHandledRef.current = true;
            navigation?.setParams({ coachId: undefined });
          } else {
            setSelectedCoachId((prev) => {
              if (prev && coachData.some((c: any) => c.id === prev)) {
                return prev;
              }
              return coachData.length > 0 ? coachData[0].id : '';
            });
          }
        }
      } else {
        // Member has no valid active gym -> NEVER execute unscoped query or show cross-gym coaches
        setCoaches([]);
        setSelectedCoachId('');
      }
    } catch (e: any) {
      console.error('Error loading appointments:', e);
      feedback.error({ title: 'Hata', message: e, fallbackMessage: 'Randevular yüklenemedi.' });
    } finally {
      setLoading(false);
    }
  };

  const getLocalTodayString = () => {
    const d = new Date();
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  };

  const handleCreateAppointment = async () => {
    if (submitting) return;

    if (!activeGymId) {
      feedback.toast('Aktif spor salonu bulunamadı. Lütfen üyeliğinizi kontrol edin.', 'warning');
      return;
    }

    if (!selectedCoachId) {
      feedback.toast('Lütfen bir eğitmen seçin.', 'warning');
      return;
    }

    const selectedCoach = coaches.find((c) => c.id === selectedCoachId);
    if (!selectedCoach || selectedCoach.gym_id !== activeGymId) {
      feedback.toast('Seçilen eğitmen geçerli değil veya aktif salonunuza ait değil.', 'warning');
      return;
    }

    if (!appointmentDate) {
      feedback.toast('Lütfen randevu tarihi seçin.', 'warning');
      return;
    }

    const todayStr = getLocalTodayString();
    if (appointmentDate < todayStr) {
      feedback.toast('Geçmiş bir tarihe randevu oluşturulamaz.', 'warning');
      return;
    }

    const timeRegex = /^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$/;
    const cleanStart = startTime.trim();
    const cleanEnd = endTime.trim();

    if (!timeRegex.test(cleanStart) || !timeRegex.test(cleanEnd)) {
      feedback.toast('Lütfen saat formatını HH:mm olarak girin (örn. 10:00).', 'warning');
      return;
    }

    const [startH, startM] = cleanStart.split(':').map(Number);
    const [endH, endM] = cleanEnd.split(':').map(Number);
    const startMinutes = startH * 60 + startM;
    const endMinutes = endH * 60 + endM;

    if (endMinutes <= startMinutes) {
      feedback.toast('Bitiş saati başlangıç saatinden sonra olmalıdır.', 'warning');
      return;
    }

    setSubmitting(true);
    try {
      const user = await AuthService.getCurrentUser();
      if (!user) throw new Error('Oturum açmış kullanıcı bulunamadı.');

      const selectedCoach = coaches.find((c) => c.id === selectedCoachId);
      const gymId = activeGymId || selectedCoach?.gym_id || null;

      const payload = {
        user_id: user.id,
        coach_id: selectedCoachId || null,
        gym_id: gymId,
        date: appointmentDate,
        appointment_date: appointmentDate,
        start_time: cleanStart,
        end_time: cleanEnd,
        service_type: serviceType,
        status: 'pending',
        notes: notes.trim() || null,
      };

      // Direct insert without requiring PostgREST embedded coach relation
      const { data: insertedRow, error: insertError } = await supabase
        .from('appointments')
        .insert(payload)
        .select('*')
        .single();

      if (insertError) {
        console.error(
          'Error inserting appointment:',
          insertError.message,
          insertError.code,
          insertError.details,
          insertError.hint
        );
        throw insertError;
      }

      const newApp: AppointmentItem = {
        ...insertedRow,
        coach: selectedCoach
          ? {
              id: selectedCoach.id,
              name: selectedCoach.name,
              surname: selectedCoach.surname,
              specialty: selectedCoach.specialty,
            }
          : undefined,
      };

      setAppointments((prev) => [newApp, ...prev.filter((a) => a.id !== newApp.id)]);
      setShowModal(false);
      setNotes('');
      setAppointmentDate('');
      feedback.success({ title: 'Başarılı', message: 'Randevu talebiniz başarıyla oluşturuldu.' });
    } catch (e: any) {
      console.error('Error creating appointment:', e);
      feedback.error({ title: 'Hata', message: e, fallbackMessage: 'Randevu kaydedilemedi.' });
    } finally {
      setSubmitting(false);
    }
  };

  const handleCancelAppointment = async (appointmentId: string) => {
    const confirmed = await feedback.destructive({
      title: 'Randevuyu İptal Et',
      message: 'Bu randevuyu iptal etmek istediğinize emin misiniz?',
      confirmText: 'İptal Et',
      cancelText: 'Vazgeç',
    });

    if (!confirmed) return;

    try {
      const { error } = await supabase
        .from('appointments')
        .update({ status: 'cancelled' })
        .eq('id', appointmentId);

      if (error) throw error;

      setAppointments((prev) =>
        prev.map((a) => (a.id === appointmentId ? { ...a, status: 'cancelled' } : a))
      );
      feedback.success({ title: 'Bilgi', message: 'Randevu iptal edildi.' });
    } catch (e: any) {
      console.error('Error cancelling appointment:', e);
      feedback.error({ title: 'Hata', message: e, fallbackMessage: 'Randevu iptal edilemedi.' });
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'confirmed':
        return { label: 'Onaylandı', color: '#10B981', bg: 'rgba(16, 185, 129, 0.1)' };
      case 'completed':
        return { label: 'Tamamlandı', color: '#3B82F6', bg: 'rgba(59, 130, 246, 0.1)' };
      case 'cancelled':
        return { label: 'İptal Edildi', color: '#EF4444', bg: 'rgba(239, 68, 68, 0.1)' };
      default:
        return { label: 'Onay Bekliyor', color: '#F59E0B', bg: 'rgba(245, 158, 11, 0.1)' };
    }
  };

  return (
    <ScreenContainer includeTopInset={true} includeBottomInset={true}>
      <View style={styles.container}>
        {/* Header */}
        <View style={styles.header}>
          <TouchableOpacity onPress={() => navigation?.goBack()} style={styles.backBtn}>
            <ArrowLeft size={22} color={Colors.textDark} />
          </TouchableOpacity>
          <Text style={styles.headerTitle}>Randevularım</Text>
          <TouchableOpacity onPress={() => setShowModal(true)} style={styles.addBtn}>
            <Plus size={20} color="#FFFFFF" />
          </TouchableOpacity>
        </View>

        {loading ? (
          <View style={styles.centerBox}>
            <ActivityIndicator size="large" color={Colors.primary} />
          </View>
        ) : (
          <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
            {appointments.length === 0 ? (
              <View style={styles.emptyState}>
                <Calendar size={48} color={Colors.textSecondaryDark} />
                <Text style={styles.emptyTitle}>Kayıtlı Randevunuz Yok</Text>
                <Text style={styles.emptySubtitle}>
                  Koçlarınızla bireysel antrenman veya danışmanlık randevusu oluşturabilirsiniz.
                </Text>
                <TouchableOpacity onPress={() => setShowModal(true)} style={styles.createBtn}>
                  <Text style={styles.createBtnText}>Yeni Randevu Al</Text>
                </TouchableOpacity>
              </View>
            ) : (
              appointments.map((item) => {
                const badge = getStatusBadge(item.status);
                const coachName = item.coach
                  ? `${item.coach.name} ${item.coach.surname || ''}`.trim()
                  : 'Salon Koçu';

                return (
                  <View key={item.id} style={styles.card}>
                    <View style={styles.cardHeader}>
                      <View style={{ flex: 1 }}>
                        <Text style={styles.coachName}>{coachName}</Text>
                        <Text style={styles.serviceText}>
                          {SERVICE_TYPES.find((s) => s.value === item.service_type)?.label || item.service_type}
                        </Text>
                      </View>
                      <View style={[styles.statusBadge, { backgroundColor: badge.bg }]}>
                        <Text style={[styles.statusText, { color: badge.color }]}>{badge.label}</Text>
                      </View>
                    </View>

                    <View style={styles.detailsRow}>
                      <View style={styles.detailItem}>
                        <Calendar size={15} color={Colors.textSecondaryDark} />
                        <Text style={styles.detailText}>{item.appointment_date}</Text>
                      </View>
                      <View style={styles.detailItem}>
                        <Clock size={15} color={Colors.textSecondaryDark} />
                        <Text style={styles.detailText}>
                          {item.start_time} - {item.end_time}
                        </Text>
                      </View>
                    </View>

                    {item.notes ? (
                      <Text style={styles.notesText}>Not: {item.notes}</Text>
                    ) : null}

                    {item.status === 'pending' || item.status === 'confirmed' ? (
                      <View style={styles.cardActions}>
                        <TouchableOpacity
                          onPress={() => handleCancelAppointment(item.id)}
                          style={styles.cancelBtn}
                        >
                          <Text style={styles.cancelBtnText}>Randevuyu İptal Et</Text>
                        </TouchableOpacity>
                      </View>
                    ) : null}
                  </View>
                );
              })
            )}
          </ScrollView>
        )}

        {/* Yeni Randevu Modal */}
        {showModal && (
          <View style={styles.modalOverlay}>
            <KeyboardAvoidingView
              behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
              style={{ width: '100%', justifyContent: 'center', alignItems: 'center' }}
            >
              <View style={styles.modalCard}>
                <ScrollView 
                  showsVerticalScrollIndicator={false} 
                  keyboardShouldPersistTaps="handled"
                  contentContainerStyle={{ paddingBottom: 24 }}
                  style={{ maxHeight: 520 }}
                >
                  <Text style={styles.modalTitle}>Yeni Randevu Talebi</Text>

                  {/* Koç Seçimi */}
                  <Text style={styles.inputLabel}>Koç Seçin</Text>
                  <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.coachSelectRow}>
                    {coaches.map((c) => (
                      <TouchableOpacity
                        key={c.id}
                        onPress={() => setSelectedCoachId(c.id)}
                        style={[
                          styles.coachChip,
                          selectedCoachId === c.id && styles.coachChipActive,
                        ]}
                      >
                        <Text
                          style={[
                            styles.coachChipText,
                            selectedCoachId === c.id && styles.coachChipTextActive,
                          ]}
                        >
                          {c.name} {c.surname || ''}
                        </Text>
                      </TouchableOpacity>
                    ))}
                  </ScrollView>

                  {/* Hizmet Türü */}
                  <Text style={styles.inputLabel}>Hizmet Türü</Text>
                  <View style={styles.serviceRow}>
                    {SERVICE_TYPES.map((st) => (
                      <TouchableOpacity
                        key={st.value}
                        onPress={() => setServiceType(st.value)}
                        style={[
                          styles.serviceChip,
                          serviceType === st.value && styles.serviceChipActive,
                        ]}
                      >
                        <Text
                          style={[
                            styles.serviceChipText,
                            serviceType === st.value && styles.serviceChipTextActive,
                          ]}
                        >
                          {st.label}
                        </Text>
                      </TouchableOpacity>
                    ))}
                  </View>

                  {/* Tarih Seçimi */}
                  <Text style={styles.inputLabel}>Tarih</Text>
                  <TouchableOpacity
                    onPress={() => setShowDatePicker(true)}
                    style={styles.dateSelector}
                  >
                    <Calendar size={18} color={Colors.primary} />
                    <Text style={{ color: appointmentDate ? Colors.textDark : Colors.textSecondaryDark }}>
                      {appointmentDate || 'Tarih Seçin (YYYY-AA-GG)'}
                    </Text>
                  </TouchableOpacity>

                  {/* Saatler */}
                  <View style={{ flexDirection: 'row', gap: 12 }}>
                    <View style={{ flex: 1 }}>
                      <Text style={styles.inputLabel}>Başlangıç Saati</Text>
                      <TouchableOpacity
                        style={styles.timeInputContainer}
                        onPress={() => setShowTimePicker('slot')}
                        activeOpacity={0.8}
                      >
                        <TextInput
                          style={styles.timeTextInput}
                          value={startTime}
                          onChangeText={setStartTime}
                          placeholder="10:00"
                          placeholderTextColor={Colors.textSecondaryDark}
                        />
                        <TouchableOpacity
                          style={styles.timeIconBtn}
                          onPress={() => setShowTimePicker('slot')}
                        >
                          <Clock size={16} color={Colors.primary} />
                        </TouchableOpacity>
                      </TouchableOpacity>
                    </View>
                    <View style={{ flex: 1 }}>
                      <Text style={styles.inputLabel}>Bitiş Saati</Text>
                      <TouchableOpacity
                        style={styles.timeInputContainer}
                        onPress={() => setShowTimePicker('slot')}
                        activeOpacity={0.8}
                      >
                        <TextInput
                          style={styles.timeTextInput}
                          value={endTime}
                          onChangeText={setEndTime}
                          placeholder="11:00"
                          placeholderTextColor={Colors.textSecondaryDark}
                        />
                        <TouchableOpacity
                          style={styles.timeIconBtn}
                          onPress={() => setShowTimePicker('slot')}
                        >
                          <Clock size={16} color={Colors.primary} />
                        </TouchableOpacity>
                      </TouchableOpacity>
                    </View>
                  </View>

                  {/* Notlar */}
                  <Text style={styles.inputLabel}>Notlar (İsteğe Bağlı)</Text>
                  <TextInput
                    style={[styles.input, { height: 60 }]}
                    value={notes}
                    onChangeText={setNotes}
                    placeholder="Örn: Sırt ağrım var, dikkat edelim."
                    placeholderTextColor={Colors.textSecondaryDark}
                    multiline
                  />

                  <View style={styles.modalButtons}>
                    <TouchableOpacity
                      onPress={() => setShowModal(false)}
                      style={styles.modalCancelBtn}
                      disabled={submitting}
                    >
                      <Text style={styles.modalCancelText}>Vazgeç</Text>
                    </TouchableOpacity>
                    <TouchableOpacity
                      onPress={handleCreateAppointment}
                      style={styles.modalSubmitBtn}
                      disabled={submitting}
                    >
                      {submitting ? (
                        <ActivityIndicator size="small" color="#FFFFFF" />
                      ) : (
                        <Text style={styles.modalSubmitText}>Randevu Oluştur</Text>
                      )}
                    </TouchableOpacity>
                  </View>
                </ScrollView>
              </View>
            </KeyboardAvoidingView>
          </View>
        )}

        <DateTimePickerModal
          visible={showDatePicker}
          mode="date"
          initialValue={appointmentDate}
          onConfirm={(val) => {
            setAppointmentDate(val);
            setShowDatePicker(false);
          }}
          onCancel={() => setShowDatePicker(false)}
        />

        <DateTimePickerModal
          visible={showTimePicker !== null}
          mode="time"
          title="Randevu Saati Seçin"
          initialValue={`${startTime} - ${endTime}`}
          onConfirm={(val) => {
            if (val && val.includes('-')) {
              const parts = val.split('-').map((s) => s.trim());
              if (parts[0]) setStartTime(parts[0]);
              if (parts[1]) setEndTime(parts[1]);
            }
            setShowTimePicker(null);
          }}
          onCancel={() => setShowTimePicker(null)}
        />
      </View>
    </ScreenContainer>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.backgroundDark,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: Colors.borderDark,
  },
  backBtn: {
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
    gap: 12,
  },
  centerBox: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  card: {
    backgroundColor: Colors.cardDark,
    borderRadius: 14,
    padding: 16,
    borderWidth: 1,
    borderColor: Colors.borderDark,
    gap: 10,
  },
  cardHeader: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
  },
  coachName: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.textDark,
  },
  serviceText: {
    fontSize: 13,
    color: Colors.primary,
    marginTop: 2,
    fontWeight: '500',
  },
  statusBadge: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 8,
  },
  statusText: {
    fontSize: 12,
    fontWeight: '600',
  },
  detailsRow: {
    flexDirection: 'row',
    gap: 16,
    paddingVertical: 4,
  },
  detailItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  detailText: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
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
    borderTopColor: Colors.borderDark,
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
    fontWeight: '600',
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
  modalOverlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0,0,0,0.7)',
    justifyContent: 'center',
    padding: 16,
    zIndex: 100,
  },
  modalCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 20,
    borderWidth: 1,
    borderColor: Colors.borderDark,
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
  coachSelectRow: {
    flexDirection: 'row',
    marginBottom: 4,
  },
  coachChip: {
    backgroundColor: Colors.backgroundDark,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 8,
    marginRight: 8,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  coachChipActive: {
    borderColor: Colors.primary,
    backgroundColor: 'rgba(255, 96, 71, 0.15)',
  },
  coachChipText: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
  },
  coachChipTextActive: {
    color: Colors.primary,
    fontWeight: '600',
  },
  serviceRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
  },
  serviceChip: {
    backgroundColor: Colors.backgroundDark,
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  serviceChipActive: {
    borderColor: Colors.primary,
    backgroundColor: 'rgba(255, 96, 71, 0.15)',
  },
  serviceChipText: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
  },
  serviceChipTextActive: {
    color: Colors.primary,
    fontWeight: '600',
  },
  dateSelector: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    backgroundColor: Colors.backgroundDark,
    borderRadius: 8,
    padding: 12,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  input: {
    backgroundColor: Colors.backgroundDark,
    borderRadius: 8,
    padding: 10,
    color: Colors.textDark,
    borderWidth: 1,
    borderColor: Colors.borderDark,
    fontSize: 13,
  },
  timeInputContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.backgroundDark,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: Colors.borderDark,
    overflow: 'hidden',
  },
  timeTextInput: {
    flex: 1,
    paddingHorizontal: 10,
    paddingVertical: 8,
    color: Colors.textDark,
    fontSize: 13,
  },
  timeIconBtn: {
    paddingHorizontal: 10,
    paddingVertical: 10,
    justifyContent: 'center',
    alignItems: 'center',
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
    borderColor: Colors.borderDark,
    alignItems: 'center',
    backgroundColor: Colors.backgroundDark,
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
