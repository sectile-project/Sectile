package dev.sectile.network.protocol.entities

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.protocol.VarInt
import dev.sectile.network.protocol.structures.ModelTransform
import dev.sectile.network.protocol.structures.MovementStates

/**
 * Update of a component of an entity.
 *
 * On the wire, the component type id (varint) is followed by the component
 * data.
 */
interface ComponentUpdate {

    /** Type of the component being updated. */
    val typeId: Int

    /** Writes the component data (without the id). */
    fun write(buf: WriteBuffer)

    /** Size of the component data (without the id). */
    fun size(): Int
}

/**
 * Update of an entity's transform (position and orientation).
 */
class TransformComponentUpdate(
    var transform: ModelTransform = ModelTransform(),
) : ComponentUpdate {

    override val typeId: Int = ComponentUpdateType.TRANSFORM.id

    override fun write(buf: WriteBuffer) {
        transform.write(buf)
    }

    override fun size(): Int = ModelTransform.SIZE
}

/**
 * Update of an entity's movement states.
 */
class MovementStatesComponentUpdate(
    var movementStates: MovementStates = MovementStates(),
) : ComponentUpdate {

    override val typeId: Int = ComponentUpdateType.MOVEMENT_STATES.id

    override fun write(buf: WriteBuffer) {
        movementStates.write(buf)
    }

    override fun size(): Int = MovementStates.SIZE
}

/**
 * Writes a component update with its id first.
 */
fun WriteBuffer.writeComponentUpdate(update: ComponentUpdate) {
    VarInt.write(this, update.typeId)
    update.write(this)
}
