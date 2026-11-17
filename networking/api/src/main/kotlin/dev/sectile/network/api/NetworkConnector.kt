package dev.sectile.network.api

import dev.sectile.network.api.packet.PacketsSender
import dev.sectile.network.api.packet.handler.PacketHandlerStorage
import dev.sectile.network.api.player.connection.ConnectionStage

/**
 * Entry point to the public networking API once the transport layer has initialized.
 *
 * The API is the only door to the server that a plugin may use: the internals of the
 * connection lifecycle stay behind it.
 *
 *  - **Gameplay**: register the handlers of the packets the client sends while it plays on
 *    [packetHandlers].
 *  - **Login**: the login is a stage machine and it is extended through
 *    [dev.sectile.network.api.player.stage.StageHooks], never by writing handlers into the
 *    storage of a login stage: a raw handler runs in parallel with the one of the server, in
 *    an undefined order, and it cannot stop the login.
 */
class NetworkConnector private constructor(
    private val stageStorages: Map<ConnectionStage, PacketHandlerStorage>,
    private val packetsSender: PacketsSender,
) {
    companion object {

        @Volatile
        private var ref: NetworkConnector? = null

        /**
         * Active gameplay ([ConnectionStage.POST_LOGIN]) packet handler registry.
         *
         * This is the only packet handler registry a plugin may write into.
         */
        val packetHandlers: PacketHandlerStorage
            get() = handlers(ConnectionStage.POST_LOGIN)

        /** Shared batch packet dispatcher for multi-connection broadcasts. */
        val packetsSender: PacketsSender
            get() = connector.packetsSender

        /**
         * Returns the [PacketHandlerStorage] associated with [stage].
         *
         * Only [ConnectionStage.POST_LOGIN] may be used: the login stages are managed by the
         * stage machine and are extended through
         * [dev.sectile.network.api.player.stage.StageHooks].
         */
        fun handlers(stage: ConnectionStage): PacketHandlerStorage {
            require(stage == ConnectionStage.POST_LOGIN) {
                "The handlers of the stage $stage are managed by the stage machine: use " +
                    "StageHooks to extend the login instead."
            }
            return connector.stageStorages[stage]
                ?: error("No packet handler storage registered for stage $stage")
        }

        /** Initializes the global [NetworkConnector] instance. */
        fun init(
            stageStorages: Map<ConnectionStage, PacketHandlerStorage>,
            packetsSender: PacketsSender,
        ) {
            if (ref != null) {
                error("Network connector already initialized")
            }
            ref = NetworkConnector(stageStorages, packetsSender)
        }

        private val connector: NetworkConnector
            get() = ref ?: error("Network connector not initialized")
    }
}
