package com.z_iti_271311_u2_ae_olazaran_mora_jesus_antonio

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.TimeUnit
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CubeRenderer : GLSurfaceView.Renderer {
    private val mMVPMatrix: FloatArray // Model-View-Projection matrix
    private val mProjectionMatrix: FloatArray // Projection matrix
    private val mViewMatrix: FloatArray // View matrix
    private val mRotationMatrix: FloatArray // Rotation matrix
    private val mFinalMVPMatrix: FloatArray // Final combined matrix

    private var mCube: Cube? = null
    var mCubeRotation: Float = 0f // Bad practice

    //private float mCubeRotation; // Recommended practice
    private var mLastUpdateMillis: Long = 0
    private var frameCounter: Int

    init {
        Log.d("Purito", "CubeRenderer Initialization")
        frameCounter = 0
        mMVPMatrix = FloatArray(16)
        mProjectionMatrix = FloatArray(16)
        mViewMatrix = FloatArray(16)
        mRotationMatrix = FloatArray(16)
        mFinalMVPMatrix = FloatArray(16)

        // Set the fixed camera position (View matrix).
        Matrix.setLookAtM(mViewMatrix, 0, 0.0f, 0.0f, -9.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f)
    }

    //public void onSurfaceCreated(EGLConfig config) {
    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        Log.d("Purito", "Creating Surface")
        // Set the background frame color
        //GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
        GLES20.glClearDepthf(1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        mCube = Cube()
    }

    //public void onSurfaceChanged(int width, int height) {
    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        Log.d("Purito", "Changing Surface")
        val ratio = width.toFloat() / height

        GLES20.glViewport(0, 0, width, height)
        // This projection matrix is applied to object coordinates in the onDrawFrame() method.
        Matrix.frustumM(mProjectionMatrix, 0, -ratio, ratio, -1.0f, 1.0f, 3.0f, 12.0f)
        // modelView = projection x view
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0)
    }

    //public void onDrawFrame() {
    override fun onDrawFrame(gl: GL10) {
        Log.d("Purito", "Drawing Frame")
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // Apply the rotation.
        Matrix.setRotateM(mRotationMatrix, 0, mCubeRotation, 1.0f, 1.0f, 1.0f)
        // Combine the rotation matrix with the projection and camera view
        Matrix.multiplyMM(mFinalMVPMatrix, 0, mMVPMatrix, 0, mRotationMatrix, 0)

        // Draw cube.
        mCube!!.draw(mFinalMVPMatrix)
        updateCubeRotation()
    }

    /**
     * Updates the cube rotation.
     */
    private fun updateCubeRotation() {
        if (mLastUpdateMillis != 0L) {
            val factor = (SystemClock.elapsedRealtime() - mLastUpdateMillis) / FRAME_TIME_MILLIS
            mCubeRotation += CUBE_ROTATION_INCREMENT * factor
            frameCounter++
            Log.d("Purito", "Rendering Frame : $frameCounter")
        }
        mLastUpdateMillis = SystemClock.elapsedRealtime()


        //Log.d("Purito","Update Interval: "+mLastUpdateMillis);
    }


    companion object {
        /**
         * Rotation increment per frame.
         */
        const val CUBE_ROTATION_INCREMENT: Float = 0.6f

        //private static final float CUBE_ROTATION_INCREMENT = 0.6f;
        /**
         * The refresh rate, in frames per second.
         */
        private const val REFRESH_RATE_FPS = 60

        /**
         * The duration, in milliseconds, of one frame.
         */
        private val FRAME_TIME_MILLIS = (TimeUnit.SECONDS.toMillis(1) / REFRESH_RATE_FPS).toFloat()
    }
}
