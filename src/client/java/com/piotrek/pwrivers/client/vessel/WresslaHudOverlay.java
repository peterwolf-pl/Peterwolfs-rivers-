package com.piotrek.pwrivers.client.vessel;

import com.piotrek.pwrivers.vessel.WresslaEntity;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Clean navigation HUD overlay for Wressla historic river catamaran.
 * Displays real-time throttle percentage, rudder angle, speed, and key controls.
 */
public final class WresslaHudOverlay implements HudElement {
	public static final WresslaHudOverlay INSTANCE = new WresslaHudOverlay();

	private WresslaHudOverlay() {}

	@Override
	public void extractRenderState(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || !(client.player.getVehicle() instanceof WresslaEntity wressla)) {
			return;
		}

		Font font = client.font;
		int width = extractor.guiWidth();
		int height = extractor.guiHeight();

		Vec3 motion = wressla.getDeltaMovement();
		double horizSpeed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
		double speedKmH = horizSpeed * 72.0; // 1 m/s = 3.6 km/h (20 ticks/sec * 3.6 = 72)

		int hudX = width / 2;
		int hudY = height - 68;

		// Main Telemetry Box
		extractor.fill(hudX - 180, hudY, hudX + 180, hudY + 28, 0x95000000);
		extractor.outline(hudX - 180, hudY, 360, 28, 0x40FFFFFF);

		// Drive status line
		Component driveMode;
		int driveColor;
		if (wressla.isEngineActive()) {
			int throttlePct = Math.round(wressla.getEngineThrottle() * 100.0F);
			String pctStr = (throttlePct > 0 ? "+" : "") + throttlePct + "%";
			if (throttlePct > 2) {
				driveMode = Component.literal("Silnik: NAPRZÓD " + pctStr);
				driveColor = 0xFF55FF55; // Bright green
			} else if (throttlePct < -2) {
				driveMode = Component.literal("Silnik: WSTECZ " + pctStr);
				driveColor = 0xFFFFAA00; // Orange
			} else {
				driveMode = Component.literal("Silnik: JAŁOWY (0%)");
				driveColor = 0xFFFFFF55; // Yellow
			}
		} else {
			driveMode = Component.literal("Silnik: WYŁĄCZONY [Z]");
			driveColor = 0xFFFFAAAA;
		}

		float rudder = wressla.getRudderAngle();
		Component rudderComp = Math.abs(rudder) < 1.0F
			? Component.literal("Ster: ŚRODEK")
			: (rudder < 0
				? Component.literal(String.format("Ster: LEWO %.0f°", -rudder))
				: Component.literal(String.format("Ster: PRAWO %.0f°", rudder)));

		Component line1 = Component.empty()
			.append(driveMode)
			.append(Component.literal("   |   "))
			.append(rudderComp)
			.append(Component.literal(String.format("   |   %.1f km/h", speedKmH)));

		Component line2 = Component.literal("[W/S] Gaz   [A/D] Ster   [Z] Włącz/Wyłącz   [Shift] Zejdź ze steru");

		extractor.centeredText(font, line1, hudX, hudY + 4, driveColor);
		extractor.centeredText(font, line2, hudX, hudY + 16, 0xFFDDDDDD);
	}
}
