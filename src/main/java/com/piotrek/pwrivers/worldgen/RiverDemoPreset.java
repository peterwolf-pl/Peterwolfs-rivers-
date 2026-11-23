package com.piotrek.pwrivers.worldgen;

import com.piotrek.pwrivers.fluid.RiverFluidRegistry;
import com.piotrek.pwrivers.network.RiverJunction;
import com.piotrek.pwrivers.network.RiverNetworkGraph;
import com.piotrek.pwrivers.network.RiverReach;
import com.piotrek.pwrivers.physics.RiverPhysicsEngine;
import com.piotrek.pwrivers.physics.RiverVortexSolver;
import com.piotrek.pwrivers.structures.RiverLockController;
import com.piotrek.pwrivers.structures.RiverLockGate;
import com.piotrek.pwrivers.structures.RiverWeir;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Odra River Demonstration Environment inspired by Wrocław, Poland.
 * Includes main river channel, island bifurcations (Wyspa Piaskowa/Słodowa),
 * navigation fairway, side canal, two working locks (Śluza Piaskowa & Śluza Mieszczańska),
 * two weirs (Jaz św. Macieja & Jaz Mieszczański), and bridge piers with wake vortices.
 */
public final class RiverDemoPreset {
	private RiverDemoPreset() {}

	/**
	 * Configures the complete mathematical network graph and hydraulic structures
	 * centered around the given coordinates.
	 */
	public static void setupOdraNetwork(double originX, double originY, double originZ) {
		RiverPhysicsEngine engine = RiverPhysicsEngine.getInstance();
		RiverNetworkGraph network = engine.getNetwork();
		network.clear();

		// 1. Upstream Odra Gorge / Main Inflow Reach (Length: 150m, flowing North/North-West)
		List<Vec3> upstreamPts = new ArrayList<>();
		upstreamPts.add(new Vec3(originX, originY, originZ - 200));
		upstreamPts.add(new Vec3(originX - 10, originY, originZ - 130));
		upstreamPts.add(new Vec3(originX - 15, originY, originZ - 70));
		upstreamPts.add(new Vec3(originX, originY, originZ));

		RiverReach upstreamReach = new RiverReach(
			"odra_upstream",
			"Odra Górna (Gorge & Rapids)",
			upstreamPts,
			180.0, // Q = 180 m^3/s
			55.0,  // width = 55m
			5.5,   // depth = 5.5m
			originY,
			0.0008,
			0.038
		);
		network.addReach(upstreamReach);

		// 2. Island Bifurcation Junction (Splits into North Odra and South Odra around Wyspa Piaskowa)
		Vec3 bifurcPos = new Vec3(originX, originY, originZ);
		RiverJunction bifurcation = new RiverJunction("odra_island_bifurcation", bifurcPos, RiverJunction.Type.BIFURCATION);
		bifurcation.addIncomingReach("odra_upstream");
		bifurcation.addOutgoingReach("odra_north_branch");
		bifurcation.addOutgoingReach("odra_south_branch");
		network.addJunction(bifurcation);

		// 3. North Odra Branch (Odra Północna - Main navigation channel with Śluza Mieszczańska)
		List<Vec3> northPts = new ArrayList<>();
		northPts.add(new Vec3(originX, originY, originZ));
		northPts.add(new Vec3(originX + 35, originY, originZ + 60));
		northPts.add(new Vec3(originX + 45, originY, originZ + 140));
		northPts.add(new Vec3(originX + 15, originY - 1.8, originZ + 220));

		RiverReach northBranch = new RiverReach(
			"odra_north_branch",
			"Odra Północna (Fairway)",
			northPts,
			100.0,
			45.0,
			4.8,
			originY,
			0.0004,
			0.030
		);
		network.addReach(northBranch);

		// 4. South Odra Branch (Odra Południowa - Weir overflow & canal branch)
		List<Vec3> southPts = new ArrayList<>();
		southPts.add(new Vec3(originX, originY, originZ));
		southPts.add(new Vec3(originX - 40, originY, originZ + 60));
		southPts.add(new Vec3(originX - 50, originY, originZ + 140));
		southPts.add(new Vec3(originX - 15, originY - 1.8, originZ + 220));

		RiverReach southBranch = new RiverReach(
			"odra_south_branch",
			"Odra Południowa (Weir Branch)",
			southPts,
			80.0,
			40.0,
			4.2,
			originY,
			0.0006,
			0.035
		);
		network.addReach(southBranch);

		// 5. Downstream Confluence Junction (Reunites downstream of islands)
		Vec3 conflPos = new Vec3(originX, originY - 1.8, originZ + 220);
		RiverJunction confluence = new RiverJunction("odra_confluence", conflPos, RiverJunction.Type.CONFLUENCE);
		confluence.addIncomingReach("odra_north_branch");
		confluence.addIncomingReach("odra_south_branch");
		confluence.addOutgoingReach("odra_downstream_basin");
		network.addJunction(confluence);

		// 6. Downstream Calm Basin Reach
		List<Vec3> downPts = new ArrayList<>();
		downPts.add(conflPos);
		downPts.add(new Vec3(originX, originY - 1.8, originZ + 300));
		downPts.add(new Vec3(originX + 10, originY - 1.8, originZ + 400));

		RiverReach downReach = new RiverReach(
			"odra_downstream_basin",
			"Odra Dolna (Calm Lock Basin)",
			downPts,
			180.0,
			70.0,
			5.8,
			originY - 1.8,
			0.0003,
			0.028
		);
		network.addReach(downReach);

		// 7. Operational Lock 1: Śluza Piaskowa (Side Navigation Lock)
		double lock1UpperY = originY;
		double lock1LowerY = originY - 1.8;
		Vec3 lock1UpPos = new Vec3(originX + 35, originY, originZ + 80);
		Vec3 lock1DownPos = new Vec3(originX + 35, originY - 1.8, originZ + 120);

		RiverLockGate lock1UpperGate = new RiverLockGate("piaskowa_upper_gate", lock1UpPos, Direction.SOUTH, 10.0, 5.0);
		RiverLockGate lock1LowerGate = new RiverLockGate("piaskowa_lower_gate", lock1DownPos, Direction.SOUTH, 10.0, 5.0);
		AABB lock1Chamber = new AABB(
			originX + 30, originY - 5.0, originZ + 80,
			originX + 40, originY + 3.0, originZ + 120
		);

		RiverLockController lock1 = new RiverLockController(
			"piaskowa",
			"Śluza Piaskowa (Wrocław)",
			lock1UpperGate,
			lock1LowerGate,
			lock1Chamber,
			lock1UpperY,
			lock1LowerY
		);
		engine.registerLock(lock1);

		// 8. Operational Lock 2: Śluza Mieszczańska
		Vec3 lock2UpPos = new Vec3(originX + 25, originY, originZ + 160);
		Vec3 lock2DownPos = new Vec3(originX + 25, originY - 1.8, originZ + 200);

		RiverLockGate lock2UpperGate = new RiverLockGate("mieszczanska_upper_gate", lock2UpPos, Direction.SOUTH, 12.0, 5.0);
		RiverLockGate lock2LowerGate = new RiverLockGate("mieszczanska_lower_gate", lock2DownPos, Direction.SOUTH, 12.0, 5.0);
		AABB lock2Chamber = new AABB(
			originX + 19, originY - 5.0, originZ + 160,
			originX + 31, originY + 3.0, originZ + 200
		);

		RiverLockController lock2 = new RiverLockController(
			"mieszczanska",
			"Śluza Mieszczańska (Wrocław)",
			lock2UpperGate,
			lock2LowerGate,
			lock2Chamber,
			lock1UpperY,
			lock1LowerY
		);
		engine.registerLock(lock2);

		// 9. Weirs: Jaz św. Macieja & Jaz Mieszczański
		RiverWeir weir1 = new RiverWeir(
			"jaz_macieja",
			"Jaz św. Macieja",
			new Vec3(originX - 45, originY, originZ + 100),
			Direction.SOUTH,
			32.0,
			originY - 0.5,
			true
		);
		engine.registerWeir(weir1);

		RiverWeir weir2 = new RiverWeir(
			"jaz_mieszczanski",
			"Jaz Mieszczański",
			new Vec3(originX - 35, originY, originZ + 180),
			Direction.SOUTH,
			30.0,
			originY - 0.7,
			false
		);
		engine.registerWeir(weir2);

		// 10. Bridge Pier Wake Vortices (Most Uniwersytecki / Most Pomorski piers)
		RiverVortexSolver vSolver = engine.getVortexSolver();
		vSolver.clearAll();

		// Two permanent wake eddy pairs behind bridge pillars
		vSolver.addVortex(new RiverVortexSolver.VortexCore(
			new Vec3(originX - 5.0, originY, originZ + 40),
			3.2,
			1.4, // CCW eddy
			999999,
			true
		));
		vSolver.addVortex(new RiverVortexSolver.VortexCore(
			new Vec3(originX + 5.0, originY, originZ + 40),
			3.2,
			-1.4, // CW eddy
			999999,
			true
		));

		// Sharp bend eddy at South Odra outer bend
		vSolver.addVortex(new RiverVortexSolver.VortexCore(
			new Vec3(originX - 55.0, originY, originZ + 120),
			4.5,
			2.1,
			999999,
			true
		));

		network.solveNetworkHydraulics();
	}

	/**
	 * Materializes the showcase river channel, stone lock masonry, and weir gates
	 * into physical world blocks on a server level.
	 */
	public static void buildWorldDemo(ServerLevel level, BlockPos origin) {
		setupOdraNetwork(origin.getX(), origin.getY(), origin.getZ());

		BlockState riverWater = RiverFluidRegistry.RIVER_WATER_BLOCK.defaultBlockState();
		BlockState stoneBricks = Blocks.STONE_BRICKS.defaultBlockState();
		BlockState gravel = Blocks.GRAVEL.defaultBlockState();
		BlockState sand = Blocks.SAND.defaultBlockState();
		BlockState ironBars = Blocks.IRON_BARS.defaultBlockState();
		BlockState oakPlanks = Blocks.OAK_PLANKS.defaultBlockState();

		int ox = origin.getX();
		int oy = origin.getY();
		int oz = origin.getZ();

		// Carve and fill upstream gorge channel (from oz - 120 to oz)
		for (int z = oz - 100; z <= oz; z++) {
			for (int x = ox - 20; x <= ox + 20; x++) {
				double distCenter = Math.abs(x - ox);
				if (distCenter <= 18) {
					// Riverbed at oy - 5
					level.setBlockAndUpdate(new BlockPos(x, oy - 6, z), (distCenter > 14) ? sand : gravel);

					// Fill water column up to oy
					for (int y = oy - 5; y <= oy; y++) {
						level.setBlockAndUpdate(new BlockPos(x, y, z), riverWater);
					}
					// Air above
					for (int y = oy + 1; y <= oy + 4; y++) {
						level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
					}
				} else if (distCenter <= 21) {
					// Embankment
					for (int y = oy - 5; y <= oy + 1; y++) {
						level.setBlockAndUpdate(new BlockPos(x, y, z), stoneBricks);
					}
				}
			}
		}

		// Carve Lock 1: Śluza Piaskowa chamber (ox + 30 to ox + 40, oz + 80 to oz + 120)
		for (int z = oz + 80; z <= oz + 120; z++) {
			for (int x = ox + 30; x <= ox + 40; x++) {
				boolean isWall = (x == ox + 30 || x == ox + 40);
				for (int y = oy - 5; y <= oy + 2; y++) {
					BlockPos bp = new BlockPos(x, y, z);
					if (isWall) {
						level.setBlockAndUpdate(bp, stoneBricks);
					} else {
						if (y == oy - 5) {
							level.setBlockAndUpdate(bp, stoneBricks); // Floor
						} else if (y <= oy - 2) {
							level.setBlockAndUpdate(bp, riverWater);   // Lower water level
						} else {
							level.setBlockAndUpdate(bp, Blocks.AIR.defaultBlockState());
						}
					}
				}
			}
		}

		// Bridge Pillars at oz + 35
		for (int y = oy - 5; y <= oy + 6; y++) {
			level.setBlockAndUpdate(new BlockPos(ox - 6, y, oz + 35), stoneBricks);
			level.setBlockAndUpdate(new BlockPos(ox - 5, y, oz + 35), stoneBricks);
			level.setBlockAndUpdate(new BlockPos(ox + 5, y, oz + 35), stoneBricks);
			level.setBlockAndUpdate(new BlockPos(ox + 6, y, oz + 35), stoneBricks);
		}
		// Bridge deck
		for (int x = ox - 22; x <= ox + 22; x++) {
			level.setBlockAndUpdate(new BlockPos(x, oy + 6, oz + 34), stoneBricks);
			level.setBlockAndUpdate(new BlockPos(x, oy + 6, oz + 35), stoneBricks);
			level.setBlockAndUpdate(new BlockPos(x, oy + 6, oz + 36), stoneBricks);
		}

		// Weir 1: Jaz św. Macieja at ox - 45, oz + 100
		for (int x = ox - 60; x <= ox - 30; x++) {
			level.setBlockAndUpdate(new BlockPos(x, oy - 1, oz + 100), ironBars);
			level.setBlockAndUpdate(new BlockPos(x, oy - 2, oz + 100), stoneBricks);
			level.setBlockAndUpdate(new BlockPos(x, oy - 3, oz + 100), stoneBricks);
			level.setBlockAndUpdate(new BlockPos(x, oy - 4, oz + 100), stoneBricks);
			level.setBlockAndUpdate(new BlockPos(x, oy - 5, oz + 100), stoneBricks);
		}
	}
}
