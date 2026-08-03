# Planet World

turn the Overworld into a finite, seamlessly wrapped torus planet, walking any direction forever will eventually lead back to where you were. The world has localized time (the sun orbits the planet), localized weather, and curved horizon rendering.

**Standalone** — no mod dependencies.

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

## Optional mod compatibility

| Stack | Status | Notes |
|-------|--------|--------|
| **Create 6.0.10** | Soft dependency |
| **Sable 2.0.x** | Soft dependency |
| **Create Aeronautics (bundled)** | WIP |
| **Terralith 2.5+** | Soft dependency | Realism (≥2048): climate remap feeds Terralith biomes; sparse wrap-safe surface seeds. Fantasy/skylands toggleable in config. |

Dev runtime flags:

```bat
gradlew.bat runServer -PwithCreateRuntime
gradlew.bat runClient -PwithSableRuntime -PwithAeronauticsRuntime
gradlew.bat runClient -PwithTerralithRuntime
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
