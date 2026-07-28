package com.visionframe.aicamera.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * SubjectDetector — Real YOLOv11n TFLite inference engine
 * Input: 640x640 RGB bitmap → Output: bounding boxes + class + confidence
 */
data class DetectedObject(
    val boundingBox: RectF,   // normalized 0..1
    val confidence: Float,
    val className: String,
    val trackingId: Int,
    val priorityScore: Int
)

class SubjectDetector(private val context: Context) {

    companion object {
        private const val TAG = "SubjectDetector"
        private const val MODEL_FILE = "yolov11n.tflite"
        private const val INPUT_SIZE = 640
        private const val CONF_THRESHOLD = 0.35f
        private val COCO_CLASSES = arrayOf(
            "person","bicycle","car","motorcycle","airplane","bus","train","truck","boat",
            "traffic light","fire hydrant","stop sign","parking meter","bench","bird","cat",
            "dog","horse","sheep","cow","elephant","bear","zebra","giraffe","backpack",
            "umbrella","handbag","tie","suitcase","frisbee","skis","snowboard","sports ball",
            "kite","baseball bat","baseball glove","skateboard","surfboard","tennis racket",
            "bottle","wine glass","cup","fork","knife","spoon","bowl","banana","apple",
            "sandwich","orange","broccoli","carrot","hot dog","pizza","donut","cake","chair",
            "couch","potted plant","bed","dining table","toilet","tv","laptop","mouse",
            "remote","keyboard","cell phone","microwave","oven","toaster","sink","refrigerator",
            "book","clock","vase","scissors","teddy bear","hair drier","toothbrush"
        )
        private val CLASS_PRIORITIES = mapOf(
            "person" to 90, "cat" to 80, "dog" to 80, "bird" to 70,
            "horse" to 70, "bear" to 70, "bicycle" to 50, "car" to 40,
            "motorcycle" to 40, "chair" to 20, "couch" to 20, "bottle" to 15
        )
    }

    private var interpreter: Interpreter? = null
    private var isInitialized = false
    private var trackingCounter = 0

    init {
        try {
            val modelBuffer = loadModelFile()
            val options = Interpreter.Options().apply {
                numThreads = 4
            }
            interpreter = Interpreter(modelBuffer, options)
            isInitialized = true
            Log.d(TAG, "YOLOv11n TFLite model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load TFLite model: ${e.message}")
            isInitialized = false
        }
    }

    private fun loadModelFile(): ByteBuffer {
        val assetFd = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(assetFd.fileDescriptor)
        val channel = inputStream.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, assetFd.startOffset, assetFd.declaredLength)
            .also { inputStream.close() }
    }

    fun detectSubjects(bitmap: Bitmap, imageWidth: Int, imageHeight: Int): List<DetectedObject> {
        if (!isInitialized || interpreter == null) {
            return emptyList()
        }

        return try {
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
            val inputBuffer = bitmapToByteBuffer(resizedBitmap)

            // YOLOv11n output: [1, 84, 8400] → 84 = 4 box coords + 80 class scores
            val outputShape = interpreter!!.getOutputTensor(0).shape()
            val output = Array(1) { Array(outputShape[1]) { FloatArray(outputShape[2]) } }

            interpreter!!.run(inputBuffer, output)

            parseYoloOutput(output[0], imageWidth, imageHeight)
        } catch (e: Exception) {
            Log.e(TAG, "Inference error: ${e.message}")
            emptyList()
        }
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
            buffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)
            buffer.putFloat((pixel and 0xFF) / 255.0f)
        }
        return buffer
    }

    private fun parseYoloOutput(output: Array<FloatArray>, imgW: Int, imgH: Int): List<DetectedObject> {
        val detections = mutableListOf<DetectedObject>()
        val numDetections = output[0].size  // 8400

        for (i in 0 until numDetections) {
            val cx = output[0][i]
            val cy = output[1][i]
            val w  = output[2][i]
            val h  = output[3][i]

            // Find best class
            var maxScore = 0f
            var maxClass = 0
            for (c in 0 until 80) {
                val score = output[4 + c][i]
                if (score > maxScore) {
                    maxScore = score
                    maxClass = c
                }
            }

            if (maxScore < CONF_THRESHOLD) continue

            // Convert YOLO normalized center format to pixel RectF
            val x1 = ((cx - w / 2f) * imgW).coerceIn(0f, imgW.toFloat())
            val y1 = ((cy - h / 2f) * imgH).coerceIn(0f, imgH.toFloat())
            val x2 = ((cx + w / 2f) * imgW).coerceIn(0f, imgW.toFloat())
            val y2 = ((cy + h / 2f) * imgH).coerceIn(0f, imgH.toFloat())

            if (x2 <= x1 || y2 <= y1) continue

            val className = if (maxClass < COCO_CLASSES.size) COCO_CLASSES[maxClass] else "object"
            val priority = CLASS_PRIORITIES[className] ?: 10

            detections.add(
                DetectedObject(
                    boundingBox = RectF(x1, y1, x2, y2),
                    confidence = maxScore,
                    className = className,
                    trackingId = ++trackingCounter,
                    priorityScore = priority
                )
            )
        }

        return nonMaxSuppression(detections).sortedByDescending { it.priorityScore }
    }

    private fun nonMaxSuppression(boxes: List<DetectedObject>, iouThreshold: Float = 0.45f): List<DetectedObject> {
        val sorted = boxes.sortedByDescending { it.confidence }.toMutableList()
        val result = mutableListOf<DetectedObject>()
        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            result.add(best)
            sorted.removeAll { iou(it.boundingBox, best.boundingBox) > iouThreshold }
        }
        return result.take(10)
    }

    private fun iou(a: RectF, b: RectF): Float {
        val interLeft = maxOf(a.left, b.left)
        val interTop = maxOf(a.top, b.top)
        val interRight = minOf(a.right, b.right)
        val interBottom = minOf(a.bottom, b.bottom)
        if (interRight <= interLeft || interBottom <= interTop) return 0f
        val interArea = (interRight - interLeft) * (interBottom - interTop)
        val unionArea = a.width() * a.height() + b.width() * b.height() - interArea
        return if (unionArea <= 0f) 0f else interArea / unionArea
    }

    fun getPrimarySubject(subjects: List<DetectedObject>): DetectedObject? =
        subjects.maxByOrNull { it.priorityScore * it.confidence }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
