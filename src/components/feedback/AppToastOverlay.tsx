import React, { useEffect, useRef } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Animated,
  SafeAreaView,
  Platform,
} from 'react-native';
import { CheckCircle2, AlertTriangle, XCircle, Info, X } from 'lucide-react-native';
import { ToastOptions, feedback } from '../../services/feedbackService';
import { useTheme } from '../../theme/ThemeContext';
import { Colors } from '../../theme/colors';

interface AppToastOverlayProps {
  options: ToastOptions | null;
}

export const AppToastOverlay: React.FC<AppToastOverlayProps> = ({ options }) => {
  const { colors, isDark } = useTheme();
  const slideAnim = useRef(new Animated.Value(-100)).current;
  const opacityAnim = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    if (options) {
      Animated.parallel([
        Animated.timing(slideAnim, {
          toValue: 0,
          duration: 250,
          useNativeDriver: true,
        }),
        Animated.timing(opacityAnim, {
          toValue: 1,
          duration: 200,
          useNativeDriver: true,
        }),
      ]).start();
    } else {
      Animated.parallel([
        Animated.timing(slideAnim, {
          toValue: -100,
          duration: 200,
          useNativeDriver: true,
        }),
        Animated.timing(opacityAnim, {
          toValue: 0,
          duration: 150,
          useNativeDriver: true,
        }),
      ]).start();
    }
  }, [options]);

  if (!options) return null;

  const variant = options.variant || 'success';

  const getIcon = () => {
    switch (variant) {
      case 'success':
        return <CheckCircle2 size={22} color={Colors.success} />;
      case 'error':
        return <XCircle size={22} color={Colors.error} />;
      case 'warning':
        return <AlertTriangle size={22} color={Colors.warning} />;
      case 'info':
      default:
        return <Info size={22} color={Colors.primary} />;
    }
  };

  const getAccentColor = () => {
    switch (variant) {
      case 'success':
        return Colors.success;
      case 'error':
        return Colors.error;
      case 'warning':
        return Colors.warning;
      case 'info':
      default:
        return Colors.primary;
    }
  };

  return (
    <SafeAreaView pointerEvents="box-none" style={styles.safeContainer}>
      <Animated.View
        style={[
          styles.toastCard,
          {
            backgroundColor: colors.cardBg,
            borderColor: colors.border,
            borderLeftColor: getAccentColor(),
            opacity: opacityAnim,
            transform: [{ translateY: slideAnim }],
          },
        ]}
      >
        <View style={styles.iconWrapper}>{getIcon()}</View>
        <View style={styles.textContainer}>
          {options.title ? (
            <Text style={[styles.title, { color: colors.textPrimary }]}>
              {options.title}
            </Text>
          ) : null}
          <Text style={[styles.message, { color: colors.textSecondary }]}>
            {options.message}
          </Text>
        </View>
        <TouchableOpacity
          style={styles.closeBtn}
          onPress={() => feedback.hideToast()}
          hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
        >
          <X size={16} color={colors.textSecondary} />
        </TouchableOpacity>
      </Animated.View>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  safeContainer: {
    position: 'absolute',
    top: Platform.OS === 'android' ? 40 : 10,
    left: 16,
    right: 16,
    zIndex: 99999,
    alignItems: 'center',
  },
  toastCard: {
    width: '100%',
    maxWidth: 420,
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 14,
    paddingHorizontal: 16,
    borderRadius: 16,
    borderWidth: 1,
    borderLeftWidth: 4,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.25,
    shadowRadius: 10,
    elevation: 8,
    gap: 12,
  },
  iconWrapper: {
    justifyContent: 'center',
    alignItems: 'center',
  },
  textContainer: {
    flex: 1,
  },
  title: {
    fontSize: 14,
    fontWeight: '700',
    marginBottom: 2,
  },
  message: {
    fontSize: 13,
    fontWeight: '500',
    lineHeight: 18,
  },
  closeBtn: {
    padding: 4,
  },
});
