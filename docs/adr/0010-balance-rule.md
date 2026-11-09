# ADR-0010: Balance rule

Status: Accepted (2026-11-09)

## Context

The operator set a balance rule that covers nearly every role. In the operator's words, a single
Pokémon "should NOT be able to solve an engineering puzzle that the player was meant to manage,
entirely." The mod must never be "the cheap way out and beats every other option, even at endgame."

The operator's benchmark is the Extreme Reactors mod. A small 6×6×6 reactor makes about 15,000 FE/t at
most (operator figure). A level-100 Electric-type makes 5,000 FE/t, and a level-100 legendary or mythical
makes 10,000 FE/t. A player is unlikely to own many such Pokémon, so matching a reactor takes a fleet.
Even 5 or more max-level legendaries must never reach the output of a very large automated fusion
reactor.

The operator also named the exception. When the machine itself caps the output, the Pokémon's output
matters less. A water wheel and a windmill cannot spin without limit. A level-100 Pokémon on a windmill
is almost wasted, because real wind and more windmills scale better.

Leveling takes time, so it is a choice: level Pokémon and use them, build a real power source, or put
spare Pokémon to work in the meantime.

## Decision

- **The rule.** No single Pokémon solves an engineering problem that the player is meant to manage.
  Pokémon are a strong early-game and midgame option. A fleet of them can compete. Top-tier engineering,
  such as large reactors and AE2 automation, always does better.
- **Exempt roles.** Roles whose machine caps the output: the Wheel Driver, the Cranker, Tailwind and the
  Fan Catalyst. Tailwind still scales with the windmill's real sails, so building sails stays the main
  lever.
- **Rules that follow from it:**
  - The Generator tops out at 5,000 FE/t, or 10,000 FE/t for a legendary or mythical Pokémon, below the
    benchmark reactor.
  - The Stoker, Burner Keeper and Brew Stoker give free fuel only, at vanilla or Create speed, one
    machine per worker.
  - The Superheater superheats only from level 50. Below level 50, a Dragon-type keeps a burner Kindled.
  - The Crystal Tender equals one AE2 Growth Accelerator, which is weaker than a powered accelerator
    array.
  - The Brewer and the Muse are small or capped by vanilla (2× on one stand, at most 15 bookshelves).
  - The operator approved the transfer rates of the Pump, Telekinesis and the Magnet as proposed.
  - Every worker is limited by Cobblemon's 16 Pokémon per pasture and by Work Stamina. Without a healer,
    uptime is 50% to 89% ([ADR-0008](0008-work-stamina-model.md)).
- **New roles.** Each new role states what caps its output and checks it against this rule before it is
  agreed. The spec sheet's BAL rows hold the checks.

## Consequences

- Built-in numbers are deliberately below the best engineering answer at each tier.
- A pack maker can change any role number ([ADR-0006](0006-data-driven-roles.md)). The built-in
  defaults follow this rule, and a pack that raises them owns that choice.
- A design review of a new role needs a named cap and a named benchmark.

## Alternatives considered

- **Natural-source parity only.** The earlier rule copied the numbers of the source that a role
  replaces. It said nothing about scale, so a cheap Pokémon could replace a whole supply chain, such as
  the blaze-cake chain for superheated burners.
- **Hard global caps per pasture.** Simple, but it would punish the machine-capped roles that need no
  cap.
