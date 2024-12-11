package com.vrcardboardkotlin

import android.opengl.GLES20
import android.util.Log
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tan

class DistortionRenderer {
    private var mTextureId: Int
    private var mRenderbufferId: Int
    private var mFramebufferId: Int
    private val mOriginalFramebufferId: IntBuffer
    private val mTextureFormat: Int
    private val mTextureType: Int
    private var mResolutionScale = 1.0f
    private var mRestoreGLStateEnabled = false
    private var mChromaticAberrationCorrectionEnabled = false
    private var mVignetteEnabled = false
    private var mLeftEyeDistortionMesh: DistortionMesh? = null
    private var mRightEyeDistortionMesh: DistortionMesh? = null
    private val mGLStateBackup: GLStateBackup
    private val mGLStateBackupAberration: GLStateBackup
    private var mHmd: HeadMountedDisplay? = null
    private var mLeftEyeViewport: EyeViewport? = null
    private var mRightEyeViewport: EyeViewport? = null
    private var mFovsChanged = false
    private var mViewportsChanged = false
    private var mTextureFormatChanged = false
    private var mDrawingFrame = false
    private var mXPxPerTanAngle = 0f
    private var mYPxPerTanAngle = 0f
    private var mMetersPerTanAngle = 0f
    private var mProgramHolder: ProgramHolder? = null
    private var mProgramHolderAberration: ProgramHolderAberration? = null

    init {
        this.mTextureId = -1
        this.mRenderbufferId = -1
        this.mFramebufferId = -1
        this.mOriginalFramebufferId = IntBuffer.allocate(1)
        this.mTextureFormat = GLES20.GL_RGB
        this.mTextureType = GLES20.GL_UNSIGNED_BYTE
        this.mGLStateBackup = GLStateBackup()
        this.mGLStateBackupAberration = GLStateBackup()
    }

    fun beforeDrawFrame() {
        this.mDrawingFrame = true
        if (this.mFovsChanged || this.mTextureFormatChanged) {
            this.updateTextureAndDistortionMesh()
        }
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, this.mOriginalFramebufferId)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, this.mFramebufferId)
    }

    fun afterDrawFrame() {
        GLES20.glBindFramebuffer(
            GLES20.GL_FRAMEBUFFER,
            mOriginalFramebufferId.array()[0]
        )
        this.undistortTexture(this.mTextureId)
        this.mDrawingFrame = false
    }

    fun undistortTexture(textureId: Int) {
        if (this.mRestoreGLStateEnabled) {
            if (this.mChromaticAberrationCorrectionEnabled) {
                mGLStateBackupAberration.readFromGL()
            } else {
                mGLStateBackup.readFromGL()
            }
        }
        if (this.mFovsChanged || this.mTextureFormatChanged) {
            this.updateTextureAndDistortionMesh()
        }
        GLES20.glViewport(0, 0, mHmd!!.screenParams.width, mHmd!!.screenParams.height)
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (this.mChromaticAberrationCorrectionEnabled) {
            GLES20.glUseProgram(mProgramHolderAberration!!.program)
        } else {
            GLES20.glUseProgram(mProgramHolder!!.program)
        }
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
        GLES20.glScissor(0, 0, mHmd!!.screenParams.width / 2, mHmd!!.screenParams.height)
        this.renderDistortionMesh(this.mLeftEyeDistortionMesh, textureId)
        GLES20.glScissor(
            mHmd!!.screenParams.width / 2, 0,
            mHmd!!.screenParams.width / 2,
            mHmd!!.screenParams.height
        )
        this.renderDistortionMesh(this.mRightEyeDistortionMesh, textureId)
        if (this.mRestoreGLStateEnabled) {
            if (this.mChromaticAberrationCorrectionEnabled) {
                mGLStateBackupAberration.writeToGL()
            } else {
                mGLStateBackup.writeToGL()
            }
        }
    }

    fun setResolutionScale(scale: Float) {
        this.mResolutionScale = scale
        this.mViewportsChanged = true
    }

    fun setRestoreGLStateEnabled(enabled: Boolean) {
        this.mRestoreGLStateEnabled = enabled
    }

    fun setChromaticAberrationCorrectionEnabled(enabled: Boolean) {
        this.mChromaticAberrationCorrectionEnabled = enabled
    }

    fun setVignetteEnabled(enabled: Boolean) {
        this.mVignetteEnabled = enabled
        this.mFovsChanged = true
    }

    fun onFovChanged(
        hmd: HeadMountedDisplay?, leftFov: FieldOfView?,
        rightFov: FieldOfView?, virtualEyeToScreenDistance: Float
    ) {
        check(!this.mDrawingFrame) { "Cannot change FOV while rendering a frame." }
        this.mHmd = HeadMountedDisplay(hmd)
        this.mLeftEyeViewport = this.initViewportForEye(leftFov, 0.0f)
        this.mRightEyeViewport = this.initViewportForEye(
            rightFov,
            mLeftEyeViewport!!.width
        )
        this.mMetersPerTanAngle = virtualEyeToScreenDistance
        val screen = mHmd!!.screenParams
        this.mXPxPerTanAngle = screen.width / (screen.widthMeters / this.mMetersPerTanAngle)
        this.mYPxPerTanAngle = screen.height / (screen.heightMeters / this.mMetersPerTanAngle)
        this.mFovsChanged = true
        this.mViewportsChanged = true
    }

    fun haveViewportsChanged(): Boolean {
        return this.mViewportsChanged
    }

    fun updateViewports(leftViewport: Viewport?, rightViewport: Viewport?) {
        leftViewport!!.setViewport(
            Math.round(mLeftEyeViewport!!.x * this.mXPxPerTanAngle * this.mResolutionScale),
            Math.round(mLeftEyeViewport!!.y * this.mYPxPerTanAngle * this.mResolutionScale),
            Math.round(mLeftEyeViewport!!.width * this.mXPxPerTanAngle * this.mResolutionScale),
            Math.round(mLeftEyeViewport!!.height * this.mYPxPerTanAngle * this.mResolutionScale)
        )
        rightViewport!!.setViewport(
            Math.round(mRightEyeViewport!!.x * this.mXPxPerTanAngle * this.mResolutionScale),
            Math.round(mRightEyeViewport!!.y * this.mYPxPerTanAngle * this.mResolutionScale),
            Math.round(mRightEyeViewport!!.width * this.mXPxPerTanAngle * this.mResolutionScale),
            Math.round(mRightEyeViewport!!.height * this.mYPxPerTanAngle * this.mResolutionScale)
        )
        this.mViewportsChanged = false
    }

    private fun updateTextureAndDistortionMesh() {
        val screen = mHmd!!.screenParams
        val cdp = mHmd!!.cardboardDeviceParams

        if (this.mProgramHolder == null) {
            this.mProgramHolder = this.createProgramHolder()
        }
        if (this.mProgramHolderAberration == null) {
            this.mProgramHolderAberration = createProgramHolder(true) as ProgramHolderAberration
        }
        val textureWidthTanAngle =
            mLeftEyeViewport!!.width + mRightEyeViewport!!.width
        val textureHeightTanAngle = max(
            mLeftEyeViewport!!.height.toDouble(),
            mRightEyeViewport!!.height.toDouble()
        ).toFloat()
        val maxTextureSize = intArrayOf(0)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTextureSize, 0)
        val textureWidthPx = min(
            Math.round(textureWidthTanAngle * this.mXPxPerTanAngle).toDouble(),
            maxTextureSize[0].toDouble()
        ).toInt()
        val textureHeightPx = min(
            Math.round(textureHeightTanAngle * this.mYPxPerTanAngle).toDouble(),
            maxTextureSize[0].toDouble()
        ).toInt()
        var xEyeOffsetTanAngleScreen =
            (screen.widthMeters / 2.0f - cdp!!.interLensDistance / 2.0f) / this.mMetersPerTanAngle
        val yEyeOffsetTanAngleScreen =
            (cdp!!.verticalDistanceToLensCenter - screen.borderSizeMeters) / this.mMetersPerTanAngle
        this.mLeftEyeDistortionMesh = this.createDistortionMesh(
            this.mLeftEyeViewport,
            textureWidthTanAngle, textureHeightTanAngle,
            xEyeOffsetTanAngleScreen, yEyeOffsetTanAngleScreen
        )
        xEyeOffsetTanAngleScreen =
            screen.widthMeters / this.mMetersPerTanAngle - xEyeOffsetTanAngleScreen
        this.mRightEyeDistortionMesh = this.createDistortionMesh(
            this.mRightEyeViewport,
            textureWidthTanAngle, textureHeightTanAngle,
            xEyeOffsetTanAngleScreen, yEyeOffsetTanAngleScreen
        )
        this.setupRenderTextureAndRenderbuffer(textureWidthPx, textureHeightPx)
        this.mFovsChanged = false
    }

    private fun initViewportForEye(fov: FieldOfView?, xOffset: Float): EyeViewport {
        val left = fov!!.left
        val right = fov.right
        val bottom = fov.bottom
        val top = fov.top

        val vp = EyeViewport()
        vp.x = xOffset
        vp.y = 0.0f
        vp.width = left + right
        vp.height = bottom + top
        vp.eyeX = left + xOffset
        vp.eyeY = bottom
        return vp
    }

    private fun createDistortionMesh(
        eyeViewport: EyeViewport?,
        textureWidthTanAngle: Float, textureHeightTanAngle: Float,
        xEyeOffsetTanAngleScreen: Float, yEyeOffsetTanAngleScreen: Float
    ): DistortionMesh {
        return DistortionMesh(
            mHmd?.cardboardDeviceParams?.distortion,
            mHmd?.cardboardDeviceParams?.distortion,
            mHmd?.cardboardDeviceParams?.distortion,
            mHmd!!.screenParams.widthMeters / this.mMetersPerTanAngle,
            mHmd!!.screenParams
                .heightMeters / this.mMetersPerTanAngle,
            xEyeOffsetTanAngleScreen,
            yEyeOffsetTanAngleScreen,
            textureWidthTanAngle,
            textureHeightTanAngle,
            eyeViewport!!.eyeX,
            eyeViewport.eyeY,
            eyeViewport.x,
            eyeViewport.y,
            eyeViewport.width,
            eyeViewport.height
        )
    }

    private fun renderDistortionMesh(mesh: DistortionMesh?, textureId: Int) {
        val holder = if (this.mChromaticAberrationCorrectionEnabled) {
            mProgramHolderAberration
        } else {
            mProgramHolder
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh!!.mArrayBufferId)
        GLES20.glVertexAttribPointer(holder!!.aPosition, 2, GLES20.GL_FLOAT, false, 36, 0 * 4)
        GLES20.glEnableVertexAttribArray(holder.aPosition)
        GLES20.glVertexAttribPointer(holder.aVignette, 1, GLES20.GL_FLOAT, false, 36, 2 * 4)
        GLES20.glEnableVertexAttribArray(holder.aVignette)
        GLES20.glVertexAttribPointer(holder.aBlueTextureCoord, 2, GLES20.GL_FLOAT, false, 36, 7 * 4)
        GLES20.glEnableVertexAttribArray(holder.aBlueTextureCoord)
        if (this.mChromaticAberrationCorrectionEnabled) {
            GLES20.glVertexAttribPointer(
                (holder as ProgramHolderAberration?)!!.aRedTextureCoord,
                2,
                GLES20.GL_FLOAT,
                false,
                36,
                3 * 4
            )
            GLES20.glEnableVertexAttribArray((holder as ProgramHolderAberration?)!!.aRedTextureCoord)
            GLES20.glVertexAttribPointer(
                (holder as ProgramHolderAberration?)!!.aGreenTextureCoord,
                2,
                GLES20.GL_FLOAT,
                false,
                36,
                5 * 4
            )
            GLES20.glEnableVertexAttribArray((holder as ProgramHolderAberration?)!!.aGreenTextureCoord)
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(mProgramHolder!!.uTextureSampler, 0)
        GLES20.glUniform1f(mProgramHolder!!.uTextureCoordScale, this.mResolutionScale)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mesh.mElementBufferId)
        GLES20.glDrawElements(GLES20.GL_TRIANGLE_STRIP, mesh.nIndices, GLES20.GL_UNSIGNED_SHORT, 0)
    }

    private fun createTexture(width: Int, height: Int, textureFormat: Int, textureType: Int): Int {
        val textureIds = intArrayOf(0)
        GLES20.glGenTextures(1, textureIds, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0])
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            textureFormat,
            width,
            height,
            0,
            textureFormat,
            textureType,
            null as Buffer?
        )
        return textureIds[0]
    }

    private fun setupRenderTextureAndRenderbuffer(width: Int, height: Int): Int {
        if (this.mTextureId != -1) {
            GLES20.glDeleteTextures(1, intArrayOf(this.mTextureId), 0)
        }
        if (this.mRenderbufferId != -1) {
            GLES20.glDeleteRenderbuffers(1, intArrayOf(this.mRenderbufferId), 0)
        }
        if (this.mFramebufferId != -1) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(this.mFramebufferId), 0)
        }
        this.mTextureId = this.createTexture(width, height, this.mTextureFormat, this.mTextureType)
        this.mTextureFormatChanged = false
        this.checkGlError("setupRenderTextureAndRenderbuffer: create texture")
        val renderbufferIds = intArrayOf(0)
        GLES20.glGenRenderbuffers(1, renderbufferIds, 0)
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, renderbufferIds[0])
        GLES20.glRenderbufferStorage(
            GLES20.GL_RENDERBUFFER,
            GLES20.GL_DEPTH_COMPONENT16,
            width,
            height
        )
        this.mRenderbufferId = renderbufferIds[0]
        this.checkGlError("setupRenderTextureAndRenderbuffer: create renderbuffer")
        val framebufferIds = intArrayOf(0)
        GLES20.glGenFramebuffers(1, framebufferIds, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebufferIds[0])
        this.mFramebufferId = framebufferIds[0]
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER,
            GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D,
            this.mTextureId,
            0
        )
        GLES20.glFramebufferRenderbuffer(
            GLES20.GL_FRAMEBUFFER,
            GLES20.GL_DEPTH_ATTACHMENT,
            GLES20.GL_RENDERBUFFER,
            renderbufferIds[0]
        )
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            val s = "Framebuffer is not complete: "
            val value = Integer.toHexString(status).toString()
            throw RuntimeException(if ((value.length != 0)) s + value else java.lang.String(s) as String)
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        return framebufferIds[0]
    }

    private fun loadShader(shaderType: Int, source: String): Int {
        var shader = GLES20.glCreateShader(shaderType)
        if (shader != 0) {
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val compiled = intArrayOf(0)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                Log.e(
                    "DistortionRenderer",
                    StringBuilder(37).append("Could not compile shader ").append(shaderType)
                        .append(":").toString()
                )
                Log.e("DistortionRenderer", GLES20.glGetShaderInfoLog(shader))
                GLES20.glDeleteShader(shader)
                shader = 0
            }
        }
        return shader
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = this.loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) {
            return 0
        }
        val pixelShader = this.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (pixelShader == 0) {
            return 0
        }
        var program = GLES20.glCreateProgram()
        if (program != 0) {
            GLES20.glAttachShader(program, vertexShader)
            this.checkGlError("glAttachShader")
            GLES20.glAttachShader(program, pixelShader)
            this.checkGlError("glAttachShader")
            GLES20.glLinkProgram(program)
            val linkStatus = intArrayOf(0)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != 1) {
                Log.e("DistortionRenderer", "Could not link program: ")
                Log.e("DistortionRenderer", GLES20.glGetProgramInfoLog(program))
                GLES20.glDeleteProgram(program)
                program = 0
            }
        }
        return program
    }

    private fun createProgramHolder(aberrationCorrected: Boolean = false): ProgramHolder {
        val holder: ProgramHolder
        val state: GLStateBackup
        if (aberrationCorrected) {
            holder = ProgramHolderAberration()
            holder.program =
                this.createProgram(VERTEX_SHADER_ABERRATION, FRAGMENT_SHADER_ABERRATION)
            if (holder.program == 0) {
                throw RuntimeException("Could not create aberration-corrected program")
            }
            state = this.mGLStateBackupAberration
        } else {
            holder = ProgramHolder()
            holder.program = this.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            if (holder.program == 0) {
                throw RuntimeException("Could not create program")
            }
            state = this.mGLStateBackup
        }
        holder.aPosition = GLES20.glGetAttribLocation(holder.program, "aPosition")
        this.checkGlError("glGetAttribLocation aPosition")
        if (holder.aPosition == -1) {
            throw RuntimeException("Could not get attrib location for aPosition")
        }
        state.addTrackedVertexAttribute(holder.aPosition)
        holder.aVignette = GLES20.glGetAttribLocation(holder.program, "aVignette")
        this.checkGlError("glGetAttribLocation aVignette")
        if (holder.aVignette == -1) {
            throw RuntimeException("Could not get attrib location for aVignette")
        }
        state.addTrackedVertexAttribute(holder.aVignette)
        if (aberrationCorrected) {
            (holder as ProgramHolderAberration).aRedTextureCoord =
                GLES20.glGetAttribLocation(holder.program, "aRedTextureCoord")
            this.checkGlError("glGetAttribLocation aRedTextureCoord")
            if (holder.aRedTextureCoord == -1) {
                throw RuntimeException("Could not get attrib location for aRedTextureCoord")
            }
            holder.aGreenTextureCoord =
                GLES20.glGetAttribLocation(holder.program, "aGreenTextureCoord")
            this.checkGlError("glGetAttribLocation aGreenTextureCoord")
            if (holder.aGreenTextureCoord == -1) {
                throw RuntimeException("Could not get attrib location for aGreenTextureCoord")
            }
            state.addTrackedVertexAttribute(holder.aRedTextureCoord)
            state.addTrackedVertexAttribute(holder.aGreenTextureCoord)
        }
        holder.aBlueTextureCoord = GLES20.glGetAttribLocation(holder.program, "aBlueTextureCoord")
        this.checkGlError("glGetAttribLocation aBlueTextureCoord")
        if (holder.aBlueTextureCoord == -1) {
            throw RuntimeException("Could not get attrib location for aBlueTextureCoord")
        }
        state.addTrackedVertexAttribute(holder.aBlueTextureCoord)
        holder.uTextureCoordScale =
            GLES20.glGetUniformLocation(holder.program, "uTextureCoordScale")
        this.checkGlError("glGetUniformLocation uTextureCoordScale")
        if (holder.uTextureCoordScale == -1) {
            throw RuntimeException("Could not get attrib location for uTextureCoordScale")
        }
        holder.uTextureSampler = GLES20.glGetUniformLocation(holder.program, "uTextureSampler")
        this.checkGlError("glGetUniformLocation uTextureSampler")
        if (holder.uTextureSampler == -1) {
            throw RuntimeException("Could not get attrib location for uTextureSampler")
        }
        return holder
    }

    private fun checkGlError(op: String) {
        val error: Int
        if ((GLES20.glGetError().also { error = it }) != 0) {
            val s = "DistortionRenderer"
            val value = (op.toString())
            Log.e(
                s,
                StringBuilder(21 + value.length).append(value).append(": glError ").append(error)
                    .toString()
            )
            val value2 = (op.toString())
            throw RuntimeException(
                StringBuilder(21 + value2.length).append(value2).append(": glError ").append(error)
                    .toString()
            )
        }
    }

    private open inner class ProgramHolder {
        var program: Int = 0
        var aPosition: Int = 0
        var aVignette: Int = 0
        var aBlueTextureCoord: Int = 0
        var uTextureCoordScale: Int = 0
        var uTextureSampler: Int = 0
    }

    private inner class ProgramHolderAberration : ProgramHolder() {
        var aRedTextureCoord: Int = 0
        var aGreenTextureCoord: Int = 0
    }

    private inner class EyeViewport {
        var x: Float = 0f
        var y: Float = 0f
        var width: Float = 0f
        var height: Float = 0f
        var eyeX: Float = 0f
        var eyeY: Float = 0f

        override fun toString(): String {
            return """
                {
                ${StringBuilder(22).append("  x: ").append(this.x).append(",\n")}${
                StringBuilder(22).append("  y: ").append(
                    this.y
                ).append(",\n")
            }${StringBuilder(26).append("  width: ").append(this.width).append(",\n")}${
                StringBuilder(27).append("  height: ").append(
                    this.height
                ).append(",\n")
            }${
                StringBuilder(25).append("  eyeX: ").append(
                    this.eyeX
                ).append(",\n")
            }${
                StringBuilder(25).append("  eyeY: ").append(
                    this.eyeY
                ).append(",\n")
            }}
                """.trimIndent()
        }
    }

    private inner class DistortionMesh(
        distortionRed: Distortion?,
        distortionGreen: Distortion?,
        distortionBlue: Distortion?,
        screenWidth: Float, screenHeight: Float,
        xEyeOffsetScreen: Float, yEyeOffsetScreen: Float,
        textureWidth: Float, textureHeight: Float,
        xEyeOffsetTexture: Float, yEyeOffsetTexture: Float,
        viewportXTexture: Float, viewportYTexture: Float,
        viewportWidthTexture: Float, viewportHeightTexture: Float
    ) {
        fun someFunction() {
            // Ahora accedes a las constantes a través de DistortionRenderer:
            val vs = DistortionRenderer.VERTEX_SHADER
            val fs = DistortionRenderer.FRAGMENT_SHADER

            // Para usar clamp:
            val clampedVal = DistortionRenderer.clamp(0.5f, 0.0f, 1.0f)
        }
        /*companion object {
            private const val TAG = "DistortionRenderer"
            const val BYTES_PER_FLOAT: Int = 4
            const val BYTES_PER_SHORT: Int = 2
            const val COMPONENTS_PER_VERT: Int = 9
            const val ROWS: Int = 40
            const val COLS: Int = 40
            const val VIGNETTE_SIZE_TAN_ANGLE: Float = 0.05f
        }*/

        var nIndices: Int
        var mArrayBufferId: Int = -1
        var mElementBufferId: Int = -1

        init {
            val vertexData = FloatArray(14400)
            var vertexOffset: Short = 0
            for (row in 0 until Companion.ROWS) {
                for (col in 0 until Companion.COLS) {
                    val uTextureBlue =
                        col / 39.0f * (viewportWidthTexture / textureWidth) + viewportXTexture / textureWidth
                    val vTextureBlue =
                        row / 39.0f * (viewportHeightTexture / textureHeight) + viewportYTexture / textureHeight
                    val xTexture = uTextureBlue * textureWidth - xEyeOffsetTexture
                    val yTexture = vTextureBlue * textureHeight - yEyeOffsetTexture
                    val rTexture = sqrt((xTexture * xTexture + yTexture * yTexture).toDouble())
                        .toFloat()
                    val textureToScreenBlue =
                        if ((rTexture > 0.0f)) (distortionBlue!!.distortInverse(rTexture) / rTexture) else 1.0f
                    val xScreen = xTexture * textureToScreenBlue
                    val yScreen = yTexture * textureToScreenBlue
                    val uScreen = (xScreen + xEyeOffsetScreen) / screenWidth
                    val vScreen = (yScreen + yEyeOffsetScreen) / screenHeight
                    val rScreen = rTexture * textureToScreenBlue
                    val screenToTextureGreen =
                        if ((rScreen > 0.0f)) distortionGreen!!.distortionFactor(rScreen) else 1.0f
                    val uTextureGreen =
                        (xScreen * screenToTextureGreen + xEyeOffsetTexture) / textureWidth
                    val vTextureGreen =
                        (yScreen * screenToTextureGreen + yEyeOffsetTexture) / textureHeight
                    val screenToTextureRed =
                        if ((rScreen > 0.0f)) distortionRed!!.distortionFactor(rScreen) else 1.0f
                    val uTextureRed =
                        (xScreen * screenToTextureRed + xEyeOffsetTexture) / textureWidth
                    val vTextureRed =
                        (yScreen * screenToTextureRed + yEyeOffsetTexture) / textureHeight
                    val vignetteSizeTexture =
                        Companion.VIGNETTE_SIZE_TAN_ANGLE / textureToScreenBlue
                    val dxTexture = xTexture + xEyeOffsetTexture - clamp(
                        xTexture + xEyeOffsetTexture,
                        viewportXTexture + vignetteSizeTexture,
                        viewportXTexture + viewportWidthTexture - vignetteSizeTexture
                    )
                    val dyTexture = yTexture + yEyeOffsetTexture - clamp(
                        yTexture + yEyeOffsetTexture,
                        viewportYTexture + vignetteSizeTexture,
                        viewportYTexture + viewportHeightTexture - vignetteSizeTexture
                    )
                    val drTexture = sqrt((dxTexture * dxTexture + dyTexture * dyTexture).toDouble())
                        .toFloat()
                    var vignette = if (this@DistortionRenderer.mVignetteEnabled) {
                        1.0f - clamp(drTexture / vignetteSizeTexture, 0.0f, 1.0f)
                    } else {
                        1.0f
                    }
                    vertexData[vertexOffset + 0] = 2.0f * uScreen - 1.0f
                    vertexData[vertexOffset + 1] = 2.0f * vScreen - 1.0f
                    vertexData[vertexOffset + 2] = vignette
                    vertexData[vertexOffset + 3] = uTextureRed
                    vertexData[vertexOffset + 4] = vTextureRed
                    vertexData[vertexOffset + 5] = uTextureGreen
                    vertexData[vertexOffset + 6] = vTextureGreen
                    vertexData[vertexOffset + 7] = uTextureBlue
                    vertexData[vertexOffset + 8] = vTextureBlue
                    vertexOffset = (vertexOffset + Companion.COMPONENTS_PER_VERT).toShort()
                }
            }
            this.nIndices = 3158
            val indexData = ShortArray(this.nIndices)
            var indexOffset: Short = 0
            vertexOffset = 0
            for (row2 in 0..38) {
                if (row2 > 0) {
                    indexData[indexOffset.toInt()] = indexData[indexOffset - 1]
                    ++indexOffset
                }
                for (col2 in 0..39) {
                    if (col2 > 0) {
                        if (row2 % 2 == 0) {
                            ++vertexOffset
                        } else {
                            --vertexOffset
                        }
                    }
                    indexData[indexOffset.toInt()] = vertexOffset
                    ++indexOffset
                    indexData[indexOffset.toInt()] = (vertexOffset + 40).toShort()
                    ++indexOffset
                }
                vertexOffset = (vertexOffset + 40).toShort()
            }
            val vertexBuffer =
                ByteBuffer.allocateDirect(vertexData.size * Companion.BYTES_PER_FLOAT).order(
                    ByteOrder.nativeOrder()
                ).asFloatBuffer()
            vertexBuffer.put(vertexData).position(0)
            val indexBuffer =
                ByteBuffer.allocateDirect(indexData.size * Companion.BYTES_PER_SHORT).order(
                    ByteOrder.nativeOrder()
                ).asShortBuffer()
            indexBuffer.put(indexData).position(0)
            val bufferIds = IntArray(2)
            GLES20.glGenBuffers(2, bufferIds, 0)
            this.mArrayBufferId = bufferIds[0]
            this.mElementBufferId = bufferIds[1]
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, this.mArrayBufferId)
            GLES20.glBufferData(
                GLES20.GL_ARRAY_BUFFER,
                vertexData.size * Companion.BYTES_PER_FLOAT,
                vertexBuffer as Buffer,
                GLES20.GL_STATIC_DRAW
            )
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, this.mElementBufferId)
            GLES20.glBufferData(
                GLES20.GL_ELEMENT_ARRAY_BUFFER,
                indexData.size * Companion.BYTES_PER_SHORT,
                indexBuffer as Buffer,
                GLES20.GL_STATIC_DRAW
            )
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        }
    }

    companion object {
        private const val TAG = "DistortionRenderer"
        const val VERTEX_SHADER: String = ("attribute vec2 aPosition;\n"
                + "attribute float aVignette;\n"
                + "attribute vec2 aBlueTextureCoord;\n"
                + "varying vec2 vTextureCoord;\n"
                + "varying float vVignette;\n"
                + "uniform float uTextureCoordScale;\n"
                + "void main() {\n"
                + "    gl_Position = vec4(aPosition, 0.0, 1.0);\n"
                + "    vTextureCoord = aBlueTextureCoord.xy * uTextureCoordScale;\n"
                + "    vVignette = aVignette;\n"
                + "}\n")
        const val FRAGMENT_SHADER: String = ("precision mediump float;\n"
                + "varying vec2 vTextureCoord;\n"
                + "varying float vVignette;\n"
                + "uniform sampler2D uTextureSampler;\n"
                + "void main() {\n"
                + "    gl_FragColor = vVignette * texture2D(uTextureSampler, vTextureCoord);\n"
                + "}\n")
        const val VERTEX_SHADER_ABERRATION: String = ("attribute vec2 aPosition;\n"
                + "attribute float aVignette;\n"
                + "attribute vec2 aRedTextureCoord;\n"
                + "attribute vec2 aGreenTextureCoord;\n"
                + "attribute vec2 aBlueTextureCoord;\n"
                + "varying vec2 vRedTextureCoord;\n"
                + "varying vec2 vBlueTextureCoord;\n"
                + "varying vec2 vGreenTextureCoord;\n"
                + "varying float vVignette;\n"
                + "uniform float uTextureCoordScale;\n"
                + "void main() {\n"
                + "    gl_Position = vec4(aPosition, 0.0, 1.0);\n"
                + "    vRedTextureCoord = aRedTextureCoord.xy * uTextureCoordScale;\n"
                + "    vGreenTextureCoord = aGreenTextureCoord.xy * uTextureCoordScale;\n"
                + "    vBlueTextureCoord = aBlueTextureCoord.xy * uTextureCoordScale;\n"
                + "    vVignette = aVignette;\n"
                + "}\n")
        const val FRAGMENT_SHADER_ABERRATION: String = ("precision mediump float;\n"
                + "varying vec2 vRedTextureCoord;\n"
                + "varying vec2 vBlueTextureCoord;\n"
                + "varying vec2 vGreenTextureCoord;\n"
                + "varying float vVignette;\n"
                + "uniform sampler2D uTextureSampler;\n"
                + "void main() {\n"
                + "    gl_FragColor = vVignette * vec4(texture2D(uTextureSampler, vRedTextureCoord).r,\n"
                + "                    texture2D(uTextureSampler, vGreenTextureCoord).g,\n"
                + "                    texture2D(uTextureSampler, vBlueTextureCoord).b, 1.0);\n"
                + "}\n")

        fun clamp(value: Float, minVal: Float, maxVal: Float): Float {
            return kotlin.math.max(minVal, kotlin.math.min(maxVal, value))
        }

        const val BYTES_PER_FLOAT: Int = 4
        const val BYTES_PER_SHORT: Int = 2
        const val COMPONENTS_PER_VERT: Int = 9
        const val ROWS: Int = 40
        const val COLS: Int = 40
        const val VIGNETTE_SIZE_TAN_ANGLE: Float = 0.05f
    }
}
