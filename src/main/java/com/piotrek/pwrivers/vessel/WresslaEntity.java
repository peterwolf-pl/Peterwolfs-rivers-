package com.piotrek.pwrivers.vessel;

import com.piotrek.pwrivers.PeterwolfsRiversMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
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
 *  - 10m x 3.5m twin-pontoon catamaran physics
 *  - Walkable teak deck: players can walk anywhere without falling off
 *  - Interactive ship steering wheel (click to claim helm, shift to release)
 *  - Outboard engine mechanics based on OceanCruiser:
 *      * Incremental throttle (W/S to adjust, holds setting when released)
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
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(HELMSMAN_ID, -1);
		builder.define(ENGINE_ACTIVE, true); // River tram engine active by default
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
		} else {
			this.helmsmanUuid = captain.getUUID();
			this.entityData.set(HELMSMAN_ID, captain.getId());
			if (captain.getVehicle() != this) {
				captain.startRiding(this);
			}
		}
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
		return this.localToWorld(new Vec3(0.0, WresslaLayout.DECK_Y, 0.5));
	}

	// =========================================================================
	// SOLID DECK COLLISION & MULTI-PLAYER WALKING
	// =========================================================================
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

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		if (other instanceof Player || (other != null && this.hasPassenger(other))) {
			return false; // Handled by deck walking carrier
		}
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	// =========================================================================
	// STEERING WHEEL CLICK INTERACTION
	// =========================================================================
	public boolean isAimingAtWheel(Player player, float partialTick) {
		Vec3 eye = player.getEyePosition(partialTick);
		Vec3 view = player.getViewVector(partialTick).scale(5.0);
		Vec3 end = eye.add(view);

		Vec3 localEye = this.worldToLocal(eye);
		Vec3 localEnd = this.worldToLocal(end);

		Optional<Vec3> hit = WresslaLayout.WHEEL_BOX.inflate(0.12).clip(localEye, localEnd);
		return hit.isPresent();
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}

		if (this.isAimingAtWheel(player, 1.0F)) {
			if (!this.level().isClientSide()) {
				if (this.isHelmsman(player)) {
					// Dismount helm
					this.setHelmsman(null);
					player.stopRiding();
				} else {
					// Claim helm
					this.setHelmsman(player);
					notifyPlayer(player, Component.translatable("message.peterwolfs_rivers.helm_claimed"));
				}
			}
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

		Vec3 oldPos = this.position();
		float oldYaw = this.getYRot();

		// Helmsman connection validation
		if (!this.level().isClientSide() && this.helmsmanUuid != null) {
			Entity currentHelmsman = this.getHelmsman();
			if (currentHelmsman == null || !currentHelmsman.isAlive() || currentHelmsman.getVehicle() != this) {
				this.setHelmsman(null);
			}
		}

		// Update controls
		Entity helmsman = this.getHelmsman();
		if (helmsman instanceof Player) {
			// Steering input: A/D adjusts rudder angle (-30 .. +30)
			if (Math.abs(this.clientRudderInput) > 0.15F) {
				float delta = this.clientRudderInput * 2.2F;
				this.setRudderAngle(Mth.clamp(this.getRudderAngle() + delta, -30.0F, 30.0F));
			} else {
				// Returns to neutral smoothly in 10 ticks
				this.setRudderAngle(Mth.approach(this.getRudderAngle(), 0.0F, 3.0F));
			}

			// Outboard motor yaw pivots with steering:
			// Turning helm right pushes stern right, pivoting catamaran bow right
			float targetMotorYaw = -this.getRudderAngle() * (float)(Math.PI / 180.0);
			this.setOutboardMotorYaw(Mth.approach(this.getOutboardMotorYaw(), targetMotorYaw, 0.08F));

			// Throttle input: W increases throttle, S decreases/reverses
			if (this.isEngineActive()) {
				if (this.clientThrottleInput > 0.15F) {
					this.setEngineThrottle(Mth.clamp(this.getEngineThrottle() + 0.02F, -0.50F, 1.00F));
				} else if (this.clientThrottleInput < -0.15F) {
					this.setEngineThrottle(Mth.clamp(this.getEngineThrottle() - 0.02F, -0.50F, 1.00F));
				}
			}
		} else {
			this.setRudderAngle(Mth.approach(this.getRudderAngle(), 0.0F, 2.0F));
			this.setOutboardMotorYaw(Mth.approach(this.getOutboardMotorYaw(), 0.0F, 0.05F));
		}

		// Movement Physics
		Vec3 currentVel = this.getDeltaMovement();
		Vec3 newVel = currentVel;
		float newYaw = oldYaw;

		if (this.isEngineActive()) {
			double headingRad = Math.toRadians(oldYaw);
			Vec3 fwd = new Vec3(-Math.sin(headingRad), 0.0, Math.cos(headingRad));

			// River tour cruising speed (~0.24 m/s max forward, ~0.12 m/s reverse)
			double motorPower = (this.getEngineThrottle() >= 0)
				? this.getEngineThrottle() * 0.24
				: this.getEngineThrottle() * 0.12;

			// Low-speed maneuver turning
			float speedFactor = 0.35F + (float) Math.abs(motorPower) * 1.2F;
			float turnRate = (this.getRudderAngle() / 30.0F) * speedFactor * 2.2F;
			if (motorPower < -1.0E-4) {
				turnRate = -turnRate; // Reversing thrust reverses steering
			}
			newYaw = Mth.wrapDegrees(oldYaw + turnRate);

			Vec3 targetVel = fwd.scale(motorPower);
			newVel = new Vec3(
				Mth.lerp(0.12, currentVel.x, targetVel.x),
				0.0,
				Mth.lerp(0.12, currentVel.z, targetVel.z)
			);

			// Propeller rotation & audio
			float throttle = this.getEngineThrottle();
			this.propellerAngle += throttle * 0.8F;

			if (Math.abs(throttle) > 0.05F) {
				this.spawnPropellerBubbles();
			}

			// Engine audio
			this.engineSoundCooldown--;
			if (this.engineSoundCooldown <= 0) {
				double power = Math.abs(throttle);
				if (power < 0.04) {
					// Idle rumble
					this.engineSoundCooldown = 20;
					this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
						SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.NEUTRAL, 0.35F, 0.70F);
				} else {
					// Cruising roar & water churn
					this.engineSoundCooldown = Math.max(5, 18 - (int)(power * 13));
					float pitch = 0.75F + (float) power * 0.30F;
					this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
						SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.NEUTRAL, 0.45F + (float) power * 0.25F, pitch);
					this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
						SoundEvents.BOAT_PADDLE_WATER, SoundSource.NEUTRAL, 0.40F + (float) power * 0.30F, pitch * 0.85F);
				}
			}
		} else {
			// Engine off - water drag slows catamaran down
			newVel = currentVel.scale(0.92);
		}

		this.setDeltaMovement(newVel);
		this.setYRot(newYaw);
		this.move(MoverType.SELF, newVel);

		// Multi-player deck carrier: carry all players standing on the deck
		this.carryDeckPassengers(oldPos, oldYaw, this.position(), newYaw);
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

	private void carryDeckPassengers(Vec3 oldPos, float oldYaw, Vec3 newPos, float newYaw) {
		AABB deckAabb = this.getBoundingBox().inflate(1.5, 2.0, 1.5);
		List<Player> standingPlayers = this.level().getEntitiesOfClass(
			Player.class, deckAabb, p -> p.isAlive() && !p.isSpectator() && p.getVehicle() != this
		);

		double dYaw = newYaw - oldYaw;
		double rad = Math.toRadians(dYaw);
		double cos = Math.cos(rad);
		double sin = Math.sin(rad);

		Vec3 deltaMove = newPos.subtract(oldPos);
		boolean moved = deltaMove.lengthSqr() > 1.0E-8 || Math.abs(dYaw) > 1.0E-4;

		for (Player p : standingPlayers) {
			Vec3 local = this.worldToLocal(p.position());
			if (!WresslaLayout.isWithinDeckBounds(local.x, local.z)) {
				continue;
			}

			if (moved) {
				Vec3 rel = p.position().subtract(oldPos);
				double rotX = rel.x * cos - rel.z * sin;
				double rotZ = rel.x * sin + rel.z * cos;
				p.setPos(newPos.x + rotX, p.getY() + (newPos.y - oldPos.y), newPos.z + rotZ);
			}

			// Adjust height so feet remain on the teak deck
			double targetY = this.getY() + WresslaLayout.DECK_Y;
			if (p.getDeltaMovement().y <= 0.08 && Math.abs(p.getY() - targetY) < 0.8) {
				p.setPos(p.getX(), targetY, p.getZ());
				p.setOnGround(true);
			}
		}
	}

	// =========================================================================
	// COORDINATE CONVERSIONS
	// =========================================================================
	public Vec3 localToWorld(Vec3 local) {
		double rad = Math.toRadians(-this.getYRot());
		double cos = Math.cos(rad);
		double sin = Math.sin(rad);
		double wx = this.getX() + local.x * cos - local.z * sin;
		double wy = this.getY() + local.y;
		double wz = this.getZ() + local.x * sin + local.z * cos;
		return new Vec3(wx, wy, wz);
	}

	public Vec3 worldToLocal(Vec3 world) {
		double dx = world.x - this.getX();
		double dy = world.y - this.getY();
		double dz = world.z - this.getZ();
		double rad = Math.toRadians(this.getYRot());
		double cos = Math.cos(rad);
		double sin = Math.sin(rad);
		double lx = dx * cos - dz * sin;
		double lz = dx * sin + dz * cos;
		return new Vec3(lx, dy, lz);
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
