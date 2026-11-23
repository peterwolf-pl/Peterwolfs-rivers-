package com.piotrek.pwrivers.physics;

import net.minecraft.util.Mth;

/**
 * Modeling of turbulent kinetic energy, vortex dissipation, whitewater rapids,
 * and hydraulic jump turbulence downstream of weirs and sluice gates.
 */
public final class RiverTurbulence {
	private RiverTurbulence() {}

	/**
	 * Computes base turbulence intensity based on flow velocity, depth, and roughness.
	 * Dimensionless turbulence intensity I = u_rms / u_mean.
	 * Typically 0.05 - 0.15 in straight channels, 0.25 - 0.50 in bends, > 0.60 in rapids/weirs.
	 */
	public static float calculateTurbulence(
		double velocity,
		double depth,
		double manningN,
		float curvature,
		boolean nearObstacle,
		boolean downstreamStructure
	) {
		double speed = Math.abs(velocity);
		if (speed < 0.01) return 0.02F;

		// Shear Reynolds number approximation / rough boundary turbulence
		float baseTurb = (float) (0.04 + 0.12 * Math.min(1.0, speed / 2.5) + (manningN - 0.02) * 1.5);

		// Bend curvature generates additional turbulent eddies
		float bendTurb = Math.abs(curvature) * 1.8F;

		// Obstacles create boundary layer separation and wake turbulence
		float obstacleTurb = nearObstacle ? 0.35F : 0.0F;

		// Hydraulic structure (weir/sluice) produces supercritical hydraulic jump turbulence
		float structureTurb = downstreamStructure ? 0.65F : 0.0F;

		float total = baseTurb + bendTurb + obstacleTurb + structureTurb;
		return (float) Mth.clamp(total, 0.02, 1.0);
	}

	/**
	 * Returns true if turbulence is strong enough to cause whitewater, surface foam, and aeration.
	 */
	public static boolean isWhitewater(float turbulenceIntensity) {
		return turbulenceIntensity >= 0.45F;
	}

	/**
	 * Rate of foam particle spawning based on local turbulence and velocity.
	 */
	public static int foamParticleRate(float turbulenceIntensity, double speed) {
		if (turbulenceIntensity < 0.25F || speed < 0.2) return 0;
		return (int) Math.round(turbulenceIntensity * 4.0 * Math.min(2.0, speed));
	}
}
