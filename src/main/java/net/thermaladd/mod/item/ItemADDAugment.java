package net.thermaladd.mod.item;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;

import cofh.api.item.IAugmentItem;
import net.thermaladd.mod.ThermalADD;
import net.thermaladd.mod.init.ModCreativeTab;

/**
 * ThermalADD's own augment item - the mod-original counterpart to Thermal Expansion's real
 * {@code cofh.thermalexpansion.item.ItemAugment}: one Item class, one sub-item per metadata
 * value, each mapped to an augment type string + level via {@link #addAugment}. Any augment
 * type string our tiles already recognize (see TileAdvancedPulverizer/TileAdvancedFurnace's
 * AUG_* constants) works here automatically - the tiles only look at
 * {@link IAugmentItem#getAugmentTypes}/{@link IAugmentItem#getAugmentLevel}, they don't care
 * which mod the item came from.
 */
public class ItemADDAugment extends Item implements IAugmentItem {

    private static final class Entry {
        final String type;
        final int level;
        final String nameSuffix;
        final String[] tooltip;

        Entry(String type, int level, String nameSuffix, String[] tooltip) {
            this.type = type;
            this.level = level;
            this.nameSuffix = nameSuffix;
            this.tooltip = tooltip;
        }
    }

    private final Map<Integer, Entry> entries = new HashMap<Integer, Entry>();
    private final Map<Integer, IIcon> icons = new HashMap<Integer, IIcon>();

    public ItemADDAugment() {
        setUnlocalizedName("addAugment");
        setCreativeTab(ModCreativeTab.TAB);
        setHasSubtypes(true);
        setMaxStackSize(64);
    }

    /**
     * Registers one metadata value as an augment: {@code nameSuffix} picks the lang keys
     * ({@code item.thermaladd.addAugment.<suffix>.name}, {@code ...icon} for the texture file
     * under {@code textures/items/}), {@code tooltip} is an array of extra lang keys appended
     * on shift-hover (may be empty).
     */
    public void addAugment(int meta, String type, int level, String nameSuffix, String[] tooltip) {
        entries.put(meta, new Entry(type, level, nameSuffix, tooltip));
    }

    public ItemStack getStack(int meta) {
        return new ItemStack(this, 1, meta);
    }

    @Override
    public void registerIcons(IIconRegister register) {
        for (Map.Entry<Integer, Entry> e : entries.entrySet()) {
            icons.put(e.getKey(), register.registerIcon(ThermalADD.MODID + ":" + e.getValue().nameSuffix));
        }
    }

    @Override
    public IIcon getIconFromDamage(int meta) {
        IIcon icon = icons.get(meta);
        return icon != null ? icon : itemIcon;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        Entry entry = entries.get(stack.getItemDamage());
        return entry != null ? "item.thermaladd.addAugment." + entry.nameSuffix : super.getUnlocalizedName(stack);
    }

    @Override
    public void getSubItems(Item item, net.minecraft.creativetab.CreativeTabs tab, List list) {
        for (Integer meta : entries.keySet()) {
            list.add(new ItemStack(this, 1, meta));
        }
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        Entry entry = entries.get(stack.getItemDamage());
        if (entry == null) {
            return;
        }
        for (String key : entry.tooltip) {
            list.add(StatCollector.translateToLocal(key));
        }
    }

    @Override
    public int getAugmentLevel(ItemStack stack, String type) {
        Entry entry = entries.get(stack.getItemDamage());
        return entry != null && entry.type.equals(type) ? entry.level : 0;
    }

    @Override
    public Set<String> getAugmentTypes(ItemStack stack) {
        Entry entry = entries.get(stack.getItemDamage());
        Set<String> types = new HashSet<String>();
        if (entry != null) {
            types.add(entry.type);
        }
        return types;
    }
}
