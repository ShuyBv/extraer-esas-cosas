package com.vrcardboardkotlin

import android.util.Log
import com.google.protobuf.nano.InvalidProtocolBufferNanoException
import com.google.protobuf.nano.MessageNano
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer

object PhoneParams {
    private val TAG: String
    private val STREAM_SENTINEL: Int = 779508118

    fun readFromInputStream(inputStream: InputStream?): Phone.PhoneParams? {
        if (inputStream == null) {
            return null
        }
        try {
            val header: ByteBuffer = ByteBuffer.allocate(8)
            if (inputStream.read(header.array(), 0, header.array().size) == -1) {
                Log.e(TAG, "Error parsing param record: end of stream.")
                return null
            }
            val sentinel: Int = header.getInt()
            val length: Int = header.getInt()
            if (sentinel != STREAM_SENTINEL) {
                Log.e(TAG, "Error parsing param record: incorrect sentinel.")
                return null
            }
            val protoBytes: ByteArray = ByteArray(length)
            if (inputStream.read(protoBytes, 0, protoBytes.size) == -1) {
                Log.e(TAG, "Error parsing param record: end of stream.")
                return null
            }
            return MessageNano.mergeFrom(
                Phone.PhoneParams() as MessageNano,
                protoBytes
            ) as Phone.PhoneParams?
        } catch (e: InvalidProtocolBufferNanoException) {
            val tag: String = TAG
            val s: String = "Error parsing protocol buffer: "
            val value: String = (e.toString())
            Log.w(tag, if ((value.length != 0)) (s + value) else (java.lang.String(s) as String))
        } catch (e2: IOException) {
            val tag2: String = TAG
            val s2: String = "Error reading Cardboard parameters: "
            val value2: String = (e2.toString())
            Log.w(
                tag2,
                if ((value2.length != 0)) (s2 + value2) else (java.lang.String(s2) as String)
            )
        }
        return null
    }

    fun readFromExternalStorage(): Phone.PhoneParams? {
        try {
            var stream: InputStream? = null
            try {
                stream =
                    BufferedInputStream(FileInputStream(ConfigUtils.getConfigFile("phone_params")))
                return readFromInputStream(stream)
            } finally {
                if (stream != null) {
                    try {
                        stream.close()
                    } catch (ex: IOException) {
                    }
                }
            }
        } catch (e: FileNotFoundException) {
            val tag: String = TAG
            val value: String = (e.toString())
            Log.d(
                tag,
                StringBuilder(43 + value.length).append("Cardboard phone parameters file not found: ")
                    .append(value).toString()
            )
            return null
        }
    }

    init {
        TAG = PhoneParams::class.java.getSimpleName()
    }
}
