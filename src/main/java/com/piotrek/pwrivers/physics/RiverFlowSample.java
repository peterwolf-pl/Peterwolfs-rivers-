package com.piotrek.pwrivers.physics;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * High-fidelity hydrological sample at an exact world position (X, Y, Z).
 * Returned by {@link RiverPhysicsEngine#sampleFlow(net.minecraft.world.level.Level, Vec3)}.
 *
 * @param velocity 3D velocity vector (m/s in Minecraft blocks per tick or SI standard)
 * @param currentSpeed Scalar horizontal speed (|vx, vz|) in blocks/sec
 * @param flowDirectionDeg Direction water flows toward, in Minecraft degrees (0=South, 90=West, 180=North, 270=East)
 * @param localDepth Total water depth from surface to riverbed at this column (blocks)
 * @param waterSurfaceHeight Exact absolute Y coordinate of water surface
 * @param turbulenceIntensity Dimensionless turbulent kinetic intensity (0.0 = laminar, 1.0 = violent rapids/hydraulic jump)
 * @param vorticity Local 2D vertical-axis vorticity curl (rad/s or 1/s, positive = counterclockwise, negative = clockwise)
 * @param localPressure Hydrostatic + hydrodynamic dynamic pressure (Pa, standard atmospheric ~101325 Pa + rho*g*h)
 * @param reachId Identifier of the containing hydrological reach, or "none"
 */
public record RiverFlowSample(
	Vec3 velocity,
	double currentSpeed,
	float flowDirectionDeg,
	double localDepth,
	double waterSurfaceHeight,
	float turbulenceIntensity,
	float vorticity,
	double localPressure,
	String reachId
) {
	public static final RiverFlowSample ZERO = new RiverFlowSample(
		Vec3.ZERO,
		0.0,
		0.0F,
		0.0,
		0.0,
		0.0F,
		0.0F,
		101325.0,
		"none"
	);

	/**
	 * Linear interpolation between two flow samples.
	 */
	public static RiverFlowSample lerp(double factor, RiverFlowSample a, RiverFlowSample b) {
		float t = (float) Mth.clamp(factor, 0.0, 1.0);
		if (t <= 0.0F) return a;
		if (t >= 1.0F) return b;

		Vec3 vel = new Vec3(
			Mth.lerp(t, a.velocity.x, b.velocity.x),
			Mth.lerp(t, a.velocity.y, b.velocity.y),
			Mth.lerp(t, a.velocity.z, b.velocity.z)
		);
		double speed = Mth.lerp(t, a.currentSpeed, b.currentSpeed);
		float dir = Mth.rotLerp(t, a.flowDirectionDeg, b.flowDirectionDeg);
		double depth = Mth.lerp(t, a.localDepth, b.localDepth);
		double surface = Mth.lerp(t, a.waterSurfaceHeight, b.waterSurfaceHeight);
		float turb = Mth.lerp(t, a.turbulenceIntensity, b.turbulenceIntensity);
		float vort = Mth.lerp(t, a.vorticity, b.vorticity);
		double press = Mth.lerp(t, a.localPressure, b.localPressure);
		String reach = (t < 0.5F) ? a.reachId : b.reachId;

		return new RiverFlowSample(vel, speed, dir, depth, surface, turb, vort, press, reach);
	}

	public boolean hasCurrent() {
		return this.currentSpeed > 0.005 || Math.abs(this.velocity.y) > 0.005;
	}
}
