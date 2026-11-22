package dev.sectile.engine.command

import dev.sectile.engine.api.Engine
import dev.sectile.engine.api.command.BaseCommand
import dev.sectile.engine.api.command.CommandSender
import dev.sectile.engine.api.command.argument.Arguments
import dev.sectile.engine.api.player.Player
import dev.sectile.engine.api.world.World
import dev.sectile.engine.chat.color.Color
import dev.sectile.engine.chat.component.Message
import dev.sectile.engine.chat.component.receiveMessage
import dev.sectile.engine.command.scope.ScopedCommandRegistryProvider
import dev.sectile.engine.command.tokenizer.CommandTokenizer
import dev.sectile.engine.command.tree.CommandTreeBuilder
import dev.sectile.engine.internal.InternalPlayer
import dev.sectile.engine.internal.registry.InternalPlayerRegistry
import dev.sectile.engine.internal.registry.InternalWorldRegistry
import dev.sectile.engine.world.SimpleWorld
import dev.sectile.kernel.logger.JavaLoggerFormatter
import dev.sectile.kernel.logger.Logger
import dev.sectile.kernel.logger.LoggerFormatter
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.player.MutablePlayerSessionState
import dev.sectile.network.api.player.PlayerAuthentication
import dev.sectile.network.api.player.PlayerSessionState
import dev.sectile.network.api.player.ScheduledTask
import dev.sectile.network.api.player.connection.ConnectionCloseCode
import dev.sectile.network.api.player.connection.ConnectionKey
import dev.sectile.network.api.player.connection.ConnectionStage
import dev.sectile.network.api.player.connection.PlayerConnection
import dev.sectile.network.protocol.chat.outbound.RawMessagePacket
import dev.sectile.network.protocol.chat.outbound.ServerMessagePacket
import dev.sectile.network.protocol.command.outbound.CommandTreeSyncPacket
import dev.sectile.sdk.command.argBoolean
import dev.sectile.sdk.command.argChoices
import dev.sectile.sdk.command.argEnum
import dev.sectile.sdk.command.argPlayer
import dev.sectile.sdk.command.args
import dev.sectile.sdk.command.asPlayerOrNull
import dev.sectile.sdk.command.register
import dev.sectile.sdk.command.subcommand
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CommandSystemTest {

    private enum class SampleMode {
        SURVIVAL,
        CREATIVE,
    }

    @BeforeTest
    fun resetRegistries() {
        InternalPlayerRegistry.all().toList().forEach(InternalPlayerRegistry::unregister)
        InternalWorldRegistry.all().toList().forEach(InternalWorldRegistry::unregister)
        if (!Engine.isInitialized()) {
            Engine.init(InternalPlayerRegistry, createRegistry(), InternalWorldRegistry)
        }
    }

    @Test
    fun `tokenizer handles quotes apostrophes and escapes`() {
        val tokens = CommandTokenizer.tokenize("/say \"hello \\\"world\\\"\" 'single quoted' Let's go")
        assertEquals(listOf("say", "hello \"world\"", "single quoted", "Let's", "go"), tokens)

        val inlineFlag = CommandTokenizer.tokenize("kick Steve --reason=\"bad behavior\" --silent")
        assertEquals(listOf("kick", "Steve", "--reason=bad behavior", "--silent"), inlineFlag)

        assertFailsWith<IllegalArgumentException> {
            CommandTokenizer.tokenize("say \"unclosed")
        }
    }

    @Test
    fun `executes dsl command with direct args and flags`() {
        val world = SimpleWorld("default", "Default")
        InternalWorldRegistry.register(world)
        val registry = createRegistry()
        val player = createPlayer(world, "Alice")
        world.add(player)

        var captured: String? = null
        registry.register("announce", "Broadcasts a message.") {
            aliases("bc")
            args("count", "message")
            flag("upper", "u", description = "Uppercase message")

            executes { sender, args ->
                val count = args.nextInt() ?: return@executes sender.receiveMessage("Invalid count")
                val rawText = args.nextString() ?: return@executes sender.receiveMessage("Missing message")
                val target = args.nextPlayer()?.username ?: "all"
                val text = if (args.hasFlag("upper", "u")) rawText.uppercase() else rawText
                val message = "x$count -> $target: $text"
                captured = message
                sender.receiveMessage(message)
            }
        }

        val sender = RecordingSender()
        assertTrue(registry.execute(sender, "/bc 3 \"Hello world\" --upper"))
        assertEquals("x3 -> all: HELLO WORLD", captured)
        assertEquals("x3 -> all: HELLO WORLD", sender.messages.last())

        assertTrue(registry.execute(sender, "announce 1 \"Let's go\" Alice"))
        assertEquals("x1 -> Alice: Let's go", captured)

        assertFalse(registry.execute(sender, "announce 1 hi --upper=yes"))
    }

    @Test
    fun `resolves subcommands BaseCommand overrides and enum arguments`() {
        val registry = createRegistry()
        var output = ""

        val gmCmd = object : BaseCommand("gamemode", "Sets game mode", listOf("gm"), "sectile.gamemode") {
            init {
                argEnum<SampleMode>("mode")
                subcommand("check", "Checks current mode") {
                    executes { sender, _ ->
                        output = "checked:${sender.name}"
                    }
                }
            }

            override fun execute(sender: CommandSender, args: Arguments) {
                val first = args.nextInt()
                if (first != null && args.size == 2) {
                    output = "variant:$first:${args.nextString()}"
                    return
                }
                args.reset()
                output = "mode:${args.nextEnum<SampleMode>()}"
            }
        }

        registry.register(gmCmd)

        val allowedSender = RecordingSender(permissions = setOf("sectile.gamemode"))
        assertTrue(registry.execute(allowedSender, "gm creative"))
        assertEquals("mode:CREATIVE", output)

        assertTrue(registry.execute(allowedSender, "gamemode 1 testing"))
        assertEquals("variant:1:testing", output)

        assertTrue(registry.execute(allowedSender, "gm check"))
        assertEquals("checked:Tester", output)
    }

    @Test
    fun `asPlayerOrNull distinguishes console sender and player sender`() {
        val world = SimpleWorld("default", "Default")
        InternalWorldRegistry.register(world)
        val player = createPlayer(world, "Bob")
        world.add(player)

        val registry = createRegistry()
        registry.register("me") {
            executes { sender, _ ->
                val p = sender.asPlayerOrNull()
                if (p == null) {
                    sender.receiveMessage("Players only")
                } else {
                    sender.receiveMessage("Hello ${p.username}")
                }
            }
        }

        val console = RecordingSender()
        assertTrue(registry.execute(console, "me"))
        assertEquals("Players only", console.messages.last())

        assertTrue(registry.execute(player, "me"))
        val packets = (player.connection as RecordingConnection).sentPackets
        val msgPacket = packets.filterIsInstance<RawMessagePacket>().last()
        assertEquals("Hello Bob", msgPacket.rawText)
    }

    @Test
    fun `completer suggests commands subcommands args flags and Hytale argTypeIds`() {
        val world = SimpleWorld("lobby", "Lobby")
        InternalWorldRegistry.register(world)
        val alice = createPlayer(world, "Alice")
        val alex = createPlayer(world, "Alex")
        world.add(alice)
        world.add(alex)

        val registry = createRegistry()

        registry.register("kick", "Kicks a player") {
            permission("sectile.kick")
            argPlayer("target")
            argChoices("reason", "spam", "grief")
            flag("silent")
            subcommand("history") {
                executes { _, _ -> }
            }
            executes { _, _ -> }
        }
        registry.register("secret") {
            permission("sectile.secret")
            executes { _, _ -> }
        }

        val sender = RecordingSender(permissions = setOf("sectile.kick"))

        assertEquals(listOf("kick"), registry.suggest(sender, "k"))
        assertEquals(listOf("/kick"), registry.suggest(sender, "/k"))
        assertEquals(setOf("history", "Alice", "Alex"), registry.suggest(sender, "kick ").toSet())
        assertEquals(setOf("Alice", "Alex"), registry.suggest(sender, "kick al").toSet())
        assertEquals(listOf("spam", "grief"), registry.suggest(sender, "kick Alice "))
        assertEquals(listOf("--silent"), registry.suggest(sender, "kick Alice --s"))

        assertEquals(setOf("Alice", "Alex"), registry.completeArgType(sender, "player", "al").toSet())
        assertEquals(listOf("spam"), registry.completeArgType(sender, "cmd:kick:1", "sp"))
    }

    @Test
    fun `command tree builder filters permissions and scoped provider unregisters on close`() {
        val world = SimpleWorld("default", "Default")
        InternalWorldRegistry.register(world)
        val player = createPlayer(world, "Viewer")
        world.add(player)

        val registry = createRegistry()

        val scoped = ScopedCommandRegistryProvider(registry)
        scoped.register("public") {
            argBoolean("flag")
            flag("verbose", description = "Verbose output")
            executes { _, _ -> }
        }
        scoped.register("admin") {
            permission("sectile.admin")
            executes { _, _ -> }
        }

        val tree = CommandTreeBuilder.build(RecordingSender(permissions = emptySet()), registry.commands)
        val entries = tree.commands!!
        assertEquals(1, entries.size)
        assertEquals("public", entries[0].name)
        assertEquals("/public <flag>", entries[0].usageText)
        assertEquals("boolean", entries[0].requiredArgs!![0].argTypeId)
        assertEquals("--verbose", entries[0].optionalArgs!![0].name)
        assertEquals("", entries[0].optionalArgs!![0].hint)
        assertNull(entries[0].optionalArgs!![0].argTypeId)

        val conn = player.connection as RecordingConnection
        assertTrue(conn.sentPackets.any { it is CommandTreeSyncPacket })

        scoped.close()
        assertNull(registry.resolve("public"))
        assertNull(registry.resolve("admin"))
        assertNotNull(conn.sentPackets.lastOrNull() as? CommandTreeSyncPacket)
    }

    @Test
    fun `arguments reader supports cursor navigation flags and typed parsing`() {
        val world = SimpleWorld("arena", "Arena")
        InternalWorldRegistry.register(world)
        val target = createPlayer(world, "Steve")
        world.add(target)

        val sampleUuid = UUID.fromString("12345678-1234-1234-1234-1234567890ab")
        val args = Arguments(
            tokens = listOf(
                "7",
                "-4",
                "yes",
                "3.5",
                "creative",
                sampleUuid.toString(),
                "Steve",
                "arena",
                "tail",
            ),
            flags = setOf("shout", "s"),
        )

        assertEquals(9, args.size)
        assertFalse(args.isEmpty)
        assertTrue(args.isNotEmpty)
        assertTrue(args.hasNext())
        assertEquals(9, args.remaining())
        assertTrue(args.isValidIndex(0))
        assertEquals("7", args[0])
        assertNull(args[99])
        assertTrue(args.hasFlag("shout"))
        assertTrue(args.hasFlag("verbose", "s"))

        assertEquals(7, args.nextPositiveInt())
        assertNull(args.nextPositiveInt())
        assertEquals(true, args.nextBoolean())
        assertEquals(3.5, args.nextDouble())
        assertEquals(SampleMode.CREATIVE, args.nextEnum<SampleMode>())
        assertEquals(sampleUuid, args.nextUuid())
        assertSame(target, args.nextPlayer())
        assertSame(world, args.nextWorld())
        assertEquals(1, args.remaining())
        assertEquals("tail", args.nextString())
        assertFalse(args.hasNext())
        assertEquals(42, args.nextInt(42))
        assertEquals(1.5, args.nextDouble(1.5))

        args.seek(1)
        assertEquals(-4, args.nextInt())
        args.move(2)
        assertEquals(SampleMode.CREATIVE, args.nextEnum<SampleMode>())
        args.reset()
        assertEquals(0, args.index)
    }

    @Test
    fun `adhoc commands consume positional tokens via args and respect flags and negative numbers`() {
        val registry = createRegistry()
        registry.register("dbg") {
            flag("shout", "s")
            executes { sender, args ->
                val text = args.nextString() ?: "empty"
                sender.receiveMessage(if (args.hasFlag("shout")) text.uppercase() else text)
            }
        }
        registry.register("add") {
            executes { sender, args ->
                val a = args.nextInt() ?: return@executes sender.receiveMessage("invalid first")
                val b = args.nextInt() ?: return@executes sender.receiveMessage("invalid second")
                sender.receiveMessage("sum=${a + b}")
            }
        }

        val sender = RecordingSender()
        assertTrue(registry.execute(sender, "/dbg"))
        assertTrue(registry.execute(sender, "/dbg hello"))
        assertTrue(registry.execute(sender, "/dbg asdas -s"))
        assertTrue(registry.execute(sender, "/add 12 -4"))
        assertTrue(registry.execute(sender, "/add 12 nope"))

        assertEquals("empty", sender.messages[0])
        assertEquals("hello", sender.messages[1])
        assertEquals("ASDAS", sender.messages[2])
        assertEquals("sum=8", sender.messages[3])
        assertEquals("invalid second", sender.messages[4])
    }

    @Test
    fun `engine player registry resolves by uuid prefix and exact name`() {
        val world = SimpleWorld("spawn", "Spawn")
        InternalWorldRegistry.register(world)
        val al = createPlayer(world, "Al")
        val alice = createPlayer(world, "Alice")
        world.add(al)
        world.add(alice)

        assertSame(world, Engine.worlds.byId("spawn"))
        assertSame(al, Engine.players.byUuid(al.uuid))
        assertSame(al, Engine.players.byName("al"))
        assertSame(alice, Engine.players.byName("ali"))
        assertNull(Engine.players.byNameExact("ali"))
        assertSame(alice, Engine.players.byNameExact("Alice"))
    }

    @Test
    fun `message optimizes raw vs styled packets supports receiveMessage and broadcast`() {
        val world = SimpleWorld("default", "Default")
        InternalWorldRegistry.register(world)
        val p1 = createPlayer(world, "Alice")
        val p2 = createPlayer(world, "Bob")
        world.add(p1)
        world.add(p2)
        val console = RecordingSender("Console")

        val raw = Message.of("Plain hello")
        assertTrue(raw.isRaw)
        assertEquals(-1, raw.colorRgb)
        assertIs<RawMessagePacket>(raw.toPacket())
        raw.sendTo(listOf(p1, p2, console))

        val conn1 = p1.connection as RecordingConnection
        val conn2 = p2.connection as RecordingConnection
        val rawPacket1 = conn1.sentPackets.last()
        val rawPacket2 = conn2.sentPackets.last()
        assertIs<RawMessagePacket>(rawPacket1)
        assertSame(rawPacket1, rawPacket2)
        assertEquals("Plain hello", console.messages.last())

        val styled = Message.of("Alert ", Color.hex("#FF5500"))
        styled.bold = true
        styled.append("check").apply {
            underline = true
        }

        assertFalse(styled.isRaw)
        assertEquals(0xFF5500, styled.colorRgb)
        assertEquals("Alert check", styled.toPlainText())

        styled.broadcast()
        val broadcast1 = conn1.sentPackets.last()
        val broadcast2 = conn2.sentPackets.last()
        assertIs<ServerMessagePacket>(broadcast1)
        assertSame(broadcast1, broadcast2)
        assertSame(styled, broadcast1.message)

        console.receiveMessage(styled)
        assertEquals("Alert check", console.messages.last())

        val registry = createRegistry()
        registry.register("notify") {
            executes { sender, _ ->
                styled.sendTo(sender)
            }
        }
        assertTrue(registry.execute(p1, "/notify"))
        assertIs<ServerMessagePacket>(conn1.sentPackets.last())
    }

    @Test
    fun `scoped command registry removes custom argTypeId completers on close while keeping builtins`() {
        val world = SimpleWorld("lobby", "Lobby")
        InternalWorldRegistry.register(world)

        val registry = createRegistry()
        val scoped = ScopedCommandRegistryProvider(registry)

        scoped.register(
            object : BaseCommand("warp") {
                init {
                    arg("target", typeId = "warp-target") { listOf("spawn", "arena") }
                    arg("world", typeId = "world") { listOf("lobby") }
                }

                override fun execute(sender: CommandSender, args: Arguments) = Unit
            },
        )

        val sender = RecordingSender()
        assertEquals(listOf("arena", "spawn"), registry.completeArgType(sender, "warp-target", "").sorted())

        scoped.close()

        assertNull(registry.resolve("warp"))
        assertTrue(registry.completeArgType(sender, "warp-target", "").isEmpty())
        assertEquals(listOf("lobby"), registry.completeArgType(sender, "world", ""))
    }

    @Test
    fun `scoped command registry cannot unregister commands from root or other scopes`() {
        val registry = createRegistry()
        registry.register(
            object : BaseCommand("rootcmd") {
                override fun execute(sender: CommandSender, args: Arguments) = Unit
            },
        )

        val scopeA = ScopedCommandRegistryProvider(registry)
        val scopeB = ScopedCommandRegistryProvider(registry)

        scopeA.register(
            object : BaseCommand("cmda") {
                override fun execute(sender: CommandSender, args: Arguments) = Unit
            },
        )
        scopeB.register(
            object : BaseCommand("cmdb") {
                override fun execute(sender: CommandSender, args: Arguments) = Unit
            },
        )

        assertFalse(scopeA.unregister("rootcmd"))
        assertFalse(scopeA.unregister("cmdb"))
        assertTrue(registry.resolve("rootcmd") != null)
        assertTrue(registry.resolve("cmdb") != null)

        assertTrue(scopeA.unregister("cmda"))
        assertNull(registry.resolve("cmda"))

        scopeB.close()
        assertNull(registry.resolve("cmdb"))
        assertTrue(registry.resolve("rootcmd") != null)
    }

    private fun createRegistry(): DefaultCommandRegistry = DefaultCommandRegistry(TestLogger)

    private fun createPlayer(world: World, username: String): Player = InternalPlayer(
        connection = RecordingConnection(),
        uuid = UUID.randomUUID(),
        username = username,
        world = world,
    ).also(InternalPlayerRegistry::register)

    private object TestLogger : Logger {
        override val loggerFormatter: LoggerFormatter = JavaLoggerFormatter
        override fun debug(message: String) = Unit
        override fun log(message: String) = Unit
        override fun info(message: String) = Unit
        override fun warn(message: String) = Unit
        override fun error(message: String) = Unit
        override fun error(message: String, throwable: Throwable) = Unit
    }

    private class RecordingSender(
        override val name: String = "Tester",
        override val uuid: UUID = UUID(0L, 1L),
        private val permissions: Set<String> = emptySet(),
    ) : CommandSender {
        val messages = ArrayList<String>()

        override fun receiveMessage(message: String) {
            messages.add(message)
        }

        override fun hasPermission(permission: String): Boolean = permission in permissions
    }

    private class TestSession : MutablePlayerSessionState {
        override var uuid: UUID = UUID(0L, 0L)
        override var username: String = ""
        override var entityId: Int = 0
        override var ready: Boolean = false
        override var viewRadiusChunks: Int = 6
    }

    private class RecordingConnection : PlayerConnection {
        private val sessionState = TestSession()
        val sentPackets = ArrayList<OutboundPacket>()

        override val stage: ConnectionStage = ConnectionStage.POST_LOGIN
        override val session: PlayerSessionState = sessionState
        override val authentication: PlayerAuthentication? = null
        override val clientCertificate: X509Certificate? = null
        override val sniHostname: String? = null

        override fun sendPacket(packet: OutboundPacket) {
            sentPackets.add(packet)
        }

        override fun sendPackets(vararg packets: OutboundPacket) {
            sentPackets.addAll(packets)
        }

        override fun setStage(stage: ConnectionStage, readTimeout: Duration?) = Unit
        override fun setReadTimeout(timeout: Duration?) = Unit
        override fun beginSession(authentication: PlayerAuthentication, entityId: Int) = Unit
        override fun updateSession(block: MutablePlayerSessionState.() -> Unit) = sessionState.block()
        override fun openAuxiliaryStreams(): CompletableFuture<Void> = CompletableFuture.completedFuture(null)
        override fun execute(task: Runnable) = task.run()
        override fun schedule(delay: Duration, task: Runnable): ScheduledTask = ScheduledTask { }
        override fun scheduleAtFixedRate(initialDelay: Duration, period: Duration, task: Runnable): ScheduledTask = ScheduledTask { }

        override fun onDisconnect(listener: Runnable): AutoCloseable = AutoCloseable { }
        override fun <T : Any> getAttribute(key: ConnectionKey<T>): T? = null
        override fun <T : Any> setAttribute(key: ConnectionKey<T>, value: T?) = Unit
        override fun <T : Any> getOrPutAttribute(key: ConnectionKey<T>, defaultValue: () -> T): T = defaultValue()
        override fun disconnect(reason: String) = Unit
        override fun close(code: ConnectionCloseCode, reason: ByteArray?) = Unit
        override fun getIp(): String = "127.0.0.1"
        override fun isOnline(): Boolean = true
    }
}
