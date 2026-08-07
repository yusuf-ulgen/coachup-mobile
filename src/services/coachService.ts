import { supabase } from './supabaseClient';

export interface Coach {
  id: string;
  name: string;
  surname?: string;
  speciality?: string;
  specialization?: string;
  bio?: string;
  avatar_url?: string;
  gym_id?: string;
  rating?: number;
  gender?: string;
}

export const CoachService = {
  async fetchCoaches(gymId?: string): Promise<Coach[]> {
    try {
      let query = supabase.from('coaches').select('*');
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
