package dev.sectile.network.protocol.structures

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.protocol.ProtocolCodec

/**
 * Reference to a protocol asset.
 *
 * 64 bytes of hash (ASCII, null-terminated) and the asset name as a UTF-8 string.
 */
class Asset(
    var hash: String = "",
    var name: String = "",
) {

    fun write(buf: WriteBuffer) {
        ProtocolCodec.writeFixedAscii(buf, hash, HASH_LENGTH)
        ProtocolCodec.writeVarString(buf, name, MAX_NAME_LENGTH)
    }

    fun read(buf: ReadBuffer) {
        hash = ProtocolCodec.readFixedAscii(buf, HASH_LENGTH)
        name = ProtocolCodec.readVarString(buf, MAX_NAME_LENGTH, "Name")
    }

    companion object {
        /** Length of the hash. */
        const val HASH_LENGTH: Int = 64

        /** Maximum length of the name. */
        const val MAX_NAME_LENGTH: Int = 512

        /** Maximum size of the serialized structure. */
        const val MAX_SIZE: Int = 2117
    }
}
