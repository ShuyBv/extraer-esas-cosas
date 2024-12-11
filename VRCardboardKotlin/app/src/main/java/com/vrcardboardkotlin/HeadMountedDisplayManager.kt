package com.vrcardboardkotlin

import android.content.Context
import android.util.Log
import android.view.Display
import android.view.WindowManager
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class HeadMountedDisplayManager(private val mContext: Context) {
    val headMountedDisplay: HeadMountedDisplay

    init {
        this.headMountedDisplay =
            HeadMountedDisplay(this.createScreenParams(), this.createCardboardDeviceParams())
    }

    fun onResume() {
        val deviceParams = this.createCardboardDeviceParamsFromExternalStorage()
        if (deviceParams != null && deviceParams != headMountedDisplay.cardboardDeviceParams) {
            headMountedDisplay.cardboardDeviceParams = deviceParams
            Log.i(
                "HeadMountedDisplayManager",
                "Successfully read updated device params from external storage"
            )
        }
        val screenParams = this.createScreenParamsFromExternalStorage(
            display
        )
        if (screenParams != null && screenParams != headMountedDisplay.screenParams) {
            headMountedDisplay.screenParams = screenParams
            Log.i(
                "HeadMountedDisplayManager",
                "Successfully read updated screen params from external storage"
            )
        }
    }

    fun onPause() {
    }

    fun updateCardboardDeviceParams(cardboardDeviceParams: CardboardDeviceParams?): Boolean {
        if (cardboardDeviceParams == null || cardboardDeviceParams == headMountedDisplay.cardboardDeviceParams) {
            return false
        }
        headMountedDisplay.cardboardDeviceParams = cardboardDeviceParams
        this.writeCardboardParamsToExternalStorage()
        return true
    }

    private fun writeCardboardParamsToExternalStorage() {
        var success = false
        var stream: OutputStream? = null
        try {
            stream =
                BufferedOutputStream(FileOutputStream(ConfigUtils.getConfigFile("current_device_params")))
            success = headMountedDisplay.cardboardDeviceParams?.writeToOutputStream(stream) ?: false
        } catch (e: FileNotFoundException) {
        } finally {
            if (stream != null) {
                try {
                    stream.close()
                } catch (ex: IOException) {
                }
            }
        }
        if (!success) {
            Log.e(
                "HeadMountedDisplayManager",
                "Could not write Cardboard parameters to external storage."
            )
        } else {
            Log.i(
                "HeadMountedDisplayManager",
                "Successfully wrote Cardboard parameters to external storage."
            )
        }
    }

    private val display: Display
        get() {
            val windowManager = mContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            return windowManager.defaultDisplay
        }

    private fun createScreenParams(): ScreenParams {
        val display = this.display
        val params = this.createScreenParamsFromExternalStorage(display)
        if (params != null) {
            Log.i("HeadMountedDisplayManager", "Successfully read screen params from external storage")
            return params
        }

        val defaultParams = ScreenParams(display)
        Log.d("HeadMountedDisplayManager", "Default Screen Params: Width=${defaultParams.width}, Height=${defaultParams.height}")
        return defaultParams
    }


    private fun createCardboardDeviceParams(): CardboardDeviceParams {
        val params = try {
            createCardboardDeviceParamsFromExternalStorage()
        } catch (e: Exception) {
            Log.w("HeadMountedDisplayManager", "Failed to load device params from external storage: ${e.message}")
            null
        } ?: run {
            Log.w("HeadMountedDisplayManager", "Using default CardboardDeviceParams")
            CardboardDeviceParams()
        }
        return params
    }

    private fun createCardboardDeviceParamsFromAssetFolder(): CardboardDeviceParams? {
        return try {
            BufferedInputStream(ConfigUtils.openAssetConfigFile(mContext.assets, "current_device_params")).use { stream ->
                CardboardDeviceParams.Companion.createFromInputStream(stream)
            }
        } catch (e: FileNotFoundException) {
            Log.d(
                "HeadMountedDisplayManager",
                "Bundled Cardboard device parameters not found: ${e.message}"
            )
            null
        } catch (e: IOException) {
            Log.e(
                "HeadMountedDisplayManager",
                "Error reading config file in asset folder: ${e.message}"
            )
            null
        }
    }

    private fun createCardboardDeviceParamsFromExternalStorage(): CardboardDeviceParams {
        return try {
            BufferedInputStream(FileInputStream(ConfigUtils.getConfigFile("current_device_params"))).use { stream ->
                CardboardDeviceParams.Companion.createFromInputStream(stream) ?: CardboardDeviceParams()
            }
        } catch (e: FileNotFoundException) {
            Log.d("HeadMountedDisplayManager", "Cardboard device parameters file not found: ${e.message}. Using defaults.")
            CardboardDeviceParams()
        }
    }

    private fun createScreenParamsFromExternalStorage(display: Display): ScreenParams {
        return try {
            BufferedInputStream(FileInputStream(ConfigUtils.getConfigFile("phone_params"))).use { stream ->
                ScreenParams.Companion.createFromInputStream(display, stream) ?: ScreenParams(display)
            }
        } catch (e: FileNotFoundException) {
            Log.d("HeadMountedDisplayManager", "Cardboard screen parameters file not found: ${e.message}. Using defaults.")
            ScreenParams(display)
        }
    }

    companion object {
        private const val TAG = "HeadMountedDisplayManager"
    }
}
