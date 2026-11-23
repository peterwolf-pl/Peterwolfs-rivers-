package com.piotrek.pwrivers.client.vessel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.piotrek.pwrivers.PeterwolfsRiversMod;
import com.piotrek.pwrivers.client.PeterwolfsRiversClient;
import com.piotrek.pwrivers.vessel.WresslaEntity;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.AABB;

public class WresslaRenderer extends EntityRenderer<WresslaEntity, WresslaRenderState> {
	public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
		PeterwolfsRiversMod.MOD_ID, "textures/entity/wressla.png"
	);

	private final WresslaModel model;
	private final Model.Simple waterMask;

	public WresslaRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new WresslaModel(context.bakeLayer(PeterwolfsRiversClient.WRESSLA_LAYER));
		this.waterMask = new Model.Simple(
			context.bakeLayer(PeterwolfsRiversClient.WRESSLA_WATER_PATCH),
			id -> RenderTypes.waterMask()
		);
		this.shadowRadius = 2.5F;
	}

	@Override
	protected AABB getBoundingBoxForCulling(WresslaEntity entity, float partialTick) {
		return entity.getBoundingBox().inflate(3.5, 3.0, 5.0);
	}

	@Override
	protected boolean affectedByCulling(WresslaEntity entity) {
		return false;
	}

	@Override
	public WresslaRenderState createRenderState() {
		return new WresslaRenderState();
	}

	@Override
	public void extractRenderState(WresslaEntity entity, WresslaRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.yaw = entity.getYRot();
		state.wheelAngle = (float) Math.toRadians(entity.getRudderAngle() * 2.5F);
		state.engineYaw = entity.getOutboardMotorYaw();
		state.engineTilt = 0.0F;
		state.propellerAngle = entity.getPropellerAngle();
		state.engineActive = entity.isEngineActive();
		state.engineThrottle = entity.getEngineThrottle();
	}

	@Override
	public void submit(WresslaRenderState state, PoseStack stack, SubmitNodeCollector collector, CameraRenderState camera) {
		super.submit(state, stack, collector, camera);

		stack.pushPose();
		stack.rotateDegrees(Axis.YP, 180.0F - state.yaw);

		// Water mask underneath twin floats
		if (camera.fogType != FogType.WATER) {
			stack.pushPose();
			stack.scale(-1.0F, -1.0F, 1.0F);
			stack.translate(0.0F, -1.5F, 0.0F);
			collector.submitModel(
				this.waterMask,
				Unit.INSTANCE,
				stack,
				TEXTURE,
				state.lightCoords,
				OverlayTexture.NO_OVERLAY,
				0
			);
			stack.popPose();
		}

		stack.scale(-1.0F, -1.0F, 1.0F);
		stack.translate(0.0F, -1.5F, 0.0F);

		this.model.setupAnim(state);
		collector.submitModel(
			this.model,
			state,
			stack,
			RenderTypes.entityCutout(TEXTURE),
			state.lightCoords,
			OverlayTexture.NO_OVERLAY,
			state.outlineColor
		);

		stack.popPose();
	}
}
