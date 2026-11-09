# ADR-0001: Pasture Work Mode instead of a Work Stand

Status: Accepted (2026-11-09)

## Context

The first draft of the idea had a new block, the Cobblemon Work Stand. A player assigns a Pokémon to
the stand. The Pokémon stands on top of the block, cannot move, and only turns to face a machine
next to the stand.

The operator prefers to drop the Work Stand. The goal is a Palworld-like base: Pokémon roam a work
site and walk to the jobs that need them. Cobblemon already has a block for this. The Pasture Block
links up to 16 Pokémon from the PC to the world. They roam within ±32 blocks of the block
(`pastureMaxWanderDistance`), and they stay in PC storage while they roam.

The operator also set these rules:

- A pasture becomes a work site through a toggle in the pasture block's own interface.
- If a pasture has work in its area that no other Pokémon holds, its Pokémon do that work.
- Only one Pokémon acts on a given block or entity, across all pastures.
- Wild Pokémon never work.
- A player can assign a specific Pokémon to a specific machine with an item, the Work Leash.

## Decision

- The mod adds no Work Stand.
- Each Cobblemon Pasture Block gets a Work Mode toggle, shown as a button in the pasture panel. Only
  the pasture's recorded placer (Cobblemon's `ownerId`) or an operator can switch it. A pasture with
  no recorded placer accepts the toggle from operators only.
- While Work Mode is on, the pasture's Pokémon are workers. Each worker takes the nearest unclaimed
  target of an auto-assign role it can do, inside the pasture's work area. The work area defaults to
  8 blocks horizontal and 6 blocks vertical. Each value can range from 2 to 32 and never exceeds
  Cobblemon's `pastureMaxWanderDistance`.
- The Work Leash binds one worker to one target with two clicks: right-click the Pokémon, then
  right-click the target. Telekinesis adds a third click for the destination
  ([ADR-0011](0011-v2-roles-in-first-release.md)). Sneak + right-click on the Pokémon clears the
  binding.

## Consequences

- No new block competes with the Pasture Block, and players keep a familiar Cobblemon workflow.
- Pokémon must path to their targets, so the mod needs AI control ([ADR-0004](0004-brain-activity-for-worker-control.md))
  and travel timeouts.
- The mod depends on Cobblemon pasture internals, which have no API and fire no events. The
  `pasture` package isolates every such read.
- Cobblemon records a pasture's placer but never enforces ownership. Our Work Mode toggle enforces
  it for the toggle only.
- Players place pastures with intent, because the work area is small compared with the roam box.

## Alternatives considered

- **Work Stand block.** Simple and robust, with no pathing and no conflict with Cobblemon's AI. The
  operator rejected it, because a static Pokémon does not match the Palworld-style base.
- **Our own pasture-like block.** Full control over storage, UI and AI. It would duplicate Cobblemon's
  pasture and confuse players who already use pastures.
- **Work everywhere, no toggle.** Every pastured Pokémon would work. Players could not keep a pasture
  for breeding or display only, and it would clash with other pasture mods in every pasture.
