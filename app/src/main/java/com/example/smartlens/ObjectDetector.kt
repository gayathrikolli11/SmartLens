package com.example.smartlens

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ObjectDetector(context: Context) {

    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()

    companion object {
        private const val MODEL_PATH = "mobilenet_v1_1.0_224_quant.tflite"
        private const val LABELS_PATH = "labels.txt"
        private const val IMAGE_SIZE = 224
    }

    init {
        try {
            // Load model
            val model = FileUtil.loadMappedFile(context, MODEL_PATH)
            interpreter = Interpreter(model)

            // Load labels
            labels = FileUtil.loadLabels(context, LABELS_PATH)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun detectObjects(bitmap: Bitmap): List<DetectionResult> {
        if (interpreter == null || labels.isEmpty()) {
            return emptyList()
        }

        // Resize bitmap to model input size
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE, IMAGE_SIZE, true)

        // Convert to ByteBuffer
        val byteBuffer = convertBitmapToByteBuffer(resizedBitmap)

        // Output array (1000 classes for MobileNet)
        val output = Array(1) { ByteArray(labels.size) }

        // Run inference
        interpreter?.run(byteBuffer, output)

        // Process results
        return processOutput(output[0])
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(1 * IMAGE_SIZE * IMAGE_SIZE * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(IMAGE_SIZE * IMAGE_SIZE)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var pixel = 0
        for (i in 0 until IMAGE_SIZE) {
            for (j in 0 until IMAGE_SIZE) {
                val value = intValues[pixel++]
                byteBuffer.put(((value shr 16) and 0xFF).toByte())
                byteBuffer.put(((value shr 8) and 0xFF).toByte())
                byteBuffer.put((value and 0xFF).toByte())
            }
        }

        return byteBuffer
    }

    private fun processOutput(output: ByteArray): List<DetectionResult> {
        val results = mutableListOf<DetectionResult>()

        for (i in output.indices) {
            val confidence = (output[i].toInt() and 0xFF) / 255.0f
            if (confidence > 0.3f && i < labels.size) {
                results.add(DetectionResult(labels[i], confidence))
            }
        }

        return results.sortedByDescending { it.confidence }.take(3)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}