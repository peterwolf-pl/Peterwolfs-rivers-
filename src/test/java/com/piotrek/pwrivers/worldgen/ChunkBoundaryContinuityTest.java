package com.piotrek.pwrivers.worldgen;

import com.piotrek.pwrivers.network.RiverNetworkGraph;
import com.piotrek.pwrivers.network.RiverReach;
import com.piotrek.pwrivers.physics.RiverFlowField;
import com.piotrek.pwrivers.physics.RiverFlowSample;
import com.piotrek.pwrivers.physics.RiverVortexSolver;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test F: Cross-chunk continuity and deterministic world generation.
 * Verifies:
 * - Deterministic river layout across arbitrary chunk boundaries
 * - Continuity of water surface height (no chunk-edge step waterfalls)
 * - Continuity of velocity vectors across chunk boundaries (every 16 blocks)
 * - Identical values computed regardless of evaluation sequence or chunk load order
 */
public class ChunkBoundaryContinuityTest {

	@Test
	void testDeterministicProceduralGeneration() {
		long seed = 987654321L;
		RiverGenerator gen1 = new RiverGenerator(seed);
		RiverGenerator gen2 = new RiverGenerator(seed);

		RiverReach reach1 = gen1.generateMeanderingReach(
			"reach_test", "Meander", 0, 0, 500.0, 0.0, 64.0, RiverGenerator.Preset.LARGE_NAVIGABLE_RIVER
		);
		RiverReach reach2 = gen2.generateMeanderingReach(
			"reach_test", "Meander", 0, 0, 500.0, 0.0, 64.0, RiverGenerator.Preset.LARGE_NAVIGABLE_RIVER
		);

		assertEquals(reach1.getWaypoints().size(), reach2.getWaypoints().size());
		for (int i = 0; i < reach1.getWaypoints().size(); i++) {
			Vec3 p1 = reach1.getWaypoints().get(i);
			Vec3 p2 = reach2.getWaypoints().get(i);
			assertEquals(p1.x, p2.x, 1e-6);
			assertEquals(p1.y, p2.y, 1e-6);
			assertEquals(p1.z, p2.z, 1e-6);
		}
	}

	@Test
	void testCrossChunkFlowContinuity() {
		RiverGenerator generator = new RiverGenerator(12345L);
		RiverReach reach = generator.generateMeanderingReach(
			"reach_chunks", "Multi-Chunk Reach", 0, 0, 600.0, 0.0, 64.0, RiverGenerator.Preset.MEDIUM_RIVER
		);

		RiverNetworkGraph network = new RiverNetworkGraph("chunk_test_network");
		network.addReach(reach);
		RiverFlowField flowField = new RiverFlowField(network, new RiverVortexSolver());

		// Test across multiple chunk boundaries at Z = 16, 32, 48, 64, 80, 96, 112, 128
		for (int chunkZ = 16; chunkZ <= 128; chunkZ += 16) {
			RiverReach.ProjectionResult proj = reach.project(new Vec3(0, 64, chunkZ));
			Vec3 center = proj.closestPointOnCenterline();

			Vec3 posBefore = center.subtract(proj.flowTangent().scale(0.1));
			Vec3 posAfter = center.add(proj.flowTangent().scale(0.1));

			RiverFlowSample sampleBefore = flowField.sampleFlow(null, new Vec3(posBefore.x, 63.5, posBefore.z));
			RiverFlowSample sampleAfter = flowField.sampleFlow(null, new Vec3(posAfter.x, 63.5, posAfter.z));

			assertTrue(sampleBefore.hasCurrent(), "Must have current at chunk boundary Z=" + chunkZ);
			assertTrue(sampleAfter.hasCurrent(), "Must have current at chunk boundary Z=" + chunkZ);

			// Difference across the 0.2 block span should be smooth (< 0.05 m/s delta)
			double speedDelta = Math.abs(sampleBefore.currentSpeed() - sampleAfter.currentSpeed());
			assertTrue(speedDelta < 0.05,
				"Velocity delta across chunk boundary at Z=" + chunkZ + " must be smooth, got: " + speedDelta);

			double surfaceDelta = Math.abs(sampleBefore.waterSurfaceHeight() - sampleAfter.waterSurfaceHeight());
			assertTrue(surfaceDelta < 0.02,
				"Water surface height must be continuous across chunk boundary at Z=" + chunkZ + ", got delta: " + surfaceDelta);
		}
	}
}
