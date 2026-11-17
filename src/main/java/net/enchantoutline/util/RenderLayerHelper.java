package net.enchantoutline.util;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.enchantoutline.mixin.RenderSetupAccessor;
import net.enchantoutline.mixin_accessors.RenderLayerAccessor;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Function;

public class RenderLayerHelper {
    public static final Logger LOGGER = LoggerFactory.getLogger(RenderLayerHelper.class);

    /** 26.3: the texture name vanilla uses for the glint overlay sampler. */
    public static final String GLINT_SAMPLER = "GlintSampler";

    public static boolean isRenderLayerDoubleSided(RenderType renderLayer){
        RenderSetup setup = ((RenderLayerAccessor)renderLayer).enchantOutline$getRenderSetup();
        if(setup != null){
            RenderPipeline pipeline = ((RenderSetupAccessor)(Object)setup).enchantOutline$getPipeline();
            if(pipeline != null){
                return !pipeline.isCull();
            }
        }
        return false;
    }

    /**
     * 26.3 no longer has the singleton glint render types ({@code RenderTypes#glint()} and friends are
     * gone - glint is baked into per-texture render types such as
     * {@code RenderTypes#itemCutoutGlint(Identifier)}). Vanilla still tells us a model is enchanted by
     * submitting it with one of those, so instead of comparing against singletons we look at the
     * pipeline: every glint pipeline is built from {@code RenderPipelines.GLINT_SNIPPET}, which adds the
     * {@code GLINT} shader define. The extra GlintSampler texture binding is checked as well so that
     * hand built glint setups that only bind the sampler still count.
     */
    public static boolean isGlintRenderType(@Nullable RenderType renderType){
        if(renderType == null){
            return false;
        }
        RenderSetup setup = ((RenderLayerAccessor)renderType).enchantOutline$getRenderSetup();
        if(setup == null){
            return false;
        }
        RenderPipeline pipeline = ((RenderSetupAccessor)(Object)setup).enchantOutline$getPipeline();
        if(pipeline != null && pipeline.getShaderDefines().flags().contains("GLINT")){
            return true;
        }
        Map<String, RenderSetup.TextureBinding> textures = ((RenderSetupAccessor)(Object)setup).enchantOutline$getTextures();
        return textures != null && textures.containsKey(GLINT_SAMPLER);
    }

    @Nullable
    public static Map<String, Identifier> getIdentifierFromSprite(TextureAtlasSprite sprite){
        if(sprite != null){
            Identifier atlas = sprite.atlasLocation();
            if(atlas != null){
                return Map.of(atlas.getPath(), atlas);
            }
        }
        return null;
    }

    @Nullable
    public static Map<String, Identifier> getIdentifierFromRenderLayer(RenderType layer) {
        if (layer != null) {
            RenderSetup setup = ((RenderLayerAccessor) ((Object) layer)).enchantOutline$getRenderSetup();
            if (setup != null) {
                Map<String, RenderSetup.TextureBinding> map = ((RenderSetupAccessor) ((Object) setup)).enchantOutline$getTextures();
                if (map == null || map.isEmpty()) {
                    return null;
                }
                //26.3: glint render types carry two textures (Sampler0 = the model/item texture,
                //GlintSampler = the glint overlay). The outline must follow Sampler0, never whatever
                //happens to be first in the (unordered) map.
                RenderSetup.TextureBinding binding = map.get("Sampler0");
                if (binding == null) {
                    for (RenderSetup.TextureBinding candidate : map.values()) {
                        if (candidate.location() != null) {
                            binding = candidate;
                            break;
                        }
                    }
                }
                if (binding != null && binding.location() != null) {
                    Identifier id = binding.location();
                    return Map.of(id.getPath(), id);
                }
            }
        }
        return null;
    }
    public static RenderType renderLayerFromRenderLayerDoubleSided(RenderType renderLayer, CustomRenderLayers customRenderLayers, Function<Map<String, Identifier>, RenderType> doubleSidedFactory, Function<Map<String, Identifier>, RenderType> singleSidedFactory, RenderType fallback, boolean isDoubleSided){
        return renderLayerFromMapDoubleSided(getIdentifierFromRenderLayer(renderLayer), customRenderLayers, doubleSidedFactory, singleSidedFactory, fallback, isDoubleSided);
    }

    public static RenderType renderLayerFromSpriteDoubleSided(TextureAtlasSprite sprite, CustomRenderLayers customRenderLayers, Function<Map<String, Identifier>, RenderType> doubleSidedFactory, Function<Map<String, Identifier>, RenderType> singleSidedFactory, RenderType fallback, boolean isDoubleSided){
        return renderLayerFromMapDoubleSided(getIdentifierFromSprite(sprite), customRenderLayers, doubleSidedFactory, singleSidedFactory, fallback, isDoubleSided);
    }

    public static RenderType renderLayerFromMapDoubleSided(@Nullable Map<String, Identifier> identifier, CustomRenderLayers customRenderLayers, Function<Map<String, Identifier>, RenderType> doubleSidedFactory, Function<Map<String, Identifier>, RenderType> singleSidedFactory, RenderType fallback, boolean isDoubleSided){
        String first;
        if(identifier != null){
            first = identifier.entrySet().stream().findFirst().map(Map.Entry::getKey).orElse(null);
        }else{
            first = null;
        }

        if(isDoubleSided){
            return renderLayerFromMapWithFallback(identifier, (id) -> getOrCreateRenderLayerMap(customRenderLayers, doubleSidedFactory, identifier, first + "_db"), fallback);
        }
        return renderLayerFromMapWithFallback(identifier, (id) -> getOrCreateRenderLayerMap(customRenderLayers, singleSidedFactory, identifier, first), fallback);
    }

    public static RenderType renderLayerFromMapWithFallback(@Nullable Map<String, Identifier> identifier, Function<Map<String, Identifier>, RenderType> layerFactory, RenderType fallback){
        if(identifier != null){
            RenderType newLayer = layerFactory.apply(identifier);
            if(newLayer != null){
                return newLayer;
            }
        }
        return fallback;
    }

    @Nullable
    public static RenderType getOrCreateRenderLayerMap(CustomRenderLayers customRenderLayers, Function<Map<String, Identifier>, RenderType> layerFactory, Map<String, Identifier> identifier, String storagePath){
        RenderType output = customRenderLayers.getCustomRenderLayer(storagePath);
        if(output != null)
        {
            return output;
        }
        return customRenderLayers.addCustomRenderLayer(storagePath, layerFactory.apply(identifier));
    }

    @Nullable
    public static RenderType getOrCreateRenderLayer(CustomRenderLayers customRenderLayers, Function<Identifier, RenderType> layerFactory, Identifier identifier, String storagePath){
        RenderType output = customRenderLayers.getCustomRenderLayer(storagePath);
        if(output != null)
        {
            return output;
        }
        return customRenderLayers.addCustomRenderLayer(storagePath, layerFactory.apply(identifier));
    }

    @Nullable
    public static RenderType getOrCreateRenderLayer(CustomRenderLayers customRenderLayers, Function<Identifier, RenderType> layerCreationFunction, Identifier identifier){
        return getOrCreateRenderLayer(customRenderLayers, layerCreationFunction, identifier, identifier.toString());
    }
}
