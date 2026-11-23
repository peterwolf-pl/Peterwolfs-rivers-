package com.piotrek.pwrivers.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.piotrek.pwrivers.config.RiversConfig;
import com.piotrek.pwrivers.physics.RiverFlowSample;
import com.piotrek.pwrivers.physics.RiverPhysicsEngine;
import com.piotrek.pwrivers.structures.RiverLockController;
import com.piotrek.pwrivers.worldgen.RiverDemoPreset;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Administration, diagnostics, and test world commands for Peterwolf's Rivers.
 */
public final class RiversCommands {
	public static boolean debugVectors = false;
	public static boolean debugVortices = false;
	public static boolean debugNetwork = false;

	private RiversCommands() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			registerCommands(dispatcher, "rivers");
			registerCommands(dispatcher, "river");
		});
	}

	private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, String rootName) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(rootName)
			// /rivers info
			.then(Commands.literal("info")
				.executes(ctx -> {
					RiverPhysicsEngine engine = RiverPhysicsEngine.getInstance();
					int reachCount = engine.getNetwork().getAllReaches().size();
					int lockCount = engine.getAllLocks().size();
					int weirCount = engine.getAllWeirs().size();

					ctx.getSource().sendSuccess(
						() -> Component.literal(String.format(
							"§b[Peterwolf's Rivers]§r Version: §e1.0.0+26.3§r\n" +
							"§7Simulation: §a%s§7 | Quality: §e%s§7 | Active Reaches: §f%d§7 | Locks: §f%d§7 | Weirs: §f%d",
							RiversConfig.simulationEnabled ? "ENABLED" : "DISABLED",
							RiversConfig.simulationQuality,
							reachCount, lockCount, weirCount
						)),
						false
					);
					return 1;
				})
			)
			// /rivers flow
			.then(Commands.literal("flow")
				.executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayer();
					if (player == null) {
						ctx.getSource().sendFailure(Component.literal("Command must be executed by a player."));
						return 0;
					}

					Vec3 pos = player.position();
					RiverFlowSample sample = RiverPhysicsEngine.getInstance().sampleFlow(player.level(), pos);

					ctx.getSource().sendSuccess(
						() -> Component.literal(String.format(
							"§b[River Flow @ %.1f, %.1f, %.1f]§r\n" +
							"§7Reach: §e%s§r\n" +
							"§7Velocity: §a[%.2f, %.2f, %.2f] m/s§r (Speed: §e%.2f m/s§r / §6%.1f km/h§r)\n" +
							"§7Flow Direction: §f%.1f°§r\n" +
							"§7Water Surface Y: §b%.2f§r | Local Depth: §b%.2f m§r\n" +
							"§7Turbulence: §c%.2f§r | Vorticity: §d%.3f rad/s§r | Pressure: §f%.0f Pa§r",
							pos.x, pos.y, pos.z,
							sample.reachId(),
							sample.velocity().x, sample.velocity().y, sample.velocity().z,
							sample.currentSpeed(), sample.currentSpeed() * 3.6,
							sample.flowDirectionDeg(),
							sample.waterSurfaceHeight(), sample.localDepth(),
							sample.turbulenceIntensity(), sample.vorticity(),
							sample.localPressure()
						)),
						false
					);
					return 1;
				})
			)
			// /rivers debug vectors
			.then(Commands.literal("debug")
				.then(Commands.literal("vectors")
					.executes(ctx -> {
						debugVectors = !debugVectors;
						ctx.getSource().sendSuccess(
							() -> Component.literal("§b[Rivers Debug]§r Flow vector visualization: " + (debugVectors ? "§aON" : "§cOFF")),
							true
						);
						return 1;
					})
				)
				.then(Commands.literal("vortices")
					.executes(ctx -> {
						debugVortices = !debugVortices;
						ctx.getSource().sendSuccess(
							() -> Component.literal("§b[Rivers Debug]§r Vorticity & eddy visualization: " + (debugVortices ? "§aON" : "§cOFF")),
							true
						);
						return 1;
					})
				)
				.then(Commands.literal("network")
					.executes(ctx -> {
						debugNetwork = !debugNetwork;
						ctx.getSource().sendSuccess(
							() -> Component.literal("§b[Rivers Debug]§r River network centerline visualization: " + (debugNetwork ? "§aON" : "§cOFF")),
							true
						);
						return 1;
					})
				)
			)
			// /rivers lock status | cycle
			.then(Commands.literal("lock")
				.then(Commands.literal("status")
					.executes(ctx -> {
						ServerPlayer player = ctx.getSource().getPlayer();
						Vec3 pos = (player != null) ? player.position() : Vec3.ZERO;
						RiverLockController nearest = null;
						double bestDistSq = Double.POSITIVE_INFINITY;

						for (RiverLockController lock : RiverPhysicsEngine.getInstance().getAllLocks()) {
							double dSq = lock.getChamberBounds().getCenter().distanceToSqr(pos);
							if (dSq < bestDistSq) {
								bestDistSq = dSq;
								nearest = lock;
							}
						}

						if (nearest == null) {
							ctx.getSource().sendSuccess(() -> Component.literal("§eNo navigation locks currently registered in this world."), false);
							return 1;
						}

						RiverLockController lock = nearest;
						ctx.getSource().sendSuccess(
							() -> Component.literal(String.format(
								"§b[Lock: %s]§r State: §e%s§r\n" +
								"§7Chamber Level: §b%.2f m§r (Upstream: §f%.2f m§r, Downstream: §f%.2f m§r)\n" +
								"§7Upper Gate: §a%.0f%%§r | Lower Gate: §a%.0f%%§r\n" +
								"§7Status: §f%s",
								lock.getLockName(),
								lock.getState().name(),
								lock.getChamberLevelY(), lock.getUpstreamLevelY(), lock.getDownstreamLevelY(),
								lock.getUpstreamGate().getOpenProgress() * 100.0F,
								lock.getDownstreamGate().getOpenProgress() * 100.0F,
								lock.getStatusMessage()
							)),
							false
						);
						return 1;
					})
				)
				.then(Commands.literal("cycle")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.executes(ctx -> {
						ServerPlayer player = ctx.getSource().getPlayer();
						Vec3 pos = (player != null) ? player.position() : Vec3.ZERO;
						RiverLockController nearest = null;
						double bestDistSq = Double.POSITIVE_INFINITY;

						for (RiverLockController lock : RiverPhysicsEngine.getInstance().getAllLocks()) {
							double dSq = lock.getChamberBounds().getCenter().distanceToSqr(pos);
							if (dSq < bestDistSq) {
								bestDistSq = dSq;
								nearest = lock;
							}
						}

						if (nearest != null) {
							RiverLockController targetLock = nearest;
							boolean started = targetLock.cycleLock();
							if (started) {
								ctx.getSource().sendSuccess(() -> Component.literal("§aInitiated cycle for " + targetLock.getLockName()), true);
							} else {
								ctx.getSource().sendFailure(Component.literal("Cannot cycle: " + targetLock.getStatusMessage()));
							}
						} else {
							ctx.getSource().sendFailure(Component.literal("No lock found nearby."));
						}
						return 1;
					})
				)
			)
			// /rivers benchmark
			.then(Commands.literal("benchmark")
				.executes(ctx -> {
					RiverPhysicsEngine engine = RiverPhysicsEngine.getInstance();
					long lastTick = engine.getLastTickTimeNanos();
					double avgMicros = engine.getAverageTickMicroseconds();
					long totalTicks = engine.getTotalSimulatedTicks();
					int cells = engine.getActiveCellCount();

					ctx.getSource().sendSuccess(
						() -> Component.literal(String.format(
							"§b[Peterwolf's Rivers Benchmark]§r\n" +
							"§7Simulated Ticks: §f%d§r\n" +
							"§7Last Tick Cost: §e%.2f µs§r (%.4f ms)\n" +
							"§7Average Tick Cost: §a%.2f µs§r (%.4f ms)\n" +
							"§7Active Cached Cells: §f%d§r\n" +
							"§7Estimated Max TPS Impact: §a< 0.05%%§r",
							totalTicks,
							lastTick / 1000.0, lastTick / 1_000_000.0,
							avgMicros, avgMicros / 1000.0,
							cells
						)),
						false
					);
					return 1;
				})
			)
			// /rivers demo
			.then(Commands.literal("demo")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> {
					ServerLevel level = ctx.getSource().getLevel();
					BlockPos origin = BlockPos.containing(ctx.getSource().getPosition());

					ctx.getSource().sendSuccess(
						() -> Component.literal("§b[Peterwolf's Rivers]§r Generating Odra River Wrocław showcase network..."),
						true
					);

					RiverDemoPreset.buildWorldDemo(level, origin);

					ctx.getSource().sendSuccess(
						() -> Component.literal(String.format(
							"§aSuccessfully built Odra River demonstration environment at [%d, %d, %d]!\n" +
							"§7Includes: Upstream gorge/rapids, Island bifurcation, Śluza Piaskowa, Śluza Mieszczańska, Jaz św. Macieja, and bridge pier wake eddies.",
							origin.getX(), origin.getY(), origin.getZ()
						)),
						true
					);
					return 1;
				})
			);

		dispatcher.register(root);
	}
}
