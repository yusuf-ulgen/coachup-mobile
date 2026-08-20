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
  result_type?: string | null;
  elapsed_seconds?: number | null;
  distance_km?: number | null;
  target_calories?: number | null;
  rounds?: number | null;
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
  result_type?: string | null;
  elapsed_seconds?: number | null;
  distance_km?: number | null;
  target_calories?: number | null;
  rounds?: number | null;
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
   * Resolves target distance in KM from catalog identifier without fake fallbacks.
   */
  runningTargetKm(catalogId?: string): number | null {
    const id = (catalogId || '').toLowerCase().trim();
    if (id === 'run_full' || id.includes('42')) return 42.195;
    if (id === 'run_half' || id.includes('21')) return 21.0975;
    if (id === 'run_10k' || id.includes('10k') || id.includes('10_km')) return 10.0;
    if (id === 'run_5k' || id.includes('5k') || id.includes('5_km')) return 5.0;
    if (id === 'run_3k' || id.includes('3k') || id.includes('3_km')) return 3.0;
    if (id === 'run_1k' || id.endsWith('_1k') || id.includes('1_km')) return 1.0;
    if (id === 'run_800m' || id.includes('800')) return 0.8;
    if (id === 'run_400m' || id.includes('400')) return 0.4;
    return null;
  },

  /**
   * Resolves target distance in KM for fixed-distance cardio (Row, SkiErg) deterministically.
   * Checks longer/specific matches first so row_5000m is never misidentified as row_500m.
   */
  cardioTargetDistanceKm(catalogId?: string): number | null {
    const id = (catalogId || '').toLowerCase().trim();
    if (id === 'row_5000m' || id.endsWith('5000m') || id.includes('5000m') || id.includes('5000')) return 5.0;
    if (id === 'row_2000m' || id.endsWith('2000m') || id.includes('2000m') || id.includes('2000')) return 2.0;
    if (id === 'row_1000m' || id.endsWith('1000m') || id.includes('1000m') || id.includes('1000') || id === 'ski_1000m') return 1.0;
    if (id === 'row_500m' || id.endsWith('500m') || id.includes('500m') || id.includes('500')) return 0.5;
    return null;
  },

  /**
   * Resolves target calories for fixed-calorie challenges (Assault Bike, Echo Bike).
   */
  cardioTargetCalories(catalogId?: string): number | null {
    const id = (catalogId || '').toLowerCase().trim();
    if (id === 'echo_100cal' || id.includes('100cal') || id.includes('100_cal')) return 100;
    if (id === 'bike_50cal' || id.includes('50cal') || id.includes('50_cal')) return 50;
    if (id === 'bike_10cal' || id.includes('10cal') || id.includes('10_cal')) return 10;
    return null;
  },

  /**
   * Resolves AMRAP time cap in seconds (default: 20 min = 1200s).
   */
  amrapCapSeconds(catalogId?: string): number {
    const id = (catalogId || '').toLowerCase().trim();
    if (id.includes('10')) return 10 * 60;
    if (id.includes('15')) return 15 * 60;
    if (id.includes('30')) return 30 * 60;
    return 20 * 60;
  },

  /**
   * Resolves canonical RecordResultType with strict priority and name/category compatibility.
   */
  getExerciseResultType(
    catalogId?: string,
    categoryId?: string,
    exerciseName?: string,
    persistedType?: string
  ): RecordResultType {
    if (
      persistedType &&
      ['weight', 'reps', 'running', 'fixed_distance_time', 'fixed_calorie_time', 'benchmark_time', 'amrap'].includes(persistedType)
    ) {
      return persistedType as RecordResultType;
    }

    const id = (catalogId || '').toLowerCase().trim();
    const cat = (categoryId || '').toLowerCase().trim();
    const name = (exerciseName || '').toLowerCase().trim();

    // 1. Explicit catalog ID matches
    if (id.includes('cindy') || name.includes('cindy') || id.includes('amrap') || name.includes('amrap')) {
      return 'amrap';
    }
    if (id.startsWith('run_') || id.includes('koşu') || id.includes('maraton')) {
      return 'running';
    }
    if (id.startsWith('row_') || id.startsWith('ski_')) {
      return 'fixed_distance_time';
    }
    if (id.startsWith('bike_') || id.startsWith('echo_')) {
      return 'fixed_calorie_time';
    }

    const benchmarkIds = ['fran', 'grace', 'isabel', 'jackie', 'helen', 'dane', 'diane', 'murph', 'kalsu', 'dt', 'linda'];
    if (benchmarkIds.some((b) => id === b || id.startsWith(`${b}_`))) {
      return 'benchmark_time';
    }

    const bodyweightIds = ['pull_up', 'chest_to_bar', 'bar_muscle_up', 'ring_muscle_up', 'push_up', 'dips', 'hspu', 'toes_to_bar'];
    if (bodyweightIds.some((b) => id === b)) {
      return 'reps';
    }

    const strengthIds = ['back_squat', 'front_squat', 'deadlift', 'bench_press', 'strict_press', 'push_press', 'thruster', 'overhead_squat', 'power_clean', 'squat_clean', 'power_snatch', 'squat_snatch', 'clean_jerk'];
    if (strengthIds.some((s) => id === s)) {
      return 'weight';
    }

    // 2. Canonical category metadata
    if (cat === 'running' || cat === 'koşu') return 'running';
    if (cat === 'bodyweight' || cat === 'vücut ağırlığı') return 'reps';
    if (cat === 'benchmark') {
      if (name.includes('cindy')) return 'amrap';
      return 'benchmark_time';
    }
    if (cat === 'cardio' || cat === 'kardiyo') {
      if (id.includes('cal') || name.includes('cal') || name.includes('kalori')) return 'fixed_calorie_time';
      return 'fixed_distance_time';
    }
    if (cat === 'strength' || cat === 'güç') return 'weight';

    // 3. Known exercise name substring matches
    if (name.includes('koşu') || name.includes('run') || name.includes('maraton')) return 'running';
    if (name.includes('row') || name.includes('kürek') || name.includes('skierg') || name.includes('ski')) return 'fixed_distance_time';
    if (name.includes('bike') || name.includes('bisiklet') || name.includes('cal') || name.includes('kalori')) return 'fixed_calorie_time';
    if (benchmarkIds.some((b) => name.includes(b))) return 'benchmark_time';
    if (name.includes('barfiks') || name.includes('pull-up') || name.includes('push-up') || name.includes('şınav') || name.includes('dips')) return 'reps';

    // 4. Safe fallback
    return 'weight';
  },

  /**
   * Extract normalized numeric metrics from any record/attempt/history item with typed priority.
   */
  extractNormalizedMetrics(record: any, resultType: RecordResultType) {
    const notes = (record?.notes || '').trim();
    const effectiveType = (record?.result_type as RecordResultType) || resultType;

    const typedElapsed =
      record?.elapsed_seconds !== undefined && record?.elapsed_seconds !== null
        ? Number(record.elapsed_seconds)
        : null;
    const typedDistance =
      record?.distance_km !== undefined && record?.distance_km !== null
        ? Number(record.distance_km)
        : null;
    const typedCalories =
      record?.target_calories !== undefined && record?.target_calories !== null
        ? Number(record.target_calories)
        : null;
    const typedRounds =
      record?.rounds !== undefined && record?.rounds !== null
        ? Number(record.rounds)
        : null;

    const weightKg =
      effectiveType === 'weight'
        ? Number(
            record?.weight_kg ??
            record?.weight ??
            record?.target_weight ??
            record?.actual_weight ??
            0
          )
        : 0;

    let reps = Number(
      record?.reps ??
      record?.actual_reps ??
      record?.target_reps ??
      1
    );
    if (typedRounds !== null && typedRounds > 0) {
      reps = typedRounds;
    }

    let elapsedSeconds = typedElapsed;

    // Legacy fallback: check time_seconds or notes
    if (elapsedSeconds === null || elapsedSeconds === undefined) {
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
    }

    if (effectiveType === 'amrap' && typedRounds === null) {
      const roundsMatch = notes.match(/(\d+)\s*tur/i);
      if (roundsMatch) {
        reps = parseInt(roundsMatch[1], 10);
      }
    }

    const epley = effectiveType === 'weight' ? this.epley1RM(weightKg, reps) : 0;

    return {
      resultType: effectiveType,
      weightKg,
      reps,
      elapsedSeconds: elapsedSeconds || 0,
      distanceKm: typedDistance,
      targetCalories: typedCalories,
      rounds: typedRounds ?? (effectiveType === 'amrap' ? reps : null),
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
        distanceKm: candidateMetrics.distanceKm ?? undefined,
        targetCalories: candidateMetrics.targetCalories ?? undefined,
      };
      if (this.isBetterRecord(candidatePayload, best)) {
        best = candidate;
      }
    }
    return best;
  },

  /**
   * Evaluates if attempt qualifies as a new PR, and inserts ONLY if strictly better.
   * Scans full user PR history for this exercise so older best records are never missed.
   */
  async evaluateAndSavePersonalRecord(
    userId: string,
    exerciseId: string,
    resultPayload: RecordResultPayload
  ): Promise<PREvaluationResult> {
    const records = await this.fetchPersonalRecordsForExercise(userId, exerciseId);
    const bestPrevious = this.findBestHistoricalRecord(records, resultPayload.resultType);
    const isNewPR = this.isBetterRecord(resultPayload, bestPrevious);
    const epley =
      resultPayload.resultType === 'weight'
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

      const saved = await this.savePersonalRecord(userId, exerciseId, {
        resultType: resultPayload.resultType,
        weightKg: resultPayload.resultType === 'weight' ? (resultPayload.weightKg || 0) : 0,
        reps: resultPayload.reps || 1,
        elapsedSeconds: resultPayload.elapsedSeconds ?? null,
        distanceKm: resultPayload.distanceKm ?? null,
        targetCalories: resultPayload.targetCalories ?? null,
        rounds: resultPayload.resultType === 'amrap' ? (resultPayload.reps || null) : null,
        notes,
      });

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
   * If limit is not specified, fetches all records for this user+exercise so best calculation is never clipped.
   */
  async fetchPersonalRecordsForExercise(
    userId: string,
    exerciseId: string,
    limit?: number
  ): Promise<any[]> {
    try {
      let query = supabase
        .from('personal_records')
        .select('*')
        .eq('user_id', userId)
        .eq('exercise_id', exerciseId)
        .order('record_date', { ascending: false });

      if (limit && limit > 0) {
        query = query.limit(limit);
      }

      const { data, error } = await query;
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
        .eq('set_type', 'main');

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
   * Explicitly checks and reports errors from both select/update/insert paths.
   */
  async upsertExerciseResult(
    userId: string,
    exerciseId: string,
    maxWeight: number,
    maxReps: number,
    oneRepMax: number
  ): Promise<{ success: boolean; error?: any }> {
    try {
      const { data: existing, error: selectErr } = await supabase
        .from('exercise_results')
        .select('id, max_weight, max_reps, one_rep_max')
        .eq('user_id', userId)
        .eq('exercise_id', exerciseId)
        .limit(1);

      if (selectErr) {
        console.warn('[RecordAttemptService] upsertExerciseResult select warning:', selectErr);
      }

      const now = new Date().toISOString();
      if (existing && existing.length > 0) {
        const row = existing[0];
        const newWeight = Math.max(Number(row.max_weight || 0), maxWeight);
        const newReps = maxWeight >= Number(row.max_weight || 0) ? maxReps : Number(row.max_reps || 1);
        const new1RM = Math.max(Number(row.one_rep_max || 0), oneRepMax);
        const { error: updateErr } = await supabase
          .from('exercise_results')
          .update({
            max_weight: newWeight,
            max_reps: newReps,
            one_rep_max: new1RM,
            last_updated: now,
          })
          .eq('id', row.id);

        if (updateErr) {
          console.warn('[RecordAttemptService] upsertExerciseResult update error:', updateErr);
          return { success: false, error: updateErr };
        }
      } else {
        const { error: insertErr } = await supabase
          .from('exercise_results')
          .insert({
            user_id: userId,
            exercise_id: exerciseId,
            max_weight: maxWeight,
            max_reps: maxReps,
            one_rep_max: oneRepMax,
            last_updated: now,
          });

        if (insertErr) {
          console.warn('[RecordAttemptService] upsertExerciseResult insert error:', insertErr);
          return { success: false, error: insertErr };
        }
      }
      return { success: true };
    } catch (e) {
      console.warn('[RecordAttemptService] upsertExerciseResult exception:', e);
      return { success: false, error: e };
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
   * Preserves semantic separation: actual_weight is strictly physical weight in kg.
   */
  async saveSet(
    setId: string,
    paramsOrWeight?:
      | {
          actualWeight?: number | null;
          actualReps?: number | null;
          rpe?: number | null;
          restSeconds?: number | null;
          isCompleted?: boolean;
          resultType?: RecordResultType;
          elapsedSeconds?: number | null;
          distanceKm?: number | null;
          targetCalories?: number | null;
          rounds?: number | null;
          notes?: string | null;
        }
      | number
      | null,
    legacyActualReps?: number | null,
    legacyRpe?: number | null,
    legacyRestSeconds?: number | null,
    legacyNotes?: string | null
  ): Promise<RecordAttemptSet> {
    const now = new Date().toISOString();
    const payload: any = {
      completed_at: now,
      is_completed: true,
    };

    if (typeof paramsOrWeight === 'object' && paramsOrWeight !== null) {
      payload.is_completed = paramsOrWeight.isCompleted ?? true;
      if (paramsOrWeight.actualWeight !== undefined) payload.actual_weight = paramsOrWeight.actualWeight;
      if (paramsOrWeight.actualReps !== undefined) payload.actual_reps = paramsOrWeight.actualReps;
      if (paramsOrWeight.rpe !== undefined && paramsOrWeight.rpe !== null) payload.rpe = paramsOrWeight.rpe;
      if (paramsOrWeight.restSeconds !== undefined && paramsOrWeight.restSeconds !== null)
        payload.rest_seconds = paramsOrWeight.restSeconds;
      if (paramsOrWeight.notes !== undefined && paramsOrWeight.notes !== null) payload.notes = paramsOrWeight.notes;
    } else {
      if (paramsOrWeight !== undefined && paramsOrWeight !== null) payload.actual_weight = paramsOrWeight;
      if (legacyActualReps !== undefined && legacyActualReps !== null) payload.actual_reps = legacyActualReps;
      if (legacyRpe !== undefined && legacyRpe !== null) payload.rpe = legacyRpe;
      if (legacyRestSeconds !== undefined && legacyRestSeconds !== null) payload.rest_seconds = legacyRestSeconds;
      if (legacyNotes !== undefined && legacyNotes !== null) payload.notes = legacyNotes;
    }

    try {
      const { data, error } = await supabase
        .from('record_attempt_sets')
        .update(payload)
        .eq('id', setId)
        .select('*')
        .single();

      if (error) throw error;
      return data;
    } catch (e) {
      console.error('[RecordAttemptService] saveSet error:', e);
      throw e;
    }
  },

  /**
   * Finalizes the record attempt with 'completed' or 'failed' status, notes, and typed metrics.
   */
  async completeAttempt(
    attemptId: string,
    success: boolean,
    notes?: string | null,
    userId?: string | null,
    resultMetrics?: {
      resultType?: RecordResultType;
      elapsedSeconds?: number | null;
      distanceKm?: number | null;
      targetCalories?: number | null;
      rounds?: number | null;
    }
  ): Promise<RecordAttempt> {
    const now = new Date().toISOString();
    const payload: any = {
      status: success ? 'completed' : 'failed',
      success: success,
      notes: notes || null,
      completed_at: now,
    };

    try {
      const { data, error } = await supabase
        .from('record_attempts')
        .update(payload)
        .eq('id', attemptId)
        .select('*')
        .single();

      if (error) throw error;

      if (success && userId) {
        StreakService.fetchStreakData(userId).catch((err: any) => {
          console.warn('[RecordAttemptService] Streak sync background warning:', err);
        });
      }
      return data;
    } catch (e) {
      console.error('[RecordAttemptService] completeAttempt error:', e);
      throw e;
    }
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
   * Inserts a record into personal_records table with canonical columns.
   */
  async savePersonalRecord(
    userId: string,
    exerciseId: string,
    paramsOrWeight?:
      | {
          weightKg?: number | null;
          weight?: number | null;
          reps?: number | null;
          notes?: string | null;
          resultType?: RecordResultType;
          elapsedSeconds?: number | null;
          distanceKm?: number | null;
          targetCalories?: number | null;
          rounds?: number | null;
        }
      | number
      | null,
    legacyReps?: number | null,
    legacyNotes?: string | null
  ): Promise<any> {
    const now = new Date().toISOString();
    let weight = 0;
    let reps = 1;
    let notes: string | null = null;

    if (typeof paramsOrWeight === 'object' && paramsOrWeight !== null) {
      weight = paramsOrWeight.weight ?? paramsOrWeight.weightKg ?? 0;
      reps = paramsOrWeight.reps ?? 1;
      notes = paramsOrWeight.notes || null;
    } else {
      weight = paramsOrWeight ?? 0;
      reps = legacyReps ?? 1;
      notes = legacyNotes || null;
    }

    const payload = {
      user_id: userId,
      exercise_id: exerciseId,
      weight,
      reps,
      record_date: now,
      notes,
    };

    try {
      const { data, error } = await supabase
        .from('personal_records')
        .insert(payload)
        .select('*')
        .single();

      if (error) throw error;
      return data;
    } catch (err) {
      console.error('[RecordAttemptService] savePersonalRecord error:', err);
      throw err;
    }
  },
};

