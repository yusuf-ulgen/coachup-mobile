export interface RouteCoordinate {
  latitude: number;
  longitude: number;
}

export interface LocationStats {
  distanceKm: number;
  currentSpeed: number; // m/s
  avgSpeed: number; // m/s
  paceMinPerKm: number; // min/km
  altitude: number; // m
  route: RouteCoordinate[];
}

export class LocationService {
  private static route: any[] = [];
  private static locationSubscription: any = null;
  private static startTime: number = 0;

  static async requestPermissions(): Promise<boolean> {
    return true;
  }

  static async startTracking(
    onLocationUpdate: (stats: LocationStats) => void
  ): Promise<void> {
    const hasPermission = await this.requestPermissions();
    if (!hasPermission) {
      throw new Error('Konum izni verilmedi');
    }

    this.route = [];
    this.startTime = Date.now();

    const interval = setInterval(() => {
      const mockLocation = {
        coords: {
          latitude: 41.0082 + (Math.random() - 0.5) * 0.001,
          longitude: 28.9784 + (Math.random() - 0.5) * 0.001,
          speed: 2.5,
          altitude: 50,
        },
        timestamp: Date.now(),
      };
      this.route.push(mockLocation);
      const stats = this.calculateStats();
      onLocationUpdate(stats);
    }, 2000);

    this.locationSubscription = { remove: () => clearInterval(interval) };
  }

  static stopTracking(): void {
    if (this.locationSubscription) {
      this.locationSubscription.remove();
      this.locationSubscription = null;
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

    for (let i = 1; i < this.route.length; i++) {
      totalDistanceKm += this.haversineDistance(
        this.route[i - 1].coords.latitude,
        this.route[i - 1].coords.longitude,
        this.route[i].coords.latitude,
        this.route[i].coords.longitude
      );
    }

    const timeElapsedSeconds = (Date.now() - this.startTime) / 1000;
    const avgSpeed = totalDistanceKm > 0 ? (totalDistanceKm * 1000) / timeElapsedSeconds : 0;
    
    // pace is minutes per km
    const paceMinPerKm = totalDistanceKm > 0 ? (timeElapsedSeconds / 60) / totalDistanceKm : 0;
    
    const routeCoords = this.route.map(l => ({
      latitude: l.coords.latitude,
      longitude: l.coords.longitude,
    }));

    return {
      distanceKm: totalDistanceKm,
      currentSpeed: lastLoc.coords.speed || 0,
      avgSpeed: avgSpeed,
      paceMinPerKm: paceMinPerKm,
      altitude: lastLoc.coords.altitude || 0,
      route: routeCoords,
    };
  }

  private static haversineDistance(lat1: number, lon1: number, lat2: number, lon2: number): number {
    const toRad = (x: number) => (x * Math.PI) / 180;

    const R = 6371; // km
    const dLat = toRad(lat2 - lat1);
    const dLon = toRad(lon2 - lon1);
    const a =
      Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) *
      Math.sin(dLon / 2) * Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
  }
}
