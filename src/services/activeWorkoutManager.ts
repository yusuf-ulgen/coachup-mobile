type Listener = () => void;

export interface ActiveWorkoutData {
  sessionId: string | null;
  programId?: string;
  title: string;
  workoutTitle?: string;
  category?: string;
  emoji?: string;
  isOutdoor?: boolean;
  hasStarted?: boolean;
  seconds: number;
  startTimeTimestamp: number | null;
  isActive: boolean;
  isOnActiveWorkoutScreen: boolean;
}

let state: ActiveWorkoutData = {
  sessionId: null,
  title: '',
  workoutTitle: '',
  category: '',
  emoji: '🏃',
  isOutdoor: false,
  hasStarted: false,
  seconds: 0,
  startTimeTimestamp: null,
  isActive: false,
  isOnActiveWorkoutScreen: false,
};

const listeners = new Set<Listener>();

export const ActiveWorkoutManager = {
  getState(): ActiveWorkoutData {
    if (state.startTimeTimestamp && state.isActive && state.hasStarted) {
      state.seconds = Math.max(0, Math.floor((Date.now() - state.startTimeTimestamp) / 1000));
    }
    return { ...state };
  },

  subscribe(listener: Listener) {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  },

  notify() {
    listeners.forEach((l) => l());
  },

  startWorkout(
    sessionId: string,
    title: string,
    programId?: string,
    initialSeconds: number = 0,
    options?: {
      workoutTitle?: string;
      category?: string;
      emoji?: string;
      isOutdoor?: boolean;
      hasStarted?: boolean;
    }
  ) {
    // If this session is already active, preserve its start time and metadata!
    if (state.sessionId === sessionId && state.isActive) {
      state.isOnActiveWorkoutScreen = true;
      if (options?.hasStarted) {
        state.hasStarted = true;
        if (!state.startTimeTimestamp) {
          state.startTimeTimestamp = Date.now() - state.seconds * 1000;
        }
      }
      this.notify();
      return;
    }

    const now = Date.now();
    const hasStarted = options?.hasStarted ?? true;
    state = {
      sessionId,
      title: title || 'Aktif Antrenman',
      workoutTitle: options?.workoutTitle || title || 'Aktif Antrenman',
      category: options?.category || '',
      emoji: options?.emoji || '🏃',
      isOutdoor: options?.isOutdoor ?? false,
      hasStarted,
      programId,
      seconds: initialSeconds,
      startTimeTimestamp: hasStarted ? now - initialSeconds * 1000 : null,
      isActive: true,
      isOnActiveWorkoutScreen: true,
    };
    this.notify();
  },

  setHasStarted(hasStarted: boolean) {
    state.hasStarted = hasStarted;
    if (hasStarted && !state.startTimeTimestamp) {
      state.startTimeTimestamp = Date.now() - state.seconds * 1000;
    }
    this.notify();
  },

  updateSeconds(seconds: number) {
    if (state.sessionId) {
      state.seconds = seconds;
      if (state.hasStarted && !state.startTimeTimestamp) {
        state.startTimeTimestamp = Date.now() - seconds * 1000;
      }
      this.notify();
    }
  },

  setScreenFocus(isOnScreen: boolean) {
    state.isOnActiveWorkoutScreen = isOnScreen;
    this.notify();
  },

  finishWorkout() {
    state = {
      sessionId: null,
      title: '',
      workoutTitle: '',
      category: '',
      emoji: '🏃',
      isOutdoor: false,
      hasStarted: false,
      seconds: 0,
      startTimeTimestamp: null,
      isActive: false,
      isOnActiveWorkoutScreen: false,
    };
    this.notify();
  },

  shouldShowOverlay(): boolean {
    return Boolean(
      state.sessionId &&
        state.isActive &&
        !state.isOnActiveWorkoutScreen
    );
  },
};

