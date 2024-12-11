package com.upv.pm_2022.sep_dic.capitulo3_vrcardboard;

public class OrientationEKF {
    private double[] rotationMatrix;
    private Matrix3x3d so3SensorFromWorld;
    private Matrix3x3d so3LastMotion;
    private Matrix3x3d mP;
    private Matrix3x3d mQ;
    private Matrix3x3d mR;
    private Matrix3x3d mRaccel;
    private Matrix3x3d mS;
    private Matrix3x3d mH;
    private Matrix3x3d mK;
    private Vector3d mNu;
    private Vector3d mz;
    private Vector3d mh;
    private Vector3d mu;
    private Vector3d mx;
    private Vector3d down;
    private Vector3d north;
    private long sensorTimeStampGyro;
    private final Vector3d lastGyro;
    private double previousAccelNorm;
    private double movingAverageAccelNormChange;
    private float filteredGyroTimestep;
    private boolean timestepFilterInit;
    private int numGyroTimestepSamples;
    private boolean gyroFilterValid;
    private Matrix3x3d getPredictedGLMatrixTempM1;
    private Matrix3x3d getPredictedGLMatrixTempM2;
    private Vector3d getPredictedGLMatrixTempV1;
    private Matrix3x3d setHeadingDegreesTempM1;
    private Matrix3x3d processGyroTempM1;
    private Matrix3x3d processGyroTempM2;
    private Matrix3x3d processAccTempM1;
    private Matrix3x3d processAccTempM2;
    private Matrix3x3d processAccTempM3;
    private Matrix3x3d processAccTempM4;
    private Matrix3x3d processAccTempM5;
    private Vector3d processAccTempV1;
    private Vector3d processAccTempV2;
    private Vector3d processAccVDelta;
    private Vector3d processMagTempV1;
    private Vector3d processMagTempV2;
    private Vector3d processMagTempV3;
    private Vector3d processMagTempV4;
    private Vector3d processMagTempV5;
    private Matrix3x3d processMagTempM1;
    private Matrix3x3d processMagTempM2;
    private Matrix3x3d processMagTempM4;
    private Matrix3x3d processMagTempM5;
    private Matrix3x3d processMagTempM6;
    private Matrix3x3d updateCovariancesAfterMotionTempM1;
    private Matrix3x3d updateCovariancesAfterMotionTempM2;
    private Matrix3x3d accObservationFunctionForNumericalJacobianTempM;
    private Matrix3x3d magObservationFunctionForNumericalJacobianTempM;
    private boolean alignedToGravity;
    private boolean alignedToNorth;
    
    public OrientationEKF() {
        super();
        this.rotationMatrix = new double[16];
        this.so3SensorFromWorld = new Matrix3x3d();
        this.so3LastMotion = new Matrix3x3d();
        this.mP = new Matrix3x3d();
        this.mQ = new Matrix3x3d();
        this.mR = new Matrix3x3d();
        this.mRaccel = new Matrix3x3d();
        this.mS = new Matrix3x3d();
        this.mH = new Matrix3x3d();
        this.mK = new Matrix3x3d();
        this.mNu = new Vector3d();
        this.mz = new Vector3d();
        this.mh = new Vector3d();
        this.mu = new Vector3d();
        this.mx = new Vector3d();
        this.down = new Vector3d();
        this.north = new Vector3d();
        this.lastGyro = new Vector3d();
        this.previousAccelNorm = 0.0;
        this.movingAverageAccelNormChange = 0.0;
        this.timestepFilterInit = false;
        this.gyroFilterValid = true;
        this.getPredictedGLMatrixTempM1 = new Matrix3x3d();
        this.getPredictedGLMatrixTempM2 = new Matrix3x3d();
        this.getPredictedGLMatrixTempV1 = new Vector3d();
        this.setHeadingDegreesTempM1 = new Matrix3x3d();
        this.processGyroTempM1 = new Matrix3x3d();
        this.processGyroTempM2 = new Matrix3x3d();
        this.processAccTempM1 = new Matrix3x3d();
        this.processAccTempM2 = new Matrix3x3d();
        this.processAccTempM3 = new Matrix3x3d();
        this.processAccTempM4 = new Matrix3x3d();
        this.processAccTempM5 = new Matrix3x3d();
        this.processAccTempV1 = new Vector3d();
        this.processAccTempV2 = new Vector3d();
        this.processAccVDelta = new Vector3d();
        this.processMagTempV1 = new Vector3d();
        this.processMagTempV2 = new Vector3d();
        this.processMagTempV3 = new Vector3d();
        this.processMagTempV4 = new Vector3d();
        this.processMagTempV5 = new Vector3d();
        this.processMagTempM1 = new Matrix3x3d();
        this.processMagTempM2 = new Matrix3x3d();
        this.processMagTempM4 = new Matrix3x3d();
        this.processMagTempM5 = new Matrix3x3d();
        this.processMagTempM6 = new Matrix3x3d();
        this.updateCovariancesAfterMotionTempM1 = new Matrix3x3d();
        this.updateCovariancesAfterMotionTempM2 = new Matrix3x3d();
        this.accObservationFunctionForNumericalJacobianTempM = new Matrix3x3d();
        this.magObservationFunctionForNumericalJacobianTempM = new Matrix3x3d();
        this.reset();
    }
    
    public void reset() {
        this.sensorTimeStampGyro = 0L;
        this.so3SensorFromWorld.setIdentity();
        this.so3LastMotion.setIdentity();
        this.mP.setZero();
        this.mP.setSameDiagonal(25.0);
        this.mQ.setZero();
        this.mQ.setSameDiagonal(1.0);
        this.mR.setZero();
        this.mR.setSameDiagonal(0.0625);
        this.mRaccel.setZero();
        this.mRaccel.setSameDiagonal(0.5625);
        this.mS.setZero();
        this.mH.setZero();
        this.mK.setZero();
        this.mNu.setZero();
        this.mz.setZero();
        this.mh.setZero();
        this.mu.setZero();
        this.mx.setZero();
        this.down.set(0.0, 0.0, 9.81);
        this.north.set(0.0, 1.0, 0.0);
        this.alignedToGravity = false;
        this.alignedToNorth = false;
    }

    public double[] getPredictedGLMatrix(final double secondsAfterLastGyroEvent) {
        final Vector3d pmu = this.getPredictedGLMatrixTempV1;
        pmu.set(this.lastGyro);
        pmu.scale(-secondsAfterLastGyroEvent);
        final Matrix3x3d so3PredictedMotion = this.getPredictedGLMatrixTempM1;
        So3Util.sO3FromMu(pmu, so3PredictedMotion);
        final Matrix3x3d so3PredictedState = this.getPredictedGLMatrixTempM2;
        Matrix3x3d.mult(so3PredictedMotion, this.so3SensorFromWorld, so3PredictedState);
        return this.glMatrixFromSo3(so3PredictedState);
    }

    public synchronized void processGyro(final Vector3d gyro, final long sensorTimeStamp) {
        final float kTimeThreshold = 0.04f;
        final float kdTDefault = 0.01f;
        if (this.sensorTimeStampGyro != 0L) {
            float dT = (sensorTimeStamp - this.sensorTimeStampGyro) * 1.0E-9f;
            if (dT > kTimeThreshold) {
                dT = (this.gyroFilterValid ? this.filteredGyroTimestep : kdTDefault );
            }
            else {
                this.filterGyroTimestep(dT);
            }
            this.mu.set(gyro);
            this.mu.scale(-dT);
            So3Util.sO3FromMu(this.mu, this.so3LastMotion);
            Matrix3x3d.mult(this.so3LastMotion, this.so3SensorFromWorld, this.so3SensorFromWorld);
            this.updateCovariancesAfterMotion();
            this.processGyroTempM2.set(this.mQ);
            this.processGyroTempM2.scale(dT * dT);
            this.mP.plusEquals(this.processGyroTempM2);
        }
        this.sensorTimeStampGyro = sensorTimeStamp;
        this.lastGyro.set(gyro);
    }
    
    private void updateAccelCovariance(final double currentAccelNorm) {
        final double currentAccelNormChange = Math.abs(currentAccelNorm - this.previousAccelNorm);
        this.previousAccelNorm = currentAccelNorm;
        final double kSmoothingFactor = 0.5;
        this.movingAverageAccelNormChange = kSmoothingFactor * currentAccelNormChange
                + kSmoothingFactor * this.movingAverageAccelNormChange;
        final double kMaxAccelNormChange = 0.15;
        final double kMinAccelNoiseSigma = 0.75;
        final double kMaxAccelNoiseSigma = 7.0;
        final double normChangeRatio = this.movingAverageAccelNormChange / kMaxAccelNormChange;
        final double accelNoiseSigma = Math.min(kMaxAccelNoiseSigma,
                kMinAccelNoiseSigma + normChangeRatio * (kMaxAccelNoiseSigma - kMinAccelNoiseSigma));
        this.mRaccel.setSameDiagonal(accelNoiseSigma * accelNoiseSigma);
    }
    
    public synchronized void processAcc(final Vector3d acc, final long sensorTimeStamp) {
        this.mz.set(acc);
        this.updateAccelCovariance(this.mz.length());
        if (this.alignedToGravity) {
            this.accObservationFunctionForNumericalJacobian(this.so3SensorFromWorld, this.mNu);
            final double eps = 1.0E-7;
            for (int dof = 0; dof < 3; ++dof) {
                final Vector3d delta = this.processAccVDelta;
                delta.setZero();
                delta.setComponent(dof, eps);
                So3Util.sO3FromMu(delta, this.processAccTempM1);
                Matrix3x3d.mult(this.processAccTempM1, this.so3SensorFromWorld, this.processAccTempM2);
                this.accObservationFunctionForNumericalJacobian(this.processAccTempM2, this.processAccTempV1);
                final Vector3d withDelta = this.processAccTempV1;
                Vector3d.sub(this.mNu, withDelta, this.processAccTempV2);
                this.processAccTempV2.scale(1.0 / eps);
                this.mH.setColumn(dof, this.processAccTempV2);
            }
            this.mH.transpose(this.processAccTempM3);
            Matrix3x3d.mult(this.mP, this.processAccTempM3, this.processAccTempM4);
            Matrix3x3d.mult(this.mH, this.processAccTempM4, this.processAccTempM5);
            Matrix3x3d.add(this.processAccTempM5, this.mRaccel, this.mS);
            this.mS.invert(this.processAccTempM3);
            this.mH.transpose(this.processAccTempM4);
            Matrix3x3d.mult(this.processAccTempM4, this.processAccTempM3, this.processAccTempM5);
            Matrix3x3d.mult(this.mP, this.processAccTempM5, this.mK);
            Matrix3x3d.mult(this.mK, this.mNu, this.mx);
            Matrix3x3d.mult(this.mK, this.mH, this.processAccTempM3);
            this.processAccTempM4.setIdentity();
            this.processAccTempM4.minusEquals(this.processAccTempM3);
            Matrix3x3d.mult(this.processAccTempM4, this.mP, this.processAccTempM3);
            this.mP.set(this.processAccTempM3);
            So3Util.sO3FromMu(this.mx, this.so3LastMotion);
            Matrix3x3d.mult(this.so3LastMotion, this.so3SensorFromWorld, this.so3SensorFromWorld);
            this.updateCovariancesAfterMotion();
        }
        else {
            So3Util.sO3FromTwoVec(this.down, this.mz, this.so3SensorFromWorld);
            this.alignedToGravity = true;
        }
    }

    private double[] glMatrixFromSo3(final Matrix3x3d so3) {
        for (int r = 0; r < 3; ++r) {
            for (int c = 0; c < 3; ++c) {
                this.rotationMatrix[4 * c + r] = so3.get(r, c);
            }
        }

        this.rotationMatrix[3] = 0.0;
        this.rotationMatrix[7] = 0.0;
        this.rotationMatrix[11] = 0.0;

        this.rotationMatrix[12] = 0.0;
        this.rotationMatrix[13] = 0.0;
        this.rotationMatrix[14] = 0.0;
        this.rotationMatrix[15] = 1.0;
        return this.rotationMatrix;
    }
    
    private void filterGyroTimestep(final float timeStep) {
        final float kFilterCoeff = 0.95f;
        final int kMinSamples = 10;
        if (!this.timestepFilterInit) {
            this.filteredGyroTimestep = timeStep;
            this.numGyroTimestepSamples = 1;
            this.timestepFilterInit = true;
        }
        else {
            this.filteredGyroTimestep = kFilterCoeff * this.filteredGyroTimestep + (1 - kFilterCoeff) * timeStep;
            if (++this.numGyroTimestepSamples > kMinSamples) {
                this.gyroFilterValid = true;
            }
        }
    }
    
    private void updateCovariancesAfterMotion() {
        this.so3LastMotion.transpose(this.updateCovariancesAfterMotionTempM1);
        Matrix3x3d.mult(this.mP, this.updateCovariancesAfterMotionTempM1, this.updateCovariancesAfterMotionTempM2);
        Matrix3x3d.mult(this.so3LastMotion, this.updateCovariancesAfterMotionTempM2, this.mP);
        this.so3LastMotion.setIdentity();
    }
    
    private void accObservationFunctionForNumericalJacobian(final Matrix3x3d so3SensorFromWorldPred, final Vector3d result) {
        Matrix3x3d.mult(so3SensorFromWorldPred, this.down, this.mh);
        So3Util.sO3FromTwoVec(this.mh, this.mz, this.accObservationFunctionForNumericalJacobianTempM);
        So3Util.muFromSO3(this.accObservationFunctionForNumericalJacobianTempM, result);
    }
}
