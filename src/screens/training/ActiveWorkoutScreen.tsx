import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Alert,
  ActivityIndicator,
  Modal,
} from 'react-native';
import {
  ArrowLeft,
  Play,
  Pause,
  CheckCircle2,
  Clock,
  Flame,
  Award,
  X,
  Activity
} from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { AuthService } from '../../services/authService';
import { supabase } from '../../services/supabaseClient';
import { TrainingService } from '../../services/trainingService';
import { LocationService, LocationStats } from '../../services/locationService';
import { MapPin, Navigation } from 'lucide-react-native';
import { HealthConnectService } from '../../services/healthConnectService';
import { ActiveWorkoutManager } from '../../services/activeWorkoutManager';
import { CustomAlert } from '../../components/CustomAlertModal';

interface ActiveWorkoutScreenProps {
  route?: any;
  navigation?: any;
}

export const ActiveWorkoutScreen: React.FC<ActiveWorkoutScreenProps> = ({
  route,
  navigation,
}) => {
  const workoutTitle = route?.params?.title || 'Aktif Antrenman';
  const category = route?.params?.category || 'Genel';
  const programId = route?.params?.programId;
  const sessionId = route?.params?.sessionId;

  const [seconds, setSeconds] = useState(0);
  const [isActive, setIsActive] = useState(true);
  
  const [exercises, setExercises] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [completedSetIds, setCompletedSetIds] = useState<Set<string>>(new Set());
  
  const [saving, setSaving] = useState(false);
  const [totalSets, setTotalSets] = useState(0);

  // Rest Timer State
  const [showRestTimer, setShowRestTimer] = useState(false);
  const [restTime, setRestTime] = useState(60);
  
  // RPE State
  const [showRpeModal, setShowRpeModal] = useState(false);
  const [rpeScore, setRpeScore] = useState<number | null>(null);

  // GPS State
  const [locationStats, setLocationStats] = useState<LocationStats | null>(null);
  const lowerCat = (category || '').toLowerCase();
  const lowerTitle = (workoutTitle || '').toLowerCase();
  const isOutdoor =
    ['running', 'walking', 'cycling', 'hyrox', 'swimming', 'koşu', 'yürüyüş', 'bisiklet', 'hyrox', 'yüzme'].includes(lowerCat) ||
    ['koşu', 'yürüyüş', 'bisiklet', 'hyrox', 'yüzme'].some((kw) => lowerTitle.includes(kw));

  // Health Data State
  const [heartRate, setHeartRate] = useState(0);
  const [activeCalories, setActiveCalories] = useState(0);

  useEffect(() => {
    loadData();
    HealthConnectService.requestPermissions();

    if (sessionId || workoutTitle) {
      ActiveWorkoutManager.startWorkout(sessionId || 'temp_session', workoutTitle, programId);
    }
    ActiveWorkoutManager.setScreenFocus(true);

    return () => {
      ActiveWorkoutManager.setScreenFocus(false);
    };
  }, [sessionId, workoutTitle, programId]);

  const selectedDay = route?.params?.selectedDay;

  const loadData = async () => {
    if (programId) {
      try {
        const data = await TrainingService.fetchProgramExercises(programId);
        const availableDays = Array.from(
          new Set(
            (data || [])
              .map((ex: any) => Math.floor((ex.order_index || 0) / 100))
              .filter((d: number) => d > 0)
          )
        );

        let filteredData = data || [];
        if (selectedDay && availableDays.length > 1) {
          filteredData = filteredData.filter(
            (ex: any) => Math.floor((ex.order_index || 0) / 100) === selectedDay
          );
        }

        setExercises(filteredData);
        let tSets = 0;
        filteredData.forEach((ex: any) => {
          tSets += ex.sets || 0;
        });
        setTotalSets(tSets);
      } catch (e) {
        console.error('Error loading workout exercises:', e);
      }
    }
    setLoading(false);
  };

  useEffect(() => {
    let interval: any = null;
    let hrInterval: any = null;
    if (isActive) {
      interval = setInterval(() => {
        setSeconds((sec) => {
          const nextSec = sec + 1;
          ActiveWorkoutManager.updateSeconds(nextSec);
          return nextSec;
        });
      }, 1000);
      
      if (isOutdoor) {
        LocationService.startTracking((stats) => {
          setLocationStats(stats);
        }).catch(e => console.error(e));
      }

      hrInterval = setInterval(async () => {
        const hr = await HealthConnectService.getLiveHeartRate();
        if (hr > 0) setHeartRate(hr);
      }, 2000);
    } else {
      clearInterval(interval);
      clearInterval(hrInterval);
      if (isOutdoor) {
        LocationService.stopTracking();
      }
    }
    return () => {
      clearInterval(interval);
      clearInterval(hrInterval);
      LocationService.stopTracking();
    };
  }, [isActive, isOutdoor]);

  useEffect(() => {
    if (seconds > 0) {
      setActiveCalories(HealthConnectService.calculateActiveCalories(seconds, heartRate || 120));
    }
  }, [seconds, heartRate]);

  // Rest timer countdown
  useEffect(() => {
    let interval: any = null;
    if (showRestTimer && restTime > 0) {
      interval = setInterval(() => {
        setRestTime((prev) => prev - 1);
      }, 1000);
    } else if (restTime === 0 && showRestTimer) {
      setShowRestTimer(false);
    }
    return () => clearInterval(interval);
  }, [showRestTimer, restTime]);

  const formatTime = (totalSeconds: number) => {
    const mins = Math.floor(totalSeconds / 60);
    const secs = totalSeconds % 60;
    return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  };

  const handleSetToggle = async (ex: any, setIndex: number) => {
    const setId = `${ex.id}_${setIndex}`;
    const isDone = completedSetIds.has(setId);

    if (isDone) {
      // Undo
      const newSet = new Set(completedSetIds);
      newSet.delete(setId);
      setCompletedSetIds(newSet);
    } else {
      // Complete
      const newSet = new Set(completedSetIds);
      newSet.add(setId);
      setCompletedSetIds(newSet);
      
      try {
        const currentUser = await AuthService.getCurrentUser();
        await TrainingService.completeSet(setId, {
          reps: ex.reps || 10,
          weight: 0,
          sessionId: sessionId,
          exerciseId: ex.exercise_id || ex.id,
          setNumber: setIndex,
          userId: currentUser?.id,
        });
      } catch (e) {
        console.warn('Error saving set:', e);
      }
      
      // Show rest timer
      setRestTime(ex.rest_seconds || 60);
      setShowRestTimer(true);
    }
  };

  const initiateFinishWorkout = () => {
    setIsActive(false);
    setShowRpeModal(true);
  };

  const finalizeWorkout = async () => {
    setShowRpeModal(false);
    setSaving(true);
    ActiveWorkoutManager.finishWorkout();
    try {
      const user = await AuthService.getCurrentUser();
      if (user) {
        await supabase.from('user_activities').insert({
          user_id: user.id,
          activity_type: category,
          duration: Math.ceil(seconds / 60),
          calories_burned: activeCalories,
          activity_date: new Date().toISOString().split('T')[0],
        });
      }
      
      if (sessionId) {
        await TrainingService.completeSession(sessionId, seconds, `RPE: ${rpeScore || '-'}`);
      }

      CustomAlert.show({
        title: 'Tebrikler! 🎉',
        message: `Antrenman başarıyla tamamlandı.\nSüre: ${formatTime(seconds)}\nZorluk (RPE): ${rpeScore || '-'} / 10`,
        type: 'success',
        buttons: [
          {
            text: 'Özeti Gör',
            onPress: () => {
              navigation?.navigate('WorkoutSummary', {
                training: { title: workoutTitle, category: { emoji: isOutdoor ? '🏃' : '🏋️' } },
                durationSeconds: seconds,
                calories: Math.ceil((seconds / 60) * 8),
                distanceKm: locationStats?.distanceKm || 0,
                avgPaceMinPerKm: locationStats?.paceMinPerKm || 0,
                perceivedEffort: `${rpeScore}/10`,
                route: locationStats?.route || [],
                completedSetsCount: completedSetIds.size,
              });
            },
          },
        ],
      });
    } catch (e) {
      console.error('Error saving workout session:', e);
      navigation?.goBack();
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
     return (
       <View style={[styles.container, { justifyContent: 'center', alignItems: 'center' }]}>
          <ActivityIndicator size="large" color={Colors.primary} />
       </View>
     )
  }

  return (
    <View style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation?.goBack()} style={styles.backButton}>
          <ArrowLeft size={24} color={Colors.textDark} />
        </TouchableOpacity>
        <Text style={styles.headerTitle} numberOfLines={1}>{workoutTitle}</Text>
      </View>

      <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
        {/* Timer Card */}
        <View style={styles.timerCard}>
          <Clock size={32} color={Colors.primary} />
          <Text style={styles.timerText}>{formatTime(seconds)}</Text>
          <Text style={styles.timerSublabel}>Geçen Süre</Text>

          <View style={styles.timerControls}>
            <TouchableOpacity
              style={styles.controlBtn}
              onPress={() => setIsActive(!isActive)}
              activeOpacity={0.8}
            >
              {isActive ? (
                <Pause size={24} color={Colors.allWhite} />
              ) : (
                <Play size={24} color={Colors.allWhite} />
              )}
            </TouchableOpacity>
          </View>
        </View>

        {/* Workout Stats Grid */}
        <View style={styles.statsGrid}>
          <View style={styles.statBox}>
            <Flame size={22} color="#FF9800" />
            <Text style={styles.statVal}>{Math.ceil((seconds / 60) * 8)} kcal</Text>
            <Text style={styles.statLbl}>Tahmini Kalori</Text>
          </View>

          {isOutdoor ? (
            <View style={styles.statBox}>
              <MapPin size={22} color={Colors.primary} />
              <Text style={styles.statVal}>{locationStats?.distanceKm.toFixed(2) || '0.00'} km</Text>
              <Text style={styles.statLbl}>Mesafe</Text>
            </View>
          ) : totalSets > 0 || exercises.length > 0 ? (
            <View style={styles.statBox}>
              <Award size={22} color={Colors.primary} />
              <Text style={styles.statVal}>
                {completedSetIds.size} / {totalSets} Set
              </Text>
              <Text style={styles.statLbl}>Tamamlanan</Text>
            </View>
          ) : (
            <View style={styles.statBox}>
              <Activity size={22} color={Colors.primary} />
              <Text style={styles.statVal}>{heartRate > 0 ? `${heartRate} bpm` : 'Aktif'}</Text>
              <Text style={styles.statLbl}>Serbest Takip</Text>
            </View>
          )}
        </View>

        {isOutdoor && (
          <View style={styles.outdoorStatsCard}>
            <View style={styles.outdoorRow}>
               <View style={styles.outdoorStat}>
                  <Text style={styles.outdoorStatLbl}>Tempo</Text>
                  <Text style={styles.outdoorStatVal}>
                    {locationStats?.paceMinPerKm ? 
                     `${Math.floor(locationStats.paceMinPerKm)}'${Math.round((locationStats.paceMinPerKm % 1) * 60)}"` 
                     : "0'00\""} /km
                  </Text>
               </View>
               <View style={styles.outdoorStat}>
                  <Text style={styles.outdoorStatLbl}>Hız</Text>
                  <Text style={styles.outdoorStatVal}>
                    {locationStats?.currentSpeed ? (locationStats.currentSpeed * 3.6).toFixed(1) : "0.0"} km/s
                  </Text>
               </View>
               <View style={styles.outdoorStat}>
                  <Text style={styles.outdoorStatLbl}>Yükseklik</Text>
                  <Text style={styles.outdoorStatVal}>
                    {locationStats?.altitude ? locationStats.altitude.toFixed(0) : "0"} m
                  </Text>
               </View>
            </View>
            {/* Simple Route Placeholder since we don't have MapView imported */}
            <View style={styles.mapPlaceholder}>
              <Navigation size={32} color={Colors.primary} />
              <Text style={styles.mapText}>Canlı GPS Takibi Aktif</Text>
              <Text style={styles.mapSubtext}>
                {locationStats?.route?.length || 0} konum noktası kaydedildi
              </Text>
            </View>
          </View>
        )}

          {/* Exercises & Sets Tracker (Hidden for pure outdoor/cardio activities) */}
        {!isOutdoor && (
          <>
            <View style={styles.sectionHeader}>
              <Text style={styles.sectionTitle}>Egzersizler & Setler</Text>
            </View>

            {exercises.length === 0 ? (
              <View style={styles.freeActivityCard}>
                <View style={styles.freeActivityBadge}>
                  <Activity size={28} color={Colors.primary} />
                </View>
                <Text style={styles.freeActivityTitle}>{workoutTitle} Takibi</Text>
                <Text style={styles.freeActivitySubtitle}>
                  Serbest aktivite modundasınız. Kalori, süre ve nabız verileriniz otomatik kaydediliyor.
                </Text>
                
                <TouchableOpacity
                  style={styles.addSetButton}
                  activeOpacity={0.8}
                  onPress={() => {
                    const newExId = `free_ex_${Date.now()}`;
                    setExercises((prev) => [
                      ...prev,
                      { id: newExId, name: `${workoutTitle} Seti`, sets: 1, reps: 10, rest_seconds: 60 }
                    ]);
                  }}
                >
                  <Text style={styles.addSetButtonText}>+ Serbest Set Ekle</Text>
                </TouchableOpacity>
              </View>
            ) : (
              exercises.map((ex, index) => {
                const exName = ex.exercises?.name || ex.exercise?.name || ex.name || `Egzersiz ${index + 1}`;
                const exSets = ex.sets || 3;
                const exReps = ex.reps || 10;
                const exRest = ex.rest_seconds || 60;
                const setArray = Array.from({ length: exSets }, (_, i) => i);

                return (
                  <View key={ex.id || index} style={styles.exerciseContainer}>
                    <Text style={styles.exerciseTitle}>{exName}</Text>
                    <Text style={styles.exerciseNotes}>
                      {exSets} Set x {exReps} Tekrar | {exRest} sn Dinlenme
                    </Text>

                    {setArray.map((sIdx) => {
                      const setId = `${ex.id}_${sIdx}`;
                      const isDone = completedSetIds.has(setId);

                      return (
                        <TouchableOpacity
                          key={sIdx}
                          style={[styles.setRow, isDone && styles.setRowDone]}
                          activeOpacity={0.8}
                          onPress={() => handleSetToggle(ex, sIdx)}
                        >
                          <View style={styles.setLeft}>
                            <Text style={[styles.setNumText, isDone && { color: Colors.primary }]}>
                              Set {sIdx + 1}
                            </Text>
                            <Text style={styles.setSubText}>{exReps} Tekrar</Text>
                          </View>
                          <CheckCircle2
                            size={24}
                            color={isDone ? Colors.primary : Colors.textSecondaryDark}
                          />
                        </TouchableOpacity>
                      );
                    })}
                  </View>
                );
              })
            )}
          </>
        )}

        {/* Complete Workout Button */}
        <TouchableOpacity
          style={styles.finishBtn}
          onPress={initiateFinishWorkout}
          disabled={saving}
          activeOpacity={0.85}
        >
          {saving ? (
            <ActivityIndicator color={Colors.allWhite} />
          ) : (
            <Text style={styles.finishBtnText}>Antrenmanı Bitir</Text>
          )}
        </TouchableOpacity>
      </ScrollView>

      {/* Rest Timer Modal */}
      <Modal visible={showRestTimer} transparent={true} animationType="fade">
        <View style={styles.modalOverlay}>
           <View style={styles.restTimerCard}>
              <Text style={styles.restTitle}>Dinlenme Zamanı</Text>
              
              <View style={styles.circularTimer}>
                 <Text style={styles.restSecondsText}>{restTime}</Text>
              </View>

              <TouchableOpacity style={styles.skipBtn} onPress={() => setShowRestTimer(false)}>
                 <Text style={styles.skipBtnText}>Atla</Text>
              </TouchableOpacity>
           </View>
        </View>
      </Modal>

      {/* RPE Modal */}
      <Modal visible={showRpeModal} transparent={true} animationType="slide">
        <View style={styles.modalOverlay}>
           <View style={styles.rpeCard}>
              <View style={styles.rpeHeader}>
                <Text style={styles.rpeTitle}>Antrenman Nasıl Geçti?</Text>
                <TouchableOpacity onPress={() => { setShowRpeModal(false); setIsActive(true); }}>
                  <X size={24} color={Colors.textSecondaryDark} />
                </TouchableOpacity>
              </View>
              <Text style={styles.rpeSubtitle}>Zorluk derecesini (RPE) 1-10 arası puanlayın.</Text>
              
              <View style={styles.rpeGrid}>
                {[1,2,3,4,5,6,7,8,9,10].map(score => (
                   <TouchableOpacity 
                     key={score} 
                     style={[styles.rpeBtn, rpeScore === score && styles.rpeBtnSelected]}
                     onPress={() => setRpeScore(score)}
                   >
                     <Text style={[styles.rpeBtnText, rpeScore === score && styles.rpeBtnTextSelected]}>{score}</Text>
                   </TouchableOpacity>
                ))}
              </View>

              <TouchableOpacity 
                style={[styles.finishBtn, { marginTop: 10, width: '100%' }]}
                onPress={finalizeWorkout}
                disabled={!rpeScore}
              >
                <Text style={styles.finishBtnText}>Kaydet ve Bitir</Text>
              </TouchableOpacity>
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
    paddingTop: 14,
    paddingBottom: 14,
    backgroundColor: Colors.cardDark,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(255, 255, 255, 0.08)',
  },
  backButton: {
    padding: 6,
    marginRight: 8,
  },
  headerTitle: {
    flex: 1,
    fontSize: 18,
    fontWeight: '700',
    color: Colors.textDark,
  },
  scrollContent: {
    paddingHorizontal: 20,
    paddingTop: 20,
    paddingBottom: 100,
  },
  timerCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 24,
    padding: 32,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: Colors.borderDark,
    marginBottom: 20,
  },
  timerText: {
    fontSize: 48,
    fontWeight: '900',
    color: Colors.textDark,
    marginVertical: 8,
  },
  timerSublabel: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
  },
  timerControls: {
    marginTop: 20,
  },
  controlBtn: {
    width: 60,
    height: 60,
    borderRadius: 30,
    backgroundColor: Colors.primary,
    justifyContent: 'center',
    alignItems: 'center',
  },
  statsGrid: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 24,
  },
  statBox: {
    flex: 1,
    backgroundColor: Colors.cardDark,
    borderRadius: 18,
    padding: 16,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  statVal: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.textDark,
    marginTop: 6,
  },
  statLbl: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
    marginTop: 2,
  },
  sectionHeader: {
    marginBottom: 12,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.textDark,
  },
  exerciseContainer: {
    marginBottom: 24,
  },
  exerciseTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: Colors.allWhite,
    marginBottom: 4,
  },
  exerciseNotes: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    marginBottom: 12,
  },
  setRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 16,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  setRowDone: {
    borderColor: Colors.primary,
    backgroundColor: 'rgba(255, 96, 71, 0.06)',
  },
  setLeft: {},
  setNumText: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.textDark,
  },
  setSubText: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    marginTop: 2,
  },
  finishBtn: {
    backgroundColor: Colors.primary,
    borderRadius: 100,
    paddingVertical: 16,
    alignItems: 'center',
    marginTop: 24,
  },
  finishBtnText: {
    color: Colors.allWhite,
    fontWeight: '700',
    fontSize: 16,
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.7)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  restTimerCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 24,
    padding: 30,
    alignItems: 'center',
    width: '100%',
    maxWidth: 320,
  },
  restTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: Colors.allWhite,
    marginBottom: 24,
  },
  circularTimer: {
    width: 120,
    height: 120,
    borderRadius: 60,
    borderWidth: 4,
    borderColor: Colors.primary,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 24,
  },
  restSecondsText: {
    fontSize: 40,
    fontWeight: '900',
    color: Colors.allWhite,
  },
  skipBtn: {
    paddingVertical: 12,
    paddingHorizontal: 32,
    borderRadius: 20,
    backgroundColor: 'rgba(255,255,255,0.1)',
  },
  skipBtnText: {
    color: Colors.allWhite,
    fontSize: 16,
    fontWeight: '600',
  },
  rpeCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 24,
    padding: 24,
    width: '100%',
  },
  rpeHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  rpeTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: Colors.allWhite,
  },
  rpeSubtitle: {
    fontSize: 14,
    color: Colors.textSecondaryDark,
    marginBottom: 24,
  },
  rpeGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
    justifyContent: 'center',
    marginBottom: 24,
  },
  rpeBtn: {
    width: 45,
    height: 45,
    borderRadius: 22.5,
    backgroundColor: Colors.backgroundDark,
    borderWidth: 1,
    borderColor: Colors.borderDark,
    justifyContent: 'center',
    alignItems: 'center',
  },
  rpeBtnSelected: {
    backgroundColor: Colors.primary,
    borderColor: Colors.primary,
  },
  rpeBtnText: {
    fontSize: 16,
    color: Colors.textDark,
    fontWeight: '600',
  },
  rpeBtnTextSelected: {
    color: Colors.allWhite,
  },
  outdoorStatsCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 20,
    marginBottom: 24,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  outdoorRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 20,
  },
  outdoorStat: {
    alignItems: 'center',
  },
  outdoorStatLbl: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
    marginBottom: 4,
  },
  outdoorStatVal: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.allWhite,
  },
  mapPlaceholder: {
    height: 150,
    backgroundColor: 'rgba(0,0,0,0.3)',
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  mapText: {
    fontSize: 14,
    fontWeight: '600',
    color: Colors.primary,
    marginTop: 8,
  },
  mapSubtext: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
    marginTop: 4,
  },
  freeActivityCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 20,
    padding: 24,
    alignItems: 'center',
    marginBottom: 24,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.08)',
  },
  freeActivityBadge: {
    width: 60,
    height: 60,
    borderRadius: 30,
    backgroundColor: 'rgba(255, 107, 0, 0.15)',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 14,
  },
  freeActivityTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: Colors.textDark,
    textAlign: 'center',
  },
  freeActivitySubtitle: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    textAlign: 'center',
    marginTop: 6,
    lineHeight: 18,
  },
  addSetButton: {
    height: 46,
    borderRadius: 14,
    backgroundColor: 'rgba(255, 107, 0, 0.15)',
    borderWidth: 1,
    borderColor: Colors.primary,
    justifyContent: 'center',
    alignItems: 'center',
  },
  addSetButtonText: {
    fontSize: 15,
    fontWeight: '700',
    color: Colors.primary,
  },
});
