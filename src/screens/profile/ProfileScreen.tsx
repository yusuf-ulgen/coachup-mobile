import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView, TextInput, ActivityIndicator, Image } from 'react-native';
import { feedback } from '../../services/feedbackService';
import { Colors } from '../../theme/colors';
import { useAuth } from '../../context/AuthContext';
import { GYM_CONFIG } from '../../config/gym';
import { UserService } from '../../services/userService';
import * as ImagePicker from 'expo-image-picker';
import { supabase } from '../../services/supabaseClient';

export const ProfileScreen: React.FC = () => {
  const { user, profile, signOut, refreshProfile } = useAuth();

  const [name, setName] = useState(profile?.name || '');
  const [surname, setSurname] = useState(profile?.surname || '');
  const [phone, setPhone] = useState(profile?.phone || '');
  const [gender, setGender] = useState(profile?.gender || 'male');
  const [height, setHeight] = useState(profile?.height_cm?.toString() || '');
  const [weight, setWeight] = useState(profile?.weight_kg?.toString() || '');
  const [birthDate, setBirthDate] = useState(profile?.birth_date || '');
  const [avatarUrl, setAvatarUrl] = useState(profile?.avatar_url || profile?.profile_image_url || '');
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);

  useEffect(() => {
    if (profile) {
      setName(profile.name || '');
      setSurname(profile.surname || '');
      setPhone(profile.phone || '');
      setGender(profile.gender || 'male');
      setHeight(profile.height_cm?.toString() || '');
      setWeight(profile.weight_kg?.toString() || '');
      setBirthDate(profile.birth_date || '');
      setAvatarUrl(profile.avatar_url || profile.profile_image_url || '');
    }
  }, [profile]);

  const handlePickImage = async () => {
    try {
      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: ['images'],
        allowsEditing: true,
        aspect: [1, 1],
        quality: 0.8,
      });

      if (!result.canceled && result.assets && result.assets.length > 0) {
        uploadAvatar(result.assets[0].uri);
      }
    } catch (error) {
      console.error(error);
      feedback.error({ title: 'Hata', message: error, fallbackMessage: 'Görsel seçilirken bir hata oluştu.' });
    }
  };

  const uploadAvatar = async (uri: string) => {
    try {
      if (!user) return;
      setUploading(true);
      
      const response = await fetch(uri);
      const blob = await response.blob();
      
      const fileName = `avatar-${user.id}-${Date.now()}.jpg`;
      const filePath = `${user.id}/${fileName}`;
      
      const { error } = await supabase.storage
        .from('avatars')
        .upload(filePath, blob, { contentType: 'image/jpeg' });
        
      if (error) throw error;
      
      const { data: { publicUrl } } = supabase.storage
        .from('avatars')
        .getPublicUrl(filePath);
        
      await UserService.updateUserProfile(user.id, { avatar_url: publicUrl });
      setAvatarUrl(publicUrl);
      refreshProfile();
      feedback.success({ title: 'Başarılı', message: 'Profil fotoğrafınız güncellendi.' });
    } catch (error) {
      console.error(error);
      feedback.error({ title: 'Hata', message: error, fallbackMessage: 'Fotoğraf yüklenemedi.' });
    } finally {
      setUploading(false);
    }
  };

  const handleSave = async () => {
    if (!user) return;
    try {
      setLoading(true);
      await UserService.updateUserProfile(user.id, {
        name,
        surname,
        phone,
        gender,
        height_cm: height ? parseFloat(height) : undefined,
        weight_kg: weight ? parseFloat(weight) : undefined,
        birth_date: birthDate || undefined,
      });
      await refreshProfile();
      feedback.success({ title: 'Başarılı', message: 'Değişiklikler kaydedildi.' });
    } catch (error) {
      console.error(error);
      feedback.error({ title: 'Hata', message: error, fallbackMessage: 'Profil güncellenirken bir hata oluştu.' });
    } finally {
      setLoading(false);
    }
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Text style={styles.headerTitle}>Profilim</Text>

      <View style={styles.profileCard}>
        <TouchableOpacity style={styles.avatarLarge} onPress={handlePickImage} disabled={uploading}>
          {uploading ? (
            <ActivityIndicator color={Colors.allWhite} />
          ) : avatarUrl ? (
            <Image source={{ uri: avatarUrl }} style={styles.avatarImage} />
          ) : (
            <Text style={styles.avatarLargeText}>{name?.[0]?.toUpperCase() || 'S'}</Text>
          )}
          <View style={styles.editIconBadge}>
            <Text style={styles.editIconText}>📷</Text>
          </View>
        </TouchableOpacity>
        <Text style={styles.emailText}>{user?.email || 'eposta@coachup.app'}</Text>
        <View style={styles.roleBadge}>
          <Text style={styles.roleBadgeText}>
            {profile?.role === 'admin' ? 'Yönetici' : 'Üye'} - {GYM_CONFIG.GYM_NAME}
          </Text>
        </View>
      </View>

      <View style={styles.formSection}>
        <View style={styles.inputGroup}>
          <Text style={styles.label}>E-posta (Değiştirilemez)</Text>
          <TextInput
            style={[styles.input, styles.inputDisabled]}
            value={user?.email || ''}
            editable={false}
          />
        </View>

        <View style={styles.row}>
          <View style={[styles.inputGroup, { flex: 1, marginRight: 8 }]}>
            <Text style={styles.label}>Ad</Text>
            <TextInput
              style={styles.input}
              value={name}
              onChangeText={setName}
              placeholder="Adınız"
              placeholderTextColor={Colors.textSecondaryDark}
            />
          </View>
          <View style={[styles.inputGroup, { flex: 1, marginLeft: 8 }]}>
            <Text style={styles.label}>Soyad</Text>
            <TextInput
              style={styles.input}
              value={surname}
              onChangeText={setSurname}
              placeholder="Soyadınız"
              placeholderTextColor={Colors.textSecondaryDark}
            />
          </View>
        </View>

        <View style={styles.inputGroup}>
          <Text style={styles.label}>Telefon</Text>
          <TextInput
            style={styles.input}
            value={phone}
            onChangeText={setPhone}
            placeholder="05XX XXX XX XX"
            keyboardType="phone-pad"
            placeholderTextColor={Colors.textSecondaryDark}
          />
        </View>

        <View style={styles.inputGroup}>
          <Text style={styles.label}>Cinsiyet</Text>
          <View style={styles.genderRow}>
            <TouchableOpacity
              style={[styles.genderBtn, gender === 'male' && styles.genderBtnActive]}
              onPress={() => setGender('male')}
            >
              <Text style={[styles.genderBtnText, gender === 'male' && styles.genderBtnTextActive]}>Erkek</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.genderBtn, gender === 'female' && styles.genderBtnActive]}
              onPress={() => setGender('female')}
            >
              <Text style={[styles.genderBtnText, gender === 'female' && styles.genderBtnTextActive]}>Kadın</Text>
            </TouchableOpacity>
          </View>
        </View>

        <View style={styles.row}>
          <View style={[styles.inputGroup, { flex: 1, marginRight: 8 }]}>
            <Text style={styles.label}>Boy (cm)</Text>
            <TextInput
              style={styles.input}
              value={height}
              onChangeText={setHeight}
              placeholder="175"
              keyboardType="numeric"
              placeholderTextColor={Colors.textSecondaryDark}
            />
          </View>
          <View style={[styles.inputGroup, { flex: 1, marginLeft: 8 }]}>
            <Text style={styles.label}>Kilo (kg)</Text>
            <TextInput
              style={styles.input}
              value={weight}
              onChangeText={setWeight}
              placeholder="70"
              keyboardType="numeric"
              placeholderTextColor={Colors.textSecondaryDark}
            />
          </View>
        </View>

        <View style={styles.inputGroup}>
          <Text style={styles.label}>Doğum Tarihi (YYYY-AA-GG)</Text>
          <TextInput
            style={styles.input}
            value={birthDate}
            onChangeText={setBirthDate}
            placeholder="1990-01-01"
            placeholderTextColor={Colors.textSecondaryDark}
          />
        </View>

        <TouchableOpacity 
          style={styles.saveBtn} 
          onPress={handleSave} 
          disabled={loading}
        >
          {loading ? (
            <ActivityIndicator color={Colors.allWhite} />
          ) : (
            <Text style={styles.saveBtnText}>Değişiklikleri Kaydet</Text>
          )}
        </TouchableOpacity>
      </View>

      <View style={styles.menuSection}>
        <TouchableOpacity style={styles.signOutItem} onPress={signOut}>
          <Text style={styles.signOutText}>🚪 Oturumu Kapat</Text>
        </TouchableOpacity>
      </View>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.backgroundDark,
  },
  content: {
    paddingTop: 60,
    paddingHorizontal: 20,
    paddingBottom: 40,
  },
  headerTitle: {
    fontSize: 24,
    fontWeight: '800',
    color: Colors.textDark,
    marginBottom: 20,
  },
  profileCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 24,
    padding: 24,
    alignItems: 'center',
    marginBottom: 24,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  avatarLarge: {
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: Colors.primary,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 16,
    position: 'relative',
  },
  avatarImage: {
    width: 80,
    height: 80,
    borderRadius: 40,
  },
  avatarLargeText: {
    color: Colors.allWhite,
    fontWeight: '900',
    fontSize: 32,
  },
  editIconBadge: {
    position: 'absolute',
    right: 0,
    bottom: 0,
    backgroundColor: Colors.cardDark,
    borderRadius: 12,
    width: 24,
    height: 24,
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  editIconText: {
    fontSize: 12,
  },
  emailText: {
    fontSize: 14,
    color: Colors.textSecondaryDark,
    marginTop: 4,
    marginBottom: 12,
  },
  roleBadge: {
    backgroundColor: Colors.primaryLight,
    paddingHorizontal: 14,
    paddingVertical: 6,
    borderRadius: 20,
  },
  roleBadgeText: {
    color: Colors.primary,
    fontWeight: '700',
    fontSize: 12,
  },
  formSection: {
    marginBottom: 24,
  },
  row: {
    flexDirection: 'row',
  },
  inputGroup: {
    marginBottom: 16,
  },
  label: {
    color: Colors.textSecondaryDark,
    fontSize: 14,
    marginBottom: 8,
    fontWeight: '600',
  },
  input: {
    backgroundColor: Colors.cardDark,
    borderWidth: 1,
    borderColor: Colors.borderDark,
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 14,
    color: Colors.textDark,
    fontSize: 16,
  },
  inputDisabled: {
    opacity: 0.5,
  },
  genderRow: {
    flexDirection: 'row',
    gap: 12,
  },
  genderBtn: {
    flex: 1,
    paddingVertical: 14,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: Colors.borderDark,
    backgroundColor: Colors.cardDark,
    alignItems: 'center',
  },
  genderBtnActive: {
    borderColor: Colors.primary,
    backgroundColor: Colors.primaryLight,
  },
  genderBtnText: {
    color: Colors.textSecondaryDark,
    fontWeight: '600',
    fontSize: 16,
  },
  genderBtnTextActive: {
    color: Colors.primary,
  },
  saveBtn: {
    backgroundColor: Colors.primary,
    borderRadius: 16,
    paddingVertical: 16,
    alignItems: 'center',
    marginTop: 8,
  },
  saveBtnText: {
    color: Colors.allWhite,
    fontSize: 16,
    fontWeight: '700',
  },
  menuSection: {
    backgroundColor: Colors.cardDark,
    borderRadius: 20,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  signOutItem: {
    paddingVertical: 18,
    paddingHorizontal: 20,
  },
  signOutText: {
    color: Colors.error,
    fontSize: 16,
    fontWeight: '700',
  },
});
