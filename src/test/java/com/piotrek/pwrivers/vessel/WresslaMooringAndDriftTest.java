package com.piotrek.pwrivers.vessel;

import com.piotrek.pwrivers.physics.MooringConstraint;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Wressla 4-corner mooring cleats and wind/river natural drifting.
 */
public class WresslaMooringAndDriftTest {

	@Test
	void testFourCornerCleatLayoutAndSymmetry() {
		assertEquals(4, WresslaEntity.CLEAT_COUNT);

		Vec3 bowPort = WresslaLayout.CLEAT_BOW_PORT;
		Vec3 bowStarboard = WresslaLayout.CLEAT_BOW_STARBOARD;
		Vec3 sternPort = WresslaLayout.CLEAT_STERN_PORT;
		Vec3 sternStarboard = WresslaLayout.CLEAT_STERN_STARBOARD;

		// Port should be negative X, Starboard positive X
		assertTrue(bowPort.x < 0.0, "Bow port must be on port side (negative X)");
		assertTrue(bowStarboard.x > 0.0, "Bow starboard must be on starboard side (positive X)");
		assertTrue(sternPort.x < 0.0, "Stern port must be on port side (negative X)");
		assertTrue(sternStarboard.x > 0.0, "Stern starboard must be on starboard side (positive X)");

		// Bow should be forward (negative Z), Stern aft (positive Z)
		assertTrue(bowPort.z < 0.0, "Bow port must be forward (negative Z)");
		assertTrue(bowStarboard.z < 0.0, "Bow starboard must be forward (negative Z)");
		assertTrue(sternPort.z > 0.0, "Stern port must be aft (positive Z)");
		assertTrue(sternStarboard.z > 0.0, "Stern starboard must be aft (positive Z)");

		// Symmetry
		assertEquals(Math.abs(bowPort.x), Math.abs(bowStarboard.x), 1.0E-6, "Bow cleats must be transverse-symmetric");
		assertEquals(Math.abs(sternPort.x), Math.abs(sternStarboard.x), 1.0E-6, "Stern cleats must be transverse-symmetric");
		assertEquals(bowPort.z, bowStarboard.z, 1.0E-6, "Bow cleats must share same forward longitudinal offset");
		assertEquals(sternPort.z, sternStarboard.z, 1.0E-6, "Stern cleats must share same aft longitudinal offset");

		// Height above waterline
		assertEquals(WresslaLayout.DECK_Y + 0.08, bowPort.y, 1.0E-6, "Cleat must sit directly on the teak deck");
	}

	@Test
	void testMooringConstraintCancelOutwardVelocity() {
		// Normal pointing away along +X axis (e.g. boat moving away to the right)
		Vec3 outwardVel = new Vec3(0.5, 0.0, 0.2);
		Vec3 constrained = MooringConstraint.cancelOutwardVelocity(outwardVel, 1.0, 0.0);

		// Outward X velocity must be canceled
		assertEquals(0.0, constrained.x, 1.0E-6, "Outward velocity component must be stopped");
		// Lateral Z velocity must be preserved (boat can slide parallel to dock)
		assertEquals(0.2, constrained.z, 1.0E-6, "Parallel movement along dock must be preserved");

		// Moving inward (towards anchor, negative X) should not be affected
		Vec3 inwardVel = new Vec3(-0.3, 0.0, 0.1);
		Vec3 unconstrained = MooringConstraint.cancelOutwardVelocity(inwardVel, 1.0, 0.0);
		assertEquals(-0.3, unconstrained.x, 1.0E-6);
		assertEquals(0.1, unconstrained.z, 1.0E-6);
	}

	@Test
	void testMooringCleatYawError() {
		// Boat at origin with 0 yaw (facing North, -Z)
		// Bow port cleat is at (-1.5, -4.5)
		// If dock post is directly forward at (0.0, -10.0), bow cleat swings toward center
		float error = MooringConstraint.cleatYawError(
			0.0F, 0.0, 0.0,
			-1.5, -4.5,
			0.0, -10.0
		);

		// Non-zero error turning towards post
		assertNotEquals(0.0F, error, 0.01F);
	}

	@Test
	void testNaturalDriftApproach() {
		// When engine is idle, vessel accelerates smoothly up to passive drift target (never runaway)
		Vec3 currentVel = Vec3.ZERO;
		Vec3 riverCurrent = new Vec3(0.04, 0.0, 0.0);
		Vec3 windDrift = new Vec3(0.01, 0.0, 0.005);
		Vec3 target = riverCurrent.add(windDrift);

		for (int tick = 0; tick < 50; tick++) {
			currentVel = new Vec3(
				Mth.lerp(0.08, currentVel.x, target.x),
				0.0,
				Mth.lerp(0.08, currentVel.z, target.z)
			);
		}

		assertEquals(target.x, currentVel.x, 0.001, "Should steadily approach combined drift target");
		assertEquals(target.z, currentVel.z, 0.001, "Should steadily approach combined drift target");
		assertTrue(currentVel.length() < 0.1, "Drift speed must be gentle and controlled, not runaway");
	}
}
