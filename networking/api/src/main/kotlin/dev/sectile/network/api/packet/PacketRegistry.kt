package dev.sectile.network.api.packet

import dev.sectile.network.api.NetworkChannel

/**
 * Global registry of Hytale protocol packet definitions.
 *
 * Each entry describes the packet's id, name, allowed direction, QUIC channel,
 * maximum payload size, and Zstd compression flag. The decoder validates incoming
 * frames against this registry before dispatching to stage handlers: unknown ids,
 * disallowed directions, channel mismatches, and oversized payloads are treated as
 * protocol violations.
 */
object PacketRegistry {

    /** Number of reserved packet slots (Hytale protocol ids reach 527 across 389 packets). */
    const val MAX_PACKETS = 1024

    private val definitions = arrayOfNulls<PacketDefinition>(MAX_PACKETS)
    var maxPacketId: Int = 0
        private set

    /**
     * Registers a packet that the client can send to the server.
     */
    fun registerToServer(
        id: Int,
        name: String,
        channel: NetworkChannel = NetworkChannel.DEFAULT,
        maxSize: Int,
        compressed: Boolean = false,
    ): PacketDefinition = register(
        PacketDefinition(id, name, PacketDirection.TO_SERVER, channel, maxSize, compressed),
    )

    /**
     * Registers a packet that the server can send to the client.
     */
    fun registerToClient(
        id: Int,
        name: String,
        channel: NetworkChannel = NetworkChannel.DEFAULT,
        maxSize: Int,
        compressed: Boolean = false,
    ): PacketDefinition = register(
        PacketDefinition(id, name, PacketDirection.TO_CLIENT, channel, maxSize, compressed),
    )

    /** Registers or replaces a [PacketDefinition] entry. */
    fun register(definition: PacketDefinition): PacketDefinition {
        require(definition.id in 0 until MAX_PACKETS) {
            "Invalid packet id ${definition.id}: must be in 0 until $MAX_PACKETS"
        }
        definitions[definition.id] = definition
        if (definition.id > maxPacketId) {
            maxPacketId = definition.id
        }
        return definition
    }

    /** Registers all [definitions] in bulk. */
    fun registerAll(definitions: Array<PacketDefinition?>) {
        for (definition in definitions) {
            definition?.let { register(it) }
        }
    }

    /** Returns the [PacketDefinition] for [id], or `null` if unregistered. */
    fun get(id: Int): PacketDefinition? {
        if (id < 0 || id >= definitions.size) return null
        return definitions[id]
    }

    /** Returns the [PacketDefinition] for [id] or throws if unregistered. */
    fun require(id: Int): PacketDefinition = get(id) ?: throw IllegalArgumentException("Unregistered packet id: $id")

    /** Returns the [PacketDefinition] for [id] if the client is allowed to send it. */
    fun getToServer(id: Int): PacketDefinition? = get(id)?.takeIf { it.inbound }

    /** Returns the [PacketDefinition] for [id] if the server is allowed to send it. */
    fun getToClient(id: Int): PacketDefinition? = get(id)?.takeIf { it.outbound }

    /** Returns the [NetworkChannel] for packet [id], or `null` if unregistered. */
    fun channelOf(id: Int): NetworkChannel? = get(id)?.channel

    /** Returns the [PacketDirection] for packet [id], or `null` if unregistered. */
    fun directionOf(id: Int): PacketDirection? = get(id)?.direction

    /** Returns all registered [PacketDefinition] entries. */
    fun all(): List<PacketDefinition> = definitions.filterNotNull()
}
