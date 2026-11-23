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
 * Test B: 90-degree river bend.
 * Verifies:
 * - Velocity variation across the channel (outer bank acceleration vs inner bank slowdown)
 * - Local turbulence generation in curved channel
 * - Secondary helical flow approximation (transverse deflection)
 * - Detectable vorticity and recirculation
 */
public class SharpBendRiverTest {
	private RiverNetworkGraph network;
	private RiverVortexSolver vortexSolver;
	private RiverFlowField flowField;

	@BeforeEach
	void setUp() {
		this.network = new RiverNetworkGraph("test_sharp_bend");
		this.vortexSolver = new RiverVortexSolver();
		this.flowField = new RiverFlowField(this.network, this.vortexSolver);

		// 90-degree bend: approaches from (0, 64, -100) to (0, 64, 0), then curves towards (+100, 64, 100)
		List<Vec3> waypoints = new ArrayList<>();
		waypoints.add(new Vec3(0, 64.0, -100));
		waypoints.add(new Vec3(0, 64.0, 0));
		waypoints.add(new Vec3(25, 64.0, 40));
		waypoints.add(new Vec3(60, 64.0, 75));
		waypoints.add(new Vec3(100, 64.0, 85));
		waypoints.add(new Vec3(200, 64.0, 85));

		RiverReach bendReach = new RiverReach(
			"reach_bend",
			"90 Degree Bend Reach",
			waypoints,
			160.0,
			40.0,  // half-width = 20m
			5.0,
			64.0,
			0.0006,
			0.035
		);
		this.network.addReach(bendReach);

		// Add bend vortex eddy in apex recirculation zone
		this.vortexSolver.addVortex(new RiverVortexSolver.VortexCore(
			new Vec3(70, 64.0, 60),
			4.0,
			1.8,
			999999,
			true
		));
	}

	@Test
	void testOuterVsInnerBankVelocitySkew() {
		// At bend waypoint (25, 64, 40):
		// Right turn means outer bank is on the left (-normal), inner bank on the right (+normal)
		// Sample outer fairway vs inner fairway at same longitudinal station
		RiverReach reach = this.network.getReach("reach_bend");
		assertNotNull(reach);

		RiverReach.ProjectionResult proj = reach.project(new Vec3(25, 64, 40));
		assertTrue(Math.abs(proj.curvature()) > 0.001F, "Curvature must be detected at bend");

		Vec3 center = proj.closestPointOnCenterline();
		Vec3 normal = proj.normalToBank();

		// Outer bank position (offset -10m) and inner bank position (offset +10m)
		Vec3 outerPos = center.subtract(normal.scale(10.0));
		Vec3 innerPos = center.add(normal.scale(10.0));

		RiverFlowSample outerSample = this.flowField.sampleFlow(null, new Vec3(outerPos.x, 63.5, outerPos.z));
		RiverFlowSample innerSample = this.flowField.sampleFlow(null, new Vec3(innerPos.x, 63.5, innerPos.z));

		assertTrue(outerSample.hasCurrent());
		assertTrue(innerSample.hasCurrent());
		assertTrue(outerSample.currentSpeed() > innerSample.currentSpeed(),
			"Outer bank speed (" + outerSample.currentSpeed() + ") must exceed inner bank speed (" + innerSample.currentSpeed() + ") in bend");
	}

	@Test
	void testBendTurbulenceIntensity() {
		// Bend sample should have elevated turbulence compared to straight inflow
		RiverFlowSample inflow = this.flowField.sampleFlow(null, new Vec3(0, 63.5, -50));
		RiverFlowSample apex = this.flowField.sampleFlow(null, new Vec3(60, 63.5, 75));

		assertTrue(apex.turbulenceIntensity() > inflow.turbulenceIntensity(),
			"Turbulence intensity at bend apex must exceed straight reach inflow");
	}

	@Test
	void testVorticityAndRecirculation() {
		// Sample inside the apex vortex zone
		RiverFlowSample eddySample = this.flowField.sampleFlow(null, new Vec3(70, 63.5, 60));
		assertTrue(Math.abs(eddySample.vorticity()) > 0.01F,
			"Apex recirculation zone must exhibit measurable vorticity");
	}
}
