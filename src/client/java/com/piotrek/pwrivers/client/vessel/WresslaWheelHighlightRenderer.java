package com.piotrek.pwrivers.client.vessel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.piotrek.pwrivers.vessel.WresslaEntity;
import com.piotrek.pwrivers.vessel.WresslaLayout;
import com.piotrek.pwrivers.vessel.WresslaMooringAim;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
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

		boolean isRiding = player.getVehicle() instanceof WresslaEntity;
		if (!isRiding) {
			boolean holdingLead = player.getMainHandItem().is(Items.LEAD) || player.getOffhandItem().is(Items.LEAD);
			boolean holdsLine = WresslaEntity.isPlayerHoldingAnyCleat(player);
			boolean aimActive = holdingLead || holdsLine || WresslaEntity.isAimingAtTiedMooring(player, level);

			if (aimActive) {
				WresslaMooringAim.Target aim = WresslaMooringAim.find(player, level, partialTick);
				if (aim != null) {
					if (aim.cleat() && level.getEntity(aim.vesselId()) instanceof WresslaEntity boat) {
						Vec3 offset = boat.getCleatLocalOffset(aim.cleatIndex());
						AABB cleatBox = WresslaMooringAim.cleatLocalBox(offset, 0.04);
						context.submitNodeCollector().submitCustomGeometry(
							poseStack,
							RenderTypes.lines(),
							(pose, consumer) -> drawLocalBox(pose, consumer, boat, cleatBox, partialTick, camPos)
						);
						return;
					} else if (aim.fence() != null) {
						AABB postBox = WresslaMooringAim.postColumn(aim.fence()).move(-camPos.x, -camPos.y, -camPos.z);
						context.submitNodeCollector().submitCustomGeometry(
							poseStack,
							RenderTypes.lines(),
							(pose, consumer) -> drawWorldBox(pose, consumer, postBox)
						);
						return;
					}
				}
			}

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

	private static void drawWorldBox(PoseStack.Pose pose, VertexConsumer consumer, AABB box) {
		float[] rx = new float[]{(float) box.minX, (float) box.maxX, (float) box.maxX, (float) box.minX,
			(float) box.minX, (float) box.maxX, (float) box.maxX, (float) box.minX};
		float[] ry = new float[]{(float) box.minY, (float) box.minY, (float) box.minY, (float) box.minY,
			(float) box.maxY, (float) box.maxY, (float) box.maxY, (float) box.maxY};
		float[] rz = new float[]{(float) box.minZ, (float) box.minZ, (float) box.maxZ, (float) box.maxZ,
			(float) box.minZ, (float) box.minZ, (float) box.maxZ, (float) box.maxZ};
		drawBoxLines(pose, consumer, rx, ry, rz);
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
			double wx = boatPos.x + rightX * localCorners[k].x + fwdX * localCorners[k].z;
			double wy = boatPos.y + localCorners[k].y;
			double wz = boatPos.z + rightZ * localCorners[k].x + fwdZ * localCorners[k].z;
			rx[k] = (float) (wx - camPos.x);
			ry[k] = (float) (wy - camPos.y);
			rz[k] = (float) (wz - camPos.z);
		}

		drawBoxLines(pose, consumer, rx, ry, rz);
	}

	private static void drawBoxLines(PoseStack.Pose pose, VertexConsumer consumer, float[] rx, float[] ry, float[] rz) {
		int[][] edges = {
			{0, 1}, {1, 2}, {2, 3}, {3, 0},
			{4, 5}, {5, 6}, {6, 7}, {7, 4},
			{0, 4}, {1, 5}, {2, 6}, {3, 7}
		};
		for (int[] e : edges) {
			drawLine(pose, consumer, rx[e[0]], ry[e[0]], rz[e[0]], rx[e[1]], ry[e[1]], rz[e[1]], 240);
		}
	}

	private static void drawLine(PoseStack.Pose pose, VertexConsumer consumer,
								 float x1, float y1, float z1,
								 float x2, float y2, float z2, int light) {
		float dx = x2 - x1;
		float dy = y2 - y1;
		float dz = z2 - z1;
		float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		float nx = 0.0F;
		float ny = 1.0F;
		float nz = 0.0F;
		if (len > 1.0E-4F) {
			nx = dx / len;
			ny = dy / len;
			nz = dz / len;
		}

		consumer.addVertex(pose, x1, y1, z1)
			.setColor(255, 230, 80, 255)
			.setNormal(pose, nx, ny, nz)
			.setLight(light)
			.setLineWidth(2.5F);

		consumer.addVertex(pose, x2, y2, z2)
			.setColor(255, 230, 80, 255)
			.setNormal(pose, nx, ny, nz)
			.setLight(light)
			.setLineWidth(2.5F);
	}
}
