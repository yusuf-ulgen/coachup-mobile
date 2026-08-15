import React, { useState, useEffect } from 'react';
import {
  Modal,
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { LinearGradient } from 'expo-linear-gradient';
import { Colors } from '../theme/colors';
import { TrainingService, TrainingProgram } from '../services/trainingService';

interface ProgramPreviewModalProps {
  visible: boolean;
  program: TrainingProgram | null;
  onClose: () => void;
  onStartWorkout: (selectedDay: number) => void;
  isStarting?: boolean;
}

export const ProgramPreviewModal: React.FC<ProgramPreviewModalProps> = ({
  visible,
  program,
  onClose,
  onStartWorkout,
  isStarting = false,
}) => {
  const [exercises, setExercises] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedDay, setSelectedDay] = useState(1);

  useEffect(() => {
    if (visible && program?.id) {
      setLoading(true);
      TrainingService.fetchProgramExercises(program.id)
        .then((data) => {
          setExercises(data || []);
          // Calculate available days
          const days = Array.from(
            new Set(
              (data || [])
                .map((ex: any) => Math.floor((ex.order_index || 0) / 100))
                .filter((d: number) => d > 0)
            )
          ).sort((a: any, b: any) => a - b);

          if (days.length > 0) {
            setSelectedDay(days[0]);
          } else {
            setSelectedDay(1);
          }
        })
        .finally(() => setLoading(false));
    }
  }, [visible, program?.id]);

  if (!program) return null;

  // Calculate available days array
  const availableDays = Array.from(
    new Set(
      exercises
        .map((ex: any) => Math.floor((ex.order_index || 0) / 100))
        .filter((d: number) => d > 0)
    )
  ).sort((a: any, b: any) => a - b);

  // Filter exercises for selected day
  const dayExercises = availableDays.length > 1
    ? exercises.filter((ex: any) => Math.floor((ex.order_index || 0) / 100) === selectedDay)
    : exercises;

  // Fallback program_text / exercise_names if no program_exercises rows exist
  const fallbackExerciseNames = program.exercise_names || [];

  return (
    <Modal visible={visible} animationType="slide" transparent={false}>
      <SafeAreaView style={styles.container}>
        {/* Top Bar */}
        <View style={styles.topHeader}>
          <TouchableOpacity onPress={onClose} style={styles.cancelButton} activeOpacity={0.7}>
            <Text style={styles.cancelText}>İptal</Text>
          </TouchableOpacity>
          <View style={styles.headerTitleContainer}>
            <Text style={styles.headerTitle} numberOfLines={2}>
              {program.name}
            </Text>
          </View>
          <View style={styles.headerRightSpacer} />
        </View>

        {/* Section Header */}
        <View style={styles.titleSection}>
          <Text style={styles.mainTitle}>Program hareketleri</Text>
          <Text style={styles.subtitle}>Başlamadan önce içeriği inceleyin</Text>
        </View>

        {/* Multi-Day Selector Tabs */}
        {availableDays.length > 1 && (
          <View style={styles.daysRow}>
            {availableDays.map((day: any) => {
              const isSelected = day === selectedDay;
              return (
                <TouchableOpacity
                  key={day}
                  style={[styles.dayTab, isSelected && styles.dayTabSelected]}
                  onPress={() => setSelectedDay(day)}
                  activeOpacity={0.8}
                >
                  <Text style={[styles.dayTabText, isSelected && styles.dayTabTextSelected]}>
                    {day}. Gün
                  </Text>
                </TouchableOpacity>
              );
            })}
          </View>
        )}

        {/* Exercise List */}
        <ScrollView contentContainerStyle={styles.listContent} showsVerticalScrollIndicator={false}>
          {loading ? (
            <ActivityIndicator size="large" color={Colors.primary} style={{ marginTop: 40 }} />
          ) : dayExercises.length > 0 ? (
            dayExercises.map((ex: any, index: number) => {
              const exName =
                ex.exercises?.name ||
                ex.exercise?.name ||
                ex.name ||
                `Egzersiz ${index + 1}`;
              const setRepDetail = `${ex.sets || 3} set × ${ex.reps || 10} tekrar${
                ex.weight_suggestion ? ` · ${ex.weight_suggestion} kg` : ''
              }`;

              return (
                <View key={ex.id || index} style={styles.exerciseCard}>
                  <Text style={styles.exerciseNumber}>{index + 1}.</Text>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.exerciseName}>{exName}</Text>
                    <Text style={styles.exerciseDetail}>{setRepDetail}</Text>
                  </View>
                </View>
              );
            })
          ) : fallbackExerciseNames.length > 0 ? (
            fallbackExerciseNames.map((name: string, index: number) => (
              <View key={index} style={styles.exerciseCard}>
                <Text style={styles.exerciseNumber}>{index + 1}.</Text>
                <View style={{ flex: 1 }}>
                  <Text style={styles.exerciseName}>{name}</Text>
                </View>
              </View>
            ))
          ) : (
            <Text style={styles.emptyText}>Bu program için hareket listesi bulunamadı.</Text>
          )}
        </ScrollView>

        {/* Bottom Action Button */}
        <View style={styles.footer}>
          <TouchableOpacity
            style={styles.startButtonTouch}
            onPress={() => onStartWorkout(selectedDay)}
            disabled={isStarting}
            activeOpacity={0.85}
          >
            <LinearGradient
              colors={['#FF6B4A', '#FF3D00']}
              start={{ x: 0, y: 0 }}
              end={{ x: 1, y: 1 }}
              style={styles.startButtonGradient}
            >
              {isStarting ? (
                <ActivityIndicator color={Colors.allWhite} />
              ) : (
                <Text style={styles.startButtonText}>Antrenmana Başla</Text>
              )}
            </LinearGradient>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    </Modal>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.backgroundDark,
  },
  topHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(255,255,255,0.06)',
    minHeight: 52,
  },
  cancelButton: {
    paddingVertical: 6,
    paddingHorizontal: 6,
    minWidth: 54,
  },
  cancelText: {
    fontSize: 15,
    color: Colors.textSecondaryDark,
    fontWeight: '500',
  },
  headerTitleContainer: {
    flex: 1,
    paddingHorizontal: 8,
    justifyContent: 'center',
    alignItems: 'center',
  },
  headerTitle: {
    fontSize: 15,
    fontWeight: '600',
    color: Colors.textDark,
    textAlign: 'center',
    lineHeight: 20,
  },
  headerRightSpacer: {
    minWidth: 54,
  },
  titleSection: {
    paddingHorizontal: 20,
    paddingTop: 16,
    paddingBottom: 12,
  },
  mainTitle: {
    fontSize: 24,
    fontWeight: '700',
    color: Colors.textDark,
    letterSpacing: -0.3,
  },
  subtitle: {
    fontSize: 14,
    color: Colors.textSecondaryDark,
    marginTop: 4,
  },
  daysRow: {
    flexDirection: 'row',
    paddingHorizontal: 20,
    gap: 10,
    marginBottom: 12,
  },
  dayTab: {
    flex: 1,
    height: 42,
    borderRadius: 21,
    backgroundColor: Colors.cardDark,
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  dayTabSelected: {
    backgroundColor: Colors.primary,
    borderColor: Colors.primary,
  },
  dayTabText: {
    fontSize: 14,
    fontWeight: '600',
    color: Colors.textSecondaryDark,
  },
  dayTabTextSelected: {
    color: Colors.allWhite,
  },
  listContent: {
    paddingHorizontal: 20,
    paddingBottom: 24,
    gap: 10,
  },
  exerciseCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    paddingHorizontal: 16,
    paddingVertical: 14,
    gap: 14,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.05)',
  },
  exerciseNumber: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.primary,
    width: 24,
  },
  exerciseName: {
    fontSize: 16,
    fontWeight: '600',
    color: Colors.textDark,
  },
  exerciseDetail: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    marginTop: 2,
  },
  emptyText: {
    textAlign: 'center',
    color: Colors.textSecondaryDark,
    marginTop: 40,
    fontSize: 14,
  },
  footer: {
    paddingHorizontal: 20,
    paddingVertical: 16,
    borderTopWidth: 1,
    borderTopColor: 'rgba(255,255,255,0.06)',
    backgroundColor: Colors.backgroundDark,
  },
  startButtonTouch: {
    borderRadius: 14,
    overflow: 'hidden',
    elevation: 8,
    shadowColor: Colors.primary,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.45,
    shadowRadius: 10,
  },
  startButtonGradient: {
    height: 52,
    justifyContent: 'center',
    alignItems: 'center',
  },
  startButtonText: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.allWhite,
    letterSpacing: 0.2,
  },
});
