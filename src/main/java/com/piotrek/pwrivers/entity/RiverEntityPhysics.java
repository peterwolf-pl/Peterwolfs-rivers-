package com.piotrek.pwrivers.entity;

import com.piotrek.pwrivers.config.RiversConfig;
import com.piotrek.pwrivers.fluid.RiverFluidRegistry;
import com.piotrek.pwrivers.integration.WindAndSailsRiverAdapter;
import com.piotrek.pwrivers.physics.RiverFlowSample;
import com.piotrek.pwrivers.physics.RiverPhysicsEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative physical interaction processor for entities in river water.
 * Governs swimming resistance, downstream drift, floating object trajectories,
 * and boat current coupling.
 */
public final class RiverEntityPhysics {
	private RiverEntityPhysics() {}

	/**
	 * Ticks hydrodynamics for all entities in a loaded server level.
	 */
	public static void tickLevel(ServerLevel level) {
		if (!RiversConfig.simulationEnabled) return;

		for (Entity entity : level.getAllEntities()) {
			if (!entity.isAlive()) continue;

			Vec3 pos = entity.position();
			BlockPos blockPos = BlockPos.containing(pos.x, pos.y + 0.1, pos.z);
			FluidState fluidState = level.getFluidState(blockPos);

			boolean isInRiverFluid = fluidState.getType() instanceof com.piotrek.pwrivers.fluid.RiverWaterFluid
				|| fluidState.is(FluidTags.WATER);

			if (!isInRiverFluid) {
				// Check block below feet as well
				BlockPos feetPos = BlockPos.containing(pos.x, pos.y - 0.2, pos.z);
				FluidState feetFluid = level.getFluidState(feetPos);
				if (feetFluid.getType() instanceof com.piotrek.pwrivers.fluid.RiverWaterFluid) {
					isInRiverFluid = true;
				}
			}

			if (isInRiverFluid) {
				applyHydrodynamics(entity, level);
			}
		}
	}

	/**
	 * Applies local flow forces, drag, and buoyancy to an entity.
	 */
	public static void applyHydrodynamics(Entity entity, ServerLevel level) {
		Vec3 pos = entity.position();
		RiverFlowSample sample = RiverPhysicsEngine.getInstance().sampleFlow(level, pos);
		if (!sample.hasCurrent()) return;

		Vec3 current = sample.velocityPerTick();
		Vec3 vel = entity.getDeltaMovement();

		if (entity instanceof ItemEntity) {
			// Floating dropped item or debris: high drag, buoyant surface tracking
			double dragCoeff = 0.14;
			double targetVx = Mth.lerp(dragCoeff, vel.x, current.x);
			double targetVz = Mth.lerp(dragCoeff, vel.z, current.z);

			// Buoyancy towards water surface height
			double surfaceY = sample.waterSurfaceHeight();
			double dy = (surfaceY - 0.1) - entity.getY();
			double targetVy = Mth.clamp(dy * 0.20, -0.25, 0.25);
			double newVy = Mth.lerp(0.35, vel.y, targetVy);

			entity.setDeltaMovement(new Vec3(targetVx, newVy, targetVz));
		} else if (entity instanceof Player player) {
			// Swimming player: upstream resistance, downstream assistance
			if (player.isSwimming() || player.isInWater()) {
				double dot = vel.x * current.x + vel.z * current.z;
				double currentSpeed = sample.currentSpeed();

				double forceFactor = 0.04;
				if (dot < 0.0) {
					// Swimming against current: experiences hydraulic drag resistance
					forceFactor = 0.06;
				}

				// Apply current drift impulse
				double impX = current.x * forceFactor;
				double impZ = current.z * forceFactor;
				entity.setDeltaMovement(vel.add(impX, 0.0, impZ));
			}
		} else if (entity instanceof AbstractBoat && RiversConfig.compatibilityEnableVanillaBoatPhysics) {
			// Vanilla boat: entrained by river current, responds to vortices
			Vec3 coupled = WindAndSailsRiverAdapter.applyRiverCoupling(entity, vel, current);
			entity.setDeltaMovement(coupled);

			// Rotational torque from river vorticity
			if (Math.abs(sample.vorticity()) > 0.05F) {
				float yawTorque = sample.vorticity() * 1.5F;
				entity.setYRot(entity.getYRot() + yawTorque);
			}
		} else if (entity instanceof com.piotrek.pwrivers.vessel.WresslaEntity) {
			// Wressla catamaran handles its own internal hydrodynamics coupling
			return;
		} else {
			// Other floating entities / generic mobs
			double drag = 0.06;
			double targetVx = Mth.lerp(drag, vel.x, current.x);
			double targetVz = Mth.lerp(drag, vel.z, current.z);
			entity.setDeltaMovement(new Vec3(targetVx, vel.y, targetVz));
		}
	}
}
