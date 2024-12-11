package com.vrcardboardkotlin

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.Matrix
import android.view.Display
import android.view.WindowManager
import java.util.concurrent.TimeUnit
import kotlin.concurrent.Volatile

//import com.google.vrtoolkit.cardboard.sensors.internal.*;

class HeadTracker(sensorEventProvider: SensorEventProvider, clock: Clock, display: Display) :
    SensorEventListener {
    private val mDisplay: Display
    private val mEkfToHeadTracker = FloatArray(16)
    private val mSensorToDisplay = FloatArray(16)
    private var mDisplayRotation: Float
    private val mNeckModelTranslation: FloatArray
    private val mTmpHeadView: FloatArray
    private val mTmpHeadView2: FloatArray
    private val mNeckModelEnabled: Boolean

    @Volatile
    private var mTracking = false
    private val mTracker: OrientationEKF
    private val mSensorEventProvider: SensorEventProvider
    private val mClock: Clock
    private var mLatestGyroEventClockTimeNs: Long = 0
    private val mGyroBias: Vector3d
    private val mLatestGyro: Vector3d
    private val mLatestAcc: Vector3d

    init {
        this.mDisplayRotation = Float.NaN
        this.mNeckModelTranslation = FloatArray(16)
        this.mTmpHeadView = FloatArray(16)
        this.mTmpHeadView2 = FloatArray(16)
        this.mNeckModelEnabled = DEFAULT_NECK_MODEL_ENABLED
        this.mGyroBias = Vector3d()
        this.mLatestGyro = Vector3d()
        this.mLatestAcc = Vector3d()
        this.mClock = clock
        this.mSensorEventProvider = sensorEventProvider
        this.mTracker = OrientationEKF()
        this.mDisplay = display
        Matrix.setIdentityM(this.mNeckModelTranslation, 0)
        Matrix.translateM(
            this.mNeckModelTranslation, 0,
            0.0f, -DEFAULT_NECK_VERTICAL_OFFSET, DEFAULT_NECK_HORIZONTAL_OFFSET
        )
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == 1) {
            mLatestAcc[event.values[0].toDouble(), event.values[1].toDouble()] =
                event.values[2].toDouble()
            mTracker.processAcc(this.mLatestAcc, event.timestamp)
        } else if (event.sensor.type == 4) {
            this.mLatestGyroEventClockTimeNs = mClock.nanoTime()
            mLatestGyro[event.values[0].toDouble(), event.values[1].toDouble()] =
                event.values[2].toDouble()
            Vector3d.Companion.sub(this.mLatestGyro, this.mGyroBias, this.mLatestGyro)
            mTracker.processGyro(this.mLatestGyro, event.timestamp)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
    }

    fun startTracking() {
        if (this.mTracking) {
            return
        }
        mTracker.reset()
        mSensorEventProvider.registerListener(this as SensorEventListener)
        mSensorEventProvider.start()
        this.mTracking = true
    }

    fun stopTracking() {
        if (!this.mTracking) {
            return
        }
        mSensorEventProvider.unregisterListener(this as SensorEventListener)
        mSensorEventProvider.stop()
        this.mTracking = false
    }

    fun setGyroBias(gyroBias: FloatArray?) {
        if (gyroBias == null) {
            mGyroBias.setZero()
            return
        }
        require(gyroBias.size == 3) { "Gyro bias should be an array of 3 values" }
        mGyroBias[gyroBias[0].toDouble(), gyroBias[1].toDouble()] = gyroBias[2].toDouble()
    }

    fun getLastHeadView(headView: FloatArray?, offset: Int) {
        require(offset + 16 <= headView!!.size) { "Not enough space to write the result" }
        var rotation = 0.0f
        when (mDisplay.rotation) {
            0 -> {
                rotation = 0.0f
            }

            1 -> {
                rotation = 90.0f
            }

            2 -> {
                rotation = 180.0f
            }

            3 -> {
                rotation = 270.0f
            }
        }
        if (rotation != this.mDisplayRotation) {
            this.mDisplayRotation = rotation
            Matrix.setRotateEulerM(this.mSensorToDisplay, 0, 0.0f, 0.0f, -rotation)
            Matrix.setRotateEulerM(this.mEkfToHeadTracker, 0, -90.0f, 0.0f, rotation)
        }
        synchronized(this.mTracker) {
            val secondsSinceLastGyroEvent = TimeUnit.NANOSECONDS.toSeconds(
                mClock.nanoTime() - this.mLatestGyroEventClockTimeNs
            ).toDouble()
            val secondsToPredictForward = secondsSinceLastGyroEvent + 1.0 / 30
            val mat = mTracker.getPredictedGLMatrix(secondsToPredictForward)
            for (i in headView.indices) {
                mTmpHeadView[i] = mat!![i].toFloat()
            }
        }
        Matrix.multiplyMM(this.mTmpHeadView2, 0, this.mSensorToDisplay, 0, this.mTmpHeadView, 0)
        Matrix.multiplyMM(headView, offset, this.mTmpHeadView2, 0, this.mEkfToHeadTracker, 0)
        if (this.mNeckModelEnabled) {
            Matrix.multiplyMM(this.mTmpHeadView, 0, this.mNeckModelTranslation, 0, headView, offset)
            Matrix.translateM(
                headView,
                offset,
                this.mTmpHeadView,
                0,
                0.0f,
                DEFAULT_NECK_VERTICAL_OFFSET,
                0.0f
            )
        }
    }

    companion object {
        private const val DEFAULT_NECK_HORIZONTAL_OFFSET = 0.08f
        private const val DEFAULT_NECK_VERTICAL_OFFSET = 0.075f
        private const val DEFAULT_NECK_MODEL_ENABLED = false
        fun createFromContext(context: Context): HeadTracker {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val display =
                (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
            return HeadTracker(DeviceSensorLooper(sensorManager), SystemClock(), display)
        }
    }
}
