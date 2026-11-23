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

	/**
	 * Primary river biome: Rivers by Peterwolf ("Rzeki Peterwolfa").
	 */
	public static final ResourceKey<Biome> RIVERS_BY_PETERWOLF = ResourceKey.create(
		Registries.BIOME,
		Identifier.fromNamespaceAndPath(MOD_ID, "rivers_by_peterwolf")
	);

	/**
	 * Backward compatibility alias pointing to RIVERS_BY_PETERWOLF.
	 */
	public static final ResourceKey<Biome> RIVER_BIOME = RIVERS_BY_PETERWOLF;

	private RiverBiomeRegistry() {}

	public static void init() {
		// Registered as dynamic datapack/registry entry
	}
}
