import * as TaskManager from 'expo-task-manager';
import * as Location from 'expo-location';
import { Platform, PermissionsAndroid } from 'react-native';

export const COACHUP_LOCATION_TASK = 'COACHUP_WORKOUT_LOCATION_TASK';

export interface RouteCoordinate {
  latitude: number;
  longitude: number;
}

export interface KmSplit {
  km: number;
  paceMinPerKm: number;
}

export interface LocationStats {
  distanceKm: number;
  currentSpeed: number; // km/h
  avgSpeed: number; // km/h
  paceMinPerKm: number; // min/km
  altitude: number; // m elevation gain
  route: RouteCoordinate[];
  lastLatitude?: number;
  lastLongitude?: number;
  isGPSSignalWeak?: boolean;
  splits?: KmSplit[];
  isTracking: boolean;
  isPaused: boolean;
}

// ── Quality Thresholds ─────────────────────────────────────────────────────────
const ACCURACY_THRESHOLD_METERS = 35; // Reject GPS fixes worse than 35m accuracy
const WEAK_SIGNAL_ACCURACY_METERS = 20; // Flag signal as weak if accuracy > 20m
const MAX_DELTA_METERS = 200; // Reject single-tick jumps > 200m
const MAX_REASONABLE_SPEED_MS = 60; // Reject speed > 216 km/h (GPS jump artifact)
const MIN_MOVEMENT_DELTA_METERS = 1.2; // Ignore GPS jitter when stationary
const MIN_MOVEMENT_SPEED_MS = 0.4; // ~1.4 km/h

// ── Global Module-level TaskManager Task Registration ───────────────────────────
TaskManager.defineTask(COACHUP_LOCATION_TASK, async ({ data, error }) => {
  if (error) {
    console.error('[LocationTask] Background location error:', error);
    return;
  }
  if (data) {
    const { locations } = data as { locations?: Location.LocationObject[] };
    if (locations && locations.length > 0) {
      LocationService.processLocations(locations);
    }
  }
});

interface InternalTrackingState {
  isTracking: boolean;
  isPaused: boolean;
  startTime: number;
  totalPausedMs: number;
  pauseStartMs: number;
  totalDistanceM: number;
  lastSplitDistanceM: number;
  lastLocation: {
    latitude: number;
    longitude: number;
    altitude?: number | null;
    speed?: number | null;
    accuracy?: number | null;
    timestamp: number;
  } | null;
  lastAltitude: number | null;
  altitudeGainM: number;
  currentSpeedKmh: number;
  avgSpeedKmh: number;
  paceMinPerKm: number;
  isGPSSignalWeak: boolean;
  route: RouteCoordinate[];
  splits: KmSplit[];
  lowSpeedCount: number;
  autoPauseEnabled: boolean;
}

type LocationListener = (stats: LocationStats) => void;

export class LocationService {
  private static listeners = new Set<LocationListener>();

  private static state: InternalTrackingState = {
    isTracking: false,
    isPaused: false,
    startTime: 0,
    totalPausedMs: 0,
    pauseStartMs: 0,
    totalDistanceM: 0,
    lastSplitDistanceM: 0,
    lastLocation: null,
    lastAltitude: null,
    altitudeGainM: 0,
    currentSpeedKmh: 0,
    avgSpeedKmh: 0,
    paceMinPerKm: 0,
    isGPSSignalWeak: false,
    route: [],
    splits: [],
    lowSpeedCount: 0,
    autoPauseEnabled: false,
  };

  /** Subscribe to live location/workout stats. Returns unsubscribe function. */
  static subscribe(listener: LocationListener): () => void {
    this.listeners.add(listener);
    // Send immediate current stats
    try {
      listener(this.getStats());
    } catch (e) {
      console.warn('[LocationService] Listener initial callback error:', e);
    }
    return () => {
      this.listeners.delete(listener);
    };
  }

  private static notifyListeners(): void {
    const stats = this.getStats();
    this.listeners.forEach((listener) => {
      try {
        listener(stats);
      } catch (err) {
        console.error('[LocationService] Error in listener callback:', err);
      }
    });
  }

  /**
   * Complete permission check & request flow.
   * NEVER returns true if required location permission or GPS service is not available.
   */
  static async requestPermissions(): Promise<boolean> {
    try {
      // 1. Check if device location services (GPS) are enabled
      const servicesEnabled = await Location.hasServicesEnabledAsync();
      if (!servicesEnabled) {
        console.warn('[LocationService] Device location services are turned off');
        return false;
      }

      // 2. Request Foreground Location Permission
      const { status: fgStatus } = await Location.requestForegroundPermissionsAsync();
      if (fgStatus !== 'granted') {
        console.warn('[LocationService] Foreground location permission denied:', fgStatus);
        return false;
      }

      // 3. Request Background Location Permission
      try {
        const { status: bgStatus } = await Location.requestBackgroundPermissionsAsync();
        console.log('[LocationService] Background location permission status:', bgStatus);
      } catch (bgErr) {
        console.warn('[LocationService] Background location permission warning:', bgErr);
      }

      // 4. Request Android 13+ (API 33+) POST_NOTIFICATIONS runtime permission
      if (Platform.OS === 'android' && Platform.Version >= 33) {
        try {
          const notifGranted = await PermissionsAndroid.request(
            PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS
          );
          console.log('[LocationService] POST_NOTIFICATIONS status:', notifGranted);
        } catch (notifErr) {
          console.warn('[LocationService] Notification permission request error:', notifErr);
        }
      }

      return true;
    } catch (e) {
      console.error('[LocationService] requestPermissions error:', e);
      return false;
    }
  }

  /**
   * Starts GPS tracking using Expo TaskManager background updates + Android Foreground Service.
   */
  static async startTracking(options?: {
    onUpdate?: LocationListener;
    isResume?: boolean;
    enableAutoPause?: boolean;
  }): Promise<LocationStats> {
    const hasPermission = await this.requestPermissions();
    if (!hasPermission) {
      throw new Error('Konum izni verilmedi veya GPS servisi kapalı');
    }

    if (options?.onUpdate) {
      this.subscribe(options.onUpdate);
    }

    const isResume = Boolean(options?.isResume);

    if (!isResume) {
      // New workout session
      this.resetTrackingState();
      this.state.startTime = Date.now();
      this.state.isTracking = true;
      this.state.isPaused = false;
      this.state.autoPauseEnabled = options?.enableAutoPause ?? false;
    } else {
      // Resuming existing session
      this.state.isTracking = true;
      if (this.state.isPaused) {
        this.state.totalPausedMs += Math.max(0, Date.now() - this.state.pauseStartMs);
        this.state.isPaused = false;
      }
      this.state.lastLocation = null; // Clear last location so no distance jump on resume
    }

    // Acquire fresh high-accuracy GPS fix for start position
    try {
      const freshPosition = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.High,
      });
      if (freshPosition && freshPosition.coords) {
        this.processSingleLocation(freshPosition);
      }
    } catch (e) {
      console.warn('[LocationService] Fresh location acquisition warning:', e);
    }

    // Start background location updates with Foreground Service
    const isTaskRunning = await Location.hasStartedLocationUpdatesAsync(COACHUP_LOCATION_TASK).catch(
      () => false
    );

    if (!isTaskRunning) {
      await Location.startLocationUpdatesAsync(COACHUP_LOCATION_TASK, {
        accuracy: Location.Accuracy.BestForNavigation,
        timeInterval: 1000,
        distanceInterval: 1,
        deferredUpdatesInterval: 1000,
        deferredUpdatesDistance: 1,
        showsBackgroundLocationIndicator: true,
        pausesUpdatesAutomatically: false,
        activityType: Location.ActivityType.Fitness,
        foregroundService: {
          notificationTitle: 'CoachUp — Antrenman Aktif',
          notificationBody: 'GPS konum takibi devam ediyor...',
          notificationColor: '#FF5E00',
          killServiceOnDestroy: false,
        },
      });
    }

    this.notifyListeners();
    return this.getStats();
  }

  /**
   * Resumes GPS tracking from a paused state.
   */
  static async resumeTracking(onUpdate?: LocationListener): Promise<void> {
    if (!this.state.isTracking) {
      await this.startTracking({ onUpdate, isResume: true });
      return;
    }

    if (this.state.isPaused) {
      this.state.totalPausedMs += Math.max(0, Date.now() - this.state.pauseStartMs);
      this.state.isPaused = false;
      this.state.lastLocation = null;
      this.notifyListeners();
    }
  }

  /**
   * Pauses GPS tracking without tearing down the foreground service or resetting stats.
   */
  static pauseTracking(manual: boolean = true): void {
    if (!this.state.isTracking || this.state.isPaused) return;
    this.state.isPaused = true;
    this.state.pauseStartMs = Date.now();
    this.state.lastLocation = null;
    this.notifyListeners();
  }

  /**
   * Stops the background TaskManager location updates and removes foreground notification.
   */
  static async stopTracking(): Promise<void> {
    try {
      const isRunning = await Location.hasStartedLocationUpdatesAsync(COACHUP_LOCATION_TASK).catch(
        () => false
      );
      if (isRunning) {
        await Location.stopLocationUpdatesAsync(COACHUP_LOCATION_TASK);
      }
    } catch (e) {
      console.warn('[LocationService] Error stopping TaskManager updates:', e);
    }
    this.state.isTracking = false;
    this.state.isPaused = false;
    this.notifyListeners();
  }

  /**
   * Resets all in-memory workout tracking state and stops updates.
   */
  static async resetSession(): Promise<void> {
    await this.stopTracking();
    this.resetTrackingState();
    this.notifyListeners();
  }

  private static resetTrackingState(): void {
    this.state = {
      isTracking: false,
      isPaused: false,
      startTime: 0,
      totalPausedMs: 0,
      pauseStartMs: 0,
      totalDistanceM: 0,
      lastSplitDistanceM: 0,
      lastLocation: null,
      lastAltitude: null,
      altitudeGainM: 0,
      currentSpeedKmh: 0,
      avgSpeedKmh: 0,
      paceMinPerKm: 0,
      isGPSSignalWeak: false,
      route: [],
      splits: [],
      lowSpeedCount: 0,
      autoPauseEnabled: false,
    };
  }

  /**
   * Processes a batch of locations received from the background TaskManager.
   */
  static processLocations(locations: Location.LocationObject[]): void {
    if (!locations || locations.length === 0) return;
    for (const loc of locations) {
      this.processSingleLocation(loc);
    }
  }

  /**
   * Core GPS filtering and metric calculation algorithm.
   */
  private static processSingleLocation(loc: Location.LocationObject): void {
    if (!this.state.isTracking) return;

    const { latitude, longitude, altitude, speed, accuracy } = loc.coords;
    const timestamp = loc.timestamp || Date.now();

    // 1. Accuracy assessment
    const isWeak = typeof accuracy === 'number' && accuracy > WEAK_SIGNAL_ACCURACY_METERS;
    this.state.isGPSSignalWeak = isWeak;

    // Reject inaccurate positions
    if (typeof accuracy === 'number' && accuracy > ACCURACY_THRESHOLD_METERS) {
      this.notifyListeners();
      return;
    }

    const prev = this.state.lastLocation;

    if (prev && !this.state.isPaused) {
      const deltaMeters = this.haversineDistance(
        prev.latitude,
        prev.longitude,
        latitude,
        longitude
      );

      const timeDeltaSec = Math.max(0.5, (timestamp - prev.timestamp) / 1000);
      const computedSpeedMs = deltaMeters / timeDeltaSec;

      // Reject impossible jumps (> 200m or speed > 60m/s = 216km/h)
      if (deltaMeters > MAX_DELTA_METERS || computedSpeedMs > MAX_REASONABLE_SPEED_MS) {
        this.state.lastLocation = {
          latitude,
          longitude,
          altitude,
          speed,
          accuracy,
          timestamp,
        };
        return;
      }

      // Filter stationary GPS jitter
      const speedMs = typeof speed === 'number' && speed >= 0 ? speed : computedSpeedMs;
      const isStationary =
        deltaMeters < MIN_MOVEMENT_DELTA_METERS && speedMs < MIN_MOVEMENT_SPEED_MS;

      if (!isStationary && deltaMeters > 0) {
        this.state.totalDistanceM += deltaMeters;
      }
    }

    this.state.lastLocation = {
      latitude,
      longitude,
      altitude,
      speed,
      accuracy,
      timestamp,
    };

    // Route polyline points
    if (!this.state.isPaused || this.state.route.length === 0) {
      this.state.route.push({ latitude, longitude });
    }

    // Speed & Pace calculation
    const speedMs = typeof speed === 'number' && speed >= 0 ? speed : 0;
    const currentSpeedKmh = speedMs * 3.6;
    this.state.currentSpeedKmh = currentSpeedKmh;

    const elapsedMs = Math.max(
      1000,
      Date.now() - this.state.startTime - this.state.totalPausedMs
    );
    const totalDistanceKm = this.state.totalDistanceM / 1000.0;
    const avgSpeedKmh = totalDistanceKm > 0 ? totalDistanceKm / (elapsedMs / 3600000.0) : 0;
    this.state.avgSpeedKmh = avgSpeedKmh;
    this.state.paceMinPerKm = avgSpeedKmh > 0 ? 60.0 / avgSpeedKmh : 0;

    // Altitude calculation
    if (typeof altitude === 'number') {
      if (this.state.lastAltitude !== null && altitude > this.state.lastAltitude) {
        this.state.altitudeGainM += altitude - this.state.lastAltitude;
      }
      this.state.lastAltitude = altitude;
    }

    // Splits calculation (every 1000m)
    const currentKm = Math.floor(totalDistanceKm);
    if (currentKm > 0 && this.state.totalDistanceM - this.state.lastSplitDistanceM >= 1000.0) {
      const splitElapsedMs = Date.now() - this.state.startTime - this.state.totalPausedMs;
      const splitDistKm = (this.state.totalDistanceM - this.state.lastSplitDistanceM) / 1000.0;
      const splitPace = splitDistKm > 0 ? (splitElapsedMs / 60000.0) / splitDistKm : 0;
      this.state.splits.push({
        km: this.state.splits.length + 1,
        paceMinPerKm: splitPace,
      });
      this.state.lastSplitDistanceM = this.state.totalDistanceM;
    }

    this.notifyListeners();
  }

  /**
   * Compatibility method for manual coordinate injection if needed.
   */
  static addRealCoordinate(
    lat: number,
    lon: number,
    speed?: number,
    altitude?: number
  ): LocationStats {
    this.processSingleLocation({
      coords: {
        latitude: lat,
        longitude: lon,
        altitude: altitude ?? null,
        accuracy: 5,
        altitudeAccuracy: 5,
        heading: 0,
        speed: speed ?? 0,
      },
      timestamp: Date.now(),
    });
    return this.getStats();
  }

  /**
   * Returns a snapshot of current location and workout statistics.
   */
  static getStats(): LocationStats {
    const lastLoc = this.state.lastLocation;
    return {
      distanceKm: this.state.totalDistanceM / 1000.0,
      currentSpeed: this.state.currentSpeedKmh,
      avgSpeed: this.state.avgSpeedKmh,
      paceMinPerKm: this.state.paceMinPerKm,
      altitude: Math.round(this.state.altitudeGainM),
      route: [...this.state.route],
      lastLatitude: lastLoc?.latitude,
      lastLongitude: lastLoc?.longitude,
      isGPSSignalWeak: this.state.isGPSSignalWeak,
      splits: [...this.state.splits],
      isTracking: this.state.isTracking,
      isPaused: this.state.isPaused,
    };
  }

  /**
   * Haversine formula to compute distance between two coordinates in meters.
   */
  static haversineDistance(
    lat1: number,
    lon1: number,
    lat2: number,
    lon2: number
  ): number {
    const toRad = (x: number) => (x * Math.PI) / 180;
    const R = 6371000; // Radius of Earth in meters
    const dLat = toRad(lat2 - lat1);
    const dLon = toRad(lon2 - lon1);
    const a =
      Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos(toRad(lat1)) *
        Math.cos(toRad(lat2)) *
        Math.sin(dLon / 2) *
        Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
  }
}

/**
 * Helper to identify if a workout category/title requires outdoor GPS tracking.
 * Covers Running (Koşu), Walking (Yürüyüş), Cycling (Bisiklet), Swimming (Yüzme), HYROX.
 */
export function isOutdoorWorkout(category?: string, title?: string): boolean {
  const cat = (category || '').toLowerCase().trim();
  const t = (title || '').toLowerCase().trim();

  const outdoorKeys = [
    'running',
    'walking',
    'cycling',
    'swimming',
    'hyrox',
    'koşu',
    'kosu',
    'yürüyüş',
    'yuruyus',
    'bisiklet',
    'yüzme',
    'yuzme',
  ];

  if (outdoorKeys.includes(cat)) return true;
  return outdoorKeys.some((k) => t.includes(k) || cat.includes(k));
}
