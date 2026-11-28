package codes.honor.cobblemondynamics.fx

import codes.honor.cobblemondynamics.role.EffectDefinition
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
        assertEquals(listOf(boltActor), resolved.effect.actorParticles)
        assertEquals(listOf(boltTarget), resolved.effect.targetParticles)

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
}
