import AsyncStorage from '@react-native-async-storage/async-storage';
import { Platform, PermissionsAndroid } from 'react-native';
import * as Location from 'expo-location';

export type PermissionChoice = 'ALWAYS_ALLOW' | 'ALLOW_ONCE' | 'DENIED' | 'NOT_SET';

export const LOCATION_PREF_KEY = '@coachup_location_perm_choice';
export const NOTIF_PREF_KEY = '@coachup_notif_perm_choice';

// In-memory session state for "ALLOW_ONCE" (resets on app restart)
let sessionLocationAllowed = false;
let sessionNotifAllowed = false;

export class PermissionPreferenceService {
  /**
   * Get location permission preference
   */
  static async getLocationPreference(): Promise<PermissionChoice> {
    if (sessionLocationAllowed) return 'ALLOW_ONCE';
    try {
      const stored = await AsyncStorage.getItem(LOCATION_PREF_KEY);
      if (stored === 'ALWAYS_ALLOW' || stored === 'DENIED') {
        return stored as PermissionChoice;
      }
      return 'NOT_SET';
    } catch (_) {
      return 'NOT_SET';
    }
  }

  /**
   * Set location permission preference
   */
  static async setLocationPreference(choice: PermissionChoice): Promise<void> {
    try {
      if (choice === 'ALLOW_ONCE') {
        sessionLocationAllowed = true;
        await AsyncStorage.removeItem(LOCATION_PREF_KEY);
      } else if (choice === 'ALWAYS_ALLOW' || choice === 'DENIED') {
        sessionLocationAllowed = choice === 'ALWAYS_ALLOW';
        await AsyncStorage.setItem(LOCATION_PREF_KEY, choice);
      } else {
        sessionLocationAllowed = false;
        await AsyncStorage.removeItem(LOCATION_PREF_KEY);
      }
    } catch (e) {
      console.warn('[PermissionPreferenceService] Error saving location pref:', e);
    }
  }

  /**
   * Get notification permission preference
   */
  static async getNotificationPreference(): Promise<PermissionChoice> {
    if (sessionNotifAllowed) return 'ALLOW_ONCE';
    try {
      const stored = await AsyncStorage.getItem(NOTIF_PREF_KEY);
      if (stored === 'ALWAYS_ALLOW' || stored === 'DENIED') {
        return stored as PermissionChoice;
      }
      return 'NOT_SET';
    } catch (_) {
      return 'NOT_SET';
    }
  }

  /**
   * Set notification permission preference
   */
  static async setNotificationPreference(choice: PermissionChoice): Promise<void> {
    try {
      if (choice === 'ALLOW_ONCE') {
        sessionNotifAllowed = true;
        await AsyncStorage.removeItem(NOTIF_PREF_KEY);
      } else if (choice === 'ALWAYS_ALLOW' || choice === 'DENIED') {
        sessionNotifAllowed = choice === 'ALWAYS_ALLOW';
        await AsyncStorage.setItem(NOTIF_PREF_KEY, choice);
      } else {
        sessionNotifAllowed = false;
        await AsyncStorage.removeItem(NOTIF_PREF_KEY);
      }
    } catch (e) {
      console.warn('[PermissionPreferenceService] Error saving notif pref:', e);
    }
  }

  /**
   * Should we prompt the user for permissions?
   * Returns true if either Location or Notification is NOT_SET (and not allowed in current session).
   */
  static async shouldPromptPermissions(): Promise<{ location: boolean; notification: boolean }> {
    const locPref = await this.getLocationPreference();
    const notifPref = await this.getNotificationPreference();

    const needLoc = locPref === 'NOT_SET';
    const needNotif = notifPref === 'NOT_SET';

    return {
      location: needLoc,
      notification: needNotif,
    };
  }

  /**
   * Request system level location permissions
   */
  static async requestLocationSystemPermission(): Promise<boolean> {
    try {
      const { status: fgStatus } = await Location.requestForegroundPermissionsAsync();
      if (fgStatus !== 'granted') return false;

      try {
        await Location.requestBackgroundPermissionsAsync();
      } catch (_) {}

      return true;
    } catch (e) {
      console.warn('[PermissionPreferenceService] Location request error:', e);
      return false;
    }
  }

  /**
   * Request system level notification permissions (Android 13+)
   */
  static async requestNotificationSystemPermission(): Promise<boolean> {
    if (Platform.OS === 'android' && Platform.Version >= 33) {
      try {
        const granted = await PermissionsAndroid.request(
          PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS
        );
        return granted === PermissionsAndroid.RESULTS.GRANTED;
      } catch (e) {
        console.warn('[PermissionPreferenceService] Notif request error:', e);
        return false;
      }
    }
    return true;
  }
}
