Planet World Mod: Master Implementation Strategy (Plan.MD)
Vision

Create a Minecraft mod that transforms the Overworld into what feels like a spherical planet of at least 10,000 blocks in circumference, using a core philosophy of Illusion over Simulation.

Instead of breaking Minecraft's core gravity and physics engines, the mod will utilize a flat, non-Euclidean wrapping world paired with an Animal Crossing-style vertex shader. This ensures flawless compatibility with vanilla fluids, structures, and physics while delivering the aesthetic and explorative experience of living on a planet.
Target Architecture

    Mod Loader: NeoForge 1.21.1

    Dependencies: Citadel (core entity/data hooks), GeckoLib (if custom animations are needed)

    Core Concept: Flat physics, continuous chunk wrapping, localized time authority, and curved rendering.

Phase 1: Core Architecture & The Seamless Wrap

Before altering visuals, the flat world must loop infinitely without loading screens.

    1.1 Project Setup & World Type:

        Register a custom World Type called "Wrapped".

        Define configuration files for planet_circumference (Minimum 10,000 blocks / 625 chunks).

    1.2 Chunk Math Interception:

        Hook into the NeoForge chunk provider. Create a modulo wrapper for chunk coordinates along the chosen axis (e.g., the X-axis).

        If the world is 625 chunks wide, any client or server request for chunk 626 must automatically resolve to chunk 0. The game will load and stitch the opposite side of the world directly in front of the player.

    1.3 Entity & Player Wrapping:

        When an entity's physical X coordinate crosses the mathematical boundary (e.g., +5000), silently and instantly snap their coordinates to the opposite boundary (-5000).

        Because chunks are seamlessly stitched, this acts as a hidden mathematical teleport that the player experiences as continuous, uninterrupted walking.

Phase 2: The Visual Illusion (Curved Rendering)

The physical blocks remain flat, but the client must render them as a massive sphere.

    2.1 Vertex Shaders:

        Implement a custom vertex shader that artificially translates the Y-coordinate of blocks downward based on their distance from the player's camera.

        This creates a rolling visual horizon. The further away a block is, the further down it renders, hiding behind the curve.

    2.2 Entity & Hitbox Visuals:

        Apply the same vertex shader logic to entity rendering so mobs and dropped items curve over the horizon alongside the terrain. (Internal hitboxes remain flat to preserve combat mechanics).

    2.3 Frustum Culling Adjustments:

        Override the game's default camera culling. Prevent the engine from hiding chunks that have been mathematically "pushed down" by the shader, ensuring mountains don't pop in and out of existence.

Phase 3: Server-Side Positional Time (The Orbiting Sun)

Time must become a spatial coordinate. The server must enforce this localized time so gameplay mechanics (like zombie burning) work flawlessly in multiplayer.

    3.1 Coordinate-to-Time Mapping:

        Implement the core time formula across the server: LocalTime = (GlobalServerTime + (EntityX / WorldWidth) * 24000) % 24000.

    3.2 Entity Tick Interception (The Zombie Test):

        Use Mixins to intercept daylight checks (e.g., Mob.isSunBurnTick()).

        Mobs must query their localized time based on their X-coordinate rather than global time. If a Zombie is in a "Daytime" coordinate band, the server sets it on fire, visible to all players.

    3.3 Block Tick Interception:

        Intercept random block ticks for crops, saplings, and daylight detectors to ensure they only grow or activate when their specific X-coordinate is experiencing local daytime.

    3.4 Localized Client Skybox:

        Override the client-side sky renderer to display the sun, moon, and stars based entirely on the player's local time mapping.

Phase 4: World Generation (Looping Terrain)

Terrain generation must be modified to ensure the world seam is undetectable.

    4.1 Seamless Noise Generation:

        Modify NeoForge noise generators to use periodic (looping) noise.

        The terrain height, temperature, and humidity at X: -5000 must perfectly interpolate and connect to the terrain at X: +5000.

    4.2 Biome Distribution Guarantee:

        Map biomes to ensure every vanilla biome is represented within the finite 10,000-block space.

        Ensure large macro-features (continents, oceans, mountain ranges) successfully bridge the wrap-around seam.

Phase 5: Multiplayer Edge Cases & Synchronization

The server must handle players interacting across different time zones and physical seams.

    5.1 Cross-Seam Entity Synchronization:

        If Player A (at X: 4995) shoots an arrow toward Player B (at X: -4995), the server must instantly snap the projectile's coordinates across the boundary while maintaining its velocity vector. This ensures seamless cross-boundary combat and interactions.

    5.2 The "Sleeping in Beds" Logic:

        Modify the sleep mechanic. When a player sleeps, instead of resetting time to 0 universally, the server fast-forwards the GlobalServerTime by the exact number of ticks required to make the sleeping player's LocalTime reach Dawn (0). Other players on the server will experience a sudden, proportional time-jump (e.g., dusk instantly becomes midnight).

    5.3 Localized Weather Systems:

        Intercept weather rendering and logic. Rain/Snow should only render and extinguish fires if the player/entity is within a specific X-coordinate band, creating sweeping weather fronts rather than global storms.

Phase 6: Configuration & Polish

Allow extensive customization for server admins and modpack creators.

    6.1 Mod Configuration (planetworld-common.toml):

        planet_circumference (Integer, min 10000)

        enable_curvature_shader (Boolean, true by default)

        curvature_intensity (Float, dictates how sharp the horizon drop-off is)

        enable_localized_time (Boolean, true by default)

        enable_localized_weather (Boolean, true by default)

    6.2 Mod Compatibility Checks:

        Ensure the vertex shader does not fatally conflict with popular optimization mods (Sodium/Embeddium, Iris/Oculus). Provide API hooks or fallback rendering if necessary.

Success Criteria

The mod is complete when a server full of players can:

    Walk continuously in one direction and encounter their own builds again without ever seeing a loading screen or world border.

    Look at the horizon and see the terrain curve away naturally.

    Stand 5,000 blocks apart and experience Midnight and Noon simultaneously, with server mechanics (mob burning, crop growth) respecting their local time.

    Shoot an arrow across the mathematical world-seam and hit a target seamlessly.