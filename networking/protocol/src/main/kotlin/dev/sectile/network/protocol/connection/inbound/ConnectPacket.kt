package dev.sectile.network.protocol.connection.inbound

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.readSlot
import dev.sectile.network.api.packet.InboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolFormatException
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.ProtocolSettings
import dev.sectile.network.protocol.connection.enums.ClientType
import dev.sectile.network.protocol.shared.HostAddress

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

    override val definition: PacketDefinition = ProtocolPacketTable.CONNECT

    override fun read(buf: ReadBuffer) {
        val base = buf.index
        val nullBits = buf.readUnsignedByte()
        protocolCrc = buf.readInt()
        protocolBuildNumber = buf.readInt()
        clientVersion = buf.readFixedAscii(CLIENT_VERSION_LENGTH)
        val clientTypeOrdinal = buf.readUnsignedByte()
        clientType = ClientType.of(clientTypeOrdinal)
            ?: throw ProtocolFormatException("Invalid enum value 'ClientType': $clientTypeOrdinal")

        val varBlockStart = base + VARIABLE_BLOCK_START
        buf.skipTo(varBlockStart)

        identityToken = buf.readSlot(base + 30, varBlockStart, nullBits and 0x01 != 0, "IdentityToken") {
            buf.readVarString(MAX_IDENTITY_TOKEN_LENGTH, "IdentityToken")
        }

        buf.requireSlot(base + 34, buf.index - varBlockStart, "Language")
        language = buf.readVarAsciiString(MAX_LANGUAGE_LENGTH, "Language")

        referralData = buf.readSlot(base + 38, varBlockStart, nullBits and 0x02 != 0, "ReferralData") {
            buf.readVarByteArray(MAX_REFERRAL_DATA_LENGTH, "ReferralData")
        }

        referralSource = buf.readSlot(base + 42, varBlockStart, nullBits and 0x04 != 0, "ReferralSource") {
            HostAddress().also { it.read(buf) }
        }
    }

    companion object {
        const val FIXED_BLOCK_SIZE: Int = 30
        const val VARIABLE_BLOCK_START: Int = 46
        const val CLIENT_VERSION_LENGTH: Int = 20
        const val MAX_IDENTITY_TOKEN_LENGTH: Int = 8_192
        const val MAX_LANGUAGE_LENGTH: Int = 100
        const val MAX_REFERRAL_DATA_LENGTH: Int = 4_096
    }
}
