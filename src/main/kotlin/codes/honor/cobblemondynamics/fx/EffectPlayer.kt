package codes.honor.cobblemondynamics.fx

import codes.honor.cobblemondynamics.CobblemonDynamics
import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.net.WorkSoundPayload
import codes.honor.cobblemondynamics.registry.DynamicsEntities
import codes.honor.cobblemondynamics.role.EffectDefinition
import codes.honor.cobblemondynamics.role.RoleDefinition
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

    fun tickWorking(
        level: ServerLevel,
        entity: PokemonEntity,
        focus: WorkFocusEntity,
        role: RoleDefinition,
        workingSince: Long,
        now: Long,
    ) {
        if (!EffectClock.shouldPlay(workingSince, now, role.effect.loopTicks)) return
        val radius = WorkEngineSettings.current.effectBroadcastRadius
        val players = level.players().filter { Recipients.inRange(it.distanceToSqr(entity), radius) }
        if (players.isNotEmpty()) play(entity, focus, role.effect, players)
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
