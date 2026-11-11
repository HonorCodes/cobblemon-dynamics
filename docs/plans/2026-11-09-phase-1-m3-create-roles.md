# M3: Create roles

Part of the [Phase 1 plan](2026-11-09-phase-1-plan.md). Read its Global Constraints first. Branch:
`m3-create-roles`. Builds on M1 and M2.

**Milestone goal:** Water-types turn dry water wheels, Fighting- and Normal-types turn hand cranks,
Dragon-types keep Blaze Burners kindled below level 50 and superheat them from level 50, each at
Create's own values, and each role switches itself off when Create or its mixin is missing. (M3 also
built a Burner Keeper role, with which Fire-types kept burners kindled. The approver's ruling after the
Phase 1 play-test, R59 of 2026-11-10, removed it: see Task 5.)

Before Task 1, open the Create 6.0.10 build 280 sources (the `-sources` jar from `maven.createmod.net`,
or a decompile of the slim jar) and check every reference in the research note's "Create 6.0.10" section:
`WaterWheelBlockEntity.determineAndApplyFlowScore`, `setFlowScoreAndUpdate(I)V`, `getGeneratedSpeed`,
`WaterWheelStructuralBlock.getMaster` and `stillValid`, `HandCrankBlockEntity.turn(boolean)`,
`ValveHandleBlockEntity` (a subclass of `HandCrankBlockEntity`), `BlazeBurnerBlockEntity.getHeatLevel()`,
`BlazeBurnerBlock.HeatLevel`, `BlockEntityBehaviourEvent`, `BlockEntityBehaviour` (its `write` and `read`
signatures in 6.0.10), `AllBlockEntityTypes.WATER_WHEEL`, `LARGE_WATER_WHEEL`, `HAND_CRANK`, `BasinBlockEntity`
and `FluidTankBlockEntity`.

Every class that names a Create type lives in `handler/create/` or `mixin/create/`, and only
`CreateCompat.register()` refers to them. `CobblemonDynamics` calls it behind
`ModList.get().isLoaded("create")`, so no Create class loads without Create.

---

### Task 1: Create integration base, mixin flags and the core GameTest run

**Rows:** CD-WHL-10, CD-BRN-07, CD-CFG-10 (GameTest without Create), CD-PLAT-05 (a pack with neither mod
loads), CD-MUS-06 and CD-UI-13 reuse the same flag mechanism later.

**Files:**
- Create: `src/main/java/codes/honor/cobblemondynamics/MixinState.java` (root package, not `mixin/`: see below)
- Modify: `src/main/java/codes/honor/cobblemondynamics/mixin/MixinPlugin.java` (`postApply` records each applied handler)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/create/CreateCompat.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/role/MixinGate.kt`
- Modify: `role/RoleSet.kt` (`withoutHandlers`), `role/RoleRegistry.kt` (`rebuild` applies `MixinGate`), `CobblemonDynamics.kt`
- Modify: `build.gradle` (source set `gametestCore`, run `gameTestServerCore`), `.github/workflows/build.yml` (CI step)
- Move: `TestKit` to `src/gametest/kotlin-shared/`, the game-rule code of `DynamicsTestMod` to `kotlin-shared/.../TestWorld.kt`,
  the structure templates to `src/gametest/resources-shared/` (and `scripts/gen_test_structures.py` writes there)
- Create: `src/gametestCore/resources/META-INF/neoforge.mods.toml` (mod id `cobblemon_dynamics_coretest`)
- Create: `src/gametestCore/kotlin/codes/honor/cobblemondynamics/coretest/CoreTestMod.kt` and `CoreTests.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/role/MixinGateTest.kt`, `src/test/kotlin/codes/honor/cobblemondynamics/MixinStateTest.kt`,
  `build/RepositoryFilesTest.kt` (the workflow runs the core GameTests)

**Interfaces:**
- Produces:

```java
// codes.honor.cobblemondynamics.MixinState. Mixin throws IllegalClassLoadError ("... is in a defined mixin package ...
// and cannot be referenced directly") for every class that loads from the config's package once the config is active
// (MixinProcessor.applyMixins, checked with javap), so this class and the duck interfaces sit outside `mixin`.
public final class MixinState {
    public static final String WATER_WHEEL = "create.WaterWheelBlockEntityMixin";
    public static final String BLAZE_BURNER = "create.BlazeBurnerBlockEntityMixin";
    public static final String ENCHANTMENT_MENU = "vanilla.EnchantmentMenuMixin";
    public static final String PASTURE_WIDGET = "cobblemon.client.PastureWidgetMixin";
    public static boolean applied(String key);
    public static void recordApplied(String mixinClassName, ClassNode target);  // true only if a call to a cobblemon_dynamics$ handler was injected
    public static void setForTest(String key, boolean value);
}
```

```kotlin
object MixinGate {
    /** Role handler id -> the mixin it needs. */
    val REQUIRED: Map<ResourceLocation, String>
    /** Disables each role whose mixin is missing, with one warning per handler. */
    fun apply(roles: RoleSet, applied: (String) -> Boolean, warn: (String) -> Unit): RoleSet
}
```

`MixinPlugin.postApply` passes the target `ClassNode` to `MixinState.recordApplied`, which records the mixin
(by its name relative to the mixin package) as applied when some `MethodInsnNode` calls a method whose name
contains `cobblemon_dynamics$`. Mixin renames each injector handler (`modify$zza000$cobblemon_dynamics$virtualFlow`)
but keeps its name inside the new one. With `defaultRequire: 0` a missing injection point applies the mixin
class but injects no call, so this check tells the two cases apart. A mixin whose target class never loads
(Create absent) never records. `CreateCompat.register()` names each `create.` mixin target class, which loads
it during mod construction, so the flags are final before the first role build. `RoleRegistry.rebuild` builds
the `RoleSet` as before and passes it through `MixinGate.apply`; each mixin warning logs once per run, because
a mixin's state never changes while the game runs.

- [ ] **Step 1: Failing unit tests**

```kotlin
class MixinGateTest {
    @Test fun `CD-WHL-10 CD-BRN-07 a missing mixin disables its roles and warns once`() {
        val wheel = role("wheel_driver", setOf("water"), handler = "cobblemon_dynamics:water_wheel")
        val superheater = role("superheater", setOf("dragon"), priority = 10, handler = "cobblemon_dynamics:burner_heat")
        val packBurner = role("pack_burner", setOf("ice"), handler = "cobblemon_dynamics:burner_heat")   // a data pack's role
        val set = RoleSet(listOf(wheel, superheater, packBurner).associateBy { it.id }, { FakeHandler(it) }, emptySet()) { }
        val warnings = mutableListOf<String>()
        val gated = MixinGate.apply(set, applied = { it != MixinState.WATER_WHEEL && it != MixinState.BLAZE_BURNER }, warn = warnings::add)
        assertTrue(gated.active.isEmpty())
        assertEquals(2, warnings.size)   // one per handler, not per role
    }
    // An applied mixin keeps its roles, and a role that needs no mixin stays on.
}
// MixinStateTest: a ClassNode whose method calls `modify$zza000$cobblemon_dynamics$virtualFlow` records the mixin; one that
// only holds the merged handler method does not; setForTest overrides the recorded state.
```

- [ ] **Step 2: The core GameTest run**

```groovy
sourceSets {
    gametest {
        kotlin.srcDir 'src/gametest/kotlin-shared'
        resources.srcDir 'src/gametest/resources-shared'
    }
    gametestCore {
        compileClasspath += sourceSets.main.output
        runtimeClasspath += sourceSets.main.output
        kotlin.srcDir 'src/gametest/kotlin-shared'
        resources.srcDir 'src/gametest/resources-shared'
    }
}
configurations {
    // Cobblemon and KotlinForForge only: neither localRuntime nor compileOnly, so the core tests cannot name Create.
    gametestCoreImplementation.extendsFrom implementation
    gametestCoreRuntimeOnly.extendsFrom runtimeOnly
}
neoForge {
    addModdingDependenciesTo sourceSets.gametestCore
    mods { cobblemon_dynamics_coretest { sourceSet sourceSets.gametestCore } }
    runs {
        gameTestServerCore {
            type = 'gameTestServer'
            sourceSet = sourceSets.gametestCore
            gameDirectory = project.file('run/gametest-core')
            loadedMods = [mods."${mod_id}", mods.cobblemon_dynamics_coretest]
            systemProperty 'neoforge.enabledGameTestNamespaces', mod_id
        }
    }
}
```

The structure templates move to `src/gametest/resources-shared/data/cobblemon_dynamics/structure/`, a resource
root of both GameTest source sets, so both runs see one copy. A folder inside `src/gametest/resources/` would
also sit in the `gametest` source set's own resource root and ship the templates twice. `TestKit` and the
game-rule code of `DynamicsTestMod` (now `TestWorld.register()`, which `DynamicsTestMod` and `CoreTestMod`
call) move to `src/gametest/kotlin-shared/`, a `kotlin.srcDir` of both source sets. `verifyJar` also rejects
the `coretest` package.

`CoreTests`:

```kotlin
// cdCfg10CreateRolesOffWithoutCreate: RoleRegistry.current.get(wheel_driver), get(superheater) and get(cranker) are
//   null; logs/latest.log of the run directory holds exactly one "Role <id> is disabled: handler" line for
//   each Create role whose file is in the data (the server's resource manager has it), and none for the others, so the
//   check counts each role file from the task that adds it.
// cdPlat05LoadsWithoutCreateAndAe2: ModList has neither "create" nor "ae2", and the Stoker role is active.
```

Add `./gradlew runGameTestServerCore` to `.github/workflows/build.yml` after the full GameTest step, and its
`run/gametest-core/logs/` to the uploaded artifact.

- [ ] **Step 3: Run, write the code, run again, commit** —
  `./gradlew test runGameTestServer runGameTestServerCore`;
  `git commit -m "Gate Create roles on Create and on their mixins, and add a GameTest run without Create"`.

---

### Task 2: Drive direction behaviour for wheels and cranks

**Rows:** CD-WHL-04, CD-WHL-06, CD-WHL-07, CD-CRK-05, CD-CRK-08.

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/create/DriveBehaviour.kt` (`DriveBehaviour` and `DriveNbt`)
- Modify: `handler/create/CreateCompat.kt` (subscribe `BlockEntityBehaviourEvent` for `WATER_WHEEL`, `LARGE_WATER_WHEEL`, `HAND_CRANK`)
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/handler/create/DriveNbtTest.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/create/DriveBehaviourTests.kt`

**Interfaces:**
- Produces:

```kotlin
class DriveBehaviour(be: SmartBlockEntity) : BlockEntityBehaviour(be) {
    var direction: Int = +1        // +1 or -1, CD-WHL-04, CD-CRK-05; private set
    var driven: Boolean = false    // true while a worker Works on it; a change calls blockEntity.sendData()
    fun flip()                     // direction = -direction; blockEntity.notifyUpdate()
    // write/read through DriveNbt: "cd_direction" always, "cd_driven" only when clientPacket is true
    companion object { val TYPE: BehaviourType<DriveBehaviour>; fun of(be: BlockEntity?): DriveBehaviour? }
}
object DriveNbt { const val DIRECTION = "cd_direction"; const val DRIVEN = "cd_driven"; write, direction, driven }
```

As built: `cd_driven` goes into client packets only, never into a save. Claims are never saved (CD-CLM-07),
so a saved `driven = true` would turn a wheel with no worker after a restart. Create posts
`BlockEntityBehaviourEvent` from `SmartBlockEntity.initialize()` (the first tick) or from the first NBT read
(`read`, before the behaviours read their NBT), on both sides (checked with `javap`), so `DriveBehaviour.of`
is null for a new block entity until its first tick, and a loaded one reads its saved direction. The event's
`forType` compares block entity types, so a valve handle, which has its own type, gets no drive.

- [ ] **Step 1: Failing tests** — `DriveNbtTest` (no saved direction reads +1; a saved +1 or −1 reads back; a
  client packet carries `cd_driven`, a save does not). GameTests: `cdWhl04NewWheelsStartPositive` (two new
  wheels read +1; flipping A leaves B at +1), `cdWhl07DirectionSurvivesReload` and
  `cdCrk08DirectionSurvivesReload` (flip and set `driven`, save the block entity with `saveWithFullMetadata`,
  load it with `BlockEntity.loadStatic`: the direction reads −1 and `driven` is false),
  `cdCrk05NewCrankStartsPositive`. CD-WHL-06 needs workers, so its GameTest is in Task 3.
- [ ] **Step 2: Write the behaviour, run, commit** —
  `git commit -m "Store a drive direction on Create wheels and hand cranks"`.

---

### Task 3: Wheel Driver

**Rows:** CD-WHL-01, CD-WHL-02, CD-WHL-03, CD-WHL-05, CD-WHL-06, CD-WHL-08, CD-WHL-09, CD-CLM-04, CD-BAL-04
(wheels), and the final parts of CD-ASG-01, CD-ASG-04, CD-ASG-19, CD-LSH-03, CD-LSH-06, CD-LSH-11,
CD-LSH-14.

**Files:**
- Create: `src/main/java/codes/honor/cobblemondynamics/mixin/create/WaterWheelBlockEntityMixin.java`
- Create: `src/main/java/codes/honor/cobblemondynamics/handler/create/RealFlowAccess.java` (duck interface; outside the
  mixin package, which Mixin reserves: see Task 1)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/create/VirtualFlow.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/create/WaterWheelHandler.kt`
- Create: `src/main/resources/data/cobblemon_dynamics/cobblemon_dynamics/roles/wheel_driver.json`
- Modify: `src/main/resources/cobblemon_dynamics.mixins.json` (`"mixins": ["create.WaterWheelBlockEntityMixin"]`)
- Modify: `handler/create/CreateCompat.kt` (registers the handler), `handler/create/DriveBehaviour.kt` (`nextFlowCheck`)
- Modify: `leash/LeashInteractions.kt` (the flip gesture), `leash/WorkLeashItem.kt` (calls it), `engine/WorkEngine.kt` (`roleOf`)
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/role/BuiltInRoleFilesTest.kt` (the Role table row, the particle split)
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/create/WheelDriverTests.kt`, `CreateKit.kt` (Create scenes,
  full run only), `TestKit.clickBlock(..., sneak)`

The mixin, from the research note:

```java
@Mixin(WaterWheelBlockEntity.class)
public abstract class WaterWheelBlockEntityMixin implements RealFlowAccess {
    @Unique private int cobblemon_dynamics$realScore;

    @ModifyArg(method = "determineAndApplyFlowScore()V", at = @At(value = "INVOKE",
        target = "Lcom/simibubi/create/content/kinetics/waterwheel/WaterWheelBlockEntity;setFlowScoreAndUpdate(I)V"))
    private int cobblemon_dynamics$virtualFlow(int realScore) {
        this.cobblemon_dynamics$realScore = realScore;
        return realScore != 0 ? realScore : VirtualFlow.sign((WaterWheelBlockEntity) (Object) this);   // -1, 0 or +1
    }

    @Override public int cobblemon_dynamics$realFlowScore() { return cobblemon_dynamics$realScore; }
}
```

Checked against the 6.0.10 build 280 jar with `javap`: `determineAndApplyFlowScore()` ends with its one call
of `setFlowScoreAndUpdate(I)V`, which returns early on an unchanged score, else calls
`updateGeneratedRotation()`, which on the server applies the speed and calls `sendData()`.
`getGeneratedSpeed()` is `clamp(flowScore, -1, 1) * 8 / getSize()`. `VirtualFlow.sign(be)` returns
`DriveBehaviour.of(be)?.takeIf { it.driven }?.direction ?: 0`. The mixin applies on both sides; the behaviour
syncs `driven` and `direction`, so the client computes the same flow and Create's goggles show the capacity
(CD-WHL-09).

`WaterWheelHandler` (`id = cobblemon_dynamics:water_wheel`, `scanKind = BLOCK_ENTITY`), as built:

- `matches`: the block entity is a `WaterWheelBlockEntity` (small or large), or the block is a valid
  `WaterWheelStructuralBlock` whose master holds one. The leash checks `matches` on the clicked block before it
  asks for the canonical target, so a click on a ring block must match too.
- `canonical`: for a `WaterWheelStructuralBlock` position, `getMaster(level, pos, state)` after
  `stillValid(level, pos, state, false)` (an instance method of the structural block); otherwise the position
  itself (CD-CLM-04). The leash and the scan both call it, so every ring block maps to the controller.
- `isPartOfTarget`: the position is the controller or a structural block whose master is the controller.
- `hasDemand`: `(be as RealFlowAccess).cobblemon_dynamics$realFlowScore() == 0` after calling
  `be.determineAndApplyFlowScore()` at most once per 20 ticks per wheel (CD-WHL-03, R43). The wheel's
  `DriveBehaviour.nextFlowCheck` holds the next allowed tick. Create also recomputes on its lazy tick (60
  ticks) and on a neighbour update, which records the real score through the mixin. A wheel with no drive
  behaviour yet has no demand.
- `onStart`: `driven = true`, then `determineAndApplyFlowScore()`. `onStop`: `driven = false`, then
  `determineAndApplyFlowScore()` while the chunk still ticks, so the wheel stops in the same tick (CD-WHL-08).
  During a chunk unload the wheel keeps the flow that it saves, and its next load recomputes it with no drive.
- `applyWork`: drives the wheel again when its drive was reset while its worker Works (a new block entity).
- `flip`: when `driven`, `DriveBehaviour.flip()` and `determineAndApplyFlowScore()` (CD-WHL-05).

The flip gesture: a sneak-right-click on a block with the Work Leash. `WorkLeashItem.onItemUseFirst` calls
`LeashInteractions.onSneakBlockClick` first, with or without a selection. It finds an active role whose handler
matches the clicked block, takes that handler's canonical target, and the work claim on it. When the player is
an operator (permission level 2), the binder of the claim's worker, or the placer of the claim's pasture, it
calls `flip` on the handler of the worker's role (`WorkEngine.roleOf`) and returns `SUCCESS`; `flip` refuses a
target that is not driven. Otherwise the direction stays unchanged and the click falls through to the normal
leash rules (CD-SEC-10 in Task 6).

`wheel_driver.json`: handler `cobblemon_dynamics:water_wheel`, types `["water"]`, `auto_assign` true,
priority 0, range 6.0, effect Water Gun: animations `["watergun", "spray", "beam", "special", "cry"]`,
actor `cobblemon:watergun_spray`, `cobblemon:watergun_actor`, target `cobblemon:watergun_target`,
`cobblemon:watergun_targetfoam`. Cobblemon's `watergun` timeline emits the spray from the user, aimed at the
target, and only the two `target` particles on the target. Target particles play at the work focus entity
(M1 Task 10), so a spray listed as a target particle would never travel from the worker (CD-FX-04).

- [ ] **Step 1: Failing tests** — `BuiltInRoleFilesTest`: the Role table row of the Wheel Driver
  (CD-FX-02) and its particle split (CD-FX-04). GameTests (`WheelDriverTests`; each scene puts the wheel on the
  stone floor with no water nearby):

```kotlin
// cdWhl01Whl02SmallAndLargeWheelsTurnAtCreateValues: two Squirtles, a dry small wheel and a dry large wheel; each wheel has
//   a Working worker (CD-WHL-01), the small one turns at getSpeed() == 8f with 256 SU and the large one at 4f with 512 SU,
//   both as calculateAddedStressCapacity() × |speed| and as the capacity of the wheel's kinetic network (CD-WHL-02).
// cdWhl03RealWaterMeansStandby: a dry wheel keeps its worker Working for 40 ticks; then a water source on the wheel's top,
//   walled on three sides, flows north over the edge into a walled one-block column on the wheel's north side, which turns
//   the wheel the same way: the worker is in STANDBY within 20 ticks of the source, the mixin's real score is not 0, and
//   its stamina does not drain in the next 40 ticks.
// cdWhl05BinderFlips: the binder sneak-clicks a worked wheel at +8 RPM: -8 RPM 2 ticks later.
// cdWhl06TakeoverKeepsDirection: worker A, bound from north of the wheel, turns it at +8; the owner clears A's binding and
//   the claim is released; worker B, bound from south of it: still +8. Each spot is checked to be on its side.
// cdWhl08StopWithinTwoTicks: release the claim on a worked dry wheel: speed 0 two ticks later.
// cdWhl09ClientSeesCapacity: the update tag holds cd_driven = true, cd_direction = 1 and FlowScore = 1, a drive behaviour
//   that reads it as a client packet is driven at +1, and the saved tag holds no cd_driven (the goggle view is a Demo in
//   the manual checklist; this asserts the data it needs reaches the client).
// cdClm04LargeWheelIsOneTarget: worker A binds through ring block 1 (the binding names the controller); binding worker B
//   through ring block 2 refuses with "Already assigned to ...", B gets no binding and no claim for 100 ticks, and the
//   registry holds one claim keyed on the controller and none on a ring block.
// cdBal04WheelsNeverExceedCreate: 1,200 ticks once both wheels turn: small at most 8 RPM, large at most 4 RPM.
```

  The water-wheel versions of the final parts below exercise engine and leash code that the furnace tests of
  M1 cover, on a target that the tests above prove; each: GameTest deferred (high confidence): verified in the end-of-phase pass.

  - CD-ASG-01 (`cdAsg01WheelVersion`: the row's four Water-types (wild, sent-out party, fainted pastured, healthy
    pastured) 3 blocks from a dry wheel for 400 ticks; only the healthy pastured one claims): GameTest deferred (high confidence): verified in the end-of-phase pass.
  - CD-ASG-04 (`cdAsg04AzumarillSkipsTheWheel`: an unleashed Azumarill 3 blocks from a dry wheel for 400 ticks: no
    claim): GameTest deferred (high confidence): verified in the end-of-phase pass.
  - CD-ASG-19 (`cdAsg19WheelDriverMovesToADryWheel`: a Wheel Driver waits 400 ticks at a wheel that real water turns;
    place a dry wheel in the work area: within 80 ticks its claim is on the dry wheel): GameTest deferred (high confidence): verified in the end-of-phase pass.
  - CD-LSH-03 (`cdLsh03BindsVaporeonToAWheel`): GameTest deferred (high confidence): verified in the end-of-phase pass.
  - CD-LSH-06 (`cdLsh06BoundMessageNamesTheWheel`: the recorded action bar reads "Bound Vaporeon to Water Wheel"):
    GameTest deferred (high confidence): verified in the end-of-phase pass.
  - CD-LSH-11 (`cdLsh11RebindMovesToWheelB`): GameTest deferred (high confidence): verified in the end-of-phase pass.
  - CD-LSH-14 (`cdLsh14FormWithoutWaterClearsBinding`: change the form to one with no Water type; 20 ticks later no
    binding): GameTest deferred (high confidence): verified in the end-of-phase pass.

- [ ] **Step 2: Write the mixin, handler, role file and gesture; run; commit** —
  `git commit -m "Turn dry Create water wheels with Water-types at Create's own speeds"`.

---

### Task 4: Cranker

**Rows:** CD-CRK-01, CD-CRK-02, CD-CRK-03, CD-CRK-04, CD-CRK-05 (a new worked crank), CD-CRK-06, CD-CRK-07,
CD-BAL-04 (crank), CD-ASG-10 (the 2-block range in game).

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/create/HandCrankHandler.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/create/CrankClock.kt`
- Create: `src/main/java/codes/honor/cobblemondynamics/mixin/create/ItemUseOverridesMixin.java`
- Create: `src/main/resources/data/cobblemon_dynamics/cobblemon_dynamics/roles/cranker.json`
- Modify: `src/main/resources/cobblemon_dynamics.mixins.json`, `src/main/java/codes/honor/cobblemondynamics/MixinState.java`
  (`ITEM_USE_OVERRIDES`), `handler/create/CreateCompat.kt` (registers the handler, loads `ItemUseOverrides`, warns when its
  mixin is missing), `handler/create/DriveBehaviour.kt` (`drivenSince`), `leash/LeashInteractions.kt` (`isWorkLeash`)
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/handler/create/CrankClockTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/role/BuiltInRoleFilesTest.kt`, `role/BuiltInRoles.kt` (parses the
  mod's role files for the unit tests)
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/create/CrankerTests.kt`, `CreateKit.handCrank`

`HandCrankHandler` (`id = cobblemon_dynamics:hand_crank`), as built: `matches` when the block entity is a
`HandCrankBlockEntity` and not a `ValveHandleBlockEntity` (CD-CRK-04). `hasDemand` is always true (CD-CRK-07).
`onStart` sets the crank's `DriveBehaviour.driven` and `drivenSince`; `applyWork` turns the crank when
`CrankClock.shouldTurn(drivenSince, now)`: on the first tick of the drive and every 5 ticks after (CD-CRK-02), and it
starts the drive again when a new block entity reset it. Checked against the 6.0.10 jar with `javap`: `turn(back)` sets
`inUse = 10` and calls `updateGeneratedRotation()` on a start or a direction change, `tick()` counts `inUse` down and stops
the crank at 0, and `getGeneratedSpeed()` is `(backwards ? -1 : 1) * 32`, passed through `convertToDirection(speed, FACING)`,
which negates it for a `FACING` along a negative axis. So the handler calls `turn(direction * FACING.axisDirection.step < 0)`,
not `turn(direction < 0)`: the speed's sign is the stored direction on every crank, and a new crank turns at +32 RPM
whichever way it faces (CD-CRK-05). Create's `turn` gives 32 RPM and 8 SU per RPM, so 256 SU (CD-CRK-03). `flip` refuses a
crank that no worker drives, reverses the direction and turns the crank at once (CD-CRK-06). `onStop` clears `driven`, and
Create stops the crank 10 ticks after the last turn, as for a player. `cranker.json`: types `["fighting", "normal"]`,
`auto_assign` true, priority 0, range 2.0, effect Pound (`pound`, `punch`, `tail`, `physical`, `cry`; no actor particles;
target particles `cobblemon:pound_target`, `cobblemon:pound_targetsparks`, which Cobblemon's `pound` timeline emits on the
target only).

The leash on a crank. Create registers `HandCrankBlock` (and the analog lever) with `ItemUseOverrides.addBlock`, and
`ItemUseOverrides.onBlockActivated`, a `RightClickBlock` listener at normal priority, uses the block for every held item but
Create's wrench and cancels the event (checked with `javap`). NeoForge then returns from `useItemOn` before
`WorkLeashItem.onItemUseFirst`, so the leash could neither bind a crank nor flip one. `ItemUseOverridesMixin` injects at the
head of `onBlockActivated` and returns for the Work Leash, the same exemption that Create gives its wrench. A protection mod
that cancels the click still stops the leash (CD-SEC-11). With no selection and no flip, the leash's click goes on to the
crank's own `useItemOn`, which turns it as before. The crank role does not depend on this mixin; when it does not apply,
`CreateCompat.register` logs one warning and the Cranker still auto-assigns.

- [ ] **Step 1: Failing tests** — `CrankClockTest`: turns at 0, 5 and 10, none at 4, and every interval is 5 ticks
  (CD-CRK-02). `BuiltInRoleFilesTest`: the Role table row (CD-FX-02) and the Pound particle split (CD-FX-04); CD-CRK-01 on
  the role file (Machop's `fighting` and Rattata's `normal` are eligible, Pikachu's `electric` is not); CD-ASG-10 on the
  role files (a spot at 6.0 blocks is in range for the Stoker and one at 6.1 is not; 2.0 and 2.1 for the Cranker).
  `CrankerTests`, each crank with no network behind it:

```kotlin
// cdCrk02Bal04CrankHolds32RpmFor600Ticks: once a Machop works a crank at 32 RPM, the crank reads exactly 32 RPM on every one of the
//   next 600 ticks, so it never drops (CD-CRK-02) and never passes Create's maximum (CD-BAL-04); the worker's spot is within 2.0 blocks
//   of the crank's center (CD-ASG-10 in game).
// cdCrk03Crk05NewCranksTurnAtCreateValues: a crank that faces up and one that faces north, each with a Working Machop: both turn at
//   +32 RPM (CD-CRK-05) with 256 SU, as calculateAddedStressCapacity() × |speed| and as the capacity of the crank's network (CD-CRK-03).
// cdCrk06BinderFlips: the crank mixin applied; the owner binds a Machop to the crank and sneak-clicks it at +32 RPM: 5 ticks later the
//   crank reads −32 RPM and its stored direction is −1.
```

  - CD-CRK-04 (`cdCrk04ValveHandleIsNotATarget`: a Machop beside a valve handle for 400 ticks: no claim; `matches` is a type
    check against Create's own class tree): GameTest deferred (high confidence): verified in the end-of-phase pass.
  - CD-CRK-07 (`cdCrk07NoNetworkStillWorks`: a crank with no network keeps its worker Working, and stamina drains; `hasDemand`
    is constant): GameTest deferred (high confidence): verified in the end-of-phase pass.
- [ ] **Step 2: Write the code, run, commit** — `git commit -m "Turn Create hand cranks with Fighting- and Normal-types"`.

---

### Task 5: Superheater

**Rows:** CD-BRN-01, CD-BRN-02, CD-BRN-03, CD-BRN-04, CD-BRN-05, CD-BRN-06, CD-SUP-01, CD-SUP-02, CD-SUP-03,
CD-SUP-04.

**Approver's ruling after the Phase 1 play-test (R59, 2026-11-10):** "Remove the fire ability to power burners; let's just
have dragons do it at pre-50 kindled and post-50 seething." M3 built a second burner role, the Burner Keeper (Fire, params
`{ "heat": "kindled" }`, priority 0). The ruling removed its role file, and the BRN rows now describe the Superheater, whose
file already gives `kindled` below level 50 and `seething` from level 50. The tests that checked the Burner Keeper check a
Lv10 Dratini, and `cdSup02FireTypeNeverClaimsABlazeBurner` checks that a Charmander never claims a burner. The text below
describes the task as it stands after the ruling.

**Files:**
- Create: `src/main/java/codes/honor/cobblemondynamics/mixin/create/BlazeBurnerBlockEntityMixin.java`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/create/PokemonHeat.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/create/HeatGate.kt` (pure, no Create types)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/create/BurnerHeatHandler.kt`
- Create: `src/main/resources/data/cobblemon_dynamics/cobblemon_dynamics/roles/superheater.json`
- Modify: `src/main/resources/cobblemon_dynamics.mixins.json`, `handler/create/CreateCompat.kt` (registers the handler)
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/handler/create/HeatGateTest.kt`, `role/BuiltInRoleFilesTest.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/create/BurnerTests.kt`, `CreateKit` (burner, basin, tank, boiler heat)

The mixin, as built:

```java
@Mixin(BlazeBurnerBlockEntity.class)
public abstract class BlazeBurnerBlockEntityMixin {
    @Inject(method = "getHeatLevel()Lcom/simibubi/create/content/processing/burner/BlazeBurnerBlock$HeatLevel;",
            at = @At("RETURN"), cancellable = true)
    private void cobblemon_dynamics$heat(CallbackInfoReturnable<BlazeBurnerBlock.HeatLevel> cir) {
        BlazeBurnerBlock.HeatLevel ours = PokemonHeat.levelFor((BlazeBurnerBlockEntity) (Object) this);
        if (ours != null && ours.isAtLeast(cir.getReturnValue())) cir.setReturnValue(ours);
    }
}
```

Checked against the 6.0.10 jar with `javap`: `getHeatLevel()` is protected and gives `SEETHING` for `SPECIAL` fuel, `KINDLED` for
`NORMAL` fuel (`FADING` below 125 ticks, `remainingBurnTime / 10000.0 < 0.0125`) and `SMOULDERING` for none; `isAtLeast`
compares ordinals. The server `tick()` counts `remainingBurnTime` down and calls `updateBlockState()`, which is
`setBlockHeat(getHeatLevel())`, on every tick while the fuel is `NONE` or `NORMAL`; `SPECIAL` fuel, which is `SEETHING`
already, updates only when it runs out. `setBlockHeat` writes the block state with `setBlockAndUpdate` only on a change. A
creative burner returns before any of this. An empty burner (`HEAT_LEVEL` `NONE`) has no block entity (`newBlockEntity`).

`PokemonHeat`, as built, holds a grant per `BlazeBurnerBlockEntity` (a weak map, server thread only; `levelFor` returns null on
a client) instead of a map from `(level, pos)`: the handler grants the heat in `onStart` and on every tick in `applyWork`, and
withdraws it in `onStop` (CD-BRN-06). A grant made on tick `t` holds through tick `t + 1`, so heat whose `onStop` never came,
after a handler error, `WorkEngine.resetRuntimeForTest()` or a server stop, still lapses within 2 ticks. A key of `(level, pos)`
would have outlived a world: a singleplayer world that the player leaves and opens again, or another world, reuses the
dimension and the position. The mixin only changes the returned value, so the burner's fuel and burn time stay untouched
(CD-BRN-04), and Create's own tick writes the block state, which basins and boilers read (CD-BRN-05, CD-SUP-03). Heat starts
on the burner's first tick after Working starts and ends on its first tick after release.

`BurnerHeatHandler` (`id = cobblemon_dynamics:burner_heat`), as built: `matches` when the block entity is a
`BlazeBurnerBlockEntity` and the block state's heat level is not `NONE` (CD-BRN-02). `hasDemand` uses Create's own public
`isValidBlockAbove()` (a `BasinBlockEntity` above, or a `FluidTankBlock`; checked with `javap`) (CD-BRN-03), and needs a heat:
a role whose params give no heat at the worker's level has no demand. A creative burner (`isCreative()`) has no demand either:
its `tick()` returns before it reads the heat level, so a worker's heat never reaches its block state. The end-of-phase pass
found that a worker Worked a creative burner for nothing, and added this check (`creativeBlazeBurnerHasNoDemand`). The
heat comes from the role params:
`HeatGate.heatFor(params, level)` returns `params.heat` when `level >= params.min_level` (default 0) and
`params.below_min_level_heat` otherwise, as Create's serialized heat name (`kindled`, `seething`), which the handler maps to
`HeatLevel` by `getSerializedName()`. `HeatGate` names no Create type, so it has unit tests.

`superheater.json`: types `["dragon"]` (CD-SUP-02), `auto_assign` true, priority 10,
range 6.0, params `{ "heat": "seething", "min_level": 50, "below_min_level_heat": "kindled" }`, effect Dragon Breath
(`dragonbreath`, `spray`, `special`, `cry`; actor `cobblemon:dragonbreath_actor`, which Cobblemon's timeline emits from the user
toward the target, and target `cobblemon:dragonbreath_target`, which it emits on the target).

- [ ] **Step 1: Failing tests** — `HeatGateTest`: Lv49 gives kindled and Lv50 gives seething (CD-SUP-01,
  CD-SUP-04); the Superheater gives kindled at every level below 50 (CD-BRN-01, CD-BRN-05); a Fire-type Charmander is not
  eligible for the Superheater and a Dratini is (CD-SUP-02); params with no `min_level`, and with no heat below it. `BuiltInRoleFilesTest`: the
  Role table rows (CD-FX-02) and the particle split of Dragon Breath (CD-FX-04). `DefaultPriorityTest`: a burner has the
  Superheater as its only role, so a pure Fire-type gets no role there (CD-SUP-02). `BurnerTests`, each burner filled and
  with no fuel:

```kotlin
// cdBrn01Brn06KindledBelowLevel50SmoulderingAfterRelease: a basin above; 1 tick after a Lv10 Dratini starts Working the burner reads
//   KINDLED (CD-BRN-01); the test releases the claim, and 2 ticks later the burner reads SMOULDERING (CD-BRN-06).
// cdBrn05BoilerCountsOneHeatBelowLevel50: a fluid tank above; a Working Lv10 Dratini: KINDLED, BoilerHeater.findHeat (the REGISTRY lookup) gives 1, and
//   the tank's BoilerData.updateTemperature counts activeHeat 1. FluidTankBlockEntity.updateBoilerTemperature only flags a boiler that has
//   an engine, so the test runs the boiler's own heat count directly.
// cdSup01Lv50Seething: a basin above; 1 tick after a Lv50 Dragonair starts Working the burner reads SEETHING.
// cdSup03Sup04Lv49ThenLv50: a fluid tank above; a Lv49 Dragonair holds KINDLED with heater heat 1 and boiler heat 1 for 20 ticks, then
//   levels up through Cobblemon (Pokemon.levelUp with a SidemodExperienceSource); within 5 ticks the burner reads SEETHING with heater
//   heat 2 and boiler heat 2.
// cdSup02FireTypeNeverClaimsABlazeBurner: a filled burner with a basin above; the Work Leash finds no role for a Charmander there, and
//   in 400 ticks it never claims the burner; then a Lv10 Dratini joins the pasture and claims it (CD-SUP-02).
```

  The Dragonair tests found an engine bug that this task fixes in a commit of its own: a Dragonair is 1.1 blocks wide, and
  navigation steers a mob 1 to 2 blocks wide to its node's corner, not its center (`Path.getEntityPosAtNode`, checked with
  `javap`, and Cobblemon's `OmniPathNavigation`). The engine's arrival rule measured from the spot's center, so on about 4
  approaches in 10 the Dragonair stopped short of "arrived" until its travel timed out. `Arrival.isAt` now measures from
  where navigation stops the mob (M1 part 2, step 5), with `ArrivalTest`.

  - CD-BRN-02 (`cdBrn02EmptyBurnerIsNeverClaimed`: an empty burner with a basin above and a Dratini beside it for 400 ticks:
    no claim; an empty burner has no block entity, so the block entity scan never offers it): GameTest deferred (high
    confidence): verified in the end-of-phase pass.
  - CD-BRN-03 (`cdBrn03NoBasinMeansStandby`: remove the basin above a worked burner: Standby within 20 ticks; demand is Create's
    `isValidBlockAbove()`, and a Working worker checks demand on every tick): GameTest deferred (high confidence): verified in
    the end-of-phase pass.
  - CD-BRN-04 (`cdBrn04FuelBurnsDownAsNormal`: two burners with 1,000 ticks of coal fuel each, one worked for 400 ticks: equal
    `getRemainingBurnTime()`, which is public in 6.0.10; the mixin changes only a returned value, and the tick counts the burn
    time down whatever the heat): GameTest deferred (high confidence): verified in the end-of-phase pass.
- [ ] **Step 2: Write the code, run, commit** —
  `git commit -m "Kindle and superheat Blaze Burners with Fire- and Dragon-types"`. After the ruling:
  `git commit -m "Remove the Burner Keeper: only Dragon-types heat Blaze Burners"`.

---

### Task 6: Create leash resets and flip permissions

**Rows:** CD-LSH-22, CD-LSH-23, CD-SEC-10 (wheel and crank).

**Files:**
- No change to `leash/LeashResets.kt`, as built (see below)
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/create/CreateLeashTests.kt`

As built, a removed Create target needs no code of its own. Checked with `javap`: a sneak-use of the wrench reaches
`WrenchItem.useOn`, because Create's `ItemUseOverrides` returns at once for its wrench, and `IWrenchable.onSneakWrenched` posts a
`BlockEvent.BreakEvent`, gives the drops to the player and calls `Level.destroyBlock`. A mechanical piston's
`PistonContraption.assemble` takes the blocks in front of it, and `removeBlocksFromWorld` removes their block entities and sets
the cells to air before the contraption entity moves them. Either way the target's block entity is gone, so the index check of
`LeashResets` (every 10 ticks, M1, CD-LSH-08) finds `stillValid` false and clears the binding with the `target_gone` reason. A
mutant that skips that check fails both GameTests below, and CD-LSH-08's.

- [ ] **Step 1: GameTests** — they passed on their first run, because the M1 check above already covers them:

```kotlin
// cdLsh22WrenchPickupClearsBinding: a Machop bound to a crank; the owner sneak-uses AllItems.WRENCH on it through TestKit.clickBlock
//   (player.gameMode.useItemOn): the crank is gone and back in the owner's inventory, and 20 ticks later the Machop has no binding
//   and the crank's position no index entry.
// cdLsh23ContraptionClearsBinding: a Charmander bound to a furnace; a mechanical piston faces west with one extension pole behind it
//   and the furnace in front (Create refuses a piston with no pole), and a creative motor on its north side, shaft south, turns it at
//   +16 RPM, which extends a west-facing piston (MechanicalPistonBlockEntity.getMovementSpeed, checked with javap). Once the furnace
//   leaves its cell and a contraption entity holds it, 20 ticks later the Charmander has no binding and the cell no index entry.
```

  - CD-SEC-10 (`cdSec10StrangersAndIdleTargetsNeverFlip`: a second player clicks a worked wheel and a worked crank, and the
    binder clicks a wheel that no worker drives: no direction changes; the fan part is M7): GameTest deferred (high
    confidence): verified in the end-of-phase pass. The refusal is `LeashInteractions.mayFlip` and the handlers' driven
    check, plain code that the binder's flip tests of Tasks 3 and 4 exercise. With Task 4's crank mixin, a stranger's
    sneak-click reaches `onItemUseFirst`, which refuses the flip, and vanilla then skips the block use of a sneaking player
    who holds an item, so Create does not turn the crank either.
- [ ] **Step 2: Run everything, commit** — `./gradlew build runGameTestServer runGameTestServerCore`;
  `git commit -m "Test that a Create wrench or contraption clears the binding of the target it takes"`. Then push and open the
  pull request with `pr-runner`, base `main`.
