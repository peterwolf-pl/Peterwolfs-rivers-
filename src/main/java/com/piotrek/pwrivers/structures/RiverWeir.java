package com.piotrek.pwrivers.structures;

import com.piotrek.pwrivers.physics.RiverHydraulics;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Fixed or movable river weir / dam / spillway.
 * Creates an elevated upstream pool, overflow spill, downstream hydraulic jump,
 * high turbulence, foam, and aeration.
 */
public class RiverWeir {
	private final String weirId;
	private final String weirName;
	private final Vec3 centerPos;
	private final Direction facing; // Downstream direction
	private final double width;
	private double baseCrestY;
	private double gateOffset = 0.0; // Movable gate elevation adjustment
	private double dischargeCoeff = 0.62; // Standard sharp/broad-crested weir coefficient
	private boolean movable;

	public RiverWeir(
		String weirId,
		String weirName,
		Vec3 centerPos,
		Direction facing,
		double width,
		double baseCrestY,
		boolean movable
	) {
		this.weirId = weirId;
		this.weirName = weirName;
		this.centerPos = centerPos;
		this.facing = facing;
		this.width = width;
		this.baseCrestY = baseCrestY;
		this.movable = movable;
	}

	public String getWeirId() { return this.weirId; }
	public String getWeirName() { return this.weirName; }
	public Vec3 getCenterPos() { return this.centerPos; }
	public Direction getFacing() { return this.facing; }
	public double getWidth() { return this.width; }
	public double getBaseCrestY() { return this.baseCrestY; }
	public double getEffectiveCrestY() { return this.baseCrestY + this.gateOffset; }
	public boolean isMovable() { return this.movable; }
	public double getGateOffset() { return this.gateOffset; }
	public void setGateOffset(double offset) { this.gateOffset = offset; }

	/**
	 * Computes weir overflow discharge using the Poleni/Francis equation:
	 * Q = (2/3) * C_d * b * sqrt(2 * g) * H^(3/2)
	 *
	 * @param upstreamWaterLevel Absolute Y elevation of upstream pool
	 */
	public double computeOverflowDischarge(double upstreamWaterLevel) {
		double head = upstreamWaterLevel - getEffectiveCrestY();
		if (head <= 0.0) return 0.0;

		return (2.0 / 3.0) * this.dischargeCoeff * this.width * Math.sqrt(2.0 * RiverHydraulics.GRAVITY) * Math.pow(head, 1.5);
	}

	/**
	 * Computes supercritical overflow velocity over the crest:
	 * v = sqrt(2 * g * H)
	 */
	public double computeCrestVelocity(double upstreamWaterLevel) {
		double head = upstreamWaterLevel - getEffectiveCrestY();
		if (head <= 0.0) return 0.0;
		return Math.sqrt(2.0 * RiverHydraulics.GRAVITY * head);
	}

	/**
	 * Returns true if position is in the immediate downstream plunge / hydraulic jump zone.
	 */
	public boolean isDownstreamPlungeZone(Vec3 pos) {
		double dx = pos.x - this.centerPos.x;
		double dz = pos.z - this.centerPos.z;

		// Downstream direction vector
		Vec3 down = new Vec3(this.facing.getStepX(), 0, this.facing.getStepZ());
		double along = dx * down.x + dz * down.z;
		double across = Math.abs(dx * down.z - dz * down.x);

		// Within 1 to 12 blocks downstream, within weir width
		return along >= 0.5 && along <= 12.0 && across <= (this.width * 0.5 + 1.0);
	}

	/**
	 * Downstream turbulent flow modifier adding localized plunge downward force
	 * and surface hydraulic jump boil.
	 */
	public Vec3 computeDownstreamFlowModifier(Vec3 pos, double upstreamWaterLevel) {
		if (!isDownstreamPlungeZone(pos)) return Vec3.ZERO;

		double crestVel = computeCrestVelocity(upstreamWaterLevel);
		if (crestVel < 0.1) return Vec3.ZERO;

		Vec3 down = new Vec3(this.facing.getStepX(), 0, this.facing.getStepZ());
		double dx = pos.x - this.centerPos.x;
		double dz = pos.z - this.centerPos.z;
		double distDown = dx * down.x + dz * down.z;

		// Immediately behind weir (< 3 blocks): water plunges downward
		// Further downstream (3 to 10 blocks): hydraulic jump creates upward boil and churning
		double yMotion = 0.0;
		if (distDown < 3.0) {
			yMotion = -0.35 * Math.min(2.0, crestVel);
		} else {
			yMotion = 0.15 * Math.sin(distDown * 0.8) * Math.min(1.5, crestVel);
		}

		double horizAccel = crestVel * Math.max(0.2, 1.0 - distDown / 12.0);
		return new Vec3(down.x * horizAccel, yMotion, down.z * horizAccel);
	}
}
