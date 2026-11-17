package net.enchantoutline.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.enchantoutline.events.EquipmentRendererQueueEnchantedCallback;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.texture.UvMapping;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EquipmentLayerRenderer.class)
public class EquipmentRendererMixin {
    /*
     * 26.3 bakes the armour glint into the layer render type itself: a foiled, untrimmed layer is
     * submitted with RenderTypes#armorCutoutNoCullGlint(texture) instead of a plain layer followed by a
     * separate glint submit. A trimmed piece keeps the plain layer type and only gets a trailing
     * trimmedArmorGlint() submit, which carries no layer texture at all.
     *
     * So hook the first submitModel of the method - always the armour layer itself, with the real
     * layer texture as Sampler0 - and decide "enchanted" from the render type *or* the stack's foil
     * flag, which also covers trimmed armour.
     */
    @WrapOperation(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V", ordinal = 0))
    <S> void enchantOutline$renderArmorEnchanted(OrderedSubmitNodeCollector instance, Model<? super S> model, S s, PoseStack poseStack, RenderType renderType, int light, int overlay, int tintColor, @Nullable UvMapping uvMapping, int outlineColor, Operation<Void> original, @Local(argsOnly = true) SubmitNodeCollector submitNodeCollector, @Local(argsOnly = true) ItemStack itemStack){
        InteractionResult result = EquipmentRendererQueueEnchantedCallback.EVENT.invoker().onQueue(submitNodeCollector, itemStack, instance, model, s, poseStack, renderType, light, overlay, tintColor, uvMapping, outlineColor);

        if(result != InteractionResult.FAIL){
            original.call(instance, model, s, poseStack, renderType, light, overlay, tintColor, uvMapping, outlineColor);
        }
    }
}
