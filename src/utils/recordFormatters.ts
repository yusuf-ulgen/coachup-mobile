// List of non-reps / time / distance / cardio / for-time exercise keywords
export const NON_REPS_KEYWORDS = [
  'km',
  'koşu',
  'kosu',
  'run',
  'maraton',
  'yürüyüş',
  'yuruyus',
  'walk',
  'yüzme',
  'yuzme',
  'swim',
  'bisiklet',
  'cycle',
  'bike',
  'row',
  'kürek',
  'kurek',
  'skierg',
  'ski',
  'kardiyo',
  'cardio',
  'cooper',
  'tempo',
  'murph',
  'fran',
  'grace',
  'isabel',
  'jackie',
  'helen',
  'diane',
  'kalsu',
  'dt',
  'linda',
  'plank',
  'wall sit',
  'l-sit',
  'hyrox',
];

export const AMRAP_KEYWORDS = ['cindy', 'amrap'];

// Regex matching distance / metric patterns like "3 km", "1 km", "5k", "400m", "50 Cal", "10k"
const DISTANCE_METRIC_REGEX = /\b\d+\s*(?:km|k|m|cal|metre|meter)\b/i;

export function isNonRepsExercise(name?: string): boolean {
  const lower = (name || '').toLowerCase().trim();
  if (!lower) return false;
  if (DISTANCE_METRIC_REGEX.test(lower)) return true;
  return NON_REPS_KEYWORDS.some((kw) => lower.includes(kw));
}

export function isAmrapExercise(name?: string): boolean {
  const lower = (name || '').toLowerCase().trim();
  if (!lower) return false;
  return AMRAP_KEYWORDS.some((kw) => lower.includes(kw));
}

/**
 * Smart formatting for Personal Record card primary values.
 * Prevents displaying "1 tekrar" on non-reps sports (Row, Run, Swim, Murph, etc.)
 */
export function formatPRDisplayValue(item: any): string {
  if (!item) return 'Tamamlandı';
  const exerciseName = (item.exercise?.name || item.exercise_name || item.name || '').trim();
  const lowerName = exerciseName.toLowerCase();
  const notes = (item.notes || '').trim();

  // 1. AMRAP (Cindy etc.)
  if (isAmrapExercise(lowerName)) {
    if (item.reps && item.reps > 0) {
      return `${item.reps} tur`;
    }
    const roundsMatch = notes.match(/(\d+)\s*(tur|round)/i);
    if (roundsMatch) return `${roundsMatch[1]} tur`;
    return 'Tamamlandı';
  }

  // 2. Non-Reps Sports (Row, Run, Swim, Bike, Ski, Murph, Fran, etc.)
  if (isNonRepsExercise(lowerName)) {
    // Check if notes contains time formatted like "04:32", "21:45 dk", "Süre: 42:15"
    const timeMatch = notes.match(/(\d{1,2}:\d{2}(?::\d{2})?|\d+\s*(?:dk|sn|min|sec))/i);
    if (timeMatch) {
      return timeMatch[0];
    }
    if (item.time_seconds && item.time_seconds > 0) {
      const m = Math.floor(item.time_seconds / 60);
      const s = item.time_seconds % 60;
      return `${m}:${s.toString().padStart(2, '0')}`;
    }
    return 'Tamamlandı';
  }

  // 3. Weight-based exercises (Squat, Bench, Deadlift, Clean & Jerk, Snatch, etc.)
  if (item.weight_kg && item.weight_kg > 0) {
    if (item.reps && item.reps > 1) {
      return `${item.weight_kg} kg (${item.reps} tekrar)`;
    }
    return `${item.weight_kg} kg`;
  }

  // 4. Bodyweight reps-based exercises (Pull-Up, Push-Up, Dips, Muscle-Up, etc.)
  if (item.reps && item.reps > 0) {
    return `${item.reps} tekrar`;
  }

  return 'Tamamlandı';
}

/**
 * Smart formatting for Personal Record accordion / detail section.
 */
export function formatPRDetailText(item: any): string {
  if (!item) return 'Kişisel Rekor';
  const exerciseName = (item.exercise?.name || item.exercise_name || item.name || '').trim();
  const lowerName = exerciseName.toLowerCase();

  if (isAmrapExercise(lowerName)) {
    return `Sonuç: ${item.reps || 1} tur`;
  }

  if (isNonRepsExercise(lowerName)) {
    if (item.time_seconds && item.time_seconds > 0) {
      const m = Math.floor(item.time_seconds / 60);
      const s = item.time_seconds % 60;
      return `Süre: ${m}:${s.toString().padStart(2, '0')} · Kardiyo / For Time Rekoru`;
    }
    return `Kategori: Kardiyo / For Time Rekoru`;
  }

  if (item.weight_kg && item.weight_kg > 0) {
    return `Ağırlık: ${item.weight_kg} kg${item.reps ? ` · Tekrar: ${item.reps}` : ''}`;
  }

  if (item.reps && item.reps > 0) {
    return `Maksimum Tekrar: ${item.reps}`;
  }

  return 'Kişisel Rekor';
}
