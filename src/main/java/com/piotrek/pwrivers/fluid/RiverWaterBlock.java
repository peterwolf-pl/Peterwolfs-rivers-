package com.piotrek.pwrivers.fluid;

import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * Liquid block representing the physical volume of Peterwolf's river water in the Minecraft world.
 */
public class RiverWaterBlock extends LiquidBlock {
	public RiverWaterBlock(FlowingFluid fluid, BlockBehaviour.Properties properties) {
		super(fluid, properties);
	}
}
