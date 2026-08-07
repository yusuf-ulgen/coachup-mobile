import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Modal,
  TextInput,
  ActivityIndicator,
} from 'react-native';
import { ArrowLeft, X, Play, Square, Flag, Timer } from 'lucide-react-native';
import { useNavigation, useRoute } from '@react-navigation/native';
import { Colors } from '../../theme/colors';

// Mocks for types that would normally come from services/models
enum TimedPhase { IDLE, COUNTDOWN, RUNNING, ENTER_REPS, ENTER_ROUNDS }

const formatStopwatch = (ms: number) => {
  const totalSec = Math.max(0, Math.floor(ms / 1000));
  const centi = Math.floor((ms % 1000) / 10);
  const m = Math.floor(totalSec / 60);
  const s = totalSec % 60;
  return `${m}:${s.toString().padStart(2, '0')}.${centi.toString().padStart(2, '0')}`;
};

export const RecordAttemptTimedModesScreen: React.FC = () => {
  const navigation = useNavigation<any>();
  const route = useRoute<any>();
  
  const {
    attempt = {},
    exercise = { name: 'Timed Exercise' },
    measureType = 'TIME',
    catalogId,
    categoryId,
  } = route.params || {};

  const isRunningMode = categoryId === 'running' || catalogId?.startsWith('run_');
  const isBodyweight = categoryId === 'bodyweight' || (measureType === 'REPS' && categoryId !== 'benchmark');
  const isBenchmark = categoryId === 'benchmark';
  const isCindyAmrap = exercise.name?.toLowerCase() === 'cindy' || catalogId?.includes('amrap');
  
  const amrapCapSeconds = 20 * 60; // 20 minutes default
  const amrapCapMs = amrapCapSeconds * 1000;
  const targetKm = 5.0; // Mock

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

  const finishAttempt = () => {
    setIsFinishing(true);
    setTimeout(() => {
      navigation.navigate('RecordAttemptSummary', {
        attempt,
        elapsedSeconds: Math.floor(elapsedMs / 1000),
      });
    }, 1000);
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
      <View style={styles.header}>
        <TouchableOpacity style={styles.iconButton} onPress={() => setShowAbandon(true)}>
          <ArrowLeft size={24} color={Colors.textLight} />
        </TouchableOpacity>
        <Text style={styles.headerTitle} numberOfLines={1}>{exercise.name}</Text>
        <TouchableOpacity style={styles.iconButton} onPress={() => setShowAbandon(true)}>
          <X size={24} color={Colors.textLight} />
        </TouchableOpacity>
      </View>

      {phase === TimedPhase.IDLE && renderIdle()}
      {phase === TimedPhase.COUNTDOWN && renderCountdown()}
      {(phase === TimedPhase.RUNNING || phase === TimedPhase.ENTER_REPS || phase === TimedPhase.ENTER_ROUNDS) && renderRunning()}

      {/* Abandon Modal */}
      <Modal visible={showAbandon} transparent animationType="fade">
        <View style={styles.modalOverlay}>
          <View style={styles.modalContent}>
            <Text style={styles.modalTitle}>Denemeyi Bırak</Text>
            <Text style={styles.modalText}>Bu denemeyi bırakmak istediğine emin misin?</Text>
            <View style={styles.modalActions}>
              <TouchableOpacity style={styles.modalButton} onPress={() => setShowAbandon(false)}>
                <Text style={styles.modalButtonText}>İptal</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.modalButton} onPress={() => {
                setShowAbandon(false);
                navigation.goBack();
              }}>
                <Text style={[styles.modalButtonText, { color: '#E53935' }]}>Bırak</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>

      {/* Reps/Rounds Modal */}
      <Modal visible={showRepsDialog || showRoundsDialog} transparent animationType="fade">
        <View style={styles.modalOverlay}>
          <View style={styles.modalContent}>
            <Text style={styles.modalTitle}>{showRepsDialog ? 'Kaç tekrar yaptın?' : 'Kaç tur yaptın?'}</Text>
            <TextInput
              style={styles.input}
              value={showRepsDialog ? repsInput : roundsInput}
              onChangeText={showRepsDialog ? setRepsInput : setRoundsInput}
              keyboardType="number-pad"
              placeholder={showRepsDialog ? 'Tekrar' : 'Tur'}
              placeholderTextColor={Colors.textSecondaryDark}
            />
            <View style={styles.modalActions}>
              <TouchableOpacity style={styles.modalButton} onPress={() => {
                setShowRepsDialog(false);
                setShowRoundsDialog(false);
                setPhase(TimedPhase.RUNNING);
              }}>
                <Text style={styles.modalButtonText}>Geri</Text>
              </TouchableOpacity>
              <TouchableOpacity 
                style={styles.modalButton} 
                onPress={() => {
                  setShowRepsDialog(false);
                  setShowRoundsDialog(false);
                  finishAttempt();
                }}
              >
                <Text style={[styles.modalButtonText, { color: Colors.primary }]}>Kaydet</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
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
