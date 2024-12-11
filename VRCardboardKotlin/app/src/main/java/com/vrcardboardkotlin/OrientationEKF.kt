package com.vrcardboardkotlin

import kotlin.math.abs
import kotlin.math.min

class OrientationEKF() {
    private val rotationMatrix: DoubleArray
    private val so3SensorFromWorld: Matrix3x3d
    private val so3LastMotion: Matrix3x3d
    private val mP: Matrix3x3d
    private val mQ: Matrix3x3d
    private val mR: Matrix3x3d
    private val mRaccel: Matrix3x3d
    private val mS: Matrix3x3d
    private val mH: Matrix3x3d
    private val mK: Matrix3x3d
    private val mNu: Vector3d
    private val mz: Vector3d
    private val mh: Vector3d
    private val mu: Vector3d
    private val mx: Vector3d
    private val down: Vector3d
    private val north: Vector3d
    private var sensorTimeStampGyro: Long = 0
    private val lastGyro: Vector3d
    private var previousAccelNorm: Double
    private var movingAverageAccelNormChange: Double
    private var filteredGyroTimestep: Float = 0f
    private var timestepFilterInit: Boolean
    private var numGyroTimestepSamples: Int = 0
    private var gyroFilterValid: Boolean
    private val getPredictedGLMatrixTempM1: Matrix3x3d
    private val getPredictedGLMatrixTempM2: Matrix3x3d
    private val getPredictedGLMatrixTempV1: Vector3d
    private val setHeadingDegreesTempM1: Matrix3x3d
    private val processGyroTempM1: Matrix3x3d
    private val processGyroTempM2: Matrix3x3d
    private val processAccTempM1: Matrix3x3d
    private val processAccTempM2: Matrix3x3d
    private val processAccTempM3: Matrix3x3d
    private val processAccTempM4: Matrix3x3d
    private val processAccTempM5: Matrix3x3d
    private val processAccTempV1: Vector3d
    private val processAccTempV2: Vector3d
    private val processAccVDelta: Vector3d
    private val processMagTempV1: Vector3d
    private val processMagTempV2: Vector3d
    private val processMagTempV3: Vector3d
    private val processMagTempV4: Vector3d
    private val processMagTempV5: Vector3d
    private val processMagTempM1: Matrix3x3d
    private val processMagTempM2: Matrix3x3d
    private val processMagTempM4: Matrix3x3d
    private val processMagTempM5: Matrix3x3d
    private val processMagTempM6: Matrix3x3d
    private val updateCovariancesAfterMotionTempM1: Matrix3x3d
    private val updateCovariancesAfterMotionTempM2: Matrix3x3d
    private val accObservationFunctionForNumericalJacobianTempM: Matrix3x3d
    private val magObservationFunctionForNumericalJacobianTempM: Matrix3x3d
    private var alignedToGravity: Boolean = false
    private var alignedToNorth: Boolean = false

    init {
        this.rotationMatrix = DoubleArray(16)
        this.so3SensorFromWorld = Matrix3x3d()
        this.so3LastMotion = Matrix3x3d()
        this.mP = Matrix3x3d()
        this.mQ = Matrix3x3d()
        this.mR = Matrix3x3d()
        this.mRaccel = Matrix3x3d()
        this.mS = Matrix3x3d()
        this.mH = Matrix3x3d()
        this.mK = Matrix3x3d()
        this.mNu = Vector3d()
        this.mz = Vector3d()
        this.mh = Vector3d()
        this.mu = Vector3d()
        this.mx = Vector3d()
        this.down = Vector3d()
        this.north = Vector3d()
        this.lastGyro = Vector3d()
        this.previousAccelNorm = 0.0
        this.movingAverageAccelNormChange = 0.0
        this.timestepFilterInit = false
        this.gyroFilterValid = true
        this.getPredictedGLMatrixTempM1 = Matrix3x3d()
        this.getPredictedGLMatrixTempM2 = Matrix3x3d()
        this.getPredictedGLMatrixTempV1 = Vector3d()
        this.setHeadingDegreesTempM1 = Matrix3x3d()
        this.processGyroTempM1 = Matrix3x3d()
        this.processGyroTempM2 = Matrix3x3d()
        this.processAccTempM1 = Matrix3x3d()
        this.processAccTempM2 = Matrix3x3d()
        this.processAccTempM3 = Matrix3x3d()
        this.processAccTempM4 = Matrix3x3d()
        this.processAccTempM5 = Matrix3x3d()
        this.processAccTempV1 = Vector3d()
        this.processAccTempV2 = Vector3d()
        this.processAccVDelta = Vector3d()
        this.processMagTempV1 = Vector3d()
        this.processMagTempV2 = Vector3d()
        this.processMagTempV3 = Vector3d()
        this.processMagTempV4 = Vector3d()
        this.processMagTempV5 = Vector3d()
        this.processMagTempM1 = Matrix3x3d()
        this.processMagTempM2 = Matrix3x3d()
        this.processMagTempM4 = Matrix3x3d()
        this.processMagTempM5 = Matrix3x3d()
        this.processMagTempM6 = Matrix3x3d()
        this.updateCovariancesAfterMotionTempM1 = Matrix3x3d()
        this.updateCovariancesAfterMotionTempM2 = Matrix3x3d()
        this.accObservationFunctionForNumericalJacobianTempM = Matrix3x3d()
        this.magObservationFunctionForNumericalJacobianTempM = Matrix3x3d()
        this.reset()
    }

    fun reset() {
        this.sensorTimeStampGyro = 0L
        so3SensorFromWorld.setIdentity()
        so3LastMotion.setIdentity()
        mP.setZero()
        mP.setSameDiagonal(25.0)
        mQ.setZero()
        mQ.setSameDiagonal(1.0)
        mR.setZero()
        mR.setSameDiagonal(0.0625)
        mRaccel.setZero()
        mRaccel.setSameDiagonal(0.5625)
        mS.setZero()
        mH.setZero()
        mK.setZero()
        mNu.setZero()
        mz.setZero()
        mh.setZero()
        mu.setZero()
        mx.setZero()
        down.set(0.0, 0.0, 9.81)
        north.set(0.0, 1.0, 0.0)
        this.alignedToGravity = false
        this.alignedToNorth = false
    }

    fun getPredictedGLMatrix(secondsAfterLastGyroEvent: Double): DoubleArray {
        val pmu: Vector3d = this.getPredictedGLMatrixTempV1
        pmu.set(this.lastGyro)
        pmu.scale(-secondsAfterLastGyroEvent)
        val so3PredictedMotion: Matrix3x3d = this.getPredictedGLMatrixTempM1
        So3Util.sO3FromMu(pmu, so3PredictedMotion)
        val so3PredictedState: Matrix3x3d = this.getPredictedGLMatrixTempM2
        Matrix3x3d.Companion.mult(so3PredictedMotion, this.so3SensorFromWorld, so3PredictedState)
        return this.glMatrixFromSo3(so3PredictedState)
    }

    @Synchronized
    fun processGyro(gyro: Vector3d?, sensorTimeStamp: Long) {
        val kTimeThreshold: Float = 0.04f
        val kdTDefault: Float = 0.01f
        if (this.sensorTimeStampGyro != 0L) {
            var dT: Float = (sensorTimeStamp - this.sensorTimeStampGyro) * 1.0E-9f
            if (dT > kTimeThreshold) {
                dT = (if (this.gyroFilterValid) this.filteredGyroTimestep else kdTDefault)
            } else {
                this.filterGyroTimestep(dT)
            }
            mu.set(gyro)
            mu.scale(-dT.toDouble())
            So3Util.sO3FromMu(this.mu, this.so3LastMotion)
            Matrix3x3d.Companion.mult(
                this.so3LastMotion,
                this.so3SensorFromWorld,
                this.so3SensorFromWorld
            )
            this.updateCovariancesAfterMotion()
            processGyroTempM2.set(this.mQ)
            processGyroTempM2.scale((dT * dT).toDouble())
            mP.plusEquals(this.processGyroTempM2)
        }
        this.sensorTimeStampGyro = sensorTimeStamp
        lastGyro.set(gyro)
    }

    private fun updateAccelCovariance(currentAccelNorm: Double) {
        val currentAccelNormChange: Double = abs(currentAccelNorm - this.previousAccelNorm)
        this.previousAccelNorm = currentAccelNorm
        val kSmoothingFactor: Double = 0.5
        this.movingAverageAccelNormChange = (kSmoothingFactor * currentAccelNormChange
                + kSmoothingFactor * this.movingAverageAccelNormChange)
        val kMaxAccelNormChange: Double = 0.15
        val kMinAccelNoiseSigma: Double = 0.75
        val kMaxAccelNoiseSigma: Double = 7.0
        val normChangeRatio: Double = this.movingAverageAccelNormChange / kMaxAccelNormChange
        val accelNoiseSigma: Double = min(
            kMaxAccelNoiseSigma,
            kMinAccelNoiseSigma + normChangeRatio * (kMaxAccelNoiseSigma - kMinAccelNoiseSigma)
        )
        mRaccel.setSameDiagonal(accelNoiseSigma * accelNoiseSigma)
    }

    @Synchronized
    fun processAcc(acc: Vector3d?, sensorTimeStamp: Long) {
        mz.set(acc)
        this.updateAccelCovariance(mz.length())
        if (this.alignedToGravity) {
            this.accObservationFunctionForNumericalJacobian(this.so3SensorFromWorld, this.mNu)
            val eps: Double = 1.0E-7
            for (dof in 0..2) {
                val delta: Vector3d = this.processAccVDelta
                delta.setZero()
                delta.setComponent(dof, eps)
                So3Util.sO3FromMu(delta, this.processAccTempM1)
                Matrix3x3d.Companion.mult(
                    this.processAccTempM1,
                    this.so3SensorFromWorld,
                    this.processAccTempM2
                )
                this.accObservationFunctionForNumericalJacobian(
                    this.processAccTempM2,
                    this.processAccTempV1
                )
                val withDelta: Vector3d = this.processAccTempV1
                Vector3d.Companion.sub(this.mNu, withDelta, this.processAccTempV2)
                processAccTempV2.scale(1.0 / eps)
                mH.setColumn(dof, this.processAccTempV2)
            }
            mH.transpose(this.processAccTempM3)
            Matrix3x3d.Companion.mult(this.mP, this.processAccTempM3, this.processAccTempM4)
            Matrix3x3d.Companion.mult(this.mH, this.processAccTempM4, this.processAccTempM5)
            Matrix3x3d.Companion.add(this.processAccTempM5, this.mRaccel, this.mS)
            mS.invert(this.processAccTempM3)
            mH.transpose(this.processAccTempM4)
            Matrix3x3d.Companion.mult(
                this.processAccTempM4,
                this.processAccTempM3,
                this.processAccTempM5
            )
            Matrix3x3d.Companion.mult(this.mP, this.processAccTempM5, this.mK)
            Matrix3x3d.Companion.mult(this.mK, this.mNu, this.mx)
            Matrix3x3d.Companion.mult(this.mK, this.mH, this.processAccTempM3)
            processAccTempM4.setIdentity()
            processAccTempM4.minusEquals(this.processAccTempM3)
            Matrix3x3d.Companion.mult(this.processAccTempM4, this.mP, this.processAccTempM3)
            mP.set(this.processAccTempM3)
            So3Util.sO3FromMu(this.mx, this.so3LastMotion)
            Matrix3x3d.Companion.mult(
                this.so3LastMotion,
                this.so3SensorFromWorld,
                this.so3SensorFromWorld
            )
            this.updateCovariancesAfterMotion()
        } else {
            So3Util.sO3FromTwoVec(this.down, this.mz, this.so3SensorFromWorld)
            this.alignedToGravity = true
        }
    }

    private fun glMatrixFromSo3(so3: Matrix3x3d): DoubleArray {
        for (r in 0..2) {
            for (c in 0..2) {
                rotationMatrix[4 * c + r] = so3.get(r, c)
            }
        }

        rotationMatrix[3] = 0.0
        rotationMatrix[7] = 0.0
        rotationMatrix[11] = 0.0

        rotationMatrix[12] = 0.0
        rotationMatrix[13] = 0.0
        rotationMatrix[14] = 0.0
        rotationMatrix[15] = 1.0
        return this.rotationMatrix
    }

    private fun filterGyroTimestep(timeStep: Float) {
        val kFilterCoeff: Float = 0.95f
        val kMinSamples: Int = 10
        if (!this.timestepFilterInit) {
            this.filteredGyroTimestep = timeStep
            this.numGyroTimestepSamples = 1
            this.timestepFilterInit = true
        } else {
            this.filteredGyroTimestep =
                kFilterCoeff * this.filteredGyroTimestep + (1 - kFilterCoeff) * timeStep
            if (++this.numGyroTimestepSamples > kMinSamples) {
                this.gyroFilterValid = true
            }
        }
    }

    private fun updateCovariancesAfterMotion() {
        so3LastMotion.transpose(this.updateCovariancesAfterMotionTempM1)
        Matrix3x3d.Companion.mult(
            this.mP,
            this.updateCovariancesAfterMotionTempM1,
            this.updateCovariancesAfterMotionTempM2
        )
        Matrix3x3d.Companion.mult(
            this.so3LastMotion,
            this.updateCovariancesAfterMotionTempM2,
            this.mP
        )
        so3LastMotion.setIdentity()
    }

    private fun accObservationFunctionForNumericalJacobian(
        so3SensorFromWorldPred: Matrix3x3d,
        result: Vector3d
    ) {
        Matrix3x3d.Companion.mult(so3SensorFromWorldPred, this.down, this.mh)
        So3Util.sO3FromTwoVec(
            this.mh,
            this.mz,
            this.accObservationFunctionForNumericalJacobianTempM
        )
        So3Util.muFromSO3(this.accObservationFunctionForNumericalJacobianTempM, result)
    }
}
