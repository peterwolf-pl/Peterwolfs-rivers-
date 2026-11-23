package com.piotrek.pwrivers.physics;

import com.piotrek.pwrivers.network.RiverJunction;
import com.piotrek.pwrivers.network.RiverNetworkGraph;
import com.piotrek.pwrivers.network.RiverReach;
import com.piotrek.pwrivers.structures.RiverSluiceValve;
import com.piotrek.pwrivers.structures.RiverWeir;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test G: Hydraulic mass conservation, weir discharge, and orifice mechanics.
 * Verifies:
 * - Island bifurcation discharge conservation (Q_main = Q_branch1 + Q_branch2)
 * - Poleni/Francis weir discharge scaling (Q ~ H^(3/2))
 * - Sluice valve orifice scaling (Q ~ sqrt(deltaH))
 * - Local hydrostatic & Bernoulli pressure approximation
 */
public class HydraulicMassConservationTest {

	@Test
	void testIslandBifurcationDischargeConservation() {
		RiverNetworkGraph network = new RiverNetworkGraph("bifurcation_net");

		List<Vec3> dummyPts = new ArrayList<>();
		dummyPts.add(new Vec3(0, 64, 0));
		dummyPts.add(new Vec3(0, 64, 50));

		// Main Inflow Reach: Q = 200 m^3/s, width = 60m
		RiverReach inReach = new RiverReach("inflow", "Main River", dummyPts, 200.0, 60.0, 5.0, 64.0, 0.0005, 0.030);
		network.addReach(inReach);

		// Branch 1 (North, 60% of width = 36m)
		RiverReach b1 = new RiverReach("branch_1", "North Branch", dummyPts, 0.0, 36.0, 4.5, 64.0, 0.0005, 0.030);
		network.addReach(b1);

		// Branch 2 (South, 40% of width = 24m)
		RiverReach b2 = new RiverReach("branch_2", "South Branch", dummyPts, 0.0, 24.0, 4.0, 64.0, 0.0005, 0.030);
		network.addReach(b2);

		RiverJunction bifurcation = new RiverJunction("island_split", new Vec3(0, 64, 50), RiverJunction.Type.BIFURCATION);
		bifurcation.addIncomingReach("inflow");
		bifurcation.addOutgoingReach("branch_1");
		bifurcation.addOutgoingReach("branch_2");
		network.addJunction(bifurcation);

		// Solve mass conservation
		network.solveNetworkHydraulics();

		assertEquals(120.0, b1.getTargetDischarge(), 0.1, "Branch 1 (36m/60m) must receive 60% of inflow (120 m^3/s)");
		assertEquals(80.0, b2.getTargetDischarge(), 0.1, "Branch 2 (24m/60m) must receive 40% of inflow (80 m^3/s)");
		assertEquals(200.0, b1.getTargetDischarge() + b2.getTargetDischarge(), 1e-4,
			"Sum of branch discharges must identically conserve total inflow discharge");
	}

	@Test
	void testWeirOverflowFrancisScaling() {
		RiverWeir weir = new RiverWeir("weir_test", "Test Weir", new Vec3(0, 64, 0), Direction.SOUTH, 20.0, 63.0, false);

		// Head = 1.0m (upstream level = 64.0, crest = 63.0)
		double q1 = weir.computeOverflowDischarge(64.0);

		// Head = 2.0m (upstream level = 65.0, crest = 63.0)
		double q2 = weir.computeOverflowDischarge(65.0);

		assertTrue(q1 > 0.0, "Weir with 1.0m overflow head must discharge water");
		assertTrue(q2 > q1, "Higher head must yield higher overflow discharge");

		// Francis scaling: Q2 / Q1 should equal (2.0 / 1.0)^(1.5) = 2.828
		double ratio = q2 / q1;
		assertEquals(Math.pow(2.0, 1.5), ratio, 0.05,
			"Weir overflow must adhere to the physical H^(3/2) Poleni/Francis scaling law");
	}

	@Test
	void testSluiceValveOrificeTorricelliScaling() {
		RiverSluiceValve valve = new RiverSluiceValve("valve_test", new Vec3(0, 60, 0), Direction.SOUTH, 2.0);
		valve.setOpeningFraction(1.0);

		// Head difference deltaH = 1.0m vs deltaH = 4.0m
		double q1 = valve.computeDischarge(1.0);
		double q4 = valve.computeDischarge(4.0);

		// Torricelli scaling: Q ~ sqrt(deltaH) => Q4 / Q1 = sqrt(4.0 / 1.0) = 2.0
		double ratio = q4 / q1;
		assertEquals(2.0, ratio, 0.05,
			"Sluice orifice flow must scale with the square root of hydraulic head difference");
	}

	@Test
	void testBernoulliAndHydrostaticPressure() {
		// Pressure at surface with speed = 0 should be atmospheric pressure (~101325 Pa)
		double pSurface = RiverHydraulics.localPressure(0.0, 0.0);
		assertEquals(RiverHydraulics.ATMOSPHERIC_PRESSURE, pSurface, 1.0);

		// Pressure at depth = 5.0m:
		// Hydrostatic = 1000 * 9.80665 * 5.0 = 49033.25 Pa
		// Dynamic (v = 2.0 m/s) = 0.5 * 1000 * 4.0 = 2000 Pa
		double pDepth = RiverHydraulics.localPressure(5.0, 2.0);
		double expected = RiverHydraulics.ATMOSPHERIC_PRESSURE + 49033.25 + 2000.0;
		assertEquals(expected, pDepth, 5.0, "Pressure calculation must combine hydrostatic and dynamic Bernoulli head");
	}
}
