# Cobblemon Dynamics 1.0.0: land claims for pastures, MineColonies and the public release

> Read this index first, then the task that you execute. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship Cobblemon Dynamics 1.0.0, the first public release (Phase 3, milestone M9), with every planned feature. The
workers of Work Mode pastures respect the land claims of FTB Chunks, Open Parties and Claims and MineColonies, by the
rights of the pasture's placer, also while the placer is offline. Quick Work respects MineColonies too. The deferred
claim mod tests run, the release workflow publishes without settings, and every `MUST` and `MUST NOT` row passes on the
release commit before the `v1.0.0` tag.

**Architecture:** The land claim gate of the Quick Work claims build gets a second question,
`ownerMayUse(ownerId, level, pos)`. Each claim mod answers it from a UUID and sends nothing. The work engine asks it after
each scan pass, on each poll of a pasture and in the choice of a bed. A third compat package, `compat/minecolonies/`,
answers both questions for colonies. The publish job of the release workflow skips a site without its settings, with a
notice.

**Tech Stack:** The stack of the [Phase 2 plan](2026-11-10-phase-2-plan.md), with these claim mods for tests and compile-only
APIs: FTB Chunks 2101.1.23, FTB Teams 2101.1.12, FTB Library 2101.1.39, Architectury API 13.0.11, Open Parties and Claims
0.33.0, MineColonies 1.1.1419-1.21.1-snapshot, Structurize 1.0.839-1.21.1-snapshot, BlockUI 1.0.212-1.21.1-snapshot,
Domum Ornamentum 1.0.237-snapshot and Multi-Piston 1.2.58-1.21.1.

**Spec:** The [spec sheet](2026-11-09-v1-spec-sheet.md): the LND area (CD-LND-01 to CD-LND-16), the amended CD-REL-04,
CD-UI-03 and CD-UI-09, and the rows of [Rows of earlier phases with a Phase 3 part](#rows-of-earlier-phases-with-a-phase-3-part). Inputs R67,
R68, R69 and RES7 hold the rulings, the request and the research. Design:
[ADR-0014](../adr/0014-land-claims-for-work-mode-pastures.md), and Amendment 1 of
[ADR-0013](../adr/0013-quick-work-for-party-pokemon.md#amendment-1-land-claims-2026-11-11). The model of this file is the
[land claims plan](2026-11-11-phase-2-quick-work-claims.md) of Phase 2. If this plan and the spec sheet disagree, the spec
sheet wins. Fix the plan in the same pull request.

## The rulings

### The approver, 2026-11-11 (R67)

"yes, ship beta.3; then work on beta.4 while we play. make beta.4 the actual release, not a beta anymore. and make sure we
have all planned features in."

- The next version after `1.0.0-beta.3` is `1.0.0`, a public release. The approver plays `1.0.0-beta.3` while this plan
  runs.
- With the ruling, the approver approved two features:
  1. **Claim checks for Work Mode pastures.** The auto-assignment and the workers of a pasture never work a target in a
     chunk that the pasture's owner may not interact with. The owner may be offline, so the check works from the owner's
     UUID: team members, parties and allies, under each claim mod's own rules. Leash bindings already obey canceled clicks
     (CD-SEC-11).
  2. **MineColonies colony protection**, for Quick Work and for pastures, beside FTB Chunks and Open Parties and Claims.
- "all planned features" is the [inventory](#inventory) below. The ruling answers open question Q9 of the spec sheet.

### COHO-2, through the coordinator, 2026-11-11 (R68)

COHO 2.4.0 (pack tag 2.4.0-rc4) ships these jars, and this plan pins them:

| Mod | Jar |
| --- | --- |
| MineColonies | `minecolonies-1.1.1419-1.21.1-snapshot.jar`. Its API ships inside the main jar. |
| Structurize | `structurize-1.0.839-1.21.1-snapshot.jar` |
| BlockUI | `blockui-1.0.212-1.21.1-snapshot.jar` |
| Domum Ornamentum | `domum-ornamentum-1.0.237-snapshot-main.jar` |
| Multi-Piston | `multipiston-1.2.58-1.21.1.jar` |

The jars are in the frozen cache `E:/rch/jars/2026-11-02/`, which is read-only. The colony settings of COHO are the
defaults of MineColonies ([ADR-0014](../adr/0014-land-claims-for-work-mode-pastures.md#the-colony-settings-of-coho-240-r68)).
The coordinator, with the set: "Design so that a colony's own ranks decide who may use a block, as FTB and OPAC decide
through their own rules. Also check how `permissioneventbypassminpermlevel` (operators) interacts with our check."

The answer on operators, from `javap -c` on `Permissions.hasPermission(Player, Action)`: MineColonies gives a player the
permissions of its operator rank only while `player.hasPermissions(permissionEventMinBypassPermLevel)` is true and the
player is in creative mode. COHO sets the level to 2.

- **Quick Work** asks `hasPermission(owner, action)` about the online owner. An operator of level 2 or higher in creative
  mode passes, as that player's own click passes. In survival mode, the operator's rank decides (CD-LND-09).
- **Pastures** ask `hasPermission(getRank(placer), action)`. The bypass never applies, because it needs the player's game
  mode at the click, and a pasture has no click. An operator's pasture follows the operator's rank in the colony
  (CD-LND-08).

### The coordinator on publication, 2026-11-11 (R69)

The approver uploads jars to CurseForge by hand: "why does it need project ids and api tokens? I already released it with
the beta.2 build and it just required the JAR". The coordinator's ruling:

1. A public `v*` tag still builds the jar and attaches it to the GitHub release.
2. The publish job to CurseForge and Modrinth skips with a notice when its secrets or project ids are absent, instead of
   failing the job.
3. When the secrets are added later, the job publishes as it does today.
4. CD-REL-04 changes to match, with a corrections entry that quotes the approver. The tests of the old behavior change,
   such as `ReleaseWorkflowTest`.

The release needs nothing from the approver. Task 6 holds the change.

### The coordinator on the design calls, 2026-11-11

Under the approver's delegation, the coordinator accepted calls 1 to 11, 13, 15 and 16 of
[Design decisions for the approver](#design-decisions-for-the-approver) as written, and changed call 14: CD-UI-03 and
CD-UI-09 rest on code reading, and their checks with two players retire. Calls 12 (Modrinth) and 17 (a play-test of the
1.0.0 candidate) are pending the approver. The coordinator moved CD-LND-01 to CD-LND-16 to `agreed`, and ADR-0014 is
accepted.

### The approver and the coordinator on the Muse, 2026-11-11 (R70)

The approver: "fairy types should be able to be thrown out from inventory at enchantment tables to grant it the bonus that they'd normally grant in the pasture/working (similarly to how fire tpyes can be thrown out at furnances to power them from player inventory)".

The coordinator's rulings on the design:

1. **An opt-in flag.** A role file field, `"quick_work": true`, lets a role with `auto_assign: false` take Quick Work orders.
   Only `muse.json` sets it. Every other leash-only role still gets "{target} needs the Work Leash" (CD-QWK-04).
2. **CD-QWK-05 changes.** A Fairy-type gets a Quick Work order only for a role that opts in, which today is the Muse at an
   enchanting table. On that order it grants the table the bonus of a leashed Muse. A Quick Worker never takes a heal claim.
   A Fairy-type sent out at a water wheel, or at any machine whose role does not opt in, still gets no order (CD-ASG-04).
3. **No demand at the start, and no Standby end.** The Muse's demand is an open menu, and Cobblemon's R key does nothing while
   a screen is open, so the order of a role that opts in skips the no-demand refusal of CD-QWK-09, and its Cobblemon waits by
   the table in Standby. It also has no Standby end of CD-QWK-19, because a player often closes the table to fetch lapis or
   books and comes back. The order ends only on its other conditions, and in Standby it still costs one order tick and starts
   no scan (CD-QWK-23).
4. **Records.** The ruling goes into the corrections of the spec sheet, the new row CD-QWK-36 holds the flag, and the role file
   docs and the Quick Work section of the player guide name the flag. Task 9 holds the work.

### The approver on the GameTests, 2026-11-11

The approver, who wanted to play: "do we really need the game tests? i thought we were pretty confident in these fixes and we'll be testing them actively in the server". The coordinator cut the runs of the `m9-claims` build to
`./gradlew build` and `./gradlew runGameTestServerFtbChunks`, because the UUID rule of FTB Chunks decides pasture work on
COHO's live server. Every other run of Tasks 4, 5 and 9 is deferred by this ruling, and the tasks mark each part.

### Earlier rulings that still bind

- **Multiplayer** (approver, 2026-11-10): every feature works in multiplayer, and no GameTest has two players. Each task
  reads its code against the multiplayer rules of the Phase 2 plan.
- **Test scope** (approver, 2026-11-09): "avoid game tests for high-confidence code". The behavior of the real claim mods
  is not high-confidence code, so their checks get GameTests.
- **The claim runs outside CI** (coordinator, 2026-11-11): CI does not run them. A pull request that touches the claims
  code runs them and records the results in its body, and each gate runs them.
- **Fail closed, and each claim mod's own rules** (coordinator, 2026-11-11).
- **The time ruling** (approver, 2026-11-11): "Skip the client tests and any tests that you're confident about with the
  code. I want the new JAR to be done within 30mins, ideally." It covered the `1.0.0-beta.2` build, and it deferred Tasks
  4 and 6 of the claims plan. CD-REL-14 needs every `MUST` row on the release commit, so Task 8 runs every check.
- **Wording** (approver, 2026-11-11): "find/replace all instances of Pokemon with Cobblemon, so we don't run into any
  copyright issues". Player-facing text calls the creatures Cobblemon. Code, lang keys and API names keep their names.
- **Tags**: the approver approves each tag before its push. `v1.0.0-beta.2` is approved after PR #30 merges.

## Inventory

Everything that a plan, a gate report, the spec sheet or a ruling planned and that `main` does not do yet. The sources
are the gate reports of [Phase 1](../testing/phase-1-gate.md) and [Phase 2](../testing/phase-2-gate.md), the deferred
lists of the plans, the open questions and the M9 rows of the spec sheet, and a check of each `SHOULD` and `MAY` row
against the code and the tests.

| # | Item | Source | Status on 2026-11-11 | Task |
| --- | --- | --- | --- | --- |
| 1 | The workers of Work Mode pastures respect land claims, by the placer's UUID | R67, Q9, known issue of the Phase 2 gate, Decision 8 of ADR-0013 Amendment 1 | Not built. New rows CD-LND-01 to CD-LND-07, CD-LND-10 to CD-LND-12 and CD-LND-15 | 1, 2, 4 |
| 2 | MineColonies colony protection for Quick Work and pastures | R67, R68 | Not built. New rows CD-LND-08, CD-LND-09, CD-LND-13 and CD-LND-14 | 3, 4, 5 |
| 3 | GameTests with the real FTB Chunks and Open Parties and Claims | Task 4 of the claims plan, deferred by the time ruling | Not run. The real-mod parts of CD-QWK-27, CD-QWK-28, CD-QWK-29, CD-QWK-31 and CD-QWK-32 have no test | 4 |
| 4 | The test pack with each claim mod, and the load lines on real servers | Task 6 of the claims plan, deferred | Not run: CD-QWK-33 and the real-server parts of CD-CMP-08 to CD-CMP-10 | 5 |
| 5 | The docs, the run list of the gate report and the gate report again | Task 7 of the claims plan, Steps 1 to 4, deferred | Not done | 7, 8 |
| 6 | Publication of `v1.0.0` (CD-REL-04) | M9, known issues (b) and (c) of the Phase 1 gate, R69 | The publish job fails without its four settings. R69 changes the row | 6 |
| 7 | The release version `1.0.0+mc1.21.1` and the `v1.0.0` tag (CD-REL-05) | M9 | `mod_version` is a beta | 8 |
| 8 | The approver's pack holds 1.0.0 (CD-REL-15) | M9 | COHO 2.4.0 ships `1.0.0-beta.3` | 8 |
| 9 | Every `MUST` row passes on the release commit, with the play-test note (CD-REL-14) | M9, the Phase 2 gate | The Phase 2 report ran on `ceec134`, before CD-QWK-26 to CD-QWK-35 and CD-CMP-08 to CD-CMP-10 existed | 8 |
| 10 | The approver's Phase 2 play-test note: checks 6, 9, 10, 14, 15, 20, 22 and 44 to 59 | The Phase 2 gate | Pending. The approver plays `1.0.0-beta.3` | 8 |
| 11 | The Demo checks that never ran: 5 (CD-PLAT-06), 8 (CD-UI-03), 23 (CD-UI-09), 36 (CD-CMP-01), 37 (CD-CMP-03), 38 (CD-CMP-04), 39 (CD-CMP-05), 40 (CD-REL-01) and the spark run of known issue (d) | The play-test note of the Phase 1 gate ("not reported") | Not run. CD-CMP-03 and CD-CMP-04 have no automated test at all. By decision 14, CD-UI-03 and CD-UI-09 now rest on code reading, so checks 8 and 23 retire | 7 (checks 8 and 23), 8 |
| 12 | The CD-REL-12 Demo, check 41 | The Phase 1 gate | The tags `v1.0.0-alpha.1` and `v1.0.0-beta.1` are pushed. Their release runs were queued in the GitHub outage of 2026-11-11 | 8 |
| 13 | CD-STK-07 (`SHOULD`): "set the burn duration to 1,600 ticks" | `SHOULD` check | Code and tests exist: `FurnaceHandler.applyWork` sets `litTime` and `litDuration` to 2 ticks when `litTime` is below 2, so the gauge reads full. `StokerTests.cdStk07GaugeIsNotEmptyWhileCooking` and check 29 (pass in the Phase 1 play-test) test a full gauge. The row text and `ARCHITECTURE.md` ("`litDuration = 1600`") differ from the code. Ruled: fix the texts, not the code | 7 |
| 14 | CD-UI-08 (`SHOULD`): icons differ in shape | `SHOULD` check | Done: `StateIcons.kt`, `StateIconsTest`, check 11 (pass) | none |
| 15 | CD-UI-10 (`SHOULD`): the tooltip of a stamina bar | `SHOULD` check | Done: `StaminaBar.kt`, `PanelLayout.kt`, `StaminaBarTest`, check 12 (pass) | none |
| 16 | CD-ART-11 (`MAY`): placeholder art in private builds | `MAY` check | Done: `BedContentFilesTest`, `ArtFilesTest`. A public release follows CD-ART-12, and every asset is approved | none |
| 17 | CD-REL-11 (`SHOULD`): the notice of no affiliation | `SHOULD` check | The README holds it, and `RepositoryFilesTest` checks it. The pages of the sites belong to the approver | 7 |
| 18 | `mod_description` reads "Pastured Pokemon power and run the machines of your base." | The wording ruling, PR #29 | Not changed. NeoForge shows it in the mod list | 7 |
| 19 | The internal docs still say Pokémon: spec sheet, plans, ADRs, `ARCHITECTURE.md`, testing docs | The coordinator's notes after PR #29 | Not done. Ruled: a pass over the prose, as the last docs step of Task 7 | 7 |
| 20 | Quick Work review finding 4: no message names a Brain that cannot hold an order | Known issue of the Phase 2 gate | Ruled: stays a known issue for 1.0.0 | none |
| 21 | Floral Healing against CD-FX-08 and CD-FX-09 | Known issue of the Phase 2 gate | Cannot run in `[1.8.4,1.9)` | none: a known issue for the port to Cobblemon 1.9 |
| 22 | CD-QWK-21 has no GameTest | Known issue of the Phase 2 gate | Unit tests and check 59, by a ruling | 8 (check 59) |
| 23 | CD-SEC-04 at exactly 10.0 blocks | Known issue of the Phase 2 gate | Stricter than the row, by a ruling | none |
| 24 | The timeout of `cdHeal17` | Known issue of the Phase 2 gate | Watch | 8 |
| 25 | Dependabot alerts on `settings.gradle` | Known issue of the Phase 2 gate | Development scope only | none |
| 26 | Logistics roles share their containers (R66) | `logistics-shared-targets`, PR #31 | Builds `1.0.0-beta.3`. This plan's branch sits on it, and it merges before Task 1 | before Task 1 |
| 27 | Open question Q9 | The spec sheet | Answered by R67 in the spec sheet of this plan | done |
| 28 | Known issue (a) of the Phase 1 gate: spots that no path reaches | The Phase 1 gate | Fixed in M6 (CD-ASG-09) | none |
| 29 | G9 names Modrinth and CurseForge | G9, R69 | The approver uploads to CurseForge by hand. Nobody uploads to Modrinth yet | decision 12, pending the approver |
| 30 | A play-test of the 1.0.0 candidate | CD-REL-14 | The play of `1.0.0-beta.3` gives the play-test note that CD-REL-14 needs | decision 17, pending the approver |
| 31 | Fairy-types Quick Work an enchanting table as the Muse | R70 | Not built. New row CD-QWK-36, amended CD-QWK-05, CD-QWK-09, CD-QWK-19 and CD-CFG-07 | 9 |
| 32 | `TailwindTests.cdTlw06Tlw09Tlw08TheBonusFollowsTheWork` fails now and then with "the Pidgey is STANDBY after the skip" | The coordinator, after `1.0.0-beta.3` | A known issue, with the cause below. The fix is deferred by the approver's ruling on the GameTests | none: a known issue |

### Known issue: the Tailwind flake (item 32)

`TailwindTests.cdTlw06Tlw09Tlw08TheBonusFollowsTheWork` releases its bound Pidgey with `TestKit.release`, which keeps the
Pidgey off the bearing for 200 ticks, and then waits for it to Work again. A probe ran eight copies of the scene in one
GameTest run on 2026-11-11, with the Pidgey's position, flight flag and state every 100 ticks. Seven passed. In copy 3, the
idle Pidgey flew up during the skip, as Cobblemon's `wanders_hover` lets a pastured flyer do (up to 6 blocks). At tick 280 it
was 4.5 blocks above the bearing. At its new claim it flew down to its spot under the turning sails, and landed on the sail
sheet, which is a Create contraption that the pathfinder does not see. From tick 380 to 880 it rode the sails, 1.6 blocks
above the bearing and `onGround`, so it never reached its spot. After 600 ticks its travel timed out, and a bound worker
then waits in Standby until its retry (CD-ASG-17), which the test read as "STANDBY after the skip". In a second run, eight
copies that held the Pidgey on its spot during the skip all passed. The fix holds the Pidgey on its spot until it claims
again, as other tests hold a worker. It is deferred by the approver's ruling on the GameTests. In play, a bound Tailwind
worker is rarely idle long enough to fly over its sails, and one that gets stuck gets the "cannot reach" message of
CD-LSH-21 and travels again after `engine.retryWaitTicks`.

## Global Constraints

The [Global Constraints of the Phase 2 plan](2026-11-10-phase-2-plan.md#global-constraints) hold, its multiplayer rules
included. Phase 3 adds these lines:

- The pasture check asks each loaded claim mod about the UUID of the pasture's placer, never about a player, and it sends
  no message (CD-LND-05, CD-LND-10).
- A check that throws refuses, with one warning per cause and per asker (CD-QWK-30, CD-LND-11).
- Only `compat/ftbchunks/`, `compat/opac/` and `compat/minecolonies/` name a class of a claim mod, behind
  `ModList.isLoaded` (CD-QWK-33, CD-LND-13). The jar holds no class of `dev.ftb`, `xaero`, `dev.architectury`,
  `com.minecolonies` or `com.ldtteam`.
- `ftbchunks`, `openpartiesandclaims` and `minecolonies` are optional dependencies with `versionRange = "*"` (CD-QWK-34,
  CD-LND-14).
- No config key turns a claim check off (CD-QWK-35, CD-LND-15).
- Player-facing text calls the creatures Cobblemon.
- CI does not run the three claim runs. A pull request that touches the claims code runs them and records the results.
- Phase 3 adds no static mutable state beyond the list of `LandClaims`, which mod construction fills.

## How to execute this plan

1. **Before Task 1.** `main` holds PR #30 (the Quick Work claims build) and PR #31 (the logistics branch,
   `1.0.0-beta.3`), on which this plan's branch sits. The coordinator moved the LND rows to `agreed` on 2026-11-11 with
   the rulings on the [design decisions](#design-decisions-for-the-approver), and the tasks follow those rulings.
2. **Two branches.** `m9-claims`, from `main`: Tasks 1 to 5, 7 and 9, in one pull request. `m9-release`, from `main` after
   `m9-claims` merges: Tasks 6 and 8, in one pull request, and then the tag.
3. **References.** At the start of each branch, check every reference of [Checked references](#checked-references)
   against the jars that Gradle downloaded. Treat an "(inferred)" fact as unverified. Where the code differs from this
   plan, fix the plan in the same commit as the code.
4. **Test first.** Write the failing test, run it and see it fail for the expected reason, write the code, run the test
   and see it pass, then commit.
5. **Done.** A task is done when its tests pass, `./gradlew build` passes, and the GameTests that it adds pass in their
   run. A branch is done when its pull request is green in CI, every review thread is resolved, and it is merged.
6. **The machine.** Follow the development notes: JDK 21 through `JAVA_HOME`, one JVM at a time on the shared machine,
   the JVM lock, and one line to COHO-2 before each large JVM run.
7. **Coverage.** After any plan edit, `py scripts/check_plan_coverage.py`, its `--phase 2` and its `--phase 3` each print
   `missing: 0`.

Commands of Phase 3, beside those of the Phase 2 plan (Windows: `gradlew.bat`, Unix: `./gradlew`):

| Purpose | Command | Pass condition |
| --- | --- | --- |
| GameTests with FTB Chunks | `./gradlew runGameTestServerFtbChunks` | exit code 0, log line `All N required tests passed` |
| GameTests with Open Parties and Claims | `./gradlew runGameTestServerOpac` | exit code 0 |
| GameTests with MineColonies | `./gradlew runGameTestServerMineColonies` | exit code 0 |
| Plan coverage, Phase 3 | `py scripts/check_plan_coverage.py --phase 3` | `missing: 0` |
| Release gate report | `py scripts/gate_report.py --phase 3 --gradle-log FILE --testpack-log FILE --art-log FILE` | exit code 0 |

## Checked references

Checked on 2026-11-11 with `javap` (and `javap -c` for the rules) on the jars of COHO 2.4.0, copied from the cache
`E:/rch/jars/2026-11-02/`, on `open-parties-and-claims-neoforge-1.21.1-0.33.0.jar` of RES6, and on
`neoforge-21.1.265-merged.jar`. [ADR-0014](../adr/0014-land-claims-for-work-mode-pastures.md#research-res7) explains what
each call decides. The Quick Work references of the [claims plan](2026-11-11-phase-2-quick-work-claims.md) still hold.

FTB Chunks 2101.1.23, package `dev.ftb.mods.ftbchunks.api` unless a line names another package of `dev.ftb.mods.ftbchunks`:

```text
ClaimedChunkManager:   public abstract dev.ftb.mods.ftbchunks.api.ClaimedChunk getChunk(dev.ftb.mods.ftblibrary.math.ChunkDimPos);
                       public abstract boolean getBypassProtection(java.util.UUID);
                       public abstract void setBypassProtection(java.util.UUID, boolean);
ClaimedChunk:          public abstract dev.ftb.mods.ftbchunks.api.ChunkTeamData getTeamData();
ChunkTeamData:         public abstract dev.ftb.mods.ftbteams.api.Team getTeam();
                       public abstract boolean isTeamMember(java.util.UUID);
                       public abstract boolean isAlly(java.util.UUID);
                       public abstract boolean canPlayerUse(net.minecraft.server.level.ServerPlayer,
                           dev.ftb.mods.ftbteams.api.property.PrivacyProperty);
FTBChunksProperties:   public static final dev.ftb.mods.ftbteams.api.property.PrivacyProperty BLOCK_INTERACT_MODE;
FTBChunksTags$Blocks:  public static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> INTERACT_WHITELIST_TAG;
ftbchunks.FTBChunksWorldConfig: public static final dev.ftb.mods.ftblibrary.snbt.config.BooleanValue DISABLE_PROTECTION;
                       public static boolean noWilderness(net.minecraft.server.level.ServerPlayer);   // null: no_wilderness only
ftbchunks.util.DimensionFilter: public static boolean isNoWildernessDimension(
                           net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>);
```

FTB Teams 2101.1.12 and FTB Library 2101.1.39:

```text
ftbteams.api.Team:                 public abstract <T> T getProperty(dev.ftb.mods.ftbteams.api.property.TeamProperty<T>);
                                   public abstract dev.ftb.mods.ftbteams.api.TeamRank getRankForPlayer(java.util.UUID);
ftbteams.api.TeamRank:             public boolean isMemberOrBetter();        // ENEMY, NONE, ALLY, INVITED, MEMBER, OFFICER, OWNER
ftbteams.api.property.PrivacyMode: ALLIES, PRIVATE, PUBLIC
ftblibrary.math.ChunkDimPos:       public dev.ftb.mods.ftblibrary.math.ChunkDimPos(net.minecraft.world.level.Level, net.minecraft.core.BlockPos);
```

The steps of `ClaimedChunkManagerImpl.shouldPreventInteraction` for a `ServerPlayer` (`javap -c`):

```text
1. DISABLE_PROTECTION, or getBypassProtection(player.getUUID())            -> allow
2. PlayerHooks.isFake(player) while ALLOW_FAKE_PLAYERS overrides           -> the override decides
3. getChunk(new ChunkDimPos(player.level(), pos)) == null && !noWilderness(player) -> allow
4. Protection.INTERACT_BLOCK: state.is(INTERACT_WHITELIST_TAG)              -> ALLOW
                              chunk != null && canPlayerUse(player, BLOCK_INTERACT_MODE) -> ALLOW
                              otherwise CHECK, which prevents unless the player is a spectator
5. canPlayerUse: PUBLIC -> true; a fake player -> canFakePlayerUse; ALLIES -> isAlly(uuid);
                 PRIVATE -> team.getRankForPlayer(uuid).isMemberOrBetter()
6. A prevention calls PlayerNotifier.notifyWithCooldown(player, ..., 2000) and logPreventedAccess.
isAlly(uuid): ALLY_MODE FORCED_ALL -> true; member or better -> true; FORCED_NONE -> false; else rank == ALLY
Any actor that is not a ServerPlayer -> false (no prevention)
```

Open Parties and Claims 0.33.0, package `xaero.pac.common.server`:

```text
claims.protection.api.IChunkProtectionAPI:
    public abstract boolean hasChunkAccess(java.util.UUID, net.minecraft.resources.ResourceLocation, int, int);
        // claim of (dimension, chunkX, chunkZ), its config, then the overriders, claims.protectClaimedChunks,
        // fullAccess "E", the claim's owner, and the fullAccess group: areInSameParty / isPlayerAllying by UUID.
        // No claim: true. A null UUID in a protected claim: false.
claims.api.IServerClaimsManagerAPI:
    public abstract xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI get(net.minecraft.resources.ResourceLocation, int, int);
```

MineColonies 1.1.1419-1.21.1-snapshot, package `com.minecolonies.api`:

```text
IMinecoloniesAPI:     public static com.minecolonies.api.IMinecoloniesAPI getInstance();
                      public abstract com.ldtteam.common.config.Configurations<ClientConfiguration, ServerConfiguration,
                          CommonConfiguration> getConfig();                       // Configurations is a class of BlockUI
colony.IColonyManager: public static com.minecolonies.api.colony.IColonyManager getInstance();
                      public abstract com.minecolonies.api.colony.IColony getColonyByPosFromWorld(net.minecraft.world.level.Level,
                          net.minecraft.core.BlockPos);                           // chunk data; loads the chunk
                      public abstract com.minecolonies.api.colony.IColony createColony(net.minecraft.server.level.ServerLevel,
                          net.minecraft.core.BlockPos, net.minecraft.world.entity.player.Player, java.lang.String, java.lang.String);
                      public abstract void deleteColonyByWorld(int, boolean, net.minecraft.server.level.ServerLevel);
colony.IColony:       public abstract com.minecolonies.api.colony.permissions.IPermissions getPermissions();
                      public abstract boolean isCoordInColony(net.minecraft.world.level.Level, net.minecraft.core.BlockPos);
                      public abstract int getID();
colony.permissions.IPermissions:
                      public abstract com.minecolonies.api.colony.permissions.Rank getRank(java.util.UUID);   // stranger: Neutral (id 3)
                      public abstract boolean hasPermission(com.minecolonies.api.colony.permissions.Rank,
                          com.minecolonies.api.colony.permissions.Action);
                      public abstract boolean hasPermission(net.minecraft.world.entity.player.Player,
                          com.minecolonies.api.colony.permissions.Action);       // + operator in creative mode
                      public abstract boolean addPlayer(com.mojang.authlib.GameProfile, com.minecolonies.api.colony.permissions.Rank);
                      public abstract boolean setPlayerRank(java.util.UUID, com.minecolonies.api.colony.permissions.Rank,
                          net.minecraft.world.level.Level);
                      public abstract boolean alterPermission(Rank, Rank, Action, boolean);
                      public abstract Rank getRankOwner(); getRankOfficer(); getRankFriend(); getRankNeutral(); getRankHostile();
colony.permissions.Rank:   public boolean isHostile();
colony.permissions.Action: ACCESS_HUTS, ACCESS_TOGGLEABLES, RIGHTCLICK_BLOCK, OPEN_CONTAINER, and 23 more
configuration.ServerConfiguration: public final ModConfigSpec$BooleanValue enableColonyProtection;
                      public final ModConfigSpec$IntValue permissionEventMinBypassPermLevel;
                      public final ModConfigSpec$BooleanValue pvp_mode;
blocks.AbstractBlockHut<B extends AbstractBlockHut<B>>   // implements com.ldtteam.structurize.blocks.interfaces.IAnchorBlock
items.ModTags:        public static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> colonyProtectionException;
```

The rule of `core.colony.permissions.ColonyPermissionEventHandler.onPlayerInteract` for a `RightClickBlock` in its colony
(`javap -c`):

```text
1. block instanceof AbstractBlockHut && !hasPermission(player, ACCESS_HUTS)                -> cancel
2. (colony free block or position, or state.is(ModTags.colonyProtectionException)) && !getRank(player).isHostile() -> pass
3. (state.is(BlockTags.DOORS) || state.is(BlockTags.FENCE_GATES)) && hasPermission(player, ACCESS_TOGGLEABLES) -> pass
4. !enableColonyProtection                                                                 -> pass
5. !hasPermission(player, RIGHTCLICK_BLOCK) && block is not air                            -> cancel (pvp_mode attackers pass)
6. an IItemHandler from Capabilities.ItemHandler.BLOCK (side null, then each side) && !hasPermission(player, OPEN_CONTAINER) -> cancel
A cancel messages the player, counts the attempt and logs a permission event in the town hall.
```

NeoForge 21.1.265:

```text
event.entity.player.PlayerInteractEvent$RightClickBlock: public RightClickBlock(net.minecraft.world.entity.player.Player,
    net.minecraft.world.InteractionHand, net.minecraft.core.BlockPos, net.minecraft.world.phys.BlockHitResult);
common.extensions.ILevelExtension: public default <T, C> T getCapability(net.neoforged.neoforge.capabilities.BlockCapability<T, C>,
    net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState, net.minecraft.world.level.block.entity.BlockEntity, C);
net.minecraft.server.players.PlayerList: public void remove(net.minecraft.server.level.ServerPlayer);
```

Artifacts for Gradle and the test pack, on `https://ldtteam.jfrog.io/ldtteam/modding/` (public, no login). Each SHA-1 is
the `.sha1` file of the repository, and it equals the SHA-1 of the COHO jar. Each SHA-512 is that of the COHO jar.

| Coordinates | SHA-1 | SHA-512 |
| --- | --- | --- |
| `com.ldtteam:minecolonies:1.1.1419-1.21.1-snapshot` | `c93453c21e1fd743f13a1dd9b5d40a15eadcc6f0` | `b82c4132afef7de53bcea9a49bada2ab7a3b59a989bf4c3ef6f65d4822b60cdc8996c7fe2d251767a98a6018960bb8118b4469fe1e1de6dc52c6d40e1e609df3` |
| `com.ldtteam:structurize:1.0.839-1.21.1-snapshot` | `85cecc947288fdca79a1ddbe9a9d0053af850d92` | `cb65848c8b999bb814561c272d9ac5e93f1e08c1d8a43d829b49f4025fdda87259682e981c33aa3c3e71fa5f37ff10cc389374afc210c62e3c92b9ed047c7715` |
| `com.ldtteam:blockui:1.0.212-1.21.1-snapshot` | `985b739fb0e01ad0bc559c5cd41be45de5cfe2bf` | `defaae595ade0f82e4be5f426978b71cbe9e7a84301f8f8e4e096581b8aefe3b053d8f5e6e0ee55844c3d4e07f3db781e70aab4663fd222a61e20c30f66cd3a3` |
| `com.ldtteam:domum-ornamentum:1.0.237-snapshot:main` | `49b92eb1f5b20fb127406fb848a62cb6afdcfea3` | `f0d56073a6053ff1318701ec1b9bfcb5aae40be658ec6e7cb4211613dfb170288e4cf408c8ff676d5369bf3789492d1a629ba8736422ee8d2fb9fa0d019e9d60` |
| `com.ldtteam:multipiston:1.2.58-1.21.1` | `2b63be739da24247328e4052ec91c928230d0390` | `60007e72516857228c45cbfee14ec18c9c4e89b626ecdd659fff3a146d5dc527d52563645e3348b79820f7eeb0b6d3aeab4394e7b6d6a065666ed04333c05a48` |

The jar files are `minecolonies-1.1.1419-1.21.1-snapshot.jar`, `structurize-1.0.839-1.21.1-snapshot.jar`,
`blockui-1.0.212-1.21.1-snapshot.jar`, `domum-ornamentum-1.0.237-snapshot-main.jar` and `multipiston-1.2.58-1.21.1.jar`,
each under `com/ldtteam/<artifact>/<version>/` of the repository.

The `[[mods]]` ids are `minecolonies`, `structurize`, `blockui`, `domum_ornamentum` and `multipiston`. MineColonies
requires the first four, and nothing in the set requires `multipiston`.

Inferred, and checked by Step 1 of Task 4. The MineColonies run is deferred by the approver's ruling on the GameTests, so only
the last fact is checked: `./gradlew build` compiled `compat/minecolonies/` without Domum Ornamentum on 2026-11-11.

- MineColonies, Structurize, BlockUI and Domum Ornamentum load on `GameTestServer`. A colony that `createColony` makes for
  a test player claims the chunks around its center at once (`ChunkDataHelper.claimColonyChunks`), so
  `getColonyByPosFromWorld` finds it in the same tick.
- Each colony registers its `ColonyPermissionEventHandler` on the NeoForge event bus, so a `RightClickBlock` that a test
  posts reaches the handler.
- By the order of the defaults in the constructor of `Permissions`, the Officer rank has `RIGHTCLICK_BLOCK` and
  `OPEN_CONTAINER`, the Friend rank has `RIGHTCLICK_BLOCK` and not `OPEN_CONTAINER`, and the Neutral rank has neither.
- `createColony` accepts any structure pack name for a colony that builds nothing.
- Kotlin compiles `compat/minecolonies/` with MineColonies, Structurize and BlockUI on the compile classpath, and without
  Domum Ornamentum.

## Source layout

Phase 3 adds or changes these files:

```text
src/main/kotlin/codes/honor/cobblemondynamics/
  compat/LandClaims.kt                         LandClaimCheck asks two questions, ClaimAsker (Task 1)
  compat/ftbchunks/FtbChunksCompat.kt          + ownerMayUse (Task 1)
  compat/ftbchunks/FtbInteractRule.kt          new: the pure rule of FTB Chunks (Task 1)
  compat/opac/OpacCompat.kt                    + ownerMayUse (Task 1)
  compat/minecolonies/MineColoniesCompat.kt    new (Task 3)
  compat/minecolonies/ColonyRightClick.kt      new: the pure rule of MineColonies (Task 3)
  engine/PastureRuntime.kt                     + placer (Task 2)
  engine/WorkEngine.kt                         the pasture checks (Task 2)
  leash/LeashResets.kt                         + LAND_CLAIM, LAND_CLAIM_DESTINATION (Task 2)
  CobblemonDynamics.kt                         + MineColoniesCompat.register() (Task 3)
src/gametest/kotlin/codes/honor/cobblemondynamics/test/
  TestLandClaims.kt                            both questions (Task 1)
  PastureClaimTests.kt                         new (Task 2)
src/gametest/kotlin-claims/                    the scenes of the three claim runs (Task 4)
src/gametestFtbChunks/, src/gametestOpac/, src/gametestMineColonies/   the three claim runs (Task 4)
```

## Rows of earlier phases with a Phase 3 part

The row is done only when its last part passes:

| Row | Earlier part | Phase 3 part |
| --- | --- | --- |
| CD-REL-04 | M0: the release workflow, `ReleaseWorkflowTest` | Task 6: the amended row (R69). Task 8: the `v1.0.0` tag and check 42 |
| CD-REL-05 | M0: the version rule | Task 8: the jar of the `v1.0.0` tag reads `1.0.0+mc1.21.1` |
| CD-REL-08 | M5, M6, M7 and Quick Work: the guide sections | Task 7: the section "Land claims" at the `v1.0.0` tag |
| CD-REL-09 | M5 and M8: the checklist | Task 6: check 42 follows the amended CD-REL-04 |
| CD-REL-11 | M0: the README notice | Task 7: the notice for the approver's pages of the sites |
| CD-REL-12 | M0: `ReleaseWorkflowTest` | Task 8: the Demo of check 41 |
| CD-REL-14 | Phase 2: the report on `ceec134` | Task 8: the report on the release commit, with the play-test note |
| CD-REL-15 | none | Task 8: the jar and its SHA-1 to COHO-2 |
| CD-QWK-26, CD-QWK-30 | The claims build: the test claim provider | Tasks 3 and 4: MineColonies as a claim mod |
| CD-QWK-27, CD-QWK-28, CD-QWK-29, CD-QWK-31, CD-QWK-32 | The claims build: the code and the test claim provider | Task 4: the real-mod GameTests of the claims plan, Task 4 |
| CD-QWK-33 | The claims build: `CompatImportsTest` | Task 5: the test pack cases of the claims plan, Task 6 |
| CD-CMP-08, CD-CMP-09, CD-CMP-10 | The claims build: the lines in the GameTest log | Task 5: the lines in the logs of real servers |
| CD-STK-07 | M1: the code, the GameTest and check 29 | Task 7: the text of the row and of `ARCHITECTURE.md`, by decision 13 |
| CD-UI-03, CD-UI-09 | M1 and M5: unit tests, a GameTest and a Demo with two players that never ran | Task 7: checks 8 and 23 retire, because the rows now rest on code reading (decision 14) |
| CD-PLAT-06, CD-CMP-01, CD-CMP-03, CD-CMP-04, CD-CMP-05, CD-REL-01 | M0 to M5: automated parts, and Demos that never ran | Task 8: the Demos, by decision 14 |
| CD-QWK-05, CD-QWK-09, CD-QWK-19 | Quick Work: the rules, their unit tests and GameTests | Task 9: the exceptions for a role that opts in to Quick Work (R70) |
| CD-CFG-07, CD-MUS-02 | M1 and M4: the role file and the Muse's role file | Task 9: the Quick Work flag of the role file (R70) |

---

### Task 1: The pasture question of the claim gate, with FTB Chunks and Open Parties and Claims

**Rows:** CD-LND-05 (the UUID, never a player), CD-LND-06 and CD-LND-07 (the checks by UUID, unit and code), CD-LND-10
(silent: the Inspect part), CD-LND-11 (fail closed: the unit part), CD-LND-15 (no key).

**Files:**
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/compat/LandClaims.kt`
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/compat/ftbchunks/FtbChunksCompat.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/compat/ftbchunks/FtbInteractRule.kt`
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/compat/opac/OpacCompat.kt`
- Modify: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/TestLandClaims.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/compat/LandClaimGateTest.kt`
- Create: `src/test/kotlin/codes/honor/cobblemondynamics/compat/FtbInteractRuleTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/config/ConfigTest.kt`

**Interfaces:**
- Produces: `LandClaimCheck.ownerMayUse(ownerId: UUID?, level: ServerLevel, pos: BlockPos): Boolean`,
  `LandClaims.ownerMayUse(...)`, `ClaimAsker`, `FtbInteractRule.allows(Facts): Boolean`.
- Consumes: the FTB Chunks, FTB Teams and Open Parties and Claims calls of [Checked references](#checked-references).

- [ ] **Step 1: Failing unit tests.** `FtbInteractRuleTest` names no class of FTB, so the unit tests run without FTB on
  the test classpath.

```kotlin
class FtbInteractRuleTest {
    private val stranger = FtbInteractRule.Facts(
        protectionDisabled = false, bypass = false, claimed = true, wildernessClosed = false, whitelisted = false,
        mode = FtbInteractRule.Mode.ALLIES, ally = false, memberOrBetter = false, hasOwner = true,
    )

    @Test
    fun `CD-LND-06 disable_protection and the admin bypass allow in every claim`() {
        assertTrue(FtbInteractRule.allows(stranger.copy(protectionDisabled = true)))
        assertTrue(FtbInteractRule.allows(stranger.copy(bypass = true)))
    }

    @Test
    fun `CD-LND-06 an unclaimed chunk allows unless the wilderness is closed, and the whitelist still allows there`() {
        assertTrue(FtbInteractRule.allows(stranger.copy(claimed = false)))
        assertFalse(FtbInteractRule.allows(stranger.copy(claimed = false, wildernessClosed = true)))
        assertTrue(FtbInteractRule.allows(stranger.copy(claimed = false, wildernessClosed = true, whitelisted = true)))
    }

    @Test
    fun `CD-LND-06 in a claim, the whitelist and then the block interact mode decide`() {
        assertFalse(FtbInteractRule.allows(stranger))
        assertTrue(FtbInteractRule.allows(stranger.copy(whitelisted = true)))
        assertTrue(FtbInteractRule.allows(stranger.copy(ally = true)))
        assertTrue(FtbInteractRule.allows(stranger.copy(mode = FtbInteractRule.Mode.PUBLIC)))
        assertFalse(FtbInteractRule.allows(stranger.copy(mode = FtbInteractRule.Mode.PRIVATE, ally = true)))
        assertTrue(FtbInteractRule.allows(stranger.copy(mode = FtbInteractRule.Mode.PRIVATE, memberOrBetter = true)))
    }

    @Test
    fun `CD-LND-05 a pasture with no placer gets no block of a claim, also a public one`() {
        val none = stranger.copy(hasOwner = false, mode = FtbInteractRule.Mode.PUBLIC)
        assertFalse(FtbInteractRule.allows(none))
        assertFalse(FtbInteractRule.allows(none.copy(whitelisted = true)))
        assertTrue(FtbInteractRule.allows(none.copy(claimed = false)))
    }
}
```

  `LandClaimGateTest` gains two tests. Fake checks implement both questions, and the gate asks with
  `ClaimAsker.PASTURE`:
  - `CD-LND-11 a pasture check that throws refuses and warns once per cause`: a `RuntimeException`, a
    `NoSuchMethodError` and a `NoClassDefFoundError` each give false, the same cause twice logs one warning, and a second
    cause logs a second one.
  - `CD-LND-11 the warning of a pasture check differs from the warning of Quick Work`: one cause from each asker logs two
    warnings. The pasture warning starts with "Pastures work no target", and the Quick Work warning keeps its text,
    "Quick Work refuses orders while the claim check of ...".

  `ConfigTest` gives its key-list test both row ids: `CD-QWK-35 CD-LND-15 the server config holds the keys of CD-CFG-01
  and no claim key`.

- [ ] **Step 2: Run** `./gradlew test --tests "codes.honor.cobblemondynamics.compat.*"`. The new tests fail to compile:
  `FtbInteractRule`, `ClaimAsker` and `ownerMayUse` do not exist.

- [ ] **Step 3: Code.** `LandClaimCheck` becomes an interface with two questions. `LandClaimGate.allows` takes the asker,
  and the warning key holds the asker, the mod id and the class of the error.

```kotlin
/** The two questions of one claim mod. Each asks about the canonical block of a target, with an empty main hand. */
interface LandClaimCheck {
    /** Quick Work: may the online [owner] right-click [pos] (CD-QWK-26). The claim mod may show its own text. */
    fun mayUse(owner: ServerPlayer, level: ServerLevel, pos: BlockPos): Boolean

    /** A pasture: may the player [ownerId], online or not, use [pos] (CD-LND-05). Null is a pasture with no placer. Silent (CD-LND-10). */
    fun ownerMayUse(ownerId: UUID?, level: ServerLevel, pos: BlockPos): Boolean
}

/** Who asks, for the warning of a check that throws (CD-QWK-30, CD-LND-11). */
enum class ClaimAsker(val refusal: String) {
    QUICK_WORK("Quick Work refuses orders"),
    PASTURE("Pastures work no target"),
}
```

  `LandClaims.mayUse` asks with `ClaimAsker.QUICK_WORK`, and the new `LandClaims.ownerMayUse(ownerId, level, pos)` asks
  every check with `ClaimAsker.PASTURE`. The warning reads "${asker.refusal} while the claim check of $id fails: $e".

  `FtbInteractRule` holds the steps 1, 3, 4 and 5 of the FTB Chunks check in their order:

```kotlin
/** The right-click rule of FTB Chunks 2101.1.23 for a player who is not a fake player, as RES7 read it (CD-LND-06). Names no FTB class. */
object FtbInteractRule {
    enum class Mode { PUBLIC, ALLIES, PRIVATE }

    data class Facts(
        val protectionDisabled: Boolean,
        val bypass: Boolean,
        val claimed: Boolean,
        val wildernessClosed: Boolean,
        val whitelisted: Boolean,
        val mode: Mode?,
        val ally: Boolean,
        val memberOrBetter: Boolean,
        val hasOwner: Boolean,
    )

    fun allows(f: Facts): Boolean = when {
        f.protectionDisabled || f.bypass -> true
        !f.claimed -> !f.wildernessClosed || f.whitelisted
        !f.hasOwner -> false
        f.whitelisted -> true
        else -> when (f.mode) {
            Mode.PUBLIC -> true
            Mode.ALLIES -> f.ally
            else -> f.memberOrBetter
        }
    }
}
```

  `FtbChunksCompat` registers an object with both questions. `mayUse` stays the check of the claims build.
  `ownerMayUse` reads the facts through the API and refuses while the manager is not loaded:

```kotlin
override fun ownerMayUse(ownerId: UUID?, level: ServerLevel, pos: BlockPos): Boolean {
    val api = FTBChunksAPI.api()
    if (!api.isManagerLoaded) return false
    val manager = api.manager
    val data = manager.getChunk(ChunkDimPos(level, pos))?.teamData
    return FtbInteractRule.allows(
        FtbInteractRule.Facts(
            protectionDisabled = FTBChunksWorldConfig.DISABLE_PROTECTION.get(),
            bypass = ownerId != null && manager.getBypassProtection(ownerId),
            claimed = data != null,
            wildernessClosed = FTBChunksWorldConfig.noWilderness(null) || DimensionFilter.isNoWildernessDimension(level.dimension()),
            whitelisted = level.getBlockState(pos).`is`(FTBChunksTags.Blocks.INTERACT_WHITELIST_TAG),
            mode = data?.team?.getProperty(FTBChunksProperties.BLOCK_INTERACT_MODE)?.let { FtbInteractRule.Mode.valueOf(it.name) },
            ally = ownerId != null && data != null && data.isAlly(ownerId),
            memberOrBetter = ownerId != null && data != null && data.team.getRankForPlayer(ownerId).isMemberOrBetter,
            hasOwner = ownerId != null,
        ),
    )
}
```

  `OpacCompat` registers an object with both questions. `mayUse` stays the check of the claims build:

```kotlin
override fun ownerMayUse(ownerId: UUID?, level: ServerLevel, pos: BlockPos): Boolean {
    val api = OpenPACServerAPI.get(level.server)
    val dimension = level.dimension().location()
    val x = SectionPos.blockToSectionCoord(pos.x)
    val z = SectionPos.blockToSectionCoord(pos.z)
    // A pasture with no placer gets no block of a claim (CD-LND-05).
    if (ownerId == null) return api.serverClaimsManager.get(dimension, x, z) == null
    return api.chunkProtection.hasChunkAccess(ownerId, dimension, x, z)
}
```

  `TestLandClaims` implements both questions with one answer for each position, and it records the UUIDs that the
  pasture question gets:

```kotlin
object TestLandClaims : LandClaimCheck {
    val refused: MutableSet<BlockPos> = HashSet()
    val throwing: MutableSet<BlockPos> = HashSet()
    /** The UUIDs that the pasture question got, for each position (CD-LND-05). */
    val askedAbout: MutableMap<BlockPos, MutableSet<UUID?>> = HashMap()

    fun register() = LandClaims.register(TEST_MOD_ID, this)

    override fun mayUse(owner: ServerPlayer, level: ServerLevel, pos: BlockPos): Boolean = answer(pos)

    override fun ownerMayUse(ownerId: UUID?, level: ServerLevel, pos: BlockPos): Boolean {
        askedAbout.getOrPut(pos.immutable()) { HashSet() } += ownerId
        return answer(pos)
    }

    private fun answer(pos: BlockPos): Boolean {
        if (pos in throwing) throw IllegalStateException("the test claim check fails at $pos")
        return pos !in refused
    }
}
```

- [ ] **Step 4: Run** `./gradlew build`. The tests of Step 1 and the old tests of `LandClaimGateTest` pass, and
  `CompatImportsTest` still passes.

- [ ] **Step 5: Inspect (CD-LND-10).** Read both `ownerMayUse` functions: neither calls `shouldPreventInteraction` or
  `onBlockInteraction`, and neither sends a message.

- [ ] **Step 6: Commit:** `git commit -m "Ask the claim mods about a pasture's placer by UUID [rag:1]"`.

---

### Task 2: The engine asks the pasture check

**Rows:** CD-LND-01 (the scan), CD-LND-02 (claims), CD-LND-03 (bindings), CD-LND-04 (beds), CD-LND-05 (the GameTest),
CD-LND-11 (the GameTest), CD-LND-12 (the cost).

**Files:**
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/engine/PastureRuntime.kt`
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/engine/WorkEngine.kt` (`poll`, `assign`, `nearestFreeBed`)
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/leash/LeashResets.kt`
- Modify: `src/main/resources/assets/cobblemon_dynamics/lang/en_us.json`
- Create: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/PastureClaimTests.kt`
- Create: `src/test/kotlin/codes/honor/cobblemondynamics/engine/PastureClaimSourceTest.kt`

**Interfaces:**
- Consumes: `LandClaims.ownerMayUse`, `PastureAdapter.placer(be)`, `LeashResets.clear(server, pokemon, reason)`,
  `TestLandClaims`, the `TestKit` helpers `placePasture`, `workMode`, `tether`, `furnace`, `bind`, `bindTransfer`,
  `claimOf`, `keyOf`, `messages` and `afterTest`.
- Produces: `PastureRuntime.placer`, the reasons `LeashResets.LAND_CLAIM` and `LeashResets.LAND_CLAIM_DESTINATION`.

- [ ] **Step 1: Failing GameTests.** Each test of `PastureClaimTests` places a Work Mode pasture of `TestKit.owner`, whom
  `PlayerList.getPlayer` does not return, so each test asks about an offline placer. Each test adds its own absolute
  positions to `TestLandClaims` and removes them in `TestKit.afterTest`.
  - `cdLnd01RefusedFurnaceGetsNoWorkerUntilAllowed`: furnaces A and B with 64 raw iron each, two Charmanders, B refused.
    For 400 ticks, no claim on B and a claim on A. Then B is allowed, and a worker claims B within 400 ticks.
  - `cdLnd02RefusalReleasesAWorkClaimWithin20Ticks`: a Charmander Works furnace A, then A is refused. Within 21 ticks
    the worker holds no claim, and for 200 more ticks it does not claim A.
  - `cdLnd02RefusalReleasesABedClaimWithin20Ticks`: a Tired Charmander sleeps on bed A, then bed A is refused. The test
    makes the worker Tired as `RestTests` does, with Work Stamina of one tick of capacity. Within 21 ticks no bed claim
    remains, and the worker is in RestingInPlace.
  - `cdLnd03RefusedBoundTargetClearsTheBinding`: a Charmander bound to furnace A Works it, then A is refused. Within 21
    ticks the binding is gone, the worker holds no claim, and `TestKit.messages(owner)` ends with "Charmander lost its
    work binding: the owner of its pasture may not use its target".
  - `cdLnd03RefusedDestinationClearsATelekinesisBinding`: an Abra bound with `TestKit.bindTransfer` from chest S to chest
    D, then D is refused. Within 21 ticks the binding is gone, and the message names the destination reason.
  - `cdLnd04TiredWorkerTakesTheAllowedBed`: bed A at 3 blocks is refused, bed B at 6 blocks is allowed. The Tired
    Charmander claims B and never A.
  - `cdLnd04OnlyBedRefusedRestsInPlace`: bed A is the only bed and is refused. The Tired Charmander claims no bed and is
    in RestingInPlace within 21 ticks.
  - `cdLnd05TheCheckGetsOnlyThePlacersUuid`: furnace B refused. After 400 ticks, `TestLandClaims.askedAbout` holds only
    the placer's UUID for the furnaces of the test, and no worker claims B.
  - `cdLnd11ThrowingCheckGivesNoClaim`: furnace A is in `TestLandClaims.throwing`. For 400 ticks no worker claims A.

  `PastureClaimSourceTest` reads `WorkEngine.kt` (CD-LND-12): `LandClaims.ownerMayUse` appears only in `poll`, in
  `assign` and in `nearestFreeBed`, and `tickWorker` and `tickLevel` do not call it.

- [ ] **Step 2: Run** `./gradlew runGameTestServer` and `./gradlew test --tests "*PastureClaimSourceTest"`. The tests of
  Step 1 fail: the engine asks no pasture check.

- [ ] **Step 3: Code.**
  - `PastureRuntime` gets `var placer: UUID? = null`. `poll` sets it from `PastureAdapter.placer(be)` before it reads
    the slots, so every check of the poll and of the next scan pass asks about the placer of the last poll.
  - `poll`, for each slot, right after it reads the binding, so that `leashed`, `rule` and `claimBound` see the result:

```kotlin
// CD-LND-03: a binding whose target or destination the placer may not use clears, and the binder learns why.
slot.binding?.let { binding ->
    landRefusal(t, runtime, binding)?.let { reason ->
        LeashResets.clear(server, slot.pokemon, reason)
        slot.binding = null
    }
}
// CD-LND-02: a work claim or a bed claim on a block that the placer may no longer use ends in this poll.
claims.claimOf(slot.worker.pokemonId)?.let { claim ->
    if (!placerMayUse(t, runtime, claim.key)) {
        when (claim.kind) {
            ClaimKind.WORK -> release(t, slot)
            // tickRest then finds no bed claim and moves the sleeper to RestingInPlace (CD-BED-11).
            ClaimKind.BED -> claims.release(slot.worker.pokemonId)
            else -> {}
        }
    }
}
```

```kotlin
/** CD-LND-03: the reason that clears [binding], or null while the placer may use its target and its destination. */
private fun landRefusal(t: Tick, runtime: PastureRuntime, binding: Binding): String? = when {
    !LandClaims.ownerMayUse(runtime.placer, t.level, binding.target) -> LeashResets.LAND_CLAIM
    binding.destination?.let { !LandClaims.ownerMayUse(runtime.placer, t.level, it) } == true -> LeashResets.LAND_CLAIM_DESTINATION
    else -> null
}

/** A heal claim names an entity of the same pasture, so only a claim on a block asks the pasture check. */
private fun placerMayUse(t: Tick, runtime: PastureRuntime, key: TargetKey): Boolean {
    val block = key as? TargetKey.Block ?: return true
    return block.dimension != t.dimension || LandClaims.ownerMayUse(runtime.placer, t.level, BlockPos.of(block.pos))
}
```

  - A shared claim of a logistics role (CD-CLM-10) is a work claim of its worker, so `claims.claimOf` returns it and the
    same check releases it.
  - `assign` filters the pass before it builds the candidates: `val allowed = found.filter {
    LandClaims.ownerMayUse(runtime.placer, t.level, it.pos) }`, one check for each target of the pass (CD-LND-01).
  - `nearestFreeBed` skips a bed that the placer may not use, before `BedSelector.pick` (CD-LND-04).
  - `LeashResets` gets `const val LAND_CLAIM = "land_claim"` and `const val LAND_CLAIM_DESTINATION =
    "land_claim_destination"`. `en_us.json` gets `"cobblemon_dynamics.binding.reason.land_claim": "the owner of its
    pasture may not use its target"` and `"cobblemon_dynamics.binding.reason.land_claim_destination": "the owner of its
    pasture may not use its destination"`.

- [ ] **Step 4: Run** `./gradlew build`, `./gradlew runGameTestServer`, `./gradlew runGameTestServerCore` and
  `./gradlew runGameTestServerPerf`. Every test passes. The perf run, whose test mod registers `TestLandClaims`, stays
  within 1.0 ms mean and 2.0 ms p95 (CD-LND-12). Record the mean and the p95 in the pull request.

- [ ] **Step 5: Multiplayer check.** Read the new code against the multiplayer rules of the Phase 2 plan: each pasture asks
  about its own placer's UUID, a refusal messages only the binder through `Feedback`, the checks run on the server thread,
  and no state outlives a poll except `PastureRuntime.placer`.

- [ ] **Step 6: Commit:** `git commit -m "Keep pasture workers out of land claims that their placer may not use [rag:1]"`.

---

### Task 3: MineColonies

**Rows:** CD-LND-08 (the rule and the pasture check), CD-LND-09 (the Quick Work check), CD-LND-13 (the unit part),
CD-LND-14 (the optional entry), and CD-QWK-26 and CD-QWK-30, which now cover MineColonies.

**Files:**
- Modify: `gradle.properties`, `build.gradle` (the repository, three compile-only dependencies, `verifyJar`)
- Modify: `src/main/templates/META-INF/neoforge.mods.toml`
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/CobblemonDynamics.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/compat/minecolonies/MineColoniesCompat.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/compat/minecolonies/ColonyRightClick.kt`
- Create: `src/test/kotlin/codes/honor/cobblemondynamics/compat/ColonyRightClickTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/build/CompatImportsTest.kt`,
  `src/test/kotlin/codes/honor/cobblemondynamics/build/ModMetadataTest.kt`

**Interfaces:**
- Consumes: the MineColonies calls of [Checked references](#checked-references), `LandClaims.register`.
- Produces: `MineColoniesCompat.register()`, `ColonyRightClick.allows(...)`.

- [ ] **Step 1: Failing unit tests.**
  - `ColonyRightClickTest` (names no MineColonies class), one test for each rule of the research, in its order:
    `CD-LND-08 a hut needs ACCESS_HUTS before every other rule`, `CD-LND-08 the protection exception passes a rank that
    is not hostile`, `CD-LND-08 a door passes with ACCESS_TOGGLEABLES`, `CD-LND-08 without colony protection only the hut
    rule holds`, `CD-LND-08 any other block needs RIGHTCLICK_BLOCK`, `CD-LND-08 a container also needs OPEN_CONTAINER`.
  - `CompatImportsTest`: `CD-LND-13 only compat/minecolonies names MineColonies, and only behind isLoaded`. Files that
    name `com.minecolonies` or `com.ldtteam` sit under `/compat/minecolonies/`, and the mod class holds
    `if (ModList.get().isLoaded("minecolonies")) MineColoniesCompat.register()`.
  - `ModMetadataTest`: `CD-LND-14 minecolonies is optional with any version, and its libraries have no entry`.

- [ ] **Step 2: Run** `./gradlew test`. The new tests fail: `ColonyRightClick` does not exist, and the entry and the
  registration are missing.

- [ ] **Step 3: Build files.**

```properties
minecolonies_version=1.1.1419-1.21.1-snapshot
structurize_version=1.0.839-1.21.1-snapshot
blockui_version=1.0.212-1.21.1-snapshot
domum_ornamentum_version=1.0.237-snapshot
multipiston_version=1.2.58-1.21.1
```

```groovy
repositories {
    maven {
        name = 'LDTTeam'
        url = 'https://ldtteam.jfrog.io/ldtteam/modding/'
        content { includeGroup 'com.ldtteam' }
    }
}
dependencies {
    // CD-LND-08, CD-LND-09, CD-LND-14: MineColonies stays optional. Only compat/minecolonies/ names it. AbstractBlockHut
    // implements an interface of Structurize, and IMinecoloniesAPI.getConfig() returns a class of BlockUI.
    compileOnly("com.ldtteam:minecolonies:${minecolonies_version}") { transitive = false }
    compileOnly("com.ldtteam:structurize:${structurize_version}") { transitive = false }
    compileOnly("com.ldtteam:blockui:${blockui_version}") { transitive = false }
}
```

  `verifyJar` adds `com/minecolonies/` and `com/ldtteam/` to the prefixes of claim mod classes that the jar must not
  hold. `neoforge.mods.toml` gains, after the entry of `openpartiesandclaims`:

```toml
[[dependencies.${mod_id}]]
modId="minecolonies"
type="optional"
versionRange="*"
ordering="AFTER"
side="BOTH"
```

- [ ] **Step 4: Code.** `ColonyRightClick` holds the order of the rules and names no MineColonies class:

```kotlin
/** The right-click rule of MineColonies 1.1.1419 for an empty main hand, as RES7 read it (CD-LND-08). */
object ColonyRightClick {
    enum class Need { ACCESS_HUTS, ACCESS_TOGGLEABLES, RIGHTCLICK_BLOCK, OPEN_CONTAINER }

    data class Block(val hut: Boolean, val exception: Boolean, val toggleable: Boolean, val container: Boolean)

    fun allows(block: Block, protection: Boolean, hostile: Boolean, has: (Need) -> Boolean): Boolean = when {
        block.hut && !has(Need.ACCESS_HUTS) -> false
        block.exception && !hostile -> true
        block.toggleable && has(Need.ACCESS_TOGGLEABLES) -> true
        !protection -> true
        !has(Need.RIGHTCLICK_BLOCK) -> false
        else -> !block.container || has(Need.OPEN_CONTAINER)
    }
}
```

  `MineColoniesCompat` registers one object for both questions. Quick Work asks the player's permissions, and a pasture
  asks the permissions of the placer's rank:

```kotlin
object MineColoniesCompat {
    fun register() = LandClaims.register("minecolonies", Check)

    private object Check : LandClaimCheck {
        /** CD-LND-09: the owner's own rule, with the operator bypass of hasPermission(Player, Action). */
        override fun mayUse(owner: ServerPlayer, level: ServerLevel, pos: BlockPos): Boolean =
            allows(level, pos, { it.getRank(owner).isHostile }) { permissions, action -> permissions.hasPermission(owner, action) }

        /** CD-LND-08: the placer's rank by UUID, online or not, with no bypass. A pasture with no placer gets no colony block. Silent. */
        override fun ownerMayUse(ownerId: UUID?, level: ServerLevel, pos: BlockPos): Boolean {
            if (ownerId == null) return level.isLoaded(pos) && colonyAt(level, pos) == null
            return allows(level, pos, { it.getRank(ownerId).isHostile }) { permissions, action ->
                permissions.hasPermission(permissions.getRank(ownerId), action)
            }
        }
    }

    private val ACTIONS = mapOf(
        ColonyRightClick.Need.ACCESS_HUTS to Action.ACCESS_HUTS,
        ColonyRightClick.Need.ACCESS_TOGGLEABLES to Action.ACCESS_TOGGLEABLES,
        ColonyRightClick.Need.RIGHTCLICK_BLOCK to Action.RIGHTCLICK_BLOCK,
        ColonyRightClick.Need.OPEN_CONTAINER to Action.OPEN_CONTAINER,
    )

    // getColonyByPosFromWorld loads the chunk, so every caller asks isLoaded first.
    private fun colonyAt(level: ServerLevel, pos: BlockPos): IColony? =
        IColonyManager.getInstance().getColonyByPosFromWorld(level, pos)?.takeIf { it.isCoordInColony(level, pos) }

    private fun allows(
        level: ServerLevel,
        pos: BlockPos,
        hostile: (IPermissions) -> Boolean,
        has: (IPermissions, Action) -> Boolean,
    ): Boolean {
        if (!level.isLoaded(pos)) return false
        val colony = colonyAt(level, pos) ?: return true
        val permissions = colony.permissions
        val state = level.getBlockState(pos)
        val block = ColonyRightClick.Block(
            hut = state.block is AbstractBlockHut<*>,
            exception = state.`is`(ModTags.colonyProtectionException),
            toggleable = state.`is`(BlockTags.DOORS) || state.`is`(BlockTags.FENCE_GATES),
            container = hasItemHandler(level, pos, state),
        )
        val protection = IMinecoloniesAPI.getInstance().config.server.enableColonyProtection.get()
        return ColonyRightClick.allows(block, protection, hostile(permissions)) { has(permissions, ACTIONS.getValue(it)) }
    }

    /** As the event handler asks: with no side, then with each side. */
    private fun hasItemHandler(level: ServerLevel, pos: BlockPos, state: BlockState): Boolean {
        val blockEntity = level.getBlockEntity(pos)
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, blockEntity, null) != null ||
            Direction.entries.any { level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, blockEntity, it) != null }
    }
}
```

  `CobblemonDynamics` registers it beside the other claim mods:
  `if (ModList.get().isLoaded("minecolonies")) MineColoniesCompat.register()`.

- [ ] **Step 5: Run** `./gradlew build`. The tests of Step 1 pass, `verifyJar` passes, and the jar holds no class of
  `com.minecolonies` or `com.ldtteam`. If Kotlin names a class of Domum Ornamentum, add
  `compileOnly("com.ldtteam:domum-ornamentum:${domum_ornamentum_version}:main") { transitive = false }` and fix the
  inferred line of [Checked references](#checked-references).

- [ ] **Step 6: Commit:** `git commit -m "Ask MineColonies before Quick Work and pasture work in a colony [rag:1]"`.

---

### Task 4: GameTests with the real claim mods

> **Partly deferred by the approver's ruling of 2026-11-11.** The approver: "do we really need the game tests? i thought we were pretty confident in these fixes and we'll be testing them actively in the server". Only
> `./gradlew runGameTestServerFtbChunks` ran, on the code of the `m9-claims` build. Its first run passed 9 of 10 tests. Its
> oracle test `cdLnd06PastureCheckEqualsFtbChunksForEachPlayer` failed: the pasture check threw for every claimed chunk,
> because `PrivacyMode` of FTB Teams 2101.1.12 has a public field `name` with the lower-case serialized name, which
> Kotlin's `name` reads before `Enum.name()`, so `Mode.valueOf("allies")` failed. The check failed closed, with the warning
> "Pastures work no target while the claim check of ftbchunks fails". With the mode mapped by its constant, the second run
> passed all 10 tests: the fixture test, the oracle test of CD-LND-06, CD-LND-10, and the seven Quick Work tests of the
> claims plan. The run also proved the first three facts of the "Inferred" list of the
> [claims plan](2026-11-11-phase-2-quick-work-claims.md). The
> `runGameTestServerOpac` and `runGameTestServerMineColonies` runs, the "Inferred" facts of MineColonies, and Step 4's check
> with each `register` call commented out wait for a follow-up.

**Rows:** CD-QWK-27, CD-QWK-28, CD-QWK-29, CD-QWK-31 and CD-QWK-32 (the real-mod tests of the claims plan), CD-LND-06,
CD-LND-07 and CD-LND-08 (the oracle tests), CD-LND-09 (Quick Work in a colony), CD-LND-10 (no packet to the placer), and
CD-QWK-26 and CD-QWK-30 for MineColonies.

**Files:**
- Modify: `build.gradle` (three source sets, their configurations, three test mods and three runs)
- Modify: `src/gametest/kotlin-shared/codes/honor/cobblemondynamics/test/TestKit.kt` (`plainOwner` and `PlainOwner`)
- Create: `src/gametest/kotlin-claims/codes/honor/cobblemondynamics/test/claims/ClaimScenes.kt`,
  `src/gametest/kotlin-claims/codes/honor/cobblemondynamics/test/claims/PastureOracle.kt`
- Create: the files of the FTB Chunks run and the Open Parties and Claims run that
  [Task 4 of the claims plan](2026-11-11-phase-2-quick-work-claims.md#task-4-gametests-with-the-real-claim-mods) lists
- Create: `src/gametestMineColonies/kotlin/codes/honor/cobblemondynamics/mctest/MineColoniesTestMod.kt`,
  `ColonyFixture.kt`, `ColonyClaimTests.kt`, and `src/gametestMineColonies/resources/META-INF/neoforge.mods.toml`
- Modify: the local notes file. Do not modify `.github/workflows/build.yml`: CI does not run the three claim runs.

**Interfaces:**
- Consumes: Task 4 of the claims plan with its facts, the fixture calls of [Checked references](#checked-references),
  `TestKit.listed`, `TestKit.pressSendOut`, `LandClaims.ownerMayUse`.
- Produces: the runs `runGameTestServerFtbChunks`, `runGameTestServerOpac` and `runGameTestServerMineColonies`, and
  `PastureOracle.assertSame(helper, players, pos, own: (ServerPlayer) -> Boolean)`.

- [ ] **Step 1: The runs and the fixtures.** Carry over Steps 1 and 2 of Task 4 of the claims plan as written, with the
  facts that the beta.2 build found for it: the action bar arrives as a `ClientboundSystemChatPacket` whose `overlay()`
  is true, the listener of `plainOwner` overrides `hasChannel`, `FtbChunksFixture.login` comes before `claim`, and
  `OpacFixture.claim` passes the sub-config index -1. Add the third run in the same form:

```groovy
sourceSets {
    // CD-LND-08, CD-LND-09: MineColonies and its three required libraries, on the mod set of the core run.
    gametestMineColonies {
        compileClasspath += sourceSets.main.output
        runtimeClasspath += sourceSets.main.output
        kotlin.srcDir 'src/gametest/kotlin-shared'
        kotlin.srcDir 'src/gametest/kotlin-claims'
        resources.srcDir 'src/gametest/resources-shared'
    }
}
dependencies {
    gametestMineColoniesImplementation("com.ldtteam:minecolonies:${minecolonies_version}") { transitive = false }
    gametestMineColoniesImplementation("com.ldtteam:structurize:${structurize_version}") { transitive = false }
    gametestMineColoniesImplementation("com.ldtteam:blockui:${blockui_version}") { transitive = false }
    gametestMineColoniesRuntimeOnly("com.ldtteam:domum-ornamentum:${domum_ornamentum_version}:main") { transitive = false }
}
```

  The run `gameTestServerMineColonies` logs to `run/gametest-minecolonies/` and enables the namespace
  `cobblemon_dynamics`, as the other two claim runs do, so that its tests use the structures of
  `src/gametest/resources-shared`. NeoForge takes a test's namespace as its structure namespace, and a namespace of its own
  would need its own copy of each structure. `ColonyFixture` makes a colony with `IColonyManager.getInstance().createColony(level,
  center, owner, "cdtest", "cdtest")`, gives players ranks with `IPermissions.addPlayer(GameProfile, Rank)` and
  `setPlayerRank`, and deletes the colony in `TestKit.afterTest` with `deleteColonyByWorld(colony.getID(), false,
  level)`. Each colony test has its own batch, because a colony claims chunks around its center. The oracle of the run
  posts the click of the player and reads its cancellation:

```kotlin
fun colonyAllows(player: ServerPlayer, pos: BlockPos): Boolean =
    !NeoForge.EVENT_BUS.post(
        PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)),
    ).isCanceled
```

  A failing fixture test for each fact of the "Inferred" list of [Checked references](#checked-references): a colony of
  the plain owner A holds furnace A in the same tick, a stranger's posted click on furnace A is canceled, and the
  default flags of the Officer, Friend and Neutral ranks are those of the list.

- [ ] **Step 2: Run** `./gradlew runGameTestServerFtbChunks`, `./gradlew runGameTestServerOpac` and
  `./gradlew runGameTestServerMineColonies`, one JVM at a time. Fix each fixture first, and record each fact of the
  "Inferred" lists that a run proves or disproves, in this plan and in the claims plan.

- [ ] **Step 3: Failing claim tests.**
  - The Quick Work tests of Step 3 of Task 4 of the claims plan, as written. In the FTB Chunks run:
    `cdQwk27ClaimOwnerGetsAnOrder`, `cdQwk27MemberGetsAnOrder`, `cdQwk27AllyGetsAnOrder`,
    `cdQwk27Qwk32StrangerGetsNoOrder`, `cdQwk27AllyGetsNoOrderInPrivateMode`, `cdQwk29MemberWhoLeavesThePartyStops` and
    `cdQwk29ClaimOfARunningOrderStopsIt`. In the Open Parties and Claims run: `cdQwk28ClaimOwnerGetsAnOrder`,
    `cdQwk28MemberGetsAnOrder`, `cdQwk28AllyGetsNoOrderByDefault`, `cdQwk28AllyGetsAnOrderWithFullAccessForAllies`,
    `cdQwk28Qwk32StrangerGetsNoOrder` and `cdQwk29RemovedMemberStops`. Their owners are of the exact class `ServerPlayer`
    (CD-QWK-31).
  - In the FTB Chunks run, `cdLnd06PastureCheckEqualsFtbChunksForEachPlayer`: the setups of CD-LND-06 on
    `PastureOracle.assertSame`, with `own = { !FTBChunksAPI.api().manager.shouldPreventInteraction(it,
    InteractionHand.MAIN_HAND, pos, Protection.INTERACT_BLOCK, null) }`. After the online checks, each player leaves with
    `PlayerList.remove`, and the pasture checks give the same answers.
  - In the Open Parties and Claims run, `cdLnd07PastureCheckEqualsFullAccessForEachPlayer`: the setups of CD-LND-07,
    with `own = { !OpenPACServerAPI.get(it.server).chunkProtection.onBlockInteraction(it, InteractionHand.MAIN_HAND,
    ItemStack.EMPTY, level, pos, Direction.UP, false, false) }`, then the same logout.
  - In the MineColonies run, `cdLnd08PastureCheckEqualsTheColonyForEachRank`: the setups of CD-LND-08 on furnace A and
    an enchanting table, with `own = { colonyAllows(it, pos) }`, then the same logout.
  - In the MineColonies run, `cdLnd09QuickWorkFollowsTheOwnersClickInTheColony`,
    `cdLnd09OperatorInCreativeModePassesAndInSurvivalDoesNot` and `cdLnd09LoweredRankEndsTheOrderWithin20Ticks`, with the
    Charmander and the send-out of `ClaimScenes.sendOutAt`.
  - In each run, `cdLnd10PasturePlacerGetsNoPacket`: the plain owner places a Work Mode pasture whose work area holds
    furnace A in a land claim that refuses the owner. A Charmander joins the pasture, and the owner's connection records
    packets for 400 ticks. No chat or action-bar packet arrives.

- [ ] **Step 4: Run** the three runs again. Every test passes. Check that each claim test fails when you comment out the
  `register` call of its claim mod in `CobblemonDynamics`, then restore the call. Record the results of the three runs,
  with their commit, in the pull request body.

- [ ] **Step 5: Where the claim runs run.** CI does not run the three claim runs, so `.github/workflows/build.yml` does not
  change. They run in the pull request of this plan, at each gate, and in each later pull request that touches the claims
  code. The claims code is the list of Step 4 of Task 4 of the claims plan, with `compat/minecolonies/`, the pasture
  checks of `WorkEngine` (`poll`, `assign` and `nearestFreeBed`) and `src/gametestMineColonies/`. The development notes'
  pull request rules hold this list. The jars of the claim mods never go into a CI artifact or a log upload.

- [ ] **Step 6: Gotchas.** Add to the local notes file what the runs showed, at least the three lines of Step 5 of Task 4
  of the claims plan and one line on how a test makes a colony.

- [ ] **Step 7: Commit:** `git commit -m "Test the claim checks against FTB Chunks, Open Parties and Claims and MineColonies [rag:2]"`.

---

### Task 5: The test pack

> **Deferred by the approver's ruling of 2026-11-11.** The approver: "do we really need the game tests? i thought we were pretty confident in these fixes and we'll be testing them actively in the server". The pins, the cases and the
> README are written, and ruff passes, but `py scripts/testpack/testpack.py compat`, `smoke` and `deps` have not run, before
> or after the change. A follow-up runs them.

**Rows:** CD-QWK-33 (a dedicated server with each claim mod), CD-CMP-08, CD-CMP-09 and CD-CMP-10 (the load lines in the
logs of real servers), CD-LND-13 (the server with MineColonies).

**Files:**
- Modify: `scripts/testpack/mods.json`, `scripts/testpack/testpack.py`, `scripts/testpack/README.md`

- [ ] **Step 1: Pins.** Carry over Step 1 of Task 6 of the claims plan: the five pins of FTB Chunks, FTB Teams, FTB
  Library, Architectury API and Open Parties and Claims. Add the five MineColonies pins, each with `"source": "maven"`, the
  repository URL of [Checked references](#checked-references) and the SHA-512 of its table. For example:

```json
"minecolonies": {"source": "maven", "version": "1.1.1419-1.21.1-snapshot", "filename": "minecolonies-1.1.1419-1.21.1-snapshot.jar",
  "url": "https://ldtteam.jfrog.io/ldtteam/modding/com/ldtteam/minecolonies/1.1.1419-1.21.1-snapshot/minecolonies-1.1.1419-1.21.1-snapshot.jar",
  "sha512": "b82c4132afef7de53bcea9a49bada2ab7a3b59a989bf4c3ef6f65d4822b60cdc8996c7fe2d251767a98a6018960bb8118b4469fe1e1de6dc52c6d40e1e609df3"}
```

- [ ] **Step 2: Failing cases.** Carry over Step 2 of Task 6 of the claims plan: the cases `compat_ftbchunks` and
  `compat_opac`, and the three load lines in `scenario_smoke` and `deps`. Add one case, and give every case the
  MineColonies package in `absent`:

```python
CompatCase("compat_minecolonies", ("minecolonies", "structurize", "blockui", "domum_ornamentum", "multipiston"),
           OUR_PACKAGE + "compat.minecolonies.MineColoniesCompat",
           (OUR_PACKAGE + "compat.ftbchunks.", OUR_PACKAGE + "compat.opac.")),
```

  Run `py scripts/testpack/testpack.py compat` and `py scripts/testpack/testpack.py smoke` on the commit before this task:
  both fail.

- [ ] **Step 3: Run** `py scripts/testpack/testpack.py compat`, `smoke` and `deps`. Each prints `PASS`. The README's case
  table gains the three cases with `CD-QWK-33` and `CD-LND-13`, and its `smoke` and `deps` rows cite CD-CMP-08 to
  CD-CMP-10, so that `scripts/gate_report.py` maps the rows to the test pack.

- [ ] **Step 4: Commit:** `git commit -m "Start a test server with each claim mod and check the load lines [rag:1]"`.

---

### Task 6: Publication without settings

**Rows:** CD-REL-04 (the amended row, R69), CD-REL-09 (check 42 follows the amended Demo).

**Files:**
- Modify: `.github/workflows/release.yml` (the publish job)
- Modify: `src/test/kotlin/codes/honor/cobblemondynamics/build/ReleaseWorkflowTest.kt`
- Modify: `docs/testing/manual-checklist.md` (check 42)

- [ ] **Step 1: Failing unit tests.** `ReleaseWorkflowTest`:
  - `CD-REL-04 a public tag publishes the jar to Modrinth and CurseForge` becomes
    `CD-REL-04 the publish job skips a site without its settings, with a notice`. The settings step holds no `exit 1`,
    writes one output for each site, `modrinth` and `curseforge`, and writes `::notice::` with the names of the absent
    settings.
  - `CD-REL-04 each site has its own publish step`: two steps use `Kira-NT/mc-publish@v3`. The Modrinth step has
    `if: steps.settings.outputs.modrinth == 'true'` and only the inputs `modrinth-id` and `modrinth-token` of a site, and
    the CurseForge step the same for `curseforge`. Both keep `files`, `version-type`, `loaders` and `game-versions`, and
    neither has `github-token`.
  - `CD-REL-05 ...` reads the `version` input of both publish steps, not of a single one.
  - The tests of CD-REL-12 stay as they are.

- [ ] **Step 2: Run** `./gradlew test --tests "*ReleaseWorkflowTest"`. The new tests fail on the current workflow.

- [ ] **Step 3: Workflow.** The settings step:

```yaml
      # CD-REL-04, R69: a public tag always gets its GitHub release. A site without its token or project id gets a notice.
      - name: Check the publishing settings
        id: settings
        env:
          MODRINTH_TOKEN: ${{ secrets.MODRINTH_TOKEN }}
          CURSEFORGE_TOKEN: ${{ secrets.CURSEFORGE_TOKEN }}
          MODRINTH_PROJECT_ID: ${{ vars.MODRINTH_PROJECT_ID }}
          CURSEFORGE_PROJECT_ID: ${{ vars.CURSEFORGE_PROJECT_ID }}
        run: |
          for pair in modrinth:MODRINTH curseforge:CURSEFORGE; do
            site=${pair%%:*}
            token=${pair#*:}_TOKEN
            project=${pair#*:}_PROJECT_ID
            if [[ -n ${!token} && -n ${!project} ]]; then
              echo "$site=true" >> "$GITHUB_OUTPUT"
            else
              echo "$site=false" >> "$GITHUB_OUTPUT"
              echo "::notice::No upload to $site. Set the secret $token and the variable $project to publish there."
            fi
          done
```

  The step "Publish to Modrinth and CurseForge" becomes two steps, "Publish to Modrinth" with
  `if: steps.settings.outputs.modrinth == 'true'` and "Publish to CurseForge" with
  `if: steps.settings.outputs.curseforge == 'true'`. Each passes only its own site's id and token, and every other input
  of the old step.

- [ ] **Step 4: Checklist.** Check 42 follows the Demo of the amended CD-REL-04: push `v1.0.0` with no publishing
  settings. Expected: the workflow succeeds, the GitHub release holds `cobblemon_dynamics-1.0.0+mc1.21.1.jar` and is not
  a pre-release, and the publish job shows two notices. The approver then uploads that jar to CurseForge by hand.

- [ ] **Step 5: Run** `./gradlew build`. Every test passes, `ManualChecklistTest` included.

- [ ] **Step 6: Commit:** `git commit -m "Skip a release site without its settings, with a notice [rag:1]"`.

---

### Task 7: Docs, wording and the SHOULD rows

**Rows:** CD-LND-16 (the guide and the integration page), CD-REL-08 (the guide at the `v1.0.0` tag), CD-REL-11 (the
notice), CD-STK-07 (the text, by decision 13), CD-UI-03 and CD-UI-09 (checks 8 and 23 retire, by decision 14).

**Files:**
- Modify: `docs/player-guide.md`, `docs/modpack-integration.md`, `README.md`, `ARCHITECTURE.md`, `gradle.properties`
- Modify: `docs/plans/2026-11-09-v1-spec-sheet.md` (CD-STK-07), `docs/testing/manual-checklist.md` (checks 8 and 23)
- Modify: `src/test/kotlin/codes/honor/cobblemondynamics/build/PlayerGuideTest.kt`
- Modify, in the wording step: the prose of the spec sheet, the plans, the ADRs, `ARCHITECTURE.md` and the testing docs

- [ ] **Step 1: Failing unit test.** `PlayerGuideTest`: `CD-LND-16 the guide has a section Land claims`. The section
  names FTB Chunks, Open Parties and Claims and MineColonies, and it holds "while you are offline". `RepositoryFilesTest`
  keeps the README notice of CD-REL-11.

- [ ] **Step 2: Docs.**
  - `docs/player-guide.md`: a section "Land claims". Quick Work asks the claim mods about the owner, and the workers of a
    pasture ask about the pasture's owner, also while that player is offline. Each claim mod decides by its own rules:
    the members and allies of an FTB Chunks team by default, the party of an Open Parties and Claims claim (allies when
    the claim's owner sets `fullAccess` to `A`), and the ranks of a colony. A worker skips a machine that its pasture's
    owner may not use, and a Work Leash binding on such a block ends with a chat message. The text says Cobblemon.
  - `docs/modpack-integration.md`: the tested versions of CD-LND-16, the pasture checks, the warning of a check that
    fails, the three claim runs, and what COHO sees: with FTB Chunks and MineColonies, pastures keep out of the claims of
    other teams and colonies.
  - `README.md`: the compatibility list names MineColonies, and the ADR table lists ADR-0014.
  - `ARCHITECTURE.md`: the sections of Step 1 of Task 7 of the claims plan, with `compat/minecolonies/`, the pasture
    question of the gate, and the checks in `poll`, `assign` and `nearestFreeBed`.
  - CD-STK-07, by decision 13 (ruled): the row reads "keep the flame gauge full while the furnace cooks", with a
    corrections entry, and the furnace line of `ARCHITECTURE.md` says that the handler raises `litTime` to 2 and sets
    `litDuration` to 2 when `litTime` is below 2. The code and check 29 stay as the approver play-tested them.
  - `docs/testing/manual-checklist.md`, by decision 14 (ruled): checks 8 (CD-UI-03) and 23 (CD-UI-09) retire in place, so
    that the numbers of the other checks stay. Each keeps its number with one line that says the row rests on code
    reading since the rulings of 2026-11-11, in a form that does not start with the bold row id. The pattern `CHECK` of
    `scripts/gate_report.py` must not match the two new lines, so that the next report shows no checklist part for the
    two rows.
  - CD-REL-11: the release notes of `v1.0.0` hold the notice, so that the approver can copy it to the pages of the sites.

- [ ] **Step 3: Tools.** `RUNS` in `scripts/gate_report.py` gains the three claim runs, as Step 2 of Task 7 of the claims
  plan gives the first two:
  `Run("minecolonies", "GameTests with MineColonies", "runGameTestServerMineColonies",
  ROOT / "run/gametest-minecolonies/logs/latest.log", ("src/gametestMineColonies/kotlin", "src/gametest/kotlin-claims",
  "src/gametest/kotlin-shared"), "cobblemon_dynamics")`. Its `PHASE_2_ROWS` gains CD-CMP-08 to CD-CMP-10, as Step 2
  of Task 7 of the claims plan gives, and equals the list of `scripts/check_plan_coverage.py`, with the rows of the
  logistics branch.

- [ ] **Step 4: Wording, the last docs step (decision 15, ruled).**
  - `gradle.properties`: `mod_description=Pastured Cobblemon power and run the machines of your base.` NeoForge shows
    this text in the mods list, so it is player-facing.
  - The prose of the spec sheet, the plans, the ADRs, `ARCHITECTURE.md` and the testing docs says Cobblemon for the
    creatures. The pass changes prose only. It never changes an API name in backticks (such as `Pokemon`,
    `PokemonEntity` or `PokemonPastureBlockEntity`), a file name or path, a lang key, a quote, or the README's notice of
    CD-REL-11, which `RepositoryFilesTest` reads ("The Pokémon Company").
  - Find each case with `git grep -P "Pok\S{1,3}mon"`, because the grep of Git Bash misses the precomposed é in a class
    such as `[eé]`.
  - After the pass, run `check_spec_sheet.py`, the checker that the Checks section of the spec sheet names. Its counts do
    not change.

- [ ] **Step 5: Run** `./gradlew build`, `py scripts/check_plan_coverage.py`, `--phase 2` and `--phase 3`. Every test passes,
  and each mode prints `missing: 0`.

- [ ] **Step 6: Commit and hand over.** `git commit -m "Document the claim checks of pastures and MineColonies [rag:2]"`. The
  coordinator opens the pull request of `m9-claims`. Its body holds the perf results of Task 2, the multiplayer check of
  Task 2, the facts that Task 4 proved, and the results of the three claim runs on the tip of the branch.

---

### Task 8: The release gate and 1.0.0

**Rows:** CD-REL-05 (the version), CD-REL-12 (check 41), CD-REL-14 (the release gate), CD-REL-15 (the pack), CD-REL-04
(the tag and check 42), and the Demos of CD-PLAT-06, CD-CMP-01, CD-CMP-03, CD-CMP-04, CD-CMP-05 and CD-REL-01
(decision 14).

**Files:**
- Modify: `gradle.properties` (`mod_version=1.0.0`)
- Modify: `scripts/gate_report.py` (`--phase 3`)
- Create: `docs/testing/phase-3-gate.md`
- Modify: `docs/testing/phase-2-gate.md` (the play-test note)

- [ ] **Step 1: The play-test note.** Record the approver's play-test of `1.0.0-beta.3` in the play-test note of
  `docs/testing/phase-2-gate.md`: date, launcher and instance, and a result for each check of its table, with check 59
  (CD-QWK-21). CD-REL-14 needs this note dated before the `v1.0.0` tag.

- [ ] **Step 2: The report script.** `PHASES` gains Phase 3: the report `docs/testing/phase-3-gate.md` holds every
  `MUST` and `MUST NOT` row, reads the deferred lists of M5 Task 9 and M8 Task 7, marks CD-REL-04 and CD-REL-15 as
  "after the tag", takes CD-REL-14 as its record with the play-test note of the Phase 2 report, and takes CD-REL-13 from
  the Phase 1 report. The rows of its Phase 1 report leave out the LND area, as the coverage script does. The docstring
  and the usage lines of the script name Phase 3, and its `--phase` takes 3.

- [ ] **Step 3: The version.** `mod_version=1.0.0`. `ModMetadataTest` and the jar check of the release workflow read the
  version from the tag, so no test changes.

- [ ] **Step 4: Run every check on one commit,** with `JAVA_HOME` set to a JDK 21, one JVM at a time, under the JVM lock:
  `./gradlew build runGameTestServer runGameTestServerCore runGameTestServerPerf --console=plain`, then
  `runGameTestServerFtbChunks`, `runGameTestServerOpac` and `runGameTestServerMineColonies`, then
  `py scripts/testpack/testpack.py` with `smoke`, `deps`, `compat`, `reload` and `restart`, then
  `py scripts/art.py check --references auto --release` and `py scripts/testpack/testpack.py client`. Delete the
  release jars in `build/libs` before the test pack. Watch `HealerTests.cdHeal17SpentHealerGoesToBedOrRestsInPlace`.

- [ ] **Step 5: The Demos that never ran (decision 14).**
  - The coordinator runs check 40 (CD-REL-01) with a pull request that holds one failing unit test, and closes it.
  - The coordinator checks check 41 (CD-REL-12): the release runs of `v1.0.0-alpha.1` and `v1.0.0-beta.1` ended, both
    GitHub releases are pre-releases, and the workflow published neither version to Modrinth or CurseForge. The
    approver's own upload of `1.0.0-beta.2` to CurseForge is not a run of the workflow.
  - The approver runs checks 5, 36, 37, 38 and 39 in the next play-test, in the play-test instance with the jars of the
    checklist. The spark run of known issue (d) of the Phase 1 gate has no ruling yet, so the coordinator asks the
    approver about it with the play-test.
  - Checks 8 and 23 retired in Task 7, because CD-UI-03 and CD-UI-09 rest on code reading (decision 14).

- [ ] **Step 6: The report.** Run `py scripts/gate_report.py --phase 3` with the saved outputs of Step 4. It writes
  `docs/testing/phase-3-gate.md` on the release commit. Every `MUST` and `MUST NOT` row passes there, except CD-REL-04 and
  CD-REL-15, which come after the tag.

- [ ] **Step 7: Commit and hand over.** `git commit -m "Run the release gate of 1.0.0 [rag:2]"`. The coordinator opens the
  pull request of `m9-release`. After it merges, the coordinator gives the approver and COHO-2 the path, the version and
  the SHA-1 of the release jar (CD-REL-15), and asks the approver for the go-ahead on the `v1.0.0` tag.

- [ ] **Step 8: The tag.** After the approver's go-ahead, the coordinator pushes `v1.0.0` on the merge commit. The release
  run builds the jar, makes a GitHub release that is not a pre-release, and shows the notices of the skipped sites. The
  approver uploads the jar to CurseForge by hand, with the optional relations to Create, Applied Energistics 2, FTB
  Chunks, Open Parties and Claims and MineColonies. Decision 12, pending the approver, says whether the approver also
  uploads it to Modrinth. Record the run URL, its jobs and the page of each site in the report, for check 42. CD-REL-15
  completes when the next release of the approver's pack names Cobblemon Dynamics 1.0.0.

---

### Task 9: Quick Work for Fairy-types at enchanting tables

**Rows:** CD-QWK-36 (the Quick Work flag), CD-QWK-05 (a Fairy-type's order only for a role that opts in), CD-QWK-09 (no
demand at the start), CD-QWK-19 (no Standby end), CD-CFG-07 (the flag in the role file), CD-MUS-02 (the rationale names the
order). Ruling: R70.

**Files:**
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/role/RoleDefinition.kt`, `role/RoleCodec.kt`,
  `src/main/resources/data/cobblemon_dynamics/cobblemon_dynamics/roles/muse.json`
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/engine/QuickWorkRules.kt`, `engine/WorkEngine.kt` (`start`, `tickOrder`)
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/engine/QuickWorkRulesTest.kt`, `role/RoleCodecTest.kt`,
  `role/BuiltInRoleFilesTest.kt`, `src/gametest/kotlin/codes/honor/cobblemondynamics/test/QuickWorkTests.kt`
- Modify: the spec sheet, Amendment 2 of ADR-0013, `docs/player-guide.md`, `ARCHITECTURE.md`

- [ ] **Step 1: Failing tests.**
  - `QuickWorkRulesTest`: `CD-QWK-36 a role that opts in takes an order with no auto-assign flag and no scan, but not with two
    targets`, `CD-QWK-05 CD-QWK-36 a Fairy-type gets an order only for a role that opts in` (the Muse at an enchanting table,
    no role at a water wheel), `CD-QWK-09 CD-QWK-36 the order of a role that opts in starts with no demand, after the other
    refusals` and `CD-QWK-19 CD-QWK-36 the order of a role that opts in has no Standby end, and keeps every other end`.
  - `RoleCodecTest`: `CD-QWK-36 CD-CFG-07 the Quick Work flag defaults to false, and a file can set it`.
  - `BuiltInRoleFilesTest`: `CD-QWK-36 only the Muse role file opts in to Quick Work`.
  - `QuickWorkTests.cdQwk36FairyAtAClosedEnchantingTableWaitsAndWorks`: a Lv50 Clefairy sent out at enchanting table A, whose
    menu is closed, gets an order and waits in Standby. A player opens the menu with a pickaxe in it: the Clefairy Works within
    20 ticks, and the offers equal those of a table with 8 bookshelves, as `MuseTests.cdMus03Lv50MuseGivesATableTheOffersOfEightShelves`
    compares them. The menu closes, and the order still holds 210 ticks later.

- [ ] **Step 2: Run** `./gradlew test --tests "*QuickWorkRulesTest" --tests "*RoleCodecTest" --tests "*BuiltInRoleFilesTest"`.
  The tests fail to compile: `quickWork` and `waitsForDemand` do not exist.

- [ ] **Step 3: Code.**
  - `RoleDefinition` gets `quickWork: Boolean = false`, and `RoleCodec` reads `Codec.BOOL.optionalFieldOf("quick_work", false)`.
    `muse.json` sets `"quick_work": true`.
  - `QuickWorkRules.mayOrder`: `((role.autoAssign && scanned) || role.quickWork) && shape == BindingShape.SINGLE`.
  - `QuickWorkRules.pick`: a Fairy-type's candidates are the fitting roles that opt in, and with none it gets `Pick.None`.
  - `QuickWorkRules.refusal` gets `waitsForDemand`, and `Watch` gets `waitsForDemand`. `endReason` skips the Standby end while
    it is true. `WorkEngine.start` passes `role.definition.quickWork`, and `tickOrder` passes the flag of the order's role.

- [ ] **Step 4: Run** `./gradlew build` and `./gradlew runGameTestServer`. Every test passes, `QuickWorkTests.cdQwk05FairyTypeGetsNoOrder`
  and `QuickWorkSourceTest` (CD-QWK-23) included.

  > **Partly deferred by the approver's ruling of 2026-11-11.** The approver: "do we really need the game tests? i thought we were pretty confident in these fixes and we'll be testing them actively in the server". On the code
  > before this task, the unit tests of Step 1 did not compile, and `cdQwk36FairyAtAClosedEnchantingTableWaitsAndWorks`
  > got no order. With the code, `./gradlew build` passed, and one `runGameTestServer` showed a bug that the test caught.
  > The order started and then ended with "Clefairy stopped working: Enchanting Table can no longer be worked", because the
  > engine's `mayKeep` released each claim of a role that is neither bound nor auto-assign. `mayKeep` now keeps the claim of a
  > Quick Worker whose role opts in. That fix passed `./gradlew build`, and the GameTest run after it is deferred.

- [ ] **Step 5: Commit:** `git commit -m "Let a Fairy-type Quick Work an enchanting table as the Muse [rag:2]"`.

---

## Design decisions for the approver

The rulings of R67, R68 and R69 settle the features. This plan made these further calls. On 2026-11-11, under the
approver's delegation, the coordinator ruled on them and moved the LND rows to `agreed`:

- Calls 1 to 11, 13, 15 and 16 stand as written below.
- Call 14 changed, and the list below holds its new form. The corrections list of the spec sheet records the amendment
  of CD-UI-03 and CD-UI-09.
- Calls 12 and 17 are pending the approver. The coordinator relays the answers, and no row depends on them.

1. **Whose rights: the placer.** The approved feature (R67) names "the pasture's owner", and the placer (`ownerId`) is the
   owner of the pasture. The other forms were each worker's owner, or both. A binding also needs its binder's click
   (CD-SEC-11).
2. **A refused binding clears.** It clears through the leash resets with the reason `land_claim` and a message to the
   binder. The other form kept the binding and held the worker in Standby, but then the binding would keep reserving
   the block against the claim's own players.
3. **FTB Chunks by UUID copies the steps of its player check.** FTB Chunks has no check by UUID, and its player check
   shows the player its text. The oracle tests of Task 4 compare the copy with FTB Chunks for each tested version.
4. **Open Parties and Claims by full access.** `hasChunkAccess` by UUID refuses the blocks of the exception lists, such
   as the crafting table, which a player without full access may click. No role works such a block.
5. **MineColonies: no operator bypass for pastures.** MineColonies grants the bypass only to a player in creative mode at
   the click. Quick Work keeps the bypass, because the owner is there.
6. **MineColonies: the free blocks of a colony are refused.** The API has no getter for them. The tag of protection
   exceptions still passes.
7. **Fail closed for pastures.** A broken API of a claim mod stops all pasture work on that server, with one warning,
   until a release of Dynamics follows the change.
8. **Pin the MineColonies of COHO, with any version.** The tests use 1.1.1419, as COHO does, although 1.1.1420 exists.
   `neoforge.mods.toml` declares `minecolonies` with `versionRange="*"`, as the other claim mods.
9. **Three claim runs outside CI.** As for FTB Chunks and Open Parties and Claims, by the coordinator's ruling of
   2026-11-11.
10. **No message for an auto-assigned worker.** The scan skips the target, and nobody asked for that target.
11. **A pasture with no placer works no block in a land claim.**
12. **Modrinth: pending the approver.** G9 names Modrinth and CurseForge. The approver uploads to CurseForge by hand, and
    the workflow uploads to a site only with its settings. The question for the approver: does the approver also upload
    1.0.0 to Modrinth, or does 1.0.0 go to CurseForge only? Task 8 Step 8 follows the answer.
13. **CD-STK-07.** The row says "set the burn duration to 1,600 ticks". The code keeps `litTime` and `litDuration` at 2
    ticks, so the gauge reads full, and the approver play-tested that (check 29). Task 7 changes the text of the row and
    of `ARCHITECTURE.md`, not the code.
14. **The Demos that never ran (changed by the coordinator).** Checks 5, 8, 23 and 36 to 40 never ran, and CD-CMP-03 and
    CD-CMP-04 have no other test. By the approver's ruling of 2026-11-10 ("no multiplayer game tests, we don't have that
    capacity"), CD-UI-03 and CD-UI-09 now rest on their automated tests and a code reading, so checks 8 and 23, which
    need two players, retire in Task 7. The coordinator runs checks 40 and 41, and the approver runs checks 5 and 36 to
    39 in the next play-test (Task 8 Step 5).
15. **The internal docs.** The spec sheet, the plans, the ADRs, `ARCHITECTURE.md` and the testing docs still say Pokémon,
    in about 274 lines. The last docs step of Task 7 replaces the word in prose only. It keeps API names in backticks,
    file names, lang keys, quotes and the README's notice of CD-REL-11.
16. **Quick Work review finding 4.** A Brain that cannot hold an order gets a message that blames the target. It stays
    a known issue for 1.0.0.
17. **A play-test of the 1.0.0 candidate: pending the approver.** CD-REL-14 needs the approver's play-test of a Phase 2
    build, which the play of `1.0.0-beta.3` gives. The pasture checks and MineColonies get automated tests only. The
    question for the approver: do you want to play the release candidate before the `v1.0.0` tag? If so, Task 8 adds
    that play-test before Step 7.

## Coverage check

`scripts/check_plan_coverage.py --phase 3` selects every row of the LND area, and the Phase 3 parts of the rows of
[Rows of earlier phases with a Phase 3 part](#rows-of-earlier-phases-with-a-phase-3-part). It checks that a task section of
this file cites each one. A task section runs from a `### Task` heading to the next heading of level 1 to 3. The Phase 1
mode leaves out the LND area, and the Phase 2 mode does not change. Run the three modes after any plan edit. Each mode
prints each missing row and `missing: N`, and exits non-zero when a row is missing.
