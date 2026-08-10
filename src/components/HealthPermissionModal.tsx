import React from 'react';
import { Modal, View, Text, StyleSheet, TouchableOpacity, ScrollView } from 'react-native';
import { Watch, HeartHandshake, ShieldAlert, X, Bluetooth } from 'lucide-react-native';
import { Colors } from '../theme/colors';

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
          <Text style={styles.title}>Nabız & Akıllı Saat Bağlantısı</Text>
          <Text style={styles.subtitle}>
            Antrenman sırasında nabız ve kalori takibini nasıl yapmak istersiniz? Tercihinizi seçin:
          </Text>

          <ScrollView style={{ width: '100%', maxHeight: 380 }} showsVerticalScrollIndicator={false}>
            {/* OPTION 1: Direct Bluetooth SmartWatch */}
            <TouchableOpacity
              style={[styles.optionCard, styles.recommendedCard]}
              onPress={onSelectBleSmartWatch}
              activeOpacity={0.85}
            >
              <View style={styles.optionHeaderRow}>
                <View style={[styles.optionIconBadge, { backgroundColor: 'rgba(255, 59, 48, 0.2)' }]}>
                  <Bluetooth size={22} color="#FF3B30" />
                </View>
                <View style={{ flex: 1 }}>
                  <View style={styles.tagRow}>
                    <Text style={styles.optionTitle}>1. Doğrudan Akıllı Saat (Bluetooth)</Text>
                    <View style={styles.recommendedBadge}>
                      <Text style={styles.recommendedText}>ÖNERİLEN</Text>
                    </View>
                  </View>
                  <Text style={styles.optionSub}>
                    Ek hiçbir uygulama indirmeden saatinizdeki (Apple, Samsung, Xiaomi, Huawei, Garmin vb.) sensörden Bluetooth ile anlık nabız okur.
                  </Text>
                </View>
              </View>
            </TouchableOpacity>

            {/* OPTION 2: Health Connect */}
            <TouchableOpacity
              style={styles.optionCard}
              onPress={onSelectHealthConnect}
              activeOpacity={0.85}
            >
              <View style={styles.optionHeaderRow}>
                <View style={[styles.optionIconBadge, { backgroundColor: 'rgba(0, 122, 255, 0.2)' }]}>
                  <HeartHandshake size={22} color="#007AFF" />
                </View>
                <View style={{ flex: 1 }}>
                  <Text style={styles.optionTitle}>2. Health Connect / Apple Health</Text>
                  <Text style={styles.optionSub}>
                    Google Health Connect veya Apple Health uygulamanızdaki kaydedilmiş nabız ve kalori verilerini senkronize eder.
                  </Text>
                </View>
              </View>
            </TouchableOpacity>

            {/* OPTION 3: No Permission */}
            <TouchableOpacity
              style={[styles.optionCard, { borderColor: '#2A2D3E' }]}
              onPress={onSelectNoPermission}
              activeOpacity={0.85}
            >
              <View style={styles.optionHeaderRow}>
                <View style={[styles.optionIconBadge, { backgroundColor: 'rgba(142, 142, 147, 0.2)' }]}>
                  <ShieldAlert size={22} color="#8E8E93" />
                </View>
                <View style={{ flex: 1 }}>
                  <Text style={[styles.optionTitle, { color: '#B0B3C7' }]}>3. İzinsiz Devam Et</Text>
                  <Text style={styles.optionSub}>
                    Sensör bağlantısı olmadan devam eder. Nabız ve kalori metrikleri "-" olarak gösterilir (rastgele veri üretilmez).
                  </Text>
                </View>
              </View>
            </TouchableOpacity>
          </ScrollView>
        </View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.8)',
    justifyContent: 'flex-end',
  },
  sheetContainer: {
    backgroundColor: '#12131A',
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    paddingHorizontal: 20,
    paddingTop: 24,
    paddingBottom: 32,
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
    zIndex: 10,
  },
  iconGlowRing: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: 'rgba(255, 59, 48, 0.12)',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 14,
    borderWidth: 1,
    borderColor: 'rgba(255, 59, 48, 0.3)',
  },
  iconCircle: {
    width: 54,
    height: 54,
    borderRadius: 27,
    backgroundColor: '#1C1E2B',
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: {
    fontSize: 21,
    fontWeight: '700',
    color: '#FFFFFF',
    marginBottom: 6,
    textAlign: 'center',
  },
  subtitle: {
    fontSize: 13,
    color: '#989BAA',
    textAlign: 'center',
    lineHeight: 18,
    marginBottom: 20,
    paddingHorizontal: 8,
  },
  optionCard: {
    width: '100%',
    backgroundColor: '#1A1C28',
    borderRadius: 16,
    padding: 16,
    borderWidth: 1,
    borderColor: '#2A2D3F',
    marginBottom: 12,
  },
  recommendedCard: {
    borderColor: 'rgba(255, 59, 48, 0.5)',
    backgroundColor: '#1E1D2B',
  },
  optionHeaderRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
  },
  optionIconBadge: {
    width: 42,
    height: 42,
    borderRadius: 21,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  tagRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 4,
  },
  optionTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: '#FFFFFF',
  },
  recommendedBadge: {
    backgroundColor: '#FF3B30',
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 10,
  },
  recommendedText: {
    fontSize: 9,
    fontWeight: '800',
    color: '#FFFFFF',
  },
  optionSub: {
    fontSize: 12,
    color: '#8E92A4',
    lineHeight: 16,
    marginTop: 2,
  },
});
