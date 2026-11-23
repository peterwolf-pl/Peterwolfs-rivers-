package com.piotrek.pwrivers.client.vessel;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

public class WresslaRenderState extends EntityRenderState {
	public float yaw;
	public float wheelAngle;
	public float engineYaw;
	public float engineTilt;
	public float propellerAngle;
	public boolean engineActive;
	public float engineThrottle;
	public boolean isAimingAtWheel;
}
