package codes.honor.cobblemondynamics.role

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

    private val EFFECT: Codec<EffectDefinition> = RecordCodecBuilder.create { i ->
        i.group(
            Codec.INT.optionalFieldOf("loop_ticks", 40).forGetter { it.loopTicks },
            Codec.STRING.listOf().fieldOf("animations").forGetter { it.animations },
            ResourceLocation.CODEC.listOf().optionalFieldOf("actor_particles", emptyList()).forGetter { it.actorParticles },
            ResourceLocation.CODEC.listOf().optionalFieldOf("target_particles", emptyList()).forGetter { it.targetParticles },
            ResourceLocation.CODEC.optionalFieldOf("sound").forGetter { Optional.ofNullable(it.sound) },
        ).apply(i) { loop, animations, actor, target, sound -> EffectDefinition(loop, animations, actor, target, sound.orElse(null)) }
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
    )

    // auto_assign has no default, so a leash-only role file that leaves it out fails to parse (CD-PMP-02, CD-MUS-02).
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
        ).apply(i, ::Body)
    }

    fun parse(id: ResourceLocation, json: JsonElement): DataResult<RoleDefinition> = BODY.parse(JsonOps.INSTANCE, json).flatMap { b ->
        when {
            b.schema != SCHEMA -> DataResult.error { "unsupported schema ${b.schema}" }

            !b.params.isJsonObject -> DataResult.error { "params must be an object" }

            else -> DataResult.success(
                RoleDefinition(
                    id = id,
                    handler = b.handler,
                    types = b.types.map { it.lowercase() }.toSet(),
                    autoAssign = b.autoAssign,
                    priority = b.priority,
                    range = b.range,
                    params = b.params.asJsonObject,
                    effect = b.effect,
                ),
            )
        }
    }

    fun describeError(id: ResourceLocation, result: DataResult<*>): String =
        "Skipping role file $id: ${result.error().map { it.message() }.orElse("unknown error")}"
}
