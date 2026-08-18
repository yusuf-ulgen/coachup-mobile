import { supabase } from './supabaseClient';
import { RecordAttemptService, RecordResultType } from './recordAttemptService';
import { RECORD_ATTEMPT_CATEGORIES } from '../models/recordAttemptCategories';
import { formatPRDisplayValue } from '../utils/recordFormatters';

export interface ExerciseResultItem {
  id: string;
  exerciseId: string;
  name: string;
  category: string;
  measureType: RecordResultType;
  bestDisplay: string;
  maxWeight: number;
  maxReps: number;
  oneRepMax: number;
  lastUpdated?: string;
}

export interface ResultsSummary {
  totalWorkouts: number;
  maxWeight: number;
  max1RM: number;
}

export interface SessionHistoryItem {
  date: string;
  sets: string;
  weight: number;
  valueDisplay: string;
  timestamp: number;
}

export interface YearGroupedHistory {
  year: string;
  sessions: SessionHistoryItem[];
}

export interface ChartDataPoint {
  x: number; // 0 to 100
  y: number; // 0 to 120
  date: string;
  value: number;
}

export interface ExerciseDetailData {
  exerciseId: string;
  exerciseName: string;
  category: string;
  resultType: RecordResultType;
  isWeight: boolean;
  maxWeight: number;
  maxReps: number;
  oneRM: number;
  bestValueDisplay: string;
  percentages: { p: number; val: number }[];
  chartPointsString: string;
  chartPointsList: ChartDataPoint[];
  history: YearGroupedHistory[];
}

export const ResultsService = {
  /**
   * Fetches the user's exercises with real results and upper summary stats.
   */
  async fetchUserResults(userId: string): Promise<{ summary: ResultsSummary; results: ExerciseResultItem[] }> {
    if (!userId) {
      return { summary: { totalWorkouts: 0, maxWeight: 0, max1RM: 0 }, results: [] };
    }

    try {
      // 1. Fetch personal records with exercise metadata
      const { data: prData, error: prError } = await supabase
        .from('personal_records')
        .select('*, exercise:exercises(id, name, category, equipment)')
        .eq('user_id', userId)
        .order('record_date', { ascending: false });

      if (prError) {
        console.warn('[ResultsService] fetchUserResults prError:', prError);
      }

      // 2. Fetch exercise_results
      const { data: erData, error: erError } = await supabase
        .from('exercise_results')
        .select('*, exercise:exercises(id, name, category, equipment)')
        .eq('user_id', userId);

      if (erError) {
        console.warn('[ResultsService] fetchUserResults erError:', erError);
      }

      const exerciseMap = new Map<string, ExerciseResultItem>();
      let globalMaxWeight = 0;
      let globalMax1RM = 0;

      // Process exercise_results (strength 1RM metrics)
      (erData || []).forEach((row: any) => {
        const ex = row.exercise;
        const exId = row.exercise_id || ex?.id;
        if (!exId) return;

        const maxW = Number(row.max_weight || 0);
        const maxR = Number(row.max_reps || 1);
        const oneRM = Number(row.one_rep_max || RecordAttemptService.epley1RM(maxW, maxR));

        if (maxW > globalMaxWeight) globalMaxWeight = maxW;
        if (oneRM > globalMax1RM) globalMax1RM = oneRM;

        const name = ex?.name || 'Egzersiz';
        const category = ex?.category || 'Genel';
        const resultType = RecordAttemptService.getExerciseResultType(undefined, category, name);

        exerciseMap.set(exId, {
          id: exId,
          exerciseId: exId,
          name,
          category,
          measureType: resultType,
          bestDisplay: `${maxW} kg (${maxR} tekrar)`,
          maxWeight: maxW,
          maxReps: maxR,
          oneRepMax: oneRM,
          lastUpdated: row.last_updated || row.created_at,
        });
      });

      // Process personal_records (includes cardio, running, benchmark, amrap, bodyweight)
      (prData || []).forEach((row: any) => {
        const ex = row.exercise;
        const exId = row.exercise_id || ex?.id;
        if (!exId) return;

        const name = ex?.name || 'Egzersiz';
        const category = ex?.category || 'Genel';
        const resultType = RecordAttemptService.getExerciseResultType(undefined, category, name);
        const metrics = RecordAttemptService.extractNormalizedMetrics(row, resultType);

        if (resultType === 'weight') {
          if (metrics.weightKg > globalMaxWeight) globalMaxWeight = metrics.weightKg;
          if (metrics.epley1RM > globalMax1RM) globalMax1RM = metrics.epley1RM;
        }

        const displayVal = formatPRDisplayValue({ ...row, exercise: ex });

        if (!exerciseMap.has(exId)) {
          exerciseMap.set(exId, {
            id: exId,
            exerciseId: exId,
            name,
            category,
            measureType: resultType,
            bestDisplay: displayVal,
            maxWeight: metrics.weightKg,
            maxReps: metrics.reps,
            oneRepMax: metrics.epley1RM,
            lastUpdated: row.record_date,
          });
        } else {
          // Compare if this PR is better than existing mapped item
          const existing = exerciseMap.get(exId)!;
          if (resultType === 'weight') {
            if (metrics.epley1RM > existing.oneRepMax) {
              existing.maxWeight = metrics.weightKg;
              existing.maxReps = metrics.reps;
              existing.oneRepMax = metrics.epley1RM;
              existing.bestDisplay = displayVal;
            }
          }
        }
      });

      const results = Array.from(exerciseMap.values());

      return {
        summary: {
          totalWorkouts: results.length,
          maxWeight: globalMaxWeight,
          max1RM: globalMax1RM,
        },
        results,
      };
    } catch (e) {
      console.error('[ResultsService] fetchUserResults exception:', e);
      return { summary: { totalWorkouts: 0, maxWeight: 0, max1RM: 0 }, results: [] };
    }
  },

  /**
   * Fetches all available catalog & DB exercises.
   */
  async fetchAllExercises(): Promise<ExerciseResultItem[]> {
    try {
      const dbExercises = await RecordAttemptService.fetchExercises();
      const items: ExerciseResultItem[] = [];
      const seenNames = new Set<string>();

      dbExercises.forEach((ex: any) => {
        const name = ex.name || '';
        if (!seenNames.has(name.toLowerCase())) {
          seenNames.add(name.toLowerCase());
          const cat = ex.category || 'Genel';
          items.push({
            id: ex.id,
            exerciseId: ex.id,
            name,
            category: cat,
            measureType: RecordAttemptService.getExerciseResultType(undefined, cat, name),
            bestDisplay: '-',
            maxWeight: 0,
            maxReps: 0,
            oneRepMax: 0,
          });
        }
      });

      // Include all catalog items not yet in DB list
      RECORD_ATTEMPT_CATEGORIES.forEach((cat) => {
        cat.exercises.forEach((ex) => {
          if (!seenNames.has(ex.name.toLowerCase())) {
            seenNames.add(ex.name.toLowerCase());
            items.push({
              id: ex.id,
              exerciseId: ex.id,
              name: ex.name,
              category: cat.name,
              measureType: RecordAttemptService.getExerciseResultType(ex.id, cat.id, ex.name),
              bestDisplay: '-',
              maxWeight: 0,
              maxReps: 0,
              oneRepMax: 0,
            });
          }
        });
      });

      return items;
    } catch (e) {
      console.error('[ResultsService] fetchAllExercises exception:', e);
      return [];
    }
  },

  /**
   * Fetches full history and computes 90-day chart data for a specific exercise.
   */
  async fetchExerciseDetail(
    userId: string,
    exerciseId: string,
    fallbackName?: string
  ): Promise<ExerciseDetailData> {
    try {
      // 1. Fetch exercise details
      let exName = fallbackName || 'Egzersiz Detayı';
      let exCategory = 'Genel';

      const isUUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(exerciseId);
      if (isUUID) {
        const { data: dbEx } = await supabase
          .from('exercises')
          .select('*')
          .eq('id', exerciseId)
          .single();
        if (dbEx) {
          exName = dbEx.name;
          exCategory = dbEx.category || 'Genel';
        }
      }

      const resultType = RecordAttemptService.getExerciseResultType(undefined, exCategory, exName);
      const isWeight = resultType === 'weight';

      // 2. Fetch all completed attempts with sets
      const attempts = isUUID ? await RecordAttemptService.fetchAttemptsForExercise(userId, exerciseId, 50) : [];
      const attemptIds = attempts.map((a: any) => a.id);
      const setsMap = await RecordAttemptService.fetchMainSetsForAttempts(attemptIds);

      // 3. Fetch personal records
      const prList = isUUID ? await RecordAttemptService.fetchPersonalRecordsForExercise(userId, exerciseId, 50) : [];
      const bestPR = RecordAttemptService.findBestHistoricalRecord(prList, resultType);

      // 4. Build session history items
      const sessionList: SessionHistoryItem[] = [];

      attempts.forEach((att) => {
        const dateStr = (att.completed_at || att.created_at || '').slice(0, 10);
        const timestamp = new Date(att.completed_at || att.created_at || 0).getTime();
        const mainSet = setsMap[att.id];
        const w = Number(mainSet?.actual_weight ?? att.target_weight ?? 0);
        const r = Number(mainSet?.actual_reps ?? att.target_reps ?? 1);

        let setsDesc = `${w} kg × ${r} tekrar`;
        let valDisplay = `${w} kg`;

        if (!isWeight) {
          setsDesc = att.notes || 'Tamamlandı';
          valDisplay = att.notes || 'Tamamlandı';
        }

        sessionList.push({
          date: dateStr,
          sets: setsDesc,
          weight: w,
          valueDisplay: valDisplay,
          timestamp,
        });
      });

      // Also include PR rows in history if not duplicate
      prList.forEach((pr) => {
        const dateStr = (pr.record_date || '').slice(0, 10);
        const timestamp = new Date(pr.record_date || 0).getTime();
        const w = Number(pr.weight_kg ?? pr.weight ?? 0);
        const r = Number(pr.reps ?? 1);

        const alreadyPresent = sessionList.some(
          (s) => Math.abs(s.timestamp - timestamp) < 60000 && s.weight === w
        );

        if (!alreadyPresent) {
          sessionList.push({
            date: dateStr,
            sets: isWeight ? `Rekor: ${w} kg × ${r}` : (pr.notes || 'Kişisel Rekor'),
            weight: w,
            valueDisplay: formatPRDisplayValue({ ...pr, exercise: { name: exName } }),
            timestamp,
          });
        }
      });

      // Sort history descending by date
      sessionList.sort((a, b) => b.timestamp - a.timestamp);

      // Group by year
      const yearMap = new Map<string, SessionHistoryItem[]>();
      sessionList.forEach((item) => {
        const yr = item.date ? item.date.slice(0, 4) : '2026';
        if (!yearMap.has(yr)) {
          yearMap.set(yr, []);
        }
        yearMap.get(yr)!.push(item);
      });

      const history: YearGroupedHistory[] = Array.from(yearMap.entries()).map(([year, sessions]) => ({
        year,
        sessions,
      }));

      // Calculate Best Values
      let maxWeight = 0;
      let maxReps = 0;
      let oneRM = 0;

      if (bestPR) {
        const m = RecordAttemptService.extractNormalizedMetrics(bestPR, resultType);
        maxWeight = m.weightKg;
        maxReps = m.reps;
        oneRM = m.epley1RM;
      }

      sessionList.forEach((s) => {
        if (s.weight > maxWeight) maxWeight = s.weight;
      });

      if (maxWeight > 0 && oneRM === 0) {
        oneRM = RecordAttemptService.epley1RM(maxWeight, maxReps || 1);
      }

      const percentages = [
        { p: 100, val: oneRM },
        { p: 90, val: oneRM * 0.9 },
        { p: 80, val: oneRM * 0.8 },
        { p: 70, val: oneRM * 0.7 },
        { p: 60, val: oneRM * 0.6 },
        { p: 50, val: oneRM * 0.5 },
      ];

      // 5. Generate 90-day chart data
      const now = Date.now();
      const ninetyDaysAgo = now - 90 * 24 * 60 * 60 * 1000;
      const recentSessions = sessionList
        .filter((s) => s.timestamp >= ninetyDaysAgo && s.weight > 0)
        .sort((a, b) => a.timestamp - b.timestamp);

      const chartPointsList: ChartDataPoint[] = [];
      let chartPointsString = '0,100';

      if (recentSessions.length > 0) {
        const minW = Math.min(...recentSessions.map((s) => s.weight)) * 0.8;
        const maxW = Math.max(...recentSessions.map((s) => s.weight)) * 1.1 || 100;
        const rangeW = Math.max(1, maxW - minW);

        const pts = recentSessions.map((s) => {
          const timeProgress = Math.max(0, Math.min(1, (s.timestamp - ninetyDaysAgo) / (now - ninetyDaysAgo)));
          const x = Math.round(timeProgress * 100);
          const y = Math.round(110 - ((s.weight - minW) / rangeW) * 90);
          return { x, y, date: s.date, value: s.weight };
        });

        chartPointsList.push(...pts);
        chartPointsString = pts.map((p) => `${p.x},${p.y}`).join(' ');
      }

      return {
        exerciseId,
        exerciseName: exName,
        category: exCategory,
        resultType,
        isWeight,
        maxWeight,
        maxReps: maxReps || 1,
        oneRM,
        bestValueDisplay: bestPR ? formatPRDisplayValue({ ...bestPR, exercise: { name: exName } }) : (maxWeight > 0 ? `${maxWeight} kg` : '-'),
        percentages,
        chartPointsString,
        chartPointsList,
        history,
      };
    } catch (e) {
      console.error('[ResultsService] fetchExerciseDetail exception:', e);
      return {
        exerciseId,
        exerciseName: fallbackName || 'Egzersiz Detayı',
        category: 'Genel',
        resultType: 'weight',
        isWeight: true,
        maxWeight: 0,
        maxReps: 0,
        oneRM: 0,
        bestValueDisplay: '-',
        percentages: [],
        chartPointsString: '0,100',
        chartPointsList: [],
        history: [],
      };
    }
  },
};
