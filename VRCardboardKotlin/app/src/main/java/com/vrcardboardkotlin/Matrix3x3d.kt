package com.vrcardboardkotlin

class Matrix3x3d {
    var m: DoubleArray

    init {
        this.m = DoubleArray(9)
    }

    fun set(
        m00: Double,
        m01: Double,
        m02: Double,
        m10: Double,
        m11: Double,
        m12: Double,
        m20: Double,
        m21: Double,
        m22: Double
    ) {
        m[0] = m00
        m[1] = m01
        m[2] = m02
        m[3] = m10
        m[4] = m11
        m[5] = m12
        m[6] = m20
        m[7] = m21
        m[8] = m22
    }

    fun set(o: Matrix3x3d) {
        m[0] = o.m[0]
        m[1] = o.m[1]
        m[2] = o.m[2]
        m[3] = o.m[3]
        m[4] = o.m[4]
        m[5] = o.m[5]
        m[6] = o.m[6]
        m[7] = o.m[7]
        m[8] = o.m[8]
    }

    fun setZero() {
        for (i in m.indices) {
            m[i] = 0.0
        }
    }

    fun setIdentity() {
        setZero()
        m[0] = 1.0
        m[4] = 1.0
        m[8] = 1.0
    }

    fun setSameDiagonal(d: Double) {
        m[0] = d
        m[4] = d
        m[8] = d
    }

    fun get(row: Int, col: Int): Double {
        return m[3 * row + col]
    }

    fun set(row: Int, col: Int, value: Double) {
        m[3 * row + col] = value
    }

    fun setColumn(col: Int, v: Vector3d?) {
        m[col] = v!!.x
        m[col + 3] = v.y
        m[col + 6] = v.z
    }

    fun scale(s: Double) {
        for (i in m.indices) {
            m[i] *= s
        }
    }

    fun plusEquals(b: Matrix3x3d) {
        for (i in m.indices) {
            m[i] += b.m[i]
        }
    }

    fun minusEquals(b: Matrix3x3d) {
        for (i in m.indices) {
            m[i] -= b.m[i]
        }
    }

    fun transpose() {
        var tmp: Double = m[1]
        m[1] = m[3]
        m[3] = tmp
        tmp = m[2]
        m[2] = m[6]
        m[6] = tmp
        tmp = m[5]
        m[5] = m[7]
        m[7] = tmp
    }

    fun transpose(result: Matrix3x3d) {
        result.m[0] = m[0]
        result.m[1] = m[3]
        result.m[2] = m[6]
        result.m[3] = m[1]
        result.m[4] = m[4]
        result.m[5] = m[7]
        result.m[6] = m[2]
        result.m[7] = m[5]
        result.m[8] = m[8]
    }

    fun determinant(): Double {
        return (this.get(0, 0) * (this.get(1, 1) * this.get(2, 2) - this.get(2, 1) * this.get(1, 2))
                - this.get(0, 1) * (this.get(1, 0) * this.get(2, 2) - this.get(1, 2) * this.get(2, 0))
                + this.get(0, 2) * (this.get(1, 0) * this.get(2, 1) - this.get(1, 1) * this.get(2, 0)))
    }

    fun invert(result: Matrix3x3d): Boolean {
        val d: Double = this.determinant()
        if (d == 0.0) {
            return false
        }
        val invdet: Double = 1.0 / d
        result.set(
            (m[4] * m[8] - m[7] * m[5]) * invdet,
            -(m[1] * m[8] - m[2] * m[7]) * invdet,
            (m[1] * m[5] - m[2] * m[4]) * invdet,
            -(m[3] * m[8] - m[5] * m[6]) * invdet,
            (m[0] * m[8] - m[2] * m[6]) * invdet,
            -(m[0] * m[5] - m[3] * m[2]) * invdet,
            (m[3] * m[7] - m[6] * m[4]) * invdet,
            -(m[0] * m[7] - m[6] * m[1]) * invdet,
            (m[0] * m[4] - m[3] * m[1]) * invdet
        )
        return true
    }

    override fun toString(): String {
        return "{ ${m.joinToString(", ")} }"
    }

    companion object {
        fun add(a: Matrix3x3d, b: Matrix3x3d, result: Matrix3x3d) {
            for (i in 0..8) {
                result.m[i] = a.m[i] + b.m[i]
            }
        }

        fun mult(a: Matrix3x3d?, b: Matrix3x3d?, result: Matrix3x3d) {
            result.set(
                (a!!.m[0] * b!!.m[0]) + (a.m[1] * b.m[3]) + (a.m[2] * b.m[6]),
                (a.m[0] * b.m[1]) + (a.m[1] * b.m[4]) + (a.m[2] * b.m[7]),
                (a.m[0] * b.m[2]) + (a.m[1] * b.m[5]) + (a.m[2] * b.m[8]),
                (a.m[3] * b.m[0]) + (a.m[4] * b.m[3]) + (a.m[5] * b.m[6]),
                (a.m[3] * b.m[1]) + (a.m[4] * b.m[4]) + (a.m[5] * b.m[7]),
                (a.m[3] * b.m[2]) + (a.m[4] * b.m[5]) + (a.m[5] * b.m[8]),
                (a.m[6] * b.m[0]) + (a.m[7] * b.m[3]) + (a.m[8] * b.m[6]),
                (a.m[6] * b.m[1]) + (a.m[7] * b.m[4]) + (a.m[8] * b.m[7]),
                (a.m[6] * b.m[2]) + (a.m[7] * b.m[5]) + (a.m[8] * b.m[8])
            )
        }

        fun mult(a: Matrix3x3d, v: Vector3d, result: Vector3d) {
            result.x = (a.m[0] * v.x) + (a.m[1] * v.y) + (a.m[2] * v.z)
            result.y = (a.m[3] * v.x) + (a.m[4] * v.y) + (a.m[5] * v.z)
            result.z = (a.m[6] * v.x) + (a.m[7] * v.y) + (a.m[8] * v.z)
        }
    }
}
