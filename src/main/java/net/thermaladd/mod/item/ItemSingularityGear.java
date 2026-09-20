package net.thermaladd.mod.item;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.Item;
import net.minecraft.util.IIcon;

import net.thermaladd.mod.ThermalADD;
import net.thermaladd.mod.init.ModCreativeTab;

/**
 * Pure crafting material - the mod-original counterpart to real Thermal Expansion's own
 * {@code TFItems.gearEnderium} (ThermalFoundation), used as the base ingredient for its own
 * recipe and consumed into the Singularity Machine Frame's recipe. Same violet-magenta-gold-
 * cyan "Singularity" gradient treatment as the other Singularity-branded items in this mod,
 * over the real GearEnderium.png shape.
 */
public class ItemSingularityGear extends Item {

    private IIcon icon;

    public ItemSingularityGear() {
        setUnlocalizedName("singularityGear");
        setCreativeTab(ModCreativeTab.TAB);
        setMaxStackSize(64);
    }

    @Override
    public void registerIcons(IIconRegister register) {
        icon = register.registerIcon(ThermalADD.MODID + ":singularityGear");
    }

    @Override
    public IIcon getIconFromDamage(int meta) {
        return icon;
    }
}
