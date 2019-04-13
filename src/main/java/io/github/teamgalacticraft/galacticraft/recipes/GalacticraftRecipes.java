package io.github.teamgalacticraft.galacticraft.recipes;

import io.github.teamgalacticraft.galacticraft.Constants;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

/**
 * @author <a href="https://github.com/teamgalacticraft">TeamGalacticraft</a>
 */
public class GalacticraftRecipes {
    public static RecipeType<FabricationRecipe> FABRICATION_TYPE;
    public static RecipeType<CompressingRecipe> COMPRESSING_TYPE;

    static FabricationRecipeSerializer<FabricationRecipe> FABRICATION_SERIALIZER;
    static CompressingRecipeSerializer<CompressingRecipe> COMPRESSING_SERIALIZER;

    public static void register() {
        // Circuit fabricator recipe stuff
        FABRICATION_TYPE = registerType("circuit_fabricator");
        FABRICATION_SERIALIZER = registerSerializer("circuit_fabricator", new FabricationRecipeSerializer<>(FabricationRecipe::new));

        COMPRESSING_TYPE = registerType("compressing");
        COMPRESSING_SERIALIZER = registerSerializer("compressing", new CompressingRecipeSerializer<>(CompressingRecipe::new));
    }

    private static <T extends Recipe<?>> RecipeType<T> registerType(String id) {
        return Registry.register(Registry.RECIPE_TYPE, new Identifier(Constants.MOD_ID, id), new RecipeType<T>() {
            public String toString() {
                return id;
            }
        });
    }

    private static <S extends RecipeSerializer<T>, T extends Recipe<?>> S registerSerializer(String id, S serializer) {
        return Registry.register(Registry.RECIPE_SERIALIZER, new Identifier(Constants.MOD_ID, id), serializer);
    }
}