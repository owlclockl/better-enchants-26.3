package net.enchantoutline.events;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.world.InteractionResult;
import org.jetbrains.annotations.Nullable;

/**
 * Hook fired from {@code SubmitNodeCollection#submitModel}, i.e. for every model submit that reaches the
 * submit node storage (special item models, armour layers, thrown tridents, ...).
 * <p>
 * 26.3 replaced the {@code TextureAtlasSprite} argument of {@code submitModel} with a {@link UvMapping}
 * (and moved the crumbling overlay to {@code submitCrumblingOverlay}), so the hook carries the UV mapping
 * instead of the sprite. The texture to outline is recovered from the render type itself - the glint render
 * types of 26.3 always bind the model texture as {@code Sampler0}.
 */
public interface RenderModelGlintCallback {
    Event<RenderModelGlintCallback> EVENT = EventFactory.createArrayBacked(RenderModelGlintCallback.class,
            (listeners) -> (receiver, model, state, poseStack, renderType, light, overlay, tintedColor, uvMapping, outlineColor) -> {
                for (RenderModelGlintCallback listener : listeners) {
                    InteractionResult result = listener.onSubmit(receiver, model, state, poseStack, renderType, light, overlay, tintedColor, uvMapping, outlineColor);

                    if (result != InteractionResult.PASS) {
                        return result;
                    }
                }

                return InteractionResult.PASS;
            });

    // Deliberately non-generic: a generic abstract method would stop this from being a functional
    // interface, and callers only ever pass the state straight back through to submitModel.
    InteractionResult onSubmit(SubmitNodeCollection receiver, Model<?> model, Object state, PoseStack poseStack,
                               RenderType renderType, int light, int overlay, int tintedColor,
                               @Nullable UvMapping uvMapping, int outlineColor);
}
