package codes.honor.cobblemondynamics.fx

import codes.honor.cobblemondynamics.CobblemonDynamics
import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.net.WorkSoundPayload
import codes.honor.cobblemondynamics.registry.DynamicsEntities
import codes.honor.cobblemondynamics.role.EffectDefinition
import codes.honor.cobblemondynamics.role.RoleDefinition
import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext
import com.cobblemon.mod.common.api.moves.animations.ActionEffectTimeline
import com.cobblemon.mod.common.api.moves.animations.ActionEffects
import com.cobblemon.mod.common.api.moves.animations.TargetsProvider
import com.cobblemon.mod.common.api.moves.animations.UsersProvider
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormEntityParticlePacket
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormParticlePacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.PacketDistributor

/** Loops each role's move effect with Cobblemon's own packets, aimed at a focus entity on the target (ADR-0007). */
object EffectPlayer {
    private val particles = CheckedParticles(JarParticleCatalog) { CobblemonDynamics.LOGGER.warn(it) }

    /** Cobblemon's move timelines emit actor particles from `special`, else `target`. Every model has `root`. */
    private val actorLocators = listOf("special", "target", "root")

    /** The focus entity has no model, so the client aims at its position. */
    private val focusLocators = listOf("middle")

    /** The queries that `MoveInstruction` adds for a move timeline in battle. */
    private val battleQueries = listOf("missed", "failed", "hurt", "target_fainted")

    fun tickWorking(
        level: ServerLevel,
        entity: PokemonEntity,
        focus: WorkFocusEntity,
        role: RoleDefinition,
        workingSince: Long,
        now: Long,
    ) {
        if (!EffectClock.shouldPlay(workingSince, now, role.effect.loopTicks)) return
        val players = recipients(level, entity)
        if (players.isNotEmpty()) play(entity, focus, role.effect, players)
    }

    /** CD-FX-10: Cobblemon's sleep particles on a worker that sleeps or rests in place, every 40 ticks, and no sound. */
    fun tickSleeping(level: ServerLevel, entity: PokemonEntity, since: Long, now: Long) {
        if (!SleepEffect.shouldPlay(since, now)) return
        val players = recipients(level, entity)
        if (players.isEmpty()) return
        for (id in particles.filter(SleepEffect.PARTICLES)) {
            SpawnSnowstormEntityParticlePacket(id, entity.id, SleepEffect.LOCATORS).sendToPlayers(players)
        }
    }

    /**
     * A completed heal. When Cobblemon's server data has the Floral Healing timeline, it runs with the healer as its user and the
     * ally as its target (CD-HEAL-16). Otherwise the healer plays its `special` animation, else its cry, and Cobblemon's heal
     * particles show on the ally (CD-HEAL-15).
     */
    fun playHeal(level: ServerLevel, healer: PokemonEntity, ally: PokemonEntity) {
        when (val choice = HealEffect.choose { ActionEffects.actionEffects[it] }) {
            is HealEffect.Choice.FloralHealing -> {
                val timeline = choice.effect as? ActionEffectTimeline ?: return
                timeline.run(floralHealingContext(timeline, healer, ally))
            }

            HealEffect.Choice.Fallback -> {
                val players = recipients(level, healer)
                if (players.isEmpty()) return
                PlayPosableAnimationPacket(healer.id, LinkedHashSet(HealEffect.FALLBACK_ANIMATIONS), emptyList()).sendToPlayers(players)
                for (id in particles.filter(HealEffect.FALLBACK_PARTICLES)) {
                    SpawnSnowstormEntityParticlePacket(id, ally.id, HealEffect.ALLY_LOCATORS).sendToPlayers(players)
                }
            }
        }
    }

    /**
     * The context of a move timeline outside a battle: the healer is the user and the ally the target, as a move instruction sets
     * them. The battle-only queries that move timelines read answer that nothing missed, failed, fainted or was hurt.
     */
    fun floralHealingContext(timeline: ActionEffectTimeline, healer: PokemonEntity, ally: PokemonEntity): ActionEffectContext {
        val runtime = MoLangRuntime().setup()
        val query = runtime.environment.query
        query.addFunction("user") { healer.asMostSpecificMoLangValue() }
        query.addFunction("target") { ally.asMostSpecificMoLangValue() }
        for (name in battleQueries) query.addFunction(name) { DoubleValue.ZERO }
        query.addFunction("instruction_id") { StringValue("cobblemon:move") }
        return ActionEffectContext(
            actionEffect = timeline,
            providers = mutableListOf(UsersProvider(healer), TargetsProvider(ally)),
            runtime = runtime,
            level = healer.level(),
        )
    }

    private fun recipients(level: ServerLevel, entity: PokemonEntity): List<ServerPlayer> {
        val radius = WorkEngineSettings.current.effectBroadcastRadius
        return level.players().filter { Recipients.inRange(it.distanceToSqr(entity), radius) }
    }

    /** The animation goes out even when no particle id is known (CD-FX-11). */
    fun play(entity: PokemonEntity, focus: WorkFocusEntity, effect: EffectDefinition, players: List<ServerPlayer>) {
        PlayPosableAnimationPacket(entity.id, LinkedHashSet(effect.animationsWithCry), emptyList()).sendToPlayers(players)
        for (shot in ParticleShots.of(effect, particles::filter)) {
            when (shot) {
                is ParticleShot.FromWorker -> SpawnSnowstormEntityParticlePacket(shot.id, entity.id, actorLocators, focus.id, focusLocators)
                is ParticleShot.AtFocus -> SpawnSnowstormParticlePacket(shot.id, focus.position())
            }.sendToPlayers(players)
        }
        val sound = effect.sound ?: return
        val payload = WorkSoundPayload(entity.id, sound)
        for (player in players) PacketDistributor.sendToPlayer(player, payload)
    }

    /** At the target center (CD-FX-04). */
    fun spawnFocus(level: ServerLevel, center: Vec3): WorkFocusEntity {
        val focus = WorkFocusEntity(DynamicsEntities.WORK_FOCUS.get(), level)
        focus.moveTo(center.x, center.y, center.z)
        level.addFreshEntity(focus)
        return focus
    }

    fun removeFocus(focus: WorkFocusEntity) {
        focus.discard()
    }
}
