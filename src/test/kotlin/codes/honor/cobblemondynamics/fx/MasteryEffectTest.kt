package codes.honor.cobblemondynamics.fx

import codes.honor.cobblemondynamics.role.EffectDefinition
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class MasteryEffectTest {
    private val pikachu = ResourceLocation.fromNamespaceAndPath("cobblemon", "pikachu")
    private val roleEffect = EffectDefinition(
        loopTicks = 40,
        idleTicks = 40,
        animations = listOf("thundershock", "special"),
        actorParticles = listOf(ResourceLocation.parse("cobblemon:thundershock_actor")),
        targetParticles = listOf(ResourceLocation.parse("cobblemon:thundershock_target")),
    )

    @Test
    fun `CD-PRG-06 picks mastery move silent animation and particles, skips audible entries, and falls back to role effect`() {
        val boltActor = ResourceLocation.parse("cobblemon:thunderbolt_actor")
        val boltTarget = ResourceLocation.parse("cobblemon:thunderbolt_target")
        val loudParticle = ResourceLocation.parse("cobblemon:bite_target")

        val animations = AnimationCatalog { _, _, names ->
            names.firstOrNull { it != "loud_roar" && it != "missing_clip" }?.let { WorkAnimation(it, 24) }
        }
        val silentParticles = ParticleCatalog { id -> id != loudParticle }
        val timelines = MoveTimelineCatalog { move ->
            when (move) {
                "thunderbolt" -> MasteryTimeline(
                    animations = listOf("loud_roar", "thunderbolt", "special"),
                    actorParticles = listOf(boltActor, loudParticle),
                    targetParticles = listOf(boltTarget, loudParticle),
                )

                "particles_only" -> MasteryTimeline(
                    animations = listOf("missing_clip"),
                    actorParticles = listOf(boltActor),
                    targetParticles = emptyList(),
                )

                "all_loud" -> MasteryTimeline(
                    animations = listOf("loud_roar"),
                    actorParticles = listOf(loudParticle),
                    targetParticles = emptyList(),
                )

                else -> null
            }
        }

        val resolved = MasteryEffect.resolve(
            species = pikachu,
            aspects = emptySet(),
            masteryMoves = listOf("thunderbolt"),
            fallback = roleEffect,
            animations = animations,
            timelines = timelines,
            silentParticles = silentParticles,
        )
        assertEquals("thunderbolt", resolved.animation?.name)
        assertEquals(listOf("thunderbolt"), resolved.effect.animations)
        assertEquals(listOf(boltActor), resolved.effect.actorParticles)
        assertEquals(listOf(boltTarget), resolved.effect.targetParticles)

        val particlesOnly = MasteryEffect.resolve(
            species = pikachu,
            aspects = emptySet(),
            masteryMoves = listOf("particles_only"),
            fallback = roleEffect,
            animations = animations,
            timelines = timelines,
            silentParticles = silentParticles,
        )
        assertEquals("thundershock", particlesOnly.animation?.name)
        assertEquals(listOf("thundershock"), particlesOnly.effect.animations)
        assertEquals(listOf(boltActor), particlesOnly.effect.actorParticles)

        val uncataloged = MasteryEffect.resolve(
            species = pikachu,
            aspects = emptySet(),
            masteryMoves = listOf("earthquake"),
            fallback = roleEffect,
            animations = animations,
            timelines = timelines,
            silentParticles = silentParticles,
        )
        assertEquals(roleEffect, uncataloged.effect)
        assertEquals("thundershock", uncataloged.animation?.name)

        val allLoud = MasteryEffect.resolve(
            species = pikachu,
            aspects = emptySet(),
            masteryMoves = listOf("all_loud"),
            fallback = roleEffect,
            animations = animations,
            timelines = timelines,
            silentParticles = silentParticles,
        )
        assertEquals(roleEffect, allLoud.effect)
        assertEquals("thundershock", allLoud.animation?.name)
    }

    @Test
    fun `CD-PRG-06 real Cobblemon jar move timelines filter out sound particles like bite_target and kinesis_actor`() {
        val realParticles = SilentParticleCatalog(CobblemonJar.bedrock.resolve("particles"))
        val realTimelines = PathMoveTimelineCatalog(CobblemonJar.root.resolve("data/cobblemon/action_effects/moves"))
        val realAnimations = BedrockAnimations(CobblemonJar.bedrock.resolve("pokemon"))

        val bite = MasteryEffect.resolve(
            species = pikachu,
            aspects = emptySet(),
            masteryMoves = listOf("bite"),
            fallback = roleEffect,
            animations = realAnimations,
            timelines = realTimelines,
            silentParticles = realParticles,
        )
        assertTrue(ResourceLocation.parse("cobblemon:bite_target") !in bite.effect.targetParticles)
        assertTrue(ResourceLocation.parse("cobblemon:bite_targetspark") in bite.effect.targetParticles)
    }

    @Test
    fun `CD-PRG-06 temp-dir catalogs ignore entity_molang sound steps and fall back when animation and particle files have sounds`(
        @TempDir temp: Path,
    ) {
        val movesDir = Files.createDirectories(temp.resolve("moves"))
        val particlesDir = Files.createDirectories(temp.resolve("particles"))
        val pokemonDir = temp.resolve("pokemon")
        val resolversDir = Files.createDirectories(pokemonDir.resolve("resolvers"))
        val posersDir = Files.createDirectories(pokemonDir.resolve("posers"))
        val animationsDir = Files.createDirectories(pokemonDir.resolve("animations"))

        Files.writeString(
            resolversDir.resolve("0_pikachu_base.json"),
            """
            {
              "species": "cobblemon:pikachu",
              "order": 0,
              "variations": [{ "aspects": [], "poser": "cobblemon:pikachu" }]
            }
            """.trimIndent(),
        )
        Files.writeString(
            posersDir.resolve("pikachu.json"),
            """
            {
              "animations": {
                "thunderbolt": "q.bedrock_primary('pikachu', 'thunderbolt')",
                "roarblast": "q.bedrock_primary('pikachu', 'roarblast')",
                "thundershock": "q.bedrock_primary('pikachu', 'thundershock')"
              }
            }
            """.trimIndent(),
        )
        Files.writeString(
            animationsDir.resolve("pikachu.animation.json"),
            """
            {
              "animations": {
                "animation.pikachu.thunderbolt": { "animation_length": 1.5 },
                "animation.pikachu.thundershock": { "animation_length": 1.0 },
                "animation.pikachu.roarblast": {
                  "animation_length": 1.2,
                  "sound_effects": { "0.0": { "effect": "pokemon.pikachu.roar" } },
                  "timeline": { "0.5": "q.sound('move.roar');" }
                }
              }
            }
            """.trimIndent(),
        )

        Files.writeString(
            particlesDir.resolve("bolt_actor.particle.json"),
            """
            {
              "particle_effect": {
                "description": { "identifier": "cobblemon:bolt_actor" }
              }
            }
            """.trimIndent(),
        )
        Files.writeString(
            particlesDir.resolve("bolt_target.particle.json"),
            """
            {
              "particle_effect": {
                "description": { "identifier": "cobblemon:bolt_target" }
              }
            }
            """.trimIndent(),
        )
        Files.writeString(
            particlesDir.resolve("roar_burst.particle.json"),
            """
            {
              "particle_effect": {
                "description": { "identifier": "cobblemon:roar_burst" },
                "events": {
                  "boom": { "sound_effect": { "event_name": "entity.generic.explode" } }
                }
              }
            }
            """.trimIndent(),
        )

        Files.writeString(
            movesDir.resolve("thunderbolt.json"),
            """
            {
              "timeline": [
                { "type": "entity_molang", "expressions": ["q.sound('move.thunderbolt');"] },
                { "type": "animation", "animation": ["roarblast", "thunderbolt"] },
                { "type": "entity_particles", "effect": "cobblemon:bolt_actor" },
                { "type": "entity_particles", "entityCondition": "q.entity.is_user == false", "effect": "cobblemon:bolt_target" }
              ]
            }
            """.trimIndent(),
        )
        Files.writeString(
            movesDir.resolve("roarblast.json"),
            """
            {
              "timeline": [
                { "type": "animation", "animation": ["roarblast"] },
                { "type": "entity_particles", "effect": "cobblemon:roar_burst" }
              ]
            }
            """.trimIndent(),
        )

        val timelines = PathMoveTimelineCatalog(movesDir)
        val silentParticles = SilentParticleCatalog(particlesDir)
        val bedrockAnimations = BedrockAnimations(pokemonDir)

        val boltResolved = MasteryEffect.resolve(
            species = pikachu,
            aspects = emptySet(),
            masteryMoves = listOf("thunderbolt"),
            fallback = roleEffect,
            animations = bedrockAnimations,
            timelines = timelines,
            silentParticles = silentParticles,
        )
        assertEquals("thunderbolt", boltResolved.animation?.name)
        assertEquals(listOf("thunderbolt"), boltResolved.effect.animations)
        assertEquals(listOf(ResourceLocation.parse("cobblemon:bolt_actor")), boltResolved.effect.actorParticles)
        assertEquals(listOf(ResourceLocation.parse("cobblemon:bolt_target")), boltResolved.effect.targetParticles)

        val roarFallback = MasteryEffect.resolve(
            species = pikachu,
            aspects = emptySet(),
            masteryMoves = listOf("roarblast"),
            fallback = roleEffect,
            animations = bedrockAnimations,
            timelines = timelines,
            silentParticles = silentParticles,
        )
        assertEquals(roleEffect, roarFallback.effect)
        assertEquals("thundershock", roarFallback.animation?.name)
    }
}
