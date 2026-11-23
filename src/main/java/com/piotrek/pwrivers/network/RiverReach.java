package com.piotrek.pwrivers.network;

import com.piotrek.pwrivers.physics.RiverHydraulics;
import com.piotrek.pwrivers.physics.RiverTurbulence;
import com.piotrek.pwrivers.physics.RiverVortexSolver;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A continuous reach of a river bounded between two junctions or hydraulic structures.
 * Modeled as a 3D centerline spline with cross-sectional channel geometry.
 */
public class RiverReach {
	private final String reachId;
	private final String reachName;
	private final List<Vec3> centerlineWaypoints = new ArrayList<>();
	private double targetDischarge; // m^3/s
	private double baseWidth;       // blocks
	private double baseDepth;       // blocks
	private double waterSurfaceY;   // reference Y level
	private double bedSlope;        // dimensionless m/m
	private double manningN;        // roughness
	private String upstreamReachId;
	private String downstreamReachId;
	private AABB boundingBox;

	public RiverReach(
		String reachId,
		String reachName,
		List<Vec3> waypoints,
		double targetDischarge,
		double baseWidth,
		double baseDepth,
		double waterSurfaceY,
		double bedSlope,
		double manningN
	) {
		this.reachId = reachId;
		this.reachName = reachName;
		this.centerlineWaypoints.addAll(waypoints);
		this.targetDischarge = targetDischarge;
		this.baseWidth = baseWidth;
		this.baseDepth = baseDepth;
		this.waterSurfaceY = waterSurfaceY;
		this.bedSlope = bedSlope;
		this.manningN = manningN;
		this.recalculateBounds();
	}

	public String getReachId() { return this.reachId; }
	public String getReachName() { return this.reachName; }
	public List<Vec3> getWaypoints() { return Collections.unmodifiableList(this.centerlineWaypoints); }
	public double getTargetDischarge() { return this.targetDischarge; }
	public void setTargetDischarge(double q) { this.targetDischarge = Math.max(0.1, q); }
	public double getBaseWidth() { return this.baseWidth; }
	public void setBaseWidth(double w) { this.baseWidth = Math.max(1.0, w); recalculateBounds(); }
	public double getBaseDepth() { return this.baseDepth; }
	public void setBaseDepth(double d) { this.baseDepth = Math.max(0.5, d); }
	public double getWaterSurfaceY() { return this.waterSurfaceY; }
	public void setWaterSurfaceY(double y) { this.waterSurfaceY = y; recalculateBounds(); }
	public double getBedSlope() { return this.bedSlope; }
	public double getManningN() { return this.manningN; }
	public String getUpstreamReachId() { return this.upstreamReachId; }
	public void setUpstreamReachId(String id) { this.upstreamReachId = id; }
	public String getDownstreamReachId() { return this.downstreamReachId; }
	public void setDownstreamReachId(String id) { this.downstreamReachId = id; }
	public AABB getBoundingBox() { return this.boundingBox; }

	public void recalculateBounds() {
		if (this.centerlineWaypoints.isEmpty()) {
			this.boundingBox = new AABB(0, 0, 0, 0, 0, 0);
			return;
		}

		double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;

		double halfW = this.baseWidth * 0.75;
		for (Vec3 pt : this.centerlineWaypoints) {
			minX = Math.min(minX, pt.x - halfW);
			maxX = Math.max(maxX, pt.x + halfW);
			minY = Math.min(minY, pt.y - this.baseDepth - 2.0);
			maxY = Math.max(maxY, pt.y + 3.0);
			minZ = Math.min(minZ, pt.z - halfW);
			maxZ = Math.max(maxZ, pt.z + halfW);
		}

		this.boundingBox = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
	}

	/**
	 * Projection result of a world position onto the river reach centerline.
	 */
	public record ProjectionResult(
		Vec3 closestPointOnCenterline,
		Vec3 flowTangent,       // unit vector pointing downstream
		Vec3 normalToBank,      // unit vector pointing toward right bank (+90 deg yaw from tangent)
		double transverseDistance, // signed distance perpendicular to centerline (+ = right bank, - = left bank)
		double segmentFraction,    // normalized distance along segment [0, 1]
		float curvature,           // local bend curvature (1/m)
		double localWidth,
		double localDepth,
		double localSurfaceY
	) {}

	/**
	 * Projects a world position onto this reach centerline.
	 */
	public ProjectionResult project(Vec3 pos) {
		if (this.centerlineWaypoints.size() < 2) {
			Vec3 p = this.centerlineWaypoints.isEmpty() ? Vec3.ZERO : this.centerlineWaypoints.get(0);
			return new ProjectionResult(p, new Vec3(0, 0, 1), new Vec3(1, 0, 0), 0.0, 0.0, 0.0F, this.baseWidth, this.baseDepth, this.waterSurfaceY);
		}

		double bestDistSq = Double.POSITIVE_INFINITY;
		Vec3 bestPoint = this.centerlineWaypoints.get(0);
		Vec3 bestTangent = new Vec3(0, 0, 1);
		double bestSignedDist = 0.0;
		double bestFraction = 0.0;
		float bestCurvature = 0.0F;

		for (int i = 0; i < this.centerlineWaypoints.size() - 1; i++) {
			Vec3 p1 = this.centerlineWaypoints.get(i);
			Vec3 p2 = this.centerlineWaypoints.get(i + 1);

			Vec3 seg = p2.subtract(p1);
			double segLenSq = seg.x * seg.x + seg.z * seg.z;
			if (segLenSq < 1e-4) continue;

			double u = ((pos.x - p1.x) * seg.x + (pos.z - p1.z) * seg.z) / segLenSq;
			double clampedU = Mth.clamp(u, 0.0, 1.0);

			Vec3 proj = new Vec3(
				p1.x + clampedU * seg.x,
				p1.y + clampedU * (p2.y - p1.y),
				p1.z + clampedU * seg.z
			);

			double dx = pos.x - proj.x;
			double dz = pos.z - proj.z;
			double distSq = dx * dx + dz * dz;

			if (distSq < bestDistSq) {
				bestDistSq = distSq;
				bestPoint = proj;
				double segLen = Math.sqrt(segLenSq);
				bestTangent = new Vec3(seg.x / segLen, (p2.y - p1.y) / segLen, seg.z / segLen);
				bestFraction = clampedU;

				// 2D cross product: seg.x * dz - seg.z * dx
				double cross = seg.x * dz - seg.z * dx;
				bestSignedDist = (cross >= 0 ? 1.0 : -1.0) * Math.sqrt(distSq);

				// Approximate local curvature from consecutive segments if available
				if (i > 0 && i < this.centerlineWaypoints.size() - 1) {
					Vec3 p0 = this.centerlineWaypoints.get(i - 1);
					Vec3 vPrev = p1.subtract(p0).normalize();
					Vec3 vNext = p2.subtract(p1).normalize();
					double dot = Mth.clamp(vPrev.x * vNext.x + vPrev.z * vNext.z, -1.0, 1.0);
					double angle = Math.acos(dot);
					double len = 0.5 * (p1.distanceTo(p0) + p2.distanceTo(p1));
					float sign = (float) Math.signum(vPrev.x * vNext.z - vPrev.z * vNext.x);
					bestCurvature = (float) (sign * (angle / Math.max(1.0, len)));
				}
			}
		}

		// Normal pointing +90 deg clockwise (right bank)
		Vec3 normal = new Vec3(bestTangent.z, 0.0, -bestTangent.x);

		return new ProjectionResult(
			bestPoint,
			bestTangent,
			normal,
			bestSignedDist,
			bestFraction,
			bestCurvature,
			this.baseWidth,
			this.baseDepth,
			bestPoint.y
		);
	}
}
