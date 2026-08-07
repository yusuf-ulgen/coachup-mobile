type Listener = () => void;

export interface ActiveWorkoutData {
  sessionId: string | null;
  programId?: string;
  title: string;
  seconds: number;
  startTimeTimestamp: number | null;
  isActive: boolean;
  isOnActiveWorkoutScreen: boolean;
}

let state: ActiveWorkoutData = {
  sessionId: null,
  title: '',
  seconds: 0,
  startTimeTimestamp: null,
  isActive: false,
  isOnActiveWorkoutScreen: false,
};

const listeners = new Set<Listener>();

export const ActiveWorkoutManager = {
  getState(): ActiveWorkoutData {
    if (state.startTimeTimestamp && state.isActive) {
      state.seconds = Math.max(0, Math.floor((Date.now() - state.startTimeTimestamp) / 1000));
    }
    return state;
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

  startWorkout(sessionId: string, title: string, programId?: string, initialSeconds: number = 0) {
    const now = Date.now();
    state = {
      sessionId,
      title: title || 'Aktif Antrenman',
      programId,
      seconds: initialSeconds,
      startTimeTimestamp: now - initialSeconds * 1000,
      isActive: true,
      isOnActiveWorkoutScreen: true,
    };
    this.notify();
  },

  updateSeconds(seconds: number) {
    if (state.sessionId) {
      state.seconds = seconds;
      if (!state.startTimeTimestamp) {
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
