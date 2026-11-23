package com.piotrek.pwrivers.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Configuration for Peterwolf's Rivers hydrodynamics simulation,
 * hydraulic structures, world generation, and graphics.
 */
public final class RiversConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("peterwolfs_rivers_config");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static File getConfigFile() {
		try {
			if (FabricLoader.getInstance() != null && FabricLoader.getInstance().getConfigDir() != null) {
				return FabricLoader.getInstance().getConfigDir().resolve("peterwolfs_rivers.json").toFile();
			}
		} catch (Throwable ignored) {}
		return new File("config/peterwolfs_rivers.json");
	}

	// Simulation
	public static boolean simulationEnabled = true;
	public static String simulationQuality = "HIGH"; // LOW, MEDIUM, HIGH, ULTRA
	public static int simulationUpdateInterval = 1;  // ticks
	public static int simulationActiveRadius = 8;    // chunks
	public static int simulationMaxActiveCells = 16384;
	public static boolean simulationAdaptiveResolution = true;

	// River Hydraulics
	public static double defaultDischarge = 120.0;     // m^3/s
	public static double defaultDepth = 4.5;           // blocks
	public static double defaultWidth = 35.0;          // blocks
	public static double flowSpeedMultiplier = 1.0;
	public static double turbulenceMultiplier = 1.0;
	public static double frictionCoefficient = 0.035;  // Manning n roughness

	// Wind Interaction
	public static double windSurfaceInfluence = 0.035; // surface drift factor
	public static double windWaveStrength = 0.6;

	// Locks & Weirs
	public static double lockFillRate = 0.05;         // blocks per tick (~1.0 m/s)
	public static double lockDrainRate = 0.05;        // blocks per tick
	public static boolean lockSafetyInterlocks = true;

	// World Generation
	public static boolean worldgenEnableCustomRivers = true;
	public static boolean worldgenEnableLargeRivers = true;
	public static boolean worldgenEnableBranches = true;
	public static boolean worldgenEnableGorges = true;

	// Graphics & Rendering
	public static boolean graphicsEnableFoam = true;
	public static boolean graphicsEnableEddies = true;
	public static boolean graphicsEnableSurfaceAnimation = true;
	public static boolean graphicsEnableDebugVectors = false;

	// Compatibility
	public static boolean compatibilityEnableWindAndSails = true;
	public static boolean compatibilityEnableVanillaBoatPhysics = true;

	private RiversConfig() {}

	public static void load() {
		File configFile = getConfigFile();
		if (!configFile.exists()) {
			save();
			return;
		}

		try (FileReader reader = new FileReader(configFile)) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

			if (json.has("simulation")) {
				JsonObject sim = json.getAsJsonObject("simulation");
				if (sim.has("enabled")) simulationEnabled = sim.get("enabled").getAsBoolean();
				if (sim.has("quality")) simulationQuality = sim.get("quality").getAsString();
				if (sim.has("updateInterval")) simulationUpdateInterval = Math.max(1, sim.get("updateInterval").getAsInt());
				if (sim.has("activeRadius")) simulationActiveRadius = Math.max(2, sim.get("activeRadius").getAsInt());
				if (sim.has("maxActiveCells")) simulationMaxActiveCells = Math.max(512, sim.get("maxActiveCells").getAsInt());
				if (sim.has("adaptiveResolution")) simulationAdaptiveResolution = sim.get("adaptiveResolution").getAsBoolean();
			}

			if (json.has("river")) {
				JsonObject riv = json.getAsJsonObject("river");
				if (riv.has("defaultDischarge")) defaultDischarge = Math.max(0.1, riv.get("defaultDischarge").getAsDouble());
				if (riv.has("defaultDepth")) defaultDepth = Math.max(0.5, riv.get("defaultDepth").getAsDouble());
				if (riv.has("defaultWidth")) defaultWidth = Math.max(1.0, riv.get("defaultWidth").getAsDouble());
				if (riv.has("flowSpeedMultiplier")) flowSpeedMultiplier = Math.max(0.0, riv.get("flowSpeedMultiplier").getAsDouble());
				if (riv.has("turbulenceMultiplier")) turbulenceMultiplier = Math.max(0.0, riv.get("turbulenceMultiplier").getAsDouble());
				if (riv.has("frictionCoefficient")) frictionCoefficient = Math.max(0.005, riv.get("frictionCoefficient").getAsDouble());
				if (riv.has("windSurfaceInfluence")) windSurfaceInfluence = Math.max(0.0, riv.get("windSurfaceInfluence").getAsDouble());
				if (riv.has("windWaveStrength")) windWaveStrength = Math.max(0.0, riv.get("windWaveStrength").getAsDouble());
			}

			if (json.has("locks")) {
				JsonObject locks = json.getAsJsonObject("locks");
				if (locks.has("fillRate")) lockFillRate = Math.max(0.005, locks.get("fillRate").getAsDouble());
				if (locks.has("drainRate")) lockDrainRate = Math.max(0.005, locks.get("drainRate").getAsDouble());
				if (locks.has("safetyInterlocks")) lockSafetyInterlocks = locks.get("safetyInterlocks").getAsBoolean();
			}

			if (json.has("worldgen")) {
				JsonObject wg = json.getAsJsonObject("worldgen");
				if (wg.has("enableCustomRivers")) worldgenEnableCustomRivers = wg.get("enableCustomRivers").getAsBoolean();
				if (wg.has("enableLargeRivers")) worldgenEnableLargeRivers = wg.get("enableLargeRivers").getAsBoolean();
				if (wg.has("enableBranches")) worldgenEnableBranches = wg.get("enableBranches").getAsBoolean();
				if (wg.has("enableGorges")) worldgenEnableGorges = wg.get("enableGorges").getAsBoolean();
			}

			if (json.has("graphics")) {
				JsonObject gfx = json.getAsJsonObject("graphics");
				if (gfx.has("enableFoam")) graphicsEnableFoam = gfx.get("enableFoam").getAsBoolean();
				if (gfx.has("enableEddies")) graphicsEnableEddies = gfx.get("enableEddies").getAsBoolean();
				if (gfx.has("enableSurfaceAnimation")) graphicsEnableSurfaceAnimation = gfx.get("enableSurfaceAnimation").getAsBoolean();
				if (gfx.has("enableDebugVectors")) graphicsEnableDebugVectors = gfx.get("enableDebugVectors").getAsBoolean();
			}

			if (json.has("compatibility")) {
				JsonObject comp = json.getAsJsonObject("compatibility");
				if (comp.has("enableWindAndSails")) compatibilityEnableWindAndSails = comp.get("enableWindAndSails").getAsBoolean();
				if (comp.has("enableVanillaBoatPhysics")) compatibilityEnableVanillaBoatPhysics = comp.get("enableVanillaBoatPhysics").getAsBoolean();
			}

			LOGGER.info("Loaded Peterwolf's Rivers configuration successfully");
		} catch (Exception e) {
			LOGGER.error("Failed to load configuration file, writing defaults", e);
			save();
		}
	}

	public static void save() {
		JsonObject root = new JsonObject();

		JsonObject sim = new JsonObject();
		sim.addProperty("enabled", simulationEnabled);
		sim.addProperty("quality", simulationQuality);
		sim.addProperty("updateInterval", simulationUpdateInterval);
		sim.addProperty("activeRadius", simulationActiveRadius);
		sim.addProperty("maxActiveCells", simulationMaxActiveCells);
		sim.addProperty("adaptiveResolution", simulationAdaptiveResolution);
		root.add("simulation", sim);

		JsonObject riv = new JsonObject();
		riv.addProperty("defaultDischarge", defaultDischarge);
		riv.addProperty("defaultDepth", defaultDepth);
		riv.addProperty("defaultWidth", defaultWidth);
		riv.addProperty("flowSpeedMultiplier", flowSpeedMultiplier);
		riv.addProperty("turbulenceMultiplier", turbulenceMultiplier);
		riv.addProperty("frictionCoefficient", frictionCoefficient);
		riv.addProperty("windSurfaceInfluence", windSurfaceInfluence);
		riv.addProperty("windWaveStrength", windWaveStrength);
		root.add("river", riv);

		JsonObject locks = new JsonObject();
		locks.addProperty("fillRate", lockFillRate);
		locks.addProperty("drainRate", lockDrainRate);
		locks.addProperty("safetyInterlocks", lockSafetyInterlocks);
		root.add("locks", locks);

		JsonObject wg = new JsonObject();
		wg.addProperty("enableCustomRivers", worldgenEnableCustomRivers);
		wg.addProperty("enableLargeRivers", worldgenEnableLargeRivers);
		wg.addProperty("enableBranches", worldgenEnableBranches);
		wg.addProperty("enableGorges", worldgenEnableGorges);
		root.add("worldgen", wg);

		JsonObject gfx = new JsonObject();
		gfx.addProperty("enableFoam", graphicsEnableFoam);
		gfx.addProperty("enableEddies", graphicsEnableEddies);
		gfx.addProperty("enableSurfaceAnimation", graphicsEnableSurfaceAnimation);
		gfx.addProperty("enableDebugVectors", graphicsEnableDebugVectors);
		root.add("graphics", gfx);

		JsonObject comp = new JsonObject();
		comp.addProperty("enableWindAndSails", compatibilityEnableWindAndSails);
		comp.addProperty("enableVanillaBoatPhysics", compatibilityEnableVanillaBoatPhysics);
		root.add("compatibility", comp);

		try {
			File configFile = getConfigFile();
			if (configFile.getParentFile() != null && !configFile.getParentFile().exists()) {
				configFile.getParentFile().mkdirs();
			}
			try (FileWriter writer = new FileWriter(configFile)) {
				GSON.toJson(root, writer);
			}
		} catch (IOException e) {
			LOGGER.error("Failed to write configuration file", e);
		}
	}
}
