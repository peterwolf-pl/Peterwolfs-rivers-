package com.piotrek.pwrivers.physics;

import com.piotrek.pwrivers.config.RiversConfig;
import com.piotrek.pwrivers.fluid.RiverWaterFluid;
import com.piotrek.pwrivers.integration.WindAndSailsRiverAdapter;
import com.piotrek.pwrivers.network.RiverNetworkGraph;
import com.piotrek.pwrivers.network.RiverReach;
import com.piotrek.pwrivers.worldgen.RiverBiomeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Localized 2D/3D depth-averaged velocity field solver.
 * Calculates detailed velocity vectors, shear profiles, secondary circulation,
 * turbulence, and vorticity at any point in the river.
 * Supports both macro-network reach projections and continuous procedural river flow.
 */
public class RiverFlowField {
	private final RiverNetworkGraph network;
	private final RiverVortexSolver vortexSolver;

	public RiverFlowField(RiverNetworkGraph network, RiverVortexSolver vortexSolver) {
		this.network = network;
		this.vortexSolver = vortexSolver;
	}

	public RiverNetworkGraph getNetwork() {
		return this.network;
	}

	public RiverVortexSolver getVortexSolver() {
		return this.vortexSolver;
	}

	/**
	 * Samples the complete hydrological state at a world position.
	 */
	public RiverFlowSample sampleFlow(Level level, Vec3 position) {
		if (position == null) return RiverFlowSample.ZERO;

		RiverReach reach = this.network.findNearestReach(position);
		if (reach == null) {
			return sampleProceduralFlow(level, position);
		}

		RiverReach.ProjectionResult proj = reach.project(position);
		double halfWidth = reach.getBaseWidth() * 0.5;
		double transDist = Math.abs(proj.transverseDistance());

		// Outside channel bank boundary
		if (transDist > halfWidth * 1.05) {
			return RiverFlowSample.ZERO;
		}

		double surfaceY = proj.localSurfaceY();
		double bedY = surfaceY - proj.localDepth();

		// Check if position is vertically within water column (with small margin for boats floating on surface)
		if (position.y > surfaceY + 0.5 || position.y < bedY - 1.5) {
			return RiverFlowSample.ZERO;
		}

		// 1. Mean cross-sectional velocity from continuity: Q = v * A
		double meanVelocity = RiverHydraulics.constrictedVelocity(
			reach.getTargetDischarge(),
			reach.getBaseWidth(),
			reach.getBaseDepth()
		) * RiversConfig.flowSpeedMultiplier;

		// 2. Transverse boundary layer profile across river width (no-slip at banks, peak in fairway)
		double normalizedOffset = Mth.clamp(proj.transverseDistance() / halfWidth, -1.0, 1.0);
		double bankFriction = Math.max(0.08, 0.20 - reach.getManningN());
		double vStreamwise = RiverHydraulics.transverseVelocityProfile(normalizedOffset, meanVelocity, bankFriction);

		// 3. Sharp bend asymmetry: outer bank accelerates, inner bank slows
		if (Math.abs(proj.curvature()) > 1e-4) {
			double bendSkew = -proj.curvature() * proj.transverseDistance() * 1.2;
			vStreamwise *= Math.max(0.2, 1.0 + bendSkew);
		}

		// 4. Vertical velocity profile (friction at riverbed, maximum speed at water surface)
		double heightAboveBed = Math.max(0.0, position.y - bedY);
		vStreamwise = RiverHydraulics.verticalVelocityProfile(heightAboveBed, proj.localDepth(), vStreamwise);

		// 5. Downstream streamwise velocity vector
		Vec3 tangent = proj.flowTangent();
		double vx = tangent.x * vStreamwise;
		double vy = tangent.y * vStreamwise;
		double vz = tangent.z * vStreamwise;

		// 6. Secondary helical circulation at river bends (cross-channel flow)
		double relativeDepth = (proj.localDepth() > 0.05) ? Mth.clamp(heightAboveBed / proj.localDepth(), 0.0, 1.0) : 0.5;
		Vec3 bendFlow = RiverVortexSolver.computeBendCirculation(
			proj.curvature(),
			vStreamwise,
			relativeDepth,
			proj.normalToBank()
		);
		vx += bendFlow.x;
		vz += bendFlow.z;

		// 7. Add induced vortex and wake recirculation velocity
		Vec3 vortexVel = this.vortexSolver.sampleVortexVelocity(position);
		vx += vortexVel.x;
		vz += vortexVel.z;

		// 8. Wind-driven surface drift (if Wind & Sails is present and near surface)
		if (level != null && RiversConfig.compatibilityEnableWindAndSails) {
			Vec3 windDrift = WindAndSailsRiverAdapter.computeSurfaceWindDrift(level, position, relativeDepth);
			vx += windDrift.x;
			vz += windDrift.z;
		}

		// Combined flow magnitude and direction
		double horizSpeed = Math.sqrt(vx * vx + vz * vz);
		float flowDeg = (float) (Mth.atan2(-vx, vz) * Mth.RAD_TO_DEG);
		if (flowDeg < 0.0F) flowDeg += 360.0F;

		// 9. Turbulence & Vorticity
		float vortexVorticity = this.vortexSolver.sampleTotalVorticity(position);
		float totalVorticity = vortexVorticity + proj.curvature() * (float) vStreamwise;

		float turbulence = RiverTurbulence.calculateTurbulence(
			vStreamwise,
			proj.localDepth(),
			reach.getManningN(),
			proj.curvature(),
			Math.abs(vortexVorticity) > 0.1F,
			false
		) * (float) RiversConfig.turbulenceMultiplier;

		// 10. Local pressure
		double depthBelowSurface = Math.max(0.0, surfaceY - position.y);
		double pressure = RiverHydraulics.localPressure(depthBelowSurface, horizSpeed);

		return new RiverFlowSample(
			new Vec3(vx, vy, vz),
			horizSpeed,
			flowDeg,
			proj.localDepth(),
			surfaceY,
			turbulence,
			totalVorticity,
			pressure,
			reach.getReachId()
		);
	}

	/**
	 * Procedural continuous river flow solver for naturally generated worlds.
	 * Evaluates an incompressible analytical stream function Psi(x, z), aligns with local channel
	 * geometry, and applies Manning resistance, depth scaling, and vertical velocity profiles.
	 */
	public RiverFlowSample sampleProceduralFlow(Level level, Vec3 position) {
		if (position == null) return RiverFlowSample.ZERO;

		double surfaceY = 62.0;
		double depth = 3.5;

		if (level != null) {
			BlockPos blockPos = BlockPos.containing(position.x, position.y, position.z);
			FluidState fluid = level.getFluidState(blockPos);
			boolean inWater = fluid.getType() instanceof RiverWaterFluid || fluid.is(FluidTags.WATER);

			if (!inWater) {
				BlockPos below = blockPos.below();
				FluidState fluidBelow = level.getFluidState(below);
				if (fluidBelow.getType() instanceof RiverWaterFluid || fluidBelow.is(FluidTags.WATER)) {
					inWater = true;
					blockPos = below;
					fluid = fluidBelow;
				}
			}

			if (!inWater) {
				return RiverFlowSample.ZERO;
			}

			Holder<Biome> biomeHolder = level.getBiome(blockPos);
			boolean isRiverBiome = biomeHolder.is(RiverBiomeRegistry.RIVERS_BY_PETERWOLF)
				|| biomeHolder.is(RiverBiomeRegistry.RIVER_BIOME)
				|| biomeHolder.is(BiomeTags.IS_RIVER);
			boolean isCustomRiverFluid = fluid.getType() instanceof RiverWaterFluid;

			// Only simulate strong river currents in river biomes or custom river fluid
			if (!isRiverBiome && !isCustomRiverFluid) {
				return RiverFlowSample.ZERO;
			}

			// Scan upward to locate water surface
			BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(blockPos.getX(), blockPos.getY(), blockPos.getZ());
			while (cursor.getY() < level.getMaxY() && (level.getFluidState(cursor).is(FluidTags.WATER) || level.getFluidState(cursor).getType() instanceof RiverWaterFluid)) {
				cursor.move(0, 1, 0);
			}
			surfaceY = cursor.getY() - 0.05;

			// Scan downward to locate riverbed
			cursor.set(blockPos.getX(), blockPos.getY(), blockPos.getZ());
			while (cursor.getY() > level.getMinY() && (level.getFluidState(cursor).is(FluidTags.WATER) || level.getFluidState(cursor).getType() instanceof RiverWaterFluid)) {
				cursor.move(0, -1, 0);
			}
			double bedY = cursor.getY() + 1.0;
			depth = Math.max(0.5, surfaceY - bedY);
		}

		// Analytical incompressible stream function: div(v) = 0
		double sx = position.x * 0.004;
		double sz = position.z * 0.004;

		double dPsi_dx = Math.cos(sx + 0.6 * Math.sin(sz))
			+ 0.5 * Math.cos(2.3 * sx + 0.9) * Math.sin(1.7 * sz)
			+ 0.25 * Math.cos(4.1 * sx + 1.5);

		double dPsi_dz = -Math.sin(sz + 0.5 * Math.cos(sx))
			+ 0.5 * Math.sin(2.3 * sx + 0.9) * Math.cos(1.7 * sz)
			- 0.25 * Math.sin(3.8 * sz + 0.7);

		double vx = -dPsi_dz;
		double vz = dPsi_dx;

		// Channel bank alignment
		if (level != null) {
			BlockPos center = BlockPos.containing(position.x, position.y, position.z);
			int r = 3;
			boolean solidEast = !level.getFluidState(center.offset(r, 0, 0)).is(FluidTags.WATER);
			boolean solidWest = !level.getFluidState(center.offset(-r, 0, 0)).is(FluidTags.WATER);
			boolean solidSouth = !level.getFluidState(center.offset(0, 0, r)).is(FluidTags.WATER);
			boolean solidNorth = !level.getFluidState(center.offset(0, 0, -r)).is(FluidTags.WATER);

			if ((solidEast || solidWest) && !(solidNorth || solidSouth)) {
				vx *= 0.25;
				if (Math.abs(vz) < 0.1) vz = (vz >= 0) ? 1.0 : -1.0;
			} else if ((solidNorth || solidSouth) && !(solidEast || solidWest)) {
				vz *= 0.25;
				if (Math.abs(vx) < 0.1) vx = (vx >= 0) ? 1.0 : -1.0;
			}
		}

		double len = Math.sqrt(vx * vx + vz * vz);
		if (len < 1e-4) {
			vx = 0.0;
			vz = 1.0;
			len = 1.0;
		}
		double dirX = vx / len;
		double dirZ = vz / len;

		// Natural river current speed ~0.95 m/s (~0.048 blocks/tick)
		double baseSpeed = 0.95 * RiversConfig.flowSpeedMultiplier;
		double depthFactor = Mth.clamp(depth / 3.0, 0.45, 1.30);
		double streamSpeed = baseSpeed * depthFactor;

		if (level != null) {
			double bankDist = estimateBankDistance(level, BlockPos.containing(position.x, position.y, position.z), 5);
			if (bankDist < 3.0) {
				double bankFriction = Math.max(0.25, bankDist / 3.0);
				streamSpeed *= bankFriction;
			}
		}

		double heightAboveBed = Math.max(0.0, position.y - (surfaceY - depth));
		streamSpeed = RiverHydraulics.verticalVelocityProfile(heightAboveBed, depth, streamSpeed);

		double finalVx = dirX * streamSpeed;
		double finalVz = dirZ * streamSpeed;

		// Vortices and Eddies
		Vec3 vortexVel = this.vortexSolver.sampleVortexVelocity(position);
		finalVx += vortexVel.x;
		finalVz += vortexVel.z;

		// Wind drift
		if (level != null && RiversConfig.compatibilityEnableWindAndSails) {
			double relDepth = (depth > 0.1) ? Mth.clamp(heightAboveBed / depth, 0.0, 1.0) : 0.5;
			Vec3 windDrift = WindAndSailsRiverAdapter.computeSurfaceWindDrift(level, position, relDepth);
			finalVx += windDrift.x;
			finalVz += windDrift.z;
		}

		double horizSpeed = Math.sqrt(finalVx * finalVx + finalVz * finalVz);
		float flowDeg = (float) (Mth.atan2(-finalVx, finalVz) * Mth.RAD_TO_DEG);
		if (flowDeg < 0.0F) flowDeg += 360.0F;

		float vortexVort = this.vortexSolver.sampleTotalVorticity(position);
		float turbulence = RiverTurbulence.calculateTurbulence(
			streamSpeed, depth, 0.032, 0.05F, Math.abs(vortexVort) > 0.1F, false
		) * (float) RiversConfig.turbulenceMultiplier;

		double depthBelowSurface = Math.max(0.0, surfaceY - position.y);
		double pressure = RiverHydraulics.localPressure(depthBelowSurface, horizSpeed);

		return new RiverFlowSample(
			new Vec3(finalVx, 0.0, finalVz),
			horizSpeed,
			flowDeg,
			depth,
			surfaceY,
			turbulence,
			vortexVort,
			pressure,
			"procedural_reach"
		);
	}

	private static double estimateBankDistance(Level level, BlockPos pos, int maxRadius) {
		for (int r = 1; r <= maxRadius; r++) {
			for (int dx = -r; dx <= r; dx += r) {
				for (int dz = -r; dz <= r; dz += r) {
					BlockPos p = pos.offset(dx, 0, dz);
					FluidState fluid = level.getFluidState(p);
					if (!fluid.is(FluidTags.WATER) && !(fluid.getType() instanceof RiverWaterFluid)) {
						return Math.sqrt(dx * dx + dz * dz);
					}
				}
			}
		}
		return maxRadius;
	}
}
