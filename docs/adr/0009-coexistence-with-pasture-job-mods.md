# ADR-0009: Coexistence with pasture-job mods

Status: Accepted (2026-11-09)

## Context

Modpacks often install Cobbleworkers (689,000 downloads) or Cobblebase (97,000 downloads). Both act on
every pastured Cobblemon, so both conflict with our workers:

- **Cobbleworkers 2.0.7** assigns jobs by type. For example, Fire-types fuel furnaces and fill lava,
  and Water-types irrigate, extinguish, fill cauldrons and fish. It sends a new path at most every 20
  ticks, which fights our navigation. It does not know our claims, so both mods may fuel the same
  furnace (inferred). It has a per-Cobblemon rest flag, `cobbleworkers.resting`, in
  `Pokemon.persistentData`. Resting Cobblemon skip every job. The key is undocumented.
- **Cobblebase 2.0.1** treats every Cobblemon it has not assigned as idle. Idle Cobblemon get ambient
  behaviour: they sleep at night, and a mixin into `PokemonEntity.tick` wipes their Brain targets and
  pins their position. It teleports Water-types on land into water every tick, which breaks a Water
  Gun worker that stands beside a wheel. It also teleports Cobblemon back when they stray. It has no
  per-pasture switch and no ambient switch.
- Both inject at the tail of Cobblemon's `PokemonPastureBlockEntity.TICKER$lambda$0`, a
  compiler-generated name. Cobblebase broke on Cobblemon 1.8 because of hooks like this (#7, fixed by #8).

The operator ruled that Work Mode pastures belong to Dynamics.

## Decision

- **Cobbleworkers.** When it is loaded, the pasture adapter sets `cobbleworkers.resting` on each Cobblemon
  in a Work Mode pasture. It records each flag it set in our `persistentData` entry `set_cw_rest`. It
  clears only those flags, when the Cobblemon leaves the pasture or Work Mode turns off.
- **Cobblebase.** When it is loaded, an optional `@Pseudo` mixin makes Cobblebase skip its pasture logic
  for Work Mode pastures. The exact Cobblebase target methods need a check at implementation, because
  some of its behaviour runs inside its own mixin handlers (inferred). If a target is missing, the mod
  logs one warning and runs without that shim.
- **Config.** `compat.cobbleworkers` and `compat.cobblebase` switch each shim off.
- **Other pastures.** Pastures without Work Mode keep the other mods' jobs.
- **Load order.** `neoforge.mods.toml` lists both mods as optional dependencies, for load order only.

### Amendment (M5, 2026-11-10)

The 2.0.1 jar confirms the inferred fact: Cobblebase runs its whole pasture loop inside its own mixin handler,
which another mixin cannot target. The shim is four `@Pseudo` mixins on the helper methods that the loop calls,
and they leave alone every pasture that is not in Work Mode. Cobblebase 2.0.1 also needs Cloth Config at run
time, although its `neoforge.mods.toml` does not list it.

## Consequences

- Packs can combine Dynamics with either mod.
- Both shims depend on internals that the other mods can change without notice. Each release of those
  mods needs a manual check from the checklist.
- A Cobblemon moved from a Work Mode pasture to a normal one gets its Cobbleworkers jobs back.

## Alternatives considered

- **Shim Cobbleworkers and declare Cobblebase incompatible.** Less code, but packs with Cobblebase
  could not add Dynamics.
- **Declare both incompatible.** The most robust, but it shuts Dynamics out of packs that already use
  Cobbleworkers.
- **Do nothing.** Both mods would move and freeze our workers.
