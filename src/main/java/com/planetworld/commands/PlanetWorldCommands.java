package com.planetworld.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.planetworld.debug.CelestialDebugState;
import com.planetworld.debug.SeasonQuarter;
import com.planetworld.season.SeasonAuthority;
import com.planetworld.wrap.WrapMath;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Debug commands for testing sun/moon motion, seasons, and polar weather.
 * <pre>
 * /planetworld time speed &lt;multiplier&gt;
 * /planetworld time speed reset
 * /planetworld season log &lt;true|false&gt;
 * /planetworld season set &lt;spring|summer|autumn|winter&gt;
 * /planetworld season query
 * /planetworld polar storm [north|south|both] [seconds]
 * /planetworld polar storm clear
 * </pre>
 */
@EventBusSubscriber(modid = com.planetworld.PlanetWorld.MOD_ID)
public final class PlanetWorldCommands {
	private static final int CHEAT_LEVEL = 2;
	private static final int DEFAULT_POLAR_STORM_SECONDS = 120;
	private static final int MAX_POLAR_STORM_SECONDS = 3600;
	private static final double MAX_TIME_SPEED = 500.0;

	private PlanetWorldCommands() {
	}

	@SubscribeEvent
	public static void register(RegisterCommandsEvent event) {
		CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
		dispatcher.register(buildRoot());
	}

	private static LiteralArgumentBuilder<CommandSourceStack> buildRoot() {
		return Commands.literal("planetworld")
				.requires(PlanetWorldCommands::hasCheatPermission)
				.then(Commands.literal("time")
						.then(Commands.literal("speed")
								.then(Commands.literal("reset")
										.executes(ctx -> resetTimeSpeed(ctx.getSource())))
								.then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(1.0, MAX_TIME_SPEED))
										.executes(ctx -> setTimeSpeed(
												ctx.getSource(),
												DoubleArgumentType.getDouble(ctx, "multiplier")
										)))))
				.then(Commands.literal("season")
						.then(Commands.literal("log")
								.then(Commands.argument("enabled", BoolArgumentType.bool())
										.executes(ctx -> setSeasonLog(
												ctx.getSource(),
												BoolArgumentType.getBool(ctx, "enabled")
										))))
						.then(Commands.literal("set")
								.then(Commands.literal("spring").executes(ctx -> setSeason(ctx.getSource(), SeasonQuarter.SPRING)))
								.then(Commands.literal("summer").executes(ctx -> setSeason(ctx.getSource(), SeasonQuarter.SUMMER)))
								.then(Commands.literal("autumn").executes(ctx -> setSeason(ctx.getSource(), SeasonQuarter.AUTUMN)))
								.then(Commands.literal("winter").executes(ctx -> setSeason(ctx.getSource(), SeasonQuarter.WINTER))))
						.then(Commands.literal("query")
								.executes(ctx -> querySeason(ctx.getSource()))))
				.then(Commands.literal("polar")
						.then(Commands.literal("storm")
								.then(Commands.literal("clear")
										.executes(ctx -> clearPolarStorm(ctx.getSource())))
								.then(Commands.literal("north")
										.executes(ctx -> startPolarStorm(ctx.getSource(), CelestialDebugState.PolarHemisphere.NORTH, DEFAULT_POLAR_STORM_SECONDS))
										.then(Commands.argument("seconds", IntegerArgumentType.integer(5, MAX_POLAR_STORM_SECONDS))
												.executes(ctx -> startPolarStorm(
														ctx.getSource(),
														CelestialDebugState.PolarHemisphere.NORTH,
														IntegerArgumentType.getInteger(ctx, "seconds")
												))))
								.then(Commands.literal("south")
										.executes(ctx -> startPolarStorm(ctx.getSource(), CelestialDebugState.PolarHemisphere.SOUTH, DEFAULT_POLAR_STORM_SECONDS))
										.then(Commands.argument("seconds", IntegerArgumentType.integer(5, MAX_POLAR_STORM_SECONDS))
												.executes(ctx -> startPolarStorm(
														ctx.getSource(),
														CelestialDebugState.PolarHemisphere.SOUTH,
														IntegerArgumentType.getInteger(ctx, "seconds")
												))))
								.then(Commands.literal("both")
										.executes(ctx -> startPolarStorm(ctx.getSource(), CelestialDebugState.PolarHemisphere.BOTH, DEFAULT_POLAR_STORM_SECONDS))
										.then(Commands.argument("seconds", IntegerArgumentType.integer(5, MAX_POLAR_STORM_SECONDS))
												.executes(ctx -> startPolarStorm(
														ctx.getSource(),
														CelestialDebugState.PolarHemisphere.BOTH,
														IntegerArgumentType.getInteger(ctx, "seconds")
												))))));
	}

	private static boolean hasCheatPermission(CommandSourceStack source) {
		return source.hasPermission(CHEAT_LEVEL);
	}

	private static ServerLevel requireOverworld(CommandSourceStack source) {
		return source.getLevel();
	}

	private static int resetTimeSpeed(CommandSourceStack source) {
		ServerLevel level = requireOverworld(source);
		CelestialDebugState.setTimeSpeedMultiplier(level, 1.0);
		source.sendSuccess(() -> Component.translatable("planetworld.command.time.speed.reset"), true);
		return 1;
	}

	private static int setTimeSpeed(CommandSourceStack source, double multiplier) {
		ServerLevel level = requireOverworld(source);
		CelestialDebugState.setTimeSpeedMultiplier(level, multiplier);
		source.sendSuccess(() -> Component.translatable("planetworld.command.time.speed.set", multiplier), true);
		return 1;
	}

	private static int setSeasonLog(CommandSourceStack source, boolean enabled) {
		ServerLevel level = requireOverworld(source);
		if (!WrapMath.isWrappedDimension(level)) {
			source.sendFailure(Component.translatable("planetworld.command.requires_wrapped"));
			return 0;
		}
		CelestialDebugState.setSeasonLogging(level, enabled);
		if (enabled) {
			float progress = SeasonAuthority.northernSeasonProgress(level);
			CelestialDebugState.setLastLoggedQuarter(level, SeasonQuarter.fromNorthernProgress(progress));
		}
		source.sendSuccess(() -> Component.translatable(
				enabled ? "planetworld.command.season.log.on" : "planetworld.command.season.log.off"
		), true);
		return 1;
	}

	private static int setSeason(CommandSourceStack source, SeasonQuarter quarter) {
		ServerLevel level = requireOverworld(source);
		if (!WrapMath.isWrappedDimension(level)) {
			source.sendFailure(Component.translatable("planetworld.command.requires_wrapped"));
			return 0;
		}
		boolean ss = SeasonAuthority.setNorthernSeason(level, quarter);
		float progress = SeasonAuthority.northernSeasonProgress(level);
		CelestialDebugState.setLastLoggedQuarter(level, SeasonQuarter.fromNorthernProgress(progress));
		source.sendSuccess(() -> Component.translatable(
				ss ? "planetworld.command.season.set.ss" : "planetworld.command.season.set.fallback",
				quarter.displayName(),
				String.format("%.3f", progress)
		), true);
		return 1;
	}

	private static int querySeason(CommandSourceStack source) {
		ServerLevel level = requireOverworld(source);
		if (!WrapMath.isWrappedDimension(level)) {
			source.sendFailure(Component.translatable("planetworld.command.requires_wrapped"));
			return 0;
		}
		float progress = SeasonAuthority.northernSeasonProgress(level);
		SeasonQuarter quarter = SeasonQuarter.fromNorthernProgress(progress);
		source.sendSuccess(() -> Component.translatable(
				"planetworld.command.season.query",
				quarter.displayName(),
				String.format("%.3f", progress),
				String.format("%.2f", SeasonAuthority.northernWarmth(level))
		), false);
		return 1;
	}

	private static int startPolarStorm(CommandSourceStack source, CelestialDebugState.PolarHemisphere hemisphere, int seconds) {
		ServerLevel level = requireOverworld(source);
		if (!WrapMath.isWrappedDimension(level)) {
			source.sendFailure(Component.translatable("planetworld.command.requires_wrapped"));
			return 0;
		}
		int ticks = seconds * 20;
		long until = level.getGameTime() + ticks;
		CelestialDebugState.setPolarStorm(level, new CelestialDebugState.PolarStorm(until, hemisphere));
		level.setWeatherParameters(0, ticks, true, false);

		Component hemisphereName = Component.translatable("planetworld.command.polar.hemisphere." + hemisphere.name().toLowerCase());
		source.sendSuccess(() -> Component.translatable(
				"planetworld.command.polar.storm.start",
				hemisphereName,
				seconds
		), true);

		if (source.getEntity() instanceof ServerPlayer player) {
			double lat = SeasonAuthority.latitude(level, player.getZ());
			source.sendSuccess(() -> Component.translatable(
					"planetworld.command.polar.storm.hint",
					String.format("%.2f", lat)
			), false);
		}
		return 1;
	}

	private static int clearPolarStorm(CommandSourceStack source) {
		ServerLevel level = requireOverworld(source);
		CelestialDebugState.clearPolarStorm(level);
		source.sendSuccess(() -> Component.translatable("planetworld.command.polar.storm.clear"), true);
		return 1;
	}
}
