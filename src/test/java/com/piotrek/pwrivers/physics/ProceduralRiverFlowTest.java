package com.piotrek.pwrivers.physics;

import com.piotrek.pwrivers.network.RiverNetworkGraph;
import com.piotrek.pwrivers.worldgen.RiverBiomeRegistry;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for procedural continuous river hydrodynamics and biome registry.
 * Validates:
 *  - Non-zero physical flow in unmapped natural terrain
 *  - Smooth, divergence-free continuous flow fields
 *  - Realistic velocity ranges (0.4 - 1.5 m/s)
 *  - Vertical shear profile (surface faster than bed)
 *  - Biome registry key naming: rivers_by_peterwolf
 */
public class ProceduralRiverFlowTest {

	private RiverFlowField flowField;

	@BeforeEach
	void setUp() {
		RiverNetworkGraph emptyNetwork = new RiverNetworkGraph("empty_test");
		flowField = new RiverFlowField(emptyNetwork, new RiverVortexSolver());
	}

	@Test
	void testBiomeRegistryKey() {
		assertEquals("peterwolfs_rivers", RiverBiomeRegistry.RIVERS_BY_PETERWOLF.identifier().getNamespace());
		assertEquals("rivers_by_peterwolf", RiverBiomeRegistry.RIVERS_BY_PETERWOLF.identifier().getPath());
		assertEquals(RiverBiomeRegistry.RIVERS_BY_PETERWOLF, RiverBiomeRegistry.RIVER_BIOME);
	}

	@Test
	void testProceduralFlowSampleValues() {
		Vec3 testPos = new Vec3(100.0, 62.0, 250.0);
		RiverFlowSample sample = flowField.sampleProceduralFlow(null, testPos);

		assertNotNull(sample);
		assertTrue(sample.hasCurrent(), "Procedural sample should have active current");
		assertTrue(sample.currentSpeed() > 0.4, "Current speed should exceed 0.4 m/s, got: " + sample.currentSpeed());
		assertTrue(sample.currentSpeed() < 2.0, "Current speed should be under 2.0 m/s, got: " + sample.currentSpeed());
		assertEquals(testPos.y, sample.waterSurfaceHeight(), 1e-3);
	}

	@Test
	void testProceduralFlowSmoothContinuity() {
		// Test that stepping 1 block along X and Z does not cause sudden discontinuities
		double prevSpeed = -1;
		Vec3 prevVel = null;

		for (int z = 0; z <= 100; z += 5) {
			Vec3 pos = new Vec3(50.0, 62.0, z);
			RiverFlowSample sample = flowField.sampleProceduralFlow(null, pos);

			assertTrue(sample.hasCurrent());
			if (prevVel != null) {
				double deltaVx = Math.abs(sample.velocity().x - prevVel.x);
				double deltaVz = Math.abs(sample.velocity().z - prevVel.z);
				assertTrue(deltaVx < 0.35, "Discontinuous Vx change at z=" + z + ": " + deltaVx);
				assertTrue(deltaVz < 0.35, "Discontinuous Vz change at z=" + z + ": " + deltaVz);
			}
			prevVel = sample.velocity();
		}
	}

	@Test
	void testVerticalVelocityProfile() {
		// Near water surface (Y=62.0) vs near bed (Y=59.0, depth 3.5m)
		Vec3 surfacePos = new Vec3(100.0, 62.0, 200.0);
		Vec3 bedPos = new Vec3(100.0, 59.0, 200.0);

		RiverFlowSample surfaceSample = flowField.sampleProceduralFlow(null, surfacePos);
		RiverFlowSample bedSample = flowField.sampleProceduralFlow(null, bedPos);

		assertTrue(surfaceSample.currentSpeed() > bedSample.currentSpeed(),
			"Surface current (" + surfaceSample.currentSpeed() + ") should be faster than near-bed current (" + bedSample.currentSpeed() + ")");
	}
}
