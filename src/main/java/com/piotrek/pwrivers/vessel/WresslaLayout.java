package com.piotrek.pwrivers.vessel;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Geometric and spatial layout for Wressla - Historic Wrocław Tram River Catamaran.
 * Proportions:
 *  - 10.0m Length overall
 *  - 3.5m Beam
 *  - 0.50m Teak deck height above waterline
 *  - Coordinates:
 *      * local.x: port (-) / starboard (+)
 *      * local.y: height above waterline
 *      * local.z: bow / forward (-) / stern / aft (+)
 */
public final class WresslaLayout {
	private WresslaLayout() {}

	public static final double LENGTH = 10.0;
	public static final double BEAM = 3.5;
	public static final double HALF_BEAM = BEAM / 2.0;
	public static final double HALF_LENGTH = LENGTH / 2.0;
	public static final double DECK_Y = 0.50;
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

	// Steering wheel & helm console bounding box (local.z: -0.20 to 0.85)
	public static final AABB WHEEL_BOX = new AABB(-0.65, 0.60, -0.20, 0.65, 2.30, 0.85);

	// Helmsman standing position behind steering console (feet on teak deck, hips at Y=1.10)
	public static final Vec3 HELMSMAN_POS = new Vec3(0.0, DECK_Y + 0.60, -0.15);

	// Outboard motor mount and propeller positions (stern = negative Z)
	public static final Vec3 MOTOR_MOUNT_POS = new Vec3(0.0, 0.20, -5.00);
	public static final Vec3 PROPELLER_LOCAL_POS = new Vec3(0.0, -0.45, -4.85);

	// 4 Mooring Cleat positions on the 4 corners of the teak deck
	// Bow = positive Z (+4.50), Stern = negative Z (-4.50)
	// Port = negative X (-1.50), Starboard = positive X (+1.50)
	public static final Vec3 CLEAT_BOW_PORT = new Vec3(-1.50, DECK_Y + 0.08, 4.50);
	public static final Vec3 CLEAT_BOW_STARBOARD = new Vec3(1.50, DECK_Y + 0.08, 4.50);
	public static final Vec3 CLEAT_STERN_PORT = new Vec3(-1.50, DECK_Y + 0.08, -4.50);
	public static final Vec3 CLEAT_STERN_STARBOARD = new Vec3(1.50, DECK_Y + 0.08, -4.50);

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
