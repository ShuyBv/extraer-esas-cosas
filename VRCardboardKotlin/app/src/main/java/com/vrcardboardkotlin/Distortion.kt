package com.vrcardboardkotlin

import kotlin.math.abs

class Distortion {
    private lateinit var mCoefficients: FloatArray

    constructor() : super() {
        this.mCoefficients = DEFAULT_COEFFICIENTS.clone()
    }

    constructor(other: Distortion?) : super() {
        this.setCoefficients(other!!.mCoefficients)
    }

    fun toProtobuf(): FloatArray {
        return mCoefficients.clone()
    }

    fun setCoefficients(coefficients: FloatArray?) {
        this.mCoefficients = (if ((coefficients != null)) coefficients.clone() else FloatArray(0))
    }

    fun distortionFactor(radius: Float): Float {
        var result = 1.0f
        var rFactor = 1.0f
        val rSquared = radius * radius
        for (ki in this.mCoefficients) {
            rFactor *= rSquared
            result += ki * rFactor
        }
        return result
    }

    fun distort(radius: Float): Float {
        return radius * this.distortionFactor(radius)
    }

    fun distortInverse(radius: Float): Float {
        var r0 = radius / 0.9f
        var r = radius * 0.9f
        var dr0 = radius - this.distort(r0)
        while (abs((r - r0).toDouble()) > 1.0E-4) {
            val dr = radius - this.distort(r)
            val r2 = r - dr * ((r - r0) / (dr - dr0))
            r0 = r
            r = r2
            dr0 = dr
        }
        return r
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }
        if (other === this) {
            return true
        }
        if (other !is Distortion) {
            return false
        }
        return mCoefficients.contentEquals(other.mCoefficients)
    }

    override fun toString(): String {
        val builder = StringBuilder().append("{\n").append("  coefficients: [")
        for (i in mCoefficients.indices) {
            builder.append((mCoefficients[i]))
            if (i < mCoefficients.size - 1) {
                builder.append(", ")
            }
        }
        builder.append("],\n}")
        return builder.toString()
    }

    companion object {
        private val DEFAULT_COEFFICIENTS = floatArrayOf(0.441f, 0.156f)
        fun parseFromProtobuf(coefficients: FloatArray?): Distortion {
            val distortion = Distortion()
            distortion.setCoefficients(coefficients)
            return distortion
        }
    }
}
