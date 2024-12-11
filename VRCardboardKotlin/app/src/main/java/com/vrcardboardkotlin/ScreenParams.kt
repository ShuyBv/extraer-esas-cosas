package com.vrcardboardkotlin

import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import java.io.InputStream

class ScreenParams {
    var width: Int
        private set
    var height: Int
        private set
    private var mXMetersPerPixel: Float
    private var mYMetersPerPixel: Float
    var borderSizeMeters: Float
        private set

    constructor(display: Display) : super() {
        val metrics = getDisplayMetrics(display)
        this.mXMetersPerPixel = METERS_PER_INCH / metrics.xdpi
        this.mYMetersPerPixel = METERS_PER_INCH / metrics.ydpi
        this.width = metrics.widthPixels
        this.height = metrics.heightPixels
        this.borderSizeMeters = DEFAULT_BORDER_SIZE_METERS

        // Ajustar si es necesario
        if (this.height > this.width) {
            val tempPx: Int = this.width
            this.width = this.height
            this.height = tempPx
            val tempMetersPerPixel: Float = this.mXMetersPerPixel
            this.mXMetersPerPixel = this.mYMetersPerPixel
            this.mYMetersPerPixel = tempMetersPerPixel
        }

        Log.d("ScreenParams", "Screen Width: $width, Height: $height")
    }

    private fun getDisplayMetrics(display: Display): DisplayMetrics {
        val metrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display.getRealMetrics(metrics)
        } else {
            display.getMetrics(metrics)
        }
        return metrics
    }



    constructor(params: ScreenParams) : super() {
        this.width = params.width
        this.height = params.height
        this.mXMetersPerPixel = params.mXMetersPerPixel
        this.mYMetersPerPixel = params.mYMetersPerPixel
        this.borderSizeMeters = params.borderSizeMeters
    }

    val widthMeters: Float
        get() {
            return this.width * this.mXMetersPerPixel
        }

    val heightMeters: Float
        get() {
            return this.height * this.mYMetersPerPixel
        }

    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }
        if (other === this) {
            return true
        }
        if (!(other is ScreenParams)) {
            return false
        }
        val o: ScreenParams = other
        return (this.width == o.width) && (this.height == o.height) && (this.mXMetersPerPixel == o.mXMetersPerPixel) && (this.mYMetersPerPixel == o.mYMetersPerPixel) && (this.borderSizeMeters == o.borderSizeMeters)
    }

    override fun toString(): String {
        return "{\n" + StringBuilder(22).append("  width: ").append(this.width).append(",\n")
            .toString() + StringBuilder(23).append("  height: ").append(
            this.height
        ).append(",\n").toString() + StringBuilder(39).append("  x_meters_per_pixel: ").append(
            this.mXMetersPerPixel
        ).append(",\n").toString() + StringBuilder(39).append("  y_meters_per_pixel: ").append(
            this.mYMetersPerPixel
        ).append(",\n").toString() + StringBuilder(39).append("  border_size_meters: ").append(
            this.borderSizeMeters
        ).append(",\n").toString() + "}"
    }

    companion object {
        private val METERS_PER_INCH: Float = 0.0254f
        private val DEFAULT_BORDER_SIZE_METERS: Float = 0.003f

        fun fromProto(display: Display, params: Phone.PhoneParams?): ScreenParams? {
            if (params == null) {
                return null
            }
            val screenParams = ScreenParams(display)
            if (params.hasXPpi()) {
                screenParams.mXMetersPerPixel = METERS_PER_INCH / params.xPpi
            }
            if (params.hasYPpi()) {
                screenParams.mYMetersPerPixel = METERS_PER_INCH / params.yPpi
            }
            if (params.hasBottomBezelHeight()) {
                screenParams.borderSizeMeters = params.bottomBezelHeight
            }
            return screenParams
        }

        fun createFromInputStream(display: Display, inputStream: InputStream?): ScreenParams? {
            val phoneParams: Phone.PhoneParams? = PhoneParams.readFromInputStream(inputStream)
            if (phoneParams == null) {
                return null
            }
            return fromProto(display, phoneParams)
        }

    }

}
