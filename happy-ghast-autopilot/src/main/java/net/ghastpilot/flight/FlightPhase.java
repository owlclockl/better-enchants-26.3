package net.ghastpilot.flight;

public enum FlightPhase {
	IDLE("happy_ghast_autopilot.phase.idle", 0xFFAAAAAA, false),
	PAUSED("happy_ghast_autopilot.phase.paused", 0xFFBBBBBB, false),
	CRUISING("happy_ghast_autopilot.phase.cruising", 0xFF55FF88, true),
	CLIMBING("happy_ghast_autopilot.phase.climbing", 0xFF55DDFF, true),
	AVOIDING("happy_ghast_autopilot.phase.avoiding", 0xFFFFCC44, true),
	APPROACHING("happy_ghast_autopilot.phase.approaching", 0xFF88FFAA, true),
	DESCENDING("happy_ghast_autopilot.phase.descending", 0xFF66EEFF, true),
	WAITING_CHUNKS("happy_ghast_autopilot.phase.waiting_chunks", 0xFFFFAA44, true),
	WRONG_DIMENSION("happy_ghast_autopilot.phase.wrong_dimension", 0xFFFF6666, false);

	private final String translationKey;
	private final int color;
	private final boolean active;

	FlightPhase(String translationKey, int color, boolean active) {
		this.translationKey = translationKey;
		this.color = color;
		this.active = active;
	}

	public String translationKey() {
		return translationKey;
	}

	public int color() {
		return color;
	}

	public boolean isActive() {
		return active;
	}
}
