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

export interface WorkoutSet {
  id: string;
  session_id: string;
  user_id: string;
  exercise_id: string;
  set_number: number;
  reps: number;
  weight?: number | null;
  rest_seconds?: number | null;
  is_completed: boolean;
  completed_at?: string | null;
  notes?: string | null;
  created_at?: string | null;
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
        .eq('is_active', true);

      if (searchText && searchText.trim().length > 0) {
        query = query.ilike('name', `%${searchText.trim()}%`);
      }

      const { data, error } = await query.order('created_at', { ascending: false });
      if (error) throw error;
      return (data || []).map((p: any) => ({ ...p, source: 'ai' }));
    } catch (e) {
      console.error('Error fetching AI programs:', e);
      return [];
    }
  },

  async fetchAllCompletedSessions(userId: string): Promise<TrainingSession[]> {
    try {
      const { data, error } = await supabase
        .from('training_sessions')
        .select('*, program:training_programs(*)')
        .eq('user_id', userId)
        .eq('status', 'completed')
        .order('completed_at', { ascending: false });

      if (error) throw error;
      return data || [];
    } catch (e) {
      console.error('Error fetching all completed sessions:', e);
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
      console.error('Error fetching completed sessions for date:', e);
      return [];
    }
  },

  async fetchProgramDetail(programId: string): Promise<TrainingProgram | null> {
    try {
      const { data, error } = await supabase
        .from('training_programs')
        .select('*')
        .eq('id', programId)
        .single();

      if (error) throw error;
      return data;
    } catch (e) {
      console.error('Error fetching program detail:', e);
      return null;
    }
  },

  async startTrainingSession(
    userId: string,
    programId?: string | null,
    gymId?: string | null,
    category?: string | null,
    title?: string | null
  ): Promise<TrainingSession> {
    const isUUID = (val: string | null | undefined): boolean => {
      if (!val) return false;
      return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(val);
    };

    const validProgramId = isUUID(programId) ? programId : null;
    const validGymId = isUUID(gymId) ? gymId : null;

    const payload: any = {
      user_id: userId,
      program_id: validProgramId,
      gym_id: validGymId,
      status: 'in_progress',
      started_at: new Date().toISOString(),
      scheduled_at: new Date().toISOString(),
      notes: category ? `builtin:${category}` : (title || null),
    };

    const { data, error } = await supabase
      .from('training_sessions')
      .insert(payload)
      .select('*, program:training_programs(*)')
      .single();

    if (error) {
      console.error('[TrainingService] Error starting training session:', error);
      throw error;
    }
    return data;
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
    return this.startTrainingSession(
      userId,
      programId,
      gymId,
      options?.category,
      options?.title || options?.notes
    );
  },

  async completeSession(
    sessionId: string,
    metrics: CompleteSessionMetrics = {}
  ): Promise<TrainingSession> {
    const isUUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(sessionId);
    if (!isUUID) {
      throw new Error(`[TrainingService] Invalid session ID format: ${sessionId}`);
    }

    const updatePayload: any = {
      status: 'completed',
      completed_at: new Date().toISOString(),
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

    try {
      const { data: sessionSets } = await supabase
        .from('workout_sets')
        .select('reps, weight, is_completed')
        .eq('session_id', sessionId);

      if (sessionSets && sessionSets.length > 0) {
        const completed = sessionSets.filter((s: any) => s.is_completed);
        const totalSets = completed.length;
        const totalReps = completed.reduce((sum: number, s: any) => sum + (s.reps || 0), 0);
        const totalWeight = completed.reduce((sum: number, s: any) => sum + ((s.weight || 0) * (s.reps || 0)), 0);

        updatePayload.total_sets = totalSets;
        updatePayload.total_reps = totalReps;
        updatePayload.total_weight = totalWeight;
      }
    } catch (setAggErr) {
      console.warn('[TrainingService] Failed to aggregate session workout sets:', setAggErr);
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
    const isUUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(sessionId);
    if (!isUUID) return;
    const { error } = await supabase
      .from('training_sessions')
      .update({ status: 'cancelled' })
      .eq('id', sessionId);
    if (error) {
      console.error('[TrainingService] cancelSession error:', error);
      throw error;
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

  async fetchSessionSets(sessionId: string): Promise<WorkoutSet[]> {
    const isUUID = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/.test(sessionId);
    if (!isUUID) return [];

    const { data, error } = await supabase
      .from('workout_sets')
      .select('*')
      .eq('session_id', sessionId)
      .order('exercise_id')
      .order('set_number', { ascending: true });

    if (error) {
      console.error('[TrainingService] fetchSessionSets error:', error.message, error.code);
      throw error;
    }
    return data || [];
  },

  async persistExerciseSets(params: {
    sessionId: string;
    userId: string;
    exerciseId: string;
    numSets: number;
    reps: number;
    weight?: number | null;
    restSeconds?: number | null;
    isCompleted: boolean;
    notes?: string | null;
  }): Promise<WorkoutSet[]> {
    const isUUID = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/.test(params.sessionId);
    if (!isUUID || !params.userId || !params.exerciseId) {
      throw new Error('Geçersiz oturum, kullanıcı veya egzersiz kimliği.');
    }

    const { data: existingSets, error: fetchErr } = await supabase
      .from('workout_sets')
      .select('*')
      .eq('session_id', params.sessionId)
      .eq('exercise_id', params.exerciseId)
      .order('set_number', { ascending: true });

    if (fetchErr) {
      console.error('[TrainingService] Error checking existing workout sets:', fetchErr.message, fetchErr.code);
      throw fetchErr;
    }

    const now = new Date().toISOString();

    if (existingSets && existingSets.length > 0) {
      const updatedSets: WorkoutSet[] = [];
      for (const setRow of existingSets) {
        const updatePayload: any = {
          reps: params.reps,
          weight: params.weight !== undefined ? params.weight : setRow.weight,
          rest_seconds: params.restSeconds !== undefined ? params.restSeconds : setRow.rest_seconds,
          is_completed: params.isCompleted,
          completed_at: params.isCompleted ? (setRow.completed_at || now) : null,
        };
        if (params.notes !== undefined) {
          updatePayload.notes = params.notes;
        }

        const { data: updated, error: updateErr } = await supabase
          .from('workout_sets')
          .update(updatePayload)
          .eq('id', setRow.id)
          .select()
          .single();

        if (updateErr) {
          console.error('[TrainingService] Error updating workout set:', updateErr.message, updateErr.code);
          throw updateErr;
        }
        updatedSets.push(updated);
      }
      return updatedSets;
    } else if (params.isCompleted) {
      const count = Math.max(1, params.numSets || 1);
      const rowsToInsert = Array.from({ length: count }).map((_, idx) => ({
        session_id: params.sessionId,
        user_id: params.userId,
        exercise_id: params.exerciseId,
        set_number: idx + 1,
        reps: params.reps,
        weight: params.weight || null,
        rest_seconds: params.restSeconds || null,
        is_completed: true,
        completed_at: now,
        notes: params.notes || null,
      }));

      const { data: inserted, error: insertErr } = await supabase
        .from('workout_sets')
        .insert(rowsToInsert)
        .select();

      if (insertErr) {
        console.error('[TrainingService] Error inserting workout sets:', insertErr.message, insertErr.code);
        throw insertErr;
      }
      return inserted || [];
    }

    return [];
  },

  async completeSet(
    setId: string,
    data: {
      reps: number;
      weight?: number | null;
      sessionId?: string;
      exerciseId?: string;
      setNumber?: number;
      userId?: string;
      isCompleted?: boolean;
      restSeconds?: number | null;
      notes?: string | null;
    }
  ): Promise<WorkoutSet> {
    const isUUID = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/.test(setId);
    const now = new Date().toISOString();
    const isCompleted = data.isCompleted !== undefined ? data.isCompleted : true;

    if (isUUID) {
      const updatePayload: any = {
        reps: data.reps,
        weight: data.weight !== undefined ? data.weight : null,
        is_completed: isCompleted,
        completed_at: isCompleted ? now : null,
      };
      if (data.restSeconds !== undefined) updatePayload.rest_seconds = data.restSeconds;
      if (data.notes !== undefined) updatePayload.notes = data.notes;

      const { data: result, error } = await supabase
        .from('workout_sets')
        .update(updatePayload)
        .eq('id', setId)
        .select()
        .single();

      if (error) {
        console.error('[TrainingService] completeSet update error:', error.message, error.code);
        throw error;
      }
      return result;
    } else {
      if (!data.sessionId || !data.userId || !data.exerciseId) {
        throw new Error('Gerekli set kimlik alanları eksik (sessionId, userId, exerciseId).');
      }

      const insertPayload: any = {
        session_id: data.sessionId,
        user_id: data.userId,
        exercise_id: data.exerciseId,
        set_number: data.setNumber || 1,
        reps: data.reps,
        weight: data.weight !== undefined ? data.weight : null,
        rest_seconds: data.restSeconds !== undefined ? data.restSeconds : null,
        is_completed: isCompleted,
        completed_at: isCompleted ? now : null,
        notes: data.notes || null,
      };

      const { data: result, error } = await supabase
        .from('workout_sets')
        .insert(insertPayload)
        .select()
        .single();

      if (error) {
        console.error('[TrainingService] completeSet insert error:', error.message, error.code);
        throw error;
      }
      return result;
    }
  },
};
