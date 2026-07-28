package com.visionframe.aicamera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AlphaAnimation
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.visionframe.aicamera.camera.CameraXManager
import com.visionframe.aicamera.databinding.ActivityMainBinding
import com.visionframe.aicamera.ui.CameraMode
import com.visionframe.aicamera.ui.CameraViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * MainActivity — VisionFrame AI Camera Entry Point
 * iPhone / Xiaomi AI camera UI experience with smooth animations & shutter blink
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: CameraViewModel by viewModels()
    private var cameraXManager: CameraXManager? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            initCamera()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        observeViewModel()
        checkAndRequestPermissions()

        // Splash screen fade out after 1.2s
        Handler(Looper.getMainLooper()).postDelayed({
            binding.splashOverlay.animate()
                .alpha(0f)
                .setDuration(400)
                .withEndAction {
                    binding.splashOverlay.visibility = View.GONE
                }
        }, 1200)
    }

    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            initCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun initCamera() {
        cameraXManager?.shutdown()
        cameraXManager = CameraXManager(
            context = this,
            lifecycleOwner = this,
            previewView = binding.viewFinder,
            onFrameAnalyzed = { _, _ -> }
        )

        cameraXManager?.startCamera { subjects, w, h ->
            viewModel.processFrame(subjects, w, h)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                val result = state.framingResult
                val smoothedRect = state.smoothedRect

                if (result != null && smoothedRect != null) {
                    binding.overlayView.showRuleOfThirds = state.showGrid
                    binding.overlayView.updateFraming(result, smoothedRect)
                }

                // AI status pill text & animation
                binding.tvAiStatus.text = state.aiStatusText
                if (binding.aiStatusBadge.alpha == 0f) {
                    binding.aiStatusBadge.animate().alpha(1f).setDuration(300).start()
                }

                // Auto capture trigger
                if (state.autoCaptureTriggered) {
                    triggerShutterBlink()
                    viewModel.resetAutoCaptureTrigger()
                }

                // Mode update
                updateModeUI(state.cameraMode)
            }
        }
    }

    private fun setupListeners() {
        // Shutter Button Click -> Blink animation, no toast notification
        binding.btnShutter.setOnClickListener {
            triggerShutterBlink()
        }

        // Toggle Grid
        binding.btnToggleGrid.setOnClickListener {
            viewModel.toggleGrid()
        }

        // Flip Camera (Front / Back)
        binding.btnFlipCamera.setOnClickListener {
            binding.btnFlipCamera.animate()
                .rotationBy(180f)
                .setDuration(300)
                .start()
            viewModel.toggleCamera()
            initCamera()
        }

        // Mode Selectors
        binding.tabPhoto.setOnClickListener { viewModel.setCameraMode(CameraMode.PHOTO) }
        binding.tabVideo.setOnClickListener { viewModel.setCameraMode(CameraMode.VIDEO) }
        binding.tabPro.setOnClickListener { viewModel.setCameraMode(CameraMode.PRO) }

        // Zoom Click Handlers
        binding.zoom05x.setOnClickListener { setZoomSelected("0.5x", binding.zoom05x) }
        binding.zoom1x.setOnClickListener { setZoomSelected("1x", binding.zoom1x) }
        binding.zoom2x.setOnClickListener { setZoomSelected("2x", binding.zoom2x) }
        binding.zoom4x.setOnClickListener { setZoomSelected("4x", binding.zoom4x) }

        // Flash Button
        binding.btnFlash.setOnClickListener {
            viewModel.toggleFlash()
        }
    }

    /**
     * Camera shutter screen blink effect (white flash overlay fade in/out)
     * No notification popup.
     */
    private fun triggerShutterBlink() {
        binding.captureFlash.visibility = View.VISIBLE
        binding.captureFlash.alpha = 1f

        val fadeOut = AlphaAnimation(1f, 0f).apply {
            duration = 250
            fillAfter = true
        }

        binding.captureFlash.startAnimation(fadeOut)

        // Capture current preview view bitmap to update thumbnail
        binding.viewFinder.bitmap?.let { bitmap ->
            binding.imgLastPhoto.setImageBitmap(bitmap)
            viewModel.setLastCapturedPhoto(bitmap)
        }
    }

    private fun setZoomSelected(zoomText: String, selectedView: View) {
        val views = listOf(binding.zoom05x, binding.zoom1x, binding.zoom2x, binding.zoom4x)
        views.forEach { v ->
            v.setBackgroundResource(0)
            (v as? android.widget.TextView)?.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        }
        selectedView.setBackgroundResource(R.drawable.zoom_selected_bg)
        (selectedView as? android.widget.TextView)?.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
        viewModel.setZoom(zoomText)
    }

    private fun updateModeUI(mode: CameraMode) {
        when (mode) {
            CameraMode.PHOTO -> {
                binding.tabPhoto.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                binding.tabVideo.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                binding.tabPro.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                binding.videoRecordDot.visibility = View.GONE
            }
            CameraMode.VIDEO -> {
                binding.tabVideo.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                binding.tabPhoto.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                binding.tabPro.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                binding.videoRecordDot.visibility = View.VISIBLE
            }
            CameraMode.PRO -> {
                binding.tabPro.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                binding.tabPhoto.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                binding.tabVideo.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                binding.videoRecordDot.visibility = View.GONE
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraXManager?.shutdown()
    }
}
