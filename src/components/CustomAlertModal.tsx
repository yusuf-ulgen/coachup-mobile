import React, { useState, useEffect } from 'react';
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
import { Colors } from '../theme/colors';

export type AlertType = 'success' | 'error' | 'warning' | 'info';

export interface AlertButton {
  text: string;
  style?: 'default' | 'cancel' | 'destructive';
  onPress?: () => void;
}

export interface AlertOptions {
  title: string;
  message?: string;
  type?: AlertType;
  buttons?: AlertButton[];
}

type Listener = (options: AlertOptions | null) => void;
const listeners = new Set<Listener>();

export const CustomAlert = {
  show(options: AlertOptions) {
    listeners.forEach((l) => l(options));
  },
  hide() {
    listeners.forEach((l) => l(null));
  },
  subscribe(listener: Listener) {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  },
};

export const CustomAlertContainer: React.FC = () => {
  const [options, setOptions] = useState<AlertOptions | null>(null);
  const [fadeAnim] = useState(new Animated.Value(0));

  useEffect(() => {
    return CustomAlert.subscribe((opts) => {
      if (opts) {
        setOptions(opts);
        Animated.timing(fadeAnim, {
          toValue: 1,
          duration: 200,
          useNativeDriver: true,
        }).start();
      } else {
        Animated.timing(fadeAnim, {
          toValue: 0,
          duration: 150,
          useNativeDriver: true,
        }).start(() => setOptions(null));
      }
    });
  }, []);

  if (!options) return null;

  const alertType: AlertType = options.type || (options.title.toLowerCase().includes('hata') ? 'error' : 'info');

  const getIcon = () => {
    switch (alertType) {
      case 'success':
        return <CheckCircle2 size={32} color="#4CAF50" />;
      case 'error':
        return <XCircle size={32} color="#F44336" />;
      case 'warning':
        return <AlertTriangle size={32} color="#FF9800" />;
      default:
        return <Info size={32} color={Colors.primary} />;
    }
  };

  const getBadgeBg = () => {
    switch (alertType) {
      case 'success':
        return 'rgba(76, 175, 80, 0.15)';
      case 'error':
        return 'rgba(244, 67, 54, 0.15)';
      case 'warning':
        return 'rgba(255, 152, 0, 0.15)';
      default:
        return 'rgba(255, 107, 0, 0.15)';
    }
  };

  const buttons: AlertButton[] =
    options.buttons && options.buttons.length > 0
      ? options.buttons
      : [{ text: 'Tamam', style: 'default' }];

  const handleButtonPress = (btn: AlertButton) => {
    CustomAlert.hide();
    if (btn.onPress) {
      setTimeout(() => {
        btn.onPress!();
      }, 100);
    }
  };

  return (
    <Modal transparent visible={!!options} animationType="none" onRequestClose={() => CustomAlert.hide()}>
      <View style={styles.backdrop}>
        <Animated.View style={[styles.dialogCard, { opacity: fadeAnim, transform: [{ scale: fadeAnim }] }]}>
          {/* Icon Badge */}
          <View style={[styles.iconBadge, { backgroundColor: getBadgeBg() }]}>{getIcon()}</View>

          {/* Title & Message */}
          <Text style={styles.title}>{options.title}</Text>
          {options.message ? <Text style={styles.message}>{options.message}</Text> : null}

          {/* Action Buttons */}
          <View style={[styles.buttonRow, buttons.length > 2 && { flexDirection: 'column' }]}>
            {buttons.map((btn, index) => {
              const isCancel = btn.style === 'cancel';
              const isDestructive = btn.style === 'destructive';

              return (
                <TouchableOpacity
                  key={index}
                  style={[
                    styles.button,
                    isCancel && styles.cancelButton,
                    isDestructive && styles.destructiveButton,
                    !isCancel && !isDestructive && styles.primaryButton,
                    buttons.length === 1 && { width: '100%' },
                    buttons.length === 2 && { flex: 1 },
                    buttons.length > 2 && { width: '100%' },
                  ]}
                  activeOpacity={0.8}
                  onPress={() => handleButtonPress(btn)}
                >
                  <Text
                    style={[
                      styles.buttonText,
                      isCancel && styles.cancelButtonText,
                      isDestructive && styles.destructiveButtonText,
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
    backgroundColor: 'rgba(0, 0, 0, 0.75)',
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 28,
  },
  dialogCard: {
    width: '100%',
    maxWidth: 340,
    backgroundColor: '#1E1F25',
    borderRadius: 24,
    padding: 24,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.08)',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 10 },
    shadowOpacity: 0.5,
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
    color: Colors.textDark,
    textAlign: 'center',
  },
  message: {
    fontSize: 14,
    color: Colors.textSecondaryDark,
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
  primaryButton: {
    backgroundColor: Colors.primary,
  },
  cancelButton: {
    backgroundColor: 'rgba(255, 255, 255, 0.08)',
  },
  destructiveButton: {
    backgroundColor: '#F44336',
  },
  buttonText: {
    fontSize: 15,
    fontWeight: '700',
    color: Colors.allWhite,
  },
  cancelButtonText: {
    color: Colors.textSecondaryDark,
  },
  destructiveButtonText: {
    color: Colors.allWhite,
  },
});
