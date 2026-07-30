package com.planetworld.wrap.compat.create;

import com.planetworld.PlanetWorld;
import net.neoforged.fml.ModList;

/**
 * Loads every Create class the circumnavigation layer patches, so a version whose internals moved
 * fails while the world is still loading rather than the first time somebody drives a train over a
 * bound. Mixins apply when their target class loads, and most of these targets are only touched deep
 * in gameplay, which would otherwise hide a broken patch until it matters.
 * <p>
 * Keep this list in step with {@code planetworld-create.mixins.json}; an entry missing here only
 * means that patch is verified later, when its target happens to load.
 */
public final class CreateCompatCheck {
	private static final String[] PATCHED_CLASSES = {
			"com.simibubi.create.content.contraptions.AbstractContraptionEntity",
			"com.simibubi.create.content.trains.GlobalRailwayManager",
			"com.simibubi.create.content.trains.entity.Carriage",
			"com.simibubi.create.content.trains.entity.Carriage$DimensionalCarriageEntity",
			"com.simibubi.create.content.trains.entity.CarriageBogey",
			"com.simibubi.create.content.trains.entity.CarriageContraptionEntity",
			"com.simibubi.create.content.trains.entity.CarriageEntityHandler",
			"com.simibubi.create.content.trains.entity.Train",
			"com.simibubi.create.content.trains.entity.TravellingPoint",
			"com.simibubi.create.content.trains.graph.TrackEdge",
			"com.simibubi.create.content.trains.graph.TrackNodeLocation",
			"com.simibubi.create.content.trains.station.StationBlockEntity",
			"com.simibubi.create.content.trains.track.ITrackBlock",
			"com.simibubi.create.content.trains.track.TrackPlacement",
			"com.simibubi.create.content.trains.track.TrackPropagator",
	};

	private CreateCompatCheck() {
	}

	public static void verifyIfCreatePresent() {
		if (!ModList.get().isLoaded("create")) {
			return;
		}
		ClassLoader loader = CreateCompatCheck.class.getClassLoader();
		for (String name : PATCHED_CLASSES) {
			try {
				// initialize=false: transformation (and therefore mixin application) happens on load,
				// while Create's own static setup stays untouched.
				Class.forName(name, false, loader);
			} catch (ClassNotFoundException | LinkageError e) {
				throw new IllegalStateException(
						"Planet World cannot apply its Create compatibility patches to " + name
								+ ". Trains would cross world bounds incorrectly, so loading stops here."
								+ " This build targets Create 6.0.10 for Minecraft 1.21.1.", e);
			}
		}
		PlanetWorld.LOGGER.info("Create circumnavigation patches verified against {} classes", PATCHED_CLASSES.length);
	}
}
