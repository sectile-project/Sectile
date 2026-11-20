package dev.sectile.network.protocol.connection

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.packet.InboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolCodec
import dev.sectile.network.protocol.ProtocolFormatException
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.ProtocolSettings
import dev.sectile.network.protocol.enums.ClientType
import dev.sectile.network.protocol.structures.HostAddress

/**
 * Inbound `Connect` packet (id 0, client -> server).
 *
 * Layout:
 *  - nullability bitmask (1 byte),
 *  - protocol CRC (int LE), build number (int LE), client version (20 null-terminated ASCII bytes),
 *    and client type (1 byte),
 *  - four 4-byte offset slots,
 *  - variable block: `identityToken`, `language`, `referralData`, and `referralSource`.
 */
class ConnectPacket(
    var protocolCrc: Int = ProtocolSettings.PROTOCOL_CRC.toInt(),
    var protocolBuildNumber: Int = ProtocolSettings.PROTOCOL_BUILD_NUMBER,
    var clientVersion: String = "",
    var clientType: ClientType = ClientType.GAME,
    var identityToken: String? = null,
    var language: String = "en",
    var referralData: ByteArray? = null,
    var referralSource: HostAddress? = null,
) : InboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.CONNECT

    override fun read(buf: ReadBuffer) {
        val base = buf.index
        val nullBits = buf.readUnsignedByte()
        protocolCrc = buf.readInt()
        protocolBuildNumber = buf.readInt()
        clientVersion = ProtocolCodec.readFixedAscii(buf, CLIENT_VERSION_LENGTH)
        val clientTypeOrdinal = buf.readUnsignedByte()
        clientType = ClientType.of(clientTypeOrdinal)
            ?: throw ProtocolFormatException("Invalid enum value 'ClientType': $clientTypeOrdinal")

        // Offset slots occupy `base + 30` through `base + 45`: the variable block starts after them.
        val varBlockStart = base + VARIABLE_BLOCK_START
        buf.skipTo(varBlockStart)

        if (nullBits and 0x01 != 0) {
            ProtocolCodec.requireSlot(buf, base + 30, buf.index - varBlockStart, "IdentityToken")
            identityToken = ProtocolCodec.readVarString(buf, MAX_IDENTITY_TOKEN_LENGTH, "IdentityToken")
        } else {
            ProtocolCodec.requireSlot(buf, base + 30, -1, "IdentityToken")
            identityToken = null
        }

        ProtocolCodec.requireSlot(buf, base + 34, buf.index - varBlockStart, "Language")
        language = ProtocolCodec.readVarAsciiString(buf, MAX_LANGUAGE_LENGTH, "Language")

        if (nullBits and 0x02 != 0) {
            ProtocolCodec.requireSlot(buf, base + 38, buf.index - varBlockStart, "ReferralData")
            referralData = ProtocolCodec.readVarByteArray(buf, MAX_REFERRAL_DATA_LENGTH, "ReferralData")
        } else {
            ProtocolCodec.requireSlot(buf, base + 38, -1, "ReferralData")
            referralData = null
        }

        if (nullBits and 0x04 != 0) {
            ProtocolCodec.requireSlot(buf, base + 42, buf.index - varBlockStart, "ReferralSource")
            referralSource = HostAddress().also { it.read(buf) }
        } else {
            ProtocolCodec.requireSlot(buf, base + 42, -1, "ReferralSource")
            referralSource = null
        }
    }

    companion object {
        /** Fixed block length in bytes. */
        const val FIXED_BLOCK_SIZE: Int = 30

        /** Start offset of the variable block. */
        const val VARIABLE_BLOCK_START: Int = 46

        /** Fixed length of the ASCII client version field. */
        const val CLIENT_VERSION_LENGTH: Int = 20

        /** Maximum identity token string length. */
        const val MAX_IDENTITY_TOKEN_LENGTH: Int = 8_192

        /** Maximum language string length. */
        const val MAX_LANGUAGE_LENGTH: Int = 100

        /** Maximum referral data byte length. */
        const val MAX_REFERRAL_DATA_LENGTH: Int = 4_096
    }
}
