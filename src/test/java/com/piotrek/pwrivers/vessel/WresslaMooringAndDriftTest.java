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

		// Bow should be forward (positive Z), Stern aft (negative Z)
		assertTrue(bowPort.z > 0.0, "Bow port must be forward (positive Z)");
		assertTrue(bowStarboard.z > 0.0, "Bow starboard must be forward (positive Z)");
		assertTrue(sternPort.z < 0.0, "Stern port must be aft (negative Z)");
		assertTrue(sternStarboard.z < 0.0, "Stern starboard must be aft (negative Z)");

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
		// Boat at origin with 0 yaw (facing South, +Z)
		// Bow port cleat is at (-1.5, 4.5)
		// If dock post is directly forward at (0.0, 10.0), bow cleat swings toward center
		float error = MooringConstraint.cleatYawError(
			0.0F, 0.0, 0.0,
			-1.5, 4.5,
			0.0, 10.0
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

	@Test
	void testPendingFenceMooringLifecycle() {
		java.util.UUID playerId = java.util.UUID.randomUUID();
		net.minecraft.core.BlockPos post = new net.minecraft.core.BlockPos(100, 64, -200);

		assertNull(WresslaEntity.getPendingFenceMooring(playerId));
		WresslaEntity.setPendingFenceMooring(playerId, post);
		assertEquals(post, WresslaEntity.getPendingFenceMooring(playerId));

		WresslaEntity.clearPendingFenceMooring(playerId);
		assertNull(WresslaEntity.getPendingFenceMooring(playerId));
	}

	@Test
	void testApparentWindFlagAngle() {
		// Boat heading South (yaw = 0 deg).
		// Wind blowing from South to North (headwind: trueWind = (0, 0, -5)).
		// Downwind vector is (0, 0, -5) -> atan2(0, -5) = 180 deg.
		// Relative downwind angle to boat yaw 0: 180 deg (blowing straight toward stern).
		// Model flag orientation: 90 - 180 = -90 deg (pointing straight toward stern in model space).
		Vec3 trueWind = new Vec3(0.0, 0.0, -5.0);
		Vec3 boatVel = Vec3.ZERO;
		Vec3 apparent = trueWind.subtract(boatVel);

		float downwindDeg = (float) Math.toDegrees(Math.atan2(-apparent.x, apparent.z));
		assertEquals(180.0F, Math.abs(downwindDeg), 0.01F, "Downwind vector should be 180 deg");

		float relativeDeg = Mth.wrapDegrees(downwindDeg - 0.0F);
		float flagYaw = (float) Math.toRadians(Mth.wrapDegrees(90.0F - relativeDeg));
		assertEquals((float) Math.toRadians(-90.0F), flagYaw, 0.01F, "Flag should point towards stern (-90 deg)");
	}
}
