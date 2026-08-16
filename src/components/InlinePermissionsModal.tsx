import React, { useState } from 'react';
import { Modal, View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { MapPin, Bell, ShieldCheck } from 'lucide-react-native';
import { Colors } from '../theme/colors';
import {
  PermissionPreferenceService,
  PermissionChoice,
} from '../services/permissionPreferenceService';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

interface InlinePermissionsModalProps {
  visible: boolean;
  promptLocation?: boolean;
  promptNotification?: boolean;
  onDismiss: () => void;
  onComplete?: () => void;
}

export const InlinePermissionsModal: React.FC<InlinePermissionsModalProps> = ({
  visible,
  promptLocation = true,
  promptNotification = true,
  onDismiss,
  onComplete,
}) => {
  const insets = useSafeAreaInsets();
  const bottomPadding = Math.max(48, insets.bottom + 28);

  // Steps: 'location' | 'notification' | 'done'
  const initialStep = promptLocation ? 'location' : promptNotification ? 'notification' : 'done';
  const [currentStep, setCurrentStep] = useState<'location' | 'notification' | 'done'>(initialStep);

  // Sync initial step when visible changes
  React.useEffect(() => {
    if (visible) {
      if (promptLocation) {
        setCurrentStep('location');
      } else if (promptNotification) {
        setCurrentStep('notification');
      } else {
        setCurrentStep('done');
      }
    }
  }, [visible, promptLocation, promptNotification]);

  const handleLocationChoice = async (choice: PermissionChoice) => {
    await PermissionPreferenceService.setLocationPreference(choice);
    if (choice === 'ALWAYS_ALLOW' || choice === 'ALLOW_ONCE') {
      await PermissionPreferenceService.requestLocationSystemPermission();
    }

    if (promptNotification) {
      setCurrentStep('notification');
    } else {
      setCurrentStep('done');
      onComplete?.();
      onDismiss();
    }
  };

  const handleNotificationChoice = async (choice: PermissionChoice) => {
    await PermissionPreferenceService.setNotificationPreference(choice);
    if (choice === 'ALWAYS_ALLOW' || choice === 'ALLOW_ONCE') {
      await PermissionPreferenceService.requestNotificationSystemPermission();
    }

    setCurrentStep('done');
    onComplete?.();
    onDismiss();
  };

  if (!visible || currentStep === 'done') {
    return null;
  }

  const isLocationStep = currentStep === 'location';

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onDismiss}>
      <View style={styles.overlay}>
        <View style={[styles.sheetContainer, { paddingBottom: bottomPadding }]}>
          {/* Top handle */}
          <View style={styles.handleBar} />

          {/* Badge Step Indicator */}
          <View style={styles.stepBadge}>
            <ShieldCheck size={14} color={Colors.primary} />
            <Text style={styles.stepBadgeText}>
              {isLocationStep && promptNotification
                ? 'İzinler · 1 / 2'
                : !isLocationStep && promptLocation
                ? 'İzinler · 2 / 2'
                : 'Uygulama İzni'}
            </Text>
          </View>

          {/* Icon Circle */}
          <View style={styles.iconGlowRing}>
            <View style={styles.iconCircle}>
              {isLocationStep ? (
                <MapPin size={32} color={Colors.primary} />
              ) : (
                <Bell size={32} color={Colors.primary} />
              )}
            </View>
          </View>

          {/* Title */}
          <Text style={styles.title}>
            {isLocationStep ? 'Konum İzni' : 'Bildirim İzni'}
          </Text>

          {/* Description */}
          <Text style={styles.description}>
            {isLocationStep
              ? 'Açık hava antrenmanlarında (Koşu, Yürüyüş, Bisiklet vb.) rotanızı, mesafenizi ve temponuzu gerçek zamanlı takip edebilmek için konum erişimine ihtiyaç duyuyoruz.'
              : 'Antrenman hatırlatıcıları, antrenör mesajları ve gelişim bildirimlerini anlık alabilmek için bildirim izni gereklidir.'}
          </Text>

          {/* 3 Option Buttons */}
          <View style={styles.buttonStack}>
            {/* 1. Her zaman izin ver */}
            <TouchableOpacity
              style={[styles.optionBtn, styles.btnAlways]}
              activeOpacity={0.85}
              onPress={() =>
                isLocationStep
                  ? handleLocationChoice('ALWAYS_ALLOW')
                  : handleNotificationChoice('ALWAYS_ALLOW')
              }
            >
              <Text style={styles.btnTextLight}>Her zaman izin ver</Text>
            </TouchableOpacity>

            {/* 2. Bu seferlik izin ver */}
            <TouchableOpacity
              style={[styles.optionBtn, styles.btnOnce]}
              activeOpacity={0.85}
              onPress={() =>
                isLocationStep
                  ? handleLocationChoice('ALLOW_ONCE')
                  : handleNotificationChoice('ALLOW_ONCE')
              }
            >
              <Text style={styles.btnTextLight}>Bu seferlik izin ver</Text>
            </TouchableOpacity>

            {/* 3. İzin verme */}
            <TouchableOpacity
              style={[styles.optionBtn, styles.btnDeny]}
              activeOpacity={0.85}
              onPress={() =>
                isLocationStep
                  ? handleLocationChoice('DENIED')
                  : handleNotificationChoice('DENIED')
              }
            >
              <Text style={styles.btnTextMuted}>İzin verme</Text>
            </TouchableOpacity>
          </View>
        </View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.78)',
    justifyContent: 'flex-end',
  },
  sheetContainer: {
    backgroundColor: '#12131A',
    borderTopLeftRadius: 30,
    borderTopRightRadius: 30,
    paddingHorizontal: 24,
    paddingTop: 14,
    borderWidth: 1,
    borderColor: '#242736',
    alignItems: 'center',
  },
  handleBar: {
    width: 38,
    height: 4,
    borderRadius: 2,
    backgroundColor: '#34384A',
    marginBottom: 16,
  },
  stepBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 12,
    paddingVertical: 5,
    borderRadius: 100,
    backgroundColor: 'rgba(255, 94, 0, 0.12)',
    borderWidth: 1,
    borderColor: 'rgba(255, 94, 0, 0.3)',
    marginBottom: 16,
  },
  stepBadgeText: {
    fontSize: 12,
    fontWeight: '700',
    color: Colors.primary,
  },
  iconGlowRing: {
    width: 76,
    height: 76,
    borderRadius: 38,
    backgroundColor: 'rgba(255, 94, 0, 0.1)',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 16,
    borderWidth: 1,
    borderColor: 'rgba(255, 94, 0, 0.25)',
  },
  iconCircle: {
    width: 58,
    height: 58,
    borderRadius: 29,
    backgroundColor: '#1A1C28',
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: {
    fontSize: 20,
    fontWeight: '800',
    color: '#FFFFFF',
    textAlign: 'center',
    marginBottom: 10,
    letterSpacing: -0.2,
  },
  description: {
    fontSize: 14,
    color: '#9E9EB2',
    textAlign: 'center',
    lineHeight: 21,
    marginBottom: 26,
    paddingHorizontal: 8,
  },
  buttonStack: {
    width: '100%',
    gap: 10,
  },
  optionBtn: {
    width: '100%',
    height: 52,
    borderRadius: 26,
    alignItems: 'center',
    justifyContent: 'center',
  },
  btnAlways: {
    backgroundColor: Colors.primary,
    elevation: 3,
    shadowColor: Colors.primary,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
  },
  btnOnce: {
    backgroundColor: '#272938',
    borderWidth: 1,
    borderColor: '#373A4F',
  },
  btnDeny: {
    backgroundColor: 'transparent',
  },
  btnTextLight: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '700',
  },
  btnTextMuted: {
    color: '#7E8199',
    fontSize: 14,
    fontWeight: '600',
  },
});
