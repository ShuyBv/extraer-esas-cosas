package com.vrcardboardkotlin

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper

class DeviceSensorLooper(private val mSensorManager: SensorManager) : SensorEventProvider {
    private var mIsRunning = false
    private var mSensorLooper: Looper? = null
    private var mSensorEventListener: SensorEventListener? = null
    private val mRegisteredListeners = ArrayList<SensorEventListener>()
    override fun start() {
        if (this.mIsRunning) {
            return
        }
        this.mSensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                for (listener in this@DeviceSensorLooper.mRegisteredListeners) {
                    synchronized(listener) {
                        listener.onSensorChanged(event)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                for (listener in this@DeviceSensorLooper.mRegisteredListeners) {
                    synchronized(listener) {
                        listener.onAccuracyChanged(sensor, accuracy)
                    }
                }
            }
        }

        val sensorThread: HandlerThread = object : HandlerThread("sensor") {
            override fun onLooperPrepared() {
                val handler = Handler(Looper.myLooper()!!)
                for (sensorType in INPUT_SENSORS) {
                    val sensor = mSensorManager.getDefaultSensor(sensorType)
                    //DeviceSensorLooper.this.mSensorManager.registerListener(DeviceSensorLooper.this.mSensorEventListener, sensor, 0, handler);
                    mSensorManager.registerListener(
                        this@DeviceSensorLooper.mSensorEventListener,
                        sensor,
                        0,
                        handler
                    )
                }
            }
        }
        sensorThread.start()
        this.mSensorLooper = sensorThread.looper
        this.mIsRunning = true
    }

    override fun stop() {
        if (!this.mIsRunning) {
            return
        }
        mSensorManager.unregisterListener(this.mSensorEventListener)
        this.mSensorEventListener = null
        mSensorLooper!!.quit()
        this.mSensorLooper = null
        this.mIsRunning = false
    }

    override fun registerListener(listener: SensorEventListener) {
        synchronized(this.mRegisteredListeners) {
            mRegisteredListeners.add(listener)
        }
    }

    override fun unregisterListener(listener: SensorEventListener) {
        synchronized(this.mRegisteredListeners) {
            mRegisteredListeners.remove(listener)
        }
    }

    companion object {
        private val INPUT_SENSORS = intArrayOf(1, 4)
    }
}
