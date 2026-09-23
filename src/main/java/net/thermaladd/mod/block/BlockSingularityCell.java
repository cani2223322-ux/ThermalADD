package net.thermaladd.mod.block;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import cofh.api.block.IDismantleable;
import cofh.api.item.IToolHammer;
import net.thermaladd.mod.ThermalADD;
import net.thermaladd.mod.init.ModCreativeTab;
import net.thermaladd.mod.tileentity.TileSingularityCell;
import net.thermaladd.mod.util.MachineDismantle;

/**
 * A single-tier "beyond spec" Energy Cell, modeled on real Thermal Expansion's own Resonant
 * Energy Cell (cofh.thermalexpansion.block.cell.BlockCell/TileCell): no facing to track (it has
 * no "front" - every side is independently configured, see TileSingularityCell/TabConfigCell),
 * one static gradient texture rather than real TE's dynamic charge-meter TESR, with the same 3
 * connection-badge overlay (Disabled/Output/Input) real TE's own Cell shows per face.
 */
public class BlockSingularityCell extends BlockContainer implements IDismantleable {

    /** Indexed by TileSingularityCell.MODE_* - all 6 faces use the same texture family (unlike a machine, this block has no Top/Bottom/Side distinction). */
    private final IIcon[] icons = new IIcon[TileSingularityCell.MODE_COUNT];

    public BlockSingularityCell() {
        super(Material.iron);
        setBlockName("singularityCell");
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(50.0F);
        setResistance(2000.0F);
        setStepSound(soundTypeMetal);
    }

    @Override
    public void registerBlockIcons(IIconRegister register) {
        icons[TileSingularityCell.MODE_DISABLED] = register.registerIcon(ThermalADD.MODID + ":singularityCell");
        icons[TileSingularityCell.MODE_OUTPUT] = register.registerIcon(ThermalADD.MODID + ":CellOutput");
        icons[TileSingularityCell.MODE_INPUT] = register.registerIcon(ThermalADD.MODID + ":CellInput");
    }

    /** Inventory/item-form rendering: no world context, so always the plain (no badge) face. */
    @Override
    public IIcon getIcon(int side, int meta) {
        return icons[TileSingularityCell.MODE_DISABLED];
    }

    /** In-world rendering: shows the same Disabled/Output/Input badge on each face that the Configuration tab shows for it. */
    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        TileEntity te = world.getTileEntity(x, y, z);
        int mode = te instanceof TileSingularityCell ? ((TileSingularityCell) te).getSideMode(side) : TileSingularityCell.MODE_DISABLED;
        return icons[mode];
    }

    @Override
    public boolean isOpaqueCube() {
        return true;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return true;
    }

    /** A faint glow that brightens with charge, same spirit as real TE's own TileCell#getLightValue. */
    @Override
    public int getLightValue(IBlockAccess world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof TileSingularityCell ? ((TileSingularityCell) te).getLightValue() : 0;
    }

    /**
     * Comparator support: proportional to how full the cell is. The tile calls markDirty() on
     * every energy change, which vanilla routes into the comparator update path for free.
     */
    @Override
    public boolean hasComparatorInputOverride() {
        return true;
    }

    @Override
    public int getComparatorInputOverride(World world, int x, int y, int z, int side) {
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof TileSingularityCell ? ((TileSingularityCell) te).getComparatorSignal() : 0;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileSingularityCell();
    }

    /**
     * Crescent Hammer support: a sneaking click dismantles the cell into an item that keeps its
     * charge and side configuration, matching real Thermal Expansion's own Energy Cells. There is
     * nothing to rotate here (the cell has no facing), so a plain wrench click just opens the GUI
     * like any other click.
     */
    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side,
            float hitX, float hitY, float hitZ) {
        ItemStack held = player.getHeldItem();
        if (held != null && held.getItem() instanceof IToolHammer && player.isSneaking()) {
            IToolHammer hammer = (IToolHammer) held.getItem();
            if (hammer.isUsable(held, player, x, y, z)) {
                if (!world.isRemote) {
                    dismantleBlock(player, world, x, y, z, false);
                    hammer.toolUsed(held, player, x, y, z);
                }
                return true;
            }
        }

        if (!world.isRemote) {
            player.openGui(ThermalADD.instance, ThermalADD.GUI_ID_SINGULARITY_CELL, world, x, y, z);
        }
        return true;
    }

    /**
     * Same "the charge and side config survive being picked back up" behavior real TE's own Energy
     * Cells have (they double as portable battery packs, not just fixed storage). Read straight off
     * the live tile - see MachineDismantle's class javadoc for why the old capture-in-breakBlock
     * handoff was wrong. Deliberately writes only these two tags rather than reusing writeToNBT()
     * wholesale: that also serializes the tile's own x/y/z, which would be stale once the item is
     * carried somewhere else and placed again.
     */
    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
        ItemStack drop = new ItemStack(Item.getItemFromBlock(this), 1, damageDropped(metadata));
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileSingularityCell) {
            TileSingularityCell tile = (TileSingularityCell) te;
            NBTTagCompound itemTag = new NBTTagCompound();
            long energy = tile.getEnergyStoredLong();
            if (energy > 0L) {
                itemTag.setLong("Energy", energy);
            }
            byte[] sides = new byte[6];
            for (int i = 0; i < 6; i++) {
                sides[i] = (byte) tile.getSideMode(i);
            }
            if (!TileSingularityCell.isDefaultSideConfig(sides)) {
                itemTag.setByteArray("Sides", sides);
            }
            // An empty, never-configured cell drops with no NBT at all, so it still stacks with a
            // freshly crafted one instead of sitting alone in its own single-item stack.
            if (!itemTag.hasNoTags()) {
                drop.setTagCompound(itemTag);
            }
        }
        ArrayList<ItemStack> drops = new ArrayList<ItemStack>();
        drops.add(drop);
        return drops;
    }

    /**
     * Any tool (or a bare hand) harvests the block. Material.iron would otherwise demand a
     * pickaxe, and without one removedByPlayer skips getDrops entirely - deleting everything
     * that only travels inside the dropped item (augments, stored RF, fluid, a box's contents).
     */
    @Override
    public boolean canHarvestBlock(EntityPlayer player, int meta) {
        return true;
    }

    /** See BlockAdvancedPulverizer#removedByPlayer - keeps the tile alive until getDrops has read it. */
    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        return willHarvest || super.removedByPlayer(world, player, x, y, z, willHarvest);
    }

    @Override
    public void harvestBlock(World world, EntityPlayer player, int x, int y, int z, int meta) {
        super.harvestBlock(world, player, x, y, z, meta);
        world.setBlockToAir(x, y, z);
    }

    @Override
    public ArrayList<ItemStack> dismantleBlock(EntityPlayer player, World world, int x, int y, int z, boolean returnDrops) {
        return MachineDismantle.dismantle(this, player, world, x, y, z, returnDrops);
    }

    @Override
    public boolean canDismantle(EntityPlayer player, World world, int x, int y, int z) {
        return true;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileSingularityCell) || !stack.hasTagCompound()) {
            return;
        }
        TileSingularityCell tile = (TileSingularityCell) te;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag.hasKey("Energy")) {
            tile.setEnergyStoredLong(tag.getLong("Energy"));
        }
        if (tag.hasKey("Sides")) {
            tile.setSideModes(tag.getByteArray("Sides"));
        }
    }
}
