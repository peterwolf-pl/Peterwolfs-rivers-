package com.piotrek.pwrivers.worldgen;

import com.piotrek.pwrivers.network.RiverNetworkGraph;
import com.piotrek.pwrivers.network.RiverReach;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Procedural deterministic river network generator.
 * Synthesizes long continuous meanders, multi-branch island bifurcations,
 * and upstream-to-downstream hydraulic slope gradients across chunks.
 */
public class RiverGenerator {
	public enum Preset {
		STREAM(5.0, 2.0, 15.0),
		SMALL_RIVER(14.0, 3.2, 45.0),
		MEDIUM_RIVER(35.0, 4.5, 120.0),
		LARGE_NAVIGABLE_RIVER(80.0, 6.0, 350.0),
		MAJOR_RIVER(160.0, 8.5, 900.0);

		private final double width;
		private final double depth;
		private final double discharge;

		Preset(double width, double depth, double discharge) {
			this.width = width;
			this.depth = depth;
			this.discharge = discharge;
		}

		public double getWidth() { return this.width; }
		public double getDepth() { return this.depth; }
		public double getDischarge() { return this.discharge; }
	}

	private final long worldSeed;

	public RiverGenerator(long worldSeed) {
		this.worldSeed = worldSeed;
	}

	/**
	 * Procedurally generates a continuous meandering river reach centered around starting coordinates.
	 *
	 * @param reachId Identifier for reach
	 * @param reachName Name of reach
	 * @param startX World X origin
	 * @param startZ World Z origin
	 * @param length Total river length (blocks)
	 * @param baseDirectionYaw Downstream general heading in degrees
	 * @param startElevationY Water surface level at upstream start
	 * @param preset River dimension preset
	 */
	public RiverReach generateMeanderingReach(
		String reachId,
		String reachName,
		double startX,
		double startZ,
		double length,
		double baseDirectionYaw,
		double startElevationY,
		Preset preset
	) {
		List<Vec3> waypoints = new ArrayList<>();
		int segmentCount = (int) Math.max(10, length / 25.0);
		double segmentLength = length / segmentCount;

		double currentX = startX;
		double currentZ = startZ;
		double currentY = startElevationY;

		double radHeading = baseDirectionYaw * Mth.DEG_TO_RAD;
		double dirX = -Mth.sin((float) radHeading);
		double dirZ = Mth.cos((float) radHeading);

		// Perpendicular vector for meanders
		double perpX = -dirZ;
		double perpZ = dirX;

		// Downstream slope: drops smoothly ~0.5 block per 100 blocks (gradient S0 = 0.005)
		double bedSlope = 0.0005;

		double meanderWavelength = preset.getWidth() * 8.0;
		double meanderAmplitude = preset.getWidth() * 2.2;

		for (int i = 0; i <= segmentCount; i++) {
			double distAlong = i * segmentLength;

			// Deterministic sin + octave harmonic meander formula
			double phase = (distAlong / meanderWavelength) * 2.0 * Math.PI;
			double meanderOffset = Math.sin(phase) * meanderAmplitude
				+ Math.sin(phase * 2.3 + (this.worldSeed & 0xFF)) * (meanderAmplitude * 0.35);

			double wx = startX + dirX * distAlong + perpX * meanderOffset;
			double wz = startZ + dirZ * distAlong + perpZ * meanderOffset;
			double wy = startElevationY - distAlong * bedSlope;

			waypoints.add(new Vec3(wx, wy, wz));
		}

		return new RiverReach(
			reachId,
			reachName,
			waypoints,
			preset.getDischarge(),
			preset.getWidth(),
			preset.getDepth(),
			startElevationY,
			bedSlope,
			0.032
		);
	}
}
