package com.piotrek.pwrivers.physics;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Computational solver for vortices, eddies, bend secondary circulation,
 * and obstacle wake recirculation.
 */
public final class RiverVortexSolver {
	/**
	 * Represents an active vortex core in the river flow field.
	 */
	public record VortexCore(
		Vec3 center,
		double coreRadius,
		double circulationStrength, // Gamma (m^2/s), sign determines rotation direction (+ = CCW, - = CW)
		int lifetimeTicks,
		boolean isPermanent // true for bridge piers / permanent bends
	) {
		/**
		 * Computes the induced velocity vector at a given position using the Lamb-Oseen vortex model:
		 * v_theta(r) = (Gamma / (2 * pi * r)) * (1 - exp(-r^2 / r_c^2))
		 */
		public Vec3 evaluateVelocity(Vec3 position) {
			double dx = position.x - this.center.x;
			double dz = position.z - this.center.z;
			double rSq = dx * dx + dz * dz;
			if (rSq < 1e-4) {
				return Vec3.ZERO;
			}
			double r = Math.sqrt(rSq);
			if (r > this.coreRadius * 4.5) {
				return Vec3.ZERO; // Out of influence range
			}

			double rcSq = this.coreRadius * this.coreRadius;
			double factor = (1.0 - Math.exp(-rSq / rcSq));
			double vTheta = (this.circulationStrength / (2.0 * Math.PI * r)) * factor;

			// Tangential unit vector (-dz/r, dx/r)
			double tx = -dz / r;
			double tz = dx / r;

			// Vertical decay: strongest at surface, dampens near riverbed
			double dy = Math.abs(position.y - this.center.y);
			double yAtten = Math.max(0.0, 1.0 - dy / 4.0);

			return new Vec3(tx * vTheta * yAtten, 0.0, tz * vTheta * yAtten);
		}

		/**
		 * Computes local vertical vorticity omega = curl(v)_y at position.
		 */
		public float evaluateVorticity(Vec3 position) {
			double dx = position.x - this.center.x;
			double dz = position.z - this.center.z;
			double rSq = dx * dx + dz * dz;
			double rcSq = this.coreRadius * this.coreRadius;
			if (rSq > rcSq * 16.0) return 0.0F;

			// Exact Lamb-Oseen vorticity: omega(r) = (Gamma / (pi * rc^2)) * exp(-r^2 / rc^2)
			double peakOmega = this.circulationStrength / (Math.PI * rcSq);
			return (float) (peakOmega * Math.exp(-rSq / rcSq));
		}
	}

	private final List<VortexCore> activeVortices = new ArrayList<>();

	public RiverVortexSolver() {}

	public synchronized void addVortex(VortexCore core) {
		this.activeVortices.add(core);
	}

	public synchronized void clearTransientVortices() {
		this.activeVortices.removeIf(v -> !v.isPermanent);
	}

	public synchronized void clearAll() {
		this.activeVortices.clear();
	}

	/**
	 * Computes total induced vortex velocity at position from all active vortices.
	 */
	public synchronized Vec3 sampleVortexVelocity(Vec3 position) {
		if (this.activeVortices.isEmpty()) return Vec3.ZERO;

		double vx = 0.0;
		double vz = 0.0;
		for (VortexCore vortex : this.activeVortices) {
			Vec3 ind = vortex.evaluateVelocity(position);
			vx += ind.x;
			vz += ind.z;
		}
		return new Vec3(vx, 0.0, vz);
	}

	/**
	 * Computes local scalar vorticity at position.
	 */
	public synchronized float sampleTotalVorticity(Vec3 position) {
		float total = 0.0F;
		for (VortexCore vortex : this.activeVortices) {
			total += vortex.evaluateVorticity(position);
		}
		return total;
	}

	/**
	 * Computes secondary circulation flow vector at a river bend.
	 *
	 * In a curved channel of radius R and curvature kappa = 1/R:
	 * Centrifugal force drives faster surface water towards the outer bank.
	 * A return current near the bed moves towards the inner bank.
	 *
	 * @param curvature Signed bend curvature: positive = bending right, negative = bending left (rad/m)
	 * @param longitudinalVelocity Main downstream speed (m/s)
	 * @param relativeDepth Relative depth from bed: 0.0 = bed, 1.0 = surface
	 * @param normalUnit Tangent-normal unit vector pointing from river centerline toward outer bank
	 */
	public static Vec3 computeBendCirculation(
		float curvature,
		double longitudinalVelocity,
		double relativeDepth,
		Vec3 normalUnit
	) {
		if (Math.abs(curvature) < 1e-4 || Math.abs(longitudinalVelocity) < 0.05) {
			return Vec3.ZERO;
		}

		// Centrifugal acceleration ac = v^2 * kappa
		double ac = (longitudinalVelocity * longitudinalVelocity) * Math.abs(curvature);

		// Surface water (zRel > 0.5) is driven outwards; bed water (zRel < 0.5) returns inwards
		// Linear or cubic profile across depth: (2 * zRel - 1.0)
		double zRel = Mth.clamp(relativeDepth, 0.0, 1.0);
		double profile = (2.0 * zRel - 1.0);

		// Secondary transverse velocity magnitude: Rozovskii / de Vriend approximation
		double vTransverse = 0.35 * Math.min(1.2, ac) * profile;

		// Direction: normalUnit points towards outer bank
		return new Vec3(normalUnit.x * vTransverse, 0.0, normalUnit.z * vTransverse);
	}
}
