package com.vrcardboardkotlin

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import android.view.MotionEvent
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.concurrent.Volatile
import kotlin.math.cos
import kotlin.math.sin

internal class UiLayer(private val mContext: Context) {
    private val mTouchWidthPx: Int

    @Volatile
    private var mTouchRect: Rect
    private var mDownWithinBounds: Boolean = false
    private val mGlStateBackup: GLStateBackup
    private val mShader: ShaderProgram
    private val mSettingsButtonRenderer: SettingsButtonRenderer?
    private val mAlignmentMarkerRenderer: AlignmentMarkerRenderer
    private val mViewport: Viewport
    private var mShouldUpdateViewport: Boolean = true

    @get:Synchronized
    val settingsButtonEnabled: Boolean = true

    @get:Synchronized
    val alignmentMarkerEnabled: Boolean = true
    private var initialized: Boolean = false

    fun updateViewport(viewport: Viewport?) {
        synchronized(this, {
            if ((this.mViewport == viewport)) {
                return
            }
            val w: Int = viewport!!.width
            val h: Int = viewport.height
            this.mTouchRect = Rect(
                (w - this.mTouchWidthPx) / 2,
                h - this.mTouchWidthPx,
                (w + this.mTouchWidthPx) / 2,
                h
            )
            mViewport.setViewport(viewport.x, viewport.y, viewport.width, viewport.height)
            this.mShouldUpdateViewport = true
        })
    }

    fun initializeGl() {
        mShader.initializeGl()
        mGlStateBackup.clearTrackedVertexAttributes()
        mGlStateBackup.addTrackedVertexAttribute(mShader.aPosition)
        mGlStateBackup.readFromGL()
        mSettingsButtonRenderer!!.initializeGl()
        mAlignmentMarkerRenderer.initializeGl()
        mGlStateBackup.writeToGL()
        this.initialized = true
    }

    fun draw() {
        if (!this.settingsButtonEnabled && !this.alignmentMarkerEnabled) {
            return
        }
        if (!this.initialized) {
            this.initializeGl()
        }
        mGlStateBackup.readFromGL()
        synchronized(this, {
            if (this.mShouldUpdateViewport) {
                this.mShouldUpdateViewport = false
                mSettingsButtonRenderer!!.updateViewport(this.mViewport)
                mAlignmentMarkerRenderer.updateViewport(this.mViewport)
            }
            mViewport.setGLViewport()
        })
        if (this.settingsButtonEnabled) {
            mSettingsButtonRenderer!!.draw()
        }
        if (this.alignmentMarkerEnabled) {
            mAlignmentMarkerRenderer.draw()
        }
        mGlStateBackup.writeToGL()
    }

    fun onTouchEvent(e: MotionEvent): Boolean {
        var touchWithinBounds: Boolean = false
        synchronized(this, {
            if (!this.settingsButtonEnabled) {
                return false
            }
            touchWithinBounds = mTouchRect.contains(e.getX().toInt(), e.getY().toInt())
        })
        if (e.getActionMasked() == 0 && touchWithinBounds) {
            this.mDownWithinBounds = true
        }
        if (!this.mDownWithinBounds) {
            return false
        }
        if (e.getActionMasked() == 1) {
            if (touchWithinBounds) {
                UiUtils.launchOrInstallCardboard(this.mContext)
            }
            this.mDownWithinBounds = false
        } else if (e.getActionMasked() == 3) {
            this.mDownWithinBounds = false
        }
        this.setPressed(this.mDownWithinBounds && touchWithinBounds)
        return true
    }

    private fun setPressed(pressed: Boolean) {
        if (this.mSettingsButtonRenderer != null) {
            mSettingsButtonRenderer.setColor(if (pressed) -12303292 else -3355444)
        }
    }

    init {
        this.mTouchRect = Rect()
        val density: Float = mContext.getResources().getDisplayMetrics().density
        val buttonWidthPx: Int = (28.0f * density).toInt()
        this.mTouchWidthPx = (buttonWidthPx * 1.5f).toInt()
        this.mGlStateBackup = GLStateBackup()
        this.mShader = ShaderProgram()
        this.mSettingsButtonRenderer = SettingsButtonRenderer(this.mShader, buttonWidthPx)
        this.mAlignmentMarkerRenderer = AlignmentMarkerRenderer(
            this.mShader,
            mTouchWidthPx.toFloat(), 4.0f * density
        )
        this.mViewport = Viewport()
    }

    private class ShaderProgram
        () {
        var program: Int = 0
        var aPosition: Int = 0
        var uMvpMatrix: Int = 0
        var uColor: Int = 0

        fun initializeGl() {
            this.program = this.createProgram(
                "uniform mat4 uMVPMatrix;\nattribute vec2 aPosition;\nvoid main() {\n    gl_Position = uMVPMatrix * vec4(aPosition, 0.0, 1.0);\n}\n",
                "precision mediump float;\nuniform vec4 uColor;\nvoid main() {\n    gl_FragColor = uColor;\n}\n"
            )
            if (this.program == 0) {
                throw RuntimeException("Could not create program")
            }
            this.aPosition = GLES20.glGetAttribLocation(this.program, "aPosition")
            checkGlError("glGetAttribLocation aPosition")
            if (this.aPosition == -1) {
                throw RuntimeException("Could not get attrib location for aPosition")
            }
            this.uMvpMatrix = GLES20.glGetUniformLocation(this.program, "uMVPMatrix")
            if (this.uMvpMatrix == -1) {
                throw RuntimeException("Could not get uniform location for uMVPMatrix")
            }
            this.uColor = GLES20.glGetUniformLocation(this.program, "uColor")
            if (this.uColor == -1) {
                throw RuntimeException("Could not get uniform location for uColor")
            }
        }

        private fun loadShader(shaderType: Int, source: String): Int {
            var shader: Int = GLES20.glCreateShader(shaderType)
            if (shader != 0) {
                GLES20.glShaderSource(shader, source)
                GLES20.glCompileShader(shader)
                val compiled: IntArray = intArrayOf(0)
                GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
                if (compiled.get(0) == 0) {
                    Log.e(
                        TAG,
                        StringBuilder(37).append("Could not compile shader ").append(shaderType)
                            .append(":").toString()
                    )
                    Log.e(TAG, GLES20.glGetShaderInfoLog(shader))
                    GLES20.glDeleteShader(shader)
                    shader = 0
                }
            }
            return shader
        }

        private fun createProgram(vertexSource: String, fragmentSource: String): Int {
            val vertexShader: Int = this.loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
            if (vertexShader == 0) {
                return 0
            }
            val pixelShader: Int = this.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
            if (pixelShader == 0) {
                return 0
            }
            var program: Int = GLES20.glCreateProgram()
            if (program != 0) {
                GLES20.glAttachShader(program, vertexShader)
                checkGlError("glAttachShader")
                GLES20.glAttachShader(program, pixelShader)
                checkGlError("glAttachShader")
                GLES20.glLinkProgram(program)
                val linkStatus: IntArray = intArrayOf(0)
                GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
                if (linkStatus.get(0) != 1) {
                    Log.e(TAG, "Could not link program: ")
                    Log.e(TAG, GLES20.glGetProgramInfoLog(program))
                    GLES20.glDeleteProgram(program)
                    program = 0
                }
                checkGlError("glLinkProgram")
            }
            return program
        }
    }

    private open class MeshRenderer(shader: ShaderProgram) {
        protected var mArrayBufferId: Int
        protected var mElementBufferId: Int
        protected var mShader: ShaderProgram
        protected var mMvp: FloatArray
        private var mNumIndices: Int = 0

        init {
            this.mArrayBufferId = -1
            this.mElementBufferId = -1
            this.mMvp = FloatArray(16)
            this.mShader = shader
        }

        fun genAndBindBuffers(vertexData: FloatArray, indexData: ShortArray) {
            val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(vertexData.size * 4).order(
                ByteOrder.nativeOrder()
            ).asFloatBuffer()
            vertexBuffer.put(vertexData).position(0)
            this.mNumIndices = indexData.size
            val indexBuffer: ShortBuffer = ByteBuffer.allocateDirect(this.mNumIndices * 4).order(
                ByteOrder.nativeOrder()
            ).asShortBuffer()
            indexBuffer.put(indexData).position(0)
            val bufferIds: IntArray = IntArray(2)
            GLES20.glGenBuffers(2, bufferIds, 0)
            this.mArrayBufferId = bufferIds.get(0)
            this.mElementBufferId = bufferIds.get(1)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, this.mArrayBufferId)
            GLES20.glBufferData(
                GLES20.GL_ARRAY_BUFFER,
                vertexData.size * 4,
                vertexBuffer as Buffer?,
                GLES20.GL_STATIC_DRAW
            )
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, this.mElementBufferId)
            GLES20.glBufferData(
                GLES20.GL_ELEMENT_ARRAY_BUFFER,
                indexData.size * 4,
                indexBuffer as Buffer?,
                GLES20.GL_STATIC_DRAW
            )
            checkGlError("genAndBindBuffers")
        }

        open fun updateViewport(viewport: Viewport) {
            Matrix.setIdentityM(this.mMvp, 0)
        }

        open fun draw() {
            GLES20.glDisable(GLES20.GL_DEPTH_TEST)
            GLES20.glDisable(GLES20.GL_CULL_FACE)
            GLES20.glUseProgram(mShader.program)
            GLES20.glUniformMatrix4fv(mShader.uMvpMatrix, 1, false, this.mMvp, 0)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, this.mArrayBufferId)
            GLES20.glVertexAttribPointer(mShader.aPosition, 2, GLES20.GL_FLOAT, false, 8, 0)
            GLES20.glEnableVertexAttribArray(mShader.aPosition)
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, this.mElementBufferId)
            GLES20.glDrawElements(5, this.mNumIndices, GLES20.GL_UNSIGNED_SHORT, 0)
        }
    }

    private class AlignmentMarkerRenderer(
        shader: ShaderProgram,
        private val mVerticalBorderPaddingPx: Float,
        private val mLineThicknessPx: Float
    ) : MeshRenderer(shader) {
        fun initializeGl() {
            val vertexData: FloatArray =
                floatArrayOf(1.0f, 1.0f, -1.0f, 1.0f, 1.0f, -1.0f, -1.0f, -1.0f)
            val indexData: ShortArray = ShortArray(vertexData.size / 2)
            for (i in indexData.indices) {
                indexData[i] = i.toShort()
            }
            this.genAndBindBuffers(vertexData, indexData)
        }

        override fun updateViewport(viewport: Viewport) {
            Matrix.setIdentityM(this.mMvp, 0)
            val xScale: Float = this.mLineThicknessPx / viewport.width
            val yScale: Float = 1.0f - 2.0f * this.mVerticalBorderPaddingPx / viewport.height
            Matrix.scaleM(this.mMvp, 0, xScale, yScale, 1.0f)
        }

        override fun draw() {
            GLES20.glUseProgram(mShader.program)
            GLES20.glUniform4f(
                mShader.uColor, Color.red(COLOR) / 255.0f, Color.green(COLOR) / 255.0f, Color.blue(
                    COLOR
                ) / 255.0f, Color.alpha(COLOR) / 255.0f
            )
            super.draw()
        }

        companion object {
            private val COLOR: Int

            init {
                COLOR = Color.argb(255, 50, 50, 50)
            }
        }
    }

    private class SettingsButtonRenderer(shader: ShaderProgram, private val mButtonWidthPx: Int) :
        MeshRenderer(shader) {
        private var mColor: Int

        init {
            this.mColor = -3355444
        }

        fun initializeGl() {
            val vertexData: FloatArray = FloatArray(120)
            val numVerticesPerRim: Int = 30
            val lerpInterval: Float = 8.0f
            for (i in 0 until numVerticesPerRim) {
                val theta: Float = i / numVerticesPerRim * 360.0f
                val mod: Float = theta % 60.0f
                var r: Float
                if (mod <= 12.0f) {
                    r = 1.0f
                } else if (mod <= 20.0f) {
                    r = lerp(1.0f, 0.75f, (mod - 12.0f) / lerpInterval)
                } else if (mod <= 40.0f) {
                    r = 0.75f
                } else if (mod <= 48.0f) {
                    r = lerp(0.75f, 1.0f, (mod - 60.0f + 20.0f) / lerpInterval)
                } else {
                    r = 1.0f
                }
                vertexData[2 * i] = r * cos(Math.toRadians((90.0f - theta).toDouble())).toFloat()
                vertexData[2 * i + 1] = r * sin(Math.toRadians((90.0f - theta).toDouble())).toFloat()
            }
            val innerStartingIndex: Int = 2 * numVerticesPerRim
            for (j in 0 until numVerticesPerRim) {
                val theta2: Float = j / numVerticesPerRim * 360.0f
                vertexData[innerStartingIndex + 2 * j] =
                    0.3125f * cos(Math.toRadians((90.0f - theta2).toDouble())).toFloat()
                vertexData[innerStartingIndex + (2 * j) + 1] =
                    0.3125f * sin(Math.toRadians((90.0f - theta2).toDouble())).toFloat()
            }

            val indexData: ShortArray = ShortArray(62)
            for (k in 0 until numVerticesPerRim) {
                indexData[2 * k] = k.toShort()
                indexData[2 * k + 1] = (numVerticesPerRim + k).toShort()
            }
            indexData[indexData.size - 2] = 0
            indexData[indexData.size - 1] = numVerticesPerRim.toShort()
            this.genAndBindBuffers(vertexData, indexData)

        }

        @Synchronized
        fun setColor(color: Int) {
            this.mColor = color
        }

        override fun updateViewport(viewport: Viewport) {
            Matrix.setIdentityM(this.mMvp, 0)
            val yScale: Float = (this.mButtonWidthPx / viewport.height).toFloat()
            val xScale: Float = yScale * viewport.height / viewport.width
            Matrix.translateM(this.mMvp, 0, 0.0f, yScale - 1.0f, 0.0f)
            Matrix.scaleM(this.mMvp, 0, xScale, yScale, 1.0f)
        }

        override fun draw() {
            GLES20.glUseProgram(mShader.program)
            synchronized(this, {
                GLES20.glUniform4f(
                    mShader.uColor, Color.red(
                        this.mColor
                    ) / 255.0f, Color.green(this.mColor) / 255.0f, Color.blue(
                        this.mColor
                    ) / 255.0f, Color.alpha(this.mColor) / 255.0f
                )
            })
            super.draw()
        }
    }

    companion object {
        private val TAG: String
        private fun checkGlError(op: String) {
            val error: Int
            if ((GLES20.glGetError().also({ error = it })) != 0) {
                val tag: String = TAG
                val value: String = op.toString().toString()
                Log.e(
                    tag,
                    StringBuilder(21 + value.length).append(value).append(": glError ")
                        .append(error).toString()
                )
                val value2: String = op.toString().toString()
                throw RuntimeException(
                    StringBuilder(21 + value2.length).append(value2).append(": glError ")
                        .append(error).toString()
                )
            }
        }

        private fun lerp(a: Float, b: Float, t: Float): Float {
            return a * (1.0f - t) + b * t
        }

        init {
            TAG = UiLayer::class.java.getSimpleName()
        }
    }
}
