package dev.sectile.network.protocol.entity.structures

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.protocol.entity.contracts.ComponentUpdate
import dev.sectile.network.protocol.entity.enums.ComponentUpdateType
import dev.sectile.network.protocol.player.structures.MovementStates

class MovementStatesComponentUpdate(
    var movementStates: MovementStates = MovementStates(),
) : ComponentUpdate {

    override val typeId: Int = ComponentUpdateType.MOVEMENT_STATES.id

    override fun write(buf: WriteBuffer) {
        movementStates.write(buf)
    }

    override fun size(): Int = MovementStates.SIZE
}
