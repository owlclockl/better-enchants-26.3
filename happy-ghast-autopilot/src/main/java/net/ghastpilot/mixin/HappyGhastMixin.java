package net.ghastpilot.mixin;

import net.ghastpilot.client.AutopilotController;
import net.ghastpilot.flight.SteeringCommand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Steers the local player's harnessed Happy Ghast when autopilot is engaged.
 *
 * <p>Uses the exact vanilla Happy Ghast flying-speed formula so movement remains 100% vanilla-legal
 * in both singleplayer and multiplayer (including unmodified vanilla servers), while allowing the
 * rider to freely look around, chat, check inventory, or browse Xaero's World Map mid-flight.
 */
@Mixin(HappyGhast.class)
public abstract class HappyGhastMixin extends Animal {

	protected HappyGhastMixin(EntityType<? extends Animal> entityType, Level level) {
		super(entityType, level);
	}

	@Inject(method = "tickRidden", at = @At("HEAD"), cancellable = true)
	private void ghastpilot$onTickRidden(Player player, Vec3 travelVector, CallbackInfo ci) {
		if (!this.level().isClientSide()) {
			return;
		}
		SteeringCommand cmd = AutopilotController.getActiveCommandFor((HappyGhast) (Object) this, player);
		if (cmd == null) {
			return;
		}
		super.tickRidden(player, travelVector);
		this.setRot(cmd.yaw(), cmd.pitch() * 0.5f);
		this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
		ci.cancel();
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
