package com.upv.pm_2022.sep_dic.capitulo3_vrcardboard;

import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;

public class MainActivity extends CardboardActivity implements CardboardView.StereoRenderer {
    private static final String TAG = "MainActivity";

    // Scene variables
    private static final float[] LIGHT_POS_IN_WORLD_SPACE = new float[]{0.0f, 2.0f, 0.0f, 1.0f};
    private final float[] lightPosInEyeSpace = new float[4];

    private static final int COORDS_PER_VERTEX = 3;

    // Floor variables
    private static float floorCoords[] = Floor.FLOOR_COORDS;
    private static float floorColors[] = Floor.FLOOR_COLORS;
    private static float floorNormals[] = Floor.FLOOR_NORMALS;
    private final int floorVertexCount = floorCoords.length / COORDS_PER_VERTEX;
    private float[] floorTransform;
    private float floorDepth = 20f;

    // Viewing variables
    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 100.0f;
    private static final float CAMERA_Z = 0.01f;

    private float[] camera;
    private float[] view;
    private float[] modelViewProjection;
    private float[] floorView;

    // Rendering variables
    private FloatBuffer floorVerticesBuffer;
    private FloatBuffer floorColorsBuffer;
    private FloatBuffer floorNormalsBuffer;
    private int floorProgram;
    private int floorPositionParam;
    private int floorColorParam;
    private int floorMVPMatrixParam;
    private int floorNormalParam;
    private int floorModelParam;
    private int floorModelViewParam;
    private int floorLightPosParam;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        CardboardView cardboardView = findViewById(R.id.cardboard_view);
        cardboardView.setRenderer(this);
        setCardboardView(cardboardView);

        camera = new float[16];
        view = new float[16];
        modelViewProjection = new float[16];

        floorTransform = new float[16];
        floorView = new float[16];
    }

    @Override
    public void onNewFrame(HeadTransform headTransform) {
        Matrix.setLookAtM(camera, 0, 0.0f, 0.0f, CAMERA_Z, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);
    }

    @Override
    public void onDrawEye(Eye eye) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        Matrix.multiplyMM(view, 0, eye.getEyeView(), 0, camera, 0);
        Matrix.multiplyMV(lightPosInEyeSpace, 0, view, 0, LIGHT_POS_IN_WORLD_SPACE, 0);

        float[] perspective = eye.getPerspective(Z_NEAR, Z_FAR);

        Matrix.multiplyMM(floorView, 0, view, 0, floorTransform, 0);
        Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, floorView, 0);
        drawFloor();
    }

    @Override
    public void onFinishFrame(Viewport viewport) {}

    @Override
    public void onSurfaceChanged(int i, int i1) {}

    @Override
    public void onSurfaceCreated(EGLConfig eglConfig) {
        initializeScene();
        compileShaders();
        prepareRenderingFloor();
    }

    @Override
    public void onRendererShutdown() {}

    private void drawFloor() {
        GLES20.glUseProgram(floorProgram);
        GLES20.glUniform3fv(floorLightPosParam, 1, lightPosInEyeSpace, 0);
        GLES20.glUniformMatrix4fv(floorModelParam, 1, false, floorTransform, 0);
        GLES20.glUniformMatrix4fv(floorModelViewParam, 1, false, floorView, 0);
        GLES20.glUniformMatrix4fv(floorMVPMatrixParam, 1, false, modelViewProjection, 0);
        GLES20.glVertexAttribPointer(floorPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, floorVerticesBuffer);
        GLES20.glVertexAttribPointer(floorNormalParam, 3, GLES20.GL_FLOAT, false, 0, floorNormalsBuffer);
        GLES20.glVertexAttribPointer(floorColorParam, 4, GLES20.GL_FLOAT, false, 0, floorColorsBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, floorVertexCount);
    }

    private void initializeScene() {
        Matrix.setIdentityM(floorTransform, 0);
        Matrix.translateM(floorTransform, 0, 0, -floorDepth, 0);
    }

    private void compileShaders() {
        int lightVertexShader = loadShader(GLES20.GL_VERTEX_SHADER, R.raw.light_vertex);
        int gridFragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, R.raw.grid_fragment);

        floorProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(floorProgram, lightVertexShader);
        GLES20.glAttachShader(floorProgram, gridFragmentShader);
        GLES20.glLinkProgram(floorProgram);
        GLES20.glUseProgram(floorProgram);
    }

    private void prepareRenderingFloor() {
        ByteBuffer bb = ByteBuffer.allocateDirect(floorCoords.length * 4);
        bb.order(ByteOrder.nativeOrder());
        floorVerticesBuffer = bb.asFloatBuffer();
        floorVerticesBuffer.put(floorCoords);
        floorVerticesBuffer.position(0);

        ByteBuffer bbColors = ByteBuffer.allocateDirect(floorColors.length * 4);
        bbColors.order(ByteOrder.nativeOrder());
        floorColorsBuffer = bbColors.asFloatBuffer();
        floorColorsBuffer.put(floorColors);
        floorColorsBuffer.position(0);

        ByteBuffer bbNormals = ByteBuffer.allocateDirect(floorNormals.length * 4);
        bbNormals.order(ByteOrder.nativeOrder());
        floorNormalsBuffer = bbNormals.asFloatBuffer();
        floorNormalsBuffer.put(floorNormals);
        floorNormalsBuffer.position(0);

        floorPositionParam = GLES20.glGetAttribLocation(floorProgram, "a_Position");
        floorNormalParam = GLES20.glGetAttribLocation(floorProgram, "a_Normal");
        floorColorParam = GLES20.glGetAttribLocation(floorProgram, "a_Color");
        floorModelParam = GLES20.glGetUniformLocation(floorProgram, "u_Model");
        floorModelViewParam = GLES20.glGetUniformLocation(floorProgram, "u_MVMatrix");
        floorMVPMatrixParam = GLES20.glGetUniformLocation(floorProgram, "u_MVP");
        floorLightPosParam = GLES20.glGetUniformLocation(floorProgram, "u_LightPos");

        GLES20.glEnableVertexAttribArray(floorPositionParam);
        GLES20.glEnableVertexAttribArray(floorNormalParam);
        GLES20.glEnableVertexAttribArray(floorColorParam);
    }

    private int loadShader(int type, int resId) {
        String code = readRawTextFile(resId);
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, code);
        GLES20.glCompileShader(shader);
        return shader;
    }

    private String readRawTextFile(int resId) {
        InputStream inputStream = getResources().openRawResource(resId);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}