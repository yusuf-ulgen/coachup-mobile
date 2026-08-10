import { NativeModules, Platform } from 'react-native';

const { HealthConnectModule, BleHeartRateModule } = NativeModules;

export type TrackingMode = 'ble' | 'health_connect' | 'none';

export class HealthConnectService {
  private static activeMode: TrackingMode = 'none';

  static getActiveMode(): TrackingMode {
    return this.activeMode;
  }

  static setActiveMode(mode: TrackingMode) {
    this.activeMode = mode;
  }

  static async isAvailable(): Promise<boolean> {
    if (Platform.OS !== 'android' || !HealthConnectModule) return false;
    try {
      return await HealthConnectModule.isAvailable();
    } catch (_: any) {
      return false;
    }
  }

  static async checkPermissions(): Promise<boolean> {
    if (this.activeMode === 'ble') {
      if (BleHeartRateModule) {
        return await BleHeartRateModule.hasPermissions();
      }
      return false;
    }
    if (this.activeMode === 'health_connect') {
      if (HealthConnectModule) {
        return await HealthConnectModule.hasPermissions();
      }
      return false;
    }
    return false;
  }

  /**
   * Option 1: Start Direct Bluetooth SmartWatch Scan & Connection (No Health Connect app required!)
   */
  static async startBleSmartWatchConnection(): Promise<boolean> {
    this.activeMode = 'ble';
    if (Platform.OS !== 'android' || !BleHeartRateModule) return false;
    try {
      await BleHeartRateModule.requestBlePermissions();
      const started = await BleHeartRateModule.startBleScan();
      return started;
    } catch (_: any) {
      return false;
    }
  }

  /**
   * Option 2: Open System Health Connect Permissions
   */
  static async openSystemPermissions(): Promise<boolean> {
    this.activeMode = 'health_connect';
    if (Platform.OS !== 'android' || !HealthConnectModule) {
      return true;
    }
    try {
      const result = await HealthConnectModule.openHealthConnectPermissions();
      const hasAll = await HealthConnectModule.hasPermissions();
      return result || hasAll;
    } catch (_: any) {
      return false;
    }
  }

  /**
   * Option 3: Select No Permission / No Tracking
   */
  static selectNoPermission() {
    this.activeMode = 'none';
    if (BleHeartRateModule) {
      try {
        BleHeartRateModule.disconnect();
      } catch (_: any) {}
    }
  }

  /**
   * Reads live heart rate depending on active tracking mode (BLE or Health Connect).
   * Returns 0 if no sensor data.
   */
  static async getLiveHeartRate(): Promise<number> {
    if (this.activeMode === 'ble' && BleHeartRateModule) {
      try {
        const bpm = await BleHeartRateModule.getLiveHeartRate();
        return bpm > 0 ? bpm : 0;
      } catch (_: any) {
        return 0;
      }
    }

    if (this.activeMode === 'health_connect' && HealthConnectModule) {
      try {
        const hr = await HealthConnectModule.getLiveHeartRate();
        return hr > 0 ? hr : 0;
      } catch (_: any) {
        return 0;
      }
    }

    return 0;
  }

  /**
   * Fetches active calories burned.
   */
  static async fetchCaloriesBurned(startTimeMs: number, endTimeMs: number): Promise<number> {
    if (this.activeMode === 'health_connect' && HealthConnectModule) {
      try {
        const calories = await HealthConnectModule.fetchCaloriesBurned(startTimeMs, endTimeMs);
        return calories > 0 ? calories : 0;
      } catch (_: any) {
        return 0;
      }
    }
    return 0;
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
    this.activeMode = 'none';
    if (BleHeartRateModule) {
      try {
        BleHeartRateModule.disconnect();
      } catch (_: any) {}
    }
  }
}
