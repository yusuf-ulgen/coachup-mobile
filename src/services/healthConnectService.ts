import { NativeModules, Platform } from 'react-native';

const { HealthConnectModule } = NativeModules;

export class HealthConnectService {
  private static isConnected: boolean | null = null;

  static async isAvailable(): Promise<boolean> {
    if (Platform.OS !== 'android' || !HealthConnectModule) return false;
    try {
      return await HealthConnectModule.isAvailable();
    } catch (_: any) {
      return false;
    }
  }

  static async checkPermissions(): Promise<boolean> {
    if (Platform.OS !== 'android' || !HealthConnectModule) {
      return this.isConnected === true;
    }
    try {
      const hasAll = await HealthConnectModule.hasPermissions();
      this.isConnected = hasAll;
      return hasAll;
    } catch (_: any) {
      return false;
    }
  }

  /**
   * Android Health Connect sistem izin aktivitesini açar.
   */
  static async openSystemPermissions(): Promise<boolean> {
    if (Platform.OS !== 'android' || !HealthConnectModule) {
      this.isConnected = true;
      return true;
    }
    try {
      const result = await HealthConnectModule.openHealthConnectPermissions();
      const hasAll = await HealthConnectModule.hasPermissions();
      this.isConnected = hasAll;
      return result;
    } catch (_: any) {
      return false;
    }
  }

  /**
   * Sistemdeki Health Connect verisinden anlık nabzı okur. Veri yoksa 0 döner.
   */
  static async getLiveHeartRate(): Promise<number> {
    if (Platform.OS !== 'android' || !HealthConnectModule) {
      return 0;
    }
    try {
      const hr = await HealthConnectModule.getLiveHeartRate();
      return hr > 0 ? hr : 0;
    } catch (_: any) {
      return 0;
    }
  }

  /**
   * Sistemdeki Health Connect verisinden yakılan aktif ve toplam kaloriyi çeker.
   */
  static async fetchCaloriesBurned(startTimeMs: number, endTimeMs: number): Promise<number> {
    if (Platform.OS !== 'android' || !HealthConnectModule) {
      return 0;
    }
    try {
      const calories = await HealthConnectModule.fetchCaloriesBurned(startTimeMs, endTimeMs);
      return calories > 0 ? calories : 0;
    } catch (_: any) {
      return 0;
    }
  }

  static calculateActiveCalories(
    durationSeconds: number,
    averageHeartRate: number,
    weightKg: number = 70,
    age: number = 25,
    isMale: boolean = true
  ): number {
    if (durationSeconds <= 0 || averageHeartRate <= 0) return 0;
    const durationMinutes = durationSeconds / 60;
    let calories = 0;
    if (isMale) {
      calories = ((age * 0.2017) - (weightKg * 0.09036) + (averageHeartRate * 0.6309) - 55.0969) * durationMinutes / 4.184;
    } else {
      calories = ((age * 0.074) - (weightKg * 0.05741) + (averageHeartRate * 0.4472) - 20.4022) * durationMinutes / 4.184;
    }
    return Math.max(0, Math.ceil(calories));
  }

  // Nabız bölgeleri (HR Zones: Isınma, Yağ Yakımı, Kardiyo, Ekstrem)
  static calculateHRZone(heartRate: number, maxHeartRate: number = 195): string {
    if (heartRate <= 0) return "-";
    const percentage = (heartRate / maxHeartRate) * 100;

    if (percentage < 60) {
      return "Isınma";
    } else if (percentage < 70) {
      return "Yağ Yakımı";
    } else if (percentage < 80) {
      return "Kardiyo";
    } else {
      return "Ekstrem";
    }
  }

  static resetPermissions() {
    this.isConnected = null;
  }
}
