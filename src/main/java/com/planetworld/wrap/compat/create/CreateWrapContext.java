package com.planetworld.wrap.compat.create;

import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Thread-local Level for Create placement redirects that lack a Level argument.
 * <p>
 * Create nests these calls (placement walks track, which walks connected blocks), so a scope must
 * restore whatever context it replaced rather than clearing it, or the outer scope silently falls
 * back to guessing a dimension.
 */
public final class CreateWrapContext {
	private static final ThreadLocal<Level> LEVEL = new ThreadLocal<>();

	private CreateWrapContext() {
	}

	/** @return the context to hand back to {@link #pop(Level)} when this scope ends. */
	@Nullable
	public static Level push(@Nullable Level level) {
		Level previous = LEVEL.get();
		LEVEL.set(level);
		return previous;
	}

	public static void pop(@Nullable Level previous) {
		if (previous == null) {
			LEVEL.remove();
		} else {
			LEVEL.set(previous);
		}
	}

	@Nullable
	public static Level level() {
		return LEVEL.get();
	}
}
