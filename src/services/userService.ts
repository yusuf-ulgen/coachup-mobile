import { supabase } from './supabaseClient';

export interface UserProfile {
  id: string;
  email: string;
  name: string;
  surname?: string;
  gender?: string;
  phone?: string;
  birth_date?: string;
  height_cm?: number;
  weight_kg?: number;
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
    const { data, error } = await supabase
      .from('users')
      .update(updates)
      .eq('id', userId)
      .select()
      .single();

    if (error) throw error;
    return data;
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
      gym_name: gymName,
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

    if (profile.role === 'individual' || profile.is_individual) return null;

    try {
      const userId = profile.id || (profile as any).user_id;
      if (!userId) return null;

      const memberships = await this.fetchAvailableMemberships(userId);
      const activeMemberships = memberships.filter((m: any) => {
        const isNotDisabled = m.is_active !== false;
        const notExpired = !m.end_date || new Date(m.end_date) > new Date();
        return isNotDisabled && notExpired;
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

