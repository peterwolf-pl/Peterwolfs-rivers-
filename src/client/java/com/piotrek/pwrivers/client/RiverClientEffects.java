package com.piotrek.pwrivers.client;

import com.piotrek.pwrivers.command.RiversCommands;
import com.piotrek.pwrivers.fluid.RiverFluidRegistry;
import com.piotrek.pwrivers.physics.RiverFlowSample;
import com.piotrek.pwrivers.physics.RiverPhysicsEngine;
import com.piotrek.pwrivers.physics.RiverTurbulence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry;

/**
 * Client-side visual effects: directional surface particles, whitewater foam in rapids,
 * weir aeration spray, and flow vector debug rendering.
 */
public final class RiverClientEffects {
	private static final int RIVER_WATER_TINT = 0xFF3F76E4; // Characteristic deep inland river blue-green tint

	private RiverClientEffects() {}

	public static void registerFluidRenderer() {
		Material still = new Material(Identifier.fromNamespaceAndPath("minecraft", "block/water_still"));
		Material flow = new Material(Identifier.fromNamespaceAndPath("minecraft", "block/water_flow"));
		Material overlay = new Material(Identifier.fromNamespaceAndPath("minecraft", "block/water_overlay"));

		BlockTintSource tintSource = new BlockTintSource() {
			@Override
			public int color(BlockState state) {
				return RIVER_WATER_TINT;
			}
		};

		FluidModel.Unbaked unbakedModel = new FluidModel.Unbaked(still, flow, overlay, tintSource);

		FluidRenderingRegistry.register(
			RiverFluidRegistry.RIVER_WATER,
			RiverFluidRegistry.FLOWING_RIVER_WATER,
			unbakedModel
		);
		FluidRenderingRegistry.setBlockTransparency(RiverFluidRegistry.RIVER_WATER_BLOCK, true);
	}

	public static void tickClientEffects() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) return;

		Level level = client.level;
		Player player = client.player;
		RandomSource random = level.getRandom();

		Vec3 playerPos = player.position();
		int range = 24;

		// 1. Surface flow drift streaks & foam particles
		for (int i = 0; i < 20; i++) {
			double rx = playerPos.x + (random.nextDouble() - 0.5) * range;
			double rz = playerPos.z + (random.nextDouble() - 0.5) * range;
			double ry = playerPos.y + (random.nextDouble() - 0.5) * 8.0;

			Vec3 samplePos = new Vec3(rx, ry, rz);
			RiverFlowSample sample = RiverPhysicsEngine.getInstance().sampleFlow(level, samplePos);

			if (sample.hasCurrent()) {
				double surfY = sample.waterSurfaceHeight();
				if (Math.abs(samplePos.y - surfY) < 1.2) {
					Vec3 v = sample.velocity();

					// Surface drift splash/current trail
					if (random.nextInt(3) == 0) {
						level.addParticle(
							ParticleTypes.SPLASH,
							rx, surfY + 0.03, rz,
							v.x * 0.15, 0.03, v.z * 0.15
						);
					}

					// Whitewater and Foam in turbulent sections
					if (RiverTurbulence.isWhitewater(sample.turbulenceIntensity())) {
						level.addParticle(
							ParticleTypes.SPLASH,
							rx, surfY + 0.05, rz,
							(random.nextDouble() - 0.5) * 0.1,
							0.08 + random.nextDouble() * 0.1,
							(random.nextDouble() - 0.5) * 0.1
						);

						if (sample.turbulenceIntensity() > 0.65F) {
							level.addParticle(
								ParticleTypes.BUBBLE_POP,
								rx, surfY, rz,
								v.x * 0.05, 0.02, v.z * 0.05
							);
						}
					}
				}
			}
		}

		// 2. Debug visual vectors
		if (RiversCommands.debugVectors && client.level.getGameTime() % 4 == 0) {
			for (int dx = -10; dx <= 10; dx += 4) {
				for (int dz = -10; dz <= 10; dz += 4) {
					Vec3 checkPos = new Vec3(playerPos.x + dx, playerPos.y, playerPos.z + dz);
					RiverFlowSample sample = RiverPhysicsEngine.getInstance().sampleFlow(level, checkPos);
					if (sample.hasCurrent()) {
						Vec3 v = sample.velocity();
						level.addParticle(
							ParticleTypes.END_ROD,
							checkPos.x, sample.waterSurfaceHeight() + 0.2, checkPos.z,
							v.x * 0.2, 0.0, v.z * 0.2
						);
					}
				}
			}
		}

		// 3. Debug vorticity / eddy cores
		if (RiversCommands.debugVortices && client.level.getGameTime() % 6 == 0) {
			for (int dx = -12; dx <= 12; dx += 6) {
				for (int dz = -12; dz <= 12; dz += 6) {
					Vec3 checkPos = new Vec3(playerPos.x + dx, playerPos.y, playerPos.z + dz);
					RiverFlowSample sample = RiverPhysicsEngine.getInstance().sampleFlow(level, checkPos);
					if (Math.abs(sample.vorticity()) > 0.1F) {
						level.addParticle(
							ParticleTypes.SOUL_FIRE_FLAME,
							checkPos.x, sample.waterSurfaceHeight() + 0.3, checkPos.z,
							0.0, 0.05, 0.0
						);
					}
				}
			}
		}
	}
}
