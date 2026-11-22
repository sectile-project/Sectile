package dev.sectile.network.protocol.entity.contracts

import dev.sectile.network.api.buffer.WriteBuffer

interface ComponentUpdate {
    val typeId: Int

    fun write(buf: WriteBuffer)

    fun size(): Int
}

fun WriteBuffer.writeComponentUpdate(update: ComponentUpdate) {
    writeVarInt(update.typeId)
    update.write(this)
}
