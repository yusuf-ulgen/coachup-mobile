import React, { useState, useRef } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  Image,
  Dimensions,
} from 'react-native';
import { feedback } from '../../services/feedbackService';
import { Eye, EyeOff } from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { AuthService } from '../../services/authService';
import { GYM_CONFIG } from '../../config/gym';
import { supabase } from '../../services/supabaseClient';
import { UserService } from '../../services/userService';

const { height: SCREEN_HEIGHT } = Dimensions.get('window');

interface RegisterScreenProps {
  onNavigateToLogin: () => void;
  onRegisterSuccess?: () => void;
}

export const RegisterScreen: React.FC<RegisterScreenProps> = ({
  onNavigateToLogin,
  onRegisterSuccess,
}) => {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [gender, setGender] = useState<'male' | 'female' | null>(null);
  const [loading, setLoading] = useState(false);
  const [resendLoading, setResendLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const emailRef = useRef<TextInput>(null);
  const passwordRef = useRef<TextInput>(null);

  const handleRegister = async () => {
    if (!name.trim()) {
      feedback.warning({ title: 'Hata', message: 'Lütfen isminizi girin' });
      return;
    }
    if (!gender) {
      feedback.warning({ title: 'Hata', message: 'Lütfen cinsiyetinizi seçin' });
      return;
    }
    if (!email.trim() || !password) {
      feedback.warning({ title: 'Hata', message: 'Lütfen tüm alanları doldurun' });
      return;
    }
    if (password.length < 6) {
      feedback.warning({ title: 'Hata', message: 'Şifre en az 6 karakter olmalıdır' });
      return;
    }

    try {
      setLoading(true);
      const emailConfirmed = await AuthService.signUp(
        email.trim(),
        password,
        name.trim(),
        gender,
        true
      );

      // Profil satırını garantile
      const { data: userData } = await supabase.auth.getUser();
      const userId = userData?.user?.id;
      if (userId) {
        await UserService.ensureProfileExists(userId, email.trim(), name.trim(), gender!);
      }

      if (emailConfirmed) {
        if (onRegisterSuccess) {
          onRegisterSuccess();
        } else {
          onNavigateToLogin();
        }
      } else {
        feedback.showDialog({
          title: 'E-posta Doğrulama',
          message: 'Kayıt oluşturuldu. Giriş yapmadan önce e-posta adresinize gelen doğrulama linkine tıklayın.',
          buttons: [
            {
              text: 'Giriş Ekranına Dön',
              onPress: async () => {
                try { await AuthService.signOut(); } catch (e) {}
                onNavigateToLogin();
              },
            },
            {
              text: 'Tekrar Gönder',
              onPress: async () => {
                if (resendLoading) return;
                try {
                  setResendLoading(true);
                  await AuthService.resendConfirmationEmail(email.trim());
                  feedback.info({ title: 'Bilgi', message: 'Doğrulama e-postası gönderildi.' });
                } catch (e: any) {
                  feedback.error({ title: 'Hata', message: e, fallbackMessage: 'Gönderilemedi.' });
                } finally {
                  setResendLoading(false);
                }
              },
            },
          ],
        });
      }
    } catch (error: any) {
      feedback.error({
        title: 'Hata',
        message: error,
        fallbackMessage: 'Kayıt başarısız. Lütfen tekrar deneyin.',
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <ScrollView
        contentContainerStyle={styles.scrollContent}
        keyboardShouldPersistTaps="handled"
        bounces={false}
      >
        {/* Top Hero Section */}
        <View style={styles.heroSection}>
          <Image
            source={GYM_CONFIG.LOGIN_HERO}
            style={styles.heroImage}
            resizeMode="cover"
          />
          <Image
            source={GYM_CONFIG.LOGIN_LOGO}
            style={styles.logoImage}
            resizeMode="contain"
          />
        </View>

        {/* Sliding Form Card */}
        <View style={styles.cardContainer}>
          <Text style={styles.title}>Kayıt Ol</Text>
          <Text style={styles.subtitle}>
            Bireysel hesabınızı oluşturun,{'\n'}salon üyeliği gerekmez.
          </Text>

          {/* Name Input */}
          <View style={styles.inputWrapper}>
            <TextInput
              style={styles.pillInput}
              placeholder="İsim"
              placeholderTextColor={Colors.textSecondaryDark}
              value={name}
              onChangeText={setName}
              autoCapitalize="words"
              returnKeyType="next"
              onSubmitEditing={() => emailRef.current?.focus()}
            />
          </View>

          {/* Email Input */}
          <View style={styles.inputWrapper}>
            <TextInput
              ref={emailRef}
              style={styles.pillInput}
              placeholder="Email adresinizi girin"
              placeholderTextColor={Colors.textSecondaryDark}
              value={email}
              onChangeText={setEmail}
              keyboardType="email-address"
              autoCapitalize="none"
              autoCorrect={false}
              returnKeyType="next"
              onSubmitEditing={() => passwordRef.current?.focus()}
            />
          </View>

          {/* Password Input */}
          <View style={styles.inputWrapper}>
            <View style={styles.passwordRow}>
              <TextInput
                ref={passwordRef}
                style={[styles.pillInput, { flex: 1 }]}
                placeholder="Şifre"
                placeholderTextColor={Colors.textSecondaryDark}
                value={password}
                onChangeText={setPassword}
                secureTextEntry={!showPassword}
                autoCapitalize="none"
                returnKeyType="done"
                onSubmitEditing={handleRegister}
              />
              <TouchableOpacity
                onPress={() => setShowPassword(!showPassword)}
                style={styles.eyeIconContainer}
                activeOpacity={0.7}
              >
                {showPassword ? (
                  <Eye size={20} color={Colors.textSecondaryDark} />
                ) : (
                  <EyeOff size={20} color={Colors.textSecondaryDark} />
                )}
              </TouchableOpacity>
            </View>
          </View>

          {/* Gender Selector Row */}
          <View style={styles.genderRow}>
            <TouchableOpacity
              style={[
                styles.genderOption,
                gender === 'male' && styles.genderOptionSelected,
              ]}
              onPress={() => setGender('male')}
              activeOpacity={0.7}
            >
              <Text
                style={[
                  styles.genderText,
                  gender === 'male' && styles.genderTextSelected,
                ]}
              >
                Erkek
              </Text>
              <View
                style={[
                  styles.radioButton,
                  gender === 'male' && styles.radioButtonSelected,
                ]}
              />
            </TouchableOpacity>

            <TouchableOpacity
              style={[
                styles.genderOption,
                gender === 'female' && styles.genderOptionSelected,
              ]}
              onPress={() => setGender('female')}
              activeOpacity={0.7}
            >
              <Text
                style={[
                  styles.genderText,
                  gender === 'female' && styles.genderTextSelected,
                ]}
              >
                Kadın
              </Text>
              <View
                style={[
                  styles.radioButton,
                  gender === 'female' && styles.radioButtonSelected,
                ]}
              />
            </TouchableOpacity>
          </View>

          {/* Register Pill Button with Arrow Circle */}
          <TouchableOpacity
            style={[styles.primaryButton, loading && styles.buttonDisabled]}
            onPress={handleRegister}
            disabled={loading}
            activeOpacity={0.85}
          >
            {loading ? (
              <>
                <ActivityIndicator color={Colors.allWhite} size="small" />
                <View style={styles.arrowCircle}>
                  <Text style={styles.arrowText}>→</Text>
                </View>
              </>
            ) : (
              <>
                <Text style={styles.buttonText}>Kayıt Ol</Text>
                <View style={styles.arrowCircle}>
                  <Text style={styles.arrowText}>→</Text>
                </View>
              </>
            )}
          </TouchableOpacity>

          {/* Back to Login Link */}
          <TouchableOpacity
            style={styles.loginLink}
            onPress={onNavigateToLogin}
            activeOpacity={0.7}
          >
            <Text style={styles.loginText}>
              Hesabınız var mı?{' '}
              <Text style={styles.loginHighlight}>Giriş yapın.</Text>
            </Text>
          </TouchableOpacity>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.backgroundDark,
  },
  scrollContent: {
    flexGrow: 1,
  },
  heroSection: {
    height: SCREEN_HEIGHT * 0.40,
    width: '100%',
    position: 'relative',
    alignItems: 'center',
  },
  heroImage: {
    width: '100%',
    height: '100%',
  },
  logoImage: {
    position: 'absolute',
    top: 56,
    width: 140,
    height: 70,
  },
  cardContainer: {
    flex: 1,
    marginTop: -30,
    backgroundColor: Colors.cardDark,
    borderTopLeftRadius: 30,
    borderTopRightRadius: 30,
    paddingHorizontal: 24,
    paddingTop: 28,
    paddingBottom: 40,
  },
  title: {
    fontSize: 32,
    fontWeight: '700',
    color: Colors.textDark,
  },
  subtitle: {
    fontSize: 15,
    color: Colors.textSecondaryDark,
    marginTop: 8,
    lineHeight: 22,
    marginBottom: 20,
  },
  inputWrapper: {
    marginBottom: 12,
  },
  pillInput: {
    height: 54,
    borderWidth: 1,
    borderColor: Colors.borderDark,
    borderRadius: 100,
    paddingHorizontal: 20,
    fontSize: 15,
    color: Colors.textDark,
    backgroundColor: Colors.cardDark,
  },
  passwordRow: {
    flexDirection: 'row',
    alignItems: 'center',
    position: 'relative',
  },
  eyeIconContainer: {
    position: 'absolute',
    right: 18,
    height: '100%',
    justifyContent: 'center',
  },
  genderRow: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 4,
    marginBottom: 16,
  },
  genderOption: {
    flex: 1,
    height: 54,
    borderWidth: 1,
    borderColor: Colors.borderDark,
    borderRadius: 100,
    paddingHorizontal: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: Colors.cardDark,
  },
  genderOptionSelected: {
    borderColor: Colors.primary,
    borderWidth: 2,
  },
  genderText: {
    fontSize: 15,
    color: Colors.textSecondaryDark,
  },
  genderTextSelected: {
    color: Colors.textDark,
    fontWeight: '600',
  },
  radioButton: {
    width: 22,
    height: 22,
    borderRadius: 11,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  radioButtonSelected: {
    borderWidth: 6,
    borderColor: Colors.primary,
  },
  primaryButton: {
    height: 56,
    backgroundColor: Colors.primary,
    borderRadius: 100,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingLeft: 24,
    paddingRight: 10,
    marginTop: 8,
  },
  buttonDisabled: {
    opacity: 0.6,
  },
  buttonText: {
    color: Colors.allWhite,
    fontSize: 16,
    fontWeight: '600',
  },
  arrowCircle: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: Colors.allWhite,
    justifyContent: 'center',
    alignItems: 'center',
  },
  arrowText: {
    color: Colors.primary,
    fontSize: 16,
    fontWeight: '700',
  },
  loginLink: {
    marginTop: 20,
    alignItems: 'center',
  },
  loginText: {
    fontSize: 14,
    color: Colors.textSecondaryDark,
  },
  loginHighlight: {
    color: Colors.primary,
    fontWeight: '600',
    textDecorationLine: 'underline',
  },
});
