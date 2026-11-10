package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.bed.BedTier
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.fx.HealEffect
import codes.honor.cobblemondynamics.fx.SleepEffect
import codes.honor.cobblemondynamics.net.WorkSoundPayload
import codes.honor.cobblemondynamics.registry.DynamicsBlocks
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormEntityParticlePacket
import com.mojang.authlib.GameProfile
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.Connection
import net.minecraft.network.PacketSendListener
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.CommonListenerCookie
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.neoforge.common.util.FakePlayer
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import java.util.UUID

/**
 * What the server sends a player beside the workers for two Demo rows, whose particles and poses only a person can judge. A fake
 * player never joins a level, so the test puts a recording fake player in the level's player list, where the effects component
 * finds its recipients. That list is global, so the test has its own batch.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class EffectPacketTests {
    /** A fake player that records each packet the server sends it, with the game time of the send. */
    class Listener(level: ServerLevel, profile: GameProfile) : FakePlayer(level, profile) {
        val sent = ArrayList<Pair<Long, Any>>()

        init {
            connection = Recording(level, this)
        }

        /** A game listener with a connection that has no channel. Every packet stops here, as with NeoForge's fake connection. */
        private class Recording(private val level: ServerLevel, private val listener: Listener) :
            ServerGamePacketListenerImpl(
                level.server,
                Connection(PacketFlow.CLIENTBOUND),
                listener,
                CommonListenerCookie.createInitial(listener.gameProfile, false),
            ) {
            override fun send(packet: Packet<*>) = record(packet)

            override fun send(packet: Packet<*>, sendListener: PacketSendListener?) = record(packet)

            private fun record(packet: Packet<*>) {
                listener.sent += level.gameTime to ((packet as? ClientboundCustomPayloadPacket)?.payload() ?: packet)
            }
        }
    }

    /** A recording player beside the pasture at (7,1,7), in the level's player list until the test ends. */
    private fun listener(helper: GameTestHelper): Listener {
        val uuid = UUID.randomUUID()
        val listener = Listener(helper.level, GameProfile(uuid, "listener_${uuid.toString().take(6)}"))
        val at = helper.absoluteVec(BlockPos(7, 2, 10).bottomCenter)
        listener.moveTo(at.x, at.y, at.z)
        val field = ServerLevel::class.java.getDeclaredField("players").apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        val players = field.get(helper.level) as MutableList<ServerPlayer>
        players += listener
        TestKit.afterTest(helper) { players -= listener }
        return listener
    }

    private fun particlesOn(listener: Listener, entity: PokemonEntity): List<Pair<Long, SpawnSnowstormEntityParticlePacket>> =
        listener.sent.mapNotNull { (tick, payload) ->
            (payload as? SpawnSnowstormEntityParticlePacket)?.takeIf { it.sourceEntityId == entity.id }?.let { tick to it }
        }

    /**
     * A Tired Charmander sleeps on the only Hay Bed and a second one rests in place. For 400 ticks each gets Cobblemon's sleep
     * particles on its `middle` locator every 40 ticks, and no work sound (CD-FX-10, the server's part; manual checklist 25). Then a
     * Clefairy joins and heals one of them. Cobblemon 1.8.4 has no Floral Healing effect, so the healer plays its `special`
     * animation, then its cry, and Cobblemon's heal particles show on the ally (CD-HEAL-15, the server's part; manual checklist 28).
     * Until the heal ends, the ally also gets its sleep particles every 40 ticks, so the heal check leaves those out.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_600, batch = LISTENER_BATCH)
    fun cdFx10Heal15SleepAndHealEffectsReachANearbyPlayer(helper: GameTestHelper) {
        val listener = listener(helper)
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        helper.setBlock(BlockPos(3, 2, 7), DynamicsBlocks.BEDS.getValue(BedTier.HAY).get())
        val sleeper = TestKit.tether(helper, pasture, owner, "charmander level=10")
        val rester = TestKit.tether(helper, pasture, owner, "charmander level=10")
        var step = "sleeper"
        var since = -1L
        var healer: PokemonEntity? = null
        helper.runAtTickTime(1_599) { helper.fail("stuck at '$step': ${TestKit.workerOf(helper, sleeper)?.status}") }
        helper.onEachTick {
            val sleeping = TestKit.workerOf(helper, sleeper) ?: return@onEachTick
            val resting = TestKit.workerOf(helper, rester) ?: return@onEachTick
            when (step) {
                "sleeper" -> {
                    TestKit.moveTo(helper, sleeper, BlockPos(3, 2, 8))
                    sleeping.stamina.fraction = 0.0
                    sleeping.tired = true
                    step = "rester"
                }

                "rester" -> if (sleeping.status == WorkerStatus.SLEEPING) {
                    resting.stamina.fraction = 0.0
                    resting.tired = true
                    step = "watch"
                }

                "watch" -> {
                    if (resting.status != WorkerStatus.RESTING_IN_PLACE) return@onEachTick
                    if (since < 0) {
                        since = helper.level.gameTime
                        listener.sent.clear()
                    }
                    helper.assertTrue(sleeping.status == WorkerStatus.SLEEPING, "the sleeper left its bed: ${sleeping.status}")
                    if (helper.level.gameTime - since < WATCH_TICKS) return@onEachTick
                    for ((what, entity) in listOf("the sleeper" to sleeper, "the worker that rests in place" to rester)) {
                        assertSleepLoop(helper, listener, entity, what)
                    }
                    val sounds = listener.sent.map { it.second }.filterIsInstance<WorkSoundPayload>()
                    helper.assertTrue(sounds.isEmpty(), "CD-FX-10: work sounds went out: $sounds")
                    healer = TestKit.tether(helper, pasture, owner, "clefairy level=50")
                    listener.sent.clear()
                    step = "heal"
                }

                "heal" -> {
                    val healed = listOf(sleeper, rester).firstOrNull { !TestKit.workerOf(helper, it)!!.tired } ?: return@onEachTick
                    assertHealEffect(helper, listener, healer!!, healed)
                    helper.succeed()
                }
            }
        }
    }

    /**
     * A Charmander Works at a furnace. Its model plays `special`, which lasts 3 s in Cobblemon 1.8.4, so each effect names only that
     * animation, and the next effect starts 100 ticks later: 60 ticks of animation, then 40 idle ticks (CD-FX-01, CD-FX-03; the
     * approver's play-test ruling of 2026-11-10). The Ember particles leave the Charmander with each animation.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_000, batch = LISTENER_BATCH)
    fun cdFx01AnimationPlaysToItsEndAndTheWorkerIdlesBeforeTheNext(helper: GameTestHelper) {
        val listener = listener(helper)
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        TestKit.furnace(helper, BlockPos(7, 2, 3), ItemStack(Items.RAW_IRON, 64))
        val charmander = TestKit.tether(helper, pasture, owner, "charmander level=10")
        helper.onEachTick {
            val plays = listener.sent.mapNotNull { (tick, payload) ->
                (payload as? PlayPosableAnimationPacket)?.takeIf { it.entityId == charmander.id }?.let { tick to it.animation }
            }
            if (plays.size < 3) return@onEachTick
            val names = plays.map { it.second }.toSet()
            helper.assertTrue(names == setOf(setOf("special")), "CD-FX-03: the Charmander's animations were $names")
            val ticks = plays.map { it.first }
            helper.assertTrue(ticks.zipWithNext { a, b -> b - a }.all { it == 100L }, "CD-FX-01: the animations started at ticks $ticks")
            val embers = particlesOn(listener, charmander).map { it.first }.toSet()
            helper.assertTrue(embers.containsAll(ticks), "CD-FX-01: the particles left at ticks $embers, the animations at $ticks")
            helper.succeed()
        }
    }

    private fun assertSleepLoop(helper: GameTestHelper, listener: Listener, entity: PokemonEntity, what: String) {
        val shots = particlesOn(listener, entity)
        val ids = shots.map { it.second.effectId }.toSet()
        helper.assertTrue(ids == SleepEffect.PARTICLES.toSet(), "CD-FX-10: $what got the particles $ids")
        helper.assertTrue(shots.all { it.second.sourceLocators == SleepEffect.LOCATORS }, "CD-FX-10: $what got other locators")
        val ticks = shots.map { it.first }.distinct().sorted()
        val gaps = ticks.zipWithNext { a, b -> b - a }.toSet()
        helper.assertTrue(gaps == setOf(SleepEffect.LOOP_TICKS.toLong()), "CD-FX-10: $what got the sleep particles at ticks $ticks")
        helper.assertTrue(ticks.size >= WATCH_TICKS / SleepEffect.LOOP_TICKS, "CD-FX-10: $what got the particles ${ticks.size} times")
    }

    private fun assertHealEffect(helper: GameTestHelper, listener: Listener, healer: PokemonEntity, ally: PokemonEntity) {
        val animations = listener.sent.map { it.second }.filterIsInstance<PlayPosableAnimationPacket>().filter { it.entityId == healer.id }
        val fallback = HealEffect.FALLBACK_ANIMATIONS.toSet()
        helper.assertTrue(
            animations.any {
                it.animation == fallback
            },
            "CD-HEAL-15: the healer's animations were ${animations.map { it.animation }}",
        )
        // The ally sleeps until the heal ends, so its sleep loop (CD-FX-10) can reach the player in the same window.
        val shots = particlesOn(listener, ally).filterNot { it.second.effectId in SleepEffect.PARTICLES }
        val ids = shots.map { it.second.effectId }.toSet()
        helper.assertTrue(ids == HealEffect.FALLBACK_PARTICLES.toSet(), "CD-HEAL-15: the ally got the particles $ids")
        helper.assertTrue(
            shots.all {
                it.second.sourceLocators == HealEffect.ALLY_LOCATORS
            },
            "CD-HEAL-15: the heal particles use other locators",
        )
    }

    private companion object {
        const val LISTENER_BATCH = "effect_listener"
        const val WATCH_TICKS = 400
    }
}
