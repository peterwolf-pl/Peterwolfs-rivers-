package com.piotrek.pwrivers.vessel;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * High-precision mooring raycast aim for Wressla corner cleats and dock fence/wall posts.
 * Replicates the authentic feel and behavior of the Wind & Sails mooring system.
 */
public final class WresslaMooringAim {
	public static final double REACH = 14.0;
	public static final double CLEAT_HALF_X = 0.22;
	public static final double CLEAT_HALF_Z = 0.35;
	public static final double CLEAT_HALF_Y = 0.22;
	public static final double POST_MIN = 0.375;
	public static final double POST_MAX = 0.625;
	public static final double CLICK_PAD = 0.12;

	private WresslaMooringAim() {}

	public record Target(boolean cleat, int vesselId, int cleatIndex, BlockPos fence, double distance) {}

	public static Target find(Player player, Level level, float partialTick) {
		Vec3 eye = player.getEyePosition(partialTick);
		Vec3 look = player.getViewVector(partialTick);
		if (look.lengthSqr() < 1.0E-8) {
			return null;
		}
		look = look.normalize();
		Vec3 end = eye.add(look.scale(REACH));

		BlockHitResult blockHit = level.clip(new ClipContext(
			eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		double occlude = REACH;
		BlockPos occludePos = null;
		if (blockHit.getType() == HitResult.Type.BLOCK) {
			occlude = eye.distanceTo(blockHit.getLocation());
			occludePos = blockHit.getBlockPos();
		}

		Target best = null;

		// 1. Cleat check on nearby Wresslas
		AABB vesselSearch = player.getBoundingBox().inflate(REACH + 4.0);
		for (WresslaEntity wressla : level.getEntitiesOfClass(WresslaEntity.class, vesselSearch)) {
			Vec3 pos = wressla.getPosition(partialTick);
			float yaw = Mth.rotLerp(partialTick, wressla.yRotO, wressla.getYRot());
			for (int i = 0; i < WresslaEntity.CLEAT_COUNT; i++) {
				double dist = cleatDistance(eye, look, pos, yaw, wressla.getCleatLocalOffset(i), CLICK_PAD);
				if (dist > REACH || dist > occlude + 0.15) {
					continue;
				}
				if (best == null || dist < best.distance) {
					best = new Target(true, wressla.getId(), i, BlockPos.ZERO, dist);
				}
			}
		}

		// 2. Fence & Wall posts in ray volume
		int minX = Mth.floor(Math.min(eye.x, end.x));
		int minY = Mth.floor(Math.min(eye.y, end.y));
		int minZ = Mth.floor(Math.min(eye.z, end.z));
		int maxX = Mth.floor(Math.max(eye.x, end.x));
		int maxY = Mth.floor(Math.max(eye.y, end.y));
		int maxZ = Mth.floor(Math.max(eye.z, end.z));

		for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
			if (!isMooringPostBlock(level, pos)) {
				continue;
			}
			BlockPos fixed = pos.immutable();
			double dist = boxDistance(eye, end, postColumn(fixed));
			if (!accept(dist, occlude, occludePos, fixed)) {
				continue;
			}
			if (best == null || dist < best.distance) {
				best = new Target(false, 0, -1, fixed, dist);
			}
		}

		// 3. Leash knots on fence posts
		AABB knotArea = new AABB(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0);
		for (LeashFenceKnotEntity knot : level.getEntitiesOfClass(LeashFenceKnotEntity.class, knotArea)) {
			BlockPos pos = knot.getPos();
			if (pos == null || !isMooringPostBlock(level, pos)) {
				continue;
			}
			double dist = boxDistance(eye, end, knot.getBoundingBox().inflate(CLICK_PAD));
			if (!accept(dist, occlude, occludePos, pos)) {
				continue;
			}
			if (best == null || dist < best.distance) {
				best = new Target(false, 0, -1, pos, dist);
			}
		}

		// 4. Direct occlude hit check: If raycast hit a fence or wall block directly, always accept
		if (occludePos != null && isMooringPostBlock(level, occludePos)) {
			if (best == null || occlude <= best.distance + 0.25) {
				best = new Target(false, 0, -1, occludePos.immutable(), occlude);
			}
		}

		return best;
	}

	public static boolean isMooringPostBlock(Level level, BlockPos pos) {
		return level.getBlockState(pos).is(BlockTags.FENCES) || level.getBlockState(pos).is(BlockTags.WALLS);
	}

	public static AABB cleatLocalBox(Vec3 offset, double pad) {
		return new AABB(
			offset.x - CLEAT_HALF_X - pad,
			offset.y - CLEAT_HALF_Y - pad,
			offset.z - CLEAT_HALF_Z - pad,
			offset.x + CLEAT_HALF_X + pad,
			offset.y + CLEAT_HALF_Y + pad,
			offset.z + CLEAT_HALF_Z + pad
		);
	}

	public static AABB postColumn(BlockPos pos) {
		return new AABB(
			pos.getX() + POST_MIN - CLICK_PAD,
			pos.getY(),
			pos.getZ() + POST_MIN - CLICK_PAD,
			pos.getX() + POST_MAX + CLICK_PAD,
			pos.getY() + 1.0,
			pos.getZ() + POST_MAX + CLICK_PAD
		);
	}

	public static double boxDistance(Vec3 from, Vec3 to, AABB box) {
		Optional<Vec3> hit = box.clip(from, to);
		return hit.map(from::distanceTo).orElse(Double.MAX_VALUE);
	}

	private static boolean accept(double dist, double occlude, BlockPos occludePos, BlockPos pos) {
		if (dist > REACH) {
			return false;
		}
		return dist <= occlude + 0.25 || (occludePos != null && occludePos.equals(pos));
	}

	private static double cleatDistance(Vec3 eye, Vec3 look, Vec3 boatPos, float yaw, Vec3 offset, double pad) {
		float yawRad = yaw * Mth.DEG_TO_RAD;
		double fwdX = -Mth.sin(yawRad);
		double fwdZ = Mth.cos(yawRad);
		double rightX = Mth.cos(yawRad);
		double rightZ = Mth.sin(yawRad);

		Vec3 relEye = eye.subtract(boatPos);
		double localEyeX = relEye.x * rightX + relEye.z * rightZ;
		double localEyeY = relEye.y;
		double localEyeZ = relEye.x * fwdX + relEye.z * fwdZ;

		double localLookX = look.x * rightX + look.z * rightZ;
		double localLookY = look.y;
		double localLookZ = look.x * fwdX + look.z * fwdZ;

		Vec3 localStart = new Vec3(localEyeX, localEyeY, localEyeZ);
		Vec3 localEnd = localStart.add(localLookX * REACH, localLookY * REACH, localLookZ * REACH);

		AABB box = cleatLocalBox(offset, pad);
		Optional<Vec3> hit = box.clip(localStart, localEnd);
		return hit.map(localStart::distanceTo).orElse(Double.MAX_VALUE);
	}
}
