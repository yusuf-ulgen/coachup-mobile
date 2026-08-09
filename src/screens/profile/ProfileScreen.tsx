import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  TextInput,
  ActivityIndicator,
  Image,
} from 'react-native';
import { feedback } from '../../services/feedbackService';
import { Colors } from '../../theme/colors';
import { useAuth } from '../../context/AuthContext';
import { GYM_CONFIG } from '../../config/gym';
import { UserService } from '../../services/userService';
import * as ImagePicker from 'expo-image-picker';
import { supabase } from '../../services/supabaseClient';
import { ScreenContainer } from '../../components/ScreenContainer';
import { Camera, LogOut, Shield, MapPin, User as UserIcon, Lock, Calendar, ArrowLeft } from 'lucide-react-native';
import { DateTimePickerModal } from '../../components/DateTimePickerModal';

export const ProfileScreen: React.FC<{ navigation?: any }> = ({ navigation }) => {
  const { user, profile, signOut, refreshProfile } = useAuth();

  const [name, setName] = useState(profile?.name || '');
  const [surname, setSurname] = useState(profile?.surname || '');
  const [phone, setPhone] = useState(profile?.phone || '');
  const [gender, setGender] = useState(profile?.gender || 'male');
  const [height, setHeight] = useState(profile?.height_cm?.toString() || '');
  const [weight, setWeight] = useState(profile?.weight_kg?.toString() || '');
  const [birthDate, setBirthDate] = useState(profile?.birth_date || '');
  const [showDatePicker, setShowDatePicker] = useState(false);
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

  // Determine active gym display dynamically from profile state
  const activeGymDisplay = profile?.is_individual
    ? 'Bireysel Üyelik'
    : profile?.gym_name || GYM_CONFIG.GYM_NAME;

  const fullName = `${name} ${surname}`.trim() || 'Kullanıcı';

  return (
    <ScreenContainer includeTopInset={true} includeBottomInset={true}>
      <ScrollView style={styles.container} contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: 16 }}>
          <TouchableOpacity onPress={() => navigation?.goBack()} style={{ paddingRight: 12, paddingVertical: 4 }}>
            <ArrowLeft size={22} color={Colors.textDark} />
          </TouchableOpacity>
          <Text style={[styles.headerTitle, { marginBottom: 0 }]}>Profilim</Text>
        </View>

        {/* ── Modern Sleek Profile Hero Card ────────────────────────── */}
        <View style={styles.heroCard}>
          <TouchableOpacity style={styles.avatarLarge} onPress={handlePickImage} disabled={uploading} activeOpacity={0.85}>
            {uploading ? (
              <ActivityIndicator color={Colors.allWhite} />
            ) : avatarUrl ? (
              <Image source={{ uri: avatarUrl }} style={styles.avatarImage} />
            ) : (
              <Text style={styles.avatarLargeText}>{name?.[0]?.toUpperCase() || 'C'}</Text>
            )}
            <View style={styles.editIconBadge}>
              <Camera size={14} color={Colors.allWhite} />
            </View>
          </TouchableOpacity>

          <Text style={styles.heroName}>{fullName}</Text>
          <Text style={styles.heroEmail}>{user?.email || 'eposta@coachup.app'}</Text>

          <View style={styles.badgeRow}>
            <View style={styles.roleBadge}>
              <Shield size={12} color={Colors.primary} style={{ marginRight: 4 }} />
              <Text style={styles.roleBadgeText}>
                {profile?.role === 'admin' ? 'Yönetici' : 'Üye'}
              </Text>
            </View>
            <View style={styles.gymBadge}>
              <MapPin size={12} color={Colors.allWhite} style={{ marginRight: 4 }} />
              <Text style={styles.gymBadgeText}>{activeGymDisplay}</Text>
            </View>
          </View>
        </View>

        {/* ── Personal Details Section Card ───────────────────────── */}
        <View style={styles.sectionCard}>
          <Text style={styles.sectionHeaderTitle}>Kişisel Bilgiler</Text>

          <View style={styles.inputGroup}>
            <View style={styles.labelRow}>
              <Text style={styles.label}>E-posta</Text>
              <View style={styles.lockBadge}>
                <Lock size={10} color={Colors.textSecondaryDark} />
                <Text style={styles.lockBadgeText}>Değiştirilemez</Text>
              </View>
            </View>
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
        </View>

        {/* ── Body Measurement Section Card ────────────────────────── */}
        <View style={styles.sectionCard}>
          <Text style={styles.sectionHeaderTitle}>Vücut & Cinsiyet</Text>

          <View style={styles.inputGroup}>
            <Text style={styles.label}>Cinsiyet</Text>
            <View style={styles.genderRow}>
              <TouchableOpacity
                style={[styles.genderBtn, gender === 'male' && styles.genderBtnActive]}
                onPress={() => setGender('male')}
                activeOpacity={0.8}
              >
                <Text style={[styles.genderBtnText, gender === 'male' && styles.genderBtnTextActive]}>Erkek</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.genderBtn, gender === 'female' && styles.genderBtnActive]}
                onPress={() => setGender('female')}
                activeOpacity={0.8}
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
            <Text style={styles.label}>Doğum Tarihi</Text>
            <TouchableOpacity
              onPress={() => setShowDatePicker(true)}
              style={[styles.input, { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }]}
            >
              <Text style={{ color: birthDate ? Colors.textDark : Colors.textSecondaryDark }}>
                {birthDate || 'YYYY-MM-DD'}
              </Text>
              <Calendar size={18} color={Colors.primary} />
            </TouchableOpacity>
          </View>

          <DateTimePickerModal
            visible={showDatePicker}
            mode="date"
            title="Doğum Tarihi Seç"
            initialValue={birthDate}
            onConfirm={(d) => {
              setBirthDate(d);
              setShowDatePicker(false);
            }}
            onCancel={() => setShowDatePicker(false)}
          />
        </View>

        {/* ── Action Buttons ────────────────────────────────────────── */}
        <TouchableOpacity 
          style={styles.saveBtn} 
          onPress={handleSave} 
          disabled={loading}
          activeOpacity={0.85}
        >
          {loading ? (
            <ActivityIndicator color={Colors.allWhite} />
          ) : (
            <Text style={styles.saveBtnText}>Değişiklikleri Kaydet</Text>
          )}
        </TouchableOpacity>

        <TouchableOpacity style={styles.signOutCard} onPress={signOut} activeOpacity={0.8}>
          <LogOut size={20} color={Colors.error} style={{ marginRight: 10 }} />
          <Text style={styles.signOutText}>Oturumu Kapat</Text>
        </TouchableOpacity>
      </ScrollView>
    </ScreenContainer>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    paddingHorizontal: 20,
    paddingTop: 16,
    paddingBottom: 40,
    gap: 16,
  },
  headerTitle: {
    fontSize: 26,
    fontWeight: '800',
    color: Colors.textDark,
    letterSpacing: -0.5,
    marginBottom: 4,
  },
  heroCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 24,
    padding: 24,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: Colors.borderDark,
    elevation: 4,
  },
  avatarLarge: {
    width: 90,
    height: 90,
    borderRadius: 45,
    backgroundColor: Colors.primary,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 14,
    position: 'relative',
    borderWidth: 3,
    borderColor: 'rgba(255, 96, 71, 0.3)',
  },
  avatarImage: {
    width: 84,
    height: 84,
    borderRadius: 42,
  },
  avatarLargeText: {
    color: Colors.allWhite,
    fontWeight: '900',
    fontSize: 36,
  },
  editIconBadge: {
    position: 'absolute',
    right: 0,
    bottom: 0,
    backgroundColor: Colors.primary,
    borderRadius: 14,
    width: 28,
    height: 28,
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 2,
    borderColor: Colors.cardDark,
  },
  heroName: {
    fontSize: 22,
    fontWeight: '800',
    color: Colors.textDark,
    textAlign: 'center',
  },
  heroEmail: {
    fontSize: 14,
    color: Colors.textSecondaryDark,
    marginTop: 4,
    marginBottom: 14,
  },
  badgeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    flexWrap: 'wrap',
    justifyContent: 'center',
  },
  roleBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.primaryLight,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: 'rgba(255, 96, 71, 0.25)',
  },
  roleBadgeText: {
    color: Colors.primary,
    fontWeight: '700',
    fontSize: 12,
  },
  gymBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.08)',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.12)',
  },
  gymBadgeText: {
    color: Colors.allWhite,
    fontWeight: '600',
    fontSize: 12,
  },
  sectionCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 20,
    padding: 20,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  sectionHeaderTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.textDark,
    marginBottom: 16,
    letterSpacing: -0.2,
  },
  row: {
    flexDirection: 'row',
  },
  inputGroup: {
    marginBottom: 16,
  },
  labelRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  label: {
    color: Colors.textSecondaryDark,
    fontSize: 13,
    fontWeight: '600',
  },
  lockBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  lockBadgeText: {
    fontSize: 11,
    color: Colors.textSecondaryDark,
  },
  input: {
    backgroundColor: 'rgba(0, 0, 0, 0.25)',
    borderWidth: 1,
    borderColor: Colors.borderDark,
    borderRadius: 14,
    paddingHorizontal: 16,
    paddingVertical: 14,
    color: Colors.textDark,
    fontSize: 15,
  },
  inputDisabled: {
    backgroundColor: 'rgba(255, 255, 255, 0.04)',
    color: 'rgba(250, 249, 248, 0.7)',
    borderColor: 'rgba(255, 255, 255, 0.08)',
  },
  genderRow: {
    flexDirection: 'row',
    gap: 12,
  },
  genderBtn: {
    flex: 1,
    paddingVertical: 14,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: Colors.borderDark,
    backgroundColor: 'rgba(0, 0, 0, 0.25)',
    alignItems: 'center',
  },
  genderBtnActive: {
    borderColor: Colors.primary,
    backgroundColor: Colors.primaryLight,
  },
  genderBtnText: {
    color: Colors.textSecondaryDark,
    fontWeight: '600',
    fontSize: 15,
  },
  genderBtnTextActive: {
    color: Colors.primary,
    fontWeight: '700',
  },
  saveBtn: {
    backgroundColor: Colors.primary,
    borderRadius: 18,
    paddingVertical: 16,
    alignItems: 'center',
    marginTop: 4,
    elevation: 4,
  },
  saveBtnText: {
    color: Colors.allWhite,
    fontSize: 16,
    fontWeight: '700',
  },
  signOutCard: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(176, 0, 32, 0.08)',
    borderRadius: 18,
    paddingVertical: 16,
    borderWidth: 1,
    borderColor: 'rgba(176, 0, 32, 0.2)',
  },
  signOutText: {
    color: Colors.error,
    fontSize: 16,
    fontWeight: '700',
  },
});
