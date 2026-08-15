import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  ActivityIndicator,
} from 'react-native';
import { SmoothModal } from '../../components/motion/SmoothModal';
import MapView, { Marker, Polyline, UrlTile, PROVIDER_GOOGLE } from 'react-native-maps';
import { useFocusEffect } from '@react-navigation/native';
import { Colors } from '../../theme/colors';
import { useTheme } from '../../theme/ThemeContext';
import {
  ChevronDown,
  ChevronUp,
  Pause,
  Play,
  Flame,
  Heart,
  Flag,
  Sun,
  Moon,
  Crosshair,
  Dumbbell,
  Check,
} from 'lucide-react-native';
import { HealthConnectService } from '../../services/healthConnectService';
import { LocationService, LocationStats, isOutdoorWorkout } from '../../services/locationService';
import { ActiveWorkoutManager } from '../../services/activeWorkoutManager';
import { TrainingService } from '../../services/trainingService';
import { feedback } from '../../services/feedbackService';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { HealthPermissionModal } from '../../components/HealthPermissionModal';

const EFFORT_OPTIONS = [
  { id: 'harika', emoji: '😁', label: 'Harika' },
  { id: 'iyi', emoji: '🙂', label: 'İyi' },
  { id: 'normal', emoji: '😐', label: 'Normal' },
  { id: 'zor', emoji: '😤', label: 'Zor' },
  { id: 'cok_zor', emoji: '🥵', label: 'Çok Zor' },
];

const DARK_MAP_STYLE = [
  { elementType: 'geometry', stylers: [{ color: '#1d2c4d' }] },
  { elementType: 'labels.text.fill', stylers: [{ color: '#8ec3b9' }] },
  { elementType: 'labels.text.stroke', stylers: [{ color: '#1a3646' }] },
  {
    featureType: 'administrative.country',
    elementType: 'geometry.stroke',
    stylers: [{ color: '#4b687a' }],
  },
  {
    featureType: 'administrative.province',
    elementType: 'geometry.stroke',
    stylers: [{ color: '#4b687a' }],
  },
  {
    featureType: 'landscape.man_made',
    elementType: 'geometry.stroke',
    stylers: [{ color: '#334e68' }],
  },
  {
    featureType: 'landscape.natural',
    elementType: 'geometry',
    stylers: [{ color: '#023e58' }],
  },
  {
    featureType: 'poi',
    elementType: 'geometry',
    stylers: [{ color: '#283d6a' }],
  },
  {
    featureType: 'poi',
    elementType: 'labels.text.fill',
    stylers: [{ color: '#6f9ba5' }],
  },
  {
    featureType: 'poi.park',
    elementType: 'geometry.fill',
    stylers: [{ color: '#023e58' }],
  },
  {
    featureType: 'road',
    elementType: 'geometry',
    stylers: [{ color: '#304a7d' }],
  },
  {
    featureType: 'road',
    elementType: 'labels.text.fill',
    stylers: [{ color: '#98a5be' }],
  },
  {
    featureType: 'road.highway',
    elementType: 'geometry',
    stylers: [{ color: '#2c4591' }],
  },
  {
    featureType: 'road.highway',
    elementType: 'geometry.stroke',
    stylers: [{ color: '#1f2835' }],
  },
  {
    featureType: 'transit',
    elementType: 'labels.text.fill',
    stylers: [{ color: '#98a5be' }],
  },
  {
    featureType: 'water',
    elementType: 'geometry',
    stylers: [{ color: '#0e1626' }],
  },
  {
    featureType: 'water',
    elementType: 'labels.text.fill',
    stylers: [{ color: '#4e6d70' }],
  },
];

export const ActiveWorkoutScreen = ({ route, navigation }: any) => {
  const insets = useSafeAreaInsets();
  const { isDark, toggleTheme, colors } = useTheme();
  const existingManager = ActiveWorkoutManager.getState();

  // Retrieve route params or fallback to ongoing ActiveWorkoutManager session metadata
  const sessionId = route.params?.sessionId || existingManager.sessionId;
  const programId = route.params?.programId || existingManager.programId;
  const selectedDay = route.params?.selectedDay || existingManager.selectedDay || 1;
  const title = route.params?.title || existingManager.title;
  const workoutTitle = route.params?.workoutTitle || existingManager.workoutTitle || title;
  const category = route.params?.category || existingManager.category || '';
  const emoji = route.params?.emoji || existingManager.emoji || '🏃';

  const activityName = workoutTitle || title || 'Koşu';
  const activityEmoji = emoji || '🏃';
  const isOutdoor = isOutdoorWorkout(category, activityName);

  const mapRef = useRef<MapView>(null);
  const hasInitialCameraCentered = useRef<boolean>(false);

  // Check ongoing session state from ActiveWorkoutManager
  const isAlreadyRunning = Boolean(
    existingManager.sessionId &&
    (existingManager.isActive || existingManager.hasStarted) &&
    (existingManager.sessionId === sessionId || !sessionId)
  );

  // Outdoor flow state
  const [hasStarted, setHasStarted] = useState(() =>
    isAlreadyRunning ? existingManager.hasStarted ?? true : !isOutdoor
  );
  const [seconds, setSeconds] = useState(() => ActiveWorkoutManager.getState().seconds);
  const [isActive, setIsActive] = useState(() =>
    isAlreadyRunning ? existingManager.isActive : !isOutdoor
  );
  const [heartRate, setHeartRate] = useState(0);
  const [activeCalories, setActiveCalories] = useState(0);
  const [locationStats, setLocationStats] = useState<LocationStats | null>(() =>
    isOutdoor ? LocationService.getStats() : null
  );
  const [hasLocationPermission, setHasLocationPermission] = useState(false);

  // Program Exercises State
  const [programExercises, setProgramExercises] = useState<any[]>([]);
  const [loadingExercises, setLoadingExercises] = useState(false);
  const [isExercisesExpanded, setIsExercisesExpanded] = useState(true);
  const [completedExercises, setCompletedExercises] = useState<Record<string, boolean>>({});

  useEffect(() => {
    if (programId) {
      setLoadingExercises(true);
      TrainingService.fetchProgramExercises(programId)
        .then((data) => {
          if (data && data.length > 0) {
            const filtered = data.filter(
              (ex: any) => Math.floor((ex.order_index || 0) / 100) === selectedDay
            );
            setProgramExercises(filtered.length > 0 ? filtered : data);
          }
        })
        .catch((e) => console.error('Error fetching workout exercises:', e))
        .finally(() => setLoadingExercises(false));
    }
  }, [programId, selectedDay]);

  const toggleExerciseCompleted = (exId: string) => {
    setCompletedExercises((prev) => ({
      ...prev,
      [exId]: !prev[exId],
    }));
  };

  // Modals & Map diagnostics
  const [showConfirmFinishModal, setShowConfirmFinishModal] = useState(false);
  const [showEffortModal, setShowEffortModal] = useState(false);
  const [showHealthPermissionModal, setShowHealthPermissionModal] = useState(false);
  const [mapDiagnosticText, setMapDiagnosticText] = useState('Harita başlatılıyor...');

  useEffect(() => {
    HealthConnectService.getStoredPreference().then((pref) => {
      if (pref !== 'DENIED') {
        HealthConnectService.startBleSmartWatchConnection();
      } else {
        HealthConnectService.selectNoPermission();
      }
    });
  }, []);

  // Check and subscribe to LocationService (Decoupled from component lifecycle)
  useEffect(() => {
    if (isOutdoor) {
      LocationService.requestPermissions().then((granted) => {
        setHasLocationPermission(granted);
      });

      // Initial stats snapshot
      const currentStats = LocationService.getStats();
      setLocationStats(currentStats);

      // Subscribe to live updates
      const unsubscribe = LocationService.subscribe((stats) => {
        setLocationStats(stats);
      });

      return () => {
        unsubscribe(); // Unsubscribe only; DO NOT stop background tracking!
      };
    }
  }, [isOutdoor]);

  useEffect(() => {
    if (route.params?.autoFinish) {
      setShowEffortModal(true);
    }
  }, [route.params?.autoFinish]);

  // Focus effect for immediate overlay visibility when navigating back/away
  useFocusEffect(
    useCallback(() => {
      ActiveWorkoutManager.setScreenFocus(true);
      return () => {
        ActiveWorkoutManager.setScreenFocus(false);
      };
    }, [])
  );

  useEffect(() => {
    const managerState = ActiveWorkoutManager.getState();

    if (!managerState.sessionId || !managerState.isActive) {
      ActiveWorkoutManager.startWorkout(
        sessionId || `free_${Date.now()}`,
        activityName,
        route.params?.programId,
        0,
        {
          workoutTitle: activityName,
          category,
          emoji: activityEmoji,
          isOutdoor,
          hasStarted: !isOutdoor,
        }
      );
    } else {
      ActiveWorkoutManager.setScreenFocus(true);
    }
  }, [sessionId, activityName, category, activityEmoji, isOutdoor]);

  const handleBackPress = () => {
    ActiveWorkoutManager.setScreenFocus(false);
    navigation.goBack();
  };

  const handleStartOutdoorRun = async () => {
    const granted = await LocationService.requestPermissions();
    setHasLocationPermission(granted);
    if (!granted) {
      feedback.error({
        title: 'Konum İzni Gerekli',
        message: 'GPS takibi başlatmak için lütfen konum iznini verin ve cihazınızın GPS servisini açın.',
      });
      return;
    }

    setHasStarted(true);
    setIsActive(true);
    ActiveWorkoutManager.setHasStarted(true);

    try {
      await LocationService.startTracking({
        enableAutoPause: false,
      });
    } catch (e: any) {
      console.error('[ActiveWorkoutScreen] Location tracking start error:', e);
      feedback.error({
        title: 'GPS Hatası',
        message: e.message || 'GPS konum takibi başlatılamadı.',
      });
    }
  };

  const handlePause = () => {
    setIsActive(false);
    ActiveWorkoutManager.pauseWorkout();
    if (isOutdoor) {
      LocationService.pauseTracking(true);
    }
  };

  const handleResume = () => {
    setIsActive(true);
    ActiveWorkoutManager.resumeWorkout();
    if (isOutdoor) {
      LocationService.resumeTracking();
    }
  };

  // Main Timer Interval (Synchronized with Manager)
  useEffect(() => {
    const updateTimer = () => {
      const mgrState = ActiveWorkoutManager.getState();
      setSeconds(mgrState.seconds);
      setIsActive(mgrState.isActive);
    };

    updateTimer();
    const timerInterval = setInterval(updateTimer, 1000);
    return () => {
      clearInterval(timerInterval);
    };
  }, []);

  // Heart Rate Interval
  useEffect(() => {
    let hrInterval: any = null;
    if (isActive && hasStarted) {
      hrInterval = setInterval(async () => {
        const hr = await HealthConnectService.getLiveHeartRate();
        setHeartRate(hr);
      }, 2000);
    }
    return () => {
      if (hrInterval) clearInterval(hrInterval);
    };
  }, [isActive, hasStarted]);

  useEffect(() => {
    if (seconds > 0) {
      setActiveCalories(HealthConnectService.calculateActiveCalories(seconds, heartRate));
    }
  }, [seconds, heartRate]);

  const formatTimeStr = (totalSec: number, formatHHMMSS = false) => {
    const hrs = Math.floor(totalSec / 3600);
    const mins = Math.floor((totalSec % 3600) / 60);
    const secs = totalSec % 60;

    if (formatHHMMSS) {
      return `${String(hrs).padStart(2, '0')}:${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
    }
    return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  };

  const handleRequestFinish = async () => {
    const confirmed = await feedback.confirm({
      title: 'Antrenmanı Tamamla',
      message: 'Antrenmanı bitirmek istiyor musunuz?',
      confirmText: 'Tamamla',
      cancelText: 'İptal',
    });

    if (confirmed) {
      setShowEffortModal(true);
    }
  };

  const handleEffortSelect = async (effortObj: typeof EFFORT_OPTIONS[0]) => {
    setShowEffortModal(false);

    const finalStats = LocationService.getStats();
    const finalDistance = isOutdoor ? (finalStats.distanceKm || locationStats?.distanceKm || 0) : 0;
    const finalPace = isOutdoor ? (finalStats.paceMinPerKm || locationStats?.paceMinPerKm || 0) : 0;
    const finalSpeed = isOutdoor ? (finalStats.avgSpeed || locationStats?.avgSpeed || (finalPace > 0 ? 60 / finalPace : 0)) : 0;
    const finalRoute = isOutdoor ? (finalStats.route.length > 0 ? finalStats.route : (locationStats?.route || [])) : [];

    ActiveWorkoutManager.finishWorkout();
    if (isOutdoor) {
      await LocationService.resetSession();
    }

    navigation.navigate('WorkoutSummary', {
      training: { title: activityName, category: { emoji: activityEmoji } },
      durationSeconds: seconds,
      calories: activeCalories,
      avgHeartRate: heartRate > 0 ? heartRate : null,
      maxHeartRate: heartRate > 0 ? heartRate + 10 : null,
      distanceKm: finalDistance,
      avgPaceMinPerKm: finalPace,
      avgSpeedKmh: finalSpeed,
      routePoints: finalRoute,
      perceivedEffort: effortObj.label,
      perceivedEmoji: effortObj.emoji,
    });
  };

  const currentLat = locationStats?.lastLatitude;
  const currentLon = locationStats?.lastLongitude;

  // Auto-center map on first valid GPS fix
  useEffect(() => {
    if (currentLat != null && currentLon != null && !hasInitialCameraCentered.current && mapRef.current) {
      hasInitialCameraCentered.current = true;
      mapRef.current.animateToRegion(
        {
          latitude: currentLat,
          longitude: currentLon,
          latitudeDelta: 0.006,
          longitudeDelta: 0.006,
        },
        800
      );
    }
  }, [currentLat, currentLon]);

  const renderProgramCard = () => {
    if (!programId) return null;

    return (
      <View style={[styles.programCardBox, { backgroundColor: colors.cardBg, borderColor: colors.border }]}>
        <TouchableOpacity
          style={styles.programCardHeader}
          onPress={() => setIsExercisesExpanded(!isExercisesExpanded)}
          activeOpacity={0.8}
        >
          <View style={{ flexDirection: 'row', alignItems: 'center', flex: 1, gap: 10 }}>
            <View style={styles.programIconBadge}>
              <Dumbbell size={18} color={Colors.primary} />
            </View>
            <View style={{ flex: 1 }}>
              <Text style={[styles.programCardTitle, { color: colors.textPrimary }]}>
                {activityName}
              </Text>
              <Text style={[styles.programCardSub, { color: colors.textSecondary }]}>
                {selectedDay}. Gün · {programExercises.length} Hareket
              </Text>
            </View>
          </View>
          <View style={styles.expandChevronCircle}>
            {isExercisesExpanded ? (
              <ChevronUp size={20} color={colors.textPrimary} />
            ) : (
              <ChevronDown size={20} color={colors.textPrimary} />
            )}
          </View>
        </TouchableOpacity>

        {isExercisesExpanded && (
          <View style={styles.programCardContent}>
            {loadingExercises ? (
              <ActivityIndicator size="small" color={Colors.primary} style={{ marginVertical: 12 }} />
            ) : programExercises.length > 0 ? (
              <View style={{ gap: 10, marginTop: 10 }}>
                {programExercises.map((ex: any, index: number) => {
                  const exId = ex.id || `ex_${index}`;
                  const isDone = Boolean(completedExercises[exId]);
                  const exName =
                    ex.exercises?.name ||
                    ex.exercise?.name ||
                    ex.name ||
                    `Egzersiz ${index + 1}`;
                  const setRepDetail = `${ex.sets || 3} set × ${ex.reps || 10} tekrar${
                    ex.weight_suggestion ? ` · ${ex.weight_suggestion} kg` : ''
                  }`;

                  return (
                    <TouchableOpacity
                      key={exId}
                      style={[
                        styles.exerciseItemRow,
                        { backgroundColor: isDone ? 'rgba(76,175,80,0.12)' : colors.iconBg },
                      ]}
                      onPress={() => toggleExerciseCompleted(exId)}
                      activeOpacity={0.8}
                    >
                      <Text style={[styles.exerciseNumberText, isDone && { color: '#4CAF50' }]}>
                        {index + 1}.
                      </Text>
                      <View style={{ flex: 1 }}>
                        <Text
                          style={[
                            styles.exerciseItemName,
                            { color: colors.textPrimary },
                            isDone && { textDecorationLine: 'line-through', opacity: 0.7 },
                          ]}
                        >
                          {exName}
                        </Text>
                        <Text style={[styles.exerciseItemDetail, { color: colors.textSecondary }]}>
                          {setRepDetail}
                        </Text>
                      </View>
                      <View
                        style={[
                          styles.checkCircle,
                          isDone
                            ? { backgroundColor: '#4CAF50', borderColor: '#4CAF50' }
                            : { borderColor: colors.border },
                        ]}
                      >
                        {isDone && <Check size={14} color={Colors.allWhite} />}
                      </View>
                    </TouchableOpacity>
                  );
                })}
              </View>
            ) : (
              <Text style={[styles.emptyExercisesText, { color: colors.textSecondary }]}>
                Bu gün için kaydedilmiş detaylı egzersiz bulunmuyor.
              </Text>
            )}
          </View>
        )}
      </View>
    );
  };

  return (
    <View style={[styles.container, { backgroundColor: colors.bg }]}>
      {/* ── OUTDOOR WORKOUT FLOW (Koşu, Yürüyüş, Bisiklet) ── */}
      {isOutdoor ? (
        <View style={styles.outdoorContainer}>
          {/* Header Bar */}
          <View style={[styles.outdoorHeader, { paddingTop: Math.max(16, insets.top + 8) }]}>
            <TouchableOpacity
              onPress={handleBackPress}
              style={[styles.iconCircleBtn, { backgroundColor: colors.iconBg, borderColor: colors.iconBorder }]}
            >
              <ChevronDown size={22} color={colors.textPrimary} />
            </TouchableOpacity>

            <View style={{ alignItems: 'center' }}>
              <Text style={[styles.outdoorTimerVal, { color: colors.textPrimary }]}>
                {formatTimeStr(seconds, false)}
              </Text>
              <Text style={[styles.outdoorTimerLbl, { color: colors.textSecondary }]}>SÜRE</Text>
            </View>

            <View style={{ flexDirection: 'row', gap: 8 }}>
              {/* Theme Toggle Button */}
              <TouchableOpacity
                onPress={toggleTheme}
                style={[styles.iconCircleBtn, { backgroundColor: colors.iconBg, borderColor: colors.iconBorder }]}
                activeOpacity={0.7}
              >
                {isDark ? (
                  <Sun size={20} color={colors.textPrimary} />
                ) : (
                  <Moon size={20} color={colors.textPrimary} />
                )}
              </TouchableOpacity>

              <TouchableOpacity
                onPress={handleRequestFinish}
                style={[styles.iconCircleBtn, { backgroundColor: 'rgba(255,96,71,0.2)' }]}
                activeOpacity={0.8}
              >
                <Flag size={20} color={Colors.primary} />
              </TouchableOpacity>
            </View>
          </View>

          <ScrollView contentContainerStyle={styles.outdoorScrollContent} showsVerticalScrollIndicator={false}>
            {/* Main Distance Card */}
            <View style={[styles.outdoorDistanceCard, { backgroundColor: colors.cardBg, borderColor: colors.border }]}>
              <Text style={styles.outdoorDistanceTitle}>MESAFE</Text>
              <Text style={[styles.outdoorDistanceVal, { color: colors.textPrimary }]}>
                {(locationStats?.distanceKm || 0).toFixed(2).replace('.', ',')}
                <Text style={[styles.outdoorDistanceUnit, { color: colors.textSecondary }]}> km</Text>
              </Text>
              <View style={[styles.cardDivider, { backgroundColor: colors.border }]} />
              <View style={styles.outdoorSplitRow}>
                <View style={styles.outdoorSplitCol}>
                  <Text style={[styles.outdoorSplitVal, { color: colors.textPrimary }]}>
                    {locationStats?.paceMinPerKm && locationStats.paceMinPerKm > 0.05 && locationStats.paceMinPerKm < 60
                      ? (60 / locationStats.paceMinPerKm).toFixed(1).replace('.', ',')
                      : '0,0'}
                  </Text>
                  <Text style={[styles.outdoorSplitLbl, { color: colors.textSecondary }]}>ORT. HIZ (km/s)</Text>
                </View>

                <View style={[styles.verticalDivider, { backgroundColor: colors.border }]} />

                <View style={styles.outdoorSplitCol}>
                  <Text style={[styles.outdoorSplitVal, { color: colors.textPrimary }]}>
                    {(locationStats?.currentSpeed || 0).toFixed(1).replace('.', ',')}
                  </Text>
                  <Text style={[styles.outdoorSplitLbl, { color: colors.textSecondary }]}>ANLIK HIZ (km/s)</Text>
                </View>
              </View>
            </View>

            {/* 3-Column Metrics Row */}
            <View style={styles.threeMetricsRow}>
              <View style={[styles.metricColumnCell, { backgroundColor: colors.cardBg, borderColor: colors.border }]}>
                <Text style={[styles.metricValText, { color: colors.textPrimary }]}>
                  +{Math.round(locationStats?.altitude || 0)}m
                </Text>
                <Text style={[styles.metricLblText, { color: colors.textSecondary }]}>YÜKSEKLİK</Text>
              </View>
              <View style={[styles.metricColumnCell, { backgroundColor: colors.cardBg, borderColor: colors.border }]}>
                <Text style={[styles.metricValText, { color: colors.textPrimary }]}>
                  {heartRate > 0 ? heartRate : '-'}
                </Text>
                <Text style={[styles.metricLblText, { color: colors.textSecondary }]}>BPM</Text>
              </View>
              <View style={[styles.metricColumnCell, { backgroundColor: colors.cardBg, borderColor: colors.border }]}>
                <Text style={[styles.metricValText, { color: colors.textPrimary }]}>
                  {activeCalories > 0 ? activeCalories : '-'}
                </Text>
                <Text style={[styles.metricLblText, { color: colors.textSecondary }]}>KCAL</Text>
              </View>
            </View>

            {renderProgramCard()}

            {/* Live Map Display Container */}
            {!hasStarted || currentLat == null || currentLon == null ? (
              <View style={[styles.gpsWaitingBox, { backgroundColor: colors.cardBg, borderColor: colors.border }]}>
                <Crosshair size={32} color={Colors.primary} style={{ marginBottom: 12 }} />
                <Text style={[styles.gpsWaitingTitle, { color: colors.textPrimary }]}>
                  {!hasStarted ? 'GPS takibi başlatılmayı bekliyor' : 'GPS konumu alınıyor...'}
                </Text>
                <Text style={[styles.gpsWaitingSub, { color: colors.textSecondary }]}>
                  Açık bir alanda durun
                </Text>
              </View>
            ) : (
              <View style={[styles.liveMapCardContainer, { borderColor: colors.border }]}>
                <MapView
                  ref={mapRef}
                  provider={PROVIDER_GOOGLE}
                  mapType="standard"
                  style={styles.mapViewStyle}
                  showsUserLocation={hasLocationPermission}
                  followsUserLocation={false}
                  showsMyLocationButton={false}
                  showsCompass={true}
                  customMapStyle={isDark ? DARK_MAP_STYLE : undefined}
                  onMapReady={() => setMapDiagnosticText('Harita Hazır')}
                  initialRegion={{
                    latitude: currentLat,
                    longitude: currentLon,
                    latitudeDelta: 0.006,
                    longitudeDelta: 0.006,
                  }}
                >
                  <Marker
                    coordinate={{
                      latitude: currentLat,
                      longitude: currentLon,
                    }}
                    title="Mevcut Konum"
                  />
                  {locationStats?.route && locationStats.route.length > 1 && (
                    <Polyline
                      coordinates={locationStats.route}
                      strokeColor={Colors.primary}
                      strokeWidth={5}
                    />
                  )}
                </MapView>

                {/* Recenter Camera Button */}
                <TouchableOpacity
                  style={[styles.mapRecenterBtn, { backgroundColor: colors.cardBg, borderColor: colors.border }]}
                  onPress={() => {
                    if (currentLat != null && currentLon != null && mapRef.current) {
                      mapRef.current.animateToRegion(
                        {
                          latitude: currentLat,
                          longitude: currentLon,
                          latitudeDelta: 0.006,
                          longitudeDelta: 0.006,
                        },
                        400
                      );
                    }
                  }}
                  activeOpacity={0.8}
                >
                  <Crosshair size={18} color={Colors.primary} />
                </TouchableOpacity>
              </View>
            )}
          </ScrollView>

          {/* Bottom Controls */}
          {!hasStarted ? (
            <View style={[styles.readyModalCardOverlay, { backgroundColor: colors.cardBg, bottom: Math.max(20, insets.bottom + 12) }]}>
              <Text style={[styles.readyModalTitle, { color: colors.textPrimary }]}>Hazır mısın?</Text>
              <Text style={[styles.readyModalSub, { color: colors.textSecondary }]}>
                Başlat'a bastığında süre ve GPS takibi başlar.
              </Text>
              <TouchableOpacity
                style={styles.outdoorStartPillBtn}
                onPress={handleStartOutdoorRun}
                activeOpacity={0.85}
              >
                <Text style={styles.outdoorStartPillBtnText}>Başlat</Text>
              </TouchableOpacity>
            </View>
          ) : !isActive ? (
            <View style={[styles.bottomDrawerSheet, { backgroundColor: colors.cardBg, bottom: Math.max(20, insets.bottom + 12), position: 'absolute', left: 16, right: 16 }]}>
              <View style={styles.drawerHandleBar} />
              <View style={{ gap: 12 }}>
                <TouchableOpacity
                  style={styles.drawerPillBtn}
                  onPress={handleResume}
                  activeOpacity={0.85}
                >
                  <Play size={20} color={Colors.allWhite} fill={Colors.allWhite} style={{ marginRight: 6 }} />
                  <Text style={styles.drawerPillBtnText}>Devam Et</Text>
                </TouchableOpacity>

                <TouchableOpacity
                  style={[styles.drawerPillBtn, { backgroundColor: colors.bg, borderWidth: 1, borderColor: colors.border }]}
                  onPress={handleRequestFinish}
                  activeOpacity={0.85}
                >
                  <Flag size={20} color={Colors.primary} style={{ marginRight: 6 }} />
                  <Text style={[styles.drawerPillBtnText, { color: Colors.primary }]}>Antrenmanı Bitir</Text>
                </TouchableOpacity>
              </View>
            </View>
          ) : (
            <View style={[styles.outdoorBottomControl, { bottom: Math.max(20, insets.bottom + 12) }]}>
              <TouchableOpacity
                style={[styles.playPauseLargeBtn, { backgroundColor: colors.pausePlayBg }]}
                onPress={handlePause}
                activeOpacity={0.85}
              >
                <Pause size={28} color={colors.pausePlayIcon} />
              </TouchableOpacity>
            </View>
          )}
        </View>
      ) : (
        /* ── INDOOR WORKOUT UI ── */
        <View style={styles.indoorContainer}>
          <View style={[styles.indoorHeader, { paddingTop: Math.max(16, insets.top + 8) }]}>
            <TouchableOpacity
              onPress={handleBackPress}
              style={[styles.iconCircleBtn, { backgroundColor: colors.iconBg, borderColor: colors.iconBorder }]}
            >
              <ChevronDown size={24} color={colors.textPrimary} />
            </TouchableOpacity>

            <View style={{ alignItems: 'center' }}>
              <Text style={[styles.indoorHeaderTitle, { color: colors.textPrimary }]}>
                {activityName}
              </Text>
              {programId && (
                <Text style={[styles.indoorHeaderSub, { color: colors.textSecondary }]}>
                  {selectedDay}. Gün Programı
                </Text>
              )}
            </View>

            <TouchableOpacity
              onPress={toggleTheme}
              style={[styles.iconCircleBtn, { backgroundColor: colors.iconBg, borderColor: colors.iconBorder }]}
              activeOpacity={0.7}
            >
              {isDark ? (
                <Sun size={20} color={colors.textPrimary} />
              ) : (
                <Moon size={20} color={colors.textPrimary} />
              )}
            </TouchableOpacity>
          </View>

          <ScrollView contentContainerStyle={styles.indoorScrollContent} showsVerticalScrollIndicator={false}>
            <View style={styles.centerTimerArea}>
              <Text style={[styles.hugeTimerHHMMSS, { color: colors.textPrimary }]}>
                {formatTimeStr(seconds, true)}
              </Text>
            </View>

            <View style={styles.centerHeartArea}>
              <Text style={[styles.heartRateText, { color: colors.textPrimary }]}>
                {heartRate > 0 ? `${heartRate} bpm` : '-'}
              </Text>
              <Heart size={28} color="#FF3B30" fill="#FF3B30" style={{ marginTop: 12 }} />
            </View>

            {renderProgramCard()}
          </ScrollView>

          <View style={[styles.bottomDrawerSheet, { backgroundColor: colors.cardBg, paddingBottom: Math.max(24, insets.bottom + 12) }]}>
            <View style={styles.drawerHandleBar} />

            {!isActive ? (
              <View style={{ gap: 12 }}>
                <TouchableOpacity
                  style={styles.drawerPillBtn}
                  onPress={handleResume}
                  activeOpacity={0.85}
                >
                  <Play size={20} color={Colors.allWhite} fill={Colors.allWhite} style={{ marginRight: 6 }} />
                  <Text style={styles.drawerPillBtnText}>Devam Et</Text>
                </TouchableOpacity>

                <TouchableOpacity
                  style={[styles.drawerPillBtn, { backgroundColor: colors.bg, borderWidth: 1, borderColor: colors.border }]}
                  onPress={handleRequestFinish}
                  activeOpacity={0.85}
                >
                  <Flag size={20} color={Colors.primary} style={{ marginRight: 6 }} />
                  <Text style={[styles.drawerPillBtnText, { color: Colors.primary }]}>Antrenmanı Bitir</Text>
                </TouchableOpacity>
              </View>
            ) : (
              <TouchableOpacity
                style={styles.drawerPillBtn}
                onPress={handlePause}
                activeOpacity={0.85}
              >
                <Pause size={20} color={Colors.allWhite} fill={Colors.allWhite} style={{ marginRight: 6 }} />
                <Text style={styles.drawerPillBtnText}>Duraklat</Text>
              </TouchableOpacity>
            )}
          </View>
        </View>
      )}

      {/* Effort Modal */}
      <SmoothModal
        visible={showEffortModal}
        onClose={() => setShowEffortModal(false)}
        variant="modal"
      >
        <View style={[styles.effortModalCard, { backgroundColor: colors.cardBg }]}>
          <Text style={[styles.effortModalTitle, { color: colors.textPrimary }]}>Nasıl Hissediyorsun?</Text>
          <Text style={[styles.effortModalSub, { color: colors.textSecondary }]}>
            Antrenmanı nasıl tamamladın? Hissettiğin zorluk derecesini seç:
          </Text>

          <View style={{ gap: 10, marginBottom: 20 }}>
            {EFFORT_OPTIONS.map((item) => (
              <TouchableOpacity
                key={item.id}
                style={[styles.effortOptionRow, { backgroundColor: colors.iconBg }]}
                onPress={() => handleEffortSelect(item)}
                activeOpacity={0.8}
              >
                <Text style={styles.effortEmojiText}>{item.emoji}</Text>
                <Text style={[styles.effortOptionLabel, { color: colors.textPrimary }]}>{item.label}</Text>
              </TouchableOpacity>
            ))}
          </View>

          <TouchableOpacity
            style={styles.effortCancelBtn}
            onPress={() => setShowEffortModal(false)}
          >
            <Text style={[styles.effortCancelBtnText, { color: colors.textSecondary }]}>İptal</Text>
          </TouchableOpacity>
        </View>
      </SmoothModal>

    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  indoorContainer: {
    flex: 1,
    justifyContent: 'space-between',
  },
  indoorHeader: {
    paddingHorizontal: 20,
    paddingTop: 50,
    paddingBottom: 10,
  },
  iconCircleBtn: {
    width: 40,
    height: 40,
    borderRadius: 20,
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 1,
  },
  centerTimerArea: {
    alignItems: 'center',
    marginTop: 20,
  },
  hugeTimerHHMMSS: {
    fontSize: 58,
    fontWeight: '900',
    letterSpacing: 2,
  },
  centerHeartArea: {
    alignItems: 'center',
    justifyContent: 'center',
    marginVertical: 40,
  },
  heartRateText: {
    fontSize: 48,
    fontWeight: '800',
    letterSpacing: 4,
  },
  bottomDrawerSheet: {
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    paddingHorizontal: 24,
    paddingTop: 12,
    paddingBottom: 34,
  },
  drawerHandleBar: {
    width: 36,
    height: 4,
    borderRadius: 2,
    backgroundColor: 'rgba(128,128,128,0.4)',
    alignSelf: 'center',
    marginBottom: 20,
  },
  drawerPillBtn: {
    height: 56,
    borderRadius: 100,
    backgroundColor: Colors.primary,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    elevation: 4,
  },
  drawerPillBtnText: {
    color: Colors.allWhite,
    fontSize: 18,
    fontWeight: '700',
  },
  outdoorContainer: {
    flex: 1,
  },
  outdoorHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingTop: 50,
    paddingBottom: 16,
  },
  outdoorTimerVal: {
    fontSize: 22,
    fontWeight: '800',
  },
  outdoorTimerLbl: {
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 1,
  },
  outdoorScrollContent: {
    paddingHorizontal: 20,
    paddingBottom: 160,
    gap: 16,
  },
  outdoorDistanceCard: {
    borderRadius: 24,
    padding: 24,
    alignItems: 'center',
    borderWidth: 1,
  },
  outdoorDistanceTitle: {
    fontSize: 11,
    fontWeight: '800',
    color: Colors.primary,
    letterSpacing: 1.5,
    marginBottom: 8,
  },
  outdoorDistanceVal: {
    fontSize: 56,
    fontWeight: '900',
  },
  outdoorDistanceUnit: {
    fontSize: 20,
    fontWeight: '600',
  },
  cardDivider: {
    height: 1,
    width: '100%',
    marginVertical: 16,
  },
  outdoorSplitRow: {
    flexDirection: 'row',
    width: '100%',
    justifyContent: 'space-around',
    alignItems: 'center',
  },
  outdoorSplitCol: {
    alignItems: 'center',
    flex: 1,
  },
  outdoorSplitVal: {
    fontSize: 20,
    fontWeight: '700',
  },
  outdoorSplitLbl: {
    fontSize: 11,
    fontWeight: '600',
    marginTop: 2,
  },
  verticalDivider: {
    width: 1,
    height: 30,
  },
  threeMetricsRow: {
    flexDirection: 'row',
    gap: 8,
  },
  metricColumnCell: {
    flex: 1,
    borderRadius: 16,
    paddingVertical: 16,
    alignItems: 'center',
    borderWidth: 1,
  },
  metricValText: {
    fontSize: 16,
    fontWeight: '700',
  },
  metricLblText: {
    fontSize: 10,
    fontWeight: '600',
    marginTop: 2,
  },
  gpsWaitingBox: {
    borderRadius: 24,
    padding: 30,
    alignItems: 'center',
    borderWidth: 1,
    marginVertical: 10,
  },
  gpsWaitingTitle: {
    fontSize: 15,
    fontWeight: '700',
  },
  gpsWaitingSub: {
    fontSize: 12,
    marginTop: 4,
  },
  liveMapCardContainer: {
    height: 220,
    borderRadius: 24,
    borderWidth: 1,
    backgroundColor: '#1E1E24',
    position: 'relative',
  },
  mapViewStyle: {
    width: '100%',
    height: '100%',
    borderRadius: 24,
  },
  mapRecenterBtn: {
    position: 'absolute',
    top: 10,
    right: 10,
    width: 36,
    height: 36,
    borderRadius: 18,
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 1,
    elevation: 4,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 3,
  },
  mapDiagBadge: {
    position: 'absolute',
    top: 8,
    left: 8,
    backgroundColor: 'rgba(0, 0, 0, 0.75)',
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 12,
  },
  mapDiagBadgeText: {
    color: '#FFFFFF',
    fontSize: 11,
    fontWeight: '700',
  },
  readyModalCardOverlay: {
    position: 'absolute',
    bottom: 30,
    left: 20,
    right: 20,
    borderRadius: 24,
    padding: 24,
    alignItems: 'center',
    elevation: 8,
  },
  readyModalTitle: {
    fontSize: 22,
    fontWeight: '800',
    marginBottom: 8,
    textAlign: 'center',
  },
  readyModalSub: {
    fontSize: 13,
    textAlign: 'center',
    marginBottom: 20,
    lineHeight: 18,
  },
  outdoorStartPillBtn: {
    width: '100%',
    height: 54,
    borderRadius: 100,
    backgroundColor: Colors.primary,
    justifyContent: 'center',
    alignItems: 'center',
    elevation: 4,
  },
  outdoorStartPillBtnText: {
    color: Colors.allWhite,
    fontSize: 18,
    fontWeight: '700',
  },
  outdoorBottomControl: {
    position: 'absolute',
    bottom: 30,
    alignSelf: 'center',
  },
  playPauseLargeBtn: {
    width: 68,
    height: 68,
    borderRadius: 34,
    justifyContent: 'center',
    alignItems: 'center',
    elevation: 6,
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.8)',
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 24,
  },
  confirmModalCard: {
    width: '100%',
    borderRadius: 24,
    padding: 24,
    elevation: 10,
  },
  confirmModalTitle: {
    fontSize: 20,
    fontWeight: '700',
    marginBottom: 8,
  },
  confirmModalSub: {
    fontSize: 14,
    marginBottom: 24,
  },
  confirmModalBtnRow: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: 16,
  },
  confirmModalCancelBtn: {
    paddingVertical: 10,
    paddingHorizontal: 16,
  },
  confirmModalCancelBtnText: {
    fontSize: 15,
    fontWeight: '600',
  },
  confirmModalActionBtn: {
    paddingVertical: 10,
    paddingHorizontal: 16,
  },
  confirmModalActionBtnText: {
    color: Colors.primary,
    fontSize: 15,
    fontWeight: '700',
  },
  effortModalCard: {
    width: '100%',
    borderRadius: 24,
    padding: 24,
    elevation: 10,
  },
  effortModalTitle: {
    fontSize: 20,
    fontWeight: '700',
    textAlign: 'center',
    marginBottom: 8,
  },
  effortModalSub: {
    fontSize: 13,
    textAlign: 'center',
    marginBottom: 20,
    lineHeight: 18,
  },
  effortOptionRow: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 14,
    paddingHorizontal: 16,
    paddingVertical: 14,
    gap: 14,
  },
  effortEmojiText: {
    fontSize: 24,
  },
  effortOptionLabel: {
    fontSize: 16,
    fontWeight: '600',
  },
  effortCancelBtn: {
    alignItems: 'center',
    paddingVertical: 12,
    marginTop: 6,
  },
  effortCancelBtnText: {
    fontSize: 15,
    fontWeight: '600',
  },
  indoorHeaderTitle: {
    fontSize: 16,
    fontWeight: '700',
  },
  indoorHeaderSub: {
    fontSize: 12,
    marginTop: 2,
  },
  indoorScrollContent: {
    paddingHorizontal: 20,
    paddingBottom: 24,
    gap: 16,
  },
  programCardBox: {
    borderRadius: 20,
    padding: 16,
    borderWidth: 1,
    marginTop: 8,
  },
  programCardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  programIconBadge: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: 'rgba(255, 96, 71, 0.15)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  programCardTitle: {
    fontSize: 16,
    fontWeight: '700',
  },
  programCardSub: {
    fontSize: 12,
    marginTop: 2,
  },
  expandChevronCircle: {
    width: 32,
    height: 32,
    borderRadius: 16,
    justifyContent: 'center',
    alignItems: 'center',
  },
  programCardContent: {
    marginTop: 4,
  },
  exerciseItemRow: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 14,
    paddingHorizontal: 14,
    paddingVertical: 12,
    gap: 12,
  },
  exerciseNumberText: {
    fontSize: 15,
    fontWeight: '700',
    color: Colors.primary,
    width: 22,
  },
  exerciseItemName: {
    fontSize: 15,
    fontWeight: '600',
  },
  exerciseItemDetail: {
    fontSize: 12,
    marginTop: 2,
  },
  checkCircle: {
    width: 22,
    height: 22,
    borderRadius: 11,
    borderWidth: 2,
    justifyContent: 'center',
    alignItems: 'center',
  },
  emptyExercisesText: {
    fontSize: 13,
    textAlign: 'center',
    marginVertical: 12,
  },
});
