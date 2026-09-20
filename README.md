# ThermalADD

A [Thermal Expansion 4](https://www.curseforge.com/minecraft/mc-mods/thermal-expansion) addon
for Minecraft **1.7.10** (Forge). Adds upgraded, multi-slot, `UltimateResonant`-tier versions
of TE4 machines, each running on Thermal Expansion's own real recipe lists, plus a couple of
mod-original "beyond spec" augments and a single-tier ultra-capacity energy cell:

- **Advanced Pulverizer** — 3 parallel input slots (instead of 1), 9 augment slots, its own
  RF tier.
- **Improved Cyclic Assembler** — 6 parallel schematic slots (instead of 1), accepts genuine
  TE schematics.
- **Advanced Furnace** — 3 parallel input slots (instead of 1), 9 augment slots.
- **Singularity Energy Cell** — a single, symmetric energy storage block modeled on Thermal
  Expansion's own Resonant Energy Cell, pushed past what the standard RF API can normally
  express. Its true internal storage holds up to **1,000,000,000,000 RF** (the GUI displays the
  real number, not the ~2.15 billion ceiling the underlying `int`-based RF API can report to
  other mods), and every side accepts or gives as much RF as a single transfer call allows -
  effectively unlimited throughput. Comes with its own real Thermal-Expansion-style
  Configuration tab (Disabled/Output/Input per face, matching TE's own Energy Cell logic
  exactly) and a violet-magenta-gold-cyan gradient reskin of TE's own cell texture. Crafted from
  3 real Resonant Energy Cells plus Enderium/Signalum ingots, a Resonant Capacitor and a Gold
  Power Coil - every non-cell ingredient is itself a multi-step crafted item, nothing raw.

All three machines support real Thermal Expansion augment items (Auto Input/Output,
Reconfigurable Sides, Redstone Control, Machine Speed, Machine Secondary, Energy Storage, ...),
a CoFH-style Configuration side tab with in-world connection badges (blue/orange/red) showing
each face's current mode, a Redstone Control tab (Disabled/Low/High, matching real TE's own
tab design), and Crescent Hammer (wrench) support for facing/side rotation. A freshly placed
machine already carries the same 3 default augments real Thermal Expansion machines do (Auto
Output, Redstone Control, Reconfigurable Sides).

### Mod-original augments

Two "beyond spec" augments that don't exist in real Thermal Expansion, crafted by upgrading
TE's own top-tier augment items:

- **Augment: Speed Level 4** — a 4th Machine Speed tier, one step past TE's own top tier.
  x10 processing speed for +200% RF/t over Level 3's already-steep cost. Works on the Advanced
  Pulverizer and Advanced Furnace.
- **Augment: Secondary Sieve Level 4** — a 4th Machine Secondary tier for the Advanced
  Pulverizer only. +200% secondary output chance (effectively guarantees any recipe with a
  nonzero secondary chance) for +25% RF/t.

## Building

Requires the CoFHCore, ThermalExpansion and ThermalFoundation jars (not included - see
[`libs/README.txt`](libs/README.txt)) placed in `libs/`. Then, from this directory:

```powershell
gradle build
```

The mod jar is produced under `build/libs/`.

## Dependencies (runtime)

- Minecraft Forge for 1.7.10 (`10.13.4.1614` or compatible)
- CoFHCore 3.1.4+
- ThermalExpansion 4.1.5+
- ThermalFoundation 1.2.6+
- Waila 1.5.10 *(optional)* — if present, the Singularity Cell's tooltip shows its real
  (beyond `int`-range) capacity instead of Waila's own generic, capped RF display. The mod
  builds and runs fine without it.

## Credits

Built on top of CoFHCore / Thermal Expansion by Team CoFH. Not affiliated with or endorsed by
Team CoFH.
