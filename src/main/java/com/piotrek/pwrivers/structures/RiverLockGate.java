package com.piotrek.pwrivers.structures;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Movable navigation lock gate (mitre gate, vertical lift gate, or flap gate).
 */
public class RiverLockGate {
	public enum State {
		CLOSED,
		OPENING,
		OPEN,
		CLOSING
	}

	private final String gateId;
	private final Vec3 centerPos;
	private final Direction facing; // Direction boat passes through
	private final double width;
	private final double height;
	private State state = State.CLOSED;
	private float openProgress = 0.0F; // 0.0 = fully closed, 1.0 = fully open
	private float openSpeed = 0.02F;   // progress per tick (~50 ticks / 2.5s for full open)

	public RiverLockGate(String gateId, Vec3 centerPos, Direction facing, double width, double height) {
		this.gateId = gateId;
		this.centerPos = centerPos;
		this.facing = facing;
		this.width = width;
		this.height = height;
	}

	public String getGateId() { return this.gateId; }
	public Vec3 getCenterPos() { return this.centerPos; }
	public Direction getFacing() { return this.facing; }
	public double getWidth() { return this.width; }
	public double getHeight() { return this.height; }
	public State getState() { return this.state; }
	public float getOpenProgress() { return this.openProgress; }

	public boolean isFullyOpen() { return this.openProgress >= 0.99F; }
	public boolean isFullyClosed() { return this.openProgress <= 0.01F; }

	public void open() {
		if (this.state != State.OPEN) {
			this.state = State.OPENING;
		}
	}

	public void close() {
		if (this.state != State.CLOSED) {
			this.state = State.CLOSING;
		}
	}

	public void tick() {
		if (this.state == State.OPENING) {
			this.openProgress = Math.min(1.0F, this.openProgress + this.openSpeed);
			if (this.openProgress >= 1.0F) {
				this.openProgress = 1.0F;
				this.state = State.OPEN;
			}
		} else if (this.state == State.CLOSING) {
			this.openProgress = Math.max(0.0F, this.openProgress - this.openSpeed);
			if (this.openProgress <= 0.0F) {
				this.openProgress = 0.0F;
				this.state = State.CLOSED;
			}
		}
	}

	public void setOpenProgressDirectly(float progress) {
		this.openProgress = Mth.clamp(progress, 0.0F, 1.0F);
		if (this.openProgress >= 1.0F) this.state = State.OPEN;
		else if (this.openProgress <= 0.0F) this.state = State.CLOSED;
		else this.state = State.OPENING;
	}

	/**
	 * Bounding box for physical collision when closed or partially closed.
	 */
	public AABB getCollisionBox() {
		if (isFullyOpen()) {
			return null; // No collision when fully open
		}

		double thick = 0.8;
		double halfW = this.width * 0.5;
		double halfH = this.height * 0.5;

		if (this.facing.getAxis() == Direction.Axis.Z) {
			return new AABB(
				this.centerPos.x - halfW, this.centerPos.y - halfH, this.centerPos.z - thick * 0.5,
				this.centerPos.x + halfW, this.centerPos.y + halfH, this.centerPos.z + thick * 0.5
			);
		} else {
			return new AABB(
				this.centerPos.x - thick * 0.5, this.centerPos.y - halfH, this.centerPos.z - halfW,
				this.centerPos.x + thick * 0.5, this.centerPos.y + halfH, this.centerPos.z + halfW
			);
		}
	}
}
