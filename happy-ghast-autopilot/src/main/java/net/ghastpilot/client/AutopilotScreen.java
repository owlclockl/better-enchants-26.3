package net.ghastpilot.client;

import net.ghastpilot.flight.CoordinateParser;
import net.ghastpilot.flight.FlightTarget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Optional;

/**
 * Clean in-game configuration and destination screen for Happy Ghast Autopilot.
 * Accessible via [H] hotkey, {@code /gp}, or Mod Menu.
 */
public final class AutopilotScreen extends Screen {

	private static final int PANEL_W = 280;
	private static final int BTN_H = 20;

	private final Screen parent;

	private EditBox xBox;
	private EditBox yBox;
	private EditBox zBox;

	private Button toggleBtn;
	private Button freeLookBtn;
	private Button altModeBtn;
	private Button descendBtn;
	private Button manualPauseBtn;
	private Button hudBtn;
	private Button mapOverlayBtn;

	private Component feedbackMessage;
	private int feedbackColor = 0xFFFF7777;
	private int panelTop;

	public AutopilotScreen(Screen parent) {
		super(Component.translatable("happy_ghast_autopilot.screen.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = (this.width - PANEL_W) / 2;
		this.panelTop = Math.max(28, (this.height - 230) / 2);

		String prevX = xBox != null ? xBox.getValue() : null;
		String prevY = yBox != null ? yBox.getValue() : null;
		String prevZ = zBox != null ? zBox.getValue() : null;

		FlightTarget current = AutopilotController.getTarget();
		if (prevX == null && current != null) {
			prevX = Integer.toString(current.x());
			prevY = current.hasY() ? Integer.toString(current.y()) : "";
			prevZ = Integer.toString(current.z());
		}

		// Row 1: Coordinate input boxes (X, Y optional, Z)
		int coordY = panelTop + 22;
		int boxW = 88;
		int gap = 8;
		xBox = createCoordBox(left, coordY, boxW, "happy_ghast_autopilot.screen.x", prevX);
		yBox = createCoordBox(left + boxW + gap, coordY, boxW, "happy_ghast_autopilot.screen.y", prevY);
		zBox = createCoordBox(left + (boxW + gap) * 2, coordY, boxW, "happy_ghast_autopilot.screen.z", prevZ);
		addRenderableWidget(xBox);
		addRenderableWidget(yBox);
		addRenderableWidget(zBox);

		// Row 2: Action buttons (Fly! | Pause/Resume | Clear | Paste | Here)
		int actY = coordY + 25;
		addRenderableWidget(Button.builder(Component.translatable("happy_ghast_autopilot.screen.fly"), b -> applyAndFly())
				.bounds(left, actY, 64, BTN_H).build());

		toggleBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
			AutopilotController.toggle();
			refreshLabels();
		}).bounds(left + 68, actY, 68, BTN_H).build());

		addRenderableWidget(Button.builder(Component.translatable("happy_ghast_autopilot.screen.clear"), b -> {
			AutopilotController.clearTarget(true);
			refreshLabels();
		}).bounds(left + 140, actY, 46, BTN_H).build());

		addRenderableWidget(Button.builder(Component.translatable("happy_ghast_autopilot.screen.paste"), b -> pasteFromClipboard())
				.bounds(left + 190, actY, 46, BTN_H).build());

		addRenderableWidget(Button.builder(Component.translatable("happy_ghast_autopilot.screen.here"), b -> fillCurrentCoords())
				.bounds(left + 240, actY, 40, BTN_H).build());

		// Row 3..5: Two-column settings toggles + altitude +/- steppers
		int halfW = (PANEL_W - 6) / 2;
		int col2 = left + halfW + 6;
		int setY1 = actY + 28;
		int setY2 = setY1 + 23;
		int setY3 = setY2 + 23;

		freeLookBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
			AutopilotConfig.get().freeLook = !AutopilotConfig.get().freeLook;
			AutopilotConfig.save();
			refreshLabels();
		}).bounds(left, setY1, halfW, BTN_H).build());

		// Altitude button + [-][+] compact steppers
		int stepW = 18;
		int altMainW = halfW - stepW * 2 - 4;
		altModeBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
			AutopilotConfig cfg = AutopilotConfig.get();
			cfg.useFixedCruiseAltitude = !cfg.useFixedCruiseAltitude;
			if (cfg.useFixedCruiseAltitude && this.minecraft != null && this.minecraft.player != null) {
				cfg.fixedCruiseAltitude = Math.clamp(Mth.floor(this.minecraft.player.getY()), -56, 312);
			}
			AutopilotConfig.save();
			refreshLabels();
		}).bounds(col2, setY1, altMainW, BTN_H).build());

		addRenderableWidget(Button.builder(Component.literal("-"), b -> adjustAltitude(-1))
				.bounds(col2 + altMainW + 2, setY1, stepW, BTN_H).build());
		addRenderableWidget(Button.builder(Component.literal("+"), b -> adjustAltitude(1))
				.bounds(col2 + altMainW + stepW + 4, setY1, stepW, BTN_H).build());

		descendBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
			AutopilotConfig.get().descendOnArrival = !AutopilotConfig.get().descendOnArrival;
			AutopilotConfig.save();
			refreshLabels();
		}).bounds(left, setY2, halfW, BTN_H).build());

		manualPauseBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
			AutopilotConfig.get().pauseOnManualInput = !AutopilotConfig.get().pauseOnManualInput;
			AutopilotConfig.save();
			refreshLabels();
		}).bounds(col2, setY2, halfW, BTN_H).build());

		hudBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
			AutopilotConfig.get().showHud = !AutopilotConfig.get().showHud;
			AutopilotConfig.save();
			refreshLabels();
		}).bounds(left, setY3, halfW, BTN_H).build());

		mapOverlayBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
			AutopilotConfig.get().showXaeroOverlay = !AutopilotConfig.get().showXaeroOverlay;
			AutopilotConfig.save();
			refreshLabels();
		}).bounds(col2, setY3, halfW, BTN_H).build());

		// Row 6: Quick-pick recent destinations (up to 3 most recent)
		int recentY = setY3 + 34;
		List<FlightTarget> recents = AutopilotConfig.get().getRecentFlightTargets();
		int shown = Math.min(3, recents.size());
		if (shown > 0) {
			int rW = (PANEL_W - (shown - 1) * 4) / shown;
			for (int i = 0; i < shown; i++) {
				FlightTarget rt = recents.get(i);
				String label = rt.label() != null ? rt.label() : (rt.x() + ", " + rt.z());
				if (label.length() > 14) {
					label = label.substring(0, 13) + "…";
				}
				addRenderableWidget(Button.builder(Component.literal(label), b -> {
					xBox.setValue(Integer.toString(rt.x()));
					yBox.setValue(rt.hasY() ? Integer.toString(rt.y()) : "");
					zBox.setValue(Integer.toString(rt.z()));
					AutopilotController.setTarget(rt, true);
					onClose();
				}).bounds(left + i * (rW + 4), recentY, rW, 18).build());
			}
		}

		// Done button
		int doneY = shown > 0 ? recentY + 24 : setY3 + 28;
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(this.width / 2 - 60, doneY, 120, BTN_H).build());

		refreshLabels();
	}

	private EditBox createCoordBox(int x, int y, int w, String placeholderKey, String initialValue) {
		EditBox box = new EditBox(this.font, x, y, w, 18, Component.translatable(placeholderKey));
		box.setMaxLength(16);
		box.setHint(Component.translatable(placeholderKey));
		if (initialValue != null) {
			box.setValue(initialValue);
		}
		return box;
	}

	private void adjustAltitude(int dir) {
		AutopilotConfig cfg = AutopilotConfig.get();
		if (cfg.useFixedCruiseAltitude) {
			cfg.fixedCruiseAltitude = Math.clamp(cfg.fixedCruiseAltitude + dir * 5, -60, 316);
		} else {
			cfg.terrainClearance = Math.clamp(cfg.terrainClearance + dir * 2, 4, 64);
		}
		AutopilotConfig.save();
		refreshLabels();
	}

	private void applyAndFly() {
		Optional<Integer> ox = CoordinateParser.parseSingleInt(xBox.getValue());
		Optional<Integer> oz = CoordinateParser.parseSingleInt(zBox.getValue());
		if (ox.isEmpty() || oz.isEmpty()) {
			// Also support typing a full coordinate string straight into the X box!
			String dim = AutopilotController.getPlayerDimension(this.minecraft);
			Optional<FlightTarget> combined = CoordinateParser.parse(xBox.getValue(), dim);
			if (combined.isPresent()) {
				AutopilotController.setTarget(combined.get(), true);
				onClose();
				return;
			}
			feedbackMessage = Component.translatable("happy_ghast_autopilot.msg.parse_error");
			feedbackColor = 0xFFFF6666;
			return;
		}

		Integer yVal = null;
		if (!yBox.getValue().isBlank()) {
			Optional<Integer> oy = CoordinateParser.parseSingleInt(yBox.getValue());
			if (oy.isPresent()) {
				yVal = Math.clamp(oy.get(), -2048, 4096);
			}
		}

		String dim = AutopilotController.getPlayerDimension(this.minecraft);
		FlightTarget newTarget = new FlightTarget(ox.get(), yVal, oz.get(), dim, null);
		AutopilotController.setTarget(newTarget, true);
		onClose();
	}

	private void pasteFromClipboard() {
		if (this.minecraft == null) {
			return;
		}
		String clip = this.minecraft.keyboardHandler.getClipboard();
		String dim = AutopilotController.getPlayerDimension(this.minecraft);
		Optional<FlightTarget> parsed = CoordinateParser.parse(clip, dim);
		if (parsed.isEmpty()) {
			feedbackMessage = Component.translatable("happy_ghast_autopilot.msg.parse_error");
			feedbackColor = 0xFFFF6666;
			return;
		}
		FlightTarget t = parsed.get();
		xBox.setValue(Integer.toString(t.x()));
		yBox.setValue(t.hasY() ? Integer.toString(t.y()) : "");
		zBox.setValue(Integer.toString(t.z()));
		feedbackMessage = Component.translatable("happy_ghast_autopilot.msg.pasted");
		feedbackColor = 0xFF66FF99;
	}

	private void fillCurrentCoords() {
		if (this.minecraft == null || this.minecraft.player == null) {
			return;
		}
		xBox.setValue(Integer.toString(Mth.floor(this.minecraft.player.getX())));
		zBox.setValue(Integer.toString(Mth.floor(this.minecraft.player.getZ())));
		feedbackMessage = null;
	}

	private void refreshLabels() {
		AutopilotConfig cfg = AutopilotConfig.get();
		boolean active = AutopilotController.isActive();

		toggleBtn.active = AutopilotController.getTarget() != null;
		toggleBtn.setMessage(Component.translatable(
				active ? "happy_ghast_autopilot.screen.pause" : "happy_ghast_autopilot.screen.resume"
		));

		freeLookBtn.setMessage(Component.translatable("happy_ghast_autopilot.screen.freelook", stateText(cfg.freeLook)));

		Component altVal = cfg.useFixedCruiseAltitude
				? Component.translatable("happy_ghast_autopilot.screen.altitude_fixed", cfg.fixedCruiseAltitude)
				: Component.translatable("happy_ghast_autopilot.screen.altitude_auto", cfg.terrainClearance);
		altModeBtn.setMessage(altVal);

		descendBtn.setMessage(Component.translatable("happy_ghast_autopilot.screen.descend", stateText(cfg.descendOnArrival)));
		manualPauseBtn.setMessage(Component.translatable("happy_ghast_autopilot.screen.manual_pause", stateText(cfg.pauseOnManualInput)));
		hudBtn.setMessage(Component.translatable("happy_ghast_autopilot.screen.hud", stateText(cfg.showHud)));
		mapOverlayBtn.setMessage(Component.translatable("happy_ghast_autopilot.screen.map_overlay", stateText(cfg.showXaeroOverlay)));
	}

	private static Component stateText(boolean on) {
		return Component.translatable(on ? "happy_ghast_autopilot.state.on" : "happy_ghast_autopilot.state.off");
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		int cx = this.width / 2;
		int left = (this.width - PANEL_W) / 2;

		graphics.text(this.font, this.title, cx - this.font.width(this.title) / 2, panelTop - 12, 0xFFFFFFFF, true);

		Component subtitle = feedbackMessage != null
				? feedbackMessage
				: Component.translatable("happy_ghast_autopilot.screen.subtitle");
		int subColor = feedbackMessage != null ? feedbackColor : 0xFF7DD3FC;
		graphics.text(this.font, subtitle, cx - this.font.width(subtitle) / 2, panelTop + 2, subColor, true);

		// Coordinate column labels
		int labelY = panelTop + 13;
		graphics.text(this.font, Component.translatable("happy_ghast_autopilot.screen.x"), left + 2, labelY, 0xFFCBD5E1, false);
		graphics.text(this.font, Component.translatable("happy_ghast_autopilot.screen.y"), left + 98, labelY, 0xFFCBD5E1, false);
		graphics.text(this.font, Component.translatable("happy_ghast_autopilot.screen.z"), left + 194, labelY, 0xFFCBD5E1, false);

		if (!AutopilotConfig.get().getRecentFlightTargets().isEmpty()) {
			Component recTitle = Component.translatable("happy_ghast_autopilot.screen.recent");
			graphics.text(this.font, recTitle, left, panelTop + 145, 0xFF94A3B8, false);
		}
	}

	@Override
	public boolean isPauseScreen() {
		// Never pause singleplayer flight when adjusting destination!
		return false;
	}

	@Override
	public void onClose() {
		AutopilotConfig.save();
		if (this.minecraft != null) {
			ScreenCompat.openScreen(this.minecraft, this.parent);
		}
	}
}
