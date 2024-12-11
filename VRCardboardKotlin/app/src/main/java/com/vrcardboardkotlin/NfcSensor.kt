package com.vrcardboardkotlin

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Handler
import android.os.Parcelable
import android.util.Log
import java.io.IOException
import java.util.Timer
import java.util.TimerTask

class NfcSensor private constructor(context: Context) {
    private val mContext: Context
    private val mNfcAdapter: NfcAdapter?
    private val mTagLock: Any
    private val mListeners: MutableList<ListenerHelper>
    private val mNfcIntentFilters: Array<IntentFilter>
    private var mCurrentNdef: Ndef? = null
    private var mCurrentTag: Tag? = null
    private var mCurrentTagIsCardboard: Boolean = false
    private var mNfcDisconnectTimer: Timer? = null
    private var mTagConnectionFailures: Int = 0

    init {
        this.mContext = context.applicationContext
        this.mNfcAdapter = NfcAdapter.getDefaultAdapter(this.mContext)
        this.mListeners = ArrayList()
        this.mTagLock = Any()

        if (this.mNfcAdapter != null) { // Solo ejecuta el código si mNfcAdapter no es null
            val ndefIntentFilter = IntentFilter("android.nfc.action.NDEF_DISCOVERED").apply {
                addAction("android.nfc.action.TECH_DISCOVERED")
                addAction("android.nfc.action.TAG_DISCOVERED")
            }
            this.mNfcIntentFilters = arrayOf(ndefIntentFilter)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                mContext.registerReceiver(
                    object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            this@NfcSensor.onNfcIntent(intent)
                        }
                    },
                    ndefIntentFilter,
                    Context.RECEIVER_NOT_EXPORTED // Asegura que el receptor no sea accesible fuera de tu app
                )
            } else {
                mContext.registerReceiver(object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        this@NfcSensor.onNfcIntent(intent)
                    }
                }, ndefIntentFilter)
            }
        } else {
            this.mNfcIntentFilters = arrayOf() // Asigna un valor vacío para evitar problemas
        }
    }



    fun addOnCardboardNfcListener(listener: OnCardboardNfcListener?) {
        if (listener == null) {
            return
        }
        synchronized(this.mListeners, {
            for (helper: ListenerHelper in this.mListeners) {
                if (helper.getListener() === listener) {
                    return
                }
            }
            mListeners.add(ListenerHelper(listener, Handler()))
        })
    }

    fun removeOnCardboardNfcListener(listener: OnCardboardNfcListener?) {
        if (listener == null) {
            return
        }
        synchronized(this.mListeners, {
            for (helper: ListenerHelper in this.mListeners) {
                if (helper.getListener() === listener) {
                    mListeners.remove(helper)
                }
            }
        })
    }

    val isNfcSupported: Boolean
        get() {
            return this.mNfcAdapter != null
        }

    val isNfcEnabled: Boolean
        get() {
            return this.isNfcSupported && mNfcAdapter!!.isEnabled()
        }

    val isDeviceInCardboard: Boolean
        get() {
            synchronized(this.mTagLock, {
                return this.mCurrentTagIsCardboard
            })
        }

    val tagContents: NdefMessage?
        get() {
            synchronized(this.mTagLock, {
                return if ((this.mCurrentNdef != null)) mCurrentNdef!!.getCachedNdefMessage() else null
            })
        }

    fun onResume(activity: Activity) {
        if (!this.isNfcEnabled) {
            return
        }
        val intent: Intent = Intent("android.nfc.action.NDEF_DISCOVERED")
        intent.setPackage(activity.getPackageName())
        val pendingIntent: PendingIntent = PendingIntent.getBroadcast(this.mContext, 0, intent, 0)
        mNfcAdapter!!.enableForegroundDispatch(
            activity,
            pendingIntent,
            this.mNfcIntentFilters,
            null as Array<Array<String?>?>?
        )
    }

    fun onPause(activity: Activity?) {
        if (!this.isNfcEnabled) {
            return
        }
        mNfcAdapter!!.disableForegroundDispatch(activity)
    }

    fun onNfcIntent(intent: Intent?) {
        if (!this.isNfcEnabled || (intent == null) || !mNfcIntentFilters.get(0)
                .matchAction(intent.getAction())
        ) {
            return
        }
        this.onNewNfcTag(intent.getParcelableExtra<Parcelable>("android.nfc.extra.TAG") as Tag?)
    }

    private fun onNewNfcTag(nfcTag: Tag?) {
        if (nfcTag == null) {
            return
        }
        synchronized(this.mTagLock, {
            val previousTag: Tag? = this.mCurrentTag
            val previousNdef: Ndef? = this.mCurrentNdef
            val previousTagWasCardboard: Boolean = this.mCurrentTagIsCardboard
            this.closeCurrentNfcTag()
            this.mCurrentTag = nfcTag
            this.mCurrentNdef = Ndef.get(nfcTag)
            if (this.mCurrentNdef == null) {
                if (previousTagWasCardboard) {
                    this.sendDisconnectionEvent()
                }
                return
            }
            var isSameTag: Boolean = false
            if (previousNdef != null) {
                val tagId1: ByteArray? = mCurrentTag!!.getId()
                val tagId2: ByteArray? = previousTag!!.getId()
                isSameTag = ((tagId1 != null) && (tagId2 != null) && tagId1.contentEquals(tagId2))
                if (!isSameTag && previousTagWasCardboard) {
                    this.sendDisconnectionEvent()
                }
            }
            val nfcTagContents: NdefMessage
            try {
                mCurrentNdef!!.connect()
                nfcTagContents = mCurrentNdef!!.getCachedNdefMessage()
            } catch (e: Exception) {
                val s: String = "NfcSensor"
                val s2: String = "Error reading NFC tag: "
                val value: String = e.toString().toString()
                Log.e(
                    s,
                    if ((value.length != 0)) (s2 + value) else (java.lang.String(s2) as String)
                )
                if (isSameTag && previousTagWasCardboard) {
                    this.sendDisconnectionEvent()
                }
                return
            }
            this.mCurrentTagIsCardboard = this.isCardboardNdefMessage(nfcTagContents)
            if (!isSameTag && this.mCurrentTagIsCardboard) {
                synchronized(this.mListeners, {
                    for (listener: ListenerHelper in this.mListeners) {
                        listener.onInsertedIntoCardboard(
                            CardboardDeviceParams.Companion.createFromNfcContents(
                                nfcTagContents
                            )
                        )
                    }
                })
            }
            if (this.mCurrentTagIsCardboard) {
                this.mTagConnectionFailures = 0
                (Timer("NFC disconnect timer").also({ this.mNfcDisconnectTimer = it })).schedule(
                    object : TimerTask() {
                        override fun run() {
                            synchronized(this@NfcSensor.mTagLock, {
                                if (!mCurrentNdef!!.isConnected()) {
                                    ++this@NfcSensor.mTagConnectionFailures
                                    if (this@NfcSensor.mTagConnectionFailures > 1) {
                                        this@NfcSensor.closeCurrentNfcTag()
                                        this@NfcSensor.sendDisconnectionEvent()
                                    }
                                }
                            })
                        }
                    },
                    250L,
                    250L
                )
            }
        })
    }

    private fun closeCurrentNfcTag() {
        if (this.mNfcDisconnectTimer != null) {
            mNfcDisconnectTimer!!.cancel()
        }
        if (this.mCurrentNdef == null) {
            return
        }
        try {
            mCurrentNdef!!.close()
        } catch (e: IOException) {
            Log.w("NfcSensor", e.toString())
        }
        this.mCurrentTag = null
        this.mCurrentNdef = null
        this.mCurrentTagIsCardboard = false
    }

    private fun sendDisconnectionEvent() {
        synchronized(this.mListeners, {
            for (listener: ListenerHelper in this.mListeners) {
                listener.onRemovedFromCardboard()
            }
        })
    }

    private fun isCardboardNdefMessage(message: NdefMessage?): Boolean {
        if (message == null) {
            return false
        }
        for (record: NdefRecord in message.getRecords()) {
            if (this.isCardboardNdefRecord(record)) {
                return true
            }
        }
        return false
    }

    private fun isCardboardNdefRecord(record: NdefRecord?): Boolean {
        if (record == null) {
            return false
        }
        val uri: Uri? = record.toUri()
        return uri != null && CardboardDeviceParams.Companion.isCardboardUri(uri)
    }

    private class ListenerHelper(
        private val listener: OnCardboardNfcListener,
        private val mHandler: Handler
    ) : OnCardboardNfcListener {

        fun getListener(): OnCardboardNfcListener {
            return listener
        }

        override fun onInsertedIntoCardboard(deviceParams: CardboardDeviceParams?) {
            mHandler.post {
                listener.onInsertedIntoCardboard(deviceParams)
            }
        }

        override fun onRemovedFromCardboard() {
            mHandler.post {
                listener.onRemovedFromCardboard()
            }
        }
    }


    interface OnCardboardNfcListener {
        fun onInsertedIntoCardboard(p0: CardboardDeviceParams?)

        fun onRemovedFromCardboard()
    }

    companion object {
        private var sInstance: NfcSensor? = null
        fun getInstance(context: Context): NfcSensor? {
            if (sInstance == null) {
                sInstance = NfcSensor(context)
            }
            return sInstance
        }
    }
}
