package com.piotrek.pwrivers.vessel;

import com.piotrek.pwrivers.client.vessel.WresslaModel;
import com.piotrek.pwrivers.client.vessel.WresslaRenderState;
import net.minecraft.SharedConstants;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ExportWresslaModel {
	@Test
	void testModelHierarchyAndAnimation() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();

		ModelPart root = WresslaModel.createBodyLayer().bakeRoot();
		assertNotNull(root, "Root model must not be null");

		ModelPart hull = root.getChild("hull");
		assertNotNull(hull, "Hull must be present");

		ModelPart wheel = hull.getChild("wheel");
		assertNotNull(wheel, "Steering wheel must be present");

		ModelPart motor = hull.getChild("outboard_motor");
		assertNotNull(motor, "Outboard motor must be present");

		ModelPart propeller = motor.getChild("propeller");
		assertNotNull(propeller, "Propeller must be present on outboard motor");

		WresslaModel model = new WresslaModel(root);
		WresslaRenderState state = new WresslaRenderState();
		state.wheelAngle = 0.52F;
		state.engineYaw = -0.45F;
		state.engineTilt = 0.10F;
		state.propellerAngle = 3.14F;

		model.setupAnim(state);

		assertEquals(0.52F, wheel.zRot, 1.0E-4F, "Wheel must rotate around Z-axis");
		assertEquals(-0.45F, motor.yRot, 1.0E-4F, "Outboard motor must pivot around Y-axis when steering");
		assertEquals(0.10F, motor.xRot, 1.0E-4F, "Outboard motor must tilt around X-axis");
		assertEquals(3.14F, propeller.zRot, 1.0E-4F, "Propeller must spin around Z-axis");
	}

	public static void main(String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		ModelPart root = WresslaModel.createBodyLayer().bakeRoot();
		System.out.println("Baked Wressla Model successfully with " + root.getAllParts().size() + " parts.");
	}
}
