import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  FlatList,
  ActivityIndicator,
} from 'react-native';
import { feedback } from '../../services/feedbackService';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {
  ArrowLeft,
  Calendar,
  CreditCard,
  TrendingUp,
  Award,
  Apple,
  MessageSquare,
  Settings as SettingsIcon,
  Shield,
  Clock,
  Dumbbell,
  CheckCircle2,
  Plus,
  X,
  Flame,
} from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { AuthService } from '../../services/authService';
import { UserService } from '../../services/userService';
import { supabase } from '../../services/supabaseClient';
import { DateTimePickerModal } from '../../components/DateTimePickerModal';
import { SmoothModal } from '../../components/motion/SmoothModal';

import { useSafeAreaInsets } from 'react-native-safe-area-context';

interface ScreenProps {
  navigation?: any;
}

// ---------------------------------------------------------------------------
// 1. Membership Screen (Salonlarım & Üyeliklerim)
// ---------------------------------------------------------------------------
export const MembershipScreen: React.FC<ScreenProps> = ({ navigation }) => {
  const insets = useSafeAreaInsets();
  const [profile, setProfile] = useState<any>(null);
  const [plans, setPlans] = useState<any[]>([]);
  const [activeMembership, setActiveMembership] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [requestLoading, setRequestLoading] = useState(false);

  useEffect(() => {
    const loadData = async () => {
      setLoading(true);
      const user = await AuthService.getCurrentUser();
      if (!user) {
        setLoading(false);
        return;
      }
      const currentProfile = await AuthService.getCurrentProfile();
      setProfile(currentProfile);
      
      const { data: membershipsData } = await supabase
        .from('user_memberships')
        .select(`
          *,
          membership_plans:plan_id (*)
        `)
        .eq('user_id', user.id)
        .eq('status', 'active')
        .order('end_date', { ascending: false })
        .limit(1)
        .single();
        
      if (membershipsData) {
        setActiveMembership(membershipsData);
      }
      
      let plansQuery = supabase.from('membership_plans').select('*');
      if (currentProfile?.gym_id) {
        plansQuery = plansQuery.eq('gym_id', currentProfile.gym_id);
      }
      const { data: plansData } = await plansQuery.order('price', { ascending: true });
      
      setPlans(plansData || []);
      setLoading(false);
    };

    loadData();
  }, []);

  const handleSelectPlan = async (plan: any) => {
    const confirmed = await feedback.confirm({
      title: 'Plan Seç / Yenile',
      message: `${plan.name} paketini seçmek istediğinize emin misiniz?`,
      confirmText: 'Onayla',
      cancelText: 'İptal',
    });

    if (!confirmed) return;

    setRequestLoading(true);
    try {
      const user = await AuthService.getCurrentUser();
      if (user) {
        const { error } = await supabase.from('membership_requests').insert([
          {
            user_id: user.id,
            plan_id: plan.id,
            status: 'pending',
          },
        ]);
        if (!error) {
          feedback.success({ title: 'Başarılı', message: 'Üyelik talebiniz yöneticiye iletildi.' });
        } else {
          feedback.error({ title: 'Hata', message: error, fallbackMessage: 'Talep iletilirken bir sorun oluştu.' });
        }
      }
    } catch (e: any) {
      feedback.error({ title: 'Hata', message: e, fallbackMessage: 'Talep iletilirken bir sorun oluştu.' });
    } finally {
      setRequestLoading(false);
    }
  };

  return (
    <View style={styles.container}>
      <View style={[styles.header, { paddingTop: Math.max(16, insets.top + 8) }]}>
        <TouchableOpacity onPress={() => navigation?.goBack()} style={styles.backBtn}>
          <ArrowLeft size={22} color={Colors.textDark} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Salonlarım & Üyeliklerim</Text>
      </View>
      {loading ? (
        <ActivityIndicator size="large" color={Colors.primary} style={{ marginTop: 40 }} />
      ) : (
        <ScrollView contentContainerStyle={[styles.content, { paddingBottom: 24 + insets.bottom }]}>
          <View style={styles.card}>
            <Text style={styles.cardHeader}>Aktif Salon Üyeliği</Text>
            <Text style={styles.cardTitle}>{profile?.gym_name || 'Kayıtlı Salon Yok'}</Text>
            {activeMembership ? (
              <>
                <View style={styles.badgeRow}>
                  <View style={styles.activeBadge}>
                    <Text style={styles.activeBadgeText}>Aktif Üyelik</Text>
                  </View>
                </View>
                <Text style={styles.cardDetail}>
                  Üyelik Tipi: {activeMembership.membership_plans?.name || 'Bilinmiyor'}
                </Text>
                <Text style={styles.cardDetail}>Bitiş Tarihi: {activeMembership.end_date}</Text>
              </>
            ) : (
              <Text style={styles.cardDetail}>Şu anda aktif bir üyeliğiniz bulunmamaktadır.</Text>
            )}
          </View>
          
          <Text style={[styles.headerTitle, { marginTop: 10, marginBottom: 5 }]}>Mevcut Paketler</Text>
          {plans.length === 0 ? (
            <Text style={styles.emptyText}>Salonunuza ait paket bulunamadı.</Text>
          ) : (
            plans.map((plan) => (
              <View key={plan.id} style={styles.card}>
                <Text style={styles.cardTitle}>{plan.name}</Text>
                <Text style={[styles.cardDetail, { fontWeight: 'bold', color: Colors.primary }]}>
                  {plan.price} ₺ / {plan.duration_days} Gün
                </Text>
                {plan.description && (
                  <Text style={[styles.cardDetail, { marginTop: 8 }]}>{plan.description}</Text>
                )}
                <TouchableOpacity 
                  style={[styles.saveBtn, { marginTop: 16, marginBottom: 0, padding: 12 }]} 
                  onPress={() => handleSelectPlan(plan)}
                  disabled={requestLoading}
                >
                  <Text style={styles.saveBtnText}>Plan Seç / Yenile</Text>
                </TouchableOpacity>
              </View>
            ))
          )}
        </ScrollView>
      )}
    </View>
  );
};

// ---------------------------------------------------------------------------
// 2. Training History Screen (Geçmiş & İstatistikler)
// ---------------------------------------------------------------------------
export const TrainingHistoryScreen: React.FC<ScreenProps> = ({ navigation }) => {
  const insets = useSafeAreaInsets();
  const [activities, setActivities] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const load = async () => {
      const user = await AuthService.getCurrentUser();
      if (!user) return;
      const { data } = await supabase
        .from('user_activities')
        .select('*')
        .eq('user_id', user.id)
        .order('activity_date', { ascending: false });
      setActivities(data || []);
      setLoading(false);
    };
    load();
  }, []);

  return (
    <View style={styles.container}>
      <View style={[styles.header, { paddingTop: Math.max(16, insets.top + 8) }]}>
        <TouchableOpacity onPress={() => navigation?.goBack()} style={styles.backBtn}>
          <ArrowLeft size={22} color={Colors.textDark} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Antrenman Geçmişi</Text>
      </View>
      {loading ? (
        <ActivityIndicator size="large" color={Colors.primary} style={{ marginTop: 40 }} />
      ) : activities.length === 0 ? (
        <View style={styles.emptyBox}>
          <Dumbbell size={40} color={Colors.textSecondaryDark} />
          <Text style={styles.emptyText}>Henüz tamamlanan antrenman geçmişiniz yok.</Text>
        </View>
      ) : (
        <FlatList
          data={activities}
          keyExtractor={(item) => item.id.toString()}
          contentContainerStyle={[styles.content, { paddingBottom: 24 + insets.bottom }]}
          renderItem={({ item }) => (
            <View style={styles.rowCard}>
              <View style={styles.iconCircle}>
                <CheckCircle2 size={22} color="#4CAF50" />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.rowTitle}>{item.activity_type || 'Antrenman'}</Text>
                <Text style={styles.rowSub}>
                  {item.duration || 45} dk · {item.calories_burned || 320} kcal
                </Text>
              </View>
              <Text style={styles.dateText}>{item.activity_date}</Text>
            </View>
          )}
        />
      )}
    </View>
  );
};

import { Modal, TextInput } from 'react-native';

// ---------------------------------------------------------------------------
// 3. Goals Screen (Hedeflerim)
// ---------------------------------------------------------------------------
export const GoalsScreen: React.FC<ScreenProps> = ({ navigation }) => {
  const insets = useSafeAreaInsets();
  const [goals, setGoals] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<'active' | 'completed'>('active');

  // Add Modal State
  const [modalVisible, setModalVisible] = useState(false);
  const [title, setTitle] = useState('');
  const [goalType, setGoalType] = useState('Kilo Verme');
  const [targetValue, setTargetValue] = useState('');
  const [targetUnit, setTargetUnit] = useState('kg');
  const [endDate, setEndDate] = useState('');
  const [showDatePicker, setShowDatePicker] = useState(false);
  const [saving, setSaving] = useState(false);

  // Progress Entry Modal State
  const [progressModalVisible, setProgressModalVisible] = useState(false);
  const [selectedGoalForProgress, setSelectedGoalForProgress] = useState<any>(null);
  const [progressInputValue, setProgressInputValue] = useState('');

  const goalTypes = ['Kilo Verme', 'Kilo Alma', 'Kas Geliştirme', 'Dayanıklılık', 'Koşu Mesafesi'];

  const getUnitForType = (type: string) => {
    if (type.includes('Kilo')) return 'kg';
    if (type.includes('Mesafe')) return 'km';
    return 'tekrar';
  };

  const getQuestionForType = (type?: string, unit?: string) => {
    if (type === 'Kilo Verme') return 'Kaç kilo verdiniz?';
    if (type === 'Kilo Alma') return 'Kaç kilo aldınız?';
    if (type === 'Kas Geliştirme') return 'Ne kadarlık kas gelişimi sağladınız? (kg)';
    if (type === 'Koşu Mesafesi') return 'Kaç km koştunuz?';
    return `Mevcut kaydettiğiniz / ulaştığınız değer nedir? (${unit || 'birim'})`;
  };

  const STORAGE_KEY = '@user_goals_cache';

  const saveLocalGoals = async (goalsList: any[]) => {
    try {
      await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(goalsList));
    } catch (e) {
      console.error('Error saving goals locally:', e);
    }
  };

  const loadGoals = async () => {
    setLoading(true);
    let localGoals: any[] = [];
    try {
      const stored = await AsyncStorage.getItem(STORAGE_KEY);
      if (stored) {
        localGoals = JSON.parse(stored);
      }
    } catch (e) {
      console.error('Error loading local goals:', e);
    }

    try {
      const user = await AuthService.getCurrentUser();
      if (user?.id) {
        const { data, error } = await supabase
          .from('user_goals')
          .select('*')
          .eq('user_id', user.id)
          .order('created_at', { ascending: false });

        if (!error && data && data.length > 0) {
          const remoteIds = new Set(data.map((g) => g.id));
          const localOnly = localGoals.filter((lg) => !remoteIds.has(lg.id));
          const merged = [...data, ...localOnly];
          setGoals(merged);
          saveLocalGoals(merged);
          setLoading(false);
          return;
        }
      }
    } catch (e) {
      console.error('Error loading remote goals:', e);
    }

    setGoals(localGoals);
    setLoading(false);
  };

  useEffect(() => {
    loadGoals();
  }, []);

  const handleAddGoal = async () => {
    if (!title || !targetValue) {
      feedback.warning({ title: 'Hata', message: 'Lütfen hedef başlığı ve hedef değerini girin.' });
      return;
    }
    setSaving(true);
    try {
      const user = await AuthService.getCurrentUser();
      const newGoal = {
        id: 'goal_' + Date.now(),
        user_id: user?.id || 'guest',
        title: title.trim(),
        goal_type: goalType,
        target_value: Number(targetValue),
        target_unit: getUnitForType(goalType),
        end_date: endDate || null,
        status: 'active',
        current_progress_value: 0,
        progress_percentage: 0,
        created_at: new Date().toISOString(),
      };

      const updated = [newGoal, ...goals];
      setGoals(updated);
      await saveLocalGoals(updated);

      if (user?.id) {
        await supabase.from('user_goals').insert([
          {
            user_id: user.id,
            title: title.trim(),
            goal_type: goalType,
            target_value: Number(targetValue),
            target_unit: getUnitForType(goalType),
            end_date: endDate || null,
            status: 'active',
            progress_percentage: 0,
          },
        ]);
      }

      setModalVisible(false);
      setTitle('');
      setTargetValue('');
      setEndDate('');
      feedback.success({ title: 'Başarılı', message: 'Yeni hedefiniz %0 ilerleme ile eklendi.' });
    } catch (e: any) {
      console.error('Goal save error:', e);
      feedback.error({ title: 'Hata', message: e, fallbackMessage: 'Hedef eklenirken bir hata oluştu.' });
    } finally {
      setSaving(false);
    }
  };

  const handleOpenProgressModal = (goal: any) => {
    setSelectedGoalForProgress(goal);
    setProgressInputValue('');
    setProgressModalVisible(true);
  };

  const handleSaveProgress = async () => {
    if (!selectedGoalForProgress || !progressInputValue) {
      feedback.warning({ title: 'Hata', message: 'Lütfen eklenecek değeri girin.' });
      return;
    }

    const addedVal = Number(progressInputValue);
    const prevVal = Number(selectedGoalForProgress.current_progress_value) || 0;
    const newTotal = prevVal + addedVal;
    const targetVal = Number(selectedGoalForProgress.target_value) || 1;
    const calcPct = Math.min(100, Math.max(0, Math.round((newTotal / targetVal) * 100)));

    const updatedGoals = goals.map((g) =>
      g.id === selectedGoalForProgress.id
        ? {
            ...g,
            current_progress_value: newTotal,
            progress_percentage: calcPct,
            status: calcPct >= 100 ? 'completed' : g.status,
            is_success: calcPct >= 100 ? true : g.is_success,
          }
        : g
    );

    setGoals(updatedGoals);
    await saveLocalGoals(updatedGoals);

    try {
      await supabase
        .from('user_goals')
        .update({
          progress_percentage: calcPct,
          status: calcPct >= 100 ? 'completed' : selectedGoalForProgress.status,
        })
        .eq('id', selectedGoalForProgress.id);
    } catch {}

    setProgressModalVisible(false);
    setProgressInputValue('');

    feedback.success({
      title: 'İlerleme Güncellendi',
      message: `%${calcPct}'ı tamamlandı (Toplam: ${newTotal} / ${targetVal} ${selectedGoalForProgress.target_unit}).`,
    });
  };

  const handleCompleteGoal = async (id: string, success: boolean = true) => {
    const nextPct = success ? 100 : 0;

    const updatedGoals = goals.map((g) =>
      g.id === id ? { ...g, status: 'completed', is_success: success, progress_percentage: nextPct } : g
    );

    setGoals(updatedGoals);
    await saveLocalGoals(updatedGoals);

    try {
      await supabase.from('user_goals').update({ status: 'completed', progress_percentage: nextPct }).eq('id', id);
    } catch {}
  };

  const filteredGoals = goals.filter((g) =>
    activeTab === 'active' ? g.status === 'active' : g.status === 'completed'
  );

  return (
    <View style={styles.container}>
      <View style={[styles.header, { paddingTop: Math.max(16, insets.top + 8) }]}>
        <TouchableOpacity onPress={() => navigation?.goBack()} style={styles.backBtn}>
          <ArrowLeft size={22} color={Colors.textDark} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Hedeflerim</Text>
        <TouchableOpacity onPress={() => setModalVisible(true)} style={{ marginLeft: 'auto' }}>
          <Plus size={24} color={Colors.primary} />
        </TouchableOpacity>
      </View>

      <View style={styles.tabContainer}>
        <TouchableOpacity 
          style={[styles.tabBtn, activeTab === 'active' && styles.tabBtnActive]} 
          onPress={() => setActiveTab('active')}
        >
          <Text style={[styles.tabText, activeTab === 'active' && styles.tabTextActive]}>Aktif Hedefler</Text>
        </TouchableOpacity>
        <TouchableOpacity 
          style={[styles.tabBtn, activeTab === 'completed' && styles.tabBtnActive]} 
          onPress={() => setActiveTab('completed')}
        >
          <Text style={[styles.tabText, activeTab === 'completed' && styles.tabTextActive]}>Tamamlanan</Text>
        </TouchableOpacity>
      </View>

      {loading ? (
        <ActivityIndicator size="large" color={Colors.primary} style={{ marginTop: 40 }} />
      ) : (
        <ScrollView contentContainerStyle={styles.content}>
          {filteredGoals.length === 0 ? (
            <View style={styles.emptyBox}>
              <Text style={styles.emptyText}>Bu kategoride hedefiniz bulunmuyor.</Text>
            </View>
          ) : (
            filteredGoals.map((g) => {
              const currentPct = g.progress_percentage ?? 0;
              const currentVal = g.current_progress_value ?? 0;
              return (
                <View key={g.id} style={styles.card}>
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                    <Text style={styles.cardTitle}>{g.title}</Text>
                    {g.goal_type && (
                      <View style={styles.activeBadge}>
                        <Text style={styles.activeBadgeText}>{g.goal_type}</Text>
                      </View>
                    )}
                  </View>
                  
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: 8 }}>
                    <Text style={styles.cardDetail}>Hedef: {g.target_value} {g.target_unit}</Text>
                    {g.end_date && <Text style={styles.cardDetail}>Bitiş: {g.end_date}</Text>}
                  </View>

                  <View style={styles.progressContainer}>
                    <View style={[styles.progressBar, { width: `${currentPct}%` }]} />
                  </View>

                  <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: 4 }}>
                    <Text style={styles.cardDetail}>
                      Mevcut: {currentVal} {g.target_unit}
                    </Text>
                    <Text style={[styles.cardDetail, { fontWeight: 'bold', color: Colors.primary }]}>
                      %{currentPct} Tamamlandı
                    </Text>
                  </View>

                  {activeTab === 'active' && (
                    <View style={{ flexDirection: 'row', gap: 6, marginTop: 12 }}>
                      <TouchableOpacity 
                        style={[styles.completeBtn, { flex: 1, backgroundColor: '#4CAF50', paddingHorizontal: 6 }]}
                        onPress={() => handleCompleteGoal(g.id, true)}
                      >
                        <CheckCircle2 size={14} color="#fff" style={{ marginRight: 2 }} />
                        <Text style={[styles.completeBtnText, { fontSize: 12 }]}>Başarılı</Text>
                      </TouchableOpacity>

                      <TouchableOpacity 
                        style={[styles.completeBtn, { flex: 1, backgroundColor: Colors.primary, paddingHorizontal: 6 }]}
                        onPress={() => handleOpenProgressModal(g)}
                      >
                        <TrendingUp size={14} color="#fff" style={{ marginRight: 2 }} />
                        <Text style={[styles.completeBtnText, { fontSize: 12 }]}>İlerleme Gir</Text>
                      </TouchableOpacity>

                      <TouchableOpacity 
                        style={[styles.completeBtn, { flex: 1, backgroundColor: '#F44336', paddingHorizontal: 6 }]}
                        onPress={() => handleCompleteGoal(g.id, false)}
                      >
                        <X size={14} color="#fff" style={{ marginRight: 2 }} />
                        <Text style={[styles.completeBtnText, { fontSize: 12 }]}>Başarısız</Text>
                      </TouchableOpacity>
                    </View>
                  )}
                </View>
              );
            })
          )}
        </ScrollView>
      )}

      {/* Yeni Hedef Ekle Modal */}
      <SmoothModal visible={modalVisible} onClose={() => setModalVisible(false)} variant="bottom-sheet">
        <View style={styles.modalContent}>
          <View style={styles.modalHeader}>
            <Text style={styles.modalTitle}>Yeni Hedef Ekle</Text>
            <TouchableOpacity onPress={() => setModalVisible(false)}>
              <X size={24} color={Colors.textDark} />
            </TouchableOpacity>
          </View>

          <ScrollView>
            <Text style={styles.inputLabel}>Hedef Başlığı</Text>
            <TextInput
              style={styles.input}
              placeholder="Örn: Yaza Hazırlık"
              placeholderTextColor={Colors.textSecondaryDark}
              value={title}
              onChangeText={setTitle}
            />

            <Text style={styles.inputLabel}>Hedef Türü</Text>
            <View style={styles.typeContainer}>
              {goalTypes.map(type => (
                <TouchableOpacity 
                  key={type} 
                  style={[styles.typeChip, goalType === type && styles.typeChipActive]}
                  onPress={() => setGoalType(type)}
                >
                  <Text style={[styles.typeChipText, goalType === type && styles.typeChipTextActive]}>
                    {type}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>

            <Text style={styles.inputLabel}>Hedef Değer</Text>
            <View style={{ flexDirection: 'row', alignItems: 'center' }}>
              <TextInput
                style={[styles.input, { flex: 1, marginRight: 10 }]}
                placeholder="Örn: 5"
                placeholderTextColor={Colors.textSecondaryDark}
                value={targetValue}
                onChangeText={setTargetValue}
                keyboardType="numeric"
              />
              <Text style={{ color: Colors.textDark, fontSize: 16 }}>{getUnitForType(goalType)}</Text>
            </View>

            <Text style={styles.inputLabel}>Bitiş Tarihi (Opsiyonel)</Text>
            <TouchableOpacity 
              onPress={() => setShowDatePicker(true)}
              style={[styles.input, { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }]}
            >
              <Text style={{ color: endDate ? Colors.textDark : Colors.textSecondaryDark }}>
                {endDate || 'YYYY-MM-DD'}
              </Text>
              <Calendar size={18} color={Colors.primary} />
            </TouchableOpacity>

            <TouchableOpacity 
              style={styles.saveBtn} 
              onPress={handleAddGoal}
              disabled={saving}
            >
              {saving ? (
                <ActivityIndicator color="#fff" />
              ) : (
                <Text style={styles.saveBtnText}>Kaydet</Text>
              )}
            </TouchableOpacity>
          </ScrollView>
        </View>
      </SmoothModal>

      {/* Date Picker Modal for Goal End Date */}
      <DateTimePickerModal
        visible={showDatePicker}
        mode="date"
        title="Bitiş Tarihi Seç"
        initialValue={endDate}
        onConfirm={(d) => {
          setEndDate(d);
          setShowDatePicker(false);
        }}
        onCancel={() => setShowDatePicker(false)}
      />

      {/* Özel Kumülatif İlerleme Gir Modal */}
      <SmoothModal visible={progressModalVisible} onClose={() => setProgressModalVisible(false)} variant="modal">
        <View style={styles.modalContent}>
          <View style={styles.modalHeader}>
            <Text style={styles.modalTitle}>İlerleme Kaydet</Text>
            <TouchableOpacity onPress={() => setProgressModalVisible(false)}>
              <X size={24} color={Colors.textDark} />
            </TouchableOpacity>
          </View>

          {selectedGoalForProgress && (() => {
            const currentAccumulated = selectedGoalForProgress.current_progress_value || 0;
            const hasPreviousProgress = currentAccumulated > 0;
            
            return (
              <View style={{ marginVertical: 10 }}>
                <Text style={[styles.inputLabel, { fontSize: 16, color: Colors.primary, marginBottom: 4 }]}>
                  {selectedGoalForProgress.title} ({selectedGoalForProgress.goal_type || 'Hedef'})
                </Text>

                {hasPreviousProgress ? (
                  <Text style={[styles.inputLabel, { fontSize: 14, color: Colors.textDark, marginBottom: 12 }]}>
                    Son ilerlemeniz: <Text style={{ fontWeight: 'bold', color: Colors.primary }}>{currentAccumulated} {selectedGoalForProgress.target_unit}</Text>. Bunun üzerine ne kadar daha eklemek istiyorsunuz?
                  </Text>
                ) : (
                  <Text style={[styles.inputLabel, { fontSize: 14, marginBottom: 12 }]}>
                    {getQuestionForType(selectedGoalForProgress.goal_type, selectedGoalForProgress.target_unit)}
                  </Text>
                )}

                <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                  <TextInput
                    style={[styles.input, { flex: 1, marginRight: 10 }]}
                    placeholder="Eklenecek miktar (Örn: 3)"
                    placeholderTextColor={Colors.textSecondaryDark}
                    value={progressInputValue}
                    onChangeText={setProgressInputValue}
                    keyboardType="numeric"
                    autoFocus
                  />
                  <Text style={{ color: Colors.textDark, fontSize: 16, fontWeight: 'bold' }}>
                    {selectedGoalForProgress.target_unit || 'birim'}
                  </Text>
                </View>

                <Text style={{ color: Colors.textSecondaryDark, fontSize: 12, marginTop: 4 }}>
                  Hedef Değer: {selectedGoalForProgress.target_value} {selectedGoalForProgress.target_unit} {hasPreviousProgress ? `(Mevcut: ${currentAccumulated})` : ''}
                </Text>

                <TouchableOpacity 
                  style={[styles.saveBtn, { marginTop: 20 }]} 
                  onPress={handleSaveProgress}
                >
                  <Text style={styles.saveBtnText}>İlerlemeyi Ekle ve Güncelle</Text>
                </TouchableOpacity>
              </View>
            );
          })()}
        </View>
      </SmoothModal>
    </View>
  );
};

// ---------------------------------------------------------------------------
// 4. Personal Records Screen (Kişisel Rekorlar & Denemeler)
// ---------------------------------------------------------------------------
export const PersonalRecordsScreen: React.FC<ScreenProps> = ({ navigation }) => {
  const [activeTab, setActiveTab] = useState<'records' | 'history'>('records');
  const [records, setRecords] = useState<any[]>([]);
  const [history, setHistory] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [streakCount, setStreakCount] = useState(0);

  const loadPRData = async () => {
    try {
      setLoading(true);
      const user = await AuthService.getCurrentUser();
      if (user) {
        // Fetch User Profile for streak
        const profile = await UserService.fetchProfile(user.id);
        setStreakCount(profile?.current_streak || 0);

        // Fetch PRs from database
        const { data: prData } = await supabase
          .from('personal_records')
          .select('*, exercise:exercises(name)')
          .eq('user_id', user.id)
          .order('weight_kg', { ascending: false });

        setRecords(prData || []);

        // Fetch Completed Training Sessions
        const { data: historyData } = await supabase
          .from('training_sessions')
          .select('*, program:training_programs(name)')
          .eq('user_id', user.id)
          .not('completed_at', 'is', null)
          .order('completed_at', { ascending: false })
          .limit(20);

        setHistory(historyData || []);
      }
    } catch (e) {
      console.error('Error loading PR data:', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadPRData();
  }, []);

  return (
    <View style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation?.goBack()} style={styles.backBtn}>
          <ArrowLeft size={22} color={Colors.textDark} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Kişisel Rekorlar (PR)</Text>
      </View>

      {/* Streak Summary Band */}
      <View style={{ flexDirection: 'row', alignItems: 'center', backgroundColor: Colors.cardDark, marginHorizontal: 16, marginTop: 12, padding: 14, borderRadius: 16, gap: 12 }}>
        <Flame size={28} color="#FF9800" />
        <View style={{ flex: 1 }}>
          <Text style={{ color: Colors.textDark, fontSize: 16, fontWeight: '700' }}>{streakCount} Günlük Seri</Text>
          <Text style={{ color: Colors.textSecondaryDark, fontSize: 13 }}>Aktif antrenman seriniz</Text>
        </View>
        <TouchableOpacity
          style={{ backgroundColor: Colors.primary, paddingHorizontal: 14, paddingVertical: 8, borderRadius: 20 }}
          onPress={() => navigation?.navigate('RecordAttemptSetup')}
        >
          <Text style={{ color: Colors.allWhite, fontSize: 13, fontWeight: '700' }}>+ Rekor Denemesi</Text>
        </TouchableOpacity>
      </View>

      {/* Tabs */}
      <View style={styles.tabContainer}>
        <TouchableOpacity
          style={[styles.tabBtn, activeTab === 'records' && styles.tabBtnActive]}
          onPress={() => setActiveTab('records')}
        >
          <Text style={[styles.tabText, activeTab === 'records' && styles.tabTextActive]}>Kişisel Rekorlar</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.tabBtn, activeTab === 'history' && styles.tabBtnActive]}
          onPress={() => setActiveTab('history')}
        >
          <Text style={[styles.tabText, activeTab === 'history' && styles.tabTextActive]}>Geçmiş Antrenmanlar</Text>
        </TouchableOpacity>
      </View>

      {loading ? (
        <ActivityIndicator size="large" color={Colors.primary} style={{ marginTop: 40 }} />
      ) : activeTab === 'records' ? (
        <ScrollView contentContainerStyle={styles.content}>
          {records.length === 0 ? (
            <View style={styles.emptyBox}>
              <Award size={40} color={Colors.textSecondaryDark} />
              <Text style={styles.emptyText}>Henüz kayıtlı kişisel rekorunuz bulunmuyor.</Text>
              <TouchableOpacity
                style={[styles.saveBtn, { marginTop: 16, paddingHorizontal: 24 }]}
                onPress={() => navigation?.navigate('RecordAttemptSetup')}
              >
                <Text style={styles.saveBtnText}>Rekor Denemesi Başlat</Text>
              </TouchableOpacity>
            </View>
          ) : (
            records.map((item) => (
              <View key={item.id} style={styles.rowCard}>
                <Award size={24} color="#FF9800" />
                <View style={{ flex: 1, marginLeft: 12 }}>
                  <Text style={styles.rowTitle}>{item.exercise?.name || item.exercise_name || 'Egzersiz PR'}</Text>
                  <Text style={styles.rowSub}>
                    {item.weight_kg ? `${item.weight_kg} kg` : ''} {item.reps ? `x ${item.reps} tekrar` : ''}
                  </Text>
                </View>
                {item.created_at && (
                  <Text style={styles.dateText}>{item.created_at.slice(0, 10)}</Text>
                )}
              </View>
            ))
          )}
        </ScrollView>
      ) : (
        <ScrollView contentContainerStyle={styles.content}>
          {history.length === 0 ? (
            <View style={styles.emptyBox}>
              <Dumbbell size={40} color={Colors.textSecondaryDark} />
              <Text style={styles.emptyText}>Tamamlanmış antrenman geçmişi bulunamadı.</Text>
            </View>
          ) : (
            history.map((item) => (
              <View key={item.id} style={styles.rowCard}>
                <CheckCircle2 size={22} color="#4CAF50" />
                <View style={{ flex: 1, marginLeft: 12 }}>
                  <Text style={styles.rowTitle}>{item.program?.name || 'Oturum'}</Text>
                  <Text style={styles.rowSub}>
                    {item.completed_at ? new Date(item.completed_at).toLocaleDateString('tr-TR') : ''}
                  </Text>
                </View>
              </View>
            ))
          )}
        </ScrollView>
      )}
    </View>
  );
};

// ---------------------------------------------------------------------------
// 5. Generic Menu Sub Screen Placeholder / Wrapper
// ---------------------------------------------------------------------------
export const GenericMenuScreen: React.FC<{ title: string; navigation?: any }> = ({
  title,
  navigation,
}) => {
  const insets = useSafeAreaInsets();
  return (
    <View style={styles.container}>
      <View style={[styles.header, { paddingTop: Math.max(16, insets.top + 8) }]}>
        <TouchableOpacity onPress={() => navigation?.goBack()} style={styles.backBtn}>
          <ArrowLeft size={22} color={Colors.textDark} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>{title}</Text>
      </View>
      <ScrollView contentContainerStyle={[styles.content, { paddingBottom: 24 + insets.bottom }]}>
        <Text style={styles.emptyText}>{title} bilgileriniz bu ekranda yer alacaktır.</Text>
      </ScrollView>
    </View>
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
    paddingHorizontal: 20,
    paddingTop: 50,
    paddingBottom: 16,
    backgroundColor: Colors.cardDark,
  },
  backBtn: {
    marginRight: 14,
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: Colors.textDark,
  },
  content: {
    padding: 20,
    gap: 14,
  },
  card: {
    backgroundColor: Colors.cardDark,
    borderRadius: 20,
    padding: 20,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  cardHeader: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
    textTransform: 'uppercase',
    fontWeight: '700',
    marginBottom: 4,
  },
  cardTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.textDark,
    marginBottom: 10,
  },
  badgeRow: {
    marginBottom: 12,
  },
  activeBadge: {
    backgroundColor: 'rgba(76, 175, 80, 0.15)',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 8,
    alignSelf: 'flex-start',
  },
  activeBadgeText: {
    color: '#4CAF50',
    fontSize: 12,
    fontWeight: '700',
  },
  cardDetail: {
    fontSize: 14,
    color: Colors.textSecondaryDark,
    marginTop: 2,
  },
  rowCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 16,
    borderWidth: 1,
    borderColor: Colors.borderDark,
    marginBottom: 12,
  },
  iconCircle: {
    marginRight: 12,
  },
  rowTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.textDark,
  },
  rowSub: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    marginTop: 2,
  },
  dateText: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
  },
  emptyBox: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 32,
  },
  emptyText: {
    color: Colors.textSecondaryDark,
    fontSize: 15,
    textAlign: 'center',
    marginTop: 12,
  },
  tabContainer: {
    flexDirection: 'row',
    paddingHorizontal: 20,
    marginTop: 10,
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
    color: Colors.textSecondaryDark,
    fontSize: 15,
    fontWeight: '600',
  },
  tabTextActive: {
    color: Colors.primary,
  },
  progressContainer: {
    height: 8,
    backgroundColor: Colors.borderDark,
    borderRadius: 4,
    marginTop: 12,
    overflow: 'hidden',
  },
  progressBar: {
    height: '100%',
    backgroundColor: Colors.primary,
    borderRadius: 4,
  },
  completeBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#4CAF50',
    paddingVertical: 10,
    borderRadius: 10,
    marginTop: 16,
  },
  completeBtnText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '700',
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.6)',
    justifyContent: 'flex-end',
  },
  modalContent: {
    backgroundColor: Colors.backgroundDark,
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    padding: 24,
    maxHeight: '85%',
  },
  modalHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 20,
  },
  modalTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: Colors.textDark,
  },
  inputLabel: {
    fontSize: 14,
    color: Colors.textSecondaryDark,
    marginBottom: 8,
    marginTop: 16,
  },
  input: {
    backgroundColor: Colors.cardDark,
    borderWidth: 1,
    borderColor: Colors.borderDark,
    borderRadius: 12,
    padding: 14,
    color: Colors.textDark,
    fontSize: 15,
  },
  typeContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  typeChip: {
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 20,
    backgroundColor: Colors.cardDark,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  typeChipActive: {
    backgroundColor: Colors.primary,
    borderColor: Colors.primary,
  },
  typeChipText: {
    color: Colors.textSecondaryDark,
    fontSize: 13,
    fontWeight: '600',
  },
  typeChipTextActive: {
    color: '#fff',
  },
  saveBtn: {
    backgroundColor: Colors.primary,
    padding: 16,
    borderRadius: 14,
    alignItems: 'center',
    marginTop: 32,
    marginBottom: 20,
  },
  saveBtnText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '700',
  },
});
