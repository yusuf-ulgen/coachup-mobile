import React, { useEffect, useRef } from 'react';
import {
  Modal,
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Animated,
  Dimensions,
} from 'react-native';
import { CheckCircle2, AlertTriangle, XCircle, Info } from 'lucide-react-native';
import { FeedbackDialogOptions, FeedbackVariant, feedback } from '../../services/feedbackService';
import { useTheme } from '../../theme/ThemeContext';
import { Colors } from '../../theme/colors';

const { width } = Dimensions.get('window');

interface AppDialogModalProps {
  options: FeedbackDialogOptions | null;
}

export const AppDialogModal: React.FC<AppDialogModalProps> = ({ options }) => {
  const { colors, isDark } = useTheme();
  const scaleAnim = useRef(new Animated.Value(0.9)).current;
  const fadeAnim = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    if (options) {
      Animated.parallel([
        Animated.timing(fadeAnim, {
          toValue: 1,
          duration: 180,
          useNativeDriver: true,
        }),
        Animated.spring(scaleAnim, {
          toValue: 1,
          friction: 8,
          tension: 65,
          useNativeDriver: true,
        }),
      ]).start();
    } else {
      Animated.parallel([
        Animated.timing(fadeAnim, {
          toValue: 0,
          duration: 120,
          useNativeDriver: true,
        }),
        Animated.timing(scaleAnim, {
          toValue: 0.9,
          duration: 120,
          useNativeDriver: true,
        }),
      ]).start();
    }
  }, [options]);

  if (!options) return null;

  const variant: FeedbackVariant =
    options.variant ||
    (options.title.toLowerCase().includes('hata')
      ? 'error'
      : options.title.toLowerCase().includes('başarılı')
      ? 'success'
      : 'info');

  const getIcon = () => {
    switch (variant) {
      case 'success':
        return <CheckCircle2 size={32} color={Colors.success} />;
      case 'error':
        return <XCircle size={32} color={Colors.error} />;
      case 'warning':
        return <AlertTriangle size={32} color={Colors.warning} />;
      case 'info':
      default:
        return <Info size={32} color={Colors.primary} />;
    }
  };

  const getBadgeBg = () => {
    switch (variant) {
      case 'success':
        return 'rgba(76, 175, 80, 0.15)';
      case 'error':
        return 'rgba(244, 67, 54, 0.15)';
      case 'warning':
        return 'rgba(255, 152, 0, 0.15)';
      case 'info':
      default:
        return 'rgba(255, 96, 71, 0.15)';
    }
  };

  const buttons =
    options.buttons && options.buttons.length > 0
      ? options.buttons
      : [{ text: 'Tamam', style: 'default' as const }];

  const handlePress = (onPress?: () => void) => {
    feedback.hideDialog();
    if (onPress) {
      setTimeout(() => {
        onPress();
      }, 100);
    }
  };

  const handleBackdropPress = () => {
    // If there's a cancel button, trigger its callback, otherwise dismiss
    const cancelBtn = buttons.find((b) => b.style === 'cancel');
    if (cancelBtn) {
      handlePress(cancelBtn.onPress);
    } else {
      handlePress(buttons[0]?.onPress);
    }
  };

  return (
    <Modal
      transparent
      visible={!!options}
      animationType="none"
      onRequestClose={handleBackdropPress}
      accessibilityViewIsModal
    >
      <View style={styles.backdrop}>
        <TouchableOpacity
          style={StyleSheet.absoluteFill}
          activeOpacity={1}
          onPress={handleBackdropPress}
        />
        <Animated.View
          style={[
            styles.dialogCard,
            {
              backgroundColor: colors.cardBg,
              borderColor: colors.border,
              opacity: fadeAnim,
              transform: [{ scale: scaleAnim }],
            },
          ]}
        >
          {/* Icon Badge */}
          <View style={[styles.iconBadge, { backgroundColor: getBadgeBg() }]}>
            {getIcon()}
          </View>

          {/* Title & Message */}
          <Text
            style={[styles.title, { color: colors.textPrimary }]}
            accessibilityRole="header"
          >
            {options.title}
          </Text>
          {options.message ? (
            <Text style={[styles.message, { color: colors.textSecondary }]}>
              {options.message}
            </Text>
          ) : null}

          {/* Action Buttons */}
          <View
            style={[
              styles.buttonRow,
              buttons.length > 2 && { flexDirection: 'column' },
            ]}
          >
            {buttons.map((btn, index) => {
              const isCancel = btn.style === 'cancel';
              const isDestructive = btn.style === 'destructive';

              return (
                <TouchableOpacity
                  key={index}
                  style={[
                    styles.button,
                    isCancel && {
                      backgroundColor: isDark
                        ? 'rgba(255, 255, 255, 0.08)'
                        : 'rgba(0, 0, 0, 0.06)',
                    },
                    isDestructive && { backgroundColor: Colors.error },
                    !isCancel && !isDestructive && { backgroundColor: Colors.primary },
                    buttons.length === 1 && { width: '100%' },
                    buttons.length === 2 && { flex: 1 },
                    buttons.length > 2 && { width: '100%' },
                  ]}
                  activeOpacity={0.8}
                  onPress={() => handlePress(btn.onPress)}
                  accessibilityRole="button"
                  accessibilityLabel={btn.text}
                >
                  <Text
                    style={[
                      styles.buttonText,
                      isCancel && { color: colors.textSecondary },
                      (isDestructive || (!isCancel && !isDestructive)) && {
                        color: Colors.allWhite,
                      },
                    ]}
                  >
                    {btn.text}
                  </Text>
                </TouchableOpacity>
              );
            })}
          </View>
        </Animated.View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.72)',
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 24,
  },
  dialogCard: {
    width: '100%',
    maxWidth: 340,
    borderRadius: 24,
    padding: 24,
    alignItems: 'center',
    borderWidth: 1,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 10 },
    shadowOpacity: 0.4,
    shadowRadius: 20,
    elevation: 12,
  },
  iconBadge: {
    width: 60,
    height: 60,
    borderRadius: 30,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 16,
  },
  title: {
    fontSize: 19,
    fontWeight: '700',
    textAlign: 'center',
  },
  message: {
    fontSize: 14,
    textAlign: 'center',
    marginTop: 8,
    lineHeight: 20,
  },
  buttonRow: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 22,
    width: '100%',
  },
  button: {
    height: 48,
    borderRadius: 14,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 16,
  },
  buttonText: {
    fontSize: 15,
    fontWeight: '700',
  },
});
