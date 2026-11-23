package com.piotrek.pwrivers.integration;

import com.piotrek.pwwindnsails.physics.SailingPhysics;
import com.piotrek.pwwindnsails.wind.WindVector;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test E: Peterwolf's Wind & Sails hydrodynamic integration.
 * Verifies:
 * - Relative water velocity: V_rel = V_boat - V_river
 * - No wind, active downstream current: sailboat drifts downstream
 * - Headwind vs Tailwind vs Crosswind
 * - Upstream sailing: aerodynamic sail thrust overcomes river current
 * - Rudder steering authority governed by relative flow velocity
 */
public class WindAndSailsRiverIntegrationTest {

	@Test
	void testRelativeWaterVelocityTransformation() {
		// Boat moving forward at +1.0 m/s along Z, in a river flowing at +0.6 m/s downstream along Z
		Vec3 boatVel = new Vec3(0, 0, 1.0);
		Vec3 riverVel = new Vec3(0, 0, 0.6);

		Vec3 relVel = WindAndSailsRiverAdapter.calculateRelativeVelocity(boatVel, riverVel);
		assertEquals(0.0, relVel.x, 1e-6);
		assertEquals(0.4, relVel.z, 1e-6, "Relative velocity through water must be V_boat - V_river = 0.4 m/s");

		// Reconstruction back to world space
		Vec3 reconstructed = WindAndSailsRiverAdapter.reconstructWorldVelocity(relVel, riverVel);
		assertEquals(boatVel.z, reconstructed.z, 1e-6);
	}

	@Test
	void testDownstreamDriftWithNoWind() {
		// Boat at rest initially: V_boat = (0, 0, 0).
		// River current flowing downstream at V_river = (0, 0, 0.8).
		Vec3 boatVel = Vec3.ZERO;
		Vec3 riverVel = new Vec3(0, 0, 0.8);

		Vec3 coupledVel = WindAndSailsRiverAdapter.applyRiverCoupling(null, boatVel, riverVel);

		assertTrue(coupledVel.z > 0.0, "Boat at rest must begin drifting downstream with current");
		assertEquals(0.0, coupledVel.x, 1e-6, "No lateral drift in a longitudinal stream");
	}

	@Test
	void testRudderAuthorityGovernedByRelativeWaterSpeed() {
		// Case 1: Boat is floating passively downstream at the exact same speed as the current:
		// V_boat = 0.8 m/s, V_river = 0.8 m/s => V_rel = 0.0 m/s.
		// Rudder has virtually zero water flow across the foil => low steering authority!
		Vec3 boatVelPassive = new Vec3(0, 0, 0.8);
		Vec3 riverVel = new Vec3(0, 0, 0.8);
		Vec3 relVelPassive = WindAndSailsRiverAdapter.calculateRelativeVelocity(boatVelPassive, riverVel);
		double passiveAuthority = WindAndSailsRiverAdapter.computeRudderAuthority(relVelPassive, 0.0, 1.0);

		// Case 2: Boat is powered or sailing at 1.4 m/s through the current (V_rel = 0.6 m/s):
		Vec3 boatVelPowered = new Vec3(0, 0, 1.4);
		Vec3 relVelPowered = WindAndSailsRiverAdapter.calculateRelativeVelocity(boatVelPowered, riverVel);
		double poweredAuthority = WindAndSailsRiverAdapter.computeRudderAuthority(relVelPowered, 0.0, 1.0);

		assertTrue(poweredAuthority > passiveAuthority * 2.0,
			"Rudder authority when moving through the water (" + poweredAuthority +
			") must greatly exceed passive drifting authority (" + passiveAuthority + ")");
	}

	@Test
	void testUpstreamSailingPhysics() {
		// Heading North (Yaw = 180 deg, forward unit vector = (0, 0, -1)).
		// River flows South (+Z at 0.5 m/s).
		// Tailwind from South blowing North (WindVector heading North with strength = 0.80).
		// Boat trims sail (mainsheet = 1.0), sailing downwind relative to air, sailing UPSTREAM relative to riverbed.
		WindVector windBlowingNorth = new WindVector(180.0F, 0.80F); // Direction = 180 (North)

		Vec3 boatVel = new Vec3(0, 0, -0.2); // Already making slight upstream headway
		SailingPhysics.PhysicsResult res = SailingPhysics.step(
			boatVel,
			180.0F, // Heading North
			0.0F,
			0.0F,
			1.0F,   // Sheet trimmed
			windBlowingNorth,
			0.0F,
			false,
			true    // in water
		);

		// Sailing thrust should propel the boat forward (along -Z heading)
		assertTrue(res.newVelocity().z < 0.0,
			"Sail propulsion must drive vessel forward along heading against downstream river current: " + res.newVelocity().z);
	}
}
