package com.piotrek.pwrivers.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.piotrek.pwrivers.PeterwolfsRiversMod;
import com.piotrek.pwrivers.client.vessel.WresslaHudOverlay;
import com.piotrek.pwrivers.client.vessel.WresslaModel;
import com.piotrek.pwrivers.client.vessel.WresslaRenderer;
import com.piotrek.pwrivers.client.vessel.WresslaWheelHighlightRenderer;
import com.piotrek.pwrivers.vessel.WresslaEntity;
import com.piotrek.pwrivers.vessel.WresslaInputPayload;
import com.piotrek.pwrivers.vessel.WresslaMooringAim;
import com.piotrek.pwrivers.vessel.WresslaMooringPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

/**
 * Client entrypoint for Peterwolf's Rivers.
 * Registers custom water shaders/models, particle effects, keybindings,
 * HUD overlay, and riverboat entity models.
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
		InputConstants.KEY_Z,
		KeyMapping.Category.GAMEPLAY
	);

	private static float lastRudder = 0.0F;
	private static float lastThrottle = 0.0F;
	private static boolean wasRidingWressla = false;
	private static boolean wasUseDown = false;

	@Override
	public void onInitializeClient() {
		// 1. Register River Water Custom Fluid Rendering
		RiverClientEffects.registerFluidRenderer();

		// 2. Register Wressla Vessel Models, Renderers & HUD
		EntityRendererRegistry.register(PeterwolfsRiversMod.WRESSLA_ENTITY, WresslaRenderer::new);
		ModelLayerRegistry.registerModelLayer(WRESSLA_LAYER, WresslaModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(WRESSLA_WATER_PATCH, WresslaModel::createWaterPatch);
		KeyMappingHelper.registerKeyMapping(KEY_WRESSLA_ENGINE);

		HudElementRegistry.addLast(PeterwolfsRiversMod.id("wressla_hud"), WresslaHudOverlay.INSTANCE);
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(WresslaWheelHighlightRenderer::render);

		// 3. Register Client Tick Events (Vessel input transmission & River visual effects)
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			if (client.player == null || client.level == null) {
				wasUseDown = false;
				return;
			}
			boolean useDown = client.options.keyUse.isDown();
			if (useDown && !wasUseDown) {
				WresslaMooringAim.Target aim = WresslaMooringAim.find(client.player, client.level, 1.0F);
				if (aim != null && WresslaEntity.shouldOwnMooringClick(client.player, aim)) {
					if (aim.cleat()) {
						ClientPlayNetworking.send(new WresslaMooringPayload(true, aim.vesselId(), aim.cleatIndex(), BlockPos.ZERO));
					} else if (aim.fence() != null) {
						ClientPlayNetworking.send(new WresslaMooringPayload(false, -1, -1, aim.fence()));
					}
					while (client.options.keyUse.consumeClick()) {
						// Eat click so player does not use lead on unrelated mobs/blocks
					}
				}
			}
			wasUseDown = useDown;
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			RiverClientEffects.tickClientEffects();

			if (client.player != null && client.player.getVehicle() instanceof WresslaEntity wressla) {
				boolean justBoarded = !wasRidingWressla;
				wasRidingWressla = true;

				if (justBoarded) {
					// Flush any buffered key clicks from walking/sneaking before boarding
					client.options.keyShift.consumeClick();
					KEY_WRESSLA_ENGINE.consumeClick();
				}

				float rudder = 0.0F;
				if (client.options.keyLeft.isDown()) rudder -= 1.0F;
				if (client.options.keyRight.isDown()) rudder += 1.0F;

				float throttle = 0.0F;
				if (client.options.keyUp.isDown()) throttle += 1.0F;
				if (client.options.keyDown.isDown()) throttle -= 1.0F;

				boolean toggleEngine = !justBoarded && KEY_WRESSLA_ENGINE.consumeClick();
				boolean dismountHelm = !justBoarded && client.options.keyShift.consumeClick();

				boolean inputChanged = Math.abs(rudder - lastRudder) > 0.01F || Math.abs(throttle - lastThrottle) > 0.01F;

				if (toggleEngine || dismountHelm || inputChanged || client.player.tickCount % 5 == 0) {
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
			} else {
				wasRidingWressla = false;
			}
		});
	}
}
