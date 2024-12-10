package com.z_iti_271311_u2_ae_olazaran_mora_jesus_antonio

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

class ColorBlobDetector {
    // Lower and Upper bounds for range checking in HSV color space
    private val mLowerBound = Scalar(0.0)
    private val mUpperBound = Scalar(0.0)

    // Color radius for range checking in HSV color space
    private var mColorRadius = Scalar(25.0, 50.0, 50.0, 0.0)

    // Spectrum representation for the color range
    val spectrum: Mat = Mat()

    // List to store detected contours
    private val mContours: MutableList<MatOfPoint> = ArrayList()

    // Cached matrices for performance optimization
    var mPyrDownMat: Mat = Mat()
    var mHsvMat: Mat = Mat()
    var mMask: Mat = Mat()
    var mDilatedMask: Mat = Mat()
    var mHierarchy: Mat = Mat()

    /**
     * Sets the color radius for HSV range detection.
     * @param radius The color radius to use for detection.
     */
    fun setColorRadius(radius: Scalar) {
        mColorRadius = radius
    }

    /**
     * Sets the HSV color to detect and adjusts bounds based on the color radius.
     * Also updates the spectrum representation for visualization.
     * @param hsvColor The HSV color to be detected.
     */
    fun setHsvColor(hsvColor: Scalar) {
        val minH =
            if ((hsvColor.`val`[0] >= mColorRadius.`val`[0])) hsvColor.`val`[0] - mColorRadius.`val`[0] else 0.0
        val maxH =
            if ((hsvColor.`val`[0] + mColorRadius.`val`[0] <= 255)) hsvColor.`val`[0] + mColorRadius.`val`[0] else 255.0

        mLowerBound.`val`[0] = minH
        mUpperBound.`val`[0] = maxH

        mLowerBound.`val`[1] = hsvColor.`val`[1] - mColorRadius.`val`[1]
        mUpperBound.`val`[1] = hsvColor.`val`[1] + mColorRadius.`val`[1]

        mLowerBound.`val`[2] = hsvColor.`val`[2] - mColorRadius.`val`[2]
        mUpperBound.`val`[2] = hsvColor.`val`[2] + mColorRadius.`val`[2]

        mLowerBound.`val`[3] = 0.0
        mUpperBound.`val`[3] = 255.0

        // Create spectrum representation for visualization
        val spectrumHsv = Mat(1, (maxH - minH).toInt(), CvType.CV_8UC3)

        var j = 0
        while (j < maxH - minH) {
            val tmp = byteArrayOf((minH + j).toInt().toByte(), 255.toByte(), 255.toByte())
            spectrumHsv.put(0, j, tmp)
            j++
        }

        Imgproc.cvtColor(spectrumHsv, spectrum, Imgproc.COLOR_HSV2RGB_FULL, 4)
    }

    /**
     * Sets the minimum contour area threshold as a percentage of the largest detected contour.
     * @param area The minimum contour area percentage.
     */
    fun setMinContourArea(area: Double) {
        mMinContourArea = area
    }

    /**
     * Processes the input RGBA image to detect blobs of the target color.
     * The process includes resizing, converting to HSV, thresholding, and finding contours.
     * @param rgbaImage The input RGBA image to process.
     */
    fun process(rgbaImage: Mat?) {
        // Reduce image size to optimize processing
        Imgproc.pyrDown(rgbaImage, mPyrDownMat)
        Imgproc.pyrDown(mPyrDownMat, mPyrDownMat)

        // Convert the image to HSV color space
        Imgproc.cvtColor(mPyrDownMat, mHsvMat, Imgproc.COLOR_RGB2HSV_FULL)

        // Apply the HSV range to create a mask
        Core.inRange(mHsvMat, mLowerBound, mUpperBound, mMask)
        Imgproc.dilate(mMask, mDilatedMask, Mat()) // Dilate the mask to fill gaps

        // Find contours in the mask
        val contours: List<MatOfPoint> = ArrayList()
        Imgproc.findContours(
            mDilatedMask,
            contours,
            mHierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE
        )

        // Find the maximum contour area
        var maxArea = 0.0
        var each = contours.iterator()
        while (each.hasNext()) {
            val wrapper = each.next()
            val area = Imgproc.contourArea(wrapper)
            if (area > maxArea) maxArea = area
        }

        // Filter contours by area and scale back to the original image size
        mContours.clear()
        each = contours.iterator()
        while (each.hasNext()) {
            val contour = each.next()
            if (Imgproc.contourArea(contour) > mMinContourArea * maxArea) {
                Core.multiply(contour, Scalar(4.0, 4.0), contour)
                mContours.add(contour)
            }
        }
    }

    /**
     * Gets the list of detected contours.
     * @return A list of detected contours.
     */
    val contours: List<MatOfPoint>
        get() = mContours

    companion object {
        /**
         * Minimum contour area in percent for contours filtering.
         */
        private var mMinContourArea = 0.1
    }
}
