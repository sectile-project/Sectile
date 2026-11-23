package dev.sectile.engine.command

import dev.sectile.engine.api.Engine
import dev.sectile.engine.api.command.CommandDispatcher
import dev.sectile.network.api.Network
import dev.sectile.network.api.packet.handler.on
import dev.sectile.network.api.player.connection.ConnectionKey
import dev.sectile.network.api.player.connection.ConnectionStage
import dev.sectile.network.api.player.stage.StageHooks
import dev.sectile.network.api.player.stage.StageSteps
import dev.sectile.network.api.player.stage.StepDecision
import dev.sectile.network.api.player.stage.StepPoint
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.chat.inbound.ChatMessagePacket
import dev.sectile.network.protocol.chat.outbound.RawMessagePacket
import dev.sectile.network.protocol.command.inbound.ArgValuesRequestPacket
import dev.sectile.network.protocol.command.outbound.ArgValuesResponsePacket

internal object CommandPacketHandler {

    private const val MAX_WIRE_SUGGESTIONS = 150
    private const val MAX_ARG_TYPE_ID_LENGTH = 128
    private const val MAX_PARTIAL_LENGTH = 256
    private const val ARG_REQUEST_COOLDOWN_MS = 50L
    private const val INVALID_CHAT_REASON = "server.general.disconnect.invalidChatMessage"

    private val ARG_RATE_LIMIT_KEY =
        ConnectionKey<MutableMap<String, Long>>("engine.command.argValuesRateLimit")

    fun register(dispatcher: CommandDispatcher) {
        registerCommandTreeHook(dispatcher)
        registerCommandChatHandler(dispatcher)
        registerArgValuesHandler(dispatcher)
    }

    private fun registerCommandTreeHook(dispatcher: CommandDispatcher) {
        StageHooks.after(StepPoint(ConnectionStage.POST_LOGIN, StageSteps.POST_LOGIN_WORLD_JOIN)) { ctx ->
            val player = Engine.players.byConnection(ctx.connection) ?: return@after StepDecision.Continue
            dispatcher.syncCommandTree(player)
            StepDecision.Continue
        }
    }

    private fun registerCommandChatHandler(dispatcher: CommandDispatcher) {
        Network.packetHandlers.on(ProtocolPacketTable.CHAT_MESSAGE, ::ChatMessagePacket) { connection, packet ->
            val message = packet.message
            if (message.isNullOrEmpty()) {
                connection.disconnect(INVALID_CHAT_REASON)
                return@on
            }
            if (message[0] != '/') return@on

            if (containsControlCharacters(message)) {
                connection.sendPacket(RawMessagePacket(rawText = "Message contains invalid characters."))
                return@on
            }

            val player = Engine.players.byConnection(connection) ?: return@on
            dispatcher.execute(player, message.substring(1))
        }
    }

    private fun registerArgValuesHandler(dispatcher: CommandDispatcher) {
        Network.packetHandlers.on(
            ProtocolPacketTable.ARG_VALUES_REQUEST,
            ::ArgValuesRequestPacket,
        ) { connection, packet ->
            val argTypeId = packet.argTypeId
            if (argTypeId == null || argTypeId.length > MAX_ARG_TYPE_ID_LENGTH) return@on
            val partial = packet.partial
            if (partial != null && partial.length > MAX_PARTIAL_LENGTH) return@on

            val player = Engine.players.byConnection(connection) ?: return@on

            val rateMap = connection.getOrPutAttribute(ARG_RATE_LIMIT_KEY) { HashMap() }
            val now = System.currentTimeMillis()
            val lastRequest = rateMap[argTypeId] ?: 0L
            if (now - lastRequest < ARG_REQUEST_COOLDOWN_MS) return@on
            rateMap[argTypeId] = now

            val suggestions = dispatcher.completeArgType(player, argTypeId, partial ?: "")
            val truncated = suggestions.size > MAX_WIRE_SUGGESTIONS
            val values = if (truncated) suggestions.take(MAX_WIRE_SUGGESTIONS) else suggestions

            connection.sendPacket(
                ArgValuesResponsePacket(
                    argTypeId = argTypeId,
                    values = values,
                    continuations = List(values.size) { false },
                    isComplete = !truncated,
                ),
            )
        }
    }

    private fun containsControlCharacters(text: String): Boolean {
        for (c in text) {
            val code = c.code
            if (code < 0x20 || code == 0x7F) {
                return true
            }
        }
        return false
    }
}
