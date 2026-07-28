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
| Period | Terrain and biomes wrap on a torus (X and Z) |
| Curvature | Physical sphere drop (d²/2R); large planets look nearly flat |
| Local time | Day/night follows X around the planet |
| Complete Coverage | Ensures every biome and structure set can appear on the planet |
| Structures | Soft clamps so vanilla structures still place inside the torus |
| Multiplayer | Localized sleep / weather bands |
| Config | `planetworld-common.toml` |

## Build

Requires **JDK 21**.

```bat
gradlew.bat build
```

Jar output: `build/libs/planetworld-1.0.0.jar`

## Run (dev)

```bat
gradlew.bat runClient
gradlew.bat runServer
```

## World type

1. Create World → World Type → **Wrapped Planet**
2. Click **Customize**:
   - Circumference slider: `256, 512, 1024, … 65536, 102400`
   - World Generation: **Normal** or **Complete Coverage**
   - Curvature tilt is automatic from circumference; feature toggles as needed
3. Click **Done**, then create the world

Skipping Customize uses defaults from `config/planetworld-common.toml` (default circumference **8192**).


## Curvature notes

- Works with the **vanilla** renderer (core shader overrides).
- **Sodium / Iris / Oculus** replace those shaders — curvature will not show unless a compatible shader pack adds the same drop.
- Press F3+T after updating the mod if an old resource cache sticks.
## Config (`config/planetworld-common.toml`)

Defaults for new worlds when Customize is not used:

- `planet_circumference` (snapped to slider steps, default 8192)
- `enable_curvature_shader`
- `curvature_intensity` (legacy unused; tilt = circumference/360 clamped 0.25–12)
- `enable_localized_time`
- `enable_localized_weather`
