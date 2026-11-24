package com.piotrek.pwrivers.vessel;

import com.piotrek.pwrivers.PeterwolfsRiversMod;
import com.piotrek.pwrivers.fluid.RiverFluidRegistry;
import com.piotrek.pwrivers.integration.WindAndSailsRiverAdapter;
import com.piotrek.pwrivers.physics.MooringConstraint;
import com.piotrek.pwrivers.physics.RiverFlowSample;
import com.piotrek.pwrivers.physics.RiverPhysicsEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wressla - Historic Wrocław Tram River Catamaran Entity.
 * Features:
 *  - 10m x 3.5m twin-pontoon catamaran hydrodynamics
 *  - Walkable teak deck: players can freely walk anywhere on deck while vessel moves
 *  - Interactive steering wheel: click to claim helm, Shift to release
 *  - Outboard engine mechanics based on OceanCruiser:
 *      * Direct responsive throttle (W forward, S reverse, holds cruising setting)
 *      * Outboard motor yaw deflection when steering (vector thrust!)
 *      * Propeller bubble particles & authentic combustion audio
 *  - 4 Mooring Cleats on the corners of the deck with 3D ropes matching Wind & Sails
 *  - Realistic wind & river current natural drift with hydrodynamic rudder authority
 */
public class WresslaEntity extends Entity implements Leashable {
	public static final int CLEAT_COUNT = 4;
	public static final int CLEAT_BOW_PORT = 0;
	public static final int CLEAT_BOW_STARBOARD = 1;
	public static final int CLEAT_STERN_PORT = 2;
	public static final int CLEAT_STERN_STARBOARD = 3;

	public static final Vec3[] CLEAT_OFFSETS = new Vec3[]{
		WresslaLayout.CLEAT_BOW_PORT,
		WresslaLayout.CLEAT_BOW_STARBOARD,
		WresslaLayout.CLEAT_STERN_PORT,
		WresslaLayout.CLEAT_STERN_STARBOARD
	};

	private static final EntityDataAccessor<Integer> HELMSMAN_ID = SynchedEntityData.defineId(WresslaEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> ENGINE_ACTIVE = SynchedEntityData.defineId(WresslaEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Float> ENGINE_THROTTLE = SynchedEntityData.defineId(WresslaEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> RUDDER_ANGLE = SynchedEntityData.defineId(WresslaEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> OUTBOARD_MOTOR_YAW = SynchedEntityData.defineId(WresslaEntity.class, EntityDataSerializers.FLOAT);

	private static final EntityDataAccessor<Integer> CLEAT_0_HOLDER = SynchedEntityData.defineId(WresslaEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> CLEAT_1_HOLDER = SynchedEntityData.defineId(WresslaEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> CLEAT_2_HOLDER = SynchedEntityData.defineId(WresslaEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> CLEAT_3_HOLDER = SynchedEntityData.defineId(WresslaEntity.class, EntityDataSerializers.INT);

	@SuppressWarnings("unchecked")
	public static final EntityDataAccessor<Integer>[] CLEAT_HOLDERS = new EntityDataAccessor[]{
		CLEAT_0_HOLDER, CLEAT_1_HOLDER, CLEAT_2_HOLDER, CLEAT_3_HOLDER
	};

	private static final EntityDataAccessor<Optional<BlockPos>> CLEAT_0_POS = SynchedEntityData.defineId(WresslaEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
	private static final EntityDataAccessor<Optional<BlockPos>> CLEAT_1_POS = SynchedEntityData.defineId(WresslaEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
	private static final EntityDataAccessor<Optional<BlockPos>> CLEAT_2_POS = SynchedEntityData.defineId(WresslaEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
	private static final EntityDataAccessor<Optional<BlockPos>> CLEAT_3_POS = SynchedEntityData.defineId(WresslaEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);

	@SuppressWarnings("unchecked")
	public static final EntityDataAccessor<Optional<BlockPos>>[] CLEAT_POS = new EntityDataAccessor[]{
		CLEAT_0_POS, CLEAT_1_POS, CLEAT_2_POS, CLEAT_3_POS
	};

	private final UUID[] cleatHolderUuids = new UUID[CLEAT_COUNT];
	private final BlockPos[] cleatHolderPositions = new BlockPos[CLEAT_COUNT];
	private final Entity[] cleatHolderEntities = new Entity[CLEAT_COUNT];
	private final long[] cleatLastInteractTimes = new long[CLEAT_COUNT];

	@Nullable
	private Leashable.LeashData leashData;
	@Nullable
	private UUID helmsmanUuid;
	public float clientRudderInput;
	public float clientThrottleInput;
	private int inputFreshTicks;
	private long lastClaimGameTime = -1L;

	private int engineSoundCooldown;
	private float propellerAngle;

	public WresslaEntity(EntityType<?> type, Level level) {
		super(type, level);
		this.blocksBuilding = true;
	}

	@Override
	@Nullable
	public Leashable.LeashData getLeashData() {
		return this.leashData;
	}

	@Override
	public void setLeashData(@Nullable Leashable.LeashData leashData) {
		this.leashData = leashData;
	}

	public static void notifyPlayer(Player player, Component message) {
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(message, true);
		} else {
			player.sendSystemMessage(message);
		}
	}

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return LinearInterpolationHandler.create(this, 3);
	}

	@Override
	protected boolean isLocalClientAuthoritative() {
		return false;
	}

	@Override
	public boolean isClientAuthoritative() {
		return false;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(HELMSMAN_ID, -1);
		builder.define(ENGINE_ACTIVE, true);
		builder.define(ENGINE_THROTTLE, 0.0F);
		builder.define(RUDDER_ANGLE, 0.0F);
		builder.define(OUTBOARD_MOTOR_YAW, 0.0F);

		for (int i = 0; i < CLEAT_COUNT; i++) {
			builder.define(CLEAT_HOLDERS[i], -1);
			builder.define(CLEAT_POS[i], Optional.empty());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.setEngineActive(input.getBooleanOr("EngineActive", true));
		this.setEngineThrottle(input.getFloatOr("EngineThrottle", 0.0F));
		this.setRudderAngle(input.getFloatOr("RudderAngle", 0.0F));
		this.setOutboardMotorYaw(input.getFloatOr("OutboardMotorYaw", 0.0F));

		for (int i = 0; i < CLEAT_COUNT; i++) {
			Optional<UUID> holderUuid = input.read("Cleat" + i + "Holder", net.minecraft.core.UUIDUtil.CODEC);
			this.cleatHolderUuids[i] = holderUuid.orElse(null);
			Optional<BlockPos> fencePos = input.read("Cleat" + i + "FencePos", BlockPos.CODEC);
			this.cleatHolderPositions[i] = fencePos.orElse(null);
			this.entityData.set(CLEAT_POS[i], fencePos);
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putBoolean("EngineActive", this.isEngineActive());
		output.putFloat("EngineThrottle", this.getEngineThrottle());
		output.putFloat("RudderAngle", this.getRudderAngle());
		output.putFloat("OutboardMotorYaw", this.getOutboardMotorYaw());

		for (int i = 0; i < CLEAT_COUNT; i++) {
			if (this.cleatHolderUuids[i] != null) {
				output.store("Cleat" + i + "Holder", net.minecraft.core.UUIDUtil.CODEC, this.cleatHolderUuids[i]);
			}
			if (this.cleatHolderPositions[i] != null) {
				output.store("Cleat" + i + "FencePos", BlockPos.CODEC, this.cleatHolderPositions[i]);
			}
		}
	}

	// =========================================================================
	// TARGETING, COLLISION & DAMAGE
	// =========================================================================
	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		if (other != null && this.hasPassenger(other)) {
			return false;
		}
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
		if (this.isInvulnerableToBase(damageSource)) {
			return false;
		}
		if (damageSource.getEntity() instanceof Player player) {
			if (player.getAbilities().instabuild) {
				this.discard();
				return true;
			}
			this.spawnAtLocation(level, PeterwolfsRiversMod.WRESSLA_ITEM);
			this.discard();
			return true;
		}
		return false;
	}

	// =========================================================================
	// HELMSMAN & PASSENGER HANDLING
	// =========================================================================
	public boolean isHelmsman(@Nullable Entity entity) {
		return entity != null && entity.getId() == this.entityData.get(HELMSMAN_ID);
	}

	@Nullable
	public Entity getHelmsman() {
		int id = this.entityData.get(HELMSMAN_ID);
		if (id < 0) return null;
		return this.level().getEntity(id);
	}

	public void setHelmsman(@Nullable LivingEntity captain) {
		if (captain == null) {
			this.helmsmanUuid = null;
			this.entityData.set(HELMSMAN_ID, -1);
			this.clientRudderInput = 0.0F;
			this.clientThrottleInput = 0.0F;
			this.inputFreshTicks = 0;
		} else {
			this.helmsmanUuid = captain.getUUID();
			this.entityData.set(HELMSMAN_ID, captain.getId());
		}
	}

	public long getLastClaimGameTime() {
		return this.lastClaimGameTime;
	}

	public boolean tryClaimHelm(LivingEntity captain) {
		if (captain == null || !captain.isAlive()) return false;
		Entity current = this.getHelmsman();
		if (current != null && current != captain && current.isAlive() && this.hasPassenger(current)) return false;
		if (captain.getVehicle() != null && captain.getVehicle() != this) return false;
		if (captain.getVehicle() != this) {
			if (!captain.startRiding(this)) return false;
		}
		this.lastClaimGameTime = this.level().getGameTime();
		this.setHelmsman(captain);
		captain.setYRot(this.getYRot());
		captain.setYHeadRot(this.getYRot());
		return true;
	}

	public boolean releaseHelm(LivingEntity captain) {
		if (!this.isHelmsman(captain) && captain.getVehicle() != this) return false;
		// Grace period: ignore dismount within 10 ticks (0.5s) of claiming to prevent immediate eject
		if (this.level().getGameTime() - this.lastClaimGameTime < 10L) {
			return false;
		}
		this.setHelmsman(null);
		if (captain.getVehicle() == this) {
			captain.stopRiding();
		}
		return true;
	}

	@Override
	@Nullable
	public LivingEntity getControllingPassenger() {
		Entity helmsman = this.getHelmsman();
		if (helmsman instanceof LivingEntity living && this.hasPassenger(living)) {
			return living;
		}
		List<Entity> pass = this.getPassengers();
		if (!pass.isEmpty() && pass.getFirst() instanceof LivingEntity living) {
			return living;
		}
		return null;
	}

	public void setControlInput(float rudder, float throttle) {
		this.clientRudderInput = Mth.clamp(rudder, -1.0F, 1.0F);
		this.clientThrottleInput = Mth.clamp(throttle, -1.0F, 1.0F);
		this.inputFreshTicks = 8;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return passenger instanceof LivingEntity && (this.getPassengers().isEmpty() || this.hasPassenger(passenger));
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		if (this.helmsmanUuid != null && this.helmsmanUuid.equals(passenger.getUUID())) {
			this.setHelmsman(null);
		}
	}

	@Override
	public Vec3 getPassengerRidingPosition(Entity passenger) {
		return this.localToWorld(WresslaLayout.HELMSMAN_POS);
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		return this.getPassengerRidingPosition(passenger).subtract(this.position());
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		return this.localToWorld(new Vec3(0.60, WresslaLayout.DECK_Y, -0.15));
	}

	// =========================================================================
	// STEERING WHEEL CLICK INTERACTION
	// =========================================================================
	public boolean isAimingAtWheel(Player player, float partialTick) {
		Vec3 eye = player.getEyePosition(partialTick);
		Vec3 view = player.getViewVector(partialTick).scale(5.5);
		Vec3 end = eye.add(view);

		Vec3 localEye = this.worldToLocal(eye);
		Vec3 localEnd = this.worldToLocal(end);

		Optional<Vec3> hit = WresslaLayout.WHEEL_BOX.inflate(0.20).clip(localEye, localEnd);
		return hit.isPresent();
	}

	public static boolean handleAimedControl(Player player) {
		// If player is already riding a Wressla at the helm:
		if (player.getVehicle() instanceof WresslaEntity wressla) {
			// NEVER dismount on right-click!
			// If player is aiming at the wheel console, allow toggling engine (ignition)
			// with debounce so boarding click doesn't immediately toggle engine.
			if (wressla.isAimingAtWheel(player, 1.0F)) {
				long now = player.level().getGameTime();
				if (now - wressla.lastClaimGameTime > 10L) {
					if (!player.level().isClientSide()) {
						wressla.toggleEngine(player);
					}
					return true;
				}
			}
			return false;
		}

		// Player is not riding; search nearby Wressla to claim helm:
		AABB search = player.getBoundingBox().inflate(6.0);
		for (WresslaEntity wressla : player.level().getEntitiesOfClass(WresslaEntity.class, search)) {
			if (wressla.isAimingAtWheel(player, 1.0F)) {
				if (!player.level().isClientSide()) {
					if (!wressla.tryClaimHelm(player)) {
						notifyPlayer(player, Component.translatable("message.peterwolfs_rivers.helm_occupied"));
					} else {
						notifyPlayer(player, Component.translatable("message.peterwolfs_rivers.helm_claimed"));
					}
				}
				return true;
			}
		}
		return false;
	}

	// =========================================================================
	// 4 CORNER MOORING CLEATS
	// =========================================================================
	public Vec3 getCleatLocalOffset(int cleat) {
		if (cleat >= 0 && cleat < CLEAT_COUNT) {
			return CLEAT_OFFSETS[cleat];
		}
		return Vec3.ZERO;
	}

	public Vec3 getCleatWorldPos(int cleat, Vec3 boatPos, float yaw) {
		Vec3 local = getCleatLocalOffset(cleat);
		float yawRad = yaw * Mth.DEG_TO_RAD;
		double fwdX = -Mth.sin(yawRad);
		double fwdZ = Mth.cos(yawRad);
		double rightX = Mth.cos(yawRad);
		double rightZ = Mth.sin(yawRad);
		double wx = boatPos.x + rightX * local.x + fwdX * local.z;
		double wy = boatPos.y + local.y;
		double wz = boatPos.z + rightZ * local.x + fwdZ * local.z;
		return new Vec3(wx, wy, wz);
	}

	public Vec3 getCleatWorldPos(int cleat) {
		return getCleatWorldPos(cleat, this.position(), this.getYRot());
	}

	public boolean isCleatAttached(int cleat) {
		if (cleat < 0 || cleat >= CLEAT_COUNT) return false;
		return this.entityData.get(CLEAT_HOLDERS[cleat]) != -1 || this.entityData.get(CLEAT_POS[cleat]).isPresent();
	}

	public boolean isCleatAttachedTo(int cleat, Entity entity) {
		if (cleat < 0 || cleat >= CLEAT_COUNT || entity == null) return false;
		return this.entityData.get(CLEAT_HOLDERS[cleat]) == entity.getId();
	}

	public boolean hasAnyCleatAttached() {
		for (int i = 0; i < CLEAT_COUNT; i++) {
			if (this.isCleatAttached(i)) return true;
		}
		return false;
	}

	public void attachCleatToEntity(int cleat, Entity holder) {
		if (cleat < 0 || cleat >= CLEAT_COUNT || holder == null) return;
		for (int i = 0; i < CLEAT_COUNT; i++) {
			if (i != cleat && this.isCleatAttachedTo(i, holder)) {
				this.detachCleat(i);
			}
		}
		this.cleatHolderUuids[cleat] = holder.getUUID();
		this.cleatHolderEntities[cleat] = holder;
		this.entityData.set(CLEAT_HOLDERS[cleat], holder.getId());
		this.cleatHolderPositions[cleat] = null;
		this.entityData.set(CLEAT_POS[cleat], Optional.empty());
	}

	public void attachCleatToPos(int cleat, BlockPos pos) {
		if (cleat < 0 || cleat >= CLEAT_COUNT || pos == null) return;
		BlockPos fixed = pos.immutable();
		this.cleatHolderPositions[cleat] = fixed;
		this.entityData.set(CLEAT_POS[cleat], Optional.of(fixed));
		this.cleatHolderUuids[cleat] = null;
		this.cleatHolderEntities[cleat] = null;
		this.entityData.set(CLEAT_HOLDERS[cleat], -1);

		if (this.level() instanceof ServerLevel serverLevel) {
			LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(serverLevel, fixed);
			knot.playPlacementSound();
		}
	}

	public void detachCleat(int cleat) {
		if (cleat < 0 || cleat >= CLEAT_COUNT) return;
		BlockPos oldPos = this.cleatHolderPositions[cleat];
		this.cleatHolderUuids[cleat] = null;
		this.cleatHolderEntities[cleat] = null;
		this.cleatHolderPositions[cleat] = null;
		this.entityData.set(CLEAT_HOLDERS[cleat], -1);
		this.entityData.set(CLEAT_POS[cleat], Optional.empty());

		if (oldPos != null && this.level() instanceof ServerLevel serverLevel) {
			Optional<LeashFenceKnotEntity> knot = LeashFenceKnotEntity.getKnot(serverLevel, oldPos);
			if (knot.isPresent()) {
				boolean otherTied = false;
				AABB area = new AABB(oldPos).inflate(16.0);
				for (WresslaEntity other : serverLevel.getEntitiesOfClass(WresslaEntity.class, area)) {
					for (int i = 0; i < CLEAT_COUNT; i++) {
						if (other.getCleatFencePos(i).filter(oldPos::equals).isPresent()) {
							otherTied = true;
							break;
						}
					}
					if (otherTied) break;
				}
				if (!otherTied) {
					knot.get().discard();
				}
			}
		}
	}

	@Nullable
	public Entity getCleatHolderEntity(int cleat) {
		if (cleat < 0 || cleat >= CLEAT_COUNT) return null;
		int id = this.entityData.get(CLEAT_HOLDERS[cleat]);
		if (id != -1) {
			Entity e = this.level().getEntity(id);
			if (e != null) {
				this.cleatHolderEntities[cleat] = e;
				return e;
			}
		}
		if (this.cleatHolderUuids[cleat] != null && this.level() instanceof ServerLevel slevel) {
			Entity e = slevel.getEntity(this.cleatHolderUuids[cleat]);
			if (e != null) {
				this.cleatHolderEntities[cleat] = e;
				this.entityData.set(CLEAT_HOLDERS[cleat], e.getId());
				return e;
			}
		}
		return this.cleatHolderEntities[cleat];
	}

	public Optional<BlockPos> getCleatFencePos(int cleat) {
		if (cleat < 0 || cleat >= CLEAT_COUNT) return Optional.empty();
		return this.entityData.get(CLEAT_POS[cleat]);
	}

	@Nullable
	public Vec3 getCleatAnchorPos(int cleat) {
		Optional<BlockPos> pos = this.getCleatFencePos(cleat);
		if (pos.isPresent()) {
			BlockPos p = pos.get();
			return new Vec3(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
		}
		Entity holder = this.getCleatHolderEntity(cleat);
		if (holder != null && holder.isAlive()) {
			return holder.position();
		}
		return null;
	}

	public static Component getCleatDisplayName(int cleat) {
		return switch (cleat) {
			case CLEAT_BOW_PORT -> Component.translatable("cleat.peterwolfs_rivers.bow_port");
			case CLEAT_BOW_STARBOARD -> Component.translatable("cleat.peterwolfs_rivers.bow_starboard");
			case CLEAT_STERN_PORT -> Component.translatable("cleat.peterwolfs_rivers.stern_port");
			case CLEAT_STERN_STARBOARD -> Component.translatable("cleat.peterwolfs_rivers.stern_starboard");
			default -> Component.literal("Cleat #" + cleat);
		};
	}

	public float applyMooringConstraintsYaw(float yaw) {
		float sumError = 0.0F;
		float totalWeight = 0.0F;

		for (int i = 0; i < CLEAT_COUNT; i++) {
			if (!this.isCleatAttached(i)) continue;
			Vec3 anchorPos = this.getCleatAnchorPos(i);
			if (anchorPos == null) {
				this.detachCleat(i);
				continue;
			}

			Vec3 cleatWorld = this.getCleatWorldPos(i, this.position(), yaw);
			double maxLine = 5.5; // Dock line max extension

			Vec3 diff = anchorPos.subtract(cleatWorld);
			double dist = diff.length();
			Vec3 local = this.getCleatLocalOffset(i);
			float w = MooringConstraint.lineYawWeight(dist, maxLine);
			float error = MooringConstraint.cleatYawError(
				yaw, this.getX(), this.getZ(), local.x, local.z, anchorPos.x, anchorPos.z
			);
			sumError += error * w;
			totalWeight += w;
		}

		if (totalWeight > 1.0E-4F) {
			float avgError = sumError / totalWeight;
			float maxStep = 2.5F;
			float appliedStep = Mth.clamp(avgError * 0.35F, -maxStep, maxStep);
			return Mth.wrapDegrees(yaw + appliedStep);
		}

		return yaw;
	}

	public Vec3 applyMooringConstraintsVelocity(Vec3 vel, float yaw, float windStrength) {
		Vec3 adjusted = vel;

		for (int i = 0; i < CLEAT_COUNT; i++) {
			if (!this.isCleatAttached(i)) continue;
			Vec3 anchorPos = this.getCleatAnchorPos(i);
			if (anchorPos == null) {
				this.detachCleat(i);
				continue;
			}

			Vec3 cleatWorld = this.getCleatWorldPos(i, this.position(), yaw);
			double maxLine = 5.5; // Dock line max extension

			Vec3 diff = anchorPos.subtract(cleatWorld);
			double dist = diff.length();

			if (dist > maxLine) {
				Vec3 norm = diff.normalize();
				double tension = Math.min(1.0, (dist - maxLine) / 2.0);
				double pullStrength = tension * 0.14;
				adjusted = adjusted.add(norm.x * pullStrength, 0.0, norm.z * pullStrength);
				adjusted = MooringConstraint.cancelOutwardVelocity(adjusted, -norm.x, -norm.z);
			}
		}

		return adjusted;
	}

	public Vec3 applyMooringConstraints(Vec3 vel, float yaw, float windStrength) {
		return this.applyMooringConstraintsVelocity(vel, yaw, windStrength);
	}

	private static final Map<UUID, BlockPos> PENDING_FENCE_MOORING = new ConcurrentHashMap<>();

	public static void setPendingFenceMooring(UUID playerUuid, BlockPos fence) {
		if (playerUuid != null && fence != null) {
			PENDING_FENCE_MOORING.put(playerUuid, fence.immutable());
		}
	}

	public static BlockPos getPendingFenceMooring(UUID playerUuid) {
		return playerUuid != null ? PENDING_FENCE_MOORING.get(playerUuid) : null;
	}

	public static void clearPendingFenceMooring(UUID playerUuid) {
		if (playerUuid != null) {
			PENDING_FENCE_MOORING.remove(playerUuid);
		}
	}

	public static boolean isPlayerHoldingAnyCleat(Player player) {
		if (player == null) return false;
		AABB area = player.getBoundingBox().inflate(WresslaMooringAim.REACH + 4.0);
		for (WresslaEntity boat : player.level().getEntitiesOfClass(WresslaEntity.class, area)) {
			for (int i = 0; i < CLEAT_COUNT; i++) {
				if (boat.isCleatAttachedTo(i, player)) {
					return true;
				}
			}
		}
		return false;
	}

	public static boolean isAimingAtTiedMooring(Player player, Level level) {
		WresslaMooringAim.Target aim = WresslaMooringAim.find(player, level, 1.0F);
		if (aim == null) return false;
		if (aim.cleat() && level.getEntity(aim.vesselId()) instanceof WresslaEntity boat) {
			return boat.isCleatAttached(aim.cleatIndex());
		}
		if (!aim.cleat() && aim.fence() != null) {
			AABB area = player.getBoundingBox().inflate(WresslaMooringAim.REACH + 4.0);
			for (WresslaEntity boat : level.getEntitiesOfClass(WresslaEntity.class, area)) {
				for (int i = 0; i < CLEAT_COUNT; i++) {
					if (boat.getCleatFencePos(i).filter(aim.fence()::equals).isPresent()) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public static boolean shouldOwnMooringClick(Player player, WresslaMooringAim.Target aim) {
		if (player == null || aim == null) return false;
		boolean holdsLead = player.getMainHandItem().is(Items.LEAD) || player.getOffhandItem().is(Items.LEAD);
		if (holdsLead || isPlayerHoldingAnyCleat(player) || getPendingFenceMooring(player.getUUID()) != null) {
			return true;
		}
		if (aim.cleat() && player.level().getEntity(aim.vesselId()) instanceof WresslaEntity boat) {
			return boat.isCleatAttached(aim.cleatIndex());
		}
		if (!aim.cleat() && aim.fence() != null) {
			AABB area = player.getBoundingBox().inflate(WresslaMooringAim.REACH + 4.0);
			for (WresslaEntity boat : player.level().getEntitiesOfClass(WresslaEntity.class, area)) {
				for (int i = 0; i < CLEAT_COUNT; i++) {
					if (boat.getCleatFencePos(i).filter(aim.fence()::equals).isPresent()) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public static void handleMooringClick(Player player, boolean isCleat, int vesselId, int cleatIndex, BlockPos fence) {
		if (player.level().isClientSide()) return;
		if (isCleat) {
			if (player.level().getEntity(vesselId) instanceof WresslaEntity wressla) {
				interactCleat(player, wressla, cleatIndex);
			}
		} else if (fence != null) {
			interactFence(player, fence);
		}
	}

	public static boolean interactCleat(Player player, WresslaEntity wressla, int cleat) {
		if (cleat < 0 || cleat >= CLEAT_COUNT || wressla == null) return false;
		Level level = player.level();
		long now = level.getGameTime();
		if (now - wressla.cleatLastInteractTimes[cleat] < 6L) {
			return true; // Debounce rapid multi-trigger interactions
		}

		boolean holdsLead = player.getMainHandItem().is(Items.LEAD) || player.getOffhandItem().is(Items.LEAD);
		BlockPos pending = getPendingFenceMooring(player.getUUID());

		// 1. Pending fence post mooring: tie cleat to selected fence
		if (pending != null && WresslaMooringAim.isMooringPostBlock(level, pending)) {
			if (!level.isClientSide()) {
				wressla.cleatLastInteractTimes[cleat] = now;
				clearPendingFenceMooring(player.getUUID());
				wressla.attachCleatToPos(cleat, pending);
				if (!player.getAbilities().instabuild) {
					ItemStack lead = player.getMainHandItem().is(Items.LEAD) ? player.getMainHandItem() : player.getOffhandItem();
					if (!lead.isEmpty()) lead.shrink(1);
				}
				notifyPlayer(player, Component.translatable("message.peterwolfs_rivers.mooring_cleat_to_post", getCleatDisplayName(cleat)));
				level.playSound(null, pending.getX() + 0.5, pending.getY() + 0.5, pending.getZ() + 0.5,
					SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0F, 1.0F);
			}
			return true;
		}

		// 2. Cleat is already tied
		if (wressla.isCleatAttached(cleat)) {
			if (holdsLead) {
				// Player clicked with a lead on an already-tied cleat: inform them
				if (!level.isClientSide()) {
					wressla.cleatLastInteractTimes[cleat] = now;
					notifyPlayer(player, Component.translatable("message.peterwolfs_rivers.mooring_cleat_already_tied", getCleatDisplayName(cleat)));
				}
				return true;
			}
			// Untie with empty hand or other item
			if (!level.isClientSide()) {
				wressla.cleatLastInteractTimes[cleat] = now;
				wressla.detachCleat(cleat);
				if (!player.getAbilities().instabuild) {
					player.getInventory().add(new ItemStack(Items.LEAD));
				}
				notifyPlayer(player, Component.translatable("message.peterwolfs_rivers.mooring_untied_cleat", getCleatDisplayName(cleat)));
				level.playSound(null, wressla.getX(), wressla.getY(), wressla.getZ(),
					SoundEvents.LEAD_UNTIED, SoundSource.PLAYERS, 1.0F, 1.0F);
			}
			return true;
		}

		// 3. Attach line to open cleat if player holds lead
		if (holdsLead) {
			if (!level.isClientSide()) {
				wressla.cleatLastInteractTimes[cleat] = now;
				wressla.attachCleatToEntity(cleat, player);
				if (!player.getAbilities().instabuild) {
					ItemStack lead = player.getMainHandItem().is(Items.LEAD) ? player.getMainHandItem() : player.getOffhandItem();
					lead.shrink(1);
				}
				notifyPlayer(player, Component.translatable("message.peterwolfs_rivers.mooring_cleat_selected", getCleatDisplayName(cleat)));
				level.playSound(null, wressla.getX(), wressla.getY(), wressla.getZ(),
					SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0F, 1.0F);
			}
			return true;
		}

		return false;
	}

	public static boolean interactFence(Player player, BlockPos fence) {
		if (fence == null) return false;
		Level level = player.level();
		BlockPos fixed = fence.immutable();
		AABB search = player.getBoundingBox().inflate(WresslaMooringAim.REACH + 4.0);
		List<WresslaEntity> boats = level.getEntitiesOfClass(WresslaEntity.class, search);

		// 1. Untie if this fence already has a cleat tied to it
		for (WresslaEntity boat : boats) {
			for (int i = 0; i < CLEAT_COUNT; i++) {
				if (boat.getCleatFencePos(i).filter(fixed::equals).isPresent()) {
					if (!level.isClientSide()) {
						boat.detachCleat(i);
						if (!player.getAbilities().instabuild) {
							player.getInventory().add(new ItemStack(Items.LEAD));
						}
						notifyPlayer(player, Component.translatable("message.peterwolfs_rivers.mooring_untied_post", getCleatDisplayName(i)));
						level.playSound(null, fixed.getX() + 0.5, fixed.getY() + 0.5, fixed.getZ() + 0.5,
							SoundEvents.LEAD_UNTIED, SoundSource.PLAYERS, 1.0F, 1.0F);
					}
					return true;
				}
			}
		}

		// 2. Tie line held by player from a cleat to this fence post
		for (WresslaEntity boat : boats) {
			for (int i = 0; i < CLEAT_COUNT; i++) {
				if (boat.isCleatAttachedTo(i, player)) {
					if (!level.isClientSide()) {
						boat.attachCleatToPos(i, fixed);
						notifyPlayer(player, Component.translatable("message.peterwolfs_rivers.mooring_cleat_to_post", getCleatDisplayName(i)));
						level.playSound(null, fixed.getX() + 0.5, fixed.getY() + 0.5, fixed.getZ() + 0.5,
							SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0F, 1.0F);
					}
					return true;
				}
			}
		}

		// 3. Select fence as pending mooring point if player holds lead
		boolean holdsLead = player.getMainHandItem().is(Items.LEAD) || player.getOffhandItem().is(Items.LEAD);
		if (holdsLead) {
			if (!level.isClientSide()) {
				setPendingFenceMooring(player.getUUID(), fixed);
				if (level instanceof ServerLevel serverLevel) {
					LeashFenceKnotEntity.getOrCreateKnot(serverLevel, fixed).playPlacementSound();
				}
				notifyPlayer(player, Component.translatable("message.peterwolfs_rivers.mooring_post_selected"));
			}
			return true;
		}

		return false;
	}

	public static boolean handleAimedMooringClick(Player player) {
		WresslaMooringAim.Target aim = WresslaMooringAim.find(player, player.level(), 1.0F);
		if (aim == null) return false;

		if (aim.cleat()) {
			if (player.level().getEntity(aim.vesselId()) instanceof WresslaEntity wressla) {
				return interactCleat(player, wressla, aim.cleatIndex());
			}
		} else if (aim.fence() != null) {
			return interactFence(player, aim.fence());
		}
		return false;
	}

	public static boolean handleEntityMooringClick(Player player, Entity entity) {
		AABB search = player.getBoundingBox().inflate(WresslaMooringAim.REACH + 2.0);
		for (WresslaEntity wressla : player.level().getEntitiesOfClass(WresslaEntity.class, search)) {
			for (int i = 0; i < CLEAT_COUNT; i++) {
				if (wressla.isCleatAttachedTo(i, player)) {
					if (!player.level().isClientSide()) {
						wressla.attachCleatToEntity(i, entity);
						notifyPlayer(player, Component.translatable("message.peterwolfs_rivers.mooring_entity_to_cleat", getCleatDisplayName(i)));
						player.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
							SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0F, 1.0F);
					}
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}

		if (handleAimedControl(player)) {
			return InteractionResult.SUCCESS;
		}

		// 1. Aimed mooring via raycast
		if (handleAimedMooringClick(player)) {
			return InteractionResult.SUCCESS;
		}

		// 2. Direct cleat proximity fallback on this vessel
		boolean holdsLead = player.getMainHandItem().is(Items.LEAD) || player.getOffhandItem().is(Items.LEAD);
		boolean holdsLine = isPlayerHoldingAnyCleat(player);
		BlockPos pendingFence = getPendingFenceMooring(player.getUUID());

		Vec3 clickWorld = location != null ? this.position().add(location) : player.getEyePosition();
		int bestCleat = -1;
		double bestDistSq = Double.MAX_VALUE;

		for (int i = 0; i < CLEAT_COUNT; i++) {
			Vec3 cleatWorld = this.getCleatWorldPos(i);
			double distSq = cleatWorld.distanceToSqr(clickWorld);
			if (distSq < bestDistSq) {
				bestDistSq = distSq;
				bestCleat = i;
			}
		}

		if (bestCleat >= 0 && bestDistSq <= 25.0) { // Within 5.0 blocks of a cleat
			if (holdsLead || holdsLine || pendingFence != null || this.isCleatAttached(bestCleat)) {
				if (interactCleat(player, this, bestCleat)) {
					return InteractionResult.SUCCESS;
				}
			}
		}

		return InteractionResult.PASS;
	}

	// =========================================================================
	// ENGINE & STEERING LOGIC (OCEANCRUISER PHYSICS)
	// =========================================================================
	public void toggleEngine(@Nullable Player player) {
		boolean newState = !this.isEngineActive();
		this.setEngineActive(newState);
		this.setEngineThrottle(0.0F);
		if (player != null && !this.level().isClientSide()) {
			notifyPlayer(
				player,
				Component.translatable(newState ? "message.peterwolfs_rivers.engine_on" : "message.peterwolfs_rivers.engine_off")
			);
		}
	}

	@Override
	public void tick() {
		super.tick();

		// Client tick: smooth interpolation and visual bubbles only
		if (this.level().isClientSide()) {
			this.getInterpolation().interpolate();
			if (this.isEngineActive() && Math.abs(this.getEngineThrottle()) > 0.05F) {
				this.spawnPropellerBubbles();
			}
			return;
		}

		// Server tick: full physics simulation

		// 1. Helmsman connection validation
		if (this.helmsmanUuid != null) {
			Entity currentHelmsman = this.getHelmsman();
			if (currentHelmsman == null || !currentHelmsman.isAlive() || !this.hasPassenger(currentHelmsman)) {
				this.setHelmsman(null);
			}
		}

		// 2. Input reading with fallback
		Entity helmsman = this.getHelmsman();
		if (helmsman instanceof Player player && this.hasPassenger(player)) {
			if (this.inputFreshTicks > 0) {
				this.inputFreshTicks--;
			} else if (player instanceof ServerPlayer sp) {
				var input = sp.getLastClientInput();
				float r = 0.0F;
				if (input.left()) r -= 1.0F;
				if (input.right()) r += 1.0F;
				float t = 0.0F;
				if (input.forward()) t += 1.0F;
				if (input.backward()) t -= 1.0F;
				this.clientRudderInput = r;
				this.clientThrottleInput = t;
			}

			// Rudder steering: A/D adjusts rudder angle (-30 .. +30)
			float targetRudder = this.clientRudderInput * 30.0F;
			if (Math.abs(this.clientRudderInput) > 0.05F) {
				this.setRudderAngle(Mth.approach(this.getRudderAngle(), targetRudder, 4.0F));
			} else {
				this.setRudderAngle(Mth.approach(this.getRudderAngle(), 0.0F, 3.0F));
			}

			// Outboard motor yaw pivots with steering:
			float targetMotorYaw = -this.getRudderAngle() * (float)(Math.PI / 180.0);
			this.setOutboardMotorYaw(Mth.approach(this.getOutboardMotorYaw(), targetMotorYaw, 0.12F));

			// Direct responsive throttle: W increases, S decreases/reverses
			if (this.isEngineActive()) {
				if (this.clientThrottleInput > 0.15F) {
					if (this.getEngineThrottle() < 0.15F && this.getEngineThrottle() >= -0.05F) {
						this.setEngineThrottle(0.25F); // Immediate forward gear engagement
					} else {
						this.setEngineThrottle(Mth.clamp(this.getEngineThrottle() + 0.04F, -0.60F, 1.00F));
					}
				} else if (this.clientThrottleInput < -0.15F) {
					if (this.getEngineThrottle() > -0.10F && this.getEngineThrottle() <= 0.05F) {
						this.setEngineThrottle(-0.20F); // Immediate reverse gear engagement
					} else {
						this.setEngineThrottle(Mth.clamp(this.getEngineThrottle() - 0.04F, -0.60F, 1.00F));
					}
				}
			}
		} else {
			this.setRudderAngle(Mth.approach(this.getRudderAngle(), 0.0F, 2.5F));
			this.setOutboardMotorYaw(Mth.approach(this.getOutboardMotorYaw(), 0.0F, 0.08F));
		}

		List<Player> deckStanders = this.collectDeckStanders();
		Vec3 oldPos = this.position();
		float oldYaw = this.getYRot();

		// Movement Physics
		Vec3 currentVel = this.getDeltaMovement();
		Vec3 newVel = currentVel;
		float newYaw = oldYaw;

		// 1. Calculate environmental drift targets (Wind & Sails + River current)
		Vec3 driftWind = WindAndSailsRiverAdapter.getWindVector(this.level(), this.position());
		float currentWindStrength = 0.0F;
		Vec3 targetWindDrift = Vec3.ZERO;
		if (driftWind != null && driftWind.lengthSqr() > 1.0E-6) {
			currentWindStrength = (float) Math.sqrt(driftWind.x * driftWind.x + driftWind.z * driftWind.z);
			targetWindDrift = driftWind.normalize().scale(Math.min(0.025, 0.018 * currentWindStrength));
		} else {
			// Standalone atmospheric gentle breeze when Wind & Sails is absent
			long dayTime = this.level().getGameTime();
			double angle = (dayTime % 24000L) * (2.0 * Math.PI / 24000.0);
			targetWindDrift = new Vec3(Math.cos(angle) * 0.008, 0.0, Math.sin(angle) * 0.008);
			currentWindStrength = 0.40F;
		}

		RiverFlowSample riverSample = RiverPhysicsEngine.getInstance().sampleFlow(this.level(), this.position());
		Vec3 riverCurrent = riverSample.hasCurrent() ? riverSample.velocityPerTick() : Vec3.ZERO;

		// Combined passive environmental drift target
		Vec3 passiveDriftTarget = riverCurrent.add(targetWindDrift);

		// 2. Propulsion vs Natural Drift
		if (this.isEngineActive()) {
			float throttle = this.getEngineThrottle();
			double headingRad = Math.toRadians(oldYaw);
			Vec3 fwd = new Vec3(-Math.sin(headingRad), 0.0, Math.cos(headingRad));

			double motorPower = (throttle >= 0)
				? throttle * 0.30
				: throttle * 0.15;

			// Outboard vector thrust turns catamaran effectively at all speeds including idle
			float speedFactor = 0.55F + (float) Math.abs(motorPower) * 1.5F;
			float turnRate = (this.getRudderAngle() / 30.0F) * speedFactor * 2.5F;
			if (motorPower < -1.0E-4) {
				turnRate = -turnRate; // Reverse steering in reverse gear
			}
			newYaw = Mth.wrapDegrees(oldYaw + turnRate);

			if (Math.abs(throttle) > 0.05F) {
				Vec3 targetVel = fwd.scale(motorPower).add(riverCurrent);
				newVel = new Vec3(
					Mth.lerp(0.15, currentVel.x, targetVel.x),
					0.0,
					Mth.lerp(0.15, currentVel.z, targetVel.z)
				);
				// Subtle leeway drift under engine
				newVel = newVel.add(targetWindDrift.scale(0.06));
			} else {
				// Engine idle in neutral: vessel naturally drifts with wind and current, exactly like wind_n_sails!
				newVel = new Vec3(
					Mth.lerp(0.08, currentVel.x, passiveDriftTarget.x),
					0.0,
					Mth.lerp(0.08, currentVel.z, passiveDriftTarget.z)
				);
			}

			// Propeller rotation & audio
			this.propellerAngle += throttle * 0.8F;

			if (Math.abs(throttle) > 0.05F) {
				this.spawnPropellerBubbles();
			}

			this.engineSoundCooldown--;
			if (this.engineSoundCooldown <= 0) {
				double power = Math.abs(throttle);
				if (power < 0.04) {
					this.engineSoundCooldown = 20;
					this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
						SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.NEUTRAL, 0.35F, 0.70F);
				} else {
					this.engineSoundCooldown = Math.max(5, 18 - (int)(power * 13));
					float pitch = 0.75F + (float) power * 0.30F;
					this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
						SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.NEUTRAL, 0.45F + (float) power * 0.25F, pitch);
					this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
						SoundEvents.BOAT_PADDLE_WATER, SoundSource.NEUTRAL, 0.40F + (float) power * 0.30F, pitch * 0.85F);
				}
			}
		} else {
			// Engine OFF: drifts naturally with wind and river current, exactly like wind_n_sails!
			newVel = new Vec3(
				Mth.lerp(0.08, currentVel.x, passiveDriftTarget.x),
				0.0,
				Mth.lerp(0.08, currentVel.z, passiveDriftTarget.z)
			);
		}

		// Responsive rudder steering influence when drifting with current or wind
		double driftSpeed = Math.sqrt(newVel.x * newVel.x + newVel.z * newVel.z);
		if (driftSpeed > 0.005) {
			float driftTurn = (this.getRudderAngle() / 30.0F) * 0.50F * (float) Math.min(1.0, driftSpeed / 0.02);
			newYaw = Mth.wrapDegrees(newYaw + driftTurn);
		}

		// River current vorticity in eddies / sharp bends
		if (riverSample.hasCurrent() && Math.abs(riverSample.vorticity()) > 0.05F) {
			newYaw = Mth.wrapDegrees(newYaw + riverSample.vorticity() * 0.8F);
		}

		// 3. Apply Mooring Constraints across the 4 corner cleats
		newYaw = this.applyMooringConstraintsYaw(newYaw);
		newVel = this.applyMooringConstraintsVelocity(newVel, newYaw, currentWindStrength);

		// Water detection & draft leveling
		double surface = this.findWaterSurface();
		double smoothedY;
		if (!Double.isNaN(surface)) {
			double targetY = surface - WresslaLayout.DRAFT * 0.55;
			smoothedY = Mth.lerp(0.25, this.getY(), targetY);
		} else {
			smoothedY = this.getY();
			newVel = newVel.add(0.0, -0.04, 0.0); // Gravity fallback
		}

		this.setDeltaMovement(newVel);
		this.setYRot(newYaw);
		this.setPos(this.getX(), smoothedY, this.getZ());
		this.move(MoverType.SELF, this.getDeltaMovement());

		// Multi-player deck carrier: carry all players standing on the deck
		Vec3 newPos = this.position();
		this.carryDeckStanders(deckStanders, oldPos, oldYaw, newPos, newYaw);
	}

	private double findWaterSurface() {
		AABB bb = this.getBoundingBox();
		int minX = Mth.floor(bb.minX);
		int maxX = Mth.ceil(bb.maxX);
		int minZ = Mth.floor(bb.minZ);
		int maxZ = Mth.ceil(bb.maxZ);
		int minY = Mth.floor(bb.minY - 1.5);
		int maxY = Mth.ceil(bb.maxY + 0.5);

		double highestSurface = Double.NaN;
		BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int y = minY; y <= maxY; y++) {
					mpos.set(x, y, z);
					FluidState fluid = this.level().getFluidState(mpos);
					if (fluid.is(FluidTags.WATER) || fluid.getType() == RiverFluidRegistry.RIVER_WATER || fluid.getType() == RiverFluidRegistry.FLOWING_RIVER_WATER) {
						double surfaceY = y + fluid.getHeight(this.level(), mpos);
						if (Double.isNaN(highestSurface) || surfaceY > highestSurface) {
							highestSurface = surfaceY;
						}
					}
				}
			}
		}
		return highestSurface;
	}

	private void spawnPropellerBubbles() {
		Vec3 propWorld = this.localToWorld(WresslaLayout.PROPELLER_LOCAL_POS);
		for (int i = 0; i < 2; i++) {
			double ox = (this.random.nextDouble() - 0.5) * 0.3;
			double oy = (this.random.nextDouble() - 0.5) * 0.2;
			double oz = (this.random.nextDouble() - 0.5) * 0.3;
			this.level().addParticle(
				ParticleTypes.BUBBLE,
				propWorld.x + ox, propWorld.y + oy, propWorld.z + oz,
				-this.getDeltaMovement().x * 0.4, 0.05, -this.getDeltaMovement().z * 0.4
			);
		}
	}

	public boolean isPlayerOnDeck(Player player) {
		if (player == null || player.isSpectator() || player.getVehicle() == this || player.isPassenger()) {
			return false;
		}
		Vec3 local = this.worldToLocal(player.position());
		if (!WresslaLayout.isWithinDeckBounds(local.x, local.z)) {
			return false;
		}
		double feetRelY = player.getY() - this.getY();
		return feetRelY >= 0.10 && feetRelY <= 2.50;
	}

	private List<Player> collectDeckStanders() {
		AABB search = this.getBoundingBox().inflate(2.0, 3.0, 2.0);
		return this.level().getEntitiesOfClass(Player.class, search, this::isPlayerOnDeck);
	}

	private void carryDeckStanders(List<Player> standers, Vec3 oldPos, float oldYaw, Vec3 newPos, float newYaw) {
		double dYaw = newYaw - oldYaw;
		double rad = Math.toRadians(-dYaw);
		double cos = Math.cos(rad);
		double sin = Math.sin(rad);

		Vec3 deltaMove = newPos.subtract(oldPos);
		boolean moved = deltaMove.lengthSqr() > 1.0E-8 || Math.abs(dYaw) > 1.0E-4;
		if (!moved) return;

		for (Player p : standers) {
			if (p.isRemoved() || p.getVehicle() == this || p.isPassenger()) continue;
			Vec3 rel = p.position().subtract(oldPos);
			double rotX = rel.x * cos - rel.z * sin;
			double rotZ = rel.x * sin + rel.z * cos;
			double targetX = newPos.x + rotX;
			double targetY = p.getY() + (newPos.y - oldPos.y);
			double targetZ = newPos.z + rotZ;

			p.setPos(targetX, targetY, targetZ);

			double targetDeckY = newPos.y + WresslaLayout.DECK_Y;
			if (p.getDeltaMovement().y <= 0.08 && Math.abs(p.getY() - targetDeckY) < 0.8) {
				p.setPos(p.getX(), targetDeckY, p.getZ());
				p.setOnGround(true);
			}
		}
	}

	// =========================================================================
	// COORDINATE CONVERSIONS
	// Local coordinates:
	//   local.x: starboard (+), port (-)
	//   local.y: height above water
	//   local.z: bow / forward (+), stern / aft (-)
	// =========================================================================
	public Vec3 localToWorld(Vec3 local) {
		float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
		double fwdX = -Mth.sin(yawRad);
		double fwdZ = Mth.cos(yawRad);
		double rightX = Mth.cos(yawRad);
		double rightZ = Mth.sin(yawRad);
		double worldX = this.getX() + rightX * local.x + fwdX * local.z;
		double worldY = this.getY() + local.y;
		double worldZ = this.getZ() + rightZ * local.x + fwdZ * local.z;
		return new Vec3(worldX, worldY, worldZ);
	}

	public Vec3 worldToLocal(Vec3 world) {
		Vec3 diff = world.subtract(this.position());
		float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
		double fwdX = -Mth.sin(yawRad);
		double fwdZ = Mth.cos(yawRad);
		double rightX = Mth.cos(yawRad);
		double rightZ = Mth.sin(yawRad);
		double localX = diff.x * rightX + diff.z * rightZ;
		double localY = diff.y;
		double localZ = diff.x * fwdX + diff.z * fwdZ;
		return new Vec3(localX, localY, localZ);
	}

	// =========================================================================
	// GETTERS / SETTERS FOR SYNCHED DATA
	// =========================================================================
	public boolean isEngineActive() { return this.entityData.get(ENGINE_ACTIVE); }
	public void setEngineActive(boolean active) { this.entityData.set(ENGINE_ACTIVE, active); }

	public float getEngineThrottle() { return this.entityData.get(ENGINE_THROTTLE); }
	public void setEngineThrottle(float throttle) { this.entityData.set(ENGINE_THROTTLE, throttle); }

	public float getRudderAngle() { return this.entityData.get(RUDDER_ANGLE); }
	public void setRudderAngle(float angle) { this.entityData.set(RUDDER_ANGLE, angle); }

	public float getOutboardMotorYaw() { return this.entityData.get(OUTBOARD_MOTOR_YAW); }
	public void setOutboardMotorYaw(float yaw) { this.entityData.set(OUTBOARD_MOTOR_YAW, yaw); }

	public float getPropellerAngle() { return this.propellerAngle; }
}
