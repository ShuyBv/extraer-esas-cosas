package com.vrcardboardkotlin

/**
 * Created by Schoen and Jonathan on 4/13/2016.
 */
object Floor {
    val FLOOR_COORDS: FloatArray = floatArrayOf(
        200f, 0f, -200f,
        -200f, 0f, -200f,
        -200f, 0f, 200f,
        200f, 0f, -200f,
        -200f, 0f, 200f,
        200f, 0f, 200f,
    )

    val FLOOR_NORMALS: FloatArray = floatArrayOf(
        0.0f, 1.0f, 0.0f,
        0.0f, 1.0f, 0.0f,
        0.0f, 1.0f, 0.0f,
        0.0f, 1.0f, 0.0f,
        0.0f, 1.0f, 0.0f,
        0.0f, 1.0f, 0.0f,
    )

    val FLOOR_COLORS: FloatArray = floatArrayOf(
        0.0f, 0.34f, 0.90f, 1.0f,
        0.0f, 0.34f, 0.90f, 1.0f,
        0.0f, 0.34f, 0.90f, 1.0f,
        0.0f, 0.34f, 0.90f, 1.0f,
        0.0f, 0.34f, 0.90f, 1.0f,
        0.0f, 0.34f, 0.90f, 1.0f,
    )
}
