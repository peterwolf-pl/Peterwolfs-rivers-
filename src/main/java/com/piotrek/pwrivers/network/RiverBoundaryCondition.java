package com.piotrek.pwrivers.network;

import net.minecraft.world.phys.Vec3;

/**
 * Boundary condition for a river network (Headwater source inflow, Lake/Ocean outlet, or Fixed Stage water level).
 */
public class RiverBoundaryCondition {
	public enum Type {
		SOURCE_INFLOW,  // Constant or hydrograph inflow discharge (m^3/s)
		OUTLET_FREE,    // Free outfall into ocean/sea level
		FIXED_STAGE     // Controlled reservoir or downstream pool elevation
	}

	private final String conditionId;
	private final Vec3 position;
	private final Type type;
	private double value; // Discharge in m^3/s for SOURCE_INFLOW, or Water Level Y for FIXED_STAGE

	public RiverBoundaryCondition(String conditionId, Vec3 position, Type type, double value) {
		this.conditionId = conditionId;
		this.position = position;
		this.type = type;
		this.value = value;
	}

	public String getConditionId() { return this.conditionId; }
	public Vec3 getPosition() { return this.position; }
	public Type getType() { return this.type; }
	public double getValue() { return this.value; }
	public void setValue(double val) { this.value = val; }
}
