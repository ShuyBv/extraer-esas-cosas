package com.vrcardboardkotlin

import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Bundle
import com.vrcardboardkotlin.CardboardView.StereoRenderer
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig

class MainActivity() : CardboardActivity(), StereoRenderer {
    private val lightPosInEyeSpace: FloatArray = FloatArray(4)

    private val floorVertexCount: Int = floorCoords!!.size / COORDS_PER_VERTEX
    private lateinit var floorTransform: FloatArray
    private lateinit var camera: FloatArray
    private lateinit var view: FloatArray
    private lateinit var modelViewProjection: FloatArray
    private lateinit var floorView: FloatArray
    private val floorDepth: Float = 20f


    // Rendering variables
    private lateinit var floorVerticesBuffer: FloatBuffer
    private lateinit var floorColorsBuffer: FloatBuffer
    private lateinit var floorNormalsBuffer: FloatBuffer
    private var floorProgram: Int = 0
    private var floorPositionParam: Int = 0
    private var floorColorParam: Int = 0
    private var floorMVPMatrixParam: Int = 0
    private var floorNormalParam: Int = 0
    private var floorModelParam: Int = 0
    private var floorModelViewParam: Int = 0
    private var floorLightPosParam: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val cardboardView: CardboardView = findViewById(R.id.cardboard_view)
        cardboardView.setRenderer(this)
        setCardboardView(cardboardView)

        camera = FloatArray(16)
        view = FloatArray(16)
        modelViewProjection = FloatArray(16)

        floorTransform = FloatArray(16)
        floorView = FloatArray(16)
    }

    override fun onNewFrame(headTransform: HeadTransform?) {
        Matrix.setLookAtM(camera, 0, 0.0f, 0.0f, CAMERA_Z, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f)
    }

    override fun onDrawEye(eye: Eye) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        Matrix.multiplyMM(view, 0, eye.eyeView, 0, camera, 0)
        Matrix.multiplyMV(lightPosInEyeSpace, 0, view, 0, LIGHT_POS_IN_WORLD_SPACE, 0)

        val perspective: FloatArray? = eye.getPerspective(Z_NEAR, Z_FAR)

        Matrix.multiplyMM(floorView, 0, view, 0, floorTransform, 0)
        Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, floorView, 0)
        drawFloor()
    }

    override fun onFinishFrame(viewport: Viewport?) {}

    override fun onSurfaceChanged(i: Int, i1: Int) {}

    override fun onSurfaceCreated(eglConfig: EGLConfig?) {
        initializeScene()
        compileShaders()
        prepareRenderingFloor()
    }

    override fun onRendererShutdown() {}

    private fun drawFloor() {
        GLES20.glUseProgram(floorProgram)
        GLES20.glUniform3fv(floorLightPosParam, 1, lightPosInEyeSpace, 0)
        GLES20.glUniformMatrix4fv(floorModelParam, 1, false, floorTransform, 0)
        GLES20.glUniformMatrix4fv(floorModelViewParam, 1, false, floorView, 0)
        GLES20.glUniformMatrix4fv(floorMVPMatrixParam, 1, false, modelViewProjection, 0)
        GLES20.glVertexAttribPointer(
            floorPositionParam,
            COORDS_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            0,
            floorVerticesBuffer
        )
        GLES20.glVertexAttribPointer(
            floorNormalParam,
            3,
            GLES20.GL_FLOAT,
            false,
            0,
            floorNormalsBuffer
        )
        GLES20.glVertexAttribPointer(
            floorColorParam,
            4,
            GLES20.GL_FLOAT,
            false,
            0,
            floorColorsBuffer
        )
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, floorVertexCount)
    }

    private fun initializeScene() {
        Matrix.setIdentityM(floorTransform, 0)
        Matrix.translateM(floorTransform, 0, 0f, -floorDepth, 0f)
    }

    private fun compileShaders() {
        val lightVertexShader: Int = loadShader(GLES20.GL_VERTEX_SHADER, R.raw.light_vertex)
        val gridFragmentShader: Int = loadShader(GLES20.GL_FRAGMENT_SHADER, R.raw.grid_fragment)

        floorProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(floorProgram, lightVertexShader)
        GLES20.glAttachShader(floorProgram, gridFragmentShader)
        GLES20.glLinkProgram(floorProgram)
        GLES20.glUseProgram(floorProgram)
    }

    private fun prepareRenderingFloor() {
        val bb: ByteBuffer = ByteBuffer.allocateDirect(floorCoords!!.size * 4)
        bb.order(ByteOrder.nativeOrder())
        floorVerticesBuffer = bb.asFloatBuffer()
        floorVerticesBuffer.put(floorCoords)
        floorVerticesBuffer.position(0)

        val bbColors: ByteBuffer = ByteBuffer.allocateDirect(floorColors!!.size * 4)
        bbColors.order(ByteOrder.nativeOrder())
        floorColorsBuffer = bbColors.asFloatBuffer()
        floorColorsBuffer.put(floorColors)
        floorColorsBuffer.position(0)

        val bbNormals: ByteBuffer = ByteBuffer.allocateDirect(floorNormals!!.size * 4)
        bbNormals.order(ByteOrder.nativeOrder())
        floorNormalsBuffer = bbNormals.asFloatBuffer()
        floorNormalsBuffer.put(floorNormals)
        floorNormalsBuffer.position(0)

        floorPositionParam = GLES20.glGetAttribLocation(floorProgram, "a_Position")
        floorNormalParam = GLES20.glGetAttribLocation(floorProgram, "a_Normal")
        floorColorParam = GLES20.glGetAttribLocation(floorProgram, "a_Color")
        floorModelParam = GLES20.glGetUniformLocation(floorProgram, "u_Model")
        floorModelViewParam = GLES20.glGetUniformLocation(floorProgram, "u_MVMatrix")
        floorMVPMatrixParam = GLES20.glGetUniformLocation(floorProgram, "u_MVP")
        floorLightPosParam = GLES20.glGetUniformLocation(floorProgram, "u_LightPos")

        GLES20.glEnableVertexAttribArray(floorPositionParam)
        GLES20.glEnableVertexAttribArray(floorNormalParam)
        GLES20.glEnableVertexAttribArray(floorColorParam)
    }

    private fun loadShader(type: Int, resId: Int): Int {
        val code: String? = readRawTextFile(resId)
        val shader: Int = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, code)
        GLES20.glCompileShader(shader)
        return shader
    }

    private fun readRawTextFile(resId: Int): String? {
        val inputStream: InputStream = getResources().openRawResource(resId)
        try {
            BufferedReader(InputStreamReader(inputStream)).use({ reader ->
                val sb: StringBuilder = StringBuilder()
                var line: String?
                while ((reader.readLine().also({ line = it })) != null) {
                    sb.append(line).append("\n")
                }
                return sb.toString()
            })
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    companion object {
        private val TAG: String = "MainActivity"

        // Scene variables
        private val LIGHT_POS_IN_WORLD_SPACE: FloatArray = floatArrayOf(0.0f, 2.0f, 0.0f, 1.0f)
        private val COORDS_PER_VERTEX: Int = 3

        // Floor variables
        private val floorCoords: FloatArray? = Floor.FLOOR_COORDS
        private val floorColors: FloatArray? = Floor.FLOOR_COLORS
        private val floorNormals: FloatArray? = Floor.FLOOR_NORMALS

        // Viewing variables
        private val Z_NEAR: Float = 0.1f
        private val Z_FAR: Float = 100.0f
        private val CAMERA_Z: Float = 0.01f
    }
}