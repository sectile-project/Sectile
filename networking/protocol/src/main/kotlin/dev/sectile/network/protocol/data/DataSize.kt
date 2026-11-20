package dev.sectile.network.protocol.data

import dev.sectile.network.protocol.VarInt

object DataSize {
    const val BYTE = 1
    const val SHORT = 2
    const val CHAR = 2
    const val INT = 4
    const val LONG = 8
    const val UUID = 16

    fun varStringSize(value: String): Int = VarInt.sizeOf(value.length) + value.length

    fun stringListSize(values: List<String>): Int = VarInt.sizeOf(values.size) + values.sumOf(::varStringSize)
}
