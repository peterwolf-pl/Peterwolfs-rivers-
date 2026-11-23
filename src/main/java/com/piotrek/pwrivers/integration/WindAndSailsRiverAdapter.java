package com.piotrek.pwrivers.integration;

import com.piotrek.pwrivers.config.RiversConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Clean integration bridge between Peterwolf's Rivers and Peterwolf's Wind & Sails.
 * Uses WindManager for authentic wind data and extends sailing vessels to respond to
 * relative water velocity.
 */
public final class WindAndSailsRiverAdapter {
	private static final Logger LOGGER = LoggerFactory.getLogger("pw_rivers_wind_adapter");

	private static boolean checkWindAndSailsLoaded() {
		try {
			return FabricLoader.getInstance() != null && FabricLoader.getInstance().isModLoaded("pw_wind_n_sails");
		} catch (Throwable t) {
			return false;
		}
	}

	private static final boolean WIND_AND_SAILS_LOADED = checkWindAndSailsLoaded();

	static {
		if (WIND_AND_SAILS_LOADED) {
			LOGGER.info("Detected Peterwolf's Wind & Sails! Enabling hydrodynamic river-sailing coupling.");
		} else {
			LOGGER.info("Peterwolf's Wind & Sails not present. Using standalone atmospheric defaults.");
		}
	}

	private WindAndSailsRiverAdapter() {}

	public static boolean isWindAndSailsAvailable() {
		return WIND_AND_SAILS_LOADED && RiversConfig.compatibilityEnableWindAndSails;
	}

	/**
	 * Fetches true wind vector at position from Wind & Sails if loaded, or calm breeze if absent.
	 */
	public static Vec3 getWindVector(Level level, Vec3 pos) {
		if (!isWindAndSailsAvailable() || level == null) {
			return Vec3.ZERO;
		}

		try {
			com.piotrek.pwwindnsails.wind.WindVector wind = com.piotrek.pwwindnsails.wind.WindManager.getInstance().getWind(level, pos);
			if (wind != null) {
				return wind.toVec3();
			}
		} catch (Throwable t) {
			// Fail-safe in case of binary incompatibility
		}
		return Vec3.ZERO;
	}

	/**
	 * Computes wind-generated surface drift vector in the top layers of the river.
	 *
	 * @param relativeDepth 0.0 at riverbed, 1.0 at water surface
	 */
	public static Vec3 computeSurfaceWindDrift(Level level, Vec3 position, double relativeDepth) {
		if (!isWindAndSailsAvailable() || relativeDepth < 0.65) {
			return Vec3.ZERO;
		}

		Vec3 wind = getWindVector(level, position);
		double windSpeed = Math.sqrt(wind.x * wind.x + wind.z * wind.z);
		if (windSpeed < 0.02) {
			return Vec3.ZERO;
		}

		// Surface boundary layer: drift decays rapidly below the immediate surface
		double surfaceFactor = (relativeDepth - 0.65) / 0.35; // 0.0 to 1.0
		double driftSpeed = windSpeed * RiversConfig.windSurfaceInfluence * surfaceFactor;

		double wx = wind.x / windSpeed;
		double wz = wind.z / windSpeed;

		return new Vec3(wx * driftSpeed, 0.0, wz * driftSpeed);
	}

	/**
	 * Hydrodynamic relative velocity transformation for sailing vessels and boats:
	 * V_rel = V_boat - V_river
	 *
	 * All hydrodynamic forces (keel lateral resistance, hull drag, rudder steering authority)
	 * depend on V_rel rather than ground-referenced velocity.
	 */
	public static Vec3 calculateRelativeVelocity(Vec3 boatVelocity, Vec3 riverVelocity) {
		if (boatVelocity == null) boatVelocity = Vec3.ZERO;
		if (riverVelocity == null) riverVelocity = Vec3.ZERO;

		return new Vec3(
			boatVelocity.x - riverVelocity.x,
			boatVelocity.y - riverVelocity.y,
			boatVelocity.z - riverVelocity.z
		);
	}

	/**
	 * Recombines new relative velocity with water velocity to update world-space motion:
	 * V_boat_new = V_rel_new + V_river
	 */
	public static Vec3 reconstructWorldVelocity(Vec3 newRelativeVelocity, Vec3 riverVelocity) {
		if (newRelativeVelocity == null) newRelativeVelocity = Vec3.ZERO;
		if (riverVelocity == null) riverVelocity = Vec3.ZERO;

		return new Vec3(
			newRelativeVelocity.x + riverVelocity.x,
			newRelativeVelocity.y + riverVelocity.y,
			newRelativeVelocity.z + riverVelocity.z
		);
	}

	/**
	 * Calculates rudder steering torque modifier based on relative water velocity over the rudder blade.
	 * When floating downstream at current speed, V_rel ~ 0, so rudder authority is minimal unless
	 * sailing or motoring relative to the stream.
	 */
	public static double computeRudderAuthority(Vec3 relativeVelocity, double forwardHeadingX, double forwardHeadingZ) {
		double vForwardRel = relativeVelocity.x * forwardHeadingX + relativeVelocity.z * forwardHeadingZ;
		double waterSpeed = Math.abs(vForwardRel);
		// Scale authority smoothly: full authority at > 0.25 m/s relative flow
		return Mth.clamp(waterSpeed / 0.25, 0.05, 1.5);
	}

	/**
	 * Applies hydrodynamic river current coupling to a Wind & Sails vessel or vanilla boat.
	 * Ensures server-authoritative continuity without duplicate forces or acceleration spikes.
	 */
	public static Vec3 applyRiverCoupling(Entity boat, Vec3 currentDeltaMovement, Vec3 sampledRiverVelocity) {
		if (sampledRiverVelocity == null || (sampledRiverVelocity.x == 0.0 && sampledRiverVelocity.z == 0.0)) {
			return currentDeltaMovement;
		}

		// Smooth entrainment / relaxation towards local current vector
		// Prevents instantaneous step changes at chunk boundaries
		double entrainmentFactor = 0.08; // smooth hydraulic momentum transfer per tick
		double targetVx = currentDeltaMovement.x + (sampledRiverVelocity.x - currentDeltaMovement.x) * entrainmentFactor;
		double targetVz = currentDeltaMovement.z + (sampledRiverVelocity.z - currentDeltaMovement.z) * entrainmentFactor;

		return new Vec3(targetVx, currentDeltaMovement.y, targetVz);
	}
}
