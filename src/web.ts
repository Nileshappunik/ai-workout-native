import { WebPlugin } from '@capacitor/core';

import type { AIWorkoutNativePlugin } from './definitions';

export class AIWorkoutNativeWeb extends WebPlugin implements AIWorkoutNativePlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }

   async startWorkout(options?: { mode?: 'squat' | 'plank' | 'yoga' }): Promise<{
    success: boolean;
    message: string;
  }> {
    console.warn('AIWorkoutNative.startWorkout() is not available on web platform');
    return {
      success: false,
      message: 'Workout features are only available on native platforms (Android/iOS)',
    };
  }

  async stopWorkout(): Promise<{
    success: boolean;
    message: string;
  }> {
    console.warn('AIWorkoutNative.stopWorkout() is not available on web platform');
    return {
      success: false,
      message: 'Workout features are only available on native platforms (Android/iOS)',
    };
  }

  async getWorkoutStats(): Promise<{
    success: boolean;
    reps: number;
    duration: number;
  }> {
    console.warn('AIWorkoutNative.getWorkoutStats() is not available on web platform');
    return {
      success: false,
      reps: 0,
      duration: 0,
    };
  }
}
