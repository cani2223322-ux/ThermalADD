package net.thermaladd.mod.item;

import java.util.List;
import java.util.Locale;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.thermaladd.mod.tileentity.TileAdvancedPulverizer;

/** Adds the "Tier: UltimateResonant" + RF stats readout real CoFH machine tooltips show. */
public class ItemBlockAdvancedPulverizer extends ItemBlock {

    public ItemBlockAdvancedPulverizer(Block block) {
        super(block);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(StatCollector.translateToLocalFormatted("tooltip.thermaladd.tier", TileAdvancedPulverizer.TIER_NAME));
        // StatCollector.translateToLocalFormatted() runs the lang string through
        // String.format() using the JVM's default Locale, not a fixed one - a ",d"-style
        // grouped number baked into the lang string itself would pick up whatever thousands
        // separator that locale uses (often not a plain comma), which Minecraft's own font has
        // no glyph for and renders as a garbled placeholder. Pre-formatting the number here
        // with an explicit Locale.ROOT and handing it over as an already-formatted %s dodges
        // that entirely - see GuiSingularityCell/ItemBlockSingularityCell/
        // ThermalADDWailaPlugin for the same fix applied where this bug was first reported.
        list.add(StatCollector.translateToLocalFormatted("tooltip.thermaladd.capacity",
                String.format(Locale.ROOT, "%,d", TileAdvancedPulverizer.BASE_ENERGY_CAPACITY)));
        list.add(StatCollector.translateToLocalFormatted("tooltip.thermaladd.input",
                String.format(Locale.ROOT, "%,d", TileAdvancedPulverizer.ENERGY_RECEIVE_PER_TICK)));
    }
}
