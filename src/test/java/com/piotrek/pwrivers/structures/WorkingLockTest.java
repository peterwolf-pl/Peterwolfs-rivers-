package com.piotrek.pwrivers.structures;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test D: Operational navigation lock with a 5-block vertical head difference.
 * Verifies:
 * - Upstream level Y = 69.0, Downstream level Y = 64.0 (5.0m lift)
 * - Both gates operate and sequence correctly
 * - Safety interlocks prevent opening gates when water level difference exists
 * - Chamber fills and drains gradually
 * - Equalization occurs and unlocks exit gates
 * - Full cycling stability
 */
public class WorkingLockTest {
	private RiverLockController lock;
	private RiverLockGate upperGate;
	private RiverLockGate lowerGate;

	@BeforeEach
	void setUp() {
		this.upperGate = new RiverLockGate("test_upper_gate", new Vec3(0, 69.0, 10), Direction.SOUTH, 10.0, 6.0);
		this.lowerGate = new RiverLockGate("test_lower_gate", new Vec3(0, 64.0, 50), Direction.SOUTH, 10.0, 6.0);
		AABB chamber = new AABB(-5, 58.0, 10, 5, 75.0, 50);

		// Upstream = 69.0, Downstream = 64.0 (5 blocks lift)
		this.lock = new RiverLockController(
			"lock_test_5m",
			"5m Lift Test Lock",
			this.upperGate,
			this.lowerGate,
			chamber,
			69.0,
			64.0
		);
	}

	@Test
	void testInitialStateAtDownstream() {
		assertEquals(64.0, this.lock.getChamberLevelY(), 0.001);
		assertEquals(RiverLockController.CycleState.LOWER_GATE_OPEN, this.lock.getState());
		assertTrue(this.lowerGate.getState() == RiverLockGate.State.OPEN || this.lowerGate.getState() == RiverLockGate.State.OPENING);
		assertTrue(this.upperGate.getState() == RiverLockGate.State.CLOSED || this.upperGate.getState() == RiverLockGate.State.CLOSING);
	}

	@Test
	void testSafetyInterlocks() {
		// When at lower level (Y = 64.0), attempting to open upper gate (Y = 69.0) must fail
		boolean openUpperResult = this.lock.requestUpperGate();
		assertFalse(openUpperResult, "Safety interlock must prevent opening upper gate when chamber is at lower level!");

		// Lower gate request should succeed since chamber is equalized with downstream
		boolean openLowerResult = this.lock.requestLowerGate();
		assertTrue(openLowerResult, "Lower gate request must succeed when equalized with downstream");
	}

	@Test
	void testCompleteLockFillingCycle() {
		// 1. Boat enters chamber from downstream, cycle initiated
		boolean cycleStarted = this.lock.cycleLock();
		assertTrue(cycleStarted, "Cycle must start from lower level");
		assertEquals(RiverLockController.CycleState.FILLING, this.lock.getState());

		// 2. Simulate ticks: chamber fills gradually from 64.0 to 69.0
		double prevLevel = this.lock.getChamberLevelY();
		for (int tick = 0; tick < 200; tick++) {
			this.lock.tick(null);
			if (this.lock.getState() == RiverLockController.CycleState.UPPER_GATE_OPEN) {
				break;
			}
			assertTrue(this.lock.getChamberLevelY() >= prevLevel, "Chamber water level must rise continuously");
			prevLevel = this.lock.getChamberLevelY();
		}

		// 3. Chamber should have reached 69.0, equalized, and upper gate opened
		assertEquals(69.0, this.lock.getChamberLevelY(), 0.05, "Chamber must equalize at upstream elevation (69.0)");
		assertEquals(RiverLockController.CycleState.UPPER_GATE_OPEN, this.lock.getState());

		// Upper gate should be open or opening, lower gate closed
		assertTrue(this.upperGate.getState() == RiverLockGate.State.OPEN || this.upperGate.getState() == RiverLockGate.State.OPENING);
		assertTrue(this.lowerGate.isFullyClosed() || this.lowerGate.getState() == RiverLockGate.State.CLOSED);
	}

	@Test
	void testCompleteLockDrainingCycle() {
		// Fast-forward to upper level
		this.lock.setChamberLevelY(69.0);
		this.lock.requestUpperGate();
		this.upperGate.setOpenProgressDirectly(1.0F);

		// Initiate draining cycle
		boolean drainStarted = this.lock.cycleLock();
		assertTrue(drainStarted);
		assertEquals(RiverLockController.CycleState.DRAINING, this.lock.getState());

		// Simulate draining
		double prevLevel = this.lock.getChamberLevelY();
		for (int tick = 0; tick < 200; tick++) {
			this.lock.tick(null);
			if (this.lock.getState() == RiverLockController.CycleState.LOWER_GATE_OPEN) {
				break;
			}
			assertTrue(this.lock.getChamberLevelY() <= prevLevel, "Chamber water level must fall continuously");
			prevLevel = this.lock.getChamberLevelY();
		}

		assertEquals(64.0, this.lock.getChamberLevelY(), 0.05, "Chamber must equalize at downstream elevation (64.0)");
		assertEquals(RiverLockController.CycleState.LOWER_GATE_OPEN, this.lock.getState());
	}
}
