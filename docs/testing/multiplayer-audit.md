# Multiplayer code audit

M8 Task 6 reads the whole mod against the eight multiplayer rules of the
[Phase 2 plan index](../plans/2026-11-10-phase-2-plan.md#global-constraints). The index states them as follows. They are
numbered here for the table.

> 1. The server owns all game state, and the client only displays it. No client code decides a bind, a claim, a mode, a
>    bonus or an amount. Clients get state through synced attachments, Create block entity packets, item data components
>    and the mod's payloads.
> 2. Per-player state is keyed by player UUID, or it lives on that player's own item stack. No code holds a single
>    "current player".
> 3. The server validates every payload from its own state: the sender's permission, distance, dimension and ownership
>    (CD-SEC-01 to CD-SEC-08).
> 4. Payload handlers run their game logic on the server thread. A handler registers with `PayloadRegistrar`'s default
>    thread, `HandlerThread.MAIN` (the registrar's constructor sets it, checked with `javap`), and never with
>    `executesOn(HandlerThread.NETWORK)`. Code on the network thread hands its work to `IPayloadContext.enqueueWork`.
> 5. Client-only classes never load on a dedicated server. They live in `client/` and `mixin/cobblemon/client/`, and only
>    code behind `FMLEnvironment.dist.isClient` names them. Code that runs on both sides, such as the fan processing types
>    and the Create mixins, names no client class.
> 6. Static mutable state is cleared on `ServerStoppedEvent`, because the integrated server stops and starts again in the
>    same JVM.
> 7. Effects and UI updates reach every player who should see them: effect packets go to every player within
>    `effectBroadcastRadius` (CD-FX-08), Create state goes out through `sendData()` to every tracking client, and panel
>    states go to every viewer of the panel.
> 8. Owner and binder checks hold when two players share a pasture or a target. A check compares the acting player's UUID
>    with the Pokémon's owner, the binding's binder or the pasture's placer, or reads operator permission.

## Scope and method

- The approver, 2026-11-10: "this MUST work in multiplayer ... we should make sure the code is made in-mind for
  multiplayer", and "no multiplayer game tests, we don't have that capacity. just make sure you're confident in the code."
- The audit read all of `src/main` at the head of PR #18, commit `fcbe537` of `m8-art-gate`. That covers Phase 1, M6, M7
  and the client sprite code of M8. Quick Work is not built yet, and it gets its own review.
- The branch then moved onto `main` at `1bc0db7`, which adds the path-check fix of PR #20. The audit read the code that
  PR #20 changed: `engine/Reach.kt`, `WorkEngine.claim` and `WorkEngine.wake`.
- Step 1 of the task ran the eight inventory searches over `src/main`, and the audit read every hit with the code around
  it.
- Facts about the platform come from `javap` and source reads of the jars in the Gradle cache: NeoForge 21.1.265,
  Cobblemon 1.8.4 and Create 6.0.10. The table names the source of each one.
- No GameTest has two players. A fix whose logic is pure gets a unit test first. The fix of row 18 is a CD-SEC-08 rule, so
  it also gets one GameTest (ruling of 2026-11-11).
- Each fix is in the commit "Audit the mod for multiplayer and fix what the audit found". Its checks are `./gradlew build`,
  `./gradlew runGameTestServer` and `py scripts/testpack/testpack.py smoke`. The smoke run starts a dedicated server with
  the mod, so it shows that no client class loads on a server (CD-PLAT-06).
- Rows: CD-SEC-01 to CD-SEC-11, CD-PLAT-06, CD-FX-08, CD-UI-05 and CD-UI-09.

Kotlin paths in the tables are under `src/main/kotlin/codes/honor/cobblemondynamics/`. A path that starts with `java/` is
under `src/main/java/codes/honor/cobblemondynamics/`, and a path that starts with `resources/` is under
`src/main/resources/`.

## Findings

The audit records 62 findings: 60 hold, and 2 needed a fix (rows 18 and 41). Row 46 holds, and it carries one open item.

| # | Rule | File and line | What the code does | Verdict | Fix, test or note |
| --- | --- | --- | --- | --- | --- |
| 1 | 1 | `client/WorkModeButton.kt:48-51` | The button asks for the opposite of the Work Mode that the server last sent, and only when that state allows a toggle. The payload carries the requested state, never a flip. | holds | The server checks each request again (row 13). |
| 2 | 1 | `client/PastureScreenHooks.kt:41`, `:64` | The panel tells the server that it opened or closed. Neither payload has a field, so the server finds the pasture through the player's own Cobblemon pasture link. | holds | |
| 3 | 1 | `client/HeldLeash.kt:22`, `client/OverviewRequests.kt:11-15` | While the Work Leash is in the main hand, the client asks for its overview at once and then every 20 ticks. | holds | After a change of level, the old outlines stay until the next reply, which the client asks for within 20 ticks. |
| 4 | 1 | `client/ClientState.kt`, `client/HeadBars.kt:39`, `client/PanelBars.kt:23`, `client/TargetOutlines.kt:31` | The client keeps the last panel state and overview that the server sent, and reads the synced `stamina_view` attachment. It draws them and decides nothing. | holds | |
| 5 | 1 | `leash/WorkLeashItem.kt:24-34`, `leash/LeashInteractions.kt:56-70` | On a client, the leash only predicts a click. It cancels a click on the player's own Cobblemon by the entity's synced owner. It consumes a block click while the client's copy of the stack holds a selection. | holds | The server reads the owner from Cobblemon's store, and it decides every select, bind and flip. |
| 6 | 1 | `handler/create/PokemonFanTypes.kt:25-49`, `java/mixin/create/WindmillBearingBlockEntityMixin.java`, `java/mixin/create/WaterWheelBlockEntityMixin.java` | On a client, the fan types, the windmill speed and the wheel flow read the catalyst, the virtual sails and the drive that the server synced. They mirror the client side of Create's own work. | holds | The server's values decide the processing, the speed and the flow. |
| 7 | 1 | `handler/vanilla/MuseBonus.kt:42-43`, `handler/create/PokemonHeat.kt:33-35` | The Muse bonus and the Superheater heat give nothing on a client. The client gets the offers through the menu's data slots, and the heat through the burner's block state. | holds | |
| 8 | 2 | `net/PlayerCooldown.kt:10`, `net/DynamicsPayloads.kt:25-27`, `pasture/WorkModeService.kt:18` | The rate limits of the overview requests, the panel opens and the toggle replies are maps by player UUID. So is the tick of each player's last accepted toggle. | holds | |
| 9 | 2 | `net/PanelViewers.kt:26-27` | The panel viewers and the last state sent to each are maps by player UUID. A view holds its `ServerPlayer`. `tick` drops the view when that player is removed, leaves the level or links another pasture. | holds | |
| 10 | 2 | `Feedback.kt:16`, `OfflineMessages.kt:10`, `handler/vanilla/EnchantingMenus.kt:23` | The chat messages that wait for offline players, at most 10 each, and the open enchanting menus, one each, are maps by player UUID. | holds | |
| 11 | 2 | `leash/LeashComponents.kt:38-43`, `leash/LeashInteractions.kt:101`, `:223` | The selection and the pending source live on the player's own Work Leash stack. A leash that passes to another player gives nothing: the server finds no such Cobblemon in the new holder's PC or party. It refuses the bind and removes the selection. | holds | The leash's tooltip still shows the name that the selection stored. |
| 12 | 2 | Inventory search 2 | No field holds a single current player. The hits are event parameters and the maps of rows 9 and 10. | holds | |
| 13 | 3 | `net/DynamicsPayloads.kt:53-55`, `:75-83`, `pasture/WorkModeService.kt:24-47`, `pasture/WorkModePolicy.kt:19-36` | `work_mode_toggle`: the server finds the pasture through Cobblemon's link and looks it up in the player's own level. It then checks placer or operator, distance and dimension, and the toggle cooldown (CD-SEC-01 to CD-SEC-05). A refused or ignored request gets the true state back at most once per cooldown. | holds | Cobblemon 1.8.4's `getLinkByPlayer` keeps a link only in a level of the same dimension type, and only while the player is closer than 10.0 blocks to the pasture's center (`javap`). So a toggle from exactly 10.0 blocks finds no link and changes nothing. That is stricter than the 10.0 boundary of CD-SEC-04, never looser. Ruling of 2026-11-11: holds. |
| 14 | 3 | `net/DynamicsPayloads.kt:56-58`, `:89-93` | `pasture_panel_open`: the sender becomes a viewer of the pasture of its own link only. A second open within 10 ticks gets the state with the next 20-tick refresh, not at once. | holds | |
| 15 | 3 | `net/DynamicsPayloads.kt:59-61` | `pasture_panel_close` removes only the sender's own view. | holds | |
| 16 | 3 | `net/DynamicsPayloads.kt:62-64`, `:96-99`, `net/LeashOverviewPayloads.kt:46-55` | `leash_overview_request`: at most one reply per player in 10 ticks. The reply holds only the sender's own bindings in the sender's level (CD-UI-09). | holds | |
| 17 | 3 | `leash/LeashInteractions.kt:96-165` | A leash block click takes every bind input from server state. Those inputs are the server's copy of the stack, the player's own PC and party, the pasture's Work Mode, the role, the work area and the index (CD-SEC-08). Vanilla checks the click's reach before `onItemUseFirst`, and a click that another mod cancels never gets there (CD-SEC-11). | holds | Row 18 covers the pending source, which a client in creative mode can write. |
| 18 | 3 | `leash/LeashInteractions.kt:104-115`, `leash/BindRules.kt:56-80` | A client in creative mode can write any item component, so it can write a `pending_source` and skip the source click that CD-SEC-11 relies on. The destination click read that source's block before any check, so a far source made the server load its chunk. The click now checks the source first and reads no block for the check. A source outside the work area is refused with "Outside its pasture's work area" (CD-TLK-09). A source in a chunk that is not loaded counts as absent. Either source leaves the stack. | **fix** | Ruling of 2026-11-11: creative mode stays trusted with item data, as vanilla trusts it, so the source stays on the stack. Unit test: `BindRulesTest`, "CD-SEC-08 a pending source is read only inside the work area and in a loaded chunk". GameTest: `LeashTests.cdSec08FarPendingSourceBindsNothingAndLoadsNoChunk`. Commit: "Audit the mod for multiplayer and fix what the audit found". |
| 19 | 3 | `leash/LeashInteractions.kt:245-266`, `leash/FlipRules.kt:7` | A sneak-click reverses only a target that a worker drives. It does so only for that worker's binder, the placer of its pasture or an operator (CD-SEC-10). | holds | |
| 20 | 3 | `command/DynamicsCommands.kt:42-45` | The root literal needs permission level 2, so no subcommand runs for a player below it (CD-SEC-09). | holds | |
| 21 | 4 | `net/DynamicsPayloads.kt:43-65` | No registrar calls `executesOn`. NeoForge 21.1.265's `PayloadRegistrar(String)` sets `HandlerThread.MAIN` (`javap`). So every handler runs on the main thread, in arrival order (CD-SEC-06). | holds | |
| 22 | 4 | Inventory search 4 | `src/main` starts no thread and uses no executor, future or parallel stream. | holds | |
| 23 | 4 | `engine/EngineEvents.kt:53-65`, `leash/LeashResets.kt:55-58` | The engine tick, the leash index check and the chunk unload run only for a `ServerLevel`. They skip every client level. | holds | |
| 24 | 4 | `engine/WorkerPush.kt:22`, `compat/CobblebaseCompat.kt:22`, `:31`, `handler/vanilla/MuseBonus.kt:43`, `handler/create/PokemonHeat.kt:35` | The helpers that mixins call on both sides return on a client before they read the engine or the heat grants. So the client thread of a singleplayer game never reads server state. | holds | `skipsPokemon` also checks `isSameThread`. |
| 25 | 4 | `role/RoleRegistry.kt:29-41`, `:60-70`, `config/WorkEngineSettings.kt:5-6` | The role set and the engine settings are read-only snapshots in `@Volatile` fields, and the registry builds a new role set under a lock. The engine takes one whole snapshot per level tick. | holds | |
| 26 | 4 | `leash/LeashResets.kt:48-54` | The leash resets listen to three Cobblemon events. A search of the Cobblemon 1.8.4 jar finds their posts in the party store, a Cobblemon's change of store, evolution and the server's release handlers. | holds | Each of these runs on the server. |
| 27 | 5 | Inventory search 5, `CobblemonDynamics.kt:65` | No class outside `client/` and `mixin/cobblemon/client/` imports a client class. Common code names `DynamicsClient` only behind `FMLEnvironment.dist.isClient`. | holds | |
| 28 | 5 | `net/DynamicsPayloads.kt:30-41`, `client/ClientPayloads.kt:9-11`, `client/DynamicsClient.kt:10` | Common code reaches the client's payload handlers only through the `ClientHandlers` interface, which `DynamicsClient` installs on the client dist. A dedicated server keeps no handler. | holds | |
| 29 | 5 | `resources/cobblemon_dynamics.mixins.json:21-24` | The two Cobblemon GUI mixins sit in the `client` list, so Mixin applies them on the client dist only. | holds | |
| 30 | 5 | `handler/create/PokemonFanTypes.kt:25-49` | The fan processing types run on both sides. They name only Create's `FanProcessingType` and its nested `AirFlowParticleAccess`. Both sit in Create's common package and carry no `@OnlyIn` (`javap` on Create 6.0.10). | holds | |
| 31 | 5 | `java/mixin/create/`, `handler/create/FanWorkers.kt`, `handler/create/TailwindBonus.kt`, `handler/create/VirtualFlow.kt` | The Create mixins that apply on both sides, and the helpers that they call, name no client class. | holds | The test-pack `smoke` run starts a dedicated server with the mod. |
| 32 | 5 | `net/DynamicsPayloads.kt:14`, `:45` | No payload is optional, so a client without the mod cannot join (CD-PLAT-06). | holds | |
| 33 | 6 | `engine/EngineEvents.kt:35-43` | `ServerStoppedEvent` stops the engine. It also resets the toggle times, the panel viewers, the payload rate limits, the disabled handlers, the waiting messages and the timing recording. | holds | |
| 34 | 6 | `engine/WorkEngine.kt:68-71`, `:890-899`, `engine/Reach.kt:14-15` | The claims, the pasture runtimes, the worker slots and their claim memos live in the engine instance. `stop()` drops the instance, and the next server starts a new one. Each claim gets a new path check, so its `cannotPlan` flag lives for that claim only. | holds | |
| 35 | 6 | `handler/vanilla/EnchantingMenus.kt:28-29` | The open menus clear at logout and on `ServerStoppedEvent`. | holds | |
| 36 | 6 | `role/RoleRegistry.kt:20-30`, `:52-63` | The server stop turns every disabled handler on again. The parsed role files and the role set stay until the next data load, which runs before the next server starts. `logged` clears on each load. | holds | Each mixin warning logs once per run, because whether a mixin applied never changes. |
| 37 | 6 | `config/WorkEngineSettings.kt:6` | The settings snapshot stays until the next server's config loads. NeoForge loads the server config before `ServerAboutToStartEvent`, so before the engine starts. | holds | `ServerLifecycleHooks` in the NeoForge 21.1.265 sources. |
| 38 | 6 | `OnceLogger.kt:10` and each of its instances | Each warning logs once per cause and run. | holds | The plan keeps these: once per run is the intended semantic. |
| 39 | 6 | `fx/BedrockAnimations.kt:45-50`, `fx/ParticleCatalog.kt:35`, `java/MixinState.java:37-39`, `handler/HandlerRegistry.kt:8`, `compat/WorkerEvents.kt:16` | Caches of the animation and particle files of the Cobblemon jar, the record of which mixins applied, and the handler and listener tables that fill while the mod loads. | holds | None of them holds the state of a world or a server. |
| 40 | 6 | `Feedback.kt:11-13` | `recorded` fills only while `recording` is on, and only the GameTests turn it on. A real server leaves it empty. | holds | |
| 41 | 6 | `handler/create/PokemonHeat.kt:16`, `:26-29`, `handler/create/CreateCompat.kt:44` | `grants` is a static `WeakHashMap` of the heat that Working workers give Blaze Burners. No listener cleared it on `ServerStoppedEvent`, so it outlived the integrated server until garbage collection. Its keys are block entities that no later server shares, and every grant lapses after 2 ticks. So no state reached the next world, but the map broke the rule as written. `PokemonHeat.reset()` now clears it on `ServerStoppedEvent`. | **fix** | `CreateCompat.register` adds the listener, so the listener exists only with Create. No unit test: the fix changes no pure logic, and a unit test cannot build the Create block entities that key the map. A reader checks `PokemonHeat.reset()` and its listener in `CreateCompat.register`. Commit: "Audit the mod for multiplayer and fix what the audit found". |
| 42 | 6 | `handler/create/FanState.kt:15-24`, `handler/create/TailwindBonus.kt:14-19`, `handler/create/FanWorkers.kt:14-20`, `handler/TargetHandler.kt:96-98` | The Phase 2 state. The fan's catalyst and mode and the windmill's virtual sails are Create block entity behaviours. The fan worker mark is an unsaved entity attachment, and each claim gets a new `ClaimMemo`. | holds | None of it is static. |
| 43 | 6 | `client/DynamicsClient.kt:16-19`, `client/OverviewRequests.kt:11-15`, `client/PanelMixinCheck.kt:10` | When the client leaves a server, `LoggingOut` clears the panel state, the overview and the open panel. The overview request count resets on each tick that no player holds the leash. Each panel mixin warns once per game session. | holds | |
| 44 | 7 | `fx/EffectPlayer.kt:127-130`, `fx/Recipients.kt:11-12` | Each work, sleep and heal packet goes to the players that track the entity that it names, within `effectBroadcastRadius` (CD-FX-08). | holds | `getPlayersWatching` reads the tracker's `seenBy` set (the `ChunkMap` patch of NeoForge 21.1.265). |
| 45 | 7 | `fx/EffectPlayer.kt:146` | A worker particle also names the focus entity, which a player at the edge of tracking can lack. | holds | Cobblemon's particle handler then has no target, and it returns when the worker itself is missing (`javap` on Cobblemon 1.8.4). |
| 46 | 7 | `fx/EffectPlayer.kt:81-83` | When Cobblemon's data holds `cobblemon:floralhealing` (CD-HEAL-16), Cobblemon's own keyframes pick the recipients. Its particle keyframe sends to every player within 200 blocks of the healer or the ally. | holds | Cobblemon 1.8.4 has no such action effect, so the path cannot run inside the declared range `[1.8.4,1.9)`. Open item, ruling of 2026-11-11: see [Open item](#open-item). |
| 47 | 7 | `handler/create/FanState.kt:60-61` | A change of catalyst or mode calls `blockInFrontChanged()`. On its next server tick the fan rebuilds its air current and calls `sendData()`, which reaches every client that tracks the fan. | holds | `javap` on Create 6.0.10's `EncasedFanBlockEntity`. |
| 48 | 7 | `handler/create/TailwindBonus.kt:39-45`, `handler/create/DriveBehaviour.kt:22-27`, `:36-39` | A new Tailwind bonus updates the network speed and calls `sendData()`. A change of the drive's driven flag calls `sendData()`, and a flip calls `notifyUpdate()`. | holds | |
| 49 | 7 | `registry/DynamicsAttachments.kt:33-38`, `:49-53` | `stamina_view` and `fan_worker` sync to the clients that track the entity. NeoForge syncs on each `setData` and `removeData`, and when a player starts to track the entity. | holds | `AttachmentHolder` and the `ServerEntity` patch of NeoForge 21.1.265. |
| 50 | 7 | `pasture/WorkModeService.kt:50-58`, `net/PanelViewers.kt:44-62` | Each Work Mode change sends the new state to every viewer of that panel, each with its own `canToggle` (CD-SEC-06). Every viewer gets the state again every 20 ticks after its panel opened (CD-UI-05). | holds | |
| 51 | 7 | `handler/vanilla/EnchantingMenus.kt:63-66` | When a Muse starts or stops, every open menu of the table computes its offers again. | holds | |
| 52 | 7 | `Feedback.kt:18-40` | Messages go to the acting player, or by UUID to an owner or a binder. A chat message to an offline binder waits for the next login (CD-LSH-13). | holds | |
| 53 | 7 | `ai/BrainBridge.kt:88-92` | The sleep pose goes out through the synced `POSE_TYPE` entity data. | holds | |
| 54 | 8 | `pasture/WorkModePolicy.kt:19`, `net/PanelViewers.kt:87` | Only the placer or an operator may set Work Mode. A pasture with no placer takes operators only (CD-SEC-01, CD-SEC-02). Each viewer's `canToggle` uses the same rule. | holds | |
| 55 | 8 | `leash/LeashInteractions.kt:63`, `:85-90` | The leash selects or clears only a Cobblemon whose store names the clicking player as its owner (CD-SEC-07, CD-LSH-10). | holds | |
| 56 | 8 | `leash/LeashInteractions.kt:101`, `:269-270` | A bind finds the selected Cobblemon only in the clicking player's own PC and party. | holds | |
| 57 | 8 | `leash/LeashInteractions.kt:200-225`, `leash/BindRules.kt:94-100` | A target that another player's Cobblemon holds refuses the bind with that Cobblemon's species, never its nickname (CD-LSH-05). | holds | |
| 58 | 8 | `leash/FlipRules.kt:7`, `leash/LeashInteractions.kt:262-266` | A flip needs the worker's binder, the placer of the worker's pasture or an operator. A missing binder or placer matches no player (CD-SEC-10). | holds | |
| 59 | 8 | `leash/LeashResets.kt:116-119` | A Cobblemon that another player gains loses its binding, and the old binder gets the reason (CD-LSH-26). | holds | |
| 60 | 8 | `engine/WorkEngine.kt:314-318`, `:321-327` | The engine works only a Cobblemon that its owner tethered. The "cannot reach" message goes to the Cobblemon's owner (CD-LSH-21). | holds | |
| 61 | 8 | `engine/WorkEngine.kt:382-396` | Healers of a shared pasture heal the workers of every owner. | holds | The approver decided this. ARCHITECTURE section 5 and the player guide say so. |
| 62 | 8 | `command/DynamicsCommands.kt:104-118`, `leash/LeashResets.kt:85-99` | An operator clears every binding of a block, and each binder gets the reason (CD-UI-17). | holds | |

## Open item

Ruling of 2026-11-11 on row 46: no code changes now. On the Floral Healing path, Cobblemon's own keyframes pick the
recipients and can play sounds. So the path can break CD-FX-08, which sends effects only within 64 blocks, and CD-FX-09,
which keeps work silent. Before the dependency range admits a Cobblemon version with `floralhealing`, the path must send
only to players within 64 blocks and play no sound. The Phase 2 gate report will list this item as a known issue.

## The fixes of the Phase 1 review

The separate review of the Phase 1 code ran before M6, and PR #15 holds its nine fixes. Each fix still holds at this
branch tip.

| Phase 1 finding | Where the fix lives | Still holds |
| --- | --- | --- |
| 1. The pending Telekinesis source has no dimension. | `leash/PendingSource.kt:16-24`, `:27-45`, `leash/LeashInteractions.kt:171-194`, `leash/WorkLeashItem.kt:48-53`, `net/DynamicsPayloads.kt:20` | Yes. Both codecs carry the dimension. A source from another level counts as absent and leaves the stack. The second click checks that the source still matches the role. The payload version is 3. |
| 2. A handler that throws stays disabled for good. | `role/RoleRegistry.kt:52-63`, `:76-80`, `role/DisabledHandlers.kt`, `engine/EngineEvents.kt:40` | Yes. The disabled handlers clear on server stop and on each role file load, and each handler logs one warning. |
| 3. No rate limit on server-bound payloads. | `net/DynamicsPayloads.kt:22-27`, `:75-99`, `net/PlayerCooldown.kt`, `claim/LeashIndex.kt:35-36` | Yes. Overview requests and panel opens wait 10 ticks per player, and a refused toggle gets one reply per cooldown. The overview copies only the sender's entries. |
| 4. A binding can reserve a target forever. | `command/DynamicsCommands.kt:54-60`, `:104-118`, `leash/LeashResets.kt:85-113` | Yes. `/cobblemondynamics unbind <pos>` needs permission level 2 and tells each binder. The player guide names this remedy. |
| 5. A refused bind shows another player's nickname. | `leash/BindRules.kt:94-100`, `leash/LeashInteractions.kt:200-225` | Yes, row 57. |
| 6. `WorkModeService.lastAccepted` is never pruned. | `pasture/WorkModeService.kt:61-68`, `engine/EngineEvents.kt:47-52` | Yes. Logout forgets the player, and the server stop clears the map. |
| 7. An owner gets no message when a binding clears while the owner is offline. | `Feedback.kt:30-44`, `OfflineMessages.kt`, `engine/EngineEvents.kt:44-46` | Yes. Up to 10 messages wait for each player, arrive at the next login and clear on server stop. |
| 8. Effects go to the players within 64 blocks, not to the players that track the worker. | `fx/EffectPlayer.kt:127-130`, `fx/Recipients.kt:11-12` | Yes, rows 44 and 45. |
| 9. Common code names `ClientState`. | `net/DynamicsPayloads.kt:30-41`, `client/ClientPayloads.kt`, `client/DynamicsClient.kt:10` | Yes, row 28. |

The review also decided three points that needed only documents. Each still holds:

- Healers of a shared pasture heal the workers of every owner. ARCHITECTURE section 5 and the player guide say so.
- The toggle's dimension check depends on Cobblemon's link. Cobblemon 1.8.4's `getLinkByPlayer` drops the link in a level
  of another dimension type, or at 10.0 blocks or more (`javap`). ARCHITECTURE section 8 records it.
- A payload handler with no `executesOn` runs on the main thread. NeoForge 21.1.265's `PayloadRegistrar(String)` sets
  `HandlerThread.MAIN` (`javap`). ARCHITECTURE section 7 records it.

The review's list of code that was right still holds:

- server authority
- state by player UUID
- the toggle checks
- the leash's ownership checks from server state
- the split between the client and the server
- the resets on `ServerStoppedEvent`, with the fix of row 41
- claims keyed by dimension and position
- the trade reset
- commands from the console

## Rulings of 2026-11-11

1. Row 41, `PokemonHeat.grants`: fix it as planned, with `PokemonHeat.reset()` from a `ServerStoppedEvent` listener that
   `CreateCompat.register` registers. Done.
2. Row 18, the written pending source: a client in creative mode stays trusted with item data, as vanilla trusts it, so the
   pending source stays on the stack. The destination click checks the source before it reads the source block, and never
   loads a chunk. Done, with a unit test and one GameTest.
3. Row 46, Floral Healing: no code change now. The [Open item](#open-item) section records the condition.
4. Row 13, the toggle at exactly 10.0 blocks: holds. It is stricter than CD-SEC-04, never looser, and the decisions of the
   development notes already record it.
