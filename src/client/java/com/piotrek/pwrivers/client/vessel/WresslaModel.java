package com.piotrek.pwrivers.client.vessel;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * 3D Model for Wressla - Historic Wrocław Tram River Catamaran.
 * Proportions: 10m Length (160 units) x 3.5m Width (56 units).
 * Features:
 *  - Twin segmented modular pontoon floats (black polyethylene with flanged ribs)
 *  - Walkable teak deck platform
 *  - Vintage Wrocław tram styling:
 *      * Tram Ruby Red bulwark panels
 *      * Bold white WRESSLA lettering and Wrocław river tram logo
 *      * Decorative filigree corner arches and white framing
 *      * Slender white canopy pillars and arched tram roof spandrels
 *      * Two-tier clerestory monitor roof ("tramwajowy dach ze świetlikiem")
 *  - Interactive rotating steering wheel ("koło sterowe")
 *  - Pivoting outboard motor with spinning 3-blade propeller
 *  - Marine accessories: 4 hanging blue fenders, orange lifebuoy, stern ladder, and blue flag
 */
public final class WresslaModel extends EntityModel<WresslaRenderState> {
	public static final int ATLAS_SIZE = 2048;

	private final ModelPart root;
	private final ModelPart hull;
	private final ModelPart wheel;
	private final ModelPart outboardMotor;
	private final ModelPart propeller;
	private final ModelPart[] cleatKnots = new ModelPart[4];

	private final ModelPart flagRoot;
	private final ModelPart flagSeg1;
	private final ModelPart flagSeg2;
	private final ModelPart flagSeg3;

	public WresslaModel(ModelPart root) {
		super(root);
		this.root = root;
		this.hull = root.getChild("hull");
		this.wheel = this.hull.getChild("wheel");
		this.outboardMotor = this.hull.getChild("outboard_motor");
		this.propeller = this.outboardMotor.getChild("propeller");
		this.cleatKnots[0] = this.hull.getChild("cleat_knot_0");
		this.cleatKnots[1] = this.hull.getChild("cleat_knot_1");
		this.cleatKnots[2] = this.hull.getChild("cleat_knot_2");
		this.cleatKnots[3] = this.hull.getChild("cleat_knot_3");

		this.flagRoot = this.hull.getChild("flag_root");
		this.flagSeg1 = this.flagRoot.getChild("flag_seg1");
		this.flagSeg2 = this.flagSeg1.getChild("flag_seg2");
		this.flagSeg3 = this.flagSeg2.getChild("flag_seg3");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		// Single waterline root at Y = 24
		PartDefinition hull = root.addOrReplaceChild("hull", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

		// =====================================================================
		// 1. FLOATS (PONTOONS)
		// =====================================================================
		CubeListBuilder floats = CubeListBuilder.create()
			// Port Float (X = -24 .. -12)
			.texOffs(1536, 0).addBox(-24.0F, -12.0F, -64.0F, 12.0F, 12.0F, 136.0F)
			.texOffs(1536, 0).addBox(-23.0F, -11.0F, -80.0F, 10.0F, 10.0F, 16.0F)
			.texOffs(1536, 0).addBox(-23.5F, -11.5F, 72.0F, 11.0F, 11.0F, 4.0F)
			// Starboard Float (X = +12 .. +24)
			.texOffs(1536, 0).addBox(12.0F, -12.0F, -64.0F, 12.0F, 12.0F, 136.0F)
			.texOffs(1536, 0).addBox(13.0F, -11.0F, -80.0F, 10.0F, 10.0F, 16.0F)
			.texOffs(1536, 0).addBox(12.5F, -11.5F, 72.0F, 11.0F, 11.0F, 4.0F)
			// Transom crossbeams and motor bracket mount
			.texOffs(1536, 0).addBox(-24.0F, -15.0F, 74.0F, 48.0F, 3.0F, 4.0F)
			.texOffs(0, 512).addBox(-5.0F, -18.0F, 77.0F, 10.0F, 10.0F, 4.0F);

		// =====================================================================
		// 2. DECK PLATFORM (3.5m wide x 10m long = 56 x 160 units)
		// =====================================================================
		CubeListBuilder deck = CubeListBuilder.create()
			.texOffs(1024, 0).addBox(-28.0F, -16.0F, -76.0F, 56.0F, 2.0F, 152.0F)
			.texOffs(1024, 0).addBox(-24.0F, -16.0F, -80.0F, 48.0F, 2.0F, 4.0F)
			// White protective rub-rail edging
			.texOffs(512, 0).addBox(-28.5F, -16.5F, -77.0F, 1.0F, 2.5F, 154.0F)
			.texOffs(512, 0).addBox(27.5F, -16.5F, -77.0F, 1.0F, 2.5F, 154.0F)
			.texOffs(512, 0).addBox(-25.0F, -16.5F, -80.5F, 50.0F, 2.5F, 1.0F)
			.texOffs(512, 0).addBox(-28.5F, -16.5F, 76.0F, 57.0F, 2.5F, 1.0F);

		// =====================================================================
		// 3. TRAM SUPERSTRUCTURE & ROOF
		// =====================================================================
		CubeListBuilder superstructure = CubeListBuilder.create()
			// Port Bulwark wall with WRESSLA decal
			.texOffs(0, 1024).addBox(-28.0F, -28.0F, -72.0F, 1.0F, 12.0F, 144.0F)
			.texOffs(512, 0).addBox(-28.5F, -29.5F, -73.0F, 2.0F, 1.5F, 146.0F)
			// Starboard Bulwark wall with WRESSLA decal
			.texOffs(0, 1024).addBox(27.0F, -28.0F, -72.0F, 1.0F, 12.0F, 144.0F)
			.texOffs(512, 0).addBox(26.5F, -29.5F, -73.0F, 2.0F, 1.5F, 146.0F)
			// Front bow railing
			.texOffs(0, 0).addBox(-26.0F, -28.0F, -78.0F, 52.0F, 12.0F, 1.0F)
			.texOffs(512, 0).addBox(-27.0F, -29.5F, -78.5F, 54.0F, 1.5F, 2.0F)
			// Stern railings with central boarding opening
			.texOffs(0, 0).addBox(-28.0F, -28.0F, 75.0F, 20.0F, 12.0F, 1.0F)
			.texOffs(512, 0).addBox(-28.5F, -29.5F, 74.5F, 21.0F, 1.5F, 2.0F)
			.texOffs(0, 0).addBox(8.0F, -28.0F, 75.0F, 20.0F, 12.0F, 1.0F)
			.texOffs(512, 0).addBox(7.5F, -29.5F, 74.5F, 21.0F, 1.5F, 2.0F)
			// Longitudinal roof support beams (raised by 0.5 block = 8 units)
			.texOffs(512, 0).addBox(-27.0F, -51.0F, -64.0F, 2.0F, 2.0F, 128.0F)
			.texOffs(512, 0).addBox(25.0F, -51.0F, -64.0F, 2.0F, 2.0F, 128.0F)
			// Lower side roofs (translucent tinted canopy)
			.texOffs(512, 512).addBox(-27.0F, -51.0F, -64.0F, 14.0F, 1.0F, 128.0F)
			.texOffs(512, 512).addBox(13.0F, -51.0F, -64.0F, 14.0F, 1.0F, 128.0F)
			// Two-Tier Clerestory Monitor Roof (transom sides & curved top)
			.texOffs(512, 512).addBox(-13.5F, -55.0F, -64.0F, 1.0F, 5.0F, 128.0F)
			.texOffs(512, 512).addBox(12.5F, -55.0F, -64.0F, 1.0F, 5.0F, 128.0F)
			.texOffs(512, 0).addBox(-14.0F, -57.0F, -66.0F, 28.0F, 2.0F, 132.0F)
			// Bow light mast
			.texOffs(512, 0).addBox(-0.75F, -42.0F, -78.0F, 1.5F, 14.0F, 1.5F)
			.texOffs(512, 0).addBox(-2.0F, -44.0F, -79.0F, 4.0F, 4.0F, 4.0F)
			// Stern flagpole (taller by 2 blocks = 32 units)
			.texOffs(512, 0).addBox(5.25F, -84.0F, 75.0F, 1.5F, 56.0F, 1.5F)
			// Port stern boarding ladder
			.texOffs(512, 0).addBox(-12.0F, -28.0F, 76.0F, 1.0F, 22.0F, 1.5F)
			.texOffs(512, 0).addBox(-8.0F, -28.0F, 76.0F, 1.0F, 22.0F, 1.5F)
			.texOffs(512, 0).addBox(-11.0F, -9.0F, 76.5F, 3.0F, 1.0F, 1.0F)
			.texOffs(512, 0).addBox(-11.0F, -15.0F, 76.5F, 3.0F, 1.0F, 1.0F)
			.texOffs(512, 0).addBox(-11.0F, -21.0F, 76.5F, 3.0F, 1.0F, 1.0F)
			// Starboard stern lifebuoy
			.texOffs(1536, 512).addBox(16.0F, -28.0F, 75.5F, 8.0F, 8.0F, 1.5F);

		// Pillars & Arched Tram Brackets (extended by 8 units to meet raised roof)
		for (int z : new int[]{-56, -28, 0, 28, 56}) {
			superstructure.texOffs(512, 0).addBox(-27.0F, -50.0F, (float)(z - 1), 2.0F, 34.0F, 2.0F);
			superstructure.texOffs(512, 0).addBox(25.0F, -50.0F, (float)(z - 1), 2.0F, 34.0F, 2.0F);
		}
		for (int z : new int[]{-42, -14, 14, 42}) {
			superstructure.texOffs(512, 0).addBox(-26.5F, -49.5F, (float)(z - 10), 1.0F, 2.5F, 20.0F);
			superstructure.texOffs(512, 0).addBox(25.5F, -49.5F, (float)(z - 10), 1.0F, 2.5F, 20.0F);
		}
		// Fenders
		for (int z : new int[]{-40, 40}) {
			superstructure.texOffs(1536, 512).addBox(-29.5F, -25.0F, (float)(z - 2.5), 2.0F, 8.0F, 5.0F);
			superstructure.texOffs(1536, 512).addBox(27.5F, -25.0F, (float)(z - 2.5), 2.0F, 8.0F, 5.0F);
		}

		// =====================================================================
		// 4. INTERIOR (BENCHES & HELM CONSOLE)
		// =====================================================================
		CubeListBuilder interior = CubeListBuilder.create()
			// Port passenger bench
			.texOffs(1024, 512).addBox(-25.0F, -22.5F, -52.0F, 7.0F, 1.5F, 104.0F)
			.texOffs(1024, 512).addBox(-26.0F, -28.0F, -52.0F, 1.5F, 5.5F, 104.0F)
			.texOffs(512, 0).addBox(-24.0F, -21.0F, -48.0F, 5.0F, 5.0F, 96.0F)
			// Starboard passenger bench
			.texOffs(1024, 512).addBox(18.0F, -22.5F, -52.0F, 7.0F, 1.5F, 104.0F)
			.texOffs(1024, 512).addBox(24.5F, -28.0F, -52.0F, 1.5F, 5.5F, 104.0F)
			.texOffs(512, 0).addBox(19.0F, -21.0F, -48.0F, 5.0F, 5.0F, 96.0F)
			// Helm console pedestal & dashboard
			.texOffs(1024, 512).addBox(-4.0F, -28.0F, -14.0F, 8.0F, 12.0F, 8.0F)
			.texOffs(1024, 512).addBox(-4.5F, -30.0F, -14.5F, 9.0F, 2.0F, 9.0F);

		hull.addOrReplaceChild("floats", floats, PartPose.ZERO);
		hull.addOrReplaceChild("deck", deck, PartPose.ZERO);
		hull.addOrReplaceChild("superstructure", superstructure, PartPose.ZERO);
		hull.addOrReplaceChild("interior", interior, PartPose.ZERO);

		// 4 Mooring Cleats on the corners of the teak deck (bow & stern, port & starboard)
		CubeListBuilder cleats = CubeListBuilder.create()
			// Bow Port (X=-24, Z=-72)
			.texOffs(512, 0).addBox(-25.0F, -17.5F, -75.0F, 2.0F, 1.5F, 6.0F)
			// Bow Starboard (X=+24, Z=-72)
			.texOffs(512, 0).addBox(23.0F, -17.5F, -75.0F, 2.0F, 1.5F, 6.0F)
			// Stern Port (X=-24, Z=+72)
			.texOffs(512, 0).addBox(-25.0F, -17.5F, 69.0F, 2.0F, 1.5F, 6.0F)
			// Stern Starboard (X=+24, Z=+72)
			.texOffs(512, 0).addBox(23.0F, -17.5F, 69.0F, 2.0F, 1.5F, 6.0F);
		hull.addOrReplaceChild("cleats", cleats, PartPose.ZERO);

		// 4 3D Rope Knots (one for each cleat, visible when cleat is tied)
		hull.addOrReplaceChild("cleat_knot_0", CubeListBuilder.create()
			.texOffs(1024, 0).addBox(-25.5F, -18.5F, -74.0F, 3.0F, 2.5F, 4.0F), PartPose.ZERO);
		hull.addOrReplaceChild("cleat_knot_1", CubeListBuilder.create()
			.texOffs(1024, 0).addBox(22.5F, -18.5F, -74.0F, 3.0F, 2.5F, 4.0F), PartPose.ZERO);
		hull.addOrReplaceChild("cleat_knot_2", CubeListBuilder.create()
			.texOffs(1024, 0).addBox(-25.5F, -18.5F, 70.0F, 3.0F, 2.5F, 4.0F), PartPose.ZERO);
		hull.addOrReplaceChild("cleat_knot_3", CubeListBuilder.create()
			.texOffs(1024, 0).addBox(22.5F, -18.5F, 70.0F, 3.0F, 2.5F, 4.0F), PartPose.ZERO);

		// =====================================================================
		// 5. STEERING WHEEL ("Koło sterowe" - clickable, rotates on Z-axis)
		// =====================================================================
		CubeListBuilder wheelBuilder = CubeListBuilder.create()
			.texOffs(1024, 512).addBox(-1.5F, -1.5F, -0.5F, 3.0F, 3.0F, 1.0F)
			.texOffs(1024, 512).addBox(-4.0F, -5.5F, -0.2F, 8.0F, 1.5F, 0.4F)
			.texOffs(1024, 512).addBox(-4.0F, 4.0F, -0.2F, 8.0F, 1.5F, 0.4F)
			.texOffs(1024, 512).addBox(-5.5F, -4.0F, -0.2F, 1.5F, 8.0F, 0.4F)
			.texOffs(1024, 512).addBox(4.0F, -4.0F, -0.2F, 1.5F, 8.0F, 0.4F)
			.texOffs(1024, 512).addBox(-0.75F, -5.0F, -0.3F, 1.5F, 10.0F, 0.6F)
			.texOffs(1024, 512).addBox(-5.0F, -0.75F, -0.3F, 10.0F, 1.5F, 0.6F);

		hull.addOrReplaceChild("wheel", wheelBuilder, PartPose.offset(0.0F, -30.0F, -5.0F));

		// =====================================================================
		// 6. OUTBOARD MOTOR (Pivots on Y-axis with steering, tilts on X-axis)
		// =====================================================================
		CubeListBuilder motorBuilder = CubeListBuilder.create()
			// Upper cowling (gloss black with Mercury red stripe)
			.texOffs(0, 512).addBox(-4.0F, -15.0F, -3.0F, 8.0F, 11.0F, 10.0F)
			.texOffs(0, 512).addBox(-3.5F, -17.0F, -2.0F, 7.0F, 2.0F, 8.0F)
			// Swivel bracket & drive shaft housing
			.texOffs(0, 512).addBox(-2.5F, -4.0F, -2.0F, 5.0F, 6.0F, 5.0F)
			.texOffs(0, 512).addBox(-2.0F, 2.0F, -1.0F, 4.0F, 8.0F, 6.0F)
			// Lower unit: cavitation plate, torpedo gearcase, skeg fin
			.texOffs(0, 512).addBox(-4.5F, 9.5F, -2.0F, 9.0F, 1.0F, 9.0F)
			.texOffs(0, 512).addBox(-2.0F, 10.5F, -3.0F, 4.0F, 3.0F, 10.0F)
			.texOffs(0, 512).addBox(-0.5F, 13.5F, 0.0F, 1.0F, 3.5F, 6.0F)
			// Steering tiller arm
			.texOffs(0, 512).addBox(-1.0F, -4.5F, -5.0F, 2.0F, 1.5F, 3.0F);

		PartDefinition motorPart = hull.addOrReplaceChild("outboard_motor", motorBuilder, PartPose.offset(0.0F, -14.0F, 80.0F));

		// Propeller (spins around Z-axis)
		CubeListBuilder propBuilder = CubeListBuilder.create()
			.texOffs(0, 512).addBox(-1.0F, -1.0F, -0.7F, 2.0F, 2.0F, 1.4F)
			.texOffs(0, 512).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 3.0F, 1.0F)
			.texOffs(0, 512).addBox(1.0F, 0.5F, -0.5F, 2.5F, 2.0F, 1.0F)
			.texOffs(0, 512).addBox(-3.5F, 0.5F, -0.5F, 2.5F, 2.0F, 1.0F);

		motorPart.addOrReplaceChild("propeller", propBuilder, PartPose.offset(0.0F, 12.0F, -3.5F));

		// =====================================================================
		// 7. ARTICULATED WIND FLAG AT STERN (Błękitny / Sky Azure)
		// =====================================================================
		PartDefinition flagRootPart = hull.addOrReplaceChild("flag_root",
			CubeListBuilder.create(),
			PartPose.offset(6.0F, -83.0F, 75.75F)
		);

		PartDefinition seg1 = flagRootPart.addOrReplaceChild("flag_seg1",
			CubeListBuilder.create()
				.texOffs(1536, 1024).addBox(0.0F, 0.0F, -0.25F, 6.0F, 8.0F, 0.5F),
			PartPose.ZERO
		);

		PartDefinition seg2 = seg1.addOrReplaceChild("flag_seg2",
			CubeListBuilder.create()
				.texOffs(1560, 1024).addBox(0.0F, 0.0F, -0.25F, 6.0F, 8.0F, 0.5F),
			PartPose.offset(6.0F, 0.0F, 0.0F)
		);

		seg2.addOrReplaceChild("flag_seg3",
			CubeListBuilder.create()
				.texOffs(1584, 1024).addBox(0.0F, 0.0F, -0.25F, 6.0F, 8.0F, 0.5F),
			PartPose.offset(6.0F, 0.0F, 0.0F)
		);

		return LayerDefinition.create(mesh, ATLAS_SIZE, ATLAS_SIZE);
	}

	public static LayerDefinition createWaterPatch() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// Water mask covering the twin floats beneath waterline
		root.addOrReplaceChild("water_mask", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-25.0F, -1.0F, -81.0F, 14.0F, 2.0F, 162.0F)
			.texOffs(0, 0).addBox(11.0F, -1.0F, -81.0F, 14.0F, 2.0F, 162.0F),
			PartPose.offset(0.0F, 24.0F, 0.0F)
		);
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(WresslaRenderState state) {
		super.setupAnim(state);
		// Steering wheel turns with rudder angle
		this.wheel.zRot = state.wheelAngle;

		// Outboard engine pivots left/right with steering and tilts
		this.outboardMotor.yRot = state.engineYaw;
		this.outboardMotor.xRot = state.engineTilt;

		// Propeller spins with engine throttle
		this.propeller.zRot = state.propellerAngle;

		// 4 corner cleat 3D rope knots visibility
		for (int i = 0; i < 4; i++) {
			this.cleatKnots[i].visible = state.cleatTied[i];
		}

		// Stern flag wind orientation & articulated waving animation (matches Wind & Sails)
		this.flagRoot.yRot = state.flagRelativeYaw;
		this.flagRoot.xRot = 0.0F;

		float windFactor = Mth.clamp((state.flagWindSpeed - 0.20F) / 1.5F, 0.0F, 1.0F);
		float limp = 1.0F - windFactor;
		float waveSpeed = 0.25F + state.flagWindSpeed * 0.15F;
		float phase = state.gameTime * waveSpeed;
		float flutter = Mth.sin(phase * 2.3F) * 0.16F;

		// Lateral fluttering in wind
		this.flagSeg1.yRot = (Mth.sin(phase) * 0.08F + flutter * 0.03F) * (0.3F + 0.7F * windFactor);
		this.flagSeg2.yRot = (Mth.sin(phase - 1.2F) * 0.22F + flutter * 0.06F) * (0.3F + 0.7F * windFactor);
		this.flagSeg3.yRot = (Mth.sin(phase - 2.4F) * 0.38F + flutter * 0.12F) * (0.3F + 0.7F * windFactor);

		// Flag drooping downwards (+Y is down towards water in model coordinates)
		float droopPitch = Mth.lerp(windFactor, 14.0F, 0.5F);
		this.flagSeg1.zRot = (float) Math.toRadians(droopPitch);

		// Light wind downward sag of trailing panels (+Y is down towards water)
		float halfPrevious = 1.8F * limp;
		this.flagSeg2.y = halfPrevious;
		this.flagSeg3.y = halfPrevious;
		this.flagSeg2.zRot = (Mth.cos(phase - 0.8F) * 0.05F + flutter * 0.02F) * windFactor;
		this.flagSeg3.zRot = (Mth.cos(phase - 2.0F) * 0.10F + flutter * 0.04F) * windFactor;
	}
}
