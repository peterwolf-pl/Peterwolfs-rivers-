package com.piotrek.pwrivers.network;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Connected topological graph of river reaches, junctions, and boundary conditions.
 * Serves as the macro-scale hydrological foundation for the entire river network.
 */
public class RiverNetworkGraph {
	private final String networkId;
	private final Map<String, RiverReach> reaches = new ConcurrentHashMap<>();
	private final Map<String, RiverJunction> junctions = new ConcurrentHashMap<>();
	private final Map<String, RiverBoundaryCondition> boundaries = new ConcurrentHashMap<>();

	public RiverNetworkGraph(String networkId) {
		this.networkId = networkId;
	}

	public String getNetworkId() { return this.networkId; }

	public void addReach(RiverReach reach) {
		this.reaches.put(reach.getReachId(), reach);
	}

	public RiverReach getReach(String reachId) {
		return this.reaches.get(reachId);
	}

	public Collection<RiverReach> getAllReaches() {
		return Collections.unmodifiableCollection(this.reaches.values());
	}

	public void addJunction(RiverJunction junction) {
		this.junctions.put(junction.getJunctionId(), junction);
	}

	public RiverJunction getJunction(String junctionId) {
		return this.junctions.get(junctionId);
	}

	public Collection<RiverJunction> getAllJunctions() {
		return Collections.unmodifiableCollection(this.junctions.values());
	}

	public void addBoundary(RiverBoundaryCondition condition) {
		this.boundaries.put(condition.getConditionId(), condition);
	}

	public Collection<RiverBoundaryCondition> getAllBoundaries() {
		return Collections.unmodifiableCollection(this.boundaries.values());
	}

	public void clear() {
		this.reaches.clear();
		this.junctions.clear();
		this.boundaries.clear();
	}

	/**
	 * Finds the closest river reach to a world position, or null if none within reach bounds.
	 */
	public RiverReach findNearestReach(Vec3 pos) {
		RiverReach bestReach = null;
		double bestDistSq = Double.POSITIVE_INFINITY;

		for (RiverReach reach : this.reaches.values()) {
			AABB bb = reach.getBoundingBox();
			if (bb != null && bb.inflate(16.0).contains(pos)) {
				RiverReach.ProjectionResult proj = reach.project(pos);
				double dx = pos.x - proj.closestPointOnCenterline().x;
				double dz = pos.z - proj.closestPointOnCenterline().z;
				double distSq = dx * dx + dz * dz;

				// Check if within channel width plus margin
				double maxInfluence = reach.getBaseWidth() * 0.9;
				if (distSq <= maxInfluence * maxInfluence && distSq < bestDistSq) {
					bestDistSq = distSq;
					bestReach = reach;
				}
			}
		}

		// Fallback pass: find closest if none found inside bounding boxes
		if (bestReach == null) {
			for (RiverReach reach : this.reaches.values()) {
				RiverReach.ProjectionResult proj = reach.project(pos);
				double dx = pos.x - proj.closestPointOnCenterline().x;
				double dz = pos.z - proj.closestPointOnCenterline().z;
				double distSq = dx * dx + dz * dz;
				double maxInfluence = reach.getBaseWidth() * 0.9;
				if (distSq <= maxInfluence * maxInfluence && distSq < bestDistSq) {
					bestDistSq = distSq;
					bestReach = reach;
				}
			}
		}

		return bestReach;
	}

	/**
	 * Solves coarse hydraulic continuity across all junctions and reaches.
	 */
	public void solveNetworkHydraulics() {
		// 1. Balance mass at junctions
		for (RiverJunction junction : this.junctions.values()) {
			junction.balanceMassConservation(this);
		}
	}
}
