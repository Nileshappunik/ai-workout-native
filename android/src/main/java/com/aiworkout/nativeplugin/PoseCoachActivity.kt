package com.aiworkout.nativeplugin

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.*
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

class PoseCoachActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var previewView: PreviewView
    private lateinit var overlay: PoseOverlayView
    private lateinit var statusText: TextView
    private lateinit var tvCounter: TextView

    private lateinit var poseDetector: PoseDetector
    private lateinit var tts: TextToSpeech

    private var lastSpokenTime = 0L
    private var lastStatusTime = 0L

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechIntent: Intent

    // ✅ ADD THESE NEW PROPERTIES
    private var workoutStartTime = 0L
    private var positionConfirmedSent = false

    // Exercise modes
    private enum class Mode {
        SQUAT,
        PLANK,
        YOGA_WARRIOR2,
        JUMPING_JACK,
        PUSH_UP,
        LUNGE,
        BICEP_CURL,
        SHOULDER_PRESS,
        BURPEE
    }

    private enum class RunState { IDLE, RUNNING, PAUSED }

    private var mode: Mode = Mode.SQUAT
    private var runState: RunState = RunState.IDLE

    // Exercise counter instance
    private val exerciseCounter = ExerciseCounter()

    private var isPerfectPosition = false
    private var hasAnnouncedStart = false

    // Replace with your actual ElevenLabs API key
    private val elevenTts by lazy {
        ElevenLabsTTS("ap2_a2305ed4-29b4-4f6a-b8ce-3f31a7f18288")
    }

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    // BroadcastReceiver to handle stop workout command
    private val stopWorkoutReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.aiworkout.STOP_WORKOUT") {
                runState = RunState.IDLE
                speak("Workout stopped. You completed ${exerciseCounter.getCount()} repetitions.")
               // finish()
                sendWorkoutResults(completed = false)  // ✅ MODIFIED
            }
        }
    }



    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @ExperimentalGetImage
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pose_coach)

        // Get workout mode from intent
        val modeString = intent.getStringExtra("workout_mode") ?: "squat"
        mode = when (modeString.lowercase()) {
            "plank" -> Mode.PLANK
            "yoga" -> Mode.YOGA_WARRIOR2
            "jumping_jack", "jumpingjack" -> Mode.JUMPING_JACK
            "push_up", "pushup" -> Mode.PUSH_UP
            "lunge" -> Mode.LUNGE
            "bicep_curl", "bicepcurl" -> Mode.BICEP_CURL
            "shoulder_press", "shoulderpress" -> Mode.SHOULDER_PRESS
            "burpee" -> Mode.BURPEE
            else -> Mode.SQUAT
        }

        previewView = findViewById(R.id.previewView)
        overlay = findViewById(R.id.overlay)
        statusText = findViewById(R.id.statusText)
        tvCounter = findViewById(R.id.tvCounter)

        tts = TextToSpeech(this, this)

        val options = AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
            .build()
        poseDetector = PoseDetection.getClient(options)

        setupSpeechRecognizer()

        // Register broadcast receiver for stop command
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                stopWorkoutReceiver,
                IntentFilter("com.aiworkout.STOP_WORKOUT"),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(stopWorkoutReceiver, IntentFilter("com.aiworkout.STOP_WORKOUT"))
        }

        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                101
            )
        } else {
            startCamera()
            startListeningForCommands()
        }

        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)

        btnStart.setOnClickListener {
            runState = RunState.RUNNING
            exerciseCounter.reset()
            hasAnnouncedStart = false
            isPerfectPosition = false
            positionConfirmedSent = false  // ✅ ADDED
            tvCounter.text = "Reps: 0"
            speak("Workout started. Get into position.")
        }

        btnStop.setOnClickListener {
            runState = RunState.IDLE
            speak("Workout stopped. You completed ${exerciseCounter.getCount()} repetitions.")
            sendWorkoutResults(completed = false)  // ✅ MODIFIED
            //finish()
        }
    }

    // ✅ ADD THIS METHOD - Handle back button press
    override fun onBackPressed() {
        sendWorkoutResults(completed = false)
        super.onBackPressed()
    }

    private fun checkBodySetup(
        pose: Pose,
        imageWidth: Int,
        imageHeight: Int
    ): String? {

        val ls = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rs = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val la = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val ra = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        if (ls == null || rs == null || la == null || ra == null) {
            return "Step back so your full body is visible"
        }

        // FACE CAMERA CHECK
        val shoulderYDiff = abs(ls.position.y - rs.position.y)
        if (shoulderYDiff > imageHeight * 0.06f) {
            return "Turn your shoulders to face the camera"
        }

        // DISTANCE REFINEMENT
        val shoulderWidth = abs(ls.position.x - rs.position.x)
        val shoulderRatio = shoulderWidth / imageWidth.toFloat()

        if (shoulderRatio > 0.42f) {
            return "Take a small step back"
        }

        // ✅ PERFECT
        return null
    }


    // ---------- Permissions ----------
    private fun hasPermissions(): Boolean {
        val camOk = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val micOk = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        return camOk && micOk
    }

    @ExperimentalGetImage
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (hasPermissions()) {
            startCamera()
            startListeningForCommands()
        } else {
            statusText.text = "Camera & Mic permissions are required."
        }
    }

    // ---------- CameraX ----------
    @ExperimentalGetImage
    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val cameraProvider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processFrame(imageProxy)
            }

            val camera = cameraProvider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
            )

        }, ContextCompat.getMainExecutor(this))
    }

    @ExperimentalGetImage
    private fun processFrame(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val img = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        poseDetector.process(img)
            .addOnSuccessListener { pose ->
                overlay.setPose(pose, img.width, img.height, img.rotationDegrees)

                if (runState == RunState.RUNNING) {
                    val setupMsg = checkBodySetup(pose, img.width, img.height)
                    if (setupMsg != null) {
                        updateStatusThrottled(setupMsg)
                        if (!isPerfectPosition) speakOnce(setupMsg)
                        isPerfectPosition = false
                    } else {
                        isPerfectPosition = true
                        if (!hasAnnouncedStart) {
                            // Send position confirmation callback
                            sendPositionConfirmed()
                            workoutStartTime = System.currentTimeMillis()
                            speakOnce("Perfect! Begin your ${mode.name.lowercase().replace('_', ' ')}s.")
                            hasAnnouncedStart = true
                        }

                        // Call appropriate exercise detection method
                        when (mode) {
                            Mode.SQUAT -> {
                                exerciseCounter.updateSquat(pose, img.width, img.height)
                                updateStatusThrottled("Mode: SQUAT • Reps: ${exerciseCounter.getCount()}")
                            }
                            Mode.JUMPING_JACK -> {
                                exerciseCounter.updateJumpingJack(pose, img.width, img.height)
                                updateStatusThrottled("Mode: JUMPING JACK • Reps: ${exerciseCounter.getCount()}")
                            }
                            Mode.PUSH_UP -> {
                                exerciseCounter.updatePushUp(pose, img.width, img.height)
                                updateStatusThrottled("Mode: PUSH UP • Reps: ${exerciseCounter.getCount()}")
                            }
                            Mode.LUNGE -> {
                                exerciseCounter.updateLunge(pose, img.width, img.height)
                                updateStatusThrottled("Mode: LUNGE • Reps: ${exerciseCounter.getCount()}")
                            }
                            Mode.PLANK -> {
                                val plankTime = exerciseCounter.updatePlank(pose, img.width, img.height)
                                updateStatusThrottled("Mode: PLANK • Time: ${plankTime}s")
                                if (plankTime > 0 && plankTime % 10 == 0) {
                                    speakOnce("$plankTime seconds")
                                }
                            }
                            Mode.BICEP_CURL -> {
                                exerciseCounter.updateBicepCurl(pose, img.width, img.height)
                                updateStatusThrottled("Mode: BICEP CURL • Reps: ${exerciseCounter.getCount()}")
                            }
                            Mode.SHOULDER_PRESS -> {
                                exerciseCounter.updateShoulderPress(pose, img.width, img.height)
                                updateStatusThrottled("Mode: SHOULDER PRESS • Reps: ${exerciseCounter.getCount()}")
                            }
                            Mode.BURPEE -> {
                                exerciseCounter.updateBurpee(pose, img.width, img.height)
                                updateStatusThrottled("Mode: BURPEE • Reps: ${exerciseCounter.getCount()}")
                            }
                            Mode.YOGA_WARRIOR2 -> {
                                coachWarrior2(pose)
                            }
                        }

                        // Update rep counter display
                        runOnUiThread {
                            tvCounter.text = "Reps: ${exerciseCounter.getCount()}"
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                // Handle error
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    // ---------- Yoga Warrior II Coaching + Score ----------
    private fun coachWarrior2(pose: Pose) {
        val ls = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rs = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val lw = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rw = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)

        val hip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val knee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val ankle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)

        if (ls == null || rs == null || lw == null || rw == null || hip == null || knee == null || ankle == null) {
            updateStatusThrottled("Keep arms, shoulders, hip, knee, ankle visible")
            return
        }

        val kneeAngle = angleDeg(hip.position, knee.position, ankle.position)
        val kneeScore = scoreInRange(kneeAngle, 80.0, 105.0)

        val leftArmDy = abs(lw.position.y - ls.position.y)
        val rightArmDy = abs(rw.position.y - rs.position.y)
        val armScore = scoreInverse(((leftArmDy + rightArmDy) / 2.0), goodMax = 25.0, badMin = 140.0)

        val total = (0.6 * kneeScore + 0.4 * armScore).toInt()

        val msg = buildString {
            append("Knee: ${kneeAngle.toInt()}° • Score: $total")
            if (kneeAngle > 120) append(" • Bend front knee more")
            if (leftArmDy > 80 || rightArmDy > 80) append(" • Raise arms to shoulder height")
        }

        updateStatusThrottled("Mode: YOGA (Warrior II) • $msg")
        if (total >= 85) speakOnce("Great warrior pose")
        else if (kneeAngle > 120) speakOnce("Bend your front knee more")
    }

    // ---------- Speech Recognition (Voice Commands) ----------
    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer.setRecognitionListener(
            SimpleRecognitionListener(
                onResultCallback = { text ->
                    handleCommand(text.lowercase())
                },
                onErrorCallback = { error ->
                    startListeningForCommands()
                },
                onEndCallback = {
                    startListeningForCommands()
                }
            )
        )
    }

    private fun startListeningForCommands() {
        try {
            speechRecognizer.stopListening()
        } catch (_: Exception) {}
        try {
            speechRecognizer.startListening(speechIntent)
        } catch (_: Exception) {}
    }

    private fun handleCommand(cmd: String) {
        when {
            cmd.contains("start") -> {
                runState = RunState.RUNNING
                exerciseCounter.reset()
                hasAnnouncedStart = false
                speak("Workout started. Get into position.")
            }
            cmd.contains("stop") -> {
                runState = RunState.IDLE
                speak("Workout stopped. You did ${exerciseCounter.getCount()} repetitions.")
            }
            // Add mode switching commands
            cmd.contains("squat") -> {
                mode = Mode.SQUAT
                exerciseCounter.reset()
                speak("Switched to squat mode")
            }
            cmd.contains("push up") || cmd.contains("pushup") -> {
                mode = Mode.PUSH_UP
                exerciseCounter.reset()
                speak("Switched to push up mode")
            }
            cmd.contains("jumping jack") -> {
                mode = Mode.JUMPING_JACK
                exerciseCounter.reset()
                speak("Switched to jumping jack mode")
            }
            cmd.contains("lunge") -> {
                mode = Mode.LUNGE
                exerciseCounter.reset()
                speak("Switched to lunge mode")
            }
            cmd.contains("plank") -> {
                mode = Mode.PLANK
                exerciseCounter.reset()
                speak("Switched to plank mode")
            }
            cmd.contains("bicep") || cmd.contains("curl") -> {
                mode = Mode.BICEP_CURL
                exerciseCounter.reset()
                speak("Switched to bicep curl mode")
            }
            cmd.contains("shoulder press") -> {
                mode = Mode.SHOULDER_PRESS
                exerciseCounter.reset()
                speak("Switched to shoulder press mode")
            }
            cmd.contains("burpee") -> {
                mode = Mode.BURPEE
                exerciseCounter.reset()
                speak("Switched to burpee mode")
            }
        }
    }

    fun speak(text: String) {
        elevenTts.speak(text)
    }

    // ---------- UI + Voice Throttling ----------
    private fun updateStatusThrottled(message: String) {
        val now = System.currentTimeMillis()
        if (now - lastStatusTime > 150) {
            runOnUiThread { statusText.text = message }
            lastStatusTime = now
        }
    }

    private fun speakOnce(message: String) {
        val now = System.currentTimeMillis()
        if (now - lastSpokenTime > 2500) {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "poseCoach")
            lastSpokenTime = now
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            speakOnce("Say Start to begin. Current mode is ${mode.name.lowercase().replace('_', ' ')}.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(stopWorkoutReceiver) } catch (_: Exception) {}
        try { speechRecognizer.destroy() } catch (_: Exception) {}
        try { tts.shutdown() } catch (_: Exception) {}
        try { poseDetector.close() } catch (_: Exception) {}
        cameraExecutor.shutdown()
    }

    // ---------- Math Helpers ----------
    private fun angleDeg(a: android.graphics.PointF, b: android.graphics.PointF, c: android.graphics.PointF): Double {
        val abx = (a.x - b.x).toDouble()
        val aby = (a.y - b.y).toDouble()
        val cbx = (c.x - b.x).toDouble()
        val cby = (c.y - b.y).toDouble()

        val dot = abx * cbx + aby * cby
        val ab = sqrt(abx * abx + aby * aby)
        val cb = sqrt(cbx * cbx + cby * cby)
        val denom = kotlin.math.max(1e-9, ab * cb)
        val cos = (dot / denom).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cos))
    }

    private fun scoreInRange(value: Double, minOk: Double, maxOk: Double, margin: Double = 40.0): Int {
        return when {
            value in minOk..maxOk -> 100
            value < minOk -> {
                val d = minOk - value
                (100 - (d / margin) * 100).toInt().coerceIn(0, 99)
            }
            else -> {
                val d = value - maxOk
                (100 - (d / margin) * 100).toInt().coerceIn(0, 99)
            }
        }
    }

    private fun scoreInverse(value: Double, goodMax: Double, badMin: Double): Int {
        if (value <= goodMax) return 100
        if (value >= badMin) return 0
        val t = (value - goodMax) / (badMin - goodMax)
        return (100 * (1.0 - t)).toInt().coerceIn(0, 100)
    }

    // ✅ MODIFIED - Send position confirmation via broadcast
    private fun sendPositionConfirmed() {
        Log.e("NileshPosition","positionConfirmedSent:- $positionConfirmedSent")
        if (!positionConfirmedSent) {
            // Send broadcast to notify plugin immediately
            val broadcastIntent = Intent("com.aiworkout.POSITION_CONFIRMED")
            broadcastIntent.putExtra("position_confirmed", true)
            broadcastIntent.putExtra("status", "Position confirmed - workout starting")
            broadcastIntent.putExtra("timestamp", System.currentTimeMillis())
            broadcastIntent.putExtra("mode", mode.name)
            sendBroadcast(broadcastIntent)

            positionConfirmedSent = true
            workoutStartTime = System.currentTimeMillis()

            Log.e("NileshPosition", "Broadcast sent - position confirmed")
        }
    }

    // ✅ ADD THIS METHOD - Send final workout results
    private fun sendWorkoutResults(completed: Boolean = true) {
        val duration = if (workoutStartTime > 0) {
            (System.currentTimeMillis() - workoutStartTime) / 1000
        } else 0L

        val resultIntent = Intent()
        resultIntent.putExtra("position_confirmed", true)
        resultIntent.putExtra("final_reps", exerciseCounter.getCount())
        resultIntent.putExtra("duration", duration)
        resultIntent.putExtra("status", if (completed) "Workout completed" else "Workout stopped")
        resultIntent.putExtra("mode", mode.name)
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}