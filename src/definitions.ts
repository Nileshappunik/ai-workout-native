export interface AIWorkoutNativePlugin {
  echo(options: { value: string }): Promise<{ value: string }>;

   /**
   * Start the AI workout with pose detection
   * @param options - Optional workout configuration
   * @param options.mode - Workout mode: 'squat', 'plank', or 'yoga' (default: 'squat')
   */
  startWorkout(options?: { mode?: 'squat' | 'plank' | 'yoga' }): Promise<{
    success: boolean;
    message: string;
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
}

