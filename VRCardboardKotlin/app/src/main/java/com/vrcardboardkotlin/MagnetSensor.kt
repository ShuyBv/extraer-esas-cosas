package com.vrcardboardkotlin

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference
import java.util.Arrays
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class MagnetSensor(context: Context) {
    private var mDetector: TriggerDetector? = null
    private var mDetectorThread: Thread? = null

    init {
        mDetector = if (HTC_ONE_MODEL == Build.MODEL) {
            VectorTriggerDetector(context)
        } else {
            ThresholdTriggerDetector(context)
        }
    }

    fun start() {
        mDetectorThread = Thread(mDetector)
        mDetectorThread!!.start()
    }

    fun stop() {
        if (mDetectorThread != null) {
            mDetectorThread!!.interrupt()
            mDetector!!.stop()
        }
    }

    fun setOnCardboardTriggerListener(listener: OnCardboardTriggerListener?) {
        mDetector!!.setOnCardboardTriggerListener(listener, Handler())
    }

    private abstract class TriggerDetector(context: Context) : Runnable, SensorEventListener {
        protected var mSensorManager: SensorManager =
            context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        protected var mMagnetometer: Sensor?
        protected var mListenerRef: WeakReference<OnCardboardTriggerListener?>? = null
        protected var mHandler: Handler? = null

        init {
            mMagnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        }

        @Synchronized
        fun setOnCardboardTriggerListener(
            listener: OnCardboardTriggerListener?, handler: Handler?
        ) {
            mListenerRef = WeakReference(listener)
            mHandler = handler
        }

        protected fun handleButtonPressed() {
            synchronized(this) {
                val listener = if (mListenerRef != null) mListenerRef!!.get() else null
                if (listener != null) {
                    mHandler!!.post(Runnable { listener.onCardboardTrigger() })
                }
            }
        }

        override fun run() {
            Looper.prepare()
            mSensorManager.registerListener(this, this.mMagnetometer, 0)
            Looper.loop()
        }

        fun stop() {
            mSensorManager.unregisterListener(this)
        }

        override fun onSensorChanged(event: SensorEvent) {
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        }

        companion object {
            protected const val TAG: String = "TriggerDetector"
        }
    }

    private class ThresholdTriggerDetector(context: Context) : TriggerDetector(context) {
        private var mLastFiring = 0L
        private val mSensorData = ArrayList<FloatArray>()
        private val mSensorTimes: ArrayList<Long>

        private fun addData(values: FloatArray, time: Long) {
            mSensorData.add(values)
            mSensorTimes.add(time)
            while (mSensorTimes[0] < time - NS_WINDOW_SIZE) {
                mSensorData.removeAt(0)
                mSensorTimes.removeAt(0)
            }
            evaluateModel(time)
        }

        private fun evaluateModel(time: Long) {
            if (time - mLastFiring < NS_WAIT_TIME || mSensorData.size < 2) {
                return
            }
            val baseline = mSensorData[mSensorData.size - 1]
            var startSecondSegment = 0
            for (i in mSensorTimes.indices) {
                if (time - mSensorTimes[i] < NS_SEGMENT_SIZE) {
                    startSecondSegment = i
                    break
                }
            }
            val offsets = FloatArray(mSensorData.size)
            computeOffsets(offsets, baseline)
            val min1 = computeMinimum(Arrays.copyOfRange(offsets, 0, startSecondSegment))
            val max2 = computeMaximum(
                Arrays.copyOfRange(
                    offsets, startSecondSegment, mSensorData.size
                )
            )
            if (min1 < mT1 && max2 > mT2) {
                mLastFiring = time
                handleButtonPressed()
            }
        }

        private fun computeOffsets(offsets: FloatArray, baseline: FloatArray) {
            for (i in mSensorData.indices) {
                val point = mSensorData[i]
                val o = floatArrayOf(
                    point[0] - baseline[0],
                    point[1] - baseline[1],
                    point[2] - baseline[2]
                )
                val magnitude = sqrt((o[0] * o[0] + o[1] * o[1] + o[2] * o[2]).toDouble())
                    .toFloat()
                offsets[i] = magnitude
            }
        }

        private fun computeMaximum(offsets: FloatArray): Float {
            var max = Float.NEGATIVE_INFINITY
            for (o in offsets) {
                max = max(o.toDouble(), max.toDouble()).toFloat()
            }
            return max
        }

        private fun computeMinimum(offsets: FloatArray): Float {
            var min = Float.POSITIVE_INFINITY
            for (o in offsets) {
                min = min(o.toDouble(), min.toDouble()).toFloat()
            }
            return min
        }

        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor == this.mMagnetometer) {
                val values = event.values
                if ((values[0] == 0.0f) && values[1] == 0.0f && values[2] == 0.0f) {
                    return
                }
                addData(event.values.clone(), event.timestamp)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        }

        init {
            mSensorTimes = ArrayList()
        }

        companion object {
            private const val TAG = "ThresholdTriggerDetector"
            private const val NS_SEGMENT_SIZE = 200000000L
            private const val NS_WINDOW_SIZE = 400000000L
            private const val NS_WAIT_TIME = 350000000L
            private var mT1 = 0
            private var mT2 = 0

            init {
                mT1 = 30
                mT2 = 130
            }
        }
    }

    private class VectorTriggerDetector(context: Context) : TriggerDetector(context) {
        private var mLastFiring = 0L
        private val mSensorData = ArrayList<FloatArray>()
        private val mSensorTimes: ArrayList<Long>

        init {
            mSensorTimes = ArrayList()
            mXThreshold = -3
            mYThreshold = 15
            mZThreshold = 6
        }

        private fun addData(values: FloatArray, time: Long) {
            mSensorData.add(values)
            mSensorTimes.add(time)
            while (mSensorTimes[0] < time - NS_THROWAWAY_SIZE) {
                mSensorData.removeAt(0)
                mSensorTimes.removeAt(0)
            }
            evaluateModel(time)
        }

        private fun evaluateModel(time: Long) {
            if (time - mLastFiring < NS_REFRESH_TIME || mSensorData.size < 2) {
                return
            }
            var baseIndex = 0
            for (i in 1 until mSensorTimes.size) {
                if (time - mSensorTimes[i] < NS_WAIT_SIZE) {
                    baseIndex = i
                    break
                }
            }
            val oldValues = mSensorData[baseIndex]
            val currentValues = mSensorData[mSensorData.size - 1]
            if ((currentValues[0] - oldValues[0] < mXThreshold) && currentValues[1] - oldValues[1] > mYThreshold && currentValues[2] - oldValues[2] > mZThreshold) {
                mLastFiring = time
                handleButtonPressed()
            }
        }

        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor == mMagnetometer) {
                val values = event.values
                if (((values[0] == 0.0f) && values[1] == 0.0f) && values[2] == 0.0f) {
                    return
                }
                addData(event.values.clone(), event.timestamp)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        }

        companion object {
            private const val NS_REFRESH_TIME = 350000000L
            private const val NS_THROWAWAY_SIZE = 500000000L
            private const val NS_WAIT_SIZE = 100000000L
            private var mXThreshold = 0
            private var mYThreshold = 0
            private var mZThreshold = 0
        }
    }

    interface OnCardboardTriggerListener {
        fun onCardboardTrigger()
    }

    companion object {
        private const val HTC_ONE_MODEL = "HTC One"
    }
}
