import { registerPlugin } from '@capacitor/core';

import type { AIWorkoutNativePlugin } from './definitions';

const AIWorkoutNative = registerPlugin<AIWorkoutNativePlugin>('AIWorkoutNative', {
  web: () => import('./web').then((m) => new m.AIWorkoutNativeWeb()),
});

export * from './definitions';
export { AIWorkoutNative };
