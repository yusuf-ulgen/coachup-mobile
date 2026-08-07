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
  Alert,
  ScrollView,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ArrowLeft, Plus, Calendar, Clock, X } from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { useAuth } from '../../context/AuthContext';
import { supabase } from '../../services/supabaseClient';

export const AppointmentsScreen = ({ navigation }: any) => {
  const { session } = useAuth();
  const [activeTab, setActiveTab] = useState<'upcoming' | 'past'>('upcoming');
  const [appointments, setAppointments] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [coaches, setCoaches] = useState<any[]>([]);

  // Modal State
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [selectedCoach, setSelectedCoach] = useState('');
  const [selectedType, setSelectedType] = useState('Kişisel Antrenman');
  const [dateStr, setDateStr] = useState('');
  const [timeStr, setTimeStr] = useState('');
  const [notes, setNotes] = useState('');
  const [saving, setSaving] = useState(false);

  const appointmentTypes = ['Kişisel Antrenman', 'Danışmanlık', 'Değerlendirme'];

  useEffect(() => {
    fetchAppointments();
    fetchCoaches();
  }, []);

  const fetchAppointments = async () => {
    if (!session?.user?.id) return;
    try {
      setLoading(true);
      const { data, error } = await supabase
        .from('appointments')
        .select(`
          *,
          coach:profiles!appointments_coach_id_fkey(full_name)
        `)
        .eq('user_id', session.user.id)
        .order('date', { ascending: false });

      if (error) {
          console.error(error);
          return;
      }
      setAppointments(data || []);
    } catch (error) {
      console.error('Randevular alınırken hata:', error);
    } finally {
      setLoading(false);
    }
  };

  const fetchCoaches = async () => {
    try {
      const { data, error } = await supabase
        .from('profiles')
        .select('id, full_name')
        .eq('role', 'coach');
      if (error) {
          console.error(error);
          return;
      }
      setCoaches(data || []);
      if (data && data.length > 0) {
        setSelectedCoach(data[0].id);
      }
    } catch (error) {
      console.error('Koçlar alınırken hata:', error);
    }
  };

  const handleSaveAppointment = async () => {
    if (!selectedCoach || !dateStr || !timeStr) {
      Alert.alert('Hata', 'Lütfen koç, tarih ve saat alanlarını doldurun.');
      return;
    }
    try {
      setSaving(true);
      const { error } = await supabase.from('appointments').insert({
        user_id: session?.user?.id,
        coach_id: selectedCoach,
        type: selectedType,
        date: dateStr,
        time: timeStr,
        notes: notes,
        status: 'pending',
      });
      if (error) throw error;
      
      setIsModalVisible(false);
      resetForm();
      fetchAppointments();
      Alert.alert('Başarılı', 'Randevu talebiniz oluşturuldu.');
    } catch (error) {
      Alert.alert('Hata', 'Randevu oluşturulamadı.');
      console.error(error);
    } finally {
      setSaving(false);
    }
  };

  const handleCancelAppointment = (id: string) => {
    Alert.alert('Randevuyu İptal Et', 'Bu randevuyu iptal etmek istediğinize emin misiniz?', [
      { text: 'Vazgeç', style: 'cancel' },
      {
        text: 'İptal Et',
        style: 'destructive',
        onPress: async () => {
          try {
            const { error } = await supabase
              .from('appointments')
              .update({ status: 'cancelled' })
              .eq('id', id);
            if (error) throw error;
            fetchAppointments();
          } catch (error) {
            Alert.alert('Hata', 'Randevu iptal edilemedi.');
          }
        },
      },
    ]);
  };

  const resetForm = () => {
    setDateStr('');
    setTimeStr('');
    setNotes('');
    setSelectedType(appointmentTypes[0]);
    if (coaches.length > 0) setSelectedCoach(coaches[0].id);
  };

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
          <Text style={styles.coachName}>{item.coach?.full_name || 'Bilinmeyen Koç'}</Text>
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
              <View style={styles.typeSelector}>
                {coaches.map(c => (
                  <TouchableOpacity
                    key={c.id}
                    style={[styles.typeChip, selectedCoach === c.id && styles.activeTypeChip]}
                    onPress={() => setSelectedCoach(c.id)}
                  >
                    <Text style={[styles.typeChipText, selectedCoach === c.id && styles.activeTypeChipText]}>
                      {c.full_name}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>

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

              <Text style={styles.label}>Tarih (YYYY-MM-DD)</Text>
              <TextInput
                style={styles.input}
                placeholder="Örn: 2026-08-15"
                placeholderTextColor={Colors.textSecondaryDark}
                value={dateStr}
                onChangeText={setDateStr}
              />

              <Text style={styles.label}>Saat Aralığı (SS:DD)</Text>
              <TextInput
                style={styles.input}
                placeholder="Örn: 09:00 - 10:00"
                placeholderTextColor={Colors.textSecondaryDark}
                value={timeStr}
                onChangeText={setTimeStr}
              />

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
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
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
    paddingBottom: 40,
  },
  label: {
    color: Colors.allWhite,
    fontSize: 14,
    fontWeight: '600',
    marginBottom: 8,
    marginTop: 16,
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
