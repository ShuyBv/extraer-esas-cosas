package com.vrcardboardkotlin

import android.opengl.GLES20
import java.nio.FloatBuffer
import java.nio.IntBuffer

internal class GLStateBackup {
    private var mCullFaceEnabled = false
    private var mScissorTestEnabled = false
    private var mDepthTestEnabled = false
    private val mViewport: IntBuffer = IntBuffer.allocate(4)
    private val mTexture2dId: IntBuffer = IntBuffer.allocate(1)
    private val mTextureUnit: IntBuffer = IntBuffer.allocate(1)
    private val mScissorBox: IntBuffer = IntBuffer.allocate(4)
    private val mShaderProgram: IntBuffer = IntBuffer.allocate(1)
    private val mArrayBufferBinding: IntBuffer = IntBuffer.allocate(1)
    private val mElementArrayBufferBinding: IntBuffer = IntBuffer.allocate(1)
    private val mClearColor: FloatBuffer = FloatBuffer.allocate(4)
    private val mVertexAttributes: ArrayList<VertexAttributeState>

    init {
        this.mVertexAttributes = ArrayList()
    }

    fun addTrackedVertexAttribute(attributeId: Int) {
        mVertexAttributes.add(VertexAttributeState(attributeId))
    }

    fun clearTrackedVertexAttributes() {
        mVertexAttributes.clear()
    }

    fun readFromGL() {
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, this.mViewport)
        this.mCullFaceEnabled = GLES20.glIsEnabled(GLES20.GL_CULL_FACE)
        this.mScissorTestEnabled = GLES20.glIsEnabled(GLES20.GL_SCISSOR_TEST)
        this.mDepthTestEnabled = GLES20.glIsEnabled(GLES20.GL_DEPTH_TEST)
        GLES20.glGetFloatv(GLES20.GL_COLOR_CLEAR_VALUE, this.mClearColor)
        GLES20.glGetIntegerv(GLES20.GL_CURRENT_PROGRAM, this.mShaderProgram)
        GLES20.glGetIntegerv(GLES20.GL_SCISSOR_BOX, this.mScissorBox)
        GLES20.glGetIntegerv(GLES20.GL_ACTIVE_TEXTURE, this.mTextureUnit)
        GLES20.glGetIntegerv(GLES20.GL_TEXTURE_BINDING_2D, this.mTexture2dId)
        GLES20.glGetIntegerv(GLES20.GL_ARRAY_BUFFER_BINDING, this.mArrayBufferBinding)
        GLES20.glGetIntegerv(
            GLES20.GL_ELEMENT_ARRAY_BUFFER_BINDING,
            this.mElementArrayBufferBinding
        )
        for (vas in this.mVertexAttributes) {
            vas.readFromGL()
        }
    }

    fun writeToGL() {
        for (vas in this.mVertexAttributes) {
            vas.writeToGL()
        }
        GLES20.glBindBuffer(
            GLES20.GL_ARRAY_BUFFER,
            mArrayBufferBinding.array()[0]
        )
        GLES20.glBindBuffer(
            GLES20.GL_ELEMENT_ARRAY_BUFFER,
            mElementArrayBufferBinding.array()[0]
        )
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexture2dId.array()[0])
        GLES20.glActiveTexture(mTextureUnit.array()[0])
        GLES20.glScissor(
            mScissorBox.array()[0],
            mScissorBox.array()[1],
            mScissorBox.array()[2],
            mScissorBox.array()[3]
        )
        GLES20.glUseProgram(mShaderProgram.array()[0])
        GLES20.glClearColor(
            mClearColor.array()[0],
            mClearColor.array()[1],
            mClearColor.array()[2],
            mClearColor.array()[3]
        )
        if (this.mCullFaceEnabled) {
            GLES20.glEnable(GLES20.GL_CULL_FACE)
        } else {
            GLES20.glDisable(GLES20.GL_CULL_FACE)
        }
        if (this.mScissorTestEnabled) {
            GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
        } else {
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        }
        if (this.mDepthTestEnabled) {
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        } else {
            GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        }
        GLES20.glViewport(
            mViewport.array()[0],
            mViewport.array()[1],
            mViewport.array()[2],
            mViewport.array()[3]
        )
    }

    private inner class VertexAttributeState(private val mAttributeId: Int) {
        private val mEnabled: IntBuffer = IntBuffer.allocate(1)

        fun readFromGL() {
            GLES20.glGetVertexAttribiv(
                this.mAttributeId,
                GLES20.GL_VERTEX_ATTRIB_ARRAY_ENABLED,
                this.mEnabled
            )
        }

        fun writeToGL() {
            if (mEnabled.array()[0] == 0) {
                GLES20.glDisableVertexAttribArray(this.mAttributeId)
            } else {
                GLES20.glEnableVertexAttribArray(this.mAttributeId)
            }
        }
    }
}
