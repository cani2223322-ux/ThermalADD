These jars are vendored (not fetched from a Maven repo) because there is no reliable public
Maven for 1.7.10-era CoFH artifacts. They are the exact versions ThermalADD is written against
and were copied from an existing Minecraft install on this machine:

  CoFHCore-1.7.10-3.1.4-329.jar        <- cofhcore-1.7.10-3.1.4-329.jar
  ThermalExpansion-1.7.10-4.1.5-248.jar <- thermalexpansion-1.7.10-4.1.5-248.jar
  ThermalFoundation-1.7.10-1.2.6-118.jar <- thermalfoundation-1.7.10-1.2.6-118.jar
  Waila-1.5.10_1.7.10.jar               <- Waila-1.5.10_1.7.10.jar

build.gradle references them via `flatDir { dirs 'libs' }` + `deobfCompile name: '...'`.
If you upgrade CoFHCore/ThermalExpansion, replace the jars here (keep the filenames in sync
with build.gradle, or update build.gradle) and re-run the build - ForgeGradle re-deobfuscates
them into its cache automatically.

Waila-1.5.10_1.7.10.jar is different from the other three: it's a compile-only reference for
net.thermaladd.mod.waila.ThermalADDWailaPlugin (a soft/optional integration - see that class's
own javadoc for how it stays safe to load with or without Waila actually installed). Unlike
CoFHCore/ThermalExpansion/ThermalFoundation, nothing in the mod hard-requires this jar to be
present for the mod to build or run correctly without Waila - it's only here so
ThermalADDWailaPlugin has real mcp.mobius.waila.api.* interfaces to compile against instead of
hand-written stand-ins that could drift from the real API.
