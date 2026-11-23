package com.piotrek.pwrivers.vessel;

import com.piotrek.pwrivers.PeterwolfsRiversMod;
import com.piotrek.pwrivers.fluid.RiverFluidRegistry;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
 */
public class WresslaEntity extends Entity {
	private static final EntityDataAccessor<Integer> HELMSMAN_ID = SynchedEntityData.defineId(WresslaEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> ENGINE_ACTIVE = SynchedEntityData.defineId(WresslaEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Float> ENGINE_THROTTLE = SynchedEntityData.defineId(WresslaEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> RUDDER_ANGLE = SynchedEntityData.defineId(WresslaEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> OUTBOARD_MOTOR_YAW = SynchedEntityData.defineId(WresslaEntity.class, EntityDataSerializers.FLOAT);

	@Nullable
	private UUID helmsmanUuid;
	public float clientRudderInput;
	public float clientThrottleInput;
	private int inputFreshTicks;

	private int engineSoundCooldown;
	private float propellerAngle;

	public WresslaEntity(EntityType<?> type, Level level) {
		super(type, level);
		this.blocksBuilding = true;
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
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(HELMSMAN_ID, -1);
		builder.define(ENGINE_ACTIVE, true);
		builder.define(ENGINE_THROTTLE, 0.0F);
		builder.define(RUDDER_ANGLE, 0.0F);
		builder.define(OUTBOARD_MOTOR_YAW, 0.0F);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.setEngineActive(input.getBooleanOr("EngineActive", true));
		this.setEngineThrottle(input.getFloatOr("EngineThrottle", 0.0F));
		this.setRudderAngle(input.getFloatOr("RudderAngle", 0.0F));
		this.setOutboardMotorYaw(input.getFloatOr("OutboardMotorYaw", 0.0F));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putBoolean("EngineActive", this.isEngineActive());
		output.putFloat("EngineThrottle", this.getEngineThrottle());
		output.putFloat("RudderAngle", this.getRudderAngle());
		output.putFloat("OutboardMotorYaw", this.getOutboardMotorYaw());
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

	public boolean tryClaimHelm(LivingEntity captain) {
		if (captain == null || !captain.isAlive()) return false;
		Entity current = this.getHelmsman();
		if (current != null && current != captain) return false;
		if (captain.getVehicle() != null && captain.getVehicle() != this) return false;
		if (captain.getVehicle() != this) {
			if (!captain.startRiding(this)) return false;
		}
		this.setHelmsman(captain);
		captain.setYRot(this.getYRot());
		captain.setYHeadRot(this.getYRot());
		return true;
	}

	public boolean releaseHelm(LivingEntity captain) {
		if (!this.isHelmsman(captain) && captain.getVehicle() != this) return false;
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
		return passenger instanceof LivingEntity && this.getPassengers().isEmpty();
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
		AABB search = player.getBoundingBox().inflate(6.0);
		for (WresslaEntity wressla : player.level().getEntitiesOfClass(WresslaEntity.class, search)) {
			boolean aiming = wressla.isAimingAtWheel(player, 1.0F);
			Vec3 local = wressla.worldToLocal(player.position());
			boolean nearHelm = local.distanceTo(WresslaLayout.HELMSMAN_POS) < 2.5;

			if (aiming || nearHelm) {
				if (!player.level().isClientSide()) {
					if (player.getVehicle() == wressla) {
						wressla.releaseHelm(player);
					} else {
						if (!wressla.tryClaimHelm(player)) {
							notifyPlayer(player, Component.translatable("message.peterwolfs_rivers.helm_occupied"));
						} else {
							notifyPlayer(player, Component.translatable("message.peterwolfs_rivers.helm_claimed"));
						}
					}
				}
				return true;
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

		if (this.isEngineActive()) {
			double headingRad = Math.toRadians(oldYaw);
			// Forward vector in Minecraft: (-sin(yaw), 0, cos(yaw))
			Vec3 fwd = new Vec3(-Math.sin(headingRad), 0.0, Math.cos(headingRad));

			float throttle = this.getEngineThrottle();
			double motorPower = (throttle >= 0)
				? throttle * 0.30
				: throttle * 0.15;

			// Outboard vector thrust turns catamaran even at idle speed!
			float speedFactor = 0.55F + (float) Math.abs(motorPower) * 1.5F;
			float turnRate = (this.getRudderAngle() / 30.0F) * speedFactor * 2.5F;
			if (motorPower < -1.0E-4) {
				turnRate = -turnRate; // Reverse steering in reverse gear
			}
			newYaw = Mth.wrapDegrees(oldYaw + turnRate);

			Vec3 targetVel = fwd.scale(motorPower);
			newVel = new Vec3(
				Mth.lerp(0.15, currentVel.x, targetVel.x),
				0.0,
				Mth.lerp(0.15, currentVel.z, targetVel.z)
			);

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
			newVel = currentVel.scale(0.92);
		}

		// River current entrainment (drift with water) & vorticity
		RiverFlowSample riverSample = RiverPhysicsEngine.getInstance().sampleFlow(this.level(), this.position());
		if (riverSample.hasCurrent()) {
			Vec3 riverCurrent = riverSample.velocity();
			double coupling = this.isEngineActive() ? 0.05 : 0.12;
			newVel = new Vec3(
				Mth.lerp(coupling, newVel.x, riverCurrent.x),
				newVel.y,
				Mth.lerp(coupling, newVel.z, riverCurrent.z)
			);
			if (Math.abs(riverSample.vorticity()) > 0.05F) {
				newYaw = Mth.wrapDegrees(newYaw + riverSample.vorticity() * 0.8F);
			}
		}

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
	//   local.z: bow / forward (-), stern / aft (+)
	// =========================================================================
	public Vec3 localToWorld(Vec3 local) {
		double radians = Math.toRadians(this.getYRot());
		double worldX = Math.cos(radians) * local.x + Math.sin(radians) * local.z;
		double worldZ = Math.sin(radians) * local.x - Math.cos(radians) * local.z;
		return new Vec3(this.getX() + worldX, this.getY() + local.y, this.getZ() + worldZ);
	}

	public Vec3 worldToLocal(Vec3 world) {
		Vec3 diff = world.subtract(this.position());
		double radians = Math.toRadians(this.getYRot());
		double localX = diff.x * Math.cos(radians) + diff.z * Math.sin(radians);
		double localZ = diff.x * Math.sin(radians) - diff.z * Math.cos(radians);
		return new Vec3(localX, diff.y, localZ);
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
