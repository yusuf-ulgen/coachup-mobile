export enum RecordMeasureType {
  WEIGHT = 'WEIGHT',
  REPS = 'REPS',
  TIME = 'TIME',
  DISTANCE = 'DISTANCE',
  CALORIES = 'CALORIES',
}

export interface RecordExercise {
  id: string;
  name: string;
  measureType: RecordMeasureType;
  equipment?: string;
  defaultTarget?: number;
  defaultReps?: number;
}

export interface RecordCategory {
  id: string;
  name: string;
  exercises: RecordExercise[];
  exerciseCount: number;
}

const wb = (id: string, name: string): RecordExercise => ({
  id,
  name,
  measureType: RecordMeasureType.WEIGHT,
  defaultTarget: 100,
  defaultReps: 1,
});

const rep = (id: string, name: string, defaultReps: number = 10): RecordExercise => ({
  id,
  name,
  measureType: RecordMeasureType.REPS,
  defaultTarget: defaultReps,
  defaultReps,
});

const time = (id: string, name: string, defaultSec: number = 120): RecordExercise => ({
  id,
  name,
  measureType: RecordMeasureType.TIME,
  defaultTarget: defaultSec,
  defaultReps: 1,
});

const cal = (id: string, name: string, defaultCal: number = 50): RecordExercise => ({
  id,
  name,
  measureType: RecordMeasureType.CALORIES,
  defaultTarget: defaultCal,
  defaultReps: 1,
});

const CATEGORIES_DATA = [
  {
    id: 'strength',
    name: 'Güç Rekorları',
    exercises: [
      wb('back_squat', 'Back Squat'),
      wb('front_squat', 'Front Squat'),
      wb('deadlift', 'Deadlift'),
      wb('bench_press', 'Bench Press'),
      wb('strict_press', 'Strict Press'),
      wb('push_press', 'Push Press'),
      wb('thruster', 'Thruster'),
      wb('overhead_squat', 'Overhead Squat'),
      wb('power_clean', 'Power Clean'),
      wb('squat_clean', 'Squat Clean'),
      wb('power_snatch', 'Power Snatch'),
      wb('squat_snatch', 'Squat Snatch'),
      wb('clean_jerk', 'Clean & Jerk'),
    ],
  },
  {
    id: 'bodyweight',
    name: 'Vücut Ağırlığı Rekorları',
    exercises: [
      rep('pull_up', 'Pull-Up', 10),
      rep('chest_to_bar', 'Chest to Bar', 8),
      rep('bar_muscle_up', 'Bar Muscle-Up', 5),
      rep('ring_muscle_up', 'Ring Muscle-Up', 5),
      rep('push_up', 'Push-Up', 50),
      rep('dips', 'Dips', 20),
      rep('hspu', 'Handstand Push-Up', 10),
      rep('toes_to_bar', 'Toes to Bar', 15),
    ],
  },
  {
    id: 'running',
    name: 'Koşu Rekorları',
    exercises: [
      time('run_400m', '400m', 90),
      time('run_800m', '800m', 210),
      time('run_1k', '1 km', 300),
      time('run_3k', '3 km', 900),
      time('run_5k', '5 km', 1500),
      time('run_10k', '10 km', 3000),
      time('run_half', '21 km (Yarı Maraton)', 6300),
      time('run_full', '42 km (Maraton)', 12600),
    ],
  },
  {
    id: 'cardio',
    name: 'Kardiyo Rekorları',
    exercises: [
      time('row_500m', '500m Row', 105),
      time('row_1000m', '1000m Row', 240),
      time('row_2000m', '2000m Row', 480),
      time('row_5000m', '5000m Row', 1200),
      cal('bike_10cal', 'Assault Bike 10 Cal', 10),
      cal('bike_50cal', 'Assault Bike 50 Cal', 50),
      cal('echo_100cal', 'Echo Bike 100 Cal', 100),
      time('ski_1000m', 'SkiErg 1000m', 240),
    ],
  },
  {
    id: 'benchmark',
    name: "CrossFit Benchmark WOD'ları",
    exercises: [
      time('fran', 'Fran', 300),
      time('grace', 'Grace', 180),
      time('isabel', 'Isabel', 180),
      time('jackie', 'Jackie', 600),
      time('helen', 'Helen', 600),
      time('dane', 'Diane', 420),
      rep('cindy', 'Cindy (AMRAP 20)', 20),
      time('murph', 'Murph', 2400),
      time('kalsu', 'Kalsu', 1200),
      time('dt', 'DT', 600),
      time('linda', 'Linda', 1200),
    ],
  },
];

export const RECORD_ATTEMPT_CATEGORIES: RecordCategory[] = CATEGORIES_DATA.map((c) => ({
  ...c,
  exerciseCount: c.exercises.length,
}));

export function measureLabel(type: RecordMeasureType, catalogId?: string): string {
  if (catalogId === 'cindy') return 'AMRAP 20 dk (tur)';
  switch (type) {
    case RecordMeasureType.WEIGHT:
      return 'Ağırlık rekoru';
    case RecordMeasureType.REPS:
      return 'Maksimum tekrar';
    case RecordMeasureType.TIME:
      return 'En iyi süre';
    case RecordMeasureType.DISTANCE:
      return 'Mesafe rekoru';
    case RecordMeasureType.CALORIES:
      return 'Kalori hedefi';
    default:
      return 'Rekor';
  }
}
