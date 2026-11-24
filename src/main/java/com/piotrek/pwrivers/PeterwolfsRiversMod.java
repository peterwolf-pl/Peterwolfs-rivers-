package com.piotrek.pwrivers;

import com.piotrek.pwrivers.command.RiversCommands;
import com.piotrek.pwrivers.config.RiversConfig;
import com.piotrek.pwrivers.entity.RiverEntityPhysics;
import com.piotrek.pwrivers.fluid.RiverFluidRegistry;
import com.piotrek.pwrivers.persistence.RiverSimulationStorage;
import com.piotrek.pwrivers.physics.RiverPhysicsEngine;
import com.piotrek.pwrivers.vessel.WresslaEntity;
import com.piotrek.pwrivers.vessel.WresslaItem;
import com.piotrek.pwrivers.vessel.WresslaInputPayload;
import com.piotrek.pwrivers.vessel.WresslaMooringPayload;
import com.piotrek.pwrivers.worldgen.RiverBiomeRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main initialization entrypoint for Peterwolf's Rivers.
 */
public class PeterwolfsRiversMod implements ModInitializer {
	public static final String MOD_ID = "peterwolfs_rivers";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	// Entity Registration
	public static final ResourceKey<EntityType<?>> WRESSLA_KEY = ResourceKey.create(
		Registries.ENTITY_TYPE, id("wressla")
	);

	public static final EntityType<WresslaEntity> WRESSLA_ENTITY = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		WRESSLA_KEY,
		EntityType.Builder.of(WresslaEntity::new, MobCategory.MISC)
			.sized(3.5F, 2.5F)
			.eyeHeight(1.8F)
			.clientTrackingRange(16)
			.build(WRESSLA_KEY)
	);

	// Item Registration
	public static final ResourceKey<Item> WRESSLA_ITEM_KEY = ResourceKey.create(
		Registries.ITEM, id("wressla")
	);

	public static final WresslaItem WRESSLA_ITEM = Registry.register(
		BuiltInRegistries.ITEM,
		WRESSLA_ITEM_KEY,
		new WresslaItem(new Item.Properties().setId(WRESSLA_ITEM_KEY).stacksTo(1))
	);

	// Creative Tab
	public static final ResourceKey<CreativeModeTab> RIVERS_TAB_KEY = ResourceKey.create(
		Registries.CREATIVE_MODE_TAB, id("main")
	);

	public static final CreativeModeTab RIVERS_TAB = Registry.register(
		BuiltInRegistries.CREATIVE_MODE_TAB,
		RIVERS_TAB_KEY,
		FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.peterwolfs_rivers.main"))
			.icon(() -> new ItemStack(WRESSLA_ITEM))
			.displayItems((params, output) -> {
				output.accept(WRESSLA_ITEM);
				output.accept(RiverFluidRegistry.RIVER_WATER_BUCKET);
			})
			.build()
	);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Peterwolf's Rivers: Computational Hydrodynamics & River Navigation Engine");

		// 1. Configuration
		RiversConfig.load();

		// 2. Custom River Fluid & Blocks
		RiverFluidRegistry.init();

		// 3. Custom Biome Registry
		RiverBiomeRegistry.init();

		// 4. Administration & Diagnostics Commands
		RiversCommands.register();

		// 5. Network Payload
		PayloadTypeRegistry.serverboundPlay().register(WresslaInputPayload.TYPE, WresslaInputPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(WresslaMooringPayload.TYPE, WresslaMooringPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(WresslaInputPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				var player = context.player();
				if (player.getVehicle() instanceof WresslaEntity wressla) {
					wressla.setControlInput(payload.rudderInput(), payload.throttleInput());
					if (payload.toggleEngine()) {
						wressla.toggleEngine(player);
					}
					if (payload.dismountHelm()) {
						wressla.releaseHelm(player);
					}
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(WresslaMooringPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				var player = context.player();
				WresslaEntity.handleMooringClick(player, payload.cleat(), payload.vesselEntityId(), payload.cleatIndex(), payload.fencePos());
			});
		});

		// 6. Helmsman, Cleat & Mooring Interaction Callbacks
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
			if (player.getVehicle() instanceof WresslaEntity) return InteractionResult.PASS;
			if (WresslaEntity.handleAimedControl(player)) return InteractionResult.SUCCESS;
			if (WresslaEntity.handleAimedMooringClick(player)) return InteractionResult.SUCCESS;
			return InteractionResult.PASS;
		});

		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
			if (player.getVehicle() instanceof WresslaEntity) return InteractionResult.PASS;
			if (WresslaEntity.handleAimedControl(player)) return InteractionResult.SUCCESS;

			BlockPos clicked = hitResult.getBlockPos();
			if (level.getBlockState(clicked).is(BlockTags.FENCES) || level.getBlockState(clicked).is(BlockTags.WALLS)) {
				if (WresslaEntity.interactFence(player, clicked)) {
					return InteractionResult.SUCCESS;
				}
			}

			if (WresslaEntity.handleAimedMooringClick(player)) return InteractionResult.SUCCESS;
			return InteractionResult.PASS;
		});

		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
			if (player.getVehicle() instanceof WresslaEntity) return InteractionResult.PASS;

			if (entity instanceof WresslaEntity wressla) {
				boolean holdsLead = player.getMainHandItem().is(net.minecraft.world.item.Items.LEAD) || player.getOffhandItem().is(net.minecraft.world.item.Items.LEAD);
				boolean holdsLine = WresslaEntity.isPlayerHoldingAnyCleat(player);
				boolean hasPending = WresslaEntity.getPendingFenceMooring(player.getUUID()) != null;
				if (holdsLead || holdsLine || hasPending) {
					net.minecraft.world.phys.Vec3 loc = hitResult != null ? hitResult.getLocation().subtract(wressla.position()) : net.minecraft.world.phys.Vec3.ZERO;
					if (wressla.interact(player, hand, loc) == InteractionResult.SUCCESS) {
						return InteractionResult.SUCCESS;
					}
				}
				return InteractionResult.PASS;
			}

			if (entity instanceof LeashFenceKnotEntity knot) {
				BlockPos pos = knot.getPos();
				if (pos != null && WresslaEntity.interactFence(player, pos)) {
					return InteractionResult.SUCCESS;
				}
			}

			if (WresslaEntity.handleAimedControl(player)) return InteractionResult.SUCCESS;
			if (WresslaEntity.handleEntityMooringClick(player, entity)) return InteractionResult.SUCCESS;
			if (WresslaEntity.handleAimedMooringClick(player)) return InteractionResult.SUCCESS;
			return InteractionResult.PASS;
		});

		// 7. Server Lifecycle & World Physics Ticks
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			RiverPhysicsEngine.getInstance().tickServer(level);
			RiverEntityPhysics.tickLevel(level);
		});

		ServerLevelEvents.LOAD.register((server, level) -> {
			RiverSimulationStorage storage = RiverSimulationStorage.get(level);
			storage.applyToEngine();
		});

		ServerLevelEvents.UNLOAD.register((server, level) -> {
			RiverSimulationStorage storage = RiverSimulationStorage.get(level);
			storage.syncFromEngine();
		});

		LOGGER.info("Peterwolf's Rivers initialized successfully!");
	}
}
