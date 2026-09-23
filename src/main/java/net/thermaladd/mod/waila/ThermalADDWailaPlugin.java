package net.thermaladd.mod.waila;

import java.util.List;
import java.util.Locale;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaRegistrar;
import mcp.mobius.waila.api.ITaggedList;

import net.thermaladd.mod.block.BlockAdvancedCharger;
import net.thermaladd.mod.block.BlockAdvancedFurnace;
import net.thermaladd.mod.block.BlockAdvancedPulverizer;
import net.thermaladd.mod.block.BlockAdvancedSawmill;
import net.thermaladd.mod.block.BlockImprovedAssembler;
import net.thermaladd.mod.block.BlockSingularityCell;
import net.thermaladd.mod.block.BlockSingularCrucible;
import net.thermaladd.mod.block.BlockSingularSmelter;
import net.thermaladd.mod.block.BlockSingularTransposer;
import net.thermaladd.mod.tileentity.TileSingularityMachine;
import net.thermaladd.mod.tileentity.TileAdvancedCharger;
import net.thermaladd.mod.tileentity.TileAdvancedFurnace;
import net.thermaladd.mod.tileentity.TileAdvancedPulverizer;
import net.thermaladd.mod.tileentity.TileAdvancedSawmill;
import net.thermaladd.mod.tileentity.TileImprovedAssembler;
import net.thermaladd.mod.tileentity.TileSingularityCell;

/**
 * Waila integration - entirely optional, and deliberately never touched by this mod's own code
 * except through the IMC message string ThermalADD#postInit sends
 * ("net.thermaladd.mod.waila.ThermalADDWailaPlugin.callbackRegister"). Waila's own registration
 * code reflectively {@code Class.forName}s that string and invokes the static method below - which
 * only happens at all if Waila is installed, meaning this class (and the mcp.mobius.waila.*
 * interfaces it implements) is never loaded by the JVM unless Waila is already present to have
 * triggered the load. No {@code @Optional.Interface}/{@code @Optional.Method} annotations needed:
 * nothing else in the mod ever imports or references this class directly, so there is no code path
 * that could try to resolve it (or fail to) with Waila absent.
 *
 * Everything shown here is fetched through {@link #getNBTData}, which Waila runs SERVER-side and
 * ships to the client with the rest of the tooltip data. Reading the client's own copy of the tile
 * instead does not work: these tiles only push a description packet on a render-state change
 * (facing/side config/active), so a client-side read of the charge or the RF/t would show whatever
 * those happened to be when the chunk loaded and then never move.
 *
 * The Cell's line additionally replaces one Waila's own bundled Thermal Expansion addon
 * (mcp.mobius.waila.addons.thermalexpansion.HUDHandlerIEnergyHandler) adds: that handler generically
 * detects any cofh.api.energy.IEnergyReceiver/IEnergyProvider - which TileSingularityCell is, for
 * compatibility with every other RF-aware block in the pack - and shows
 * getEnergyStored()/getMaxEnergyStored() through the 32-bit-int-capped API the cell cannot avoid
 * exposing (see TileSingularityCell's own class javadoc: the whole RF API this game version runs on
 * is int end to end, hard-capped around 2.15 billion, nowhere near the cell's real 1-trillion
 * capacity) - hence Waila showing "2147483647" as the max instead of the real number. That handler
 * tags its line "RFEnergyStorage" and skips adding one if that tag is already present, so removing
 * any existing entry under that tag and adding our own real (long-valued) replacement under the
 * same tag fixes the display regardless of which of the two providers Waila runs first.
 */
public class ThermalADDWailaPlugin implements IWailaDataProvider {

    private static final String TAG_ENERGY = "RFEnergyStorage";

    private static final String KEY_ENERGY = "taEnergy";
    private static final String KEY_CAPACITY = "taCapacity";
    private static final String KEY_RATE = "taRate";
    private static final String KEY_MAX_RATE = "taMaxRate";
    private static final String KEY_BUSY_LINES = "taBusyLines";
    private static final String KEY_TOTAL_LINES = "taTotalLines";
    private static final String KEY_FLUID_AMOUNT = "taFluidAmount";
    private static final String KEY_FLUID_CAPACITY = "taFluidCapacity";
    private static final String KEY_FLUID_NAME = "taFluidName";

    public static void callbackRegister(IWailaRegistrar registrar) {
        ThermalADDWailaPlugin provider = new ThermalADDWailaPlugin();
        register(registrar, provider, BlockSingularityCell.class);
        register(registrar, provider, BlockAdvancedPulverizer.class);
        register(registrar, provider, BlockAdvancedFurnace.class);
        register(registrar, provider, BlockAdvancedSawmill.class);
        register(registrar, provider, BlockAdvancedCharger.class);
        register(registrar, provider, BlockImprovedAssembler.class);
        register(registrar, provider, BlockSingularSmelter.class);
        register(registrar, provider, BlockSingularCrucible.class);
        register(registrar, provider, BlockSingularTransposer.class);
    }

    /** Both halves are needed: the NBT provider produces the numbers server-side, the body provider draws them. */
    private static void register(IWailaRegistrar registrar, ThermalADDWailaPlugin provider, Class<?> blockClass) {
        registrar.registerBodyProvider(provider, blockClass);
        registrar.registerNBTProvider(provider, blockClass);
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
        NBTTagCompound data = accessor.getNBTData();
        if (data == null || !data.hasKey(KEY_ENERGY)) {
            return currenttip;
        }

        String energyLine = StatCollector.translateToLocalFormatted("waila.thermaladd.energy",
                format(data.getLong(KEY_ENERGY)), format(data.getLong(KEY_CAPACITY)));
        if (currenttip instanceof ITaggedList) {
            // Replace Waila's own RF line on every block, not just the Cell. The machines are
            // IEnergyReceiver + IEnergyInfo too, so Waila's bundled TE handler adds its own
            // "X / Y RF" to them as well - leaving it there showed the energy twice. Tagging ours
            // the same way also stops that handler adding its line if it happens to run after us.
            ITaggedList<String, String> tagged = (ITaggedList<String, String>) currenttip;
            tagged.removeEntries(TAG_ENERGY);
            tagged.add(energyLine, TAG_ENERGY);
        } else {
            currenttip.add(energyLine);
        }

        if (data.hasKey(KEY_RATE)) {
            currenttip.add(StatCollector.translateToLocalFormatted("waila.thermaladd.consumption",
                    format(data.getInteger(KEY_RATE)), format(data.getInteger(KEY_MAX_RATE))));
        }
        if (data.hasKey(KEY_TOTAL_LINES)) {
            currenttip.add(StatCollector.translateToLocalFormatted("waila.thermaladd.lines",
                    String.valueOf(data.getInteger(KEY_BUSY_LINES)), String.valueOf(data.getInteger(KEY_TOTAL_LINES))));
        }
        if (data.hasKey(KEY_FLUID_CAPACITY)) {
            // Fluid names are looked up client-side so they come out in the player's own language.
            Fluid fluid = data.hasKey(KEY_FLUID_NAME) ? FluidRegistry.getFluid(data.getString(KEY_FLUID_NAME)) : null;
            String name = fluid != null
                    ? fluid.getLocalizedName(new FluidStack(fluid, data.getInteger(KEY_FLUID_AMOUNT)))
                    : StatCollector.translateToLocal("waila.thermaladd.fluidEmpty");
            currenttip.add(StatCollector.translateToLocalFormatted("waila.thermaladd.fluid", name,
                    format(data.getInteger(KEY_FLUID_AMOUNT)), format(data.getInteger(KEY_FLUID_CAPACITY))));
        }
        return currenttip;
    }

    @Override
    public List<String> getWailaTail(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return currenttip;
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity te, NBTTagCompound tag, World world, int x, int y, int z) {
        if (te instanceof TileSingularityCell) {
            TileSingularityCell cell = (TileSingularityCell) te;
            tag.setLong(KEY_ENERGY, cell.getEnergyStoredLong());
            tag.setLong(KEY_CAPACITY, cell.getCapacityLong());
        } else if (te instanceof TileAdvancedPulverizer) {
            TileAdvancedPulverizer tile = (TileAdvancedPulverizer) te;
            int busy = 0;
            for (int i = 0; i < TileAdvancedPulverizer.INPUT_SLOTS; i++) {
                if (tile.getProgressMax(i) > 0) {
                    busy++;
                }
            }
            writeMachine(tag, tile.getEnergy(), tile.getMaxEnergy(), tile.getEnergyPerTick(),
                    tile.getMaxEnergyPerTick(), busy, TileAdvancedPulverizer.INPUT_SLOTS);
        } else if (te instanceof TileAdvancedFurnace) {
            TileAdvancedFurnace tile = (TileAdvancedFurnace) te;
            int busy = 0;
            for (int i = 0; i < TileAdvancedFurnace.INPUT_SLOTS; i++) {
                if (tile.getProgressMax(i) > 0) {
                    busy++;
                }
            }
            writeMachine(tag, tile.getEnergy(), tile.getMaxEnergy(), tile.getEnergyPerTick(),
                    tile.getMaxEnergyPerTick(), busy, TileAdvancedFurnace.INPUT_SLOTS);
        } else if (te instanceof TileAdvancedSawmill) {
            TileAdvancedSawmill tile = (TileAdvancedSawmill) te;
            int busy = 0;
            for (int i = 0; i < TileAdvancedSawmill.INPUT_SLOTS; i++) {
                if (tile.getProgressMax(i) > 0) {
                    busy++;
                }
            }
            writeMachine(tag, tile.getEnergy(), tile.getMaxEnergy(), tile.getEnergyPerTick(),
                    tile.getMaxEnergyPerTick(), busy, TileAdvancedSawmill.INPUT_SLOTS);
        } else if (te instanceof TileAdvancedCharger) {
            TileAdvancedCharger tile = (TileAdvancedCharger) te;
            int busy = 0;
            for (int i = 0; i < TileAdvancedCharger.LINE_SLOTS; i++) {
                if (tile.getProgressMax(i) > 0) {
                    busy++;
                }
            }
            writeMachine(tag, tile.getEnergy(), tile.getMaxEnergy(), tile.getEnergyPerTick(),
                    tile.getMaxEnergyPerTick(), busy, TileAdvancedCharger.LINE_SLOTS);
        } else if (te instanceof TileSingularityMachine) {
            TileSingularityMachine tile = (TileSingularityMachine) te;
            int busy = 0;
            for (int i = 0; i < tile.getLineCount(); i++) {
                if (tile.getProgressMax(i) > 0) {
                    busy++;
                }
            }
            writeMachine(tag, tile.getEnergy(), tile.getMaxEnergy(), tile.getEnergyPerTick(),
                    tile.getMaxEnergyPerTick(), busy, tile.getLineCount());
            FluidStack fluid = tile.getTankFluid();
            if (tile.hasTank()) {
                tag.setInteger(KEY_FLUID_AMOUNT, fluid != null ? fluid.amount : 0);
                tag.setInteger(KEY_FLUID_CAPACITY, tile.getTankCapacity());
                if (fluid != null && fluid.amount > 0) {
                    tag.setString(KEY_FLUID_NAME, fluid.getFluid().getName());
                }
            }
        } else if (te instanceof TileImprovedAssembler) {
            // No per-line progress to report: the Assembler's schematic slots are configuration,
            // and its crafts complete within a tick rather than accumulating visible progress.
            TileImprovedAssembler tile = (TileImprovedAssembler) te;
            tag.setLong(KEY_ENERGY, tile.getEnergy());
            tag.setLong(KEY_CAPACITY, TileImprovedAssembler.ENERGY_CAPACITY);
            tag.setInteger(KEY_RATE, tile.getEnergyPerTick());
            tag.setInteger(KEY_MAX_RATE, tile.getMaxEnergyPerTick());
        }
        return tag;
    }

    private static void writeMachine(NBTTagCompound tag, int energy, int maxEnergy, int rate, int maxRate,
            int busyLines, int totalLines) {
        tag.setLong(KEY_ENERGY, energy);
        tag.setLong(KEY_CAPACITY, maxEnergy);
        tag.setInteger(KEY_RATE, rate);
        tag.setInteger(KEY_MAX_RATE, maxRate);
        tag.setInteger(KEY_BUSY_LINES, busyLines);
        tag.setInteger(KEY_TOTAL_LINES, totalLines);
    }

    /**
     * Locale.ROOT rather than the JVM default: StatCollector.translateToLocalFormatted runs the
     * lang string through String.format() with the default locale, and a grouped number formatted
     * under some locales picks up a thousands separator Minecraft's font has no glyph for, which
     * renders as a garbled placeholder. Pre-formatting here and passing an already-formatted %s
     * dodges that - same fix as in GuiSingularityCell/ItemBlockSingularityCell.
     */
    private static String format(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
