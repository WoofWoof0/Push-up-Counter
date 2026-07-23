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
import com.google.mediapipe.formats.proto.LandmarkProto
import com.google.mediapipe.framework.image.BitmapImageBuilder
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

    //declare the threshold of the angles.
    var upAngle = 160
    var downAngle = 90

    val minVisibility = 0.6f

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

            val angle : Float
            val shoulderArray : FloatArray
            val elbowArray : FloatArray
            val wristArray : FloatArray
            var visElbow : com.google.mediapipe.tasks.components.containers.NormalizedLandmark

            if (landmarks[13].visibility().orElse(0.0f) > landmarks[14].visibility().orElse(0.0f)) {
                shoulderArray = floatArrayOf(leftShoulder.x(),leftShoulder.y())
                elbowArray = floatArrayOf(leftElbow.x(),leftElbow.y())
                wristArray = floatArrayOf(leftWrist.x(),leftWrist.y())
                visElbow = leftElbow
            } else {
                shoulderArray = floatArrayOf(rightShoulder.x(),rightShoulder.y())
                elbowArray = floatArrayOf(rightElbow.x(),rightElbow.y())
                wristArray = floatArrayOf(rightWrist.x(),rightWrist.y())
                visElbow = rightElbow
            }

            if (leftShoulder.visibility().orElse(0.0f) > minVisibility && leftElbow.visibility().orElse(0.0f) > minVisibility && leftWrist.visibility().orElse(0.0f) > minVisibility) {

                angle = calculate(shoulderArray,elbowArray,wristArray)

            } else {

                angle = calculate(shoulderArray, elbowArray, wristArray)

            }

            if (angle >= upAngle) {
                if (stage == "down") {
                    counter+=1
                }
                stage = "up"
            } else if (angle <= downAngle){
                if (stage == "up") {
                    stage = "down"
                }
            }

            runOnUiThread {
                val overlayView = findViewById<OverlayView>(R.id.overlayView)
                overlayView.counter = counter
                overlayView.stage = stage
                overlayView.invalidate()
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

        val imageAnalyzer = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).setOutputImageFormat(
            ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()
            val previewView = findViewById<PreviewView>(R.id.previewView)
            preview.setSurfaceProvider(previewView.surfaceProvider)
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            imageAnalyzer.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                // get the underlying Android Image
                val bitmap = imageProxy.toBitmap()
                val mpImage = BitmapImageBuilder(bitmap).build()
                val frameTimestamp = imageProxy.imageInfo.timestamp
                poseLandmarker?.detectAsync(mpImage, frameTimestamp)
                // Close the ImageProxy to prevent camera freeze
                imageProxy.close()
            }

            cameraProvider.bindToLifecycle(this, cameraSelector,preview, imageAnalyzer)},
            ContextCompat.getMainExecutor((this)))
    }
}