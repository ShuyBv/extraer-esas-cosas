package com.vrcardboardkotlin

import android.opengl.Matrix
import kotlin.math.tan

class FieldOfView {
    var left: Float = 0f
    var right: Float = 0f
    var bottom: Float = 0f
    var top: Float = 0f

    constructor() : super() {
        this.left = DEFAULT_MAX_FOV_LEFT_RIGHT
        this.right = DEFAULT_MAX_FOV_LEFT_RIGHT
        this.bottom = DEFAULT_MAX_FOV_BOTTOM
        this.top = DEFAULT_MAX_FOV_TOP
    }

    constructor(left: Float, right: Float, bottom: Float, top: Float) : super() {
        this.left = left
        this.right = right
        this.bottom = bottom
        this.top = top
    }

    constructor(other: FieldOfView?) : super() {
        this.copy(other)
    }

    fun toProtobuf(): FloatArray {
        return floatArrayOf(this.left, this.right, this.bottom, this.top)
    }

    fun copy(other: FieldOfView?) {
        this.left = other!!.left
        this.right = other.right
        this.bottom = other.bottom
        this.top = other.top
    }

    fun toPerspectiveMatrix(near: Float, far: Float, perspective: FloatArray?, offset: Int) {
        require(offset + 16 <= perspective!!.size) { "Not enough space to write the result" }
        val l = (-tan(Math.toRadians(left.toDouble()))).toFloat() * near
        val r = tan(Math.toRadians(right.toDouble()))
            .toFloat() * near
        val b = (-tan(Math.toRadians(bottom.toDouble()))).toFloat() * near
        val t = tan(Math.toRadians(top.toDouble()))
            .toFloat() * near
        Matrix.frustumM(perspective, offset, l, r, b, t, near, far)
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }
        if (other === this) {
            return true
        }
        if (other !is FieldOfView) {
            return false
        }
        val o = other
        return this.left == o.left && (this.right == o.right) && (this.bottom == o.bottom) && (this.top == o.top)
    }

    override fun toString(): String {
        return """
            {
            ${StringBuilder(25).append("  left: ").append(this.left).append(",\n")}${
            StringBuilder(26).append("  right: ").append(
                this.right
            ).append(",\n")
        }${StringBuilder(27).append("  bottom: ").append(this.bottom).append(",\n")}${
            StringBuilder(24).append("  top: ").append(
                this.top
            ).append(",\n")
        }}
            """.trimIndent()
    }

    companion object {
        private const val DEFAULT_MAX_FOV_LEFT_RIGHT = 40.0f
        private const val DEFAULT_MAX_FOV_BOTTOM = 40.0f
        private const val DEFAULT_MAX_FOV_TOP = 40.0f
        fun parseFromProtobuf(angles: FloatArray?): FieldOfView? {
            if (angles!!.size != 4) {
                return null
            }
            return FieldOfView(angles[0], angles[1], angles[2], angles[3])
        }
    }
}
