import React, { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
  ScrollView,
} from 'react-native';
import { feedback } from '../../services/feedbackService';
import { Eye, EyeOff, Lock, Check, ArrowLeft } from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { supabase } from '../../services/supabaseClient';

import { useSafeAreaInsets } from 'react-native-safe-area-context';

interface PasswordSettingsScreenProps {
  navigation: any;
}

export const PasswordSettingsScreen: React.FC<PasswordSettingsScreenProps> = ({
  navigation,
}) => {
  const insets = useSafeAreaInsets();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const [showCurrent, setShowCurrent] = useState(false);
  const [showNew, setShowNew] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);

  const [loading, setLoading] = useState(false);

  const isMinLength = newPassword.length >= 6;
  const isMatch = newPassword === confirmPassword && confirmPassword.length > 0;

  const handleChangePassword = async () => {
    if (!newPassword || !confirmPassword) {
      feedback.warning({ title: 'Hata', message: 'Lütfen tüm alanları doldurun' });
      return;
    }
    if (!isMinLength) {
      feedback.warning({ title: 'Hata', message: 'Şifre en az 6 karakter olmalıdır' });
      return;
    }
    if (!isMatch) {
      feedback.warning({ title: 'Hata', message: 'Yeni şifreler eşleşmiyor' });
      return;
    }

    try {
      setLoading(true);
      const { error } = await supabase.auth.updateUser({
        password: newPassword,
      });

      if (error) throw error;

      feedback.success({
        title: 'Başarılı',
        message: 'Şifreniz başarıyla güncellendi.',
      });
      navigation.goBack();
    } catch (e: any) {
      feedback.error({
        title: 'Hata',
        message: e,
        fallbackMessage: 'Şifre güncellenemedi',
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <View style={styles.container}>
      {/* Header */}
      <View style={[styles.header, { paddingTop: Math.max(16, insets.top + 8) }]}>
        <TouchableOpacity
          style={styles.backButton}
          onPress={() => navigation.goBack()}
        >
          <ArrowLeft size={20} color={Colors.textDark} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Şifre Değiştir</Text>
        <View style={{ width: 40 }} />
      </View>

      <ScrollView contentContainerStyle={[styles.scrollContent, { paddingBottom: 24 + insets.bottom }]}>
        <Text style={styles.description}>
          Hesap güvenliğinizi korumak için güçlü ve en az 6 karakterli bir şifre belirleyin.
        </Text>

        {/* Current Password */}
        <View style={styles.inputWrapper}>
          <Text style={styles.label}>Mevcut Şifre</Text>
          <View style={styles.passwordRow}>
            <TextInput
              style={styles.input}
              placeholder="Mevcut şifreniz"
              placeholderTextColor={Colors.textSecondaryDark}
              value={currentPassword}
              onChangeText={setCurrentPassword}
              secureTextEntry={!showCurrent}
              autoCapitalize="none"
            />
            <TouchableOpacity
              style={styles.eyeIcon}
              onPress={() => setShowCurrent(!showCurrent)}
            >
              {showCurrent ? (
                <Eye size={20} color={Colors.textSecondaryDark} />
              ) : (
                <EyeOff size={20} color={Colors.textSecondaryDark} />
              )}
            </TouchableOpacity>
          </View>
        </View>

        {/* New Password */}
        <View style={styles.inputWrapper}>
          <Text style={styles.label}>Yeni Şifre</Text>
          <View style={styles.passwordRow}>
            <TextInput
              style={styles.input}
              placeholder="Yeni şifreniz"
              placeholderTextColor={Colors.textSecondaryDark}
              value={newPassword}
              onChangeText={setNewPassword}
              secureTextEntry={!showNew}
              autoCapitalize="none"
            />
            <TouchableOpacity
              style={styles.eyeIcon}
              onPress={() => setShowNew(!showNew)}
            >
              {showNew ? (
                <Eye size={20} color={Colors.textSecondaryDark} />
              ) : (
                <EyeOff size={20} color={Colors.textSecondaryDark} />
              )}
            </TouchableOpacity>
          </View>
        </View>

        {/* Confirm Password */}
        <View style={styles.inputWrapper}>
          <Text style={styles.label}>Yeni Şifre (Tekrar)</Text>
          <View style={styles.passwordRow}>
            <TextInput
              style={styles.input}
              placeholder="Yeni şifrenizi tekrar girin"
              placeholderTextColor={Colors.textSecondaryDark}
              value={confirmPassword}
              onChangeText={setConfirmPassword}
              secureTextEntry={!showConfirm}
              autoCapitalize="none"
            />
            <TouchableOpacity
              style={styles.eyeIcon}
              onPress={() => setShowConfirm(!showConfirm)}
            >
              {showConfirm ? (
                <Eye size={20} color={Colors.textSecondaryDark} />
              ) : (
                <EyeOff size={20} color={Colors.textSecondaryDark} />
              )}
            </TouchableOpacity>
          </View>
        </View>

        {/* Requirements */}
        <View style={styles.requirementsBox}>
          <View style={styles.reqRow}>
            <Check
              size={16}
              color={isMinLength ? '#4CAF50' : Colors.textSecondaryDark}
            />
            <Text
              style={[
                styles.reqText,
                isMinLength && { color: '#4CAF50', fontWeight: '600' },
              ]}
            >
              En az 6 karakter uzunluğunda
            </Text>
          </View>
          <View style={styles.reqRow}>
            <Check
              size={16}
              color={isMatch ? '#4CAF50' : Colors.textSecondaryDark}
            />
            <Text
              style={[
                styles.reqText,
                isMatch && { color: '#4CAF50', fontWeight: '600' },
              ]}
            >
              Şifreler eşleşiyor
            </Text>
          </View>
        </View>

        {/* Save Button */}
        <TouchableOpacity
          style={[
            styles.saveButton,
            (!isMinLength || !isMatch || loading) && styles.disabledButton,
          ]}
          onPress={handleChangePassword}
          disabled={!isMinLength || !isMatch || loading}
        >
          {loading ? (
            <ActivityIndicator color={Colors.allWhite} />
          ) : (
            <Text style={styles.saveButtonText}>Şifreyi Güncelle</Text>
          )}
        </TouchableOpacity>
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
  scrollContent: {
    padding: 20,
  },
  description: {
    fontSize: 14,
    color: Colors.textSecondaryDark,
    marginBottom: 24,
    lineHeight: 20,
  },
  inputWrapper: {
    marginBottom: 16,
  },
  label: {
    fontSize: 13,
    fontWeight: '600',
    color: Colors.textDark,
    marginBottom: 8,
  },
  passwordRow: {
    position: 'relative',
    justifyContent: 'center',
  },
  input: {
    height: 52,
    backgroundColor: Colors.cardDark,
    borderWidth: 1,
    borderColor: Colors.borderDark,
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingRight: 50,
    fontSize: 15,
    color: Colors.textDark,
  },
  eyeIcon: {
    position: 'absolute',
    right: 16,
    padding: 4,
  },
  requirementsBox: {
    backgroundColor: Colors.cardDark,
    borderRadius: 12,
    padding: 16,
    marginVertical: 16,
    gap: 8,
  },
  reqRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  reqText: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
  },
  saveButton: {
    height: 54,
    backgroundColor: Colors.primary,
    borderRadius: 27,
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 12,
  },
  disabledButton: {
    opacity: 0.5,
  },
  saveButtonText: {
    color: Colors.allWhite,
    fontSize: 16,
    fontWeight: '700',
  },
});
