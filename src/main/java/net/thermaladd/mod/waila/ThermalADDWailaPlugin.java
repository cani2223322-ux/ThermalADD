package net.thermaladd.mod.waila;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaRegistrar;
import mcp.mobius.waila.api.ITaggedList;

import net.thermaladd.mod.block.BlockSingularityCell;
import net.thermaladd.mod.tileentity.TileSingularityCell;

/**
 * Waila integration for the Singularity Cell - entirely optional, and deliberately never
 * touched by this mod's own code except through the IMC message string
 * ThermalADD#postInit sends ("net.thermaladd.mod.waila.ThermalADDWailaPlugin.callbackRegister").
 * Waila's own registration code (mcp.mobius.waila.server.ProxyServer#callbackRegistration)
 * reflectively {@code Class.forName}s that string and invokes the static method below - which
 * only happens at all if Waila is installed and processing that message, meaning this class
 * (and the mcp.mobius.waila.* interfaces it implements) is never loaded by the JVM unless Waila
 * is already present to have triggered the load. No {@code @Optional.Interface}/
 * {@code @Optional.Method} annotations needed: nothing else in the mod ever imports or
 * references this class directly, so there is no code path that could try to resolve it (or
 * fail to) with Waila absent.
 *
 * What it actually fixes: Waila's own bundled Thermal Expansion addon
 * (mcp.mobius.waila.addons.thermalexpansion.HUDHandlerIEnergyHandler) generically detects any
 * cofh.api.energy.IEnergyReceiver/IEnergyProvider - which TileSingularityCell is, for
 * compatibility with every other RF-aware block in the pack - and shows
 * getEnergyStored()/getMaxEnergyStored() through that same 32-bit-int-capped API the cell can't
 * avoid exposing (see TileSingularityCell's own class javadoc: the whole RF API this game
 * version runs on is int end to end, hard-capped around 2.15 billion, nowhere near the cell's
 * real 1-trillion capacity) - hence Waila showing "2147483647" as the max instead of the real
 * number. That handler tags its added line "RFEnergyStorage" and skips adding one if that tag
 * is already present on the tooltip list, so removing any existing entry under that tag and
 * adding our own real (long-valued) replacement under the same tag fixes the display regardless
 * of which of the two providers Waila happens to run first for a given hover.
 */
public class ThermalADDWailaPlugin implements IWailaDataProvider {

    private static final String TAG_ENERGY = "RFEnergyStorage";

    public static void callbackRegister(IWailaRegistrar registrar) {
        registrar.registerBodyProvider(new ThermalADDWailaPlugin(), BlockSingularityCell.class);
    }

    @Override
    public ItemStack getWailaStack(IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return null;
    }

    @Override
    public List<String> getWailaHead(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return currenttip;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> getWailaBody(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor, IWailaConfigHandler config) {
        TileEntity te = accessor.getTileEntity();
        if (!(te instanceof TileSingularityCell)) {
            return currenttip;
        }
        TileSingularityCell cell = (TileSingularityCell) te;
        String line = String.format("%,d", cell.getEnergyStoredLong()) + " / " + String.format("%,d", cell.getCapacityLong()) + " RF";

        if (currenttip instanceof ITaggedList) {
            ITaggedList<String, String> tagged = (ITaggedList<String, String>) currenttip;
            tagged.removeEntries(TAG_ENERGY);
            tagged.add(line, TAG_ENERGY);
        } else {
            currenttip.add(line);
        }
        return currenttip;
    }

    @Override
    public List<String> getWailaTail(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return currenttip;
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity te, NBTTagCompound tag, World world, int x, int y, int z) {
        return tag;
    }
}
