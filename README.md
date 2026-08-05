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
| Curvature | Client horizon drop via vanilla terrain shaders (inactive under Sodium — see compat table) |
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
| **Terralith 2.5+** | Soft dependency | Realism (≥2048): climate remap feeds Terralith biomes; sparse wrap-safe surface seeds at C≥8192. Fantasy/skylands toggleable in config. |
| **Serene Seasons** | Soft dependency | SeasonAuthority reads SS when present; else 5-day half-year. Hemisphere-aware sun/polar hazards; does not replace SS crop/snow. |
| **Still Life + Lithosphere** | Soft dependency | Customize pack option when both loaded (exclusive with Terralith). Lithosphere keeps its terrain (PW Realism landmask disabled). Seed catalog TBD. |
| **Blooming Biosphere** | Soft dependency | Customize pack option when exclusive of Terralith/Still Life. Seed catalog TBD. |
| **Farmer's Delight** | Soft dependency | Cold farmland also reverts rich soil farmland (crops drop). |
| **Sodium** | Soft dependency | Toast cleared via `pack.mcmeta` `ignored_shaders`. Horizon curvature **degrades** (flat) under Sodium; wrap / local time / weather still work. SCSS backend TBD. |
| **Iris / Oculus** | Soft dependency | Vanilla `renderSky` tilt skipped (Iris owns sky). Local time via `getTimeOfDay` still applies. |
| **End Remastered 6.x** | Soft dependency | Realism (≥2048): land-anchored plains stronghold so custom eyes can locate an End portal. |
| Citadel / GeckoLib | **Not used** | — |

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
