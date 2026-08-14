declare module 'expo-splash-screen' {
  export function preventAutoHideAsync(): Promise<boolean>;
  export function hideAsync(): Promise<boolean>;
}

declare module 'expo-location' {
  export enum Accuracy {
    Lowest = 1,
    Low = 2,
    Balanced = 3,
    High = 4,
    Highest = 5,
    BestForNavigation = 6,
  }

  export interface LocationObject {
    coords: {
      latitude: number;
      longitude: number;
      altitude: number | null;
      accuracy: number | null;
      altitudeAccuracy: number | null;
      heading: number | null;
      speed: number | null;
    };
    timestamp: number;
  }

  export interface LocationSubscription {
    remove(): void;
  }

  export function requestForegroundPermissionsAsync(): Promise<{ status: string }>;
  export function requestBackgroundPermissionsAsync(): Promise<{ status: string }>;
  export function getLastKnownPositionAsync(options?: any): Promise<LocationObject | null>;
  export function watchPositionAsync(
    options: any,
    callback: (location: LocationObject) => void
  ): Promise<LocationSubscription>;
  export function getCurrentPositionAsync(options?: any): Promise<LocationObject>;
}
