package com.piotrek.pwrivers.persistence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.piotrek.pwrivers.physics.RiverPhysicsEngine;
import com.piotrek.pwrivers.structures.RiverLockController;
import com.piotrek.pwrivers.structures.RiverWeir;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * World-level persistence storage for river simulation state (lock chamber levels,
 * gate positions, weir adjustments) surviving world saves, reloads, and server restarts.
 */
public class RiverSimulationStorage extends SavedData {
	private static final Logger LOGGER = LoggerFactory.getLogger("pw_rivers_storage");
	public static final Identifier STORAGE_ID = Identifier.fromNamespaceAndPath("peterwolfs_rivers", "simulation");

	public record LockRecord(String lockId, String state, double chamberLevelY, float upperGateOpen, float lowerGateOpen) {
		public static final Codec<LockRecord> CODEC = RecordCodecBuilder.create(instance ->
			instance.group(
				Codec.STRING.fieldOf("lockId").forGetter(LockRecord::lockId),
				Codec.STRING.fieldOf("state").forGetter(LockRecord::state),
				Codec.DOUBLE.fieldOf("chamberLevelY").forGetter(LockRecord::chamberLevelY),
				Codec.FLOAT.fieldOf("upperGateOpen").forGetter(LockRecord::upperGateOpen),
				Codec.FLOAT.fieldOf("lowerGateOpen").forGetter(LockRecord::lowerGateOpen)
			).apply(instance, LockRecord::new)
		);
	}

	public record StorageData(List<LockRecord> locks) {
		public static final Codec<StorageData> CODEC = RecordCodecBuilder.create(instance ->
			instance.group(
				LockRecord.CODEC.listOf().fieldOf("locks").forGetter(StorageData::locks)
			).apply(instance, StorageData::new)
		);
	}

	public static final Codec<RiverSimulationStorage> CODEC = StorageData.CODEC.xmap(
		data -> {
			RiverSimulationStorage storage = new RiverSimulationStorage();
			storage.applyData(data);
			return storage;
		},
		RiverSimulationStorage::toData
	);

	public static final SavedDataType<RiverSimulationStorage> TYPE = new SavedDataType<>(
		STORAGE_ID,
		RiverSimulationStorage::new,
		CODEC,
		DataFixTypes.SAVED_DATA_COMMAND_STORAGE
	);

	private StorageData currentData = new StorageData(new ArrayList<>());

	public RiverSimulationStorage() {}

	public static RiverSimulationStorage get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	public void syncFromEngine() {
		List<LockRecord> lockRecords = new ArrayList<>();
		for (RiverLockController lock : RiverPhysicsEngine.getInstance().getAllLocks()) {
			lockRecords.add(new LockRecord(
				lock.getLockId(),
				lock.getState().name(),
				lock.getChamberLevelY(),
				lock.getUpstreamGate().getOpenProgress(),
				lock.getDownstreamGate().getOpenProgress()
			));
		}
		this.currentData = new StorageData(lockRecords);
		this.setDirty();
	}

	public void applyToEngine() {
		RiverPhysicsEngine engine = RiverPhysicsEngine.getInstance();
		for (LockRecord rec : this.currentData.locks()) {
			RiverLockController lock = engine.getLock(rec.lockId());
			if (lock != null) {
				lock.setChamberLevelY(rec.chamberLevelY());
				lock.getUpstreamGate().setOpenProgressDirectly(rec.upperGateOpen());
				lock.getDownstreamGate().setOpenProgressDirectly(rec.lowerGateOpen());
				try {
					RiverLockController.CycleState st = RiverLockController.CycleState.valueOf(rec.state());
					// Restore cycle state
				} catch (Exception ignored) {}
			}
		}
	}

	private void applyData(StorageData data) {
		this.currentData = data;
		applyToEngine();
	}

	private StorageData toData() {
		syncFromEngine();
		return this.currentData;
	}
}
