package dev.sectile.network.protocol.structures

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer

/**
 * Player movement states.
 *
 * 3 bytes of flags plus the number of extra jumps.
 */
class MovementStates(
    var idle: Boolean = false,
    var horizontalIdle: Boolean = false,
    var jumping: Boolean = false,
    var flying: Boolean = false,
    var walking: Boolean = false,
    var running: Boolean = false,
    var sprinting: Boolean = false,
    var crouching: Boolean = false,
    var forcedCrouching: Boolean = false,
    var falling: Boolean = false,
    var fallingFar: Boolean = false,
    var climbing: Boolean = false,
    var inFluid: Boolean = false,
    var swimming: Boolean = false,
    var swimJumping: Boolean = false,
    var onGround: Boolean = false,
    var mantling: Boolean = false,
    var sliding: Boolean = false,
    var mounting: Boolean = false,
    var rolling: Boolean = false,
    var sitting: Boolean = false,
    var gliding: Boolean = false,
    var sleeping: Boolean = false,
    var extraJumpsUsed: Int = 0,
) {

    fun write(buf: WriteBuffer) {
        val bits = ByteArray(3)
        if (idle) bits[0] = (bits[0].toInt() or 0x01).toByte()
        if (horizontalIdle) bits[0] = (bits[0].toInt() or 0x02).toByte()
        if (jumping) bits[0] = (bits[0].toInt() or 0x04).toByte()
        if (flying) bits[0] = (bits[0].toInt() or 0x08).toByte()
        if (walking) bits[0] = (bits[0].toInt() or 0x10).toByte()
        if (running) bits[0] = (bits[0].toInt() or 0x20).toByte()
        if (sprinting) bits[0] = (bits[0].toInt() or 0x40).toByte()
        if (crouching) bits[0] = (bits[0].toInt() or 0x80).toByte()
        if (forcedCrouching) bits[1] = (bits[1].toInt() or 0x01).toByte()
        if (falling) bits[1] = (bits[1].toInt() or 0x02).toByte()
        if (fallingFar) bits[1] = (bits[1].toInt() or 0x04).toByte()
        if (climbing) bits[1] = (bits[1].toInt() or 0x08).toByte()
        if (inFluid) bits[1] = (bits[1].toInt() or 0x10).toByte()
        if (swimming) bits[1] = (bits[1].toInt() or 0x20).toByte()
        if (swimJumping) bits[1] = (bits[1].toInt() or 0x40).toByte()
        if (onGround) bits[1] = (bits[1].toInt() or 0x80).toByte()
        if (mantling) bits[2] = (bits[2].toInt() or 0x01).toByte()
        if (sliding) bits[2] = (bits[2].toInt() or 0x02).toByte()
        if (mounting) bits[2] = (bits[2].toInt() or 0x04).toByte()
        if (rolling) bits[2] = (bits[2].toInt() or 0x08).toByte()
        if (sitting) bits[2] = (bits[2].toInt() or 0x10).toByte()
        if (gliding) bits[2] = (bits[2].toInt() or 0x20).toByte()
        if (sleeping) bits[2] = (bits[2].toInt() or 0x40).toByte()
        buf.writeBytes(bits)
        buf.writeByte(extraJumpsUsed)
    }

    fun read(buf: ReadBuffer) {
        val bits = buf.readBytes(3)
        idle = bits[0].toInt() and 0x01 != 0
        horizontalIdle = bits[0].toInt() and 0x02 != 0
        jumping = bits[0].toInt() and 0x04 != 0
        flying = bits[0].toInt() and 0x08 != 0
        walking = bits[0].toInt() and 0x10 != 0
        running = bits[0].toInt() and 0x20 != 0
        sprinting = bits[0].toInt() and 0x40 != 0
        crouching = bits[0].toInt() and 0x80 != 0
        forcedCrouching = bits[1].toInt() and 0x01 != 0
        falling = bits[1].toInt() and 0x02 != 0
        fallingFar = bits[1].toInt() and 0x04 != 0
        climbing = bits[1].toInt() and 0x08 != 0
        inFluid = bits[1].toInt() and 0x10 != 0
        swimming = bits[1].toInt() and 0x20 != 0
        swimJumping = bits[1].toInt() and 0x40 != 0
        onGround = bits[1].toInt() and 0x80 != 0
        mantling = bits[2].toInt() and 0x01 != 0
        sliding = bits[2].toInt() and 0x02 != 0
        mounting = bits[2].toInt() and 0x04 != 0
        rolling = bits[2].toInt() and 0x08 != 0
        sitting = bits[2].toInt() and 0x10 != 0
        gliding = bits[2].toInt() and 0x20 != 0
        sleeping = bits[2].toInt() and 0x40 != 0
        extraJumpsUsed = buf.readUnsignedByte()
    }

    companion object {
        /** Fixed size of the structure. */
        const val SIZE: Int = 4
    }
}
