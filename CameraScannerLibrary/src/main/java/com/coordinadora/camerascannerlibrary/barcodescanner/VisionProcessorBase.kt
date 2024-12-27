package com.coordinadora.camerascannerlibrary.barcodescanner

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskExecutors
import com.google.mlkit.vision.common.InputImage
import java.nio.ByteBuffer
import java.util.Timer
import java.util.TimerTask
import kotlin.math.max
import kotlin.math.min

abstract class VisionProcessorBase<T>(context: Context) : VisionImageProcessor {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val fpsTimer = Timer()
    private val executor = ScopedExecutor(TaskExecutors.MAIN_THREAD)
    private var isShutdown = false
    private var numRuns = 0
    private var totalRunMs: Long = 0
    private var maxRunMs: Long = 0
    private var minRunMs = Long.MAX_VALUE
    private var frameProcessedInOneSecondInterval = 0
    private var framesPerSecond = 0

    private var latestImage: ByteBuffer? = null
    private var latestImageMetaData: FrameMetadata? = null
    private var processingImage: ByteBuffer? = null
    private var processingMetaData: FrameMetadata? = null

    init {
        fpsTimer.schedule(object : TimerTask() {
            override fun run() {
                framesPerSecond = frameProcessedInOneSecondInterval
                frameProcessedInOneSecondInterval = 0
            }
        }, 0, 1000)
    }

    override fun processBitmap(bitmap: Bitmap?, graphicOverlay: GraphicOverlay?) {
        requestDetectInImage(InputImage.fromBitmap(bitmap!!, 0), graphicOverlay!!, null)
    }

    @Synchronized
    override fun processByteBuffer(
        data: ByteBuffer?,
        frameMetadata: FrameMetadata?,
        graphicOverlay: GraphicOverlay?
    ) {
        latestImage = data
        latestImageMetaData = frameMetadata
        graphicOverlay?.let { processLatestImage(it)}
    }

    @Synchronized
    private fun processLatestImage(graphicOverlay: GraphicOverlay) {
        processingImage = latestImage
        processingMetaData = latestImageMetaData
        latestImage = null
        latestImageMetaData = null
        processingImage?.let { data ->
            processingMetaData?.let { metadata ->
                if (!isShutdown) {
                    processImage(data, metadata, graphicOverlay)
                }
            }
        }
    }

    private fun processImage(data: ByteBuffer, frameMetadata: FrameMetadata, graphicOverlay: GraphicOverlay) {
        val image = InputImage.fromByteBuffer(data, frameMetadata.width, frameMetadata.height, frameMetadata.rotation, InputImage.IMAGE_FORMAT_NV21)
        requestDetectInImage(image, graphicOverlay, null)
    }

    @ExperimentalGetImage
    override fun processImageProxy(image: ImageProxy?, graphicOverlay: GraphicOverlay?) {
        if (isShutdown) {
            image?.close()
            return
        }
        val inputImage = InputImage.fromMediaImage(image?.image!!, image.imageInfo.rotationDegrees)
        requestDetectInImage(inputImage, graphicOverlay!!, null).addOnCompleteListener { image.close() }
    }

    private fun requestDetectInImage(image: InputImage, graphicOverlay: GraphicOverlay, originalCameraImage: Bitmap?): Task<T> {
        val startMs = SystemClock.elapsedRealtime()

        return detectInImage(image).addOnSuccessListener(executor) { results ->
            updateLatencyMetrics(SystemClock.elapsedRealtime() - startMs)
            graphicOverlay.clear()
            originalCameraImage?.let {
                graphicOverlay.add(CameraImageGraphic(graphicOverlay, it))
            }
            onSuccess(results, graphicOverlay)
            graphicOverlay.postInvalidate()
        }.addOnFailureListener(executor) { e ->
            onFailure(e, graphicOverlay)
        }
    }

    private fun updateLatencyMetrics(currentLatencyMs: Long) {
        numRuns++
        frameProcessedInOneSecondInterval++
        totalRunMs += currentLatencyMs
        maxRunMs = max(currentLatencyMs, maxRunMs)
        minRunMs = min(currentLatencyMs, minRunMs)

        if (frameProcessedInOneSecondInterval == 1) {
            val mi = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(mi)
        }
    }

    private fun onFailure(e: Exception, graphicOverlay: GraphicOverlay) {
        graphicOverlay.clear()
        graphicOverlay.postInvalidate()
        e.printStackTrace()
        onFailure(e)
    }

    override fun stop() {
        executor.shutdown()
        isShutdown = true
        numRuns = 0
        totalRunMs = 0
        fpsTimer.cancel()
    }

    protected abstract fun detectInImage(image: InputImage): Task<T>
    protected abstract fun onSuccess(results: T, graphicOverlay: GraphicOverlay)
    protected abstract fun onFailure(e: Exception)
}