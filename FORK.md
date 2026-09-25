# Effortless Building overflow fix (community fork)

This fork is based on [Effortless Building by Requioss](https://www.curseforge.com/minecraft/mc-mods/effortless-building), Minecraft 1.21.1 branch, version 4.2. It exists to distribute a narrow fix while [upstream issue #36](https://github.com/Requios/effortless-building-multi/issues/36) is open. It is not an official release.

## Change

`ItemUsageTracker.computeItem` adds the vanilla inventory count to an AE2 network count. If the cached network count is `Integer.MAX_VALUE`, signed 32-bit addition wraps negative. The preview can then index a block list with a negative position and crash the client, even when the building interface is closed. This fork adds the values as `long` and caps the result at `Integer.MAX_VALUE`.

The changed source is `common/src/main/java/nl/requios/effortlessbuilding/utilities/ItemUsageTracker.java`. The distributed NeoForge-compatible universal JAR was made by applying `fork-patch/PatchEffortlessBuilding.java` to the official [CurseForge file 8515718](https://www.curseforge.com/minecraft/mc-mods/effortless-building/files/8515718). The patcher changes only the NeoForge `ItemUsageTracker.class` entry. Its output is intended to replace the original JAR, never to be installed beside it.

## Reproduce the binary patch

Use Java 21 or newer and ASM 9.10.1 (`org.ow2.asm:asm:9.10.1`). From this repo's root:

```powershell
javac -cp path\to\asm-9.10.1.jar fork-patch\PatchEffortlessBuilding.java
java -cp "fork-patch;path\to\asm-9.10.1.jar" PatchEffortlessBuilding path\to\effortlessbuilding-4.2+1.21.1.jar build\effortlessbuilding-4.2+1.21.1-overflow-fix.jar
```

The JAR keeps the original mod ID and version. The change was tested in an ARC 2026 NeoForge 21.1.250 client with AE2 19.2.17; two affected players report no repeat of this crash so far. It is not a claim of exhaustive compatibility testing. The server still needs its normal Effortless Building installation for actual building features.

Original code and assets: Requioss and contributors. Fork modification: florianfinn. This fork follows the original LGPL-3.0 license; see `LICENSE`. The original project, not this fork, should receive normal support requests unrelated to this fix.
