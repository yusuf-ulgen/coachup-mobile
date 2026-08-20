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
  gym_id?: string | null;
  gym_name?: string | null;
  current_streak?: number;
  is_individual?: boolean;
  avatar_url?: string | null;
  profile_image_url?: string | null;
  is_admin?: boolean;
  is_gym_manager?: boolean;
  managed_gym_id?: string | null;
  // Settings & Address Fields
  default_screen?: string;
  notifications_enabled?: boolean;
  biometrics_enabled?: boolean;
  weight_unit?: 'kg' | 'lbs';
  address_title?: string | null;
  city?: string | null;
  district?: string | null;
  neighborhood?: string | null;
  street?: string | null;
  building_no?: string | null;
  door_no?: string | null;
  postal_code?: string | null;
  theme_mode?: 'dark' | 'light' | 'system';
}

export interface UserMembership {
  id: string;
  user_id: string;
  plan_id: string;
  gym_id: string;
  start_date?: string | null;
  end_date?: string | null;
  is_active: boolean;
  auto_renew?: boolean;
  payment_status?: string | null;
  total_price?: number | null;
  notes?: string | null;
  coach_id?: string | null;
  created_at?: string;
  // Compatibility read-only fields
  membership_plan_id?: string;
  status?: string;
  plan?: any;
  gym?: any;
}

export const UserService = {
  async fetchProfile(userId: string): Promise<UserProfile | null> {
    try {
      if (!userId || typeof userId !== 'string') return null;

      const { data, error } = await supabase
        .from('users')
        .select('*')
        .eq('id', userId)
        .single();

      if (error) {
        console.error('[UserService.fetchProfile] Error fetching profile:', {
          code: error.code,
          message: error.message,
          details: error.details,
        });
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
      console.error('[UserService.fetchProfile] failed:', e);
      return null;
    }
  },

  async updateUserProfile(userId: string, updates: Partial<UserProfile>): Promise<UserProfile> {
    try {
      if (!userId || typeof userId !== 'string' || userId.trim().length === 0) {
        throw new Error('Geçersiz kullanıcı kimliği.');
      }

      const payload: Record<string, any> = {};

      // Demographics & Profile Info
      if (updates.name !== undefined) payload.name = updates.name ? updates.name.trim() : null;
      if (updates.surname !== undefined) payload.surname = updates.surname ? updates.surname.trim() : null;
      if (updates.phone !== undefined) payload.phone = updates.phone ? updates.phone.trim() : null;
      if (updates.gender !== undefined) payload.gender = updates.gender;
      if (updates.birth_date !== undefined) payload.birth_date = updates.birth_date || null;
      if (updates.avatar_url !== undefined) payload.avatar_url = updates.avatar_url;
      if (updates.profile_image_url !== undefined) payload.profile_image_url = updates.profile_image_url;
      if (updates.gym_id !== undefined) payload.gym_id = updates.gym_id;
      if (updates.is_individual !== undefined) payload.is_individual = updates.is_individual;

      // Canonical body metrics (height / weight)
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

      // Address Fields
      if (updates.address_title !== undefined) payload.address_title = updates.address_title ? updates.address_title.trim() : null;
      if (updates.city !== undefined) payload.city = updates.city ? updates.city.trim() : null;
      if (updates.district !== undefined) payload.district = updates.district ? updates.district.trim() : null;
      if (updates.neighborhood !== undefined) payload.neighborhood = updates.neighborhood ? updates.neighborhood.trim() : null;
      if (updates.street !== undefined) payload.street = updates.street ? updates.street.trim() : null;
      if (updates.building_no !== undefined) payload.building_no = updates.building_no ? updates.building_no.trim() : null;
      if (updates.door_no !== undefined) payload.door_no = updates.door_no ? updates.door_no.trim() : null;
      if (updates.postal_code !== undefined) payload.postal_code = updates.postal_code ? updates.postal_code.trim() : null;

      // Settings Fields
      if (updates.default_screen !== undefined) payload.default_screen = updates.default_screen;
      if (updates.notifications_enabled !== undefined) payload.notifications_enabled = updates.notifications_enabled;
      if (updates.biometrics_enabled !== undefined) payload.biometrics_enabled = updates.biometrics_enabled;
      if (updates.weight_unit !== undefined) payload.weight_unit = updates.weight_unit;
      if (updates.theme_mode !== undefined) payload.theme_mode = updates.theme_mode;

      // NOTE: Privileged fields (is_admin, is_gym_manager, managed_gym_id, role, is_super_admin)
      // are explicitly omitted to prevent unprivileged privilege escalation.

      const { data, error } = await supabase
        .from('users')
        .update(payload)
        .eq('id', userId)
        .select()
        .single();

      if (error) {
        console.error('[UserService.updateUserProfile] Supabase update error:', {
          code: error.code,
          message: error.message,
          details: error.details,
          hint: error.hint,
        });
        throw error;
      }

      if (data) {
        data.height_cm = data.height;
        data.weight_kg = data.weight;
      }

      return data;
    } catch (e: any) {
      console.error('[UserService.updateUserProfile] error:', e);
      throw e;
    }
  },

  async fetchAvailableMemberships(userId: string): Promise<UserMembership[]> {
    try {
      const { data, error } = await supabase
        .from('user_memberships')
        .select('*, plan:membership_plans(*), gym:gyms(*)')
        .eq('user_id', userId);

      if (error) {
        console.error('[UserService.fetchAvailableMemberships] error:', error);
        throw error;
      }
      return (data as UserMembership[]) || [];
    } catch (e) {
      console.error('[UserService.fetchAvailableMemberships] failed:', e);
      return [];
    }
  },

  async selectMembership(userId: string, gymId: string, _gymName: string) {
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
      profile.is_admin === true ||
      profile.is_gym_manager === true;

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
        const startDateStr = m.start_date ? String(m.start_date).slice(0, 10) : null;
        const endDateStr = m.end_date ? String(m.end_date).slice(0, 10) : null;

        const isStarted = !startDateStr || startDateStr <= todayStr;
        const notExpired = !endDateStr || endDateStr >= todayStr;

        // Canonical LIVE check: is_active boolean
        if (m.is_active !== undefined && m.is_active !== null) {
          return m.is_active === true && isStarted && notExpired;
        }

        // Legacy read compatibility fallback if is_active column is missing
        const status = (m.status || '').toLowerCase().trim();
        const isLegacyActive = status === 'active' || (!status && m.is_active !== false);
        return isLegacyActive && isStarted && notExpired;
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
      profile.is_admin === true ||
      profile.is_gym_manager === true;
    if (isStaff) return true;

    const activeGymId = await this.resolveActiveGymIdForContent(profile);
    return !!activeGymId;
  },
};
