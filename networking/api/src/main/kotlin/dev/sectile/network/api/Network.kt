package dev.sectile.network.api

import dev.sectile.network.api.packet.PacketsSender

/**
 * Entry point to the public networking API once the transport layer has initialized.
 *
 * Plugins may only register packet handlers (`PacketHandlerRegistry`) and login stage hooks
 * (`StageHookRegistry`) through their `PluginScope`, ensuring every registration is cleaned up
 * automatically when the plugin disables or reloads.
 */
class Network private constructor(
    private val packetsSender: PacketsSender,
) {
    companion object {

        @Volatile
        private var ref: Network? = null

        val packetsSender: PacketsSender
            get() = connector.packetsSender

        fun init(packetsSender: PacketsSender) {
            if (ref != null) {
                error("Network connector already initialized")
            }
            ref = Network(packetsSender)
        }

        fun isInitialized(): Boolean = ref != null

        private val connector: Network
            get() = ref ?: error("Network connector not initialized")
    }
}
