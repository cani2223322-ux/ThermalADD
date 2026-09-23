package net.thermaladd.mod.nei;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;

import codechicken.nei.api.IConfigureNEI;
import codechicken.nei.recipe.TemplateRecipeHandler.RecipeTransferRect;
import codechicken.nei.recipe.TemplateRecipeHandler.RecipeTransferRectHandler;
import net.thermaladd.mod.ThermalADD;
import net.thermaladd.mod.client.gui.GuiAdvancedFurnace;
import net.thermaladd.mod.client.gui.GuiAdvancedPulverizer;
import net.thermaladd.mod.client.gui.GuiAdvancedSawmill;
import net.thermaladd.mod.client.gui.GuiImprovedAssembler;
import net.thermaladd.mod.client.gui.GuiSingularCrucible;
import net.thermaladd.mod.client.gui.GuiSingularSmelter;
import net.thermaladd.mod.client.gui.GuiSingularTransposer;

/**
 * NEI integration: clicking a machine's progress arrow opens that machine's recipes, exactly like
 * real Thermal Expansion's own GUIs do with NEI installed.
 *
 * No recipe handlers of our own are needed. These machines run on TE's own recipe managers, and
 * TE already ships an NEI handler for each (cofh.thermalexpansion.plugins.nei.handlers.*); a
 * RecipeTransferRect only has to name the handler's overlay identifier - "thermalexpansion." plus
 * the machine name, as RecipeHandlerBase#getOverlayIdentifier builds it - for NEI to open TE's
 * own recipe pages. The Assembler points at NEI's "crafting", the vanilla crafting-table recipes
 * it actually runs.
 *
 * NEI discovers this class by itself (any class named NEI*Config implementing IConfigureNEI) and
 * only when NEI is installed. Nothing else in the mod references it, so without NEI it is simply
 * never loaded - the same arrangement the Waila plugin relies on.
 */
public class NEIThermalADDConfig implements IConfigureNEI {

    @Override
    public void loadConfig() {
        register(GuiAdvancedPulverizer.class, "thermalexpansion.pulverizer", GuiAdvancedPulverizer.recipeAreas());
        register(GuiAdvancedFurnace.class, "thermalexpansion.furnace", GuiAdvancedFurnace.recipeAreas());
        register(GuiAdvancedSawmill.class, "thermalexpansion.sawmill", GuiAdvancedSawmill.recipeAreas());
        register(GuiImprovedAssembler.class, "crafting", GuiImprovedAssembler.recipeAreas());
        register(GuiSingularSmelter.class, "thermalexpansion.smelter", GuiSingularSmelter.recipeAreas());
        register(GuiSingularCrucible.class, "thermalexpansion.crucible", GuiSingularCrucible.recipeAreas());
        register(GuiSingularTransposer.class, "thermalexpansion.transposer", GuiSingularTransposer.recipeAreas());
    }

    private static void register(Class<? extends GuiContainer> gui, String outputId, Rectangle[] areas) {
        LinkedList<RecipeTransferRect> rects = new LinkedList<RecipeTransferRect>();
        for (int i = 0; i < areas.length; i++) {
            rects.add(new RecipeTransferRect(areas[i], outputId));
        }
        List<Class<? extends GuiContainer>> guis = new ArrayList<Class<? extends GuiContainer>>();
        guis.add(gui);
        RecipeTransferRectHandler.registerRectsToGuis(guis, rects);
    }

    @Override
    public String getName() {
        return ThermalADD.NAME;
    }

    @Override
    public String getVersion() {
        return ThermalADD.VERSION;
    }
}
