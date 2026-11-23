package com.piotrek.pwrivers.vessel;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Geometric and spatial layout for Wressla - Historic Wrocław Tram River Catamaran.
 * Proportions:
 *  - 10.0m Length overall (160 px)
 *  - 3.5m Beam (56 px)
 *  - 0.60m Upright draft
 *  - 0.50m Deck height above waterline
 *  - Twin modular segmented pontoons with central tunnel
 *  - Outboard engine mounted on stern transom bracket (Z = +5.0m)
 *  - Helm console and steering wheel located at Z = -0.60m
 */
public final class WresslaLayout {
	private WresslaLayout() {}

	public static final double LENGTH = 10.0;
	public static final double BEAM = 3.5;
	public static final double HALF_BEAM = BEAM / 2.0; // 1.75m
	public static final double HALF_LENGTH = LENGTH / 2.0; // 5.0m
	public static final double DECK_Y = 0.50; // Metres above waterline
	public static final double DRAFT = 0.60;

	// Twin floats
	public static final double FLOAT_WIDTH = 0.75;
	public static final double FLOAT_PORT_X = -1.125;
	public static final double FLOAT_STARBOARD_X = 1.125;

	// Walking deck area
	public static final double DECK_MIN_X = -1.75;
	public static final double DECK_MAX_X = 1.75;
	public static final double DECK_MIN_Z = -4.80;
	public static final double DECK_MAX_Z = 4.80;

	// Steering wheel bounding box in local vehicle coordinates (Z- forward, Z+ aft)
	public static final AABB WHEEL_BOX = new AABB(-0.35, 1.15, -0.85, 0.35, 1.95, -0.35);

	// Helmsman standing position behind steering console
	public static final Vec3 HELMSMAN_POS = new Vec3(0.0, DECK_Y, -0.15);

	// Outboard motor mount and propeller positions
	public static final Vec3 MOTOR_MOUNT_POS = new Vec3(0.0, 0.20, 5.00);
	public static final Vec3 PROPELLER_LOCAL_POS = new Vec3(0.0, -0.45, 4.85);

	public static boolean isWithinDeckBounds(double localX, double localZ) {
		return localX >= DECK_MIN_X && localX <= DECK_MAX_X && localZ >= DECK_MIN_Z && localZ <= DECK_MAX_Z;
	}

	public static double getDeckHeightAt(double localX, double localZ) {
		if (isWithinDeckBounds(localX, localZ)) {
			return DECK_Y;
		}
		return Double.NaN;
	}
}
