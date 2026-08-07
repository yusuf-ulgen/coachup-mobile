import React, { useState } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, Modal, TextInput, SafeAreaView, Image, ActivityIndicator, Platform, Alert } from 'react-native';
import { Camera, Image as ImageIcon, Plus, X } from 'lucide-react-native';
import * as ImagePicker from 'expo-image-picker';
import { Colors } from '../../theme/colors';
import { supabase } from '../../services/supabaseClient';
import { useAuth } from '../../context/AuthContext';

export const ProgressTrackingScreen = () => {
  const { session } = useAuth();
  const [activeTab, setActiveTab] = useState<'measurements' | 'photos'>('measurements');
  const [modalVisible, setModalVisible] = useState(false);
  const [uploading, setUploading] = useState(false);

  // Sahte Veriler
  const [measurements, setMeasurements] = useState({
    weight: '75',
    bodyFat: '18',
    bmi: '24.5',
    chest: '102',
    waist: '85',
    hips: '98',
    arm: '35',
    leg: '58',
  });

  const [newMeasurement, setNewMeasurement] = useState({ ...measurements });

  const [photos, setPhotos] = useState<{ before: string | null; after: string | null }>({
    before: null,
    after: null,
  });

  const handleSaveMeasurement = () => {
    setMeasurements(newMeasurement);
    setModalVisible(false);
  };

  const uploadPhoto = async (type: 'before' | 'after', source: 'camera' | 'gallery') => {
    try {
      let result;
      if (source === 'camera') {
        const permission = await ImagePicker.requestCameraPermissionsAsync();
        if (!permission.granted) {
          Alert.alert('Hata', 'Kamera izni gerekli.');
          return;
        }
        result = await ImagePicker.launchCameraAsync({
          mediaTypes: ImagePicker.MediaTypeOptions.Images,
          quality: 0.5,
        });
      } else {
        const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
        if (!permission.granted) {
          Alert.alert('Hata', 'Galeri izni gerekli.');
          return;
        }
        result = await ImagePicker.launchImageLibraryAsync({
          mediaTypes: ImagePicker.MediaTypeOptions.Images,
          quality: 0.5,
        });
      }

      if (!result.canceled && result.assets && result.assets.length > 0) {
        setUploading(true);
        const img = result.assets[0];
        
        // Supabase Upload (Base64 or arrayBuffer)
        const ext = img.uri.substring(img.uri.lastIndexOf('.') + 1);
        const fileName = `${session?.user?.id || 'unknown'}_${type}_${Date.now()}.${ext}`;
        
        // React Native fetch implementation for file upload
        const formData = new FormData();
        formData.append('file', {
          uri: img.uri,
          name: fileName,
          type: `image/${ext}`,
        } as any);

        // Fetch to Supabase Storage (Using workaround since we can't easily fetch blob in RN without polyfills)
        // A simple way using supabase js: we need to use arraybuffer
        const res = await fetch(img.uri);
        const blob = await res.blob();
        const reader = new FileReader();
        
        reader.readAsDataURL(blob);
        reader.onloadend = async () => {
          const base64data = reader.result as string;
          // Decode base64 to array buffer
          const base64Str = base64data.split(',')[1];
          const binaryStr = atob(base64Str);
          const len = binaryStr.length;
          const bytes = new Uint8Array(len);
          for (let i = 0; i < len; i++) {
            bytes[i] = binaryStr.charCodeAt(i);
          }

          const { data, error } = await supabase
            .storage
            .from('progress_photos')
            .upload(fileName, bytes.buffer, {
              contentType: `image/${ext}`
            });

          if (error) {
            Alert.alert('Yükleme Hatası', error.message);
          } else {
            const { data: { publicUrl } } = supabase.storage.from('progress_photos').getPublicUrl(fileName);
            setPhotos(prev => ({ ...prev, [type]: publicUrl }));
          }
          setUploading(false);
        };
      }
    } catch (error: any) {
      Alert.alert('Hata', error.message);
      setUploading(false);
    }
  };

  const renderMeasurementTab = () => (
    <View>
      {/* Özet Kartı */}
      <View style={styles.summaryCard}>
        <View style={styles.summaryItem}>
          <Text style={styles.summaryValue}>{measurements.weight}</Text>
          <Text style={styles.summaryLabel}>Kilo (kg)</Text>
        </View>
        <View style={styles.summaryDivider} />
        <View style={styles.summaryItem}>
          <Text style={styles.summaryValue}>%{measurements.bodyFat}</Text>
          <Text style={styles.summaryLabel}>Yağ Oranı</Text>
        </View>
        <View style={styles.summaryDivider} />
        <View style={styles.summaryItem}>
          <Text style={styles.summaryValue}>{measurements.bmi}</Text>
          <Text style={styles.summaryLabel}>BMI</Text>
        </View>
      </View>

      {/* Vücut Ölçümleri */}
      <View style={styles.measurementsCard}>
        <View style={styles.cardHeader}>
          <Text style={styles.cardTitle}>Vücut Ölçümleri (cm)</Text>
          <TouchableOpacity onPress={() => { setNewMeasurement(measurements); setModalVisible(true); }}>
            <Plus color={Colors.primary} size={20} />
          </TouchableOpacity>
        </View>
        
        <View style={styles.measurementGrid}>
          {[
            { label: 'Göğüs', key: 'chest' },
            { label: 'Bel', key: 'waist' },
            { label: 'Kalça', key: 'hips' },
            { label: 'Kol', key: 'arm' },
            { label: 'Bacak', key: 'leg' },
          ].map((item) => (
            <View key={item.key} style={styles.measurementItem}>
              <Text style={styles.measurementLabel}>{item.label}</Text>
              <Text style={styles.measurementValue}>{measurements[item.key as keyof typeof measurements]}</Text>
            </View>
          ))}
        </View>
      </View>
    </View>
  );

  const renderPhotosTab = () => (
    <View style={styles.photosContainer}>
      {(['before', 'after'] as const).map((type) => (
        <View key={type} style={styles.photoCard}>
          <Text style={styles.photoTitle}>{type === 'before' ? 'Öncesi' : 'Sonrası'}</Text>
          <View style={styles.photoBox}>
            {photos[type] ? (
              <Image source={{ uri: photos[type]! }} style={styles.photoImage} />
            ) : (
              <View style={styles.emptyPhoto}>
                <ImageIcon color={Colors.textSecondaryDark} size={40} />
                <Text style={styles.emptyPhotoText}>Fotoğraf Yok</Text>
              </View>
            )}
          </View>
          <View style={styles.photoActions}>
            <TouchableOpacity 
              style={styles.photoButton} 
              onPress={() => uploadPhoto(type, 'camera')}
              disabled={uploading}
            >
              <Camera size={18} color={Colors.allWhite} />
              <Text style={styles.photoButtonText}>Kamera</Text>
            </TouchableOpacity>
            <TouchableOpacity 
              style={[styles.photoButton, styles.photoButtonOutline]} 
              onPress={() => uploadPhoto(type, 'gallery')}
              disabled={uploading}
            >
              <ImageIcon size={18} color={Colors.primary} />
              <Text style={[styles.photoButtonText, { color: Colors.primary }]}>Galeri</Text>
            </TouchableOpacity>
          </View>
        </View>
      ))}
      {uploading && (
        <View style={styles.loadingOverlay}>
          <ActivityIndicator size="large" color={Colors.primary} />
          <Text style={styles.loadingText}>Yükleniyor...</Text>
        </View>
      )}
    </View>
  );

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>Gelişim & Ölçümler</Text>
      </View>

      {/* Segmented Control */}
      <View style={styles.segmentedControl}>
        <TouchableOpacity 
          style={[styles.segmentButton, activeTab === 'measurements' && styles.segmentButtonActive]}
          onPress={() => setActiveTab('measurements')}
        >
          <Text style={[styles.segmentText, activeTab === 'measurements' && styles.segmentTextActive]}>Ölçümler</Text>
        </TouchableOpacity>
        <TouchableOpacity 
          style={[styles.segmentButton, activeTab === 'photos' && styles.segmentButtonActive]}
          onPress={() => setActiveTab('photos')}
        >
          <Text style={[styles.segmentText, activeTab === 'photos' && styles.segmentTextActive]}>Fotoğraflar</Text>
        </TouchableOpacity>
      </View>

      <ScrollView contentContainerStyle={styles.scrollContent}>
        {activeTab === 'measurements' ? renderMeasurementTab() : renderPhotosTab()}
      </ScrollView>

      {/* Yeni Ölçüm Modal */}
      <Modal visible={modalVisible} transparent animationType="slide">
        <View style={styles.modalOverlay}>
          <View style={styles.modalContent}>
            <View style={styles.modalHeader}>
              <Text style={styles.modalTitle}>Yeni Ölçüm Ekle</Text>
              <TouchableOpacity onPress={() => setModalVisible(false)}>
                <X color={Colors.textSecondaryDark} />
              </TouchableOpacity>
            </View>
            <ScrollView style={styles.modalScroll}>
              <View style={styles.inputGroup}>
                <Text style={styles.inputLabel}>Kilo (kg)</Text>
                <TextInput style={styles.input} keyboardType="numeric" value={newMeasurement.weight} onChangeText={t => setNewMeasurement(prev => ({...prev, weight: t}))} />
              </View>
              <View style={styles.inputGroup}>
                <Text style={styles.inputLabel}>Yağ Oranı (%)</Text>
                <TextInput style={styles.input} keyboardType="numeric" value={newMeasurement.bodyFat} onChangeText={t => setNewMeasurement(prev => ({...prev, bodyFat: t}))} />
              </View>
              <View style={styles.inputGroup}>
                <Text style={styles.inputLabel}>Göğüs (cm)</Text>
                <TextInput style={styles.input} keyboardType="numeric" value={newMeasurement.chest} onChangeText={t => setNewMeasurement(prev => ({...prev, chest: t}))} />
              </View>
              <View style={styles.inputGroup}>
                <Text style={styles.inputLabel}>Bel (cm)</Text>
                <TextInput style={styles.input} keyboardType="numeric" value={newMeasurement.waist} onChangeText={t => setNewMeasurement(prev => ({...prev, waist: t}))} />
              </View>
              <View style={styles.inputGroup}>
                <Text style={styles.inputLabel}>Kalça (cm)</Text>
                <TextInput style={styles.input} keyboardType="numeric" value={newMeasurement.hips} onChangeText={t => setNewMeasurement(prev => ({...prev, hips: t}))} />
              </View>
            </ScrollView>
            <TouchableOpacity style={styles.saveButton} onPress={handleSaveMeasurement}>
              <Text style={styles.saveButtonText}>Kaydet</Text>
            </TouchableOpacity>
          </View>
        </View>
      </Modal>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.backgroundDark,
  },
  header: {
    paddingHorizontal: 16,
    paddingTop: Platform.OS === 'android' ? 20 : 0,
    paddingBottom: 16,
  },
  headerTitle: {
    fontSize: 24,
    fontWeight: 'bold',
    color: Colors.allWhite,
  },
  segmentedControl: {
    flexDirection: 'row',
    marginHorizontal: 16,
    backgroundColor: Colors.cardDark,
    borderRadius: 12,
    padding: 4,
    marginBottom: 16,
  },
  segmentButton: {
    flex: 1,
    paddingVertical: 10,
    alignItems: 'center',
    borderRadius: 8,
  },
  segmentButtonActive: {
    backgroundColor: Colors.primary,
  },
  segmentText: {
    fontSize: 14,
    color: Colors.textSecondaryDark,
    fontWeight: '600',
  },
  segmentTextActive: {
    color: Colors.allWhite,
  },
  scrollContent: {
    padding: 16,
    paddingBottom: 40,
  },
  summaryCard: {
    flexDirection: 'row',
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 20,
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  summaryItem: {
    alignItems: 'center',
    flex: 1,
  },
  summaryValue: {
    fontSize: 24,
    fontWeight: 'bold',
    color: Colors.primary,
    marginBottom: 4,
  },
  summaryLabel: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
  },
  summaryDivider: {
    width: 1,
    height: '100%',
    backgroundColor: Colors.borderDark,
  },
  measurementsCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 20,
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  cardTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: Colors.allWhite,
  },
  measurementGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 16,
  },
  measurementItem: {
    width: '45%',
    backgroundColor: Colors.backgroundDark,
    borderRadius: 12,
    padding: 16,
  },
  measurementLabel: {
    fontSize: 14,
    color: Colors.textSecondaryDark,
    marginBottom: 8,
  },
  measurementValue: {
    fontSize: 20,
    fontWeight: 'bold',
    color: Colors.allWhite,
  },
  photosContainer: {
    gap: 20,
  },
  photoCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 16,
  },
  photoTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: Colors.allWhite,
    marginBottom: 12,
  },
  photoBox: {
    width: '100%',
    height: 250,
    backgroundColor: Colors.backgroundDark,
    borderRadius: 12,
    overflow: 'hidden',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 16,
  },
  photoImage: {
    width: '100%',
    height: '100%',
    resizeMode: 'cover',
  },
  emptyPhoto: {
    alignItems: 'center',
  },
  emptyPhotoText: {
    marginTop: 8,
    color: Colors.textSecondaryDark,
  },
  photoActions: {
    flexDirection: 'row',
    gap: 12,
  },
  photoButton: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: Colors.primary,
    paddingVertical: 12,
    borderRadius: 8,
    gap: 8,
  },
  photoButtonOutline: {
    backgroundColor: 'transparent',
    borderWidth: 1,
    borderColor: Colors.primary,
  },
  photoButtonText: {
    color: Colors.allWhite,
    fontSize: 14,
    fontWeight: '600',
  },
  loadingOverlay: {
    ...StyleSheet.absoluteFill,
    backgroundColor: 'rgba(0,0,0,0.65)',
    justifyContent: 'center',
    alignItems: 'center',
    borderRadius: 16,
    zIndex: 10,
  },
  loadingText: {
    color: Colors.allWhite,
    marginTop: 12,
    fontSize: 16,
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.6)',
    justifyContent: 'flex-end',
  },
  modalContent: {
    backgroundColor: Colors.cardDark,
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    padding: 24,
    maxHeight: '80%',
  },
  modalHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 20,
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: Colors.allWhite,
  },
  modalScroll: {
    marginBottom: 20,
  },
  inputGroup: {
    marginBottom: 16,
  },
  inputLabel: {
    fontSize: 14,
    color: Colors.textSecondaryDark,
    marginBottom: 8,
  },
  input: {
    backgroundColor: Colors.backgroundDark,
    borderRadius: 8,
    padding: 12,
    color: Colors.allWhite,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  saveButton: {
    backgroundColor: Colors.primary,
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
  },
  saveButtonText: {
    color: Colors.allWhite,
    fontSize: 16,
    fontWeight: 'bold',
  },
});
