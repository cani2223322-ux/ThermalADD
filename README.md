# ThermalADD

A [Thermal Expansion 4](https://www.curseforge.com/minecraft/mc-mods/thermal-expansion) addon
for Minecraft **1.7.10** (Forge). Adds upgraded, multi-slot, `UltimateResonant`-tier versions
of TE4 machines, each running on Thermal Expansion's own real recipe lists:

- **Advanced Pulverizer** — 3 parallel input slots (instead of 1), 9 augment slots, its own
  RF tier.
- **Improved Cyclic Assembler** — 6 parallel schematic slots (instead of 1), accepts genuine
  TE schematics.
- **Advanced Furnace** — 3 parallel input slots (instead of 1), 9 augment slots.

All three machines support real Thermal Expansion augment items (Auto Input/Output,
Reconfigurable Sides, Redstone Control, Machine Speed, Energy Storage, ...), a CoFH-style
Configuration side tab with in-world connection badges (blue/orange/red) showing each face's
current mode, and Crescent Hammer (wrench) support for facing/side rotation.

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

## Credits

Built on top of CoFHCore / Thermal Expansion by Team CoFH. Not affiliated with or endorsed by
Team CoFH.
