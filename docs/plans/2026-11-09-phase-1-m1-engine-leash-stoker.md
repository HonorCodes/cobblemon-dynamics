# M1 (part 1 of 2): engine core

Part of the [Phase 1 plan](2026-11-09-phase-1-plan.md). Read its Global Constraints first. Branch:
`m1-engine-leash-stoker`. Part 2: [integration](2026-11-09-phase-1-m1-engine-leash-stoker-part2.md).

**Milestone goal:** a Fire-type in a Work Mode pasture finds a furnace with input, walks to it, keeps it
lit with a looping Flamethrower effect, and a player can bind a specific Pokémon to a specific furnace with
the Work Leash. Part 1 builds the pure rules that the spec tests on a fake clock. Part 2 wires them into
the game.

Before Task 1, check the API references of this milestone against the downloaded jars (index, "How to
execute", item 3).

---

### Task 1: Server and client config

**Rows:** CD-CFG-01 (every key defined), CD-CFG-02, CD-CFG-05 (the key), CD-CFG-06, CD-CFG-11, CD-CFG-12,
CD-CFG-14, CD-CFG-15.

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/config/ConfigRanges.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/config/EngineSettings.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/config/ServerConfig.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/config/ClientConfig.kt`
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/CobblemonDynamics.kt` (register both configs)
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/config/ConfigTest.kt`

**Interfaces:**
- Produces: `data class EngineSettings` with the fields and defaults below, `EngineSettings.DEFAULT`, and
  `ServerConfig.snapshot(): EngineSettings` (reads the live config). `ClientConfig.workSounds: Boolean`,
  `ClientConfig.headBarRange: Int`, `ClientConfig.panelBars: Boolean`.
- Produces: `fun EngineSettings.effectiveRadius(configured: Int, maxWander: Int): Int`.

- [ ] **Step 1: Write the failing test**

```kotlin
package codes.honor.cobblemondynamics.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfigTest {
    @Test
    fun `CD-CFG-02 work radii accept 2 to 32 and default to 8 and 6`() {
        for (r in listOf(2, 32)) assertTrue(r in ConfigRanges.WORK_RADIUS)
        for (r in listOf(1, 33)) assertFalse(r in ConfigRanges.WORK_RADIUS)
        assertTrue(ServerConfig.isValid("workArea.horizontalRadius", 2))
        assertFalse(ServerConfig.isValid("workArea.horizontalRadius", 33))
        assertFalse(ServerConfig.isValid("workArea.verticalRadius", 1))
        assertEquals(8, EngineSettings.DEFAULT.horizontalRadius)
        assertEquals(6, EngineSettings.DEFAULT.verticalRadius)
    }

    @Test
    fun `CD-CFG-11 CD-CFG-12 CD-CFG-15 engine timing defaults`() {
        val s = EngineSettings.DEFAULT
        assertEquals(40, s.scanIntervalTicks)
        assertEquals(512, s.maxBlockChecksPerTick)
        assertEquals(600, s.travelTimeoutTicks)
        assertEquals(6000, s.retryWaitTicks)
        assertEquals(200, s.standbyMoveTicks)
    }

    @Test
    fun `CD-CFG-14 radii clamp to Cobblemon's pastureMaxWanderDistance`() {
        assertEquals(20, EngineSettings.effectiveRadius(configured = 32, maxWander = 20))
        assertEquals(19, EngineSettings.effectiveRadius(configured = 19, maxWander = 20))
    }

    @Test
    fun `CD-CFG-01 every documented key exists`() {
        val keys = listOf(
            "workArea.horizontalRadius", "workArea.verticalRadius", "engine.scanIntervalTicks",
            "engine.maxBlockChecksPerTick", "engine.travelTimeoutTicks", "engine.retryWaitTicks",
            "engine.standbyMoveTicks", "engine.effectBroadcastRadius", "workMode.toggleCooldownTicks",
            "leash.pendingSelectionTicks", "stamina.ticksPerLevel", "stamina.healerRecoveryMinutesAtLevel1",
            "stamina.healerRecoveryMinutesAtLevel100", "stamina.flushIntervalTicks", "beds.speedFactors",
            "heal.cost", "heal.lowStaminaThreshold", "roles.disabled", "compat.cobbleworkers", "compat.cobblebase",
        )
        for (k in keys) assertTrue(ServerConfig.hasKey(k), k)
    }

    @Test
    fun `CD-CFG-06 client workSounds defaults to false`() {
        assertTrue(ClientConfig.hasKey("effects.workSounds"))
        assertFalse(ClientConfig.defaultWorkSounds)
    }
}
```

- [ ] **Step 2: Run it and see it fail**

Run: `./gradlew test --tests "codes.honor.cobblemondynamics.config.ConfigTest"`
Expected: FAIL, unresolved references `ConfigRanges`, `ServerConfig`, `EngineSettings`, `ClientConfig`.

- [ ] **Step 3: Write the config classes**

```kotlin
// ConfigRanges.kt
package codes.honor.cobblemondynamics.config

object ConfigRanges {
    val WORK_RADIUS = 2..32
    val SCAN_INTERVAL = 20..1_200
    val BLOCK_CHECKS = 64..4_096
    val TRAVEL_TIMEOUT = 100..6_000
    val RETRY_WAIT = 0..72_000
    val STANDBY_MOVE = 20..6_000
    val EFFECT_RADIUS = 16..128
    val TOGGLE_COOLDOWN = 0..200
    val PENDING_SELECTION = 100..6_000
    val TICKS_PER_LEVEL = 20..72_000
    val HEALER_MINUTES = 1..10_000
    val FLUSH_INTERVAL = 20..12_000
    val BED_FACTOR = 1..64
    val HEAL_COST = 0.01..1.0
    val LOW_STAMINA_THRESHOLD = 0.0..1.0
    val HEAD_BAR_RANGE = 4..64
}
```

```kotlin
// EngineSettings.kt
package codes.honor.cobblemondynamics.config

import net.minecraft.resources.ResourceLocation

/** An immutable snapshot of the server config. Pure engine code takes this instead of reading the config. */
data class EngineSettings(
    val horizontalRadius: Int = 8,
    val verticalRadius: Int = 6,
    val scanIntervalTicks: Int = 40,
    val maxBlockChecksPerTick: Int = 512,
    val travelTimeoutTicks: Int = 600,
    val retryWaitTicks: Int = 6_000,
    val standbyMoveTicks: Int = 200,
    val effectBroadcastRadius: Int = 64,
    val toggleCooldownTicks: Int = 10,
    val pendingSelectionTicks: Int = 600,
    val ticksPerLevel: Int = 3_600,
    val healerMinutesAtLevel1: Int = 1_200,
    val healerMinutesAtLevel100: Int = 120,
    val flushIntervalTicks: Int = 600,
    val bedFactors: List<Int> = listOf(2, 3, 4, 8),
    val healCost: Double = 0.25,
    val lowStaminaThreshold: Double = 0.25,
    val disabledRoles: Set<ResourceLocation> = emptySet(),
    val compatCobbleworkers: Boolean = true,
    val compatCobblebase: Boolean = true,
) {
    companion object {
        val DEFAULT = EngineSettings()

        /** CD-CFG-14: Cobblemon teleports or recalls a pastured Pokémon outside its roam box. */
        fun effectiveRadius(configured: Int, maxWander: Int): Int = minOf(configured, maxWander)
    }
}
```

`ServerConfig` builds a `ModConfigSpec` with `defineInRange` from `ConfigRanges` for every key of the
architecture's section 9 table, with the defaults of `EngineSettings.DEFAULT`, comments from that table,
`beds.speedFactors` as `defineList` (4 entries, each in `BED_FACTOR`), and `roles.disabled` as a list of
resource location strings. NeoForge's list validator checks only the elements: it keeps a list of the
wrong size and drops a bad element from it. So `snapshot()` falls back to the default factors when the
list does not hold 4 valid entries. It exposes:

```kotlin
object ServerConfig {
    val SPEC: ModConfigSpec
    fun snapshot(): EngineSettings            // reads every value; disabledRoles parsed with ResourceLocation.tryParse
    fun hasKey(path: String): Boolean = SPEC.spec.contains(path)
    fun isValid(path: String, value: Any): Boolean =
        (SPEC.spec.get<Any>(path) as ModConfigSpec.ValueSpec).test(value)
}
```

`ClientConfig` builds `effects.workSounds` (default `false`), `hud.headBarRange` (default 16, range
`HEAD_BAR_RANGE`) and `hud.panelBars` (default `true`), with `hasKey(path)` and
`val defaultWorkSounds: Boolean get() = workSoundsValue.default`, the default that the spec writes to a
new file. A `const val` with a camel-case name fails ktlint's `property-naming` rule.

Play-test change (2026-11-10): `effects.workSounds` is gone, because work is silent (CD-CFG-06).

Register both in the mod constructor:

```kotlin
container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC)   // writes config/cobblemon_dynamics-server.toml
container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC)   // writes config/cobblemon_dynamics-client.toml
modBus.addListener<ModConfigEvent.Loading> { if (it.config.spec === ServerConfig.SPEC) WorkEngineSettings.reload() }
modBus.addListener<ModConfigEvent.Reloading> { if (it.config.spec === ServerConfig.SPEC) WorkEngineSettings.reload() }
```

FML 4.0.45 (NeoForge 21.1) writes a server config to the global `config/` folder. A file with the same
name in a world's `serverconfig/` folder overrides it for that world, and a new world's `serverconfig/`
folder stays empty. Checked in `ConfigTracker.resolveBasePath` and in a `runGameTestServer` run.

`WorkEngineSettings` is a holder (`@Volatile var current: EngineSettings = EngineSettings.DEFAULT`, and
`fun reload() { current = ServerConfig.snapshot() }`) in the same package.

If `ModConfigSpec` cannot be built in a plain JUnit run, keep the `ConfigRanges` and `EngineSettings`
assertions and move the `hasKey` and `isValid` assertions to a GameTest in Task 8.

- [ ] **Step 4: Run it and see it pass**

Run: `./gradlew test --tests "codes.honor.cobblemondynamics.config.ConfigTest"`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/codes/honor/cobblemondynamics/config src/main/kotlin/codes/honor/cobblemondynamics/CobblemonDynamics.kt src/test/kotlin/codes/honor/cobblemondynamics/config
git commit -m "Add the server and client configs with ranges and defaults"
```

---

### Task 2: Role model, role files and role registry

**Rows:** CD-CFG-07, CD-CFG-08, CD-CFG-09, CD-CFG-10, CD-CFG-05, CD-ASG-02, CD-ASG-16 (rule), CD-LSH-16,
CD-FX-02 (Stoker file), CD-FX-03.

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/role/RoleDefinition.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/role/RoleCodec.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/role/RoleSelection.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/role/RoleSet.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/role/RoleRegistry.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/TargetHandler.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/HandlerRegistry.kt`
- Create: `src/main/resources/data/cobblemon_dynamics/cobblemon_dynamics/roles/stoker.json`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/role/RoleCodecTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/role/RoleSelectionTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/role/RoleSetTest.kt`

**Interfaces:**
- Produces:

```kotlin
data class EffectDefinition(
    val loopTicks: Int,
    val animations: List<String>,                 // as written in the file
    val actorParticles: List<ResourceLocation>,
    val targetParticles: List<ResourceLocation>,
    val sound: ResourceLocation?,
) { val animationsWithCry: List<String> }          // CD-FX-03: file order, then "cry" last, once

data class RoleDefinition(
    val id: ResourceLocation, val handler: ResourceLocation, val types: Set<String>,
    val autoAssign: Boolean, val priority: Int, val range: Double,
    val params: JsonObject, val effect: EffectDefinition,
)

object RoleCodec { fun parse(id: ResourceLocation, json: JsonElement): DataResult<RoleDefinition> }

object RoleSelection {
    fun isEligible(role: RoleDefinition, pokemonTypes: Collection<String>): Boolean
    fun best(candidates: Collection<RoleDefinition>): RoleDefinition?   // priority desc, then id asc
}

enum class ScanKind { BLOCK_ENTITY, BLOCK, NONE }
enum class BindingShape { SINGLE, SOURCE_AND_DESTINATION }

interface TargetHandler {
    val id: ResourceLocation
    val scanKind: ScanKind
    val bindingShape: BindingShape get() = BindingShape.SINGLE
    fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean
    fun canonical(level: ServerLevel, pos: BlockPos): BlockPos = pos
    fun isPartOfTarget(level: ServerLevel, canonical: BlockPos, pos: BlockPos): Boolean = pos == canonical
    fun targetCenter(canonical: BlockPos): Vec3 = Vec3.atCenterOf(canonical)
    fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean
    fun hasDemand(ctx: WorkContext): Boolean
    fun onStart(ctx: WorkContext) {}
    fun applyWork(ctx: WorkContext)
    fun onStop(ctx: WorkContext) {}
    fun flip(level: ServerLevel, canonical: BlockPos): Boolean = false
}

class WorkContext(
    val level: ServerLevel, val target: BlockPos, val worker: PokemonEntity, val pokemon: Pokemon,
    val role: RoleDefinition, val now: Long,
    val destination: BlockPos? = null, val sourceFace: Direction? = null, val destinationFace: Direction? = null,
)

data class ActiveRole(val definition: RoleDefinition, val handler: TargetHandler)

class RoleSet(roles: Map<ResourceLocation, RoleDefinition>, handlers: (ResourceLocation) -> TargetHandler?,
              disabled: Set<ResourceLocation>, log: (String) -> Unit) {
    val active: List<ActiveRole>                    // enabled, handler present, sorted by id
    fun get(id: ResourceLocation): ActiveRole?
    fun forTypes(types: Collection<String>): List<ActiveRole>
}

object HandlerRegistry { fun register(handler: TargetHandler); fun get(id: ResourceLocation): TargetHandler? }
object RoleRegistry : SimpleJsonResourceReloadListener {   // folder "cobblemon_dynamics/roles"
    val current: RoleSet
    fun disableHandler(handlerId: ResourceLocation, reason: String)   // used by mixin flags in M3 and M4
}
```

- [ ] **Step 1: Write the failing tests**

```kotlin
// RoleCodecTest.kt
package codes.honor.cobblemondynamics.role

import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RoleCodecTest {
    private val id = ResourceLocation.fromNamespaceAndPath("cobblemon_dynamics", "wheel_driver")

    private fun parse(text: String) = RoleCodec.parse(id, JsonParser.parseString(text))

    private val waterGun = """
        { "schema": 1, "handler": "cobblemon_dynamics:water_wheel", "types": ["water"], "auto_assign": true,
          "priority": 0, "range": 6.0, "params": {},
          "effect": { "loop_ticks": 40, "animations": ["watergun", "spray", "beam", "special"],
                      "actor_particles": ["cobblemon:watergun_actor"],
                      "target_particles": ["cobblemon:watergun_spray", "cobblemon:watergun_target"] } }
    """

    @Test
    fun `CD-FX-03 the Water Gun list ends with cry in the listed order`() {
        val role = parse(waterGun).getOrThrow()
        assertEquals(listOf("watergun", "spray", "beam", "special", "cry"), role.effect.animationsWithCry)
    }

    @Test
    fun `CD-FX-03 a cry written mid-list moves to the end once`() {
        val role = parse(waterGun.replace("\"spray\",", "\"cry\", \"spray\",")).getOrThrow()
        assertEquals(listOf("watergun", "spray", "beam", "special", "cry"), role.effect.animationsWithCry)
    }

    @Test
    fun `CD-CFG-07 a file carries types, flag, priority, range, handler, params and effect`() {
        val role = parse(waterGun).getOrThrow()
        assertEquals(setOf("water"), role.types)
        assertTrue(role.autoAssign)
        assertEquals(0, role.priority)
        assertEquals(6.0, role.range)
        assertEquals(ResourceLocation.parse("cobblemon_dynamics:water_wheel"), role.handler)
        assertEquals(40, role.effect.loopTicks)
    }

    @Test
    fun `CD-CFG-09 a malformed file is an error that names the file`() {
        val result = parse("""{ "schema": 1, "types": "water" }""")
        assertTrue(result.error().isPresent)
        assertTrue(RoleCodec.describeError(id, result).contains("cobblemon_dynamics:wheel_driver"))
    }

    @Test
    fun `types are lower-cased`() {
        assertEquals(setOf("water"), parse(waterGun.replace("[\"water\"]", "[\"Water\"]")).getOrThrow().types)
    }
}
```

```kotlin
// RoleSelectionTest.kt
package codes.honor.cobblemondynamics.role

import com.google.gson.JsonObject
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

fun role(path: String, types: Set<String>, priority: Int = 0, autoAssign: Boolean = true, handler: String = "cobblemon_dynamics:test") =
    RoleDefinition(
        id = ResourceLocation.fromNamespaceAndPath("cobblemon_dynamics", path),
        handler = ResourceLocation.parse(handler), types = types, autoAssign = autoAssign, priority = priority,
        range = 6.0, params = JsonObject(),
        effect = EffectDefinition(40, listOf("special"), emptyList(), emptyList(), null),
    )

class RoleSelectionTest {
    @Test
    fun `CD-ASG-02 the secondary type counts and an unlisted type does not`() {
        val wheelDriver = role("wheel_driver", setOf("water"))
        assertTrue(RoleSelection.isEligible(wheelDriver, listOf("water", "flying")))   // Gyarados
        assertTrue(RoleSelection.isEligible(wheelDriver, listOf("flying", "water")))
        assertFalse(RoleSelection.isEligible(wheelDriver, listOf("electric")))         // Pikachu
    }

    @Test
    fun `CD-ASG-16 the higher priority wins`() {
        val burnerKeeper = role("burner_keeper", setOf("fire"), priority = 0)
        val superheater = role("superheater", setOf("dragon"), priority = 10)
        assertEquals(superheater, RoleSelection.best(listOf(burnerKeeper, superheater)))
        val brewer = role("brewer", setOf("poison"), priority = 0)
        val brewStoker = role("brew_stoker", setOf("fire"), priority = 10)
        assertEquals(brewStoker, RoleSelection.best(listOf(brewer, brewStoker)))
    }

    @Test
    fun `CD-LSH-16 on a priority tie the role id that sorts first wins`() {
        val pump = role("pump", setOf("water"), autoAssign = false)
        val telekinesis = role("telekinesis", setOf("psychic"), autoAssign = false)
        assertEquals(pump, RoleSelection.best(listOf(telekinesis, pump)))
    }
}
```

```kotlin
// RoleSetTest.kt
package codes.honor.cobblemondynamics.role

import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RoleSetTest {
    private val stoker = role("stoker", setOf("fire"), handler = "cobblemon_dynamics:furnace")
    private val wheel = role("wheel_driver", setOf("water"), handler = "cobblemon_dynamics:water_wheel")

    @Test
    fun `CD-CFG-10 a role whose handler is missing is disabled with one line that names it`() {
        val lines = mutableListOf<String>()
        val set = RoleSet(mapOf(stoker.id to stoker, wheel.id to wheel), { if (it.path == "furnace") FakeHandler(it) else null }, emptySet(), lines::add)
        assertEquals(listOf(stoker.id), set.active.map { it.definition.id })
        assertEquals(1, lines.size)
        assertEquals(true, lines.single().contains("cobblemon_dynamics:wheel_driver"))
    }

    @Test
    fun `CD-CFG-05 a role disabled in the config is inactive`() {
        val set = RoleSet(mapOf(stoker.id to stoker), { FakeHandler(it) }, setOf(stoker.id)) { }
        assertNull(set.get(stoker.id))
    }

    @Test
    fun `forTypes keeps only eligible roles`() {
        val set = RoleSet(mapOf(stoker.id to stoker, wheel.id to wheel), { FakeHandler(it) }, emptySet()) { }
        assertEquals(listOf(stoker.id), set.forTypes(listOf("fire", "flying")).map { it.definition.id })
    }
}
```

`FakeHandler` is a test helper in `src/test/kotlin/codes/honor/cobblemondynamics/role/FakeHandler.kt` that
implements `TargetHandler` with `scanKind = ScanKind.NONE` and throws on every world call. It only needs
`id`. If `TargetHandler`'s world-typed parameters force Minecraft classes to initialize, keep the methods
unimplemented with `error("not used")`. The JVM loads parameter types lazily, so no bootstrap runs.

- [ ] **Step 2: Run them and see them fail**

Run: `./gradlew test --tests "codes.honor.cobblemondynamics.role.*"`
Expected: FAIL, unresolved references.

- [ ] **Step 3: Write the role code**

```kotlin
// RoleSelection.kt
package codes.honor.cobblemondynamics.role

object RoleSelection {
    fun isEligible(role: RoleDefinition, pokemonTypes: Collection<String>): Boolean =
        pokemonTypes.any { it.lowercase() in role.types }

    private val ORDER = compareByDescending<RoleDefinition> { it.priority }.thenBy { it.id.toString() }

    fun best(candidates: Collection<RoleDefinition>): RoleDefinition? = candidates.minWithOrNull(ORDER)
}
```

```kotlin
// RoleDefinition.kt (EffectDefinition part)
val animationsWithCry: List<String>
    get() = animations.filter { it != "cry" } + "cry"
```

Play-test change (2026-11-10): work is silent. `animationsWithCry` and the `sound` key are gone, and
`RoleCodec` skips a role file that names `cry`, whose animation plays the Pokémon's cry, or a `sound`.

`RoleCodec` uses a `RecordCodecBuilder` over the file body:

```kotlin
private val EFFECT = RecordCodecBuilder.create<EffectDefinition> { i ->
    i.group(
        Codec.INT.optionalFieldOf("loop_ticks", 40).forGetter { it.loopTicks },
        Codec.STRING.listOf().fieldOf("animations").forGetter { it.animations },
        ResourceLocation.CODEC.listOf().optionalFieldOf("actor_particles", emptyList()).forGetter { it.actorParticles },
        ResourceLocation.CODEC.listOf().optionalFieldOf("target_particles", emptyList()).forGetter { it.targetParticles },
        ResourceLocation.CODEC.optionalFieldOf("sound").forGetter { Optional.ofNullable(it.sound) },
    ).apply(i) { loop, anim, actor, target, sound -> EffectDefinition(loop, anim, actor, target, sound.orElse(null)) }
}

private data class Body(val schema: Int, val handler: ResourceLocation, val types: List<String>, val autoAssign: Boolean,
                        val priority: Int, val range: Double, val params: JsonElement, val effect: EffectDefinition)

private val BODY = RecordCodecBuilder.create<Body> { i ->
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

fun parse(id: ResourceLocation, json: JsonElement): DataResult<RoleDefinition> =
    BODY.parse(JsonOps.INSTANCE, json).flatMap { b ->
        if (b.schema != 1) DataResult.error { "unsupported schema ${b.schema}" }
        else if (!b.params.isJsonObject) DataResult.error { "params must be an object" }
        else DataResult.success(RoleDefinition(id, b.handler, b.types.map { it.lowercase() }.toSet(), b.autoAssign,
            b.priority, b.range, b.params.asJsonObject, b.effect))
    }

fun describeError(id: ResourceLocation, result: DataResult<*>): String =
    "Skipping role file $id: ${result.error().map { it.message() }.orElse("unknown error")}"
```

`auto_assign` is required with no default, so CD-PMP-02 and CD-MUS-02 ("true or missing" fails) are
enforced by the codec.

`RoleSet` builds `active` from the parsed map: a role whose handler id is unknown logs
`"Role $id is disabled: handler $handler is not registered"` once and is skipped. A role in `disabled`
is skipped silently. `RoleRegistry` extends `SimpleJsonResourceReloadListener(Gson(), "cobblemon_dynamics/roles")`.
In `apply`, it parses each entry, logs `RoleCodec.describeError` at ERROR for each failure (one line per
file), builds a `RoleSet` with `HandlerRegistry::get` and `WorkEngineSettings.current.disabledRoles`, and
publishes it to `current`. Register it with `NeoForge.EVENT_BUS.addListener<AddReloadListenerEvent> { it.addListener(RoleRegistry) }`.
A server config load or reload (`ModConfigEvent.Loading` or `ModConfigEvent.Reloading`) calls
`RoleRegistry.rebuild()`, which rebuilds `current` from the last parsed map, so `roles.disabled` applies
without `/reload`. The load needs the rebuild as much as the reload does: a server reads its data packs
before it loads the server config, so the `RoleSet` that `apply` builds at start sees the default
settings. A rebuild logs each missing-handler line at most once per load of the role files, so a server
start still logs one line per disabled role (CD-CFG-10). `disableHandler` logs one warning per handler
id and rebuilds `current` without that handler.

`stoker.json`:

```json
{
  "schema": 1,
  "handler": "cobblemon_dynamics:furnace",
  "types": ["fire"],
  "auto_assign": true,
  "priority": 0,
  "range": 6.0,
  "params": {},
  "effect": {
    "loop_ticks": 40,
    "animations": ["flamethrower", "fire", "spray", "special", "cry"],
    "actor_particles": ["cobblemon:flamethrower_actor"],
    "target_particles": ["cobblemon:flamethrower_target", "cobblemon:flamethrower_targetburst"]
  }
}
```

Play-test change (2026-11-10): the Stoker casts Ember, as the Brew Stoker does, with the actor particle
`cobblemon:ember_actor` and the target particle `cobblemon:ember_target`. The approver found Flamethrower too
flashy for a loop.

- [ ] **Step 4: Run them and see them pass**

Run: `./gradlew test --tests "codes.honor.cobblemondynamics.role.*"`
Expected: PASS, 11 tests.

- [ ] **Step 5: Add the role-file data test**

`src/test/kotlin/codes/honor/cobblemondynamics/role/BuiltInRoleFilesTest.kt` reads every file under
`src/main/resources/data/cobblemon_dynamics/cobblemon_dynamics/roles/`, parses it with `RoleCodec`, and
checks the Role table (CD-FX-02): a map from role id to `(handler, types, autoAssign, priority, first
animation)`. In M1 the map holds the Stoker row only:
`stoker -> ("cobblemon_dynamics:furnace", {"fire"}, true, 0, "flamethrower")`, with `"ember"` since the
play-test. M4 extends the map to every Phase 1 role. The test fails when a file is missing from the map or differs from it.

Run: `./gradlew test --tests "codes.honor.cobblemondynamics.role.BuiltInRoleFilesTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/codes/honor/cobblemondynamics/role src/main/kotlin/codes/honor/cobblemondynamics/handler src/main/resources/data src/test/kotlin/codes/honor/cobblemondynamics/role
git commit -m "Load data-driven role files and pick roles by type and priority"
```

---

### Task 3: Claim registry

**Rows:** CD-CLM-01, CD-CLM-02, CD-CLM-03, CD-CLM-06 (registry rule), CD-CLM-07 (memory only),
CD-CLM-09, CD-HEAL-09 (registry rule), CD-WMD-05 (registry part), CD-CLM-04 (key from the canonical
position).

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/claim/TargetKey.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/claim/Claim.kt` (`ClaimKind`, `Claim`, `ClaimResult`)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/claim/ClaimRegistry.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/pasture/PastureKey.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/claim/ClaimRegistryTest.kt`

**Interfaces:**
- Produces:

```kotlin
data class PastureKey(val dimension: ResourceLocation, val pos: Long)

sealed interface TargetKey {
    data class Block(val dimension: ResourceLocation, val pos: Long) : TargetKey
    data class Entity(val uuid: UUID) : TargetKey
    companion object { fun block(dimension: ResourceLocation, pos: BlockPos) = Block(dimension, pos.asLong()) }
}

enum class ClaimKind { WORK, BED, HEAL }
data class Claim(val worker: UUID, val key: TargetKey, val kind: ClaimKind, val pasture: PastureKey, val sinceTick: Long)

sealed interface ClaimResult {
    data class Granted(val claim: Claim) : ClaimResult
    data object TargetTaken : ClaimResult
    data object WorkerBusy : ClaimResult
    data object Reserved : ClaimResult
}

fun interface ReservationView { fun reservedFor(key: TargetKey): UUID? }

class ClaimRegistry(private val reservations: ReservationView) {
    fun tryClaim(worker: UUID, key: TargetKey, kind: ClaimKind, pasture: PastureKey, now: Long): ClaimResult
    fun claimOf(worker: UUID): Claim?
    fun holderOf(key: TargetKey): Claim?
    fun isFree(key: TargetKey, forWorker: UUID): Boolean     // no claim and no reservation for another worker
    fun release(worker: UUID): Claim?
    fun releaseTarget(key: TargetKey): Claim?
    fun releaseAll(predicate: (Claim) -> Boolean): List<Claim>
    fun all(): List<Claim>
    fun clear()
}
```

- [ ] **Step 1: Write the failing test**

```kotlin
package codes.honor.cobblemondynamics.claim

import codes.honor.cobblemondynamics.pasture.PastureKey
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class ClaimRegistryTest {
    private val overworld = ResourceLocation.withDefaultNamespace("overworld")
    private val nether = ResourceLocation.withDefaultNamespace("the_nether")
    private val pastureA = PastureKey(overworld, BlockPos(0, 64, 10).asLong())
    private val pastureB = PastureKey(overworld, BlockPos(40, 64, 10).asLong())
    private val furnace = TargetKey.block(overworld, BlockPos(0, 64, 0))
    private val reservations = mutableMapOf<TargetKey, UUID>()
    private val registry = ClaimRegistry { reservations[it] }
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()

    @Test
    fun `CD-CLM-01 two pastures with different owners claim one furnace in the same tick`() {
        assertInstanceOf(ClaimResult.Granted::class.java, registry.tryClaim(a, furnace, ClaimKind.WORK, pastureA, 0))
        assertEquals(ClaimResult.TargetTaken, registry.tryClaim(b, furnace, ClaimKind.WORK, pastureB, 0))
        assertEquals(a, registry.holderOf(furnace)?.worker)
    }

    @Test
    fun `CD-CLM-02 a worker holds at most one claim of any kind`() {
        registry.tryClaim(a, furnace, ClaimKind.WORK, pastureA, 0)
        val second = TargetKey.block(overworld, BlockPos(3, 64, 0))
        assertEquals(ClaimResult.WorkerBusy, registry.tryClaim(a, second, ClaimKind.WORK, pastureA, 1))
        val bed = TargetKey.block(overworld, BlockPos(5, 64, 5))
        registry.tryClaim(b, bed, ClaimKind.BED, pastureA, 0)
        assertEquals(ClaimResult.WorkerBusy, registry.tryClaim(b, TargetKey.Entity(UUID.randomUUID()), ClaimKind.HEAL, pastureA, 1))
    }

    @Test
    fun `CD-CLM-03 equal coordinates in two dimensions do not collide`() {
        registry.tryClaim(a, TargetKey.block(overworld, BlockPos(0, 64, 0)), ClaimKind.WORK, pastureA, 0)
        val result = registry.tryClaim(b, TargetKey.block(nether, BlockPos(0, 64, 0)), ClaimKind.WORK, PastureKey(nether, 0), 0)
        assertInstanceOf(ClaimResult.Granted::class.java, result)
    }

    @Test
    fun `CD-CLM-06 a reserved target refuses every worker but the bound one`() {
        reservations[furnace] = a
        assertEquals(ClaimResult.Reserved, registry.tryClaim(b, furnace, ClaimKind.WORK, pastureB, 0))
        assertInstanceOf(ClaimResult.Granted::class.java, registry.tryClaim(a, furnace, ClaimKind.WORK, pastureA, 0))
    }

    @Test
    fun `CD-CLM-09 a binding is not a claim, so a Tired leashed worker gets a bed claim`() {
        reservations[furnace] = a
        val bed = TargetKey.block(overworld, BlockPos(5, 64, 5))
        assertInstanceOf(ClaimResult.Granted::class.java, registry.tryClaim(a, bed, ClaimKind.BED, pastureA, 0))
    }

    @Test
    fun `CD-HEAL-09 one heal claim per ally`() {
        val ally = TargetKey.Entity(UUID.randomUUID())
        registry.tryClaim(a, ally, ClaimKind.HEAL, pastureA, 0)
        assertEquals(ClaimResult.TargetTaken, registry.tryClaim(b, ally, ClaimKind.HEAL, pastureA, 0))
    }

    @Test
    fun `CD-WMD-05 releasing by pasture frees work, bed and heal claims`() {
        registry.tryClaim(a, furnace, ClaimKind.WORK, pastureA, 0)
        registry.tryClaim(b, TargetKey.Entity(UUID.randomUUID()), ClaimKind.HEAL, pastureA, 0)
        val other = UUID.randomUUID()
        registry.tryClaim(other, TargetKey.block(overworld, BlockPos(9, 64, 9)), ClaimKind.BED, pastureB, 0)
        assertEquals(2, registry.releaseAll { it.pasture == pastureA }.size)
        assertNull(registry.claimOf(a))
        assertNull(registry.claimOf(b))
        assertEquals(other, registry.all().single().worker)
    }

    @Test
    fun `release frees both indexes`() {
        registry.tryClaim(a, furnace, ClaimKind.WORK, pastureA, 0)
        registry.release(a)
        assertInstanceOf(ClaimResult.Granted::class.java, registry.tryClaim(b, furnace, ClaimKind.WORK, pastureB, 1))
    }
}
```

- [ ] **Step 2: Run it and see it fail**

Run: `./gradlew test --tests "codes.honor.cobblemondynamics.claim.ClaimRegistryTest"`
Expected: FAIL, unresolved references.

- [ ] **Step 3: Write the registry**

```kotlin
package codes.honor.cobblemondynamics.claim

import codes.honor.cobblemondynamics.pasture.PastureKey
import java.util.UUID

/** In-memory only (CD-CLM-07). Scans rebuild every claim after a restart. */
class ClaimRegistry(private val reservations: ReservationView) {
    private val byKey = HashMap<TargetKey, Claim>()
    private val byWorker = HashMap<UUID, Claim>()

    fun tryClaim(worker: UUID, key: TargetKey, kind: ClaimKind, pasture: PastureKey, now: Long): ClaimResult {
        if (worker in byWorker) return ClaimResult.WorkerBusy
        if (key in byKey) return ClaimResult.TargetTaken
        if (kind == ClaimKind.WORK) {
            val bound = reservations.reservedFor(key)
            if (bound != null && bound != worker) return ClaimResult.Reserved
        }
        val claim = Claim(worker, key, kind, pasture, now)
        byKey[key] = claim
        byWorker[worker] = claim
        return ClaimResult.Granted(claim)
    }

    fun claimOf(worker: UUID): Claim? = byWorker[worker]
    fun holderOf(key: TargetKey): Claim? = byKey[key]

    fun isFree(key: TargetKey, forWorker: UUID): Boolean {
        if (key in byKey) return false
        val bound = reservations.reservedFor(key)
        return bound == null || bound == forWorker
    }

    fun release(worker: UUID): Claim? = byWorker.remove(worker)?.also { byKey.remove(it.key) }
    fun releaseTarget(key: TargetKey): Claim? = byKey.remove(key)?.also { byWorker.remove(it.worker) }

    fun releaseAll(predicate: (Claim) -> Boolean): List<Claim> =
        byKey.values.filter(predicate).onEach { byKey.remove(it.key); byWorker.remove(it.worker) }

    fun all(): List<Claim> = byKey.values.toList()

    fun clear() {
        byKey.clear()
        byWorker.clear()
    }
}
```

- [ ] **Step 4: Run it and see it pass**

Run: `./gradlew test --tests "codes.honor.cobblemondynamics.claim.ClaimRegistryTest"`
Expected: PASS, 8 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/codes/honor/cobblemondynamics/claim src/main/kotlin/codes/honor/cobblemondynamics/pasture/PastureKey.kt src/test/kotlin/codes/honor/cobblemondynamics/claim
git commit -m "Add the global claim registry keyed by dimension and canonical target"
```

---

### Task 4: Scan scheduling and the block budget

**Rows:** CD-ASG-06, CD-ASG-07, CD-CFG-11, CD-PERF-03 (rule).

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/engine/ScanScheduler.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/engine/BlockScanCursor.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/engine/ScanSchedulerTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/engine/BlockScanCursorTest.kt`

**Interfaces:**
- Produces:

```kotlin
class ScanScheduler(val intervalTicks: Int, firstStart: Long) {
    val running: Boolean
    fun shouldStart(now: Long): Boolean
    fun start(now: Long)
    fun complete(lastWorkTick: Long)       // next start = first boundary (start + k × interval, k ≥ 1) after lastWorkTick
    companion object { fun phaseFor(posHash: Long, interval: Int): Long }   // stagger, 0 until interval
}

class BlockScanCursor(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int, val budget: Int) {
    val total: Int
    val done: Boolean
    fun step(visit: (x: Int, y: Int, z: Int) -> Unit): Int     // visits at most `budget` positions
    fun ticksNeeded(): Int                                      // ceil(total / budget)
}
```

- [ ] **Step 1: Write the failing tests**

```kotlin
// ScanSchedulerTest.kt
package codes.honor.cobblemondynamics.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ScanSchedulerTest {
    /** Runs a fake clock and returns the ticks at which passes start. Each pass needs `work` ticks. */
    private fun starts(interval: Int, work: Int, until: Long): List<Long> {
        val s = ScanScheduler(interval, firstStart = 0)
        val out = mutableListOf<Long>()
        var remaining = 0
        for (now in 0..until) {
            if (s.shouldStart(now)) { s.start(now); out += now; remaining = work }
            if (s.running) {
                remaining--
                if (remaining == 0) s.complete(now)
            }
        }
        return out
    }

    @Test
    fun `CD-ASG-06 passes start at ticks 0, 40 and 80 and never at 39`() {
        val s = starts(interval = 40, work = 1, until = 100)
        assertEquals(listOf(0L, 40L, 80L), s)
        assertFalse(39L in s)
    }

    @Test
    fun `CD-ASG-06 a 108-tick pass is followed by a pass at tick 120`() {
        assertEquals(listOf(0L, 120L, 240L), starts(interval = 40, work = 108, until = 250))
    }

    @Test
    fun `CD-ASG-06 a pass never starts before the previous one completes`() {
        assertEquals(listOf(0L, 80L), starts(interval = 40, work = 41, until = 100))
    }

    @Test
    fun `CD-CFG-11 an 80-tick interval starts passes every 80 ticks`() {
        assertEquals(listOf(0L, 80L, 160L), starts(interval = 80, work = 1, until = 200))
    }
}
```

```kotlin
// BlockScanCursorTest.kt
package codes.honor.cobblemondynamics.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BlockScanCursorTest {
    @Test
    fun `CD-ASG-07 the default 17 x 13 x 17 area takes 8 ticks and no tick checks a 513th position`() {
        val c = BlockScanCursor(-8, -6, -8, 8, 6, 8, budget = 512)
        assertEquals(3_757, c.total)
        val perTick = mutableListOf<Int>()
        while (!c.done) perTick += c.step { _, _, _ -> }
        assertEquals(8, perTick.size)
        assertTrue(perTick.all { it <= 512 })
        assertEquals(3_757, perTick.sum())
    }

    @Test
    fun `CD-ASG-06 radius 32 by 6 needs 108 ticks at 512 per tick`() {
        assertEquals(108, BlockScanCursor(-32, -6, -32, 32, 6, 32, budget = 512).ticksNeeded())
    }

    @Test
    fun `CD-CFG-11 a budget of 256 never checks more than 256 positions in a tick`() {
        val c = BlockScanCursor(-8, -6, -8, 8, 6, 8, budget = 256)
        while (!c.done) assertTrue(c.step { _, _, _ -> } <= 256)
    }

    @Test
    fun `every position is visited exactly once`() {
        val c = BlockScanCursor(0, 0, 0, 2, 1, 2, budget = 4)
        val seen = mutableSetOf<Triple<Int, Int, Int>>()
        while (!c.done) c.step { x, y, z -> assertTrue(seen.add(Triple(x, y, z))) }
        assertEquals(18, seen.size)
    }
}
```

- [ ] **Step 2: Run them and see them fail**

Run: `./gradlew test --tests "codes.honor.cobblemondynamics.engine.Scan*" --tests "codes.honor.cobblemondynamics.engine.BlockScan*"`
Expected: FAIL, unresolved references.

- [ ] **Step 3: Write the classes**

```kotlin
// ScanScheduler.kt
package codes.honor.cobblemondynamics.engine

class ScanScheduler(val intervalTicks: Int, firstStart: Long) {
    private var nextStart = firstStart
    private var currentStart = -1L

    val running: Boolean get() = currentStart >= 0

    fun shouldStart(now: Long): Boolean = !running && now >= nextStart

    fun start(now: Long) {
        currentStart = now
    }

    /** [lastWorkTick] is the tick on which the pass did its last work. */
    fun complete(lastWorkTick: Long) {
        val k = (lastWorkTick - currentStart) / intervalTicks + 1
        nextStart = currentStart + k * intervalTicks
        currentStart = -1
    }

    companion object {
        fun phaseFor(posHash: Long, interval: Int): Long = Math.floorMod(posHash, interval.toLong())
    }
}
```

```kotlin
// BlockScanCursor.kt
package codes.honor.cobblemondynamics.engine

class BlockScanCursor(
    private val minX: Int, private val minY: Int, private val minZ: Int,
    maxX: Int, maxY: Int, maxZ: Int,
    val budget: Int,
) {
    private val sizeX = maxX - minX + 1
    private val sizeY = maxY - minY + 1
    private val sizeZ = maxZ - minZ + 1
    val total: Int = sizeX * sizeY * sizeZ
    private var index = 0

    val done: Boolean get() = index >= total

    fun ticksNeeded(): Int = (total + budget - 1) / budget

    fun step(visit: (x: Int, y: Int, z: Int) -> Unit): Int {
        val end = minOf(total, index + budget)
        val count = end - index
        while (index < end) {
            val x = index % sizeX
            val z = (index / sizeX) % sizeZ
            val y = index / (sizeX * sizeZ)
            visit(minX + x, minY + y, minZ + z)
            index++
        }
        return count
    }
}
```

- [ ] **Step 4: Run them and see them pass**

Run: same as Step 2. Expected: PASS, 8 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/codes/honor/cobblemondynamics/engine/ScanScheduler.kt src/main/kotlin/codes/honor/cobblemondynamics/engine/BlockScanCursor.kt src/test/kotlin/codes/honor/cobblemondynamics/engine
git commit -m "Schedule scan passes on 40-tick boundaries with a per-tick block budget"
```

---

### Task 5: Work area and work spots

**Rows:** CD-ASG-08 (area rule), CD-ASG-09, CD-ASG-10, CD-CFG-14.

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/engine/WorkArea.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/engine/WorkSpots.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/engine/WorkAreaTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/engine/WorkSpotsTest.kt`

**Interfaces:**
- Produces:

```kotlin
data class WorkArea(val center: BlockPos, val horizontal: Int, val vertical: Int) {
    fun contains(pos: BlockPos): Boolean
    val minX: Int; val maxX: Int; val minY: Int; val maxY: Int; val minZ: Int; val maxZ: Int
    fun chunkRange(): Pair<ChunkPos, ChunkPos>
    companion object { fun of(pasture: BlockPos, settings: EngineSettings, maxWander: Int): WorkArea }
}

interface SpotGrid {
    fun isStandable(x: Int, y: Int, z: Int): Boolean   // a floor below, room for the body at y and y + 1
    fun blocksSight(x: Int, y: Int, z: Int): Boolean   // the caller excludes the target's own blocks
    fun fitsBody(x: Int, y: Int, z: Int): Boolean = true   // added in the end-of-phase pass: room for a wide or tall body
}

object WorkSpots {
    fun inRange(spotCenter: Vec3, targetCenter: Vec3, range: Double): Boolean
    fun hasLineOfSight(grid: SpotGrid, from: Vec3, to: Vec3): Boolean
    fun find(grid: SpotGrid, area: WorkArea, targetCenter: Vec3, range: Double, from: Vec3): BlockPos?
}
```

A spot's center is `(x + 0.5, y + 0.5, z + 0.5)`. `find` checks every position within `ceil(range)` of
the target center that is inside the area, standable, in range and has line of sight from its center to
the target center. It returns the one nearest to `from` (the worker), then the one nearest to the
target, then the lowest `BlockPos.asLong()`.

As built after the end-of-phase pass (M5): a spot is standable for a worker only when its whole body fits
there. The engine snaps a worker to the spot's center, and navigation stops a mob 1 block wide or wider at
`spot + (int)(bbWidth + 1) / 2` (`Arrival.stopOffset`), so `WorkerBody.boxes` gives the body's box at both
places, and `LevelSpotGrid.fitsBody` needs both free of block collisions. A body that stays inside the
spot's two cells (at most 1 block wide and 2 high) needs no more than `isStandable`. `find` asks `fitsBody`
only of a spot that would be the best so far. The pass found the gap: with one cell checked, a Camerupt 2.3
blocks wide got a spot in a gap one block wide, where its body overlapped the walls and its path could not
go (`WideWorkerTests.cdAsg09WideWorkerGetsASpotItsBodyFits`, `WorkSpotsTest`, `WorkerBodyTest`).

Play-test change (2026-11-10): the spot nearest to the worker could stand on top of the target. `find`
now takes the target's `TargetShape` (its blocks within one block of the canonical position) and never
returns a spot that stands on the target. It ranks a spot beside the target at its base height first, then
another spot at that height, then a spot at another height, and within each the one nearest to the target,
then to the worker (`WorkSpotsTest`, `WorkSpotTests`).

- [ ] **Step 1: Write the failing tests**

```kotlin
// WorkAreaTest.kt
package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.config.EngineSettings
import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkAreaTest {
    private val pasture = BlockPos(100, 64, 100)
    private val area = WorkArea.of(pasture, EngineSettings.DEFAULT, maxWander = 32)

    @Test
    fun `CD-ASG-08 offsets 8 and 6 are inside, 9 and 7 are outside`() {
        assertTrue(area.contains(pasture.offset(8, 0, 0)))
        assertTrue(area.contains(pasture.offset(0, 0, -8)))
        assertFalse(area.contains(pasture.offset(9, 0, 0)))
        assertTrue(area.contains(pasture.offset(0, 6, 0)))
        assertFalse(area.contains(pasture.offset(0, 7, 0)))
        assertFalse(area.contains(pasture.offset(0, -7, 0)))
    }

    @Test
    fun `CD-CFG-14 a radius above the roam limit acts as the roam limit`() {
        val wide = WorkArea.of(pasture, EngineSettings.DEFAULT.copy(horizontalRadius = 32), maxWander = 20)
        assertEquals(20, wide.horizontal)
        assertTrue(wide.contains(pasture.offset(20, 0, 0)))
        assertFalse(wide.contains(pasture.offset(21, 0, 0)))
    }
}
```

```kotlin
// WorkSpotsTest.kt
package codes.honor.cobblemondynamics.engine

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A flat floor at y = 0 with optional walls. Positions at y = 1 are standable unless a wall fills them. */
class GridFixture(private val walls: Set<BlockPos> = emptySet(), private val target: BlockPos) : SpotGrid {
    override fun isStandable(x: Int, y: Int, z: Int): Boolean =
        y == 1 && BlockPos(x, y, z) !in walls && BlockPos(x, y + 1, z) !in walls && BlockPos(x, y, z) != target
    override fun blocksSight(x: Int, y: Int, z: Int): Boolean =
        BlockPos(x, y, z) in walls || (y <= 0)
}

class WorkSpotsTest {
    private val target = BlockPos(0, 1, 0)
    private val center = Vec3.atCenterOf(target)
    private val area = WorkArea(BlockPos(0, 1, 0), 8, 6)

    @Test
    fun `CD-ASG-10 6_0 is in range for a ranged role and 6_1 is not`() {
        assertTrue(WorkSpots.inRange(center.add(6.0, 0.0, 0.0), center, 6.0))
        assertFalse(WorkSpots.inRange(center.add(6.1, 0.0, 0.0), center, 6.0))
    }

    @Test
    fun `CD-ASG-10 2_0 is in range for the Cranker and 2_1 is not`() {
        assertTrue(WorkSpots.inRange(center.add(0.0, 0.0, 2.0), center, 2.0))
        assertFalse(WorkSpots.inRange(center.add(0.0, 0.0, 2.1), center, 2.0))
    }

    @Test
    fun `CD-ASG-09 the chosen spot has a clear line to the target center`() {
        // The worker stands east of the target behind a wall at x = 2. The nearest spot behind the wall is refused.
        val walls = (0..3).flatMap { y -> (-2..2).map { z -> BlockPos(2, y, z) } }.toSet()
        val grid = GridFixture(walls, target)
        val from = Vec3(4.5, 1.0, 0.5)
        val spot = WorkSpots.find(grid, area, center, 6.0, from)
        assertNotNull(spot) // JUnit's assertNotNull returns Unit, not the value
        assertTrue(area.contains(spot!!))
        assertTrue(grid.isStandable(spot.x, spot.y, spot.z))
        assertTrue(WorkSpots.hasLineOfSight(grid, Vec3.atCenterOf(spot), center))
        assertTrue(spot.x < 2, "spot $spot is behind the wall")
    }

    @Test
    fun `CD-ASG-09 a spot outside the work area is never chosen`() {
        val farTarget = BlockPos(8, 1, 0)
        val grid = GridFixture(emptySet(), farTarget)
        val spot = WorkSpots.find(grid, area, Vec3.atCenterOf(farTarget), 6.0, Vec3(14.5, 1.0, 0.5))!!
        assertTrue(spot.x <= 8)
    }

    @Test
    fun `the nearest spot to the worker wins`() {
        val grid = GridFixture(emptySet(), target)
        assertEquals(BlockPos(1, 1, 0), WorkSpots.find(grid, area, center, 6.0, Vec3(1.5, 1.0, 0.5)))
    }
}
```

- [ ] **Step 2: Run them and see them fail**

Run: `./gradlew test --tests "codes.honor.cobblemondynamics.engine.Work*"`
Expected: FAIL, unresolved references.

- [ ] **Step 3: Write the classes**

`WorkArea.of` uses `EngineSettings.effectiveRadius` for both radii. `contains` is
`abs(dx) <= horizontal && abs(dz) <= horizontal && abs(dy) <= vertical`.

```kotlin
// WorkSpots.kt
package codes.honor.cobblemondynamics.engine

import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil
import kotlin.math.floor

object WorkSpots {
    private const val EPSILON = 1e-9

    fun inRange(spotCenter: Vec3, targetCenter: Vec3, range: Double): Boolean =
        spotCenter.distanceToSqr(targetCenter) <= range * range + EPSILON

    /** 3D DDA through block cells; the start and end cells never block. */
    fun hasLineOfSight(grid: SpotGrid, from: Vec3, to: Vec3): Boolean {
        var x = floor(from.x).toInt(); var y = floor(from.y).toInt(); var z = floor(from.z).toInt()
        val endX = floor(to.x).toInt(); val endY = floor(to.y).toInt(); val endZ = floor(to.z).toInt()
        val d = to.subtract(from)
        val stepX = Mth.sign(d.x); val stepY = Mth.sign(d.y); val stepZ = Mth.sign(d.z)
        fun next(p: Double, dp: Double, s: Int) = if (s == 0) Double.MAX_VALUE else ((if (s > 0) floor(p) + 1 else floor(p)) - p) / dp
        var tMaxX = next(from.x, d.x, stepX); var tMaxY = next(from.y, d.y, stepY); var tMaxZ = next(from.z, d.z, stepZ)
        val tDeltaX = if (stepX == 0) Double.MAX_VALUE else stepX / d.x
        val tDeltaY = if (stepY == 0) Double.MAX_VALUE else stepY / d.y
        val tDeltaZ = if (stepZ == 0) Double.MAX_VALUE else stepZ / d.z
        while (!(x == endX && y == endY && z == endZ)) {
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) { x += stepX; tMaxX += tDeltaX }
            else if (tMaxY <= tMaxZ) { y += stepY; tMaxY += tDeltaY }
            else { z += stepZ; tMaxZ += tDeltaZ }
            if (x == endX && y == endY && z == endZ) break
            if (grid.blocksSight(x, y, z)) return false
            if (minOf(tMaxX, tMaxY, tMaxZ) > 1.0 + EPSILON) break
        }
        return true
    }

    fun find(grid: SpotGrid, area: WorkArea, targetCenter: Vec3, range: Double, from: Vec3): BlockPos? {
        val r = ceil(range).toInt()
        val cx = floor(targetCenter.x).toInt(); val cy = floor(targetCenter.y).toInt(); val cz = floor(targetCenter.z).toInt()
        var best: BlockPos? = null
        var bestKey = Triple(Double.MAX_VALUE, Double.MAX_VALUE, Long.MAX_VALUE)
        for (x in cx - r..cx + r) for (y in cy - r..cy + r) for (z in cz - r..cz + r) {
            val pos = BlockPos(x, y, z)
            if (!area.contains(pos) || !grid.isStandable(x, y, z)) continue
            val c = Vec3.atCenterOf(pos)
            if (!inRange(c, targetCenter, range) || !hasLineOfSight(grid, c, targetCenter)) continue
            val key = Triple(c.distanceToSqr(from), c.distanceToSqr(targetCenter), pos.asLong())
            if (compareValuesBy(key, bestKey, { it.first }, { it.second }, { it.third }) < 0) { best = pos; bestKey = key }
        }
        return best
    }
}
```

- [ ] **Step 4: Run them and see them pass**

Run: same as Step 2. Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/codes/honor/cobblemondynamics/engine/WorkArea.kt src/main/kotlin/codes/honor/cobblemondynamics/engine/WorkSpots.kt src/test/kotlin/codes/honor/cobblemondynamics/engine/WorkAreaTest.kt src/test/kotlin/codes/honor/cobblemondynamics/engine/WorkSpotsTest.kt
git commit -m "Add the work area box and line-of-sight work spot selection"
```

---

### Task 6: Worker state machine and assignment rules

**Rows:** CD-ASG-03 (rule), CD-ASG-04 (rule), CD-ASG-05 (rule), CD-ASG-11, CD-ASG-12, CD-ASG-13, CD-ASG-14,
CD-ASG-17, CD-ASG-18, CD-ASG-19 (unit), CD-ASG-20, CD-CFG-12, CD-CFG-15, CD-STA-09 (refusal hook).

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/engine/WorkerStatus.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/engine/Worker.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/engine/WorkerMachine.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/engine/Assigner.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/engine/WorkerMachineTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/engine/AssignerTest.kt`

**Interfaces:**
- Produces:

```kotlin
enum class WorkerStatus { IDLE, TRAVELING, WORKING, STANDBY, TO_BED, SLEEPING, RESTING_IN_PLACE, HEALING }

class Worker(val pokemonId: UUID, var pasture: PastureKey) {
    var status: WorkerStatus = WorkerStatus.IDLE
    var tired: Boolean = false
    var leashed: Boolean = false
    var claimTick: Long = -1           // tick of the current work claim
    var statusSince: Long = 0          // tick of the last status change
    var retryAt: Long = -1             // leashed retry after a missed arrival; -1 when not waiting
    var missedArrival: Boolean = false
    val skipUntil: MutableMap<TargetKey, Long> = HashMap()
    fun isSkipped(key: TargetKey, now: Long): Boolean
}

sealed interface MachineEvent {
    data object StartedWorking : MachineEvent
    data object EnteredStandby : MachineEvent
    data class ReleaseClaim(val skip: TargetKey?, val until: Long) : MachineEvent   // auto timeout
    data object MissedArrival : MachineEvent                                          // leashed: message the owner
    data object Retry : MachineEvent                                                  // leashed: travel again
}

class WorkerMachine(private val settings: EngineSettings) {
    fun onClaim(w: Worker, now: Long)                      // -> TRAVELING, claimTick = now
    fun onTick(w: Worker, now: Long, arrived: Boolean, demand: () -> Boolean, key: TargetKey?): MachineEvent?
    fun onRelease(w: Worker, now: Long)                    // -> IDLE, clears timers
    fun canMoveFromStandby(w: Worker, now: Long): Boolean  // !leashed && STANDBY && !missedArrival && now - statusSince >= standbyMoveTicks
}

data class WorkerView(val id: UUID, val pos: Vec3, val types: Set<String>, val hasFairyType: Boolean, val tired: Boolean,
                      val isSkipped: (TargetKey) -> Boolean)
data class CandidateView(val key: TargetKey, val center: Vec3, val roles: List<RoleDefinition>, val hasDemand: () -> Boolean)

object Assigner {
    /** CD-ASG-03: the nearest free target of an auto-assign role the worker can do. */
    fun pickForIdle(w: WorkerView, candidates: List<CandidateView>, isFree: (TargetKey) -> Boolean): Pair<CandidateView, RoleDefinition>?
    /** CD-ASG-19: the nearest free target with demand, for an auto worker that waited in Standby long enough. */
    fun pickForStandbyMove(w: WorkerView, current: TargetKey, candidates: List<CandidateView>, isFree: (TargetKey) -> Boolean): Pair<CandidateView, RoleDefinition>?
}
```

Rules that `onTick` applies, in this order:

1. `TRAVELING` and `arrived`: `WORKING` if `demand()`, else `STANDBY` (CD-ASG-11, CD-ASG-12).
2. `TRAVELING`, not arrived, and `now - claimTick >= travelTimeoutTicks`: an auto worker returns
   `ReleaseClaim(key, now + retryWaitTicks)` and goes `IDLE` (CD-ASG-13, CD-ASG-14). A leashed worker goes
   `STANDBY` with `missedArrival = true`, `retryAt = claimTick + travelTimeoutTicks + retryWaitTicks`, and
   returns `MissedArrival` (CD-ASG-17, CD-LSH-21).
3. `STANDBY` with `missedArrival` and `now >= retryAt`: `TRAVELING`, `claimTick = now`, returns `Retry`.
   The retry is a fresh claim, as `onClaim`: it clears `missedArrival` and `retryAt`. Without that, a
   worker that arrives after the retry to no demand matches this rule again on the next tick and
   bounces back to `TRAVELING`.
4. `WORKING` and not `demand()`: `STANDBY` (checked every tick).
5. `STANDBY` without `missedArrival`, and `(now - statusSince) % 20 == 0`: `WORKING` if `demand()`
   (CD-ASG-12, R45).

- [ ] **Step 1: Write the failing tests**

```kotlin
// WorkerMachineTest.kt
package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.config.EngineSettings
import codes.honor.cobblemondynamics.pasture.PastureKey
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class WorkerMachineTest {
    private val dim = ResourceLocation.withDefaultNamespace("overworld")
    private val key = TargetKey.block(dim, BlockPos.ZERO)
    private val machine = WorkerMachine(EngineSettings.DEFAULT)
    private fun worker(leashed: Boolean = false) = Worker(UUID.randomUUID(), PastureKey(dim, 0)).also { it.leashed = leashed }

    @Test
    fun `CD-ASG-11 arrival with demand puts the worker in Working on the same tick`() {
        val w = worker(); machine.onClaim(w, 0)
        machine.onTick(w, 50, arrived = true, demand = { true }, key = key)
        assertEquals(WorkerStatus.WORKING, w.status)
    }

    @Test
    fun `CD-ASG-12 no demand keeps Standby, and new demand resumes work within 20 ticks`() {
        val w = worker(); machine.onClaim(w, 0)
        machine.onTick(w, 10, arrived = true, demand = { true }, key = key)
        machine.onTick(w, 11, arrived = true, demand = { false }, key = key)
        assertEquals(WorkerStatus.STANDBY, w.status)
        var demand = false
        for (t in 12L..200L) { if (t == 100L) demand = true; machine.onTick(w, t, true, { demand }, key); if (w.status == WorkerStatus.WORKING) { assertTrue(t - 100 <= 20); return } }
        error("never resumed")
    }

    @Test
    fun `CD-ASG-13 an auto claim is held at tick 599 and released at 600`() {
        val w = worker(); machine.onClaim(w, 0)
        assertEquals(null, machine.onTick(w, 599, arrived = false, demand = { true }, key = key))
        val e = machine.onTick(w, 600, arrived = false, demand = { true }, key = key)
        assertInstanceOf(MachineEvent.ReleaseClaim::class.java, e)
        assertEquals(WorkerStatus.IDLE, w.status)
    }

    @Test
    fun `CD-ASG-14 the timed-out target is refused for 6,000 ticks`() {
        val w = worker(); machine.onClaim(w, 0)
        val e = machine.onTick(w, 600, false, { true }, key) as MachineEvent.ReleaseClaim
        w.skipUntil[key] = e.until
        assertTrue(w.isSkipped(key, 600 + 5_999))
        assertFalse(w.isSkipped(key, 600 + 6_000))
    }

    @Test
    fun `CD-ASG-17 a leashed worker waits in Standby from 600 to 6,599 and travels at 6,600`() {
        val w = worker(leashed = true); machine.onClaim(w, 0)
        assertEquals(MachineEvent.MissedArrival, machine.onTick(w, 600, false, { true }, key))
        for (t in 601L..6_599L) { machine.onTick(w, t, false, { true }, key); assertEquals(WorkerStatus.STANDBY, w.status, "tick $t") }
        assertEquals(MachineEvent.Retry, machine.onTick(w, 6_600, false, { true }, key))
        assertEquals(WorkerStatus.TRAVELING, w.status)
    }

    @Test
    fun `CD-ASG-12 a leashed worker that arrives after its retry stays in Standby`() {
        val w = worker(leashed = true); machine.onClaim(w, 0)
        machine.onTick(w, 600, false, { true }, key)         // missed arrival
        machine.onTick(w, 6_600, false, { true }, key)       // retry
        machine.onTick(w, 6_700, true, { false }, key)       // arrives, and the target has no demand
        assertEquals(null, machine.onTick(w, 6_701, true, { false }, key))
        assertEquals(WorkerStatus.STANDBY, w.status)
    }

    @Test
    fun `CD-CFG-12 a 1,200-tick timeout holds at 1,199 and releases at 1,200`() {
        val m = WorkerMachine(EngineSettings.DEFAULT.copy(travelTimeoutTicks = 1_200))
        val w = worker(); m.onClaim(w, 0)
        assertEquals(null, m.onTick(w, 1_199, false, { true }, key))
        assertInstanceOf(MachineEvent.ReleaseClaim::class.java, m.onTick(w, 1_200, false, { true }, key))
    }

    @Test
    fun `CD-ASG-19 CD-CFG-15 the Standby move opens at the configured delay`() {
        val w = worker(); machine.onClaim(w, 0)
        machine.onTick(w, 5, true, { false }, key)                // Standby since 5
        assertFalse(machine.canMoveFromStandby(w, 5 + 199))
        assertTrue(machine.canMoveFromStandby(w, 5 + 200))
        val slow = WorkerMachine(EngineSettings.DEFAULT.copy(standbyMoveTicks = 400))
        assertFalse(slow.canMoveFromStandby(w, 5 + 399))
        assertTrue(slow.canMoveFromStandby(w, 5 + 400))
    }

    @Test
    fun `CD-ASG-20 a leashed worker never moves from Standby`() {
        val w = worker(leashed = true); machine.onClaim(w, 0)
        machine.onTick(w, 5, true, { false }, key)
        assertFalse(machine.canMoveFromStandby(w, 10_000))
    }

    @Test
    fun `CD-ASG-18 a scripted day leaves exactly one state on every tick`() {
        val w = worker(); var arrived = false; var demand = true
        machine.onClaim(w, 0)
        for (t in 0L until 24_000L) {
            if (t == 100L) arrived = true
            if (t % 3_000 == 0L) demand = !demand
            if (t == 20_000L) machine.onRelease(w, t)
            machine.onTick(w, t, arrived, { demand }, key)
            assertTrue(w.status in WorkerStatus.entries)
        }
    }
}
```

M2 Task 4 extends `CD-ASG-18` with the rest states and a healer.

```kotlin
// AssignerTest.kt
package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.role.role
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class AssignerTest {
    private val dim = ResourceLocation.withDefaultNamespace("overworld")
    private val stoker = role("stoker", setOf("fire"))
    private val pump = role("pump", setOf("water"), autoAssign = false)
    private fun candidate(x: Int, roles: List<codes.honor.cobblemondynamics.role.RoleDefinition> = listOf(stoker), demand: Boolean = true) =
        CandidateView(TargetKey.block(dim, BlockPos(x, 64, 0)), Vec3(x + 0.5, 64.5, 0.5), roles) { demand }
    private fun fire(tired: Boolean = false, fairy: Boolean = false, types: Set<String> = setOf("fire")) =
        WorkerView(UUID.randomUUID(), Vec3(0.5, 64.0, 0.5), types, fairy, tired) { false }

    @Test
    fun `CD-ASG-03 the nearest free target wins`() {
        val near = candidate(3); val far = candidate(6)
        assertEquals(near.key, Assigner.pickForIdle(fire(), listOf(far, near)) { true }!!.first.key)
        assertEquals(far.key, Assigner.pickForIdle(fire(), listOf(far, near)) { it != near.key }!!.first.key)
    }

    @Test
    fun `CD-ASG-04 a Fairy type never takes a machine through an auto claim`() {
        assertNull(Assigner.pickForIdle(fire(fairy = true, types = setOf("fire", "fairy")), listOf(candidate(3))) { true })
    }

    @Test
    fun `CD-ASG-05 a leash-only role is never auto-assigned`() {
        val water = WorkerView(UUID.randomUUID(), Vec3.ZERO, setOf("water"), false, false) { false }
        assertNull(Assigner.pickForIdle(water, listOf(candidate(3, listOf(pump)))) { true })
    }

    @Test
    fun `CD-STA-09 a Tired worker gets no machine`() {
        assertNull(Assigner.pickForIdle(fire(tired = true), listOf(candidate(3))) { true })
    }

    @Test
    fun `CD-ASG-19 a Standby move needs demand and never picks the current target`() {
        val current = candidate(3, demand = false); val empty = candidate(4, demand = false); val busy = candidate(6, demand = true)
        assertEquals(busy.key, Assigner.pickForStandbyMove(fire(), current.key, listOf(current, empty, busy)) { true }!!.first.key)
    }
}
```

- [ ] **Step 2: Run them and see them fail**

Run: `./gradlew test --tests "codes.honor.cobblemondynamics.engine.WorkerMachineTest" --tests "codes.honor.cobblemondynamics.engine.AssignerTest"`
Expected: FAIL, unresolved references.

- [ ] **Step 3: Write the classes**

`Worker.isSkipped(key, now)` is `now < (skipUntil[key] ?: Long.MIN_VALUE)`. Every status change goes
through one private `set(w, status, now)` that also sets `statusSince`. `WorkerMachine.onTick` implements
the five rules above, in that order, and returns at most one event: the first rule that matches the
status applies, and the others wait for the next tick. Entering `WORKING` (rules 1 and 5) returns
`StartedWorking`, and entering `STANDBY` through rule 1 or 4 returns `EnteredStandby`, so the engine
knows when to call `handler.onStart` and `handler.onStop`. `onRelease` sets `IDLE`, `claimTick = -1`,
`retryAt = -1` and `missedArrival = false`, and the auto timeout of rule 2 does the same reset.

`Assigner.pickForIdle` returns `null` when the worker is tired or has a Fairy type. Otherwise it keeps
the candidates that are free and not skipped, maps each to `RoleSelection.best` of its roles that are
`autoAssign` and eligible for the worker's types, drops candidates with no role, and returns the
candidate nearest to the worker (ties: lowest `BlockPos` long of the key, so the choice is stable; block
keys sort before entity keys, and entity keys sort by UUID).
`pickForStandbyMove` applies the same filters, drops `current`, keeps only candidates whose
`hasDemand()` is true, and returns the nearest.

- [ ] **Step 4: Run them and see them pass**

Run: same as Step 2. Expected: PASS, 15 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/codes/honor/cobblemondynamics/engine src/test/kotlin/codes/honor/cobblemondynamics/engine
git commit -m "Add the worker state machine with arrival timeouts, retries and Standby moves"
```
