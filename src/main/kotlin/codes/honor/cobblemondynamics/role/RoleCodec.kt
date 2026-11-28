package codes.honor.cobblemondynamics.role

import codes.honor.cobblemondynamics.stamina.WorkTraits
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.JsonOps
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.ExtraCodecs
import java.util.Optional

/** Parses the body of one role file, `data/{namespace}/cobblemon_dynamics/roles/{role}.json`. */
object RoleCodec {
    private const val SCHEMA = 1
    private const val DEFAULT_TICKS = 40
    private const val CRY = "cry"
    private const val ANY = "any"
    private const val ALL = "all"
    private const val RARE_MULTIPLIER = "rare_multiplier"
    private const val RARE_LABELS = "rare_labels"
    private const val DEFAULT_RARE_MULTIPLIER = 2.0

    private val EFFECT: Codec<EffectDefinition> = RecordCodecBuilder.create { i ->
        i.group(
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("loop_ticks", DEFAULT_TICKS).forGetter { it.loopTicks },
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("idle_ticks", DEFAULT_TICKS).forGetter { it.idleTicks },
            Codec.STRING.listOf().fieldOf("animations").forGetter { it.animations },
            ResourceLocation.CODEC.listOf().optionalFieldOf("actor_particles", emptyList()).forGetter { it.actorParticles },
            ResourceLocation.CODEC.listOf().optionalFieldOf("target_particles", emptyList()).forGetter { it.targetParticles },
        ).apply(i, ::EffectDefinition)
    }

    private data class Body(
        val schema: Int,
        val handler: ResourceLocation,
        val types: List<String>,
        val autoAssign: Boolean,
        val priority: Int,
        val range: Double,
        val params: JsonElement,
        val effect: EffectDefinition,
        val quickWork: Optional<Boolean>,
        val typeMatch: String,
        val leash: Boolean,
        val masteryMoves: List<String>,
        val rareMultiplier: Optional<Double>,
        val rareLabels: Optional<List<String>>,
    )

    private val BODY: Codec<Body> = RecordCodecBuilder.create { i ->
        i.group(
            Codec.INT.fieldOf("schema").forGetter { it.schema },
            ResourceLocation.CODEC.fieldOf("handler").forGetter { it.handler },
            Codec.STRING.listOf().fieldOf("types").forGetter { it.types },
            Codec.BOOL.fieldOf("auto_assign").forGetter { it.autoAssign },
            Codec.INT.optionalFieldOf("priority", 0).forGetter { it.priority },
            Codec.DOUBLE.optionalFieldOf("range", 6.0).forGetter { it.range },
            ExtraCodecs.JSON.optionalFieldOf("params", JsonObject()).forGetter { it.params },
            EFFECT.fieldOf("effect").forGetter { it.effect },
            Codec.BOOL.optionalFieldOf("quick_work").forGetter { it.quickWork },
            Codec.STRING.optionalFieldOf("type_match", ANY).forGetter { it.typeMatch },
            Codec.BOOL.optionalFieldOf("leash", true).forGetter { it.leash },
            Codec.STRING.listOf().optionalFieldOf("mastery_moves", emptyList()).forGetter { it.masteryMoves },
            Codec.DOUBLE.optionalFieldOf(RARE_MULTIPLIER).forGetter { it.rareMultiplier },
            Codec.STRING.listOf().optionalFieldOf(RARE_LABELS).forGetter { it.rareLabels },
        ).apply(i, ::Body)
    }

    fun parse(id: ResourceLocation, json: JsonElement): DataResult<RoleDefinition> = BODY.parse(JsonOps.INSTANCE, json).flatMap { b ->
        when {
            b.schema != SCHEMA -> DataResult.error { "unsupported schema ${b.schema}" }

            !b.params.isJsonObject -> DataResult.error { "params must be an object" }

            b.effect.animations.any { it.equals(CRY, ignoreCase = true) } ->
                DataResult.error { "the effect names the animation $CRY, which plays the Pokémon's cry, and work effects are silent" }

            ((json as? JsonObject)?.get("effect") as? JsonObject)?.has("sound") == true ->
                DataResult.error { "the effect names a sound, and work effects are silent" }

            b.typeMatch.lowercase() != ANY && b.typeMatch.lowercase() != ALL -> DataResult.error {
                "type_match must be \"$ANY\" or \"$ALL\", not \"${b.typeMatch}\""
            }

            b.typeMatch.lowercase() == ALL && b.types.isEmpty() -> DataResult.error { "type_match \"$ALL\" needs at least one type" }

            !b.autoAssign && !b.leash && !b.quickWork.orElse(false) ->
                DataResult.error { "a role with auto_assign false and leash false never works" }

            b.rareMultiplier.isPresent && (b.rareMultiplier.get().isNaN() || b.rareMultiplier.get() < 0.0) ->
                DataResult.error { "rare_multiplier must be 0 or more" }

            else -> {
                val params = b.params.asJsonObject
                DataResult.success(
                    RoleDefinition(
                        id = id,
                        handler = b.handler,
                        types = b.types.map { it.lowercase() }.toSet(),
                        autoAssign = b.autoAssign,
                        priority = b.priority,
                        range = b.range,
                        params = params,
                        effect = b.effect,
                        quickWork = b.quickWork.orElse(null),
                        allTypes = b.typeMatch.lowercase() == ALL,
                        leash = b.leash,
                        masteryMoves = b.masteryMoves.map(WorkTraits::normalizeMoveName).filter { it.isNotEmpty() }.toSet(),
                        rareMultiplier = b.rareMultiplier.orElseGet { rareMultiplierFrom(params) },
                        rareLabels = b.rareLabels.map { list -> list.map { it.lowercase() }.toSet() }
                            .orElseGet { rareLabelsFrom(params) },
                    ),
                )
            }
        }
    }

    private fun rareMultiplierFrom(params: JsonObject): Double =
        params.get(RARE_MULTIPLIER)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble ?: DEFAULT_RARE_MULTIPLIER

    private fun rareLabelsFrom(params: JsonObject): Set<String> {
        val array = params.get(RARE_LABELS)?.takeIf { it.isJsonArray }?.asJsonArray ?: return RoleDefinition.DEFAULT_RARE_LABELS
        return array.filter { it.isJsonPrimitive && it.asJsonPrimitive.isString }.mapTo(LinkedHashSet()) { it.asString.lowercase() }
    }

    fun describeError(id: ResourceLocation, result: DataResult<*>): String =
        "Skipping role file $id: ${result.error().map { it.message() }.orElse("unknown error")}"
}
