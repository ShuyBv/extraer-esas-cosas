package com.vrcardboardkotlin

import android.hardware.SensorEventListener

interface SensorEventProvider {
    fun start()

    fun stop()

    fun registerListener(p0: SensorEventListener)

    fun unregisterListener(p0: SensorEventListener)
}
