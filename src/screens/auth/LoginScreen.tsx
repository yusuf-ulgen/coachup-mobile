import React, { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
  Alert,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  Image,
  Dimensions,
} from 'react-native';
import { Eye, EyeOff } from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { AuthService } from '../../services/authService';
import { GYM_CONFIG } from '../../config/gym';

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

  const handleLogin = async () => {
    if (!email.trim() || !password) {
      Alert.alert('Hata', 'Lütfen tüm alanları doldurun');
      return;
    }

    try {
      setLoading(true);
      await AuthService.signIn(email.trim(), password);
      if (onLoginSuccess) {
        onLoginSuccess();
      }
    } catch (error: any) {
      const msg = error.message || '';
      if (msg.includes('invalid_credentials') || msg.includes('E-posta veya şifre hatalı')) {
        Alert.alert('Giriş Başarısız', 'E-posta veya şifre hatalı.');
      } else if (msg.includes('email_not_confirmed')) {
        Alert.alert(
          'E-posta Doğrulama Gerekli',
          'E-posta adresinizi doğrulamadınız. Gelen kutunuzu kontrol edin.',
          [
            { text: 'Tamam' },
            {
              text: 'Tekrar Gönder',
              onPress: async () => {
                try {
                  await AuthService.resendConfirmationEmail(email.trim());
                  Alert.alert('Bilgi', 'Doğrulama e-postası tekrar gönderildi.');
                } catch (e: any) {
                  Alert.alert('Hata', e.message || 'Gönderilemedi.');
                }
              },
            },
          ]
        );
      } else {
        Alert.alert('Hata', msg || 'Giriş yapılamadı. Lütfen tekrar deneyin.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
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
            />
          </View>

          {/* Password Input */}
          <View style={styles.inputWrapper}>
            <View style={styles.passwordRow}>
              <TextInput
                style={[styles.pillInput, { flex: 1 }]}
                placeholder="Şifre"
                placeholderTextColor={Colors.textSecondaryDark}
                value={password}
                onChangeText={setPassword}
                secureTextEntry={!showPassword}
                autoCapitalize="none"
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
              <ActivityIndicator color={Colors.allWhite} />
            ) : (
              <>
                <Text style={styles.buttonText}>Giriş Yap</Text>
                <View style={styles.arrowCircle}>
                  <Text style={styles.arrowText}>→</Text>
                </View>
              </>
            )}
          </TouchableOpacity>

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
    height: SCREEN_HEIGHT * 0.45,
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
    right: 18,
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
});
