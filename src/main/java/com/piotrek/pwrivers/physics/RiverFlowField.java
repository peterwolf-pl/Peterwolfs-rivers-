package com.piotrek.pwrivers.physics;

import com.piotrek.pwrivers.config.RiversConfig;
import com.piotrek.pwrivers.integration.WindAndSailsRiverAdapter;
import com.piotrek.pwrivers.network.RiverNetworkGraph;
import com.piotrek.pwrivers.network.RiverReach;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Localized 2D/3D depth-averaged velocity field solver.
 * Calculates detailed velocity vectors, shear profiles, secondary circulation,
 * turbulence, and vorticity at any point in the river.
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
			return RiverFlowSample.ZERO;
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
			// If curvature > 0 (curving right), outer bank is negative offset (left bank)
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
}
