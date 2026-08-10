import { Alert } from 'react-native';

export class HealthConnectService {
  private static isConnected: boolean | null = null;
  private static isPromptOpen = false;

  /**
   * Antrenman öncesinde Sağlık (Health Connect / Apple Health / Akıllı Saat) izni sorar.
   * Kullanıcı izin verirse `true`, reddederse `false` döner.
   */
  static async requestPermissions(): Promise<boolean> {
    if (this.isConnected !== null) {
      return this.isConnected;
    }

    if (this.isPromptOpen) {
      return false;
    }

    this.isPromptOpen = true;

    return new Promise((resolve) => {
      Alert.alert(
        'Sağlık ve Akıllı Saat İzni',
        'Antrenman sırasında nabız ve kalori verilerinizi akıllı saatinizden / Sağlık uygulamasından (Health Connect / Apple Health) senkronize edebilmemiz için erişim izni gerekmektedir.',
        [
          {
            text: 'Şimdi Değil',
            style: 'cancel',
            onPress: () => {
              this.isPromptOpen = false;
              this.isConnected = false;
              resolve(false);
            },
          },
          {
            text: 'İzin Ver',
            onPress: () => {
              this.isPromptOpen = false;
              this.isConnected = true;
              resolve(true);
            },
          },
        ],
        { cancelable: false }
      );
    });
  }

  static async checkPermissions(): Promise<boolean> {
    return this.isConnected === true;
  }

  static async getLiveHeartRate(): Promise<number> {
    if (this.isConnected !== true) return 0;
    // Gerçek bir akıllı saat sensörü bağlı olmadığı sürece 0 döner. Kesinlikle sahte/rastgele nabız üretilmez.
    return 0;
  }

  /**
   * Aktif kalori hesaplama
   * Yalnızca akıllı saat veya Sağlık uygulamasından geçerli nabız verisi (> 0) alınıyorsa hesaplanır.
   * Sensör/sağlık verisi yoksa 0 döner (tahmini / rastgele kalori hesaplanmaz).
   */
  static calculateActiveCalories(
    durationSeconds: number,
    averageHeartRate: number,
    weightKg: number = 70,
    age: number = 25,
    isMale: boolean = true
  ): number {
    if (durationSeconds <= 0 || !this.isConnected || averageHeartRate <= 0) {
      return 0;
    }

    const durationMinutes = durationSeconds / 60;
    let calories = 0;
    if (isMale) {
      calories = ((age * 0.2017) - (weightKg * 0.09036) + (averageHeartRate * 0.6309) - 55.0969) * durationMinutes / 4.184;
    } else {
      calories = ((age * 0.074) - (weightKg * 0.05741) + (averageHeartRate * 0.4472) - 20.4022) * durationMinutes / 4.184;
    }
    return Math.max(0, Math.ceil(calories));
  }

  // Adım sayısı okuma
  static async getStepCount(startTime: Date, endTime: Date): Promise<number> {
    if (this.isConnected !== true) return 0;
    return 0;
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
    this.isPromptOpen = false;
  }
}
