package com.vrcardboardkotlin

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import java.util.concurrent.CountDownLatch
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.concurrent.Volatile
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.tan

class CardboardView : GLSurfaceView {
    private var mRendererHelper: RendererHelper? = null
    private var mHeadTracker: HeadTracker? = null
    private var mHmdManager: HeadMountedDisplayManager? = null
    private var mUiLayer: UiLayer? = null
    private var mShutdownLatch: CountDownLatch? = null
    private var mVRMode: Boolean = true
    private var mRendererSet: Boolean = false

    @Volatile
    private var mRestoreGLStateEnabled: Boolean

    @Volatile
    private var mDistortionCorrectionEnabled: Boolean

    @Volatile
    private var mChromaticAberrationCorrectionEnabled: Boolean

    @Volatile
    private var mVignetteEnabled: Boolean

    constructor(context: Context) : super(context) {
        this.mVRMode = true
        this.mRendererSet = false
        this.mRestoreGLStateEnabled = true
        this.mDistortionCorrectionEnabled = true
        this.mChromaticAberrationCorrectionEnabled = false
        this.mVignetteEnabled = true
        this.init(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        this.mVRMode = true
        this.mRendererSet = false
        this.mRestoreGLStateEnabled = true
        this.mDistortionCorrectionEnabled = true
        this.mChromaticAberrationCorrectionEnabled = false
        this.mVignetteEnabled = true
        this.init(context)
    }

    fun setRenderer(renderer: Renderer?) {
        if (renderer == null) {
            return
        }
        mRendererHelper!!.setRenderer(renderer)
        super.setRenderer(mRendererHelper as GLSurfaceView.Renderer?)
        this.mRendererSet = true
    }

    fun setRenderer(renderer: StereoRenderer?) {
        this.setRenderer(if ((renderer != null)) StereoRendererHelper(renderer) else (null as Renderer?))
    }

    val headMountedDisplay: HeadMountedDisplay?
        get() = mHmdManager?.headMountedDisplay

    fun updateCardboardDeviceParams(cardboardDeviceParams: CardboardDeviceParams?) {
        if (mHmdManager!!.updateCardboardDeviceParams(cardboardDeviceParams)) {
            mRendererHelper!!.setCardboardDeviceParams(this.cardboardDeviceParams)
        }
    }

    val cardboardDeviceParams: CardboardDeviceParams?
        get() = mHmdManager?.headMountedDisplay?.cardboardDeviceParams

    override fun onResume() {
        mHmdManager!!.onResume()
        mRendererHelper!!.setCardboardDeviceParams(this.cardboardDeviceParams)
        if (this.mRendererSet) {
            super.onResume()
        }
        val phoneParams = PhoneParams.readFromExternalStorage()
        if (phoneParams != null) {
            mHeadTracker!!.setGyroBias(phoneParams.gyroBias)
        }
        mHeadTracker!!.startTracking()
    }

    override fun onPause() {
        mHmdManager!!.onPause()
        if (this.mRendererSet) {
            super.onPause()
        }
        mHeadTracker!!.stopTracking()
    }

    override fun queueEvent(r: Runnable) {
        if (!this.mRendererSet) {
            r.run()
            return
        }
        super.queueEvent(r)
    }

    override fun setRenderer(renderer: GLSurfaceView.Renderer) {
        throw RuntimeException("Please use the CardboardView renderer interfaces")
    }

    public override fun onDetachedFromWindow() {
        if (this.mRendererSet && this.mShutdownLatch == null) {
            this.mShutdownLatch = CountDownLatch(1)
            mRendererHelper!!.shutdown()
            try {
                mShutdownLatch!!.await()
            } catch (e: InterruptedException) {
                val s = "CardboardView"
                val s2 = "Interrupted during shutdown: "
                val value = e.toString().toString()
                Log.e(s, if ((value.length != 0)) s2 + value else java.lang.String(s2) as String)
            }
            this.mShutdownLatch = null
        }
        super.onDetachedFromWindow()
    }

    private fun init(context: Context) {
        this.setEGLContextClientVersion(2)
        this.preserveEGLContextOnPause = true
        this.mHeadTracker = HeadTracker.Companion.createFromContext(context)
        this.mHmdManager = HeadMountedDisplayManager(context)
        this.mRendererHelper = RendererHelper()
        this.mUiLayer = UiLayer(context)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        return mUiLayer!!.onTouchEvent(e) || super.onTouchEvent(e)
    }

    private inner class RendererHelper : GLSurfaceView.Renderer {
        private val mHeadTransform = HeadTransform()
        private val mMonocular = Eye(0)
        private val mLeftEye = Eye(1)
        private val mRightEye = Eye(2)
        private val mLeftEyeTranslate: FloatArray
        private val mRightEyeTranslate: FloatArray
        private var mRenderer: Renderer? = null
        private var mSurfaceCreated = false
        private val mHmd = HeadMountedDisplay(this@CardboardView.headMountedDisplay)
        private var mDistortionRenderer: DistortionRenderer? = null
        private val mVRMode: Boolean
        private val mDistortionCorrectionEnabled: Boolean
        private var mProjectionChanged: Boolean
        private var mInvalidSurfaceSize = false

        init {
            this.updateFieldOfView(
                mLeftEye.fov,
                mRightEye.fov
            )
            DistortionRenderer().also { this.mDistortionRenderer = it }.setRestoreGLStateEnabled(
                this@CardboardView.mRestoreGLStateEnabled
            )
            mDistortionRenderer?.setChromaticAberrationCorrectionEnabled(this@CardboardView.mChromaticAberrationCorrectionEnabled)
            mDistortionRenderer?.setVignetteEnabled(this@CardboardView.mVignetteEnabled)
            this.mLeftEyeTranslate = FloatArray(16)
            this.mRightEyeTranslate = FloatArray(16)
            this.mVRMode = this@CardboardView.mVRMode
            this.mDistortionCorrectionEnabled = this@CardboardView.mDistortionCorrectionEnabled
            this.mProjectionChanged = true
        }

        fun setRenderer(renderer: Renderer?) {
            this.mRenderer = renderer
        }

        fun shutdown() {
            this@CardboardView.queueEvent {
                if (this@RendererHelper.mRenderer != null && this@RendererHelper.mSurfaceCreated) {
                    this@RendererHelper.mSurfaceCreated = false
                    mRenderer!!.onRendererShutdown()
                }
                mShutdownLatch!!.countDown()
            }
        }

        fun setCardboardDeviceParams(newParams: CardboardDeviceParams?) {
            val deviceParams = CardboardDeviceParams(newParams)
            this@CardboardView.queueEvent {
                mHmd.cardboardDeviceParams = deviceParams
                this@RendererHelper.mProjectionChanged = true
            }
        }

        private fun getFrameParams(
            head: HeadTransform,
            leftEye: Eye,
            rightEye: Eye,
            monocular: Eye
        ) {
            val cdp = mHmd.cardboardDeviceParams ?: return
            val screen = mHmd.screenParams
            mHeadTracker!!.getLastHeadView(head.headView, 0)
            val halfInterpupillaryDistance = cdp.interLensDistance * 0.5f
            if (this.mVRMode) {
                Matrix.setIdentityM(this.mLeftEyeTranslate, 0)
                Matrix.setIdentityM(this.mRightEyeTranslate, 0)
                Matrix.translateM(this.mLeftEyeTranslate, 0, halfInterpupillaryDistance, 0.0f, 0.0f)
                Matrix.translateM(
                    this.mRightEyeTranslate,
                    0,
                    -halfInterpupillaryDistance,
                    0.0f,
                    0.0f
                )
                Matrix.multiplyMM(leftEye.eyeView, 0, this.mLeftEyeTranslate, 0, head.headView, 0)
                Matrix.multiplyMM(rightEye.eyeView, 0, this.mRightEyeTranslate, 0, head.headView, 0)
            } else {
                System.arraycopy(head.headView, 0, monocular.eyeView, 0, head.headView.size)
            }
            if (this.mProjectionChanged) {
                monocular.viewport.setViewport(0, 0, screen.width, screen.height)
                mUiLayer!!.updateViewport(monocular.viewport)
                if (!this.mVRMode) {
                    this.updateMonocularFieldOfView(monocular.fov)
                } else if (this.mDistortionCorrectionEnabled) {
                    this.updateFieldOfView(leftEye.fov, rightEye.fov)
                    mDistortionRenderer!!.onFovChanged(
                        this.mHmd, leftEye.fov, rightEye.fov,
                        virtualEyeToScreenDistance
                    )
                } else {
                    this.updateUndistortedFovAndViewport()
                }
                leftEye.setProjectionChanged()
                rightEye.setProjectionChanged()
                monocular.setProjectionChanged()
                this.mProjectionChanged = false
            }
            if (this.mDistortionCorrectionEnabled) {
                mDistortionRenderer?.beforeDrawFrame()
                mRenderer?.onDrawFrame(this.mHeadTransform, this.mLeftEye, this.mRightEye)
                mDistortionRenderer?.afterDrawFrame()
            } else {
                mRenderer?.onDrawFrame(this.mHeadTransform, this.mLeftEye, this.mRightEye)
            }
        }

        override fun onDrawFrame(gl: GL10) {
            if (this.mRenderer == null || !this.mSurfaceCreated || this.mInvalidSurfaceSize) {
                return
            }
            this.getFrameParams(this.mHeadTransform, this.mLeftEye, this.mRightEye, this.mMonocular)
            if (this.mVRMode) {
                if (this.mDistortionCorrectionEnabled) {
                    mDistortionRenderer!!.beforeDrawFrame()
                    mRenderer!!.onDrawFrame(this.mHeadTransform, this.mLeftEye, this.mRightEye)
                    mDistortionRenderer!!.afterDrawFrame()
                } else {
                    mRenderer!!.onDrawFrame(this.mHeadTransform, this.mLeftEye, this.mRightEye)
                }
            } else {
                mRenderer!!.onDrawFrame(this.mHeadTransform, this.mMonocular, null)
            }
            mRenderer!!.onFinishFrame(mMonocular.viewport)
            if (this.mVRMode) {
                mUiLayer!!.draw()
            }
        }

        override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
            val screen = mHmd.screenParams

            if (width != screen.width || height != screen.height) {
                Log.w(TAG, "Mismatch: Surface(${width}x${height}) vs Screen(${screen.width}x${screen.height})")
                this@CardboardView.post {
                    layoutParams = layoutParams.apply {
                        this.width = screen.width
                        this.height = screen.height
                    }
                    requestLayout()
                }
                this.mInvalidSurfaceSize = true
                return
            }

            this.mInvalidSurfaceSize = false

            // Asegúrate de notificar al renderer si las dimensiones ya coinciden
            mRenderer?.onSurfaceChanged(width, height)
        }




        override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
            if (this.mRenderer == null) {
                return
            }
            this.mSurfaceCreated = true
            mRenderer!!.onSurfaceCreated(config)
            mUiLayer!!.initializeGl()
        }

        private fun updateFieldOfView(leftEyeFov: FieldOfView?, rightEyeFov: FieldOfView?) {
            val cdp = mHmd.cardboardDeviceParams ?: return
            val screen = mHmd.screenParams
            val distortion = cdp.distortion ?: return
            val eyeToScreenDist = this.virtualEyeToScreenDistance

            // Distancias para calcular ángulos
            val outerDist = (screen.widthMeters - cdp.interLensDistance) / 2.0f
            val innerDist = cdp.interLensDistance / 2.0f
            val bottomDist = cdp.verticalDistanceToLensCenter - screen.borderSizeMeters
            val topDist = screen.heightMeters + screen.borderSizeMeters - cdp.verticalDistanceToLensCenter

            // Ángulos calculados
            val outerAngle = Math.toDegrees(atan(distortion.distort(outerDist / eyeToScreenDist).toDouble())).toFloat()
            val innerAngle = Math.toDegrees(atan(distortion.distort(innerDist / eyeToScreenDist).toDouble())).toFloat()
            val bottomAngle = Math.toDegrees(atan(distortion.distort(bottomDist / eyeToScreenDist).toDouble())).toFloat()
            val topAngle = Math.toDegrees(atan(distortion.distort(topDist / eyeToScreenDist).toDouble())).toFloat()

            // Configurar los valores de FieldOfView para los ojos izquierdo y derecho
            leftEyeFov?.apply {
                left = min(outerAngle, cdp.leftEyeMaxFov?.left ?: Companion.DEFAULT_FOV)
                right = min(innerAngle, cdp.leftEyeMaxFov?.right ?: Companion.DEFAULT_FOV)
                bottom = min(bottomAngle, cdp.leftEyeMaxFov?.bottom ?: Companion.DEFAULT_FOV)
                top = min(topAngle, cdp.leftEyeMaxFov?.top ?: Companion.DEFAULT_FOV)
            }

            rightEyeFov?.apply {
                left = leftEyeFov?.right ?: Companion.DEFAULT_FOV
                right = leftEyeFov?.left ?: Companion.DEFAULT_FOV
                bottom = leftEyeFov?.bottom ?: Companion.DEFAULT_FOV
                top = leftEyeFov?.top ?: Companion.DEFAULT_FOV
            }
        }

        private fun updateMonocularFieldOfView(monocularFov: FieldOfView?) {
            val screen = mHmd.screenParams
            val monocularBottomFov = 22.5f
            val monocularLeftFov =
                Math.toDegrees(atan(tan(Math.toRadians(monocularBottomFov.toDouble())) * screen.widthMeters / screen.heightMeters))
                    .toFloat()

            // Asignación directa a las propiedades
            monocularFov?.left = monocularLeftFov
            monocularFov?.right = monocularLeftFov
            monocularFov?.bottom = monocularBottomFov
            monocularFov?.top = monocularBottomFov
        }


        private fun updateUndistortedFovAndViewport() {
            val screen = mHmd.screenParams
            val cdp = mHmd.cardboardDeviceParams
            val halfLensDistance = cdp!!.interLensDistance / 2.0f
            val eyeToScreen = this.virtualEyeToScreenDistance
            val left = screen.widthMeters / 2.0f - halfLensDistance
            val right = halfLensDistance
            val bottom = cdp.verticalDistanceToLensCenter - screen.borderSizeMeters
            val top =
                screen.borderSizeMeters + screen.heightMeters - cdp.verticalDistanceToLensCenter
            val leftEyeFov = mLeftEye.fov
            leftEyeFov.left =
                Math.toDegrees(atan2(left.toDouble(), eyeToScreen.toDouble()))
                    .toFloat()
            leftEyeFov.right =
                Math.toDegrees(atan2(right.toDouble(), eyeToScreen.toDouble()))
                    .toFloat()
            leftEyeFov.bottom =
                Math.toDegrees(atan2(bottom.toDouble(), eyeToScreen.toDouble()))
                    .toFloat()
            leftEyeFov.top =
                Math.toDegrees(atan2(top.toDouble(), eyeToScreen.toDouble()))
                    .toFloat()
            val rightEyeFov = mRightEye.fov
            rightEyeFov.left = leftEyeFov.right
            rightEyeFov.right = leftEyeFov.left
            rightEyeFov.bottom = leftEyeFov.bottom
            rightEyeFov.top = leftEyeFov.top
            mLeftEye.viewport.setViewport(0, 0, screen.width / 2, screen.height)
            mRightEye.viewport.setViewport(screen.width / 2, 0, screen.width / 2, screen.height)
        }

        private val virtualEyeToScreenDistance: Float
            get() = mHmd.cardboardDeviceParams?.screenToLensDistance ?: 0f
    }

    private inner class StereoRendererHelper(private val mStereoRenderer: StereoRenderer) :
        Renderer {
        private var mVRMode: Boolean

        init {
            this.mVRMode = this@CardboardView.mVRMode
        }

        fun setVRModeEnabled(enabled: Boolean) {
            this@CardboardView.queueEvent { this@StereoRendererHelper.mVRMode = enabled }
        }

        override fun onDrawFrame(head: HeadTransform?, leftEye: Eye, rightEye: Eye?) {
            mStereoRenderer.onNewFrame(head)
            GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
            leftEye.viewport.setGLViewport()
            leftEye.viewport.setGLScissor()
            mStereoRenderer.onDrawEye(leftEye)
            if (rightEye == null) {
                return
            }
            rightEye.viewport.setGLViewport()
            rightEye.viewport.setGLScissor()
            mStereoRenderer.onDrawEye(rightEye)
        }

        override fun onFinishFrame(viewport: Viewport?) {
            viewport!!.setGLViewport()
            viewport.setGLScissor()
            mStereoRenderer.onFinishFrame(viewport)
        }

        override fun onSurfaceChanged(width: Int, height: Int) {
            if (this.mVRMode) {
                mStereoRenderer.onSurfaceChanged(width / 2, height)
            } else {
                mStereoRenderer.onSurfaceChanged(width, height)
            }
        }

        override fun onSurfaceCreated(config: EGLConfig?) {
            mStereoRenderer.onSurfaceCreated(config)
        }

        override fun onRendererShutdown() {
            mStereoRenderer.onRendererShutdown()
        }
    }

    interface StereoRenderer {
        fun onNewFrame(p0: HeadTransform?)
        fun onDrawEye(p0: Eye)
        fun onFinishFrame(p0: Viewport?)
        fun onSurfaceChanged(p0: Int, p1: Int)
        fun onSurfaceCreated(p0: EGLConfig?)
        fun onRendererShutdown()
    }

    interface Renderer {
        fun onDrawFrame(p0: HeadTransform?, p1: Eye, p2: Eye?)
        fun onFinishFrame(p0: Viewport?)
        fun onSurfaceChanged(p0: Int, p1: Int)
        fun onSurfaceCreated(p0: EGLConfig?)
        fun onRendererShutdown()
    }

    companion object {
        private const val TAG = "CardboardView"
        private const val DEFAULT_FOV = 40.0f // Valor predeterminado para el FOV (puedes ajustarlo)
    }
}
