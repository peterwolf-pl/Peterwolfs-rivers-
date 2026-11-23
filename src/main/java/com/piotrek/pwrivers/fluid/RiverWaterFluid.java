package com.piotrek.pwrivers.fluid;

import com.piotrek.pwrivers.physics.RiverFlowSample;
import com.piotrek.pwrivers.physics.RiverPhysicsEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Custom fluid implementation for Peterwolf's Rivers: river_water.
 * Decoupled from vanilla fluid spreading to preserve hydraulic mass conservation.
 * Hydrodynamic flow vector is sampled directly from RiverPhysicsEngine.
 */
public abstract class RiverWaterFluid extends FlowingFluid {
	@Override
	public Fluid getFlowing() {
		return RiverFluidRegistry.FLOWING_RIVER_WATER;
	}

	@Override
	public Fluid getSource() {
		return RiverFluidRegistry.RIVER_WATER;
	}

	@Override
	public Item getBucket() {
		return RiverFluidRegistry.RIVER_WATER_BUCKET;
	}

	@Override
	public Vec3 getFlow(BlockGetter level, BlockPos pos, FluidState state) {
		Level l = level instanceof Level lvl ? lvl : null;
		RiverFlowSample sample = RiverPhysicsEngine.getInstance().sampleFlow(l, Vec3.atCenterOf(pos));
		if (sample.hasCurrent()) {
			return sample.velocity();
		}
		return super.getFlow(level, pos, state);
	}

	@Override
	public void animateTick(Level level, BlockPos pos, FluidState state, RandomSource random) {
		if (random.nextInt(48) == 0) {
			level.playLocalSound(
				pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
				SoundEvents.WATER_AMBIENT,
				net.minecraft.sounds.SoundSource.BLOCKS,
				random.nextFloat() * 0.25F + 0.75F,
				random.nextFloat() + 0.5F,
				false
			);
		}
	}

	@Override
	public ParticleOptions getDripParticle() {
		return ParticleTypes.DRIPPING_WATER;
	}

	@Override
	protected boolean canConvertToSource(ServerLevel level) {
		return false; // Rivers maintain levels through simulation rather than vanilla infinite water
	}

	@Override
	protected void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state) {
		BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
		Block.dropResources(state, level, pos, blockEntity);
	}

	@Override
	public int getSlopeFindDistance(LevelReader level) {
		return 4;
	}

	@Override
	public BlockState createLegacyBlock(FluidState state) {
		return RiverFluidRegistry.RIVER_WATER_BLOCK.defaultBlockState()
			.setValue(RiverWaterBlock.LEVEL, getLegacyLevel(state));
	}

	@Override
	public boolean isSame(Fluid fluid) {
		return fluid == getSource() || fluid == getFlowing();
	}

	@Override
	public int getDropOff(LevelReader level) {
		return 1;
	}

	@Override
	public int getTickDelay(LevelReader level) {
		return 5;
	}

	@Override
	protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluid, Direction direction) {
		return direction == Direction.DOWN && !isSame(fluid);
	}

	@Override
	protected float getExplosionResistance() {
		return 100.0F;
	}

	@Override
	public Optional<SoundEvent> getPickupSound() {
		return Optional.of(SoundEvents.BUCKET_FILL);
	}

	public static class Flowing extends RiverWaterFluid {
		@Override
		protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
			super.createFluidStateDefinition(builder);
			builder.add(LEVEL);
		}

		@Override
		public int getAmount(FluidState state) {
			return state.getValue(LEVEL);
		}

		@Override
		public boolean isSource(FluidState state) {
			return false;
		}
	}

	public static class Source extends RiverWaterFluid {
		@Override
		public int getAmount(FluidState state) {
			return 8;
		}

		@Override
		public boolean isSource(FluidState state) {
			return true;
		}
	}
}
