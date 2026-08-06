import React, { useState } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  TextInput,
} from 'react-native';
import { ArrowLeft, Flame, Award, ChevronRight, Check } from 'lucide-react-native';
import { Colors } from '../../theme/colors';

interface RecordAttemptSetupScreenProps {
  navigation?: any;
}

const CATEGORIES = [
  { id: 'bodybuilding', name: 'Vücut Geliştirme', icon: '🏋️' },
  { id: 'powerlifting', name: 'Powerlifting', icon: '⚡' },
  { id: 'crossfit', name: 'CrossFit WOD', icon: '🔥' },
  { id: 'endurance', name: 'Kondisyon & Kardiyo', icon: '🏃' },
  { id: 'calisthenics', name: 'Calisthenics', icon: '🤸' },
];

const EXERCISES = [
  { id: 'bench_press', name: 'Bench Press', category: 'bodybuilding', defaultVal: 100 },
  { id: 'squat', name: 'Squat', category: 'bodybuilding', defaultVal: 140 },
  { id: 'deadlift', name: 'Deadlift', category: 'powerlifting', defaultVal: 180 },
  { id: 'overhead_press', name: 'Overhead Press', category: 'bodybuilding', defaultVal: 70 },
  { id: 'pull_up', name: 'Pull Up (Barfiks)', category: 'calisthenics', defaultVal: 20 },
];

export const RecordAttemptSetupScreen: React.FC<RecordAttemptSetupScreenProps> = ({
  navigation,
}) => {
  const [step, setStep] = useState<'category' | 'exercise' | 'config'>('category');
  const [selectedCategory, setSelectedCategory] = useState<any>(null);
  const [selectedExercise, setSelectedExercise] = useState<any>(null);
  const [targetWeight, setTargetWeight] = useState(100);
  const [targetReps, setTargetReps] = useState(1);
  const [includeWarmup, setIncludeWarmup] = useState(true);

  return (
    <View style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity
          onPress={() => {
            if (step === 'config') setStep('exercise');
            else if (step === 'exercise') setStep('category');
            else navigation?.goBack();
          }}
          style={styles.backBtn}
        >
          <ArrowLeft size={22} color={Colors.textDark} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Rekor Denemesi</Text>
      </View>

      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        {step === 'category' && (
          <View>
            <Text style={styles.stepTitle}>Kategori Seçin</Text>
            <Text style={styles.stepSubtitle}>Hangi branşta rekor denemek istiyorsunuz?</Text>

            <View style={styles.list}>
              {CATEGORIES.map((cat) => (
                <TouchableOpacity
                  key={cat.id}
                  style={styles.cardBtn}
                  onPress={() => {
                    setSelectedCategory(cat);
                    setStep('exercise');
                  }}
                  activeOpacity={0.8}
                >
                  <Text style={styles.cardEmoji}>{cat.icon}</Text>
                  <Text style={styles.cardName}>{cat.name}</Text>
                  <ChevronRight size={18} color={Colors.textSecondaryDark} />
                </TouchableOpacity>
              ))}
            </View>
          </View>
        )}

        {step === 'exercise' && (
          <View>
            <Text style={styles.stepTitle}>Hareket Seçin</Text>
            <Text style={styles.stepSubtitle}>
              {selectedCategory?.name} kategorisi için hareket seçin
            </Text>

            <View style={styles.list}>
              {EXERCISES.map((ex) => (
                <TouchableOpacity
                  key={ex.id}
                  style={styles.cardBtn}
                  onPress={() => {
                    setSelectedExercise(ex);
                    setTargetWeight(ex.defaultVal);
                    setStep('config');
                  }}
                  activeOpacity={0.8}
                >
                  <Flame size={20} color={Colors.primary} />
                  <Text style={styles.cardName}>{ex.name}</Text>
                  <ChevronRight size={18} color={Colors.textSecondaryDark} />
                </TouchableOpacity>
              ))}
            </View>
          </View>
        )}

        {step === 'config' && (
          <View>
            <Text style={styles.stepTitle}>{selectedExercise?.name} Rekor Denemesi</Text>
            <Text style={styles.stepSubtitle}>Hedef ağırlık ve tekrar sayınızı girin</Text>

            <View style={styles.configCard}>
              <Text style={styles.inputLabel}>Hedef Ağırlık (kg)</Text>
              <View style={styles.stepperRow}>
                <TouchableOpacity
                  style={styles.stepBtn}
                  onPress={() => setTargetWeight(Math.max(5, targetWeight - 2.5))}
                >
                  <Text style={styles.stepBtnText}>-</Text>
                </TouchableOpacity>
                <Text style={styles.valText}>{targetWeight} kg</Text>
                <TouchableOpacity
                  style={styles.stepBtn}
                  onPress={() => setTargetWeight(targetWeight + 2.5)}
                >
                  <Text style={styles.stepBtnText}>+</Text>
                </TouchableOpacity>
              </View>

              <Text style={[styles.inputLabel, { marginTop: 20 }]}>Hedef Tekrar</Text>
              <View style={styles.stepperRow}>
                <TouchableOpacity
                  style={styles.stepBtn}
                  onPress={() => setTargetReps(Math.max(1, targetReps - 1))}
                >
                  <Text style={styles.stepBtnText}>-</Text>
                </TouchableOpacity>
                <Text style={styles.valText}>{targetReps} Tekrar</Text>
                <TouchableOpacity
                  style={styles.stepBtn}
                  onPress={() => setTargetReps(targetReps + 1)}
                >
                  <Text style={styles.stepBtnText}>+</Text>
                </TouchableOpacity>
              </View>

              <TouchableOpacity
                style={styles.warmupToggle}
                onPress={() => setIncludeWarmup(!includeWarmup)}
              >
                <View style={[styles.checkbox, includeWarmup && styles.checkboxActive]}>
                  {includeWarmup && <Check size={14} color={Colors.allWhite} />}
                </View>
                <Text style={styles.warmupText}>Isınma setlerini otomatik ekle</Text>
              </TouchableOpacity>
            </View>

            <TouchableOpacity
              style={styles.startBtn}
              onPress={() => {
                navigation?.navigate('ActiveWorkout', {
                  title: `${selectedExercise?.name} PR Denemesi`,
                  category: selectedCategory?.name || 'PR',
                });
              }}
              activeOpacity={0.85}
            >
              <Text style={styles.startBtnText}>Rekor Denemesini Başlat</Text>
            </TouchableOpacity>
          </View>
        )}
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.backgroundDark,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingTop: 50,
    paddingBottom: 16,
    backgroundColor: Colors.cardDark,
  },
  backBtn: {
    marginRight: 14,
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: Colors.textDark,
  },
  content: {
    padding: 20,
  },
  stepTitle: {
    fontSize: 22,
    fontWeight: '700',
    color: Colors.textDark,
    marginBottom: 4,
  },
  stepSubtitle: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    marginBottom: 20,
  },
  list: {
    gap: 12,
  },
  cardBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 16,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  cardEmoji: {
    fontSize: 22,
    marginRight: 12,
  },
  cardName: {
    flex: 1,
    fontSize: 16,
    fontWeight: '600',
    color: Colors.textDark,
    marginLeft: 8,
  },
  configCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 20,
    padding: 20,
    borderWidth: 1,
    borderColor: Colors.borderDark,
    marginBottom: 24,
  },
  inputLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: Colors.textDark,
    marginBottom: 10,
  },
  stepperRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: Colors.backgroundDark,
    borderRadius: 14,
    padding: 8,
  },
  stepBtn: {
    width: 44,
    height: 44,
    borderRadius: 10,
    backgroundColor: Colors.cardDark,
    justifyContent: 'center',
    alignItems: 'center',
  },
  stepBtnText: {
    fontSize: 20,
    fontWeight: '700',
    color: Colors.textDark,
  },
  valText: {
    fontSize: 20,
    fontWeight: '800',
    color: Colors.primary,
  },
  warmupToggle: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 20,
    gap: 10,
  },
  checkbox: {
    width: 22,
    height: 22,
    borderRadius: 6,
    borderWidth: 2,
    borderColor: Colors.textSecondaryDark,
    justifyContent: 'center',
    alignItems: 'center',
  },
  checkboxActive: {
    backgroundColor: Colors.primary,
    borderColor: Colors.primary,
  },
  warmupText: {
    fontSize: 14,
    color: Colors.textDark,
    fontWeight: '500',
  },
  startBtn: {
    backgroundColor: Colors.primary,
    borderRadius: 100,
    paddingVertical: 16,
    alignItems: 'center',
  },
  startBtnText: {
    color: Colors.allWhite,
    fontWeight: '700',
    fontSize: 16,
  },
});
