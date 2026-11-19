package dev.sectile.engine.chat.component

import dev.sectile.engine.api.Engine
import dev.sectile.engine.api.MessageReceiver
import dev.sectile.engine.api.player.Player
import dev.sectile.engine.chat.color.Color
import dev.sectile.network.api.NetworkConnector
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.player.connection.PlayerConnection
import dev.sectile.network.protocol.packet.server.FormattedMessageData
import dev.sectile.network.protocol.packet.server.RawMessagePacket
import dev.sectile.network.protocol.packet.server.ServerMessagePacket

class Message(
    override var rawText: String? = null,
    override var messageId: String? = null,
) : FormattedMessageData {

    private var bitmaskData: Byte = 0
    private var _children: MutableList<Message>? = null

    override var link: String? = null

    var color: Color = Color.WHITE
        set(value) {
            setFlag(true, COLOR_MASK)
            field = value
        }

    val hasColor: Boolean
        get() = (bitmaskData.toInt() and COLOR_MASK) != 0

    override val colorRgb: Int
        get() = if (hasColor) color.rgb else -1

    override var bold: Boolean
        get() = (bitmaskData.toInt() and BOLD_MASK) != 0
        set(enabled) = setFlag(enabled, BOLD_MASK)

    override var italic: Boolean
        get() = (bitmaskData.toInt() and ITALIC_MASK) != 0
        set(enabled) = setFlag(enabled, ITALIC_MASK)

    override var monospace: Boolean
        get() = (bitmaskData.toInt() and MONOSPACE_MASK) != 0
        set(enabled) = setFlag(enabled, MONOSPACE_MASK)

    override var strikethrough: Boolean
        get() = (bitmaskData.toInt() and STRIKETHROUGH_MASK) != 0
        set(enabled) = setFlag(enabled, STRIKETHROUGH_MASK)

    override var underline: Boolean
        get() = (bitmaskData.toInt() and UNDERLINE_MASK) != 0
        set(enabled) = setFlag(enabled, UNDERLINE_MASK)

    override var markupEnabled: Boolean
        get() = (bitmaskData.toInt() and MARKUP_MASK) != 0
        set(enabled) = setFlag(enabled, MARKUP_MASK)

    override val children: List<Message>
        get() = _children ?: emptyList()

    val isRaw: Boolean
        get() = bitmaskData == 0.toByte() && link == null && _children.isNullOrEmpty()

    fun clearColor() {
        color = Color.WHITE
        setFlag(false, COLOR_MASK)
    }

    fun append(child: Message): Message = apply {
        val list = _children ?: ArrayList<Message>(2).also { _children = it }
        list.add(child)
    }

    fun append(text: String, color: Color? = null): Message = append(
        Message(rawText = text).apply {
            if (color != null) this.color = color
        },
    )

    fun append(text: String, block: Message.() -> Unit): Message = append(
        Message(rawText = text).apply(block),
    )

    fun toPlainText(): String {
        val self = rawText ?: messageId ?: ""
        val kids = _children
        if (kids.isNullOrEmpty()) return self
        return buildString {
            append(self)
            for (child in kids) {
                append(child.toPlainText())
            }
        }
    }

    fun toPacket(): OutboundPacket = if (isRaw) RawMessagePacket(rawText, messageId) else ServerMessagePacket(this)

    fun sendTo(player: Player) {
        if (player.connection.isOnline()) {
            player.connection.sendPacket(toPacket())
        }
    }

    fun sendTo(receiver: MessageReceiver) {
        if (receiver is Player) {
            sendTo(receiver)
        } else {
            receiver.receiveMessage(toPlainText())
        }
    }

    fun sendTo(vararg receivers: MessageReceiver) {
        if (receivers.isEmpty()) return
        dispatchAll(receivers.asIterable())
    }

    fun sendTo(receivers: Iterable<MessageReceiver>) {
        dispatchAll(receivers)
    }

    fun broadcast() {
        sendTo(Engine.players.all())
    }

    private fun dispatchAll(receivers: Iterable<MessageReceiver>) {
        val connections = ArrayList<PlayerConnection>()
        var plain: String? = null
        for (receiver in receivers) {
            if (receiver is Player) {
                val conn = receiver.connection
                if (conn.isOnline()) {
                    connections.add(conn)
                }
            } else {
                val text = plain ?: toPlainText().also { plain = it }
                receiver.receiveMessage(text)
            }
        }

        if (connections.isEmpty()) return

        val packet = toPacket()
        if (connections.size > 1 && NetworkConnector.isInitialized()) {
            NetworkConnector.packetsSender.sendPacket(connections, packet)
        } else {
            for (conn in connections) {
                conn.sendPacket(packet)
            }
        }
    }

    private fun setFlag(enabled: Boolean, mask: Int) {
        val bits = bitmaskData.toInt()
        bitmaskData = (if (enabled) bits or mask else bits and mask.inv()).toByte()
    }

    companion object {
        private const val BOLD_MASK = 1
        private const val ITALIC_MASK = 1 shl 1
        private const val MONOSPACE_MASK = 1 shl 2
        private const val STRIKETHROUGH_MASK = 1 shl 3
        private const val UNDERLINE_MASK = 1 shl 4
        private const val COLOR_MASK = 1 shl 5
        private const val MARKUP_MASK = 1 shl 6

        fun raw(text: String): Message = Message(rawText = text)

        fun of(text: String, color: Color? = null): Message = Message(rawText = text).apply {
            if (color != null) this.color = color
        }

        fun translatable(messageId: String): Message = Message(messageId = messageId)
    }
}

fun MessageReceiver.receiveMessage(message: Message) = message.sendTo(this)
