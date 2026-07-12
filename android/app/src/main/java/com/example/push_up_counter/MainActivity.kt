package com.example.push_up_counter

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.google.mediapipe.framework.image.MediaImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
class MainActivity : AppCompatActivity() {
    var poseLandmarker: PoseLandmarker? = null
    val modelName = "pose_landmarker_full.task"
    val baseOptionBuilder = BaseOptions.builder().setModelAssetPath(modelName)
    val baseOptions = baseOptionBuilder.build()

    val optionsBuilder = PoseLandmarker.PoseLandmarkerOptions.builder()
        .setBaseOptions(baseOptionBuilder.build())
        .setResultListener { result, image ->  }
        .setErrorListener {  }
        .setRunningMode(RunningMode.LIVE_STREAM)

    val option = optionsBuilder.build()

    @androidx.camera.core.ExperimentalGetImage
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        poseLandmarker = PoseLandmarker.createFromOptions(this, option)

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                100
            )
        }
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        val imageAnalyzer = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()
            val previewView = findViewById<PreviewView>(R.id.previewView)
            preview.setSurfaceProvider(previewView.surfaceProvider)
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            imageAnalyzer.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                // get the underlying Android Image
                val mediaImage= imageProxy.image
                if (mediaImage != null) {
                    // Convert to MediaPipe image using MediaImageBuilder
                    val mpImage = MediaImageBuilder(mediaImage).build()

                    // Extract the timestamp (in millisecond)
                    val frameTimestamp = imageProxy.imageInfo.timestamp
                    poseLandmarker?.detectAsync(mpImage, frameTimestamp)
                }
                // Close the ImageProxy to prevent camera freeze
                imageProxy.close()
            }
            cameraProvider.bindToLifecycle(this, cameraSelector,preview, imageAnalyzer)},
            ContextCompat.getMainExecutor((this)))
    }
}