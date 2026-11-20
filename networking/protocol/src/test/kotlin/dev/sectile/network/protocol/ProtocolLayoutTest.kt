package dev.sectile.network.protocol

import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.protocol.auth.AuthGrantPacket
import dev.sectile.network.protocol.auth.AuthTokenPacket
import dev.sectile.network.protocol.auth.ConnectAcceptPacket
import dev.sectile.network.protocol.auth.PasswordAcceptedPacket
import dev.sectile.network.protocol.auth.PasswordRejectedPacket
import dev.sectile.network.protocol.auth.PasswordResponsePacket
import dev.sectile.network.protocol.auth.ServerAuthTokenPacket
import dev.sectile.network.protocol.connection.ClientDisconnectPacket
import dev.sectile.network.protocol.connection.ConnectPacket
import dev.sectile.network.protocol.connection.InsecurePlayerOptionsPacket
import dev.sectile.network.protocol.connection.NetworkTickPacket
import dev.sectile.network.protocol.connection.PingPacket
import dev.sectile.network.protocol.connection.PongPacket
import dev.sectile.network.protocol.connection.RequestInsecurePlayerOptionsPacket
import dev.sectile.network.protocol.connection.ServerDisconnectPacket
import dev.sectile.network.protocol.enums.ClientDisconnectReason
import dev.sectile.network.protocol.enums.ClientType
import dev.sectile.network.protocol.enums.DisconnectType
import dev.sectile.network.protocol.enums.PongType
import dev.sectile.network.protocol.enums.StreamType
import dev.sectile.network.protocol.stream.StreamOpenPacket
import dev.sectile.network.protocol.stream.StreamOpenResponsePacket
import dev.sectile.network.protocol.structures.FormattedMessage
import dev.sectile.network.protocol.structures.HostAddress
import dev.sectile.network.protocol.structures.InstantData
import dev.sectile.network.protocol.structures.PlayerSkin
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Verifies wire layouts for handshake, authentication, and connection lifecycle packets.
 *
 * Expected offsets come from Hytale's generated protocol specification.
 */
class ProtocolLayoutTest {

    private fun serialize(packet: OutboundPacket): ByteArray {
        val buffer = TestWriteBuffer()
        packet.write(buffer)
        return buffer.toByteArray()
    }

    private inline fun wireBytes(block: (TestWriteBuffer) -> Unit): ByteArray {
        val buffer = TestWriteBuffer()
        block(buffer)
        return buffer.toByteArray()
    }

    @Test
    fun `connect matches the canonical layout`() {
        val bytes = wireBytes { buf ->
            buf.writeByte(0x07)
            buf.writeInt(ProtocolSettings.PROTOCOL_CRC.toInt())
            buf.writeInt(ProtocolSettings.PROTOCOL_BUILD_NUMBER)
            ProtocolCodec.writeFixedAscii(buf, "1.2.3", ConnectPacket.CLIENT_VERSION_LENGTH)
            buf.writeByte(ClientType.GAME.ordinal)

            val identitySlot = ProtocolCodec.reserveOffset(buf)
            val languageSlot = ProtocolCodec.reserveOffset(buf)
            val referralDataSlot = ProtocolCodec.reserveOffset(buf)
            val referralSourceSlot = ProtocolCodec.reserveOffset(buf)
            val varStart = buf.index

            buf.writeIntAt(identitySlot, buf.index - varStart)
            ProtocolCodec.writeVarString(buf, "token", ConnectPacket.MAX_IDENTITY_TOKEN_LENGTH)

            buf.writeIntAt(languageSlot, buf.index - varStart)
            ProtocolCodec.writeVarAsciiString(buf, "en", ConnectPacket.MAX_LANGUAGE_LENGTH)

            buf.writeIntAt(referralDataSlot, buf.index - varStart)
            ProtocolCodec.writeVarByteArray(buf, byteArrayOf(1, 2, 3), ConnectPacket.MAX_REFERRAL_DATA_LENGTH)

            buf.writeIntAt(referralSourceSlot, buf.index - varStart)
            HostAddress(25565, "localhost").write(buf)
        }

        // Fixed block: nullBits (1) + crc (4) + build (4) + version (20) + type (1) = 30 bytes.
        assertEquals(0x07, bytes[0].toInt() and 0xFF)
        assertEquals(ProtocolSettings.PROTOCOL_CRC.toInt(), readIntLE(bytes, 1))
        assertEquals(ProtocolSettings.PROTOCOL_BUILD_NUMBER, readIntLE(bytes, 5))
        assertEquals("1.2.3", String(bytes, 9, 5, Charsets.US_ASCII))
        assertEquals(0, bytes[29].toInt())

        // Offset slots: 30, 34, 38, 42 (offsets relative to variable block start).
        assertEquals(0, readIntLE(bytes, 30))
        assertEquals(6, readIntLE(bytes, 34))
        assertEquals(9, readIntLE(bytes, 38))
        assertEquals(13, readIntLE(bytes, 42))
        assertEquals(46 + 6 + 3 + 4 + 12, bytes.size)

        val read = ConnectPacket()
        read.read(TestReadBuffer(bytes))
        assertEquals(ProtocolSettings.PROTOCOL_CRC.toInt(), read.protocolCrc)
        assertEquals("1.2.3", read.clientVersion)
        assertEquals(ClientType.GAME, read.clientType)
        assertEquals("token", read.identityToken)
        assertEquals("en", read.language)
        assertArrayEquals(byteArrayOf(1, 2, 3), read.referralData)
        assertEquals("localhost", read.referralSource!!.host)
        assertEquals(25565, read.referralSource!!.port)
    }

    @Test
    fun `client disconnect is exactly two bytes`() {
        val bytes = byteArrayOf(
            ClientDisconnectReason.PLAYER_ABORT.ordinal.toByte(),
            DisconnectType.CRASH.ordinal.toByte(),
        )
        assertEquals(2, bytes.size)

        val read = ClientDisconnectPacket()
        read.read(TestReadBuffer(bytes))
        assertEquals(ClientDisconnectReason.PLAYER_ABORT, read.reason)
        assertEquals(DisconnectType.CRASH, read.type)
    }

    @Test
    fun `server disconnect carries the reason inline`() {
        val message = FormattedMessage().also { it.rawText = "bye" }
        val packet = ServerDisconnectPacket(message, DisconnectType.DISCONNECT)
        val bytes = serialize(packet)

        assertEquals(0x01, bytes[0].toInt() and 0xFF)
        assertEquals(0, bytes[1].toInt())
        // FormattedMessage begins directly at offset 2 without an offset slot.
        assertEquals(0x20, bytes[2].toInt() and 0xFF)

        val parsedMessage = FormattedMessage().also { it.read(TestReadBuffer(bytes.copyOfRange(2, bytes.size))) }
        assertEquals("bye", parsedMessage.rawText)
    }

    @Test
    fun `ping and pong keep their fixed sizes`() {
        val ping = PingPacket(7, InstantData(123L, 456), 1, 2, 3)
        val pingBytes = serialize(ping)
        assertEquals(28, pingBytes.size)
        assertEquals(7, readIntLE(pingBytes, 0))
        assertEquals(1, readIntLE(pingBytes, 16))
        assertEquals(2, readIntLE(pingBytes, 20))
        assertEquals(3, readIntLE(pingBytes, 24))

        val pongBytes = wireBytes { buf ->
            buf.writeInt(7)
            InstantData(123L, 456).write(buf)
            buf.writeByte(PongType.TICK.ordinal)
            buf.writeShort(9)
        }
        assertEquals(19, pongBytes.size)

        val readPong = PongPacket()
        readPong.read(TestReadBuffer(pongBytes))
        assertEquals(7, readPong.id)
        assertEquals(PongType.TICK, readPong.type)
        assertEquals(9, readPong.packetQueueSize)
        assertEquals(123L, readPong.time.seconds)
        assertEquals(456, readPong.time.nanos)
    }

    @Test
    fun `network tick is eight bytes`() {
        val packet = NetworkTickPacket(1_000, 42)
        val bytes = serialize(packet)
        assertEquals(8, bytes.size)
        assertEquals(1_000, readIntLE(bytes, 0))
        assertEquals(42, readIntLE(bytes, 4))
    }

    @Test
    fun `auth packets use offset slots`() {
        val grant = AuthGrantPacket("grant", "identity")
        val grantBytes = serialize(grant)
        assertEquals(0x03, grantBytes[0].toInt() and 0xFF)
        assertEquals(0, readIntLE(grantBytes, 1))
        assertEquals(6, readIntLE(grantBytes, 5))
        assertEquals("grant", ProtocolCodec.readVarString(TestReadBuffer(grantBytes).also { it.skipTo(9) }, 64, "grant"))

        val tokenBytes = wireBytes { buf ->
            buf.writeByte(0x03)
            val accessSlot = ProtocolCodec.reserveOffset(buf)
            val grantSlot = ProtocolCodec.reserveOffset(buf)
            val varStart = buf.index
            buf.writeIntAt(accessSlot, buf.index - varStart)
            ProtocolCodec.writeVarString(buf, "access", AuthTokenPacket.MAX_ACCESS_TOKEN_LENGTH)
            buf.writeIntAt(grantSlot, buf.index - varStart)
            ProtocolCodec.writeVarString(buf, "serverGrant", AuthTokenPacket.MAX_SERVER_AUTH_GRANT_LENGTH)
        }
        val readToken = AuthTokenPacket()
        readToken.read(TestReadBuffer(tokenBytes))
        assertEquals("access", readToken.accessToken)
        assertEquals("serverGrant", readToken.serverAuthGrant)

        val serverToken = ServerAuthTokenPacket("serverAccess", byteArrayOf(9, 9, 9))
        val serverTokenBytes = serialize(serverToken)
        assertEquals(0x03, serverTokenBytes[0].toInt() and 0xFF)
        assertEquals(0, readIntLE(serverTokenBytes, 1))
        assertEquals(13, readIntLE(serverTokenBytes, 5))
        val serverReader = TestReadBuffer(serverTokenBytes).also { it.skipTo(9) }
        assertEquals("serverAccess", ProtocolCodec.readVarString(serverReader, 64, "token"))
        assertArrayEquals(byteArrayOf(9, 9, 9), ProtocolCodec.readVarByteArray(serverReader, 64, "challenge"))
    }

    @Test
    fun `password packets carry their data inline`() {
        val accept = ConnectAcceptPacket(byteArrayOf(1, 2))
        val acceptBytes = serialize(accept)
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x01, 0x02), acceptBytes)

        val responseBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val readResponse = PasswordResponsePacket()
        readResponse.read(TestReadBuffer(responseBytes))
        assertArrayEquals(byteArrayOf(3, 4), readResponse.hash)

        val rejected = PasswordRejectedPacket(2, byteArrayOf(5))
        val rejectedBytes = serialize(rejected)
        assertEquals(0x01, rejectedBytes[0].toInt() and 0xFF)
        assertEquals(2, readIntLE(rejectedBytes, 1))
        assertEquals(1, rejectedBytes[5].toInt())
        assertEquals(5, rejectedBytes[6].toInt())

        assertEquals(0, serialize(PasswordAcceptedPacket()).size)
    }

    @Test
    fun `insecure player options keeps the uuid and the slots`() {
        val skin = PlayerSkin().also { it.cape = "cape" }
        val bytes = wireBytes { buf ->
            buf.writeByte(0x01)
            buf.writeUUID(UUID(1L, 2L))
            val usernameSlot = ProtocolCodec.reserveOffset(buf)
            val skinSlot = ProtocolCodec.reserveOffset(buf)
            val varStart = buf.index
            buf.writeIntAt(usernameSlot, buf.index - varStart)
            ProtocolCodec.writeVarAsciiString(buf, "player", InsecurePlayerOptionsPacket.MAX_USERNAME_LENGTH)
            buf.writeIntAt(skinSlot, buf.index - varStart)
            skin.write(buf)
        }

        assertEquals(0x01, bytes[0].toInt() and 0xFF)
        assertEquals(0, readIntLE(bytes, 17))
        assertEquals(7, readIntLE(bytes, 21))

        val read = InsecurePlayerOptionsPacket()
        read.read(TestReadBuffer(bytes))
        assertEquals(UUID(1L, 2L), read.uuid)
        assertEquals("player", read.username)
        assertEquals("cape", read.skin!!.cape)
    }

    @Test
    fun `stream packets are minimal`() {
        val openPacket = StreamOpenPacket()
        openPacket.read(TestReadBuffer(byteArrayOf(StreamType.VOICE.ordinal.toByte())))
        assertEquals(StreamType.VOICE, openPacket.type)

        assertEquals(0, serialize(RequestInsecurePlayerOptionsPacket()).size)

        val response = StreamOpenResponsePacket(StreamType.VOICE, false, "no")
        val bytes = serialize(response)
        assertEquals(0x01, bytes[0].toInt() and 0xFF)
        assertEquals(1, bytes[1].toInt())
        assertEquals(0, bytes[2].toInt())
        assertEquals("no", ProtocolCodec.readVarString(TestReadBuffer(bytes).also { it.skipTo(3) }, 16, "reason"))
    }

    @Test
    fun `absent optional fields carry minus one in their slot`() {
        val bytes = wireBytes { buf ->
            buf.writeByte(0x00)
            buf.writeInt(ProtocolSettings.PROTOCOL_CRC.toInt())
            buf.writeInt(ProtocolSettings.PROTOCOL_BUILD_NUMBER)
            ProtocolCodec.writeFixedAscii(buf, "1", ConnectPacket.CLIENT_VERSION_LENGTH)
            buf.writeByte(ClientType.GAME.ordinal)
            buf.writeInt(-1)
            val languageSlot = ProtocolCodec.reserveOffset(buf)
            buf.writeInt(-1)
            buf.writeInt(-1)
            val varStart = buf.index
            buf.writeIntAt(languageSlot, buf.index - varStart)
            ProtocolCodec.writeVarAsciiString(buf, "en", ConnectPacket.MAX_LANGUAGE_LENGTH)
        }

        assertEquals(0x00, bytes[0].toInt() and 0xFF)
        assertEquals(-1, readIntLE(bytes, 30))
        assertEquals(-1, readIntLE(bytes, 38))
        assertEquals(-1, readIntLE(bytes, 42))

        val read = ConnectPacket()
        read.read(TestReadBuffer(bytes))
        assertEquals("1", read.clientVersion)
        assertEquals("en", read.language)
        assertNull(read.identityToken)
        assertNull(read.referralData)
        assertNull(read.referralSource)
    }

    @Test
    fun `varints are canonical`() {
        val buffer = TestWriteBuffer()
        VarInt.write(buffer, 0)
        VarInt.write(buffer, 127)
        VarInt.write(buffer, 128)
        VarInt.write(buffer, 300)
        val bytes = buffer.toByteArray()
        assertEquals(0, bytes[0].toInt())
        assertEquals(127, bytes[1].toInt())
        assertEquals(0x80, bytes[2].toInt() and 0xFF)
        assertEquals(1, bytes[3].toInt())
        assertEquals(0xAC, bytes[4].toInt() and 0xFF)
        assertEquals(2, bytes[5].toInt())

        // A zero continuation terminator in a later group is non-canonical.
        val invalid = byteArrayOf(0x80.toByte(), 0x00)
        var thrown = false
        try {
            VarInt.read(TestReadBuffer(invalid))
        } catch (_: ProtocolFormatException) {
            thrown = true
        }
        assertTrue(thrown, "Non-canonical varint must be rejected")
    }

    private fun readIntLE(bytes: ByteArray, offset: Int): Int {
        var value = 0
        for (shift in 0 until 4) {
            value = value or ((bytes[offset + shift].toInt() and 0xFF) shl (shift * 8))
        }
        return value
    }
}
