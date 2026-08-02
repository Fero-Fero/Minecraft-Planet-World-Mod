# Planet World

NeoForge **1.21.1** mod that turns the Overworld into a finite, seamlessly wrapped torus planet: walk east (or north) forever and come back to your own builds — rivers, rails, redstone, and arrows cross the world boundary as if it were anywhere else on the map. Plus localized time (the sun orbits the planet), localized weather, and curved horizon rendering.

**Standalone** — no runtime mod dependencies.

## License and attribution

This mod is licensed **AGPL-3.0**. The world-wrapping core is ported from
[Circumnavigate](https://github.com/FamroFexl/Circumnavigate) by **Famro Fexl** (AGPL-3.0),
adapted from Fabric to NeoForge. Full license text in `LICENSE`.

## Features

| Phase | What it does |
|-------|----------------|
| Period | Terrain and biomes repeat every circumference along X |
| Curvature | Client horizon drop + entity visual offset (hitboxes stay flat) |
| Local time | `LocalTime = (global + wrapX(x)/width * 24000) % 24000` |
| Multiplayer | Localized sleep / weather bands |
| Config | `planetworld-common.toml` |

## Build

Requires **JDK 21**.

```bat
gradlew.bat build
gradlew.bat check
```

`check` includes `verifyCreateWrapMath`, `verifyCreateCompatJar`, and `verifySableCompatJar`.

Jar output: `build/libs/planetworld-1.0.0.jar`

## Optional mod compatibility

| Stack | Status | Notes |
|-------|--------|--------|
| **Create 6.0.10** | Soft dependency | Train circumnavigation + graph/signal substrate + schematicannon / display-link range. Newer 6.0.x: `CreateCompatCheck` fails loud at setup if internals moved. |
| **Sable 2.0.x** | Soft dependency | Sub-level pose wrap + distance + broadcast projector + teleport trailer. |
| **Create Aeronautics (bundled)** | Soft dependency | Same Sable substrate; vehicle acceptance still playtest. |
| Citadel / GeckoLib | **Not used** | — |

Dev runtime flags:

```bat
gradlew.bat runServer -PwithCreateRuntime
gradlew.bat runClient -PwithSableRuntime -PwithAeronauticsRuntime
```

Sable/Aeronautics jars are copied from `mods/` or `run/mods/` (not Maven).

### Changelog (compat)

- Travel “rescue after blocked” **removed** — seam edges come from discovery / one-shot heal only.
- Create G6–G7 code pass: End transformer guard, schematicannon + click-to-link torus range, heal session clear on stop.
- Core: pathfinder wrap neighbours, LVT-named move deltas, Level clip short-path.

## Run (dev)

```bat
gradlew.bat runClient
gradlew.bat runServer
```

## World type

1. Create World → World Type → **Wrapped Planet**
2. Click **Customize**:
   - Circumference slider: `256, 512, 1024, … 65536, 102400`
   - Curvature intensity and feature toggles
3. Click **Done**, then create the world

Skipping Customize uses defaults from `config/planetworld-common.toml` (default circumference **8192**).

## Config (`config/planetworld-common.toml`)

Defaults for new worlds when Customize is not used:

- `planet_circumference` (snapped to slider steps, default 8192)
- `enable_curvature_shader`
- `curvature_intensity`
- `enable_localized_time`
- `enable_localized_weather`
