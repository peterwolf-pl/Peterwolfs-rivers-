package com.piotrek.pwrivers.client.vessel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.piotrek.pwrivers.vessel.WresslaEntity;
import com.piotrek.pwrivers.vessel.WresslaLayout;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Highlights the steering wheel with a crisp wireframe outline when targeted.
 */
public final class WresslaWheelHighlightRenderer {
	private WresslaWheelHighlightRenderer() {}

	public static void render(LevelRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;
		ClientLevel level = client.level;
		if (player == null || level == null) {
			return;
		}

		Vec3 camPos = context.levelState().cameraRenderState.pos;
		if (camPos == null) {
			return;
		}

		float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		PoseStack poseStack = context.poseStack();

		boolean isHelmsman = player.getVehicle() instanceof WresslaEntity wressla && wressla.isHelmsman(player);
		if (!isHelmsman) {
			AABB searchBox = player.getBoundingBox().inflate(6.0);
			List<WresslaEntity> boats = level.getEntitiesOfClass(WresslaEntity.class, searchBox);
			for (WresslaEntity boat : boats) {
				if (boat.isAimingAtWheel(player, partialTick)) {
					context.submitNodeCollector().submitCustomGeometry(
						poseStack,
						RenderTypes.lines(),
						(pose, consumer) -> drawLocalBox(pose, consumer, boat, WresslaLayout.WHEEL_BOX, partialTick, camPos)
					);
					break;
				}
			}
		}
	}

	private static void drawLocalBox(PoseStack.Pose pose, VertexConsumer consumer, WresslaEntity boat, AABB box, float partialTick, Vec3 camPos) {
		float boatYaw = Mth.rotLerp(partialTick, boat.yRotO, boat.getYRot());
		Vec3 boatPos = boat.getPosition(partialTick);
		float yawRad = boatYaw * Mth.DEG_TO_RAD;
		double fwdX = -Mth.sin(yawRad);
		double fwdZ = Mth.cos(yawRad);
		double rightX = Mth.cos(yawRad);
		double rightZ = Mth.sin(yawRad);

		Vec3[] localCorners = new Vec3[]{
			new Vec3(box.minX, box.minY, box.minZ),
			new Vec3(box.maxX, box.minY, box.minZ),
			new Vec3(box.maxX, box.minY, box.maxZ),
			new Vec3(box.minX, box.minY, box.maxZ),
			new Vec3(box.minX, box.maxY, box.minZ),
			new Vec3(box.maxX, box.maxY, box.minZ),
			new Vec3(box.maxX, box.maxY, box.maxZ),
			new Vec3(box.minX, box.maxY, box.maxZ)
		};

		float[] rx = new float[8];
		float[] ry = new float[8];
		float[] rz = new float[8];

		for (int k = 0; k < 8; k++) {
			rx[k] = (float) (boatPos.x + rightX * localCorners[k].x + fwdX * localCorners[k].z - camPos.x);
			ry[k] = (float) (boatPos.y + localCorners[k].y - camPos.y);
			rz[k] = (float) (boatPos.z + rightZ * localCorners[k].x + fwdZ * localCorners[k].z - camPos.z);
		}

		drawBoxLines(pose, consumer, rx, ry, rz);
	}

	private static void drawBoxLines(PoseStack.Pose pose, VertexConsumer consumer, float[] x, float[] y, float[] z) {
		int[][] edges = {
			{0, 1}, {1, 2}, {2, 3}, {3, 0},
			{4, 5}, {5, 6}, {6, 7}, {7, 4},
			{0, 4}, {1, 5}, {2, 6}, {3, 7}
		};
		for (int[] e : edges) {
			consumer.addVertex(pose, x[e[0]], y[e[0]], z[e[0]])
				.setColor(0, 0, 0, 240)
				.setNormal(pose, 0.0F, 1.0F, 0.0F);
			consumer.addVertex(pose, x[e[1]], y[e[1]], z[e[1]])
				.setColor(0, 0, 0, 240)
				.setNormal(pose, 0.0F, 1.0F, 0.0F);
		}
	}
}
