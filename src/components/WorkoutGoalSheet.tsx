import React, { useState } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Modal,
} from 'react-native';
import { Colors } from '../theme/colors';

export interface WorkoutGoal {
  type: 'none' | 'distance' | 'duration';
  distanceKm?: number;
  durationSeconds?: number;
  label?: string;
}

interface WorkoutGoalSheetProps {
  visible: boolean;
  onClose: () => void;
  onSelectGoal: (goal: WorkoutGoal) => void;
  isOutdoor?: boolean;
}

const DISTANCE_OPTIONS = [1.0, 3.0, 5.0, 10.0, 15.0, 21.1, 42.2];
const DURATION_OPTIONS = [
  { secs: 15 * 60, label: '15 dk' },
  { secs: 20 * 60, label: '20 dk' },
  { secs: 30 * 60, label: '30 dk' },
  { secs: 45 * 60, label: '45 dk' },
  { secs: 60 * 60, label: '1 sa' },
  { secs: 90 * 60, label: '1.5 sa' },
  { secs: 120 * 60, label: '2 sa' },
];

export const WorkoutGoalSheet: React.FC<WorkoutGoalSheetProps> = ({
  visible,
  onClose,
  onSelectGoal,
  isOutdoor = false,
}) => {
  const [tab, setTab] = useState<'distance' | 'duration'>(isOutdoor ? 'distance' : 'duration');
  const [selectedDistance, setSelectedDistance] = useState<number | null>(5.0);
  const [selectedDuration, setSelectedDuration] = useState<number | null>(30 * 60);

  // Sync tab when isOutdoor changes
  React.useEffect(() => {
    if (!isOutdoor) setTab('duration');
    else setTab('distance');
  }, [isOutdoor]);

  const handleConfirm = () => {
    if (isOutdoor && tab === 'distance' && selectedDistance) {
      onSelectGoal({
        type: 'distance',
        distanceKm: selectedDistance,
        label: `${selectedDistance} km hedef`,
      });
    } else if (tab === 'duration' && selectedDuration) {
      const mins = selectedDuration / 60;
      onSelectGoal({
        type: 'duration',
        durationSeconds: selectedDuration,
        label: `Hedef: ${mins} dk`,
      });
    } else {
      onSelectGoal({ type: 'none', label: 'Hedefsiz' });
    }
    onClose();
  };

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <TouchableOpacity style={styles.overlay} activeOpacity={1} onPress={onClose}>
        <TouchableOpacity style={styles.sheetBox} activeOpacity={1}>
          <Text style={styles.sheetTitle}>Hedef Belirle</Text>

          {/* Segmented Tab: Mesafe | Süre (Only show Mesafe if isOutdoor is true) */}
          {isOutdoor ? (
            <View style={styles.tabRow}>
              <TouchableOpacity
                style={[styles.tabBtn, tab === 'distance' && styles.tabBtnActive]}
                onPress={() => setTab('distance')}
              >
                <Text style={[styles.tabBtnText, tab === 'distance' && styles.tabBtnTextActive]}>
                  Mesafe
                </Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.tabBtn, tab === 'duration' && styles.tabBtnActive]}
                onPress={() => setTab('duration')}
              >
                <Text style={[styles.tabBtnText, tab === 'duration' && styles.tabBtnTextActive]}>
                  Süre
                </Text>
              </TouchableOpacity>
            </View>
          ) : (
            <View style={styles.tabRow}>
              <View style={[styles.tabBtn, styles.tabBtnActive]}>
                <Text style={[styles.tabBtnText, styles.tabBtnTextActive]}>Süre</Text>
              </View>
            </View>
          )}

          {/* Options Grid */}
          <View style={styles.grid}>
            {tab === 'distance'
              ? DISTANCE_OPTIONS.map((km) => {
                  const isSelected = selectedDistance === km;
                  return (
                    <TouchableOpacity
                      key={km}
                      style={[styles.gridCell, isSelected && styles.gridCellSelected]}
                      onPress={() => setSelectedDistance(km)}
                    >
                      <Text style={[styles.gridMainText, isSelected && styles.gridTextSelected]}>
                        {km === Math.floor(km) ? km.toString() : km.toFixed(1)}
                      </Text>
                      <Text style={[styles.gridSubText, isSelected && styles.gridTextSelected]}>
                        km
                      </Text>
                    </TouchableOpacity>
                  );
                })
              : DURATION_OPTIONS.map((dur) => {
                  const isSelected = selectedDuration === dur.secs;
                  return (
                    <TouchableOpacity
                      key={dur.secs}
                      style={[styles.gridCell, isSelected && styles.gridCellSelected]}
                      onPress={() => setSelectedDuration(dur.secs)}
                    >
                      <Text style={[styles.gridMainText, isSelected && styles.gridTextSelected]}>
                        {dur.label}
                      </Text>
                    </TouchableOpacity>
                  );
                })}
          </View>

          {/* Actions: Hedefsiz | Tamam */}
          <View style={styles.actionsRow}>
            <TouchableOpacity
              style={styles.freeRunBtn}
              onPress={() => {
                onSelectGoal({ type: 'none', label: 'Hedefsiz' });
                onClose();
              }}
            >
              <Text style={styles.freeRunBtnText}>Hedefsiz</Text>
            </TouchableOpacity>

            <TouchableOpacity style={styles.confirmBtn} onPress={handleConfirm}>
              <Text style={styles.confirmBtnText}>Tamam</Text>
            </TouchableOpacity>
          </View>
        </TouchableOpacity>
      </TouchableOpacity>
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.65)',
    justifyContent: 'flex-end',
  },
  sheetBox: {
    backgroundColor: Colors.cardDark,
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    padding: 24,
    borderTopWidth: 1,
    borderColor: Colors.borderDark,
  },
  sheetTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.textDark,
    textAlign: 'center',
    marginBottom: 16,
  },
  tabRow: {
    flexDirection: 'row',
    backgroundColor: Colors.backgroundDark,
    borderRadius: 12,
    padding: 4,
    marginBottom: 20,
  },
  tabBtn: {
    flex: 1,
    paddingVertical: 10,
    borderRadius: 10,
    alignItems: 'center',
  },
  tabBtnActive: {
    backgroundColor: Colors.primary,
  },
  tabBtnText: {
    fontSize: 14,
    fontWeight: '600',
    color: Colors.textSecondaryDark,
  },
  tabBtnTextActive: {
    color: Colors.allWhite,
  },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
    marginBottom: 20,
  },
  gridCell: {
    width: '31%',
    backgroundColor: Colors.backgroundDark,
    borderRadius: 14,
    paddingVertical: 14,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  gridCellSelected: {
    backgroundColor: Colors.primary,
    borderColor: Colors.primary,
  },
  gridMainText: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.textDark,
  },
  gridSubText: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
    marginTop: 2,
  },
  gridTextSelected: {
    color: Colors.allWhite,
  },
  actionsRow: {
    flexDirection: 'row',
    gap: 12,
  },
  freeRunBtn: {
    flex: 1,
    backgroundColor: Colors.backgroundDark,
    borderRadius: 14,
    paddingVertical: 14,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  freeRunBtnText: {
    color: Colors.textDark,
    fontWeight: '600',
    fontSize: 15,
  },
  confirmBtn: {
    flex: 1,
    backgroundColor: Colors.primary,
    borderRadius: 14,
    paddingVertical: 14,
    alignItems: 'center',
  },
  confirmBtnText: {
    color: Colors.allWhite,
    fontWeight: '700',
    fontSize: 15,
  },
});
