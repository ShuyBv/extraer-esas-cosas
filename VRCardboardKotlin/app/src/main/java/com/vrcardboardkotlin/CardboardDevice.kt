package com.vrcardboardkotlin

import com.google.protobuf.nano.CodedInputByteBufferNano
import com.google.protobuf.nano.CodedOutputByteBufferNano
import com.google.protobuf.nano.InternalNano
import com.google.protobuf.nano.MessageNano
import com.google.protobuf.nano.WireFormatNano
import java.io.IOException
import kotlin.concurrent.Volatile

interface CardboardDevice {
    class DeviceParams : MessageNano() {
        private var bitField0_ = 0
        var vendor: String? = null
            private set
        var model: String? = null
            private set
        var screenToLensDistance: Float = 0f
            private set
        var interLensDistance: Float = 0f
            private set
        var leftEyeFieldOfViewAngles: FloatArray = floatArrayOf()
        var trayBottomToLensHeight: Float = 0f
            private set
        var distortionCoefficients: FloatArray = floatArrayOf()
        var hasMagnet: Boolean = false
            private set

        fun setVendor(value: String?): DeviceParams {
            if (value == null) {
                throw NullPointerException()
            }
            this.vendor = value
            this.bitField0_ = this.bitField0_ or 0x1
            return this
        }

        fun setModel(value: String?): DeviceParams {
            if (value == null) {
                throw NullPointerException()
            }
            this.model = value
            this.bitField0_ = this.bitField0_ or 0x2
            return this
        }

        fun clearModel(): DeviceParams {
            this.model = ""
            this.bitField0_ = this.bitField0_ and -0x3
            return this
        }

        fun setScreenToLensDistance(value: Float): DeviceParams {
            this.screenToLensDistance = value
            this.bitField0_ = this.bitField0_ or 0x4
            return this
        }

        fun setInterLensDistance(value: Float): DeviceParams {
            this.interLensDistance = value
            this.bitField0_ = this.bitField0_ or 0x8
            return this
        }

        fun setTrayBottomToLensHeight(value: Float): DeviceParams {
            this.trayBottomToLensHeight = value
            this.bitField0_ = this.bitField0_ or 0x10
            return this
        }

        fun setHasMagnet(value: Boolean): DeviceParams {
            this.hasMagnet = value
            this.bitField0_ = this.bitField0_ or 0x20
            return this
        }

        init {
            this.clear()
        }

        fun clear(): DeviceParams {
            this.bitField0_ = 0
            this.vendor = ""
            this.model = ""
            this.screenToLensDistance = 0.0f
            this.interLensDistance = 0.0f
            this.leftEyeFieldOfViewAngles = WireFormatNano.EMPTY_FLOAT_ARRAY
            this.trayBottomToLensHeight = 0.0f
            this.distortionCoefficients = WireFormatNano.EMPTY_FLOAT_ARRAY
            this.hasMagnet = false
            this.cachedSize = -1
            return this
        }

        @Throws(IOException::class)
        override fun writeTo(output: CodedOutputByteBufferNano) {
            var dataSize: Int
            if ((this.bitField0_ and 0x1) != 0x0) {
                output.writeString(1, this.vendor)
            }
            if ((this.bitField0_ and 0x2) != 0x0) {
                output.writeString(2, this.model)
            }
            if ((this.bitField0_ and 0x4) != 0x0) {
                output.writeFloat(3, this.screenToLensDistance)
            }
            if ((this.bitField0_ and 0x8) != 0x0) {
                output.writeFloat(4, this.interLensDistance)
            }
            if (this.leftEyeFieldOfViewAngles != null && leftEyeFieldOfViewAngles!!.size > 0) {
                dataSize = 4 * leftEyeFieldOfViewAngles!!.size
                output.writeRawVarint32(42)
                output.writeRawVarint32(dataSize)
                for (i in leftEyeFieldOfViewAngles!!.indices) {
                    output.writeFloatNoTag(leftEyeFieldOfViewAngles!![i])
                }
            }
            if ((this.bitField0_ and 0x10) != 0x0) {
                output.writeFloat(6, this.trayBottomToLensHeight)
            }
            if (this.distortionCoefficients != null && distortionCoefficients!!.size > 0) {
                dataSize = 4 * distortionCoefficients!!.size
                output.writeRawVarint32(58)
                output.writeRawVarint32(dataSize)
                for (i in distortionCoefficients!!.indices) {
                    output.writeFloatNoTag(distortionCoefficients!![i])
                }
            }
            if ((this.bitField0_ and 0x20) != 0x0) {
                output.writeBool(10, this.hasMagnet)
            }
            super.writeTo(output)
        }

        override fun computeSerializedSize(): Int {
            var size: Int
            var dataSize: Int
            size = super.computeSerializedSize()
            if ((this.bitField0_ and 0x1) != 0x0) {
                size += CodedOutputByteBufferNano.computeStringSize(1, this.vendor)
            }
            if ((this.bitField0_ and 0x2) != 0x0) {
                size += CodedOutputByteBufferNano.computeStringSize(2, this.model)
            }
            if ((this.bitField0_ and 0x4) != 0x0) {
                size += CodedOutputByteBufferNano.computeFloatSize(3, this.screenToLensDistance)
            }
            if ((this.bitField0_ and 0x8) != 0x0) {
                size += CodedOutputByteBufferNano.computeFloatSize(4, this.interLensDistance)
            }
            if (this.leftEyeFieldOfViewAngles != null && leftEyeFieldOfViewAngles!!.size > 0) {
                dataSize = 4 * leftEyeFieldOfViewAngles!!.size
                size += dataSize
                size = ++size + CodedOutputByteBufferNano.computeRawVarint32Size(dataSize)
            }
            if ((this.bitField0_ and 0x10) != 0x0) {
                size += CodedOutputByteBufferNano.computeFloatSize(6, this.trayBottomToLensHeight)
            }
            if (this.distortionCoefficients != null && distortionCoefficients!!.size > 0) {
                dataSize = 4 * distortionCoefficients!!.size
                size += dataSize
                size = ++size + CodedOutputByteBufferNano.computeRawVarint32Size(dataSize)
            }
            if ((this.bitField0_ and 0x20) != 0x0) {
                size += CodedOutputByteBufferNano.computeBoolSize(10, this.hasMagnet)
            }
            return size
        }

        @Throws(IOException::class)
        override fun mergeFrom(input: CodedInputByteBufferNano): DeviceParams {
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

                    10 -> {
                        this.vendor = input.readString()
                        this.bitField0_ = this.bitField0_ or 0x1
                        continue
                    }

                    18 -> {
                        this.model = input.readString()
                        this.bitField0_ = this.bitField0_ or 0x2
                        continue
                    }

                    29 -> {
                        this.screenToLensDistance = input.readFloat()
                        this.bitField0_ = this.bitField0_ or 0x4
                        continue
                    }

                    37 -> {
                        this.interLensDistance = input.readFloat()
                        this.bitField0_ = this.bitField0_ or 0x8
                        continue
                    }

                    45 -> {
                        arrayLength = WireFormatNano.getRepeatedFieldArrayLength(input, 45)
                        i =
                            (if ((this.leftEyeFieldOfViewAngles == null)) 0 else leftEyeFieldOfViewAngles!!.size)
                        newArray = FloatArray(i + arrayLength)
                        if (i != 0) {
                            System.arraycopy(this.leftEyeFieldOfViewAngles, 0, newArray, 0, i)
                        }
                        while (i < newArray.size - 1) {
                            newArray[i] = input.readFloat()
                            input.readTag()
                            ++i
                        }
                        newArray[i] = input.readFloat()
                        this.leftEyeFieldOfViewAngles = newArray
                        continue
                    }

                    42 -> {
                        length = input.readRawVarint32()
                        limit = input.pushLimit(length)
                        arrayLength2 = length / 4
                        j =
                            (if ((this.leftEyeFieldOfViewAngles == null)) 0 else leftEyeFieldOfViewAngles!!.size)
                        newArray2 = FloatArray(j + arrayLength2)
                        if (j != 0) {
                            System.arraycopy(this.leftEyeFieldOfViewAngles, 0, newArray2, 0, j)
                        }
                        while (j < newArray2.size) {
                            newArray2[j] = input.readFloat()
                            ++j
                        }
                        this.leftEyeFieldOfViewAngles = newArray2
                        input.popLimit(limit)
                        continue
                    }

                    53 -> {
                        this.trayBottomToLensHeight = input.readFloat()
                        this.bitField0_ = this.bitField0_ or 0x10
                        continue
                    }

                    61 -> {
                        arrayLength = WireFormatNano.getRepeatedFieldArrayLength(input, 61)
                        i =
                            (if ((this.distortionCoefficients == null)) 0 else distortionCoefficients!!.size)
                        newArray = FloatArray(i + arrayLength)
                        if (i != 0) {
                            System.arraycopy(this.distortionCoefficients, 0, newArray, 0, i)
                        }
                        while (i < newArray.size - 1) {
                            newArray[i] = input.readFloat()
                            input.readTag()
                            ++i
                        }
                        newArray[i] = input.readFloat()
                        this.distortionCoefficients = newArray
                        continue
                    }

                    58 -> {
                        length = input.readRawVarint32()
                        limit = input.pushLimit(length)
                        arrayLength2 = length / 4
                        j =
                            (if ((this.distortionCoefficients == null)) 0 else distortionCoefficients!!.size)
                        newArray2 = FloatArray(j + arrayLength2)
                        if (j != 0) {
                            System.arraycopy(this.distortionCoefficients, 0, newArray2, 0, j)
                        }
                        while (j < newArray2.size) {
                            newArray2[j] = input.readFloat()
                            ++j
                        }
                        this.distortionCoefficients = newArray2
                        input.popLimit(limit)
                        continue
                    }

                    80 -> {
                        this.hasMagnet = input.readBool()
                        this.bitField0_ = this.bitField0_ or 0x20
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
            private var _emptyArray: Array<DeviceParams?>? = null
            fun emptyArray(): Array<DeviceParams?>? {
                if (_emptyArray == null) {
                    synchronized(InternalNano.LAZY_INIT_LOCK) {
                        if (_emptyArray == null) {
                            _emptyArray = arrayOfNulls(0)
                        }
                    }
                }
                return _emptyArray
            }
        }
    }
}
