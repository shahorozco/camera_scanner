package com.coordinadora.camerascannerlibrary.main

import android.Manifest.permission.CAMERA
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.coordinadora.camerascannerlibrary.R
import com.coordinadora.camerascannerlibrary.barcodescanner.BarcodeScannerProcessor
import com.coordinadora.camerascannerlibrary.barcodescanner.CameraXViewModel
import com.coordinadora.camerascannerlibrary.barcodescanner.ExchangeScannedData
import com.coordinadora.camerascannerlibrary.barcodescanner.GraphicOverlay
import com.coordinadora.camerascannerlibrary.barcodescanner.VisionImageProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CameraDialogFragment : DialogFragment(), ExchangeScannedData {
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var toneGen: ToneGenerator
    private lateinit var barcodeRegex: Regex
    private lateinit var graphicOverlay: GraphicOverlay
    private lateinit var previewView: PreviewView
    private lateinit var imgExpandCollapse: ImageView
    private lateinit var imgTorch: ImageView
    private lateinit var imgClose: ImageView

    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null
    private var imageProcessor: VisionImageProcessor? = null
    private var needUpdateGraphicOverlayImageSourceInfo = false
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var cameraSelector: CameraSelector? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private val stateLensFacing = "lens_facing"
    private var isFullScreen = true
    private var isFlashOn: Boolean = false
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var listener: DialogListener? = null
    private var context: Context? = null
    private var canScan = true
    private var handlerScheduled = false
    private val handler = Handler(Looper.getMainLooper())
    private var resetRunnable: Runnable? = null

    interface DialogListener {
        fun validateScanning(scanning: String)
        fun setupScannerDrawable()
        fun getUnits(): List<*>
    }

    interface UnitProvider {
        fun getUnits(): List<ScannableUnit>
    }

    interface ScannableUnit {
        fun hasCode(code: String): Boolean
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as DialogListener
        this.context = context
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FloatingDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_fragment_camera, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        startCameraPermissionRequest()
        initializeComponents(savedInstanceState)
        setupDialogProperties()
        setupButtons()
        collapseDialog()
    }

    private fun initializeViews(view: View) {
        graphicOverlay = view.findViewById(R.id.graphic_overlay)
        previewView = view.findViewById(R.id.preview_view)
        imgExpandCollapse = view.findViewById(R.id.img_expand_collapse)
        imgTorch = view.findViewById(R.id.img_torch)
        imgClose = view.findViewById(R.id.img_close)
    }

    private fun startCameraPermissionRequest() {
        requestPermissionLauncher.launch(CAMERA)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            setupCamera()
        } else {
            Toast.makeText(
                context,
                "Camera permission is required to use this feature",
                Toast.LENGTH_LONG
            ).show()
            this.dismiss()
        }
    }

    private fun initializeComponents(savedInstanceState: Bundle?) {
        toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        barcodeRegex = Regex("\\x1D")

        savedInstanceState?.let {
            lensFacing = it.getInt(stateLensFacing, CameraSelector.LENS_FACING_BACK)
        }
    }

    private fun setupDialogProperties() {
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            attributes.apply {
                width = context.resources.displayMetrics.widthPixels
                height = context.resources.displayMetrics.heightPixels
                gravity = Gravity.BOTTOM or Gravity.END
                x = 5.dpToPixels(context)
                y = 45.dpToPixels(context)

                flags =
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }

            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

            val decorView = decorView
            decorView.setOnTouchListener(DraggableTouchListener(this))
        }
    }

    private fun setupButtons() {
        imgExpandCollapse.setOnClickListener {
            if (isFullScreen) {
                collapseDialog()
            } else {
                expandDialog()
            }

            isFullScreen = !isFullScreen
        }

        imgTorch.setOnClickListener {
            toggleFlash()
        }

        imgClose.setOnClickListener {
            dismiss()
        }
    }

    private fun collapseDialog() {
        imgClose.visibility = View.GONE
        imgExpandCollapse.setImageResource(R.drawable.ic_expand)

        val size = 170.dpToPixels(requireContext())

        dialog?.window?.apply {
            val params = this.attributes
            params.width = size
            params.height = size
            params.x = 5.dpToPixels(context)
            params.y = 45.dpToPixels(context)
            this.attributes = params
        }

        animateSize(size, size)
    }

    private fun toggleFlash() {
        isFlashOn = !isFlashOn
        camera?.cameraControl?.enableTorch(isFlashOn)
        imgTorch.setImageResource(if (isFlashOn) R.drawable.ic_flash_light_on else R.drawable.ic_flash_light_off)
    }

    private fun getFlashMode(): Int {
        return if (isFlashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }

    private fun expandDialog() {
        imgClose.visibility = View.VISIBLE
        imgExpandCollapse.setImageResource(R.drawable.ic_collapse)

        val margin = 5.dpToPixels(requireContext())

        val targetWidth =
            resources.displayMetrics.widthPixels - 2 * margin
        val targetHeight =
            resources.displayMetrics.heightPixels - 2 * margin

        dialog?.window?.apply {
            val params = attributes
            params.width = targetWidth
            params.height = targetHeight
            params.x = margin
            params.y = margin
            attributes = params
        }

        animateSize(targetWidth, targetHeight)
    }

    private class DraggableTouchListener(val window: Window) : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = window.attributes.x
                    initialY = window.attributes.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()

                    window.attributes = window.attributes.apply {
                        x = initialX - deltaX
                        y = initialY - deltaY
                    }

                    return true
                }
            }

            return false
        }
    }

    private fun animateSize(width: Int, height: Int) {
        dialog?.window?.let { window ->
            val widthAnimator = ValueAnimator.ofInt(window.attributes.width, width)
            widthAnimator.addUpdateListener { animation ->
                val params = window.attributes
                params.width = animation.animatedValue as Int
                window.attributes = params
            }

            val heightAnimator = ValueAnimator.ofInt(window.attributes.height, height)
            heightAnimator.addUpdateListener { animation ->
                val params = window.attributes
                params.height = animation.animatedValue as Int
                window.attributes = params
            }

            widthAnimator.duration = 200
            heightAnimator.duration = 200

            widthAnimator.start()
            heightAnimator.start()
        }
    }

    private fun setupCamera() {
        cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        initCamera()
    }

    private fun initCamera() {
        ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireContext().applicationContext as Application)
        )[CameraXViewModel::class.java].processCameraProvider.observe(this) { provider ->
            cameraProvider = provider

            bindCameraUseCases()
            bindAnalysisUseCase()
        }
    }

    private fun bindCameraUseCases() {
        if (previewUseCase != null) cameraProvider.unbind(previewUseCase!!)

        imageCapture = ImageCapture.Builder()
            .setFlashMode(getFlashMode())
            .build()

        previewUseCase = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        camera = cameraProvider.bindToLifecycle(this, cameraSelector!!, previewUseCase)
    }

    private fun bindAnalysisUseCase() {
        if (analysisUseCase != null) cameraProvider.unbind(analysisUseCase!!)

        imageProcessor?.stop()
        imageProcessor = BarcodeScannerProcessor(requireActivity(), this as ExchangeScannedData)

        val builder = ImageAnalysis.Builder()
        needUpdateGraphicOverlayImageSourceInfo = true

        analysisUseCase = builder.build().also {
            it.setAnalyzer(ContextCompat.getMainExecutor(requireContext())) { imageProxy ->
                updateGraphicOverlay(imageProxy)
                imageProcessor?.processImageProxy(imageProxy, graphicOverlay)
            }
            cameraProvider.bindToLifecycle(this, cameraSelector!!, it)
        }
    }

    private fun updateGraphicOverlay(imageProxy: ImageProxy) {
        if (needUpdateGraphicOverlayImageSourceInfo) {
            val isImageFlipped = lensFacing == CameraSelector.LENS_FACING_FRONT
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            if (rotationDegrees == 0 || rotationDegrees == 180) {
                graphicOverlay.setImageSourceInfo(
                    imageProxy.width,
                    imageProxy.height,
                    isImageFlipped
                )
            } else {
                graphicOverlay.setImageSourceInfo(
                    imageProxy.height,
                    imageProxy.width,
                    isImageFlipped
                )
            }

            needUpdateGraphicOverlayImageSourceInfo = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        toneGen.release()
    }

    override fun sendScannedCode(code: String?) {
        if (!canScan || code == null) return

        val codeScanning = code.replace(barcodeRegex, "")
        canScan = false

        scope.launch {
            val unitProvider = context as? UnitProvider ?: return@launch
            val listPackage = unitProvider.getUnits()

            val isCodeInList = listPackage.any { it.hasCode(codeScanning) }

            if (!isCodeInList) {
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                listener?.validateScanning(codeScanning)
            }

            if (!handlerScheduled) {
                handlerScheduled = true

                resetRunnable = Runnable {
                    canScan = true
                    handlerScheduled = false
                }

                handler.postDelayed(resetRunnable!!, 4000)
            }
        }
    }

    fun enableScanner() {
        canScan = true
        handlerScheduled = false

        resetRunnable?.let {
            handler.removeCallbacks(it)
        }
    }

    fun enableScannerPostDelayed(delayMillis: Long) {
        Handler(Looper.getMainLooper()).postDelayed({ enableScanner() }, delayMillis)
    }

    private fun Int.dpToPixels(context: Context): Int =
        (this * context.resources.displayMetrics.density).toInt()
}