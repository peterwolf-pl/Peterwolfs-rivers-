package com.piotrek.pwrivers.physics;

import com.piotrek.pwrivers.network.RiverNetworkGraph;
import com.piotrek.pwrivers.network.RiverReach;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test C: Narrow passage / gorge acceleration.
 * Verifies:
 * - Flow acceleration when channel width is constricted by 50%
 * - Conservation of mass flow / continuity (Q = v * A)
 * - Absence of unstable velocity spikes
 */
public class NarrowPassageContinuityTest {
	private RiverNetworkGraph network;
	private RiverFlowField flowField;

	@BeforeEach
	void setUp() {
		this.network = new RiverNetworkGraph("test_narrow_passage");
		this.flowField = new RiverFlowField(this.network, new RiverVortexSolver());

		// Reach 1: Wide River section (Width = 60m, Depth = 5m, Q = 180 m^3/s)
		List<Vec3> widePts = new ArrayList<>();
		widePts.add(new Vec3(0, 64.0, 0));
		widePts.add(new Vec3(0, 64.0, 100));

		RiverReach wideReach = new RiverReach(
			"reach_wide",
			"Wide River Section",
			widePts,
			180.0,
			60.0, // Area = 300 m^2 => mean vel = 0.60 m/s
			5.0,
			64.0,
			0.0004,
			0.030
		);
		this.network.addReach(wideReach);

		// Reach 2: Constricted Gorge / Bridge Passage (Width = 30m, 50% constriction)
		List<Vec3> gorgePts = new ArrayList<>();
		gorgePts.add(new Vec3(0, 64.0, 100));
		gorgePts.add(new Vec3(0, 64.0, 200));

		RiverReach gorgeReach = new RiverReach(
			"reach_gorge",
			"Constricted Gorge (50% Width)",
			gorgePts,
			180.0, // Conserved Q = 180 m^3/s
			30.0, // Area = 150 m^2 => mean vel = 1.20 m/s (2x faster)
			5.0,
			64.0,
			0.0004,
			0.030
		);
		this.network.addReach(gorgeReach);
	}

	@Test
	void testContinuityAndFlowAcceleration() {
		// Sample centerline in wide section (Z = 50) and gorge section (Z = 150)
		RiverFlowSample wideSample = this.flowField.sampleFlow(null, new Vec3(0, 63.5, 50));
		RiverFlowSample gorgeSample = this.flowField.sampleFlow(null, new Vec3(0, 63.5, 150));

		assertTrue(wideSample.hasCurrent());
		assertTrue(gorgeSample.hasCurrent());

		// In constricted section with half the width, velocity should approximately double
		double ratio = gorgeSample.currentSpeed() / wideSample.currentSpeed();
		assertEquals(2.0, ratio, 0.25,
			"Narrowing channel width by 50% must accelerate flow speed by ~2x due to mass conservation");

		// Ensure velocity remains bounded and physically realistic (no explosive spikes)
		assertTrue(gorgeSample.currentSpeed() < 5.0,
			"Constricted velocity must remain numerically stable and bounded");
	}

	@Test
	void testManningHydraulicVelocityFormula() {
		// Verify Manning formula directly
		double v1 = RiverHydraulics.manningVelocity(0.030, 4.0, 0.0005);
		assertTrue(v1 > 0.5 && v1 < 2.5, "Manning velocity for typical river reach should be realistic: " + v1);

		// Discharges match continuity
		double qWide = RiverHydraulics.discharge(0.60, 60.0, 5.0);
		double qGorge = RiverHydraulics.discharge(1.20, 30.0, 5.0);
		assertEquals(qWide, qGorge, 1e-4, "Discharge must be identically conserved across constrictions");
	}
}
