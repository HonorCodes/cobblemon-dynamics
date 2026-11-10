# M3: Create roles

Part of the [Phase 1 plan](2026-11-09-phase-1-plan.md). Read its Global Constraints first. Branch:
`m3-create-roles`. Builds on M1 and M2.

**Milestone goal:** Water-types turn dry water wheels, Fighting- and Normal-types turn hand cranks,
Fire-types keep Blaze Burners kindled and Dragon-types of level 50 or higher superheat them, each at
Create's own values, and each role switches itself off when Create or its mixin is missing.

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
        val keeper = role("burner_keeper", setOf("fire"), handler = "cobblemon_dynamics:burner_heat")
        val superheater = role("superheater", setOf("dragon"), priority = 10, handler = "cobblemon_dynamics:burner_heat")
        val set = RoleSet(listOf(wheel, keeper, superheater).associateBy { it.id }, { FakeHandler(it) }, emptySet()) { }
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
// cdCfg10CreateRolesOffWithoutCreate: RoleRegistry.current.get(wheel_driver), get(burner_keeper), get(superheater) and
//   get(cranker) are null; logs/latest.log of the run directory holds exactly one "Role <id> is disabled: handler" line for
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
- Create: `src/main/java/codes/honor/cobblemondynamics/mixin/create/RealFlowAccess.java` (duck interface)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/create/VirtualFlow.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/create/WaterWheelHandler.kt`
- Create: `src/main/resources/data/cobblemon_dynamics/cobblemon_dynamics/roles/wheel_driver.json`
- Modify: `src/main/resources/cobblemon_dynamics.mixins.json` (`"mixins": ["create.WaterWheelBlockEntityMixin"]`)
- Modify: `leash/LeashInteractions.kt` (the flip gesture)
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/create/WheelDriverTests.kt`

The mixin, from the research note:

```java
@Mixin(WaterWheelBlockEntity.class)
abstract class WaterWheelBlockEntityMixin implements RealFlowAccess {
    @Unique private int cobblemon_dynamics$realScore;

    @ModifyArg(method = "determineAndApplyFlowScore()V", at = @At(value = "INVOKE",
        target = "Lcom/simibubi/create/content/kinetics/waterwheel/WaterWheelBlockEntity;setFlowScoreAndUpdate(I)V"))
    private int cobblemon_dynamics$virtualFlow(int realScore) {
        this.cobblemon_dynamics$realScore = realScore;
        int virtual = VirtualFlow.sign((WaterWheelBlockEntity) (Object) this);   // -1, 0 or +1
        return realScore != 0 ? realScore : virtual;
    }

    @Override public int cobblemon_dynamics$realFlowScore() { return cobblemon_dynamics$realScore; }
}
```

`VirtualFlow.sign(be)` returns `DriveBehaviour.of(be)?.takeIf { it.driven }?.direction ?: 0`. The mixin
applies on both sides; the behaviour syncs `driven` and `direction`, so the client computes the same flow
and Create's goggles show the capacity (CD-WHL-09).

`WaterWheelHandler` (`id = cobblemon_dynamics:water_wheel`, `scanKind = BLOCK_ENTITY`):

- `matches`: the block entity is a `WaterWheelBlockEntity` (small or large).
- `canonical`: for a `WaterWheelStructuralBlock` position, `getMaster(level, pos, state)` after
  `stillValid(level, pos, state, false)`; otherwise the position itself (CD-CLM-04). The leash and the scan
  both call it, so every ring block maps to the controller.
- `isPartOfTarget`: the position is the controller or a structural block whose master is the controller.
- `hasDemand`: `(be as RealFlowAccess).cobblemon_dynamics$realFlowScore() == 0` after calling
  `be.determineAndApplyFlowScore()` at most once per 20 ticks per wheel (CD-WHL-03, R43).
- `onStart`: `driven = true`, then `determineAndApplyFlowScore()`. `onStop`: `driven = false`, then
  `determineAndApplyFlowScore()`, so the wheel stops in the same tick (CD-WHL-08).
- `flip`: when `driven`, `DriveBehaviour.flip()` and `determineAndApplyFlowScore()` (CD-WHL-05).

The flip gesture: a sneak-right-click on a block with the Work Leash. If the block's canonical target
holds a `DriveBehaviour` that is `driven`, and the player is the worker's binder, the pasture's placer
or an operator, call `handler.flip` and return `SUCCESS`. Otherwise leave the direction unchanged and fall
through to the normal leash rules (CD-SEC-10 in Task 6).

`wheel_driver.json`: handler `cobblemon_dynamics:water_wheel`, types `["water"]`, `auto_assign` true,
priority 0, range 6.0, effect Water Gun: animations `["watergun", "spray", "beam", "special", "cry"]`,
actor `cobblemon:watergun_spray`, `cobblemon:watergun_actor`, target `cobblemon:watergun_target`,
`cobblemon:watergun_targetfoam`. Cobblemon's `watergun` timeline emits the spray from the user, aimed at the
target, and only the two `target` particles on the target. Target particles play at the work focus entity
(M1 Task 10), so a spray listed as a target particle would never travel from the worker (CD-FX-04).

- [ ] **Step 1: Failing GameTests** (`WheelDriverTests`; each scene puts the wheel on a stone base with
  no water nearby):

```kotlin
// cdWhl01SmallAndLargeWheelsAreTargets: one Squirtle claims a dry small wheel, another a dry large wheel.
// cdWhl02CreateValues: Working on a small wheel: getSpeed() == 8f and the generated capacity is 256 SU
//   (calculateAddedStressCapacity() × |speed|); on a large wheel 4f and 512 SU.
// cdWhl03RealWaterMeansStandby: a dry wheel keeps its worker Working; place flowing water at a checked offset:
//   the worker is in STANDBY within 20 ticks and its stamina does not drain.
// cdWhl05BinderFlips: the binder sneak-clicks a worked wheel at +8 RPM: -8 RPM 2 ticks later.
// cdWhl06TakeoverKeepsDirection: worker A north of the wheel at +8, release A, bind B south of it: still +8.
// cdWhl08StopWithinTwoTicks: release the claim on a worked dry wheel: speed 0 two ticks later.
// cdWhl09ClientSeesCapacity: the behaviour's update tag holds cd_driven = true and cd_direction = 1 (the goggle view is
//   a Demo in the manual checklist; this asserts the data it needs reaches the client).
// cdClm04LargeWheelIsOneTarget: worker A claims through ring block 1; binding worker B to ring block 2 refuses with
//   "Already assigned to ..." and the registry holds one claim keyed on the controller.
// cdBal04WheelsNeverExceedCreate: 1,200 ticks: small at most 8 RPM, large at most 4 RPM.
// cdAsg01WheelVersion: the row's four Water-types (wild, sent-out party, fainted pastured, healthy pastured) 3 blocks from
//   a dry wheel for 400 ticks; only the healthy pastured one claims.
// cdAsg04AzumarillSkipsTheWheel: an unleashed Azumarill 3 blocks from a dry wheel for 400 ticks: no claim.
// cdAsg19WheelDriverMovesToADryWheel: a Wheel Driver waits 400 ticks at a wheel that real water turns; place a dry wheel in
//   the work area: within 80 ticks its claim is on the dry wheel.
// cdLsh03BindsVaporeonToAWheel / cdLsh06BoundMessageNamesTheWheel ("Bound Vaporeon to Water Wheel") /
// cdLsh11RebindMovesToWheelB / cdLsh14FormWithoutWaterClearsBinding (change the form to one with no Water type).
```

- [ ] **Step 2: Write the mixin, handler, role file and gesture; run; commit** —
  `git commit -m "Turn dry Create water wheels with Water-types at Create's own speeds"`.

---

### Task 4: Cranker

**Rows:** CD-CRK-01, CD-CRK-02, CD-CRK-03, CD-CRK-04, CD-CRK-06, CD-CRK-07, CD-BAL-04 (crank), CD-ASG-10
(the 2-block range in game).

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/create/HandCrankHandler.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/create/CrankClock.kt`
- Create: `src/main/resources/data/cobblemon_dynamics/cobblemon_dynamics/roles/cranker.json`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/handler/create/CrankClockTest.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/create/CrankerTests.kt`

`HandCrankHandler` (`id = cobblemon_dynamics:hand_crank`): `matches` when the block entity is a
`HandCrankBlockEntity` and not a `ValveHandleBlockEntity` (CD-CRK-04). `hasDemand` is always true
(CD-CRK-07). `applyWork` calls `be.turn(direction < 0)` when `CrankClock.shouldTurn(workingSince, now)`,
every 5 ticks (CD-CRK-02). Create's `turn` keeps the crank at 32 RPM and 256 SU (CD-CRK-03). `flip`
reverses the direction; the next `turn` applies it within 5 ticks (CD-CRK-06). `cranker.json`: types
`["fighting", "normal"]`, `auto_assign` true, priority 0, range 2.0, effect Pound (`pound`, `punch`, `tail`,
`physical`, `cry`; target particles `cobblemon:pound_target`, `cobblemon:pound_targetsparks`).

- [ ] **Step 1: Failing tests** — `CrankClockTest`: turns at 0, 5 and 10, none at 4 (CD-CRK-02).
  `CrankerTests`: CD-CRK-01 is a unit test on the role file (Machop and Rattata eligible, Pikachu not);
  `cdCrk02Holds32RpmFor600Ticks` (the speed never drops below 32 on any tick); `cdCrk03CreateValues`;
  `cdCrk04ValveHandleIsNotATarget` (a Machop beside a valve handle 400 ticks: no claim);
  `cdCrk06BinderFlips` (−32 RPM 5 ticks after the click); `cdCrk07NoNetworkStillWorks` (Working and
  stamina drains); `cdBal04CrankNeverExceeds32`.
- [ ] **Step 2: Write the code, run, commit** — `git commit -m "Turn Create hand cranks with Fighting- and Normal-types"`.

---

### Task 5: Burner Keeper and Superheater

**Rows:** CD-BRN-01, CD-BRN-02, CD-BRN-03, CD-BRN-04, CD-BRN-05, CD-BRN-06, CD-SUP-01, CD-SUP-02, CD-SUP-03,
CD-SUP-04.

**Files:**
- Create: `src/main/java/codes/honor/cobblemondynamics/mixin/create/BlazeBurnerBlockEntityMixin.java`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/create/PokemonHeat.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/create/BurnerHeatHandler.kt`
- Create: `src/main/resources/data/cobblemon_dynamics/cobblemon_dynamics/roles/burner_keeper.json`
- Create: `src/main/resources/data/cobblemon_dynamics/cobblemon_dynamics/roles/superheater.json`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/handler/create/HeatGateTest.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/create/BurnerTests.kt`

The mixin (research note):

```java
@Mixin(BlazeBurnerBlockEntity.class)
abstract class BlazeBurnerBlockEntityMixin {
    @Inject(method = "getHeatLevel()Lcom/simibubi/create/content/processing/burner/BlazeBurnerBlock$HeatLevel;",
            at = @At("RETURN"), cancellable = true)
    private void cobblemon_dynamics$heat(CallbackInfoReturnable<BlazeBurnerBlock.HeatLevel> cir) {
        BlazeBurnerBlock.HeatLevel ours = PokemonHeat.levelFor((BlazeBurnerBlockEntity) (Object) this);
        if (ours != null && ours.isAtLeast(cir.getReturnValue())) cir.setReturnValue(ours);
    }
}
```

`PokemonHeat` keeps a map from `(level, pos)` to the heat that a Working worker supplies; the handler sets
it in `onStart` and every tick in `applyWork`, and removes it in `onStop` (CD-BRN-06). The mixin only
changes the returned value, so the burner's fuel and burn time stay untouched (CD-BRN-04), and Create's own
tick writes the block state, which the boiler reads (CD-BRN-05, CD-SUP-03).

`BurnerHeatHandler` (`id = cobblemon_dynamics:burner_heat`): `matches` when the block entity is a
`BlazeBurnerBlockEntity` and the block state's heat level is not `NONE` (CD-BRN-02). `hasDemand` when the
block entity directly above is a `BasinBlockEntity` or a `FluidTankBlockEntity` (CD-BRN-03). The heat comes
from the role params: `HeatGate.heatFor(params, level)` returns `params.heat` when `level >= params.min_level`
(default 0) and `params.below_min_level_heat` otherwise.

`burner_keeper.json`: types `["fire"]`, priority 0, params `{ "heat": "kindled" }`, effect Flamethrower.
`superheater.json`: types `["dragon"]` (CD-SUP-02), priority 10, params
`{ "heat": "seething", "min_level": 50, "below_min_level_heat": "kindled" }`, effect Dragon Breath
(`dragonbreath`, `spray`, `special`, `cry`; actor `cobblemon:dragonbreath_actor`, target
`cobblemon:dragonbreath_target`).

- [ ] **Step 1: Failing tests** — `HeatGateTest`: Lv49 gives kindled and Lv50 gives seething (CD-SUP-01,
  CD-SUP-04); the Burner Keeper gives kindled at every level; a Fire-type Charmander is not eligible for the
  Superheater and a Dratini is (CD-SUP-02). `BurnerTests`: `cdBrn01KindledOneTickAfterWorking` (filled burner,
  no fuel, basin above), `cdBrn02EmptyBurnerIsNeverClaimed` (400 ticks), `cdBrn03NoBasinMeansStandby`,
  `cdBrn04FuelBurnsDownAsNormal` (two burners with 1,000 ticks of coal, one worked for 400 ticks: equal
  remaining burn time, read through an accessor or `saveWithoutMetadata` NBT), `cdBrn05BoilerCountsOneHeat`
  and `cdSup03BoilerCountsTwoHeat` (read `BoilerHeater.REGISTRY` for the burner's block state, and the boiler's
  `BoilerData` heat after `updateBoilerTemperature`), `cdBrn06ReleaseReturnsToSmouldering` (2 ticks),
  `cdSup01Lv50Seething`, `cdSup04Lv49ThenLv50` (level the Dragonair up through Cobblemon and read the
  heat and the boiler heat before and after).
- [ ] **Step 2: Write the code, run, commit** —
  `git commit -m "Kindle and superheat Blaze Burners with Fire- and Dragon-types"`.

---

### Task 6: Create leash resets and flip permissions

**Rows:** CD-LSH-22, CD-LSH-23, CD-SEC-10 (wheel and crank).

**Files:**
- Modify: `leash/LeashResets.kt` (a removed Create target is a target loss)
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/create/CreateLeashTests.kt`

- [ ] **Step 1: Failing GameTests** — `cdLsh22WrenchPickupClearsBinding` (sneak-use a Create wrench on a
  bound crank through `player.gameMode.useItemOn` with `AllItems.WRENCH` in the main hand and the player
  sneaking; 20 ticks later no binding), `cdLsh23ContraptionClearsBinding` (a mechanical piston pushes a block
  that carries a bound furnace into a contraption when it extends; 20 ticks after assembly no binding),
  `cdSec10StrangersAndIdleTargetsNeverFlip` (a second player clicks a worked wheel and a worked crank, and the
  binder clicks a wheel that no worker drives: no direction changes; the fan part is M7).
- [ ] **Step 2: Write the code, run everything, commit, push, open the pull request** —
  `./gradlew build runGameTestServer runGameTestServerCore`; `/commit`; `pr-runner` with base `main`.
