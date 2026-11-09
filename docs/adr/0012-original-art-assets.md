# ADR-0012: Original art assets

Status: Accepted (2026-11-09)

## Context

The mod adds four bed blocks, the Work Leash item, UI sprites for the pasture panel and head bars, and a
mod icon. The operator ruled that the first release includes our own art and models for each bed tier
and each new block.

The art cannot come from the mods that we integrate with:

- Cobblemon's 530 model folders carry a Creative Commons NonCommercial license.
- Create's assets are All Rights Reserved.
- AE2's textures and models are CC BY-NC-SA 3.0.
- Mojang's textures are not ours to copy.

The operator chose these options: Claude drafts every asset and the operator approves each one, in the
vanilla 16×16 style.

## Decision

- **Assets in the first release:**
  - Four bed tiers, each with a block model, textures and an item model.
  - The Work Leash: a lead with a red-and-white clasp.
  - UI sprites: the Work Mode button in 4 states (on, off, disabled, hover), the stamina bar, and 5
    state icons (Working, Standby, Tired, Sleeping, Healing).
  - The mod icon and the project icon: a cog with a lightning bolt above a fence post. It has no Pokémon
    likeness.
- **Bed concepts:**
  - Hay Bed: a round straw nest.
  - Wool Bed: a round wool cushion on a low wooden frame.
  - Plush Bed: a thick velvet cushion with gold trim.
  - Starlit Bed: a midnight-blue cushion with a gold star pattern, a faint glow and an occasional
    sparkle.
- **Rules:**
  - Every asset is original. No pixel comes from Mojang, Cobblemon, Create or AE2.
  - Textures use the vanilla 16×16 style. Block models use a format that Blockbench can open.
  - Source files live under `art/` in the repository, under the repository's MPL-2.0 license.
  - Claude drafts each asset. The operator approves each one on a screenshot review page before the
    public release.
  - Private builds may use placeholder art.

## Consequences

- The art sits on the M8 milestone, before the public release. It does not block the private tests.
- Each asset needs a few review rounds.
- The beds read as one family across tiers, so players can tell the tier at a glance.
- MPL-2.0 covers the art as well as the code, so a pack can redistribute both together.

## Alternatives considered

- **An artist makes the finals.** Higher quality, but it adds a dependency on another person's schedule.
- **A hybrid.** Claude builds the models and first-pass textures, and an artist repaints them. It keeps
  part of the same dependency.
- **32×32 textures.** Room for detail on the Plush and Starlit beds, but they look out of place next to
  vanilla and Create blocks.
