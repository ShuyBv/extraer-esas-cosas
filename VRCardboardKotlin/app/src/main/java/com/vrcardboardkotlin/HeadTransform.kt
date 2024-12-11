package com.vrcardboardkotlin

import android.opengl.Matrix

class HeadTransform {
    val headView: FloatArray

    init {
        Matrix.setIdentityM(FloatArray(16).also { this.headView = it }, 0)
    }
}
