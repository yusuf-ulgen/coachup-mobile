import React from 'react';
import { Modal, View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { Watch, X } from 'lucide-react-native';

interface HealthPermissionModalProps {
  visible: boolean;
  onDismiss: () => void;
  onSelectBleSmartWatch: () => void;
  onSelectHealthConnect: () => void;
  onSelectNoPermission: () => void;
}

export const HealthPermissionModal: React.FC<HealthPermissionModalProps> = ({
  visible,
  onDismiss,
  onSelectBleSmartWatch,
  onSelectHealthConnect,
  onSelectNoPermission,
}) => {
  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onRequestClose={onDismiss}
    >
      <View style={styles.overlay}>
        <View style={styles.dialogCard}>
          {/* Top Close Button */}
          <TouchableOpacity style={styles.closeBtn} onPress={onDismiss} activeOpacity={0.7}>
            <X size={18} color="#8E92A4" />
          </TouchableOpacity>

          {/* Top Icon */}
          <View style={styles.iconCircle}>
            <Watch size={28} color="#007AFF" />
          </View>

          {/* Title & Prompt */}
          <Text style={styles.title}>Nabız & Akıllı Saat Bağlantısı</Text>
          <Text style={styles.subtitle}>
            Nabız ve kalori verilerinizi nasıl almak istersiniz?
          </Text>

          {/* Stacked Clean Text Action Buttons */}
          <View style={styles.actionsContainer}>
            <TouchableOpacity
              style={styles.actionBtn}
              onPress={onSelectBleSmartWatch}
              activeOpacity={0.7}
            >
              <Text style={styles.actionTextPrimary}>Doğrudan Akıllı Saat (Bluetooth)</Text>
            </TouchableOpacity>

            <View style={styles.divider} />

            <TouchableOpacity
              style={styles.actionBtn}
              onPress={onSelectHealthConnect}
              activeOpacity={0.7}
            >
              <Text style={styles.actionTextPrimary}>Health Connect / Apple Health</Text>
            </TouchableOpacity>

            <View style={styles.divider} />

            <TouchableOpacity
              style={styles.actionBtn}
              onPress={onSelectNoPermission}
              activeOpacity={0.7}
            >
              <Text style={styles.actionTextCancel}>İzin Verme</Text>
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
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 24,
  },
  dialogCard: {
    width: '100%',
    maxWidth: 340,
    backgroundColor: '#232530',
    borderRadius: 24,
    paddingTop: 28,
    paddingBottom: 20,
    paddingHorizontal: 20,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#323545',
    elevation: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.4,
    shadowRadius: 12,
  },
  closeBtn: {
    position: 'absolute',
    top: 14,
    right: 14,
    padding: 6,
    borderRadius: 16,
    backgroundColor: 'rgba(255, 255, 255, 0.06)',
  },
  iconCircle: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: 'rgba(0, 122, 255, 0.12)',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 16,
  },
  title: {
    fontSize: 18,
    fontWeight: '700',
    color: '#FFFFFF',
    marginBottom: 8,
    textAlign: 'center',
  },
  subtitle: {
    fontSize: 13,
    color: '#A0A4B8',
    textAlign: 'center',
    lineHeight: 18,
    marginBottom: 24,
    paddingHorizontal: 10,
  },
  actionsContainer: {
    width: '100%',
    alignItems: 'center',
  },
  actionBtn: {
    width: '100%',
    paddingVertical: 14,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 12,
  },
  actionTextPrimary: {
    fontSize: 15,
    fontWeight: '600',
    color: '#FFFFFF',
    textAlign: 'center',
  },
  actionTextCancel: {
    fontSize: 15,
    fontWeight: '600',
    color: '#FF453A',
    textAlign: 'center',
  },
  divider: {
    width: '100%',
    height: 1,
    backgroundColor: 'rgba(255, 255, 255, 0.08)',
  },
});
