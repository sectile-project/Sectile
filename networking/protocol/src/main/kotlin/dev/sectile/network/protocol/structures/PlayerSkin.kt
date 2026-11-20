package dev.sectile.network.protocol.structures

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.protocol.ProtocolCodec

/**
 * Player appearance.
 *
 * Hytale protocol structure: 3 bytes of nullability mask (20 optional fields),
 * 20 offset slots of 4 bytes, and the variable block with the strings.
 * Each part is an ASCII string of at most 96 characters.
 */
class PlayerSkin {

    private val parts: Array<String?> = arrayOfNulls(FIELD_NAMES.size)

    var bodyCharacteristic: String?
        get() = parts[0]
        set(value) {
            parts[0] = value
        }
    var underwear: String?
        get() = parts[1]
        set(value) {
            parts[1] = value
        }
    var face: String?
        get() = parts[2]
        set(value) {
            parts[2] = value
        }
    var eyes: String?
        get() = parts[3]
        set(value) {
            parts[3] = value
        }
    var ears: String?
        get() = parts[4]
        set(value) {
            parts[4] = value
        }
    var mouth: String?
        get() = parts[5]
        set(value) {
            parts[5] = value
        }
    var facialHair: String?
        get() = parts[6]
        set(value) {
            parts[6] = value
        }
    var haircut: String?
        get() = parts[7]
        set(value) {
            parts[7] = value
        }
    var eyebrows: String?
        get() = parts[8]
        set(value) {
            parts[8] = value
        }
    var pants: String?
        get() = parts[9]
        set(value) {
            parts[9] = value
        }
    var overpants: String?
        get() = parts[10]
        set(value) {
            parts[10] = value
        }
    var undertop: String?
        get() = parts[11]
        set(value) {
            parts[11] = value
        }
    var overtop: String?
        get() = parts[12]
        set(value) {
            parts[12] = value
        }
    var shoes: String?
        get() = parts[13]
        set(value) {
            parts[13] = value
        }
    var headAccessory: String?
        get() = parts[14]
        set(value) {
            parts[14] = value
        }
    var faceAccessory: String?
        get() = parts[15]
        set(value) {
            parts[15] = value
        }
    var earAccessory: String?
        get() = parts[16]
        set(value) {
            parts[16] = value
        }
    var skinFeature: String?
        get() = parts[17]
        set(value) {
            parts[17] = value
        }
    var gloves: String?
        get() = parts[18]
        set(value) {
            parts[18] = value
        }
    var cape: String?
        get() = parts[19]
        set(value) {
            parts[19] = value
        }

    /** Writes the structure with the canonical protocol layout. */
    fun write(buf: WriteBuffer) {
        val nullBits = ByteArray(3)
        for (index in parts.indices) {
            if (parts[index] != null) {
                nullBits[index ushr 3] = (nullBits[index ushr 3].toInt() or (1 shl (index and 7))).toByte()
            }
        }
        buf.writeBytes(nullBits)

        val slots = IntArray(parts.size)
        for (index in parts.indices) {
            slots[index] = ProtocolCodec.reserveOffset(buf)
        }

        val varBlockStart = buf.index
        for (index in parts.indices) {
            val value = parts[index]
            if (value == null) {
                buf.writeIntAt(slots[index], -1)
                continue
            }
            buf.writeIntAt(slots[index], buf.index - varBlockStart)
            ProtocolCodec.writeVarAsciiString(buf, value, MAX_PART_LENGTH)
        }
    }

    /** Reads the structure with the canonical protocol layout. */
    fun read(buf: ReadBuffer) {
        val base = buf.index
        val nullBits = buf.readBytes(3)
        val slots = IntArray(parts.size)
        for (index in parts.indices) {
            slots[index] = buf.readIntAt(base + 3 + index * 4)
        }

        // The offset slots span from `base + 3` to `base + 82`.
        val varBlockStart = base + VARIABLE_BLOCK_START
        buf.skipTo(varBlockStart)

        for (index in parts.indices) {
            val present = (nullBits[index ushr 3].toInt() and (1 shl (index and 7))) != 0
            if (!present) {
                ProtocolCodec.requireSlot(buf, base + 3 + index * 4, -1, FIELD_NAMES[index])
                parts[index] = null
                continue
            }
            ProtocolCodec.requireSlot(buf, base + 3 + index * 4, buf.index - varBlockStart, FIELD_NAMES[index])
            parts[index] = ProtocolCodec.readVarAsciiString(buf, MAX_PART_LENGTH, FIELD_NAMES[index])
        }
    }

    companion object {
        /** Maximum length of each part. */
        const val MAX_PART_LENGTH: Int = 96

        /** Maximum size of the serialized structure. */
        const val MAX_SIZE: Int = 2103

        /** Position of the variable block. */
        const val VARIABLE_BLOCK_START: Int = 83

        private val FIELD_NAMES = arrayOf(
            "BodyCharacteristic", "Underwear", "Face", "Eyes", "Ears", "Mouth", "FacialHair", "Haircut",
            "Eyebrows", "Pants", "Overpants", "Undertop", "Overtop", "Shoes", "HeadAccessory",
            "FaceAccessory", "EarAccessory", "SkinFeature", "Gloves", "Cape",
        )
    }
}
