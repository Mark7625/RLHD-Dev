package rs117.hd.gui;

public enum DockType
{
	SIDEBAR,
	WINDOW,
	/**
	 * Docked below the client canvas. Not a RuneLite-supported layout slot - installed via
	 * reflection into RuneLite's internal client panel, so it may break across RuneLite versions.
	 */
	BOTTOM
}
