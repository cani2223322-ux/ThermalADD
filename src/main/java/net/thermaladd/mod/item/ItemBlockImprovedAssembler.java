package net.thermaladd.mod.item;

import java.util.List;
import java.util.Locale;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.thermaladd.mod.tileentity.TileImprovedAssembler;

/** Adds the same "Tier: UltimateResonant" + RF stats readout as {@link ItemBlockAdvancedPulverizer}. */
public class ItemBlockImprovedAssembler extends ItemBlock {

    public ItemBlockImprovedAssembler(Block block) {
        super(block);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(StatCollector.translateToLocalFormatted("tooltip.thermaladd.tier", TileImprovedAssembler.TIER_NAME));
        // See ItemBlockAdvancedPulverizer's own comment on this same pattern - pre-formatting
        // with a fixed Locale here avoids StatCollector.translateToLocalFormatted() picking up
        // whatever thousands separator the JVM's default Locale uses (often not a plain comma,
        // and Minecraft's font has no glyph for most of the alternatives).
        list.add(StatCollector.translateToLocalFormatted("tooltip.thermaladd.capacity",
                String.format(Locale.ROOT, "%,d", TileImprovedAssembler.ENERGY_CAPACITY)));
        list.add(StatCollector.translateToLocalFormatted("tooltip.thermaladd.input",
                String.format(Locale.ROOT, "%,d", TileImprovedAssembler.ENERGY_RECEIVE_PER_TICK)));
    }
}
