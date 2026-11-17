package net.ghastpilot.mixin.xaero;

import net.ghastpilot.HappyGhastAutopilotClient;
import net.ghastpilot.compat.xaero.XaeroMapIntegration;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.gui.GuiMap;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

import java.util.ArrayList;

/**
 * Non-invasive integration with Xaero's World Map:
 * <ol>
 *   <li>Adds right-click options ("Fly Ghast here", "Fly & land", "Clear target") to {@link GuiMap#getRightClickOptions()}.</li>
 *   <li>Renders the flight route line and target pin inside {@link GuiMap#renderPreDropdown}.</li>
 * </ol>
 * Uses reflective field lookup inside {@link XaeroMapIntegration} so field changes in future Xaero builds
 * degrade gracefully instead of crashing the client.
 */
@Mixin(value = GuiMap.class, remap = false)
public abstract class GuiMapMixin {

	@Unique
	private static boolean ghastpilot$loggedMenuError;

	@Unique
	private static boolean ghastpilot$loggedOverlayError;

	@Inject(method = "getRightClickOptions", at = @At("RETURN"), remap = false)
	private void ghastpilot$appendRightClickOptions(CallbackInfoReturnable<ArrayList<RightClickOption>> cir) {
		try {
			ArrayList<RightClickOption> options = cir.getReturnValue();
			if (options != null) {
				XaeroMapIntegration.appendMapOptions((GuiMap) (Object) this, options);
			}
		} catch (Throwable t) {
			if (!ghastpilot$loggedMenuError) {
				ghastpilot$loggedMenuError = true;
				HappyGhastAutopilotClient.LOGGER.warn("Failed to append Happy Ghast options to Xaero World Map menu", t);
			}
		}
	}

	@Inject(method = "renderPreDropdown", at = @At("HEAD"), remap = false)
	private void ghastpilot$renderRouteOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		try {
			XaeroMapIntegration.renderRouteOverlay((GuiMap) (Object) this, graphics);
		} catch (Throwable t) {
			if (!ghastpilot$loggedOverlayError) {
				ghastpilot$loggedOverlayError = true;
				HappyGhastAutopilotClient.LOGGER.warn("Failed to render Happy Ghast route overlay on Xaero World Map", t);
			}
		}
	}
}
