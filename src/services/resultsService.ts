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
  status?: string;
  isSuccess?: boolean;
}

export interface YearGroupedHistory {
  year: string;
  sessions: SessionHistoryItem[];
}

export interface ChartDataPoint {
  x: number; // 0 to 100
  y: number; // 20 to 110
  date: string;
  value: number;
  formattedValue: string;
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
        const resultType = RecordAttemptService.getExerciseResultType(undefined, category, name, row.result_type);

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
        const resultType = RecordAttemptService.getExerciseResultType(undefined, category, name, row.result_type);
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
   * Fetches full history, truthful performance metrics and computes 90-day progression for ANY exercise type.
   * STRICT SEPARATION: Failed/abandoned attempts appear in attempt history only and NEVER contribute
   * to maxWeight, maxReps, 1RM, bestValueDisplay, or chart progression points.
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

      // 2. Fetch all attempts and sets
      const attempts = isUUID ? await RecordAttemptService.fetchAttemptsForExercise(userId, exerciseId, 100) : [];
      const attemptIds = attempts.map((a: any) => a.id);
      const setsMap = await RecordAttemptService.fetchMainSetsForAttempts(attemptIds);

      // 3. Fetch full PR history without row clipping
      const prList = isUUID ? await RecordAttemptService.fetchPersonalRecordsForExercise(userId, exerciseId) : [];
      const bestPR = RecordAttemptService.findBestHistoricalRecord(prList, resultType);

      // 4. Build Valid Performance List (ONLY successful completed performances)
      interface ValidPerformance {
        timestamp: number;
        date: string;
        weightKg: number;
        reps: number;
        epley1RM: number;
        elapsedSeconds: number;
        metricValue: number; // The primary numeric metric for chart progression
        formattedValue: string;
      }

      const validPerformances: ValidPerformance[] = [];

      // Add valid PR records
      prList.forEach((pr) => {
        const dateStr = (pr.record_date || '').slice(0, 10);
        const timestamp = new Date(pr.record_date || 0).getTime();
        const m = RecordAttemptService.extractNormalizedMetrics(pr, resultType);

        let metricVal = m.epley1RM || m.weightKg;
        let formatted = `${m.weightKg} kg`;

        if (resultType === 'reps') {
          metricVal = m.reps;
          formatted = `${m.reps} tekrar`;
        } else if (resultType === 'amrap') {
          metricVal = m.reps;
          formatted = `${m.reps} tur`;
        } else if (
          resultType === 'running' ||
          resultType === 'fixed_distance_time' ||
          resultType === 'fixed_calorie_time' ||
          resultType === 'benchmark_time'
        ) {
          metricVal = m.elapsedSeconds;
          const min = Math.floor(m.elapsedSeconds / 60);
          const s = m.elapsedSeconds % 60;
          formatted = `${min}:${s.toString().padStart(2, '0')}`;
        }

        if (metricVal > 0) {
          validPerformances.push({
            timestamp,
            date: dateStr,
            weightKg: m.weightKg,
            reps: m.reps,
            epley1RM: m.epley1RM,
            elapsedSeconds: m.elapsedSeconds,
            metricValue: metricVal,
            formattedValue: formatted,
          });
        }
      });

      // Add successful completed attempts (strictly excluding failed/abandoned)
      attempts
        .filter((att) => att.status === 'completed' && att.success === true)
        .forEach((att) => {
          const dateStr = (att.completed_at || att.created_at || '').slice(0, 10);
          const timestamp = new Date(att.completed_at || att.created_at || 0).getTime();
          const mainSet = setsMap[att.id];
          const m = RecordAttemptService.extractNormalizedMetrics(
            {
              ...att,
              actual_weight: mainSet?.actual_weight,
              actual_reps: mainSet?.actual_reps,
            },
            resultType
          );

          let metricVal = m.epley1RM || m.weightKg;
          let formatted = `${m.weightKg} kg`;

          if (resultType === 'reps') {
            metricVal = m.reps;
            formatted = `${m.reps} tekrar`;
          } else if (resultType === 'amrap') {
            metricVal = m.reps;
            formatted = `${m.reps} tur`;
          } else if (
            resultType === 'running' ||
            resultType === 'fixed_distance_time' ||
            resultType === 'fixed_calorie_time' ||
            resultType === 'benchmark_time'
          ) {
            metricVal = m.elapsedSeconds;
            const min = Math.floor(m.elapsedSeconds / 60);
            const s = m.elapsedSeconds % 60;
            formatted = `${min}:${s.toString().padStart(2, '0')}`;
          }

          const alreadyPresent = validPerformances.some(
            (p) => Math.abs(p.timestamp - timestamp) < 60000 && p.metricValue === metricVal
          );

          if (!alreadyPresent && metricVal > 0) {
            validPerformances.push({
              timestamp,
              date: dateStr,
              weightKg: m.weightKg,
              reps: m.reps,
              epley1RM: m.epley1RM,
              elapsedSeconds: m.elapsedSeconds,
              metricValue: metricVal,
              formattedValue: formatted,
            });
          }
        });

      // 5. Calculate Truthful Performance Best Metrics (from validPerformances ONLY)
      let maxWeight = 0;
      let maxReps = 0;
      let oneRM = 0;

      if (bestPR) {
        const m = RecordAttemptService.extractNormalizedMetrics(bestPR, resultType);
        maxWeight = m.weightKg;
        maxReps = m.reps;
        oneRM = m.epley1RM;
      }

      validPerformances.forEach((p) => {
        if (isWeight) {
          if (p.weightKg > maxWeight) maxWeight = p.weightKg;
          if (p.epley1RM > oneRM) oneRM = p.epley1RM;
          if (p.reps > maxReps) maxReps = p.reps;
        } else {
          if (p.reps > maxReps) maxReps = p.reps;
        }
      });

      if (isWeight && maxWeight > 0 && oneRM === 0) {
        oneRM = RecordAttemptService.epley1RM(maxWeight, maxReps || 1);
      }

      let bestValueDisplay = '-';
      if (bestPR) {
        bestValueDisplay = formatPRDisplayValue({ ...bestPR, exercise: { name: exName } });
      } else if (validPerformances.length > 0) {
        if (isWeight) {
          bestValueDisplay = `${maxWeight} kg`;
        } else {
          const sorted = [...validPerformances].sort((a, b) => {
            if (
              resultType === 'running' ||
              resultType === 'fixed_distance_time' ||
              resultType === 'fixed_calorie_time' ||
              resultType === 'benchmark_time'
            ) {
              return a.metricValue - b.metricValue; // lower time is better
            }
            return b.metricValue - a.metricValue; // higher reps/rounds is better
          });
          bestValueDisplay = sorted[0].formattedValue;
        }
      }

      const percentages = isWeight
        ? [
            { p: 100, val: oneRM },
            { p: 90, val: oneRM * 0.9 },
            { p: 80, val: oneRM * 0.8 },
            { p: 70, val: oneRM * 0.7 },
            { p: 60, val: oneRM * 0.6 },
            { p: 50, val: oneRM * 0.5 },
          ]
        : [];

      // 6. Generate Real 90-day Chart Data for ANY result type (using valid performances ONLY)
      const now = Date.now();
      const ninetyDaysAgo = now - 90 * 24 * 60 * 60 * 1000;
      const recentPerformances = validPerformances
        .filter((p) => p.timestamp >= ninetyDaysAgo && p.metricValue > 0)
        .sort((a, b) => a.timestamp - b.timestamp);

      const chartPointsList: ChartDataPoint[] = [];
      let chartPointsString = '';

      if (recentPerformances.length >= 1) {
        const isLowerBetter =
          resultType === 'running' ||
          resultType === 'fixed_distance_time' ||
          resultType === 'fixed_calorie_time' ||
          resultType === 'benchmark_time';

        const values = recentPerformances.map((p) => p.metricValue);
        const minVal = Math.min(...values);
        const maxVal = Math.max(...values);
        const rangeVal = Math.max(1, maxVal - minVal);

        const pts = recentPerformances.map((p, idx) => {
          const timeProgress =
            recentPerformances.length === 1
              ? 0.5
              : Math.max(0, Math.min(1, (p.timestamp - ninetyDaysAgo) / (now - ninetyDaysAgo)));
          const x = Math.round(timeProgress * 100);

          // If lower time is better: faster time (minVal) gives higher visual Y (top = 20), slower gives lower Y (bottom = 110)
          let y = 65;
          if (rangeVal > 0) {
            if (isLowerBetter) {
              y = Math.round(20 + ((p.metricValue - minVal) / rangeVal) * 90);
            } else {
              y = Math.round(110 - ((p.metricValue - minVal) / rangeVal) * 90);
            }
          }

          return {
            x,
            y,
            date: p.date,
            value: p.metricValue,
            formattedValue: p.formattedValue,
          };
        });

        chartPointsList.push(...pts);
        chartPointsString = pts.map((p) => `${p.x},${p.y}`).join(' ');
      }

      // 7. Build Full Session History (Truthfully shows completed, failed, abandoned attempts)
      const sessionList: SessionHistoryItem[] = [];

      attempts.forEach((att) => {
        const dateStr = (att.completed_at || att.created_at || '').slice(0, 10);
        const timestamp = new Date(att.completed_at || att.created_at || 0).getTime();
        const isSuccess = att.status === 'completed' && att.success === true;
        const isAbandoned = att.status === 'abandoned';
        const isFailed = att.status === 'failed' || !att.success;

        const mainSet = setsMap[att.id];
        const w = Number(mainSet?.actual_weight ?? att.target_weight ?? 0);
        const r = Number(mainSet?.actual_reps ?? att.target_reps ?? 1);

        let setsDesc = `${w} kg × ${r} tekrar`;
        let valDisplay = `${w} kg`;

        if (!isWeight) {
          setsDesc = att.notes || 'Tamamlandı';
          valDisplay = att.notes || 'Tamamlandı';
        }

        if (isAbandoned) {
          setsDesc = 'Deneme Bırakıldı';
          valDisplay = 'Bırakıldı';
        } else if (isFailed) {
          setsDesc = 'Başarısız Deneme';
          valDisplay = 'Başarısız';
        }

        sessionList.push({
          date: dateStr,
          sets: setsDesc,
          weight: isSuccess && isWeight ? w : 0,
          valueDisplay: valDisplay,
          timestamp,
          status: att.status,
          isSuccess,
        });
      });

      // Include PR rows if not already represented
      prList.forEach((pr) => {
        const dateStr = (pr.record_date || '').slice(0, 10);
        const timestamp = new Date(pr.record_date || 0).getTime();
        const w = Number(pr.weight_kg ?? pr.weight ?? 0);
        const r = Number(pr.reps ?? 1);

        const alreadyPresent = sessionList.some(
          (s) => Math.abs(s.timestamp - timestamp) < 60000 && (s.weight === w || !isWeight)
        );

        if (!alreadyPresent) {
          sessionList.push({
            date: dateStr,
            sets: isWeight ? `Rekor: ${w} kg × ${r}` : (pr.notes || 'Kişisel Rekor'),
            weight: w,
            valueDisplay: formatPRDisplayValue({ ...pr, exercise: { name: exName } }),
            timestamp,
            status: 'completed',
            isSuccess: true,
          });
        }
      });

      // Sort history descending by date
      sessionList.sort((a, b) => b.timestamp - a.timestamp);

      // Group by year (no fake '2026' fallback)
      const yearMap = new Map<string, SessionHistoryItem[]>();
      sessionList.forEach((item) => {
        const yr = item.date && item.date.length >= 4 ? item.date.slice(0, 4) : 'Diğer';
        if (!yearMap.has(yr)) {
          yearMap.set(yr, []);
        }
        yearMap.get(yr)!.push(item);
      });

      const history: YearGroupedHistory[] = Array.from(yearMap.entries()).map(([year, sessions]) => ({
        year,
        sessions,
      }));

      return {
        exerciseId,
        exerciseName: exName,
        category: exCategory,
        resultType,
        isWeight,
        maxWeight,
        maxReps: maxReps || 1,
        oneRM,
        bestValueDisplay,
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
        chartPointsString: '',
        chartPointsList: [],
        history: [],
      };
    }
  },
};
