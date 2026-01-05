import { PluginListenerHandle } from '@capacitor/core';

export interface AIWorkoutNativePlugin {
  echo(options: { value: string }): Promise<{ value: string }>;

  /**
   * Start the AI workout with pose detection
   * @param options - Optional workout configuration
   * @param options.mode - Workout mode: 'squat', 'plank', or 'yoga' (default: 'squat')
   */
  startWorkout(options?: { 
    mode?: 'squat' | 'plank' | 'yoga' | 'jumping_jack' | 'push_up' | 'lunge' | 'bicep_curl' | 'shoulder_press' | 'burpee' 
  }): Promise<{
    success: boolean;
    positionConfirmed?: boolean;
    finalReps?: number;
    duration?: number;
    status?: string;
    mode?: string;
    message?: string;
  }>;

  /**
   * Stop the current workout session
   */
  stopWorkout(): Promise<{
    success: boolean;
    message: string;
  }>;

  /**
   * Get workout statistics (future implementation)
   */
  getWorkoutStats(): Promise<{
    success: boolean;
    reps: number;
    duration: number;
  }>;

  // ✅ ADD THESE LISTENER METHODS
  /**
   * Listen for workout events (position confirmation, rep counts, etc.)
   */
  addListener(
    eventName: 'workoutEvent',
    listenerFunc: (event: WorkoutEvent) => void
  ): Promise<PluginListenerHandle>;

  /**
   * Remove all listeners
   */
  removeAllListeners(): Promise<void>;
}

// ✅ ADD EVENT INTERFACE
export interface WorkoutEvent {
  event: 'positionConfirmed' | 'repCompleted' | 'workoutUpdate';
  positionConfirmed?: boolean;
  status?: string;
  timestamp?: number;
  mode?: string;
  reps?: number;
}