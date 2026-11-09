# ADR-0011: v2 roles in the first release

Status: Accepted (2026-11-09)

## Context

The first catalog put four roles on a v2 roadmap: Telekinesis (Psychic), Fan Catalyst (Fire, Water,
Ghost), Tailwind (Flying) and Magnet (Steel). The operator then moved them into the first release, in
phases: test v1 privately, then build v2, then make the mod public and add it to the operator's pack.

These four roles move items or change how a machine behaves:

- Telekinesis reroutes items between two containers.
- A Fan Catalyst starts processing on a Create encased fan.
- Tailwind speeds up a Create windmill and every machine on its network.
- The Magnet pulls dropped items into a container.

Research facts (see the integration-points note):

- Create's windmill speed is the sail count ÷ 8 RPM, capped at 16 RPM (128 sails), with 512 SU per RPM.
  A mixin can add virtual sails in `getGeneratedSpeed()`. A windmill still needs 8 real sails to assemble.
- An encased fan finds its catalyst along the air current and covers every block downstream. A mixin on
  `findEntities` can keep the fan from pushing or harming its own worker. Our own fan processing type
  must exist on both sides, because the client rebuilds the air current itself.
- NeoForge item handlers cover chests, barrels, hoppers, Create vaults, depots and chutes. The AE2 ME
  Interface, block and cable part, accepts items into a 9-slot buffer that it sends to the network when
  it has a channel and power.

## Decision

- **Scope.** Version 1.0 ships all 16 roles. Phase 2 builds the four roles after the Phase 1 test gate.
- **Phases.**
  1. Phase 1, private: milestones M0 to M5 build the 12 v1 roles. The gate: every v1 MUST row passes, and
     the operator tests the mod in a private world.
  2. Phase 2, private: M6 builds Telekinesis and the Magnet. M7 builds the Fan Catalyst and Tailwind. M8
     makes the final art, and the operator reviews it. The gate: every v2 MUST row passes, and the
     operator tests again.
  3. Phase 3, public: M9 publishes to Modrinth and CurseForge and adds the mod to the operator's pack.
- **Leash only.** None of the four roles auto-assigns. A player binds each worker with the Work Leash, so
  Pokémon never reroute items, start processing on a fan, or speed up a windmill on their own.
- **Two-point leash for Telekinesis.**
  1. Right-click the Psychic-type Pokémon.
  2. Right-click the source container.
  3. Right-click the destination container.
  - The binding remembers the faces that the player clicked, and transfers use those faces.
  - A pending selection expires after 30 s, or when the player right-clicks air.
  - The binding reserves the source only. Any number of workers can feed one destination, for example a
    single AE2 ME Interface.
  - Only the source must sit inside the pasture's work area. The destination may sit anywhere within
    32 blocks of the pasture block on each axis (the operator's ruling of 2026-11-09). A destination
    at 33 blocks is refused with a message.
- **Rates.**
  - Telekinesis and the Magnet: ceil(64 × level ÷ 100) items per second (Lv1 1, Lv50 32, Lv100 64).
  - Tailwind: floor(real sails × level ÷ 100) virtual sails, capped at 128 sails in total.
- **Fire Fan Catalyst mode.** Blasting by default. Sneak + right-click on the fan with the Work Leash
  switches that fan to smoking, the same gesture that flips a wheel's direction.

## Consequences

- The public release comes later, but it is complete.
- Two private test gates catch v1 problems before the v2 work builds on them.
- Phase 2 adds three Create mixins: the fan's `findEntities`, the nozzle push, and the windmill speed.
  It also adds our own fan processing types.
- The Work Leash gains a third click and a pending-selection timeout.

## Alternatives considered

- **Ship v1, then v2 as an update.** An earlier public release, but packs would get the mod in two big
  steps, and the operator wants one complete release for the pack.
- **Auto-assign Tailwind and fans.** Less clicking, but a Pokémon could start processing on a fan or
  speed up a network that nobody asked it to.
