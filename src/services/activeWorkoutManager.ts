type Listener = () => void;

export interface ActiveWorkoutData {
  sessionId: string | null;
  programId?: string;
  selectedDay?: number;
  title: string;
  workoutTitle?: string;
  category?: string;
  emoji?: string;
  isOutdoor?: boolean;
  hasStarted?: boolean;
  seconds: number;
  startTimeTimestamp: number | null;
  totalPausedMs: number;
  pauseStartMs: number | null;
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
  totalPausedMs: 0,
  pauseStartMs: null,
  isActive: false,
  isOnActiveWorkoutScreen: false,
};

const listeners = new Set<Listener>();

export const ActiveWorkoutManager = {
  getState(): ActiveWorkoutData {
    if (state.startTimeTimestamp && state.hasStarted) {
      if (state.isActive) {
        const elapsedMs = Date.now() - state.startTimeTimestamp - state.totalPausedMs;
        state.seconds = Math.max(0, Math.floor(elapsedMs / 1000));
      }
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
      selectedDay?: number;
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
      if (options?.selectedDay !== undefined) {
        state.selectedDay = options.selectedDay;
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
      selectedDay: options?.selectedDay || 1,
      seconds: initialSeconds,
      startTimeTimestamp: hasStarted ? now - initialSeconds * 1000 : null,
      totalPausedMs: 0,
      pauseStartMs: null,
      isActive: true,
      isOnActiveWorkoutScreen: true,
    };
    this.notify();
  },

  setHasStarted(hasStarted: boolean) {
    state.hasStarted = hasStarted;
    if (hasStarted) {
      state.isActive = true;
      if (!state.startTimeTimestamp) {
        state.startTimeTimestamp = Date.now() - state.seconds * 1000;
        state.totalPausedMs = 0;
        state.pauseStartMs = null;
      }
    }
    this.notify();
  },

  setIsActive(isActive: boolean) {
    if (isActive) {
      this.resumeWorkout();
    } else {
      this.pauseWorkout();
    }
  },

  pauseWorkout() {
    if (state.isActive && state.hasStarted) {
      state.isActive = false;
      state.pauseStartMs = Date.now();
      this.notify();
    }
  },

  resumeWorkout() {
    if (!state.isActive && state.hasStarted) {
      state.isActive = true;
      if (state.pauseStartMs) {
        state.totalPausedMs += Math.max(0, Date.now() - state.pauseStartMs);
        state.pauseStartMs = null;
      }
      this.notify();
    }
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
      totalPausedMs: 0,
      pauseStartMs: null,
      isActive: false,
      isOnActiveWorkoutScreen: false,
    };
    this.notify();
  },

  shouldShowOverlay(): boolean {
    return Boolean(
      state.sessionId &&
        (state.isActive || state.hasStarted) &&
        !state.isOnActiveWorkoutScreen
    );
  },
};
