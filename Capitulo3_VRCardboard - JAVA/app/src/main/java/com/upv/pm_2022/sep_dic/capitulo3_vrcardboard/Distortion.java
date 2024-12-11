package com.upv.pm_2022.sep_dic.capitulo3_vrcardboard;

import java.util.*;

public class Distortion {
    private static final float[] DEFAULT_COEFFICIENTS;
    private float[] mCoefficients;
    
    public Distortion() {
        super();
        this.mCoefficients = Distortion.DEFAULT_COEFFICIENTS.clone();
    }
    
    public Distortion(final Distortion other) {
        super();
        this.setCoefficients(other.mCoefficients);
    }
    
    public static Distortion parseFromProtobuf(final float[] coefficients) {
        final Distortion distortion = new Distortion();
        distortion.setCoefficients(coefficients);
        return distortion;
    }
    
    public float[] toProtobuf() {
        return this.mCoefficients.clone();
    }
    
    public void setCoefficients(final float[] coefficients) {
        this.mCoefficients = ((coefficients != null) ? coefficients.clone() : new float[0]);
    }
    
    public float distortionFactor(final float radius) {
        float result = 1.0f;
        float rFactor = 1.0f;
        final float rSquared = radius * radius;
        for (final float ki : this.mCoefficients) {
            rFactor *= rSquared;
            result += ki * rFactor;
        }
        return result;
    }
    
    public float distort(final float radius) {
        return radius * this.distortionFactor(radius);
    }
    
    public float distortInverse(final float radius) {
        float r0 = radius / 0.9f;
        float r = radius * 0.9f;
        float dr0 = radius - this.distort(r0);
        while (Math.abs(r - r0) > 1.0E-4) {
            final float dr = radius - this.distort(r);
            final float r2 = r - dr * ((r - r0) / (dr - dr0));
            r0 = r;
            r = r2;
            dr0 = dr;
        }
        return r;
    }
    
    @Override
    public boolean equals(final Object other) {
        if (other == null) {
            return false;
        }
        if (other == this) {
            return true;
        }
        if (!(other instanceof Distortion)) {
            return false;
        }
        final Distortion o = (Distortion)other;
        return Arrays.equals(this.mCoefficients, o.mCoefficients);
    }
    
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder().append("{\n").append("  coefficients: [");
        for (int i = 0; i < this.mCoefficients.length; ++i) {
            builder.append((this.mCoefficients[i]));
            if (i < this.mCoefficients.length - 1) {
                builder.append(", ");
            }
        }
        builder.append("],\n}");
        return builder.toString();
    }
    
    static {
        DEFAULT_COEFFICIENTS = new float[] { 0.441f, 0.156f };
    }
}
