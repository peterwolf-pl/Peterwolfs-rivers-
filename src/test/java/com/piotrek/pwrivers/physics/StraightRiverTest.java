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
 * Test A: 500-block-long straight river.
 * Verifies:
 * - Continuous downstream flow
 * - Slower water near banks (parabolic no-slip boundary layer)
 * - Maximum velocity in fairway centerline
 * - Stable water levels
 * - Absence of spurious vortices in straight channel
 * - Smooth transition across 16-block chunk intervals
 */
public class StraightRiverTest {
	private RiverNetworkGraph network;
	private RiverVortexSolver vortexSolver;
	private RiverFlowField flowField;

	@BeforeEach
	void setUp() {
		this.network = new RiverNetworkGraph("test_straight_river");
		this.vortexSolver = new RiverVortexSolver();
		this.flowField = new RiverFlowField(this.network, this.vortexSolver);

		// 500-block long straight river flowing South along Z-axis (X = 0, Z = 0 to 500)
		List<Vec3> waypoints = new ArrayList<>();
		for (int z = 0; z <= 500; z += 50) {
			waypoints.add(new Vec3(0, 64.0, z));
		}

		RiverReach reach = new RiverReach(
			"reach_straight",
			"Straight River 500m",
			waypoints,
			150.0, // Q = 150 m^3/s
			40.0,  // width = 40m (half-width = 20m)
			5.0,   // depth = 5m
			64.0,  // water surface Y = 64
			0.0005,
			0.030
		);
		this.network.addReach(reach);
	}

	@Test
	void testCenterlineFlowDirectionAndSpeed() {
		// Sample at multiple points along the 500m channel at Z = 50, 150, 250, 350, 450
		for (int z = 50; z <= 450; z += 100) {
			Vec3 pos = new Vec3(0, 63.5, z); // Centerline near surface
			RiverFlowSample sample = this.flowField.sampleFlow(null, pos);

			assertTrue(sample.hasCurrent(), "Centerline must have active downstream flow at Z=" + z);
			assertTrue(sample.velocity().z > 0.4, "Water must flow downstream (+Z) at Z=" + z);
			assertEquals(0.0, sample.velocity().x, 0.05, "Straight channel must have minimal lateral velocity");
			assertEquals(0.0F, sample.flowDirectionDeg(), 1.0F, "Heading +Z must match Minecraft 0 degrees (South)");
			assertEquals(64.0, sample.waterSurfaceHeight(), 0.01, "Water surface elevation must be stable");
		}
	}

	@Test
	void testTransverseVelocityProfileAndBankFriction() {
		// Across channel at Z = 250:
		// X = 0 (center), X = 10 (half-way), X = 18 (near bank, half-width=20), X = 25 (outside)
		RiverFlowSample center = this.flowField.sampleFlow(null, new Vec3(0, 63.5, 250));
		RiverFlowSample mid = this.flowField.sampleFlow(null, new Vec3(10, 63.5, 250));
		RiverFlowSample nearBank = this.flowField.sampleFlow(null, new Vec3(18, 63.5, 250));
		RiverFlowSample outside = this.flowField.sampleFlow(null, new Vec3(25, 63.5, 250));

		assertTrue(center.currentSpeed() > mid.currentSpeed(),
			"Center velocity (" + center.currentSpeed() + ") must exceed mid-channel (" + mid.currentSpeed() + ")");
		assertTrue(mid.currentSpeed() > nearBank.currentSpeed(),
			"Mid-channel velocity (" + mid.currentSpeed() + ") must exceed near-bank (" + nearBank.currentSpeed() + ")");
		assertFalse(outside.hasCurrent(), "Outside the channel banks, current speed must be zero");
	}

	@Test
	void testNoSpuriousVorticesInStraightChannel() {
		for (int z = 50; z <= 450; z += 50) {
			RiverFlowSample sample = this.flowField.sampleFlow(null, new Vec3(0, 63.5, z));
			assertEquals(0.0F, sample.vorticity(), 0.001F,
				"Straight channel without obstacles must have zero spurious vorticity");
		}
	}

	@Test
	void testSmoothChunkBoundaryTransitions() {
		// Sample across chunk boundary at Z = 15.9 and Z = 16.1
		RiverFlowSample beforeChunk = this.flowField.sampleFlow(null, new Vec3(0, 63.5, 15.9));
		RiverFlowSample afterChunk = this.flowField.sampleFlow(null, new Vec3(0, 63.5, 16.1));

		assertEquals(beforeChunk.currentSpeed(), afterChunk.currentSpeed(), 0.05,
			"Velocity across chunk boundaries must be smooth and continuous");
		assertEquals(beforeChunk.waterSurfaceHeight(), afterChunk.waterSurfaceHeight(), 0.01,
			"Water surface across chunk boundaries must be seamless");
	}
}
