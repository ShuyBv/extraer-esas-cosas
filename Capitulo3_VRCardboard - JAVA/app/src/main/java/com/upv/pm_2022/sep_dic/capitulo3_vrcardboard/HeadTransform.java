package com.upv.pm_2022.sep_dic.capitulo3_vrcardboard;

import android.opengl.*;

public class HeadTransform {
    private final float[] mHeadView;
    
    public HeadTransform() {
        super();
        Matrix.setIdentityM(this.mHeadView = new float[16], 0);
    }
    
    float[] getHeadView() {
        return this.mHeadView;
    }
}
