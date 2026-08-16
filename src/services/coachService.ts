import { supabase } from './supabaseClient';

export interface Coach {
  id: string;
  name: string;
  surname?: string;
  specialty?: string;
  specializations?: string[] | string;
  certifications?: string[] | string;
  experience_years?: number;
  bio?: string;
  avatar_url?: string;
  gym_id?: string;
  is_active?: boolean;
  rating?: number;
  gender?: string;
}

export const CoachService = {
  async fetchCoaches(gymId?: string): Promise<Coach[]> {
    try {
      let query = supabase.from('coaches').select('*').eq('is_active', true);
      if (gymId) {
        query = query.eq('gym_id', gymId);
      }
      const { data, error } = await query.order('name', { ascending: true });
      if (error) throw error;
      return data || [];
    } catch (e) {
      console.error('Error fetching coaches:', e);
      return [];
    }
  },

  async fetchCoachDetail(coachId: string): Promise<Coach | null> {
    try {
      const { data, error } = await supabase
        .from('coaches')
        .select('*')
        .eq('id', coachId)
        .single();

      if (error) throw error;
      return data;
    } catch (e) {
      console.error('Error fetching coach detail:', e);
      return null;
    }
  },
};
