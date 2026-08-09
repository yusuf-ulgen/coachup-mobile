import React from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
} from 'react-native';
import { Colors } from '../theme/colors';
import { Play } from 'lucide-react-native';
import { SmoothModal } from './motion/SmoothModal';

interface PreWorkoutStartModalProps {
  visible: boolean;
  activityTitle: string;
  activityEmoji?: string;
  onClose: () => void;
  onStart: () => void;
}

export const PreWorkoutStartModal: React.FC<PreWorkoutStartModalProps> = ({
  visible,
  activityTitle,
  activityEmoji = '🏋️',
  onClose,
  onStart,
}) => {
  return (
    <SmoothModal
      visible={visible}
      onClose={onClose}
      variant="modal"
    >
      <View style={styles.container}>
        {/* Top Emoji */}
        <Text style={styles.emojiText}>{activityEmoji}</Text>

        {/* Title */}
        <Text style={styles.titleText}>{activityTitle}</Text>

        {/* Subtitle */}
        <Text style={styles.subText}>Antrenmana başlamak ister misiniz?</Text>

        {/* Primary Action Button: ► Başla */}
        <TouchableOpacity style={styles.startBtn} onPress={onStart} activeOpacity={0.85}>
          <Play size={18} color={Colors.allWhite} fill={Colors.allWhite} style={{ marginRight: 6 }} />
          <Text style={styles.startBtnText}>Başla</Text>
        </TouchableOpacity>

        {/* Secondary Action: Vazgeç */}
        <TouchableOpacity style={styles.cancelBtn} onPress={onClose} activeOpacity={0.7}>
          <Text style={styles.cancelBtnText}>Vazgeç</Text>
        </TouchableOpacity>
      </View>
    </SmoothModal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: '#050201', // Pitch dark background as in Image 1
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 24,
  },
  container: {
    width: '100%',
    alignItems: 'center',
    paddingVertical: 20,
  },
  emojiText: {
    fontSize: 72,
    marginBottom: 20,
  },
  titleText: {
    fontSize: 28,
    fontWeight: '800',
    color: Colors.textDark,
    marginBottom: 8,
    textAlign: 'center',
  },
  subText: {
    fontSize: 15,
    color: Colors.textSecondaryDark,
    marginBottom: 40,
    textAlign: 'center',
  },
  startBtn: {
    width: '100%',
    height: 56,
    borderRadius: 100,
    backgroundColor: Colors.primary,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    elevation: 4,
    marginBottom: 24,
  },
  startBtnText: {
    color: Colors.allWhite,
    fontSize: 18,
    fontWeight: '700',
  },
  cancelBtn: {
    paddingVertical: 12,
    paddingHorizontal: 24,
  },
  cancelBtnText: {
    color: Colors.textSecondaryDark,
    fontSize: 16,
    fontWeight: '600',
  },
});
