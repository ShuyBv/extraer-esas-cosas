package com.vrcardboardkotlin

import com.google.protobuf.nano.CodedInputByteBufferNano
import com.google.protobuf.nano.CodedOutputByteBufferNano
import com.google.protobuf.nano.MessageNano
import com.google.protobuf.nano.WireFormatNano
import java.io.IOException
import kotlin.concurrent.Volatile

interface Phone {
    class PhoneParams() : MessageNano() {
        private var bitField0_: Int = 0
        var xPpi: Float = 0f
            private set
        var yPpi: Float = 0f
            private set
        var bottomBezelHeight: Float = 0f
            private set
        var gyroBias: FloatArray? = null

        fun hasXPpi(): Boolean {
            return (this.bitField0_ and 0x1) != 0x0
        }

        fun hasYPpi(): Boolean {
            return (this.bitField0_ and 0x2) != 0x0
        }

        fun hasBottomBezelHeight(): Boolean {
            return (this.bitField0_ and 0x4) != 0x0
        }

        init {
            this.clear()
        }

        fun clear(): PhoneParams {
            this.bitField0_ = 0
            this.xPpi = 0.0f
            this.yPpi = 0.0f
            this.bottomBezelHeight = 0.0f
            this.gyroBias = WireFormatNano.EMPTY_FLOAT_ARRAY
            this.cachedSize = -1
            return this
        }

        @Throws(IOException::class)
        override fun writeTo(output: CodedOutputByteBufferNano) {
            val dataSize: Int
            if ((this.bitField0_ and 0x1) != 0x0) {
                output.writeFloat(1, this.xPpi)
            }
            if ((this.bitField0_ and 0x2) != 0x0) {
                output.writeFloat(2, this.yPpi)
            }
            if ((this.bitField0_ and 0x4) != 0x0) {
                output.writeFloat(3, this.bottomBezelHeight)
            }
            if (this.gyroBias != null && gyroBias!!.size > 0) {
                dataSize = 4 * gyroBias!!.size
                output.writeRawVarint32(34)
                output.writeRawVarint32(dataSize)
                for (i in gyroBias!!.indices) {
                    output.writeFloatNoTag(gyroBias!!.get(i))
                }
            }
            super.writeTo(output)
        }

        override fun computeSerializedSize(): Int {
            var size: Int
            val dataSize: Int
            size = super.computeSerializedSize()
            if ((this.bitField0_ and 0x1) != 0x0) {
                size += CodedOutputByteBufferNano.computeFloatSize(1, this.xPpi)
            }
            if ((this.bitField0_ and 0x2) != 0x0) {
                size += CodedOutputByteBufferNano.computeFloatSize(2, this.yPpi)
            }
            if ((this.bitField0_ and 0x4) != 0x0) {
                size += CodedOutputByteBufferNano.computeFloatSize(3, this.bottomBezelHeight)
            }
            if (this.gyroBias != null && gyroBias!!.size > 0) {
                dataSize = 4 * gyroBias!!.size
                size += dataSize
                size = ++size + CodedOutputByteBufferNano.computeRawVarint32Size(dataSize)
            }
            return size
        }

        @Throws(IOException::class)
        override fun mergeFrom(input: CodedInputByteBufferNano): PhoneParams {
            var tag: Int
            var arrayLength: Int
            var i: Int
            var newArray: FloatArray
            var length: Int
            var limit: Int
            var arrayLength2: Int
            var j: Int
            var newArray2: FloatArray
            while (true) {
                tag = input.readTag()
                when (tag) {
                    0 -> {
                        return this
                    }

                    13 -> {
                        this.xPpi = input.readFloat()
                        this.bitField0_ = this.bitField0_ or 0x1
                        continue
                    }

                    21 -> {
                        this.yPpi = input.readFloat()
                        this.bitField0_ = this.bitField0_ or 0x2
                        continue
                    }

                    29 -> {
                        this.bottomBezelHeight = input.readFloat()
                        this.bitField0_ = this.bitField0_ or 0x4
                        continue
                    }

                    37 -> {
                        val arrayLength = WireFormatNano.getRepeatedFieldArrayLength(input, 37)
                        val currentSize = this.gyroBias?.size ?: 0
                        val newArray = FloatArray(currentSize + arrayLength)
                        if (currentSize != 0) {
                            System.arraycopy(this.gyroBias, 0, newArray, 0, currentSize)
                        }
                        for (i in currentSize until newArray.size - 1) {
                            newArray[i] = input.readFloat()
                            input.readTag()
                        }
                        newArray[newArray.size - 1] = input.readFloat()
                        this.gyroBias = newArray
                    }

                    34 -> {
                        val length = input.readRawVarint32()
                        val limit = input.pushLimit(length)
                        val arrayLength = length / 4
                        val currentSize = this.gyroBias?.size ?: 0
                        val newArray = FloatArray(currentSize + arrayLength)
                        if (currentSize != 0) {
                            System.arraycopy(this.gyroBias, 0, newArray, 0, currentSize)
                        }
                        for (i in currentSize until newArray.size) {
                            newArray[i] = input.readFloat()
                        }
                        this.gyroBias = newArray
                        input.popLimit(limit)
                    }

                    else -> {
                        if (!WireFormatNano.parseUnknownField(input, tag)) {
                            return this
                        }
                        continue
                    }
                }
            }
        }

        companion object {
            @Volatile
            private var _emptyArray: Array<PhoneParams> = emptyArray()
        }
    }
}
