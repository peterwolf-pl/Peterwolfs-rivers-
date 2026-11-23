package com.piotrek.pwrivers.vessel;

import com.piotrek.pwrivers.PeterwolfsRiversMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Item for deploying the Wressla River Catamaran.
 * Can be placed directly on open water (rivers, oceans, lakes) or on solid docks/shorelines.
 */
public class WresslaItem extends Item {
	public WresslaItem(Properties properties) {
		super(properties);
	}

	/**
	 * Places the catamaran when right-clicking on solid blocks (docks, piers, shoreline).
	 */
	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		Player player = context.getPlayer();
		ItemStack stack = context.getItemInHand();
		BlockPos clicked = context.getClickedPos();

		if (!level.isClientSide()) {
			spawnVessel(level, player, stack, clicked.getX() + 0.5D, clicked.getY() + 1.10D, clicked.getZ() + 0.5D);
		}
		if (player != null) {
			player.awardStat(Stats.ITEM_USED.get(this));
		}
		return InteractionResult.SUCCESS;
	}

	/**
	 * Places the catamaran when right-clicking open water, river water, or lakes.
	 */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);

		if (hit.getType() == HitResult.Type.MISS) {
			return InteractionResult.PASS;
		}

		if (hit.getType() == HitResult.Type.BLOCK) {
			if (!level.isClientSide()) {
				spawnVessel(level, player, stack, hit.getLocation().x, hit.getLocation().y + 0.25D, hit.getLocation().z);
			}
			player.awardStat(Stats.ITEM_USED.get(this));
			return InteractionResult.SUCCESS;
		}

		return InteractionResult.PASS;
	}

	private void spawnVessel(Level level, @Nullable Player player, ItemStack stack, double x, double y, double z) {
		if (level instanceof ServerLevel serverLevel) {
			WresslaEntity wressla = PeterwolfsRiversMod.WRESSLA_ENTITY.create(
				serverLevel,
				EntitySpawnReason.SPAWN_ITEM_USE
			);

			if (wressla != null) {
				wressla.setPos(x, y, z);
				wressla.setYRot(player != null ? player.getYRot() : 0.0F);
				serverLevel.addFreshEntity(wressla);
				level.gameEvent(player, GameEvent.ENTITY_PLACE, BlockPos.containing(x, y, z));
				level.playSound(
					null, x, y, z,
					SoundEvents.BOAT_PADDLE_WATER, SoundSource.BLOCKS, 1.2F, 0.9F
				);
				if (player != null && !player.getAbilities().instabuild) {
					stack.shrink(1);
				}
			}
		}
	}
}
