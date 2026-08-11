import * as Location from 'expo-location';

export interface RouteCoordinate {
  latitude: number;
  longitude: number;
}

export interface LocationStats {
  distanceKm: number;
  currentSpeed: number; // km/h
  avgSpeed: number; // km/h
  paceMinPerKm: number; // min/km
  altitude: number; // m
  route: RouteCoordinate[];
  lastLatitude?: number;
  lastLongitude?: number;
}

export class LocationService {
  private static route: any[] = [];
  private static subscription: Location.LocationSubscription | null = null;
  private static startTime: number = 0;
  private static initialAltitude: number | null = null;

  static async requestPermissions(): Promise<boolean> {
    try {
      const { status } = await Location.requestForegroundPermissionsAsync();
      return status === 'granted';
    } catch (e) {
      console.warn('Location permission error:', e);
      return false;
    }
  }

  static async startTracking(
    onLocationUpdate: (stats: LocationStats) => void,
    isResume: boolean = false
  ): Promise<void> {
    const hasPermission = await this.requestPermissions();
    if (!hasPermission) {
      throw new Error('Konum izni verilmedi');
    }

    if (!isResume) {
      this.route = [];
      this.startTime = Date.now();
      this.initialAltitude = null;
    }

    // Clear previous subscription if any
    this.stopTracking();

    // Fetch initial location immediately
    try {
      const initialLoc = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.Balanced,
      });
      if (initialLoc && initialLoc.coords) {
        const stats = this.addRealCoordinate(
          initialLoc.coords.latitude,
          initialLoc.coords.longitude,
          initialLoc.coords.speed || 0,
          initialLoc.coords.altitude || 0
        );
        onLocationUpdate(stats);
      }
    } catch (e) {
      console.warn('Initial location error:', e);
    }

    this.subscription = await Location.watchPositionAsync(
      {
        accuracy: Location.Accuracy.High,
        timeInterval: 2000,
        distanceInterval: 3,
        showsBackgroundLocationIndicator: true,
        foregroundService: {
          notificationTitle: 'CoachUP Antrenman Takibi',
          notificationBody: 'Konumunuz ve rotanız arka planda takip ediliyor...',
          notificationColor: '#FF5E00',
        },
      },
      (position) => {
        const { latitude, longitude, speed, altitude } = position.coords;
        const stats = this.addRealCoordinate(
          latitude,
          longitude,
          speed || 0,
          altitude || 0
        );
        onLocationUpdate(stats);
      }
    );
  }

  static async resumeTracking(
    onLocationUpdate: (stats: LocationStats) => void
  ): Promise<void> {
    return this.startTracking(onLocationUpdate, true);
  }

  static addRealCoordinate(
    lat: number,
    lon: number,
    speed?: number,
    altitude?: number
  ): LocationStats {
    const point = {
      coords: {
        latitude: lat,
        longitude: lon,
        speed: speed || 0,
        altitude: altitude || 0,
      },
      timestamp: Date.now(),
    };

    if (altitude && altitude !== 0 && this.initialAltitude === null) {
      this.initialAltitude = altitude;
    }

    this.route.push(point);
    return this.calculateStats();
  }

  static stopTracking(): void {
    if (this.subscription) {
      this.subscription.remove();
      this.subscription = null;
    }
  }

  private static calculateStats(): LocationStats {
    if (this.route.length === 0) {
      return {
        distanceKm: 0,
        currentSpeed: 0,
        avgSpeed: 0,
        paceMinPerKm: 0,
        altitude: 0,
        route: [],
      };
    }

    const lastLoc = this.route[this.route.length - 1];
    let totalDistanceKm = 0;
    let cumulativeElevationGain = 0;

    for (let i = 1; i < this.route.length; i++) {
      totalDistanceKm += this.haversineDistance(
        this.route[i - 1].coords.latitude,
        this.route[i - 1].coords.longitude,
        this.route[i].coords.latitude,
        this.route[i].coords.longitude
      );

      const prevAlt = this.route[i - 1].coords.altitude;
      const currAlt = this.route[i].coords.altitude;
      if (currAlt && prevAlt && currAlt > prevAlt) {
        cumulativeElevationGain += currAlt - prevAlt;
      }
    }

    const timeElapsedSeconds = (Date.now() - this.startTime) / 1000;
    const avgSpeedKmh =
      totalDistanceKm > 0 && timeElapsedSeconds > 0
        ? totalDistanceKm / (timeElapsedSeconds / 3600)
        : 0;
    const paceMinPerKm =
      totalDistanceKm > 0 && timeElapsedSeconds > 0
        ? timeElapsedSeconds / 60 / totalDistanceKm
        : 0;

    const routeCoords = this.route.map((l) => ({
      latitude: l.coords.latitude,
      longitude: l.coords.longitude,
    }));

    // Elevation gain relative to start (+0, +1, +2, +3...)
    const elevationGainM =
      cumulativeElevationGain > 0
        ? cumulativeElevationGain
        : this.initialAltitude !== null && lastLoc.coords.altitude
        ? Math.max(0, lastLoc.coords.altitude - this.initialAltitude)
        : 0;

    return {
      distanceKm: totalDistanceKm,
      currentSpeed: lastLoc.coords.speed ? lastLoc.coords.speed * 3.6 : 0,
      avgSpeed: avgSpeedKmh,
      paceMinPerKm: paceMinPerKm,
      altitude: Math.round(elevationGainM),
      route: routeCoords,
      lastLatitude: lastLoc.coords.latitude,
      lastLongitude: lastLoc.coords.longitude,
    };
  }

  private static haversineDistance(
    lat1: number,
    lon1: number,
    lat2: number,
    lon2: number
  ): number {
    const toRad = (x: number) => (x * Math.PI) / 180;
    const R = 6371; // km
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
