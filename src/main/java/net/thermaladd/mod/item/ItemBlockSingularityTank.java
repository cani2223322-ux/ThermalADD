package net.thermaladd.mod.item;

import java.util.List;
import java.util.Locale;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;
import net.thermaladd.mod.tileentity.TileSingularityTank;

/** Capacity, and whatever fluid a dismantled tank is carrying. */
public class ItemBlockSingularityTank extends ItemBlock {

    public ItemBlockSingularityTank(Block block) {
        super(block);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        FluidStack fluid = null;
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey(TileSingularityTank.TAG_FLUID)) {
            fluid = FluidStack.loadFluidStackFromNBT(stack.getTagCompound().getCompoundTag(TileSingularityTank.TAG_FLUID));
        }
        String capacity = String.format(Locale.ROOT, "%,d", TileSingularityTank.CAPACITY);
        if (fluid == null) {
            list.add(StatCollector.translateToLocalFormatted("tooltip.thermaladd.tank.empty", capacity));
        } else {
            list.add(StatCollector.translateToLocalFormatted("tooltip.thermaladd.tank.contents",
                    fluid.getLocalizedName(), String.format(Locale.ROOT, "%,d", fluid.amount), capacity));
        }
        if (stack.hasTagCompound() && stack.getTagCompound().getByte(TileSingularityTank.TAG_MODE) == 1) {
            list.add(StatCollector.translateToLocal("tooltip.thermaladd.tank.pouring"));
        }
    }
}
