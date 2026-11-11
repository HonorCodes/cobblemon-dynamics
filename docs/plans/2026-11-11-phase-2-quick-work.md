# Quick Work: party Pokémon work the block that their owner aims at

Part of the [Phase 2 plan](2026-11-10-phase-2-plan.md). Read its Global Constraints, including the multiplayer rules,
first. Branch: `quick-work`. Builds on M7. It is one pull request into `main`, which merges before M8 Task 6 starts, so the
multiplayer audit, the end-of-phase pass and the Phase 2 gate of M8 cover it.

**Milestone goal:** A player sends out a party Pokémon with R while the crosshair is on a block that one of the Pokémon's
auto-assign roles works. The Pokémon then gets a Quick Work order: it claims the block, walks to it and works it as a pasture
worker does. The order ends for good when the owner walks away, recalls the Pokémon, logs out or changes dimension, when the
Pokémon gets Tired, battles or cannot reach the block, when the block breaks, and when the block needs no work for the
Standby time. After the end, the Pokémon follows its owner, and no Quick Work code runs for it.

**Design:** [ADR-0013](../adr/0013-quick-work-for-party-pokemon.md) and the QWK rows of the
[spec sheet](2026-11-09-v1-spec-sheet.md). The milestone builds a small solo runtime beside the pasture runtimes of the work
engine. It has one worker, a work area around its target and no scan, and it reuses the claims, `WorkerMachine`,
`BrainBridge`, the handlers, the effects and Work Stamina.

Before Task 1, check the references of this milestone against the jars (rule 3 of the index).

Checked on 2026-11-11 with `javap` on the Cobblemon 1.8.4 jar (`neoforge-1.8.4+1.21.1.jar`) and on the merged NeoForge
21.1.265 jar (the NeoForm `compiledWithNeoForge` output), and in the Cobblemon 1.8.4 sources and the NeoForge 21.1.265
patched Minecraft sources:

- `SendOutPokemonHandler` is a Kotlin object with `handle(SendOutPokemonPacket, MinecraftServer, ServerPlayer)`, and
  `SendOutPokemonPacket(int)` is public.
- `CobblemonEvents.POKEMON_SENT_PRE` is a `CancelableObservable<PokemonSentEvent.Pre>`, and `POKEMON_SENT_POST` is an
  `EventObservable<PokemonSentEvent.Post>`. `Pre` has `getPokemon()`, `getLevel()` (a `ServerLevel`) and `getPosition()`.
  `Post` adds `getPokemonEntity()`. `Observable.subscribe(Priority, (T) -> Unit)` and `Priority.LOWEST` exist.
- `PlayerPartyStore.getPlayerUUID()`, the static `BattleRegistry.getBattleByParticipatingPlayer(ServerPlayer)`,
  `PokemonEntity.isBattling()`, `getBattleId()`, `getBeamMode()` and `getOwner()`, and `Pokemon.getEntity()`, `getState()`,
  `getStoreCoordinates()`, `getOwnerPlayer()`, `getOwnerUUID()`, `isFainted()`, `isBattleClone()` and
  `getDisplayName(boolean)` exist.
- In the sources: `POKEMON_SENT_PRE` fires inside `Pokemon.sendOut` before the entity exists (`Pokemon.kt:800`). For a
  player it fires inside the packet handler, because only an `NPCEntity` is a `PokemonSender` (`Pokemon.kt:832-836`).
  `POKEMON_SENT_POST` fires `SEND_OUT_DURATION` (1.5 s) later, unless a recall started (`Pokemon.kt:898-909`). Cobblemon's
  server packets run on the main thread (`CobblemonNeoForgeNetworkManager.kt:27-30`). `recallWithAnimation` sets `BEAM_MODE`
  to 3 at once (`PokemonEntity.kt:876`). `Pokemon.entity` is null once the Pokémon is not out (`Pokemon.kt:444-445`).
  `getOwnerEntity` finds a player through `PlayerList.getPlayer` (`Pokemon.kt:1537-1549`).
- In the Cobblemon jar, `pokemon_follows_owner.json` adds `move_to_owner` to the `idle` activity, with `teleportDistance` 32
  and `maxDistance` 5 for a party Pokémon, and `pokemon_owned.json` applies it to every Pokémon that is not wild.
- `Entity.calculateViewVector(float, float)` is public and final. `Entity.getEyePosition()`, `getXRot()`, `getYRot()`,
  `isAlive()` and `lookAt(EntityAnchorArgument.Anchor, Vec3)` exist, and `lookAt` sets `xRot`, `yRot` and `yHeadRot`
  (`Entity.java:3294-3305`). `BlockGetter.clip(ClipContext)` and
  `ClipContext(Vec3, Vec3, ClipContext.Block, ClipContext.Fluid, Entity)` exist, with `Block.OUTLINE` and `Fluid.NONE`.
  `BlockHitResult.getType()` and `getBlockPos()`, `HitResult.Type.BLOCK`, `AABB(BlockPos)` and `AABB.distanceToSqr(Vec3)` exist.
- `ServerPlayer.mayInteract(Level, BlockPos)` returns `super.mayInteract(level, pos) && level.mayInteract(this, pos)`
  (`ServerPlayer.java:2036-2038`). `Entity.mayInteract` returns true (`Entity.java:3683-3685`), and
  `ServerLevel.mayInteract(Player, BlockPos)` checks spawn protection and the world border (`ServerLevel.java:806-808`).
  `ServerPlayer.isSpectator()` exists.
- `PlayerList` holds `private final Map<UUID, ServerPlayer> playersByUUID` and has `getPlayer(UUID)`. `FakePlayer` extends
  `ServerPlayer` and overrides neither `mayInteract` nor `isSpectator`. `PlayerEvent.PlayerLoggedOutEvent` exists.
- Minecraft's translations take positional arguments such as `%1$s` (`TranslatableContents.java:61`).

Inferred, and checked by the GameTests of Task 3 on 2026-11-11:

- In the GameTest server, `POKEMON_SENT_POST` fires about 30 ticks after `SendOutPokemonHandler.handle`, because the entity
  runs its scheduled tasks when it ticks. Measured: the order's claim came 31 ticks after the key press.
- A fake player in `PlayerList.playersByUUID`, and not in `PlayerList.players`, is found by `getPlayer` and gets no
  broadcast, no spawns and no ticks. Cobblemon then makes a party Pokémon of that player follow it. Shown by
  `cdQwk13Qwk14Qwk15LeashEndsTheOrderForGood`.
- `raycastSafeSendout` puts the Charmander of the test scene in front of the furnace face that its owner looks at. Measured:
  the Charmander came out at (5.375, 2, 11.5) of the test, in front of the east face of furnace A, which spans x 4 to 5.
- The work activity replaces the idle activity of a party Pokémon too, so `move_to_owner` does not run while the Pokémon holds
  a work order, and the idle activity comes back when the work order clears. Shown by CD-QWK-12 and CD-QWK-15.

Found by the GameTests of Task 3 on 2026-11-11:

- The hold of a work order renews Cobblemon's `PATH_COOLDOWN` for 40 ticks on every tick (`WorkMoveTask`), and
  `move_to_owner` runs only with no `PATH_COOLDOWN`. Then Cobblemon's `FollowWalkTargetTask` waits `20 + random(20)` ticks
  before it plans the walk (Cobblemon sources, `entity/ai/FollowWalkTargetTask.kt`, `resetPathUpdateCountdownTicks`). In 8
  runs the follow began 41 ticks after the end, and a Charmander came only 1.0 to 2.2 blocks closer to its owner within 100
  ticks, so CD-QWK-15 failed in most runs. The end of an order now erases `PATH_COOLDOWN` (`BrainBridge.endHold`): in 8 runs the
  follow began 1 tick after the end, and the Charmander came 3.6 to 4.6 blocks closer within 100 ticks.
- A battle id that names no battle does not stay. Cobblemon's `PokemonServerDelegate.tick` clears it on the entity's next
  tick when `BattleRegistry.getBattle` gives null or an ended battle (`entity/pokemon/PokemonServerDelegate.kt:315-321`). The
  engine's level tick comes after the entity ticks, so it never sees `isBattling`, and a GameTest that only sets `battleId`
  cannot pass. In a real battle, `BattleRegistry.startShowdown` gives each Pokémon of the actors that is out the battle's id
  (`battles/BattleRegistry.kt:162-168`), and the id stays while the battle runs. A wild battle that `BattleBuilder.pve` started
  for a listed fake owner had ended and left the registry 1 tick later, with no error in the log. So the CD-QWK-21 GameTest
  needs a battle that stays in the GameTest server, and it waits for the end-of-phase pass (Task 3, Step 6).

---

### Task 1: Quick Work settings and the pure rules

**Rows:** CD-CFG-16, CD-CFG-17 and CD-CFG-18 (the keys), CD-CFG-01 (the three new keys in the key lists), CD-QWK-03,
CD-QWK-04 and CD-QWK-05 (the pick rule), CD-QWK-06, CD-QWK-07, CD-QWK-08 and CD-QWK-09 (the order of the refusals),
CD-QWK-13, CD-QWK-16, CD-QWK-18, CD-QWK-19, CD-QWK-20 and CD-QWK-21 (the end rule and the trace range rule), CD-UI-12 (the
lang keys).

**Files:**
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/config/ConfigRanges.kt`, `config/EngineSettings.kt`,
  `config/ServerConfig.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/engine/QuickWorkRules.kt`
- Modify: `src/main/resources/assets/cobblemon_dynamics/lang/en_us.json`
- Modify: `scripts/testpack/testpack.py` (`SERVER_CONFIG_KEYS`)
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/config/ConfigTest.kt`, and create
  `src/test/kotlin/codes/honor/cobblemondynamics/engine/QuickWorkRulesTest.kt`

**Interfaces:**
- Consumes: `RoleSelection.best`, `RoleDefinition`, `BindingShape`, `WorkerStatus`.
- Produces:

```kotlin
data class EngineSettings(
    // ... Phase 1 values ...
    val quickWorkEnabled: Boolean = true,
    val quickWorkTargetRange: Int = 16,
    val quickWorkLeashRange: Int = 16,
)

/** The lang key constants STARTED, NEEDS_LEASH, NO_PERMISSION, TIRED, CLAIMED, NO_DEMAND and CANNOT_REACH, AIM_TICKS and RECALL_BEAM. */
object QuickWorkRules {
    sealed interface Pick          // Pick.Order(role), Pick.NeedsLeash or Pick.None
    data class Watch(/* what one tick of an order sees */)

    fun isExpired(aimTick: Long, now: Long): Boolean
    fun dropExpired(aims: MutableMap<UUID, QuickAim>, now: Long)
    fun beyondLeash(ownerDistanceSqr: Double, leashRange: Int): Boolean
    fun traceRange(enabled: Boolean, ownerBattling: Boolean, targetRange: Int, leashRange: Int): Int?
    fun mayOrder(role: RoleDefinition, scanned: Boolean, shape: BindingShape): Boolean
    fun pick(fitting: List<RoleDefinition>, types: List<String>, hasFairyType: Boolean, orderable: (RoleDefinition) -> Boolean): Pick
    fun refusal(mayUse: Boolean, tired: Boolean, targetFree: Boolean, hasDemand: () -> Boolean): String?
    fun endReason(w: Watch): QuickEnd?
}

enum class QuickEnd(val messageKey: String) { TOO_FAR, RECALLED, TIRED, TARGET, NO_DEMAND, OWNER_LEFT, BATTLE, UNREACHABLE }
```

- [ ] **Step 1: Failing unit tests.**

```kotlin
// ConfigTest: CFG_01_KEYS gains "quickWork.enabled", "quickWork.targetRange" and "quickWork.leashRange" after
// "leash.pendingSelectionTicks", in the order that the config file writes them.
@Test
fun `CD-CFG-16 CD-CFG-17 CD-CFG-18 Quick Work keys, ranges and defaults`() {
    val s = EngineSettings.DEFAULT
    assertTrue(s.quickWorkEnabled)
    assertEquals(16, s.quickWorkTargetRange)
    assertEquals(16, s.quickWorkLeashRange)
    for (key in listOf("quickWork.targetRange", "quickWork.leashRange")) {
        assertTrue(ServerConfig.isValid(key, 2), key)
        assertTrue(ServerConfig.isValid(key, 24), key)
        assertFalse(ServerConfig.isValid(key, 1), key)
        assertFalse(ServerConfig.isValid(key, 25), key)
    }
}
```

```kotlin
package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.handler.BindingShape
import codes.honor.cobblemondynamics.role.BuiltInRoles
import codes.honor.cobblemondynamics.role.RoleDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class QuickWorkRulesTest {
    private val stoker = BuiltInRoles.parse("stoker")
    private val brewStoker = BuiltInRoles.parse("brew_stoker")
    private val brewer = BuiltInRoles.parse("brewer")
    private val telekinesis = BuiltInRoles.parse("telekinesis")
    private val pump = BuiltInRoles.parse("pump")
    private val wheelDriver = BuiltInRoles.parse("wheel_driver")

    /** The handlers of the default role files: a leash-only handler has the scan kind NONE, and Telekinesis names two targets. */
    private fun orderable(role: RoleDefinition): Boolean = QuickWorkRules.mayOrder(
        role,
        scanned = role.handler.path !in setOf("fluid_fill", "telekinesis"),
        shape = if (role.handler.path == "telekinesis") BindingShape.SOURCE_AND_DESTINATION else BindingShape.SINGLE,
    )

    private fun pick(fitting: List<RoleDefinition>, types: List<String>, fairy: Boolean = false) =
        QuickWorkRules.pick(fitting, types, fairy, ::orderable)

    private val calm = QuickWorkRules.Watch(
        entityGone = false,
        battling = false,
        ownerPresent = true,
        ownerDistanceSqr = 100.0,
        leashRange = 16,
        holdsClaim = true,
        status = WorkerStatus.WORKING,
        ticksInStatus = 0,
        standbyLimit = 200,
    )

    @Test
    fun `CD-QWK-03 the order gets the role that an auto claim gets`() {
        assertEquals(QuickWorkRules.Pick.Order(brewStoker), pick(listOf(brewer, brewStoker), listOf("poison", "fire")))
        assertEquals(QuickWorkRules.Pick.Order(stoker), pick(listOf(stoker), listOf("fire")))
    }

    @Test
    fun `CD-QWK-03 CD-QWK-04 a leash-only or two-target role gives no order but the Work Leash message`() {
        assertEquals(QuickWorkRules.Pick.NeedsLeash, pick(listOf(telekinesis), listOf("psychic")))
        assertEquals(QuickWorkRules.Pick.NeedsLeash, pick(listOf(pump), listOf("water")))
        val autoTelekinesis = telekinesis.copy(autoAssign = true)
        assertFalse(QuickWorkRules.mayOrder(autoTelekinesis, scanned = true, shape = BindingShape.SOURCE_AND_DESTINATION))
    }

    @Test
    fun `CD-QWK-05 a Fairy-type is a healer and gets no order`() =
        assertEquals(QuickWorkRules.Pick.None, pick(listOf(wheelDriver), listOf("water", "fairy"), fairy = true))

    @Test
    fun `CD-QWK-03 a block that no role fits gives nothing`() = assertEquals(QuickWorkRules.Pick.None, pick(emptyList(), listOf("fire")))

    @Test
    fun `CD-QWK-06 to CD-QWK-09 the refusals come in their order, and the demand is asked last`() {
        var asked = 0
        val noDemand = {
            asked++
            false
        }
        // The arguments: mayUse, tired, targetFree, hasDemand.
        assertEquals(QuickWorkRules.NO_PERMISSION, QuickWorkRules.refusal(false, true, false, noDemand))
        assertEquals(QuickWorkRules.TIRED, QuickWorkRules.refusal(true, true, false, noDemand))
        assertEquals(QuickWorkRules.CLAIMED, QuickWorkRules.refusal(true, false, false, noDemand))
        assertEquals(0, asked)
        assertEquals(QuickWorkRules.NO_DEMAND, QuickWorkRules.refusal(true, false, true, noDemand))
        assertNull(QuickWorkRules.refusal(mayUse = true, tired = false, targetFree = true) { true })
    }

    @Test
    fun `CD-CFG-16 CD-CFG-18 CD-QWK-21 the trace reaches the smaller range, and none while off or in a battle`() {
        assertEquals(16, QuickWorkRules.traceRange(enabled = true, ownerBattling = false, targetRange = 16, leashRange = 16))
        assertEquals(8, QuickWorkRules.traceRange(enabled = true, ownerBattling = false, targetRange = 24, leashRange = 8))
        assertNull(QuickWorkRules.traceRange(enabled = false, ownerBattling = false, targetRange = 16, leashRange = 16))
        assertNull(QuickWorkRules.traceRange(enabled = true, ownerBattling = true, targetRange = 16, leashRange = 16))
    }

    @Test
    fun `CD-QWK-13 the order ends past the leash and not at it`() {
        assertNull(QuickWorkRules.endReason(calm.copy(ownerDistanceSqr = 256.0)))
        assertEquals(QuickEnd.TOO_FAR, QuickWorkRules.endReason(calm.copy(ownerDistanceSqr = 256.01)))
    }

    @Test
    fun `CD-QWK-16 CD-QWK-18 CD-QWK-20 CD-QWK-21 each end has its reason`() {
        assertEquals(QuickEnd.RECALLED, QuickWorkRules.endReason(calm.copy(entityGone = true)))
        assertEquals(QuickEnd.BATTLE, QuickWorkRules.endReason(calm.copy(battling = true)))
        assertEquals(QuickEnd.OWNER_LEFT, QuickWorkRules.endReason(calm.copy(ownerPresent = false)))
        assertEquals(QuickEnd.TARGET, QuickWorkRules.endReason(calm.copy(holdsClaim = false)))
        assertNull(QuickWorkRules.endReason(calm))
    }

    @Test
    fun `CD-QWK-16 a gone entity wins over every other end`() = assertEquals(
        QuickEnd.RECALLED,
        QuickWorkRules.endReason(calm.copy(entityGone = true, battling = true, ownerPresent = false, holdsClaim = false)),
    )

    @Test
    fun `CD-QWK-19 the Standby time ends the order at the limit and not before`() {
        val standby = calm.copy(status = WorkerStatus.STANDBY)
        assertNull(QuickWorkRules.endReason(standby.copy(ticksInStatus = 199)))
        assertEquals(QuickEnd.NO_DEMAND, QuickWorkRules.endReason(standby.copy(ticksInStatus = 200)))
        assertNull(QuickWorkRules.endReason(calm.copy(ticksInStatus = 5_000)))
    }

    @Test
    fun `CD-UI-12 each end has its own message key, and a timeout reuses the leash's`() {
        assertEquals(QuickWorkRules.CANNOT_REACH, QuickEnd.UNREACHABLE.messageKey)
        assertEquals(QuickEnd.entries.size, QuickEnd.entries.map { it.messageKey }.toSet().size)
    }
}
```

- [ ] **Step 2: Run them and see them fail** to compile.

- [ ] **Step 3: Write the code.**

`ConfigRanges` gains `val QUICK_WORK_RANGE = 2..24`. `EngineSettings` gains the three values of the interface. `ServerConfig`
gains three fields, a section after `leash`, and three lines in `snapshot()`:

```kotlin
b.comment("Quick Work: a party Pokémon that its owner sends out at a block works that block.").push("quickWork")
quickWorkEnabled = b.comment("Start Quick Work orders. With false, a send-out never starts one.")
    .define("enabled", d.quickWorkEnabled)
quickWorkTargetRange = b.int(
    "targetRange",
    d.quickWorkTargetRange,
    ConfigRanges.QUICK_WORK_RANGE,
    "Blocks that the view of a player who sends out a Pokémon reaches to find its target. Never more than leashRange.",
)
quickWorkLeashRange = b.int(
    "leashRange",
    d.quickWorkLeashRange,
    ConfigRanges.QUICK_WORK_RANGE,
    "Blocks from the owner's eyes to the target at which a Quick Work order ends.",
)
b.pop()
```

```kotlin
package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.handler.BindingShape
import codes.honor.cobblemondynamics.role.RoleDefinition
import codes.honor.cobblemondynamics.role.RoleSelection

/** The pure rules of a Quick Work order (ADR-0013). The engine reads every input from server state. */
object QuickWorkRules {
    const val STARTED = "cobblemon_dynamics.quick_work.started"
    const val NEEDS_LEASH = "cobblemon_dynamics.quick_work.needs_leash"
    const val NO_PERMISSION = "cobblemon_dynamics.quick_work.no_permission"
    const val TIRED = "cobblemon_dynamics.quick_work.tired"
    const val CLAIMED = "cobblemon_dynamics.quick_work.claimed"
    const val NO_DEMAND = "cobblemon_dynamics.quick_work.no_demand"

    /** R57's text, which the leash sends already. Every Quick Work key takes the Pokémon's name, then the target's name. */
    const val CANNOT_REACH = "cobblemon_dynamics.leash.cannot_reach"

    /** An aim waits this long for the end of its send-out, which Cobblemon posts 30 ticks after the key press. */
    const val AIM_TICKS = 100L

    /** Cobblemon's `BEAM_MODE` while a recall plays (`PokemonEntity.recallWithAnimation`). */
    const val RECALL_BEAM = 3

    sealed interface Pick {
        data class Order(val role: RoleDefinition) : Pick

        /** Only roles that need the Work Leash fit the block (CD-QWK-04). */
        data object NeedsLeash : Pick

        /** No role fits, or the Pokémon is a healer. The send-out stays a normal one, with no message. */
        data object None : Pick
    }

    /** What one tick of an order sees, before its worker's own tick. */
    data class Watch(
        /** Removed, dead, in Cobblemon's recall beam, or no longer the Pokémon's sent-out entity. */
        val entityGone: Boolean,
        val battling: Boolean,
        /** Online and in the order's level. */
        val ownerPresent: Boolean,
        /** From the owner's eye position to the box of the aimed block. */
        val ownerDistanceSqr: Double,
        val leashRange: Int,
        val holdsClaim: Boolean,
        val status: WorkerStatus,
        val ticksInStatus: Long,
        val standbyLimit: Int,
    )

    /** CD-QWK-01: an aim this old can no longer start an order. Game time is the overworld's in every level. */
    fun isExpired(aimTick: Long, now: Long): Boolean = now - aimTick > AIM_TICKS

    /**
     * CD-QWK-25: drops every aim of [aims] that can no longer start an order, such as the aim of a send-out that a later subscriber
     * cancelled, so that no aim waits for its owner's logout.
     */
    fun dropExpired(aims: MutableMap<UUID, QuickAim>, now: Long) {
        aims.values.removeIf { isExpired(it.tick, now) }
    }

    /** CD-QWK-13: past the leash, from the owner's eye position to the box of the aimed block. The start and the end of an order ask it. */
    fun beyondLeash(ownerDistanceSqr: Double, leashRange: Int): Boolean = ownerDistanceSqr > leashRange.toDouble() * leashRange

    /** CD-CFG-16, CD-CFG-18, CD-QWK-21: how far the trace of a send-out reaches, or null for no trace. */
    fun traceRange(enabled: Boolean, ownerBattling: Boolean, targetRange: Int, leashRange: Int): Int? =
        if (enabled && !ownerBattling) minOf(targetRange, leashRange) else null

    /** CD-QWK-03: a role that an auto claim takes, from a pasture scan, on one target. */
    fun mayOrder(role: RoleDefinition, scanned: Boolean, shape: BindingShape): Boolean =
        role.autoAssign && scanned && shape == BindingShape.SINGLE

    /**
     * CD-QWK-03 to CD-QWK-05. [fitting] are the roles that the Pokémon's [types], primary first, make it eligible for and whose handler
     * matches the aimed block. [orderable] is [mayOrder] of each. A Fairy-type is a healer, which no auto claim gives a machine
     * (CD-ASG-04).
     */
    fun pick(fitting: List<RoleDefinition>, types: List<String>, hasFairyType: Boolean, orderable: (RoleDefinition) -> Boolean): Pick {
        if (hasFairyType || fitting.isEmpty()) return Pick.None
        val role = RoleSelection.best(fitting.filter(orderable), types) ?: return Pick.NeedsLeash
        return Pick.Order(role)
    }

    /** CD-QWK-06 to CD-QWK-09: the first refusal of an order, in this order, or null to try the claim. [hasDemand] runs last. */
    fun refusal(mayUse: Boolean, tired: Boolean, targetFree: Boolean, hasDemand: () -> Boolean): String? = when {
        !mayUse -> NO_PERMISSION
        tired -> TIRED
        !targetFree -> CLAIMED
        !hasDemand() -> NO_DEMAND
        else -> null
    }

    /** CD-QWK-13, CD-QWK-16, CD-QWK-18 to CD-QWK-21: the first reason that ends an order now, or null. */
    fun endReason(w: Watch): QuickEnd? = when {
        w.entityGone -> QuickEnd.RECALLED
        w.battling -> QuickEnd.BATTLE
        !w.ownerPresent -> QuickEnd.OWNER_LEFT
        beyondLeash(w.ownerDistanceSqr, w.leashRange) -> QuickEnd.TOO_FAR
        !w.holdsClaim -> QuickEnd.TARGET
        w.status == WorkerStatus.STANDBY && w.ticksInStatus >= w.standbyLimit -> QuickEnd.NO_DEMAND
        else -> null
    }
}

/** Why an order ended, with the lang key of the owner's message. */
enum class QuickEnd(val messageKey: String) {
    TOO_FAR("cobblemon_dynamics.quick_work.end.too_far"),
    RECALLED("cobblemon_dynamics.quick_work.end.recalled"),
    TIRED("cobblemon_dynamics.quick_work.end.tired"),
    TARGET("cobblemon_dynamics.quick_work.end.target"),
    NO_DEMAND("cobblemon_dynamics.quick_work.end.no_demand"),
    OWNER_LEFT("cobblemon_dynamics.quick_work.end.owner_left"),
    BATTLE("cobblemon_dynamics.quick_work.end.battle"),
    UNREACHABLE(QuickWorkRules.CANNOT_REACH),
}
```

  `en_us.json` gains these keys. Each takes the Pokémon's name as `%1$s` and the target's name as `%2$s`, and uses the ones
  that it needs:

```json
  "cobblemon_dynamics.quick_work.started": "%1$s goes to work on %2$s",
  "cobblemon_dynamics.quick_work.needs_leash": "%2$s needs the Work Leash",
  "cobblemon_dynamics.quick_work.no_permission": "You may not use %2$s",
  "cobblemon_dynamics.quick_work.tired": "%1$s is too Tired to work",
  "cobblemon_dynamics.quick_work.claimed": "%2$s is already being worked",
  "cobblemon_dynamics.quick_work.no_demand": "%2$s needs no work now",
  "cobblemon_dynamics.quick_work.end.too_far": "%1$s stopped working: you are too far from %2$s",
  "cobblemon_dynamics.quick_work.end.recalled": "%1$s stopped working: it was recalled",
  "cobblemon_dynamics.quick_work.end.tired": "%1$s is Tired and stopped working",
  "cobblemon_dynamics.quick_work.end.target": "%1$s stopped working: %2$s can no longer be worked",
  "cobblemon_dynamics.quick_work.end.no_demand": "%1$s stopped working: %2$s needs no more work",
  "cobblemon_dynamics.quick_work.end.owner_left": "%1$s stopped working: you left its dimension",
  "cobblemon_dynamics.quick_work.end.battle": "%1$s stopped working for a battle",
```

  `SERVER_CONFIG_KEYS` in `scripts/testpack/testpack.py` gains the three keys at the same place as `CFG_01_KEYS`, so the test
  that compares the two lists passes.

- [ ] **Step 4: Run** `./gradlew test --tests "codes.honor.cobblemondynamics.engine.QuickWorkRulesTest"` and
  `./gradlew test --tests "codes.honor.cobblemondynamics.config.ConfigTest"`, then `./gradlew build`. `LangTest` checks the new
  keys, and `PlayerGuideTest` checks that the guide names the three config keys (the guide has them since 2026-11-11).

- [ ] **Step 5: Commit:** `git commit -m "Add the Quick Work settings and the pure rules of an order [rag:1]"`.

---

### Task 2: The engine runs one order outside a pasture

**Rows:** CD-QWK-02 (the order's claim and work), CD-QWK-10 (a claim with no reachable spot), CD-QWK-14 (an end leaves no
state), CD-QWK-17 (Tired ends the order), CD-QWK-22 (a travel timeout ends it), CD-QWK-23 (the order's tick), CD-ASG-01
(claims for a Pokémon on an order), CD-CLM-01 and CD-CLM-06 (an order's claim is a normal work claim with no pasture),
CD-SEC-10 (no binder and no placer behind a Quick Work claim).

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/engine/WorkSite.kt`, `engine/WorkerSlot.kt`, `engine/QuickRuntime.kt`
- Modify: `engine/PastureRuntime.kt`, `engine/Worker.kt`, `engine/WorkEngine.kt`
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/claim/Claim.kt`, `claim/ClaimRegistry.kt`
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/leash/LeashInteractions.kt` (`mayFlip`)
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/claim/ClaimRegistryTest.kt`

**Interfaces:**
- Consumes: `QuickWorkRules` and `QuickEnd` (Task 1), `WorkSpots.findReachable` and `Reach.of` through the engine's `claim`.
- Produces:

```kotlin
/** One worker with the live objects that its tick needs. It was `PastureRuntime.Slot`, and its fields stay as they were. */
class WorkerSlot(val worker: Worker, var entity: PokemonEntity, var pokemon: Pokemon)

/** Where workers work: a pasture in Work Mode or one Quick Work order (ADR-0013). The engine's per-worker steps take either. */
sealed interface WorkSite {
    val level: ServerLevel

    /** The pasture that holds the site's claims, or null for a Quick Work order, which no pasture holds. */
    val pastureKey: PastureKey?

    /** In the order the workers joined. */
    val slots: MutableMap<UUID, WorkerSlot>

    /** The box in which a worker of the site finds its work spot. */
    fun area(settings: EngineSettings): WorkArea
}

/** The block that an owner aimed at when it sent out a party Pokémon, kept until Cobblemon ends the send-out (CD-QWK-01). */
data class QuickAim(val owner: UUID, val dimension: ResourceLocation, val pos: BlockPos, val tick: Long)

class QuickRuntime(level, owner: UUID, aimed: BlockPos, targetName: Component, slot: WorkerSlot, area: WorkArea) : WorkSite

class Worker(val pokemonId: UUID, var pasture: PastureKey?)                       // null for a Quick Worker
data class Claim(val worker: UUID, val key: TargetKey, val kind: ClaimKind, val pasture: PastureKey?, val sinceTick: Long)
fun ClaimRegistry.tryClaim(worker: UUID, key: TargetKey, kind: ClaimKind, pasture: PastureKey?, now: Long): ClaimResult

class WorkEngine {
    fun isQuickWorker(pokemonId: UUID): Boolean
    fun aimOf(pokemonId: UUID): QuickAim?
    /** CD-QWK-01: a send-out replaces the Pokémon's aim, and one with no block in view ([aim] null) leaves none. */
    fun keepAim(pokemonId: UUID, aim: QuickAim?)
    /** CD-QWK-25: a player who logs out leaves no aim. Its orders end on the next tick (CD-QWK-20). */
    fun forgetAims(owner: UUID)
    /** CD-QWK-02 to CD-QWK-11: Cobblemon ended a send-out. Its aim, if it has one, becomes an order, a refusal or nothing. */
    fun onSentOut(level: ServerLevel, pokemon: Pokemon, entity: PokemonEntity)
}
```

- [ ] **Step 1: Failing unit test** in `ClaimRegistryTest`:

```kotlin
@Test
fun `CD-CLM-01 a Quick Work claim names no pasture and stays when a pasture releases its claims`() {
    assertInstanceOf(ClaimResult.Granted::class.java, registry.tryClaim(a, furnace, ClaimKind.WORK, null, 0))
    registry.tryClaim(b, TargetKey.block(overworld, BlockPos(3, 64, 0)), ClaimKind.WORK, pastureA, 0)
    registry.releaseAll { it.pasture == pastureA }
    assertEquals(a, registry.holderOf(furnace)?.worker)
    assertNull(registry.holderOf(furnace)?.pasture)
}

@Test
fun `CD-CLM-06 CD-QWK-07 a leash binding refuses a Quick Work claim`() {
    reservations[furnace] = b
    assertEquals(ClaimResult.Reserved, registry.tryClaim(a, furnace, ClaimKind.WORK, null, 0))
}
```

- [ ] **Step 2: Run it and see it fail** to compile: `tryClaim` takes no null pasture yet.

- [ ] **Step 3: Write the code.**
  - Move `PastureRuntime.Slot` to `engine/WorkerSlot.kt` as `WorkerSlot`, with every field and KDoc. Replace
    `PastureRuntime.Slot` with `WorkerSlot` in `WorkEngine`.
  - `Worker.pasture` and `Claim.pasture` become `PastureKey?`, and `ClaimRegistry.tryClaim` takes a `PastureKey?`. The KDoc of
    each says that null means a Quick Work order. `releaseAll { it.pasture == key }` needs no change.
  - `LeashInteractions.mayFlip` reads the binder and the placer only for a claim with a pasture. With no binder and no
    placer, only an operator flips a target that a Quick Worker drives (CD-SEC-10). The binder needs the check too: a party
    Pokémon can still carry a binding that `LeashResets` has not cleared yet, for example while its pasture is unloaded.

```kotlin
val pasture = claim.pasture
val binder = pasture?.let { engine.pokemonOf(claim.worker)?.let(BindingStore::read)?.binder }
val placer = pasture?.let { PastureAdapter.pastureAt(level, BlockPos.of(it.pos)) }?.let(PastureAdapter::placer)
```

  - `PastureRuntime` implements `WorkSite`: `override val level`, `override val pastureKey get() = key`,
    `override val slots = LinkedHashMap<UUID, WorkerSlot>()`, and
    `override fun area(settings) = WorkArea.of(pos, settings, PastureAdapter.maxWanderDistance())`. In `WorkEngine`, replace
    `area(t, runtime)` with `runtime.area(t.settings)` (in `scan` and `nearestFreeBed`) and delete `area`.
  - `QuickRuntime`, in `engine/QuickRuntime.kt` with `QuickAim`:

```kotlin
/**
 * One Quick Work order (ADR-0013): a party Pokémon that its owner sent out at a target. It has one worker, a work area around the
 * target and no scan, and the engine forgets it when the order ends.
 */
class QuickRuntime(
    override val level: ServerLevel,
    val owner: UUID,
    /** The block that the owner aimed at. The leash counts from its box (CD-QWK-13). */
    val aimed: BlockPos,
    /** The target's name at the start, for a message after the target breaks. */
    val targetName: Component,
    val slot: WorkerSlot,
    private val workArea: WorkArea,
) : WorkSite {
    val dimension: ResourceLocation = level.dimension().location()

    override val pastureKey: PastureKey? get() = null

    override val slots: MutableMap<UUID, WorkerSlot> = linkedMapOf(slot.worker.pokemonId to slot)

    override fun area(settings: EngineSettings): WorkArea = workArea

    companion object {
        /** The cells within [range] of [center]: the cells that `WorkSpots.find` reads for a target with that center. */
        fun areaAround(center: Vec3, range: Double): WorkArea {
            val r = ceil(range).toInt()
            return WorkArea(BlockPos.containing(center), r, r)
        }
    }
}
```

  - `WorkEngine`, new state and accessors:

```kotlin
/** Quick Work orders by Pokémon UUID (ADR-0013). Empty most of the time, and then no Quick Work code runs per tick. */
private val orders = LinkedHashMap<UUID, QuickRuntime>()

/** The aim of each send-out by Pokémon UUID, from the key press until Cobblemon ends the send-out (CD-QWK-01). */
private val aims = HashMap<UUID, QuickAim>()

fun isQuickWorker(pokemonId: UUID): Boolean = pokemonId in orders

fun aimOf(pokemonId: UUID): QuickAim? = aims[pokemonId]

fun keepAim(pokemonId: UUID, aim: QuickAim?) {
    if (aim == null) {
        aims.remove(pokemonId)
        return
    }
    QuickWorkRules.dropExpired(aims, aim.tick)
    aims[pokemonId] = aim
}

fun forgetAims(owner: UUID) {
    aims.values.removeIf { it.owner == owner }
}

private fun siteOf(slot: WorkerSlot): WorkSite? = slot.worker.pasture?.let { pastures[it] } ?: orders[slot.worker.pokemonId]

private fun dimensionOf(slot: WorkerSlot): ResourceLocation? =
    slot.worker.pasture?.dimension ?: orders[slot.worker.pokemonId]?.dimension
```

  - `WorkEngine`, the steps that change type. `claim`, `tickWorker`, `tickStamina` and `drop` take a `site: WorkSite` in place
    of `runtime: PastureRuntime`. `claim` reads `site.area(t.settings)` and passes `site.pastureKey` to `tryClaim`. `drop`
    removes the slot from `site.slots`. `tickWorker` calls `tickHeal` only for a `PastureRuntime`, and returns the event that
    the worker's machine gave: assign `t.machine.onTick(...)` to a `var event: MachineEvent?` declared before the `guarded`
    block, run the same `when (val e = event)`, and `return event` at the end. `tickStamina` ends an order where a pasture
    worker goes to rest:

```kotlin
// A worker that joins Tired, after a restart for example, takes a bed on its first tick too (CD-BED-14).
if (worker.tired && !worker.status.isResting) {
    when (site) {
        is PastureRuntime -> goToRest(t, site, slot)

        // CD-QWK-17: no pasture holds a Quick Worker, so it takes no bed. The end flushes its Work Stamina.
        is QuickRuntime -> return endOrder(t, site, QuickEnd.TIRED)
    }
} else if (!worker.tired && worker.status.isResting && site is PastureRuntime) {
    wake(t, site, slot)
}
```

  - `WorkEngine`, the uses of a worker's pasture. `release(pokemonId)` finds the site with `siteOf(slot)`, and
    `resetRuntimeForTest` unclaims through `siteOf(slot)` and also clears `orders` and `aims`. `takenSpots` filters with
    `dimensionOf(it) == t.dimension`, so pasture workers never take a Quick Worker's spot. `context` passes
    `pasture = slot.worker.pasture?.let { BlockPos.of(it.pos) }`. Only the Magnet reads it, and the Magnet is leash only.
  - `WorkEngine.tickLevel`, with the orders of the level after the pasture workers:

```kotlin
fun tickLevel(level: ServerLevel) {
    val dimension = level.dimension().location()
    val indexed = level.getExistingDataOrNull(DynamicsAttachments.WORK_PASTURES)?.all().orEmpty()
    // CD-QWK-24: with no order, this check is all the Quick Work that a level tick does.
    val quick = if (orders.isEmpty()) emptyList() else orders.values.filter { it.level === level }
    if (indexed.isEmpty() && quick.isEmpty() && pastures.keys.none { it.dimension == dimension }) return
    val t = tick(level)
    // ... the index, the polls, validateClaims, the scans and the pasture workers, as before ...
    for (order in quick) tickOrder(t, order)
    for (runtime in runtimesOf(dimension)) for (slot in runtime.slots.values.toList()) direct(t, slot)
    for (order in quick) if (orders[order.slot.worker.pokemonId] === order) direct(t, order.slot)
}
```

  - `WorkEngine`, the start of an order:

```kotlin
fun onSentOut(level: ServerLevel, pokemon: Pokemon, entity: PokemonEntity) {
    val aim = aims.remove(pokemon.uuid) ?: return
    val t = tick(level)
    if (!t.settings.quickWorkEnabled || QuickWorkRules.isExpired(aim.tick, t.now) || aim.dimension != t.dimension) return
    if (entity.isRemoved || entity.isBattling || pokemon.uuid in slots || !BrainBridge.canHoldOrder(entity)) return
    // CD-QWK-25: only the party Pokémon of the player who aimed, as at the key press.
    val store = pokemon.storeCoordinates.get()?.store as? PlayerPartyStore
    if (pokemon.isFainted() || pokemon.isBattleClone() || store?.playerUUID != aim.owner) return
    val owner = server.playerList.getPlayer(aim.owner)?.takeIf { it.level() === level } ?: return
    // CD-QWK-21: an owner who entered a battle while the Pokémon came out gets no order.
    if (BattleRegistry.getBattleByParticipatingPlayer(owner) != null || !level.isLoaded(aim.pos)) return
    val types = typesOf(pokemon)
    val state = level.getBlockState(aim.pos)
    val blockEntity = level.getBlockEntity(aim.pos)
    val fitting = t.roles.forTypes(types).filter { role ->
        guarded(role.handler, false) { role.handler.matches(level, aim.pos, state, blockEntity) }
    }
    val byId = fitting.associateBy { it.definition.id }
    val pick = QuickWorkRules.pick(fitting.map { it.definition }, types.toList(), hasFairyType(pokemon)) { def ->
        val handler = byId.getValue(def.id).handler
        QuickWorkRules.mayOrder(def, handler.scanKind != ScanKind.NONE, handler.bindingShape)
    }
    val name = state.block.name
    when (pick) {
        QuickWorkRules.Pick.None -> {}
        QuickWorkRules.Pick.NeedsLeash -> tell(aim.owner, QuickWorkRules.NEEDS_LEASH, pokemon, name)
        is QuickWorkRules.Pick.Order -> start(t, owner, pokemon, entity, aim, byId.getValue(pick.role.id), name)
    }
}

/** CD-QWK-06 to CD-QWK-11: the refusals, then a normal work claim through the engine's own spot search and path check. */
private fun start(
    t: Tick,
    owner: ServerPlayer,
    pokemon: Pokemon,
    entity: PokemonEntity,
    aim: QuickAim,
    role: ActiveRole,
    name: Component,
) {
    // CD-QWK-13: an owner who walked past the leash while the Pokémon came out gets the leash's message, and no order starts.
    if (QuickWorkRules.beyondLeash(AABB(aim.pos).distanceToSqr(owner.eyePosition), t.settings.quickWorkLeashRange)) {
        return tell(aim.owner, QuickEnd.TOO_FAR.messageKey, pokemon, name)
    }
    val canonical = guarded(role.handler, aim.pos) { role.handler.canonical(t.level, aim.pos) }
    if (!guarded(role.handler, false) { role.handler.stillValid(t.level, canonical) }) return
    // CD-STA-11: a party Pokémon recovers for its time away when it loads.
    val worker = Worker(pokemon.uuid, pasture = null)
    worker.stamina = StaminaStore.load(pokemon, t.now, t.settings)
    worker.staminaUntil = t.now
    val slot = WorkerSlot(worker, entity, pokemon)
    val center = guarded(role.handler, Vec3.atCenterOf(canonical)) { role.handler.targetCenter(canonical) }
    val order = QuickRuntime(t.level, aim.owner, aim.pos, name, slot, QuickRuntime.areaAround(center, role.definition.range))
    val refusal = QuickWorkRules.refusal(
        mayUse = !owner.isSpectator && owner.mayInteract(t.level, canonical),
        tired = worker.tired,
        targetFree = claims.isFree(TargetKey.block(t.dimension, canonical), pokemon.uuid),
    ) { guarded(role.handler, false) { role.handler.hasDemand(context(t, slot, canonical, role)) } }
    if (refusal != null) return tell(aim.owner, refusal, pokemon, name)
    when (claim(t, order, slot, canonical, role, takenSpots(t), moving = false)) {
        Attempt.GRANTED -> {
            orders[pokemon.uuid] = order
            slots[pokemon.uuid] = slot
            tell(aim.owner, QuickWorkRules.STARTED, pokemon, name)
        }

        Attempt.REFUSED -> tell(aim.owner, QuickWorkRules.CLAIMED, pokemon, name)

        // No work spot that a path reaches (CD-QWK-10). An order never searches again, so the owner learns why now.
        else -> tell(aim.owner, QuickWorkRules.CANNOT_REACH, pokemon, name)
    }
}

/** Every Quick Work message goes to the order's owner only, with the Pokémon's name and the target's name (CD-QWK-25). */
private fun tell(owner: UUID, key: String, pokemon: Pokemon, target: Component) =
    Feedback.actionBar(owner, server, Component.translatable(key, pokemon.getDisplayName(), target))
```

  - `start` gives every claim result other than `GRANTED` and `REFUSED` the "cannot reach" message, because an order never
    tries again. So a result that `claim` gains later, such as the one for a navigation that cannot plan, needs no change
    in `start`.
  - The review of 2026-11-11 added three checks. An owner who entered a battle while the Pokémon came out gets no order
    (CD-QWK-21). An owner who walked past the leash in that time gets the leash's message and no order (CD-QWK-13). A new aim
    drops every aim that can no longer start an order, and a send-out that is cancelled when its event reaches Quick Work
    leaves no aim (CD-QWK-25). Cobblemon's `CancelableObservable.emit` stops at the first subscriber after which the event is
    cancelled (`api/reactive/EventObservables.kt`), so a later subscriber at `LOWEST` can still cancel a send-out after its
    aim exists.
  - `WorkEngine`, one tick and the end of an order:

```kotlin
/** One tick of an order: its end checks, then its worker's own steps, as a pasture worker's (CD-QWK-13 to CD-QWK-23). */
private fun tickOrder(t: Tick, order: QuickRuntime) {
    val slot = order.slot
    val worker = slot.worker
    val entity = slot.entity
    val owner = server.playerList.getPlayer(order.owner)
    val watch = QuickWorkRules.Watch(
        entityGone = !entity.isAlive || entity.beamMode == QuickWorkRules.RECALL_BEAM || slot.pokemon.entity !== entity,
        battling = entity.isBattling,
        ownerPresent = owner != null && owner.level() === t.level,
        ownerDistanceSqr = owner?.let { AABB(order.aimed).distanceToSqr(it.eyePosition) } ?: Double.MAX_VALUE,
        leashRange = t.settings.quickWorkLeashRange,
        holdsClaim = claims.claimOf(worker.pokemonId)?.kind == ClaimKind.WORK,
        status = worker.status,
        ticksInStatus = t.now - worker.statusSince,
        standbyLimit = t.settings.standbyMoveTicks,
    )
    QuickWorkRules.endReason(watch)?.let { return endOrder(t, order, it) }
    // CD-QWK-22: the machine released the claim of a worker whose travel timed out, so the order ends, and it never tries again.
    if (tickWorker(t, order, slot) is MachineEvent.ReleaseClaim) return endOrder(t, order, QuickEnd.UNREACHABLE)
    if (orders[worker.pokemonId] === order) tickStamina(t, order, slot)
}

/**
 * The order ends for good (CD-QWK-14): the release path, with `onStop` for a Working worker, a flush of Work Stamina into the party
 * Pokémon's data, and no slot, order or view left. The Brain bridge cleared the work order, so Cobblemon's follow-owner task takes
 * over (CD-QWK-15).
 */
private fun endOrder(t: Tick, order: QuickRuntime, reason: QuickEnd) {
    drop(t, order, order.slot)
    orders.remove(order.slot.worker.pokemonId)
    BrainBridge.endHold(order.slot.entity)
    tell(order.owner, reason.messageKey, order.slot.pokemon, order.targetName)
}
```

  `BrainBridge.endHold` (Task 3) erases Cobblemon's `PATH_COOLDOWN`, which the hold of the work order renews on every tick.
  Without it, `move_to_owner` waits up to 40 ticks after the end (see the checked facts at the top of this file).

- [ ] **Step 4: Run the unit tests and see them pass.** Then run `./gradlew build`, `./gradlew runGameTestServer` and
  `./gradlew runGameTestServerCore`. Every Phase 1 and Phase 2 GameTest still passes, which shows that pastures work as
  before. Nothing starts an order yet, because nothing calls `onSentOut`.

- [ ] **Step 5: Commit:** `git commit -m "Run one-worker orders outside a pasture in the work engine [rag:1]"`.

---

### Task 3: The send-out, the trace and the GameTests

**Rows:** CD-QWK-01 (the trace at the key press), CD-QWK-02 (the order claims and works), CD-QWK-11 (the start message),
CD-QWK-12 (no follow pull while it works), CD-QWK-13 (the leash), CD-QWK-14 (no restart and no state after the end),
CD-QWK-15 (the follow after the end), CD-QWK-16 (a recall), CD-QWK-21 (no aim in a battle), CD-QWK-25 (keys, messages and
the logout), CD-CFG-16 (no trace while Quick Work is off). Deferred to the end-of-phase pass: CD-QWK-04 to CD-QWK-10 and
CD-QWK-17 to CD-QWK-22.

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/engine/QuickWork.kt`
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/CobblemonDynamics.kt` (`QuickWork.register()` after
  `LeashResets.register()`)
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/ai/BrainBridge.kt` (`endHold`) and `engine/WorkEngine.kt` (`endOrder`
  calls it, CD-QWK-15)
- Modify: `src/gametest/kotlin-shared/codes/honor/cobblemondynamics/test/TestKit.kt` (`listedOwner`, `lookAt`, `partyPokemon`,
  `pressSendOut`)
- Create: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/QuickWorkTests.kt`
- Modify: `docs/plans/2026-11-10-phase-2-m8-art-gate.md` (the deferred list of Task 7)

**Interfaces:**
- Consumes: `WorkEngine.keepAim`, `onSentOut` and `forgetAims` (Task 2), `QuickWorkRules.traceRange` (Task 1).
- Produces:

```kotlin
/** The Cobblemon and NeoForge events of Quick Work (ADR-0013). Server thread only. */
object QuickWork {
    fun register()

    /** The first block that [player]'s view hits within [range] blocks, from the server's copy of its eye position and rotation. */
    fun aimedBlock(player: ServerPlayer, range: Int): BlockHitResult?
}

object TestKit {
    fun listedOwner(helper: GameTestHelper, rel: BlockPos, name: String = "owner"): ServerPlayer
    fun lookAt(helper: GameTestHelper, player: ServerPlayer, rel: BlockPos)
    fun partyPokemon(player: ServerPlayer, spec: String): Pokemon
    fun pressSendOut(player: ServerPlayer, slot: Int = 0)
}
```

Rule 6 of the index keeps a GameTest where Cobblemon's behaviour is uncertain: its send-out events and their timing, the
recall, the follow-owner task and navigation. This task writes three such tests. The refusals and the other ends run code
whose pure parts Task 1 tests, so their GameTests wait for the end-of-phase pass. No test has two players. A fourth test,
for the inferred battle fact of CD-QWK-21, ran on 2026-11-11 and showed that the fact is wrong (see the checked facts at the
top of this file). It waits for the end-of-phase pass, which needs a battle that stays in the GameTest server.

- [ ] **Step 1: The test kit.**

```kotlin
/**
 * A fake owner at [rel] that `PlayerList.getPlayer` finds, as Cobblemon's owner lookup and Quick Work need. It joins only the player
 * list's UUID map, so it gets no broadcast, no spawns and no ticks, and it leaves the map when the test ends.
 */
fun listedOwner(helper: GameTestHelper, rel: BlockPos, name: String = "owner"): ServerPlayer {
    val player = owner(helper, name)
    val pos = helper.absoluteVec(rel.bottomCenter)
    place(player, pos.x, pos.y, pos.z)
    @Suppress("UNCHECKED_CAST")
    val byUuid = PLAYERS_BY_UUID.get(helper.level.server.playerList) as MutableMap<UUID, ServerPlayer>
    byUuid[player.uuid] = player
    afterTest(helper) { byUuid.remove(player.uuid) }
    return player
}

private val PLAYERS_BY_UUID: Field = PlayerList::class.java.getDeclaredField("playersByUUID").apply { isAccessible = true }

/** Turns [player]'s eyes to the center of the block at [rel], as a client's rotation packets do on a real server. */
fun lookAt(helper: GameTestHelper, player: ServerPlayer, rel: BlockPos) =
    player.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(Vec3.atCenterOf(rel)))

/** A new Pokémon in the first free slot of [player]'s party. */
fun partyPokemon(player: ServerPlayer, spec: String): Pokemon = pokemon(spec).also { Cobblemon.storage.getParty(player).add(it) }

/** The R key: Cobblemon's own server handler of the send-out packet for party slot [slot]. It recalls a Pokémon that is out. */
fun pressSendOut(player: ServerPlayer, slot: Int = 0) = SendOutPokemonHandler.handle(SendOutPokemonPacket(slot), player.server, player)
```

- [ ] **Step 2: Failing GameTests** in `QuickWorkTests`. The tests list fake players in the player list, so they run in their
  own batch:

```kotlin
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class QuickWorkTests {
    private class Scene(
        val owner: ServerPlayer,
        val charmander: Pokemon,
        val a: AbstractFurnaceBlockEntity,
        val b: AbstractFurnaceBlockEntity,
    )

    /** Furnaces A and B with 8 raw iron each, and a listed owner 10 blocks east of A who looks at A, with a Charmander in slot 0. */
    private fun scene(helper: GameTestHelper): Scene {
        val a = TestKit.furnace(helper, A, ItemStack(Items.RAW_IRON, 8))
        val b = TestKit.furnace(helper, B, ItemStack(Items.RAW_IRON, 8))
        val owner = TestKit.listedOwner(helper, OWNER)
        val charmander = TestKit.partyPokemon(owner, "charmander level=10")
        TestKit.lookAt(helper, owner, A)
        return Scene(owner, charmander, a, b)
    }

    private fun claimedBy(helper: GameTestHelper, pokemon: Pokemon): BlockPos? =
        (TestKit.engine(helper).claims.claimOf(pokemon.uuid)?.key as? TargetKey.Block)?.let { BlockPos.of(it.pos) }

    @GameTest(template = "floor_23x10x23", timeoutTicks = 1_000, batch = BATCH)
    fun cdQwk01Qwk02Qwk11Qwk12SendOutAtAFurnaceWorksIt(helper: GameTestHelper) {
        val s = scene(helper)
        TestKit.pressSendOut(s.owner)
        // CD-QWK-01: the aim at the key press counts, so a turn to furnace B before the send-out ends changes nothing.
        helper.runAfterDelay(1) { TestKit.lookAt(helper, s.owner, B) }
        var workingSince = -1L
        helper.onEachTick {
            val engine = TestKit.engine(helper)
            if (helper.tick == 100L && claimedBy(helper, s.charmander) == null) helper.fail("CD-QWK-02: no claim after 100 ticks")
            if (helper.tick == 400L && workingSince < 0) helper.fail("CD-QWK-02: not Working after 400 ticks")
            val worker = engine.worker(s.charmander.uuid) ?: return@onEachTick
            val entity = engine.entityOf(s.charmander.uuid) ?: return@onEachTick
            if (worker.status != WorkerStatus.WORKING) return@onEachTick
            if (workingSince < 0) workingSince = helper.tick
            // CD-QWK-12: Cobblemon's follow-owner task would walk it to its owner, who stands more than 5 blocks away.
            val spot = worker.spot ?: throw GameTestAssertException("a Working worker with no spot")
            if (!WorkEngine.isArrived(entity, spot)) helper.fail("CD-QWK-12: the Charmander left its work spot at tick ${helper.tick}")
        }
        helper.succeedWhen {
            val engine = TestKit.engine(helper)
            helper.assertTrue(claimedBy(helper, s.charmander) == s.a.blockPos, "CD-QWK-01: the order is not on furnace A")
            helper.assertTrue(engine.claims.holderOf(TestKit.keyOf(helper, s.b.blockPos)) == null, "CD-QWK-01: furnace B is claimed")
            helper.assertTrue(
                TestKit.messages(s.owner).contains("Charmander goes to work on Furnace"),
                "CD-QWK-11: ${TestKit.messages(s.owner)}",
            )
            helper.assertTrue(workingSince >= 0 && helper.tick - workingSince >= 200, "CD-QWK-12: fewer than 200 ticks of Working")
            val lit = helper.level.getBlockState(s.a.blockPos).getValue(AbstractFurnaceBlock.LIT)
            helper.assertTrue(lit, "CD-QWK-02: furnace A is not lit")
            helper.assertTrue((engine.worker(s.charmander.uuid)?.stamina?.fraction ?: 1.0) < 1.0, "CD-QWK-02: no Work Stamina drained")
        }
    }

    @GameTest(template = "floor_23x10x23", timeoutTicks = 1_400, batch = BATCH)
    fun cdQwk13Qwk14Qwk15LeashEndsTheOrderForGood(helper: GameTestHelper) {
        val s = scene(helper)
        TestKit.pressSendOut(s.owner)
        var movedAt = -1L
        var endedAt = -1L
        var distanceAtEnd = 0.0
        helper.onEachTick {
            val engine = TestKit.engine(helper)
            val entity = s.charmander.entity ?: return@onEachTick
            val tick = helper.tick
            when {
                movedAt < 0 -> if (engine.worker(s.charmander.uuid)?.status == WorkerStatus.WORKING) {
                    // CD-QWK-13: 17.5 blocks from the box of furnace A, past the default leash of 16.
                    val far = helper.absoluteVec(FAR.bottomCenter)
                    TestKit.place(s.owner, far.x, far.y, far.z)
                    movedAt = tick
                }

                endedAt < 0 -> if (!engine.isQuickWorker(s.charmander.uuid) && engine.claims.claimOf(s.charmander.uuid) == null) {
                    endedAt = tick
                    distanceAtEnd = entity.distanceTo(s.owner).toDouble()
                } else if (tick - movedAt > 2) {
                    helper.fail("CD-QWK-13: the order outlived the leash by ${tick - movedAt} ticks")
                }

                tick == endedAt + 100 -> {
                    helper.assertTrue(!BrainBridge.holdsOrder(entity), "CD-QWK-15: the Brain still holds a work order")
                    val closer = entity.distanceTo(s.owner) <= distanceAtEnd - 2
                    helper.assertTrue(closer, "CD-QWK-15: the Charmander does not follow its owner")
                    val near = helper.absoluteVec(NEAR.bottomCenter)
                    TestKit.place(s.owner, near.x, near.y, near.z)
                    TestKit.lookAt(helper, s.owner, A)
                }

                tick > endedAt + 100 -> {
                    // CD-QWK-14: the owner is back beside furnace A and looks at it, and nothing starts again.
                    val holder = engine.claims.holderOf(TestKit.keyOf(helper, s.a.blockPos))
                    helper.assertTrue(holder == null, "CD-QWK-14: furnace A is claimed again")
                    helper.assertTrue(engine.worker(s.charmander.uuid) == null, "CD-QWK-14: the engine still holds a slot")
                    helper.assertTrue(engine.aimOf(s.charmander.uuid) == null, "CD-QWK-14: the engine still holds an aim")
                }
            }
        }
        helper.succeedWhen {
            helper.assertTrue(endedAt >= 0 && helper.tick >= endedAt + 300, "the test runs 200 ticks past the owner's return")
            helper.assertTrue(
                TestKit.messages(s.owner).contains("Charmander stopped working: you are too far from Furnace"),
                "CD-QWK-13: ${TestKit.messages(s.owner)}",
            )
        }
    }

    @GameTest(template = "floor_23x10x23", timeoutTicks = 1_000, batch = BATCH)
    fun cdQwk16RecallEndsTheOrder(helper: GameTestHelper) {
        val s = scene(helper)
        TestKit.pressSendOut(s.owner)
        var recalledAt = -1L
        helper.onEachTick {
            val engine = TestKit.engine(helper)
            if (recalledAt < 0) {
                if (engine.worker(s.charmander.uuid)?.status == WorkerStatus.WORKING) {
                    TestKit.pressSendOut(s.owner)
                    recalledAt = helper.tick
                }
            } else if (helper.tick - recalledAt > 2 && engine.claims.claimOf(s.charmander.uuid) != null) {
                helper.fail("CD-QWK-16: the order outlived the recall by ${helper.tick - recalledAt} ticks")
            }
        }
        helper.succeedWhen {
            helper.assertTrue(recalledAt >= 0 && helper.tick - recalledAt >= 40, "no recall yet")
            helper.assertTrue(s.charmander.entity == null, "Cobblemon did not recall the Charmander")
            helper.assertTrue(!TestKit.engine(helper).isQuickWorker(s.charmander.uuid), "CD-QWK-16: the order remains")
            helper.assertTrue(
                TestKit.messages(s.owner).contains("Charmander stopped working: it was recalled"),
                "CD-QWK-16: ${TestKit.messages(s.owner)}",
            )
        }
    }

    private companion object {
        const val BATCH = "quick_work"
        val A = BlockPos(4, 2, 11)
        val B = BlockPos(4, 2, 6)
        val OWNER = BlockPos(14, 2, 11)
        val FAR = BlockPos(22, 2, 11)
        val NEAR = BlockPos(10, 2, 11)
    }
}
```

- [ ] **Step 3: Run them and see them fail:** each test fails on its first check, because no send-out has an aim yet.

- [ ] **Step 4: Write the code** in `engine/QuickWork.kt`, and call `QuickWork.register()` after `LeashResets.register()`:

```kotlin
/** The Cobblemon and NeoForge events of Quick Work (ADR-0013). Server thread only. */
object QuickWork {
    fun register() {
        // Cobblemon stops the emission at the subscriber that cancels a send-out, and LOWEST runs after the other priorities. A
        // send-out that is cancelled when it reaches this subscriber leaves no aim. One that a later LOWEST subscriber cancels leaves
        // an aim that the next aim drops when it is too old to start an order (CD-QWK-25).
        CobblemonEvents.POKEMON_SENT_PRE.subscribe(Priority.LOWEST) { event ->
            EngineTiming.time { WorkEngine.get()?.keepAim(event.pokemon.uuid, if (event.isCanceled) null else aimOf(event)) }
        }
        CobblemonEvents.POKEMON_SENT_POST.subscribe(Priority.NORMAL) { event ->
            EngineTiming.time { WorkEngine.get()?.onSentOut(event.level, event.pokemon, event.pokemonEntity) }
        }
        // CD-QWK-25: Cobblemon posts no POKEMON_SENT_POST for a send-out that a recall cut short, so the recall drops its aim. The
        // entity tick that can call a recall runs on the client too, which must never touch the server's engine.
        CobblemonEvents.POKEMON_RECALL_POST.subscribe(Priority.NORMAL) { event ->
            WorkEngine.get()?.takeIf { it.server.isSameThread }?.keepAim(event.pokemon.uuid, null)
        }
        NeoForge.EVENT_BUS.addListener<PlayerEvent.PlayerLoggedOutEvent> { event -> WorkEngine.get()?.forgetAims(event.entity.uuid) }
    }

    /**
     * CD-QWK-01, CD-QWK-21, CD-CFG-16: the aim of a send-out. Cobblemon posts the event inside its handler of the R key, before the
     * entity exists, so the block in view now is the block that the player meant. The send-out itself ends 30 ticks later.
     */
    private fun aimOf(event: PokemonSentEvent.Pre): QuickAim? {
        val store = event.pokemon.storeCoordinates.get()?.store as? PlayerPartyStore ?: return null
        val owner = event.level.server.playerList.getPlayer(store.playerUUID)?.takeIf { it.level() === event.level } ?: return null
        val s = WorkEngineSettings.current
        val inBattle = BattleRegistry.getBattleByParticipatingPlayer(owner) != null
        val range = QuickWorkRules.traceRange(s.quickWorkEnabled, inBattle, s.quickWorkTargetRange, s.quickWorkLeashRange) ?: return null
        val hit = aimedBlock(owner, range) ?: return null
        return QuickAim(owner.uuid, event.level.dimension().location(), hit.blockPos.immutable(), event.level.gameTime)
    }

    fun aimedBlock(player: ServerPlayer, range: Int): BlockHitResult? {
        val eye = player.eyePosition
        val end = eye.add(player.calculateViewVector(player.xRot, player.yRot).scale(range.toDouble()))
        val hit = player.level().clip(ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
        return hit.takeIf { it.type == HitResult.Type.BLOCK }
    }
}
```

  The trace uses `xRot` and `yRot`, as Cobblemon's own send-out trace does (`PlayerExtensions.kt:342-352`). It does not use
  `Entity.pick`, because a `LivingEntity` reads its head rotation there. `ClipContext.Block.OUTLINE` with `Fluid.NONE` hits
  what the crosshair outlines, so a machine behind water still counts.

  The recall listener closes a gap of the design. When the owner recalls a Pokémon during its send-out animation, Cobblemon
  completes the send-out with no `POKEMON_SENT_POST` (`Pokemon.kt:901-904`), and `Pokemon.recall` posts
  `POKEMON_RECALL_POST` (`Pokemon.kt:977-983`, checked with `javap`: `PokemonRecallEvent.Post.getPokemon()`). Without the
  listener, that aim would stay until the Pokémon's next send-out, its owner's logout or the server stop. Every caller of
  `Pokemon.recall` is server code except the entity's tether check, which runs in the entity tick on both sides, so the
  listener acts only on the server thread.

- [ ] **Step 5: Run** `./gradlew runGameTestServer` twice in a row, and see the three tests pass both times. Check each
  inferred fact at the top of this file against what the tests show, and correct this file where one is wrong.

- [ ] **Step 6: Deferred rows.** By rule 6 of the index, these GameTests wait for the end-of-phase pass: "GameTest deferred
  (high confidence): verified in the end-of-phase pass". Add this group to the deferred list of M8 Task 7, before its
  closing paragraph:

```markdown
- Quick Work Task 3:
  - CD-QWK-04: `QuickWorkTests.cdQwk04ChestAndCauldronNeedTheLeash`.
  - CD-QWK-05: `QuickWorkTests.cdQwk05FairyTypeGetsNoOrder`.
  - CD-QWK-06: `QuickWorkTests.cdQwk06RefusedOwnerAndSpectatorGetNoOrder`.
  - CD-QWK-07: `QuickWorkTests.cdQwk07ClaimedFurnaceGivesNoOrder`.
  - CD-QWK-08: `QuickWorkTests.cdQwk08TiredPokemonGetsNoOrder`.
  - CD-QWK-09: `QuickWorkTests.cdQwk09EmptyFurnaceGivesNoOrder`.
  - CD-QWK-10: `QuickWorkTests.cdQwk10FurnaceWithNoSpotGivesNoOrder`.
  - CD-QWK-17: `QuickWorkTests.cdQwk17TiredWorkerStops`.
  - CD-QWK-18: `QuickWorkTests.cdQwk18BrokenFurnaceEndsTheOrder`.
  - CD-QWK-19: `QuickWorkTests.cdQwk19StandbyTimeEndsTheOrder`.
  - CD-QWK-20: `QuickWorkTests.cdQwk20OwnerWhoLeavesEndsTheOrder`.
  - CD-QWK-21: `QuickWorkTests.cdQwk21BattleEndsTheOrder`.
  - CD-QWK-22: `QuickWorkTests.cdQwk22TrappedWorkerTimesOut`.
```

  Each deferred test uses the scene of this task and its row's acceptance test. A refusing owner for CD-QWK-06 is a
  `FakePlayer` subclass, as `TestKit.MenuCountingPlayer` is, whose `mayInteract` returns false. CD-QWK-08 and CD-QWK-17 save
  the Work Stamina first with `StaminaStore.save`. CD-QWK-20 removes the owner from the player list's UUID map.

  CD-QWK-21 is not a high-confidence deferral. Its test gives the working entity a battle id, which must name a battle that
  `BattleRegistry` keeps for the whole check, because Cobblemon clears any other battle id on the entity's next tick (see the
  checked facts at the top of this file). A wild battle that `BattleBuilder.pve` starts for a listed fake owner ended within 1
  tick on 2026-11-11, so the end-of-phase pass must first find a battle that stays. The engine reads `entity.isBattling`, and
  the unit test of the end rule covers the pure part.

- [ ] **Step 7: Multiplayer check.** Read the Quick Work code against the eight multiplayer rules of the index, and record each
  point in the pull request body:
  - The server owns all state. The client sends nothing new, and the aim comes from the server's copy of the rotation.
  - `orders` and `aims` are keyed by the Pokémon's UUID and hold the owner's UUID. No field holds a `ServerPlayer`, and
    `tickOrder` looks the owner up by UUID on each tick.
  - No new payload. Cobblemon's own send-out packet runs on the main thread, and both events fire there.
  - No client class: `QuickWork` and the engine name no `net.minecraft.client` class.
  - No new static mutable state: the orders and the aims live in the engine instance, which `ServerStoppedEvent` drops. A
    logout drops the player's aims, and its orders end on the next tick. A recall drops the Pokémon's aim, on the server
    thread only.
  - Every message goes through `tell` to the order's owner only. Effects, head bars and the work focus entity reach the
    players near the worker, as for a pasture worker.
  - The permission, the claim and the owner checks compare the owner's UUID and ask `mayInteract` of the owner, never of
    another player.

- [ ] **Step 8: Commit:** `git commit -m "Start Quick Work orders from Cobblemon's send-out of a party Pokémon [rag:1]"`.

---

### Task 4: Cost, docs and the pull request

**Rows:** CD-QWK-23 (the order's tick, by inspection), CD-QWK-24 (the cost with no order), CD-QWK-25 (by inspection),
CD-REL-08 (the Quick Work section of the player guide), CD-REL-09 (no Quick Work row has a Demo test), CD-PERF-01 (the
reference world with no order).

**Files:**
- Modify: `ARCHITECTURE.md` (sections 1, 3, 4, 5, 6, 8, 9 and 13), `README.md` (How it works, Documentation)
- Modify: `src/test/kotlin/codes/honor/cobblemondynamics/build/PlayerGuideTest.kt` (the section "Quick Work")
- Modify: `src/test/kotlin/codes/honor/cobblemondynamics/build/ManualChecklistTest.kt` and `scripts/gate_report.py` (QWK as a
  Phase 2 area, and CD-CFG-16 to CD-CFG-18 as Phase 2 rows)
- Check: `docs/player-guide.md` (its Quick Work section and config keys, written on 2026-11-11, against the code)

- [ ] **Step 1: Measure the cost with no order (CD-QWK-24).** The reference world of CD-PERF-01 has 64 pasture workers and
  no Quick Work order, so it measures what Quick Work adds to every tick. On the commit before the first Quick Work commit
  (`git merge-base HEAD origin/main`), run `./gradlew runGameTestServerPerf` three times, one JVM at a time on the shared
  machine. After each run, read the `CD-PERF-01 mean` and `CD-PERF-02 p95` values from
  `run/gametest-perf/logs/latest.log`. Then do the same three runs on the head of this branch. The row passes when the highest
  mean of the Quick Work runs is at most 0.02 ms per tick above the highest mean of the earlier runs. Put the twelve numbers
  in the pull request body.

- [ ] **Step 2: Inspect the cost (CD-QWK-23, CD-QWK-24).** Read `tickLevel` with an empty `orders` map: the only Quick Work
  code that runs is the `isEmpty()` check. Read `tickOrder`: one player lookup by UUID, one box distance, one claim lookup,
  the pure end rule, and then `tickWorker`, `tickStamina` and `direct`, which a pasture worker runs too. No order starts a
  scan pass or reads a block other than its own target and its spot cells. Record both readings in the pull request body.

- [ ] **Step 3: Docs.**
  - `ARCHITECTURE.md`: section 1, a party Pokémon also works on a Quick Work order (ADR-0013). Section 3, the work engine runs
    Quick Work orders, one sent-out party Pokémon each, with no scan. Section 4, two rows: the Quick Work order (`QuickRuntime`
    in the engine by Pokémon UUID, not saved, synced through the stamina view) and the aim (`QuickAim` by Pokémon UUID, from
    the key press to the end of the send-out, not saved). Section 5, a Quick Worker enters the machine at Traveling, and its
    order ends where a pasture worker would release its claim, rest or move. Section 6, a step after step 2: each order checks
    its end, then ticks its worker as steps 1 to 3 do, and a level with no order skips it. Section 8, the send-out events and
    the follow-owner facts of ADR-0013. Section 9, the three `quickWork` keys. Section 13, `QuickWorkTests` and the listed fake
    owners.
  - `README.md`: the bullet "Only pastured Pokémon work" names Quick Work as the one exception, and the Documentation table
    lists Quick Work in the player guide's row.
  - `PlayerGuideTest`: the first test's list of sections gains "Quick Work" (CD-REL-08).
  - `docs/player-guide.md`: check the Quick Work section and the three config rows against the code and the lang file, and fix
    any difference in the same commit.
  - The manual checklist needs no change, because no QWK row has a Demo test (CD-REL-09).

- [ ] **Step 4: Tools.** `ManualChecklistTest.PHASE_2_AREAS` and `PHASE_2_AREAS` in `scripts/gate_report.py` gain `QWK`.
  `gate_report.py` also leaves CD-CFG-16 to CD-CFG-18 out of its Phase 1 rows, as `scripts/check_plan_coverage.py` does
  with its `PHASE_2_ROWS`. Then a Phase 1 report keeps the rows that the Phase 1 gate held, and M8 Task 8's Phase 2 report
  holds every row.

- [ ] **Step 5: Run** `./gradlew build`, `./gradlew runGameTestServer`, `./gradlew runGameTestServerCore`,
  `py scripts/check_plan_coverage.py`, `py scripts/check_plan_coverage.py --phase 2` and `py scripts/testpack/testpack.py smoke`.
  Both coverage modes print `missing: 0`.

- [ ] **Step 6: Commit, push, open the pull request.** `git commit -m "Document Quick Work and measure its cost [rag:2]"`.
  The pull request body holds the numbers of Step 1, the readings of Step 2 and the multiplayer check of Task 3. It merges
  before M8 Task 6 starts.

---

## Design decisions for the approver

The rulings of R63 settle the feature. This milestone makes these further calls:

1. **The aim at the key press.** The trace runs at `POKEMON_SENT_PRE`, inside Cobblemon's handler of the R key, and the order
   starts at `POKEMON_SENT_POST`, 30 ticks later. A trace at the later event would follow a player who turns to a second
   machine while the Pokémon appears.
2. **A server switch.** `quickWork.enabled` (default `true`) is not in the rulings. A server whose protection mod only cancels
   clicks needs a way to turn Quick Work off, because a ray-traced target makes no click.
3. **The leash measure.** The leash counts from the owner's eye position to the box of the aimed block, and the trace never
   reaches past the leash. So a new order always starts inside its leash, also on a large water wheel whose canonical block
   is farther away than the block in view.
4. **Ranges of 2 to 24 blocks.** A worker stands within 6 blocks of its target, so at 24 blocks it stays inside the teleport
   distance of 32 blocks that Cobblemon gives party Pokémon.
5. **No Quick Work for Fairy-types.** A Fairy-type is a healer, and no auto claim gives it a machine (CD-ASG-04). A Water/Fairy
   Pokémon therefore does not turn a water wheel through Quick Work.
6. **A quiet send-out.** A block that no role of the Pokémon fits gives no message, so a normal send-out stays quiet. Only a
   block that a leash-only role fits gets "{target} needs the Work Leash".
7. **More ends than the rulings list.** A travel timeout ends the order with "{Pokémon} cannot reach {target}", and a faint
   ends it as a recall does. Cobblemon's recall beam ends it at once, not 1.5 s later.
8. **No flip.** No binder and no placer stand behind a Quick Work claim, so only an operator can flip a wheel or a crank that
   a Quick Worker drives.
9. **No made-up click and no claim-mod compat yet.** `mayInteract` is the permission check. Compat with the claim APIs of
   Open Parties and Claims or FTB Chunks can follow as separate work.
