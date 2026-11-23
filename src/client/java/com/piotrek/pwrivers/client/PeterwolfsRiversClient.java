package com.piotrek.pwrivers.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.piotrek.pwrivers.PeterwolfsRiversMod;
import com.piotrek.pwrivers.client.vessel.WresslaModel;
import com.piotrek.pwrivers.client.vessel.WresslaRenderer;
import com.piotrek.pwrivers.client.vessel.WresslaWheelHighlightRenderer;
import com.piotrek.pwrivers.vessel.WresslaEntity;
import com.piotrek.pwrivers.vessel.WresslaInputPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Client entrypoint for Peterwolf's Rivers.
 * Registers custom water shaders/models, particle effects, keybindings,
 * and riverboat entity models.
 */
public class PeterwolfsRiversClient implements ClientModInitializer {
	public static final ModelLayerLocation WRESSLA_LAYER = new ModelLayerLocation(
		PeterwolfsRiversMod.id("wressla"), "main"
	);
	public static final ModelLayerLocation WRESSLA_WATER_PATCH = new ModelLayerLocation(
		PeterwolfsRiversMod.id("wressla"), "water_patch"
	);

	public static final KeyMapping KEY_WRESSLA_ENGINE = new KeyMapping(
		"key.peterwolfs_rivers.engine",
		InputConstants.Type.KEYBOARD,
		29, // Ctrl key
		KeyMapping.Category.GAMEPLAY
	);

	private static float lastRudder = 0.0F;
	private static float lastThrottle = 0.0F;

	@Override
	public void onInitializeClient() {
		// 1. Register River Water Custom Fluid Rendering
		RiverClientEffects.registerFluidRenderer();

		// 2. Register Wressla Vessel Models and Renderers
		EntityRendererRegistry.register(PeterwolfsRiversMod.WRESSLA_ENTITY, WresslaRenderer::new);
		ModelLayerRegistry.registerModelLayer(WRESSLA_LAYER, WresslaModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(WRESSLA_WATER_PATCH, WresslaModel::createWaterPatch);
		KeyMappingHelper.registerKeyMapping(KEY_WRESSLA_ENGINE);

		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(WresslaWheelHighlightRenderer::render);

		// 3. Register Client Tick Events (Vessel input transmission & River visual effects)
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			RiverClientEffects.tickClientEffects();

			if (client.player != null && client.player.getVehicle() instanceof WresslaEntity wressla) {
				if (wressla.isHelmsman(client.player)) {
					float rudder = 0.0F;
					if (client.options.keyLeft.isDown()) rudder -= 1.0F;
					if (client.options.keyRight.isDown()) rudder += 1.0F;

					float throttle = 0.0F;
					if (client.options.keyUp.isDown()) throttle += 1.0F;
					if (client.options.keyDown.isDown()) throttle -= 1.0F;

					boolean toggleEngine = KEY_WRESSLA_ENGINE.consumeClick();
					boolean dismountHelm = client.options.keyShift.consumeClick();

					if (rudder != lastRudder || throttle != lastThrottle || toggleEngine || dismountHelm) {
						lastRudder = rudder;
						lastThrottle = throttle;
						ClientPlayNetworking.send(new WresslaInputPayload(
							wressla.getId(),
							rudder,
							throttle,
							toggleEngine,
							dismountHelm
						));
					}
				}
			}
		});
	}
}
