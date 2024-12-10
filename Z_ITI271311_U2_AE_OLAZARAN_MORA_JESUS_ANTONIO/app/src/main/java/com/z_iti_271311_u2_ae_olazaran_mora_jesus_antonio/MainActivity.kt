package com.z_iti_271311_u2_ae_olazaran_mora_jesus_antonio

import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.z_iti_271311_u2_ae_olazaran_mora_jesus_antonio.ColorBlobDetector
import com.z_iti_271311_u2_ae_olazaran_mora_jesus_antonio.CubeRenderer
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.JavaCameraView
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "OCVSample::Activity"
    }

    private var mIsColorSelected = false
    private lateinit var mRgba: Mat
    private lateinit var mBlobColorRgba: Scalar
    private lateinit var mBlobColorHsv: Scalar
    private lateinit var mDetector: ColorBlobDetector
    private lateinit var mSpectrum: Mat
    private lateinit var SPECTRUM_SIZE: Size
    private lateinit var CONTOUR_COLOR: Scalar

    private var mOpenCvCameraView: JavaCameraView? = null
    private var mGLView: GLSurfaceView? = null
    private var mRenderer: CubeRenderer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV initialization failed")
            Toast.makeText(this, "OpenCV initialization failed", Toast.LENGTH_SHORT).show()
        }

        // Set landscape orientation and keep the screen on
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            MaterialTheme {
                Surface {
                    MainScreen { cameraView, glView ->
                        // Store references to camera and OpenGL views
                        mOpenCvCameraView = cameraView
                        mGLView = glView

                        // Initialize OpenCV and OpenGL components
                        setupOpenCVAndOpenGL()
                    }
                }
            }
        }
    }

    private fun setupOpenCVAndOpenGL() {
        // Null checks to ensure views are available
        val openCvCameraView = mOpenCvCameraView ?: return
        val glView = mGLView ?: return

        // Setup the OpenCV Camera View
        openCvCameraView.setCvCameraViewListener(object : CameraBridgeViewBase.CvCameraViewListener2 {
            override fun onCameraViewStarted(width: Int, height: Int) {
                // Initialize matrices and variables for image processing
                mRgba = Mat(height, width, CvType.CV_8UC4)
                mDetector = ColorBlobDetector()
                mSpectrum = Mat()
                mBlobColorRgba = Scalar(255.0)
                mBlobColorHsv = Scalar(255.0)
                SPECTRUM_SIZE = Size(200.0, 64.0)
                CONTOUR_COLOR = Scalar(255.0, 0.0, 0.0, 255.0)
            }

            override fun onCameraViewStopped() {
                // Release resources when camera view stops
                mRgba.release()
            }

            override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
                // Process the frame from the camera
                mRgba = inputFrame.rgba()

                if (mIsColorSelected) {
                    mDetector.process(mRgba)
                    val contours = mDetector.contours
                    Log.i(TAG, "Contours count: ${contours.size}")
                    Imgproc.drawContours(mRgba, contours, -1, CONTOUR_COLOR)

                    // Display detected color and spectrum
                    val colorLabel = mRgba.submat(4, 68, 4, 68)
                    colorLabel.setTo(mBlobColorRgba)

                    val spectrumLabel = mRgba.submat(4, 4 + mSpectrum.rows(), 70, 70 + mSpectrum.cols())
                    mSpectrum.copyTo(spectrumLabel)
                }

                return mRgba
            }
        })

        // OpenGL Setup (only initialize once)
        if (mRenderer == null) {
            mRenderer = CubeRenderer()

            // Configure GLSurfaceView for OpenGL rendering
            glView.setEGLContextClientVersion(2)
            glView.setZOrderOnTop(true)
            glView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            glView.holder.setFormat(PixelFormat.RGBA_8888)

            // Assign renderer to GLSurfaceView
            glView.setRenderer(mRenderer)
            glView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }

        // Enable OpenCV Camera View
        openCvCameraView.setCameraPermissionGranted()
        openCvCameraView.enableView()
        openCvCameraView.setOnTouchListener(::onTouch)
    }

    @Composable
    fun MainScreen(onCameraViewReady: (JavaCameraView, GLSurfaceView) -> Unit) {
        var selectedButton by remember { mutableStateOf(0) }

        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(10f)) {
                var cameraView: JavaCameraView? by remember { mutableStateOf(null) }
                var glView: GLSurfaceView? by remember { mutableStateOf(null) }

                // Display the OpenCV Camera View
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(1f), // Ensure camera view is on top
                    factory = { context ->
                        JavaCameraView(context, CameraBridgeViewBase.CAMERA_ID_ANY).apply {
                            visibility = View.VISIBLE
                            cameraView = this
                        }
                    }
                )

                // Display the OpenGL view
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(0f), // Ensure it's behind camera view
                    factory = { context ->
                        GLSurfaceView(context).apply {
                            setZOrderOnTop(true)
                            glView = this
                        }
                    }
                )

                // Pass views to the parent composable
                LaunchedEffect(cameraView, glView) {
                    cameraView?.let { camera ->
                        glView?.let { gl ->
                            onCameraViewReady(camera, gl)
                        }
                    }
                }
            }

            // Display buttons for user interaction
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                arrayOf("Button 1", "Button 2", "Button 3").forEachIndexed { index, text ->
                    Button(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { selectedButton = index }
                    ) {
                        Text(text)
                    }
                }
            }
        }
    }

    private fun onTouch(view: View, event: MotionEvent): Boolean {
        val cols = mRgba.cols()
        val rows = mRgba.rows()

        // Calculate touch coordinates relative to the image
        val scaleX = cols.toFloat() / view.width
        val scaleY = rows.toFloat() / view.height
        val x = (event.x * scaleX).toInt()
        val y = (event.y * scaleY).toInt()

        Log.i(TAG, "Touch image coordinates: ($x, $y)")

        if (x < 0 || y < 0 || x >= cols || y >= rows) return false

        // Define the region around the touch point
        val touchedRect = Rect().apply {
            this.x = if (x > 4) x - 4 else 0
            this.y = if (y > 4) y - 4 else 0
            this.width = if (x + 4 < cols) x + 4 - this.x else cols - this.x
            this.height = if (y + 4 < rows) y + 4 - this.y else rows - this.y
        }

        // Analyze the touched region
        val touchedRegionRgba = mRgba.submat(touchedRect)
        val touchedRegionHsv = Mat()
        Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv, Imgproc.COLOR_RGB2HSV_FULL)

        // Calculate the average color
        mBlobColorHsv = Core.sumElems(touchedRegionHsv)
        val pointCount = touchedRect.width * touchedRect.height
        mBlobColorHsv.`val` = mBlobColorHsv.`val`.mapIndexed { index, value ->
            value / pointCount
        }.toDoubleArray()

        // Convert HSV to RGBA
        mBlobColorRgba = convertScalarHsv2Rgba(mBlobColorHsv)

        Log.i(TAG, "Touched RGBA color: (${mBlobColorRgba.`val`[0]}, ${mBlobColorRgba.`val`[1]}, ${mBlobColorRgba.`val`[2]}, ${mBlobColorRgba.`val`[3]})")

        // Update detector with the selected color
        mDetector.setHsvColor(mBlobColorHsv)
        mIsColorSelected = true

        touchedRegionRgba.release()
        touchedRegionHsv.release()

        return false
    }

    private fun convertScalarHsv2Rgba(hsvColor: Scalar): Scalar {
        val pointMatRgba = Mat()
        val pointMatHsv = Mat(1, 1, CvType.CV_8UC3, hsvColor)
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4)

        return Scalar(pointMatRgba.get(0, 0))
    }

    override fun onPause() {
        super.onPause()
        mOpenCvCameraView?.disableView()
        mGLView?.onPause()
    }

    override fun onResume() {
        super.onResume()

        mGLView?.onResume()

        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV initialization failed")
            Toast.makeText(this, "OpenCV initialization failed", Toast.LENGTH_SHORT).show()
        } else {
            mOpenCvCameraView?.enableView()
            mOpenCvCameraView?.setOnTouchListener(::onTouch)
        }
    }
}
