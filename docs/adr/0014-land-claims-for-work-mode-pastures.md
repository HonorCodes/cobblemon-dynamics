# ADR-0014: Land claims for Work Mode pastures

Status: Accepted (2026-11-11). The coordinator accepted it under the approver's delegation, with the rulings on the calls
of the [Phase 3 plan](../plans/2026-11-11-phase-3-release.md#design-decisions-for-the-approver). The calls of this record
stand as written.

## Context

The approver's ruling of 2026-11-11, input R67 of the [spec sheet](../plans/2026-11-09-v1-spec-sheet.md), reads: "yes,
ship beta.3; then work on beta.4 while we play. make beta.4 the actual release, not a beta anymore. and make sure we have
all planned features in." With it, the approver approved two features:

1. Claim checks for Work Mode pastures. The auto-assignment and the workers of a pasture must never work a target in a
   chunk that the pasture's owner may not interact with. The owner may be offline, so the check works from the owner's
   UUID, with the team members, parties and allies of each claim mod, under that mod's own rules. Leash bindings already
   obey canceled clicks (CD-SEC-11).
2. MineColonies colony protection, for Quick Work and for pastures, beside FTB Chunks and Open Parties and Claims.

The ruling answers open question Q9 of the spec sheet. [Amendment 1 of ADR-0013](0013-quick-work-for-party-pokemon.md#amendment-1-land-claims-2026-11-11)
gave claim checks to Quick Work only. Its Decision 8 kept pastures as they were, and the Phase 2 gate report lists that as
a known issue. Input R68 holds the MineColonies jars and settings of COHO 2.4.0, which COHO-2 sent through the
coordinator.

### The engine today

- The work area of a pasture can reach into the land claim of another player. A scan pass offers every target with
  demand in the area, and the workers then work the machines there.
- The engine changes block entities directly. Claim mods guard clicks and other player events, so they never see this
  work.
- A leash binding needs the binder's own click, which a claim mod can cancel (CD-SEC-11). A binding stays when the right
  ends later, for example when the binder leaves a party. A binding reserves its target across all pastures (ADR-0005).
- Quick Work asks each claim mod about the owner's online `ServerPlayer` (CD-QWK-31). That check needs a player in the
  player list, and FTB Chunks shows the player its own text when it prevents a click.

COHO 2.4.0 runs FTB Chunks and MineColonies. So on COHO, a pasture can work the machines in another team's claim or in a
colony today.

### Research (RES7)

The research used `javap` on 2026-11-11, on the jars of COHO 2.4.0 from the cache `E:/rch/jars/2026-11-02/` and on the
Open Parties and Claims jar of RES6. The [Phase 3 plan](../plans/2026-11-11-phase-3-release.md#checked-references) lists
the signatures.

#### FTB Chunks 2101.1.23 and FTB Teams 2101.1.12

- FTB Chunks has no check by UUID. `ClaimedChunkManager.shouldPreventInteraction(Entity, InteractionHand, BlockPos,
  Protection, Entity)` returns false for every actor that is not a `ServerPlayer`. For a player, it takes these steps, in
  this order (`javap -c` on `ClaimedChunkManagerImpl`, `Protection` and `ChunkTeamDataImpl`):
  1. With `disable_protection` on, or with the player's admin bypass (`getBypassProtection(UUID)`), it allows.
  2. For a fake player while the `fake_players` setting overrides, the setting decides.
  3. In a chunk that no team claims, it allows, unless `FTBChunksWorldConfig.noWilderness(player)` is true.
  4. `Protection.INTERACT_BLOCK` allows a block of the tag `ftbchunks:interact_whitelist`. In a claim, it allows when
     `ChunkTeamData.canPlayerUse(player, FTBChunksProperties.BLOCK_INTERACT_MODE)` is true. Every other case prevents,
     unless the player is a spectator.
  5. `canPlayerUse` reads the team's mode. `public` allows, `allies` asks `ChunkTeamData.isAlly(UUID)`, and `private`
     asks `Team.getRankForPlayer(UUID).isMemberOrBetter()`. A fake player gets the team's fake player settings instead.
  6. A prevention shows "Interaction prevented here by claim protection!" with a cooldown of 2,000 ms, and it logs the
     prevented access in the team data.
- `isAlly(UUID)` is true for every player while `ally_mode` is `forced_all`, and true for a member or better. While
  `ally_mode` is `forced_none`, it is false for everyone else. Otherwise it is true for the rank `ALLY`.
- `noWilderness(null)` returns the value of `no_wilderness`. With a player, it also reads the dimension list and the
  permission of FTB Ranks.
- Every read of steps 1, 3, 4 and 5 works from a UUID and the team data, which FTB Teams keeps for offline players.

#### Open Parties and Claims 0.33.0

- `IChunkProtectionAPI.hasChunkAccess(UUID, ResourceLocation, int, int)` is a public check by UUID. It finds the claim of
  the chunk and the player config of the claim. It returns true where no claim exists.
- In a claim, it applies the access overriders of other mods, `claims.protectClaimedChunks`, the value `E` of
  `claims.protection.exceptions.fullAccess`, the claim's owner, and then the group of `fullAccess`. The party group asks
  `areInSameParty(owner, accessor)`, and the ally group asks `isPlayerAllying(owner, accessor)`, both by UUID. So an
  offline accessor gets the same answer as an online one.
- A player without full access can still use the blocks of the server's exception lists, for example
  `minecraft:crafting_table` in the default lists. `hasChunkAccess` does not read those lists.

#### MineColonies 1.1.1419-1.21.1-snapshot

- The API ships in the main jar, in the package `com.minecolonies.api`.
- `IColonyManager.getColonyByPosFromWorld(Level, BlockPos)` reads the owning colony from the chunk data, and it returns
  null outside colonies. It calls `Level.getChunkAt`, which loads the chunk, so a caller asks `isLoaded` first.
- `IColony.getPermissions()` returns `IPermissions`. `getRank(UUID)` returns the player's rank, or the Neutral rank
  (id 3) for a player outside the colony's list. `hasPermission(Rank, Action)` reads the flags of the rank, and a fully
  abandoned colony adds flags of its own.
- `hasPermission(Player, Action)` asks the rank first. Then it gives a player the permissions of the operator rank while
  `hasPermissions(permissionEventMinBypassPermLevel)` is true and the player is in creative mode.
- `ColonyPermissionEventHandler.onPlayerInteract` decides a right-click on a block of its colony, in this order:
  1. A hut block (`AbstractBlockHut`) without `ACCESS_HUTS` cancels.
  2. A free block of the colony, or a block of `ModTags.colonyProtectionException`, passes for a rank that is not hostile.
  3. A door or a fence gate passes with `ACCESS_TOGGLEABLES`.
  4. With `enableColonyProtection` off, the click passes.
  5. Without `RIGHTCLICK_BLOCK`, the click cancels, unless `pvp_mode` is on and the player is a valid attacker.
  6. A block with an item handler (`Capabilities.ItemHandler.BLOCK`, with no side or any side) without `OPEN_CONTAINER`
     cancels.
- A cancellation messages the player, counts the attempt and adds a permission event to the town hall.
- The API has setters for the free blocks and positions of a colony, but no getters.
- The built-in ranks are Owner (0), Officer (1), Friend (2), Neutral (3) and Hostile (4). By the order of the defaults in
  the constructor of `Permissions` (inferred), a Friend may right-click blocks but not open containers, and an Officer
  may do both.
- The code is GPL 3.0: `license="GPL3"` in `neoforge.mods.toml` and GPL 3.0 in the Maven POM.
  `assets/minecolonies/LICENSE` reads "All Rights Reserved".
- MineColonies requires NeoForge `[21.1.0,)`, Minecraft `[1.21,1.22)`, `structurize` `[1.0.837-1.21.1-snapshot,)`,
  `blockui` `[1.0.199-1.21.1-snapshot,)` and `domum_ornamentum` `[1.0.223-snapshot,)`. No mod of the set requires
  `multipiston`, which itself requires `blockui` and `structurize`.
- `IMinecoloniesAPI.getConfig()` returns `com.ldtteam.common.config.Configurations`, a class of BlockUI.

#### The colony settings of COHO 2.4.0 (R68)

| Setting | Value | Effect on the checks |
| --- | --- | --- |
| `enablecolonyprotection` | `true` | Rules 5 and 6 apply. |
| `permissioneventbypassminpermlevel` | `2` | An operator of level 2 or higher in creative mode passes the Quick Work check. The pasture check does not read it. |
| `pvp_mode` | `false` | The exception for attackers never applies. |
| `turnoffexplosionsincolonies` | `"DAMAGE_ENTITIES"` | A right-click does not read it. |
| `maxColonySize` | `20` | A right-click does not read it. |

Each colony keeps the permissions of its ranks in its world data, so no setting of the pack decides who may use a block.

#### Artifacts for Gradle

| Mod | Coordinates | SHA-1 |
| --- | --- | --- |
| MineColonies | `com.ldtteam:minecolonies:1.1.1419-1.21.1-snapshot` | `c93453c21e1fd743f13a1dd9b5d40a15eadcc6f0` |
| Structurize | `com.ldtteam:structurize:1.0.839-1.21.1-snapshot` | `85cecc947288fdca79a1ddbe9a9d0053af850d92` |
| BlockUI | `com.ldtteam:blockui:1.0.212-1.21.1-snapshot` | `985b739fb0e01ad0bc559c5cd41be45de5cfe2bf` |
| Domum Ornamentum | `com.ldtteam:domum-ornamentum:1.0.237-snapshot:main` | `49b92eb1f5b20fb127406fb848a62cb6afdcfea3` |
| Multi-Piston | `com.ldtteam:multipiston:1.2.58-1.21.1` | `2b63be739da24247328e4052ec91c928230d0390` |

The repository of all five is `https://ldtteam.jfrog.io/ldtteam/modding/`, which serves them without a login. Each SHA-1
is the `.sha1` file of the repository, and it equals the SHA-1 of the jar in the COHO cache. The newest MineColonies on
the repository on 2026-11-11 is `1.1.1420-1.21.1-snapshot`. COHO pins 1.1.1419, so the build pins it too.

## Decision

1. **Whose rights.** Every pasture check asks about the placer of the pasture, the `ownerId` of its block entity, which
   each poll reads. The placer is the pasture's owner (Terms), and the placer turns Work Mode on (CD-WMD-03). The check
   takes the UUID and never a player, so an offline placer gets the same answer as an online one (CD-LND-05). A pasture
   with no placer works no block in a land claim.
2. **What the check covers.** It covers each block that a worker of the pasture acts on: a target of a scan pass, the
   bound target and the Telekinesis destination of a binding, and the bed of a Tired worker. It asks about the canonical
   block, as Quick Work does. It does not cover a work spot, where a worker only stands, or a healed ally, which is an
   entity of the same pasture.
3. **When.** The engine asks after each scan pass, before it offers the targets to the workers (CD-LND-01). It asks on
   each poll of a pasture, every 20 ticks, for each claim and binding of the pasture's workers (CD-LND-02, CD-LND-03).
   It also asks in the choice of a bed (CD-LND-04).
4. **What a refusal does.** The scan does not offer the target. A held work claim or bed claim ends, and the worker goes
   back to Idle or rests in place. A binding clears through the leash resets with the reason `land_claim` or
   `land_claim_destination`, which tells the binder, also at the next login (CD-LND-03). An auto-assigned worker's
   owner gets no message.
5. **The checks by UUID.**
   - FTB Chunks: steps 1, 3, 4 and 5 of the research above, through the APIs of FTB Chunks and FTB Teams, with the
     placer's UUID (CD-LND-06). Step 3 asks `noWilderness(null)` and `DimensionFilter.isNoWildernessDimension`. The check
     skips step 2, because the placer is not a fake player. It skips the spectator test of step 4 and the permission of
     FTB Ranks, because a pasture has no player. A pure rule, `FtbInteractRule`, holds the order of the steps, so that a
     unit test reads it.
   - Open Parties and Claims: `hasChunkAccess(placer, dimension, chunkX, chunkZ)` (CD-LND-07).
   - MineColonies: rules 1 to 6 of the research with `getRank(placer)` and `hasPermission(Rank, Action)`, without the
     free blocks of rule 2 and without the exception for attackers of rule 5 (CD-LND-08). A pure rule,
     `ColonyRightClick`, holds the order of the rules.
6. **MineColonies for Quick Work.** The Quick Work check uses the same colony rule with `hasPermission(Player, Action)`
   on the owner's `ServerPlayer`. So the owner gets what the owner's own click would get, the operator bypass included
   (CD-LND-09). CD-QWK-26, CD-QWK-29 and CD-QWK-30 apply to it as to every claim mod.
7. **Silent.** The pasture checks send no message, and they call no check of a claim mod that notifies a player or counts
   an attempt (CD-LND-10). Only the leash resets message a binder.
8. **Fail closed.** A pasture check that throws an `Exception` or a `LinkageError` refuses, with one warning per cause
   (CD-LND-11). The warning names pastures, so it differs from the warning of Quick Work.
9. **No switch.** No config key turns the pasture checks off (CD-LND-15).
10. **One gate, two questions.** `LandClaimCheck` gets a second method, `ownerMayUse(ownerId: UUID?, level, pos)`, beside
    `mayUse(owner: ServerPlayer, level, pos)` of Quick Work. `LandClaims` asks every registered check, in order, for both
    questions. Only `compat/ftbchunks/`, `compat/opac/` and `compat/minecolonies/` name a class of a claim mod, behind
    `ModList.isLoaded` (CD-LND-13).
11. **MineColonies in the build.** The build compiles against MineColonies, Structurize and BlockUI as compile-only
    dependencies from the repository of LDTTeam. `neoforge.mods.toml` declares `minecolonies` as an optional dependency
    with `versionRange="*"` (CD-LND-14). The jar holds no class of `com.minecolonies` or `com.ldtteam`, which `verifyJar`
    checks. The code of MineColonies is GPL 3.0. Dynamics calls its API at run time only, and no jar or artifact of
    Dynamics holds a part of it.
12. **Tests.** The test claim provider of the full test mod tests the engine path with the placer's UUID. Three GameTest
    runs outside CI, `runGameTestServerFtbChunks`, `runGameTestServerOpac` and `runGameTestServerMineColonies`, each load
    the core mod set and one claim mod. In each run, a pasture check must give the answer of the claim mod's own check for
    the same player while online, and the same answer after the player logs out. The test pack starts a dedicated server
    with each claim mod.

## Consequences

- On COHO, the workers of a pasture no longer work the machines in another team's claim, or in a colony where the
  placer's rank may not use them. The pasture of a team member or an ally works in the team's claims, as FTB Chunks lets
  that player click there.
- A pasture keeps working while its placer is offline, and the placer's rights still hold.
- The FTB Chunks check copies the order of the steps of `shouldPreventInteraction`, because FTB Chunks has no check by
  UUID. A later FTB Chunks that changes those steps makes the copy wrong without an error. The oracle tests of the FTB
  Chunks run catch such a change for each tested version, and `docs/modpack-integration.md` names the tested versions.
- The checks of Open Parties and Claims and MineColonies are stricter than a click in a few cases: the blocks of the
  exception lists of Open Parties and Claims, the free blocks of a colony, and the attackers of `pvp_mode`. No role works
  such a block on COHO.
- An operator's pasture gets no operator bypass in a colony, because MineColonies grants the bypass only to a player in
  creative mode at the click. Quick Work keeps the bypass.
- Each poll costs one check for each claim and binding of the pasture, and each scan pass costs one check for each
  target. The perf run measures the engine path with the test claim provider.
- A broken API of a claim mod stops all pasture work on that server, with one warning, until a release of Dynamics
  follows the change.
- CI does not test the real claim mods. A pull request that touches the claims code runs the three claim runs before it
  merges, and each gate runs them.
- The jars of MineColonies are compile-only dependencies and test-run mods, as the jars of FTB are.

## Alternatives considered

- **The worker's owner instead of the placer.** Each tethering of a Cobblemon pasture records its own player, so a
  worker's owner can differ from the placer (inferred). The approved feature (R67) names "the pasture's owner", and the
  placer turns Work Mode on, so the placer decides. A binding also needs its binder's click (CD-SEC-11).
- **The online player while the placer is online.** FTB Chunks would show its text on every scan pass, and the answer
  would change when the placer logs out.
- **A fake player with the placer's profile.** FTB Chunks would apply the team's fake player settings and its
  `fake_players` override, not the placer's rights. MineColonies would look up the real player of the fake one.
- **The Cobblemon as the actor of Open Parties and Claims.** The mod finds the owner of a tamed entity, but then it
  applies its rules for entities, not its rules for players.
- **Keep a refused binding and wait.** The binding would keep reserving the block against the workers of the claim's own
  players.
- **A cache of answers for each chunk.** The maps of the claim mods are cheap to read, and a cache would answer late
  after a change of rights.
- **A mixin into a claim mod.** It breaks when the claim mod changes its internals.
- **The newest MineColonies, 1.1.1420.** COHO pins 1.1.1419, and the tests must run the pack's jars. The entry
  `versionRange="*"` lets a pack load any version, and a check that fails refuses. Since 2026-11-12, COHO 2.5.0 ships
  1.1.1420, whose change since 1.1.1419 is blueprints only, so the build and the test pack pin it (R74 of the spec
  sheet).
- **A config switch.** The "must never" of R67 rules it out (CD-LND-15).
