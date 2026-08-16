import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, TextInput, Image, ActivityIndicator } from 'react-native';
import { feedback } from '../../services/feedbackService';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Camera, Image as ImageIcon, Plus, ArrowLeft, History } from 'lucide-react-native';
import * as ImagePicker from 'expo-image-picker';
import { Colors } from '../../theme/colors';
import { supabase } from '../../services/supabaseClient';
import { useAuth } from '../../context/AuthContext';
import { SmoothModal } from '../../components/motion/SmoothModal';
import { FadeView } from '../../components/motion/FadeView';
import { decode } from 'base64-arraybuffer';

export const ProgressTrackingScreen = ({ navigation }: any) => {
  const { session, profile } = useAuth();
  const [activeTab, setActiveTab] = useState<'measurements' | 'photos'>('measurements');
  const [loading, setLoading] = useState(true);
  const [modalVisible, setModalVisible] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [savingMeasurement, setSavingMeasurement] = useState(false);

  // Real Data State
  const [measurementHistory, setMeasurementHistory] = useState<any[]>([]);
  const [latestMeasurement, setLatestMeasurement] = useState<any>(null);
  const [photoList, setPhotoList] = useState<any[]>([]);

  // Form State
  const [formWeight, setFormWeight] = useState('');
  const [formHeight, setFormHeight] = useState('');
  const [formBodyFat, setFormBodyFat] = useState('');
  const [formMuscleMass, setFormMuscleMass] = useState('');
  const [formChest, setFormChest] = useState('');
  const [formWaist, setFormWaist] = useState('');
  const [formHips, setFormHips] = useState('');
  const [formArmLeft, setFormArmLeft] = useState('');
  const [formNotes, setFormNotes] = useState('');

  useEffect(() => {
    loadProgressData();
  }, [session?.user?.id]);

  const loadProgressData = async () => {
    if (!session?.user?.id) return;
    setLoading(true);
    try {
      // 1. Vücut Ölçümlerini Çek
      const { data: mData } = await supabase
        .from('body_measurements')
        .select('*')
        .eq('user_id', session.user.id)
        .order('measurement_date', { ascending: false });

      const mList = mData || [];
      setMeasurementHistory(mList);
      if (mList.length > 0) {
        setLatestMeasurement(mList[0]);
      }

      // 2. Gelişim Fotoğraflarını Çek
      const { data: pData } = await supabase
        .from('progress_photos')
        .select('*')
        .eq('user_id', session.user.id)
        .order('taken_date', { ascending: false });

      setPhotoList(pData || []);
    } catch (e) {
      console.error('Error loading progress data:', e);
    } finally {
      setLoading(false);
    }
  };

  const handleSaveMeasurement = async () => {
    if (!session?.user?.id) return;
    setSavingMeasurement(true);
    try {
      const payload: Record<string, any> = {
        user_id: session.user.id,
        gym_id: profile?.gym_id || null,
        measurement_date: new Date().toISOString().split('T')[0],
        weight: formWeight ? parseFloat(formWeight.replace(',', '.')) : null,
        height: formHeight ? parseFloat(formHeight.replace(',', '.')) : null,
        body_fat_percentage: formBodyFat ? parseFloat(formBodyFat.replace(',', '.')) : null,
        muscle_mass: formMuscleMass ? parseFloat(formMuscleMass.replace(',', '.')) : null,
        chest: formChest ? parseFloat(formChest.replace(',', '.')) : null,
        waist: formWaist ? parseFloat(formWaist.replace(',', '.')) : null,
        hips: formHips ? parseFloat(formHips.replace(',', '.')) : null,
        arm_left: formArmLeft ? parseFloat(formArmLeft.replace(',', '.')) : null,
        notes: formNotes || null,
      };

      const { data, error } = await supabase
        .from('body_measurements')
        .insert(payload)
        .select()
        .single();

      if (error) throw error;

      setLatestMeasurement(data);
      setMeasurementHistory((prev) => [data, ...prev]);
      setModalVisible(false);
      feedback.success({ title: 'Başarılı', message: 'Vücut ölçümünüz kaydedildi.' });
    } catch (e: any) {
      console.error('Error saving measurement:', e);
      feedback.error({ title: 'Hata', message: e, fallbackMessage: 'Ölçüm kaydedilemedi.' });
    } finally {
      setSavingMeasurement(false);
    }
  };

  const uploadPhoto = async (photoType: 'before' | 'after' | 'progress', source: 'camera' | 'gallery') => {
    try {
      let result;
      if (source === 'camera') {
        const permission = await ImagePicker.requestCameraPermissionsAsync();
        if (!permission.granted) {
          feedback.warning({ title: 'Hata', message: 'Kamera izni gerekli.' });
          return;
        }
        result = await ImagePicker.launchCameraAsync({
          mediaTypes: ['images'],
          quality: 0.6,
          base64: true,
        });
      } else {
        const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
        if (!permission.granted) {
          feedback.warning({ title: 'Hata', message: 'Galeri izni gerekli.' });
          return;
        }
        result = await ImagePicker.launchImageLibraryAsync({
          mediaTypes: ['images'],
          quality: 0.6,
          base64: true,
        });
      }

      if (!result.canceled && result.assets && result.assets.length > 0) {
        setUploading(true);
        const img = result.assets[0];
        const userId = session?.user?.id;
        if (!userId || !img.base64) return;

        let actualBucket = 'progress-photos';
        let actualPath = `${userId}/${Date.now()}_${photoType}.jpg`;

        const { error: uploadErr } = await supabase.storage
          .from(actualBucket)
          .upload(actualPath, decode(img.base64), {
            contentType: 'image/jpeg',
            upsert: true,
          });

        if (uploadErr) {
          // If progress-photos fails, try avatars bucket (always exists)
          actualBucket = 'avatars';
          actualPath = `${userId}/progress_${Date.now()}_${photoType}.jpg`;
          const { error: retryErr } = await supabase.storage
            .from(actualBucket)
            .upload(actualPath, decode(img.base64), { contentType: 'image/jpeg', upsert: true });
          if (retryErr) throw new Error(`Fotoğraf yüklenemedi: ${retryErr.message}`);
        }

        const { data: { publicUrl } } = supabase.storage
          .from(actualBucket)
          .getPublicUrl(actualPath);

        if (!publicUrl) {
          throw new Error('Fotoğraf URL adresi oluşturulamadı.');
        }

        // Save DB metadata pointing strictly to the real uploaded location
        const { data: photoDb, error: dbErr } = await supabase
          .from('progress_photos')
          .insert({
            user_id: userId,
            gym_id: profile?.gym_id || null,
            photo_url: publicUrl,
            photo_type: photoType,
            taken_date: new Date().toISOString().split('T')[0],
          })
          .select()
          .single();

        if (dbErr) throw dbErr;

        setPhotoList((prev) => [photoDb, ...prev]);
        feedback.success({ title: 'Başarılı', message: 'Gelişim fotoğrafınız kaydedildi.' });
      }
    } catch (e: any) {
      console.error('Error uploading photo:', e);
      feedback.error({ title: 'Hata', message: e, fallbackMessage: 'Fotoğraf yüklenemedi.' });
    } finally {
      setUploading(false);
    }
  };

  const currentWeight = latestMeasurement?.weight || profile?.weight_kg || '--';
  const currentBodyFat = latestMeasurement?.body_fat_percentage || '--';
  const currentMuscleMass = latestMeasurement?.muscle_mass || '--';

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation?.goBack()} style={styles.backButton}>
          <ArrowLeft size={24} color={Colors.textDark} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Gelişim Takibi</Text>
      </View>

      {/* Tab Switcher */}
      <View style={styles.tabContainer}>
        <TouchableOpacity
          style={[styles.tabButton, activeTab === 'measurements' && styles.activeTabButton]}
          onPress={() => setActiveTab('measurements')}
        >
          <Text style={[styles.tabText, activeTab === 'measurements' && styles.activeTabText]}>
            Vücut Ölçüleri
          </Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.tabButton, activeTab === 'photos' && styles.activeTabButton]}
          onPress={() => setActiveTab('photos')}
        >
          <Text style={[styles.tabText, activeTab === 'photos' && styles.activeTabText]}>
            Gelişim Fotoğrafları
          </Text>
        </TouchableOpacity>
      </View>

      {loading ? (
        <View style={styles.centerBox}>
          <ActivityIndicator size="large" color={Colors.primary} />
        </View>
      ) : (
        <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
          {activeTab === 'measurements' ? (
            <FadeView>
              {/* Özet Kartları */}
              <View style={styles.statsGrid}>
                <View style={styles.statCard}>
                  <Text style={styles.statLabel}>Kilo</Text>
                  <Text style={styles.statValue}>{currentWeight} kg</Text>
                </View>
                <View style={styles.statCard}>
                  <Text style={styles.statLabel}>Yağ Oranı</Text>
                  <Text style={styles.statValue}>%{currentBodyFat}</Text>
                </View>
                <View style={styles.statCard}>
                  <Text style={styles.statLabel}>Kas Kütlesi</Text>
                  <Text style={styles.statValue}>{currentMuscleMass} kg</Text>
                </View>
              </View>

              {/* Detaylı Ölçümler Listesi */}
              <View style={styles.measurementsCard}>
                <View style={styles.cardHeader}>
                  <Text style={styles.cardTitle}>Son Ölçüm Detayları</Text>
                  <TouchableOpacity
                    style={styles.addButton}
                    onPress={() => {
                      setFormWeight(latestMeasurement?.weight ? String(latestMeasurement.weight) : '');
                      setFormChest(latestMeasurement?.chest ? String(latestMeasurement.chest) : '');
                      setFormWaist(latestMeasurement?.waist ? String(latestMeasurement.waist) : '');
                      setFormHips(latestMeasurement?.hips ? String(latestMeasurement.hips) : '');
                      setModalVisible(true);
                    }}
                  >
                    <Plus size={16} color={Colors.allWhite} />
                    <Text style={styles.addButtonText}>Yeni Ölçüm</Text>
                  </TouchableOpacity>
                </View>

                <View style={styles.measurementRow}>
                  <Text style={styles.measurementLabel}>Göğüs</Text>
                  <Text style={styles.measurementValue}>{latestMeasurement?.chest || '--'} cm</Text>
                </View>
                <View style={styles.measurementRow}>
                  <Text style={styles.measurementLabel}>Bel</Text>
                  <Text style={styles.measurementValue}>{latestMeasurement?.waist || '--'} cm</Text>
                </View>
                <View style={styles.measurementRow}>
                  <Text style={styles.measurementLabel}>Kalça</Text>
                  <Text style={styles.measurementValue}>{latestMeasurement?.hips || '--'} cm</Text>
                </View>
                <View style={styles.measurementRow}>
                  <Text style={styles.measurementLabel}>Kol (Sol/Sağ)</Text>
                  <Text style={styles.measurementValue}>{latestMeasurement?.arm_left || '--'} cm</Text>
                </View>
                <View style={[styles.measurementRow, { borderBottomWidth: 0 }]}>
                  <Text style={styles.measurementLabel}>Bacak</Text>
                  <Text style={styles.measurementValue}>{latestMeasurement?.thigh_left || '--'} cm</Text>
                </View>
              </View>

              {/* Geçmiş Ölçümler */}
              {measurementHistory.length > 0 ? (
                <View style={styles.historySection}>
                  <Text style={styles.sectionTitle}>Ölçüm Geçmişi</Text>
                  {measurementHistory.map((m) => (
                    <View key={m.id} style={styles.historyItem}>
                      <Text style={styles.historyDate}>
                        {new Date(m.measurement_date).toLocaleDateString('tr-TR')}
                      </Text>
                      <Text style={styles.historyValues}>
                        {m.weight ? `${m.weight} kg` : ''} {m.body_fat_percentage ? `• %${m.body_fat_percentage} Yağ` : ''}
                      </Text>
                    </View>
                  ))}
                </View>
              ) : null}
            </FadeView>
          ) : (
            <FadeView>
              {/* Fotoğraf Ekleme Butonları */}
              <View style={styles.photoActions}>
                <TouchableOpacity
                  style={styles.photoBtn}
                  onPress={() => uploadPhoto('progress', 'camera')}
                  disabled={uploading}
                >
                  <Camera size={20} color={Colors.primary} />
                  <Text style={styles.photoBtnText}>Kamera ile Çek</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  style={styles.photoBtn}
                  onPress={() => uploadPhoto('progress', 'gallery')}
                  disabled={uploading}
                >
                  <ImageIcon size={20} color={Colors.primary} />
                  <Text style={styles.photoBtnText}>Galeriden Seç</Text>
                </TouchableOpacity>
              </View>

              {uploading && (
                <View style={{ padding: 20, alignItems: 'center' }}>
                  <ActivityIndicator size="small" color={Colors.primary} />
                  <Text style={{ color: Colors.textSecondaryDark, marginTop: 8 }}>Fotoğraf yükleniyor...</Text>
                </View>
              )}

              {/* Fotoğraf Galerisi */}
              <View style={styles.photoGrid}>
                {photoList.length === 0 ? (
                  <View style={styles.emptyPhotos}>
                    <ImageIcon size={48} color={Colors.textSecondaryDark} />
                    <Text style={styles.emptyTitle}>Henüz Fotoğraf Eklenmedi</Text>
                    <Text style={styles.emptySubtitle}>
                      Gelişiminizi görsel olarak takip etmek için düzenli olarak fotoğraf yükleyebilirsiniz.
                    </Text>
                  </View>
                ) : (
                  photoList.map((p) => (
                    <View key={p.id} style={styles.photoCard}>
                      <Image source={{ uri: p.photo_url }} style={styles.photoImg} resizeMode="cover" />
                      <Text style={styles.photoDate}>
                        {new Date(p.taken_date || p.created_at).toLocaleDateString('tr-TR')}
                      </Text>
                    </View>
                  ))
                )}
              </View>
            </FadeView>
          )}
        </ScrollView>
      )}

      {/* Yeni Ölçüm Modal */}
      <SmoothModal visible={modalVisible} onClose={() => setModalVisible(false)}>
        <View style={styles.modalContent}>
          <Text style={styles.modalTitle}>Yeni Ölçüm Ekle</Text>
          <ScrollView style={{ maxHeight: 380 }} showsVerticalScrollIndicator={false}>
            <View style={styles.modalInputs}>
              <View style={styles.inputGroup}>
                <Text style={styles.inputLabel}>Kilo (kg)</Text>
                <TextInput
                  style={styles.modalInput}
                  keyboardType="numeric"
                  placeholder="75.5"
                  placeholderTextColor={Colors.textSecondaryDark}
                  value={formWeight}
                  onChangeText={setFormWeight}
                />
              </View>
              <View style={styles.inputGroup}>
                <Text style={styles.inputLabel}>Boy (cm)</Text>
                <TextInput
                  style={styles.modalInput}
                  keyboardType="numeric"
                  placeholder="180"
                  placeholderTextColor={Colors.textSecondaryDark}
                  value={formHeight}
                  onChangeText={setFormHeight}
                />
              </View>
              <View style={styles.inputGroup}>
                <Text style={styles.inputLabel}>Yağ Oranı (%)</Text>
                <TextInput
                  style={styles.modalInput}
                  keyboardType="numeric"
                  placeholder="18"
                  placeholderTextColor={Colors.textSecondaryDark}
                  value={formBodyFat}
                  onChangeText={setFormBodyFat}
                />
              </View>
              <View style={styles.inputGroup}>
                <Text style={styles.inputLabel}>Göğüs (cm)</Text>
                <TextInput
                  style={styles.modalInput}
                  keyboardType="numeric"
                  placeholder="100"
                  placeholderTextColor={Colors.textSecondaryDark}
                  value={formChest}
                  onChangeText={setFormChest}
                />
              </View>
              <View style={styles.inputGroup}>
                <Text style={styles.inputLabel}>Bel (cm)</Text>
                <TextInput
                  style={styles.modalInput}
                  keyboardType="numeric"
                  placeholder="85"
                  placeholderTextColor={Colors.textSecondaryDark}
                  value={formWaist}
                  onChangeText={setFormWaist}
                />
              </View>
              <View style={styles.inputGroup}>
                <Text style={styles.inputLabel}>Kalça (cm)</Text>
                <TextInput
                  style={styles.modalInput}
                  keyboardType="numeric"
                  placeholder="98"
                  placeholderTextColor={Colors.textSecondaryDark}
                  value={formHips}
                  onChangeText={setFormHips}
                />
              </View>
            </View>
          </ScrollView>

          <TouchableOpacity
            style={[styles.saveButton, savingMeasurement && { opacity: 0.7 }]}
            onPress={handleSaveMeasurement}
            disabled={savingMeasurement}
          >
            {savingMeasurement ? (
              <ActivityIndicator size="small" color="#FFFFFF" />
            ) : (
              <Text style={styles.saveButtonText}>Kaydet</Text>
            )}
          </TouchableOpacity>
        </View>
      </SmoothModal>
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
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#1E293B',
  },
  backButton: {
    padding: 4,
    marginRight: 12,
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: Colors.textDark,
  },
  tabContainer: {
    flexDirection: 'row',
    padding: 16,
    gap: 12,
  },
  tabButton: {
    flex: 1,
    paddingVertical: 10,
    alignItems: 'center',
    borderRadius: 8,
    backgroundColor: '#1E293B',
    borderWidth: 1,
    borderColor: '#334155',
  },
  activeTabButton: {
    backgroundColor: Colors.primary,
    borderColor: Colors.primary,
  },
  tabText: {
    fontSize: 13,
    fontWeight: '600',
    color: Colors.textSecondaryDark,
  },
  activeTabText: {
    color: '#FFFFFF',
  },
  content: {
    padding: 16,
    gap: 16,
  },
  centerBox: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  statsGrid: {
    flexDirection: 'row',
    gap: 12,
  },
  statCard: {
    flex: 1,
    backgroundColor: '#1E293B',
    padding: 14,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#334155',
    alignItems: 'center',
    gap: 4,
  },
  statLabel: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
  },
  statValue: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.textDark,
  },
  measurementsCard: {
    backgroundColor: '#1E293B',
    borderRadius: 14,
    padding: 16,
    borderWidth: 1,
    borderColor: '#334155',
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  cardTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.textDark,
  },
  addButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    backgroundColor: Colors.primary,
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 6,
  },
  addButtonText: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: '600',
  },
  measurementRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: '#334155',
  },
  measurementLabel: {
    fontSize: 14,
    color: Colors.textSecondaryDark,
  },
  measurementValue: {
    fontSize: 14,
    fontWeight: '600',
    color: Colors.textDark,
  },
  historySection: {
    gap: 10,
    marginTop: 8,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.textDark,
  },
  historyItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    backgroundColor: '#1E293B',
    padding: 12,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#334155',
  },
  historyDate: {
    fontSize: 13,
    color: Colors.textDark,
  },
  historyValues: {
    fontSize: 13,
    color: Colors.primary,
    fontWeight: '500',
  },
  photoActions: {
    flexDirection: 'row',
    gap: 12,
  },
  photoBtn: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    backgroundColor: '#1E293B',
    padding: 12,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#334155',
  },
  photoBtnText: {
    fontSize: 13,
    fontWeight: '600',
    color: Colors.textDark,
  },
  photoGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
  },
  photoCard: {
    width: '48%',
    backgroundColor: '#1E293B',
    borderRadius: 10,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: '#334155',
  },
  photoImg: {
    width: '100%',
    height: 160,
  },
  photoDate: {
    fontSize: 11,
    color: Colors.textSecondaryDark,
    padding: 8,
    textAlign: 'center',
  },
  emptyPhotos: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 40,
    gap: 10,
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
    paddingHorizontal: 24,
    lineHeight: 18,
  },
  modalContent: {
    padding: 16,
    gap: 12,
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.textDark,
  },
  modalInputs: {
    gap: 10,
    paddingVertical: 6,
  },
  inputGroup: {
    gap: 4,
  },
  inputLabel: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
  },
  modalInput: {
    backgroundColor: '#0F172A',
    borderRadius: 8,
    padding: 10,
    color: Colors.textDark,
    borderWidth: 1,
    borderColor: '#334155',
    fontSize: 14,
  },
  saveButton: {
    backgroundColor: Colors.primary,
    padding: 12,
    borderRadius: 8,
    alignItems: 'center',
    marginTop: 8,
  },
  saveButtonText: {
    color: '#FFFFFF',
    fontWeight: '700',
  },
});
