package net.ghastpilot.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.ghastpilot.HappyGhastAutopilotClient;
import net.ghastpilot.flight.FlightSettings;
import net.ghastpilot.flight.FlightTarget;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class AutopilotConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int MAX_RECENT_TARGETS = 5;
	private static AutopilotConfig instance = new AutopilotConfig();

	/** Allow the player to freely move their camera while the ghast steers itself. */
	public boolean freeLook = true;

	/** When false, holds a smart cruise altitude safely above terrain; when true, cruises at fixedCruiseAltitude. */
	public boolean useFixedCruiseAltitude = false;

	/** Target cruise Y when useFixedCruiseAltitude is enabled. */
	public int fixedCruiseAltitude = 130;

	/** Minimum clearance in blocks above terrain/trees in auto cruise mode. */
	public int terrainClearance = 12;

	/** Gently descend/ascend to target Y upon reaching (X, Z) when target has an explicit Y coordinate. */
	public boolean descendOnArrival = true;

	/** Horizontal arrival tolerance in blocks. */
	public double arrivalRadius = 2.5;

	/** Automatically pause autopilot if the player presses manual WASD movement keys. */
	public boolean pauseOnManualInput = true;

	/** Render compact status/ETA HUD card on screen. */
	public boolean showHud = true;

	/** Draw destination pin and route line on Xaero's World Map. */
	public boolean showXaeroOverlay = true;

	/** Close Xaero's World Map and start flying immediately when selecting "Fly here" while riding a Happy Ghast. */
	public boolean autoStartFromMap = true;

	/** Play a gentle chime upon reaching the destination. */
	public boolean playArrivalSound = true;

	/** Last few targets picked via map, GUI, or command. */
	public List<RecentEntry> recentTargets = new ArrayList<>();

	public static final class RecentEntry {
		public int x;
		public Integer y;
		public int z;
		public String dimension = FlightTarget.DEFAULT_DIMENSION;
		public String label;

		public RecentEntry() {
		}

		public RecentEntry(FlightTarget target) {
			this.x = target.x();
			this.y = target.y();
			this.z = target.z();
			this.dimension = target.dimension();
			this.label = target.label();
		}

		public FlightTarget toTarget() {
			return new FlightTarget(x, y, z, dimension, label);
		}
	}

	public static AutopilotConfig get() {
		return instance;
	}

	public FlightSettings toFlightSettings(double worldMinY, double worldMaxY) {
		return new FlightSettings(
				useFixedCruiseAltitude,
				fixedCruiseAltitude,
				terrainClearance,
				descendOnArrival,
				arrivalRadius,
				worldMinY,
				worldMaxY
		);
	}

	public void recordRecent(FlightTarget target) {
		if (target == null) {
			return;
		}
		if (recentTargets == null) {
			recentTargets = new ArrayList<>();
		}
		recentTargets.removeIf(entry -> entry != null && target.sameCoordinatesAndDimension(entry.toTarget()));
		recentTargets.addFirst(new RecentEntry(target));
		while (recentTargets.size() > MAX_RECENT_TARGETS) {
			recentTargets.removeLast();
		}
		save();
	}

	public List<FlightTarget> getRecentFlightTargets() {
		if (recentTargets == null || recentTargets.isEmpty()) {
			return List.of();
		}
		List<FlightTarget> out = new ArrayList<>(recentTargets.size());
		for (RecentEntry e : recentTargets) {
			if (e != null) {
				out.add(e.toTarget());
			}
		}
		return out;
	}

	private void sanitize() {
		fixedCruiseAltitude = Math.clamp(fixedCruiseAltitude, -60, 316);
		terrainClearance = Math.clamp(terrainClearance, 4, 64);
		if (!Double.isFinite(arrivalRadius)) {
			arrivalRadius = 2.5;
		}
		arrivalRadius = Math.clamp(arrivalRadius, 1.5, 12.0);
		if (recentTargets == null) {
			recentTargets = new ArrayList<>();
		}
		while (recentTargets.size() > MAX_RECENT_TARGETS) {
			recentTargets.removeLast();
		}
	}

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("happy-ghast-autopilot.json");
	}

	public static void load() {
		Path path = configPath();
		if (!Files.isRegularFile(path)) {
			instance = new AutopilotConfig();
			save();
			return;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			AutopilotConfig loaded = GSON.fromJson(reader, AutopilotConfig.class);
			if (loaded != null) {
				loaded.sanitize();
				instance = loaded;
			}
		} catch (Exception e) {
			HappyGhastAutopilotClient.LOGGER.warn("Failed to load config, using defaults", e);
			instance = new AutopilotConfig();
		}
	}

	public static void save() {
		instance.sanitize();
		Path path = configPath();
		try {
			Files.createDirectories(path.getParent());
			Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (Exception e) {
			try {
				try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
					GSON.toJson(instance, writer);
				}
			} catch (Exception inner) {
				HappyGhastAutopilotClient.LOGGER.warn("Failed to save config", inner);
			}
		}
	}
}
