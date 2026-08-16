import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
} from 'react-native';
import { feedback } from '../../services/feedbackService';
import { ArrowLeft, Moon, Sun, Monitor, Check } from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { AuthService } from '../../services/authService';
import { UserService } from '../../services/userService';

interface AppearanceSettingsScreenProps {
  navigation: any;
}

import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTheme } from '../../theme/ThemeContext';

export const AppearanceSettingsScreen: React.FC<AppearanceSettingsScreenProps> = ({
  navigation,
}) => {
  const insets = useSafeAreaInsets();
  const { themeMode, setThemeMode } = useTheme();
  const [userId, setUserId] = useState<string | null>(null);

  useEffect(() => {
    const loadProfile = async () => {
      try {
        const profile = await AuthService.getCurrentProfile();
        if (profile) {
          setUserId(profile.id);
        }
      } catch (e) {
        console.error('Error loading profile:', e);
      }
    };
    loadProfile();
  }, []);

  const handleSelectTheme = async (mode: 'dark' | 'light' | 'system') => {
    setThemeMode(mode);
    if (userId) {
      try {
        await UserService.updateUserProfile(userId, { theme_mode: mode });
      } catch (e: any) {
        console.warn('Could not save theme_mode to DB profile:', e);
        feedback.error({
          title: 'Hata',
          message: e,
          fallbackMessage: 'Tema ayarı kaydedilemedi.',
        });
      }
    }
  };

  const themes = [
    {
      id: 'dark',
      title: 'Karanlık Tema (Koyu)',
      desc: 'CoachUp varsayılan sporcu teması',
      icon: Moon,
    },
    {
      id: 'light',
      title: 'Açık Tema (Aydınlık)',
      desc: 'Gündüz kullanımı için açık arka plan',
      icon: Sun,
    },
    {
      id: 'system',
      title: 'Sistem Teması',
      desc: 'Cihazınızın sistem ayarlarına uyum sağlar',
      icon: Monitor,
    },
  ];

  return (
    <View style={styles.container}>
      <View style={[styles.header, { paddingTop: Math.max(16, insets.top + 8) }]}>
        <TouchableOpacity
          style={styles.backButton}
          onPress={() => navigation.goBack()}
        >
          <ArrowLeft size={20} color={Colors.textDark} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Görünüm ve Tema</Text>
        <View style={{ width: 40 }} />
      </View>

      <View style={styles.content}>
        <Text style={styles.description}>
          Uygulamanın renk temasını kişiselleştirin.
        </Text>

        {themes.map((item) => {
          const IconComp = item.icon;
          const isSelected = themeMode === item.id;
          return (
            <TouchableOpacity
              key={item.id}
              style={[styles.themeCard, isSelected && styles.themeCardSelected]}
              onPress={() => handleSelectTheme(item.id as any)}
              activeOpacity={0.8}
            >
              <View style={styles.iconBox}>
                <IconComp size={24} color={isSelected ? Colors.primary : Colors.textDark} />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.themeTitle}>{item.title}</Text>
                <Text style={styles.themeDesc}>{item.desc}</Text>
              </View>
              {isSelected && (
                <View style={styles.checkCircle}>
                  <Check size={16} color={Colors.allWhite} />
                </View>
              )}
            </TouchableOpacity>
          );
        })}
      </View>
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
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingTop: 50,
    paddingBottom: 16,
    borderBottomWidth: 1,
    borderBottomColor: Colors.borderDark,
  },
  backButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: Colors.cardDark,
    justifyContent: 'center',
    alignItems: 'center',
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.textDark,
  },
  content: {
    padding: 20,
  },
  description: {
    fontSize: 14,
    color: Colors.textSecondaryDark,
    marginBottom: 24,
  },
  themeCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 16,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: Colors.borderDark,
    gap: 16,
  },
  themeCardSelected: {
    borderColor: Colors.primary,
    borderWidth: 2,
  },
  iconBox: {
    width: 44,
    height: 44,
    borderRadius: 12,
    backgroundColor: Colors.backgroundDark,
    justifyContent: 'center',
    alignItems: 'center',
  },
  themeTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: Colors.textDark,
  },
  themeDesc: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    marginTop: 2,
  },
  checkCircle: {
    width: 24,
    height: 24,
    borderRadius: 12,
    backgroundColor: Colors.primary,
    justifyContent: 'center',
    alignItems: 'center',
  },
});
