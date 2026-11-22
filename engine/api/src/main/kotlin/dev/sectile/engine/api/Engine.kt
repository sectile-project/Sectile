package dev.sectile.engine.api

import dev.sectile.engine.api.command.CommandDispatcher
import dev.sectile.engine.api.player.PlayerRegistry
import dev.sectile.engine.api.world.WorldCatalog

class Engine private constructor(
    val players: PlayerRegistry,
    val commands: CommandDispatcher,
    val worlds: WorldCatalog,
) {

    companion object {
        @Volatile
        private var ref: Engine? = null

        val instance: Engine
            get() = ref ?: error("Engine not initialized.")

        val players: PlayerRegistry get() = instance.players
        val commands: CommandDispatcher get() = instance.commands
        val worlds: WorldCatalog get() = instance.worlds

        fun isInitialized(): Boolean = ref != null

        fun init(
            players: PlayerRegistry,
            commands: CommandDispatcher,
            worlds: WorldCatalog,
        ) {
            check(ref == null) { "Engine already initialized." }
            ref = Engine(players, commands, worlds)
        }
    }
}
