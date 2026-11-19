package dev.sectile.engine.api

import dev.sectile.engine.api.command.CommandRegistry
import dev.sectile.engine.api.player.PlayerRegistry
import dev.sectile.engine.api.world.WorldRegistry

class Engine private constructor(
    val players: PlayerRegistry,
    val commands: CommandRegistry,
    val worlds: WorldRegistry,
) {

    companion object {
        @Volatile
        private var ref: Engine? = null

        val instance: Engine
            get() = ref ?: error("Engine not initialized.")

        val players: PlayerRegistry get() = instance.players
        val commands: CommandRegistry get() = instance.commands
        val worlds: WorldRegistry get() = instance.worlds

        fun isInitialized(): Boolean = ref != null

        fun init(
            players: PlayerRegistry,
            commands: CommandRegistry,
            worlds: WorldRegistry,
        ) {
            check(ref == null) { "Engine already initialized." }
            ref = Engine(players, commands, worlds)
        }
    }
}
