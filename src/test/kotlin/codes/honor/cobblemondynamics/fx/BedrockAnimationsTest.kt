package codes.honor.cobblemondynamics.fx

import codes.honor.cobblemondynamics.role.BuiltInRoles
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** How the server picks a worker's animation and its length from Cobblemon's client assets, as Cobblemon's client picks it. */
class BedrockAnimationsTest {
    @TempDir
    lateinit var dir: Path

    private val alpha = ResourceLocation.parse("cobblemon:alpha")
    private val beta = ResourceLocation.parse("cobblemon:beta")

    private fun write(relative: String, json: String) {
        val path = dir.resolve(relative)
        Files.createDirectories(path.parent)
        Files.writeString(path, json)
    }

    /** Two species. Alpha has a JSON poser and an Alolan form. Beta has no JSON poser, as Cobblemon's Kotlin models have none. */
    private fun fixture(): BedrockAnimations {
        write(
            "resolvers/0001_alpha/0_alpha_base.json",
            """{ "species": "cobblemon:alpha", "order": 0, "variations": [
                 { "aspects": [], "poser": "cobblemon:alpha", "model": "cobblemon:alpha.geo" },
                 { "aspects": ["shiny"], "texture": "cobblemon:textures/pokemon/alpha_shiny.png" },
                 { "aspects": ["alolan"], "poser": "cobblemon:alpha_alolan" } ] }""",
        )
        write(
            "resolvers/0002_beta/0_beta_base.json",
            """{ "species": "cobblemon:beta", "variations": [ { "poser": "cobblemon:beta" } ] }""",
        )
        write(
            "posers/0001_alpha/alpha.json",
            """{ "animations": {
                 "special": "q.bedrock_primary('alpha', 'special', q.curve('symmetrical_wide'))",
                 "physical": "q.bedrock_primary('alpha', 'strike', 'look', q.curve('symmetrical_wide'))",
                 "status": "q.bedrock_primary('alpha', 'status', q.curve('symmetrical_wide'))",
                 "loud": "q.bedrock_primary('alpha', 'loud')",
                 "flap": "q.bedrock_primary('alpha', 'flap')",
                 "quiet": "q.bedrock_primary('alpha', 'quiet')" } }""",
        )
        write("posers/0001_alpha/alpha_alolan.json", """{ "animations": { "special": "q.bedrock_primary('alpha', 'special_alolan')" } }""")
        write(
            "animations/0001_alpha/alpha.animation.json",
            """{ "animations": {
                 "animation.alpha.special": { "animation_length": 3.0 },
                 "animation.alpha.strike": { "animation_length": 2.0 },
                 "animation.alpha.special_alolan": { "animation_length": 4.0 },
                 "animation.alpha.status": { "loop": true },
                 "animation.alpha.loud": { "animation_length": 2.0, "sound_effects": { "0.0": { "effect": "pokemon.alpha.cry" } } },
                 "animation.alpha.flap": { "animation_length": 2.0,
                   "timeline": { "0.5": "q.has_entity ? { q.sound('animation.leather.wing_flap.medium'); };" } },
                 "animation.alpha.quiet": { "animation_length": 2.0, "sound_effects": {}, "timeline": { "0.5": "v.x = 1;" } } } }""",
        )
        write(
            "animations/0002_beta/beta.animation.json",
            """{ "animations": { "animation.beta.special": { "animation_length": 1.5 },
                 "animation.beta.physical": { "animation_length": 3.0833 } } }""",
        )
        return BedrockAnimations(dir)
    }

    @Test
    fun `CD-FX-03 the first name of the role's list that the model has plays, with the length of its animation`() {
        assertEquals(WorkAnimation("special", 60), fixture().first(alpha, emptySet(), listOf("ember", "fire", "special", "physical")))
    }

    @Test
    fun `a poser entry can point to an animation of another name`() {
        assertEquals(WorkAnimation("physical", 40), fixture().first(alpha, emptySet(), listOf("physical")))
    }

    @Test
    fun `the poser is the one of the last variation whose aspects the Pokémon has`() {
        val animations = fixture()
        assertEquals(WorkAnimation("special", 80), animations.first(alpha, setOf("alolan", "shiny"), listOf("special")))
        assertEquals(WorkAnimation("special", 60), animations.first(alpha, setOf("shiny"), listOf("special")))
    }

    @Test
    fun `a model with no JSON poser plays the animation named after its poser`() {
        val animations = fixture()
        assertEquals(WorkAnimation("special", 30), animations.first(beta, emptySet(), listOf("watergun", "special")))
        assertEquals(WorkAnimation("physical", 62), animations.first(beta, emptySet(), listOf("physical")))
    }

    @Test
    fun `an animation with no length is passed over`() {
        assertEquals(WorkAnimation("special", 60), fixture().first(alpha, emptySet(), listOf("status", "special")))
    }

    @Test
    fun `a model with none of the names, and a species with no assets, play no animation`() {
        val animations = fixture()
        assertNull(animations.first(alpha, emptySet(), listOf("pound", "punch")))
        assertNull(animations.first(ResourceLocation.parse("cobblemon:gamma"), emptySet(), listOf("special")))
    }

    /** The approver's play-test ruling (2026-11-10): work is silent, so an animation with a sound keyframe never plays. */
    @Test
    fun `CD-FX-09 an animation that plays a sound, through a sound keyframe or a timeline, is passed over`() {
        assertEquals(WorkAnimation("quiet", 40), fixture().first(alpha, emptySet(), listOf("loud", "flap", "quiet")))
        assertNull(fixture().first(alpha, emptySet(), listOf("loud", "flap")))
    }

    private val jar by lazy { BedrockAnimations(CobblemonJar.bedrock.resolve("pokemon")) }

    private fun animationsOf(role: String) = BuiltInRoles.parse(role).effect.animations

    private fun animationOf(species: String, role: String) =
        jar.first(ResourceLocation.parse("cobblemon:$species"), emptySet(), animationsOf(role))

    /**
     * Cobblemon 1.8.4 gives Machop, Eevee, Vaporeon, Grimer, Rockruff and Dragonair no `special` or `physical` animation. They used to
     * fall back to their cry, which played the cry's sound on every loop. Now they play no animation, and their particles loop every
     * 40 ticks.
     */
    @Test
    fun `CD-FX-09 a worker whose model has no attack animation plays no animation, and its particles loop every 40 ticks`() {
        val cases = listOf(
            "machop" to "cranker",
            "eevee" to "cranker",
            "vaporeon" to "wheel_driver",
            "grimer" to "brewer",
            "rockruff" to "crystal_tender",
            "dragonair" to "superheater",
        )
        for ((species, role) in cases) {
            assertNull(animationOf(species, role), species)
            assertEquals(40, EffectClock.periodTicks(null, BuiltInRoles.parse(role).effect), species)
        }
    }

    /** Bellibolt's `special` animation plays its cry, and Volcarona's beats its wings with a sound, in Cobblemon 1.8.4. */
    @Test
    fun `CD-FX-09 a model whose attack animation plays a sound plays no animation`() {
        assertNull(animationOf("bellibolt", "generator"))
        assertNull(animationOf("volcarona", "stoker"))
    }

    /** Cobblemon 1.8.4: Diglett's poser defines `physical` and none of the Cultivator's other names. */
    @Test
    fun `CD-FX-03 a Diglett Cultivator plays physical`() {
        assertEquals("physical", animationOf("diglett", "cultivator")?.name)
    }

    @Test
    fun `CD-HEAL-15 a Clefairy healer plays its 3 s special animation`() {
        val clefairy = jar.first(ResourceLocation.parse("cobblemon:clefairy"), emptySet(), HealEffect.FALLBACK_ANIMATIONS)
        assertEquals(WorkAnimation("special", 60), clefairy)
    }

    /** Cobblemon 1.8.4: Charmander's JSON poser maps `special` to `animation.charmander.special`, which lasts 3 s. */
    @Test
    fun `CD-FX-01 a Charmander Stoker plays its 3 s special animation, so its loop lasts 100 ticks`() {
        val animation = jar.first(ResourceLocation.parse("cobblemon:charmander"), emptySet(), animationsOf("stoker"))
        assertEquals(WorkAnimation("special", 60), animation)
        assertEquals(100, EffectClock.periodTicks(animation, BuiltInRoles.parse("stoker").effect))
    }

    /** Cobblemon 1.8.4 builds Squirtle's model in Kotlin, with no JSON poser, and names its animation `animation.squirtle.special`. */
    @Test
    fun `CD-FX-01 a Squirtle Wheel Driver plays its 3 s special animation, so its loop lasts 100 ticks`() {
        val animation = jar.first(ResourceLocation.parse("cobblemon:squirtle"), emptySet(), animationsOf("wheel_driver"))
        assertEquals(WorkAnimation("special", 60), animation)
        assertEquals(100, EffectClock.periodTicks(animation, BuiltInRoles.parse("wheel_driver").effect))
    }
}
