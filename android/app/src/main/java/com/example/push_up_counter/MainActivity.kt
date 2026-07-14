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
import kotlin.math.acos
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {
    var poseLandmarker: PoseLandmarker? = null
    val modelName = "pose_landmarker_full.task"
    val baseOptionBuilder = BaseOptions.builder().setModelAssetPath(modelName)
    val baseOptions = baseOptionBuilder.build()


    // declare the counter variable for the push-up numbers of the user.
    var counter = 0
    // declare the current stage of the push-up.
    var stage: String? = null

    //decalre the threshold of the angles.
    var upAngle = 160
    var downAngle = 90
    private fun calculate(a: FloatArray, b: FloatArray, c: FloatArray): Float {

        val ba = FloatArray(2) {a[it] - b[it]}
        val bc = FloatArray(2) {c[it] - b[it]}

        val dot = FloatArray(2) {ba[it] * bc[it]}.sum()

        val normBa = sqrt(FloatArray(2){ba[it]*ba[it]}.sum())
        val normBc = sqrt(FloatArray(2){bc[it]*bc[it]}.sum())

        val cosine = dot / (normBa * normBc)
        val clipCosine = cosine.coerceIn(-1.0f,1.0f)

        val angleInRadians: Double = acos(clipCosine.toDouble())
        val angleInDegree: Double = Math.toDegrees(angleInRadians)

        return angleInDegree.toFloat()
    }

    val optionsBuilder = PoseLandmarker.PoseLandmarkerOptions.builder()
        .setBaseOptions(baseOptionBuilder.build())
        .setResultListener { result, image ->
            val  landmarks = result.landmarks().firstOrNull() ?: return@setResultListener
            val leftShoulder = landmarks[11]
            val rightShoulder = landmarks[12]

            val leftElbow = landmarks[13]
            val rightElbow = landmarks[14]

            val leftWrist = landmarks[15]
            val rightWrist = landmarks[16]

            val leftShoulderArray = floatArrayOf(leftShoulder.x(),leftShoulder.y())
            val rightShoulderArray = floatArrayOf(rightShoulder.x(),rightShoulder.y())

            val leftElbowArray = floatArrayOf(leftElbow.x(),leftElbow.y())
            val rightElbowArray = floatArrayOf(rightElbow.x(),rightElbow.y())

            val leftWristArray = floatArrayOf(leftWrist.x(),leftWrist.y())
            val rightWristArray = floatArrayOf(rightWrist.x(),rightWrist.y())

            val leftAngle = calculate(leftShoulderArray,leftElbowArray,leftWristArray)

            if (leftAngle >= upAngle) {
                if (stage == "down") {
                    counter += 1
                }
                stage = "up"
            } else if (leftAngle <= downAngle) {
                if (stage == "up"){
                    stage = "down"
                }
            }
        }
        .setErrorListener {  }
        .setRunningMode(RunningMode.LIVE_STREAM)

    val option = optionsBuilder.build()


    //put androidx.camera.core.ExperimentalGetImage to suppress the warning prompt.
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