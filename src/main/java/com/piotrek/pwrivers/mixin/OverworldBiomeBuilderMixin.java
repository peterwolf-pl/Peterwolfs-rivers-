package com.piotrek.pwrivers.mixin;

import com.mojang.datafixers.util.Pair;
import com.piotrek.pwrivers.worldgen.RiverBiomeRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Consumer;

/**
 * Mixin into Minecraft OverworldBiomeBuilder to register Rivers by Peterwolf
 * as the primary river biome across the Overworld.
 */
@Mixin(OverworldBiomeBuilder.class)
public class OverworldBiomeBuilderMixin {
	@ModifyVariable(method = "addBiomes", at = @At("HEAD"), argsOnly = true)
	private Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> wrapRiverBiomes(
		Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> original
	) {
		return pair -> {
			if (Biomes.RIVER.equals(pair.getSecond())) {
				original.accept(Pair.of(pair.getFirst(), RiverBiomeRegistry.RIVERS_BY_PETERWOLF));
			} else {
				original.accept(pair);
			}
		};
	}
}
