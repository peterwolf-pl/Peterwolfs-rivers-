package com.piotrek.pwrivers.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/**
 * Biome registry for Peterwolf's custom river biomes.
 */
public final class RiverBiomeRegistry {
	public static final String MOD_ID = "peterwolfs_rivers";

	public static final ResourceKey<Biome> RIVER_BIOME = ResourceKey.create(
		Registries.BIOME,
		Identifier.fromNamespaceAndPath(MOD_ID, "river")
	);

	private RiverBiomeRegistry() {}

	public static void init() {
		// Registered as dynamic datapack/registry entry
	}
}
