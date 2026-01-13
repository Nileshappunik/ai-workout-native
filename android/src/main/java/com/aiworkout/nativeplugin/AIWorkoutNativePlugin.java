package com.aiworkout.nativeplugin;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import androidx.activity.result.ActivityResult;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "AIWorkoutNative")
public class AIWorkoutNativePlugin extends Plugin {

    private AIWorkoutNative implementation = new AIWorkoutNative();
    private PluginCall savedCall;

    @PluginMethod
    public void echo(PluginCall call) {
        String value = call.getString("value");
        JSObject ret = new JSObject();
        ret.put("value", implementation.echo(value));
        call.resolve(ret);
    }

    // ✅ START AI WORKOUT with event listener using Manager
    @PluginMethod
    public void startWorkout(PluginCall call) {
        try {
            savedCall = call;
            call.setKeepAlive(true);

            String mode = call.getString("mode", "squat");

            // ✅ Set up listener using Manager (no more BroadcastReceiver)
            WorkoutEventManager.INSTANCE.setListener(new WorkoutEventManager.WorkoutEventListener() {
                @Override
                public void onPositionConfirmed(String mode, long timestamp) {
                    Log.d("AIWorkoutPlugin", "Position confirmed received - Mode: " + mode);

                    // Emit event to Ionic side
                    JSObject ret = new JSObject();
                    ret.put("event", "positionConfirmed");
                    ret.put("positionConfirmed", true);
                    ret.put("status", "Position confirmed - workout starting");
                    ret.put("timestamp", timestamp);
                    ret.put("mode", mode);

                    notifyListeners("workoutEvent", ret);
                }

                @Override
                public void onWorkoutUpdate(int reps, long duration) {
                    Log.d("AIWorkoutPlugin", "Workout update - Reps: " + reps);

                    JSObject ret = new JSObject();
                    ret.put("event", "workoutUpdate");
                    ret.put("reps", reps);
                    ret.put("duration", duration);

                    notifyListeners("workoutEvent", ret);
                }

                @Override
                public void onWorkoutStopped(int reps, long duration) {
                    Log.d("AIWorkoutPlugin", "Workout stopped - Reps: " + reps);

                    JSObject ret = new JSObject();
                    ret.put("event", "workoutStopped");
                    ret.put("reps", reps);
                    ret.put("duration", duration);

                    notifyListeners("workoutEvent", ret);
                }

                @Override
                public void onShowIonicUI() {
                    Log.d("AIWorkoutPlugin", "Show Ionic UI");

                    JSObject ret = new JSObject();
                    ret.put("event", "showIonicUI");
                    ret.put("show", true);

                    notifyListeners("workoutEvent", ret);
                }
            });

            Intent intent = new Intent(getContext(), PoseCoachActivity.class);
            intent.putExtra("workout_mode", mode);

            startActivityForResult(call, intent, "workoutResultCallback");

        } catch (Exception e) {
            Log.e("AIWorkoutPlugin", "Failed to start workout: " + e.getMessage());
            call.reject("Failed to start workout: " + e.getMessage());
        }
    }

    @ActivityCallback
    private void workoutResultCallback(PluginCall call, ActivityResult result) {
        // ✅ Clear listener when activity finishes (no more BroadcastReceiver unregistration)
        WorkoutEventManager.INSTANCE.clearListener();

        if (call == null) {
            return;
        }

        JSObject ret = new JSObject();

        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            Intent data = result.getData();

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

    // ✅ UPDATED - Direct method call instead of broadcast
    @PluginMethod
    public void stopWorkout(PluginCall call) {
        try {
            PoseCoachActivity activity = PoseCoachActivity.Companion.getInstance();
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    activity.stopWorkout();
                });

                JSObject ret = new JSObject();
                ret.put("success", true);
                ret.put("message", "Workout stop signal sent");
                call.resolve(ret);
            } else {
                JSObject ret = new JSObject();
                ret.put("success", false);
                ret.put("message", "Activity not found");
                call.resolve(ret);
            }
        } catch (Exception e) {
            Log.e("AIWorkoutPlugin", "Failed to stop workout: " + e.getMessage());
            call.reject("Failed to stop workout: " + e.getMessage());
        }
    }

    @PluginMethod
    public void getWorkoutStats(PluginCall call) {
        try {
            JSObject ret = new JSObject();
            ret.put("success", true);
            ret.put("reps", 0);
            ret.put("duration", 0);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to get workout stats: " + e.getMessage());
        }
    }
}