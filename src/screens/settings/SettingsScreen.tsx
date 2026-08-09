import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Switch,
  ScrollView,
} from 'react-native';
import { feedback } from '../../services/feedbackService';
import {
  ChevronRight,
  Home,
  Bell,
  Fingerprint,
  Scale,
  MapPin,
  Lock,
} from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { Header } from '../../components/Header';
import { SideMenu } from '../../components/SideMenu';
import { AuthService } from '../../services/authService';
import { UserService } from '../../services/userService';

interface SettingsScreenProps {
  navigation: any;
}

import AsyncStorage from '@react-native-async-storage/async-storage';
import * as LocalAuthentication from 'expo-local-authentication';

export const SettingsScreen: React.FC<SettingsScreenProps> = ({ navigation }) => {
  const [menuVisible, setMenuVisible] = useState(false);
  const [userProfile, setUserProfile] = useState<any>(null);

  // Settings State
  const [defaultScreen, setDefaultScreen] = useState<string>('home');
  const [notificationsEnabled, setNotificationsEnabled] = useState(true);
  const [biometricsEnabled, setBiometricsEnabled] = useState(false);
  const [weightUnit, setWeightUnit] = useState<'kg' | 'lbs'>('kg');

  useEffect(() => {
    const loadSettings = async () => {
      try {
        const localDefault = await AsyncStorage.getItem('@user_default_screen');
        if (localDefault) setDefaultScreen(localDefault);

        const storedBio = await AsyncStorage.getItem('@app_setting_biometrics_enabled');
        if (storedBio !== null) {
          setBiometricsEnabled(JSON.parse(storedBio));
        }

        const profile = await AuthService.getCurrentProfile();
        if (profile) {
          setUserProfile(profile);
          if (profile.default_screen) setDefaultScreen(profile.default_screen);
          if (profile.weight_unit) setWeightUnit(profile.weight_unit);
          if (profile.notifications_enabled !== undefined) {
            setNotificationsEnabled(profile.notifications_enabled);
          }
          if (storedBio === null && profile.biometrics_enabled !== undefined) {
            setBiometricsEnabled(profile.biometrics_enabled);
          }
        }
      } catch (e) {
        console.error('Error loading settings:', e);
      }
    };
    loadSettings();
  }, []);

  const saveSetting = async (key: string, value: any) => {
    setUserProfile((prev: any) => ({ ...prev, [key]: value }));
    try {
      await AsyncStorage.setItem(`@app_setting_${key}`, JSON.stringify(value));
      if (key === 'default_screen') {
        await AsyncStorage.setItem('@user_default_screen', value);
      }
    } catch {}

    try {
      if (userProfile?.id) {
        await UserService.updateUserProfile(userProfile.id, { [key]: value });
      }
    } catch (e: any) {
      console.log(`Note: ${key} DB sync skipped or column missing in schema cache.`);
    }
  };

  const handleDefaultScreenChange = (screen: string) => {
    setDefaultScreen(screen);
    saveSetting('default_screen', screen);
    feedback.toast('Varsayılan başlangıç ekranı güncellendi.', 'success');
  };

  const handleNotificationsToggle = (val: boolean) => {
    setNotificationsEnabled(val);
    saveSetting('notifications_enabled', val);
  };

  const handleBiometricsToggle = async (val: boolean) => {
    if (val) {
      try {
        const hasHardware = await LocalAuthentication.hasHardwareAsync();
        const isEnrolled = await LocalAuthentication.isEnrolledAsync();

        if (!hasHardware || !isEnrolled) {
          setBiometricsEnabled(false);
          await AsyncStorage.setItem('@app_setting_biometrics_enabled', JSON.stringify(false));
          feedback.warning({
            title: 'Biyometrik Veri Bulunamadı',
            message: 'Telefonunuzda kayıtlı biyometrik veri (Parmak izi veya Face ID) bulunamadı. Lütfen telefon ayarlarınızdan biyometrik veri kaydedip sonra tekrar deneyin.',
          });
          return;
        }

        const result = await LocalAuthentication.authenticateAsync({
          promptMessage: 'Biyometrik girişi aktifleştirmek için doğrulama yapın',
          fallbackLabel: 'Şifre Kullan',
        });

        if (result.success) {
          setBiometricsEnabled(true);
          await saveSetting('biometrics_enabled', true);
          feedback.toast('Biyometrik giriş başarıyla aktif edildi.', 'success');
        } else {
          setBiometricsEnabled(false);
          await AsyncStorage.setItem('@app_setting_biometrics_enabled', JSON.stringify(false));
          feedback.warning({
            title: 'Doğrulama Başarısız',
            message: 'Biyometrik doğrulama tamamlanamadı.',
          });
        }
      } catch (e) {
        setBiometricsEnabled(false);
        await AsyncStorage.setItem('@app_setting_biometrics_enabled', JSON.stringify(false));
        feedback.warning({
          title: 'Biyometrik Veri Bulunamadı',
          message: 'Telefonunuzda kayıtlı biyometrik veri bulunamadı. Lütfen telefon ayarlarınızdan biyometrik veri ekleyip tekrar deneyin.',
        });
      }
    } else {
      setBiometricsEnabled(false);
      await saveSetting('biometrics_enabled', false);
      feedback.toast('Biyometrik giriş kapatıldı.', 'info');
    }
  };

  const handleWeightUnitToggle = (unit: 'kg' | 'lbs') => {
    setWeightUnit(unit);
    saveSetting('weight_unit', unit);
  };

  return (
    <View style={styles.container}>
      <Header
        title="Ayarlar"
        onMenuPress={() => setMenuVisible(true)}
        onNotificationPress={() => navigation.navigate('Notifications')}
      />

      <ScrollView contentContainerStyle={styles.scrollContent}>
        {/* Section: Tercihler */}
        <Text style={styles.sectionHeader}>GENEL TERCİHLER</Text>

        {/* Default Screen */}
        <View style={styles.card}>
          <View style={styles.rowHeader}>
            <Home size={20} color={Colors.primary} />
            <Text style={styles.rowTitle}>Varsayılan Başlangıç Ekranı</Text>
          </View>
          <View style={styles.optionsRow}>
            {[
              { id: 'home', label: 'Anasayfa' },
              { id: 'calendar', label: 'Takvim' },
              { id: 'training', label: 'Antrenman' },
              { id: 'qr', label: 'QR Tarama' },
            ].map((item) => (
              <TouchableOpacity
                key={item.id}
                style={[
                  styles.chip,
                  defaultScreen === item.id && styles.chipSelected,
                ]}
                onPress={() => handleDefaultScreenChange(item.id)}
              >
                <Text
                  style={[
                    styles.chipText,
                    defaultScreen === item.id && styles.chipTextSelected,
                  ]}
                >
                  {item.label}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        {/* Notifications Toggle */}
        <View style={styles.cardRow}>
          <View style={styles.rowHeader}>
            <Bell size={20} color={Colors.primary} />
            <Text style={styles.rowTitle}>Anlık Bildirimler</Text>
          </View>
          <Switch
            value={notificationsEnabled}
            onValueChange={handleNotificationsToggle}
            trackColor={{ false: Colors.borderDark, true: Colors.primary }}
            thumbColor={Colors.allWhite}
          />
        </View>

        {/* Biometrics Toggle */}
        <View style={styles.cardRow}>
          <View style={[styles.rowHeader, { flex: 1, marginRight: 8 }]}>
            <Fingerprint size={20} color={Colors.primary} />
            <Text style={styles.rowTitle}>Biyometrik Giriş</Text>
          </View>
          <Switch
            value={biometricsEnabled}
            onValueChange={handleBiometricsToggle}
            trackColor={{ false: Colors.borderDark, true: Colors.primary }}
            thumbColor={Colors.allWhite}
          />
        </View>

        {/* Weight Unit Selection */}
        <View style={styles.card}>
          <View style={styles.rowHeader}>
            <Scale size={20} color={Colors.primary} />
            <Text style={styles.rowTitle}>Ağırlık Birimi</Text>
          </View>
          <View style={styles.optionsRow}>
            {[
              { id: 'kg', label: 'Kilogram (kg)' },
              { id: 'lbs', label: 'Pound (lbs)' },
            ].map((unit) => (
              <TouchableOpacity
                key={unit.id}
                style={[
                  styles.chip,
                  weightUnit === unit.id && styles.chipSelected,
                ]}
                onPress={() => handleWeightUnitToggle(unit.id as 'kg' | 'lbs')}
              >
                <Text
                  style={[
                    styles.chipText,
                    weightUnit === unit.id && styles.chipTextSelected,
                  ]}
                >
                  {unit.label}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        {/* Section: HESAP VE GÜVENLİK */}
        <Text style={styles.sectionHeader}>HESAP VE UYGULAMA</Text>

        {/* Address Link */}
        <TouchableOpacity
          style={styles.navRow}
          onPress={() => navigation.navigate('AddressSettings')}
        >
          <View style={styles.rowHeader}>
            <MapPin size={20} color={Colors.primary} />
            <Text style={styles.rowTitle}>Adres Bilgilerim</Text>
          </View>
          <ChevronRight size={20} color={Colors.textSecondaryDark} />
        </TouchableOpacity>

        {/* Password Link */}
        <TouchableOpacity
          style={styles.navRow}
          onPress={() => navigation.navigate('PasswordSettings')}
        >
          <View style={styles.rowHeader}>
            <Lock size={20} color={Colors.primary} />
            <Text style={styles.rowTitle}>Şifre Değiştir</Text>
          </View>
          <ChevronRight size={20} color={Colors.textSecondaryDark} />
        </TouchableOpacity>


      </ScrollView>

      <SideMenu
        visible={menuVisible}
        onClose={() => setMenuVisible(false)}
        userProfile={userProfile}
        onNavigate={(route) => navigation.navigate(route)}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.backgroundDark,
  },
  scrollContent: {
    padding: 20,
    paddingBottom: 40,
  },
  sectionHeader: {
    fontSize: 13,
    fontWeight: '700',
    color: Colors.textSecondaryDark,
    marginTop: 16,
    marginBottom: 10,
    letterSpacing: 0.8,
  },
  card: {
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 16,
    marginBottom: 12,
  },
  cardRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 16,
    marginBottom: 12,
  },
  navRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 16,
    marginBottom: 12,
  },
  rowHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  rowTitle: {
    fontSize: 15,
    fontWeight: '600',
    color: Colors.textDark,
  },
  optionsRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginTop: 12,
  },
  chip: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 20,
    backgroundColor: Colors.backgroundDark,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  chipSelected: {
    backgroundColor: Colors.primary,
    borderColor: Colors.primary,
  },
  chipText: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    fontWeight: '500',
  },
  chipTextSelected: {
    color: Colors.allWhite,
    fontWeight: '600',
  },
});
