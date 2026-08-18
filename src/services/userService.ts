import { supabase } from './supabaseClient';
import { formatLocalDate } from '../utils/dateUtils';

export interface UserProfile {
  id: string;
  email: string;
  name: string;
  surname?: string | null;
  gender?: string | null;
  phone?: string | null;
  birth_date?: string | null;
  height?: number | null;
  weight?: number | null;
  height_cm?: number | null; // Compatibility alias
  weight_kg?: number | null; // Compatibility alias
  role?: string;
  gym_id?: string;
  gym_name?: string;
  current_streak?: number;
  is_individual?: boolean;
  avatar_url?: string;
  profile_image_url?: string;
  // Settings & Address Fields
  default_screen?: string;
  notifications_enabled?: boolean;
  biometrics_enabled?: boolean;
  weight_unit?: 'kg' | 'lbs';
  address_title?: string;
  city?: string;
  district?: string;
  neighborhood?: string;
  street?: string;
  building_no?: string;
  door_no?: string;
  postal_code?: string;
  theme_mode?: 'dark' | 'light' | 'system';
}

export const UserService = {
  async fetchProfile(userId: string): Promise<UserProfile | null> {
    try {
      const { data, error } = await supabase
        .from('users')
        .select('*')
        .eq('id', userId)
        .single();

      if (error) {
        console.error('Error fetching profile from users table:', error);
        return null;
      }

      // Populate compatibility aliases
      if (data) {
        data.height_cm = data.height;
        data.weight_kg = data.weight;
      }

      // If user has gym_id but missing gym_name, fetch from gyms table
      if (data?.gym_id && !data?.gym_name) {
        const { data: gym } = await supabase
          .from('gyms')
          .select('name')
          .eq('id', data.gym_id)
          .single();

        if (gym?.name) {
          data.gym_name = gym.name;
        }
      }

      return data;
    } catch (e) {
      console.error('fetchProfile failed:', e);
      return null;
    }
  },

  async updateUserProfile(userId: string, updates: Partial<UserProfile>) {
    try {
      const payload: Record<string, any> = {};

      if (updates.name !== undefined) payload.name = updates.name;
      if (updates.surname !== undefined) payload.surname = updates.surname;
      if (updates.phone !== undefined) payload.phone = updates.phone;
      if (updates.gender !== undefined) payload.gender = updates.gender;
      if (updates.birth_date !== undefined) payload.birth_date = updates.birth_date;
      if (updates.avatar_url !== undefined) payload.avatar_url = updates.avatar_url;
      if (updates.profile_image_url !== undefined) payload.profile_image_url = updates.profile_image_url;
      if (updates.gym_id !== undefined) payload.gym_id = updates.gym_id;

      // Canonical height & weight mappings
      if (updates.height !== undefined) {
        payload.height = updates.height;
      } else if (updates.height_cm !== undefined) {
        payload.height = updates.height_cm;
      }

      if (updates.weight !== undefined) {
        payload.weight = updates.weight;
      } else if (updates.weight_kg !== undefined) {
        payload.weight = updates.weight_kg;
      }

      const { data, error } = await supabase
        .from('users')
        .update(payload)
        .eq('id', userId)
        .select()
        .single();

      if (error) {
        console.error('Error updating user profile in Supabase:', error);
        throw error;
      }

      if (data) {
        data.height_cm = data.height;
        data.weight_kg = data.weight;
      }

      return data;
    } catch (e: any) {
      console.error('updateUserProfile error:', e);
      throw e;
    }
  },

  async fetchAvailableMemberships(userId: string) {
    try {
      const { data, error } = await supabase
        .from('user_memberships')
        .select('*, plan:membership_plans(*), gym:gyms(*)')
        .eq('user_id', userId);

      if (error) throw error;
      return data || [];
    } catch (e) {
      console.error('Error fetching memberships:', e);
      return [];
    }
  },

  async selectMembership(userId: string, gymId: string, gymName: string) {
    return this.updateUserProfile(userId, {
      gym_id: gymId,
    });
  },

  async ensureProfileExists(
    userId: string,
    email: string,
    name: string,
    gender: string
  ): Promise<void> {
    try {
      const { data: existing } = await supabase
        .from('users')
        .select('id')
        .eq('id', userId)
        .single();
      if (existing) return;
      await supabase.from('users').upsert({
        id: userId,
        email,
        name,
        gender,
        role: 'individual',
        is_individual: true,
      });
    } catch (e) {
      console.error('ensureProfileExists failed:', e);
    }
  },

  async resolveActiveGymIdForContent(profile?: UserProfile | null): Promise<string | null> {
    if (!profile) return null;

    // Staff/admin exception
    const isStaff =
      profile.role === 'admin' ||
      profile.role === 'gym_manager' ||
      (profile as any).is_admin === true ||
      (profile as any).is_gym_manager === true;

    if (isStaff) {
      return profile.gym_id && profile.gym_id.trim().length > 0 ? profile.gym_id : 'staff';
    }

    try {
      const userId = profile.id || (profile as any).user_id;
      if (!userId) return null;

      // 1. Primary Source of Truth: Active user_memberships
      const todayStr = formatLocalDate(new Date());
      const memberships = await this.fetchAvailableMemberships(userId);
      const activeMemberships = memberships.filter((m: any) => {
        const status = (m.status || '').toLowerCase().trim();
        const isNotExplicitlyInactive =
          status !== 'cancelled' &&
          status !== 'expired' &&
          status !== 'frozen' &&
          status !== 'pending' &&
          status !== 'inactive' &&
          m.is_active !== false;

        const isStatusActive = status ? status === 'active' : isNotExplicitlyInactive;

        const startDateStr = m.start_date ? String(m.start_date).slice(0, 10) : null;
        const endDateStr = m.end_date ? String(m.end_date).slice(0, 10) : null;

        const isStarted = !startDateStr || startDateStr <= todayStr;
        const notExpired = !endDateStr || endDateStr >= todayStr;

        return isStatusActive && isNotExplicitlyInactive && isStarted && notExpired;
      });

      const gymIds = Array.from(
        new Set(
          activeMemberships
            .map((m: any) => m.gym_id || m.plan?.gym_id)
            .filter((id: string) => !!id && id.trim().length > 0)
        )
      );

      if (gymIds.length > 0) {
        if (profile.gym_id && gymIds.includes(profile.gym_id)) {
          return profile.gym_id;
        }
        return gymIds[0] as string;
      }
    } catch (e) {
      console.error('Error resolving active gym ID:', e);
    }

    return null;
  },

  async hasActiveMembership(profile?: UserProfile | null): Promise<boolean> {
    if (!profile) return false;
    const isStaff =
      profile.role === 'admin' ||
      profile.role === 'gym_manager' ||
      (profile as any).is_admin === true ||
      (profile as any).is_gym_manager === true;
    if (isStaff) return true;

    const activeGymId = await this.resolveActiveGymIdForContent(profile);
    return !!activeGymId;
  },
};
