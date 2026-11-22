package dev.sectile.network.protocol.entity.structures

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.protocol.entity.contracts.ComponentUpdate
import dev.sectile.network.protocol.entity.enums.ComponentUpdateType
import dev.sectile.network.protocol.shared.math.ModelTransform

class TransformComponentUpdate(
    var transform: ModelTransform = ModelTransform(),
) : ComponentUpdate {

    override val typeId: Int = ComponentUpdateType.TRANSFORM.id

    override fun write(buf: WriteBuffer) {
        transform.write(buf)
    }

    override fun size(): Int = ModelTransform.SIZE
}
