package com.vrcardboardkotlin

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object So3Util {
    private var temp31: Vector3d? = null
    private var sO3FromTwoVecN: Vector3d? = null
    private var sO3FromTwoVecA: Vector3d? = null
    private var sO3FromTwoVecB: Vector3d? = null
    private var sO3FromTwoVecRotationAxis: Vector3d? = null
    private var sO3FromTwoVec33R1: Matrix3x3d? = null
    private var sO3FromTwoVec33R2: Matrix3x3d? = null
    private var muFromSO3R2: Vector3d? = null
    private var rotationPiAboutAxisTemp: Vector3d? = null

    fun sO3FromTwoVec(a: Vector3d, b: Vector3d, result: Matrix3x3d) {
        Vector3d.Companion.cross(a, b, sO3FromTwoVecN)
        if (sO3FromTwoVecN!!.length() == 0.0) {
            val dot: Double = Vector3d.Companion.dot(a, b)
            if (dot >= 0.0) {
                result.setIdentity()
            } else {
                Vector3d.Companion.ortho(a, sO3FromTwoVecRotationAxis)
                rotationPiAboutAxis(sO3FromTwoVecRotationAxis, result)
            }
            return
        }
        sO3FromTwoVecA!!.set(a)
        sO3FromTwoVecB!!.set(b)
        sO3FromTwoVecN!!.normalize()
        sO3FromTwoVecA!!.normalize()
        sO3FromTwoVecB!!.normalize()
        val r1: Matrix3x3d? = sO3FromTwoVec33R1
        r1!!.setColumn(0, sO3FromTwoVecA)
        r1.setColumn(1, sO3FromTwoVecN)
        Vector3d.Companion.cross(sO3FromTwoVecN, sO3FromTwoVecA, temp31)
        r1.setColumn(2, temp31)
        val r2: Matrix3x3d? = sO3FromTwoVec33R2
        r2!!.setColumn(0, sO3FromTwoVecB)
        r2.setColumn(1, sO3FromTwoVecN)
        Vector3d.Companion.cross(sO3FromTwoVecN, sO3FromTwoVecB, temp31)
        r2.setColumn(2, temp31)
        r1.transpose()
        Matrix3x3d.Companion.mult(r2, r1, result)
    }

    private fun rotationPiAboutAxis(v: Vector3d?, result: Matrix3x3d) {
        rotationPiAboutAxisTemp!!.set(v)
        rotationPiAboutAxisTemp!!.scale(3.141592653589793 / rotationPiAboutAxisTemp!!.length())
        val invTheta: Double = 0.3183098861837907
        val kA: Double = 0.0
        val kB: Double = 0.20264236728467558
        rodriguesSo3Exp(rotationPiAboutAxisTemp, kA, kB, result)
    }

    fun sO3FromMu(w: Vector3d, result: Matrix3x3d) {
        val thetaSq: Double = Vector3d.Companion.dot(w, w)
        val theta: Double = sqrt(thetaSq)
        val kA: Double
        val kB: Double
        if (thetaSq < 1.0E-8) {
            kA = 1.0 - 0.1666666716337204 * thetaSq
            kB = 0.5
        } else if (thetaSq < 1.0E-6) {
            kB = 0.5 - 0.0416666679084301 * thetaSq
            kA = 1.0 - thetaSq * 0.1666666716337204 * (1.0 - 0.1666666716337204 * thetaSq)
        } else {
            val invTheta: Double = 1.0 / theta
            kA = sin(theta) * invTheta
            kB = (1.0 - cos(theta)) * (invTheta * invTheta)
        }
        rodriguesSo3Exp(w, kA, kB, result)
    }

    fun muFromSO3(so3: Matrix3x3d, result: Vector3d) {
        val cosAngle: Double = (so3.get(0, 0) + so3.get(1, 1) + so3.get(2, 2) - 1.0) * 0.5
        result.set(
            (so3.get(2, 1) - so3.get(1, 2)) / 2.0,
            (so3.get(0, 2) - so3.get(2, 0)) / 2.0,
            (so3.get(1, 0) - so3.get(0, 1)) / 2.0
        )
        val sinAngleAbs: Double = result.length()
        if (cosAngle > 0.7071067811865476) {
            if (sinAngleAbs > 0.0) {
                result.scale(asin(sinAngleAbs) / sinAngleAbs)
            }
        } else if (cosAngle > -0.7071067811865476) {
            val angle: Double = acos(cosAngle)
            result.scale(angle / sinAngleAbs)
        } else {
            val angle: Double = 3.141592653589793 - asin(sinAngleAbs)
            val d0: Double = so3.get(0, 0) - cosAngle
            val d: Double = so3.get(1, 1) - cosAngle
            val d2: Double = so3.get(2, 2) - cosAngle
            val r2: Vector3d? = muFromSO3R2
            if (d0 * d0 > d * d && d0 * d0 > d2 * d2) {
                r2!!.set(
                    d0,
                    (so3.get(1, 0) + so3.get(0, 1)) / 2.0,
                    (so3.get(0, 2) + so3.get(2, 0)) / 2.0
                )
            } else if (d * d > d2 * d2) {
                r2!!.set(
                    (so3.get(1, 0) + so3.get(0, 1)) / 2.0,
                    d,
                    (so3.get(2, 1) + so3.get(1, 2)) / 2.0
                )
            } else {
                r2!!.set(
                    (so3.get(0, 2) + so3.get(2, 0)) / 2.0,
                    (so3.get(2, 1) + so3.get(1, 2)) / 2.0,
                    d2
                )
            }
            if (Vector3d.Companion.dot(r2, result) < 0.0) {
                r2.scale(-1.0)
            }
            r2.normalize()
            r2.scale(angle)
            result.set(r2)
        }
    }

    private fun rodriguesSo3Exp(w: Vector3d?, kA: Double, kB: Double, result: Matrix3x3d) {
        val wx2: Double = w!!.x * w.x
        val wy2: Double = w.y * w.y
        val wz2: Double = w.z * w.z
        result.set(0, 0, 1.0 - kB * (wy2 + wz2))
        result.set(1, 1, 1.0 - kB * (wx2 + wz2))
        result.set(2, 2, 1.0 - kB * (wx2 + wy2))
        var a: Double = kA * w.z
        var b: Double = kB * (w.x * w.y)
        result.set(0, 1, b - a)
        result.set(1, 0, b + a)
        a = kA * w.y
        b = kB * (w.x * w.z)
        result.set(0, 2, b + a)
        result.set(2, 0, b - a)
        a = kA * w.x
        b = kB * (w.y * w.z)
        result.set(1, 2, b - a)
        result.set(2, 1, b + a)
    }

    init {
        temp31 = Vector3d()
        sO3FromTwoVecN = Vector3d()
        sO3FromTwoVecA = Vector3d()
        sO3FromTwoVecB = Vector3d()
        sO3FromTwoVecRotationAxis = Vector3d()
        sO3FromTwoVec33R1 = Matrix3x3d()
        sO3FromTwoVec33R2 = Matrix3x3d()
        muFromSO3R2 = Vector3d()
        rotationPiAboutAxisTemp = Vector3d()
    }
}
