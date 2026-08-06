import { supabase } from './supabaseClient';

export interface UserProfile {
  id: string;
  email: string;
  name: string;
  surname?: string;
  gender?: string;
  role?: string;
  gym_id?: string;
  gym_name?: string;
  current_streak?: number;
  is_individual?: boolean;
  avatar_url?: string;
  profile_image_url?: string;
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
};
