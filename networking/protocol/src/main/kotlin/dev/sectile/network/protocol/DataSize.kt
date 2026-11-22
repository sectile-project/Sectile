package dev.sectile.network.protocol

object DataSize {
    const val BYTE = 1
    const val BOOLEAN = 1
    const val SHORT = 2
    const val CHAR = 2
    const val INT = 4
    const val FLOAT = 4
    const val LONG = 8
    const val DOUBLE = 8
    const val UUID = 16

    const val MAX_STRING_LENGTH = 4_096_000
    const val MAX_ARRAY_LENGTH = 4_096_000

    @JvmStatic
    fun varInt(value: Int): Int {
        if (value < 0) throw ProtocolFormatException("VarInt cannot encode negative values: $value")
        if (value == 0) return 1
        return (32 - Integer.numberOfLeadingZeros(value) + 6) / 7
    }

    @JvmStatic
    fun varBytesSize(length: Int): Int = varInt(length) + length

    fun varBytesSize(bytes: ByteArray): Int = varBytesSize(bytes.size)

    @JvmStatic
    fun varStringSize(value: String): Int = varBytesSize(utf8Length(value))

    @JvmStatic
    fun stringListSize(values: List<String>): Int = varInt(values.size) + values.sumOf(::varStringSize)

    fun varListSize(count: Int, elementSize: Int): Int = varInt(count) + count * elementSize

    inline fun <T> varListSize(values: Collection<T>, itemSize: (T) -> Int): Int = varInt(values.size) + values.sumOf(itemSize)

    private fun utf8Length(value: String): Int {
        for (i in value.indices) {
            if (value[i].code >= 0x80) {
                return value.toByteArray(Charsets.UTF_8).size
            }
        }
        return value.length
    }
}
