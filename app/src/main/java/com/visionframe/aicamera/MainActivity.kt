package com.visionframe.aicamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.visionframe.aicamera.camera.CameraXManager
import com.visionframe.aicamera.databinding.ActivityMainBinding
import com.visionframe.aicamera.ui.CameraViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * MainActivity - Entry Point for VisionFrame AI Camera Native Application
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
        } else {
            Toast.makeText(this, "Izin kamera diperlukan untuk aplikasi ini", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        observeViewModel()
        checkAndRequestPermissions()

        // Preload Splash Screen auto-dismiss after 1.5 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            binding.splashOverlay.animate()
                .alpha(0f)
                .setDuration(400)
                .withEndAction {
                    binding.splashOverlay.visibility = View.GONE
                }
        }, 1500)
    }

    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            initCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun initCamera() {
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

                if (state.autoCaptureTriggered) {
                    Toast.makeText(this@MainActivity, "📷 Auto Capture PERFECT SHOT!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnToggleGrid.setOnClickListener {
            viewModel.toggleGrid()
        }

        binding.btnShutter.setOnClickListener {
            Toast.makeText(this, "Capturing Photo...", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraXManager?.shutdown()
    }
}
