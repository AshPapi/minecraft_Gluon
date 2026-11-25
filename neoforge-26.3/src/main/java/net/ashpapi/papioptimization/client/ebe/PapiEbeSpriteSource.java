package net.ashpapi.papioptimization.client.ebe;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PapiEbeSpriteSource implements SpriteSource {

    public static final PapiEbeSpriteSource INSTANCE = new PapiEbeSpriteSource();
    private static final MapCodec<PapiEbeSpriteSource> CODEC = MapCodec.unit(INSTANCE);

    private static final String[] DIRECTORIES = {
        "entity/chest",
        "entity/shulker",
        "entity/decorated_pot"
    };

    private static final Identifier[] SINGLE_TEXTURES = {
        Identifier.withDefaultNamespace("entity/skeleton/skeleton"),
        Identifier.withDefaultNamespace("entity/skeleton/wither_skeleton"),
        Identifier.withDefaultNamespace("entity/zombie/zombie"),
        Identifier.withDefaultNamespace("entity/creeper/creeper"),
        Identifier.withDefaultNamespace("entity/piglin/piglin"),
        Identifier.withDefaultNamespace("entity/enderdragon/dragon"),
        Identifier.withDefaultNamespace("entity/banner/banner_base"),
        Identifier.withDefaultNamespace("entity/banner/base")
    };

    private PapiEbeSpriteSource() {}

    public static Identifier toGluonSpriteId(Identifier vanillaSpriteId) {
        return Identifier.fromNamespaceAndPath(
                "gluon",
                "ebe/" + vanillaSpriteId.getNamespace() + "/" + vanillaSpriteId.getPath()
        );
    }

    @Override
    public void run(ResourceManager resourceManager, Output output) {
        for (String dir : DIRECTORIES) {
            FileToIdConverter converter = new FileToIdConverter("textures/" + dir, ".png");
            for (Map.Entry<Identifier, Resource> entry : converter.listMatchingResources(resourceManager).entrySet()) {
                Identifier fileId = entry.getKey();
                Identifier relId = converter.fileToId(fileId);
                if ("shulker".equals(dir.substring("entity/".length())) && "spark".equals(relId.getPath())) {
                    continue;
                }
                Identifier vanillaSpriteId = Identifier.fromNamespaceAndPath(relId.getNamespace(), dir + "/" + relId.getPath());
                output.add(toGluonSpriteId(vanillaSpriteId), entry.getValue());
            }
        }
        for (Identifier single : SINGLE_TEXTURES) {
            Identifier resourceLoc = TEXTURE_ID_CONVERTER.idToFile(single);
            Optional<Resource> res = resourceManager.getResource(resourceLoc);
            res.ifPresent(resource -> output.add(toGluonSpriteId(single), resource));
        }
    }

    @Override
    public MapCodec<? extends SpriteSource> codec() {
        return CODEC;
    }
}
