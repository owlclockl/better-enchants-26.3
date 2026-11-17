package net.ghastpilot.mixin;

import net.ghastpilot.client.AutopilotController;
import net.ghastpilot.flight.SteeringCommand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Steers the local player's harnessed Happy Ghast when autopilot is engaged.
 *
 * <p>Hooks {@code getRiddenRotation} and {@code getRiddenInput} using the exact vanilla Happy Ghast
 * speed formula ({@code 3.9F * Attributes.FLYING_SPEED}), keeping movement 100% vanilla-legal in both
 * singleplayer and multiplayer while letting the player look around freely, chat, open inventory,
 * or browse Xaero's World Map mid-flight.
 */
@Mixin(HappyGhast.class)
public abstract class HappyGhastMixin extends Animal {

	protected HappyGhastMixin(EntityType<? extends Animal> entityType, Level level) {
		super(entityType, level);
	}

	@Inject(method = "getRiddenRotation", at = @At("HEAD"), cancellable = true)
	private void ghastpilot$onGetRiddenRotation(LivingEntity rider, CallbackInfoReturnable<Vec2> cir) {
		if (!this.level().isClientSide() || !(rider instanceof Player player)) {
			return;
		}
		SteeringCommand cmd = AutopilotController.getActiveCommandFor((HappyGhast) (Object) this, player);
		if (cmd == null) {
			return;
		}
		cir.setReturnValue(new Vec2(cmd.pitch() * 0.5f, cmd.yaw()));
	}

	@Inject(method = "getRiddenInput", at = @At("HEAD"), cancellable = true)
	private void ghastpilot$onGetRiddenInput(Player player, Vec3 travelVector, CallbackInfoReturnable<Vec3> cir) {
		if (!this.level().isClientSide()) {
			return;
		}
		SteeringCommand cmd = AutopilotController.getActiveCommandFor((HappyGhast) (Object) this, player);
		if (cmd == null) {
			return;
		}
		double[] local = cmd.computeLocalInputComponents();
		double speedScale = 3.9f * this.getAttributeValue(Attributes.FLYING_SPEED);
		cir.setReturnValue(new Vec3(local[0], local[1], local[2]).scale(speedScale));
	}
}
