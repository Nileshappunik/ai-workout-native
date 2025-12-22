package com.demo.aiworlout

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.max

class PoseOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val pointPaint = Paint().apply {
        style = Paint.Style.FILL
        strokeWidth = 10f
        isAntiAlias = true
        color = Color.WHITE
    }

    private val linePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
        color = Color.WHITE
    }

    private var pose: Pose? = null
    private var imgWidth = 0
    private var imgHeight = 0
    private var rotation = 0

    fun setPose(p: Pose, imageWidth: Int, imageHeight: Int, rotationDeg: Int) {
        this.pose = p
        this.imgWidth = imageWidth
        this.imgHeight = imageHeight
        this.rotation = rotationDeg
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p = pose ?: return
        if (imgWidth == 0 || imgHeight == 0) return

        // Scale image coords to view coords
        val (srcW, srcH) = when (rotation) {
            90, 270 -> imgHeight to imgWidth
            else -> imgWidth to imgHeight
        }

        val scaleX = width.toFloat() / max(1, srcW).toFloat()
        val scaleY = height.toFloat() / max(1, srcH).toFloat()

        fun drawPoint(type: Int) {
            val lm = p.getPoseLandmark(type) ?: return
            val x = lm.position.x * scaleX
            val y = lm.position.y * scaleY
            canvas.drawCircle(x, y, 8f, pointPaint)
        }

        fun drawLine(a: Int, b: Int) {
            val l1 = p.getPoseLandmark(a) ?: return
            val l2 = p.getPoseLandmark(b) ?: return
            val x1 = l1.position.x * scaleX
            val y1 = l1.position.y * scaleY
            val x2 = l2.position.x * scaleX
            val y2 = l2.position.y * scaleY
            canvas.drawLine(x1, y1, x2, y2, linePaint)
        }

        // Connections (simple skeleton)
        val pairs = listOf(
            PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,

            PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
            PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
            PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,

            PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
            PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
            PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
            PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE,

            PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
            PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP
        )

        for ((a, b) in pairs) drawLine(a, b)

        // Draw key points
        val points = listOf(
            PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST,
            PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE,
            PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE
        )
        points.forEach { drawPoint(it) }
    }
}
