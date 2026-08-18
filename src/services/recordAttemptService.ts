import { supabase } from './supabaseClient';
import { RecordExercise, RecordMeasureType } from '../models/recordAttemptCategories';
import { StreakService } from './streakService';

export interface RecordAttempt {
  id: string;
  user_id: string;
  exercise_id: string;
  target_weight?: number | null;
  target_reps?: number | null;
  estimated_1rm?: number | null;
  status: 'in_progress' | 'completed' | 'failed' | 'abandoned';
  success: boolean;
  notes?: string | null;
  source_device?: string | null;
  started_at?: string | null;
  completed_at?: string | null;
  created_at?: string | null;
  exercise?: { id: string; name: string };
}

export interface RecordAttemptSet {
  id: string;
  attempt_id: string;
  user_id: string;
  set_index: number;
  set_type: 'warmup' | 'main';
  prescribed_weight?: number | null;
  prescribed_reps?: number | null;
  actual_weight?: number | null;
  actual_reps?: number | null;
  rpe?: number | null;
  rest_seconds?: number | null;
  is_completed: boolean;
  completed_at?: string | null;
  notes?: string | null;
  created_at?: string | null;
}

export interface PlannedSet {
  weight?: number;
  reps?: number;
  type: 'warmup' | 'main';
}

function normalizeKey(str: string): string {
  return (str || '')
    .toLowerCase()
    .replace(/&/g, 'and')
    .replace(/[^a-z0-9]/g, '');
}

export const RecordAttemptService = {
  /**
   * Calculates Epley 1RM: weight * (1 + reps / 30)
   */
  epley1RM(weight: number, reps: number): number {
    const w = Math.max(0, weight || 0);
    const r = Math.max(1, reps || 1);
    if (r === 1) return Math.round(w);
    return Math.round(w * (1 + r / 30));
  },

  /**
   * Resolves target distance in KM from catalog identifier
   */
  runningTargetKm(catalogId?: string): number {
    const id = (catalogId || '').toLowerCase();
    if (id.includes('400')) return 0.4;
    if (id.includes('800')) return 0.8;
    if (id.includes('1k') || id.endsWith('_1k')) return 1.0;
    if (id.includes('3k')) return 3.0;
    if (id.includes('5k')) return 5.0;
    if (id.includes('10k')) return 10.0;
    if (id.includes('half') || id.includes('21')) return 21.0975;
    if (id.includes('full') || id.includes('42')) return 42.195;
    return 1.0;
  },

  /**
   * Resolves AMRAP time cap in seconds (default: 20 min)
   */
  amrapCapSeconds(catalogId?: string): number {
    const id = (catalogId || '').toLowerCase();
    if (id.includes('10')) return 10 * 60;
    if (id.includes('15')) return 15 * 60;
    if (id.includes('30')) return 30 * 60;
    return 20 * 60;
  },

  /**
   * Fetch all exercises
   */
  async fetchExercises(): Promise<any[]> {
    const { data, error } = await supabase
      .from('exercises')
      .select('*')
      .order('name', { ascending: true });
    if (error) {
      console.warn('[RecordAttemptService] fetchExercises error:', error);
      return [];
    }
    return data || [];
  },

  /**
   * Resolves a catalog exercise to a real DB exercise UUID; creates one if missing.
   */
  async resolveOrCreateExercise(
    catalog: RecordExercise,
    categoryId?: string
  ): Promise<{ id: string; name: string }> {
    try {
      // 1. Try ILIKE exact match
      const { data: dbExercises } = await supabase
        .from('exercises')
        .select('id, name')
        .ilike('name', catalog.name)
        .limit(1);

      if (dbExercises && dbExercises.length > 0) {
        return dbExercises[0];
      }

      // 2. Try normalized name match from list
      const allEx = await this.fetchExercises();
      const catalogKey = normalizeKey(catalog.name);
      const slugKey = normalizeKey(catalog.id.replace(/_/g, ' '));

      const match = allEx.find((ex: any) => {
        const exKey = normalizeKey(ex.name);
        return (
          exKey === catalogKey ||
          exKey === slugKey ||
          (catalogKey.length >= 4 && exKey.includes(catalogKey)) ||
          (exKey.length >= 4 && catalogKey.includes(exKey))
        );
      });

      if (match) {
        return { id: match.id, name: match.name };
      }

      // 3. Create new exercise row if not found
      const category = categoryId || 'record_attempt';
      const { data: newEx, error } = await supabase
        .from('exercises')
        .insert({
          name: catalog.name,
          category,
          equipment: catalog.equipment || null,
        })
        .select('id, name')
        .single();

      if (error) {
        console.error('[RecordAttemptService] resolveOrCreateExercise insert error:', error);
        throw error;
      }

      return newEx;
    } catch (e) {
      console.error('[RecordAttemptService] resolveOrCreateExercise exception:', e);
      throw e;
    }
  },

  /**
   * Fetches past completed attempts for a single exercise.
   */
  async fetchAttemptsForExercise(
    userId: string,
    exerciseId: string,
    limit: number = 20
  ): Promise<RecordAttempt[]> {
    try {
      const { data, error } = await supabase
        .from('record_attempts')
        .select('*')
        .eq('user_id', userId)
        .eq('exercise_id', exerciseId)
        .neq('status', 'in_progress')
        .order('completed_at', { ascending: false })
        .limit(limit);

      if (error) {
        console.warn('[RecordAttemptService] fetchAttemptsForExercise error:', error);
        return [];
      }
      return data || [];
    } catch (e) {
      console.warn('[RecordAttemptService] fetchAttemptsForExercise exception:', e);
      return [];
    }
  },

  /**
   * Fetches past personal records for a single exercise.
   */
  async fetchPersonalRecordsForExercise(
    userId: string,
    exerciseId: string,
    limit: number = 10
  ): Promise<any[]> {
    try {
      const { data, error } = await supabase
        .from('personal_records')
        .select('*')
        .eq('user_id', userId)
        .eq('exercise_id', exerciseId)
        .order('record_date', { ascending: false })
        .limit(limit);

      if (error) {
        console.warn('[RecordAttemptService] fetchPersonalRecordsForExercise error:', error);
        return [];
      }
      return data || [];
    } catch (e) {
      console.warn('[RecordAttemptService] fetchPersonalRecordsForExercise exception:', e);
      return [];
    }
  },

  /**
   * Fetches main sets for a list of attempt IDs.
   */
  async fetchMainSetsForAttempts(attemptIds: string[]): Promise<Record<string, RecordAttemptSet>> {
    if (!attemptIds || attemptIds.length === 0) return {};
    try {
      const { data, error } = await supabase
        .from('record_attempt_sets')
        .select('*')
        .in('attempt_id', attemptIds)
        .eq('set_type', 'main')
        .eq('is_completed', true);

      if (error) {
        console.warn('[RecordAttemptService] fetchMainSetsForAttempts error:', error);
        return {};
      }

      const map: Record<string, RecordAttemptSet> = {};
      (data || []).forEach((s: RecordAttemptSet) => {
        map[s.attempt_id] = s;
      });
      return map;
    } catch (e) {
      console.warn('[RecordAttemptService] fetchMainSetsForAttempts exception:', e);
      return {};
    }
  },

  /**
   * Creates ONE canonical record_attempts row with status 'in_progress'.
   */
  async startAttempt(
    userId: string,
    exerciseId: string,
    targetWeight?: number,
    targetReps?: number
  ): Promise<RecordAttempt> {
    const now = new Date().toISOString();
    const w = targetWeight ?? 0;
    const r = targetReps ?? 1;
    const estimated = w > 0 ? this.epley1RM(w, r) : null;

    const payload = {
      user_id: userId,
      exercise_id: exerciseId,
      target_weight: w,
      target_reps: r,
      estimated_1rm: estimated,
      status: 'in_progress',
      success: false,
      started_at: now,
    };

    const { data, error } = await supabase
      .from('record_attempts')
      .insert(payload)
      .select('*, exercise:exercises(id, name)')
      .single();

    if (error) {
      console.error('[RecordAttemptService] startAttempt error:', error);
      throw error;
    }

    return data;
  },

  /**
   * Inserts planned sets (warmup + main) for the attempt into record_attempt_sets.
   */
  async insertPlannedSets(
    attemptId: string,
    userId: string,
    plan: PlannedSet[]
  ): Promise<RecordAttemptSet[]> {
    if (!plan || plan.length === 0) return [];

    const inserts = plan.map((p, idx) => ({
      attempt_id: attemptId,
      user_id: userId,
      set_index: idx + 1,
      set_type: p.type || 'main',
      prescribed_weight: p.weight ?? null,
      prescribed_reps: p.reps ?? null,
      is_completed: false,
    }));

    const { data, error } = await supabase
      .from('record_attempt_sets')
      .insert(inserts)
      .select('*')
      .order('set_index', { ascending: true });

    if (error) {
      console.error('[RecordAttemptService] insertPlannedSets error:', error);
      throw error;
    }

    return data || [];
  },

  /**
   * Updates an individual set in record_attempt_sets upon completion.
   */
  async saveSet(
    setId: string,
    actualWeight?: number | null,
    actualReps?: number | null,
    rpe?: number | null,
    restSeconds?: number | null,
    notes?: string | null
  ): Promise<RecordAttemptSet> {
    const now = new Date().toISOString();
    const payload: any = {
      is_completed: true,
      completed_at: now,
    };

    if (actualWeight !== undefined && actualWeight !== null) {
      payload.actual_weight = actualWeight;
    }
    if (actualReps !== undefined && actualReps !== null) {
      payload.actual_reps = actualReps;
    }
    if (rpe !== undefined && rpe !== null) {
      payload.rpe = rpe;
    }
    if (restSeconds !== undefined && restSeconds !== null) {
      payload.rest_seconds = restSeconds;
    }
    if (notes !== undefined && notes !== null) {
      payload.notes = notes;
    }

    const { data, error } = await supabase
      .from('record_attempt_sets')
      .update(payload)
      .eq('id', setId)
      .select('*')
      .single();

    if (error) {
      console.error('[RecordAttemptService] saveSet error:', error);
      throw error;
    }

    return data;
  },

  /**
   * Finalizes the record attempt with 'completed' or 'failed' status and notes.
   */
  async completeAttempt(
    attemptId: string,
    success: boolean,
    notes?: string | null,
    userId?: string | null
  ): Promise<RecordAttempt> {
    const now = new Date().toISOString();
    const payload = {
      status: success ? 'completed' : 'failed',
      success: success,
      notes: notes || null,
      completed_at: now,
    };

    const { data, error } = await supabase
      .from('record_attempts')
      .update(payload)
      .eq('id', attemptId)
      .select('*, exercise:exercises(id, name)')
      .single();

    if (error) {
      console.error('[RecordAttemptService] completeAttempt error:', error);
      throw error;
    }

    if (success && userId) {
      StreakService.fetchStreakData(userId).catch((err: any) => {
        console.warn('[RecordAttemptService] Streak sync background warning:', err);
      });
    }

    return data;
  },

  /**
   * Marks the attempt as abandoned.
   */
  async abandonAttempt(attemptId: string): Promise<void> {
    try {
      const isUUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(attemptId);
      if (!isUUID) return;

      const now = new Date().toISOString();
      const { error } = await supabase
        .from('record_attempts')
        .update({
          status: 'abandoned',
          success: false,
          completed_at: now,
        })
        .eq('id', attemptId);

      if (error) {
        console.warn('[RecordAttemptService] abandonAttempt error:', error);
      }
    } catch (e) {
      console.warn('[RecordAttemptService] abandonAttempt exception:', e);
    }
  },

  /**
   * Inserts a record into personal_records table.
   */
  async savePersonalRecord(
    userId: string,
    exerciseId: string,
    weightKg?: number | null,
    reps?: number | null,
    notes?: string | null
  ): Promise<any> {
    const now = new Date().toISOString();
    const payload = {
      user_id: userId,
      exercise_id: exerciseId,
      weight_kg: weightKg ?? 0,
      reps: reps ?? 1,
      record_date: now,
      notes: notes || null,
    };

    const { data, error } = await supabase
      .from('personal_records')
      .insert(payload)
      .select('*')
      .single();

    if (error) {
      console.error('[RecordAttemptService] savePersonalRecord error:', error);
      throw error;
    }

    return data;
  },
};
