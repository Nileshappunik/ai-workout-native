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
    private PluginCall savedCall; // ✅ Store the call to emit events later

    // ✅ BroadcastReceiver to listen for position confirmation
    private final BroadcastReceiver positionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("com.aiworkout.POSITION_CONFIRMED")) {
                boolean confirmed = intent.getBooleanExtra("position_confirmed", false);
                String status = intent.getStringExtra("status");
                long timestamp = intent.getLongExtra("timestamp", 0);
                String mode = intent.getStringExtra("mode");

                // Emit event to Ionic side
                JSObject ret = new JSObject();
                ret.put("event", "positionConfirmed");
                ret.put("positionConfirmed", confirmed);
                ret.put("status", status);
                ret.put("timestamp", timestamp);
                ret.put("mode", mode);

                // Notify listeners
                notifyListeners("workoutEvent", ret);
            }
        }
    };

    @PluginMethod
    public void echo(PluginCall call) {
        String value = call.getString("value");
        JSObject ret = new JSObject();
        ret.put("value", implementation.echo(value));
        call.resolve(ret);
    }

    // ✅ START AI WORKOUT with event listener
    @PluginMethod
    public void startWorkout(PluginCall call) {
        try {
            savedCall = call;
            call.setKeepAlive(true); // ✅ Keep call alive for events

            String mode = call.getString("mode", "squat");

            // Register broadcast receiver
            IntentFilter filter = new IntentFilter("com.aiworkout.POSITION_CONFIRMED");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getContext().registerReceiver(positionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                ContextCompat.registerReceiver(getContext(), positionReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
            }

            Intent intent = new Intent(getContext(), PoseCoachActivity.class);
            intent.putExtra("workout_mode", mode);

            startActivityForResult(call, intent, "workoutResultCallback");

        } catch (Exception e) {
            call.reject("Failed to start workout: " + e.getMessage());
        }
    }

    @ActivityCallback
    private void workoutResultCallback(PluginCall call, ActivityResult result) {
        // Unregister receiver when activity finishes
        try {
            getContext().unregisterReceiver(positionReceiver);
        } catch (Exception e) {
            // Already unregistered
        }

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

        //call.release(ret); // ✅ Release the call
    }

    @PluginMethod
    public void stopWorkout(PluginCall call) {
        try {
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