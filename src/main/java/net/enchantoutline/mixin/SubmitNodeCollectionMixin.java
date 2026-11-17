package net.enchantoutline.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.enchantoutline.events.RenderModelGlintCallback;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.world.InteractionResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hook on the real {@code submitModel} implementation of {@link SubmitNodeCollection}.
 * <p>
 * 26.3 changed the signature once more: the {@code TextureAtlasSprite} argument became a
 * {@link UvMapping} and the crumbling overlay was split off into its own {@code submitCrumblingOverlay}
 * method. Glint is still expressed as "this model is submitted with a glint render type", which the mod
 * detects in {@link net.enchantoutline.util.RenderLayerHelper#isGlintRenderType(RenderType)}.
 */
@Mixin(SubmitNodeCollection.class)
public class SubmitNodeCollectionMixin {

    @Inject(
            method = "submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    <S> void enchantOutline$submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType,
                                        int lightCoords, int overlayCoords, int tintedColor,
                                        @Nullable UvMapping uvMapping, int outlineColor,
                                        CallbackInfo ci) {
        InteractionResult result = RenderModelGlintCallback.EVENT.invoker().onSubmit(
                (SubmitNodeCollection) (Object) this, model, state, poseStack, renderType,
                lightCoords, overlayCoords, tintedColor, uvMapping, outlineColor);

        if (result == InteractionResult.FAIL) {
            ci.cancel();
        }
    }
}
