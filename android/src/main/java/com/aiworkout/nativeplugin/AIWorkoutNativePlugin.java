package com.aiworkout.nativeplugin;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import androidx.activity.result.ActivityResult;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
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
//    @PluginMethod
//        public void startWorkout(PluginCall call) {
//        try {
//            // Get optional parameters
//            String mode = call.getString("mode", "squat"); // squat, plank, yoga
//
//            Intent intent = new Intent(getContext(), PoseCoachActivity.class);
//            intent.putExtra("workout_mode", mode);
//            getActivity().startActivity(intent);
//
//            JSObject ret = new JSObject();
//            ret.put("success", true);
//            ret.put("message", "Workout started with mode: " + mode);
//            call.resolve(ret);
//        } catch (Exception e) {
//            call.reject("Failed to start workout: " + e.getMessage());
//        }
//    }

    // ✅ START AI WORKOUT (Launch Native Pose Screen with callback)
    @PluginMethod
    public void startWorkout(PluginCall call) {
        try {
            // Get optional parameters
            String mode = call.getString("mode", "squat");

            Intent intent = new Intent(getContext(), PoseCoachActivity.class);
            intent.putExtra("workout_mode", mode);

            // Use startActivityForResult to get callback
            startActivityForResult(call, intent, "workoutResultCallback");

        } catch (Exception e) {
            call.reject("Failed to start workout: " + e.getMessage());
        }
    }

    // ✅ Handle the result from PoseCoachActivity
    @ActivityCallback
    private void workoutResultCallback(PluginCall call, ActivityResult result) {
        if (call == null) {
            return;
        }

        JSObject ret = new JSObject();

        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            Intent data = result.getData();

            // Get data from the activity
            boolean positionConfirmed = data.getBooleanExtra("position_confirmed", false);
            int finalReps = data.getIntExtra("final_reps", 0);
            long duration = data.getLongExtra("duration", 0);
            String status = data.getStringExtra("status");
            String mode = data.getStringExtra("mode");

            ret.put("success", true);
            ret.put("positionConfirmed", positionConfirmed);
            ret.put("finalReps", finalReps);
            ret.put("duration", duration);
            ret.put("status", status);
            ret.put("mode", mode);

            call.resolve(ret);
        } else {
            ret.put("success", false);
            ret.put("message", "Workout cancelled or failed");
            call.resolve(ret);
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
