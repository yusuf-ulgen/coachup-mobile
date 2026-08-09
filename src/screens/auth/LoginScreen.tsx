import React, { useState, useEffect, useRef } from 'react';
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
  TouchableWithoutFeedback,
  Keyboard,
} from 'react-native';
import { feedback } from '../../services/feedbackService';
import AsyncStorage from '@react-native-async-storage/async-storage';
import * as LocalAuthentication from 'expo-local-authentication';
import { Eye, EyeOff, Mail, Fingerprint } from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { AuthService } from '../../services/authService';
import { GYM_CONFIG } from '../../config/gym';
import { SmoothModal } from '../../components/motion/SmoothModal';

const { height: SCREEN_HEIGHT } = Dimensions.get('window');

interface LoginScreenProps {
  onNavigateToRegister: () => void;
  onLoginSuccess?: () => void;
}

export const LoginScreen: React.FC<LoginScreenProps> = ({
  onNavigateToRegister,
  onLoginSuccess,
}) => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [showVerificationModal, setShowVerificationModal] = useState(false);
  const [resendLoading, setResendLoading] = useState(false);
  const [biometricsAvailable, setBiometricsAvailable] = useState(false);
  const passwordRef = useRef<TextInput>(null);

  useEffect(() => {
    const checkBiometrics = async () => {
      try {
        const stored = await AsyncStorage.getItem('@app_setting_biometrics_enabled');
        if (stored === 'true') {
          const hasHW = await LocalAuthentication.hasHardwareAsync();
          const enrolled = await LocalAuthentication.isEnrolledAsync();
          if (hasHW && enrolled) {
            setBiometricsAvailable(true);
          }
        }
      } catch {}
    };
    checkBiometrics();
  }, []);

  const handleLogin = async () => {
    if (!email.trim() || !password) {
      feedback.warning({ title: 'Hata', message: 'Lütfen tüm alanları doldurun' });
      return;
    }

    try {
      setLoading(true);
      await AuthService.signIn(email.trim(), password);
      await AuthService.ensureProfileFromAuthIfMissing();
      if (onLoginSuccess) {
        onLoginSuccess();
      }
    } catch (error: any) {
      const msg = error?.message || '';
      if (msg.includes('email_not_confirmed')) {
        setShowVerificationModal(true);
      } else {
        feedback.error({
          title: 'Giriş Başarısız',
          message: error,
          fallbackMessage: 'Giriş yapılamadı. Lütfen tekrar deneyin.',
        });
      }
    } finally {
      setLoading(false);
    }
  };

  const handleBiometricLogin = async () => {
    try {
      const result = await LocalAuthentication.authenticateAsync({
        promptMessage: 'CoachUP Girişi için doğrulanın',
        fallbackLabel: 'Şifre Kullan',
      });

      if (result.success) {
        setLoading(true);
        const user = await AuthService.getCurrentUser();
        if (user) {
          await AuthService.ensureProfileFromAuthIfMissing();
          if (onLoginSuccess) onLoginSuccess();
          feedback.toast('Biyometrik kimlik doğrulama başarılı.', 'success');
        } else {
          feedback.warning({
            title: 'Oturum Bilgisi Bulunamadı',
            message: 'Biyometrik giriş öncesinde lütfen en az 1 kez email ve şifre ile giriş yapın.',
          });
        }
      }
    } catch (e) {
      console.error('Biometric auth error:', e);
    } finally {
      setLoading(false);
    }
  };

  return (
    <TouchableWithoutFeedback onPress={Keyboard.dismiss}>
      <KeyboardAvoidingView
        style={styles.container}
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      >
        <ScrollView
        contentContainerStyle={styles.scrollContent}
        keyboardShouldPersistTaps="handled"
        bounces={false}
      >
        {/* Top Hero Image & Logo */}
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

        {/* Sliding Card Container */}
        <View style={styles.cardContainer}>
          <Text style={styles.title}>Giriş Yap</Text>
          <Text style={styles.subtitle}>
            Kaldığın yerden devam etmek için{'\n'}giriş yap.
          </Text>

          {/* Email Input */}
          <View style={styles.inputWrapper}>
            <TextInput
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
                style={[styles.pillInput, { flex: 1, paddingRight: 48 }]}
                placeholder="Şifre"
                placeholderTextColor={Colors.textSecondaryDark}
                value={password}
                onChangeText={setPassword}
                secureTextEntry={!showPassword}
                autoCapitalize="none"
                returnKeyType="done"
                onSubmitEditing={handleLogin}
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

          {/* Primary Pill Button with Arrow Circle */}
          <TouchableOpacity
            style={[styles.primaryButton, loading && styles.buttonDisabled]}
            onPress={handleLogin}
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
                <Text style={styles.buttonText}>Giriş Yap</Text>
                <View style={styles.arrowCircle}>
                  <Text style={styles.arrowText}>→</Text>
                </View>
              </>
            )}
          </TouchableOpacity>

          {biometricsAvailable && (
            <TouchableOpacity
              style={[
                styles.primaryButton,
                {
                  backgroundColor: Colors.cardDark || '#222',
                  marginTop: 10,
                  borderWidth: 1,
                  borderColor: Colors.primary,
                },
              ]}
              onPress={handleBiometricLogin}
              disabled={loading}
              activeOpacity={0.85}
            >
              <Fingerprint size={20} color={Colors.primary} style={{ marginRight: 8 }} />
              <Text style={[styles.buttonText, { color: Colors.textDark }]}>
                Biyometrik ile Giriş Yap
              </Text>
            </TouchableOpacity>
          )}

          {/* Register Link */}
          <TouchableOpacity
            style={styles.registerLink}
            onPress={onNavigateToRegister}
            activeOpacity={0.7}
          >
            <Text style={styles.registerText}>
              Hesabınız yok mu?{' '}
              <Text style={styles.registerHighlight}>Hesap oluşturun.</Text>
            </Text>
          </TouchableOpacity>
        </View>

        {/* E-posta Doğrulama Modalı */}
        <SmoothModal
          visible={showVerificationModal}
          onClose={() => setShowVerificationModal(false)}
          variant="modal"
        >
          <View style={styles.modalContent}>
            <View style={styles.modalIconContainer}>
              <Mail size={32} color={Colors.primary} />
            </View>
            <Text style={styles.modalTitle}>E-posta Doğrulama</Text>
            <Text style={styles.modalMessage}>
              Hesabınızı kullanmaya başlamak için e-posta adresinizi doğrulamanız gerekmektedir. Gelen kutunuzu kontrol edin.
            </Text>
            
            <TouchableOpacity
              style={[styles.primaryButton, { marginTop: 24 }]}
              onPress={async () => {
                try {
                  setResendLoading(true);
                  await AuthService.resendConfirmationEmail(email.trim());
                  feedback.info({ title: 'Bilgi', message: 'Doğrulama e-postası tekrar gönderildi.' });
                } catch (e: any) {
                  feedback.error({ title: 'Hata', message: e, fallbackMessage: 'Gönderilemedi.' });
                } finally {
                  setResendLoading(false);
                  setShowVerificationModal(false);
                }
              }}
              disabled={resendLoading}
            >
              {resendLoading ? (
                <ActivityIndicator color={Colors.allWhite} />
              ) : (
                <Text style={styles.buttonText}>Tekrar Gönder</Text>
              )}
            </TouchableOpacity>
            
            <TouchableOpacity
              style={styles.modalCancelButton}
              onPress={() => setShowVerificationModal(false)}
            >
              <Text style={styles.modalCancelText}>Kapat</Text>
            </TouchableOpacity>
          </View>
        </SmoothModal>

      </ScrollView>
      </KeyboardAvoidingView>
    </TouchableWithoutFeedback>
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
    height: SCREEN_HEIGHT * 0.50,
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
    fontSize: 16,
    color: Colors.textSecondaryDark,
    marginTop: 12,
    lineHeight: 22,
    marginBottom: 24,
  },
  inputWrapper: {
    marginBottom: 16,
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
    right: 12,
    height: '100%',
    justifyContent: 'center',
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
    marginTop: 16,
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
    color: Colors.black100,
    fontSize: 16,
    fontWeight: '700',
  },
  registerLink: {
    marginTop: 20,
    alignItems: 'center',
  },
  registerText: {
    fontSize: 14,
    color: Colors.textSecondaryDark,
  },
  registerHighlight: {
    color: Colors.primary,
    fontWeight: '600',
    textDecorationLine: 'underline',
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.6)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  modalContent: {
    backgroundColor: Colors.cardDark,
    borderRadius: 24,
    padding: 24,
    width: '100%',
    alignItems: 'center',
  },
  modalIconContainer: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: 'rgba(239, 68, 68, 0.1)',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 16,
  },
  modalTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: Colors.textDark,
    marginBottom: 12,
  },
  modalMessage: {
    fontSize: 15,
    color: Colors.textSecondaryDark,
    textAlign: 'center',
    lineHeight: 22,
  },
  modalCancelButton: {
    marginTop: 16,
    paddingVertical: 12,
    width: '100%',
    alignItems: 'center',
  },
  modalCancelText: {
    color: Colors.textSecondaryDark,
    fontSize: 15,
    fontWeight: '600',
  },
});
