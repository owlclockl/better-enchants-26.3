package net.ghastpilot.mixin.xaero;

import net.ghastpilot.HappyGhastAutopilotClient;
import net.ghastpilot.compat.xaero.XaeroMapIntegration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.dropdown.rightclick.RightClickOption;
import xaero.map.mods.gui.Waypoint;
import xaero.map.mods.gui.WaypointReader;

import java.util.ArrayList;

/**
 * Adds a "Fly Ghast to waypoint" entry when right-clicking any waypoint icon on Xaero's World Map.
 */
@Mixin(value = WaypointReader.class, remap = false)
public abstract class WaypointReaderMixin {

	@Unique
	private static boolean ghastpilot$loggedWaypointError;

	@Inject(
			method = "getRightClickOptions(Lxaero/map/mods/gui/Waypoint;Lxaero/map/gui/IRightClickableElement;)Ljava/util/ArrayList;",
			at = @At("RETURN"),
			remap = false
	)
	private void ghastpilot$appendWaypointOptions(
			Waypoint element,
			IRightClickableElement target,
			CallbackInfoReturnable<ArrayList<RightClickOption>> cir
	) {
		try {
			ArrayList<RightClickOption> options = cir.getReturnValue();
			if (element != null && options != null) {
				XaeroMapIntegration.appendWaypointOptions(element, target, options);
			}
		} catch (Throwable t) {
			if (!ghastpilot$loggedWaypointError) {
				ghastpilot$loggedWaypointError = true;
				HappyGhastAutopilotClient.LOGGER.warn("Failed to append Happy Ghast option to Xaero waypoint menu", t);
			}
		}
	}
}
