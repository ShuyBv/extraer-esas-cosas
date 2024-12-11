package com.vrcardboardkotlin

import kotlin.concurrent.Volatile

class Eye(type: Int) {
    val eyeView: FloatArray = FloatArray(16)
    val viewport: Viewport = Viewport()
    val fov: FieldOfView = FieldOfView()

    @Volatile
    private var mProjectionChanged: Boolean = true
    private var mPerspective: FloatArray = FloatArray(16)
    private var mLastZNear = 0f
    private var mLastZFar = 0f

    fun getPerspective(zNear: Float, zFar: Float): FloatArray {
        if (!mProjectionChanged && mLastZNear == zNear && mLastZFar == zFar) {
            return mPerspective
        }
        fov.toPerspectiveMatrix(zNear, zFar, mPerspective, 0)
        mLastZNear = zNear
        mLastZFar = zFar
        mProjectionChanged = false
        return mPerspective
    }

    fun setProjectionChanged() {
        mProjectionChanged = true
    }
}

