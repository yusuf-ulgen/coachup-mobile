import React from 'react';
import { Modal, View, Text, StyleSheet, TouchableOpacity, Dimensions } from 'react-native';
import { Watch, Heart, Flame, ShieldCheck, X } from 'lucide-react-native';
import { Colors } from '../theme/colors';

const { width } = Dimensions.get('window');

interface HealthPermissionModalProps {
  visible: boolean;
  onDismiss: () => void;
  onGrantPermission: () => void;
}

export const HealthPermissionModal: React.FC<HealthPermissionModalProps> = ({
  visible,
  onDismiss,
  onGrantPermission,
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
          {/* Close button */}
          <TouchableOpacity style={styles.closeBtn} onPress={onDismiss} activeOpacity={0.7}>
            <X size={20} color="#8E8E93" />
          </TouchableOpacity>

          {/* Header Icon */}
          <View style={styles.iconGlowRing}>
            <View style={styles.iconCircle}>
              <Watch size={32} color="#FF3B30" />
            </View>
          </View>

          {/* Titles */}
          <Text style={styles.title}>Akıllı Saat & Sağlık İzni</Text>
          <Text style={styles.subtitle}>
            Antrenman metriklerinizi akıllı saatiniz ve Sağlık (Health Connect) uygulaması ile doğrudan senkronize edin.
          </Text>

          {/* Feature List */}
          <View style={styles.featureBox}>
            <View style={styles.featureRow}>
              <View style={[styles.featureIconBadge, { backgroundColor: 'rgba(255, 59, 48, 0.15)' }]}>
                <Heart size={20} color="#FF3B30" fill="#FF3B30" />
              </View>
              <View style={styles.featureTextCol}>
                <Text style={styles.featureTitle}>Canlı Nabız Takibi</Text>
                <Text style={styles.featureSub}>Akıllı saatinizden anlık nabız ve nabız bölgeleri</Text>
              </View>
            </View>

            <View style={styles.divider} />

            <View style={styles.featureRow}>
              <View style={[styles.featureIconBadge, { backgroundColor: 'rgba(255, 149, 0, 0.15)' }]}>
                <Flame size={20} color="#FF9500" fill="#FF9500" />
              </View>
              <View style={styles.featureTextCol}>
                <Text style={styles.featureTitle}>Gerçek Kalori Hesabı</Text>
                <Text style={styles.featureSub}>Tahmin değil, saatinizden gelen kesin kalori verisi</Text>
              </View>
            </View>

            <View style={styles.divider} />

            <View style={styles.featureRow}>
              <View style={[styles.featureIconBadge, { backgroundColor: 'rgba(52, 199, 89, 0.15)' }]}>
                <ShieldCheck size={20} color="#34C759" />
              </View>
              <View style={styles.featureTextCol}>
                <Text style={styles.featureTitle}>Güvenli Bağlantı</Text>
                <Text style={styles.featureSub}>Verileriniz yalnızca sizin cihazınızda işlenir</Text>
              </View>
            </View>
          </View>

          {/* Buttons */}
          <View style={styles.buttonCol}>
            <TouchableOpacity
              style={styles.primaryPillBtn}
              onPress={onGrantPermission}
              activeOpacity={0.85}
            >
              <Watch size={20} color="#FFFFFF" style={{ marginRight: 8 }} />
              <Text style={styles.primaryPillBtnText}>İzinleri Aç (Health Connect)</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={styles.secondaryBtn}
              onPress={onDismiss}
              activeOpacity={0.7}
            >
              <Text style={styles.secondaryBtnText}>Şimdi Değil (İzinsiz Devam Et)</Text>
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
    paddingTop: 24,
    paddingBottom: 36,
    borderWidth: 1,
    borderColor: '#242736',
    alignItems: 'center',
  },
  closeBtn: {
    position: 'absolute',
    top: 16,
    right: 16,
    padding: 8,
    borderRadius: 20,
    backgroundColor: '#1C1E2B',
  },
  iconGlowRing: {
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: 'rgba(255, 59, 48, 0.12)',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 16,
    borderWidth: 1,
    borderColor: 'rgba(255, 59, 48, 0.3)',
  },
  iconCircle: {
    width: 60,
    height: 60,
    borderRadius: 30,
    backgroundColor: '#1C1E2B',
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: {
    fontSize: 22,
    fontWeight: '700',
    color: '#FFFFFF',
    marginBottom: 8,
    textAlign: 'center',
  },
  subtitle: {
    fontSize: 14,
    color: '#989BAA',
    textAlign: 'center',
    lineHeight: 20,
    marginBottom: 24,
    paddingHorizontal: 12,
  },
  featureBox: {
    width: '100%',
    backgroundColor: '#1A1C28',
    borderRadius: 18,
    padding: 16,
    borderWidth: 1,
    borderColor: '#2A2D3F',
    marginBottom: 24,
  },
  featureRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 4,
  },
  featureIconBadge: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 14,
  },
  featureTextCol: {
    flex: 1,
  },
  featureTitle: {
    fontSize: 15,
    fontWeight: '600',
    color: '#FFFFFF',
    marginBottom: 2,
  },
  featureSub: {
    fontSize: 12,
    color: '#8E92A4',
  },
  divider: {
    height: 1,
    backgroundColor: '#262939',
    marginVertical: 12,
  },
  buttonCol: {
    width: '100%',
    gap: 12,
  },
  primaryPillBtn: {
    height: 52,
    backgroundColor: Colors.primary || '#FF3B30',
    borderRadius: 26,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    elevation: 4,
    shadowColor: Colors.primary || '#FF3B30',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
  },
  primaryPillBtnText: {
    fontSize: 16,
    fontWeight: '700',
    color: '#FFFFFF',
  },
  secondaryBtn: {
    height: 44,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
  },
  secondaryBtnText: {
    fontSize: 14,
    fontWeight: '500',
    color: '#7E8299',
  },
});
