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

    private lateinit var poseDetector: PoseDetector
    private lateinit var tts: TextToSpeech

    private var lastSpokenTime = 0L
    private var lastStatusTime = 0L

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechIntent: Intent

    private enum class Mode { SQUAT, PLANK, YOGA_WARRIOR2 }
    private enum class RunState { IDLE, RUNNING, PAUSED }

    private var mode: Mode = Mode.SQUAT
    private var runState: RunState = RunState.IDLE

    private var repCount = 0
    private var isDown = false   // squat state

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
                speak("Workout stopped. You completed $repCount repetitions.")
                finish() // Close the activity
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
            else -> Mode.SQUAT
        }

        previewView = findViewById(R.id.previewView)
        overlay = findViewById(R.id.overlay)
        statusText = findViewById(R.id.statusText)

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
        val tvCounter = findViewById<TextView>(R.id.tvCounter)

        btnStart.setOnClickListener {
            runState = RunState.RUNNING
            repCount = 0
            isDown = false
            hasAnnouncedStart = false
            tvCounter.text = "Reps: 0"
            speak("Workout started. Get into position.")
        }

        btnStop.setOnClickListener {
            runState = RunState.IDLE
            speak("Workout stopped. You completed $repCount repetitions.")
            finish() // Close the activity
        }
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

            analysis.setAnalyzer(cameraExecutor) { proxy ->
                processFrame(proxy)
            }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    @ExperimentalGetImage
    private fun processFrame(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        poseDetector.process(input)
            .addOnSuccessListener { pose ->
                // Draw skeleton always
                overlay.setPose(pose, imageProxy.width, imageProxy.height, imageProxy.imageInfo.rotationDegrees)

                // If not running, just show pose + instructions
                if (runState != RunState.RUNNING) {
                    val setupMsg = checkBodySetup(pose, imageProxy.width, imageProxy.height)
                    if (setupMsg != null) {
                        updateStatusThrottled(setupMsg)
                        speakOnce(setupMsg)
                        isPerfectPosition = false
                        hasAnnouncedStart = false
                    } else {
                        updateStatusThrottled("Perfect! Ready to start. Press START or say 'start'.")
                        if (!isPerfectPosition) {
                            isPerfectPosition = true
                            speakOnce("Perfect position. Say start when ready.")
                        }
                    }
                    imageProxy.close()
                    return@addOnSuccessListener
                }

                // Running → check position is OK first
                val setupMsg = checkBodySetup(pose, imageProxy.width, imageProxy.height)
                if (setupMsg != null) {
                    updateStatusThrottled("⚠️ $setupMsg")
                    speakOnce(setupMsg)
                    hasAnnouncedStart = false
                    imageProxy.close()
                    return@addOnSuccessListener
                }

                // Perfect → start or continue
                if (!hasAnnouncedStart) {
                    speakOnce("Begin your reps now!")
                    hasAnnouncedStart = true
                }

                // Coach based on mode
                when (mode) {
                    Mode.SQUAT -> coachSquat(pose)
                    Mode.PLANK -> coachPlank(pose)
                    Mode.YOGA_WARRIOR2 -> coachWarrior2(pose)
                }

                imageProxy.close()
            }
            .addOnFailureListener {
                imageProxy.close()
            }
    }

    // ---------- Squat Coaching ----------
    private fun coachSquat(pose: Pose) {
        val hip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val knee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val ankle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)

        if (hip == null || knee == null || ankle == null) {
            updateStatusThrottled("Keep your left side visible for squat tracking")
            return
        }

        val angle = angleDeg(hip.position, knee.position, ankle.position)

        // DOWN
        if (angle < 90 && !isDown) {
            isDown = true
        }

        // UP → COUNT
        if (angle > 160 && isDown) {
            isDown = false
            repCount++

            runOnUiThread {
                findViewById<TextView>(R.id.tvCounter).text = "Reps: $repCount"
            }

            // 🔊 ONLY NUMBER
            speak(repCount.toString())
        }
    }


    // ---------- Plank Coaching + Score ----------
    private fun coachPlank(pose: Pose) {
        val shoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val hip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val ankle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val elbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)

        if (shoulder == null || hip == null || ankle == null || elbow == null) {
            updateStatusThrottled("Keep shoulder, elbow, hip, ankle visible (side view works best)")
            return
        }

        // Body straightness: angle at hip using shoulder-hip-ankle ~ 160–180 ideal
        val hipAngle = angleDeg(shoulder.position, hip.position, ankle.position)
        val straightScore = scoreInRange(hipAngle, 165.0, 180.0)

        // Elbow under shoulder: x-distance small (simple heuristic)
        val elbowShoulderDx = abs(elbow.position.x - shoulder.position.x)
        val elbowScore = scoreInverse(elbowShoulderDx.toDouble(), goodMax = 35.0, badMin = 120.0)

        val total = (0.7 * straightScore + 0.3 * elbowScore).toInt()

        val msg = buildString {
            append("Hip angle: ${hipAngle.toInt()}° • Score: $total")
            if (hipAngle < 155) append(" • Raise hips a bit")
            else if (hipAngle > 182) append(" • Don't over-arch")
            if (elbowShoulderDx > 90) append(" • Bring elbow under shoulder")
        }

        updateStatusThrottled("Mode: PLANK • $msg")
        if (total >= 85) speakOnce("Perfect plank")
        else if (hipAngle < 155) speakOnce("Raise hips slightly")
    }

    // ---------- Yoga Warrior II Coaching + Score ----------
    private fun coachWarrior2(pose: Pose) {
        // Front knee bend + arms straight line
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

        val kneeAngle = angleDeg(hip.position, knee.position, ankle.position) // front knee
        val kneeScore = scoreInRange(kneeAngle, 80.0, 105.0)

        // Arms horizontal: wrists y close to shoulders y (simple)
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
                    // restart listening on common errors
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
                repCount = 0
                isDown = false
                hasAnnouncedStart = false
                speak("Workout started. Get into position.")
            }
            cmd.contains("stop") -> {
                runState = RunState.IDLE
                speak("Workout stopped. You did $repCount repetitions.")
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
            speakOnce("Say Start to begin. You can say Squat, Plank, or Yoga.")
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
        // angle ABC at point B
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

    // Score 0..100 where inside [min..max] gives 100, outside drops linearly
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

    // Score 0..100 where <= goodMax is 100, >= badMin is 0
    private fun scoreInverse(value: Double, goodMax: Double, badMin: Double): Int {
        if (value <= goodMax) return 100
        if (value >= badMin) return 0
        val t = (value - goodMax) / (badMin - goodMax)
        return (100 * (1.0 - t)).toInt().coerceIn(0, 100)
    }
}