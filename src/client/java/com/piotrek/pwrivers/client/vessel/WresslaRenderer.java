package com.piotrek.pwrivers.client.vessel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.piotrek.pwrivers.PeterwolfsRiversMod;
import com.piotrek.pwrivers.client.PeterwolfsRiversClient;
import com.piotrek.pwrivers.integration.WindAndSailsRiverAdapter;
import com.piotrek.pwrivers.vessel.WresslaEntity;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Optional;

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
		return entity.getBoundingBox().inflate(3.5, 4.0, 5.0);
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

		for (int i = 0; i < WresslaEntity.CLEAT_COUNT; i++) {
			state.cleatTied[i] = entity.isCleatAttached(i);
		}

		if (state.leashStates == null) {
			state.leashStates = new ArrayList<>();
		} else {
			state.leashStates.clear();
		}

		Vec3 boatPos = entity.getPosition(partialTick);
		float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());

		for (int i = 0; i < WresslaEntity.CLEAT_COUNT; i++) {
			if (!state.cleatTied[i]) {
				continue;
			}

			Optional<BlockPos> knotPos = entity.getCleatFencePos(i);
			Entity holder = entity.getCleatHolderEntity(i);

			if (knotPos.isPresent() || (holder != null && holder.isAlive())) {
				EntityRenderState.LeashState leashState = new EntityRenderState.LeashState();
				Vec3 cleatWorld = entity.getCleatWorldPos(i, boatPos, yaw);

				leashState.offset = cleatWorld.subtract(boatPos);
				leashState.start = cleatWorld;

				if (knotPos.isPresent()) {
					BlockPos kp = knotPos.get();
					leashState.end = new Vec3(kp.getX() + 0.5, kp.getY() + 0.5 + 0.2, kp.getZ() + 0.5);
				} else {
					leashState.end = holder.getRopeHoldPosition(partialTick);
				}

				BlockPos startPos = BlockPos.containing(cleatWorld);
				BlockPos endPos = BlockPos.containing(leashState.end);

				leashState.startBlockLight = entity.level().getBrightness(LightLayer.BLOCK, startPos);
				leashState.startSkyLight = entity.level().getBrightness(LightLayer.SKY, startPos);
				leashState.endBlockLight = entity.level().getBrightness(LightLayer.BLOCK, endPos);
				leashState.endSkyLight = holder != null
					? holder.level().getBrightness(LightLayer.SKY, endPos)
					: entity.level().getBrightness(LightLayer.SKY, endPos);

				double dist = cleatWorld.distanceTo(leashState.end);
				leashState.slack = dist < 3.5;

				state.leashStates.add(leashState);
			}
		}

		// Stern wind flag calculation (matches Wind & Sails behavior)
		state.gameTime = (entity.level() != null ? entity.level().getGameTime() : 0L) + partialTick;

		Vec3 boatVel = entity.getDeltaMovement();
		Vec3 trueWind = WindAndSailsRiverAdapter.getWindVector(entity.level(), entity.position());
		if (trueWind.lengthSqr() < 1.0E-4) {
			// Gentle river valley breeze when calm or Wind & Sails mod absent
			double rad = state.gameTime * 0.015;
			trueWind = new Vec3(Math.sin(rad) * 0.5, 0.0, Math.cos(rad) * 0.5);
		}

		// Apparent wind = trueWind - boatVelocity
		Vec3 apparentWind = trueWind.subtract(boatVel.x * 20.0, 0.0, boatVel.z * 20.0);
		float apparentSpeed = (float) apparentWind.length();
		state.flagWindSpeed = apparentSpeed;

		float downwindDeg = (float) Math.toDegrees(Math.atan2(-apparentWind.x, apparentWind.z));
		float relativeDeg = Mth.wrapDegrees(downwindDeg - state.yaw);
		state.flagRelativeYaw = (float) Math.toRadians(Mth.wrapDegrees(90.0F - relativeDeg));
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
