package com.upv.pm_2022.sep_dic.capitulo3_vrcardboard;

public class Eye {
    private final float[] mEyeView;
    private final Viewport mViewport;
    private final FieldOfView mFov;
    private volatile boolean mProjectionChanged;
    private float[] mPerspective;
    private float mLastZNear;
    private float mLastZFar;
    
    public Eye(final int type) {
        super();
        this.mEyeView = new float[16];
        this.mViewport = new Viewport();
        this.mFov = new FieldOfView();
        this.mProjectionChanged = true;
    }
    
    public float[] getEyeView() {
        return this.mEyeView;
    }
    
    public float[] getPerspective(final float zNear, final float zFar) {
        if (!this.mProjectionChanged && this.mLastZNear == zNear && this.mLastZFar == zFar) {
            return this.mPerspective;
        }
        if (this.mPerspective == null) {
            this.mPerspective = new float[16];
        }
        this.getFov().toPerspectiveMatrix(zNear, zFar, this.mPerspective, 0);
        this.mLastZNear = zNear;
        this.mLastZFar = zFar;
        this.mProjectionChanged = false;
        return this.mPerspective;
    }
    
    public Viewport getViewport() {
        return this.mViewport;
    }
    
    public FieldOfView getFov() {
        return this.mFov;
    }
    
    public void setProjectionChanged() {
        this.mProjectionChanged = true;
    }
}
