package dev.sectile.network.internal.stages

import dev.sectile.network.protocol.world.SetChunkPacket
import dev.sectile.network.protocol.world.SetColumnPacket

/**
 * Sends the chunks around the spawn point.
 *
 * The server does not have a world yet, so the spawn area is sent as empty columns and
 * chunks: the client only needs the shape of the world to start rendering it.
 */
internal object SpawnChunks {

    /** Radius, in chunks, of the spawn area that is sent to the client. */
    const val INITIAL_CHUNK_RADIUS: Int = 2

    /** Sends the empty spawn chunks to the client. */
    fun send(context: StageContext) {
        val connection = context.connection
        for (cx in -INITIAL_CHUNK_RADIUS..INITIAL_CHUNK_RADIUS) {
            for (cz in -INITIAL_CHUNK_RADIUS..INITIAL_CHUNK_RADIUS) {
                connection.sendPackets(
                    SetColumnPacket(cx, cz),
                    SetChunkPacket(cx, 0, cz),
                )
            }
        }
    }
}
