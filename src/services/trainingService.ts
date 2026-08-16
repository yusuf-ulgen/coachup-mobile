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
  program_text?: string;
  exercise_names?: string[];
  difficulty?: 'beginner' | 'intermediate' | 'advanced' | string;
  visible_member_ids?: string[];
  source?: 'gym' | 'ai' | 'assigned';
  assigned_info?: {
    start_date?: string;
    end_date?: string;
    status?: string;
    progress?: number;
    coach?: { id: string; name: string; surname?: string };
  };
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
  async fetchAssignedPrograms(userId: string): Promise<TrainingProgram[]> {
    try {
      const { data, error } = await supabase
        .from('user_assigned_programs')
        .select(`
          *,
          program:training_programs(*),
          coach:coaches(id, name, surname)
        `)
        .eq('user_id', userId)
        .order('created_at', { ascending: false });

      if (error || !data) {
        console.error('Error fetching assigned programs:', error);
        return [];
      }

      return data
        .filter((item: any) => item.program)
        .map((item: any) => ({
          ...item.program,
          source: 'assigned',
          assigned_info: {
            start_date: item.start_date,
            end_date: item.end_date,
            status: item.status,
            progress: item.progress,
            coach: item.coach,
          },
        }));
    } catch (e) {
      console.error('Error in fetchAssignedPrograms:', e);
      return [];
    }
  },

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
      return (data || []).map((p: any) => ({ ...p, source: 'gym' }));
    } catch (e) {
      console.error('Error fetching gym programs:', e);
      return [];
    }
  },

  async fetchAiPrograms(searchText?: string): Promise<TrainingProgram[]> {
    try {
      let query = supabase
        .from('training_programs')
        .select('*')
        .eq('is_active', true)
        .eq('category', 'ai_program');

      if (searchText) {
        query = query.ilike('name', `%${searchText}%`);
      }

      const { data, error } = await query;
      if (error) throw error;
      return (data || []).map((p: any) => ({ ...p, source: 'ai' }));
    } catch (e) {
      console.error('Error fetching AI programs:', e);
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
    const encodedNotes = notes ? `Duration: ${durationSeconds || 0}s | ${notes}` : `Duration: ${durationSeconds || 0}s`;
    const { data, error } = await supabase
      .from('training_sessions')
      .update({
        completed_at: now,
        status: 'completed',
        notes: encodedNotes,
      })
      .eq('id', sessionId)
      .select()
      .single();

    if (error) throw error;
    return data;
  },

  async fetchProgramExercises(programId: string) {
    try {
      const { data, error } = await supabase
        .from('program_exercises')
        .select('*, exercises(*)')
        .eq('program_id', programId)
        .order('order_index', { ascending: true });

      if (error) throw error;
      return data || [];
    } catch (e) {
      console.error('Error fetching program exercises:', e);
      return [];
    }
  },

  async completeSet(
    setId: string,
    data: { reps: number; weight: number; sessionId?: string; exerciseId?: string; setNumber?: number; userId?: string }
  ) {
    try {
      const isUUID = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/.test(setId);

      if (isUUID) {
        const { data: result, error } = await supabase
          .from('workout_sets')
          .update({
            reps_count: data.reps,
            weight: data.weight,
            created_at: new Date().toISOString(),
          })
          .eq('id', setId)
          .select();

        if (error) throw error;
        return result;
      } else {
        const payload: any = {
          reps_count: data.reps,
          weight: data.weight,
          created_at: new Date().toISOString(),
        };
        if (data.exerciseId) payload.exercise_id = data.exerciseId;

        const { data: result, error } = await supabase
          .from('workout_sets')
          .insert(payload)
          .select();

        if (error) throw error;
        return result;
      }
    } catch (e) {
      console.error('Error completing set:', e);
      return null;
    }
  }
};
