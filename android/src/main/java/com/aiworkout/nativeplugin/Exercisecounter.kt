package com.aiworkout.nativeplugin

import android.graphics.PointF
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.*

/**
 * Exercise Counter for multiple exercise types
 * Ported from Swift RELExerciseCounter
 */
class ExerciseCounter {

    private var count: Int = 0
    private var plankTime: Int = 0
    private var state: ExerciseState = ExerciseState.START
    private var holdStartTime: Long? = null
    private var lastStateChangeTime: Long = 0
    private val debounceInterval: Long = 300 // 0.3 seconds in milliseconds

    // Public accessors
    fun getCount(): Int = count
    fun getPlankTime(): Int = plankTime

    // Reset counters and state
    fun reset() {
        count = 0
        plankTime = 0
        state = ExerciseState.START
        holdStartTime = null
        lastStateChangeTime = 0
    }

    // MARK: - Helper Methods
    private fun canChangeState(): Boolean {
        val currentTime = System.currentTimeMillis()
        return currentTime - lastStateChangeTime >= debounceInterval
    }

    private fun updateState(newState: ExerciseState, incrementCount: Boolean = false) {
        if (!canChangeState()) return

        state = newState
        lastStateChangeTime = System.currentTimeMillis()

        if (incrementCount) {
            count++
        }
    }

    // MARK: - Math Helper Functions
    private fun distance(a: PointF, b: PointF): Float {
        return hypot(a.x - b.x, a.y - b.y)
    }

    private fun angleBetween(a: PointF, b: PointF, c: PointF): Float {
        // Angle at point b formed by a-b-c (in degrees)
        val v1x = a.x - b.x
        val v1y = a.y - b.y
        val v2x = c.x - b.x
        val v2y = c.y - b.y

        val mag1 = hypot(v1x, v1y)
        val mag2 = hypot(v2x, v2y)

        if (mag1 < 1e-6f || mag2 < 1e-6f) return 0f

        var cosv = (v1x * v2x + v1y * v2y) / (mag1 * mag2)
        cosv = cosv.coerceIn(-1f, 1f)

        return Math.toDegrees(acos(cosv.toDouble())).toFloat()
    }

    private fun slope(a: PointF, b: PointF): Float {
        if (abs(b.x - a.x) < 1e-6f) return Float.MAX_VALUE
        return (b.y - a.y) / (b.x - a.x)
    }

    // MARK: - Exercise Detection Methods

    /**
     * 1. Squat Detection (improved knee angle detection)
     */
    fun updateSquat(pose: Pose, imageWidth: Int, imageHeight: Int) {
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP) ?: return
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE) ?: return
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE) ?: return
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP) ?: return
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE) ?: return
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE) ?: return

        val leftKneeAngle = angleBetween(leftHip.position, leftKnee.position, leftAnkle.position)
        val rightKneeAngle = angleBetween(rightHip.position, rightKnee.position, rightAnkle.position)
        val avgKneeAngle = (leftKneeAngle + rightKneeAngle) / 2

        val downThreshold = 110f
        val upThreshold = 150f

        when (state) {
            ExerciseState.START, ExerciseState.UP -> {
                if (avgKneeAngle < downThreshold) {
                    updateState(ExerciseState.DOWN)
                }
            }
            ExerciseState.DOWN -> {
                if (avgKneeAngle > upThreshold) {
                    updateState(ExerciseState.UP, incrementCount = true)
                }
            }
            else -> {}
        }
    }

    /**
     * 2. Jumping Jack Detection (improved detection)
     */
    fun updateJumpingJack(pose: Pose, imageWidth: Int, imageHeight: Int) {
        val lShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER) ?: return
        val rShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER) ?: return
        val lWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST) ?: return
        val rWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST) ?: return
        val lAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE) ?: return
        val rAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE) ?: return

        val shoulderWidth = distance(lShoulder.position, rShoulder.position)
        val footDistance = abs(lAnkle.position.x - rAnkle.position.x)
        val handsUp = (lWrist.position.y < lShoulder.position.y - 20) &&
                (rWrist.position.y < rShoulder.position.y - 20)

        val feetWide = footDistance > shoulderWidth * 1.4f
        val feetTogether = footDistance < shoulderWidth * 1.1f

        when (state) {
            ExerciseState.START, ExerciseState.CLOSED -> {
                if (handsUp && feetWide) {
                    updateState(ExerciseState.OPEN, incrementCount = true)
                }
            }
            ExerciseState.OPEN -> {
                if (!handsUp && feetTogether) {
                    updateState(ExerciseState.CLOSED)
                }
            }
            else -> {}
        }
    }

    /**
     * 3. Push Up Detection (chest to ground detection)
     */
    fun updatePushUp(pose: Pose, imageWidth: Int, imageHeight: Int) {
        val nose = pose.getPoseLandmark(PoseLandmark.NOSE) ?: return
        val lWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST) ?: return
        val rWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST) ?: return
        val lShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER) ?: return
        val rShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER) ?: return

        val avgWristY = (lWrist.position.y + rWrist.position.y) / 2
        val avgShoulderY = (lShoulder.position.y + rShoulder.position.y) / 2
        val shoulderToWristDistance = abs(avgShoulderY - avgWristY)

        // Check if person is in plank position (horizontal body alignment)
        val bodyHorizontal = abs(nose.position.y - avgShoulderY) < imageHeight * 0.1f

        val downThreshold = imageHeight * 0.03f  // Close to ground
        val upThreshold = imageHeight * 0.08f    // Arms extended

        if (bodyHorizontal) {
            when (state) {
                ExerciseState.START, ExerciseState.UP -> {
                    if (shoulderToWristDistance < downThreshold) {
                        updateState(ExerciseState.DOWN)
                    }
                }
                ExerciseState.DOWN -> {
                    if (shoulderToWristDistance > upThreshold) {
                        updateState(ExerciseState.UP, incrementCount = true)
                    }
                }
                else -> {}
            }
        }
    }

    /**
     * 4. Lunge Detection (alternating leg detection)
     */
    fun updateLunge(pose: Pose, imageWidth: Int, imageHeight: Int) {
        val lHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP) ?: return
        val rHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP) ?: return
        val lKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE) ?: return
        val rKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE) ?: return
        val lAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE) ?: return
        val rAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE) ?: return

        val leftKneeAngle = angleBetween(lHip.position, lKnee.position, lAnkle.position)
        val rightKneeAngle = angleBetween(rHip.position, rKnee.position, rAnkle.position)

        // Detect if one leg is significantly more bent (lunge position)
        val angleDifference = abs(leftKneeAngle - rightKneeAngle)
        val minAngle = min(leftKneeAngle, rightKneeAngle)

        val lungeThreshold = 30f  // Significant angle difference
        val deepBendThreshold = 110f  // One knee significantly bent

        when (state) {
            ExerciseState.START, ExerciseState.UP -> {
                if (angleDifference > lungeThreshold && minAngle < deepBendThreshold) {
                    updateState(ExerciseState.DOWN)
                }
            }
            ExerciseState.DOWN -> {
                if (angleDifference < 20 && leftKneeAngle > 140 && rightKneeAngle > 140) {
                    updateState(ExerciseState.UP, incrementCount = true)
                }
            }
            else -> {}
        }
    }

    /**
     * 5. Plank Hold Detection (time-based with improved stability detection)
     */
    fun updatePlank(pose: Pose, imageWidth: Int, imageHeight: Int): Int {
        val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
        val lShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val lHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val lAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        if (nose == null || lShoulder == null || rShoulder == null ||
            lHip == null || rHip == null || lAnkle == null || rAnkle == null) {
            holdStartTime = null
            plankTime = 0
            return 0
        }

        val avgShoulder = PointF(
            (lShoulder.position.x + rShoulder.position.x) / 2,
            (lShoulder.position.y + rShoulder.position.y) / 2
        )
        val avgHip = PointF(
            (lHip.position.x + rHip.position.x) / 2,
            (lHip.position.y + rHip.position.y) / 2
        )
        val avgAnkle = PointF(
            (lAnkle.position.x + rAnkle.position.x) / 2,
            (lAnkle.position.y + rAnkle.position.y) / 2
        )

        // Check body alignment (straight line from shoulders to ankles)
        val shoulderHipAngle = angleBetween(avgShoulder, avgHip, avgAnkle)
        val isPlankPosition = abs(shoulderHipAngle - 180) < 25

        // Check if person is horizontal (not standing)
        val bodyHorizontal = abs(avgShoulder.y - avgHip.y) < imageHeight * 0.15f

        if (isPlankPosition && bodyHorizontal) {
            if (holdStartTime == null) {
                holdStartTime = System.currentTimeMillis()
            }
            val elapsed = (System.currentTimeMillis() - (holdStartTime ?: System.currentTimeMillis())) / 1000
            plankTime = elapsed.toInt()
            return plankTime
        } else {
            holdStartTime = null
            plankTime = 0
            return 0
        }
    }

    /**
     * 6. Bicep Curl Detection (elbow angle detection)
     */
    fun updateBicepCurl(pose: Pose, imageWidth: Int, imageHeight: Int) {
        val lShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER) ?: return
        val lElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW) ?: return
        val lWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST) ?: return
        val rShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER) ?: return
        val rElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW) ?: return
        val rWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST) ?: return

        val leftElbowAngle = angleBetween(lShoulder.position, lElbow.position, lWrist.position)
        val rightElbowAngle = angleBetween(rShoulder.position, rElbow.position, rWrist.position)
        val avgElbowAngle = (leftElbowAngle + rightElbowAngle) / 2

        val contractedThreshold = 60f   // Arms curled up
        val extendedThreshold = 140f    // Arms extended down

        when (state) {
            ExerciseState.START, ExerciseState.EXTENDED -> {
                if (avgElbowAngle < contractedThreshold) {
                    updateState(ExerciseState.CONTRACTED)
                }
            }
            ExerciseState.CONTRACTED -> {
                if (avgElbowAngle > extendedThreshold) {
                    updateState(ExerciseState.EXTENDED, incrementCount = true)
                }
            }
            else -> {}
        }
    }

    /**
     * 7. Shoulder Press Detection (vertical arm movement)
     */
    fun updateShoulderPress(pose: Pose, imageWidth: Int, imageHeight: Int) {
        val lShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER) ?: return
        val lWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST) ?: return
        val rShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER) ?: return
        val rWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST) ?: return

        val avgShoulderY = (lShoulder.position.y + rShoulder.position.y) / 2
        val avgWristY = (lWrist.position.y + rWrist.position.y) / 2

        val handsAboveShoulders = avgWristY < avgShoulderY - 30  // Hands well above shoulders
        val handsAtShoulderLevel = abs(avgWristY - avgShoulderY) < 30  // Hands at shoulder level

        when (state) {
            ExerciseState.START, ExerciseState.LOWERED -> {
                if (handsAboveShoulders) {
                    updateState(ExerciseState.RAISED, incrementCount = true)
                }
            }
            ExerciseState.RAISED -> {
                if (handsAtShoulderLevel) {
                    updateState(ExerciseState.LOWERED)
                }
            }
            else -> {}
        }
    }

    /**
     * 8. Burpee Detection (complex multi-phase movement)
     */
    fun updateBurpee(pose: Pose, imageWidth: Int, imageHeight: Int) {
        val nose = pose.getPoseLandmark(PoseLandmark.NOSE) ?: return
        val lShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER) ?: return
        val rShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER) ?: return
        val lHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP) ?: return
        val rHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP) ?: return
        val lWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST) ?: return
        val rWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST) ?: return

        val avgShoulderY = (lShoulder.position.y + rShoulder.position.y) / 2
        val avgHipY = (lHip.position.y + rHip.position.y) / 2
        val avgWristY = (lWrist.position.y + rWrist.position.y) / 2

        // Check if person is in plank/push-up position
        val isInPlankPosition = abs(avgShoulderY - avgHipY) < imageHeight * 0.1f &&
                avgWristY > avgShoulderY

        // Check if person is standing with hands up (jump phase)
        val isJumping = nose.position.y < avgShoulderY - 20 &&
                lWrist.position.y < lShoulder.position.y &&
                rWrist.position.y < rShoulder.position.y

        // Check if person is in normal standing position
        val isStanding = avgHipY < avgShoulderY && !isJumping && !isInPlankPosition

        when (state) {
            ExerciseState.START, ExerciseState.STANDING -> {
                if (isInPlankPosition) {
                    updateState(ExerciseState.DOWN)
                }
            }
            ExerciseState.DOWN -> {
                if (isJumping) {
                    updateState(ExerciseState.JUMPING, incrementCount = true)
                } else if (isStanding) {
                    updateState(ExerciseState.STANDING)
                }
            }
            ExerciseState.JUMPING -> {
                if (isStanding) {
                    updateState(ExerciseState.STANDING)
                }
            }
            else -> {}
        }
    }
}

/**
 * Exercise States for tracking movement phases
 */
enum class ExerciseState {
    START,
    DOWN,
    UP,
    UP_DETECTED,
    CLOSED,
    OPEN,
    EXTENDED,
    CONTRACTED,
    HOLDING,
    JUMPING,
    STANDING,
    LOWERED,
    RAISED
}