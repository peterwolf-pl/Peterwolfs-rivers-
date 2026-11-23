package com.piotrek.pwrivers.network;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Confluence of multiple tributary reaches or bifurcation into river branches (e.g. around islands).
 * Enforces hydraulic mass conservation: sum(Q_in) = sum(Q_out).
 */
public class RiverJunction {
	public enum Type {
		CONFLUENCE, // Multiple inflows join into one reach
		BIFURCATION, // One inflow splits into multiple branches
		COMPLEX     // Multi-inflow, multi-outflow braided river node
	}

	private final String junctionId;
	private final Vec3 position;
	private final Type type;
	private final List<String> incomingReachIds = new ArrayList<>();
	private final List<String> outgoingReachIds = new ArrayList<>();

	public RiverJunction(String junctionId, Vec3 position, Type type) {
		this.junctionId = junctionId;
		this.position = position;
		this.type = type;
	}

	public String getJunctionId() { return this.junctionId; }
	public Vec3 getPosition() { return this.position; }
	public Type getType() { return this.type; }

	public List<String> getIncomingReachIds() { return Collections.unmodifiableList(this.incomingReachIds); }
	public List<String> getOutgoingReachIds() { return Collections.unmodifiableList(this.outgoingReachIds); }

	public void addIncomingReach(String reachId) {
		if (!this.incomingReachIds.contains(reachId)) {
			this.incomingReachIds.add(reachId);
		}
	}

	public void addOutgoingReach(String reachId) {
		if (!this.outgoingReachIds.contains(reachId)) {
			this.outgoingReachIds.add(reachId);
		}
	}

	/**
	 * Balances discharges between incoming and outgoing reaches conserving total mass flow.
	 */
	public void balanceMassConservation(RiverNetworkGraph network) {
		double totalInflow = 0.0;
		for (String inId : this.incomingReachIds) {
			RiverReach reach = network.getReach(inId);
			if (reach != null) {
				totalInflow += reach.getTargetDischarge();
			}
		}

		if (this.outgoingReachIds.isEmpty() || totalInflow <= 0.0) return;

		// Divide outflow among branches proportional to branch width
		double totalBranchWidth = 0.0;
		for (String outId : this.outgoingReachIds) {
			RiverReach reach = network.getReach(outId);
			if (reach != null) {
				totalBranchWidth += reach.getBaseWidth();
			}
		}

		if (totalBranchWidth <= 0.0) totalBranchWidth = 1.0;

		for (String outId : this.outgoingReachIds) {
			RiverReach reach = network.getReach(outId);
			if (reach != null) {
				double fraction = reach.getBaseWidth() / totalBranchWidth;
				reach.setTargetDischarge(totalInflow * fraction);
			}
		}
	}
}
