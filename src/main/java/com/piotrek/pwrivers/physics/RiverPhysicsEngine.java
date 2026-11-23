package com.piotrek.pwrivers.physics;

import com.piotrek.pwrivers.config.RiversConfig;
import com.piotrek.pwrivers.network.RiverNetworkGraph;
import com.piotrek.pwrivers.structures.RiverLockController;
import com.piotrek.pwrivers.structures.RiverWeir;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Primary hydrodynamics simulation engine for Peterwolf's Rivers.
 * Manages the macro river network, shallow-water flow fields, vortex solvers,
 * hydraulic structures, and dirty simulation cell caches.
 */
public final class RiverPhysicsEngine {
	private static final Logger LOGGER = LoggerFactory.getLogger("pw_rivers_physics");
	private static final RiverPhysicsEngine INSTANCE = new RiverPhysicsEngine();

	private final RiverNetworkGraph networkGraph = new RiverNetworkGraph("primary_world_network");
	private final RiverVortexSolver vortexSolver = new RiverVortexSolver();
	private final RiverFlowField flowField = new RiverFlowField(this.networkGraph, this.vortexSolver);

	private final Map<String, RiverLockController> activeLocks = new ConcurrentHashMap<>();
	private final Map<String, RiverWeir> activeWeirs = new ConcurrentHashMap<>();

	// Spatial caching & dirty region tracking
	private final Map<Long, RiverFlowSample> cachedCellSamples = new ConcurrentHashMap<>();
	private final Set<Long> dirtyChunks = ConcurrentHashMap.newKeySet();

	// Benchmark diagnostics
	private long lastTickTimeNanos = 0;
	private long totalSimulatedTicks = 0;
	private double averageTickMicroseconds = 0.0;

	private RiverPhysicsEngine() {}

	public static RiverPhysicsEngine getInstance() {
		return INSTANCE;
	}

	public RiverNetworkGraph getNetwork() {
		return this.networkGraph;
	}

	public RiverVortexSolver getVortexSolver() {
		return this.vortexSolver;
	}

	public RiverFlowField getFlowField() {
		return this.flowField;
	}

	public void registerLock(RiverLockController lock) {
		this.activeLocks.put(lock.getLockId(), lock);
	}

	public RiverLockController getLock(String lockId) {
		return this.activeLocks.get(lockId);
	}

	public Collection<RiverLockController> getAllLocks() {
		return Collections.unmodifiableCollection(this.activeLocks.values());
	}

	public void registerWeir(RiverWeir weir) {
		this.activeWeirs.put(weir.getWeirId(), weir);
	}

	public RiverWeir getWeir(String weirId) {
		return this.activeWeirs.get(weirId);
	}

	public Collection<RiverWeir> getAllWeirs() {
		return Collections.unmodifiableCollection(this.activeWeirs.values());
	}

	/**
	 * Mark a block position as modified, scheduling incremental local flow recalculation.
	 */
	public void markDirty(BlockPos pos) {
		long chunkKey = ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4);
		this.dirtyChunks.add(chunkKey);
	}

	public void markChunkDirty(ChunkPos chunkPos) {
		this.dirtyChunks.add(chunkPos.pack());
	}

	/**
	 * Primary query method: samples the river velocity field, surface height, depth,
	 * turbulence, and vorticity at an exact world position.
	 */
	public RiverFlowSample sampleFlow(Level level, Vec3 position) {
		if (!RiversConfig.simulationEnabled || position == null) {
			return RiverFlowSample.ZERO;
		}

		// 1. Check if position is inside an active lock chamber
		for (RiverLockController lock : this.activeLocks.values()) {
			if (lock.isInsideChamber(position)) {
				double surfaceY = lock.getChamberLevelY();
				double depth = Math.max(0.5, surfaceY - lock.getChamberBounds().minY);

				// Water current inside chamber is calm unless sluices are actively filling/draining
				Vec3 vel = Vec3.ZERO;
				float turb = 0.05F;
				if (lock.getState() == RiverLockController.CycleState.FILLING) {
					turb = 0.45F;
					vel = new Vec3(0, 0.05, 0); // Upwelling boil during filling
				} else if (lock.getState() == RiverLockController.CycleState.DRAINING) {
					turb = 0.40F;
					vel = new Vec3(0, -0.05, 0);
				}

				return new RiverFlowSample(
					vel,
					0.0,
					0.0F,
					depth,
					surfaceY,
					turb,
					0.0F,
					RiverHydraulics.localPressure(Math.max(0, surfaceY - position.y), 0.0),
					"lock_" + lock.getLockId()
				);
			}
		}

		// 2. Sample standard shallow-water flow field
		RiverFlowSample baseSample = this.flowField.sampleFlow(level, position);

		// 3. Modulate flow if near an active weir or spillway
		for (RiverWeir weir : this.activeWeirs.values()) {
			if (weir.isDownstreamPlungeZone(position)) {
				Vec3 weirMod = weir.computeDownstreamFlowModifier(position, baseSample.waterSurfaceHeight());
				Vec3 modVel = baseSample.velocity().add(weirMod);
				double newSpeed = Math.sqrt(modVel.x * modVel.x + modVel.z * modVel.z);
				float newTurb = Math.max(baseSample.turbulenceIntensity(), 0.75F);

				return new RiverFlowSample(
					modVel,
					newSpeed,
					baseSample.flowDirectionDeg(),
					baseSample.localDepth(),
					baseSample.waterSurfaceHeight(),
					newTurb,
					baseSample.vorticity() + 0.4F,
					baseSample.localPressure(),
					baseSample.reachId()
				);
			}
		}

		return baseSample;
	}

	/**
	 * Server simulation tick: updates locks, weirs, and performs incremental recalculation.
	 */
	public void tickServer(ServerLevel level) {
		long startNanos = System.nanoTime();

		// Tick active lock controllers
		for (RiverLockController lock : this.activeLocks.values()) {
			lock.tick(level);
		}

		// Incremental dirty cell clearance
		if (!this.dirtyChunks.isEmpty()) {
			this.cachedCellSamples.clear(); // Clear cached active cells in dirty regions
			this.dirtyChunks.clear();
		}

		long elapsedNanos = System.nanoTime() - startNanos;
		this.lastTickTimeNanos = elapsedNanos;
		this.totalSimulatedTicks++;

		double currentMicros = elapsedNanos / 1000.0;
		if (this.totalSimulatedTicks == 1) {
			this.averageTickMicroseconds = currentMicros;
		} else {
			this.averageTickMicroseconds = 0.95 * this.averageTickMicroseconds + 0.05 * currentMicros;
		}
	}

	public long getLastTickTimeNanos() {
		return this.lastTickTimeNanos;
	}

	public double getAverageTickMicroseconds() {
		return this.averageTickMicroseconds;
	}

	public long getTotalSimulatedTicks() {
		return this.totalSimulatedTicks;
	}

	public int getActiveCellCount() {
		return this.cachedCellSamples.size();
	}
}
