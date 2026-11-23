package com.piotrek.pwrivers.fluid;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;

/**
 * Registry holding the custom river water fluid, fluid blocks, and bucket items.
 */
public final class RiverFluidRegistry {
	public static final String MOD_ID = "peterwolfs_rivers";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	// Fluid Resource Keys
	public static final ResourceKey<Fluid> RIVER_WATER_KEY = ResourceKey.create(
		Registries.FLUID, id("river_water")
	);
	public static final ResourceKey<Fluid> FLOWING_RIVER_WATER_KEY = ResourceKey.create(
		Registries.FLUID, id("flowing_river_water")
	);

	// Fluid Instances
	public static final FlowingFluid RIVER_WATER = Registry.register(
		BuiltInRegistries.FLUID,
		RIVER_WATER_KEY,
		new RiverWaterFluid.Source()
	);
	public static final FlowingFluid FLOWING_RIVER_WATER = Registry.register(
		BuiltInRegistries.FLUID,
		FLOWING_RIVER_WATER_KEY,
		new RiverWaterFluid.Flowing()
	);

	// Block Resource Key & Block
	public static final ResourceKey<net.minecraft.world.level.block.Block> RIVER_WATER_BLOCK_KEY = ResourceKey.create(
		Registries.BLOCK, id("river_water_block")
	);
	public static final RiverWaterBlock RIVER_WATER_BLOCK = Registry.register(
		BuiltInRegistries.BLOCK,
		RIVER_WATER_BLOCK_KEY,
		new RiverWaterBlock(
			RIVER_WATER,
			BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).setId(RIVER_WATER_BLOCK_KEY)
		)
	);

	// Bucket Item Key & Item
	public static final ResourceKey<Item> RIVER_WATER_BUCKET_KEY = ResourceKey.create(
		Registries.ITEM, id("river_water_bucket")
	);
	public static final Item RIVER_WATER_BUCKET = Registry.register(
		BuiltInRegistries.ITEM,
		RIVER_WATER_BUCKET_KEY,
		new BucketItem(
			RIVER_WATER,
			new Item.Properties()
				.setId(RIVER_WATER_BUCKET_KEY)
				.craftRemainder(Items.BUCKET)
				.stacksTo(1)
		)
	);

	private RiverFluidRegistry() {}

	public static void init() {
		// Triggers static initialization
	}
}
