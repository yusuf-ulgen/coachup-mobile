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
        .eq('is_active', true)
        .eq('category', 'ai_program');

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
    const isUUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(sessionId);
    if (!isUUID) {
      throw new Error(`[TrainingService] Invalid session ID format: ${sessionId}`);
    }

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
      status: 'completed',
      completed_at: now,
    };

    if (metrics.durationSeconds !== undefined && metrics.durationSeconds !== null) {
      updatePayload.duration_minutes = Math.max(1, Math.round(metrics.durationSeconds / 60));
    }
    if (metrics.calories !== undefined && metrics.calories !== null && metrics.calories > 0) {
      updatePayload.calories_burned = Math.round(metrics.calories);
    }
    if (metrics.notes) {
      updatePayload.notes = metrics.notes;
    }

    // Fetch workout_sets for session totals
    try {
      const { data: sessionSets, error: setFetchErr } = await supabase
        .from('workout_sets')
        .select('reps, reps_count, weight, is_completed')
        .eq('session_id', sessionId);

      if (!setFetchErr && sessionSets) {
        const getReps = (s: any) => (s.reps !== null && s.reps !== undefined) ? Number(s.reps) : Number(s.reps_count || 0);
        const completed = sessionSets.filter((s: any) => s.is_completed);
        updatePayload.total_sets = completed.length;
        updatePayload.total_reps = completed.reduce((sum: number, s: any) => sum + getReps(s), 0);
        updatePayload.total_weight = completed.reduce((sum: number, s: any) => sum + ((Number(s.weight) || 0) * getReps(s)), 0);
      }
    } catch (setEx) {
      console.warn('[TrainingService] Note calculating set aggregates:', setEx);
    }

    let finalData: any = null;
    const { data, error } = await supabase
      .from('training_sessions')
      .update(updatePayload)
      .eq('id', sessionId)
      .select('*, program:training_programs(*)')
      .single();

    if (error) {
      console.warn('[TrainingService] completeSession select relation error, falling back to base select:', error);
      const { data: baseData, error: baseErr } = await supabase
        .from('training_sessions')
        .update(updatePayload)
        .eq('id', sessionId)
        .select('*')
        .single();

      if (baseErr) {
        console.error('[TrainingService] completeSession fallback error:', baseErr);
        throw baseErr;
      }
      finalData = baseData;
    } else {
      finalData = data;
    }

    if (finalData?.user_id) {
      StreakService.fetchStreakData(finalData.user_id).catch((err: any) => {
        console.warn('[TrainingService] Streak sync background warning:', err);
      });
    }

    return finalData;
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
      program_id: params.programId || null,
      scheduled_at: startedAt,
      started_at: startedAt,
      completed_at: now,
      status: 'completed',
      duration_minutes: duration > 0 ? Math.max(1, Math.round(duration / 60)) : null,
      notes: params.title || params.category || params.metrics?.notes || null,
    };

    if (params.metrics?.calories && params.metrics.calories > 0) {
      insertPayload.calories_burned = Math.round(params.metrics.calories);
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
    const existingList = existingSets || [];

    // Map existing rows by set_number (first canonical occurrence)
    const existingBySetNum = new Map<number, any>();
    existingList.forEach((s) => {
      if (!existingBySetNum.has(s.set_number)) {
        existingBySetNum.set(s.set_number, s);
      } else {
        console.warn(`[TrainingService] Duplicate set_number ${s.set_number} detected for session ${params.sessionId} exercise ${params.exerciseId}`);
      }
    });

    const targetSetCount = Math.max(1, params.numSets || 1);
    const resultSets: WorkoutSet[] = [];

    // Reconcile each prescribed set number (1..targetSetCount)
    for (let k = 1; k <= targetSetCount; k++) {
      const existing = existingBySetNum.get(k);

      if (existing) {
        // UPDATE existing set for set_number k
        const updatePayload: any = {
          reps: params.reps,
          weight: params.weight !== undefined ? params.weight : existing.weight,
          rest_seconds: params.restSeconds !== undefined ? params.restSeconds : existing.rest_seconds,
          is_completed: params.isCompleted,
          completed_at: params.isCompleted ? (existing.completed_at || now) : null,
        };
        if (params.notes !== undefined) {
          updatePayload.notes = params.notes;
        }

        const { data: updated, error: updateErr } = await supabase
          .from('workout_sets')
          .update(updatePayload)
          .eq('id', existing.id)
          .select()
          .single();

        if (updateErr) {
          console.error(`[TrainingService] Error updating workout set #${k}:`, updateErr.message, updateErr.code);
          throw updateErr;
        }
        resultSets.push(updated);
      } else if (params.isCompleted) {
        // INSERT missing prescribed set for set_number k
        const insertRow = {
          session_id: params.sessionId,
          user_id: params.userId,
          exercise_id: params.exerciseId,
          set_number: k,
          reps: params.reps,
          weight: params.weight || null,
          rest_seconds: params.restSeconds || null,
          is_completed: true,
          completed_at: now,
          notes: params.notes || null,
        };

        const { data: inserted, error: insertErr } = await supabase
          .from('workout_sets')
          .insert(insertRow)
          .select()
          .single();

        if (insertErr) {
          console.error(`[TrainingService] Error inserting workout set #${k}:`, insertErr.message, insertErr.code);
          throw insertErr;
        }
        resultSets.push(inserted);
      }
    }

    return resultSets;
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

      const targetSetNumber = data.setNumber || 1;

      // FIX 11 & Phase 7 A1: Idempotency check with strict error handling
      const { data: existingRow, error: lookupError } = await supabase
        .from('workout_sets')
        .select('id, weight, rest_seconds, notes')
        .eq('session_id', data.sessionId)
        .eq('exercise_id', data.exerciseId)
        .eq('set_number', targetSetNumber)
        .maybeSingle();

      if (lookupError) {
        console.error('[TrainingService] completeSet lookup error:', lookupError.message, lookupError.code);
        throw lookupError;
      }

      if (existingRow?.id) {
        const updatePayload: any = {
          reps: data.reps,
          weight: data.weight !== undefined ? data.weight : existingRow.weight,
          is_completed: isCompleted,
          completed_at: isCompleted ? now : null,
        };
        if (data.restSeconds !== undefined) updatePayload.rest_seconds = data.restSeconds;
        if (data.notes !== undefined) updatePayload.notes = data.notes;

        const { data: result, error: updateErr } = await supabase
          .from('workout_sets')
          .update(updatePayload)
          .eq('id', existingRow.id)
          .select()
          .single();

        if (updateErr) throw updateErr;
        return result;
      }

      const insertPayload: any = {
        session_id: data.sessionId,
        user_id: data.userId,
        exercise_id: data.exerciseId,
        set_number: targetSetNumber,
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
