import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Modal,
  Image,
  Dimensions,
  ScrollView,
  Alert,
} from 'react-native';
import { X, Image as ImageIcon, Share2 } from 'lucide-react-native';
import { Colors } from '../../theme/colors';

const { width: SCREEN_WIDTH } = Dimensions.get('window');

const TEMPLATES = [
  { id: 'MAP_FOCUSED', title: 'Rota Odaklı', desc: 'Harita ve rota çizimini ön plana çıkarır' },
  { id: 'DETAILED', title: 'Detaylı İstatistik', desc: 'Tüm performans verilerini şık kartlarda sunar' },
  { id: 'SIMPLE', title: 'Minimal', desc: 'Sadece mesafe ve süre vurgusu yapar' },
  { id: 'CARD_STORY', title: 'Hikaye Kartı', desc: 'Koyu arka plan ile sosyal medya için ideal kart' },
];

export const WorkoutShareSheet: React.FC<{
  visible: boolean;
  training: any;
  durationSeconds: number;
  distanceKm: number;
  totalCalories: number;
  onDismiss: () => void;
}> = ({ visible, training, durationSeconds, distanceKm, totalCalories, onDismiss }) => {
  const [activeTemplate, setActiveTemplate] = useState(0);

  const handleShare = () => {
    // In a real app, use react-native-view-shot and react-native-share
    Alert.alert('Paylaş', 'Görsel paylaşım simülasyonu başarılı.');
  };

  if (!visible) return null;

  return (
    <Modal visible={visible} animationType="slide" transparent>
      <View style={styles.container}>
        <View style={styles.header}>
          <TouchableOpacity style={styles.iconButton} onPress={onDismiss}>
            <X size={20} color={Colors.allWhite} />
          </TouchableOpacity>
          <Text style={styles.headerTitle}>Aktiviteyi Paylaş</Text>
          <View style={{ width: 36 }} />
        </View>

        <View style={styles.previewContainer}>
          <ScrollView 
            horizontal 
            pagingEnabled 
            showsHorizontalScrollIndicator={false}
            onMomentumScrollEnd={(e) => {
              const idx = Math.round(e.nativeEvent.contentOffset.x / SCREEN_WIDTH);
              setActiveTemplate(idx);
            }}
          >
            {TEMPLATES.map((tpl, i) => (
              <View key={tpl.id} style={styles.page}>
                <View style={styles.cardPreview}>
                  <Text style={styles.previewTitle}>{training?.title || 'Antrenman'}</Text>
                  <Text style={styles.previewData}>{Math.floor(durationSeconds/60)} dk {durationSeconds%60} sn</Text>
                  {distanceKm > 0 && <Text style={styles.previewData}>{distanceKm.toFixed(2)} km</Text>}
                  <Text style={styles.previewTemplateName}>{tpl.title}</Text>
                </View>
              </View>
            ))}
          </ScrollView>

          <View style={styles.pagination}>
            {TEMPLATES.map((_, i) => (
              <View key={i} style={[styles.dot, activeTemplate === i && styles.dotActive]} />
            ))}
          </View>
          
          <Text style={styles.templateTitle}>{TEMPLATES[activeTemplate].title}</Text>
          <Text style={styles.templateDesc}>{TEMPLATES[activeTemplate].desc}</Text>
        </View>

        <View style={styles.footer}>
          <TouchableOpacity style={styles.secondaryButton}>
            <ImageIcon size={18} color={Colors.allWhite} />
            <Text style={styles.secondaryButtonText}>Arka Plan Fotoğrafı Ekle</Text>
          </TouchableOpacity>
          
          <TouchableOpacity style={styles.primaryButton} onPress={handleShare}>
            <Share2 size={18} color={Colors.allWhite} />
            <Text style={styles.primaryButtonText}>Görsel Olarak Paylaş</Text>
          </TouchableOpacity>
        </View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0A0A0F',
    paddingTop: 48,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  iconButton: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: 'rgba(255,255,255,0.1)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  headerTitle: {
    flex: 1,
    textAlign: 'center',
    fontSize: 18,
    fontWeight: 'bold',
    color: Colors.allWhite,
  },
  previewContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  page: {
    width: SCREEN_WIDTH,
    alignItems: 'center',
    justifyContent: 'center',
  },
  cardPreview: {
    width: SCREEN_WIDTH * 0.82,
    aspectRatio: 9 / 16,
    backgroundColor: '#18102B',
    borderRadius: 20,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.12)',
    padding: 24,
    alignItems: 'center',
    justifyContent: 'center',
  },
  previewTitle: {
    color: Colors.primary,
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 16,
  },
  previewData: {
    color: Colors.allWhite,
    fontSize: 24,
    fontWeight: '900',
    marginBottom: 8,
  },
  previewTemplateName: {
    color: 'rgba(255,255,255,0.5)',
    fontSize: 12,
    marginTop: 24,
  },
  pagination: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    marginVertical: 14,
  },
  dot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: 'rgba(255,255,255,0.3)',
    marginHorizontal: 3,
  },
  dotActive: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: Colors.primary,
  },
  templateTitle: {
    fontSize: 14,
    fontWeight: 'bold',
    color: Colors.allWhite,
    marginBottom: 4,
  },
  templateDesc: {
    fontSize: 12,
    color: 'rgba(255,255,255,0.5)',
    textAlign: 'center',
    paddingHorizontal: 32,
  },
  footer: {
    paddingHorizontal: 20,
    paddingBottom: 32,
    paddingTop: 16,
  },
  secondaryButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    height: 48,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.15)',
    backgroundColor: 'rgba(255,255,255,0.06)',
    marginBottom: 10,
  },
  secondaryButtonText: {
    color: Colors.allWhite,
    fontSize: 14,
    fontWeight: '500',
    marginLeft: 8,
  },
  primaryButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    height: 52,
    borderRadius: 14,
    backgroundColor: Colors.primary,
  },
  primaryButtonText: {
    color: Colors.allWhite,
    fontSize: 16,
    fontWeight: 'bold',
    marginLeft: 8,
  },
});
