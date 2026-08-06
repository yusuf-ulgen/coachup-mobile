import { supabase } from './supabaseClient';

export interface TrainingProgram {
  id: string;
  name: string;
  description?: string;
  category?: string;
  gym_id?: string;
  is_active: boolean;
  privacy?: string;
  exercises_count?: number;
}

export interface TrainingSession {
  id: string;
  user_id: string;
  gym_id?: string;
  program_id?: string;
  coach_id?: string;
  scheduled_at?: string;
  started_at?: string;
  completed_at?: string;
  status: 'scheduled' | 'in_progress' | 'completed' | 'cancelled';
  notes?: string;
  duration_seconds?: number;
  program?: TrainingProgram;
}

export const TrainingService = {
  async fetchGymPrograms(gymId?: string): Promise<TrainingProgram[]> {
    try {
      let query = supabase
        .from('training_programs')
        .select('*')
        .eq('is_active', true);

      if (gymId) {
        query = query.eq('gym_id', gymId);
      }

      const { data, error } = await query.order('name', { ascending: true });
      if (error) throw error;
      return data || [];
    } catch (e) {
      console.error('Error fetching gym programs:', e);
      return [];
    }
  },

  async fetchCompletedSessionsForDate(userId: string, dateStr: string): Promise<TrainingSession[]> {
    try {
      const startOfDay = `${dateStr}T00:00:00.000Z`;
      const endOfDay = `${dateStr}T23:59:59.999Z`;

      const { data, error } = await supabase
        .from('training_sessions')
        .select('*, program:training_programs(*)')
        .eq('user_id', userId)
        .eq('status', 'completed')
        .gte('completed_at', startOfDay)
        .lte('completed_at', endOfDay)
        .order('completed_at', { ascending: false });

      if (error) throw error;
      return data || [];
    } catch (e) {
      console.error('Error fetching completed sessions:', e);
      return [];
    }
  },

  async startSession(userId: string, programId: string, gymId?: string): Promise<TrainingSession> {
    const now = new Date().toISOString();
    const { data, error } = await supabase
      .from('training_sessions')
      .insert({
        user_id: userId,
        program_id: programId,
        gym_id: gymId,
        scheduled_at: now,
        started_at: now,
        status: 'in_progress',
      })
      .select('*, program:training_programs(*)')
      .single();

    if (error) throw error;
    return data;
  },

  async completeSession(sessionId: string, durationSeconds?: number, notes?: string) {
    const now = new Date().toISOString();
    const { data, error } = await supabase
      .from('training_sessions')
      .update({
        completed_at: now,
        status: 'completed',
        duration_seconds: durationSeconds,
        notes: notes,
      })
      .eq('id', sessionId)
      .select()
      .single();

    if (error) throw error;
    return data;
  },
};
