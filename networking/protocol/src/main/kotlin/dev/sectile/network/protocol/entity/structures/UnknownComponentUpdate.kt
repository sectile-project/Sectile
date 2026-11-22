package dev.sectile.network.protocol.entity.structures

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.protocol.entity.contracts.ComponentUpdate

/**
 * Placeholder for inbound component updates that the server does not parse beyond their type ID.
 */
class UnknownComponentUpdate(override val typeId: Int) : ComponentUpdate {

    override fun write(buf: WriteBuffer) = Unit

    override fun size(): Int = 0
}
