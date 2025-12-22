import { WebPlugin } from '@capacitor/core';

import type { AIWorkoutNativePlugin } from './definitions';

export class AIWorkoutNativeWeb extends WebPlugin implements AIWorkoutNativePlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
