export class HealthConnectService {
  private static isConnected = false;

  // Health Connect / Apple HealthKit izinlerini simüle eder
  static async requestPermissions(): Promise<boolean> {
    return new Promise((resolve) => {
      setTimeout(() => {
        this.isConnected = true;
        resolve(true);
      }, 500);
    });
  }

  static async checkPermissions(): Promise<boolean> {
    return this.isConnected;
  }

  // Anlık nabız okuma (simülasyon: 90 - 160 arası)
  static async getLiveHeartRate(): Promise<number> {
    if (!this.isConnected) return 0;
    // Küçük dalgalanmalarla gerçekçi nabız
    return Math.floor(Math.random() * (160 - 90 + 1)) + 90;
  }

  // Aktif kalori hesaplama
  // Süre ve nabıza dayanarak hesaplanır
  static calculateActiveCalories(
    durationSeconds: number,
    averageHeartRate: number,
    weightKg: number = 70,
    age: number = 25,
    isMale: boolean = true
  ): number {
    if (durationSeconds <= 0 || averageHeartRate === 0) return 0;
    const durationMinutes = durationSeconds / 60;
    
    let calories = 0;
    if (isMale) {
      calories = ((age * 0.2017) - (weightKg * 0.09036) + (averageHeartRate * 0.6309) - 55.0969) * durationMinutes / 4.184;
    } else {
      calories = ((age * 0.074) - (weightKg * 0.05741) + (averageHeartRate * 0.4472) - 20.4022) * durationMinutes / 4.184;
    }
    
    return Math.max(0, Math.ceil(calories));
  }

  // Adım sayısı okuma simülasyonu
  static async getStepCount(startTime: Date, endTime: Date): Promise<number> {
    if (!this.isConnected) return 0;
    const durationMins = (endTime.getTime() - startTime.getTime()) / 60000;
    return Math.floor(durationMins * 100); // dakikada ortalama 100 adım
  }

  // Nabız bölgeleri (HR Zones: Isınma, Yağ Yakımı, Kardiyo, Ekstrem)
  static calculateHRZone(heartRate: number, maxHeartRate: number = 195): string {
    if (heartRate === 0) return "-";
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
}
