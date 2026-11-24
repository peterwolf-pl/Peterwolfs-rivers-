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

	public final boolean[] cleatTied = new boolean[4];
	public int targetedCleat = -1;

	// Stern flag wind wave animation
	public float flagRelativeYaw;
	public float flagPitch;
	public float flagWindSpeed;
	public float gameTime;
}
