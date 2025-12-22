package com.aiworkout.nativeplugin;

import android.content.Intent;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "AIWorkoutNative")
public class AIWorkoutNativePlugin extends Plugin {

    private AIWorkoutNative implementation = new AIWorkoutNative();
    // ✅ Existing echo method (keep)
    @PluginMethod
    public void echo(PluginCall call) {
        String value = call.getString("value");

        JSObject ret = new JSObject();
        ret.put("value", implementation.echo(value));
        call.resolve(ret);
    }

    // ✅ START AI WORKOUT (Launch Native Pose Screen)
    @PluginMethod
    public void startWorkout(PluginCall call) {
        try {
            // Get optional parameters
            String mode = call.getString("mode", "squat"); // squat, plank, yoga

            Intent intent = new Intent(getContext(), PoseCoachActivity.class);
            intent.putExtra("workout_mode", mode);
            getActivity().startActivity(intent);

            JSObject ret = new JSObject();
            ret.put("success", true);
            ret.put("message", "Workout started with mode: " + mode);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to start workout: " + e.getMessage());
        }
    }

    // ✅ STOP WORKOUT (Optional – future cleanup)
    @PluginMethod
    public void stopWorkout(PluginCall call) {
        try {
            // Send broadcast to stop the workout activity
            Intent intent = new Intent("com.aiworkout.STOP_WORKOUT");
            getContext().sendBroadcast(intent);

            JSObject ret = new JSObject();
            ret.put("success", true);
            ret.put("message", "Workout stop signal sent");
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to stop workout: " + e.getMessage());
        }
    }

    // ✅ Get Workout Stats (Optional - for retrieving results)
    @PluginMethod
    public void getWorkoutStats(PluginCall call) {
        try {
            // You can implement SharedPreferences to store/retrieve workout stats
            JSObject ret = new JSObject();
            ret.put("success", true);
            ret.put("reps", 0); // Placeholder - implement actual stats retrieval
            ret.put("duration", 0);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to get workout stats: " + e.getMessage());
        }
    }


}
