These jars are vendored (not fetched from a Maven repo) because there is no reliable public
Maven for 1.7.10-era CoFH artifacts. They are the exact versions ThermalADD is written against
and were copied from an existing Minecraft install on this machine:

  CoFHCore-1.7.10-3.1.4-329.jar        <- cofhcore-1.7.10-3.1.4-329.jar
  ThermalExpansion-1.7.10-4.1.5-248.jar <- thermalexpansion-1.7.10-4.1.5-248.jar
  ThermalFoundation-1.7.10-1.2.6-118.jar <- thermalfoundation-1.7.10-1.2.6-118.jar

build.gradle references them via `flatDir { dirs 'libs' }` + `deobfCompile name: '...'`.
If you upgrade CoFHCore/ThermalExpansion, replace the jars here (keep the filenames in sync
with build.gradle, or update build.gradle) and re-run the build - ForgeGradle re-deobfuscates
them into its cache automatically.
