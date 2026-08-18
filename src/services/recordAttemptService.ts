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

export type RecordResultType =
  | 'weight'
  | 'reps'
  | 'running'
  | 'fixed_distance_time'
  | 'fixed_calorie_time'
  | 'benchmark_time'
  | 'amrap';

export interface RecordResultPayload {
  resultType: RecordResultType;
  exerciseId: string;
  catalogId?: string;
  weightKg?: number;
  reps?: number;
  elapsedSeconds?: number;
  distanceKm?: number;
  targetCalories?: number;
  rpe?: number;
  notes?: string;
}

export interface PREvaluationResult {
  isNewPR: boolean;
  previousBest: any | null;
  currentResult: RecordResultPayload;
  savedRecord?: any;
  epley1RM?: number;
}

function normalizeKey(str: string): string {
  return (str || '')
    .toLowerCase()
    .replace(/&/g, 'and')
    .replace(/[^a-z0-9]/g, '');
}

export const RecordAttemptService = {
  /**
   * Calculates canonical Epley 1RM: weight * (1 + reps / 30)
   */
  epley1RM(weight: number, reps: number): number {
    const w = Math.max(0, weight || 0);
    const r = Math.max(1, reps || 1);
    if (r === 1) return Math.round(w);
    return Math.round(w * (1 + r / 30));
  },

  /**
   * Resolves target distance in KM from catalog identifier without fake 1.0 fallback.
   */
  runningTargetKm(catalogId?: string): number | null {
    const id = (catalogId || '').toLowerCase();
    if (id.includes('400')) return 0.4;
    if (id.includes('800')) return 0.8;
    if (id.includes('1k') || id.endsWith('_1k') || id.includes('1_km')) return 1.0;
    if (id.includes('3k') || id.includes('3_km')) return 3.0;
    if (id.includes('5k') || id.includes('5_km')) return 5.0;
    if (id.includes('10k') || id.includes('10_km')) return 10.0;
    if (id.includes('half') || id.includes('21')) return 21.0975;
    if (id.includes('full') || id.includes('42')) return 42.195;
    return null;
  },

  /**
   * Resolves target distance in KM for fixed-distance cardio (Row, SkiErg).
   */
  cardioTargetDistanceKm(catalogId?: string): number | null {
    const id = (catalogId || '').toLowerCase();
    if (id.includes('500m') || id.includes('500')) return 0.5;
    if (id.includes('1000m') || id.includes('1000') || id.includes('1k')) return 1.0;
    if (id.includes('2000m') || id.includes('2000') || id.includes('2k')) return 2.0;
    if (id.includes('5000m') || id.includes('5000') || id.includes('5k')) return 5.0;
    return null;
  },

  /**
   * Resolves target calories for fixed-calorie challenges (Assault Bike, Echo Bike).
   */
  cardioTargetCalories(catalogId?: string): number | null {
    const id = (catalogId || '').toLowerCase();
    if (id.includes('10cal') || id.includes('10_cal')) return 10;
    if (id.includes('50cal') || id.includes('50_cal')) return 50;
    if (id.includes('100cal') || id.includes('100_cal')) return 100;
    return null;
  },

  /**
   * Resolves AMRAP time cap in seconds (default: 20 min = 1200s).
   */
  amrapCapSeconds(catalogId?: string): number {
    const id = (catalogId || '').toLowerCase();
    if (id.includes('10')) return 10 * 60;
    if (id.includes('15')) return 15 * 60;
    if (id.includes('30')) return 30 * 60;
    return 20 * 60;
  },

  /**
   * Resolves canonical RecordResultType based on catalog/category metadata.
   */
  getExerciseResultType(
    catalogId?: string,
    categoryId?: string,
    exerciseName?: string
  ): RecordResultType {
    const id = (catalogId || '').toLowerCase();
    const cat = (categoryId || '').toLowerCase();
    const name = (exerciseName || '').toLowerCase();

    if (name.includes('cindy') || id.includes('cindy') || id.includes('amrap')) {
      return 'amrap';
    }
    if (cat === 'strength') return 'weight';
    if (cat === 'bodyweight') return 'reps';
    if (cat === 'running' || id.startsWith('run_')) return 'running';
    if (cat === 'benchmark') {
      if (name.includes('cindy') || id.includes('cindy')) return 'amrap';
      return 'benchmark_time';
    }
    if (
      cat === 'cardio' ||
      id.startsWith('row_') ||
      id.startsWith('ski_') ||
      id.startsWith('bike_') ||
      id.startsWith('echo_')
    ) {
      if (id.includes('cal') || name.includes('cal')) return 'fixed_calorie_time';
      return 'fixed_distance_time';
    }
    return 'weight';
  },

  /**
   * Extract normalized numeric metrics from any record/attempt/history item.
   */
  extractNormalizedMetrics(record: any, resultType: RecordResultType) {
    const notes = (record?.notes || '').trim();
    let weightKg = Number(
      record?.weight_kg ??
      record?.weight ??
      record?.target_weight ??
      record?.actual_weight ??
      0
    );
    let reps = Number(
      record?.reps ??
      record?.actual_reps ??
      record?.target_reps ??
      1
    );
    let elapsedSeconds: number | null = null;

    if (record?.time_seconds && Number(record.time_seconds) > 0) {
      elapsedSeconds = Number(record.time_seconds);
    } else {
      const colonTime = notes.match(/(?:Süre:\s*)?(\d{1,2}):(\d{2})/i);
      if (colonTime) {
        const min = parseInt(colonTime[1], 10);
        const sec = parseInt(colonTime[2], 10);
        elapsedSeconds = min * 60 + sec;
      } else {
        const secMatch = notes.match(/(\d+)\s*sn/i);
        if (secMatch) {
          elapsedSeconds = parseInt(secMatch[1], 10);
        }
      }
    }

    // Historical compatibility: if seconds were stored in weight_kg for non-weight exercise
    if (resultType !== 'weight' && resultType !== 'reps') {
      if (!elapsedSeconds && weightKg > 0) {
        elapsedSeconds = Math.round(weightKg);
        weightKg = 0;
      }
    }

    if (resultType === 'amrap') {
      const roundsMatch = notes.match(/(\d+)\s*tur/i);
      if (roundsMatch) {
        reps = parseInt(roundsMatch[1], 10);
      }
    }

    const epley = resultType === 'weight' ? this.epley1RM(weightKg, reps) : 0;

    return {
      weightKg,
      reps,
      elapsedSeconds: elapsedSeconds || 0,
      epley1RM: epley,
    };
  },

  /**
   * Pure PR comparison rule. Returns true if newResult strictly beats previousBestRecord.
   */
  isBetterRecord(newResult: RecordResultPayload, previousBestRecord: any): boolean {
    if (!previousBestRecord) return true;
    const prev = this.extractNormalizedMetrics(previousBestRecord, newResult.resultType);

    switch (newResult.resultType) {
      case 'weight': {
        const new1RM = this.epley1RM(newResult.weightKg || 0, newResult.reps || 1);
        return new1RM > prev.epley1RM;
      }
      case 'reps': {
        const newReps = newResult.reps || 0;
        return newReps > prev.reps;
      }
      case 'running':
      case 'fixed_distance_time':
      case 'fixed_calorie_time':
      case 'benchmark_time': {
        const newTime = newResult.elapsedSeconds || 0;
        if (newTime <= 0) return false;
        if (prev.elapsedSeconds <= 0) return true;
        return newTime < prev.elapsedSeconds;
      }
      case 'amrap': {
        const newRounds = newResult.reps || 0;
        return newRounds > prev.reps;
      }
      default:
        return false;
    }
  },

  /**
   * Finds the best PR from historical records list using canonical comparison.
   */
  findBestHistoricalRecord(records: any[], resultType: RecordResultType): any | null {
    if (!records || records.length === 0) return null;
    let best = records[0];
    for (let i = 1; i < records.length; i++) {
      const candidate = records[i];
      const candidateMetrics = this.extractNormalizedMetrics(candidate, resultType);
      const candidatePayload: RecordResultPayload = {
        resultType,
        exerciseId: candidate.exercise_id,
        weightKg: candidateMetrics.weightKg,
        reps: candidateMetrics.reps,
        elapsedSeconds: candidateMetrics.elapsedSeconds,
      };
      if (this.isBetterRecord(candidatePayload, best)) {
        best = candidate;
      }
    }
    return best;
  },

  /**
   * Evaluates if attempt qualifies as a new PR, and inserts ONLY if strictly better.
   */
  async evaluateAndSavePersonalRecord(
    userId: string,
    exerciseId: string,
    resultPayload: RecordResultPayload
  ): Promise<PREvaluationResult> {
    const records = await this.fetchPersonalRecordsForExercise(userId, exerciseId, 50);
    const bestPrevious = this.findBestHistoricalRecord(records, resultPayload.resultType);
    const isNewPR = this.isBetterRecord(resultPayload, bestPrevious);
    const epley = resultPayload.resultType === 'weight'
      ? this.epley1RM(resultPayload.weightKg || 0, resultPayload.reps || 1)
      : undefined;

    if (isNewPR) {
      let notes = resultPayload.notes || '';
      if (resultPayload.resultType === 'weight') {
        notes = `${resultPayload.weightKg || 0} kg × ${resultPayload.reps || 1}${
          resultPayload.rpe ? ` · RPE: ${resultPayload.rpe}` : ''
        }`;
      } else if (resultPayload.resultType === 'reps') {
        notes = `${resultPayload.reps || 0} tekrar`;
      } else if (resultPayload.resultType === 'running') {
        const sec = resultPayload.elapsedSeconds || 0;
        const m = Math.floor(sec / 60);
        const s = sec % 60;
        notes = `${resultPayload.distanceKm ? `${resultPayload.distanceKm} km Koşu: ` : ''}${m}:${s
          .toString()
          .padStart(2, '0')} (${sec} sn)`;
      } else if (resultPayload.resultType === 'fixed_distance_time') {
        const sec = resultPayload.elapsedSeconds || 0;
        const m = Math.floor(sec / 60);
        const s = sec % 60;
        notes = `${resultPayload.distanceKm ? `${Math.round(resultPayload.distanceKm * 1000)}m: ` : ''}${m}:${s
          .toString()
          .padStart(2, '0')} (${sec} sn)`;
      } else if (resultPayload.resultType === 'fixed_calorie_time') {
        const sec = resultPayload.elapsedSeconds || 0;
        const m = Math.floor(sec / 60);
        const s = sec % 60;
        notes = `${resultPayload.targetCalories || 0} Cal: ${m}:${s.toString().padStart(2, '0')} (${sec} sn)`;
      } else if (resultPayload.resultType === 'benchmark_time') {
        const sec = resultPayload.elapsedSeconds || 0;
        const m = Math.floor(sec / 60);
        const s = sec % 60;
        notes = `Süre: ${m}:${s.toString().padStart(2, '0')} (${sec} sn)`;
      } else if (resultPayload.resultType === 'amrap') {
        notes = `AMRAP 20 dk: ${resultPayload.reps || 0} tur`;
      }

      const saved = await this.savePersonalRecord(
        userId,
        exerciseId,
        resultPayload.resultType === 'weight' ? (resultPayload.weightKg || 0) : 0,
        resultPayload.reps || 1,
        notes
      );

      if (resultPayload.resultType === 'weight' && epley) {
        await this.upsertExerciseResult(
          userId,
          exerciseId,
          resultPayload.weightKg || 0,
          resultPayload.reps || 1,
          epley
        );
      }

      return {
        isNewPR: true,
        previousBest: bestPrevious,
        currentResult: resultPayload,
        savedRecord: saved,
        epley1RM: epley,
      };
    }

    return {
      isNewPR: false,
      previousBest: bestPrevious,
      currentResult: resultPayload,
      epley1RM: epley,
    };
  },

  /**
   * Fetch all exercises from DB.
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
   * Read-only lookup for an existing exercise in DB. Does NOT insert a new row.
   */
  async findExistingExercise(
    catalog: RecordExercise,
    categoryId?: string
  ): Promise<{ id: string; name: string } | null> {
    try {
      const isUUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(catalog.id);
      if (isUUID) {
        const { data: dbEx } = await supabase
          .from('exercises')
          .select('id, name')
          .eq('id', catalog.id)
          .limit(1);
        if (dbEx && dbEx.length > 0) return dbEx[0];
      }

      const { data: dbExercises } = await supabase
        .from('exercises')
        .select('id, name')
        .ilike('name', catalog.name)
        .limit(1);

      if (dbExercises && dbExercises.length > 0) {
        return dbExercises[0];
      }

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

      return null;
    } catch (e) {
      console.warn('[RecordAttemptService] findExistingExercise warning:', e);
      return null;
    }
  },

  /**
   * Resolves a catalog exercise to a real DB exercise UUID; creates one if missing (used on start).
   */
  async resolveOrCreateExercise(
    catalog: RecordExercise,
    categoryId?: string
  ): Promise<{ id: string; name: string }> {
    try {
      const existing = await this.findExistingExercise(catalog, categoryId);
      if (existing) return existing;

      // Create new exercise row if not found
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
    limit: number = 20
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
   * Upserts the exercise_results row for strength 1RM progression tracking.
   */
  async upsertExerciseResult(
    userId: string,
    exerciseId: string,
    maxWeight: number,
    maxReps: number,
    oneRepMax: number
  ): Promise<void> {
    try {
      const { data: existing } = await supabase
        .from('exercise_results')
        .select('id, max_weight, max_reps, one_rep_max')
        .eq('user_id', userId)
        .eq('exercise_id', exerciseId)
        .limit(1);

      const now = new Date().toISOString();
      if (existing && existing.length > 0) {
        const row = existing[0];
        const newWeight = Math.max(Number(row.max_weight || 0), maxWeight);
        const newReps = maxWeight >= Number(row.max_weight || 0) ? maxReps : Number(row.max_reps || 1);
        const new1RM = Math.max(Number(row.one_rep_max || 0), oneRepMax);
        await supabase
          .from('exercise_results')
          .update({
            max_weight: newWeight,
            max_reps: newReps,
            one_rep_max: new1RM,
            last_updated: now,
          })
          .eq('id', row.id);
      } else {
        await supabase
          .from('exercise_results')
          .insert({
            user_id: userId,
            exercise_id: exerciseId,
            max_weight: maxWeight,
            max_reps: maxReps,
            one_rep_max: oneRepMax,
            last_updated: now,
          });
      }
    } catch (e) {
      console.warn('[RecordAttemptService] upsertExerciseResult non-blocking warning:', e);
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
   * Marks the attempt as abandoned and propagates database errors.
   */
  async abandonAttempt(attemptId: string): Promise<void> {
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
      console.error('[RecordAttemptService] abandonAttempt error:', error);
      throw error;
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

