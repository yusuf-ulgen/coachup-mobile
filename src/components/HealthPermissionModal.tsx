import React from 'react';
import { Modal, View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { Bluetooth } from 'lucide-react-native';

interface HealthPermissionModalProps {
  visible: boolean;
  onDismiss: () => void;
  onSelectAlwaysAllow: () => void;
  onSelectAllowOnce: () => void;
  onSelectNoPermission: () => void;
}

export const HealthPermissionModal: React.FC<HealthPermissionModalProps> = ({
  visible,
  onDismiss,
  onSelectAlwaysAllow,
  onSelectAllowOnce,
  onSelectNoPermission,
}) => {
  return (
    <Modal
      visible={visible}
      transparent
      animationType="slide"
      onRequestClose={onDismiss}
    >
      <View style={styles.overlay}>
        <View style={styles.sheetContainer}>
          {/* Handle bar */}
          <View style={styles.handleBar} />

          {/* Header Bluetooth Icon with Orange Glow */}
          <View style={styles.iconGlowRing}>
            <View style={styles.iconCircle}>
              <Bluetooth size={28} color="#FF5E00" />
            </View>
          </View>

          {/* Question Text */}
          <Text style={styles.questionText}>
            Nabız verisini almak için Bluetooth izni vermek istiyor musunuz?
          </Text>

          {/* 3 Option Buttons */}
          <View style={styles.buttonStack}>
            {/* Option 1: Her zaman izin ver */}
            <TouchableOpacity
              style={[styles.optionBtn, styles.btnAlwaysAllow]}
              onPress={onSelectAlwaysAllow}
              activeOpacity={0.85}
            >
              <Text style={styles.btnTextLight}>Her zaman izin ver</Text>
            </TouchableOpacity>

            {/* Option 2: Bu seferlik izin ver */}
            <TouchableOpacity
              style={[styles.optionBtn, styles.btnAllowOnce]}
              onPress={onSelectAllowOnce}
              activeOpacity={0.85}
            >
              <Text style={styles.btnTextLight}>Bu seferlik izin ver</Text>
            </TouchableOpacity>

            {/* Option 3: İzin verme */}
            <TouchableOpacity
              style={[styles.optionBtn, styles.btnNoPermission]}
              onPress={onSelectNoPermission}
              activeOpacity={0.85}
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
    backgroundColor: 'rgba(0, 0, 0, 0.75)',
    justifyContent: 'flex-end',
  },
  sheetContainer: {
    backgroundColor: '#12131A',
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    paddingHorizontal: 24,
    paddingTop: 12,
    paddingBottom: 36,
    borderWidth: 1,
    borderColor: '#242736',
    alignItems: 'center',
  },
  handleBar: {
    width: 36,
    height: 4,
    borderRadius: 2,
    backgroundColor: '#34384A',
    marginBottom: 20,
  },
  iconGlowRing: {
    width: 68,
    height: 68,
    borderRadius: 34,
    backgroundColor: 'rgba(255, 94, 0, 0.12)',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 16,
    borderWidth: 1,
    borderColor: 'rgba(255, 94, 0, 0.3)',
  },
  iconCircle: {
    width: 52,
    height: 52,
    borderRadius: 26,
    backgroundColor: '#1A1C28',
    alignItems: 'center',
    justifyContent: 'center',
  },
  questionText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#FFFFFF',
    textAlign: 'center',
    lineHeight: 22,
    marginBottom: 24,
    paddingHorizontal: 8,
  },
  buttonStack: {
    width: '100%',
    gap: 12,
  },
  optionBtn: {
    width: '100%',
    height: 50,
    borderRadius: 25,
    alignItems: 'center',
    justifyContent: 'center',
  },
  btnAlwaysAllow: {
    backgroundColor: '#FF5E00', // Vibrant Orange
  },
  btnAllowOnce: {
    backgroundColor: '#3B3E52', // Dark Slate Accent
  },
  btnNoPermission: {
    backgroundColor: '#2A2C3A', // Dark Warm Ash
  },
  btnTextLight: {
    fontSize: 15,
    fontWeight: '700',
    color: '#FFFFFF',
  },
  btnTextMuted: {
    fontSize: 15,
    fontWeight: '600',
    color: '#A0A4B8',
  },
});

