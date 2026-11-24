package com.piotrek.pwrivers.vessel;

import com.piotrek.pwrivers.vessel.WresslaLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WresslaLayoutTest {
	@Test
	void testDimensions() {
		assertEquals(10.0, WresslaLayout.LENGTH, 1.0E-6);
		assertEquals(3.5, WresslaLayout.BEAM, 1.0E-6);
		assertEquals(0.50, WresslaLayout.DECK_Y, 1.0E-6);
		assertEquals(0.60, WresslaLayout.DRAFT, 1.0E-6);
	}

	@Test
	void testDeckWalkabilityBounds() {
		assertTrue(WresslaLayout.isWithinDeckBounds(0.0, 0.0));
		assertTrue(WresslaLayout.isWithinDeckBounds(1.5, 3.0));
		assertTrue(WresslaLayout.isWithinDeckBounds(-1.5, -3.0));
		assertFalse(WresslaLayout.isWithinDeckBounds(2.0, 0.0));
		assertFalse(WresslaLayout.isWithinDeckBounds(0.0, 6.0));
	}

	@Test
	void testWheelInteractionBox() {
		assertNotNull(WresslaLayout.WHEEL_BOX);
		assertTrue(WresslaLayout.WHEEL_BOX.contains(0.0, 1.5, 0.30));
	}

	@Test
	void testOutboardMotorPosition() {
		assertEquals(-5.00, WresslaLayout.MOTOR_MOUNT_POS.z, 1.0E-6);
		assertEquals(-4.85, WresslaLayout.PROPELLER_LOCAL_POS.z, 1.0E-6);
		assertTrue(WresslaLayout.PROPELLER_LOCAL_POS.y < 0.0, "Propeller must be beneath waterline for thrust and bubbles");
	}
}
