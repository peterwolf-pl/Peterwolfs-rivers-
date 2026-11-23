package com.piotrek.pwrivers.structures;

import com.piotrek.pwrivers.physics.RiverHydraulics;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Submerged sluice valve / culvert gate for filling and emptying lock chambers
 * or regulating bypass channels.
 */
public class RiverSluiceValve {
	private final String valveId;
	private final Vec3 position;
	private final Direction flowDirection;
	private final double orificeArea; // m^2
	private double openingFraction = 0.0; // 0.0 = fully closed, 1.0 = fully open
	private double dischargeCoefficient = 0.65;

	public RiverSluiceValve(String valveId, Vec3 position, Direction flowDirection, double orificeArea) {
		this.valveId = valveId;
		this.position = position;
		this.flowDirection = flowDirection;
		this.orificeArea = orificeArea;
	}

	public String getValveId() { return this.valveId; }
	public Vec3 getPosition() { return this.position; }
	public Direction getFlowDirection() { return this.flowDirection; }
	public double getOpeningFraction() { return this.openingFraction; }
	public void setOpeningFraction(double fraction) { this.openingFraction = Mth.clamp(fraction, 0.0, 1.0); }

	/**
	 * Computes orifice discharge: Q = C_d * A_eff * sqrt(2 * g * deltaH)
	 */
	public double computeDischarge(double headDifference) {
		if (this.openingFraction <= 0.0 || headDifference <= 0.0) return 0.0;
		double effArea = this.orificeArea * this.openingFraction;
		return this.dischargeCoefficient * effArea * Math.sqrt(2.0 * RiverHydraulics.GRAVITY * headDifference);
	}

	/**
	 * Jet velocity issuing from the orifice: v = sqrt(2 * g * deltaH)
	 */
	public double computeJetVelocity(double headDifference) {
		if (this.openingFraction <= 0.0 || headDifference <= 0.0) return 0.0;
		return Math.sqrt(2.0 * RiverHydraulics.GRAVITY * headDifference);
	}
}
