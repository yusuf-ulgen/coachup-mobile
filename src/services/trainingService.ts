import { supabase } from './supabaseClient';
import { StreakService } from './streakService';

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

export interface CompleteSessionMetrics {
  durationSeconds?: number | null;
  distanceKm?: number | null;
  avgHeartRate?: number | null;
  maxHeartRate?: number | null;
  calories?: number | null;
  avgPace?: number | null;
  avgSpeed?: number | null;
  altitudeGain?: number | null;
  perceivedEffort?: string | null;
  notes?: string | null;
}

export interface TrainingSession {
  id: string;
  user_id: string;
  gym_id?: string | null;
  program_id?: string | null;
  coach_id?: string | null;
  scheduled_at?: string;
  started_at?: string;
  completed_at?: string | null;
  status: 'scheduled' | 'in_progress' | 'completed' | 'cancelled' | 'abandoned';
  notes?: string | null;
  duration_seconds?: number | null;
  distance_km?: number | null;
  avg_heart_rate?: number | null;
  max_heart_rate?: number | null;
  calories?: number | null;
  avg_pace?: number | null;
  avg_speed?: number | null;
  altitude_gain?: number | null;
  perceived_effort?: string | null;
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

  async startSession(
    userId: string,
    programId?: string | null,
    gymId?: string | null,
    options?: {
      notes?: string;
      category?: string;
      title?: string;
    }
  ): Promise<TrainingSession> {
    const now = new Date().toISOString();
    const payload: any = {
      user_id: userId,
      gym_id: gymId || null,
      scheduled_at: now,
      started_at: now,
      status: 'in_progress',
    };

    if (programId) {
      payload.program_id = programId;
    }

    if (options?.notes) {
      payload.notes = options.notes;
    } else if (options?.category) {
      payload.notes = `builtin:${options.category}`;
    } else if (options?.title) {
      payload.notes = options.title;
    }

    const { data, error } = await supabase
      .from('training_sessions')
      .insert(payload)
      .select('*, program:training_programs(*)')
      .single();

    if (error) {
      console.error('[TrainingService] startSession error:', error);
      throw error;
    }
    return data;
  },

  async completeSession(
    sessionId: string,
    metricsOrDuration?: CompleteSessionMetrics | number,
    legacyNotes?: string
  ): Promise<TrainingSession> {
    const now = new Date().toISOString();

    let metrics: CompleteSessionMetrics = {};
    if (typeof metricsOrDuration === 'number') {
      metrics = {
        durationSeconds: metricsOrDuration,
        notes: legacyNotes,
      };
    } else if (metricsOrDuration && typeof metricsOrDuration === 'object') {
      metrics = metricsOrDuration;
    }

    const updatePayload: any = {
      completed_at: now,
      status: 'completed',
    };

    if (metrics.durationSeconds !== undefined && metrics.durationSeconds !== null) {
      updatePayload.duration_seconds = Math.round(metrics.durationSeconds);
    }
    if (metrics.distanceKm !== undefined && metrics.distanceKm !== null && metrics.distanceKm > 0) {
      updatePayload.distance_km = Number(metrics.distanceKm.toFixed(3));
    }
    if (metrics.avgHeartRate !== undefined && metrics.avgHeartRate !== null && metrics.avgHeartRate > 0) {
      updatePayload.avg_heart_rate = Math.round(metrics.avgHeartRate);
    }
    if (metrics.maxHeartRate !== undefined && metrics.maxHeartRate !== null && metrics.maxHeartRate > 0) {
      updatePayload.max_heart_rate = Math.round(metrics.maxHeartRate);
    }
    if (metrics.calories !== undefined && metrics.calories !== null && metrics.calories > 0) {
      updatePayload.calories = Math.round(metrics.calories);
    }
    if (metrics.avgPace !== undefined && metrics.avgPace !== null && metrics.avgPace > 0) {
      updatePayload.avg_pace = Number(metrics.avgPace.toFixed(2));
    }
    if (metrics.avgSpeed !== undefined && metrics.avgSpeed !== null && metrics.avgSpeed > 0) {
      updatePayload.avg_speed = Number(metrics.avgSpeed.toFixed(2));
    }
    if (metrics.altitudeGain !== undefined && metrics.altitudeGain !== null && metrics.altitudeGain > 0) {
      updatePayload.altitude_gain = Number(metrics.altitudeGain.toFixed(1));
    }
    if (metrics.perceivedEffort) {
      updatePayload.perceived_effort = metrics.perceivedEffort;
    }
    if (metrics.notes) {
      updatePayload.notes = metrics.notes;
    }

    const { data, error } = await supabase
      .from('training_sessions')
      .update(updatePayload)
      .eq('id', sessionId)
      .select('*, program:training_programs(*)')
      .single();

    if (error) {
      console.error('[TrainingService] completeSession error:', error);
      throw error;
    }

    if (data?.user_id) {
      StreakService.fetchStreakData(data.user_id).catch((err: any) => {
        console.warn('[TrainingService] Streak sync background warning:', err);
      });
    }

    return data;
  },

  async createAndCompleteSession(
    userId: string,
    params: {
      programId?: string | null;
      gymId?: string | null;
      category?: string | null;
      title?: string | null;
      startedAt?: string | null;
      metrics?: CompleteSessionMetrics;
    }
  ): Promise<TrainingSession> {
    const now = new Date().toISOString();
    const duration = params.metrics?.durationSeconds || 0;
    const startedAt = params.startedAt || (duration > 0 ? new Date(Date.now() - duration * 1000).toISOString() : now);

    const insertPayload: any = {
      user_id: userId,
      gym_id: params.gymId || null,
      scheduled_at: startedAt,
      started_at: startedAt,
      completed_at: now,
      status: 'completed',
      notes: params.metrics?.notes || (params.category ? `builtin:${params.category}` : params.title || null),
    };

    if (params.programId) insertPayload.program_id = params.programId;
    if (params.metrics?.durationSeconds !== undefined && params.metrics?.durationSeconds !== null) {
      insertPayload.duration_seconds = Math.round(params.metrics.durationSeconds);
    }
    if (params.metrics?.distanceKm && params.metrics.distanceKm > 0) {
      insertPayload.distance_km = Number(params.metrics.distanceKm.toFixed(3));
    }
    if (params.metrics?.avgHeartRate && params.metrics.avgHeartRate > 0) {
      insertPayload.avg_heart_rate = Math.round(params.metrics.avgHeartRate);
    }
    if (params.metrics?.maxHeartRate && params.metrics.maxHeartRate > 0) {
      insertPayload.max_heart_rate = Math.round(params.metrics.maxHeartRate);
    }
    if (params.metrics?.calories && params.metrics.calories > 0) {
      insertPayload.calories = Math.round(params.metrics.calories);
    }
    if (params.metrics?.avgPace && params.metrics.avgPace > 0) {
      insertPayload.avg_pace = Number(params.metrics.avgPace.toFixed(2));
    }
    if (params.metrics?.avgSpeed && params.metrics.avgSpeed > 0) {
      insertPayload.avg_speed = Number(params.metrics.avgSpeed.toFixed(2));
    }
    if (params.metrics?.altitudeGain && params.metrics.altitudeGain > 0) {
      insertPayload.altitude_gain = Number(params.metrics.altitudeGain.toFixed(1));
    }
    if (params.metrics?.perceivedEffort) {
      insertPayload.perceived_effort = params.metrics.perceivedEffort;
    }

    const { data, error } = await supabase
      .from('training_sessions')
      .insert(insertPayload)
      .select('*, program:training_programs(*)')
      .single();

    if (error) {
      console.error('[TrainingService] createAndCompleteSession error:', error);
      throw error;
    }

    if (data?.user_id) {
      StreakService.fetchStreakData(data.user_id).catch((err: any) => {
        console.warn('[TrainingService] Streak sync background warning:', err);
      });
    }

    return data;
  },

  async cancelSession(sessionId: string): Promise<void> {
    try {
      const isUUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(sessionId);
      if (!isUUID) return;
      const { error } = await supabase
        .from('training_sessions')
        .update({ status: 'cancelled' })
        .eq('id', sessionId);
      if (error) {
        console.warn('[TrainingService] cancelSession error:', error);
      }
    } catch (e) {
      console.warn('[TrainingService] cancelSession exception:', e);
    }
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
