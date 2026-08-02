package com.planetworld.wrap.compat.sable;

import com.planetworld.PlanetWorld;
import com.planetworld.wrap.core.WorldSpaceProjector;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Loads every Sable class the sub-level compatibility layer patches, so a version whose internals
 * moved fails while the world is still loading rather than the first time somebody flies an airship
 * over a bound. Mixins apply when their target class loads, and Sable's math entry points are only
 * touched once a sub-level exists, which would otherwise hide a broken patch until it matters.
 * <p>
 * Keep this list in step with {@code planetworld-sable.mixins.json}; an entry missing here only means
 * that patch is verified later, when its target happens to load.
 */
public final class SableCompatCheck {
	private static final String[] PATCHED_CLASSES = {
			"dev.ryanhcode.sable.ActiveSableCompanion",
			"dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem",
	};

	private SableCompatCheck() {
	}

	public static void verifyIfSablePresent() {
		if (!ModList.get().isLoaded("sable")) {
			return;
		}
		ClassLoader loader = SableCompatCheck.class.getClassLoader();
		for (String name : PATCHED_CLASSES) {
			try {
				// initialize=false: transformation (and therefore mixin application) happens on load,
				// while Sable's own static setup stays untouched.
				Class.forName(name, false, loader);
			} catch (ClassNotFoundException | LinkageError e) {
				throw new IllegalStateException(
						"Planet World cannot apply its Sable compatibility patches to " + name
								+ ". Physics vehicles would measure world distances the long way around"
								+ " the planet, so loading stops here."
								+ " This build targets Sable 2.0.x for Minecraft 1.21.1.", e);
			}
		}
		registerWorldSpaceProjector(loader);
		PlanetWorld.LOGGER.info("Sable sub-level patches verified against {} classes", PATCHED_CLASSES.length);
	}

	/**
	 * Core broadcast distance must project plot positions to parent-world poses before measuring on
	 * the torus. Registering here keeps {@code wrap/core} free of Sable type names.
	 */
	private static void registerWorldSpaceProjector(ClassLoader loader) {
		try {
			Class<?> companionClass = Class.forName("dev.ryanhcode.sable.companion.SableCompanion", false, loader);
			Object companion = companionClass.getField("INSTANCE").get(null);
			MethodHandle project = MethodHandles.publicLookup().findVirtual(
					companionClass,
					"projectOutOfSubLevel",
					MethodType.methodType(Vec3.class, Level.class, Vec3.class)
			);
			WorldSpaceProjector.set((level, x, y, z) -> {
				try {
					return (Vec3) project.invoke(companion, level, new Vec3(x, y, z));
				} catch (Throwable t) {
					throw new IllegalStateException("Sable projectOutOfSubLevel failed", t);
				}
			});
		} catch (Throwable t) {
			throw new IllegalStateException(
					"Planet World could not register Sable's sub-level projector for torus broadcasts",
					t
			);
		}
	}
}
