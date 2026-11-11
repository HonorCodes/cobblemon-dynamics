# M7: Fan Catalyst and Tailwind

Part of the [Phase 2 plan](2026-11-10-phase-2-plan.md). Read its Global Constraints, including the multiplayer rules,
first. Branch: `m7-fan-tailwind`. Builds on M6.

**Milestone goal:** Fire-, Water- and Ghost-types stand in front of a Create encased fan as its catalyst, so the fan blasts
or smokes, washes or haunts the items in its air current at Create's own speed, and never pushes or harms its worker.
Flying-types add virtual sails to a Create windmill. Each role turns itself off when Create or its mixin is missing.

Before Task 1, check the references of this milestone against the jars (rule 3). Every class that names a Create type
stays in `handler/create/` or `mixin/create/`, and only `CreateCompat.register` names them from outside.

Checked with `javap` on 2026-11-10, on the Create 6.0.10 build 280 slim jar, the merged NeoForge 21.1.265 jar, the
MixinExtras 0.5.5 jar and the Cobblemon 1.8.4 jar:

- `FanProcessingType` has `isValidAt(Level, BlockPos)`, `getPriority()`, `canProcess(ItemStack, Level)`,
  `process(ItemStack, Level)`, `spawnProcessingParticles(Level, Vec3)`, `morphAirFlow(AirFlowParticleAccess, RandomSource)`,
  `affectEntity(Entity, Level)` and the static `getAt(Level, BlockPos)`. `getAt` returns the first valid type of
  `FanProcessingTypeRegistry.SORTED_TYPES_VIEW`, which the registry's bake callback sorts by priority, highest first.
- `CreateRegistries.FAN_PROCESSING_TYPE` is the registry key, and `CreateBuiltInRegistries.FAN_PROCESSING_TYPE` is a
  synced NeoForge registry. `AllFanProcessingTypes.BLASTING`, `SMOKING`, `SPLASHING` and `HAUNTING` are public static
  instances.
- `AirCurrent` holds `public final IAirCurrentSource source`, `public AABB bounds`, `public Direction direction`,
  `public boolean pushing`, `public float maxDistance`, `protected List<Entity> caughtEntities` and
  `protected List<Pair<TransportedItemStackHandlerBehaviour, FanProcessingType>> affectedItemHandlers`.
  `findEntities()` sets `caughtEntities` to `level.getEntities(null, bounds)`. `tick()` calls
  `tickAffectedEntities(Level)` and then `tickAffectedHandlers()` on both sides. `tickAffectedEntities` walks
  `caughtEntities`: it pushes each entity, then processes an item entity (`FanProcessing.applyProcessing`) or calls
  `affectEntity`. `rebuild()` sets `direction` to the source's `getAirflowOriginSide()` and tests
  `source.getAirCurrentPos().relative(direction, i)` with `FanProcessingType.getAt`.
- So `findEntities` and `caughtEntities` live in `AirCurrent`, not in `EncasedFanBlockEntity`, as ARCHITECTURE section 8
  says. The mixin targets `AirCurrent`.
- `EncasedFanBlockEntity` has `public AirCurrent airCurrent` and `blockInFrontChanged()`, which sets `updateAirFlow`. On its
  next server tick the fan rebuilds the current and calls `sendData()`. `getAirflowOriginSide()` reads
  `EncasedFanBlock.FACING`. `read(..., clientPacket)` calls `super.read` first, which reads every behaviour, and then
  `airCurrent.rebuild()` for a client packet.
- `SmartBlockEntity.read` posts `BlockEntityBehaviourEvent` on the first read, then reads every behaviour.
  `SmartBlockEntity.tick()` ticks every behaviour. `BlockEntityBehaviourEvent.forType` and `attach` exist.
- `TransportedItemStackHandlerBehaviour.handleProcessingOnAllItems(Function)` and `TransportedResult.doNothing()` exist.
- `KineticBlockEntity.getSpeed()`, `CreativeMotorBlockEntity.generatedSpeed` (a `ScrollValueBehaviour` with
  `setValue(int)`) and `CreativeMotorBlockEntity.MAX_SPEED` exist. `CKinetics` holds `fanProcessingTime`,
  `fanBlockCheckRate`, `windmillSailsPerRPM` and `minimumWindmillSails`. `AllConfigs.server().kinetics` reaches them.
- Create's recipes `create:splashing/wool` (any wool to white wool) and `create:haunting/soul_sand` (`#minecraft:sand` to
  soul sand) are in the jar.
- `WindmillBearingBlockEntity.getGeneratedSpeed()` returns 0 when the bearing is not `running`, `lastGeneratedSpeed` when
  `movedContraption` is null, and otherwise `clamp(getSailBlocks() ÷ windmillSailsPerRPM, 1, 16) × getAngleSpeedDirection()`,
  where `getSailBlocks()` is a call on the `BearingContraption`. `updateGeneratedRotation()` exists.
  `MechanicalBearingBlockEntity` has `assemble()`, `disassemble()`, `isRunning()` and `getMovedContraption()`.
  `WindmillBearingBlock.useItemOn` sets `assembleNextTick`, or disassembles a running windmill, for a player who
  `mayBuild()`.
- Create attaches a `SailBlock` to its neighbors across its `FACING` axis (`BlockMovementChecksImpl`), so a flat sheet of
  sails holds together.
- `AllBlocks.ENCASED_FAN`, `CREATIVE_MOTOR`, `WINDMILL_BEARING`, `SAIL`, `SHAFT`, `DEPOT`, `BELT` and
  `AllBlockEntityTypes.ENCASED_FAN`, `WINDMILL_BEARING` exist. `BeltConnectorItem.createBelts(Level, BlockPos, BlockPos)`,
  `BeltBlockEntity.getControllerBE()`, `BeltInventory.addItem(TransportedItemStack)`, `TransportedItemStack(ItemStack)`
  with `beltPosition`, and `DepotBlockEntity.setHeldItem(ItemStack)` and `getHeldItem()` exist.
- `com.llamalad7.mixinextras.injector.ModifyExpressionValue` is in MixinExtras 0.5.5, which NeoForge 21.1.265 ships.
- Cobblemon timelines: `firespin` lists `firespin` and `special` and emits `firespin_actorburst` and `firespin_actor` from
  the user and `firespin_targetburst` and `firespin_target` on the target. `bubble` lists `bubble`, `water`, `spray` and
  `special`, emits `bubble_actor` from the user inside a `sequence` step, and names the target particle
  `cobblemon:bubble_target`, which no particle file of Cobblemon 1.8.4 defines. `willowisp` lists `willowisp`, `fire` and
  `special`, with `willowisp_actor` and `willowisp_target`. `gust` lists `gust`, `flying` and `special`, with `gust_actor`
  and `gust_target` and `gust_targethit`. None of these particle files plays a sound. Pidgey is Normal and Flying, with a
  hitbox of 0.5 × 0.7.

Checked in Task 2 on 2026-11-11, with `javap` and the `FanTests` GameTests:

- `caughtEntities` is a mutable list. `findEntities` sets it to `level.getEntities(null, bounds)`, which builds a
  `Lists.newArrayList()`, and Create's own `tickAffectedEntities` removes entries through its iterator.
- A positive creative motor value makes the fan in front of it blow out of its face. `CreateKit.fan` checks
  `getAirFlowDirection()` after a tick, and the check passes in every `FanTests` scene.
- `createBelts` reads a shaft's axis only for a vertical belt, whose x and z steps are equal, so a flat belt needs no
  shafts at its ends. It destroys the blocks in its way. A belt has no controller until its first tick, which calls
  `BeltBlock.initBelt` while `beltLength` is 0, so `CreateKit.belt` calls `initBelt` itself before it adds the item. An
  unpowered belt holds its item in place, and the fan processes it there.
- A wild Pokémon with `setNoAi(true)` is never pushed by a fan. `Mob.isEffectiveAi()` is false with no AI, and
  `LivingEntity.travel` moves only an entity that `isControlledByLocalInstance()`, which falls back to `isEffectiveAi()`.
  The fan still sets the entity's velocity, so `cdFan12MarkedWorkerIsNeitherPushedNorHaunted` reads the velocity.
- `GameTestHelper.onEachTick` adds one callback for each tick that is left to the map that the test's tick walks. A call
  from inside a callback changes that map during the walk, so `CreateKit.catalyse` registers it when the test starts.

Inferred (check before the task that uses it):

- `handleProcessingOnAllItems` with `doNothing()` changes nothing (Task 3).
- A sheet of sails whose `FACING` is the bearing's facing assembles as one windmill, and a speed change reaches a shaft
  behind the bearing within 1 tick (Task 4).
- `MixinState`'s handler-call check sees the call that `@ModifyExpressionValue` injects, as it does for `@ModifyArg`
  (Task 4).

`CreateKit` additions (full test mod only). Task 2 adds all but `windmill`, which Task 4 adds with its first test:

```kotlin
/** A creative motor at [rel] whose shaft faces [facing], turning at [rpm]. */
fun motor(helper: GameTestHelper, rel: BlockPos, facing: Direction, rpm: Int): CreativeMotorBlockEntity {
    helper.setBlock(rel, AllBlocks.CREATIVE_MOTOR.defaultState.setValue(DirectionalKineticBlock.FACING, facing))
    return helper.getBlockEntity<CreativeMotorBlockEntity>(rel).also { setRpm(it, rpm) }
}

fun setRpm(motor: CreativeMotorBlockEntity, rpm: Int) {
    motor.generatedSpeed.setValue(rpm)
    motor.updateGeneratedRotation()
}

/** An encased fan at [rel] facing [facing], with a creative motor behind it at [rpm], so that it blows out of its face. */
fun fan(helper: GameTestHelper, rel: BlockPos, facing: Direction, rpm: Int): EncasedFanBlockEntity {
    helper.setBlock(rel, AllBlocks.ENCASED_FAN.defaultState.setValue(DirectionalKineticBlock.FACING, facing))
    motor(helper, rel.relative(facing.opposite), facing, rpm)
    val fan = helper.getBlockEntity<EncasedFanBlockEntity>(rel)
    helper.runAfterDelay(1) {
        if (fan.airFlowDirection != facing) throw GameTestAssertException("the fan at $rel blows ${fan.airFlowDirection}, not $facing")
    }
    return fan
}

/** A depot at [rel] that holds [stack]. A depot one block below an air current is in the current. */
fun depot(helper: GameTestHelper, rel: BlockPos, stack: ItemStack): DepotBlockEntity {
    helper.setBlock(rel, AllBlocks.DEPOT.defaultState)
    return helper.getBlockEntity<DepotBlockEntity>(rel).also { it.setHeldItem(stack) }
}

/**
 * An unpowered belt from [from] to [to] along one axis, with [stack] at its middle. Returns its controller. A belt finds its
 * controller in its first tick, through `BeltBlock.initBelt`, which this calls at once.
 */
fun belt(helper: GameTestHelper, from: BlockPos, to: BlockPos, stack: ItemStack): BeltBlockEntity {
    BeltConnectorItem.createBelts(helper.level, helper.absolutePos(from), helper.absolutePos(to))
    BeltBlock.initBelt(helper.level, helper.absolutePos(from))
    val controller = helper.getBlockEntity<BeltBlockEntity>(from).controllerBE ?: throw GameTestAssertException("no belt controller")
    controller.getInventory().addItem(TransportedItemStack(stack).apply { beltPosition = from.distManhattan(to) / 2f + 0.5f })
    return controller
}

/**
 * Holds [kind] (and [mode], for Fire) on [fan] for the rest of the test, as a Working worker does. Starts on the fan's first tick,
 * when Create attaches the fan state.
 */
fun catalyse(helper: GameTestHelper, fan: EncasedFanBlockEntity, kind: FanCatalyst, mode: FireMode = FireMode.BLASTING) {
    var held: FanState? = null
    helper.onEachTick {
        val state = held ?: FanState.of(fan)?.also {
            if (it.fireMode != mode) it.flipMode()
            held = it
        } ?: return@onEachTick
        state.grant(kind, helper.level.gameTime)
    }
}

/** A windmill bearing at [rel] that faces up, with a shaft below it and a flat sheet of [sails] sails on its face. */
fun windmill(helper: GameTestHelper, rel: BlockPos, sails: Int): WindmillBearingBlockEntity {
    helper.setBlock(rel.below(), AllBlocks.SHAFT.defaultState.setValue(ShaftBlock.AXIS, Direction.Axis.Y))
    helper.setBlock(rel, AllBlocks.WINDMILL_BEARING.defaultState.setValue(BearingBlock.FACING, Direction.UP))
    val sail = AllBlocks.SAIL.defaultState.setValue(SailBlock.FACING, Direction.UP)
    val width = ceil(sqrt(sails.toDouble())).toInt()
    // Row by row from one corner; the sheet always covers the block on the bearing's face, at its center.
    for (i in 0 until sails) helper.setBlock(rel.offset(i % width - width / 2, 1, i / width - width / 2), sail)
    return helper.getBlockEntity(rel)
}
```

---

### Task 1: Handler hooks for spot rules, claim-long state and flip messages

**Rows:** CD-FAN-03 (a handler's own spot rule), CD-FAN-04 (a bind refusal when the worker has no room), CD-FAN-10 and
CD-FAN-16 (a message after a flip), CD-SEC-10 (the flip permission as a pure rule), CD-ASG-09 (every spot rule of the
engine still applies).

**Files:**
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/handler/TargetHandler.kt`
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/engine/WorkSpots.kt` (`allowed`)
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/engine/WorkEngine.kt` (`spotFor`, `hasSpot`, the claim hooks)
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/leash/BindRules.kt` (`Input.noSpotRefusal`)
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/leash/LeashInteractions.kt` (the refusal, `FlipRules`, the flip
  message)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/leash/FlipRules.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/engine/WorkSpotsTest.kt`,
  `src/test/kotlin/codes/honor/cobblemondynamics/leash/BindRulesTest.kt`, `src/test/kotlin/codes/honor/cobblemondynamics/leash/FlipRulesTest.kt`

**Interfaces:**
- Consumes: `WorkSpots.findReachable`, `Reach.of` (M6 Task 1).
- Produces:

```kotlin
interface TargetHandler {
    // ... Phase 1 and M6 members ...

    /** Whether a worker [bodyHeight] blocks high may work this target from [spot]. The engine asks only of a spot that passes its own checks. */
    fun allowsSpot(level: ServerLevel, canonical: BlockPos, spot: BlockPos, bodyHeight: Double): Boolean = true

    /** The lang key that refuses a Work Leash bind when the worker has no spot at the target, or null to bind and report later (CD-LSH-21). */
    val noSpotRefusal: String? get() = null

    /** A work claim on the context's target starts for its worker, or the claim follows the Pokémon to a new entity. */
    fun onClaim(ctx: WorkContext) {}

    /** The work claim of the context's worker ends, by any path, or leaves that entity for the Pokémon's new entity. */
    fun onUnclaim(ctx: WorkContext) {}

    /** The action-bar message after a [flip] that changed the target, or null for none. */
    fun flipMessage(level: ServerLevel, canonical: BlockPos): Component? = null
}
```

The claim hooks take a `WorkContext`, as `onStart` and `onStop` do, and no member of `TargetHandler` names `PokemonEntity` in its
signature. JUnit reflects on the interface through the unit tests' `FakeHandler`, and `PokemonEntity` does not load in a unit test:
it overrides `LivingEntity.getDimensions`, which is final without the game's transformers. The first build with
`onClaim(level, canonical, worker: PokemonEntity)` failed in test discovery with that `IncompatibleClassChangeError`.

```kotlin
object WorkSpots {
    fun find(..., excluded: Set<Long> = emptySet(), allowed: (BlockPos) -> Boolean = { true }): BlockPos?
    fun findReachable(..., reachable: (BlockPos) -> Boolean, maxChecks: Int = MAX_PATH_CHECKS, allowed: (BlockPos) -> Boolean = { true }): BlockPos?
}

class WorkEngine {
    /** CD-FAN-04: whether [pokemonId]'s worker has a spot at [canonical] for [role]. True while the engine knows no entity for it, so the claim decides. */
    fun hasSpot(level: ServerLevel, pasture: BlockPos, pokemonId: UUID, role: ActiveRole, canonical: BlockPos): Boolean
}

/** Who may flip a driven target with the Work Leash: its worker's binder, the placer of the worker's pasture, or an operator (CD-WHL-05, CD-SEC-10). */
object FlipRules {
    fun mayFlip(actor: UUID, isOperator: Boolean, binder: UUID?, placer: UUID?): Boolean
}
```

- [ ] **Step 1: Failing unit tests.**

```kotlin
// WorkSpotsTest
@Test
fun `CD-FAN-03 a handler that allows one spot gets that spot or none`() {
    val front = BlockPos(1, 1, 0)
    val shape = TargetShape.of(target)
    val far = Vec3(-3.5, 1.0, 0.5)
    assertEquals(front, WorkSpots.find(GridFixture(emptySet(), target), area, shape, center, 6.0, far, allowed = { it == front }))
    assertNull(WorkSpots.find(GridFixture(setOf(front), target), area, shape, center, 6.0, far, allowed = { it == front }))
}

// BindRulesTest: the input helper gains noSpot: String? = null, passed as noSpotRefusal = noSpot.
@Test
fun `CD-FAN-04 a target with no room for the worker refuses with the handler's message`() = assertEquals(
    "cobblemon_dynamics.leash.no_room_in_front",
    refusal(input(noSpot = "cobblemon_dynamics.leash.no_room_in_front")).messageKey,
)

@Test
fun `CD-FAN-04 a target bound to another Pokemon is named before the room`() = assertEquals(
    BindRules.ALREADY_ASSIGNED,
    refusal(input(boundTo = own("Charmander"), noSpot = "cobblemon_dynamics.leash.no_room_in_front")).messageKey,
)

class FlipRulesTest {
    private val binder = UUID.randomUUID()
    private val placer = UUID.randomUUID()
    private val stranger = UUID.randomUUID()

    @Test
    fun `CD-SEC-10 a stranger may not flip a target that another player's worker drives`() =
        assertFalse(FlipRules.mayFlip(stranger, isOperator = false, binder = binder, placer = placer))

    @Test
    fun `CD-WHL-05 the binder, the placer and an operator may flip`() {
        assertTrue(FlipRules.mayFlip(binder, false, binder, placer))
        assertTrue(FlipRules.mayFlip(placer, false, binder, placer))
        assertTrue(FlipRules.mayFlip(stranger, true, binder, placer))
    }

    @Test
    fun `CD-SEC-10 with no binder and no placer only an operator may flip`() {
        assertFalse(FlipRules.mayFlip(stranger, false, null, null))
        assertTrue(FlipRules.mayFlip(stranger, true, null, null))
    }
}
```

- [ ] **Step 2: Run them and see them fail** to compile.

- [ ] **Step 3: Write the code.**
  - `WorkSpots.find` skips a cell that `allowed` refuses, before the line-of-sight check, and `findReachable` passes
    `allowed` to each `find`.
  - `BindRules.Input` gains `val noSpotRefusal: String? = null` after `destinationInReach`. `evaluate` returns
    `Outcome.Refuse(it)` for it right after the `ALREADY_ASSIGNED` check.
  - `FlipRules.mayFlip(actor, isOperator, binder, placer) = isOperator || actor == binder || actor == placer`, and
    `LeashInteractions.mayFlip` becomes:

```kotlin
private fun mayFlip(player: ServerPlayer, level: ServerLevel, engine: WorkEngine, claim: Claim): Boolean {
    val binder = engine.pokemonOf(claim.worker)?.let(BindingStore::read)?.binder
    val placer = PastureAdapter.pastureAt(level, BlockPos.of(claim.pasture.pos))?.let(PastureAdapter::placer)
    return FlipRules.mayFlip(player.uuid, player.hasPermissions(PERMISSION_OPERATOR), binder, placer)
}
```

  - `LeashInteractions.onSneakBlockClick` sends the message of a flip that changed the target:

```kotlin
if (!mayFlip(player, level, engine, claim)) return false
val flipped = guarded(handler, false) { handler.flip(level, canonical) }
if (flipped) guarded(handler, null) { handler.flipMessage(level, canonical) }?.let { Feedback.actionBar(player, it) }
return flipped
```

  - `LeashInteractions.onBlockClick` passes, for a click with no pending source:

```kotlin
noSpotRefusal = best?.handler?.noSpotRefusal?.takeIf {
    pending == null && pasture != null && WorkEngine.get()?.hasSpot(level, pasture, pokemon.uuid, best, canonical) == false
},
```

  `pasture` comes from `pokemon?.let`, so Kotlin knows that `pokemon` is not null where `pasture` is not, and warns about a
  `pokemon != null` check there.

  - `WorkEngine` finds every spot through one function, which `claim` and `hasSpot` share:

```kotlin
/** The engine's spot checks (CD-ASG-09), the handler's own rule and, with [reachable], a path from the worker. */
private fun spotFor(
    level: ServerLevel, area: WorkArea, entity: PokemonEntity, role: ActiveRole, target: BlockPos, others: Set<Long>,
    reachable: (BlockPos) -> Boolean,
): BlockPos? {
    val grid = LevelSpotGrid(level, role.handler, target, others, entity.bbWidth.toDouble(), entity.bbHeight.toDouble())
    val shape = TargetShape.around(target) { level.isLoaded(it) && role.handler.isPartOfTarget(level, target, it) }
    val allowed: (BlockPos) -> Boolean = { role.handler.allowsSpot(level, target, it, entity.bbHeight.toDouble()) }
    return WorkSpots.findReachable(
        grid, area, shape, role.handler.targetCenter(target), role.definition.range, entity.position(), reachable, allowed = allowed,
    )
}

fun hasSpot(level: ServerLevel, pasture: BlockPos, pokemonId: UUID, role: ActiveRole, canonical: BlockPos): Boolean {
    val entity = entityOf(pokemonId) ?: return true
    val t = tick(level)
    val others = takenSpots(t).also { taken -> slots[pokemonId]?.worker?.spot?.let { taken.remove(it.asLong()) } }
    val area = WorkArea.of(pasture, t.settings, PastureAdapter.maxWanderDistance())
    // At bind time only room counts: a missing path shows later as the "cannot reach" message (CD-LSH-21).
    return guarded(role.handler, true) { spotFor(level, area, entity, role, canonical, others) { true } != null }
}
```

  - The claim hooks get the claim's `WorkContext`, with the slot's memo:
    - `claim` calls `role.handler.onClaim(ctx)`, guarded, after the registry grants the claim.
    - `clear(t, slot)` calls `onUnclaim` on the slot's role and target before it drops them, when both are set. Every end of a
      work claim goes through `clear`: a release, a drop, a timeout, a Tired worker and a failed handler.
    - `poll` calls `onUnclaim` for the old entity and `onClaim` for the new one when Cobblemon gives a worker with a work claim
      a new entity. The old entity can outlive the change, and a mark left on it would exempt it from every fan.
    - `resetRuntimeForTest` calls `onUnclaim` for every slot with a work claim.

- [ ] **Step 4: Run the unit tests and see them pass.** Then run `./gradlew runGameTestServer`: every Phase 1 and M6 GameTest
  still passes, because the default hooks change nothing.

- [ ] **Step 5: Multiplayer check.** `FlipRules` compares UUIDs and the operator permission, never "the" player. The flip
  message and the bind refusal go to the clicking player's action bar only. `hasSpot` reads the engine's state on the
  server thread.

- [ ] **Step 6: Commit:** `git commit -m "Let handlers rule on work spots, hold state for a whole claim and answer a flip"`.

---

### Task 2: Fan state, the Pokémon catalyst types and the air current mixin

**Rows:** CD-FAN-05, CD-FAN-06, CD-FAN-07, CD-FAN-08, CD-FAN-09, CD-FAN-11, CD-FAN-12 (the exemption), CD-FAN-13, CD-FAN-14
(the synced state), CD-FAN-17, CD-BAL-04 (the fan part).

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/create/FanNbt.kt` (`FanCatalyst`, `FireMode`, `FanNbt`:
  no Create class, so unit tests load it)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/create/FanState.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/create/PokemonFanTypes.kt` (`PokemonFanType`,
  `PokemonFanTypes`)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/create/FanWorkers.kt`
- Create: `src/main/java/codes/honor/cobblemondynamics/handler/create/AirCurrentAccess.java`
- Create: `src/main/java/codes/honor/cobblemondynamics/mixin/create/AirCurrentMixin.java`
- Modify: `src/main/java/codes/honor/cobblemondynamics/MixinState.java` (`AIR_CURRENT`)
- Modify: `src/main/resources/cobblemon_dynamics.mixins.json` (`create.AirCurrentMixin`)
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/role/MixinGate.kt` (`fan_catalyst` needs `AIR_CURRENT`)
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/registry/DynamicsAttachments.kt` (`FAN_WORKER`)
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/handler/create/CreateCompat.kt` (`register(modBus)`: the types,
  the fan state, the `AirCurrent` class literal), `src/main/kotlin/codes/honor/cobblemondynamics/CobblemonDynamics.kt`
  (`CreateCompat.register(modBus)`)
- Modify: `src/main/resources/assets/cobblemon_dynamics/lang/en_us.json` (the two `cobblemon_dynamics.fan.mode` keys:
  `FireMode` names them, and `LangTest` needs each key literal of the code in `en_us`)
- Modify: `ARCHITECTURE.md` (sections 2, 4 and 8: the mixin is `AirCurrentMixin`, the mark lasts for the claim, there is no
  nozzle mixin), `docs/adr/0011-v2-roles-in-first-release.md` (an amendment: two Create mixins, not three). This task adds
  the mixin, so its commit fixes the text that named the wrong class.
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/handler/create/FanNbtTest.kt`,
  `src/test/kotlin/codes/honor/cobblemondynamics/role/MixinGateTest.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/create/FanTests.kt`, `CreateKit.kt` (the additions above,
  except `windmill`)

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces:

```kotlin
enum class FanCatalyst(val serialName: String) {
    FIRE("fire"), WATER("water"), GHOST("ghost");
    companion object { fun parse(name: String?): FanCatalyst? }
}

enum class FireMode(val serialName: String, val langKey: String) {
    BLASTING("blasting", "cobblemon_dynamics.fan.mode.blasting"),
    SMOKING("smoking", "cobblemon_dynamics.fan.mode.smoking");
    fun flipped(): FireMode
    companion object { fun parse(name: String?): FireMode? }
}

/** The NBT keys and rules of [FanState]. */
object FanNbt {
    const val MODE = "cd_fire_mode"
    const val CATALYST = "cd_catalyst"
    /** The mode always goes in (CD-FAN-11). The catalyst goes only into client packets, because claims are never saved. */
    fun write(tag: CompoundTag, mode: FireMode, catalyst: FanCatalyst?, clientPacket: Boolean)
    /** Blasting unless the tag says smoking (CD-FAN-09). */
    fun mode(tag: CompoundTag): FireMode
    fun catalyst(tag: CompoundTag, clientPacket: Boolean, current: FanCatalyst?): FanCatalyst?
}

class FanState(be: SmartBlockEntity) : BlockEntityBehaviour(be) {
    val fireMode: FireMode
    val catalyst: FanCatalyst?
    fun catalyses(kind: FanCatalyst, mode: FireMode?): Boolean
    fun grant(kind: FanCatalyst, now: Long)
    fun withdraw()
    fun flipMode(): FireMode
    companion object { val TYPE: BehaviourType<FanState>; fun of(be: BlockEntity?): FanState? }
}

object PokemonFanTypes {
    const val PRIORITY = 500
    val REGISTER: DeferredRegister<FanProcessingType>
    val BLASTING, SMOKING, SPLASHING, HAUNTING: DeferredHolder<FanProcessingType, PokemonFanType>
}

object FanWorkers {
    @JvmStatic fun isExempt(entity: Entity): Boolean
    fun mark(worker: PokemonEntity, fan: BlockPos)
    fun clear(worker: PokemonEntity)
}
```

```java
/** What AirCurrentMixin adds to Create's air current. It sits outside the mixin package, which Mixin reserves. */
public interface AirCurrentAccess {
    List<Entity> cobblemon_dynamics$caughtEntities();

    List<Pair<TransportedItemStackHandlerBehaviour, FanProcessingType>> cobblemon_dynamics$affectedHandlers();
}
```

- [ ] **Step 1: Failing unit tests.**

```kotlin
class FanNbtTest {
    @Test
    fun `CD-FAN-09 a fan with no saved mode blasts`() = assertEquals(FireMode.BLASTING, FanNbt.mode(CompoundTag()))

    @Test
    fun `CD-FAN-11 a fan's mode survives a save`() {
        val tag = CompoundTag().also { FanNbt.write(it, FireMode.SMOKING, FanCatalyst.FIRE, clientPacket = false) }
        assertEquals(FireMode.SMOKING, FanNbt.mode(tag))
    }

    @Test
    fun `CD-FAN-14 the client packet carries the catalyst and the mode, and a save never holds the catalyst`() {
        val save = CompoundTag().also { FanNbt.write(it, FireMode.BLASTING, FanCatalyst.GHOST, clientPacket = false) }
        assertFalse(save.contains(FanNbt.CATALYST))
        assertEquals(FanCatalyst.WATER, FanNbt.catalyst(save, clientPacket = false, current = FanCatalyst.WATER))
        val packet = CompoundTag().also { FanNbt.write(it, FireMode.SMOKING, FanCatalyst.GHOST, clientPacket = true) }
        assertEquals(FanCatalyst.GHOST, FanNbt.catalyst(packet, clientPacket = true, current = null))
        assertEquals(FireMode.SMOKING, FanNbt.mode(packet))
        val cleared = CompoundTag().also { FanNbt.write(it, FireMode.BLASTING, null, clientPacket = true) }
        assertNull(FanNbt.catalyst(cleared, clientPacket = true, current = FanCatalyst.FIRE))
    }

    @Test
    fun `CD-FAN-10 a flip switches between blasting and smoking`() {
        assertEquals(FireMode.SMOKING, FireMode.BLASTING.flipped())
        assertEquals(FireMode.BLASTING, FireMode.SMOKING.flipped())
    }
}

// MixinGateTest
@Test
fun `CD-FAN-17 a missing air current mixin disables every Fan Catalyst role with one warning`() {
    val roles = listOf("fire", "water", "ghost").map {
        role("fan_catalyst_$it", setOf(it), autoAssign = false, handler = "cobblemon_dynamics:fan_catalyst")
    }
    val set = RoleSet(roles.associateBy { it.id }, { FakeHandler(it) }, emptySet()) { }
    val warnings = mutableListOf<String>()
    val gated = MixinGate.apply(set, applied = { it != MixinState.AIR_CURRENT }, warn = warnings::add)
    assertTrue(gated.active.isEmpty())
    assertEquals(1, warnings.size)
}
```

- [ ] **Step 2: Run them and see them fail** to compile.

- [ ] **Step 3: Write the code.**

```kotlin
class FanState(be: SmartBlockEntity) : BlockEntityBehaviour(be) {
    var fireMode: FireMode = FireMode.BLASTING
        private set

    /** The catalyst of the Working worker in front of the fan. Clients get it, so they build the same air current (CD-FAN-14). */
    var catalyst: FanCatalyst? = null
        private set

    /** The last tick that the catalyst holds with no renewal. Server only. */
    private var until = Long.MIN_VALUE

    fun catalyses(kind: FanCatalyst, mode: FireMode?): Boolean = catalyst == kind && (mode == null || mode == fireMode)

    /** For this tick and the next. The handler renews it on every Working tick, so a missed release lapses within 2 ticks. */
    fun grant(kind: FanCatalyst, now: Long) {
        until = now + 1
        if (catalyst != kind) {
            catalyst = kind
            changed()
        }
    }

    fun withdraw() {
        until = Long.MIN_VALUE
        if (catalyst != null) {
            catalyst = null
            changed()
        }
    }

    /** CD-FAN-10: the new mode, saved with the fan (CD-FAN-11). */
    fun flipMode(): FireMode {
        fireMode = fireMode.flipped()
        blockEntity.setChanged()
        changed()
        return fireMode
    }

    override fun tick() {
        val level = blockEntity.level ?: return
        if (!level.isClientSide && catalyst != null && level.gameTime > until) withdraw()
    }

    /** The fan rebuilds its air current on its next server tick and sends it, with this state, to every client that tracks it. */
    private fun changed() {
        (blockEntity as? EncasedFanBlockEntity)?.blockInFrontChanged()
    }

    override fun getType(): BehaviourType<*> = TYPE

    override fun write(nbt: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) =
        FanNbt.write(nbt, fireMode, catalyst, clientPacket)

    override fun read(nbt: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        fireMode = FanNbt.mode(nbt)
        catalyst = FanNbt.catalyst(nbt, clientPacket, catalyst)
    }

    companion object {
        @JvmField
        val TYPE = BehaviourType<FanState>("cobblemon_dynamics:fan_state")

        /** Null for a block entity that has none, and for a new fan before its first tick. */
        fun of(be: BlockEntity?): FanState? = BlockEntityBehaviour.get(be, TYPE)
    }
}
```

```kotlin
/**
 * Create's fan processing for a Pokémon catalyst. It is valid only in the block in front of a fan whose [FanState] holds a
 * catalyst of [kind] (and, for Fire, [mode]). Everything else is Create's own type, so items process as Create's bulk
 * processing does, at Create's speed (CD-FAN-05 to CD-FAN-08, CD-BAL-04). It runs on both sides, so it names no client class.
 */
class PokemonFanType(private val kind: FanCatalyst, private val mode: FireMode?, private val create: FanProcessingType) : FanProcessingType {
    override fun isValidAt(level: Level, pos: BlockPos): Boolean = Direction.entries.any { side ->
        val fan = level.getBlockEntity(pos.relative(side)) as? EncasedFanBlockEntity ?: return@any false
        fan.blockState.getValue(EncasedFanBlock.FACING) == side.opposite && FanState.of(fan)?.catalyses(kind, mode) == true
    }

    /** CD-FAN-13: above Create's splashing at 400, so a worker that stands in water is still its own catalyst. */
    override fun getPriority(): Int = PokemonFanTypes.PRIORITY

    override fun canProcess(stack: ItemStack, level: Level): Boolean = create.canProcess(stack, level)

    override fun process(stack: ItemStack, level: Level): List<ItemStack>? = create.process(stack, level)

    override fun spawnProcessingParticles(level: Level, pos: Vec3) = create.spawnProcessingParticles(level, pos)

    override fun morphAirFlow(particle: FanProcessingType.AirFlowParticleAccess, random: RandomSource) = create.morphAirFlow(particle, random)

    override fun affectEntity(entity: Entity, level: Level) = create.affectEntity(entity, level)
}

object PokemonFanTypes {
    /** Above Create's built-in types: splashing 400, haunting 300, smoking 200 and blasting 100. */
    const val PRIORITY = 500

    val REGISTER: DeferredRegister<FanProcessingType> = DeferredRegister.create(CreateRegistries.FAN_PROCESSING_TYPE, MOD_ID)
    val BLASTING = REGISTER.register("pokemon_blasting", Supplier { PokemonFanType(FanCatalyst.FIRE, FireMode.BLASTING, AllFanProcessingTypes.BLASTING) })
    val SMOKING = REGISTER.register("pokemon_smoking", Supplier { PokemonFanType(FanCatalyst.FIRE, FireMode.SMOKING, AllFanProcessingTypes.SMOKING) })
    val SPLASHING = REGISTER.register("pokemon_splashing", Supplier { PokemonFanType(FanCatalyst.WATER, null, AllFanProcessingTypes.SPLASHING) })
    val HAUNTING = REGISTER.register("pokemon_haunting", Supplier { PokemonFanType(FanCatalyst.GHOST, null, AllFanProcessingTypes.HAUNTING) })
}
```

```kotlin
/** The fan worker mark: the fan that a worker holds a work claim on. It syncs to clients, which push entities too (CD-FAN-12). */
object FanWorkers {
    /** A worker with a work claim on a fan: no fan pushes, burns, harms or processes it. */
    @JvmStatic
    fun isExempt(entity: Entity): Boolean = entity is PokemonEntity && entity.hasData(DynamicsAttachments.FAN_WORKER)

    fun mark(worker: PokemonEntity, fan: BlockPos) {
        worker.setData(DynamicsAttachments.FAN_WORKER, fan)
    }

    fun clear(worker: PokemonEntity) {
        if (worker.hasData(DynamicsAttachments.FAN_WORKER)) worker.removeData(DynamicsAttachments.FAN_WORKER)
    }
}

// DynamicsAttachments
/** On a fan worker's `PokemonEntity`, for the length of its claim. Synced to tracking clients, never saved. */
val FAN_WORKER: DeferredHolder<AttachmentType<*>, AttachmentType<BlockPos>> = REGISTER.register("fan_worker", Supplier {
    AttachmentType.builder(Supplier { BlockPos.ZERO }).sync(BlockPos.STREAM_CODEC).build()
})
```

```java
@Mixin(AirCurrent.class)
public abstract class AirCurrentMixin implements AirCurrentAccess {
    @Shadow
    protected List<Entity> caughtEntities;

    @Shadow
    protected List<Pair<TransportedItemStackHandlerBehaviour, FanProcessingType>> affectedItemHandlers;

    /**
     * A fan's worker stands in its air current. Taking it out of the caught entities before Create walks the list stops the push,
     * the burning, the harm and the processing for that worker, on both sides, from the next tick of its mark (CD-FAN-12).
     */
    @Inject(method = "tickAffectedEntities(Lnet/minecraft/world/level/Level;)V", at = @At("HEAD"))
    private void cobblemon_dynamics$exemptWorkers(Level level, CallbackInfo ci) {
        caughtEntities.removeIf(FanWorkers::isExempt);
    }

    @Override
    public List<Entity> cobblemon_dynamics$caughtEntities() {
        return caughtEntities;
    }

    @Override
    public List<Pair<TransportedItemStackHandlerBehaviour, FanProcessingType>> cobblemon_dynamics$affectedHandlers() {
        return affectedItemHandlers;
    }
}
```

  `MixinState` gains `AIR_CURRENT = "create.AirCurrentMixin"`. `MixinGate.REQUIRED` gains
  `id("fan_catalyst") to MixinState.AIR_CURRENT`. `CreateCompat.register(modBus)` registers `PokemonFanTypes.REGISTER` on
  `modBus`, adds `AirCurrent::class.java` to its mixin targets, and attaches `FanState` to
  `AllBlockEntityTypes.ENCASED_FAN` in its `BlockEntityBehaviourEvent` listener, beside the drive behaviours.

- [ ] **Step 4: Run the unit tests and see them pass.**

- [ ] **Step 5: GameTests** in `create/FanTests` (Create fans and the mixin, so rule 6 keeps them). Each places its fan at
  `fanRel` = (3, 2, 7), facing east, and holds the catalyst with `CreateKit.catalyse`, with no Pokémon:

```kotlin
/** Raw iron on an unpowered belt under the current of a fan with a Fire catalyst in blasting mode becomes an iron ingot, no sooner than Create's processing time (CD-FAN-05, CD-BAL-04). */
@GameTest(template = "floor_15x6x15", timeoutTicks = 600)
fun cdFan05Bal04BlastingOnABeltKeepsCreatesTime(helper: GameTestHelper) {
    val fan = CreateKit.fan(helper, fanRel, Direction.EAST, rpm = 64)
    val belt = CreateKit.belt(helper, BlockPos(5, 1, 7), BlockPos(7, 1, 7), ItemStack(Items.RAW_IRON))
    val start = helper.level.gameTime
    CreateKit.catalyse(helper, fan, FanCatalyst.FIRE)
    val minimum = AllConfigs.server().kinetics.fanProcessingTime.get()
    helper.succeedWhen {
        val stack = belt.getInventory().transportedItems.firstOrNull()?.stack ?: throw GameTestAssertException("the belt holds no item")
        helper.assertTrue(stack.`is`(Items.IRON_INGOT), "CD-FAN-05: the belt holds $stack")
        val elapsed = helper.level.gameTime - start
        helper.assertTrue(elapsed >= minimum - 1, "CD-BAL-04: blasting took $elapsed ticks, under Create's $minimum")
    }
}

/** In smoking mode, raw beef becomes steak, and raw iron stays raw iron (CD-FAN-06). */
@GameTest(template = "floor_15x6x15", timeoutTicks = 600)
fun cdFan06SmokingCooksBeefAndLeavesIron(helper: GameTestHelper) {
    val fan = CreateKit.fan(helper, fanRel, Direction.EAST, rpm = 64)
    val beef = CreateKit.depot(helper, BlockPos(5, 1, 7), ItemStack(Items.BEEF))
    val iron = CreateKit.depot(helper, BlockPos(6, 1, 7), ItemStack(Items.RAW_IRON))
    CreateKit.catalyse(helper, fan, FanCatalyst.FIRE, FireMode.SMOKING)
    helper.succeedWhen {
        helper.assertTrue(iron.heldItem.`is`(Items.RAW_IRON), "CD-FAN-06: smoking turned the raw iron into ${iron.heldItem}")
        helper.assertTrue(beef.heldItem.`is`(Items.COOKED_BEEF), "CD-FAN-06: the beef depot holds ${beef.heldItem}")
    }
}

/** A Water catalyst washes red wool into white wool (CD-FAN-07). */
@GameTest(template = "floor_15x6x15", timeoutTicks = 600)
fun cdFan07WaterWashesRedWool(helper: GameTestHelper) {
    val fan = CreateKit.fan(helper, fanRel, Direction.EAST, rpm = 64)
    val wool = CreateKit.depot(helper, BlockPos(5, 1, 7), ItemStack(Items.RED_WOOL))
    CreateKit.catalyse(helper, fan, FanCatalyst.WATER)
    helper.succeedWhen { helper.assertTrue(wool.heldItem.`is`(Items.WHITE_WOOL), "CD-FAN-07: the depot holds ${wool.heldItem}") }
}

/** A Ghost catalyst haunts sand into soul sand, Create's haunting recipe `create:haunting/soul_sand` (CD-FAN-08). */
@GameTest(template = "floor_15x6x15", timeoutTicks = 600)
fun cdFan08GhostHauntsSand(helper: GameTestHelper) {
    val fan = CreateKit.fan(helper, fanRel, Direction.EAST, rpm = 64)
    val sand = CreateKit.depot(helper, BlockPos(5, 1, 7), ItemStack(Items.SAND))
    CreateKit.catalyse(helper, fan, FanCatalyst.GHOST)
    helper.succeedWhen { helper.assertTrue(sand.heldItem.`is`(Items.SOUL_SAND), "CD-FAN-08: the depot holds ${sand.heldItem}") }
}

/**
 * A water source fills the block in front of the fan, where a worker would stand, over a depot of sand. With a Ghost catalyst,
 * the sand becomes soul sand, not Create's washed clay, because our type outranks splashing at 400 (CD-FAN-13). The depot sits
 * under the front block, so water that flows on downstream does not reach it.
 */
@GameTest(template = "floor_15x6x15", timeoutTicks = 600)
fun cdFan13GhostInWaterStillHaunts(helper: GameTestHelper) {
    val fan = CreateKit.fan(helper, fanRel, Direction.EAST, rpm = 64)
    val sand = CreateKit.depot(helper, BlockPos(4, 1, 7), ItemStack(Items.SAND))
    helper.setBlock(BlockPos(4, 2, 7), Blocks.WATER)
    CreateKit.catalyse(helper, fan, FanCatalyst.GHOST)
    helper.succeedWhen {
        helper.assertFalse(sand.heldItem.`is`(Items.CLAY_BALL), "CD-FAN-13: the sand was washed")
        helper.assertTrue(sand.heldItem.`is`(Items.SOUL_SAND), "CD-FAN-13: the depot holds ${sand.heldItem}")
    }
}

/**
 * A fan at 256 RPM with a Ghost catalyst. A wild Gastly with the fan worker mark stands in the block in front of the fan, and an
 * unmarked Gastly stands 3 blocks downstream, both with no AI. After 100 ticks the marked one has not moved and has no Blindness
 * or Slowness, and the unmarked one was pushed or haunted, so the current works (CD-FAN-12). A mob with no AI never moves on its
 * own velocity, so the push shows in the velocity that the fan gives it: east for the unmarked one, none for the marked one.
 */
@GameTest(template = "floor_15x6x15", timeoutTicks = 200)
fun cdFan12MarkedWorkerIsNeitherPushedNorHaunted(helper: GameTestHelper) {
    helper.assertTrue(MixinState.applied(MixinState.AIR_CURRENT), "the air current mixin did not apply")
    val fan = CreateKit.fan(helper, fanRel, Direction.EAST, rpm = 256)
    CreateKit.catalyse(helper, fan, FanCatalyst.GHOST)
    val marked = TestKit.wild(helper, BlockPos(4, 2, 7), "gastly level=10").apply { isNoAi = true }
    val control = TestKit.wild(helper, BlockPos(7, 2, 7), "gastly level=10").apply { isNoAi = true }
    FanWorkers.mark(marked, helper.absolutePos(fanRel))
    val markedStart = marked.position()
    val controlStart = control.position()
    var pushed = false
    helper.onEachTick {
        helper.assertTrue(abs(marked.deltaMovement.x) <= PUSH, "CD-FAN-12: the fan pushed the marked Gastly: ${marked.deltaMovement}")
        if (control.deltaMovement.x > PUSH) pushed = true
    }
    helper.runAfterDelay(100) {
        helper.assertTrue(marked.position().distanceTo(markedStart) <= 0.5, "CD-FAN-12: the fan moved the marked Gastly")
        val harmed = marked.hasEffect(MobEffects.BLINDNESS) || marked.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
        helper.assertFalse(harmed, "CD-FAN-12: the marked Gastly was haunted")
        val acted = control.position().distanceTo(controlStart) > 1.0 || control.hasEffect(MobEffects.BLINDNESS)
        helper.assertTrue(acted, "the current did nothing to the unmarked Gastly, so the scene proves nothing")
        helper.assertTrue(pushed, "the current never pushed the unmarked Gastly, so the scene proves nothing about the push")
        helper.succeed()
    }
}

/** A velocity along the current above this is a push. Create's first push at 256 RPM adds about 0.3 blocks per tick. */
const val PUSH = 0.01
```

  The exemption scene keeps every assertion of the first draft. A fan cannot push a mob with no AI (see the checked facts
  above), so the draft's "not moved" check alone proved nothing about the push. The velocity checks and the mixin check are
  new.

  Deferred:
  - CD-FAN-11 with a real reload (`cdFan11SmokingSurvivesARestart`: flip a fan to smoking, save the fan's block entity to
    NBT, load it into a new block entity, and read smoking): GameTest deferred (high confidence): verified in the
    end-of-phase pass. `FanNbtTest` holds the rule, and Phase 1's `cdWhl07Crk08FlipsByBoundWorkersSurviveARestart` runs the
    same Create save path.

- [ ] **Step 6: Run** `./gradlew build` and `./gradlew runGameTestServer`. Check the log for `MixinState`: the mixin
  applied.

- [ ] **Step 7: Multiplayer check.** The catalyst and the mode go to every tracking client through the fan's
  `sendData()`. The mark goes through NeoForge's attachment sync. `PokemonFanType` and `AirCurrentMixin` run on both sides
  and name no client class. The renewal deadline is a field of the behaviour, not static state.

- [ ] **Step 8: Commit:** `git commit -m "Give Create fans Pokémon catalyst types and keep a fan's worker out of its air current"`.

**The shadow guard (2026-11-11, a ruling on the risk of this task's mixin).** `AirCurrentMixin` shadows `caughtEntities` and
`affectedItemHandlers`, and the mixin config has `"required": true`, so a Create 6.0.x that renamed either field would crash
the game at load, against CD-FAN-17. `MixinPlugin.shouldApplyMixin` therefore reads each mixin and its target through
Mixin's bytecode provider, and skips a mixin whose target lacks a member that it shadows (`MixinMembers`, unit-tested on
synthetic class nodes in `MixinMembersTest`). A skipped mixin records no handler call, so `MixinGate` turns its role off
with one warning. The check covers every mixin with `@Shadow` members: `AirCurrentMixin` and `EnchantmentMenuMixin`.
Checked with `javap` on sponge-mixin 0.16.4+mixin.0.8.7, which NeoForge 21.1.265 ships:

- `MixinInfo.readDeclaredTargets` asks the plugin about each target before `readTargetClasses` reads the target through
  `ClassInfo.forName`, and the mixin class itself was read before that, through the same provider.
- `@Shadow` is `RUNTIME` retention. An unmatched shadow field throws `InvalidMixinException` ("@Shadow field %s was not
  located in the target class %s"). `TargetClassContext` matches a shadow by name or alias and descriptor in the target
  class's own members.
- The ModLauncher provider is `MixinLaunchPluginLegacy`. `getClassNode(String)` reads the transformed class, and
  `getClassNode(name, false, flags)` throws ("does not currently support retrieval of untransformed bytecode"), so the
  plugin calls the one-argument form.

---

### Task 3: The Fan Catalyst

**Rows:** CD-FAN-01, CD-FAN-02, CD-FAN-03, CD-FAN-04, CD-FAN-10, CD-FAN-12, CD-FAN-14 (the Demo), CD-FAN-15, CD-FAN-16,
CD-SEC-10 (the fan part), CD-FX-02 (the three Fan Catalyst rows), CD-CFG-10 (off without Create), CD-REL-08 (the guide
section), CD-REL-09 (the checklist entries).

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/create/FanCatalystHandler.kt` (`FanCatalystHandler`,
  `FanItems`)
- Create: `src/main/resources/data/cobblemon_dynamics/cobblemon_dynamics/roles/fan_catalyst_fire.json`,
  `fan_catalyst_water.json`, `fan_catalyst_ghost.json`
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/handler/create/CreateCompat.kt` (registers the handler)
- Modify: `src/main/resources/assets/cobblemon_dynamics/lang/en_us.json`
- Modify: `docs/player-guide.md` (`### Fan Catalyst`), `docs/testing/manual-checklist.md` (checks for CD-FAN-14 and CD-FAN-16)
- Task 2 made the `ARCHITECTURE.md` fixes and the ADR-0011 amendment, in the commit that adds the mixin.
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/role/BuiltInRoleFilesTest.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/create/FanCatalystTests.kt`
- Test: `src/gametestCore/kotlin/codes/honor/cobblemondynamics/coretest/CoreTests.kt` (`createRoles` gains the three files)

**Interfaces:**
- Consumes: `TargetHandler.allowsSpot`, `noSpotRefusal`, `onClaim`, `onUnclaim`, `flipMessage` (Task 1), `FanState`,
  `FanCatalyst`, `FireMode`, `FanWorkers`, `AirCurrentAccess` (Task 2).
- Produces: `FanCatalystHandler` (id `cobblemon_dynamics:fan_catalyst`, `NO_ROOM = "cobblemon_dynamics.leash.no_room_in_front"`),
  `FanItems.any(fan: EncasedFanBlockEntity): Boolean`.

The three role files share the handler. Each names its catalyst in `params` and has the effect that the Role table gives its
type. `fan_catalyst_fire.json`:

```json
{
  "schema": 1,
  "handler": "cobblemon_dynamics:fan_catalyst",
  "types": ["fire"],
  "auto_assign": false,
  "priority": 0,
  "range": 6.0,
  "params": { "catalyst": "fire" },
  "effect": {
    "loop_ticks": 40,
    "idle_ticks": 40,
    "animations": ["firespin", "special"],
    "actor_particles": ["cobblemon:firespin_actorburst", "cobblemon:firespin_actor"],
    "target_particles": ["cobblemon:firespin_targetburst", "cobblemon:firespin_target"]
  }
}
```

`fan_catalyst_water.json` has `"types": ["water"]`, `"params": { "catalyst": "water" }`, the animations `["bubble",
"water", "spray", "special"]` and `"actor_particles": ["cobblemon:bubble_actor"]`, with no target particle.
`fan_catalyst_ghost.json` has `"types": ["ghost"]`, `"params": { "catalyst": "ghost" }`, the animations `["willowisp",
"fire", "special"]`, `"actor_particles": ["cobblemon:willowisp_actor"]` and `"target_particles": ["cobblemon:willowisp_target"]`.
Every other key is as in the Fire file.

- [ ] **Step 1: Failing unit tests** in `BuiltInRoleFilesTest`:

```kotlin
// roleTable gains:
"fan_catalyst_fire" to Row("cobblemon_dynamics:fan_catalyst", setOf("fire"), false, 0, "firespin"),
"fan_catalyst_water" to Row("cobblemon_dynamics:fan_catalyst", setOf("water"), false, 0, "bubble"),
"fan_catalyst_ghost" to Row("cobblemon_dynamics:fan_catalyst", setOf("ghost"), false, 0, "willowisp"),

@Test
fun `CD-FAN-01 Charmander, Squirtle and Gastly may be a Fan Catalyst, and Pikachu may not`() {
    val roles = listOf("fire", "water", "ghost").map { BuiltInRoles.parse("fan_catalyst_$it") }
    fun eligible(vararg types: String) = roles.any { RoleSelection.isEligible(it, types.toList()) }
    assertTrue(eligible("fire"), "Charmander")
    assertTrue(eligible("water"), "Squirtle")
    assertTrue(eligible("ghost", "poison"), "Gastly")
    assertFalse(eligible("electric"), "Pikachu")
}

@Test
fun `CD-FAN-02 every Fan Catalyst role file sets auto_assign to false`() {
    for (name in listOf("fan_catalyst_fire", "fan_catalyst_water", "fan_catalyst_ghost")) {
        val key = autoAssignKey(name)
        assertTrue(key != null && key.isJsonPrimitive && key.asJsonPrimitive.isBoolean && !key.asBoolean, "$name.json has auto_assign $key")
    }
}

/** Fire Spin and Will-O-Wisp split their particles as Cobblemon's timelines do. */
@Test
fun `CD-FX-04 the Fire and Ghost Fan Catalysts split their particles as their timelines do`() {
    for ((file, move) in listOf("fan_catalyst_fire" to "firespin", "fan_catalyst_ghost" to "willowisp")) {
        val effect = BuiltInRoles.parse(file).effect
        val timeline = MoveTimeline.read(move)
        assertEquals(timeline.animations, effect.animations, file)
        assertEquals(timeline.userParticles, effect.actorParticles.map { it.toString() }, file)
        assertEquals(timeline.targetParticles, effect.targetParticles.map { it.toString() }, file)
    }
}

/**
 * Bubble emits `bubble_actor` from the user inside a `sequence` step, which `MoveTimeline` does not read. Its target step
 * names `cobblemon:bubble_target`, which no particle file of Cobblemon 1.8.4 defines, so the role names no target particle.
 */
@Test
fun `CD-FX-04 the Water Fan Catalyst blows Bubble from the worker`() {
    val effect = BuiltInRoles.parse("fan_catalyst_water").effect
    assertEquals(MoveTimeline.read("bubble").animations, effect.animations)
    assertEquals(listOf("cobblemon:bubble_actor"), effect.actorParticles.map { it.toString() })
    assertEquals(emptyList<String>(), effect.targetParticles.map { it.toString() })
}
```

- [ ] **Step 2: Run them and see them fail:** the role files do not exist.

- [ ] **Step 3: Write the code.**

```kotlin
/**
 * The Fan Catalyst: a Fire-, Water- or Ghost-type stands in the block in front of a Create encased fan's face, where a catalyst
 * covers the whole air current (CD-FAN-03). It holds its catalyst on the fan's [FanState] while it Works, and the fan's worker
 * mark for its whole claim, so that the fan never pushes it off its spot on the way in (CD-FAN-12).
 */
object FanCatalystHandler : TargetHandler {
    const val NO_ROOM = "cobblemon_dynamics.leash.no_room_in_front"
    private const val CATALYST = "catalyst"
    private val warnings = OnceLogger()

    override val id: ResourceLocation = id("fan_catalyst")

    /** Leash only (CD-FAN-02, CD-ASG-05). */
    override val scanKind: ScanKind = ScanKind.NONE

    /** CD-FAN-04 */
    override val noSpotRefusal: String = NO_ROOM

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        blockEntity is EncasedFanBlockEntity

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = level.getBlockEntity(canonical) is EncasedFanBlockEntity

    /** CD-FAN-03: only the block in front of the fan's face. A fan that faces up or down has no such spot (CD-ASG-09). */
    override fun allowsSpot(level: ServerLevel, canonical: BlockPos, spot: BlockPos, bodyHeight: Double): Boolean =
        spot == front(level, canonical)

    override fun onClaim(ctx: WorkContext) = FanWorkers.mark(ctx.worker, ctx.target)

    override fun onUnclaim(ctx: WorkContext) = FanWorkers.clear(ctx.worker)

    /** CD-FAN-15: the fan spins, and its air current holds an item. */
    override fun hasDemand(ctx: WorkContext): Boolean {
        val fan = fanAt(ctx) ?: return false
        return fan.speed != 0f && FanItems.any(fan)
    }

    override fun onStart(ctx: WorkContext) = applyWork(ctx)

    /** Renews the catalyst on every Working tick, also on a fan whose block entity was replaced. */
    override fun applyWork(ctx: WorkContext) {
        val kind = kind(ctx) ?: return
        fanAt(ctx)?.let(FanState::of)?.grant(kind, ctx.now)
    }

    override fun onStop(ctx: WorkContext) {
        if (ctx.level.isLoaded(ctx.target)) fanAt(ctx)?.let(FanState::of)?.withdraw()
    }

    /** CD-FAN-10: only a driven fan, one with a Working catalyst, switches its mode. */
    override fun flip(level: ServerLevel, canonical: BlockPos): Boolean {
        val state = FanState.of(level.getBlockEntity(canonical))?.takeIf { it.catalyst != null } ?: return false
        state.flipMode()
        return true
    }

    /** CD-FAN-16: "Fan mode: Blasting" or "Fan mode: Smoking". */
    override fun flipMessage(level: ServerLevel, canonical: BlockPos): Component? =
        FanState.of(level.getBlockEntity(canonical))?.let { Component.translatable(it.fireMode.langKey) }

    private fun fanAt(ctx: WorkContext): EncasedFanBlockEntity? = ctx.level.getBlockEntity(ctx.target) as? EncasedFanBlockEntity

    private fun front(level: ServerLevel, canonical: BlockPos): BlockPos? = (level.getBlockEntity(canonical) as? EncasedFanBlockEntity)
        ?.let { canonical.relative(it.blockState.getValue(EncasedFanBlock.FACING)) }

    private fun kind(ctx: WorkContext): FanCatalyst? {
        val name = ctx.role.params.get(CATALYST)?.takeIf { it.isJsonPrimitive }?.asString
        return FanCatalyst.parse(name) ?: null.also {
            warnings.warn(ctx.role.id) { "Role ${ctx.role.id} names no fan catalyst (fire, water or ghost), so its workers process nothing" }
        }
    }
}

object FanItems {
    /** Whether [fan]'s air current holds an item: an item entity that it caught, or an item on a belt or a depot under it. */
    fun any(fan: EncasedFanBlockEntity): Boolean {
        val current = (fan.airCurrent as Any) as? AirCurrentAccess ?: return false
        if (current.`cobblemon_dynamics$caughtEntities`().any { it is ItemEntity && it.isAlive }) return true
        return current.`cobblemon_dynamics$affectedHandlers`().any { holdsItem(it.left) }
    }

    private fun holdsItem(handler: TransportedItemStackHandlerBehaviour): Boolean {
        var found = false
        handler.handleProcessingOnAllItems {
            found = true
            TransportedItemStackHandlerBehaviour.TransportedResult.doNothing()
        }
        return found
    }
}
```

  Register the handler in `CreateCompat.register`. Lang key (Task 2 added the two `cobblemon_dynamics.fan.mode` keys, which
  `FireMode` names):

```json
"cobblemon_dynamics.leash.no_room_in_front": "No room in front of the fan",
```

- [ ] **Step 4: Run the unit tests and see them pass.**

- [ ] **Step 5: GameTests** in `create/FanCatalystTests` (a real worker in a fan's current, so rule 6 keeps them). Each
  places a Work Mode pasture at (11, 1, 5) with the owner beside it, and fans at x = 3, facing east, over a stone floor.
  The pasture is within 8 blocks of every fan, and no fan's current crosses it:

```kotlin
/**
 * A Charmander bound to a fan that faces east takes the block east of the fan as its spot and Works while 64 raw iron sit on a
 * depot downstream (CD-FAN-03). With the motor stopped, it waits in Standby within 20 ticks. With the motor on again and the
 * depot emptied, it waits in Standby within 20 ticks too (CD-FAN-15).
 */
@GameTest(template = "floor_15x6x15", timeoutTicks = 1000)
fun cdFan03Fan15TheWorkerStandsInFrontAndWaitsWithNoWork(helper: GameTestHelper) {
    val fanRel = BlockPos(3, 2, 7)
    CreateKit.fan(helper, fanRel, Direction.EAST, rpm = 64)
    val motor = helper.getBlockEntity<CreativeMotorBlockEntity>(fanRel.west())
    val depot = CreateKit.depot(helper, BlockPos(6, 1, 7), ItemStack(Items.RAW_IRON, 64))
    val scene = scene(helper)
    val charmander = TestKit.tether(helper, scene.pasture, scene.owner, "charmander level=20")
    fun status() = TestKit.workerOf(helper, charmander)?.status
    var since = 0L
    helper.startSequence()
        .thenIdle(1)
        .thenExecute { TestKit.bind(helper, scene.owner, charmander, fanRel) ?: throw GameTestAssertException("no binding") }
        .thenWaitUntil { helper.assertTrue(status() == WorkerStatus.WORKING, "the Charmander is ${status()}") }
        .thenExecute {
            val spot = TestKit.workerOf(helper, charmander)?.spot
            helper.assertTrue(spot == helper.absolutePos(fanRel.east()), "CD-FAN-03: the spot is $spot")
            CreateKit.setRpm(motor, 0)
            since = helper.level.gameTime
        }
        .thenWaitUntil {
            helper.assertTrue(status() == WorkerStatus.STANDBY, "the Charmander is ${status()} with the fan stopped")
            helper.assertTrue(helper.level.gameTime - since <= 20, "CD-FAN-15: no Standby within 20 ticks of the stop")
        }
        .thenExecute { CreateKit.setRpm(motor, 64) }
        .thenWaitUntil { helper.assertTrue(status() == WorkerStatus.WORKING, "the Charmander is ${status()} with the fan on again") }
        .thenExecute {
            depot.setHeldItem(ItemStack.EMPTY)
            since = helper.level.gameTime
        }
        .thenWaitUntil {
            helper.assertTrue(status() == WorkerStatus.STANDBY, "the Charmander is ${status()} with no item in the current")
            helper.assertTrue(helper.level.gameTime - since <= 20, "CD-FAN-15: no Standby within 20 ticks of the last item")
        }
        .thenSucceed()
}

/**
 * The binder sneak-clicks a worked fan twice: smoking, then blasting, each with its action-bar message (CD-FAN-10, CD-FAN-16).
 * With the claim released, the binder's sneak-click on the fan that no worker drives changes nothing (CD-SEC-10).
 */
@GameTest(template = "floor_15x6x15", timeoutTicks = 600)
fun cdFan10Fan16Sec10TheBinderFlipsOnlyADrivenFan(helper: GameTestHelper) {
    val fanRel = BlockPos(3, 2, 7)
    val fan = CreateKit.fan(helper, fanRel, Direction.EAST, rpm = 64)
    CreateKit.depot(helper, BlockPos(6, 1, 7), ItemStack(Items.RAW_IRON, 64))
    val scene = scene(helper)
    val charmander = TestKit.tether(helper, scene.pasture, scene.owner, "charmander level=20")
    fun mode() = FanState.of(fan)?.fireMode
    fun flip() {
        TestKit.leash(scene.owner)
        TestKit.clickBlock(helper, scene.owner, fanRel, sneak = true)
    }
    helper.startSequence()
        .thenIdle(1)
        .thenExecute { TestKit.bind(helper, scene.owner, charmander, fanRel) }
        .thenWaitUntil { helper.assertTrue(TestKit.workerOf(helper, charmander)?.status == WorkerStatus.WORKING, "not Working") }
        .thenExecute {
            flip()
            helper.assertTrue(mode() == FireMode.SMOKING, "CD-FAN-10: the first flip left ${mode()}")
            helper.assertTrue(TestKit.lastMessage(scene.owner) == "Fan mode: Smoking", "CD-FAN-16: ${TestKit.lastMessage(scene.owner)}")
            flip()
            helper.assertTrue(mode() == FireMode.BLASTING, "CD-FAN-10: the second flip left ${mode()}")
            helper.assertTrue(TestKit.lastMessage(scene.owner) == "Fan mode: Blasting", "CD-FAN-16: ${TestKit.lastMessage(scene.owner)}")
            TestKit.release(helper, charmander, helper.absolutePos(fanRel))
        }
        .thenIdle(3)
        .thenExecute {
            flip()
            helper.assertTrue(mode() == FireMode.BLASTING, "CD-SEC-10: a fan that no worker drives flipped to ${mode()}")
        }
        .thenSucceed()
}

/**
 * Three fans at 256 RPM, with a bound Charmander, Squirtle and Gastly, each with 64 items on a depot downstream. For 1,200
 * ticks of their claims, no worker moves more than 0.5 blocks from its spot, takes damage, burns, or gains Blindness or
 * Slowness (CD-FAN-12).
 */
@GameTest(template = "floor_15x6x15", timeoutTicks = 1800)
fun cdFan12ThreeWorkersStayPutAt256Rpm(helper: GameTestHelper) {
    val scene = scene(helper)
    val workers = listOf(
        Triple("charmander level=20", 3, ItemStack(Items.RAW_IRON, 64)),
        Triple("squirtle level=20", 7, ItemStack(Items.RED_WOOL, 64)),
        Triple("gastly level=20", 11, ItemStack(Items.SAND, 64)),
    ).map { (spec, z, stack) ->
        CreateKit.fan(helper, BlockPos(3, 2, z), Direction.EAST, rpm = 256)
        CreateKit.depot(helper, BlockPos(6, 1, z), stack)
        BlockPos(3, 2, z) to TestKit.tether(helper, scene.pasture, scene.owner, spec)
    }
    helper.runAfterDelay(1) { for ((fanRel, worker) in workers) TestKit.bind(helper, scene.owner, worker, fanRel) }
    helper.onEachTick {
        for ((fanRel, worker) in workers) {
            val spot = TestKit.workerOf(helper, worker)?.takeIf { it.status == WorkerStatus.WORKING || it.status == WorkerStatus.STANDBY }
                ?.spot ?: continue
            val name = worker.pokemon.species.name
            helper.assertTrue(spot == helper.absolutePos(fanRel.east()), "$name has the spot $spot")
            helper.assertTrue(worker.position().distanceTo(Vec3.atBottomCenterOf(spot)) <= 0.5, "CD-FAN-12: the fan moved $name")
            helper.assertTrue(worker.health >= worker.maxHealth && worker.remainingFireTicks <= 0, "CD-FAN-12: $name took damage or burns")
            val effects = worker.hasEffect(MobEffects.BLINDNESS) || worker.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
            helper.assertFalse(effects, "CD-FAN-12: $name gained Blindness or Slowness")
        }
    }
    helper.runAfterDelay(1300) { helper.succeed() }
}
```

  `scene(helper)` returns the owner and the pasture. Deferred:
  - CD-FAN-04 (`cdFan04StoneInFrontRefusesTheBind`: stone in front of the fan, then a bind with "No room in front of the
    fan" and no binding): GameTest deferred (high confidence): verified in the end-of-phase pass. `BindRulesTest` holds the
    rule, and `hasSpot` runs the claim's own spot search.
  - CD-FAN-14 is a Demo: manual checklist, below. `FanNbtTest` checks what the client packet carries.

  `CoreTests.createRoles` gains `fan_catalyst_fire`, `fan_catalyst_water` and `fan_catalyst_ghost` (CD-CFG-10).

- [ ] **Step 6: Docs.**
  - The guide: `### Fan Catalyst` under `## Roles`, with one subsection for each file, so that `PlayerGuideTest` finds
    the headings `Fan Catalyst Fire`, `Fan Catalyst Water` and `Fan Catalyst Ghost`:

```markdown
### Fan Catalyst

A Fire-, Water- or Ghost-type stands in the block in front of a Create encased fan and takes the place of the lava, the
water or the soul fire there. The fan then processes the items in its air current as Create does, at Create's own speed.
The Fan Catalyst is leash only: select the Pokémon with the Work Leash, then right-click the fan. The block in front of
the fan must hold the Pokémon, so a fan that faces up or down cannot take one ("No room in front of the fan"). The fan
does not push, burn or harm its own worker. A worker waits in Standby while the fan stands still or no item is in its
air current.

#### Fan Catalyst Fire

Blasting by default. Sneak and right-click the worked fan with the Work Leash to switch it to smoking, and again to switch
it back. The action bar shows the new mode. Each fan keeps its own mode, also after a restart.

#### Fan Catalyst Water

Washing, as Create's bulk washing does.

#### Fan Catalyst Ghost

Haunting, as Create's bulk haunting does.

A Pokémon with two of these types takes the role whose name sorts first: Fire, then Ghost, then Water.

Role file parameter: `catalyst` (`fire`, `water` or `ghost`).
```

  - The manual checklist, after the last check:

```markdown
44. **CD-FAN-14.** Setup: three fans at 64 RPM in a row, with a bound Charmander, Squirtle and Gastly, each with items on
    a depot in its air current. Action: watch the air of each fan. Expected: flame particles in the Charmander's current,
    water drops in the Squirtle's and soul particles in the Gastly's, as Create shows for lava, water and soul fire.
45. **CD-FAN-16.** Setup: a Charmander Working at a fan. Action: sneak and right-click the fan with the Work Leash twice.
    Expected: the action bar shows "Fan mode: Smoking", then "Fan mode: Blasting".
```

  - Task 2 already fixed ARCHITECTURE sections 2, 4 and 8 and added the amendment at the end of ADR-0011: Phase 2 adds two
    Create mixins, the air current's `tickAffectedEntities` and the windmill speed. The fan worker mark lasts for the
    worker's claim. A nozzle fills the block in front of its own fan, so no nozzle mixin is needed.

- [ ] **Step 7: Run** `./gradlew build`, `./gradlew runGameTestServer` and `./gradlew runGameTestServerCore`.

- [ ] **Step 8: Multiplayer check.** The flip goes through `FlipRules`. The mark syncs to every tracking client, so every
  client leaves the worker in place. The bind refusal and the mode message go only to the clicking player.

- [ ] **Step 9: Commit:** `git commit -m "Let leashed Fire-, Water- and Ghost-types act as catalysts for Create fans [rag:2]"`.

---

### Task 4: Tailwind

**Rows:** CD-TLW-01, CD-TLW-02, CD-TLW-03, CD-TLW-04, CD-TLW-05, CD-TLW-06, CD-TLW-07, CD-TLW-08, CD-TLW-09, CD-TLW-10,
CD-BAL-04 (the windmill part), CD-FX-02 (the Tailwind row), CD-CFG-10 (off without Create), CD-REL-08, CD-REL-09.

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/create/TailwindMath.kt` (`TailwindMath`, `TailwindNbt`,
  `TailwindParams`: no Create class)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/create/TailwindBonus.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/create/TailwindHandler.kt`
- Create: `src/main/java/codes/honor/cobblemondynamics/mixin/create/WindmillBearingBlockEntityMixin.java`
- Create: `src/main/resources/data/cobblemon_dynamics/cobblemon_dynamics/roles/tailwind.json`
- Modify: `MixinState.java` (`WINDMILL_BEARING`), `cobblemon_dynamics.mixins.json`, `role/MixinGate.kt`, `handler/create/CreateCompat.kt`
  (the behaviour, the class literal, the handler)
- Modify: `docs/player-guide.md` (`### Tailwind`), `docs/testing/manual-checklist.md` (CD-TLW-07), `ARCHITECTURE.md`
  (section 8: the renewal and the spot rule)
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/handler/create/TailwindMathTest.kt`, `TailwindNbtTest.kt`,
  `src/test/kotlin/codes/honor/cobblemondynamics/role/MixinGateTest.kt`, `BuiltInRoleFilesTest.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/create/TailwindTests.kt`, `CoreTests.kt` (`createRoles`
  gains `tailwind`)

**Interfaces:**
- Consumes: `TargetHandler.allowsSpot` (Task 1).
- Produces:

```kotlin
object TailwindMath {
    /** floor(S × L × perLevel) virtual sails, with L clamped to 1..100, so that S real and virtual sails stay within [maxTotal] (CD-TLW-03, CD-TLW-04). */
    fun bonus(realSails: Int, level: Int, perLevel: Double, maxTotal: Int): Int
    /** Create's windmill speed for [sails] sails: sails ÷ [sailsPerRpm] RPM, at least 1 and at most 16. */
    fun rpm(sails: Int, sailsPerRpm: Int = 8): Int
    /** A Tailwind spot keeps the worker's body out of the turning sails: beside or behind the bearing's face. */
    fun clearOfSails(bearing: BlockPos, facing: Direction, spot: BlockPos, bodyHeight: Double): Boolean
}

object TailwindNbt {
    const val SAILS = "cd_bonus_sails"
    /** The bonus goes only into client packets (CD-TLW-07). A save never holds it, because claims are never saved. */
    fun write(tag: CompoundTag, sails: Int, clientPacket: Boolean)
    fun sails(tag: CompoundTag, clientPacket: Boolean, current: Int): Int
}

/** `bonus_per_level_of_real_sails` (default 0.01) and `max_total_sails` (default 128), as ARCHITECTURE section 9 gives them. */
data class TailwindParams(val perLevel: Double, val maxTotalSails: Int) { companion object { fun of(params: JsonObject): TailwindParams } }

class TailwindBonus(be: SmartBlockEntity) : BlockEntityBehaviour(be) {
    val sails: Int
    fun grant(sails: Int, now: Long)
    fun withdraw()
    companion object {
        val TYPE: BehaviourType<TailwindBonus>
        /** What the windmill mixin counts: Create's real sails plus the bonus. It never lowers Create's own count. */
        @JvmStatic fun totalSails(bearing: WindmillBearingBlockEntity, realSails: Int): Int
    }
}
```

`tailwind.json`:

```json
{
  "schema": 1,
  "handler": "cobblemon_dynamics:tailwind",
  "types": ["flying"],
  "auto_assign": false,
  "priority": 0,
  "range": 6.0,
  "params": {
    "bonus_per_level_of_real_sails": 0.01,
    "max_total_sails": 128
  },
  "effect": {
    "loop_ticks": 40,
    "idle_ticks": 40,
    "animations": ["gust", "flying", "special"],
    "actor_particles": ["cobblemon:gust_actor"],
    "target_particles": ["cobblemon:gust_target", "cobblemon:gust_targethit"]
  }
}
```

- [ ] **Step 1: Failing unit tests.**

```kotlin
class TailwindMathTest {
    @Test
    fun `CD-TLW-03 the bonus is floor of real sails times level over 100`() {
        assertEquals(32, TailwindMath.bonus(64, 50, 0.01, 128))
        assertEquals(12, TailwindMath.rpm(64 + 32))
        assertEquals(8, TailwindMath.bonus(8, 100, 0.01, 128))
        assertEquals(2, TailwindMath.rpm(8 + 8))
        assertEquals(64, TailwindMath.bonus(64, 100, 0.01, 128))
        assertEquals(16, TailwindMath.rpm(64 + 64))
    }

    @Test
    fun `CD-TLW-04 real and virtual sails stay within 128`() {
        assertEquals(128, 100 + TailwindMath.bonus(100, 100, 0.01, 128))
        assertEquals(127, 96 + TailwindMath.bonus(96, 33, 0.01, 128))
        assertEquals(0, TailwindMath.bonus(200, 100, 0.01, 128))
    }

    @Test
    fun `CD-TLW-03 the bonus is exact for every sail count and level, with no floating-point slip`() {
        for (sails in 8..128) for (level in 1..100) {
            assertEquals(minOf(sails * level / 100, maxOf(0, 128 - sails)), TailwindMath.bonus(sails, level, 0.01, 128), "S=$sails L=$level")
        }
    }

    @Test
    fun `a level above 100 adds no more than level 100`() =
        assertEquals(TailwindMath.bonus(40, 100, 0.01, 128), TailwindMath.bonus(40, 150, 0.01, 128))

    @Test
    fun `a Tailwind spot stays beside or behind the bearing's face`() {
        val bearing = BlockPos(0, 64, 0)
        assertTrue(TailwindMath.clearOfSails(bearing, Direction.EAST, BlockPos(0, 64, 1), 1.8))
        assertTrue(TailwindMath.clearOfSails(bearing, Direction.EAST, BlockPos(-1, 64, 0), 1.8))
        assertFalse(TailwindMath.clearOfSails(bearing, Direction.EAST, BlockPos(1, 64, 1), 0.5))
        assertTrue(TailwindMath.clearOfSails(bearing, Direction.UP, BlockPos(1, 64, 0), 0.7))
        assertFalse(TailwindMath.clearOfSails(bearing, Direction.UP, BlockPos(1, 64, 0), 1.5))
        assertTrue(TailwindMath.clearOfSails(bearing, Direction.DOWN, BlockPos(1, 64, 0), 1.5))
        assertFalse(TailwindMath.clearOfSails(bearing, Direction.DOWN, BlockPos(1, 63, 0), 0.5))
    }
}

class TailwindNbtTest {
    @Test
    fun `CD-TLW-07 the bonus goes only into client packets`() {
        val save = CompoundTag().also { TailwindNbt.write(it, 32, clientPacket = false) }
        assertFalse(save.contains(TailwindNbt.SAILS))
        assertEquals(5, TailwindNbt.sails(save, clientPacket = false, current = 5))
        val packet = CompoundTag().also { TailwindNbt.write(it, 32, clientPacket = true) }
        assertEquals(32, TailwindNbt.sails(packet, clientPacket = true, current = 0))
    }
}

// MixinGateTest
@Test
fun `CD-TLW-10 a missing windmill mixin disables Tailwind with one warning`() {
    val tailwind = role("tailwind", setOf("flying"), autoAssign = false, handler = "cobblemon_dynamics:tailwind")
    val set = RoleSet(mapOf(tailwind.id to tailwind), { FakeHandler(it) }, emptySet()) { }
    val warnings = mutableListOf<String>()
    val gated = MixinGate.apply(set, applied = { it != MixinState.WINDMILL_BEARING }, warn = warnings::add)
    assertTrue(gated.active.isEmpty())
    assertEquals(1, warnings.size)
}

// BuiltInRoleFilesTest
// roleTable gains:
"tailwind" to Row("cobblemon_dynamics:tailwind", setOf("flying"), false, 0, "gust"),

@Test
fun `CD-TLW-01 Pidgey may be a Tailwind worker, and Pikachu may not`() {
    val tailwind = BuiltInRoles.parse("tailwind")
    assertTrue(RoleSelection.isEligible(tailwind, listOf("normal", "flying")), "Pidgey")
    assertFalse(RoleSelection.isEligible(tailwind, listOf("electric")), "Pikachu")
}

@Test
fun `CD-TLW-02 the Tailwind role file sets auto_assign to false`() {
    val key = autoAssignKey("tailwind")
    assertTrue(key != null && key.isJsonPrimitive && key.asJsonPrimitive.isBoolean && !key.asBoolean, "tailwind.json has auto_assign $key")
}

/** Cobblemon's `gust` timeline sends the gust from the user, and its two hits land on the target. */
@Test
fun `CD-FX-04 Tailwind's Gust blows from the worker and hits the bearing`() {
    val effect = BuiltInRoles.parse("tailwind").effect
    val gust = MoveTimeline.read("gust")
    assertEquals(gust.animations, effect.animations)
    assertEquals(gust.userParticles, effect.actorParticles.map { it.toString() })
    assertEquals(gust.targetParticles, effect.targetParticles.map { it.toString() })
}
```

- [ ] **Step 2: Run them and see them fail** to compile.

- [ ] **Step 3: Write the code.**

```kotlin
object TailwindMath {
    private const val EPSILON = 1e-6

    fun bonus(realSails: Int, level: Int, perLevel: Double, maxTotal: Int): Int {
        if (realSails <= 0 || perLevel <= 0.0) return 0
        val raw = floor(realSails.toDouble() * level.coerceIn(1, 100) * perLevel + EPSILON).toLong()
        return raw.coerceAtMost((maxTotal - realSails).toLong()).coerceAtLeast(0).toInt()
    }

    fun rpm(sails: Int, sailsPerRpm: Int = 8): Int = (sails / sailsPerRpm).coerceIn(1, 16)

    fun clearOfSails(bearing: BlockPos, facing: Direction, spot: BlockPos, bodyHeight: Double): Boolean = when (facing) {
        Direction.UP -> spot.y + bodyHeight <= bearing.y + 1 + EPSILON
        Direction.DOWN -> spot.y >= bearing.y
        else -> (spot.x - bearing.x) * facing.stepX + (spot.z - bearing.z) * facing.stepZ <= 0
    }
}
```

```kotlin
class TailwindBonus(be: SmartBlockEntity) : BlockEntityBehaviour(be) {
    var sails: Int = 0
        private set

    /** The last tick that the bonus holds with no renewal. Server only. */
    private var until = Long.MIN_VALUE

    /** For this tick and the next. The handler renews it on every Working tick, so a missed release lapses within 2 ticks (CD-TLW-09). */
    fun grant(sails: Int, now: Long) {
        until = now + 1
        set(sails)
    }

    fun withdraw() {
        until = Long.MIN_VALUE
        set(0)
    }

    override fun tick() {
        val level = blockEntity.level ?: return
        if (!level.isClientSide && sails != 0 && level.gameTime > until) withdraw()
    }

    /** CD-TLW-06: the network takes the new speed on this server tick, and every tracking client gets the bonus (CD-TLW-07). */
    private fun set(value: Int) {
        if (sails == value) return
        sails = value
        val bearing = blockEntity as? WindmillBearingBlockEntity ?: return
        if (bearing.level?.isClientSide == false) bearing.updateGeneratedRotation()
        bearing.sendData()
    }

    override fun getType(): BehaviourType<*> = TYPE

    override fun write(nbt: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) = TailwindNbt.write(nbt, sails, clientPacket)

    override fun read(nbt: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        sails = TailwindNbt.sails(nbt, clientPacket, sails)
    }

    companion object {
        @JvmField
        val TYPE = BehaviourType<TailwindBonus>("cobblemon_dynamics:tailwind")

        @JvmStatic
        fun totalSails(bearing: WindmillBearingBlockEntity, realSails: Int): Int = realSails + (BlockEntityBehaviour.get(bearing, TYPE)?.sails ?: 0)
    }
}
```

```java
@Mixin(WindmillBearingBlockEntity.class)
public abstract class WindmillBearingBlockEntityMixin {
    /**
     * A Working Tailwind worker's virtual sails count toward the speed, on both sides. Assembly reads the sail field itself, so
     * they never help a windmill assemble (CD-TLW-05), and Create's own clamp keeps the speed at 16 RPM or less (CD-BAL-04).
     */
    @ModifyExpressionValue(method = "getGeneratedSpeed()F", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/contraptions/bearing/BearingContraption;getSailBlocks()I"))
    private int cobblemon_dynamics$virtualSails(int sails) {
        return TailwindBonus.totalSails((WindmillBearingBlockEntity) (Object) this, sails);
    }
}
```

```kotlin
/** Tailwind: a Flying-type adds virtual sails to a Create windmill while it Works, recomputed on every tick (CD-TLW-03). */
object TailwindHandler : TargetHandler {
    override val id: ResourceLocation = id("tailwind")

    /** Leash only (CD-TLW-02, CD-ASG-05). */
    override val scanKind: ScanKind = ScanKind.NONE

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        blockEntity is WindmillBearingBlockEntity

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = level.getBlockEntity(canonical) is WindmillBearingBlockEntity

    override fun allowsSpot(level: ServerLevel, canonical: BlockPos, spot: BlockPos, bodyHeight: Double): Boolean {
        val facing = level.getBlockState(canonical).takeIf { it.hasProperty(BearingBlock.FACING) }?.getValue(BearingBlock.FACING) ?: return false
        return TailwindMath.clearOfSails(canonical, facing, spot, bodyHeight)
    }

    /** CD-TLW-08: the windmill is assembled and turns. A running windmill always gives at least 1 RPM. */
    override fun hasDemand(ctx: WorkContext): Boolean {
        val bearing = bearingAt(ctx) ?: return false
        return bearing.isRunning && bearing.movedContraption != null
    }

    override fun onStart(ctx: WorkContext) = applyWork(ctx)

    /** CD-TLW-03, CD-TLW-04, CD-TLW-06: more real sails or a new level count on the next tick. */
    override fun applyWork(ctx: WorkContext) {
        val bearing = bearingAt(ctx) ?: return
        val contraption = bearing.movedContraption?.contraption as? BearingContraption ?: return
        val params = TailwindParams.of(ctx.role.params)
        val bonus = TailwindMath.bonus(contraption.sailBlocks, ctx.pokemon.level, params.perLevel, params.maxTotalSails)
        BlockEntityBehaviour.get(bearing, TailwindBonus.TYPE)?.grant(bonus, ctx.now)
    }

    /** CD-TLW-09 */
    override fun onStop(ctx: WorkContext) {
        if (ctx.level.isLoaded(ctx.target)) bearingAt(ctx)?.let { BlockEntityBehaviour.get(it, TailwindBonus.TYPE) }?.withdraw()
    }

    private fun bearingAt(ctx: WorkContext): WindmillBearingBlockEntity? = ctx.level.getBlockEntity(ctx.target) as? WindmillBearingBlockEntity
}
```

  `MixinState` gains `WINDMILL_BEARING = "create.WindmillBearingBlockEntityMixin"`, `MixinGate.REQUIRED` gains
  `id("tailwind") to MixinState.WINDMILL_BEARING`, and `CreateCompat` adds `WindmillBearingBlockEntity::class.java` to its
  mixin targets, attaches `TailwindBonus` to `AllBlockEntityTypes.WINDMILL_BEARING` and registers `TailwindHandler`.

- [ ] **Step 4: Run the unit tests and see them pass.**

- [ ] **Step 5: GameTests** in `create/TailwindTests` (Create's windmill, its network and the mixin, so rule 6 keeps them).
  Each places a Work Mode pasture at (7, 1, 2) with the owner beside it, and the bearing at `bearingRel` = (7, 2, 9):

```kotlin
/**
 * A bearing with 7 real sails and a bound Lv100 Pidgey: the owner's empty-hand click does not assemble it (CD-TLW-05). With an
 * 8th real sail, the same click does, which shows that the click reaches the bearing.
 */
@GameTest(template = "floor_15x6x15", timeoutTicks = 300)
fun cdTlw05SevenSailsNeverAssemble(helper: GameTestHelper) {
    val bearing = CreateKit.windmill(helper, bearingRel, sails = 7)
    val scene = scene(helper)
    val pidgey = TestKit.tether(helper, scene.pasture, scene.owner, "pidgey level=100")
    fun click() {
        scene.owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
        TestKit.clickBlock(helper, scene.owner, bearingRel)
    }
    helper.startSequence()
        .thenIdle(1)
        .thenExecute {
            helper.assertTrue(TestKit.bind(helper, scene.owner, pidgey, bearingRel) != null, "the bind to the bearing was refused")
            click()
        }
        .thenIdle(20)
        .thenExecute {
            helper.assertFalse(bearing.isRunning, "CD-TLW-05: the windmill assembled with 7 real sails")
            helper.setBlock(bearingRel.offset(0, 1, 1), AllBlocks.SAIL.defaultState.setValue(SailBlock.FACING, Direction.UP))
            click()
        }
        .thenIdle(20)
        .thenExecute { helper.assertTrue(bearing.isRunning, "the click did not assemble the windmill with 8 real sails") }
        .thenSucceed()
}

/**
 * A 64-sail windmill turns its shaft at 8 RPM. A Lv50 Pidgey adds 32 virtual sails, so 2 ticks after it starts to Work the
 * shaft turns at 12 RPM (CD-TLW-03, CD-TLW-06). Releasing the claim takes the sails away: 8 RPM 2 ticks later (CD-TLW-09).
 * Working again after the release's 200-tick skip, the Pidgey waits in Standby within 20 ticks of the disassembly (CD-TLW-08).
 */
@GameTest(template = "floor_15x6x15", timeoutTicks = 1000)
fun cdTlw06Tlw09Tlw08TheBonusFollowsTheWork(helper: GameTestHelper) {
    val bearing = CreateKit.windmill(helper, bearingRel, sails = 64)
    val scene = scene(helper)
    val pidgey = TestKit.tether(helper, scene.pasture, scene.owner, "pidgey level=50")
    fun rpm(): Float = abs(helper.getBlockEntity<KineticBlockEntity>(bearingRel.below()).speed)
    fun status() = TestKit.workerOf(helper, pidgey)?.status
    var since = 0L
    helper.startSequence()
        .thenIdle(1)
        .thenExecute { bearing.assemble() }
        .thenWaitUntil { helper.assertTrue(rpm() == 8f, "the 64-sail windmill turns at ${rpm()} RPM") }
        .thenExecute { TestKit.bind(helper, scene.owner, pidgey, bearingRel) ?: throw GameTestAssertException("no binding") }
        .thenWaitUntil { helper.assertTrue(status() == WorkerStatus.WORKING, "the Pidgey is ${status()}") }
        .thenIdle(2)
        .thenExecute {
            helper.assertTrue(rpm() == 12f, "CD-TLW-06: ${rpm()} RPM 2 ticks after the Pidgey started to Work")
            TestKit.release(helper, pidgey, helper.absolutePos(bearingRel))
        }
        .thenIdle(2)
        .thenExecute { helper.assertTrue(rpm() == 8f, "CD-TLW-09: ${rpm()} RPM 2 ticks after the release") }
        .thenWaitUntil { helper.assertTrue(status() == WorkerStatus.WORKING, "the Pidgey is ${status()} after the skip") }
        .thenExecute {
            bearing.disassemble()
            since = helper.level.gameTime
        }
        .thenWaitUntil {
            helper.assertTrue(status() == WorkerStatus.STANDBY, "the Pidgey is ${status()} at a disassembled windmill")
            helper.assertTrue(helper.level.gameTime - since <= 20, "CD-TLW-08: no Standby within 20 ticks")
        }
        .thenSucceed()
}

/** 100 real sails and a Lv100 Pidgey: a bonus of 28, 128 sails and 16 RPM, never more over 200 ticks (CD-TLW-04, CD-BAL-04). */
@GameTest(template = "floor_15x6x15", timeoutTicks = 800)
fun cdBal04Tlw04TheWindmillNeverPassesSixteenRpm(helper: GameTestHelper) {
    val bearing = CreateKit.windmill(helper, bearingRel, sails = 100)
    val scene = scene(helper)
    val pidgey = TestKit.tether(helper, scene.pasture, scene.owner, "pidgey level=100")
    fun rpm(): Float = abs(helper.getBlockEntity<KineticBlockEntity>(bearingRel.below()).speed)
    helper.startSequence()
        .thenIdle(1)
        .thenExecute { bearing.assemble() }
        .thenExecute { TestKit.bind(helper, scene.owner, pidgey, bearingRel) }
        .thenWaitUntil { helper.assertTrue(TestKit.workerOf(helper, pidgey)?.status == WorkerStatus.WORKING, "not Working") }
        .thenExecuteFor(200) { helper.assertTrue(rpm() <= 16f, "CD-BAL-04: the windmill turns at ${rpm()} RPM") }
        .thenExecute {
            helper.assertTrue(BlockEntityBehaviour.get(bearing, TailwindBonus.TYPE)?.sails == 28, "CD-TLW-04: the bonus is not 28")
            helper.assertTrue(rpm() == 16f, "the worked windmill turns at ${rpm()} RPM, not 16")
        }
        .thenSucceed()
}
```

  `CoreTests.createRoles` gains `tailwind` (CD-CFG-10). CD-TLW-07 is a Demo: manual checklist, below.

- [ ] **Step 6: Docs.** The guide section and the checklist entry:

```markdown
### Tailwind

A Flying-type adds wind to a Create windmill. Tailwind is leash only: select the Pokémon with the Work Leash, then
right-click the windmill bearing. While the worker Works, the windmill counts floor(real sails × level ÷ 100) more sails,
and real and added sails together stay at 128 or less. So a 64-sail windmill turns at 12 RPM with a level 50 worker, and
at 16 RPM, Create's maximum, with a level 100 worker. A windmill still needs 8 real sails to assemble. The worker stands
beside or behind the bearing, never where the sails turn. Under a windmill that faces up, the worker needs room below
the sails, so assemble the windmill before you bind. A worker waits in Standby while the windmill is not assembled.

Role file parameters: `bonus_per_level_of_real_sails` (0.01) and `max_total_sails` (128).
```

```markdown
46. **CD-TLW-07.** Setup: a 64-sail windmill with a bound level 50 Flying-type that Works. Action: watch the sails for 30
    seconds. Expected: the sails turn smoothly at the faster speed, and they never snap back.
```

  ARCHITECTURE section 8, Tailwind: the bonus lapses 2 ticks after its last renewal, and a spot stays clear of the sails.

- [ ] **Step 7: Run** `./gradlew build`, then `./gradlew runGameTestServer` and `./gradlew runGameTestServerCore` twice in
  a row.

- [ ] **Step 8: Multiplayer check.** The bonus syncs to every tracking client through `sendData()`, and the mixin reads it
  on both sides, so every client spins the windmill at the server's speed. The deadline is a field of the behaviour, not
  static state.

- [ ] **Step 9: Commit, push, open the pull request.** `git commit -m "Add virtual sails to Create windmills with leashed Flying-types [rag:2]"`.
  Then follow the rehydration note's pull request steps (HAR-809), with `py scripts/check_plan_coverage.py --phase 2` among
  the checks. The body lists the deferred rows of Tasks 2 and 3, the corrections to ARCHITECTURE and ADR-0011, and every
  deviation from this file.
