# Integration points: research snapshot (2026-11-09)

This note records what five research passes found in the source of the mods that Cobblemon Dynamics
integrates with. The fifth pass covered the v2 roles and the v1 gaps: the windmill, the encased fan,
item handlers, the enchanting table, cauldrons and the remaining move effects. It is a snapshot at the versions below. Class names, line numbers and ids can change
in later releases, so check a reference again before you build on it. Items marked "(inferred)" come
from reasoning, not from reading the code.

[ARCHITECTURE.md](../../ARCHITECTURE.md) explains how the mod uses these points. The ADRs in
[docs/adr/](../adr/) record the decisions.

## Versions and sources

| Project | Version | Source ref | License |
|---|---|---|---|
| Cobblemon | 1.8.4 (2026-10-27) | gitlab.com/cable-mc/cobblemon, tag `1.8.4`, commit `b79dbcca802e10fb7eda89c568a874f7891a2d28` | Code MPL-2.0. 530 model folders carry a Creative Commons NonCommercial license. |
| Create | 6.0.10 (published on Modrinth 2026-04-21, tag commit dated 2026-04-20) | github.com/Creators-of-Create/Create, tag `mc1.21.1-6.0.10`, commit `ac0c444d9828…` | Code MIT. Assets All Rights Reserved. |
| Applied Energistics 2 | 19.2.19 (2026-11-09) | github.com/AppliedEnergistics/Applied-Energistics-2, tag `neoforge/v19.2.19`, commit `da9bd7f` | Code LGPLv3. API MIT. Textures and models CC BY-NC-SA 3.0. |
| NeoForge | 21.1.265 | sources jar `neoforge-21.1.265-sources` | LGPL-2.1 |
| Minecraft | 1.21.1, Mojang mappings | `javap` of the NeoForge-patched jar | — |
| Cobbleworkers | 2.0.7+1.8.3 | released jar, decompiled with Vineflower 1.11.1. GitHub `Accieo/cobbleworkers` commit `2986bde` is frozen at 1.8.0. | Jar metadata says MPL-2.0. The jar ships no license file. |
| Cobblebase | 2.0.1+1.8.0 | github.com/notlown/cobblebase, commit `00a05e2` | MPL-2.0 |

## Cobblemon 1.8.4

Path shorthand: `K/` is `common/src/main/kotlin/com/cobblemon/mod/common/` and `R/` is
`common/src/main/resources/`.

### Source and build

- The canonical repository is gitlab.com/cable-mc/cobblemon (`neoforge/src/main/resources/META-INF/neoforge.mods.toml:4`).
- `gradle.properties` sets `mod_version=1.8.4` and `mc_version=1.21.1`.
- Maven repositories: `https://artefacts.cobblemon.com/releases` and
  `https://maven.impactdev.net/repository/development/`
  (`gradle/build-logic/src/main/kotlin/cobblemon.publish-conventions.gradle.kts:18-41`).
- Coordinates: `com.cobblemon:neoforge:1.8.4+1.21.1` (`:51`, `utilities/Versioning.kt:13-18`). A sources jar
  exists.
- The POM lists no dependencies. Declare `thedarkcolour:kotlinforforge-neoforge:5.10.0` yourself, from
  `https://thedarkcolour.github.io/KotlinForForge/`.
- Build versions: Kotlin 2.2.20, KotlinForForge 5.10.0, NeoForge 21.1.195 (`gradle/libs.versions.toml:3,38-39`).
- Runtime: `modLoader="kotlinforforge"`, `loaderVersion="[5.10,)"`, Java `[21,21.999999)` (`neoforge.mods.toml:1-2,41-42`).
- The MoLang library is a local jar bundled into Cobblemon's jar (`neoforge/build.gradle.kts:63-69`).
  Addons compile against it through that jar (inferred).

### Pasture block

| Item | Reference |
|---|---|
| Block, two blocks tall. Only the bottom half has a block entity. | `K/block/PastureBlock.kt:131` |
| Block entity, type `cobblemon:pasture` | `K/block/entity/PokemonPastureBlockEntity.kt` |
| `open class Tethering`: roam bounds, `playerId`, `tetheringId`, `pokemonId`, `pcId`, `pasturePos`, `box`, `canRoamTo()` | `PokemonPastureBlockEntity.kt:59-97` |
| Helper API | `K/api/pasture/` |
| `Tethering.getPokemon()` returns `Cobblemon.storage.getPC(pcId)[pokemonId]`. The Pokémon never leaves the PC. | `PokemonPastureBlockEntity.kt:70-74` |
| PCs load from disk on demand, even for offline owners. A PC's UUID is the player's UUID. | `K/api/storage/factory/FileBackedPokemonStoreFactory.kt:39,65-93` |
| `Pokemon.tetheringId` marks a pastured Pokémon. Moving it out of the PC clears the id, and the entity then removes itself. | `K/pokemon/Pokemon.kt:526,776`, `K/entity/pokemon/PokemonServerDelegate.kt:306-309` |
| `tether()` creates the entity and adds it to the world. It sets `pokemon.tetheringId` and `entity.tethering` after that. | `PokemonPastureBlockEntity.kt:160-212`, `:185-199` |
| Roam box = block position ±`pastureMaxWanderDistance`. Wandering skips targets outside it. A Pokémon outside the box is teleported back, or recalled if that fails. | `PokemonPastureBlockEntity.kt:129-133`, `K/api/ai/config/task/WanderTaskConfig.kt:187-192`, `K/entity/pokemon/PokemonEntity.kt:740-770` |
| Block entity NBT holds the tetherings, the roam box, `ownerId` and `ownerName`. Entity NBT holds only the link. Tethered entities always save. | `PokemonPastureBlockEntity.kt:351-398`, `PokemonEntity.kt:907-921,1360-1367` |
| Chunk unload keeps the link. `remove()` clears `tetheringId` only when the entity is really destroyed. On load the entity re-links if the ids match, otherwise it dies at 0 health. | `PokemonEntity.kt:1792-1806,960-992` |
| No respawn. Every 40 ticks the block entity prunes links that no longer match. | `PokemonPastureBlockEntity.kt:291-304` |
| Pastured Pokémon never despawn and survive the owner logging out. | `PokemonEntity.kt:1369`, `PokemonServerDelegate.kt:346-348` |
| Every 20 ticks with the owner online, the entity swaps to the PC's Pokémon instance. | `PokemonEntity.kt:719-731` |
| Anyone can open a pasture. The placer is recorded but never enforced. | `PastureBlock.kt:257-303,238-255` |
| Default permissions: canPasture, canUnpastureOthers, maxPokemon 16. A `PasturePermissionController` can override them. | `PasturePermissionControllers.kt:29-38` |
| Adding needs: fewer than `maxPokemon` for that player, fewer than 16 total, not fainted, and the density check. | `K/net/serverhandling/pasture/PasturePokemonHandler.kt:23-43,137-157` |
| Removing works on the server only for your own Pokémon. `canUnpastureOthers` is used only by the client UI. | `UnpasturePokemonHandler.kt:26-32` |
| Breaking the block releases all Pokémon. The network packet caps the count at 255. | `PokemonPastureBlockEntity.kt:306-311` |
| Entity to pasture: `PokemonEntity.tethering?.pasturePos`, or MoLang `q.entity.is_pastured`. | `PokemonEntity.kt:306`, `K/api/molang/function/PokemonEntityMoLangFunctions.kt:123` |

Config (`config/cobblemon/main.json`, `K/config/CobblemonConfig.kt:285-295`):

| Key | Default |
|---|---|
| `defaultPasturedPokemonLimit` | 16 |
| `pastureBlockUpdateTicks` | 40 |
| `pastureMaxWanderDistance` | 32 |
| `pastureMaxPerChunk` | 4.0 |

### Pasture UI

- The server sends `OpenPasturePacket` (`cobblemon:open_pasture`) with the pcId, a random link id
  `pastureId`, the limit, the Pokémon list and the permissions. `OpenPastureHandler` opens `PCGUI` with a
  `PasturePCGUIConfiguration` (`K/client/net/pasture/OpenPastureHandler.kt:20-29`).
- `StorageWidget` adds the `PastureWidget` (`K/client/gui/pc/StorageWidget.kt:146-147`). The widget holds
  the scroll list and the `RecallButton` (`K/client/gui/pasture/PastureWidget.kt:37-79`).
- Other packets, server to client: `close_pasture`, `pasture_pokemon_added`, `pasture_pokemon_removed`,
  `update_pasture_conflict_flag`. Client to server: `pasture_pokemon`, `unpasture_pokemon`,
  `unpasture_all_pokemon`, `set_pasture_conflict`.
- A toggle needs no mixin. Use NeoForge `ScreenEvent.Init.Post` and check
  `screen is PCGUI && screen.configuration is PasturePCGUIConfiguration`. `configuration` is public
  (`K/client/gui/pc/PCGUI.kt:57`).
- Panel geometry: top-left corner ((w − 349) / 2 + 267, (h − 205) / 2 + 8), size 82 × 169
  (`PCGUI.kt:64-69,137-138,181-183`). The Recall button sits at panel offset (6, 153).
- Mixin targets for drawing inside the panel: `PastureWidget.renderWidget` and `mouseClicked`, or the end of
  `PCGUI.init()`. `storageWidget` is private (`PCGUI.kt:109`) and needs an accessor.
- The client never learns the pasture's position. On the server, use
  `PastureLinkManager.getLinkByPlayer(player)`, which is valid within 10 blocks in the same dimension
  (`:34-44`). Then check `ownerId`.
- The pasture has no per-pasture settings. The only toggle is the per-Pokémon "Defend" flag
  `PokemonBehaviourFlag.PASTURE_CONFLICT` (`K/entity/pokemon/PokemonBehaviourFlag.kt:21-25`). Its handler
  checks no ownership and calls `remakeBrain()` (`SetPastureConflictHandler.kt:20-31`). The flag set is a
  byte limited to 7 entries and is sent as enum positions, so an addon cannot add flags.
- The pasture block entity sends no update data to the client.

### Brain AI

- Pokémon AI is Brain-based. The goal code is commented out (`PokemonEntity.kt:1135-1140`), and the Brain
  ticks in `customServerAiStep` (`:772`).
- Behaviours come from `form.baseAI` if set, otherwise from all auto behaviours plus `form.ai`
  (`K/pokemon/ai/PokemonBrain.kt:96-104`). No species sets `baseAI` in 1.8.4.
- Any namespace's `behaviours/pokemon/auto/*.json` applies automatically. The code comments call it
  "addon-friendly" (`K/CobblemonBehaviours.kt:72-103`). File ids drop the folder path (`:97`).
- In-source docs: `K/api/ai/config/README.md` and `K/api/ai/config/task/README.md`.
- Relevant JSON: `pokemon_core.json` has wander control at idle priority 0. `core.json` has
  `follow_walk_target` in core and `look_at_target` in idle only. `wanders.json` runs at idle priority 7.
  `pokemon_owned.json:18-24` attacks hostile mobs when a pastured Pokémon has Defend on.
- MoLang conditions are evaluated once, when the Brain is built.
- Registries (public mutable maps): `TaskConfig.types` (`K/api/ai/config/task/TaskConfig.kt:44-127`) and
  `BehaviourConfig.types` (`K/api/ai/config/BehaviourConfig.kt:38-47`). Activities come from vanilla
  `BuiltInRegistries.ACTIVITY` (`K/util/adapters/ActivityAdapter.kt:22`). Cobblemon's own activities are in
  `K/CobblemonActivities.kt:17-31`.
- Pattern for switching back: `SwitchFromActionEffectTaskConfig.kt:20-34`.
- Each custom task must declare its memories with `addMemories(...)`, or `setMemory` does nothing
  (inferred from vanilla Brain rules).
- The Brain is built in the entity constructor (`PokemonEntity.kt:430-434`), before `tethering` is set.
  So `is_pastured` is false for a new pastured Pokémon until `remakeBrain()` runs.
- Replacing `entity.pokemon` rebuilds the Brain (`PokemonEntity.kt:255-265`). The 20-tick PC sync does this.
- Wandering needs both `WALK_TARGET` and `PATH_COOLDOWN` to be absent (`WanderTaskConfig.kt:128-133`).

### Animations and particles

- `PlayPosableAnimationPacket(entityId, names, expressions)` plays the first name that the model has
  (`K/net/messages/client/animation/PlayPosableAnimationPacket.kt:25-45`). `PokemonEntity.cry()`
  (`:1821`) shows how to send it. It plays once, so resend it to loop (inferred).
- Pose coverage is thin. Of 759 JSON pose files, 105 define `special`, 116 `physical`, 616 `cry` and 406
  `sleep`. Of 308 code-defined models, 4 mention `special` or `physical`.
- `RunPosableMoLangPacket` runs client-side `q.play_animation`, `q.particle(id, locator)` and `q.sound`
  on an entity (`K/client/render/models/blockbench/PosableState.kt:278,320,336`).
- `SpawnSnowstormParticlePacket(id, Vec3)` places an effect at a position.
  `SpawnSnowstormEntityParticlePacket(id, source, sourceLocators, targetedEntityId?, targetLocators?)`
  attaches one to an entity. The client aims at the target's locator, or at the target entity's position
  when it has no model (`K/client/net/effect/SpawnSnowstormEntityParticleHandler.kt:28-52`).
- Action-effect timelines can run outside battle. The built-in `q.entity.run_action_effect`
  (`PokemonEntityMoLangFunctions.kt:129-158`) passes the Pokémon only as the target, and the animation
  and particle steps need `is_user` by default (`EntityConditionalActionEffectKeyframe.kt:25-39`). A
  custom context must copy battles (`K/battles/interpreter/instructions/MoveInstruction.kt:106-136`) and
  define the battle-only queries `missed`, `target_fainted`, `hurt`, `failed` and `move` (`:144-185`).
- Targets must be entities. Vanilla Marker entities are never sent to clients (inferred). A client
  packet could build `ParticleStorm(..., targetPos = { blockCenter })` instead
  (`K/client/particle/ParticleStorm.kt:47-59`), but that class is `internal`.
- Counts: 138 move timelines (156 timelines in total) and 128 move particle folders. Timelines live in
  `R/data/cobblemon/action_effects/moves/`. Particles live in
  `R/assets/cobblemon/bedrock/particles/moves/<move>/`.

Each timeline file lists its animation names at line 9. The client plays the first name that the model
has (`PosableState.kt:428-430`). The client skips an unknown particle id with no error
(`SpawnSnowstormEntityParticleHandler.kt:26`). All particle ids below use the `cobblemon:` namespace. Ids
in brackets are sub-events of the timeline.

Effect ids for the v1 roles:

| Move | Timeline id | Animation names tried | Particle ids |
|---|---|---|---|
| Water Gun | `cobblemon:watergun` | watergun, spray, beam, special | `watergun_spray`, `watergun_actor`, `watergun_target`, `watergun_targetfoam` |
| Thunder Shock | `cobblemon:thundershock` | thundershock, electric, special | `thundershock_actor`, `_actorbolts`, `_actorsparks`, `_target`, `_targetbolts`, `_targetimpact` |
| Flamethrower | `cobblemon:flamethrower` | flamethrower, fire, spray, special | `flamethrower_actor`, `_target`, `_targetburst`. The files `_target_linger` and `_targetcinder` exist, but the timeline does not use them. |
| Ember | `cobblemon:ember` | ember, fire, spray, special | `ember_actor`, `ember_target` [`ember_fire`, `ember_firesparks`, `ember_targetlinger`] |
| Dragon Breath | `cobblemon:dragonbreath` | dragonbreath, spray, special | `dragonbreath_actor`, `dragonbreath_target` |
| Pound | `cobblemon:pound` | pound, punch, tail, physical | `pound_target`, `pound_targetsparks` |
| Rock Throw | `cobblemon:rockthrow` | rockthrow, rock, physical | `rockthrow_actor` [`rockthrow_actorsend`, `rockthrow_target`, `rockthrow_targetstars`] |
| Poison Gas | `cobblemon:poisongas` | poisongas, spray, special. The target plays `recoil`. | `poisongas_actor`, `poisongas_target` [`poisongas_actorbubbles`, `poisongas_actorspray`] |
| Fairy Wind | `cobblemon:fairywind` | fairywind, special | `fairywind_actor`, `fairywind_target`, `fairywind_targethit` [`fairywind_actorspark`] |

Effect ids for the v2 roles:

| Move | Timeline id | Animation names tried | Particle ids |
|---|---|---|---|
| Kinesis (Telekinesis) | `cobblemon:kinesis` | kinesis, status | `kinesis_actor`, `kinesis_actorspiral` |
| Gust (Tailwind) | `cobblemon:gust` | gust, flying, special | `gust_actor`, `gust_target`, `gust_targethit` |
| Fire Spin (Fire Fan Catalyst) | `cobblemon:firespin` | firespin, special | `firespin_actorburst`, `firespin_actor`, `firespin_targetburst`, `firespin_target` [`firespin_targetcinder`] |
| Bubble (Water Fan Catalyst) | `cobblemon:bubble` | bubble, water, spray, special | `bubble_actor`, `bubble_target` [`bubble_actorspray`, `bubble_actorbigspray`, `bubble_actorpop`, `bubble_targetpop`]. `bubble_target` has no particle file (`bubble.json:42`), so that step shows nothing. |
| Will-O-Wisp (Ghost Fan Catalyst) | `cobblemon:willowisp` | willowisp, fire, special. The target plays `recoil`. | `willowisp_actor`, `willowisp_target` [`willowisp_actorappear`, `_actorburst`, `_actorcinder`, `_actorproj`, `_actorwisp`] |

Other moves that the research read, for later use:

- Confusion: animations confusion, psychic, special. Particles `confusion_actorglint`, `confusion_actor`,
  `confusion_target`, `confusion_impact`, `confusion_impactdots`.
- Psychic: animations psychic, special. The target also plays `q.bedrock_stateful('psychic','target')`.
  Particles `psychic_actor`, `psychic_actordots`, `psychic_target`, `psychic_impact`, `psychic_impactdots`.
- Bullet Punch: animations bulletpunch, punch, physical. Particles `bulletpunch_target`
  [`bulletpunch_targetsparks`]. It is the only Steel move with a timeline (inferred, because the repo
  holds no move-type data).
- No tailwind, magnet rise, magnet bomb or magnetic flux timeline exists. The closest wind particles are
  `gust_*`, `fairywind_*`, `icywind_*` and `leafstorm_actorwind`. Spiral candidates are
  `kinesis_actorspiral`, `blizzard_targetswirl` and `flamecharge_actorspiral` (fit inferred).
- Two Steel-looking particles exist outside the move timelines. `generic/impact_steel` has no
  references. `metal_sparks` is used only in the Magneton and Magnezone faint animations. The Magnet
  role uses `metal_sparks`.

Other effect assets:

- Sleep: `cobblemon:sleep_actor` and `cobblemon:sleep_actorresidual`
  (`R/assets/cobblemon/bedrock/particles/generic/ailments/`).
- Heal: no heal move has a timeline. None of these do: Floral Healing, Heal Pulse, Life Dew, Pollen Puff,
  Wish, Moonlight, Morning Sun, Synthesis, Recover, Rest, Draining Kiss, Lunar Blessing and Jungle Healing.
  Only Synthesis has particles: `cobblemon:synthesis_accretion`, `cobblemon:synthesis_sun` and
  `cobblemon:synthesis_sunlight`.
- Reusable heal visuals: `cobblemon:heal_circles` and `cobblemon:heal_sparkles` (generic, unused by
  Cobblemon). Drain timelines with heal particles: `cobblemon:absorb` (`absorb_actorheal`),
  `cobblemon:megadrain`, `cobblemon:gigadrain` (`gigadrain_actorheal`, `gigadrain_actorhealbig`),
  `cobblemon:leechlife` and `cobblemon:drainpunch`. Fairy visuals: `cobblemon:fairywind` and
  `cobblemon:impact_fairy`.

### Sleep

- `PoseType.SLEEP` is chosen when the `POKEMON_SLEEPING` memory is set, or the battle sleep status is set,
  and the species has `behaviour.resting.canSleep` (`PokemonServerDelegate.kt:376-399`).
- `pokemon_sleeps.json` makes wild and pastured Pokémon find a resting place at night. In the sleeping
  activity, `WakeUpTask` ends sleep when the drowsy memory clears.
- `pokemon_sleeps_on_trainer_bed.json` makes party Pokémon sleep on the owner's vanilla bed while the
  owner sleeps.
- Species flags (`K/pokemon/ai/RestBehaviour.kt:26-39`): `canSleep` defaults to false, and 484 species set it
  true. `willSleepOnBed` is true for 334 species. Further rules cover light, blocks, biomes and times.
- To make a pastured Pokémon nap on a block:
  1. Walk it to the block with a custom activity.
  2. Stop its navigation and snap it to the block center.
  3. Set `enablePoseTypeRecalculation=false` and `POSE_TYPE=SLEEP` (`PokemonEntity.kt:205,311`). Keep it
     out of Cobblemon's sleeping activity, so the wake-up task cannot end it.
  4. Restore the recalculation flag afterwards. Cobblemon saves it to NBT (`:943`).
  5. Do not set the SLEEP status (`GoToSleepTask.kt:38`). It is the battle status, and it persists.

### Pokémon data

| Data | Reference (`K/pokemon/Pokemon.kt` unless noted) |
|---|---|
| `level` | `:320` |
| `primaryType`, `secondaryType`, `types`, compared with `ElementalTypes.WATER` and the other constants | `:447-454` |
| `isLegendary()`, `isMythical()`, `isUltraBeast()`, `hasLabels(...)` | `:1219-1244` |
| Label constants (paradox at `:146`) | `K/api/pokemon/labels/CobblemonPokemonLabels.kt` |
| A form's own labels replace the species labels. Galarian Articuno re-declares legendary. | `K/pokemon/FormData.kt:202-203` |
| Species files per label: legendary 71, mythical 23, ultra_beast 11, paradox 20. Alpha Pokémon (`isAlpha`) exist in 1.8. | — |
| `shiny` | `:481` |
| `ivs[Stats.X]`, `evs[...]` | `:227,241` |
| `nature`, `mintedNature`, `effectiveNature` | `:494-504` |
| `friendship` | `:394` |
| `moveSet.getMoves()` | `:507` |
| `currentHealth`, `maxHealth`, `isFainted()` | `:336,585,1028` |
| `getOwnerUUID()` returns the player UUID for PC Pokémon. `entity.ownerUUID` comes from the tether. | `:1562-1573` |
| `isWild()` means no storage at all. `isPlayerOwned()` sits next to it. Battle clones must be excluded too. | `:1577` |
| `persistentData`: a mutable tag that cannot be reassigned. Saved in PC and party under `PersistentData`, and sent to clients. Call `onChange()` after an edit, or the change may revert. | `:748,2283`, `K/util/codec/internal/PokemonP2.kt:108` |
| The UUID is stable and saved. Only `clone(newUUID=true)` changes it. Evolution changes the same object in place. | `:1451-1459`, `K/api/pokemon/evolution/Evolution.kt:196-203` |

Name clash: "stamina" already means ride stamina. `Pokemon.rideStamina` (`:425`) is saved as `RideStamina`
(`K/util/DataKeys.kt:64`) and synced through the entity's `RIDE_STAMINA` data (`:217`). Riding also has
`RidingStat.STAMINA`, the config key `infiniteRideStamina` and the event `RIDE_EVENT_APPLY_STAMINA`.
Fullness, which the pasture ticks, is the other fatigue-like stat.

Size: hitbox = form `hitbox` × `baseScale` × `effectiveScale` × entity scale (`PokemonEntity.kt:1331-1337`).
Pikachu is 0.35 blocks wide. Wailord is 8 wide and 5.25 tall. Cobblemon's own sleep-spot check uses the
rounded-up hitbox (`:1154`).

### Events

All events are `@JvmField` fields in `K/api/events/CobblemonEvents.kt`.

| Event | Line | Payload |
|---|---|---|
| `POKEMON_SENT_PRE` / `POST` | 160 / 163 | pokemon, level, position (plus entity on POST) |
| `POKEMON_RECALL_PRE` / `POST` | 166 / 169 | pokemon, oldEntity |
| `POKEMON_RELEASED_EVENT_PRE` / `POST` | 246 / 249 | player, pokemon, storage |
| `POKEMON_FAINTED` | 93 | pokemon, faintedTimer |
| `LEVEL_UP_EVENT` | 187 | pokemon, oldLevel, newLevel (changeable) |
| `EVOLUTION_ACCEPTED` (cancelable) | 96 | — |
| `EVOLUTION_COMPLETE` | 105 | pokemon, sourcePokemon, evolution |
| `POKEMON_GAINED` | 313 | playerId, pokemon |
| `POKEMON_ENTITY_SAVE` / `LOAD` | 197 / 200 | entity, nbt. Cancelling LOAD discards the entity. |
| `POKEMON_HEALED` | 190 | — |
| `FRIENDSHIP_UPDATED` | 87 | — |
| `HELD_ITEM_PRE` / `POST` | 301 / 304 | — |
| `POKEMON_INTERACTION_GUI_CREATION` (client) | 194 | can add interaction wheel options |

- Cobblemon has no events for pasture, unpasture or storage moves. The options are the
  `Pokemon.storeCoordinates` observable (`Pokemon.kt:724`), a mixin on `tether`, `releasePokemon` and
  `releaseAllPokemon`, or polling. NeoForge's join-level event sees `tethering == null` for a Pokémon that
  was just pastured.
- Kotlin subscription: `CobblemonEvents.LEVEL_UP_EVENT.subscribe(Priority.NORMAL) { e -> ... }`.
- Java subscription: `CobblemonEvents.POKEMON_FAINTED.subscribe(Priority.HIGH, e -> { ... });`.
- Priorities run from HIGHEST to LOWEST (`K/api/Priority.kt`). Cancel a cancelable event with `e.cancel()`.

### Interaction

- NeoForge `PlayerInteractEvent.EntityInteract` fires first, on both sides. Cancelling it skips both the
  entity's own handler and the item's `interactLivingEntity`.
- Cobblemon's NeoForge listener (`neoforge/src/main/kotlin/com/cobblemon/mod/neoforge/event/NeoForgePlatformEventHandler.kt:120-131`)
  only blocks name tags on certain entities (`K/Cobblemon.kt:271-275`). Subscribe at HIGH priority.
- `PokemonEntity.mobInteract` (`PokemonEntity.kt:1179-1293`) handles, for the owner or a wild Pokémon:
  shears, bowl, flowers, stash items, dye, water bucket and the NPC editor. A sneaking main-hand click
  opens the interaction wheel (`:1283`), including for pastured Pokémon while the owner is online.
  Otherwise, for the owner, it tries item-gated `pokemon_interactions` data, then evolution items, then
  items that implement `PokemonEntityInteraction` (`:1448-1531`). Only after all of that does the item's
  `interactLivingEntity` run.
- Implementing `PokemonEntityInteraction` is simple but misses sneak-clicks. Cancelling `EntityInteract`
  after the check `target.tethering?.playerId == player.uuid` is more robust.

### Rare items

Comet Shard, Star Piece, Big Nugget, Pearl and Bottle Cap do not exist in 1.8.4.

| Item id | How a player gets it |
|---|---|
| `cobblemon:master_ball` | Crafted: Nether Star, 2 netherite ingots and 2 shulker shells |
| `cobblemon:ability_patch` | No recipe or loot. Creative tab only. |
| `cobblemon:ability_capsule` | Brewing, and ruins loot |
| `cobblemon:rare_candy` | Defeating tier-4 Alpha Pokémon, and gilded-chest loot |
| `cobblemon:exp_candy_xl` | Defeating tier-4 Alpha Pokémon |
| `cobblemon:masterpiece_teacup` | Ruins archaeology. A recipe duplicates it. |
| `cobblemon:relic_coin` and `cobblemon:relic_coin_sack` | Ruins archaeology. The sack is crafted. |
| 17 fossils, for example `cobblemon:old_amber_fossil` | Rare archaeology |
| `cobblemon:pp_max` | Brewing |

### Top integration risks found

1. Pasture internals have no API and no events. `Tethering`, `tetheredPokemon`, the step order in
   `tether()`, `PastureLinkManager`, and the hard-coded `PCGUI` and `PastureWidget` layout and private
   fields can all change without notice.
2. The behaviour system still moves: `TaskConfig.types`, the Brain-build context, wander control (added in
   2025), the auto folder rules and Brain rebuild timing. A future species `baseAI` would skip an auto
   behaviour.
3. Effect timelines drift. They depend on battle-only queries (1.8.4 just added `q.target_fainted`) and
   on `is_user` rules. Particle ids get renamed or retimed. Attack pose coverage is thin.
4. The tether box teleports or recalls a Pokémon outside 32 blocks. The 20-tick PC sync rebuilds the Brain.
   `persistentData` needs `onChange()`.
5. Fixed formats couple tightly: the 7-flag behaviour byte, the `OpenPasturePacket` format, KotlinForForge
   5.10 or later with Kotlin 2.2.20, and the split between MPL code and NonCommercial assets.

## Create 6.0.10

Java paths are relative to `src/main/java/com/simibubi/create/`.

### Build and dependencies

- Versions (`gradle.properties:7-8,18,29,33,37-38`): Create 6.0.10, Minecraft 1.21.1, NeoForge 21.1.219,
  Registrate MC1.21-1.3.0+67, Ponder 1.0.82, Flywheel 1.0.6 (range `[1.0.0,2.0)`). 21.1.219 is also the
  minimum in `src/main/templates/META-INF/neoforge.mods.toml:21-23`.
- The `mc1.21.1/dev` branch was 41 commits ahead of the tag.
- Maven, from the wiki page `depend-on-create/neoforge-1.21.1` and checked on the maven:

```groovy
repositories {
  maven { url = "https://maven.createmod.net" }          // Create, Ponder, Flywheel
  maven { url = "https://maven.ithundxr.dev/snapshots" } // Registrate
}
implementation("com.simibubi.create:create-1.21.1:6.0.10-280:slim") { transitive = false }
implementation("net.createmod.ponder:ponder-neoforge:1.0.82+mc1.21.1")
compileOnly("dev.engine-room.flywheel:flywheel-neoforge-api-1.21.1:1.0.6")
runtimeOnly("dev.engine-room.flywheel:flywheel-neoforge-1.21.1:1.0.6")
implementation("com.tterrag.registrate:Registrate:MC1.21-1.3.0+67")
```

- There is no plain `6.0.10` artifact, only builds 6.0.10-275 to -281. The `Git-Hash` in the `-280` slim
  jar's manifest is exactly the tag commit. Build -279 is from before the tag, and -281 from after. Pin -280.
- The slim jar has no bundled jars (`build.gradle:215-222`). The full jar bundles Registrate, Flywheel and
  Ponder (`:124-141`).
- The Ponder coordinates changed after 6.0.8. Catnip (`net.createmod.catnip`, with `VecHelper`) is inside
  the Ponder jar.
- Create's own build also adds Fuzss's maven, commented "needed by ponder" (`build.gradle:112-113`).

### Public API

- `api/boiler/BoilerHeater`: boiler heat.
- `api/stress/BlockStressValues`: stress capacity and impact registries.
- `api/data/datamaps/BlazeBurnerFuel` and `api/registry/CreateDataMaps.java:27-41`: item fuel data maps only.
- `api/registry/CreateBuiltInRegistries.java:35`: the fan processing type registry. The
  `FanProcessingType` interface itself sits in `content/`.
- `api/event/BlockEntityBehaviourEvent.java:36-60`: attaches your own behaviour (saved to NBT, synced to
  clients, ticked) to any Create machine block entity. Create posts it at
  `foundation/blockEntity/SmartBlockEntity.java:67,116`.
- No API covers water-wheel flow, crank input or virtual burner fuel.

### Water wheel and large water wheel

- Classes: `content/kinetics/waterwheel/WaterWheelBlockEntity` extends `GeneratingKineticBlockEntity`
  (line 41). `LargeWaterWheelBlockEntity` only overrides `getSize()` to return 2 (lines 13-16). Block entity
  types: `AllBlockEntityTypes.java:708,715`.
- `public int flowScore` (line 72) is saved as `FlowScore` (lines 191, 214).
- `public void determineAndApplyFlowScore()` (lines 122-152) checks the positions from `getOffsetsToCheck()`
  (85-87). A small wheel checks the 4 neighbours perpendicular to its axis (47-52). A large wheel checks 12
  positions just outside its 3 × 3 ring (54-68).
- For each position it takes `getFlowVectorAtPosition(BlockPos)` (154-161), projects it onto the wheel plane
  and normalizes it. It computes `tangent = VecHelper.rotate(normalize(offset), 90, axis)` and adds
  `signum(dot)` to the score when `|dot| > 0.5` (139-145).
- It finishes with `setFlowScoreAndUpdate(int)` (163-169). That returns early when the score is unchanged,
  and otherwise calls `updateGeneratedRotation()`.
- `getGeneratedSpeed()` (223-226) is `Mth.clamp(flowScore, -1, 1) * 8 / getSize()`: ±8 RPM for the small
  wheel and ±4 RPM for the large one. The direction is the sign of `flowScore`.
- Capacity comes from `calculateAddedStressCapacity()` (`content/kinetics/base/KineticBlockEntity.java:185-189`),
  which reads `BlockStressValues`, which reads the server config `kinetics.stressValues.v2.capacity`
  (`infrastructure/config/CStress.java:25,36-46`). Defaults: water wheel 32 SU/RPM, so 256 SU
  (`AllBlocks.java:580`). Large wheel 128 SU/RPM, so 512 SU (`AllBlocks.java:595`).
- A generator that a network turns the other way at an equal or higher speed is destroyed
  (`GeneratingKineticBlockEntity.java:145-148`).
- Recomputation triggers: `lazyTick()` every 60 ticks (lines 78, 114-120). The first tick after load, through
  `SmartBlockEntity.initialize()` (64-72). There is no `onLoad` hook. For a small wheel, `updateShape` and
  `onPlace` schedule a 1-tick block tick that recomputes (`WaterWheelBlock.java:57-83`). For a large wheel,
  the structural blocks' `updateShape` schedules a tick on the controller (`WaterWheelStructuralBlock.java:119-135`),
  and `LargeWaterWheelBlock.tick` (108-130) re-places the ring and recomputes.
- The ticker runs on both client and server (`foundation/block/IBE.java:67-73`), so the client also
  recomputes `flowScore` itself.
- Multiblock: the center `LargeWaterWheelBlock` holds the block entity. Eight invisible
  `WaterWheelStructuralBlock`s form the ring, each with `FACING` toward the center. Find the controller with
  `WaterWheelStructuralBlock.getMaster(BlockGetter level, BlockPos pos, BlockState state)` (137-144), after
  `stillValid(level, pos, state, false)` (146-158).

Recommended mixin. The large wheel inherits the method:

```java
@Mixin(WaterWheelBlockEntity.class)
abstract class WaterWheelBlockEntityMixin {
  @ModifyArg(method = "determineAndApplyFlowScore()V", at = @At(value = "INVOKE",
    target = "Lcom/simibubi/create/content/kinetics/waterwheel/WaterWheelBlockEntity;setFlowScoreAndUpdate(I)V"))
  private int cobblemon_dynamics$virtualFlow(int realScore) {
    int v = VirtualFlow.sign((WaterWheelBlockEntity) (Object) this); // -1, 0, +1
    return realScore != 0 ? realScore : v;
  }
}
```

- Alternative, to let Create's geometry decide:
  `@Inject(method = "getFlowVectorAtPosition(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"), cancellable = true)`.
- To start or stop: call the public `determineAndApplyFlowScore()` on the server. `updateGeneratedRotation()`
  alone does nothing useful, because it reads the unchanged `flowScore`. Scheduling a block tick on the
  controller also works.
- Do not set `flowScore` from outside. The next lazy tick or neighbour update resets it to 0, which detaches
  and reattaches the network (inferred).
- Keep the worker state in a behaviour attached through `BlockEntityBehaviourEvent`. Behaviour data goes into
  client packets (`SmartBlockEntity.java:93-95,110-119`), so the client's own recompute stays consistent.
  Without it, the goggle tooltip shows 0 SU (`GeneratingKineticBlockEntity.java:72-76`) (inferred).

### Hand crank and valve handle

- `content/kinetics/crank/HandCrankBlockEntity`: `public int inUse` (27), `public boolean backwards` (28).
- `public void turn(boolean back)` (39-49) sets `inUse = 10`. It calls `updateGeneratedRotation()` only on a
  start or a direction change. `tick()` (85-101) counts `inUse` down and stops the crank at 0.
- Speed: ±32 RPM (`HandCrankBlock.getRotationSpeed()`, 54-56), with the sign applied in `getGeneratedSpeed()`
  (58-65). Capacity: 8 SU/RPM, so 256 SU (`AllBlocks.java:649`).
- A player's right-click (`useItemOn`, 63-77) is `turn(shift)` plus hunger.
- Continuous cranking: call `turn(back)` on the server at least every 9 ticks. Every 5 ticks is safe. No
  mixin is needed.
- The crank sound depends on the client's `inUse` (`tickAudio`, 122-131). Call `turn` on the client too, or
  play the sound yourself (inferred).
- `ValveHandleBlockEntity.activate(boolean sneak)` (113-139) turns a set angle, 45° by default (55-61). It
  refuses while the network turns and during its cooldown. It cannot supply continuous power.

### Blaze Burner

- `BlazeBurnerBlock.HeatLevel`: `NONE, SMOULDERING, FADING, KINDLED, SEETHING` (310-332). The heat level is
  the block state property `HEAT_LEVEL` (line 72). An empty burner (`NONE`) has no block entity (112-118).
- `BlazeBurnerBlockEntity`: `FuelType` is `NONE, NORMAL, SPECIAL` (407-409). `activeFuel` and
  `remainingBurnTime` are protected fields (59-60).
- `getHeatLevel()` (342-352): SPECIAL gives SEETHING. NORMAL gives KINDLED, or FADING below 125 ticks. NONE
  gives SMOULDERING.
- The server `tick()` (90-120) calls `updateBlockState()` every tick while the fuel is NONE or NORMAL. When
  SPECIAL runs out, the fuel becomes NORMAL with 5,000 ticks left. `setBlockHeat` writes the block state
  only on a level change (224-230).
- `tryUpdateFuel` (236-306): the data map `create:superheated_blaze_burner_fuels` makes fuel SPECIAL, and a
  blaze cake gives 3,200 ticks. Otherwise an item's furnace burn time makes it NORMAL, and coal gives 1,600
  ticks. The same fuel is topped up only when 500 ticks or fewer remain (272-283). Each insert plays a sound
  (298).
- Basins read the block state below them (`content/processing/basin/BasinBlockEntity.java:618-624`), cached
  for one tick (344-345, 795-803). Recipes test heat with `HeatCondition.testBlazeBurner`
  (`content/processing/recipe/HeatCondition.java:32-38`). HEATED accepts FADING, KINDLED or SEETHING.
  SUPERHEATED needs SEETHING.

Recommended mixin:

```java
@Inject(method = "getHeatLevel()Lcom/simibubi/create/content/processing/burner/BlazeBurnerBlock$HeatLevel;",
        at = @At("RETURN"), cancellable = true)
private void cobblemon_dynamics$heat(CallbackInfoReturnable<BlazeBurnerBlock.HeatLevel> cir) {
  var v = PokemonHeat.levelFor((BlazeBurnerBlockEntity) (Object) this); // null, KINDLED, SEETHING
  if (v != null && v.isAtLeast(cir.getReturnValue())) cir.setReturnValue(v);
}
```

- The tick updates the block state every tick, so heat should start within one tick and stop one tick
  after the Pokémon leaves (inferred). The mixin changes no saved data, plays no sound and leaves no burn
  time behind.
- Fallback with no mixin: `BlazeBurnerBlock.tryInsert(BlockState, Level, BlockPos, ItemStack, boolean doNotConsume, boolean forceOverflow, boolean simulate)`
  (181-206) with `doNotConsume = true` and a new coal or blaze cake stack, repeated at 500 ticks or fewer
  remaining. It plays a sound on each insert, and the burner keeps burning after the Pokémon leaves: up to
  2,100 ticks with coal, or 3,700 ticks seething plus 5,000 kindled with cake.
- Create's own egg handler writes those fields directly (`BlazeBurnerHandler.java:64-71`).
- Do not heat empty burners. Setting one to SMOULDERING would make it drop a full Blaze Burner (loot table
  at line 289), which duplicates a blaze (inferred).

### Steam engine boiler

- `api/boiler/BoilerHeater` holds `SimpleRegistry<Block, BoilerHeater> REGISTRY` (line 35) with
  `float getHeat(Level, BlockPos, BlockState)` (line 50). Burner defaults (`content/fluids/tank/BoilerHeaters.java:25-37`):
  SMOULDERING gives passive heat (0), FADING and KINDLED give 1, SEETHING gives 2.
- `BoilerData.updateTemperature` (`content/fluids/tank/BoilerData.java:382-407`) adds the heat of the blocks
  under the boiler. It runs when `needsHeatLevelUpdate` is set, which happens when the block below the tank
  changes (`FluidTankBlock.java:147-152`, then `FluidTankBlockEntity.updateBoilerTemperature()`, 271-278).
- `setBlockHeat` uses `setBlockAndUpdate` (`BlazeBurnerBlockEntity.java:228`), which triggers the tank's
  update. A heated burner powers a boiler with no extra code. Output is still capped by water supply and
  boiler size (187-192). Each heat level fully powers one engine (178-185).

### Encased fan processing (v2: Fan Catalyst)

Type and registry:

- `content/kinetics/fan/processing/FanProcessingType` has `boolean isValidAt(Level, BlockPos)` (19),
  `int getPriority()` (21) and `affectEntity` (32). `getAt` (40-47) returns the first valid type, highest
  priority first.
- Types register in `CreateBuiltInRegistries.FAN_PROCESSING_TYPE`, key `create:fan_processing_type`
  (`api/registry/CreateRegistries.java:29`). The registry is synced to clients
  (`CreateBuiltInRegistries.java:35, 61-62`). It is sorted by priority when it is baked
  (`FanProcessingTypeRegistry.java:20-24`).
- Built-in types (`AllFanProcessingTypes.java`): splashing 400, haunting 300, smoking 200, blasting 100.
  Each checks a fluid tag, then the block tag `create:fan_processing_catalysts/<type>`.

Catalyst position:

- `AirCurrent.rebuild()` (`content/kinetics/fan/AirCurrent.java:159-227`) tests each
  `fanPos.relative(FACING, i)` for i from 1 to ceil(maxDistance), with `FanProcessingType.getAt`.
- The type is sticky (`:189-210`, `getTypeAt` `:343-360`). It covers the catalyst's own block and every
  block downstream until another catalyst replaces it. A pushing fan runs to `maxDistance`, and a pulling
  fan runs to the fan face.
- `maxDistance` = lerp(min(|RPM| ÷ 256, 1), 3, 20) (`IAirCurrentSource.java:31-38`). Only solid blocks cut
  it short (`:229-257`). Entities do not.
- Belts and depots are checked at position i and one block below (`:311-334`).
- The server rebuilds every 30 ticks (`CKinetics.java:28`), or when
  `EncasedFanBlockEntity.blockInFrontChanged()` (120-122) is called.
- A Pokémon that stands in the catalyst block is inside its own segment, so the fan affects it unless it
  is exempt.

Smoking and blasting:

- Smoking (`:311-382`): catalysts are fire, a lit campfire, or a smouldering Blaze Burner. It uses SMOKING
  recipes only and leaves other items alone. Entities get 2 s of fire and 2 damage.
- Blasting (`:101-196`): catalysts are lava, or a Blaze Burner at FADING or hotter (line 110). It uses
  SMELTING recipes, then BLASTING recipes. It destroys food whose smelting result equals its smoking result,
  and any item without a recipe that is not fire-resistant (`FanProcessing.java:37-46`). Entities get 10 s
  of fire and 4 damage.
- A Pokémon-heated Blaze Burner in a fan line is already a blasting catalyst, with no extra code.
- Both types skip entities where `fireImmune()` is true. In Cobblemon that covers Fire-types, lava
  swimmers and a behaviour flag (`PokemonEntity.kt:797-799`, `Pokemon.kt:1041-1043`).
- Haunting gives Blindness and Slowness to any `LivingEntity` (`:276-279`).

Push path and the worker exemption:

- `EncasedFanBlockEntity.tick()` (125-152) calls `findEntities()` every 5 ticks (`:336-340`,
  `getEntities(null, bounds)`).
- Every tick, `tickAffectedEntities(Level)` (76-129) pushes entities (85-100). It then calls `affectEntity`
  (126-127) or processes items (114-124). This runs on both sides.
- The only built-in exemption is a creative-flying player (131-136). There is no entity tag for this.
- This mixin exempts an entity from the push, from `affectEntity` and from item processing:

```java
@Shadow protected List<Entity> caughtEntities;
@Inject(method = "findEntities", at = @At("TAIL"))
private void cobblemon_dynamics$exempt(CallbackInfo ci) { caughtEntities.removeIf(FanWorkers::isExempt); }
```

- The predicate must also work on the client. Nozzles push entities separately
  (`NozzleBlockEntity.java:91-111, 149-164`), so they need the same exemption.

What the client needs for our own fan processing type:

1. The same type id, registered on both sides.
2. `isValidAt` must return true on the `ClientLevel`. The client rebuilds the segments itself from each fan
   packet (`EncasedFanBlockEntity.java:45-49`), which is sent at least every 30 ticks (129-141). So the
   client must know which Pokémon is a working catalyst at which position.
3. `morphAirFlow` (`AirFlowParticle.java:61-87`) and `spawnProcessingParticles` (`AirCurrent.java:115-117,
   147-149`) run on the client.
4. `affectEntity` also runs on the client, so it needs an `isClientSide` guard.
5. A priority above 400, so that a worker that stands in water still counts as its own catalyst.

### Windmill bearing (v2: Tailwind)

- Sail count: `BearingContraption.sailBlocks` (`BearingContraption.java:25`) is counted once, at assembly,
  in `addBlock` (62-67). It counts blocks in `#create:windmill_sails`. A copycat panel counts by its
  material.
- Speed: `WindmillBearingBlockEntity.getGeneratedSpeed()` (65-74) returns 0 when the windmill is not
  running, and `lastGeneratedSpeed` when `movedContraption == null`. Otherwise it returns
  clamp(sailBlocks ÷ `windmillSailsPerRPM`, 1, 16) × (CLOCKWISE ? 1 : −1), with integer division.
- Capacity: `calculateAddedStressCapacity()` (`KineticBlockEntity.java:185-189`) gives SU per RPM. The
  network multiplies it by |`getGeneratedSpeed()`| (`KineticNetwork.java:165-175`).
- Defaults: 512 SU/RPM (`CStress.setCapacity(512.0)`, `AllBlocks.java:1184`). `windmillSailsPerRPM` 8,
  `minimumWindmillSails` 8 and `maxRotationSpeed` 256 (`CKinetics.java:14, 20, 21`). The maximum is 128
  sails, 16 RPM and 8,192 SU.
- Recompute: `updateGeneratedRotation()` runs only on assembly (`MechanicalBearingBlockEntity.java:205`),
  disassembly (222), the direction scroll option (`WindmillBearingBlockEntity.java:112-117`) and
  `reActivateSource` (`GeneratingKineticBlockEntity.java:31-35, 48-54`). No timer runs it. Rotation
  (`getAngularSpeed`, 142-151) and every capacity recalculation read `getGeneratedSpeed()` live.
- Injection point, in `public float getGeneratedSpeed()` (`()F`):

```java
@ModifyExpressionValue(method = "getGeneratedSpeed", at = @At(value = "INVOKE",
  target = "Lcom/simibubi/create/content/contraptions/bearing/BearingContraption;getSailBlocks()I"))
private int cobblemon_dynamics$virtualSails(int sails) { return sails + TailwindBonus.of(this); }
```

- Call `updateGeneratedRotation()` on the server after each bonus change.
- Assembly reads the sail field directly (`BearingContraption.java:44-45`). Virtual sails cannot make a
  windmill with fewer than 8 real sails assemble.
- The client calls the same method. Sync the bonus, or the client spins at the wrong rate and gets
  corrected every 3 ticks (lazyTick 277-281, `clientAngleDiff` 101-105) (inferred).
- A speed change while the windmill runs (`GeneratingKineticBlockEntity.java:89-164`):
  - When the windmill is the network's source, it detaches and reattaches (160-163), which propagates the
    whole network again. Blocks over 256 RPM, blocks that flicker over 128 RPM, and direction conflicts
    break (`RotationPropagator.java:235-247`).
  - When another source is faster and the new speed is still slower, nothing happens. When the new speed
    is slower and has the opposite sign, the bearing breaks itself (145-148). When the new speed is
    faster, the windmill takes over as the source (151-157).
  - Capacity and stress then update (107-112), followed by `sendData()`.
  - `onSpeedChanged` keeps `assembleNextTick` (`WindmillBearingBlockEntity.java:39-44`), so the windmill
    does not assemble again. A sign flip snaps the angle and calls `Contraption.stop()`
    (`MechanicalBearingBlockEntity.java:128-135`, `Contraption.java:1329-1337`). A change of magnitude
    only adds no flicker (`KineticBlockEntity.java:191-196`).
- Capacity and stress both scale with |RPM|, so Tailwind raises throughput, not stress headroom
  (inferred).

### Item handlers (v2: Telekinesis and Magnet)

All three ignore the side:

- Item vault (`ItemVaultBlockEntity.java:69-79, 338-379`): any vault block returns the controller's
  combined handler, 20 stacks per block (`CLogistics.java:14`). It returns null when no controller exists.
- Depot: 9 slots, but only slot 0 accepts an insert, and only while the depot and its output buffer are
  empty (`DepotItemHandler.java:28-41`). It keeps a reference to the stack that you pass in
  (`DepotBehaviour.java:338`).
- Chute: 1 slot, and it accepts only when it is empty (`ChuteBlockEntity.java:486-488`). It shrinks your
  input stack and stores that same instance (`ChuteItemHandler.java:29-35`, `ItemHelper.java:330-339`).

Insert a copy of each stack, as `HopperBlockEntity.addItem` does. A copy also protects against the depot
and chute behaviour above.

### Create on Fabric

No build exists for 1.21.1. The Modrinth project `create-fabric` lists only 1.18.2, 1.19.2 and 1.20.1. The
latest release is 6.0.8.1+build.1744-mc1.20.1 (2025-12-02). The Fabricators-of-Create branch
`mc1.21.1/fabric/dev` had its last commit on 2025-03-11.

## Applied Energistics 2 19.2.19

Java paths are relative to `src/main/java/appeng/`.

### Build and dependencies

- License: code LGPLv3 (`LICENSE`). API MIT. Textures and models CC BY-NC-SA 3.0. Text CC0
  (`README.md:34-50`, POM in `build.gradle:272`).
- Maven Central: `org.appliedenergistics:appliedenergistics2:19.2.19`, or `:19.2.19:api` (`README.md:79-105`).
  The api jar holds only `appeng/api/**`, as binary and sources (`build.gradle:224-228, 240`). Central has
  19.2.1-beta through 19.2.19.
- GuideME is a required dependency, `[21.1.1,)` (`src/main/neoforge.mods.toml:32-35`). The POM lists
  `org.appliedenergistics:guideme:21.1.1` at runtime scope, so it arrives transitively.
- AE2 builds against NeoForge 21.1.169 or later (`gradle.properties:12-13`).

### Growth Accelerator

- `blockentity/misc/GrowthAcceleratorBlockEntity.java`: for each of the 6 neighbours, if
  `adjState.is(AETags.GROWTH_ACCELERATABLE)`, it calls `adjState.randomTick((ServerLevel) level, adjPos, level.getRandom())`
  (lines 104-113). It does not check `isRandomlyTicking`.
- Tag: `ae2:growth_acceleratable` (`api/ids/AETags.java:79-84`), documented as "regardless of what the
  blocks are".
- Rate: `TickingRequest(speed, speed, false)` (lines 60-65). The config `crafting.growthAccelerator` defaults
  to every 10 ticks, range 1 to 100 (`core/AEConfig.java:612-613`).
- Power: 8 AE/t idle on the grid (lines 52, 59). Each cycle also drains 8 × `ticksSinceLastCall` from its
  own 1,600 AE buffer (lines 49-50, 101-102). It counts as powered if the grid is powered or the buffer
  holds at least 8 AE (line 131). It accepts FE only on its front and back faces (lines 57, 80-83).
- Tag contents (`src/generated/resources/data/ae2/tags/block/growth_acceleratable.json`): bamboo_sapling,
  bamboo, sugar_cane, vine, twisting vines, weeping vines, cave vines, sweet_berry_bush, nether_wart, kelp,
  cocoa. Optional: `#minecraft:crops` and `#minecraft:saplings`. Required: `#c:budding_blocks`.
- `#c:budding_blocks` holds `minecraft:budding_amethyst` (NeoForge `data/c/tags/block/budding_blocks.json`)
  and AE2's flawless, flawed, chipped and damaged budding quartz
  (`src/generated/resources/data/c/tags/block/budding_blocks.json`, `datagen/providers/tags/BlockTagsProvider.java:68-81`).
- Budding certus: each random tick has a 1/5 chance to grow. After a successful growth, every block except
  flawless has a 1/12 chance to degrade (`decorative/solid/BuddingCertusQuartzBlock.java:19-20, 31-67`).
  Extra ticks speed up decay too.
- An addon can copy the accelerator: it is a public vanilla call on tagged blocks. Use
  `TagKey.create(Registries.BLOCK, ResourceLocation.parse("c:budding_blocks"))`, which needs no compile
  dependency on AE2.
- Rate comparison (inferred): with vanilla `randomTickSpeed=3`, a block gets a random tick about every
  4096 ÷ 3 ≈ 1,365 ticks. One extra tick per 10 ticks is about 137 times vanilla per block.

### Forge Energy acceptors

`init/InitCapabilityProviders.java:81-84` registers `Capabilities.EnergyStorage.BLOCK` for every
`AEBasePoweredBlockEntity` subclass through `getEnergyStorage(side)`. That method returns the adapter only
for the block's power sides. A `null` side works only when all six faces are power sides
(`blockentity/powersink/AEBasePoweredBlockEntity.java:172-180`). Push FE with a real `Direction`.

| Block | Power sides | Where FE goes | Reference |
|---|---|---|---|
| Energy Acceptor (block) | all | straight into the grid (buffer 0) | `EnergyAcceptorBlockEntity.java:33-37, 55-62` |
| Controller | all | the grid (8,000 AE local) | `ControllerBlockEntity.java:53-56, 119-127` |
| Charger | all except front | 1,600 AE buffer | `ChargerBlockEntity.java:56, 78-79` |
| Inscriber | all except front | 1,600 AE buffer | `InscriberBlockEntity.java:126, 218-219` |
| Growth Accelerator | front and back | 1,600 AE buffer | see above |
| ME Chest | all | 500 AE × usage multiplier | `MEChestBlockEntity.java:129` |

- The Energy Acceptor cable part also accepts FE (`InitCapabilityProviders.java:243-244`). The FE P2P tunnel
  moves FE without conversion (`:238-239`).
- The adapter's `receiveEnergy` calls `injectExternalPower(PowerUnit.FE, …)` with simulate mapped to
  SIMULATE or MODULATE, and returns the amount accepted. `canReceive` is true, `canExtract` is false, and
  `extractEnergy` returns 0 (`helpers/ForgeEnergyAdapter.java:38-70`).
- Conversion: config `powerRatios.forgeEnergy`, default 0.5 (`core/AEConfig.java:50, 659-661, 700`,
  `api/config/PowerUnit.java:63-65`). So 2 FE = 1 AE, and 10,000 FE/t = 5,000 AE/t.

### ME Interface item insertion (v2: Telekinesis)

- The interface block (`init/InitCapabilityProviders.java:122-132`) and the interface cable part
  (`:230-234`) expose `GENERIC_INTERNAL_INV`, which is `getStorage()`. A LOWEST-priority adapter (`:94-101`,
  `AppEngBase.java:142`) turns it into `ItemHandler.BLOCK` (`GenericStackItemStorage.java:43-52`).
- An insert lands in 9 local slots, one stack each (`GenericStackInv.java:241-246`). A slot with a config
  filter accepts only that item (`InterfaceLogic.java:127-140`).
- Unconfigured contents go to the network on the grid tick (`:373-379, 411-440`). That needs a channel
  (`:109`) and power. Without them, the items stay in the buffer.
- Query the cable part from the face that it sits on. A `null` side reaches the cable, which returns
  nothing (`RegisterPartCapabilitiesEvent.java:42-51`, `IPartHost.java:54-60`).
- `ME_STORAGE` reaches the grid directly when nothing is configured (`:253-259`). That is AE2 API, not an
  `IItemHandler`, so Telekinesis does not use it.

### Feasibility notes (later versions)

- Crafting CPU parallelism needs a mixin. Co-processor threads are summed from member blocks when the cluster
  forms (`me/cluster/implementations/CraftingCPUCluster.java:157-162`, max 16 per block) and read through
  `getCoProcessors()` (`:243-244`). Each tick allows `getCoProcessors() + 1` pattern pushes
  (`crafting/execution/CraftingCpuLogic.java:157`). Without a mixin on `getCoProcessors()`, the only route is
  a new block that implements `block/crafting/ICraftingUnitType`, which is not in the api jar.
- Inscriber and molecular assembler speed: faking cards needs a mixin, because speed comes from
  `upgrades.getInstalledUpgrades(AEItems.SPEED_CARD)` inside `tickingRequest`
  (`InscriberBlockEntity.java:338-358`, `blockentity/crafting/MolecularAssemblerBlockEntity.java:412-418`). A
  no-mixin route (inferred) calls the public `IGridTickable.tickingRequest(node, n)` extra times. Assembler
  progress scales with `ticksSinceLastCall` and draws grid power (`:499-506`). This route bypasses AE2's
  sleep and wake scheduling.

## NeoForge 21.1

Source: the NeoForge 21.1.265 sources jar (paths below are relative to `net/neoforged/neoforge/`) and the
1.21.1 docs at docs.neoforged.net/docs/1.21.1/.

### Capabilities

- `capabilities/Capabilities.java:20-51`: `EnergyStorage.BLOCK` is
  `BlockCapability<IEnergyStorage, @Nullable Direction>`, with `ENTITY` and `ITEM` variants.
  `ItemHandler.BLOCK` gives `IItemHandler`, and `FluidHandler.BLOCK` gives `IFluidHandler`.
- `energy/IEnergyStorage.java:17-56`: `receiveEnergy(int, boolean simulate)` returns the amount accepted,
  or that would be accepted. Simulating leaves `getEnergyStored()` unchanged. If `canReceive()` is false,
  `receiveEnergy` returns 0. Nothing in 21.1 replaces this interface.
- `items/IItemHandler.java:49-56`: `insertItem(slot, stack, simulate)` returns the remainder and must not
  change the input. Fluids use `fill` and `drain` with `FluidAction.SIMULATE` or `EXECUTE`
  (`fluids/capability/IFluidHandler.java:18-27, 78-103`).
- Side context is the face of the target block being accessed. `null` means unsided (inferred). Vanilla
  furnaces and the brewing stand use `SidedInvWrapper` (`capabilities/CapabilityHooks.java:80-88`). With
  `null`, every slot shows. With a face, only `getSlotsForFace` shows (`items/wrapper/SidedInvWrapper.java:49-57`).
- For repeated queries, use `BlockCapabilityCache.create(cap, serverLevel, pos, side)`
  (`capabilities/BlockCapabilityCache.java:31, 59`). Providers must call `level.invalidateCapabilities(pos)`
  (docs `inventories/capabilities.md:231`).
- Later port: NeoForge 21.9.1-beta (Minecraft 1.21.9) replaced these interfaces with a transfer API
  (neoforged.net/news/21.9-transfer-rework/): `ResourceHandler<ItemResource>`, `ResourceHandler<FluidResource>`
  and `EnergyHandler`. Transactions replace `simulate`. An `IEnergyStorage.of(handler)` adapter exists for
  migration. On the 26.1.x branch, `Capabilities.Energy.BLOCK` is `neoforge:energy_handler`, and
  `IEnergyStorage` is `@Deprecated(since="1.21.9", forRemoval=true)`.

### Vanilla item handlers (v2: Telekinesis and Magnet)

NeoForge registers these providers in `capabilities/CapabilityHooks.java:60-100`:

- Chests (`:71-73`): `InvWrapper(ChestBlock.getContainer(…, true))`. The side is ignored, and a double chest
  is one 54-slot handler.
- Barrels, dispensers, droppers and similar blocks (`:90-100`): `InvWrapper`. The side is ignored.
- Hoppers (`:75-78`): `VanillaHopperItemHandler`. The side is ignored. A real insert into an empty hopper
  sets an 8-tick cooldown (`items/VanillaHopperItemHandler.java:21-37`).
- Sided containers, such as furnaces, the brewing stand and shulker boxes (`:80-88`): `SidedInvWrapper`.
  A `null` side shows all slots (`SidedInvWrapper.java:49-56, 79-84`). An insert still goes through
  `canPlaceItemThroughFace(…, null)` and `canPlaceItem` (`:112, 139`). An extract skips the face check
  (`:190`).

Dropped items for the Magnet:

- The cheapest query is `level.getEntitiesOfClass(ItemEntity.class, box, predicate)`. It goes through
  `EntityTypeTest.forClass` to a per-section `ClassInstanceMultiMap.find(ItemEntity.class)` lookup.
- Do not query by `EntityType.ITEM`. Its base class is `Entity`, so the query scans every entity.
- To cap the result count, use `Level#getEntities(EntityTypeTest, AABB, Predicate, List, int)`. The vanilla
  hopper uses the same `getEntitiesOfClass` call.
- Pickup delay: the delay counts down each tick unless it is 32767, which means "never".
  `hasPickUpDelay()` means the delay is above 0. The default delay is 10. A player picks an item up only at
  0 with a matching `target`, unless NeoForge's `ItemEntityPickupEvent.Pre` overrides it. A hopper ignores
  the delay. The Magnet skips items with `hasPickUpDelay()` (inferred design).
- Insert a copy, as `HopperBlockEntity.addItem` does:

```java
ItemStack rem = ItemHandlerHelper.insertItemStacked(h, ie.getItem().copy(), false);
if (rem.isEmpty()) ie.discard(); else ie.setItem(rem);
```

### Data attachments

- Block entity attachments save with the block entity NBT under `neoforge:attachments`
  (`attachment/AttachmentHolder.java:27`). The patched `BlockEntity.setData` and `removeData` call
  `setChanged()` themselves. A change to an object from `getData` needs a manual `setChanged()`
  (`attachment/AttachmentType.java:37-39`, docs `datastorage/attachments.md:80-93`). This works on any block
  entity, including Cobblemon's pasture (inferred).
- Entity attachments save with the entity NBT. They are not copied on player death unless the type calls
  `copyOnDeath()` (`AttachmentType.java:44, 224`).
- `ServerLevel` attachments are stored in the SavedData `neoforge_data_attachments`, which always re-saves
  (`LevelAttachmentsSavedData.java:16-26, 45-49`). The 1.21.1 docs page leaves this out.
- Serialization: `.serialize(Codec)`, `.serialize(Codec, Predicate)`, `IAttachmentSerializer`, or
  `serializable(INBTSerializable)` (`AttachmentType.java:119-196`). A codec-based attachment cannot
  reference its holder.
- Client sync is automatic since 21.1.197 (backport #2544). 21.1.264 fixed the initial sync to respect
  `sendToPlayer`. The API: `Builder.sync(StreamCodec)`, `sync(predicate, codec)` or
  `sync(AttachmentSyncHandler)` (`AttachmentType.java:250-273`). Sync happens on initial tracking, on default
  creation, on `setData` and on `removeData`. For other changes, call `holder.syncData(type)`
  (`attachment/AttachmentSyncHandler.java:15-24`). It works for BlockEntity, LevelChunk, Entity and Level
  (`AttachmentSync.java:74-81`). The 1.21.1 docs still say to send your own packet (`attachments.md:98`),
  which is out of date.

### Interaction hooks

Checked in the Minecraft bytecode.

- Block right-click order, on both sides (`ServerPlayerGameMode.useItemOn`, lines 338-395, and docs
  `items/interactionpipeline.md`):
  1. The `RightClickBlock` event.
  2. `Item#onItemUseFirst`, unless the event set `useItem` to FALSE.
  3. `Block#useItemOn`, subject to sneaking and the event's `useBlock`.
  4. `useWithoutItem` (main hand only, on `PASS_TO_DEFAULT_BLOCK_INTERACTION`).
  5. `Item#useOn`.
- `UseItemOnBlockEvent` also fires once per phase (`event/entity/player/UseItemOnBlockEvent.java:29-42`).
- `onItemUseFirst` (`common/extensions/IItemExtension.java:106-111`) runs before a furnace or chest opens
  its GUI. `useOn` never runs when the block consumes the click.
- Entity right-click order (`Player.interactOn`, lines 1051 and later; `PlayerInteractEvent.java:52-118`):
  `EntityInteractSpecific` event, then `Entity#interactAt`, then the `EntityInteract` event, then
  `Entity#interact` (which calls `mobInteract` on a Mob), then `Item#interactLivingEntity`.
- Cancellation: the default cancellation result is PASS, which lets the client try other interactions
  (`PlayerInteractEvent.java:88-90, 116`). Set `setCancellationResult(SUCCESS)`. Cancelling `RightClickBlock`
  also sets `useBlock` and `useItem` to FALSE (`:216-222`).
- A client-only cancel does not stop the server. The client sends `ServerboundInteractPacket` before it
  fires its entity events (`MultiPlayerGameMode`, lines 422-434), and it always builds
  `ServerboundUseItemOnPacket` (lines 298-299). Cancel on both sides. The javadoc warns about desync (`:161`).
- `LeftClickBlock`: the client fires START, and `CLIENT_HOLD` every tick while held. The server fires it
  first in `handleBlockBreakAction` for START, STOP and ABORT, and returns early if cancelled (line 139 and
  later). A client cancel skips `Block#attack` and local mining but still sends START (lines 161-169). In
  creative mode, `setUseBlock` and `setUseItem` have no effect (`PlayerInteractEvent.java:286-300`).

### GameTest

- `@GameTestHolder(MODID)` classes are found through FML scan data (`gametest/GameTestHooks.java:41-63`).
  `RegisterGameTestsEvent` on the mod bus registers classes or single methods
  (`event/RegisterGameTestsEvent.java:18-41`).
- The template namespace comes from `@GameTest.templateNamespace`, then `@GameTestHolder.value`, then
  `minecraft` (`GameTestHooks.java:85-99`). `@PrefixGameTestTemplate(false)` drops the default lowercase
  class-name prefix (`gametest/PrefixGameTestTemplate.java:15-35`).
- Structure templates live in `data/<ns>/structure/*.nbt` (docs `misc/gametest.md:166-182`). AE2 builds its
  scenes in code with `@GameTestGenerator` instead (AE2 `server/testworld/GameTestPlotAdapter.java:28-50`,
  `core/AppEngBase.java:300-304`).
- Tests run in the IDE, with `neoforge.gameTestServer`, or with `neoforge.enableGameTest`. Filter with
  `neoforge.enabledGameTestNamespaces=a,b`, with no spaces (`GameTestHooks.java:32-37, 65-72`). The server's
  exit code is the number of failed required tests (docs `gametest.md:250`).
- Kotlin: vanilla `GameTestRegistry` calls static methods directly, and otherwise calls `Class.newInstance()`.
  Use a class with a public no-argument constructor, or an `object` with `@JvmStatic`. A plain `object`
  member fails (inferred).
- ModDevGradle 2.x run: `runs { gameTestServer { type = "gameTestServer"; systemProperty 'neoforge.enabledGameTestNamespaces', mod_id } }`,
  run as `gradlew runGameTestServer` (MDG README, AE2 `build.gradle:177-180`).
- Other mods load in the run. On 1.21.8 and older, any jar on the runtime classpath with
  `META-INF/neoforge.mods.toml` loads as a mod. Plain libraries need `additionalRuntimeClasspath`. Cobblemon
  works as-is, because its jar nests only MongoDB drivers.
- JUnit with a live server: `unitTest { enable(); testedMod = mods.x }` plus `net.neoforged:testframework`
  (AE2 `build.gradle:183-186`).
- Later port: `GameTestHolder` and `PrefixGameTestTemplate` no longer exist on NeoForge's 1.21.5 and 26.1.x
  branches.

### ModDevGradle and Kotlin

- ModDevGradle 2.0.148 was the latest release, and 21.1.265 the latest NeoForge 21.1.
- KotlinForForge 5.x covers Minecraft 1.20.6 to 1.21.8, and 6.x covers 1.21.9 and later. 5.10.0 bundles
  Kotlin 2.2.20, 5.11.0 bundles 2.3.0, and 5.12.0 bundles 2.4.0.
- Access transformers: ModDevGradle and NeoForge read `META-INF/accesstransformer.cfg` by default. Set
  `validateAccessTransformers = true`.
- Interface injection: `neoForge { interfaceInjectionData.from 'interfaces.json' }`. It applies at
  development time only, so a mixin must also add the interface at runtime.
- Mixin: declare it with `[[mixins]] config="…mixins.json"` in `neoforge.mods.toml` (docs
  `gettingstarted/modfiles.md:171-177`). NeoForge ships Mixin and MixinExtras. No refmap is needed
  (inferred, because Cobblemon's configs declare none).
- Write mixin classes in Java. Cobblemon does the same: none of its 48 common mixin classes carry
  `kotlin/Metadata`.
- Mixins into Kotlin classes target JVM names: `Foo$Companion`, `FileKt`, `getX` and `setX`, `$default`
  overloads, and mangled `internal` names. An `inline` function is copied into its callers, so an injection
  into the declaration misses those copies (inferred).

## Vanilla 1.21.1 (Mojang mappings)

### Furnace family

- `AbstractFurnaceBlockEntity` package-private `int` fields: `litTime` (remaining burn time), `litDuration`
  (burn duration), `cookingProgress` and `cookingTotalTime`. Also `protected final ContainerData dataAccess`
  (indices 0 to 3) and `private boolean isLit()`, which is `litTime > 0`.
- NBT keys are `BurnTime`, `CookTime` and `CookTimeTotal`. `litDuration` is not saved. It is recomputed from
  the fuel slot on load.
- Coal burns 1,600 ticks (NeoForge `data/neoforge/data_maps/item/furnace_fuels.json:102-104`). A standard
  cook takes 200 ticks.
- These fields have other names on NeoForge's 1.21.4 and 1.21.11 branches: `litTimeRemaining`,
  `litTotalTime`, `cookingTimer` and `cookingTotalTime`.
- `serverTick` (lines 279-343):
  1. `wasLit = isLit()`. If lit, it decrements `litTime`.
  2. If lit, or if it has both fuel and input, it looks up the recipe.
  3. It consumes fuel only when the furnace is not lit and `canBurn` passes:
     `litTime = litDuration = getBurnDuration(fuel)` (`stack.getBurnTime(recipeType)`), then it shrinks the
     fuel stack (297-310).
  4. If lit and `canBurn` passes, it increments `cookingProgress` and smelts at the total. Otherwise progress
     resets to 0.
  5. If not lit, progress falls by 2 per tick.
  6. It writes LIT only when `wasLit != isLit()` (334-337).
- The patched `canBurn` (346-363) checks for non-empty input, a recipe, and room for the result in the output
  slot. Its descriptor has an extra trailing `AbstractFurnaceBlockEntity` parameter.
- Keeping a furnace lit from outside (inferred design):
  1. On each tick that a recipe can run, set `litTime = max(litTime, 2)` and `litDuration = 1600`. A value of
     2 survives the decrement in any tick order.
  2. No fuel item is consumed, because step 3 of `serverTick` runs only when the furnace is unlit.
  3. `wasLit` is read after our write, so the furnace never turns LIT on by itself. Set `LIT=true` on the
     change from unlit to heated.
  4. When the top-up stops, the furnace goes out within 2 ticks and clears LIT itself.
  5. This needs access transformer entries on `litTime`, `litDuration` and `canBurn`. Call `setChanged()`
     after a write.

### Brewing stand

- `BrewingStandBlockEntity` (lines 93-120): fields `fuel` (0 to 20, `FUEL_USES = 20`) and `brewTime`.
- If `fuel <= 0` and slot 4 holds Blaze Powder (`Items.BLAZE_POWDER`, hard-coded in 1.21.1), it sets
  `fuel = 20` and shrinks the stack.
- It spends fuel only when a brew starts: `fuel--` and `brewTime = 400` (113-117).
- External fuel (inferred): keep `fuel >= 1` while a brew can start. This needs an access transformer entry
  on `fuel`, or on `dataAccess` index 1.

### Enchanting table (Muse)

- The cost code is the lambda `EnchantmentMenu.lambda$slotsChanged$0(ItemStack, Level, BlockPos)V` (source
  lines 103-136). It runs in this order:
  1. A float `j` sums `getEnchantPowerBonus` for each offset that passes `isValidBookShelf`. That check
     needs a bonus above 0 and a transmitter block at the midpoint.
  2. It calls `random.setSeed(enchantmentSeed)`.
  3. For each row, `getEnchantmentCost` computes the cost and clamps the power to 15. A cost below row + 1
     becomes 0. Then `EventHooks.onEnchantmentLevelSet(level, pos, k, (int) j, stack, cost)` fires.
- NeoForge's default `IBlockExtension#getEnchantPowerBonus(BlockState, LevelReader, BlockPos)` is
  `is(ENCHANTMENT_POWER_PROVIDER) ? 1 : 0` (`IBlockExtension.java:489-491`). It works per block, not per
  table.
- The most faithful hook is a mixin. It keeps vanilla's random sequence, and it also feeds the event:

```java
@ModifyVariable(method = "lambda$slotsChanged$0",
  at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;setSeed(J)V"))
private float cobblemon_dynamics$shelves(float power, @Local(argsOnly = true) Level level,
                                         @Local(argsOnly = true) BlockPos table) {
  return power + MuseBonus.of(level, table);
}
```

- The alternative needs no mixin. Handle `EnchantmentLevelSetEvent`, match `getPos()`, recompute with
  min(15, `getPower()` + N), apply the below-row-plus-one rule again, and call `setEnchantLevel`. The event
  does not expose the menu's random source, so the handler must seed its own random source in a fixed way
  to prevent rerolls (inferred).
- A table that already has 15 power gains nothing either way.
- NeoForge 21.1.265 bundles MixinExtras 0.5.5 (changelog line 17), which provides `@Local` and
  `@ModifyExpressionValue`.

### Cauldrons (Pump)

- NeoForge exposes cauldrons. `CauldronFluidContent` registers the empty, water and lava cauldrons
  (`:111-113`) with `FluidHandler.BLOCK`, through `CauldronWrapper` (`:162-172`). Powder snow cauldrons are
  not covered.
- For water, one wrapper step is 1,000 mB, which is 3 levels (`CauldronWrapper.java:75-101, 130-150`). A
  fill works only on an empty cauldron, and a drain only on a full one. A cauldron at level 1 or 2 does
  nothing through the wrapper.
- To fill level by level, set the block state directly, as vanilla's `receiveStalactiteDrip` does:

```java
BlockState n = s.is(Blocks.CAULDRON) ? Blocks.WATER_CAULDRON.defaultBlockState() // LEVEL 1
    : s.setValue(LayeredCauldronBlock.LEVEL, s.getValue(LayeredCauldronBlock.LEVEL) + 1); // if !isFull
level.setBlockAndUpdate(pos, n);
level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(n));
level.levelEvent(1047, pos, 0);
```

## Prior art

### Cobbleworkers 2.0.7

Paths come from the decompiled 2.0.7 NeoForge jar, under `accieo/cobbleworkers/`, unless they are marked as
GitHub source (`CW-src`, commit `2986bde`, version 1.8.0).

- **Jobs.** 32 data-pack jobs in `data/cobbleworkers/jobs/*.json`, assigned by type, species, move or ability.
  - Fire: `fuel_generator` targets furnaces, blast furnaces and smokers that hold items. It adds 80 s of burn
    time (one coal) on an 80 s cooldown, capped at 20,000 ticks (`job/executor/AddFuelExecutor.kt:17,30-36`).
    Fire also runs `lava_generator`, which fills cauldrons.
  - Dragon: `brewing_stand_fuel_generator` adds 2 fuel charges.
  - Water: `irrigator` (sets farmland moisture 0–4 to 7 in a 3 × 3 area), `water_generator`, `extinguisher`
    and `fishing_looter`. Ice: `powder_snow_generator`.
  - Electric: only `voltaconia_harvester`. Psychic: `item_gatherer` and `lucibell_harvester`.
  - All other jobs harvest or loot.
  - CW-src 1.8.0 had 22 hard-coded workers (`common/src/main/kotlin/accieo/cobbleworkers/jobs/WorkerDispatcher.kt:24-47`).
- **Pasture hook.** One mixin on `PokemonPastureBlockEntity`: TAIL of the static ticker lambda
  `TICKER$lambda$0(Level, BlockPos, BlockState, PokemonPastureBlockEntity)` and HEAD of `onBroken()`
  (`mixin/PokemonPastureBlockEntityMixin.java:13-31`). Accessor mixins expose the furnace and brewing stand
  fuel fields.
- **Detection.** `PastureSystem.tickPasture` walks `getTetheredPokemon()`, then `Tethering.getPokemon()`, then
  `getEntity()`. It skips Pokémon that are fainted, in `PoseType.SLEEP`, or marked resting
  (`systems/PastureSystem.kt:74-87`). It caches eligible jobs for 100 ticks (`:44-63`).
- **State.** Every (Pokémon, job) pair runs its own state machine (`job/context/JobContextManager.kt:14-17`):
  IDLE, then the components in order, then DEPOSITING, then IDLE (`job/JobStateMachine.kt:28-144`).
- **Movement.** `navigation.moveTo(x+0.5, y, z+0.5, 0.4)` at most every 20 ticks, with arrival at 2.5 blocks or
  less (`services/NavigationService.kt:13-55`). No Brain tasks, behaviours or goals.
- **Scanning.** A box of ±8 X/Z and ±5 Y at 50 blocks per tick, then a 45 s wait
  (`systems/PastureDeferredScanSystem.kt:44-88`, defaults in `config/CobbleworkersConfig.kt:3-9`).
- **Claims.** A global `ClaimService.blockClaims` (block position to Pokémon UUID and tick) with `putIfAbsent`
  and a 30 s expiry (`services/ClaimService.kt:11-18,75-79`), used by `NavigationExecutor`
  (`job/executor/NavigationExecutor.kt:28-42`). The key has no dimension (inferred). A claim leak with no expiry
  (#73) was fixed on 2026-07-01.
- **UI.** Nothing on the pasture screen, "by design, since cobbleworkers is currently a server-side mod" (#70).
  A per-Pokémon "Rest" option on the interaction wheel uses `CobblemonEvents.POKEMON_INTERACTION_GUI_CREATION`
  (`neoforge/client/CobbleworkersNeoForgeClient.kt:43-46`, `client/CobbleworkersInteractWheel.kt:20-25`).
- **Rest flag.** `cobbleworkers.resting` in `Pokemon.persistentData` (`services/RestManager.kt:9-31`). Resting
  or sleeping Pokémon skip every job (`systems/PastureSystem.kt:81`).
- **Extension.** Data-pack jobs in `data/<namespace>/jobs/*.json` load at server start and on `/reload`.
  `JobRegistry.clear()` runs first, and `"enabled": false` skips a job. Requirement keys: species, types,
  abilities, moves, marks, level, shiny, friendship, aspects, cosmetic, conditions
  (`job/definition/Requirements.kt:15-25`). The 10 component types are fixed
  (`job/serialization/ComponentTypes.kt:3-14`), and the `Component` base class is sealed at the JVM level.
- **Healer job.** A Pokémon that knows Wish, Soft-Boiled, Moonlight, Recover, Roost, Heal Bell, Aromatherapy,
  Synthesis, Rest or Life Dew gives a player Regeneration for 20 s on a 30 s cooldown
  (`job/executor/StatusEffectExecutor.kt:31`). It never heals Pokémon.
- **No fatigue.** It has no stamina, hunger or bed mechanics. Its only item is
  `cobbleworkers:worker_inventory_selector` (paper, red apricorn and string).
- **Health (2026-11-09).** 123 GitHub commits, none since 2026-04-05. Two contributors. 0 open and 52 closed
  issues, most answered within a day. Closed-source releases 2.0.3 to 2.0.7 shipped between 2026-05-14 and
  2026-11-01. Pull requests are not accepted.

### Cobblebase 2.0.1

Paths: `core/` is `common/src/main/kotlin/notlown/cobblebase/core/`, and `mixin/` is
`common/src/main/java/notlown/cobblebase/mixin/`.

- **Skills.** 45 skill files in `common/src/main/resources/data/cobblebase/skills/`. Each species gets a
  hand-picked list with a proficiency of 1 to 5, in about 1,367 `species_skills/*.json` files. `furnace_fuel`
  and `brew_fuel` add 1,600 ticks, "Same as a piece of coal" (`core/executors/FurnaceFuelExecutor.kt:69`).
  Modded furnaces work through the tag `cobblebase:furnace_compatible`, or by dropping a coal into slot 1
  (`:26-35`). `CHANGELOG-1.5.5.md:345` lists Create's Blaze Burner as "not yet supported".
- **Pasture hooks** (`mixin/PokemonPastureBlockEntityMixin.java:39-350`): `TICKER$lambda$0` at TAIL runs the
  main loop. `checkPokemon` at HEAD and TAIL saves and restores the positions of Water-types and fishing or
  diving workers. `releasePokemon` at HEAD cleans up.
- **Entity hook.** `PokemonEntity.tick` at HEAD is a "sleep lock". It forgets the Brain's `WALK_TARGET`,
  `LOOK_TARGET` and `PATH` and pins the X and Z position (`mixin/PokemonEntitySleepLockMixin.java:53-115`).
- **Assignment.** Work is opt-in per Pokémon. Without an assignment, a Pokémon is idle and gets ambient sit,
  sleep, socialize and wander behaviour (`core/BaseManager.kt:182-257`). Idle Pokémon sleep at night, between
  time 13000 and 22999 (`mixin/PokemonPastureBlockEntityMixin.java:239-279`, `core/AmbientBehavior.kt:566-575`).
- **Teleports.** Pokémon beyond max(configured distance, range + 4) go back to the pasture, and drowning
  non-Water-types are pulled out (`core/BaseManager.kt:149-174`). Water-types on land are moved into water
  within 20 blocks every tick (`mixin/PokemonPastureBlockEntityMixin.java:217-232`).
- **Claims.** One soft reservation per executor. For furnace fuel, `furnaceTarget` maps Pokémon UUID to block
  position with a 600-tick expiry (`FurnaceFuelExecutor.kt:63-70,96-113`). Nothing spans executors. The key
  has no dimension (inferred).
- **UI.** Mixins into `com.cobblemon.mod.common.client.gui.pasture.PastureWidget`
  (`neoforge/src/main/java/notlown/cobblebase/neoforge/mixin/PastureWidgetMixin.java:26-138`): `<init>` at
  RETURN builds a "Cobblebase" button, `renderWidget` at TAIL draws it, and `mouseClicked` at HEAD intercepts
  the click. A second mixin hooks `PCGUI.mouseClicked` (`PCGUIClickPriorityMixin.java:16-20`). The button
  position became configurable after it collided with Cobbreeding's egg button (`CHANGELOG-1.5.5.md:12`).
- **Effects.** `SkillEffects.playSuccess` sends `PlayPosableAnimationPacket(entityId, names, [])` to nearby
  players through `CobblemonNetwork.sendPacketToPlayer`. Each effect type has a list of names, sent with and
  without a species prefix (`core/effects/SkillEffects.kt:40-52,73-93`).
- **Extension.** `ExecutorRegistry.register(name, SkillExecutor)` is public, and `SkillExecutor` is a plain
  interface (`core/ExecutorRegistry.kt:102-104`, `core/SkillExecutor.kt:13-29`). Skills and species load only
  from the mod's own jar (`core/SkillRegistry.kt:86-104`, `core/SpeciesSkillRegistry.kt:70-87`), so data
  packs cannot add skills, although the docs say they can (inferred).
- **Healer and Mentor.** The healer heals a player and that player's party, never pastured workers
  (`core/executors/HealerExecutor.kt:23-30,151-170`). The mentor gives pasture-mates passive XP
  (`core/executors/MentorExecutor.kt:14-22`). No stamina, beds or blocks.
- **Health (2026-11-09).** 597 commits, 80 in the last six months (77 of them in May). Two contributors. 0 open
  and 7 closed issues. The Cobblemon 1.8 crash fix came from an outside pull request (#8, merged after 16 days).
