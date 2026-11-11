# Manual play-test checklist

This checklist holds the checks that need a person. It covers each `Demo:` test of the Phase 1 rows in the
[spec sheet](../plans/2026-11-09-v1-spec-sheet.md) (CD-REL-09). It also covers the real-restart checks and the
play-test checks that the end-of-phase pass of
[M5 Task 9](../plans/2026-11-09-phase-1-m5-ui-compat-gate.md#task-9-phase-1-gate) and the
[rehydration note](../plans/2026-11-09-phase-1-rehydration.md#carry-over-items) list.

Each check names its spec row, the setup, the action and the expected result. If the expected result does
not happen, the check fails. In a doubtful case, the row's "Fails if" text in the spec sheet decides. Record
each result in the play-test note of the gate report, `docs/testing/phase-1-gate.md`: pass, fail with what
you saw, or blocked with the reason.

Marks after the row id:

- **(scripted):** a test pack command runs the check and prints the result.
- **(server):** the check needs the [server](#server-for-the-server-checks).
- **(2 players):** the check needs the server and a second player.
- **(restart):** the check needs a real restart. In single player, click **Save and Quit to Title**, then open
  the world again. On the server, run `stop` and start it again. You can set up several restart checks and
  restart once.

## Play-test setup

### Client

1. Set `JAVA_HOME` to a JDK 21, then run `./gradlew build`.
2. Make sure that `build/libs/` holds only the jar of this build. Delete a jar that a `-Prelease_version`
   build left there, because the test pack copies the newest jar.
3. Run `py scripts/testpack/testpack.py client`. It fills `run/testpack/client/mods/` with the pinned
   Cobblemon, Create, AE2, GuideME and Kotlin for Forge jars and the built jar. It also names the NeoForge
   version for the instance.
4. In a launcher, make a clean NeoForge 21.1.265 instance. Never use an existing modpack instance.
5. Copy the files of `run/testpack/client/mods/` into the `mods/` folder of the instance.
6. If the game folder of the instance is new, write the line `onboardAccessibility:false` into its
   `options.txt` before the first start, as `run/client/options.txt` does. Otherwise the game first opens the
   accessibility onboarding screen, which you can also click through.
7. Start the game. Make a creative flat world with cheats on.
8. Run `/gamerule doPokemonSpawning false`, so that no wild Pokémon walk into the test area.
9. Give yourself Pokémon with `/pokegive <species> level=<n>`, for example `/pokegive charmander level=1`.
   Put them in a pasture through its panel.

### Optional mods for the compatibility checks

`run/testpack/cache/` holds these pinned jars. If one is missing, run `py scripts/testpack/testpack.py compat`
first, which downloads them.

| Mod | File |
| --- | --- |
| Cobbleworkers 2.0.7 | `cobbleworkers-neoforge-2.0.7+1.8.3.jar` |
| Cobblebase 2.0.1 | `cobblebase-neoforge-2.0.1+1.8.0.jar` |
| Cloth Config 15.0.140 | `cloth-config-15.0.140-neoforge.jar`. Cobblebase needs it at run time, but does not declare it. |

Add only the mods that a check names. Take them out of the instance after the check.

### Server for the server checks

1. Run `py scripts/testpack/testpack.py smoke`. It leaves a server with the pinned mods and the built jar in
   `run/testpack/smoke/`.
2. In `run/testpack/smoke/`, start the server:
   `"$JAVA_HOME/bin/java" -Xmx3G @user_jvm_args.txt @libraries/net/neoforged/neoforge/21.1.265/win_args.txt nogui`.
   On Linux, use `unix_args.txt`.
3. Join `127.0.0.1:<port>`, where `<port>` is the `server-port` value in `run/testpack/smoke/server.properties`.
4. The server runs with `online-mode=false`, so each client needs its own player name.
5. In the server console, run `op <name>` for the player who places the pastures. The second player stays a
   normal player.

The next run of the test pack deletes `run/testpack/smoke/`. Copy the folder first if you want to keep its
world.

### Useful facts

- With the default config, a level 1 worker is Tired after 3 minutes of Working. It loses 1% of Work
  Stamina about every 2 seconds. Use level 1 workers for the checks that need a Tired worker.
- `/cobblemondynamics stamina @e[type=cobblemon:pokemon,sort=nearest,limit=1]` shows the Work Stamina, the
  state and the recovery rule of the nearest Pokémon. `/cobblemondynamics claims` lists the claims and the
  bindings.
- The game shows the state ToBed as "To Bed", and RestingInPlace as "Resting in Place".
- Give a furnace 64 raw iron with `/give @s raw_iron 64`. It then cooks for more than 10 minutes.

## Platform

1. **CD-PLAT-01** (scripted). Setup: the built jar. Action: run `py scripts/testpack/testpack.py deps`.
   Expected: `CASE neoforge-floor: PASS` (NeoForge 21.1.219 loads the mod), `CASE neoforge-below-floor: PASS`
   (FML rejects NeoForge 21.1.218) and `DEPS PASS`.
2. **CD-PLAT-02** (scripted). Setup: the `deps` run of check 1, and `py scripts/testpack/testpack.py smoke`.
   Action: read the output. Expected: `CASE cobblemon-below-floor: PASS` (FML rejects Cobblemon 1.8.3), and
   `SMOKE PASS` with the pinned Cobblemon 1.8.4.
3. **CD-PLAT-03** (scripted). Setup: the `deps` run of check 1. Action: read the output. Expected:
   `CASE kff-floor: PASS` (Kotlin for Forge 5.10.0 loads the mod).
4. **CD-PLAT-05** (scripted). Setup: the `deps` run of check 1. Action: read the output. Expected:
   `CASE create-below-floor: PASS` (FML rejects Create 6.0.9), `CASE ae2-below-floor: PASS` (FML rejects AE2
   19.1.3-beta) and `CASE without-create-ae2: PASS` (the mod loads with neither mod).
5. **CD-PLAT-06** (server). Setup: the server, and the play-test instance with `cobblemon_dynamics-*.jar`
   taken out of its `mods/` folder. Action: join the server. Expected: NeoForge refuses the client with its
   message about the missing mod, and the client never enters the world. Put the jar back afterward.

## Work Mode, assignment and the pasture panel

6. **CD-UI-01.** Setup: a pasture that you placed. Action: open its panel. Expected: a Work Mode button sits
   directly above the Recall button, and it overlaps no other control.
7. **CD-UI-02.** Setup: two pastures, one with Work Mode on and one with Work Mode off. Action: open each
   panel, then click one of the buttons. Expected: within 1 second of each opening, the buttons read
   "Work Mode: On" and "Work Mode: Off", as set. After the click, the label changes within 1 second.
8. **CD-UI-03** (2 players). Setup: a pasture that player A placed. Player B is not an operator. Action: as
   player B, open the pasture's panel, hover over the Work Mode button and click it. Expected: the button is
   disabled, the click changes nothing, and the tooltip reads "Only the pasture's owner can change Work Mode".
9. **CD-UI-04.** Setup: a Work Mode pasture with 4 workers in mixed states. A Charmander works at a furnace
   with 64 raw iron (Working). A second Charmander waits at an empty furnace (Standby). A Squirtle has no
   target (Idle), and a Clefairy has nobody to heal (Idle). Action: open the panel. Expected: each of the 4
   rows shows a Work Stamina bar and a state icon.
10. **CD-UI-07.** Setup: a Work Mode pasture with three level 1 Charmanders and a Squirtle. Its work area
    holds one Hay Bed, two furnaces with 64 raw iron and one empty furnace. Action: hold the Work Leash and
    watch the icons in the panel and over the heads for 5 minutes. When two Charmanders are Tired, pasture a
    level 30 Clefairy. Expected: Idle (the Squirtle), Traveling, Working, Standby (at the empty furnace),
    Sleeping (on the Hay Bed) and Healing (the Clefairy) each show their own icon. ToBed and RestingInPlace
    show the Tired icon. No state shows a wrong icon or no icon, and no two of the seven icons look alike.
11. **CD-UI-08.** Setup: the seven state icons in the panel, as in check 10. Action: turn on a grayscale
    filter, for example the Windows color filter (Settings, Accessibility, Color filters, Grayscale), and
    look at the icons. Expected: no two icons look the same.
12. **CD-UI-10.** Setup: a level 1 Charmander Working at a furnace with 64 raw iron. Action: open the panel
    and hold the mouse over the Charmander's bar until the tooltip reads 42%. Expected: the tooltip shows
    "42% Work Stamina" and the state name "Working".
13. **CD-CLM-07** (restart). Setup: two Charmanders Working at two furnaces, with no other Fire-type target in
    the work area. `/cobblemondynamics claims` lists two `claim work` lines. Action: click **Save and Quit to
    Title**. In the instance's `config/cobblemon_dynamics-server.toml`, set `disabled =
    ["cobblemon_dynamics:stoker"]` under `[roles]`. Open the world, wait 5 seconds and run
    `/cobblemondynamics claims`. Set `disabled = []` again afterward. Expected: both furnaces go out, which
    shows that the Stoker is off. The reply holds no `claim work` line. With the Stoker off, no new work claim
    can come, so a work claim in the reply would be one that the world saved.
14. **CD-ASG-09** (carry-over check). Setup: a Work Mode pasture with a wide Fire-type, for example a
    Camerupt. Its furnace, with input, stands at the end of a corridor one block wide, so that each work spot
    has walls beside it. Action: watch the worker for 30 seconds. Expected: no part of its body sits inside a
    block. If it does, record the species and the position.
15. **CD-ASG-14** (carry-over check). Setup: a Work Mode pasture with 8 Charmanders, and 8 furnaces with input
    in a ring 3 blocks from the pasture block. Action: watch the workers for 1 minute. Expected: within 30
    seconds, every Charmander reaches a furnace and is Working. No two workers push each other back and forth
    until the travel timeout, and no Charmander is Idle after 1 minute.

## Work Leash

16. **CD-LSH-02.** Setup: a rideable Pokémon of yours, for example a Charizard, in a Work Mode pasture.
    Action: with the Work Leash in your main hand, right-click the Charizard. Then sneak and right-click it.
    Expected: the plain click shows "Selected Charizard" in the action bar. Neither click opens Cobblemon's
    interaction wheel, and neither click mounts the Charizard.
17. **CD-LSH-04.** Setup: a Charmander in a Work Mode pasture and a furnace in its work area. Action: select
    the Charmander with the Work Leash, then right-click the furnace. Expected: the action bar reads "Bound
    Charmander to Furnace", and the furnace screen does not open.
18. **CD-LSH-06.** Setup: a Vaporeon in a Work Mode pasture, and a Create water wheel with no water in its work
    area. Action: select the Vaporeon with the Work Leash, then right-click the wheel. Expected: the action bar
    reads "Bound Vaporeon to Water Wheel".
19. **CD-LSH-13.** Setup: in a Work Mode pasture, an Eevee bound to a Create hand crank, and a Charmander
    bound to a furnace. Action: right-click the Eevee with a Water Stone, so that it evolves into a Vaporeon.
    Then break the Charmander's furnace. Expected: the chat shows "Vaporeon lost its work binding: its new
    form cannot do the job". Then it shows "Charmander lost its work binding: its target was broken or
    replaced".
20. **CD-LSH-20** (Phase 2). Setup: an Alakazam in a Work Mode pasture, and a chest in its work area. Action:
    select the Alakazam with the Work Leash, then right-click the chest. Expected: the action bar reads
    "Select a destination for Alakazam". The check needs the Telekinesis role, which arrives in M6. Record it
    as blocked in the Phase 1 play-test. In Phase 1, its GameTest with the test role
    `cobblemon_dynamics:test_transfer` is the evidence.
21. **CD-LSH-21.** Setup: a Charmander in a Work Mode pasture, and a furnace with input in its work area. Build
    a ring of blocks two high around the furnace, one block away from it. The floor beside the furnace stays
    open, but no path leads to it. Action: bind the Charmander to the furnace and wait 31 seconds. Expected:
    within 601 ticks (30 seconds) of the bind, the action bar reads "Charmander cannot reach Furnace".
22. **CD-UI-06.** Setup: a worker Working at its target. Read its position `<x> <y> <z>` with
    `/data get entity @e[type=cobblemon:pokemon,sort=nearest,limit=1] Pos`. Action: hold the Work Leash, and
    teleport to `<x + 16> <y> <z>` with `/tp @s`. Then move 0.1 blocks farther with `/tp @s ~0.1 ~ ~`. Then
    select another item. Expected: the bar and the icon show over the worker at 16.0 blocks. They do not show
    at 16.1 blocks, and they do not show after the item switch.
23. **CD-UI-09** (2 players). Setup: player A binds two workers to two furnaces. Player B places a pasture,
    turns Work Mode on and binds a worker to a third furnace nearby. Action: player A holds the Work Leash and
    looks at the three furnaces. Expected: player A sees an outline around each of the two furnaces of player
    A, and none around the furnace of player B.
24. **CD-LSH-07** (restart). Setup: a Charmander bound to a furnace. Action: restart. Wait 5 seconds and run
    `/cobblemondynamics claims`. Then turn Work Mode off and on in the panel. Wait 5 seconds and run the
    command again. Expected: both replies hold a `binding` line with the furnace's position and the
    Charmander.

## Work Stamina and beds

25. **CD-FX-10.** Setup: a Work Mode pasture with one Hay Bed, and two level 1 Charmanders Working at two
    furnaces with 64 raw iron. After 3 minutes, both are Tired: one sleeps on the bed, and one rests in place.
    Action: watch and listen to both for 20 seconds (400 ticks). Expected: Cobblemon's sleep particles show on
    both workers every 2 seconds, and no sound comes from them.
26. **CD-STA-04** (restart). Setup: a level 5 Charmander that has Worked for 2 minutes, at about 87%. Move it
    to the PC and back into the pasture. Then read its value V with `/cobblemondynamics stamina`. Action:
    restart, and read the value again within 10 seconds of the world loading. Expected: the value is within 2
    points of V. It is not 100%.
27. **CD-BED-14** (restart). Setup: a level 1 worker that is Tired and sleeps on a Hay Bed. It sleeps for about
    90 seconds. Action: restart while it sleeps. Expected: within 2 seconds (40 ticks) of the world loading,
    the worker lies on a free bed again, and its icon shows Sleeping.

## Healers

28. **CD-HEAL-15.** Setup: a Work Mode pasture with a level 30 Clefairy and a level 1 Charmander Working at a
    furnace with 64 raw iron. Action: watch until the Clefairy heals the Charmander. The heal starts when the
    Charmander drops under 25%, after about 2 minutes. Expected: when the Clefairy reaches the Charmander, it
    plays its special pose. Heal circles show on the Charmander, and its bar goes to 100%. No sound plays,
    and the client does not crash.

## Stoker

29. **CD-STK-07.** Setup: a Charmander Working at a furnace with 64 raw iron and an empty fuel slot. Action:
    with an empty hand, open the furnace screen and watch it for 10 seconds. Expected: the flame gauge is full
    while the furnace cooks, never empty, and the fuel slot stays empty.

## Wheel Driver

30. **CD-FX-04.** Setup: a Water-type Working on a Create water wheel with no water. Action: watch the worker
    for 10 seconds. Expected: the Water Gun spray goes from the worker to the axle of the wheel.
31. **CD-FX-05.** Setup: as in check 30. Action: look at the axle of the wheel, first as normal, then with
    hitboxes shown (F3+B). Expected: nothing renders at the axle in either view.
32. **CD-WHL-09.** Setup: as in check 30, with a small water wheel. Action: wear Create's Engineer's Goggles
    and look at the wheel. Expected: the goggle tooltip shows a stress capacity of 256 SU.
33. **CD-WHL-07** (restart). Setup: a Vaporeon bound to a small water wheel with no water, and Working. Sneak
    and right-click the wheel once with the Work Leash. The wheel turns the other way, and
    `/data get block <x> <y> <z> cd_direction` on the wheel reads -1. Action: restart. Expected: after the
    restart, the wheel turns the same way as before the restart, and the command reads -1 again.

## Cranker

34. **CD-CRK-08** (restart). Setup: a Machop bound to a Create hand crank, and Working. Sneak and right-click
    the crank once with the Work Leash. The crank turns the other way, and
    `/data get block <x> <y> <z> cd_direction` on the crank reads -1. Action: restart. Expected: after the
    restart, the crank turns the same way as before the restart, and the command reads -1 again.

## Work effects

35. **CD-FX-09.** Setup: a Machop and an Eevee Working at two Create hand cranks, a Vaporeon Working at a
    water wheel with no water, and 2 Charmanders Working at 2 furnaces with input. Action: listen for 10
    seconds. Expected: no sound comes from the workers' effects. The Machop, the Eevee and the Vaporeon play no
    animation, and their particles show every 2 seconds. A Pokémon's own ambient cry, which Cobblemon plays
    about once a minute, is not a work effect.

## Compatibility

36. **CD-CMP-01.** Setup: the play-test client with the Cobbleworkers jar added. A Work Mode pasture with a
    Charmander Working at a furnace with 64 raw iron. A second furnace with raw iron and no fuel stands 4
    blocks away. Action: watch for 60 seconds (1,200 ticks). Expected: the Charmander stays at the furnace that
    it works, and it keeps Working. Cobbleworkers never sends it to the other furnace, which stays unlit.
37. **CD-CMP-03.** Setup: the play-test client with the Cobblebase and Cloth Config jars added. A Work Mode
    pasture with a Water-type Working on a Create water wheel on dry land. A pool of water lies 8 blocks away.
    Action: watch for 60 seconds (1,200 ticks). Then run `/time set 14000`. Break the wheel and place it again
    5 blocks away, inside the work area. Expected: Cobblebase never moves the worker into the water. At night,
    the worker walks to the new wheel within 30 seconds and works it, so Cobblebase does not freeze it.
38. **CD-CMP-04.** Setup: the play-test client with the Cobblebase and Cloth Config jars added. A pasture with
    Work Mode off, with a Fire-type next to a furnace with input. With Cobblebase's own button in the pasture
    panel, give the Fire-type a furnace fuel job. Action: watch for 60 seconds. Expected: the Cobblebase job
    runs as usual: the Fire-type fuels the furnace, and the furnace lights.
39. **CD-CMP-05.** Setup: the play-test client with Cloth Config and Cobblebase 1.5.4 in place of Cobblebase
    2.0.1. Get `cobblebase-neoforge-1.5.4+1.7.0.jar` from <https://modrinth.com/mod/cobblebase/version/arXwBcAs>.
    Cobblebase 1.5.4 has no `NavigationHelper.findNearbyWaterCached`, which one hook targets. Action: start
    the game, open a world with a Work Mode pasture and play for 2 minutes. Then search `logs/latest.log`
    for `Cobblebase hooks`. Expected: the game runs. The log holds this line once, and it does not repeat:
    "Cobblebase hooks [waterTeleport] did not apply, so Cobblebase can still move or freeze the workers of
    Work Mode pastures". If the game fails with an error that names only Cobblebase classes, record the check
    as blocked. Cobblebase 1.5.4 itself then fails on Cobblemon 1.8.4.

## Release

40. **CD-REL-01.** Setup: a branch from `main` with one unit test that fails, for example `assertTrue(false)`
    in a new test. Action: open a draft pull request into `main`. Expected: the build check of the pull request
    fails. Then close the pull request and delete the branch. The check costs one CI run.
41. **CD-REL-12** (at the gate, after the approver's go-ahead). Setup: the commit that the gate report names,
    after the M5 merge. Action: follow the steps of the CD-REL-12 demo in M5 Task 9: push `v1.0.0-alpha.1`,
    then `v1.0.0-beta.1`. Expected: for each tag, the `release` run shows `Build the release jar` and
    `GitHub release` as `success`, and `Modrinth and CurseForge` as `skipped`. The GitHub release is a
    pre-release with the jar, and neither site lists the version.
42. **CD-REL-04** (at the public release, M9). Setup: the Phase 2 gate has passed (CD-REL-14). Action: push the
    tag `v1.0.0`. Expected: after the `release` run ends, Modrinth and CurseForge both list the version
    `1.0.0+mc1.21.1`. This check is not part of the Phase 1 play-test.

## Play-test fixes (2026-11-10)

These checks repeat the findings of the approver's first play-test after their fixes.

43. **CD-FX-01.** Setup: a Charmander Working at a furnace with 64 raw iron. Action: watch the Charmander for
    20 seconds. Expected: it plays its whole special animation, then stands in its idle pose for about 2
    seconds before the next one. No animation starts again before it ends.
44. **CD-ASG-09.** Setup: a Work Mode pasture with a Charmander, and a furnace with 64 raw iron on the floor 5
    blocks from the pasture block, with open floor around it. Action: watch the Charmander until it Works.
    Expected: it stands on the floor beside the furnace, not on top of it and not several blocks away.

## Approver rulings (2026-11-10)

These checks show the changes that the approver ruled after the Phase 1 play-test.

45. **CD-BRN-01.** Setup: a Work Mode pasture with a level 10 Dratini, and a Blaze Burner with a blaze, no fuel and a
    basin on top, 4 blocks from the pasture block. Action: watch the Dratini until it Works. Expected: the Dratini works
    the burner with Dragon Breath, and the burner burns with kindled flames, not with the blue flames of superheat.
46. **CD-SUP-02.** Setup: as in check 45, with a level 10 Charmander in place of the Dratini, and no furnace or brewing
    stand in the work area. Action: watch for 30 seconds. Then select the Charmander with the Work Leash and click the
    burner. Expected: the Charmander never walks to the burner, and the click shows "No role fits".
