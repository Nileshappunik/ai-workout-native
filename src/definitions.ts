export interface AIWorkoutNativePlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
