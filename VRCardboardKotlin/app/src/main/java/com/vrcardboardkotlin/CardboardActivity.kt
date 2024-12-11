package com.vrcardboardkotlin

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import com.vrcardboardkotlin.MagnetSensor.OnCardboardTriggerListener
import com.vrcardboardkotlin.NfcSensor.OnCardboardNfcListener

open class CardboardActivity : Activity() {
    private var mCardboardView: CardboardView? = null
    private var mMagnetSensor: MagnetSensor? = null
    private var mNfcSensor: NfcSensor? = null
    private val sensorListener: SensorListener
    private var mVolumeKeysMode = 0

    init {
        sensorListener = SensorListener()
    }

    fun setCardboardView(cardboardView: CardboardView?) {
        mCardboardView = cardboardView
        if (cardboardView == null) {
            return
        }
        val tagContents = mNfcSensor?.tagContents
        if (tagContents != null) {
            updateCardboardDeviceParams(
                CardboardDeviceParams.Companion.createFromNfcContents(
                    tagContents
                )
            )
        }
    }

    fun setVolumeKeysMode(mode: Int) {
        mVolumeKeysMode = mode
    }


    fun areVolumeKeysDisabled(): Boolean {
        return when (mVolumeKeysMode) {
            VolumeKeys.NOT_DISABLED -> {
                false
            }

            VolumeKeys.DISABLED_WHILE_IN_CARDBOARD -> {
                mNfcSensor!!.isDeviceInCardboard
            }

            VolumeKeys.DISABLED -> {
                true
            }

            else -> {
                throw IllegalStateException(
                    StringBuilder()
                        .append("Invalid volume keys mode ")
                        .append(mVolumeKeysMode).toString()
                )
            }
        }
    }

    fun onInsertedIntoCardboard(cardboardDeviceParams: CardboardDeviceParams?) {
        this.updateCardboardDeviceParams(cardboardDeviceParams)
    }

    fun onRemovedFromCardboard() {
    }

    fun onCardboardTrigger() {
    }

    protected fun updateCardboardDeviceParams(newParams: CardboardDeviceParams?) {
        if (mCardboardView != null) {
            mCardboardView!!.updateCardboardDeviceParams(newParams)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.ALPHA_CHANGED)

        mMagnetSensor = MagnetSensor(this)
        mMagnetSensor!!.setOnCardboardTriggerListener(sensorListener)
        mNfcSensor = NfcSensor.Companion.getInstance(this)
        mNfcSensor!!.addOnCardboardNfcListener(sensorListener)

        mNfcSensor!!.onNfcIntent(this.intent)
        setVolumeKeysMode(VolumeKeys.DISABLED_WHILE_IN_CARDBOARD)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            val handler = Handler()
            window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
                if ((visibility and 0x2) == 0x0) {
                    handler.postDelayed(
                        { this@CardboardActivity.setFullscreenMode() },
                        NAVIGATION_BAR_TIMEOUT_MS
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (mCardboardView != null) {
            mCardboardView!!.onResume()
        }
        mMagnetSensor!!.start()
        mNfcSensor!!.onResume(this)
    }

    override fun onPause() {
        super.onPause()
        if (mCardboardView != null) {
            mCardboardView!!.onPause()
        }
        mMagnetSensor!!.stop()
        mNfcSensor!!.onPause(this)
    }

    override fun onDestroy() {
        mNfcSensor!!.removeOnCardboardNfcListener(sensorListener)
        super.onDestroy()
    }

    override fun setContentView(view: View) {
        if (view is CardboardView) {
            setCardboardView(view)
        }
        super.setContentView(view)
    }

    override fun setContentView(view: View, params: ViewGroup.LayoutParams) {
        if (view is CardboardView) {
            setCardboardView(view)
        }
        super.setContentView(view, params)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
                && areVolumeKeysDisabled()) || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
                && areVolumeKeysDisabled()) || super.onKeyUp(keyCode, event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setFullscreenMode()
        }
    }

    private fun setFullscreenMode() {
        window.decorView.systemUiVisibility = 5894
    }

    object VolumeKeys {
        const val NOT_DISABLED: Int = 0
        const val DISABLED: Int = 1
        const val DISABLED_WHILE_IN_CARDBOARD: Int = 2
    }

    private inner class SensorListener

        : OnCardboardTriggerListener, OnCardboardNfcListener {
        override fun onInsertedIntoCardboard(deviceParams: CardboardDeviceParams?) {
            this@CardboardActivity.onInsertedIntoCardboard(deviceParams)
        }

        override fun onRemovedFromCardboard() {
            this@CardboardActivity.onRemovedFromCardboard()
        }

        override fun onCardboardTrigger() {
            this@CardboardActivity.onCardboardTrigger()
        }
    }

    companion object {
        private const val NAVIGATION_BAR_TIMEOUT_MS = 2000L
    }
}
