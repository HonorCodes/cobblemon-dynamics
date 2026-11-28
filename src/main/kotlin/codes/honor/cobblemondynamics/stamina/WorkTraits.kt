package codes.honor.cobblemondynamics.stamina

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.config.EngineSettings
import codes.honor.cobblemondynamics.handler.capability.FeCurve
import codes.honor.cobblemondynamics.role.RoleDefinition
import com.cobblemon.mod.common.api.moves.categories.DamageCategories
import com.cobblemon.mod.common.api.pokemon.feature.FlagSpeciesFeature
import com.cobblemon.mod.common.api.pokemon.feature.SpeciesFeature
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.resources.ResourceLocation
import java.util.UUID

/** Trait bitmask flags for a worker's active progression bonuses (ADR-0023). */
object TraitFlags {
    const val SHINY = 1
    const val ALPHA = 2
    const val MAJESTIC = 4
    const val MASTERY = 8
    const val MATE = 16

    fun has(mask: Int, flag: Int): Boolean = (mask and flag) != 0
}

/** Plain facts of one move in a worker's moveset (CD-PRG-05). */
data class MoveFacts(val name: String, val type: String, val power: Double, val isStatus: Boolean)

/** Plain facts of one tethered worker for pasture-wide Mate evaluation (CD-PRG-07). */
data class MateCandidate(val pokemonId: UUID, val species: ResourceLocation, val gender: Gender)

/** Evaluated traits and multipliers of one worker (ADR-0023). */
data class EvaluatedTraits(val flags: Int, val masteryMoves: List<String>, val traitMultiplier: Double, val combinedMultiplier: Double) {
    val isShiny: Boolean get() = TraitFlags.has(flags, TraitFlags.SHINY)
    val isAlpha: Boolean get() = TraitFlags.has(flags, TraitFlags.ALPHA)
    val isMajestic: Boolean get() = TraitFlags.has(flags, TraitFlags.MAJESTIC)
    val hasMastery: Boolean get() = TraitFlags.has(flags, TraitFlags.MASTERY)
    val hasMate: Boolean get() = TraitFlags.has(flags, TraitFlags.MATE)

    companion object {
        val NONE = EvaluatedTraits(0, emptyList(), 1.0, 1.0)
    }
}

/** Pure variant, Move Mastery, Mate, and rarity multiplier rules (CD-PRG-02 to CD-PRG-10). */
object WorkTraits {
    const val MASTERY_MIN_POWER = 60.0

    private const val ALPHA_ASPECT = "alpha"
    private const val MAJESTIC_ASPECT = "majestic"
    private const val COBBLEMON = "cobblemon"

    private val NIDORAN_FAMILY = setOf("nidoranm", "nidorino", "nidoking", "nidoranf", "nidorina", "nidoqueen")
    private val VOLBEAT_ILLUMISE = setOf("volbeat", "illumise")
    private val TAUROS_MILTANK = setOf("tauros", "miltank")

    private val OUTPUT_OR_PACE_HANDLERS = setOf(
        "fe_push",
        "healing_charge",
        "fluid_fill",
        "telekinesis",
        "magnet",
        "cultivator",
        "pot_cultivator",
        "harvester",
        "pot_harvester",
        "picker",
        "lumberjack",
        "soul_collector",
        "freezer",
        "budding",
        "mekanism_heat",
        "pnc_heat",
        "enchanting",
    )

    fun normalizeMoveName(raw: String): String = raw.substringAfter(':').lowercase().filter { it in 'a'..'z' || it in '0'..'9' }

    fun isAlpha(isAlphaFlag: Boolean, aspects: Set<String> = emptySet(), forcedAspects: Set<String> = emptySet()): Boolean = isAlphaFlag ||
        aspects.any { it.equals(ALPHA_ASPECT, ignoreCase = true) } ||
        forcedAspects.any { it.equals(ALPHA_ASPECT, ignoreCase = true) }

    fun isMajestic(
        aspects: Set<String> = emptySet(),
        forcedAspects: Set<String> = emptySet(),
        features: List<SpeciesFeature> = emptyList(),
    ): Boolean = aspects.any { it.equals(MAJESTIC_ASPECT, ignoreCase = true) } ||
        forcedAspects.any { it.equals(MAJESTIC_ASPECT, ignoreCase = true) } ||
        features.any { it is FlagSpeciesFeature && it.enabled && it.name.equals(MAJESTIC_ASPECT, ignoreCase = true) }

    fun qualifyingMasteryMoves(
        moves: Iterable<MoveFacts>,
        roleTypes: Set<String>,
        roleMasteryMoves: Set<String> = emptySet(),
    ): List<String> {
        val types = roleTypes.mapTo(HashSet()) { it.lowercase() }
        val listed = roleMasteryMoves.mapTo(HashSet(), ::normalizeMoveName)
        return moves.mapNotNull { move ->
            val id = normalizeMoveName(move.name)
            val byPower = !move.isStatus && move.power >= MASTERY_MIN_POWER && move.type.lowercase() in types
            id.takeIf { id.isNotEmpty() && (byPower || id in listed) }
        }.distinct()
    }

    fun matedWorkers(candidates: Iterable<MateCandidate>): Set<UUID> {
        val byFamily = candidates.filter { it.gender == Gender.MALE || it.gender == Gender.FEMALE }.groupBy { mateFamily(it.species) }
        val mated = HashSet<UUID>()
        for (members in byFamily.values) {
            val hasMale = members.any { it.gender == Gender.MALE }
            val hasFemale = members.any { it.gender == Gender.FEMALE }
            if (hasMale && hasFemale) members.mapTo(mated) { it.pokemonId }
        }
        return mated
    }

    private fun mateFamily(species: ResourceLocation): String {
        val path = species.path.lowercase()
        if (species.namespace.equals(COBBLEMON, ignoreCase = true)) {
            when (path) {
                in NIDORAN_FAMILY -> return "$COBBLEMON:nidoran_family"
                in VOLBEAT_ILLUMISE -> return "$COBBLEMON:volbeat_illumise"
                in TAUROS_MILTANK -> return "$COBBLEMON:tauros_miltank"
            }
        }
        return "${species.namespace.lowercase()}:$path"
    }

    fun isOutputOrPace(handler: ResourceLocation): Boolean = handler.namespace == MOD_ID && handler.path in OUTPUT_OR_PACE_HANDLERS

    fun evaluate(
        shiny: Boolean = false,
        alpha: Boolean = false,
        majestic: Boolean = false,
        moves: Iterable<MoveFacts> = emptyList(),
        roleTypes: Set<String> = emptySet(),
        roleMasteryMoves: Set<String> = emptySet(),
        hasMate: Boolean = false,
        labels: Set<String> = emptySet(),
        rareLabels: Set<String> = RoleDefinition.DEFAULT_RARE_LABELS,
        rareMultiplier: Double = 2.0,
        outputOrPace: Boolean = true,
        settings: EngineSettings = EngineSettings.DEFAULT,
    ): EvaluatedTraits {
        val mastery = qualifyingMasteryMoves(moves, roleTypes, roleMasteryMoves)
        var flags = 0
        var traitProduct = 1.0
        if (shiny) {
            flags = flags or TraitFlags.SHINY
            traitProduct *= settings.shinyMultiplier
        }
        if (alpha) {
            flags = flags or TraitFlags.ALPHA
            traitProduct *= settings.alphaMultiplier
        }
        if (majestic) {
            flags = flags or TraitFlags.MAJESTIC
            traitProduct *= settings.majesticMultiplier
        }
        if (mastery.isNotEmpty()) {
            flags = flags or TraitFlags.MASTERY
            traitProduct *= settings.moveMasteryMultiplier
        }
        if (hasMate) {
            flags = flags or TraitFlags.MATE
            traitProduct *= settings.mateMultiplier
        }
        val rareFactor = if (outputOrPace && FeCurve.isRare(labels, rareLabels)) rareMultiplier else 1.0
        return EvaluatedTraits(
            flags = flags,
            masteryMoves = mastery,
            traitMultiplier = traitProduct,
            combinedMultiplier = traitProduct * rareFactor,
        )
    }

    fun ofPokemon(
        pokemon: Pokemon,
        role: RoleDefinition?,
        hasMate: Boolean,
        fallbackTypes: Set<String> = emptySet(),
        settings: EngineSettings,
    ): EvaluatedTraits {
        val moves = pokemon.moveSet.map { move ->
            MoveFacts(
                name = move.name,
                type = move.type.showdownId,
                power = move.power,
                isStatus = move.damageCategory == DamageCategories.STATUS,
            )
        }
        return evaluate(
            shiny = pokemon.shiny,
            alpha = isAlpha(pokemon.isAlpha, pokemon.aspects, pokemon.forcedAspects),
            majestic = isMajestic(pokemon.aspects, pokemon.forcedAspects, pokemon.features),
            moves = moves,
            roleTypes = role?.types ?: fallbackTypes,
            roleMasteryMoves = role?.masteryMoves.orEmpty(),
            hasMate = hasMate,
            labels = pokemon.form.labels,
            rareLabels = role?.rareLabels ?: RoleDefinition.DEFAULT_RARE_LABELS,
            rareMultiplier = role?.rareMultiplier ?: 2.0,
            outputOrPace = role != null && isOutputOrPace(role.handler),
            settings = settings,
        )
    }
}
