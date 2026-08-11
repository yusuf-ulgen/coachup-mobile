import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
} from 'react-native';
import { SmoothModal } from '../../components/motion/SmoothModal';
import MapView, { Marker, Polyline, UrlTile, PROVIDER_GOOGLE } from 'react-native-maps';
import { useFocusEffect } from '@react-navigation/native';
import { Colors } from '../../theme/colors';
import { useTheme } from '../../theme/ThemeContext';
import {
  ChevronDown,
  Pause,
  Play,
  Flame,
  Heart,
  Flag,
  Sun,
  Moon,
  Crosshair,
} from 'lucide-react-native';
import { HealthConnectService } from '../../services/healthConnectService';
import { LocationService, LocationStats } from '../../services/locationService';
import { ActiveWorkoutManager } from '../../services/activeWorkoutManager';
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
  const title = route.params?.title || existingManager.title;
  const workoutTitle = route.params?.workoutTitle || existingManager.workoutTitle || title;
  const category = route.params?.category || existingManager.category || '';
  const emoji = route.params?.emoji || existingManager.emoji || '🏃';

  const activityName = workoutTitle || title || 'Koşu';
  const activityEmoji = emoji || '🏃';
  const lowerCat = (category || '').toLowerCase();
  const lowerTitle = activityName.toLowerCase();

  const isOutdoor =
    ['running', 'walking', 'cycling', 'hyrox', 'swimming', 'koşu', 'yürüyüş', 'bisiklet', 'hyrox', 'yüzme'].includes(lowerCat) ||
    ['koşu', 'yürüyüş', 'bisiklet', 'hyrox', 'yüzme'].some((kw) => lowerTitle.includes(kw));

  // Check ongoing session state from ActiveWorkoutManager
  const isAlreadyRunning = Boolean(
    existingManager.sessionId &&
    existingManager.isActive &&
    (existingManager.sessionId === sessionId || !sessionId)
  );

  // Outdoor flow state
  const [hasStarted, setHasStarted] = useState(() => (isAlreadyRunning ? existingManager.hasStarted ?? true : !isOutdoor));
  const [seconds, setSeconds] = useState(() => ActiveWorkoutManager.getState().seconds);
  const [isActive, setIsActive] = useState(() => (isAlreadyRunning ? true : !isOutdoor));
  const [heartRate, setHeartRate] = useState(0);
  const [activeCalories, setActiveCalories] = useState(0);
  const [locationStats, setLocationStats] = useState<LocationStats | null>(null);
  const [hasLocationPermission, setHasLocationPermission] = useState(false);

  // Modals & Map diagnostics
  const [showConfirmFinishModal, setShowConfirmFinishModal] = useState(false);
  const [showEffortModal, setShowEffortModal] = useState(false);
  const [showHealthPermissionModal, setShowHealthPermissionModal] = useState(false);
  const [mapDiagnosticText, setMapDiagnosticText] = useState('Harita başlatılıyor...');

  useEffect(() => {
    HealthConnectService.checkPermissions().then((hasPerms) => {
      if (!hasPerms) {
        setShowHealthPermissionModal(true);
      }
    });
  }, []);

  const handleSelectAlwaysAllow = async () => {
    setShowHealthPermissionModal(false);
    await HealthConnectService.startBleSmartWatchConnection();
  };

  const handleSelectAllowOnce = async () => {
    setShowHealthPermissionModal(false);
    await HealthConnectService.startBleSmartWatchConnection();
  };

  const handleSelectNoPermission = () => {
    setShowHealthPermissionModal(false);
    HealthConnectService.selectNoPermission();
  };

  useEffect(() => {
    if (isOutdoor) {
      LocationService.requestPermissions().then((granted) => {
        setHasLocationPermission(granted);
      });
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
    const hasPerms = await HealthConnectService.checkPermissions();
    if (!hasPerms) {
      setShowHealthPermissionModal(true);
    }
    setHasStarted(true);
    setIsActive(true);
    ActiveWorkoutManager.setHasStarted(true);
  };

  // Main Timer & GPS & Heart Rate Interval (Synchronized with Manager)
  useEffect(() => {
    let timerInterval: any = null;
    let hrInterval: any = null;

    if (isActive && hasStarted) {
      timerInterval = setInterval(() => {
        const mgrSeconds = ActiveWorkoutManager.getState().seconds;
        setSeconds(mgrSeconds);
      }, 1000);

      if (isOutdoor) {
        LocationService.startTracking((stats) => {
          setLocationStats(stats);
        }).catch((e) => console.error('Location tracking error:', e));
      }

      hrInterval = setInterval(async () => {
        const hr = await HealthConnectService.getLiveHeartRate();
        setHeartRate(hr);
      }, 2000);
    } else {
      clearInterval(timerInterval);
      clearInterval(hrInterval);
      if (isOutdoor) {
        LocationService.stopTracking();
      }
    }

    return () => {
      clearInterval(timerInterval);
      clearInterval(hrInterval);
      LocationService.stopTracking();
    };
  }, [isActive, hasStarted, isOutdoor]);

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

  const handleEffortSelect = (effortObj: typeof EFFORT_OPTIONS[0]) => {
    setShowEffortModal(false);
    ActiveWorkoutManager.finishWorkout();
    LocationService.stopTracking();

    const finalDistance = locationStats?.distanceKm || 0;
    const finalPace = locationStats?.paceMinPerKm || 0;
    const finalSpeed = locationStats?.currentSpeed || (finalPace > 0 ? 60 / finalPace : 0);

    navigation.navigate('WorkoutSummary', {
      training: { title: activityName, category: { emoji: activityEmoji } },
      durationSeconds: seconds,
      calories: activeCalories,
      avgHeartRate: heartRate > 0 ? heartRate : null,
      maxHeartRate: heartRate > 0 ? heartRate + 10 : null,
      distanceKm: finalDistance,
      avgPaceMinPerKm: finalPace,
      avgSpeedKmh: finalSpeed,
      perceivedEffort: effortObj.label,
      perceivedEmoji: effortObj.emoji,
    });
  };

  const currentLat = locationStats?.lastLatitude || 41.0082;
  const currentLon = locationStats?.lastLongitude || 28.9784;

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

            {/* Live Map Display Container with OpenStreetMap UrlTile fallback */}
            {!hasStarted ? (
              <View style={[styles.gpsWaitingBox, { backgroundColor: colors.cardBg, borderColor: colors.border }]}>
                <Crosshair size={32} color={Colors.primary} style={{ marginBottom: 12 }} />
                <Text style={[styles.gpsWaitingTitle, { color: colors.textPrimary }]}>GPS konumu bekleniyor...</Text>
                <Text style={[styles.gpsWaitingSub, { color: colors.textSecondary }]}>Açık bir alanda durun</Text>
              </View>
            ) : (
              <View style={[styles.liveMapCardContainer, { borderColor: colors.border }]}>
                <MapView
                  provider={PROVIDER_GOOGLE}
                  mapType="standard"
                  style={styles.mapViewStyle}
                  showsUserLocation={hasLocationPermission}
                  followsUserLocation={hasLocationPermission}
                  showsMyLocationButton={hasLocationPermission}
                  showsCompass={true}
                  customMapStyle={isDark ? DARK_MAP_STYLE : undefined}
                  onMapReady={() => setMapDiagnosticText('Harita Hazır')}
                  initialRegion={{
                    latitude: currentLat,
                    longitude: currentLon,
                    latitudeDelta: 0.008,
                    longitudeDelta: 0.008,
                  }}
                  region={{
                    latitude: currentLat,
                    longitude: currentLon,
                    latitudeDelta: 0.008,
                    longitudeDelta: 0.008,
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
                  onPress={() => setIsActive(true)}
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
                onPress={() => setIsActive(false)}
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
              style={[styles.iconCircleBtn, { backgroundColor: colors.iconBg }]}
            >
              <ChevronDown size={24} color={colors.textPrimary} />
            </TouchableOpacity>
          </View>

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

          <View style={[styles.bottomDrawerSheet, { backgroundColor: colors.cardBg, paddingBottom: Math.max(24, insets.bottom + 12) }]}>
            <View style={styles.drawerHandleBar} />

            {!isActive ? (
              <View style={{ gap: 12 }}>
                <TouchableOpacity
                  style={styles.drawerPillBtn}
                  onPress={() => setIsActive(true)}
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
                onPress={() => setIsActive(false)}
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

      {/* Health & Wearable Permission Modal */}
      <HealthPermissionModal
        visible={showHealthPermissionModal}
        onDismiss={() => setShowHealthPermissionModal(false)}
        onSelectAlwaysAllow={handleSelectAlwaysAllow}
        onSelectAllowOnce={handleSelectAllowOnce}
        onSelectNoPermission={handleSelectNoPermission}
      />
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
});
