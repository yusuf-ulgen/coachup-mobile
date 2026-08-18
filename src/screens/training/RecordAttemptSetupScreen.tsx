import React, { useState, useEffect, useCallback } from 'react';
import { useFocusEffect } from '@react-navigation/native';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  ActivityIndicator,
} from 'react-native';
import {
  ArrowLeft,
  ChevronRight,
  Dumbbell,
  User,
  Flame,
  Activity,
  Award,
  Plus,
} from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { useAuth } from '../../context/AuthContext';
import { supabase } from '../../services/supabaseClient';
import { RecordAttemptService } from '../../services/recordAttemptService';
import { feedback } from '../../services/feedbackService';
import {
  RECORD_ATTEMPT_CATEGORIES,
  RecordCategory,
  RecordExercise,
  RecordMeasureType,
  measureLabel,
} from '../../models/recordAttemptCategories';
import { formatPRDisplayValue } from '../../utils/recordFormatters';

import { useSafeAreaInsets } from 'react-native-safe-area-context';

interface RecordAttemptSetupScreenProps {
  navigation?: any;
}

type SetupStep = 'category' | 'exercise' | 'detail';

export const RecordAttemptSetupScreen: React.FC<RecordAttemptSetupScreenProps> = ({ navigation }) => {
  const insets = useSafeAreaInsets();
  const { session } = useAuth();
  const userId = session?.user?.id;

  const [step, setStep] = useState<SetupStep>('category');
  const [selectedCategory, setSelectedCategory] = useState<RecordCategory | null>(null);
  const [selectedExercise, setSelectedExercise] = useState<RecordExercise | null>(null);

  // Target Config
  const [targetValue, setTargetValue] = useState<number>(100);
  const [targetReps, setTargetReps] = useState<number>(1);
  const [includeWarmup, setIncludeWarmup] = useState<boolean>(false);

  // DB History states
  const [historyLoading, setHistoryLoading] = useState<boolean>(false);
  const [pastPR, setPastPR] = useState<any | null>(null);
  const [pastAttempts, setPastAttempts] = useState<any[]>([]);
  const [setsByAttempt, setSetsByAttempt] = useState<Record<string, any>>({});
  const [isStarting, setIsStarting] = useState<boolean>(false);
  const unresolvedAttemptIdRef = React.useRef<string | null>(null);

  const applyDefaults = (ex: RecordExercise) => {
    setSelectedExercise(ex);
    setIncludeWarmup(false);
    setTargetReps(ex.defaultReps || 1);
    setTargetValue(ex.defaultTarget || 100);
    setStep('detail');
  };

  // Fetch history for selected exercise from DB (READ-ONLY: Does NOT create exercise row)
  const loadHistoryForExercise = useCallback(async (catalogEx: RecordExercise) => {
    if (!userId) return;
    setHistoryLoading(true);
    try {
      // 1. Read-only lookup in DB
      const dbEx = await RecordAttemptService.findExistingExercise(
        catalogEx,
        selectedCategory?.id
      );

      if (dbEx?.id) {
        const resultType = RecordAttemptService.getExerciseResultType(
          catalogEx.id,
          selectedCategory?.id,
          catalogEx.name
        );

        // 2. Fetch Personal Records & find best historical PR
        const prList = await RecordAttemptService.fetchPersonalRecordsForExercise(
          userId,
          dbEx.id,
          50
        );
        const best = RecordAttemptService.findBestHistoricalRecord(prList, resultType);
        setPastPR(best);

        // 3. Fetch past completed attempts
        const attempts = await RecordAttemptService.fetchAttemptsForExercise(
          userId,
          dbEx.id,
          10
        );
        setPastAttempts(attempts);

        // 4. Fetch sets for attempts
        if (attempts.length > 0) {
          const attemptIds = attempts.map((a: any) => a.id);
          const setsMap = await RecordAttemptService.fetchMainSetsForAttempts(attemptIds);
          setSetsByAttempt(setsMap);
        } else {
          setSetsByAttempt({});
        }
      } else {
        setPastPR(null);
        setPastAttempts([]);
        setSetsByAttempt({});
      }
    } catch (e) {
      console.error('[RecordAttemptSetupScreen] History load error:', e);
      setPastPR(null);
      setPastAttempts([]);
      setSetsByAttempt({});
    } finally {
      setHistoryLoading(false);
    }
  }, [userId, selectedCategory]);

  useFocusEffect(
    useCallback(() => {
      if (selectedExercise && userId && step === 'detail') {
        loadHistoryForExercise(selectedExercise);
      }
    }, [selectedExercise, userId, step, loadHistoryForExercise])
  );

  const handleBack = () => {
    if (step === 'detail') {
      setSelectedExercise(null);
      setStep('exercise');
    } else if (step === 'exercise') {
      setSelectedCategory(null);
      setStep('category');
    } else {
      navigation?.goBack();
    }
  };

  const getCategoryIcon = (catId: string) => {
    switch (catId) {
      case 'strength':
        return <Dumbbell size={22} color={Colors.primary} />;
      case 'bodyweight':
        return <User size={22} color={Colors.primary} />;
      case 'running':
        return <Flame size={22} color={Colors.primary} />;
      case 'cardio':
        return <Activity size={22} color={Colors.primary} />;
      case 'benchmark':
        return <Award size={22} color={Colors.primary} />;
      default:
        return <Dumbbell size={22} color={Colors.primary} />;
    }
  };

  const adjustValue = (delta: number) => {
    setTargetValue((prev) => Math.max(0, prev + delta));
  };

  const adjustReps = (delta: number) => {
    setTargetReps((prev) => Math.max(1, prev + delta));
  };

  const calculatePlan = () => {
    if (!selectedExercise) return [];
    if (selectedExercise.measureType !== RecordMeasureType.WEIGHT) {
      return [{ weight: targetValue, reps: targetReps, type: 'main' as const }];
    }
    if (includeWarmup && targetValue >= 20) {
      return [
        { weight: Math.round((targetValue * 0.4) / 2.5) * 2.5, reps: 5, type: 'warmup' as const },
        { weight: Math.round((targetValue * 0.6) / 2.5) * 2.5, reps: 3, type: 'warmup' as const },
        { weight: Math.round((targetValue * 0.8) / 2.5) * 2.5, reps: 2, type: 'warmup' as const },
        { weight: Math.round((targetValue * 0.9) / 2.5) * 2.5, reps: 1, type: 'warmup' as const },
        { weight: targetValue, reps: targetReps, type: 'main' as const },
      ];
    }
    return [{ weight: targetValue, reps: targetReps, type: 'main' as const }];
  };

  const handleStartAttempt = async () => {
    if (!selectedExercise || !userId || isStarting) return;
    setIsStarting(true);
    let createdAttemptId: string | null = null;

    try {
      // 0. Retry cleanup guard: if a previous attempt in this screen failed during set initialization, finalize it first
      if (unresolvedAttemptIdRef.current) {
        try {
          await RecordAttemptService.abandonAttempt(unresolvedAttemptIdRef.current);
          unresolvedAttemptIdRef.current = null;
        } catch (cleanupErr) {
          console.warn('[RecordAttemptSetupScreen] Unresolved attempt cleanup warning:', cleanupErr);
          throw new Error('Önceki deneme temizlenemedi. Lütfen internet bağlantınızı kontrol edip tekrar deneyin.');
        }
      }

      // 1. Resolve or create exercise in DB (WRITE operation: allowed on start)
      const dbEx = await RecordAttemptService.resolveOrCreateExercise(
        selectedExercise,
        selectedCategory?.id
      );

      const isWeight = selectedExercise.measureType === RecordMeasureType.WEIGHT;

      if (isWeight) {
        // Weight mode: Warmups + Main set
        const plan = calculatePlan();
        const attempt = await RecordAttemptService.startAttempt(
          userId,
          dbEx.id,
          targetValue,
          targetReps
        );
        createdAttemptId = attempt.id;
        unresolvedAttemptIdRef.current = attempt.id;

        const plannedSets = await RecordAttemptService.insertPlannedSets(
          attempt.id,
          userId,
          plan
        );
        unresolvedAttemptIdRef.current = null;

        navigation?.navigate('RecordAttemptSession', {
          attempt,
          exercise: dbEx,
          catalogExercise: selectedExercise,
          recordType: selectedExercise.measureType,
          targetValue,
          targetReps,
          plan: plannedSets,
        });
      } else {
        // Timed / Bodyweight / Benchmark / Running / Cardio modes
        const attempt = await RecordAttemptService.startAttempt(
          userId,
          dbEx.id,
          targetValue,
          targetReps
        );
        createdAttemptId = attempt.id;
        unresolvedAttemptIdRef.current = attempt.id;

        const plannedSets = await RecordAttemptService.insertPlannedSets(
          attempt.id,
          userId,
          [{ weight: targetValue, reps: targetReps, type: 'main' }]
        );
        unresolvedAttemptIdRef.current = null;

        navigation?.navigate('RecordAttemptTimedModes', {
          attempt,
          exercise: dbEx,
          catalogExercise: selectedExercise,
          measureType: selectedExercise.measureType,
          catalogId: selectedExercise.id,
          categoryId: selectedCategory?.id,
          targetValue,
          targetReps,
          plannedSet: plannedSets[0],
        });
      }
    } catch (e: any) {
      console.error('[RecordAttemptSetupScreen] Failed to start attempt:', e);
      if (createdAttemptId) {
        try {
          await RecordAttemptService.abandonAttempt(createdAttemptId);
          unresolvedAttemptIdRef.current = null;
        } catch (abandonErr) {
          console.warn('[RecordAttemptSetupScreen] Compensating abandon error:', abandonErr);
          unresolvedAttemptIdRef.current = createdAttemptId;
        }
      }
      feedback.error({
        title: 'Başlatılamadı',
        message: e,
        fallbackMessage: 'Rekor denemesi başlatılamadı. Lütfen tekrar deneyin.',
      });
    } finally {
      setIsStarting(false);
    }
  };

  const formatHistoryAttemptValue = (attempt: any, measureType: RecordMeasureType) => {
    const mainSet = setsByAttempt[attempt.id];
    const w = mainSet?.actual_weight ?? attempt.target_weight ?? 0;
    const r = mainSet?.actual_reps ?? attempt.target_reps ?? 1;

    switch (measureType) {
      case RecordMeasureType.WEIGHT:
        return `${w} kg × ${r}`;
      case RecordMeasureType.REPS:
        return `${r} tekrar`;
      case RecordMeasureType.TIME:
      case RecordMeasureType.DISTANCE: {
        const notes = (attempt.notes || '').trim();
        const timeMatch = notes.match(/(\d{1,2}:\d{2})/);
        if (timeMatch) return timeMatch[0];
        const secMatch = notes.match(/(\d+)\s*sn/);
        if (secMatch) {
          const sec = parseInt(secMatch[1], 10);
          const m = Math.floor(sec / 60);
          const s = sec % 60;
          return `${m}:${s.toString().padStart(2, '0')}`;
        }
        return `${Math.round(w)} sn`;
      }
      case RecordMeasureType.CALORIES: {
        const notes = (attempt.notes || '').trim();
        const timeMatch = notes.match(/(\d{1,2}:\d{2})/);
        if (timeMatch) return `${Math.round(w)} cal (${timeMatch[0]})`;
        return `${Math.round(w)} cal`;
      }
      default:
        return `${w} × ${r}`;
    }
  };

  return (
    <View style={styles.container}>
      {/* ── Header ────────────────────────────────────────────────────────── */}
      <View style={[styles.headerRow, { paddingTop: Math.max(16, insets.top + 8) }]}>
        <TouchableOpacity onPress={handleBack} style={styles.backBtn} activeOpacity={0.8}>
          <ArrowLeft size={20} color={Colors.textDark} />
        </TouchableOpacity>
        <View style={{ flex: 1 }}>
          <Text style={styles.headerTitle}>
            {step === 'category'
              ? 'Rekor Denemesi'
              : step === 'exercise'
              ? selectedCategory?.name || 'Hareket Seç'
              : selectedExercise?.name || 'Hazırlık'}
          </Text>
          <Text style={styles.headerSubtitle}>
            {step === 'category'
              ? 'Kategori seçin'
              : step === 'exercise'
              ? `${selectedCategory?.exerciseCount || 0} hareket`
              : measureLabel(selectedExercise?.measureType || RecordMeasureType.WEIGHT, selectedExercise?.id)}
          </Text>
        </View>
      </View>

      <ScrollView contentContainerStyle={[styles.scrollContent, { paddingBottom: 24 + insets.bottom }]} showsVerticalScrollIndicator={false}>
        {/* ── Step 1: Category Selection ──────────────────────────────────── */}
        {step === 'category' && (
          <View style={styles.cardsList}>
            {RECORD_ATTEMPT_CATEGORIES.map((cat) => (
              <TouchableOpacity
                key={cat.id}
                style={styles.categoryCard}
                onPress={() => {
                  setSelectedCategory(cat);
                  setStep('exercise');
                }}
                activeOpacity={0.8}
              >
                <View style={styles.categoryIconBadge}>{getCategoryIcon(cat.id)}</View>
                <View style={{ flex: 1 }}>
                  <Text style={styles.categoryTitle}>{cat.name}</Text>
                  <Text style={styles.categorySubtitle}>{cat.exerciseCount} hareket</Text>
                </View>
                <ChevronRight size={20} color={Colors.textSecondaryDark} />
              </TouchableOpacity>
            ))}
          </View>
        )}

        {/* ── Step 2: Exercise Selection ──────────────────────────────────── */}
        {step === 'exercise' && selectedCategory && (
          <View style={styles.cardsList}>
            {selectedCategory.exercises.map((ex) => (
              <TouchableOpacity
                key={ex.id}
                style={styles.exerciseCard}
                onPress={() => applyDefaults(ex)}
                activeOpacity={0.8}
              >
                <View style={{ flex: 1 }}>
                  <Text style={styles.exerciseTitle}>{ex.name}</Text>
                  <Text style={styles.exerciseSubtitle}>{measureLabel(ex.measureType, ex.id)}</Text>
                </View>
                <ChevronRight size={18} color={Colors.textSecondaryDark} />
              </TouchableOpacity>
            ))}
          </View>
        )}

        {/* ── Step 3: Detail & Setup ──────────────────────────────────────── */}
        {step === 'detail' && selectedExercise && (
          <View style={{ gap: 16 }}>
            {/* Selected Exercise Banner */}
            <View style={styles.selectedBanner}>
              <Dumbbell size={22} color={Colors.primary} />
              <View style={{ flex: 1 }}>
                <Text style={styles.bannerTitle}>{selectedExercise.name}</Text>
                <Text style={styles.bannerSub}>{selectedCategory?.name || ''}</Text>
              </View>
              <TouchableOpacity
                onPress={() => {
                  setSelectedExercise(null);
                  setStep('exercise');
                }}
              >
                <Text style={styles.changeBtnText}>Değiştir</Text>
              </TouchableOpacity>
            </View>

            {/* History Section */}
            <View style={styles.historyBox}>
              <Text style={styles.sectionHeaderTitle}>Geçmiş</Text>
              {historyLoading ? (
                <ActivityIndicator size="small" color={Colors.primary} style={{ marginVertical: 12 }} />
              ) : pastPR || pastAttempts.length > 0 ? (
                <View style={{ gap: 10 }}>
                  {pastPR && (
                    <View style={styles.prBox}>
                      <View style={{ flex: 1 }}>
                        <Text style={styles.prBoxLabel}>Kişisel Rekor</Text>
                        <Text style={styles.prBoxVal}>
                          {formatPRDisplayValue({ ...pastPR, exercise: selectedExercise })}
                        </Text>
                      </View>
                      <Text style={styles.prBoxDate}>{pastPR.record_date?.slice(0, 10)}</Text>
                    </View>
                  )}

                  {pastAttempts.map((attempt) => {
                    const isSuccess = attempt.status === 'completed' && attempt.success;
                    const isAbandoned = attempt.status === 'abandoned';
                    const statusText = isSuccess ? 'Başarılı' : isAbandoned ? 'Bırakıldı' : 'Başarısız';
                    const badgeBg = isSuccess
                      ? 'rgba(76,175,80,0.15)'
                      : isAbandoned
                      ? 'rgba(158,158,158,0.15)'
                      : 'rgba(244,67,54,0.15)';
                    const badgeColor = isSuccess ? '#4CAF50' : isAbandoned ? '#9E9E9E' : '#F44336';
                    const dateStr = (attempt.completed_at || attempt.created_at || '').slice(0, 10);
                    return (
                      <View key={attempt.id} style={styles.attemptHistoryRow}>
                        <View style={{ flex: 1 }}>
                          <Text style={styles.attemptValText}>
                            {formatHistoryAttemptValue(attempt, selectedExercise.measureType)}
                          </Text>
                          <Text style={styles.attemptDateText}>{dateStr}</Text>
                        </View>
                        <View style={[styles.statusBadge, { backgroundColor: badgeBg }]}>
                          <Text style={[styles.statusBadgeText, { color: badgeColor }]}>
                            {statusText}
                          </Text>
                        </View>
                      </View>
                    );
                  })}
                </View>
              ) : (
                <Text style={styles.noHistoryText}>Bu harekette henüz kayıt yok. İlk rekoru sen kır!</Text>
              )}
            </View>

            {/* Target Setup Cards */}
            {/* 1. Target Value / Weight / Time / Distance / Calories */}
            {selectedExercise.measureType === RecordMeasureType.WEIGHT && (
              <View style={styles.targetCard}>
                <Text style={styles.targetCardLabel}>HEDEF AĞIRLIK (KG)</Text>
                <View style={styles.targetControlsRow}>
                  <TouchableOpacity
                    style={styles.adjustBtn}
                    onPress={() => adjustValue(-2.5)}
                    activeOpacity={0.8}
                  >
                    <Text style={styles.adjustBtnMinus}>−</Text>
                  </TouchableOpacity>
                  <Text style={styles.targetValText}>{targetValue} kg</Text>
                  <TouchableOpacity
                    style={[styles.adjustBtn, styles.adjustBtnPlus]}
                    onPress={() => adjustValue(2.5)}
                    activeOpacity={0.8}
                  >
                    <Plus size={20} color={Colors.allWhite} />
                  </TouchableOpacity>
                </View>
              </View>
            )}

            {selectedExercise.measureType === RecordMeasureType.TIME && (
              <View style={styles.targetCard}>
                <Text style={styles.targetCardLabel}>HEDEF SÜRE (SN)</Text>
                <View style={styles.targetControlsRow}>
                  <TouchableOpacity style={styles.adjustBtn} onPress={() => adjustValue(-10)} activeOpacity={0.8}>
                    <Text style={styles.adjustBtnMinus}>−</Text>
                  </TouchableOpacity>
                  <Text style={styles.targetValText}>{Math.round(targetValue)} sn</Text>
                  <TouchableOpacity
                    style={[styles.adjustBtn, styles.adjustBtnPlus]}
                    onPress={() => adjustValue(10)}
                    activeOpacity={0.8}
                  >
                    <Plus size={20} color={Colors.allWhite} />
                  </TouchableOpacity>
                </View>
              </View>
            )}

            {selectedExercise.measureType === RecordMeasureType.CALORIES && (
              <View style={styles.targetCard}>
                <Text style={styles.targetCardLabel}>HEDEF KALORİ</Text>
                <View style={styles.targetControlsRow}>
                  <TouchableOpacity style={styles.adjustBtn} onPress={() => adjustValue(-5)} activeOpacity={0.8}>
                    <Text style={styles.adjustBtnMinus}>−</Text>
                  </TouchableOpacity>
                  <Text style={styles.targetValText}>{Math.round(targetValue)} cal</Text>
                  <TouchableOpacity
                    style={[styles.adjustBtn, styles.adjustBtnPlus]}
                    onPress={() => adjustValue(5)}
                    activeOpacity={0.8}
                  >
                    <Plus size={20} color={Colors.allWhite} />
                  </TouchableOpacity>
                </View>
              </View>
            )}

            {selectedExercise.measureType === RecordMeasureType.DISTANCE && (
              <View style={styles.targetCard}>
                <Text style={styles.targetCardLabel}>HEDEF MESAFE (KM)</Text>
                <View style={styles.targetControlsRow}>
                  <TouchableOpacity style={styles.adjustBtn} onPress={() => adjustValue(-0.5)} activeOpacity={0.8}>
                    <Text style={styles.adjustBtnMinus}>−</Text>
                  </TouchableOpacity>
                  <Text style={styles.targetValText}>{targetValue} km</Text>
                  <TouchableOpacity
                    style={[styles.adjustBtn, styles.adjustBtnPlus]}
                    onPress={() => adjustValue(0.5)}
                    activeOpacity={0.8}
                  >
                    <Plus size={20} color={Colors.allWhite} />
                  </TouchableOpacity>
                </View>
              </View>
            )}

            {/* 2. Target Reps setup card — ONLY for WEIGHT and REPS types (Güç ve Vücut Ağırlığı) */}
            {(selectedExercise.measureType === RecordMeasureType.WEIGHT ||
              selectedExercise.measureType === RecordMeasureType.REPS) && (
              <View style={styles.targetCard}>
                <Text style={styles.targetCardLabel}>HEDEF TEKRAR</Text>
                <View style={styles.targetControlsRow}>
                  <TouchableOpacity style={styles.adjustBtn} onPress={() => adjustReps(-1)} activeOpacity={0.8}>
                    <Text style={styles.adjustBtnMinus}>−</Text>
                  </TouchableOpacity>
                  <Text style={styles.targetValText}>{targetReps} tekrar</Text>
                  <TouchableOpacity
                    style={[styles.adjustBtn, styles.adjustBtnPlus]}
                    onPress={() => adjustReps(1)}
                    activeOpacity={0.8}
                  >
                    <Plus size={20} color={Colors.allWhite} />
                  </TouchableOpacity>
                </View>
              </View>
            )}

            {/* Warmup Toggle (Only for WEIGHT type) */}
            {selectedExercise.measureType === RecordMeasureType.WEIGHT && (
              <View style={styles.warmupCard}>
                <Text style={styles.targetCardLabel}>ISINMA</Text>
                <View style={styles.warmupToggleRow}>
                  <TouchableOpacity
                    style={[styles.warmupOptionChip, includeWarmup && styles.warmupOptionChipActive]}
                    onPress={() => setIncludeWarmup(true)}
                    activeOpacity={0.8}
                  >
                    <Text style={[styles.warmupOptionText, includeWarmup && styles.warmupOptionTextActive]}>
                      Isınma setleriyle
                    </Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={[styles.warmupOptionChip, !includeWarmup && styles.warmupOptionChipActive]}
                    onPress={() => setIncludeWarmup(false)}
                    activeOpacity={0.8}
                  >
                    <Text style={[styles.warmupOptionText, !includeWarmup && styles.warmupOptionTextActive]}>
                      Direkt rekor
                    </Text>
                  </TouchableOpacity>
                </View>
              </View>
            )}

            {/* Start Button */}
            <TouchableOpacity
              style={[styles.startBtn, isStarting && { opacity: 0.7 }]}
              onPress={handleStartAttempt}
              activeOpacity={0.8}
              disabled={isStarting}
            >
              {isStarting ? (
                <ActivityIndicator size="small" color={Colors.allWhite} />
              ) : (
                <>
                  <Flame size={20} color={Colors.allWhite} />
                  <Text style={styles.startBtnText}>Rekor Denemesine Başla</Text>
                </>
              )}
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
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingTop: 50,
    paddingBottom: 16,
    gap: 14,
  },
  backBtn: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: Colors.cardDark,
    justifyContent: 'center',
    alignItems: 'center',
  },
  headerTitle: {
    fontSize: 22,
    fontWeight: '700',
    color: Colors.textDark,
  },
  headerSubtitle: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    marginTop: 2,
  },
  scrollContent: {
    paddingHorizontal: 20,
    paddingBottom: 40,
  },
  cardsList: {
    gap: 12,
    marginTop: 8,
  },
  categoryCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 16,
    gap: 14,
    borderWidth: 1,
    borderColor: 'rgba(250,249,248,0.06)',
  },
  categoryIconBadge: {
    width: 44,
    height: 44,
    borderRadius: 12,
    backgroundColor: 'rgba(255,96,71,0.12)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  categoryTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: Colors.textDark,
  },
  categorySubtitle: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
    marginTop: 2,
  },
  exerciseCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.cardDark,
    borderRadius: 14,
    paddingHorizontal: 16,
    paddingVertical: 14,
    borderWidth: 1,
    borderColor: 'rgba(250,249,248,0.06)',
  },
  exerciseTitle: {
    fontSize: 15,
    fontWeight: '600',
    color: Colors.textDark,
  },
  exerciseSubtitle: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
    marginTop: 2,
  },
  selectedBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(255,96,71,0.1)',
    borderRadius: 16,
    padding: 16,
    gap: 12,
    borderWidth: 1,
    borderColor: 'rgba(255,96,71,0.3)',
  },
  bannerTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.textDark,
  },
  bannerSub: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
    marginTop: 2,
  },
  changeBtnText: {
    fontSize: 13,
    fontWeight: '700',
    color: Colors.primary,
  },
  historyBox: {
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 16,
    borderWidth: 1,
    borderColor: 'rgba(250,249,248,0.06)',
  },
  sectionHeaderTitle: {
    fontSize: 15,
    fontWeight: '600',
    color: Colors.textDark,
    marginBottom: 10,
  },
  prBox: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: 'rgba(255,96,71,0.1)',
    borderRadius: 12,
    padding: 12,
    borderWidth: 1,
    borderColor: 'rgba(255,96,71,0.2)',
  },
  prBoxLabel: {
    fontSize: 11,
    color: Colors.primary,
    fontWeight: '700',
  },
  prBoxVal: {
    fontSize: 17,
    fontWeight: '800',
    color: Colors.textDark,
    marginTop: 2,
  },
  prBoxDate: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
  },
  attemptHistoryRow: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.backgroundDark,
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  attemptValText: {
    fontSize: 14,
    fontWeight: '600',
    color: Colors.textDark,
  },
  attemptDateText: {
    fontSize: 11,
    color: Colors.textSecondaryDark,
    marginTop: 2,
  },
  statusBadge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 20,
  },
  statusBadgeText: {
    fontSize: 11,
    fontWeight: '600',
  },
  noHistoryText: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
  },
  targetCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 16,
    borderWidth: 1,
    borderColor: 'rgba(250,249,248,0.06)',
    gap: 12,
  },
  targetCardLabel: {
    fontSize: 11,
    fontWeight: '700',
    color: Colors.textSecondaryDark,
    letterSpacing: 0.5,
  },
  targetControlsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  adjustBtn: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: Colors.backgroundDark,
    justifyContent: 'center',
    alignItems: 'center',
  },
  adjustBtnPlus: {
    backgroundColor: Colors.primary,
  },
  adjustBtnMinus: {
    fontSize: 24,
    fontWeight: '700',
    color: Colors.textDark,
  },
  targetValText: {
    fontSize: 24,
    fontWeight: '800',
    color: Colors.textDark,
  },
  warmupCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 16,
    borderWidth: 1,
    borderColor: 'rgba(250,249,248,0.06)',
    gap: 12,
  },
  warmupToggleRow: {
    flexDirection: 'row',
    gap: 10,
  },
  warmupOptionChip: {
    flex: 1,
    paddingVertical: 12,
    borderRadius: 12,
    backgroundColor: Colors.backgroundDark,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  warmupOptionChipActive: {
    backgroundColor: 'rgba(255,96,71,0.15)',
    borderColor: Colors.primary,
  },
  warmupOptionText: {
    fontSize: 13,
    fontWeight: '500',
    color: Colors.textSecondaryDark,
  },
  warmupOptionTextActive: {
    color: Colors.primary,
    fontWeight: '700',
  },
  startBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    backgroundColor: Colors.primary,
    borderRadius: 100,
    paddingVertical: 16,
    elevation: 4,
    marginTop: 8,
  },
  startBtnText: {
    color: Colors.allWhite,
    fontSize: 16,
    fontWeight: '700',
  },
});
