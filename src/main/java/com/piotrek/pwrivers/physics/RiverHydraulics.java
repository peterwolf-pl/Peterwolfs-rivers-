package com.piotrek.pwrivers.physics;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Computational hydraulics formulas based on open channel flow mechanics:
 * Manning's equation, continuity equation, cross-sectional boundary layers,
 * backwater curves, and vertical velocity profiles.
 */
public final class RiverHydraulics {
	public static final double GRAVITY = 9.80665;
	public static final double WATER_DENSITY = 1000.0; // kg/m^3
	public static final double ATMOSPHERIC_PRESSURE = 101325.0; // Pa

	private RiverHydraulics() {}

	/**
	 * Computes cross-sectional area for a wide trapezoidal/rectangular channel.
	 */
	public static double channelArea(double width, double depth) {
		return Math.max(0.1, width) * Math.max(0.1, depth);
	}

	/**
	 * Computes wetted perimeter P_w = width + 2 * depth.
	 */
	public static double wettedPerimeter(double width, double depth) {
		return Math.max(0.1, width) + 2.0 * Math.max(0.1, depth);
	}

	/**
	 * Hydraulic radius R_h = A / P_w.
	 */
	public static double hydraulicRadius(double width, double depth) {
		return channelArea(width, depth) / wettedPerimeter(width, depth);
	}

	/**
	 * Computes mean flow velocity using Manning's open channel resistance formula:
	 * v = (1 / n) * (R_h)^(2/3) * (S_0)^(1/2)
	 *
	 * @param manningN Roughness coefficient (e.g. 0.030 for clean natural channels, 0.045 for winding rivers with gravel)
	 * @param hydraulicRadius Hydraulic radius (m)
	 * @param bedSlope Energy/bed gradient slope (m/m, dimensionless)
	 * @return Mean velocity in m/s
	 */
	public static double manningVelocity(double manningN, double hydraulicRadius, double bedSlope) {
		double n = Math.max(0.01, manningN);
		double s = Math.max(0.00005, bedSlope);
		double rh = Math.max(0.05, hydraulicRadius);
		return (1.0 / n) * Math.pow(rh, 2.0 / 3.0) * Math.sqrt(s);
	}

	/**
	 * Computes cross-sectional discharge Q = v * A (m^3/s).
	 */
	public static double discharge(double velocity, double width, double depth) {
		return velocity * channelArea(width, depth);
	}

	/**
	 * Solves for flow acceleration in a constricted/narrow channel based on mass conservation:
	 * Q = v1 * A1 = v2 * A2  =>  v2 = Q / A2
	 */
	public static double constrictedVelocity(double discharge, double localWidth, double localDepth) {
		double area = channelArea(localWidth, localDepth);
		return discharge / area;
	}

	/**
	 * Transverse velocity distribution across river width:
	 * Parabolic boundary layer model with no-slip condition (v -> 0 at banks)
	 * and peak velocity in the fairway/centerline.
	 *
	 * @param normalizedOffset Offset from center normalized to [-1.0, 1.0] where -1 = left bank, +1 = right bank
	 * @param centerlineVelocity Peak velocity at center
	 * @param bankFrictionFactor Factor determining residual bank velocity (0.10 to 0.25)
	 */
	public static double transverseVelocityProfile(double normalizedOffset, double centerlineVelocity, double bankFrictionFactor) {
		double eta = Mth.clamp(Math.abs(normalizedOffset), 0.0, 1.0);
		// Parabolic profile: (1 - eta^2)
		double profile = (1.0 - eta * eta);
		// Scale between bank slip and center peak
		return centerlineVelocity * (bankFrictionFactor + (1.0 - bankFrictionFactor) * profile);
	}

	/**
	 * Vertical velocity profile from riverbed to surface.
	 * Follows the 1/6th power law: v(z) = v_surface * (z / depth)^(1/6).
	 * Friction from the riverbed slows water down near bottom; maximum velocity occurs near surface.
	 *
	 * @param heightAboveBed Vertical height above riverbed (0 = bed, depth = surface)
	 * @param totalDepth Total depth of water column
	 * @param surfaceVelocity Velocity at water surface
	 */
	public static double verticalVelocityProfile(double heightAboveBed, double totalDepth, double surfaceVelocity) {
		if (totalDepth <= 0.05) return surfaceVelocity * 0.5;
		double zRel = Mth.clamp(heightAboveBed / totalDepth, 0.02, 1.0);
		// 1/6th power law:
		double profile = Math.pow(zRel, 1.0 / 6.0);
		return surfaceVelocity * profile;
	}

	/**
	 * Computes hydrostatic + dynamic Bernoulli pressure at a given depth.
	 */
	public static double localPressure(double depthBelowSurface, double currentSpeed) {
		double hydrostatic = WATER_DENSITY * GRAVITY * Math.max(0.0, depthBelowSurface);
		double dynamic = 0.5 * WATER_DENSITY * currentSpeed * currentSpeed;
		return ATMOSPHERIC_PRESSURE + hydrostatic + dynamic;
	}
}
