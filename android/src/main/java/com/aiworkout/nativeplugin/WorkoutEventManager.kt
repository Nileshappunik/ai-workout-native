package com.aiworkout.nativeplugin

import android.util.Log

object WorkoutEventManager {

    private var eventListener: WorkoutEventListener? = null

    interface WorkoutEventListener {
        fun onPositionConfirmed(mode: String, timestamp: Long)
        fun onWorkoutUpdate(reps: Int, duration: Long)
        fun onWorkoutStopped(reps: Int, duration: Long)
        fun onShowIonicUI()
    }

    fun setListener(listener: WorkoutEventListener?) {
        Log.d("WorkoutEventManager", "Listener set: ${listener != null}")
        this.eventListener = listener
    }

    fun notifyPositionConfirmed(mode: String, timestamp: Long) {
        Log.d("WorkoutEventManager", "Position confirmed - Mode: $mode, Timestamp: $timestamp")
        eventListener?.onPositionConfirmed(mode, timestamp)
    }

    fun notifyWorkoutUpdate(reps: Int, duration: Long) {
        Log.d("WorkoutEventManager", "Workout update - Reps: $reps, Duration: $duration")
        eventListener?.onWorkoutUpdate(reps, duration)
    }

    fun notifyWorkoutStopped(reps: Int, duration: Long) {
        Log.d("WorkoutEventManager", "Workout stopped - Reps: $reps, Duration: $duration")
        eventListener?.onWorkoutStopped(reps, duration)
    }

    fun notifyShowIonicUI() {
        Log.d("WorkoutEventManager", "Show Ionic UI")
        eventListener?.onShowIonicUI()
    }

    fun clearListener() {
        Log.d("WorkoutEventManager", "Listener cleared")
        eventListener = null
    }
}