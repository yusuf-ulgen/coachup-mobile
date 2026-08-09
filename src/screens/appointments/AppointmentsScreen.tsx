import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  FlatList,
  Modal,
  TextInput,
  ActivityIndicator,
  ScrollView,
} from 'react-native';
import { feedback } from '../../services/feedbackService';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ArrowLeft, Plus, Calendar, Clock, X, ChevronDown, Check } from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { useAuth } from '../../context/AuthContext';
import { supabase } from '../../services/supabaseClient';

import AsyncStorage from '@react-native-async-storage/async-storage';
import { CoachService } from '../../services/coachService';
import { DateTimePickerModal } from '../../components/DateTimePickerModal';

export const AppointmentsScreen = ({ route, navigation }: any) => {
  const { session } = useAuth();
  const [activeTab, setActiveTab] = useState<'upcoming' | 'past'>('upcoming');
  const [appointments, setAppointments] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [coaches, setCoaches] = useState<any[]>([]);

  // Modal State
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [isCoachDropdownOpen, setIsCoachDropdownOpen] = useState(false);
  const [selectedCoach, setSelectedCoach] = useState('');
  const [selectedType, setSelectedType] = useState('Kişisel Antrenman');
  const [dateStr, setDateStr] = useState('');
  const [timeStr, setTimeStr] = useState('');
  const [showDatePicker, setShowDatePicker] = useState(false);
  const [showTimePicker, setShowTimePicker] = useState(false);
  const [notes, setNotes] = useState('');
  const [saving, setSaving] = useState(false);

  const appointmentTypes = ['Kişisel Antrenman', 'Danışmanlık', 'Değerlendirme'];

  const getStorageKey = () => `@user_appointments_${session?.user?.id || 'guest'}`;

  const loadLocalAppointments = async () => {
    try {
      const json = await AsyncStorage.getItem(getStorageKey());
      return json ? JSON.parse(json) : [];
    } catch {
      return [];
    }
  };

  const saveLocalAppointments = async (list: any[]) => {
    try {
      await AsyncStorage.setItem(getStorageKey(), JSON.stringify(list));
    } catch (e) {
      console.error('Error saving local appointments:', e);
    }
  };

  useEffect(() => {
    fetchAppointments();
    fetchCoaches();

    if (route?.params?.coachId || route?.params?.openModal) {
      if (route?.params?.coachId) {
        setSelectedCoach(route.params.coachId);
      }
      setIsModalVisible(true);
    }
  }, [route?.params]);

  const fetchAppointments = async () => {
    setLoading(true);
    const local = await loadLocalAppointments();

    if (!session?.user?.id) {
      setAppointments(local);
      setLoading(false);
      return;
    }

    try {
      const { data, error } = await supabase
        .from('appointments')
        .select('*')
        .eq('user_id', session.user.id)
        .order('date', { ascending: false });

      const allCoaches = await CoachService.fetchCoaches();
      const coachMap = new Map(allCoaches.map((c) => [c.id, `${c.name || ''} ${c.surname || ''}`.trim()]));

      let remoteEnriched: any[] = [];
      if (!error && data) {
        remoteEnriched = data.map((app) => ({
          ...app,
          coachName: app.coach_name || coachMap.get(app.coach_id) || 'Koç',
          time: app.time || app.start_time || (app.date && app.date.includes(' ') ? app.date.split(' ')[1] : ''),
        }));
      }

      // Merge local appointments if not yet in remote
      const remoteIds = new Set(remoteEnriched.map((a) => a.id));
      const localOnly = local.filter((l: any) => !remoteIds.has(l.id));
      const combined = [...remoteEnriched, ...localOnly];

      setAppointments(combined);
      saveLocalAppointments(combined);
    } catch (error) {
      console.error('Randevular alınırken hata:', error);
      setAppointments(local);
    } finally {
      setLoading(false);
    }
  };

  const fetchCoaches = async () => {
    try {
      const data = await CoachService.fetchCoaches();
      setCoaches(data || []);
      if (data && data.length > 0 && !selectedCoach) {
        setSelectedCoach(data[0].id);
      }
    } catch (error) {
      console.error('Koçlar alınırken hata:', error);
    }
  };

  const handleSaveAppointment = async () => {
    if (!selectedCoach || !dateStr || !timeStr) {
      feedback.warning({
        title: 'Hata',
        message: 'Lütfen koç, tarih ve saat alanlarını doldurun.',
      });
      return;
    }
    try {
      setSaving(true);
      const newApp = {
        id: 'app_' + Date.now(),
        user_id: session?.user?.id || 'guest',
        coach_id: selectedCoach,
        type: selectedType,
        date: dateStr,
        time: timeStr,
        notes: notes,
        status: 'pending',
        created_at: new Date().toISOString(),
      };

      // 1. Try standard insert first
      let insertError: any = null;
      if (session?.user?.id) {
        const res = await supabase.from('appointments').insert({
          user_id: session.user.id,
          coach_id: selectedCoach,
          type: selectedType,
          date: dateStr,
          time: timeStr,
          notes: notes,
          status: 'pending',
        });
        insertError = res.error;

        // If error is PGRST204 ('time' column missing), try fallback insert without 'time' column
        if (insertError && (insertError.code === 'PGRST204' || insertError.message?.includes('time'))) {
          console.log('Fallback insert: time column missing in appointments schema cache.');
          const fallbackNotes = notes ? `${notes} (Saat: ${timeStr})` : `Saat: ${timeStr}`;
          const res2 = await supabase.from('appointments').insert({
            user_id: session.user.id,
            coach_id: selectedCoach,
            type: selectedType,
            date: dateStr,
            notes: fallbackNotes,
            status: 'pending',
          });
          insertError = res2.error;
        }
      }

      // 2. Save locally so appointment is visible immediately regardless of DB schema state
      const currentList = await loadLocalAppointments();
      const updatedList = [newApp, ...currentList];
      await saveLocalAppointments(updatedList);
      setAppointments((prev) => [newApp, ...prev]);

      setIsModalVisible(false);
      resetForm();
      fetchAppointments();
      feedback.success({
        title: 'Başarılı',
        message: 'Randevu talebiniz oluşturuldu.',
      });
    } catch (error) {
      console.error('Save appointment error:', error);
      feedback.error({
        title: 'Hata',
        message: error,
        fallbackMessage: 'Randevu oluşturulamadı.',
      });
    } finally {
      setSaving(false);
    }
  };

  const handleCancelAppointment = async (id: string) => {
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
        .eq('id', id);
      if (error) throw error;
      fetchAppointments();
      feedback.toast('Randevu iptal edildi.', 'info');
    } catch (error) {
      feedback.error({
        title: 'Hata',
        message: error,
        fallbackMessage: 'Randevu iptal edilemedi.',
      });
    }
  };

  const resetForm = () => {
    setDateStr('');
    setTimeStr('');
    setNotes('');
    setSelectedType(appointmentTypes[0]);
    if (coaches.length > 0) setSelectedCoach(coaches[0].id);
  };

  const selectedCoachObj = coaches.find((c) => c.id === selectedCoach);
  const today = new Date().toISOString().split('T')[0];

  const filteredAppointments = appointments.filter((app) => {
    if (activeTab === 'upcoming') {
      return app.date >= today && app.status !== 'cancelled';
    } else {
      return app.date < today || app.status === 'cancelled';
    }
  });

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'confirmed':
        return { text: 'Onaylandı', color: Colors.success };
      case 'cancelled':
        return { text: 'İptal', color: Colors.error };
      case 'pending':
      default:
        return { text: 'Bekliyor', color: Colors.warning };
    }
  };

  const renderItem = ({ item }: { item: any }) => {
    const badge = getStatusBadge(item.status);
    return (
      <View style={styles.card}>
        <View style={styles.cardHeader}>
          <Text style={styles.coachName}>{item.coachName || item.coach?.name || 'Bilinmeyen Koç'}</Text>
          <View style={[styles.badge, { backgroundColor: badge.color + '20' }]}>
            <Text style={[styles.badgeText, { color: badge.color }]}>{badge.text}</Text>
          </View>
        </View>
        <View style={styles.cardBody}>
          <Text style={styles.typeText}>{item.type}</Text>
          <View style={styles.dateRow}>
            <Calendar size={16} color={Colors.textSecondaryDark} />
            <Text style={styles.dateText}>{item.date}</Text>
            <Clock size={16} color={Colors.textSecondaryDark} style={{ marginLeft: 12 }} />
            <Text style={styles.dateText}>{item.time}</Text>
          </View>
          {item.notes ? (
            <Text style={styles.notesText} numberOfLines={2}>{item.notes}</Text>
          ) : null}
        </View>
        {item.status === 'pending' && activeTab === 'upcoming' && (
          <View style={styles.cardFooter}>
            <TouchableOpacity 
              style={styles.cancelBtn}
              onPress={() => handleCancelAppointment(item.id)}
            >
              <Text style={styles.cancelBtnText}>İptal Et</Text>
            </TouchableOpacity>
          </View>
        )}
      </View>
    );
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtn}>
          <ArrowLeft color={Colors.allWhite} size={24} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Randevularım</Text>
        <TouchableOpacity onPress={() => setIsModalVisible(true)} style={styles.addBtn}>
          <Plus color={Colors.allWhite} size={24} />
        </TouchableOpacity>
      </View>

      <View style={styles.tabsContainer}>
        <TouchableOpacity
          style={[styles.tab, activeTab === 'upcoming' && styles.activeTab]}
          onPress={() => setActiveTab('upcoming')}
        >
          <Text style={[styles.tabText, activeTab === 'upcoming' && styles.activeTabText]}>
            Yaklaşan
          </Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.tab, activeTab === 'past' && styles.activeTab]}
          onPress={() => setActiveTab('past')}
        >
          <Text style={[styles.tabText, activeTab === 'past' && styles.activeTabText]}>
            Geçmiş
          </Text>
        </TouchableOpacity>
      </View>

      {loading ? (
        <ActivityIndicator size="large" color={Colors.primary} style={{ marginTop: 40 }} />
      ) : (
        <FlatList
          data={filteredAppointments}
          keyExtractor={(item) => item.id.toString()}
          renderItem={renderItem}
          contentContainerStyle={styles.listContent}
          ListEmptyComponent={
            <Text style={styles.emptyText}>Bu kategoride randevu bulunamadı.</Text>
          }
        />
      )}

      <Modal visible={isModalVisible} animationType="slide" transparent>
        <View style={styles.modalContainer}>
          <View style={styles.modalContent}>
            <View style={styles.modalHeader}>
              <Text style={styles.modalTitle}>Yeni Randevu Al</Text>
              <TouchableOpacity onPress={() => { setIsModalVisible(false); resetForm(); }}>
                <X color={Colors.allWhite} size={24} />
              </TouchableOpacity>
            </View>

            <ScrollView contentContainerStyle={styles.formContainer}>
              <Text style={styles.label}>Koç Seçimi</Text>
              <TouchableOpacity
                style={styles.dropdownTrigger}
                onPress={() => setIsCoachDropdownOpen(true)}
                activeOpacity={0.8}
              >
                <Text style={styles.dropdownTriggerText}>
                  {selectedCoachObj
                    ? `${selectedCoachObj.name || ''} ${selectedCoachObj.surname || ''}`.trim()
                    : 'Koç Seçiniz'}
                </Text>
                <ChevronDown size={20} color={Colors.textSecondaryDark} />
              </TouchableOpacity>

              <Text style={styles.label}>Randevu Türü</Text>
              <View style={styles.typeSelector}>
                {appointmentTypes.map(t => (
                  <TouchableOpacity
                    key={t}
                    style={[styles.typeChip, selectedType === t && styles.activeTypeChip]}
                    onPress={() => setSelectedType(t)}
                  >
                    <Text style={[styles.typeChipText, selectedType === t && styles.activeTypeChipText]}>
                      {t}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>

              <Text style={styles.label}>Tarih</Text>
              <TouchableOpacity
                onPress={() => setShowDatePicker(true)}
                style={[styles.input, { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }]}
              >
                <Text style={{ color: dateStr ? Colors.textDark : Colors.textSecondaryDark }}>
                  {dateStr || 'Tarih Seçin (YYYY-MM-DD)'}
                </Text>
                <Calendar size={18} color={Colors.primary} />
              </TouchableOpacity>

              <Text style={styles.label}>Saat Aralığı</Text>
              <TouchableOpacity
                onPress={() => setShowTimePicker(true)}
                style={[styles.input, { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }]}
              >
                <Text style={{ color: timeStr ? Colors.textDark : Colors.textSecondaryDark }}>
                  {timeStr || 'Saat Seçin (Örn: 10:00 - 11:00)'}
                </Text>
                <Clock size={18} color={Colors.primary} />
              </TouchableOpacity>

              <Text style={styles.label}>Not (İsteğe bağlı)</Text>
              <TextInput
                style={[styles.input, styles.textArea]}
                placeholder="Randevu ile ilgili notunuz..."
                placeholderTextColor={Colors.textSecondaryDark}
                value={notes}
                onChangeText={setNotes}
                multiline
                textAlignVertical="top"
              />

              {/* Date & Time Picker Modals */}
              <DateTimePickerModal
                visible={showDatePicker}
                mode="date"
                title="Randevu Tarihi Seç"
                initialValue={dateStr}
                onConfirm={(d) => {
                  setDateStr(d);
                  setShowDatePicker(false);
                }}
                onCancel={() => setShowDatePicker(false)}
              />

              <DateTimePickerModal
                visible={showTimePicker}
                mode="time"
                title="Randevu Saati Seç"
                initialValue={timeStr}
                onConfirm={(t) => {
                  setTimeStr(t);
                  setShowTimePicker(false);
                }}
                onCancel={() => setShowTimePicker(false)}
              />

              <TouchableOpacity 
                style={styles.saveBtn} 
                onPress={handleSaveAppointment}
                disabled={saving}
              >
                {saving ? (
                  <ActivityIndicator color={Colors.allWhite} />
                ) : (
                  <Text style={styles.saveBtnText}>Kaydet</Text>
                )}
              </TouchableOpacity>
            </ScrollView>
          </View>
        </View>
      </Modal>

      {/* Koç Seçim Dropdown Modalı */}
      <Modal visible={isCoachDropdownOpen} animationType="fade" transparent>
        <TouchableOpacity 
          style={styles.dropdownOverlay} 
          activeOpacity={1} 
          onPress={() => setIsCoachDropdownOpen(false)}
        >
          <View style={styles.dropdownModalBox}>
            <View style={styles.dropdownHeader}>
              <Text style={styles.dropdownHeaderTitle}>Koç Seçin</Text>
              <TouchableOpacity onPress={() => setIsCoachDropdownOpen(false)}>
                <X color={Colors.allWhite} size={20} />
              </TouchableOpacity>
            </View>
            <ScrollView style={{ maxHeight: 300 }}>
              {coaches.length === 0 ? (
                <Text style={[styles.emptyText, { marginVertical: 20 }]}>Sistemde kayıtlı koç bulunamadı.</Text>
              ) : (
                coaches.map((c) => {
                  const isSelected = c.id === selectedCoach;
                  const fullName = `${c.name || ''} ${c.surname || ''}`.trim() || 'İsimsiz Koç';
                  return (
                    <TouchableOpacity
                      key={c.id}
                      style={[styles.dropdownItemRow, isSelected && styles.dropdownItemRowSelected]}
                      onPress={() => {
                        setSelectedCoach(c.id);
                        setIsCoachDropdownOpen(false);
                      }}
                      activeOpacity={0.7}
                    >
                      <View style={{ flex: 1 }}>
                        <Text style={[styles.dropdownItemTitle, isSelected && { color: Colors.primary }]}>
                          {fullName}
                        </Text>
                        {c.speciality || c.specialization ? (
                          <Text style={styles.dropdownItemSubtitle}>
                            {c.speciality || c.specialization}
                          </Text>
                        ) : null}
                      </View>
                      {isSelected && <Check size={18} color={Colors.primary} />}
                    </TouchableOpacity>
                  );
                })
              )}
            </ScrollView>
          </View>
        </TouchableOpacity>
      </Modal>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Colors.backgroundDark },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingVertical: 16,
  },
  backBtn: { padding: 8, marginLeft: -8 },
  addBtn: { padding: 8, marginRight: -8 },
  headerTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: Colors.allWhite,
  },
  tabsContainer: {
    flexDirection: 'row',
    marginHorizontal: 20,
    marginBottom: 16,
    backgroundColor: Colors.cardDark,
    borderRadius: 8,
    padding: 4,
  },
  tab: {
    flex: 1,
    paddingVertical: 10,
    alignItems: 'center',
    borderRadius: 6,
  },
  activeTab: {
    backgroundColor: Colors.backgroundDark,
  },
  tabText: {
    color: Colors.textSecondaryDark,
    fontWeight: '600',
    fontSize: 14,
  },
  activeTabText: {
    color: Colors.primary,
  },
  listContent: {
    paddingHorizontal: 20,
    paddingBottom: 24,
  },
  emptyText: {
    color: Colors.textSecondaryDark,
    textAlign: 'center',
    marginTop: 40,
    fontSize: 16,
  },
  card: {
    backgroundColor: Colors.cardDark,
    borderRadius: 12,
    padding: 16,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  coachName: {
    fontSize: 16,
    fontWeight: 'bold',
    color: Colors.allWhite,
  },
  badge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
  },
  badgeText: {
    fontSize: 12,
    fontWeight: 'bold',
  },
  cardBody: {
    gap: 8,
  },
  typeText: {
    color: Colors.allWhite,
    fontSize: 14,
  },
  dateRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  dateText: {
    color: Colors.textSecondaryDark,
    fontSize: 13,
    marginLeft: 6,
  },
  notesText: {
    color: Colors.textSecondaryDark,
    fontSize: 13,
    fontStyle: 'italic',
    marginTop: 4,
  },
  cardFooter: {
    marginTop: 12,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: Colors.borderDark,
    alignItems: 'flex-end',
  },
  cancelBtn: {
    paddingVertical: 6,
    paddingHorizontal: 12,
  },
  cancelBtnText: {
    color: Colors.error,
    fontSize: 14,
    fontWeight: '600',
  },
  modalContainer: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.6)',
    justifyContent: 'flex-end',
  },
  modalContent: {
    backgroundColor: Colors.backgroundDark,
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    maxHeight: '90%',
  },
  modalHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 20,
    borderBottomWidth: 1,
    borderBottomColor: Colors.borderDark,
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: Colors.allWhite,
  },
  formContainer: {
    padding: 20,
    gap: 16,
  },
  label: {
    fontSize: 14,
    fontWeight: '600',
    color: Colors.allWhite,
    marginBottom: -8,
  },
  dropdownTrigger: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: Colors.cardDark,
    borderWidth: 1,
    borderColor: Colors.borderDark,
    borderRadius: 8,
    padding: 14,
  },
  dropdownTriggerText: {
    color: Colors.allWhite,
    fontSize: 15,
    fontWeight: '500',
  },
  dropdownOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.7)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  dropdownModalBox: {
    width: '100%',
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 16,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  dropdownHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingBottom: 12,
    marginBottom: 8,
    borderBottomWidth: 1,
    borderBottomColor: Colors.borderDark,
  },
  dropdownHeaderTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: Colors.allWhite,
  },
  dropdownItemRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 12,
    paddingHorizontal: 12,
    borderRadius: 8,
    marginBottom: 4,
  },
  dropdownItemRowSelected: {
    backgroundColor: Colors.backgroundDark,
  },
  dropdownItemTitle: {
    fontSize: 15,
    fontWeight: '600',
    color: Colors.allWhite,
  },
  dropdownItemSubtitle: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
    marginTop: 2,
  },
  input: {
    backgroundColor: Colors.cardDark,
    borderWidth: 1,
    borderColor: Colors.borderDark,
    borderRadius: 8,
    padding: 12,
    color: Colors.allWhite,
    fontSize: 15,
  },
  textArea: {
    height: 100,
  },
  typeSelector: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  typeChip: {
    backgroundColor: Colors.cardDark,
    borderWidth: 1,
    borderColor: Colors.borderDark,
    borderRadius: 20,
    paddingVertical: 8,
    paddingHorizontal: 16,
  },
  activeTypeChip: {
    backgroundColor: Colors.primary,
    borderColor: Colors.primary,
  },
  typeChipText: {
    color: Colors.textSecondaryDark,
    fontSize: 13,
    fontWeight: '500',
  },
  activeTypeChipText: {
    color: Colors.allWhite,
  },
  saveBtn: {
    backgroundColor: Colors.primary,
    borderRadius: 8,
    padding: 16,
    alignItems: 'center',
    marginTop: 32,
  },
  saveBtnText: {
    color: Colors.allWhite,
    fontSize: 16,
    fontWeight: 'bold',
  },
});
