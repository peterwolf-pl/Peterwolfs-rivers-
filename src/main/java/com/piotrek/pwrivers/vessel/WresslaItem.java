package com.piotrek.pwrivers.vessel;

import com.piotrek.pwrivers.PeterwolfsRiversMod;
import com.piotrek.pwrivers.vessel.WresslaEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.HitResult;

public class WresslaItem extends Item {
	public WresslaItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		HitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);

		if (hit.getType() == HitResult.Type.MISS) {
			return InteractionResult.PASS;
		}

		if (hit.getType() == HitResult.Type.BLOCK) {
			BlockPos hitPos = BlockPos.containing(hit.getLocation());

			if (!level.getFluidState(hitPos).is(FluidTags.WATER)) {
				hitPos = hitPos.above();
			}

			if (!level.getFluidState(hitPos).is(FluidTags.WATER)) {
				return InteractionResult.PASS;
			}

			if (level instanceof ServerLevel serverLevel) {
				WresslaEntity wressla = PeterwolfsRiversMod.WRESSLA_ENTITY.create(
					serverLevel,
					EntitySpawnReason.SPAWN_ITEM_USE
				);

				if (wressla != null) {
					wressla.setPos(hit.getLocation().x, hitPos.getY() + 0.60, hit.getLocation().z);
					wressla.setYRot(player.getYRot());
					serverLevel.addFreshEntity(wressla);
					level.gameEvent(player, GameEvent.ENTITY_PLACE, hitPos);
					level.playSound(null, wressla.getX(), wressla.getY(), wressla.getZ(),
						SoundEvents.BOAT_PADDLE_WATER, SoundSource.BLOCKS, 1.2F, 0.9F);
				}

				if (!player.getAbilities().instabuild) {
					stack.shrink(1);
				}
			}

			player.awardStat(Stats.ITEM_USED.get(this));
			return InteractionResult.SUCCESS;
		}

		return InteractionResult.PASS;
	}
}
