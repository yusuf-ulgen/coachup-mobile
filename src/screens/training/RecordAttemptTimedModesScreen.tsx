import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  TextInput,
  ActivityIndicator,
} from 'react-native';
import { SmoothModal } from '../../components/motion/SmoothModal';
import { ArrowLeft, X, Play, Square, Flag, Timer } from 'lucide-react-native';
import { useNavigation, useRoute } from '@react-navigation/native';
import { Colors } from '../../theme/colors';
import { feedback } from '../../services/feedbackService';
import { useAuth } from '../../context/AuthContext';
import { RecordAttemptService } from '../../services/recordAttemptService';

import { useSafeAreaInsets } from 'react-native-safe-area-context';

enum TimedPhase { IDLE, COUNTDOWN, RUNNING, ENTER_REPS, ENTER_ROUNDS }

const formatStopwatch = (ms: number) => {
  const totalSec = Math.max(0, Math.floor(ms / 1000));
  const centi = Math.floor((ms % 1000) / 10);
  const m = Math.floor(totalSec / 60);
  const s = totalSec % 60;
  return `${m}:${s.toString().padStart(2, '0')}.${centi.toString().padStart(2, '0')}`;
};

export const RecordAttemptTimedModesScreen: React.FC = () => {
  const insets = useSafeAreaInsets();
  const navigation = useNavigation<any>();
  const route = useRoute<any>();
  const { session } = useAuth();
  const userId = session?.user?.id;
  
  const {
    attempt = {},
    exercise = { name: 'Timed Exercise' },
    catalogExercise,
    measureType = 'TIME',
    catalogId,
    categoryId,
    targetValue,
    targetReps,
    plannedSet,
  } = route.params || {};

  const isRunningMode = categoryId === 'running' || catalogId?.startsWith('run_');
  const isBodyweight = categoryId === 'bodyweight' || (measureType === 'REPS' && categoryId !== 'benchmark');
  const isBenchmark = categoryId === 'benchmark';
  const isCindyAmrap = exercise.name?.toLowerCase() === 'cindy' || catalogId?.includes('amrap');
  
  const amrapCapSeconds = RecordAttemptService.amrapCapSeconds(catalogId);
  const amrapCapMs = amrapCapSeconds * 1000;
  const targetKm = RecordAttemptService.runningTargetKm(catalogId || exercise?.id || '');

  const [phase, setPhase] = useState<TimedPhase>(TimedPhase.IDLE);
  const [countdown, setCountdown] = useState(3);
  const [elapsedMs, setElapsedMs] = useState(0);
  const [isFinishing, setIsFinishing] = useState(false);
  const [showAbandon, setShowAbandon] = useState(false);
  
  const [showRepsDialog, setShowRepsDialog] = useState(false);
  const [repsInput, setRepsInput] = useState('');
  
  const [showRoundsDialog, setShowRoundsDialog] = useState(false);
  const [roundsInput, setRoundsInput] = useState('');

  const remainingMs = Math.max(0, amrapCapMs - elapsedMs);

  const handleAbandon = async () => {
    const confirmed = await feedback.destructive({
      title: 'Denemeyi Bırak',
      message: 'Bu denemeyi bırakmak istediğine emin misin?',
      confirmText: 'Bırak',
      cancelText: 'İptal',
    });

    if (confirmed) {
      if (attempt?.id) {
        await RecordAttemptService.abandonAttempt(attempt.id);
      }
      navigation.goBack();
    }
  };

  // Timer loop
  useEffect(() => {
    let interval: any;
    if (phase === TimedPhase.RUNNING) {
      const startTime = Date.now() - elapsedMs;
      interval = setInterval(() => {
        const currentElapsed = Date.now() - startTime;
        if (isCindyAmrap && currentElapsed >= amrapCapMs) {
          setElapsedMs(amrapCapMs);
          setPhase(TimedPhase.ENTER_ROUNDS);
          setShowRoundsDialog(true);
        } else {
          setElapsedMs(currentElapsed);
        }
      }, 50);
    }
    return () => clearInterval(interval);
  }, [phase, isCindyAmrap, elapsedMs]);

  // Countdown loop
  useEffect(() => {
    let interval: any;
    if (phase === TimedPhase.COUNTDOWN) {
      interval = setInterval(() => {
        setCountdown((prev) => {
          if (prev <= 1) {
            clearInterval(interval);
            setPhase(TimedPhase.RUNNING);
            return 3;
          }
          return prev - 1;
        });
      }, 1000);
    }
    return () => clearInterval(interval);
  }, [phase]);

  const handleStart = () => {
    setPhase(TimedPhase.COUNTDOWN);
  };

  const handleStop = () => {
    if (phase !== TimedPhase.RUNNING || isFinishing) return;
    if (isBodyweight) {
      setPhase(TimedPhase.ENTER_REPS);
      setShowRepsDialog(true);
    } else if (isCindyAmrap) {
      setPhase(TimedPhase.ENTER_ROUNDS);
      setShowRoundsDialog(true);
    } else {
      finishAttempt();
    }
  };

  const finishAttempt = async (enteredValue?: number) => {
    if (isFinishing) return;
    setIsFinishing(true);

    const elapsedSec = Math.max(1, Math.floor(elapsedMs / 1000));
    let targetDisplayVal = elapsedSec;
    let repsVal = 1;
    let notesStr = `Süre: ${elapsedSec} sn`;

    if (isBodyweight) {
      repsVal = enteredValue !== undefined ? enteredValue : (parseInt(repsInput, 10) || targetReps || 10);
      targetDisplayVal = repsVal;
      notesStr = `${repsVal} tekrar (${elapsedSec} sn)`;
    } else if (isCindyAmrap) {
      repsVal = enteredValue !== undefined ? enteredValue : (parseInt(roundsInput, 10) || 20);
      targetDisplayVal = repsVal;
      notesStr = `AMRAP: ${repsVal} tur (${elapsedSec} sn)`;
    } else if (isRunningMode) {
      targetDisplayVal = targetKm;
      notesStr = `${targetKm} km - ${elapsedSec} sn`;
    } else if (categoryId === 'cardio') {
      targetDisplayVal = targetValue || 50;
      notesStr = `${targetDisplayVal} cal (${elapsedSec} sn)`;
    }

    try {
      // 1. Update plannedSet if available
      if (plannedSet?.id) {
        await RecordAttemptService.saveSet(
          plannedSet.id,
          isRunningMode ? targetKm : (isBodyweight || isCindyAmrap ? 0 : targetDisplayVal),
          repsVal,
          9,
          0,
          notesStr
        );
      }

      // 2. Finalize canonical attempt
      if (attempt?.id && userId) {
        await RecordAttemptService.completeAttempt(
          attempt.id,
          true,
          notesStr,
          userId
        );

        // 3. Save to personal records
        if (exercise?.id) {
          await RecordAttemptService.savePersonalRecord(
            userId,
            exercise.id,
            isRunningMode ? elapsedSec : (isBodyweight || isCindyAmrap ? 0 : targetDisplayVal),
            repsVal,
            notesStr
          );
        }
      }

      navigation.navigate('RecordAttemptSummary', {
        attempt,
        exercise,
        recordType: measureType?.toLowerCase() || (isBodyweight ? 'reps' : isRunningMode ? 'distance' : 'time'),
        targetValue: targetDisplayVal,
        targetReps: repsVal,
        success: true,
        elapsedSeconds: elapsedSec,
        rpe: 9,
      });
    } catch (e: any) {
      console.error('[RecordAttemptTimedModesScreen] Failed to finalize attempt:', e);
      feedback.error({
        title: 'Kayıt Hatası',
        message: e,
        fallbackMessage: 'Zamanlı rekor denemesi kaydedilemedi. Lütfen tekrar deneyin.',
      });
    } finally {
      setIsFinishing(false);
    }
  };

  const renderIdle = () => (
    <View style={styles.centerContainer}>
      {isRunningMode ? <Flag size={56} color={Colors.primary} /> : <Timer size={56} color={Colors.primary} />}
      <Text style={styles.title}>{isCindyAmrap ? `AMRAP ${amrapCapSeconds/60} dk` : (isRunningMode ? `${targetKm} km hedef` : 'Zamanlı Antrenman')}</Text>
      <Text style={styles.subtitle}>Başlamak için hazır olduğunuzda butona basın.</Text>
      <TouchableOpacity style={styles.primaryButton} onPress={handleStart}>
        <Play color={Colors.allWhite} size={24} />
        <Text style={styles.primaryButtonText}>Başlat</Text>
      </TouchableOpacity>
    </View>
  );

  const renderCountdown = () => (
    <View style={styles.centerContainer}>
      <Text style={styles.countdownText}>{countdown}</Text>
    </View>
  );

  const renderRunning = () => (
    <View style={styles.centerContainer}>
      {isCindyAmrap ? (
        <>
          <Text style={styles.label}>AMRAP</Text>
          <Text style={styles.timerText}>{formatStopwatch(remainingMs)}</Text>
        </>
      ) : (
        <Text style={styles.timerText}>{formatStopwatch(elapsedMs)}</Text>
      )}
      <Text style={styles.subtitle}>{isBodyweight ? 'Tekrarlarını say, bitirince durdur' : 'İşin bitince süreyi durdur'}</Text>
      
      <TouchableOpacity 
        style={[styles.primaryButton, { backgroundColor: '#E53935', marginTop: 40 }]} 
        onPress={handleStop}
        disabled={isFinishing}
      >
        {isFinishing ? (
          <ActivityIndicator color={Colors.allWhite} />
        ) : (
          <>
            <Square color={Colors.allWhite} size={24} />
            <Text style={styles.primaryButtonText}>Bitir</Text>
          </>
        )}
      </TouchableOpacity>
    </View>
  );

  return (
    <View style={styles.container}>
      <View style={[styles.header, { paddingTop: Math.max(16, insets.top + 8) }]}>
        <TouchableOpacity style={styles.iconButton} onPress={handleAbandon}>
          <ArrowLeft size={24} color={Colors.textLight} />
        </TouchableOpacity>
        <Text style={styles.headerTitle} numberOfLines={1}>{exercise.name}</Text>
        <TouchableOpacity style={styles.iconButton} onPress={handleAbandon}>
          <X size={24} color={Colors.textLight} />
        </TouchableOpacity>
      </View>

      {phase === TimedPhase.IDLE && renderIdle()}
      {phase === TimedPhase.COUNTDOWN && renderCountdown()}
      {(phase === TimedPhase.RUNNING || phase === TimedPhase.ENTER_REPS || phase === TimedPhase.ENTER_ROUNDS) && renderRunning()}

      {/* Reps/Rounds Modal */}
      <SmoothModal
        visible={showRepsDialog || showRoundsDialog}
        onClose={() => {
          if (!isFinishing) {
            setShowRepsDialog(false);
            setShowRoundsDialog(false);
          }
        }}
        variant="modal"
      >
        <View style={styles.modalContent}>
          <Text style={styles.modalTitle}>{showRepsDialog ? 'Kaç tekrar yaptın?' : 'Kaç tur yaptın?'}</Text>
          <TextInput
            style={styles.input}
            value={showRepsDialog ? repsInput : roundsInput}
            onChangeText={showRepsDialog ? setRepsInput : setRoundsInput}
            keyboardType="number-pad"
            placeholder={showRepsDialog ? 'Tekrar' : 'Tur'}
            placeholderTextColor={Colors.textSecondaryDark}
            editable={!isFinishing}
          />
          <View style={styles.modalActions}>
            <TouchableOpacity 
              style={styles.modalButton} 
              onPress={() => {
                setShowRepsDialog(false);
                setShowRoundsDialog(false);
                setPhase(TimedPhase.RUNNING);
              }}
              disabled={isFinishing}
            >
              <Text style={styles.modalButtonText}>Geri</Text>
            </TouchableOpacity>
            <TouchableOpacity 
              style={styles.modalButton} 
              onPress={() => {
                const val = showRepsDialog ? parseInt(repsInput, 10) : parseInt(roundsInput, 10);
                setShowRepsDialog(false);
                setShowRoundsDialog(false);
                finishAttempt(isNaN(val) ? undefined : val);
              }}
              disabled={isFinishing}
            >
              {isFinishing ? (
                <ActivityIndicator size="small" color={Colors.primary} />
              ) : (
                <Text style={[styles.modalButtonText, { color: Colors.primary }]}>Kaydet</Text>
              )}
            </TouchableOpacity>
          </View>
        </View>
      </SmoothModal>
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
    paddingHorizontal: 16,
    paddingTop: 48,
    paddingBottom: 16,
  },
  iconButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: Colors.cardDark,
    alignItems: 'center',
    justifyContent: 'center',
  },
  headerTitle: {
    flex: 1,
    color: Colors.allWhite,
    fontSize: 17,
    fontWeight: '600',
    textAlign: 'center',
  },
  centerContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  title: {
    color: Colors.allWhite,
    fontSize: 18,
    fontWeight: '600',
    marginTop: 16,
    marginBottom: 8,
  },
  subtitle: {
    color: Colors.textSecondaryDark,
    fontSize: 14,
    textAlign: 'center',
    marginBottom: 32,
  },
  primaryButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: Colors.primary,
    height: 56,
    borderRadius: 28,
    paddingHorizontal: 32,
    width: '100%',
  },
  primaryButtonText: {
    color: Colors.allWhite,
    fontSize: 17,
    fontWeight: 'bold',
    marginLeft: 8,
  },
  countdownText: {
    fontSize: 96,
    fontWeight: '900',
    color: Colors.primary,
  },
  timerText: {
    fontSize: 56,
    fontWeight: 'bold',
    color: Colors.allWhite,
  },
  label: {
    fontSize: 14,
    fontWeight: 'bold',
    color: Colors.primary,
    letterSpacing: 1,
    marginBottom: 8,
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.6)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  modalContent: {
    width: '100%',
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 24,
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: Colors.allWhite,
    marginBottom: 12,
  },
  modalText: {
    fontSize: 14,
    color: Colors.textSecondaryDark,
    marginBottom: 24,
  },
  input: {
    backgroundColor: Colors.backgroundDark,
    color: Colors.allWhite,
    borderRadius: 8,
    padding: 16,
    fontSize: 16,
    marginBottom: 24,
  },
  modalActions: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
  },
  modalButton: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    marginLeft: 8,
  },
  modalButtonText: {
    fontSize: 16,
    color: Colors.allWhite,
    fontWeight: '500',
  },
});
