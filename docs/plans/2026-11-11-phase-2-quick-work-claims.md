# Quick Work respects land claims, and the load lines

Part of the [Phase 2 plan](2026-11-10-phase-2-plan.md). Read its Global Constraints, including the multiplayer rules,
first. Branch: `quick-work-claims`, from `main` after the Phase 2 gate (M8 Task 8). It is one pull request into `main`. Its
last task runs the Phase 2 gate report again (CD-REL-14), and the approver's Phase 2 play-test uses its jar.

**Milestone goal:** With FTB Chunks or Open Parties and Claims on the server, a Quick Work order starts only when the claim
mod lets the owner right-click the block. The order ends within 20 ticks when that right ends. The members of the claim's
team or party can Quick Work its machines, and so can its allies where the claim's rules let them. A stranger cannot. The
mod also logs three INFO lines at load, for its mixins, its payloads and its roles.

**Design:** [Amendment 1 of ADR-0013](../adr/0013-quick-work-for-party-pokemon.md#amendment-1-land-claims-2026-11-11), the
rows CD-QWK-26 to CD-QWK-35 and CD-CMP-08 to CD-CMP-10 of the [spec sheet](2026-11-09-v1-spec-sheet.md), and the amended
CD-QWK-23. Inputs R64, R65 and RES6 hold the rulings, the request and the research. The engine asks a small gate,
`LandClaims`, which names no class of a claim mod. Each claim mod adds its check to the gate from its own compat package,
behind `ModList.isLoaded`, as the Create integration does.

Before Task 1, check the references of this milestone against the jars (rule 3 of the index).

Checked on 2026-11-11 with `javap` on the jars of COHO 2.4.0 (copied from the cache `E:/rch/jars/2026-11-02/`) and on
`open-parties-and-claims-neoforge-1.21.1-0.33.0.jar` from Modrinth. The ADR amendment explains what each call decides and
does.

FTB Chunks 2101.1.23, package `dev.ftb.mods.ftbchunks.api` unless a line names another package of `dev.ftb.mods.ftbchunks`:

```text
FTBChunksAPI:          public static dev.ftb.mods.ftbchunks.api.FTBChunksAPI$API api();
FTBChunksAPI$API:      public abstract boolean isManagerLoaded();
                       public abstract dev.ftb.mods.ftbchunks.api.ClaimedChunkManager getManager();
                       public abstract dev.ftb.mods.ftbchunks.api.ClaimResult claimAsPlayer(net.minecraft.server.level.ServerPlayer,
                           net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, net.minecraft.world.level.ChunkPos, boolean);
ClaimedChunkManager:   public abstract boolean shouldPreventInteraction(net.minecraft.world.entity.Entity, net.minecraft.world.InteractionHand,
                           net.minecraft.core.BlockPos, dev.ftb.mods.ftbchunks.api.Protection, net.minecraft.world.entity.Entity);
                           (parameters 0 and 4 are @org.jetbrains.annotations.Nullable)
                       public abstract dev.ftb.mods.ftbchunks.api.ClaimedChunk getChunk(dev.ftb.mods.ftblibrary.math.ChunkDimPos);
Protection:            public static final dev.ftb.mods.ftbchunks.api.Protection INTERACT_BLOCK;
ClaimedChunk:          public abstract void unclaim(net.minecraft.commands.CommandSourceStack, boolean);
ClaimResult:           public abstract java.lang.String getResultId();
                       public default boolean isSuccess();
FTBChunksProperties:   public static final dev.ftb.mods.ftbteams.api.property.PrivacyProperty BLOCK_INTERACT_MODE;
neoforge.FTBChunksExpectedImpl: public static dev.ftb.mods.ftbchunks.api.Protection getBlockInteractProtection();   // returns INTERACT_BLOCK
```

FTB Teams 2101.1.12 and FTB Library 2101.1.39:

```text
ftbteams.data.TeamManagerImpl:  public static dev.ftb.mods.ftbteams.data.TeamManagerImpl INSTANCE;
                                public void playerLoggedIn(net.minecraft.server.level.ServerPlayer, java.util.UUID, java.lang.String);
                                public dev.ftb.mods.ftbteams.data.PartyTeam createParty(net.minecraft.server.level.ServerPlayer, java.lang.String)
                                    throws com.mojang.brigadier.exceptions.CommandSyntaxException;
ftbteams.data.PartyTeam:        public int join(net.minecraft.server.level.ServerPlayer) throws ...CommandSyntaxException;
                                public int addAlly(net.minecraft.commands.CommandSourceStack, java.util.Collection<com.mojang.authlib.GameProfile>)
                                    throws ...CommandSyntaxException;
                                public int leave(java.util.UUID) throws ...CommandSyntaxException;
                                public int forceDisband(net.minecraft.commands.CommandSourceStack) throws ...CommandSyntaxException;
ftbteams.api.Team:              public abstract <T> void setProperty(dev.ftb.mods.ftbteams.api.property.TeamProperty<T>, T);
ftbteams.api.property.PrivacyMode: public static final dev.ftb.mods.ftbteams.api.property.PrivacyMode PRIVATE;
ftblibrary.math.ChunkDimPos:    public dev.ftb.mods.ftblibrary.math.ChunkDimPos(net.minecraft.world.level.Level, net.minecraft.core.BlockPos);
```

Architectury API 13.0.11: `dev.architectury.hooks.level.entity.forge.PlayerHooksImpl.isFake(Player)` returns true for each
`ServerPlayer` whose class is not `ServerPlayer` itself.

Open Parties and Claims 0.33.0, package `xaero.pac.common`:

```text
server.api.OpenPACServerAPI:    public static xaero.pac.common.server.api.OpenPACServerAPI get(net.minecraft.server.MinecraftServer);
                                public xaero.pac.common.server.claims.protection.api.IChunkProtectionAPI getChunkProtection();
                                public xaero.pac.common.server.parties.party.api.IPartyManagerAPI getPartyManager();
                                public xaero.pac.common.server.claims.api.IServerClaimsManagerAPI getServerClaimsManager();
                                public xaero.pac.common.server.player.config.api.v2.IPlayerConfigManagerAPI getPlayerConfigManager();
IChunkProtectionAPI:            public abstract boolean onBlockInteraction(net.minecraft.world.entity.Entity, net.minecraft.world.InteractionHand,
                                    net.minecraft.world.item.ItemStack, net.minecraft.server.level.ServerLevel, net.minecraft.core.BlockPos,
                                    net.minecraft.core.Direction, boolean, boolean);
                                    (entity, hand and heldItem @javax.annotation.Nullable; world, pos and direction @Nonnull;
                                     the boolean parameters are named breaking and messages)
IPartyManagerAPI:               public abstract xaero.pac.common.server.parties.party.api.IServerPartyAPI createPartyForOwner(net.minecraft.world.entity.player.Player);
                                public abstract void removeParty(xaero.pac.common.server.parties.party.api.IServerPartyAPI);
IServerPartyAPI:                public abstract xaero.pac.common.parties.party.member.api.IPartyMemberAPI addMember(java.util.UUID,
                                    xaero.pac.common.parties.party.member.PartyMemberRank, java.lang.String);
                                public abstract xaero.pac.common.parties.party.member.api.IPartyMemberAPI removeMember(java.util.UUID);
                                public abstract void addAllyParty(java.util.UUID);
IPartyAPI:                      public abstract java.util.UUID getId();
PartyMemberRank:                public static final xaero.pac.common.parties.party.member.PartyMemberRank MEMBER;
IServerClaimsManagerAPI:        public abstract xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI claim(net.minecraft.resources.ResourceLocation,
                                    java.util.UUID, int, int, int, boolean);       // dimension, id, subConfigIndex, x, z, forceload
                                public abstract void unclaim(net.minecraft.resources.ResourceLocation, int, int);
v2.IPlayerConfigManagerAPI:     public abstract xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI getLoadedConfig(java.util.UUID);
v2.IPlayerConfigAPI:            public abstract <T> xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI$SetResult tryToSet(
                                    xaero.pac.common.server.player.config.api.v2.IPlayerConfigOptionSpecAPI<T>, T);
v2.PlayerConfigOptions:         public static final xaero.pac.common.server.player.config.api.v2.IPlayerConfigOptionSpecAPI<java.lang.String> FULL_ACCESS;
v2.IPlayerConfigAPI$SetResult:  SUCCESS, INVALID, ILLEGAL_OPTION, DEFAULTED, NOT_DIRECTLY_CONFIGURABLE
```

Checked in the same jars:

- `claims.protection.exceptions.fullAccess` defaults to `P` and is in the default `playerConfigurablePlayerConfigOptions`
  list, so the owner of a claim can set it to `A`. `claims.protection.exceptions.blocksByPlayers` defaults to `N`.
- The Maven artifacts of the [ADR amendment](../adr/0013-quick-work-for-party-pokemon.md#artifacts-for-gradle) have the
  same SHA-1 as the research jars.
- The mods.toml of FTB Chunks requires `architectury` `[13.0.11,)`, `ftblibrary` `[2101.1.39,)` and `ftbteams`
  `[2101.1.9,)`. Neither claim mod declares the other one as incompatible.
- The `[[mods]]` entries of the two jars name the mod ids `ftbchunks` (`modId = "ftbchunks"`) and
  `openpartiesandclaims` (`modId="openpartiesandclaims"`).
- FTB Chunks' `FTBChunks.blockRightClick` asks `shouldPreventInteraction` with the protection of
  `FTBChunksExpected.getBlockInteractProtection()` and no entity, so the check of this plan is the check of a right-click.

Inferred, and checked by Step 1 of Task 4:

- The GameTest server starts FTB Teams, FTB Chunks and Open Parties and Claims, because their server start listeners run on
  `GameTestServer` too.
- `TeamManagerImpl.INSTANCE.playerLoggedIn` gives a test player its own team, and FTB Chunks makes its team data from the
  team event that this call posts. Packets to the player go to its test connection.
- Without FTB Ranks, `FTBTUtils.canPlayerUseCommand` lets the party owner add members and allies.
- One alliance from the claim owner's party to C's party is enough for the group `A` of Open Parties and Claims.

---

### Task 1: The claim gate and the pure rules

**Rows:** CD-QWK-26 (the gate asks every loaded claim mod, and the refusal order stays that of CD-QWK-06 to CD-QWK-09),
CD-QWK-29 (the tick rule of the second check), CD-QWK-30 (a check that throws refuses, with one warning per cause),
CD-QWK-35 (no config key for the checks), CD-UI-12 (a lost permission reuses the refusal's lang key).

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/compat/LandClaims.kt`
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/engine/QuickWorkRules.kt`
- Test: create `src/test/kotlin/codes/honor/cobblemondynamics/compat/LandClaimGateTest.kt`, and modify
  `src/test/kotlin/codes/honor/cobblemondynamics/engine/QuickWorkRulesTest.kt` and
  `src/test/kotlin/codes/honor/cobblemondynamics/config/ConfigTest.kt`

**Interfaces:**
- Consumes: `OnceLogger`, `QuickWorkRules.NO_PERMISSION`.
- Produces:

```kotlin
fun interface LandClaimCheck { fun mayUse(owner: ServerPlayer, level: ServerLevel, pos: BlockPos): Boolean }
class LandClaimGate(warnings: OnceLogger) { fun <C> allows(checks: List<Pair<String, C>>, ask: (C) -> Boolean): Boolean }
object LandClaims { fun register(modId: String, check: LandClaimCheck); fun mayUse(owner: ServerPlayer, level: ServerLevel, pos: BlockPos): Boolean }
// QuickWorkRules: const val PERMISSION_TICKS = 20L; fun permissionDue(startedAt: Long, now: Long): Boolean
// QuickEnd: PERMISSION(QuickWorkRules.NO_PERMISSION)
```

- [ ] **Step 1: Failing unit tests.**

```kotlin
package codes.honor.cobblemondynamics.compat

import codes.honor.cobblemondynamics.OnceLogger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LandClaimGateTest {
    private val lines = mutableListOf<String>()
    private val gate = LandClaimGate(OnceLogger(lines::add))
    private val both = listOf("ftbchunks" to 1, "openpartiesandclaims" to 2)

    @Test
    fun `CD-QWK-26 every claim mod must allow, and the first refusal ends the questions`() {
        val asked = mutableListOf<Int>()
        assertTrue(gate.allows(both) { asked += it; true })
        assertEquals(listOf(1, 2), asked)
        asked.clear()
        assertFalse(gate.allows(both) { asked += it; it != 1 })
        assertEquals(listOf(1), asked)
        assertTrue(gate.allows(emptyList<Pair<String, Int>>()) { error("no claim mod to ask") })
        assertTrue(lines.isEmpty(), "$lines")
    }

    @Test
    fun `CD-QWK-30 a check that throws refuses, and each cause warns once`() {
        val thrown = listOf(RuntimeException("a"), NoSuchMethodError("shouldPreventInteraction"), NoClassDefFoundError("dev/ftb/X"))
        for (error in thrown) assertFalse(gate.allows(listOf("ftbchunks" to 1)) { throw error }, "$error")
        assertFalse(gate.allows(listOf("ftbchunks" to 1)) { throw RuntimeException("the same cause again") })
        assertEquals(3, lines.size, "$lines")
        assertTrue(lines.all { it.startsWith("Quick Work refuses orders while the claim check of ftbchunks fails: ") }, "$lines")
        assertFalse(gate.allows(listOf("openpartiesandclaims" to 2)) { throw RuntimeException("a") })
        assertEquals(4, lines.size, "another mod is another cause")
    }
}
```

```kotlin
// QuickWorkRulesTest
@Test
fun `CD-QWK-29 an order asks its permission again on every 20th tick after its start`() {
    assertFalse(QuickWorkRules.permissionDue(startedAt = 100, now = 100))
    assertFalse(QuickWorkRules.permissionDue(startedAt = 100, now = 119))
    assertTrue(QuickWorkRules.permissionDue(startedAt = 100, now = 120))
    assertFalse(QuickWorkRules.permissionDue(startedAt = 100, now = 121))
    assertTrue(QuickWorkRules.permissionDue(startedAt = 100, now = 140))
}

// The test `CD-UI-12 each end has its own message key, and a timeout reuses the leash's` gains this line, and its name ends
// "..., a timeout reuses the leash's and a lost permission the refusal's":
assertEquals(QuickWorkRules.NO_PERMISSION, QuickEnd.PERMISSION.messageKey)
```

```kotlin
// ConfigTest
@Test
fun `CD-QWK-35 the server config holds the keys of CD-CFG-01 and no other`() =
    assertEquals(CFG_01_KEYS.toSet(), keys(ServerConfig.SPEC.spec).toSet())

/** The full path of every value, as PlayerGuideTest reads it. */
private fun keys(config: UnmodifiableConfig, prefix: String = ""): List<String> = config.valueMap().flatMap { (name, value) ->
    val path = if (prefix.isEmpty()) name else "$prefix.$name"
    if (value is UnmodifiableConfig) keys(value, path) else listOf(path)
}
```

- [ ] **Step 2: Run** `./gradlew test --tests "codes.honor.cobblemondynamics.compat.LandClaimGateTest"` and
  `./gradlew test --tests "codes.honor.cobblemondynamics.engine.QuickWorkRulesTest"`. Both fail to compile, because the
  gate, `permissionDue` and `QuickEnd.PERMISSION` do not exist. `ConfigTest` passes already: it guards CD-QWK-35.

- [ ] **Step 3: Code.**

```kotlin
package codes.honor.cobblemondynamics.compat

import codes.honor.cobblemondynamics.OnceLogger
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import java.util.concurrent.CopyOnWriteArrayList

/** The question of one claim mod: may [owner] right-click [pos] in [level] with an empty main hand (CD-QWK-26). */
fun interface LandClaimCheck {
    fun mayUse(owner: ServerPlayer, level: ServerLevel, pos: BlockPos): Boolean
}

/** Every check must allow, in order, and the first refusal ends the questions. A check that throws refuses (CD-QWK-30). */
class LandClaimGate(private val warnings: OnceLogger) {
    fun <C> allows(checks: List<Pair<String, C>>, ask: (C) -> Boolean): Boolean = checks.all { (id, check) ->
        try {
            ask(check)
        } catch (e: Exception) {
            refuse(id, e)
        } catch (e: LinkageError) {
            refuse(id, e)
        }
    }

    private fun refuse(id: String, e: Throwable): Boolean {
        warnings.warn("$id:${e.javaClass.name}") { "Quick Work refuses orders while the claim check of $id fails: $e" }
        return false
    }
}

/**
 * The claim mods that Quick Work asks (CD-QWK-26, CD-QWK-29). Mod construction fills the list, as it fills HandlerRegistry, and
 * nothing changes it later, so it holds no state of a world. This file names no class of a claim mod.
 */
object LandClaims {
    private val checks = CopyOnWriteArrayList<Pair<String, LandClaimCheck>>()
    private val gate = LandClaimGate(OnceLogger())

    fun register(modId: String, check: LandClaimCheck) {
        checks += modId to check
    }

    fun mayUse(owner: ServerPlayer, level: ServerLevel, pos: BlockPos): Boolean =
        gate.allows(checks) { it.mayUse(owner, level, pos) }
}
```

  `QuickWorkRules` gains `const val PERMISSION_TICKS = 20L` and
  `fun permissionDue(startedAt: Long, now: Long): Boolean = now > startedAt && (now - startedAt) % PERMISSION_TICKS == 0L`,
  with the KDoc "CD-QWK-29: an order asks its permission again on every 20th tick after its start." `QuickEnd` gains
  `PERMISSION(QuickWorkRules.NO_PERMISSION)`, so an order that loses its permission ends with "You may not use {target}".
  No lang key changes.

- [ ] **Step 4: Run** the two tests of Step 2 and `./gradlew test --tests "codes.honor.cobblemondynamics.config.ConfigTest"`,
  then `./gradlew build`. `LangTest` still passes, because the end reuses a key.

- [ ] **Step 5: Commit:** `git commit -m "Add the land claim gate and the rule of the second permission check [rag:1]"`.

---

### Task 2: The engine asks the gate at the start and every 20 ticks

**Rows:** CD-QWK-26 (the refusal at the start), CD-QWK-29 (the end within 20 ticks), CD-QWK-30 (a check that throws gives
no order), CD-QWK-23 (the amended cost, by inspection), CD-QWK-31 (the engine passes the owner from `PlayerList.getPlayer`),
CD-QWK-32 (the engine's message comes after the check).

**Files:**
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/engine/WorkEngine.kt` (`start`, `tickOrder` and a new private
  `mayUse`), `src/main/kotlin/codes/honor/cobblemondynamics/engine/QuickRuntime.kt`
- Create: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/TestLandClaims.kt`,
  `src/gametest/kotlin/codes/honor/cobblemondynamics/test/QuickWorkClaimTests.kt`
- Modify: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/DynamicsTestMod.kt`

**Interfaces:**
- Consumes: `LandClaims.mayUse`, `QuickWorkRules.permissionDue`, `QuickEnd.PERMISSION`.
- Produces: `QuickRuntime(level, owner, aimed, canonical, startedAt, targetName, slot, workArea)`, and the test claim provider
  `TestLandClaims` with the sets `refused` and `throwing`.

- [ ] **Step 1: Failing GameTests.** `TestLandClaims` is the test mod's claim provider. `DynamicsTestMod` registers it, as
  it registers `ProtectedBlocks`. With empty sets it allows every block, so it changes no other test.

```kotlin
package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.compat.LandClaims
import net.minecraft.core.BlockPos

/** A claim mod for the engine path (CD-QWK-26, CD-QWK-29, CD-QWK-30). Tests add absolute positions and remove them after the test. */
object TestLandClaims {
    val refused: MutableSet<BlockPos> = HashSet()
    val throwing: MutableSet<BlockPos> = HashSet()

    fun register() = LandClaims.register(TEST_MOD_ID) { _, _, pos ->
        if (pos in throwing) throw IllegalStateException("the test claim check fails at $pos")
        pos !in refused
    }
}
```

  `QuickWorkClaimTests` uses the scene of `QuickWorkTests` (furnaces A and B with raw iron, a listed owner 10 blocks east
  of A who looks at A) in its own batch, `quick_work_claims`. Each test removes its positions from both sets in
  `TestKit.afterTest`.
  - `cdQwk26RefusedClaimGivesNoOrder`: `refused` holds furnace A. The owner sends out a Charmander at A. Then the test
    removes A from `refused`, and the owner sends out a second Charmander at A. It fails if the first Charmander gets an
    order or a claim on any tick, if "You may not use Furnace" is missing, or if the second Charmander gets no order within
    100 ticks.
  - `cdQwk29LostPermissionEndsTheOrder`: the Charmander works furnace A. On the first Working tick the test adds A to
    `refused`. It fails if the order still exists 21 ticks later, or if "You may not use Furnace" is missing.
  - `cdQwk30ThrowingCheckRefuses`: `throwing` holds furnace A, and the owner sends out a Charmander at A. It fails if the
    Charmander gets an order or a claim, or if "You may not use Furnace" is missing. The unit test of Task 1 checks the
    warning.

- [ ] **Step 2: Run** `./gradlew runGameTestServer`. The three tests fail: the engine asks no claim provider, so the first
  Charmander gets an order, the order of `cdQwk29` runs on, and the third Charmander works.

- [ ] **Step 3: Code.** One private function holds the whole permission, which the start and the second check share:

```kotlin
/** CD-QWK-06, CD-QWK-26: the permission of an order. The start and every 20th tick of the order ask it (CD-QWK-29). */
private fun mayUse(owner: ServerPlayer, level: ServerLevel, pos: BlockPos): Boolean =
    !owner.isSpectator && owner.mayInteract(level, pos) && LandClaims.mayUse(owner, level, pos)
```

  - `start`: `mayUse = mayUse(owner, t.level, canonical)` replaces `!owner.isSpectator && owner.mayInteract(t.level,
    canonical)`. The refusal still goes out through `tell` after the check, so the engine's message is the last one in that
    tick (CD-QWK-32). The new `QuickRuntime` gets `canonical = canonical` and `startedAt = t.now`.
  - `tickOrder`, after the end rule:

```kotlin
QuickWorkRules.endReason(watch)?.let { return endOrder(t, order, it) }
// CD-QWK-29: the end rule passed, so the owner is online and in the order's level, which the FTB Chunks check needs.
if (owner != null && QuickWorkRules.permissionDue(order.startedAt, t.now) && !mayUse(owner, t.level, order.canonical)) {
    return endOrder(t, order, QuickEnd.PERMISSION)
}
```

  - `QuickRuntime` gains `val canonical: BlockPos` (the target of its claim) and `val startedAt: Long` (the game time of the
    start), after `aimed`.

- [ ] **Step 4: Run** `./gradlew runGameTestServer` and `./gradlew runGameTestServerCore`. Every test passes, the
  `QuickWorkTests` included: the test provider allows them.

- [ ] **Step 5: Inspect (CD-QWK-23, CD-QWK-31).** Read `tickOrder`: the claim checks run only when `permissionDue` is true,
  and only for `order.canonical`. Both calls of `mayUse` take the player that `server.playerList.getPlayer` returned for
  the owner's UUID (`onSentOut` and `tickOrder`). Record both readings in the pull request body.

- [ ] **Step 6: Multiplayer check.** Read the new code against the multiplayer rules of the index:
  - The gate keeps no state of a world or a player. Mod construction fills its list, and nothing changes it later.
  - Each check asks about the owner of the order, by the owner's UUID, never about another player.
  - The message goes to the owner only, through `tell`.
  - The check runs on the server thread, inside the level tick.

- [ ] **Step 7: Commit:** `git commit -m "Ask the land claim gate when an order starts and every 20 ticks [rag:1]"`.

---

### Task 3: The FTB Chunks and Open Parties and Claims checks

**Rows:** CD-QWK-27 and CD-QWK-28 (the checks of the two claim mods), CD-QWK-31 (each check passes on the owner that the
engine gives it), CD-QWK-32 (no message from Open Parties and Claims), CD-QWK-33 (the compat classes load only with their
mod), CD-QWK-34 (optional entries with any version in `neoforge.mods.toml`).

**Files:**
- Modify: `gradle.properties`, `build.gradle` (two repositories, four compile-only dependencies),
  `src/main/templates/META-INF/neoforge.mods.toml` (two optional dependencies)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/compat/ftbchunks/FtbChunksCompat.kt`,
  `src/main/kotlin/codes/honor/cobblemondynamics/compat/opac/OpacCompat.kt`
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/CobblemonDynamics.kt`
- Test: modify `src/test/kotlin/codes/honor/cobblemondynamics/build/ModMetadataTest.kt`, and create
  `src/test/kotlin/codes/honor/cobblemondynamics/build/CompatImportsTest.kt`

**Interfaces:**
- Consumes: `LandClaims.register`, and the FTB Chunks and Open Parties and Claims calls of the checked list above.
- Produces: `FtbChunksCompat.register()` and `OpacCompat.register()`.

- [ ] **Step 1: Failing unit tests.**

```kotlin
// ModMetadataTest
@Test
fun `CD-QWK-34 the claim mods are optional with any version`() {
    for (id in listOf("ftbchunks", "openpartiesandclaims")) {
        val d = requireDependency(id)
        assertEquals("optional", d.getString("type"), id)
        assertEquals("*", d.getString("versionRange"), id)
        assertEquals("BOTH", d.getString("side"), id)
    }
    assertNull(dependency("ftbteams"), "FTB Teams is a dependency of FTB Chunks, not of this mod")
}
```

```kotlin
package codes.honor.cobblemondynamics.build

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class CompatImportsTest {
    private val root = File(System.getProperty("cd.projectDir"))

    @Test
    fun `CD-QWK-33 only the compat package of a claim mod names it, and only behind isLoaded`() {
        val sources = File(root, "src/main").walk().filter { it.extension == "kt" || it.extension == "java" }
        for (file in sources) {
            val text = file.readText()
            val path = file.invariantSeparatorsPath
            if ("dev.ftb." in text) assertTrue("/compat/ftbchunks/" in path, "$path names FTB")
            if ("xaero.pac." in text) assertTrue("/compat/opac/" in path, "$path names Open Parties and Claims")
        }
        val mod = File(root, "src/main/kotlin/codes/honor/cobblemondynamics/CobblemonDynamics.kt").readText()
        assertTrue("if (ModList.get().isLoaded(\"ftbchunks\")) FtbChunksCompat.register()" in mod)
        assertTrue("if (ModList.get().isLoaded(\"openpartiesandclaims\")) OpacCompat.register()" in mod)
    }
}
```

- [ ] **Step 2: Run** `./gradlew test --tests "codes.honor.cobblemondynamics.build.CompatImportsTest"` and
  `./gradlew test --tests "codes.honor.cobblemondynamics.build.ModMetadataTest"`. Both fail: the mod has no `register`
  call for either claim mod, and `neoforge.mods.toml` has no entry for either one.

- [ ] **Step 3: Build files.** `src/main/templates/META-INF/neoforge.mods.toml` gains two entries after the one of
  `cobblebase`, as the coordinator ruled on 2026-11-11. They match the entries of Cobbleworkers and Cobblebase, and the
  mod ids are those of the jars:

```toml
[[dependencies.${mod_id}]]
modId="ftbchunks"
type="optional"
versionRange="*"
ordering="AFTER"
side="BOTH"

[[dependencies.${mod_id}]]
modId="openpartiesandclaims"
type="optional"
versionRange="*"
ordering="AFTER"
side="BOTH"
```

  `gradle.properties` gains:

```properties
ftb_chunks_version=2101.1.23
ftb_teams_version=2101.1.12
ftb_library_version=2101.1.39
architectury_version=13.0.11+neoforge
opac_version=neoforge-1.21.1-0.33.0
```

  `build.gradle` gains two repositories, each filtered to its group, and the compile-only dependencies. FTB Teams and FTB
  Library are in the list because types of the FTB Chunks API come from them. Architectury is needed only at run time
  (Task 4).

```groovy
    maven {
        name = 'FTB'
        url = 'https://maven.ftb.dev/releases'
        content { includeGroup 'dev.ftb.mods' }
    }
    maven {
        name = 'Modrinth'
        url = 'https://api.modrinth.com/maven'
        content { includeGroup 'maven.modrinth' }
    }

    // CD-QWK-27, CD-QWK-28, CD-QWK-34: the claim mods stay optional. Only compat/ftbchunks/ and compat/opac/ name them.
    compileOnly("dev.ftb.mods:ftb-chunks-neoforge:${ftb_chunks_version}") { transitive = false }
    compileOnly("dev.ftb.mods:ftb-teams-neoforge:${ftb_teams_version}") { transitive = false }
    compileOnly("dev.ftb.mods:ftb-library-neoforge:${ftb_library_version}") { transitive = false }
    compileOnly("maven.modrinth:open-parties-and-claims:${opac_version}") { transitive = false }
```

- [ ] **Step 4: Code.**

```kotlin
package codes.honor.cobblemondynamics.compat.ftbchunks

import codes.honor.cobblemondynamics.compat.LandClaims
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI
import dev.ftb.mods.ftbchunks.api.Protection
import net.minecraft.world.InteractionHand

/**
 * The FTB Chunks check of Quick Work (CD-QWK-27). Every class that names an FTB type sits in this package, and only [register] is
 * called from outside, behind `ModList.isLoaded("ftbchunks")`, so no FTB class loads without FTB Chunks (CD-QWK-33).
 */
object FtbChunksCompat {
    fun register() = LandClaims.register("ftbchunks") { owner, level, pos ->
        val api = FTBChunksAPI.api()
        // FTB Chunks reads the chunk and the block of player.level(). It shows its own action-bar text when it prevents.
        owner.level() === level && api.isManagerLoaded &&
            !api.manager.shouldPreventInteraction(owner, InteractionHand.MAIN_HAND, pos, Protection.INTERACT_BLOCK, null)
    }
}
```

```kotlin
package codes.honor.cobblemondynamics.compat.opac

import codes.honor.cobblemondynamics.compat.LandClaims
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import xaero.pac.common.server.api.OpenPACServerAPI

/**
 * The Open Parties and Claims check of Quick Work (CD-QWK-28). Only [register] is called from outside, behind
 * `ModList.isLoaded("openpartiesandclaims")` (CD-QWK-33).
 */
object OpacCompat {
    fun register() = LandClaims.register("openpartiesandclaims") { owner, level, pos ->
        // An empty hand with breaking false skips the item checks, and messages false sends nothing (CD-QWK-32).
        !OpenPACServerAPI.get(level.server).chunkProtection
            .onBlockInteraction(owner, InteractionHand.MAIN_HAND, ItemStack.EMPTY, level, pos, Direction.UP, false, false)
    }
}
```

  `CobblemonDynamics`, after the Create line:

```kotlin
// CD-QWK-33: each claim compat class loads only when its mod is present.
if (ModList.get().isLoaded("ftbchunks")) FtbChunksCompat.register()
if (ModList.get().isLoaded("openpartiesandclaims")) OpacCompat.register()
```

- [ ] **Step 5: Run** `./gradlew build`. `CompatImportsTest` and `ModMetadataTest` pass, and `verifyJar` shows that the jar
  holds no FTB or Open Parties and Claims class. `./gradlew runGameTestServer` and `./gradlew runGameTestServerCore` load neither claim mod, so
  neither compat class loads there (CD-QWK-33).

- [ ] **Step 6: Commit:** `git commit -m "Ask FTB Chunks and Open Parties and Claims before Quick Work [rag:1]"`.

---

### Task 4: GameTests with the real claim mods

> **Deferred by the approver's time ruling (2026-11-11).** The approver, 2026-11-11: "Skip the client tests and any tests that you're confident about with the code. I want the new JAR to be done within 30mins, ideally." The `1.0.0-beta.2` build ships Tasks 1, 2, 3 and 5 without
> this task, so no automated test has run the checks against the real FTB Chunks or Open Parties and Claims yet. A follow-up
> does this task. The build found these facts for it on 2026-11-11:
>
> - `ServerPlayer.displayClientMessage(Component, boolean)` calls `sendSystemMessage(Component, boolean)`, which sends
>   `new ClientboundSystemChatPacket(component, overlay)` (`javap -c` on `neoforge-21.1.265-merged.jar`). So an action-bar
>   message, of Quick Work and of FTB Chunks' `PlayerNotifier`, is a `ClientboundSystemChatPacket` whose `overlay()` is true,
>   not a `ClientboundSetActionBarTextPacket`. `lastActionBar` must read those packets, and the chat check of
>   `cdQwk28Qwk32StrangerGetsNoOrder` must count only the system chat packets whose `overlay()` is false.
> - `ICommonPacketListener.hasChannel` reads `connection.channel()` (`NetworkRegistry.hasChannel`, then
>   `ChannelAttributes.getPayloadSetup`, NeoForge 21.1.265 sources). The connection of `plainOwner` has no Netty channel, so
>   the call fails (inferred). FTB Teams' `syncAllToPlayer` and FTB Library's `NetworkHelperImpl.sendTo` ask it, so the
>   listener of `plainOwner` overrides `hasChannel(ResourceLocation)`.
> - `ChunkTeamDataImpl` starts with `maxClaimChunks = -1`, and only `updateLimits()` sets it. FTB Chunks calls it from its
>   `TeamEvent.PLAYER_LOGGED_IN` listener, so `FtbChunksFixture.login` must come before `claim`.
> - `PartyTeam.join(ServerPlayer)` needs no invite. `createParty` and `addAlly` with a player source ask the command nodes
>   `ftbteams.party.create` (no party yet) and `ftbteams.party.allies.add` (officer or better) through
>   `FTBTUtils.canPlayerUseCommand`.
> - Open Parties and Claims reads the sub-config index -1 as the main config (`PlayerConfig.getEffectiveSubConfig(int)`), so
>   `OpacFixture.claim` passes -1.

**Rows:** CD-QWK-27 and CD-QWK-28 (members, allies and strangers), CD-QWK-29 (a member who leaves, a chunk that a stranger
claims, a member that a party removes), CD-QWK-31 (owners of the exact class `ServerPlayer`), CD-QWK-32 (the last
action-bar packet, and no chat from Open Parties and Claims).

**Files:**
- Modify: `build.gradle` (two source sets, their configurations, two test mods and two runs, with the versions that Task 3
  added to `gradle.properties`)
- Modify: `src/gametest/kotlin-shared/codes/honor/cobblemondynamics/test/TestKit.kt` (`plainOwner` and `PlainOwner`)
- Create: `src/gametest/kotlin-claims/codes/honor/cobblemondynamics/test/claims/ClaimScenes.kt`
- Create: `src/gametestFtbChunks/kotlin/codes/honor/cobblemondynamics/ftbtest/FtbChunksTestMod.kt`, `FtbChunksFixture.kt`,
  `FtbChunksClaimTests.kt`, and `src/gametestFtbChunks/resources/META-INF/neoforge.mods.toml`
- Create: `src/gametestOpac/kotlin/codes/honor/cobblemondynamics/opactest/OpacTestMod.kt`, `OpacFixture.kt`,
  `OpacClaimTests.kt`, and `src/gametestOpac/resources/META-INF/neoforge.mods.toml`
- Modify: the local notes file. Do not modify `.github/workflows/build.yml`: CI does not run the two claim runs.

**Interfaces:**
- Consumes: the fixture calls of the checked list above, `TestKit.listed`, `TestKit.pressSendOut`, `TestWorld.register`.
- Produces: `TestKit.plainOwner(helper, rel, name): TestKit.PlainOwner`, the runs `runGameTestServerFtbChunks` and
  `runGameTestServerOpac`.

- [ ] **Step 1: The runs, and a failing fixture test for each mod.** Each run loads the mod set of the core run (Cobblemon
  and KotlinForForge, no Create and no AE2) and one claim mod, so a fault of one claim mod never hides a result of the
  other one. Both runs share `src/gametest/kotlin-shared` and the new `src/gametest/kotlin-claims`, which name no class of a
  claim mod.

```groovy
sourceSets {
    // CD-QWK-27 to CD-QWK-32: one claim mod each, on the mod set of the core run.
    gametestFtbChunks {
        compileClasspath += sourceSets.main.output
        runtimeClasspath += sourceSets.main.output
        kotlin.srcDir 'src/gametest/kotlin-shared'
        kotlin.srcDir 'src/gametest/kotlin-claims'
        resources.srcDir 'src/gametest/resources-shared'
    }
    gametestOpac {
        compileClasspath += sourceSets.main.output
        runtimeClasspath += sourceSets.main.output
        kotlin.srcDir 'src/gametest/kotlin-shared'
        kotlin.srcDir 'src/gametest/kotlin-claims'
        resources.srcDir 'src/gametest/resources-shared'
    }
}
configurations {
    gametestFtbChunksImplementation.extendsFrom implementation
    gametestFtbChunksRuntimeOnly.extendsFrom runtimeOnly
    gametestOpacImplementation.extendsFrom implementation
    gametestOpacRuntimeOnly.extendsFrom runtimeOnly
}
neoForge {
    addModdingDependenciesTo sourceSets.gametestFtbChunks
    addModdingDependenciesTo sourceSets.gametestOpac
    mods {
        cobblemon_dynamics_ftbtest { sourceSet sourceSets.gametestFtbChunks }
        cobblemon_dynamics_opactest { sourceSet sourceSets.gametestOpac }
    }
    runs {
        gameTestServerFtbChunks {
            type = 'gameTestServer'
            sourceSet = sourceSets.gametestFtbChunks
            gameDirectory = project.file('run/gametest-ftbchunks')
            loadedMods = [mods."${mod_id}", mods.cobblemon_dynamics_ftbtest]
            systemProperty 'neoforge.enabledGameTestNamespaces', mod_id
        }
        gameTestServerOpac {
            type = 'gameTestServer'
            sourceSet = sourceSets.gametestOpac
            gameDirectory = project.file('run/gametest-opac')
            loadedMods = [mods."${mod_id}", mods.cobblemon_dynamics_opactest]
            systemProperty 'neoforge.enabledGameTestNamespaces', mod_id
        }
    }
}
dependencies {
    gametestFtbChunksImplementation("dev.ftb.mods:ftb-chunks-neoforge:${ftb_chunks_version}") { transitive = false }
    gametestFtbChunksImplementation("dev.ftb.mods:ftb-teams-neoforge:${ftb_teams_version}") { transitive = false }
    gametestFtbChunksImplementation("dev.ftb.mods:ftb-library-neoforge:${ftb_library_version}") { transitive = false }
    gametestFtbChunksRuntimeOnly("maven.modrinth:architectury-api:${architectury_version}") { transitive = false }
    gametestOpacImplementation("maven.modrinth:open-parties-and-claims:${opac_version}") { transitive = false }
}
```

  Each test mod's `neoforge.mods.toml` copies the one of `cobblemon_dynamics_coretest`, with the mod id
  `cobblemon_dynamics_ftbtest` or `cobblemon_dynamics_opactest`, and a second required dependency on `ftbchunks` or
  `openpartiesandclaims`. Each test mod class calls `TestWorld.register()`, as `CoreTestMod` does.

  `TestKit` gains an owner of the exact class `ServerPlayer`. Architectury counts every subclass of `ServerPlayer` as a fake
  player, a `FakePlayer` included, so FTB Chunks judges only this kind of owner as a player:

```kotlin
/** A player of the exact class ServerPlayer and the packets that the server sent it (CD-QWK-31, CD-QWK-32). */
class PlainOwner(val player: ServerPlayer, val packets: MutableList<Packet<*>>)

/**
 * An owner at [rel] that `PlayerList.getPlayer` finds until the test ends, as [listedOwner], but of the exact class ServerPlayer.
 * Its connection has no channel and keeps every packet, as the listener of `EffectPacketTests` does.
 */
fun plainOwner(helper: GameTestHelper, rel: BlockPos, name: String = "owner"): PlainOwner {
    val uuid = UUID.randomUUID()
    val profile = GameProfile(uuid, "${name}_${uuid.toString().take(6)}")
    val server = helper.level.server
    val player = ServerPlayer(server, helper.level, profile, ClientInformation.createDefault())
    val packets = ArrayList<Packet<*>>()
    // The constructor sets player.connection to this listener.
    object : ServerGamePacketListenerImpl(server, Connection(PacketFlow.CLIENTBOUND), player, CommonListenerCookie.createInitial(profile, false)) {
        override fun send(packet: Packet<*>) {
            packets += packet
        }

        override fun send(packet: Packet<*>, listener: PacketSendListener?) {
            packets += packet
        }
    }
    return PlainOwner(listed(helper, player, rel), packets)
}
```

  `ClaimScenes` (package `codes.honor.cobblemondynamics.test.claims`) holds the shared scene: furnace A with 8 raw iron at
  (4, 2, 11) of `floor_23x10x23`, plain owners at (14, 2, 11) who look at A, `sendOutAt`, `hasOrder` (the engine's
  `isQuickWorker`), and `lastActionBar(owner)`, the text of the last `ClientboundSetActionBarTextPacket` in
  `owner.packets`. Every claim test has its own batch, because a land claim covers a whole chunk and two tests of one batch
  can share a chunk. Each test unclaims its chunk and removes its parties in `TestKit.afterTest`.

```kotlin
package codes.honor.cobblemondynamics.ftbtest

/** FTB Teams makes the own team of a player only at login, and test players never log in (RES6). */
object FtbChunksFixture {
    fun login(vararg players: ServerPlayer) = players.forEach { TeamManagerImpl.INSTANCE.playerLoggedIn(it, it.uuid, it.gameProfile.name) }

    fun party(owner: ServerPlayer, members: List<ServerPlayer>, allies: List<ServerPlayer>): PartyTeam {
        val party = TeamManagerImpl.INSTANCE.createParty(owner, "qw_${owner.uuid.toString().take(8)}")
        members.forEach { party.join(it) }
        if (allies.isNotEmpty()) party.addAlly(owner.createCommandSourceStack(), allies.map { it.gameProfile })
        return party
    }

    fun claim(player: ServerPlayer, level: ServerLevel, pos: BlockPos) {
        val result = FTBChunksAPI.api().claimAsPlayer(player, level.dimension(), ChunkPos(pos), false)
        if (!result.isSuccess) throw GameTestAssertException("FTB Chunks refused the claim: ${result.resultId}")
    }

    fun unclaim(level: ServerLevel, pos: BlockPos) =
        FTBChunksAPI.api().manager.getChunk(ChunkDimPos(level, pos))?.unclaim(level.server.createCommandSourceStack(), false)

    fun disband(party: PartyTeam, level: ServerLevel) = party.forceDisband(level.server.createCommandSourceStack())
}
```

```kotlin
package codes.honor.cobblemondynamics.opactest

object OpacFixture {
    private fun api(level: ServerLevel) = OpenPACServerAPI.get(level.server)

    fun party(owner: ServerPlayer, members: List<ServerPlayer>): IServerPartyAPI {
        val party = api(owner.serverLevel()).partyManager.createPartyForOwner(owner)
            ?: throw GameTestAssertException("Open Parties and Claims made no party for ${owner.gameProfile.name}")
        members.forEach { party.addMember(it.uuid, PartyMemberRank.MEMBER, it.gameProfile.name) }
        return party
    }

    fun claim(owner: ServerPlayer, level: ServerLevel, pos: BlockPos) {
        val chunk = ChunkPos(pos)
        api(level).serverClaimsManager.claim(level.dimension().location(), owner.uuid, 0, chunk.x, chunk.z, false)
    }

    fun unclaim(level: ServerLevel, pos: BlockPos) {
        val chunk = ChunkPos(pos)
        api(level).serverClaimsManager.unclaim(level.dimension().location(), chunk.x, chunk.z)
    }

    /** The owner's `claims.protection.exceptions.fullAccess` becomes `A`, which the default player config lets players set. */
    fun giveAlliesFullAccess(owner: ServerPlayer) {
        val result = api(owner.serverLevel()).playerConfigManager.getLoadedConfig(owner.uuid).tryToSet(PlayerConfigOptions.FULL_ACCESS, "A")
        if (result != IPlayerConfigAPI.SetResult.SUCCESS) throw GameTestAssertException("fullAccess: $result")
    }
}
```

  The first test of each run checks the fixture against the claim mod's own check, before any Quick Work test:
  - `ftbFixtureAnswersLikeARightClick`: A's party claims the chunk of furnace A, with B a member and D a stranger.
    `shouldPreventInteraction(…, Protection.INTERACT_BLOCK, null)` must be false for A and B and true for D.
  - `opacFixtureAnswersLikeARightClick`: A claims the chunk, with B a member of A's party. `onBlockInteraction` with an
    empty hand must be false for A and B and true for D.

- [ ] **Step 2: Run** `./gradlew runGameTestServerFtbChunks` and `./gradlew runGameTestServerOpac`, one JVM at a time. Read
  `run/gametest-ftbchunks/logs/latest.log` and `run/gametest-opac/logs/latest.log`. The fixture tests must pass before
  Step 3. If a fixture test fails, fix the fixture first, and record each fact of the "Inferred" list above that the run
  proved or disproved. If the GameTest server cannot host a claim mod at all, stop and report it: the fallback is the test claim
  provider of Task 2 for the engine path, plus the test pack's dedicated servers of Task 6.

- [ ] **Step 3: Failing Quick Work tests.** Each test sends out a Charmander at furnace A with `ClaimScenes.sendOutAt`, in its
  own batch.
  - FTB Chunks run (`FtbChunksClaimTests`), A's party claims the chunk of furnace A, B is a member, C an ally (added with
    `addAlly`) and D a stranger:
    - `cdQwk27ClaimOwnerGetsAnOrder`, `cdQwk27MemberGetsAnOrder`, `cdQwk27AllyGetsAnOrder`: the order exists within 100
      ticks of the send-out.
    - `cdQwk27Qwk32StrangerGetsNoOrder`: D gets no order and no claim on any tick, and the last action-bar packet that D got
      in the refusal tick reads "You may not use Furnace".
    - `cdQwk27AllyGetsNoOrderInPrivateMode`: the party sets `FTBChunksProperties.BLOCK_INTERACT_MODE` to
      `PrivacyMode.PRIVATE` with `setProperty`, and C gets no order.
    - `cdQwk29MemberWhoLeavesThePartyStops`: B's Charmander works furnace A, and B leaves the party (`party.leave(b.uuid)`).
      The order is gone 21 ticks later, with "You may not use Furnace".
    - `cdQwk29ClaimOfARunningOrderStopsIt`: the chunk starts unclaimed, and a stranger's Charmander works furnace A. Then
      the party of A claims the chunk. The order is gone 21 ticks later, with the message.
  - Open Parties and Claims run (`OpacClaimTests`), A claims the chunk, B is a member of A's party, C a member of a party that
    A's party allies (`addAllyParty`) and D a stranger:
    - `cdQwk28ClaimOwnerGetsAnOrder`, `cdQwk28MemberGetsAnOrder`: the order exists within 100 ticks.
    - `cdQwk28AllyGetsNoOrderByDefault`, then `cdQwk28AllyGetsAnOrderWithFullAccessForAllies` after
      `OpacFixture.giveAlliesFullAccess(a)`.
    - `cdQwk28Qwk32StrangerGetsNoOrder`: D gets no order, the last action-bar packet reads "You may not use Furnace", and D
      got no `ClientboundSystemChatPacket`.
    - `cdQwk29RemovedMemberStops`: B's Charmander works furnace A, and A's party removes B (`removeMember`). The order is
      gone 21 ticks later, with the message.

  Run both runs again. The Quick Work tests pass only with the code of Tasks 2 and 3. Check that each test fails when you
  comment out the `register` call of its claim mod in `CobblemonDynamics`, then restore the call.

- [ ] **Step 4: Where the claim runs run.** By the coordinator's ruling of 2026-11-11, CI does not run
  `runGameTestServerFtbChunks` or `runGameTestServerOpac`, so `.github/workflows/build.yml` does not change. The two runs
  run in these places:
  - the pull request of this plan, which records both results in its body
  - each later pull request that touches the claims code: `compat/LandClaims.kt`, `compat/ftbchunks/`, `compat/opac/`,
    the permission checks of `WorkEngine` (`mayUse`, `start` and `tickOrder`), `src/gametest/kotlin-claims/`,
    `src/gametestFtbChunks/` and `src/gametestOpac/`. The development notes'
    pull request rules hold this rule.
  - each phase gate, whose report lists both commands (Task 7 Step 2)

  The test claim provider of Task 2 stays in the full run, which CI runs, so CI still tests the engine path of the claim
  checks. The FTB jars never go into a CI artifact or a log upload, because they are "All Rights Reserved".

- [ ] **Step 5: Gotchas.** Add to the local notes file what the runs showed, at least these three lines:
  - Architectury's `PlayerHooks.isFake` is true for every subclass of `ServerPlayer`, so FTB Chunks treats each
    `FakePlayer` and test player subclass as a fake player. Claim tests use `TestKit.plainOwner`.
  - FTB Teams gives a player a team only at login. Claim tests call `TeamManagerImpl.INSTANCE.playerLoggedIn` first.
  - CI does not run `runGameTestServerFtbChunks` or `runGameTestServerOpac`, so a green CI says nothing about the real
    claim mods. A pull request that touches the claims code runs both.

- [ ] **Step 6: Commit:** `git commit -m "Test the claim checks against FTB Chunks and Open Parties and Claims [rag:2]"`.

---

### Task 5: The INFO lines at load

**Rows:** CD-CMP-08 (the mixins), CD-CMP-09 (the payloads), CD-CMP-10 (the roles).

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/LoadLines.kt`, `src/main/kotlin/codes/honor/cobblemondynamics/LoadLog.kt`
- Modify: `src/main/java/codes/honor/cobblemondynamics/MixinState.java`,
  `src/main/java/codes/honor/cobblemondynamics/mixin/MixinPlugin.java`,
  `src/main/kotlin/codes/honor/cobblemondynamics/CobblemonDynamics.kt`,
  `src/main/kotlin/codes/honor/cobblemondynamics/net/DynamicsPayloads.kt`,
  `src/main/kotlin/codes/honor/cobblemondynamics/role/RoleSet.kt`, `role/MixinGate.kt`, `role/RoleRegistry.kt`
- Test: create `src/test/kotlin/codes/honor/cobblemondynamics/LoadLinesTest.kt` and `LoadLogTest.kt`, and modify
  `MixinStateTest.kt`, `role/RoleSetTest.kt` and `role/MixinGateTest.kt`

**Interfaces:**
- Produces:

```kotlin
object LoadLines {
    enum class MixinStatus { APPLIED, SKIPPED, NO_INJECTION, NOT_APPLIED_YET }
    data class MixinEntry(val name: String, val status: MixinStatus, val reason: String? = null)
    fun mixins(entries: List<MixinEntry>): String
    fun payloads(version: String, toClient: List<ResourceLocation>, toServer: List<ResourceLocation>): String
    fun roles(loaded: Int, active: Int, disabled: Map<ResourceLocation, String>): String
}
/** One INFO line per distinct text between two calls of newLoad. RoleRegistry's `logged` set moves here. */
class LoadLog(sink: (String) -> Unit) { fun newLoad(); fun info(line: String) }
// MixinState (Java): recordOffered(String), recordSkipped(String, String reason), recordApplied(String, ClassNode, boolean hasHandlers),
//                    offered(), skipped(), merged(), withHandlers(), applied(String)
// RoleSet: val disabled: Map<ResourceLocation, String>; MixinGate.apply adds its own reasons to the set that it returns
```

  The three lines, in the form that the tests check:

```text
Mixins: 8 applied [create.AirCurrentMixin, create.BlazeBurnerBlockEntityMixin, create.ItemUseOverridesMixin, create.WaterWheelBlockEntityMixin, create.WindmillBearingBlockEntityMixin, vanilla.EnchantmentMenuAccess, vanilla.EnchantmentMenuMixin, vanilla.WorkerPushMixin], 4 skipped [compat.cobblebase.AmbientBehaviorMixin (cobblebase absent), compat.cobblebase.BaseManagerMixin (cobblebase absent), compat.cobblebase.NavigationHelperMixin (cobblebase absent), compat.cobblebase.PassiveXpMixin (cobblebase absent)], 0 applied with no injection [], 0 not applied yet []
Payloads, version 3: 2 to the client [cobblemon_dynamics:pasture_panel_state, cobblemon_dynamics:leash_overview], 4 to the server [cobblemon_dynamics:work_mode_toggle, cobblemon_dynamics:pasture_panel_open, cobblemon_dynamics:pasture_panel_close, cobblemon_dynamics:leash_overview_request]
Roles: 16 loaded, 9 active, 7 disabled [cobblemon_dynamics:cranker (handler cobblemon_dynamics:hand_crank is not registered), ...]
```

- [ ] **Step 1: Failing unit tests.**
  - `LoadLinesTest`: `mixins` with 8 applied, 4 skipped because `cobblebase` is absent, 1 skipped with the reason
    `AirCurrent has no caughtEntities`, 1 with no injection and 2 not applied yet names each mixin under its heading, in
    name order, with each count. `payloads` with the six ids of the Network payloads table and version `3` gives the line
    above. `roles` over the 16 built-in role files (`BuiltInRoles`), with no handler for the 5 Create handler ids and
    `cobblemon_dynamics:muse` in `roles.disabled`, gives "Roles: 16 loaded, 8 active, 8 disabled", and each disabled id
    with its reason.
  - `LoadLogTest`: a line logs once, the same line again logs nothing, and after `newLoad()` it logs again.
  - `MixinStateTest`: a mixin that the plugin offered and skipped reads skipped with its reason. An accessor mixin with no
    handler reads applied after `postApply`. A mixin with a handler that its target never calls reads no injection.
  - `RoleSetTest` and `MixinGateTest`: `disabled` holds `roles.disabled` for a role of the config list, `handler <id> is not
    registered` for a role with no handler, and `its mixin <name> did not apply` for a role that the gate removes.

- [ ] **Step 2: Run** `./gradlew test`. The new tests fail to compile.

- [ ] **Step 3: Code.**
  - `MixinPlugin.shouldApplyMixin` records each mixin that Mixin offers it, and records the reason of each skip:
    `<mod id> absent` for a missing mod, and `<target simple name> has no <members>` for a missing shadow. Mixin asks the
    plugin while it prepares the config, before the targets load. `postApply` passes `MixinState.recordApplied` whether the
    mixin class declares a handler, a method whose name holds `cobblemon_dynamics$`. `MixinState.applied` keeps its meaning,
    so the role gates do not change.
  - `CobblemonDynamics` logs the mixin line once, in `FMLLoadCompleteEvent.enqueueWork`. By then the Create integration
    and the Cobblebase hooks have loaded their mixin targets. On a client, the two client mixins read "not applied yet",
    because their targets load when a pasture panel first opens. A dedicated server never offers them.
  - `DynamicsPayloads.register` keeps the id of each payload that it registers, by direction, and logs the payload line
    after the last registration.
  - `RoleRegistry` moves its `logged` set into a `LoadLog`. `replace` calls `newLoad()`, and `rebuild` logs the role line
    through `info`, so the line logs once for each role file load, and again after a config change only when it changes.
  - `RoleSet` keeps `disabled`. `MixinGate.apply` and `RoleSet.withoutHandlers` carry it over and add the roles that they
    remove.

- [ ] **Step 4: Run** `./gradlew build`, `./gradlew runGameTestServer` and `./gradlew runGameTestServerCore`. Then read the
  three lines in `run/gametest/logs/latest.log`: 8 mixins applied and 4 skipped, 6 payloads, and 16 roles loaded and active.
  CD-PERF-05 still holds: the lines come at load, not from a tick.

- [ ] **Step 5: Commit:** `git commit -m "Log the mixins, the payloads and the roles at load [rag:1]"`.

---

### Task 6: The test pack

> **Deferred by the approver's time ruling (2026-11-11).** The approver, 2026-11-11: "Skip the client tests and any tests that you're confident about with the code. I want the new JAR to be done within 30mins, ideally." The `1.0.0-beta.2` build ran no test pack case, so the
> dedicated servers of CD-QWK-33 and the load lines in the logs of real servers wait for a follow-up. The log of the build's
> `runGameTestServer` holds the three lines once each: 8 mixins applied and the 4 Cobblebase mixins skipped because
> `cobblebase` is absent, the 6 payloads of version 3, and "Roles: 16 loaded, 16 active, 0 disabled".

**Rows:** CD-QWK-33 (a dedicated server with each claim mod loads only that mod's compat package), CD-CMP-08 to CD-CMP-10
(the three lines in the logs of real servers).

**Files:**
- Modify: `scripts/testpack/mods.json`, `scripts/testpack/testpack.py`, `scripts/testpack/README.md`

- [ ] **Step 1: Pins.** The `compat` group of `mods.json` gains five pins. FTB publishes FTB Chunks, FTB Teams and FTB
  Library on its Maven, not on Modrinth, so their pins carry `"source": "maven"`, and `resolve` leaves such a pin as it is.
  Each SHA-512 below is that of the research jar, and the Maven SHA-1 of each jar matched the jar on 2026-11-11.

```json
"ftbchunks": {"source": "maven", "version": "2101.1.23", "filename": "ftb-chunks-neoforge-2101.1.23.jar",
  "url": "https://maven.ftb.dev/releases/dev/ftb/mods/ftb-chunks-neoforge/2101.1.23/ftb-chunks-neoforge-2101.1.23.jar",
  "sha512": "78a48e8a1f789827836c88046ad0ce07e3eb5b29cc8c681c8d22a556308c0fe13fad66475535e9a810450b86f660db51f8c582dabdee026bd80e4ae959fbac89"},
"ftbteams": {"source": "maven", "version": "2101.1.12", "filename": "ftb-teams-neoforge-2101.1.12.jar",
  "url": "https://maven.ftb.dev/releases/dev/ftb/mods/ftb-teams-neoforge/2101.1.12/ftb-teams-neoforge-2101.1.12.jar",
  "sha512": "c2c8853615c1ec97e0ace07c320db8f0ad4a18c59746e1d30c86f7af0e6ad36d9bbfe0ca6a365aa63d29244e1690323296fb29d99a38bab97e2c19181a57e376"},
"ftblibrary": {"source": "maven", "version": "2101.1.39", "filename": "ftb-library-neoforge-2101.1.39.jar",
  "url": "https://maven.ftb.dev/releases/dev/ftb/mods/ftb-library-neoforge/2101.1.39/ftb-library-neoforge-2101.1.39.jar",
  "sha512": "e28a5c032395b96ce696800ca7689315074110e572186d8ba08bdf326e0ff43f6fcce942c35a3fe60637dc2bf2077f04b3e222892e60016fe545339dcc7529aa"},
"architectury": {"project": "architectury-api", "version": "13.0.11", "version_number": "13.0.11+neoforge",
  "filename": "architectury-13.0.11-neoforge.jar",
  "url": "https://cdn.modrinth.com/data/lhGA9TYQ/versions/1IiqEQGl/architectury-13.0.11-neoforge.jar",
  "sha512": "d9f7c3bb8162577dfb461ffdf04bd6a3563c7586934a0e2a744c14421beffb8286f0d88d4c758317003f20f99fe8072a39b9d675af061e036970d36db36027f0"},
"openpartiesandclaims": {"project": "open-parties-and-claims", "version": "0.33.0", "version_number": "neoforge-1.21.1-0.33.0",
  "filename": "open-parties-and-claims-neoforge-1.21.1-0.33.0.jar",
  "url": "https://cdn.modrinth.com/data/gF3BGWvG/versions/yWmXD7GW/open-parties-and-claims-neoforge-1.21.1-0.33.0.jar",
  "sha512": "646fd9796ef2dfd1132e22aacf1a417639233d90fb7eb862a718badfed955d54714c88327b7ddc651f98cd5b7d2a80e8c91233d2232680d4998fdd10ef392ffb"}
```

- [ ] **Step 2: Failing cases.** `COMPAT_CASES` gains two cases, and each of the two old cases gains both claim packages in
  `absent`:

```python
CompatCase("compat_ftbchunks", ("ftbchunks", "ftbteams", "ftblibrary", "architectury"),
           OUR_PACKAGE + "compat.ftbchunks.FtbChunksCompat", (OUR_PACKAGE + "compat.opac.",)),
CompatCase("compat_opac", ("openpartiesandclaims",), OUR_PACKAGE + "compat.opac.OpacCompat",
           (OUR_PACKAGE + "compat.ftbchunks.",)),
```

  `scenario_smoke` also checks the three lines of Task 5: one `Mixins: 8 applied [` line with the 4 Cobblebase mixins
  skipped because `cobblebase` is absent, one payload line with the six ids, and one `Roles: 16 loaded, 16 active,
  0 disabled` line. The `without-create-ae2` case of `deps` checks a `Roles: 16 loaded, 9 active, 7 disabled` line that
  names the Wheel Driver, the Cranker, the Superheater, the three Fan Catalysts and Tailwind. Run
  `py scripts/testpack/testpack.py compat` and `py scripts/testpack/testpack.py smoke` on the commit of Task 4: both fail.

- [ ] **Step 3: Run** both commands and `py scripts/testpack/testpack.py deps` on the commit of Task 5. Each prints `PASS`.
  The README's case table gains the two cases, with `CD-QWK-33`, and its `smoke` and `deps` rows cite CD-CMP-08 to
  CD-CMP-10, so `scripts/gate_report.py` maps the rows to the test pack.

- [ ] **Step 4: Commit:** `git commit -m "Start a test server with each claim mod and check the load lines [rag:1]"`.

---

### Task 7: Docs, the gate report and the pull request

> **Partly deferred by the same ruling (2026-11-11).** The `1.0.0-beta.2` build changed only this plan and the testing
> claims of `docs/modpack-integration.md`. Steps 1 to 4 wait for the follow-up of Tasks 4 and 6.

**Rows:** CD-REL-08 (the player guide's Quick Work section), CD-REL-09 (no new Demo row), CD-REL-14 (the Phase 2 gate report
on the new commit), and the coverage of CD-QWK-23, CD-QWK-26 to CD-QWK-35 and CD-CMP-08 to CD-CMP-10.

**Files:**
- Modify: `ARCHITECTURE.md` (sections 2, 3, 6, 8, 10 and 13), `README.md` (the compatibility list)
- Modify: `scripts/gate_report.py` (`RUNS`, `PHASE_2_ROWS` and its docstring)
- Check: `docs/player-guide.md` and `docs/modpack-integration.md`, which this plan's pull request wrote on 2026-11-11
- Modify: `docs/testing/phase-2-gate.md` (through `scripts/gate_report.py`, and its known issues)

- [ ] **Step 1: Docs.**
  - `ARCHITECTURE.md`: section 2, the packages `compat/ftbchunks/` and `compat/opac/` and the file `compat/LandClaims.kt`.
    Section 3, Quick Work asks the land claim gate. Section 6, the order's tick asks its permission on every 20th tick.
    Section 8, the claim APIs of RES6. Section 10, the five artifacts of the ADR amendment, the two repositories and the
    optional entries of `ftbchunks` and `openpartiesandclaims` in `neoforge.mods.toml`. Section 13, the two claim runs,
    which CI does not run, `TestKit.plainOwner` and the test claim provider.
  - `README.md`: the compatibility list names FTB Chunks and Open Parties and Claims.
  - `docs/player-guide.md` and `docs/modpack-integration.md`: check the Quick Work texts, the tested versions and the three
    INFO lines against the code, and fix any difference in the same commit.
  - The manual checklist needs no change, because no new row has a Demo test (CD-REL-09).

- [ ] **Step 2: Tools.** `RUNS` in `scripts/gate_report.py` gains two runs: `Run("ftbchunks", "GameTests with FTB Chunks",
  "runGameTestServerFtbChunks", ROOT / "run/gametest-ftbchunks/logs/latest.log", ("src/gametestFtbChunks/kotlin",
  "src/gametest/kotlin-claims", "src/gametest/kotlin-shared"), "cobblemon_dynamics")`, and the same for `opac`. So the
  run summary of each gate report lists both commands and their results. `PHASE_2_ROWS` gains CD-CMP-08 to CD-CMP-10, as
  `scripts/check_plan_coverage.py` has them since 2026-11-11, and the docstring names them.

- [ ] **Step 3: Run** `./gradlew build`, `./gradlew runGameTestServer`, `./gradlew runGameTestServerCore`,
  `./gradlew runGameTestServerPerf`, `./gradlew runGameTestServerFtbChunks`, `./gradlew runGameTestServerOpac`,
  `py scripts/testpack/testpack.py smoke`, `deps`, `compat`, `reload` and `restart`, `py scripts/check_plan_coverage.py` and
  `py scripts/check_plan_coverage.py --phase 2`. Run the JVMs one at a time on the shared machine. Both coverage modes print
  `missing: 0`.

- [ ] **Step 4: The gate report.** Run `py scripts/gate_report.py --phase 2` with the logs of Step 3. It rewrites the
  generated blocks of `docs/testing/phase-2-gate.md` on the new commit. Each new MUST and MUST NOT row must pass there.

- [ ] **Step 5: Commit and hand over.** `git commit -m "Document the claim checks and run the Phase 2 gate again [rag:2]"`.
  The coordinator opens the pull request. Its body holds the readings of Task 2 Step 5, the multiplayer check of Task 2
  Step 6, the facts that Task 4 Step 2 proved, and the results of `runGameTestServerFtbChunks` and
  `runGameTestServerOpac` on the tip of the branch, because CI does not run them. After the merge, the coordinator sends
  COHO-2 the SHA-1 of the new jar, so COHO can set `quickWork.enabled = true`.

---

## Design decisions for the approver

The rulings of R64 and R65 settle the feature. This plan makes these further calls. On 2026-11-11, under the approver's
delegation, the coordinator ruled on calls 2, 3, 4, 7, 8 and 9. Calls 4 and 7 changed, and the list below holds their
new form. Calls 1, 5 and 6 got no ruling of their own.

1. **An empty main hand.** Each check asks about a right-click with an empty main hand. The Pokémon works the block, so the
   item in the owner's hand plays no part. For FTB Chunks this is the full check of a right-click. For Open Parties and
   Claims an empty hand skips its item rules.
2. **The whole permission every 20 ticks.** The second check asks the spectator check and `mayInteract` again, not only the
   claim mods, so the start and the second check share one function. Kept by the coordinator.
3. **The message of FTB Chunks.** FTB Chunks shows "Interaction prevented here by claim protection!" when its check
   prevents, and it has no silent form. The engine's own message follows in the same tick and replaces it. The other way, a
   mixin into FTB Chunks or a copy of its rules, breaks when FTB Chunks changes. Kept by the coordinator, as it is.
4. **Optional entries with any version.** `neoforge.mods.toml` declares `ftbchunks` and `openpartiesandclaims` as optional
   dependencies with `versionRange="*"`, as it declares Cobbleworkers and Cobblebase (Task 3, CD-QWK-34). NeoForge
   checks no version of either claim mod. A check that throws refuses, so an untested version cannot open claims to
   strangers, and `docs/modpack-integration.md` lists the tested versions. The coordinator changed this call: the plan
   first declared no entry.
5. **The canonical block.** The checks ask about the canonical block, as `mayInteract` does. A large water wheel across a
   chunk border counts as the chunk of its canonical block.
6. **Test owners of the exact class `ServerPlayer`.** FTB Chunks treats every subclass as a fake player. The tests of the
   claim runs use such owners, so they test the path of a real player.
7. **Two GameTest runs outside CI.** One run per claim mod, each on the core mod set, so a fault of one mod cannot hide a
   result of the other one. CI does not run them. They run in the pull request of this plan, in each later pull request
   that touches the claims code, and at each phase gate, whose report lists them (Task 4 Step 4). The coordinator changed
   this call: the plan first ran them in CI, which would add a few minutes to each CI run.
8. **A test claim provider in the full run.** It tests the engine path, the second check and a check that throws, with no
   claim mod. CI runs it with the full run.
9. **When the lines log.** The mixin line logs when mod loading completes, the payload line when the payloads register, and
   the role line on each role file load and on each config change that changes it. The coordinator kept the line formats
   of Task 5 as written.

The coordinator also ruled on three points outside this list:

- **Fail closed.** When a claim mod changes its API, every Quick Work order on the server is refused, with one warning per
  cause. `docs/modpack-integration.md` explains the warning to a pack maintainer.
- **The rules of each claim mod.** Each claim mod applies its own rules, so allies of an FTB Chunks team can Quick Work in
  its claims by default, and allies of an Open Parties and Claims party cannot. The player guide and the integration page
  say so.
- **Pasture work areas.** They are not a non-goal of the spec sheet. The Phase 2 gate report lists them as a known issue,
  and the spec sheet's open question Q9 holds them as an open decision for Phase 3, because the approver has not ruled on
  this part of the scope of 1.0.
