package com.piotrek.pwrivers.structures;

import com.piotrek.pwrivers.config.RiversConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Controller for an operational navigation lock chamber.
 * Regulates chamber water levels, gate sequencing, buoyancy integration,
 * and hydraulic safety interlocks.
 */
public class RiverLockController {
	public enum CycleState {
		LOWER_GATE_OPEN,   // Ready for boat entry/exit from downstream
		LOWER_EQUALIZED,   // Gates closed, level at downstream
		FILLING,           // Sluices filling chamber, water level rising
		UPPER_EQUALIZED,   // Gates closed, level at upstream
		UPPER_GATE_OPEN,   // Ready for boat entry/exit from upstream
		DRAINING           // Sluices emptying chamber, water level falling
	}

	private final String lockId;
	private final String lockName;
	private final RiverLockGate upstreamGate;
	private final RiverLockGate downstreamGate;
	private final AABB chamberBounds;
	private double upstreamLevelY;
	private double downstreamLevelY;
	private double chamberLevelY;
	private CycleState state;
	private String statusMessage = "";

	public RiverLockController(
		String lockId,
		String lockName,
		RiverLockGate upstreamGate,
		RiverLockGate downstreamGate,
		AABB chamberBounds,
		double upstreamLevelY,
		double downstreamLevelY
	) {
		this.lockId = lockId;
		this.lockName = lockName;
		this.upstreamGate = upstreamGate;
		this.downstreamGate = downstreamGate;
		this.chamberBounds = chamberBounds;
		this.upstreamLevelY = upstreamLevelY;
		this.downstreamLevelY = downstreamLevelY;
		this.chamberLevelY = downstreamLevelY; // Start at lower level
		this.state = CycleState.LOWER_GATE_OPEN;
		this.downstreamGate.open();
		this.upstreamGate.close();
		this.statusMessage = "Ready at downstream level";
	}

	public String getLockId() { return this.lockId; }
	public String getLockName() { return this.lockName; }
	public RiverLockGate getUpstreamGate() { return this.upstreamGate; }
	public RiverLockGate getDownstreamGate() { return this.downstreamGate; }
	public AABB getChamberBounds() { return this.chamberBounds; }
	public double getUpstreamLevelY() { return this.upstreamLevelY; }
	public void setUpstreamLevelY(double y) { this.upstreamLevelY = y; }
	public double getDownstreamLevelY() { return this.downstreamLevelY; }
	public void setDownstreamLevelY(double y) { this.downstreamLevelY = y; }
	public double getChamberLevelY() { return this.chamberLevelY; }
	public void setChamberLevelY(double y) { this.chamberLevelY = y; }
	public CycleState getState() { return this.state; }
	public String getStatusMessage() { return this.statusMessage; }

	public boolean isInsideChamber(Vec3 pos) {
		return this.chamberBounds.contains(pos);
	}

	/**
	 * Main simulation tick: updates gates, chamber water level, and boat buoyancy.
	 */
	public void tick(Level level) {
		this.upstreamGate.tick();
		this.downstreamGate.tick();

		double fillRate = RiversConfig.lockFillRate;
		double drainRate = RiversConfig.lockDrainRate;

		switch (this.state) {
			case FILLING -> {
				this.upstreamGate.close();
				this.downstreamGate.close();

				if (this.chamberLevelY < this.upstreamLevelY) {
					this.chamberLevelY = Math.min(this.upstreamLevelY, this.chamberLevelY + fillRate);
					this.statusMessage = String.format("Filling chamber: %.2f / %.2f m", this.chamberLevelY, this.upstreamLevelY);
				} else {
					this.chamberLevelY = this.upstreamLevelY;
					this.state = CycleState.UPPER_EQUALIZED;
					this.statusMessage = "Chamber equalized with upstream. Opening upper gate...";
					this.upstreamGate.open();
					this.state = CycleState.UPPER_GATE_OPEN;
				}
			}
			case DRAINING -> {
				this.upstreamGate.close();
				this.downstreamGate.close();

				if (this.chamberLevelY > this.downstreamLevelY) {
					this.chamberLevelY = Math.max(this.downstreamLevelY, this.chamberLevelY - drainRate);
					this.statusMessage = String.format("Draining chamber: %.2f / %.2f m", this.chamberLevelY, this.downstreamLevelY);
				} else {
					this.chamberLevelY = this.downstreamLevelY;
					this.state = CycleState.LOWER_EQUALIZED;
					this.statusMessage = "Chamber equalized with downstream. Opening lower gate...";
					this.downstreamGate.open();
					this.state = CycleState.LOWER_GATE_OPEN;
				}
			}
			case UPPER_GATE_OPEN -> {
				if (!this.upstreamGate.isFullyOpen()) {
					this.upstreamGate.open();
				}
				this.statusMessage = "Upper gate open (upstream passage clear)";
			}
			case LOWER_GATE_OPEN -> {
				if (!this.downstreamGate.isFullyOpen()) {
					this.downstreamGate.open();
				}
				this.statusMessage = "Lower gate open (downstream passage clear)";
			}
			default -> {}
		}

		// Apply chamber buoyancy to all vessels and entities inside the lock chamber
		if (level != null && !level.isClientSide()) {
			applyChamberBuoyancy(level);
		}
	}

	/**
	 * Smooth continuous buoyancy lifting/lowering boats inside chamber without teleportation.
	 */
	private void applyChamberBuoyancy(Level level) {
		AABB inflated = this.chamberBounds.inflate(1.0);
		List<Entity> entities = level.getEntities((Entity) null, inflated, e -> true);

		for (Entity entity : entities) {
			if (this.chamberBounds.contains(entity.position())) {
				double targetY = this.chamberLevelY;
				double dy = targetY - entity.getY();

				// Smooth hydrodynamic buoyant lift/fall force
				Vec3 vel = entity.getDeltaMovement();
				double targetYVel = Mth.clamp(dy * 0.25, -0.4, 0.4);
				double newYVel = Mth.lerp(0.35, vel.y, targetYVel);

				// Dampen horizontal drift while chamber is filling/draining
				double damp = (this.state == CycleState.FILLING || this.state == CycleState.DRAINING) ? 0.90 : 0.98;
				entity.setDeltaMovement(new Vec3(vel.x * damp, newYVel, vel.z * damp));
			}
		}
	}

	/**
	 * Cycles the lock: if lower, fills to upper; if upper, drains to lower.
	 *
	 * @return true if cycle successfully initiated; false if blocked by safety interlocks
	 */
	public boolean cycleLock() {
		if (this.state == CycleState.FILLING || this.state == CycleState.DRAINING) {
			return false; // Already in progress
		}

		if (this.state == CycleState.LOWER_GATE_OPEN || this.state == CycleState.LOWER_EQUALIZED) {
			// Close lower gate, start filling
			this.downstreamGate.close();
			this.upstreamGate.close();
			this.state = CycleState.FILLING;
			this.statusMessage = "Initiated chamber filling cycle";
			return true;
		} else if (this.state == CycleState.UPPER_GATE_OPEN || this.state == CycleState.UPPER_EQUALIZED) {
			// Close upper gate, start draining
			this.upstreamGate.close();
			this.downstreamGate.close();
			this.state = CycleState.DRAINING;
			this.statusMessage = "Initiated chamber draining cycle";
			return true;
		}

		return false;
	}

	/**
	 * Request to open lower gate with safety interlock check.
	 */
	public boolean requestLowerGate() {
		if (RiversConfig.lockSafetyInterlocks && Math.abs(this.chamberLevelY - this.downstreamLevelY) > 0.15) {
			this.statusMessage = "INTERLOCK BLOCKED: Cannot open lower gate with water level difference!";
			return false;
		}
		this.upstreamGate.close();
		this.downstreamGate.open();
		this.state = CycleState.LOWER_GATE_OPEN;
		return true;
	}

	/**
	 * Request to open upper gate with safety interlock check.
	 */
	public boolean requestUpperGate() {
		if (RiversConfig.lockSafetyInterlocks && Math.abs(this.chamberLevelY - this.upstreamLevelY) > 0.15) {
			this.statusMessage = "INTERLOCK BLOCKED: Cannot open upper gate with water level difference!";
			return false;
		}
		this.downstreamGate.close();
		this.upstreamGate.open();
		this.state = CycleState.UPPER_GATE_OPEN;
		return true;
	}

	public CompoundTag saveNbt() {
		CompoundTag tag = new CompoundTag();
		tag.putString("lockId", this.lockId);
		tag.putString("state", this.state.name());
		tag.putDouble("chamberLevelY", this.chamberLevelY);
		tag.putDouble("upstreamLevelY", this.upstreamLevelY);
		tag.putDouble("downstreamLevelY", this.downstreamLevelY);
		tag.putFloat("upperGateOpen", this.upstreamGate.getOpenProgress());
		tag.putFloat("lowerGateOpen", this.downstreamGate.getOpenProgress());
		return tag;
	}

	public void loadNbt(CompoundTag tag) {
		if (tag.contains("chamberLevelY")) {
			this.chamberLevelY = tag.getDouble("chamberLevelY").orElse(this.chamberLevelY);
		}
		if (tag.contains("state")) {
			tag.getString("state").ifPresent(st -> {
				try {
					this.state = CycleState.valueOf(st);
				} catch (Exception ignored) {}
			});
		}
		if (tag.contains("upperGateOpen")) {
			tag.getFloat("upperGateOpen").ifPresent(this.upstreamGate::setOpenProgressDirectly);
		}
		if (tag.contains("lowerGateOpen")) {
			tag.getFloat("lowerGateOpen").ifPresent(this.downstreamGate::setOpenProgressDirectly);
		}
	}
}
