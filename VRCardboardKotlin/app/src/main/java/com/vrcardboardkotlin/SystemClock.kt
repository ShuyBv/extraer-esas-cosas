package com.vrcardboardkotlin

class SystemClock() : Clock {
    override fun nanoTime(): Long {
        return System.nanoTime()
    }
}
