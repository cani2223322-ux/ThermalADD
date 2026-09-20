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

import net.thermaladd.mod.ThermalADD;
import net.thermaladd.mod.init.ModCreativeTab;
import net.thermaladd.mod.tileentity.TileSingularityCell;
import net.thermaladd.mod.util.PendingAugmentDrops;

/**
 * A single-tier "beyond spec" Energy Cell, modeled on real Thermal Expansion's own Resonant
 * Energy Cell (cofh.thermalexpansion.block.cell.BlockCell/TileCell): a plain cube, no facing to
 * track (it accepts and provides energy on every side unconditionally - real TE's own side-
 * configuration/redstone-control tabs are deliberately left out here for the same "no separate
 * tier/upgrade item of its own" simplicity this mod's other blocks already use), one static
 * gradient texture rather than real TE's dynamic charge-meter TESR.
 */
public class BlockSingularityCell extends BlockContainer {

    private IIcon icon;

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
        icon = register.registerIcon(ThermalADD.MODID + ":singularityCell");
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        return icon;
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

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileSingularityCell();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side,
            float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            player.openGui(ThermalADD.instance, ThermalADD.GUI_ID_SINGULARITY_CELL, world, x, y, z);
        }
        return true;
    }

    /**
     * Same "the charge survives being picked back up" behavior real TE's own Energy Cells have
     * (they double as portable battery packs, not just fixed storage) - captures the tile's
     * true long-valued charge into PendingAugmentDrops (see BlockAdvancedPulverizer's own use
     * of it for augments) just before the tile is destroyed, for getDrops() to pick up.
     */
    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileSingularityCell) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setLong("Energy", ((TileSingularityCell) te).getEnergyStoredLong());
            PendingAugmentDrops.put(x, y, z, tag);
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
        ItemStack drop = new ItemStack(Item.getItemFromBlock(this), 1, damageDropped(metadata));
        NBTTagCompound tag = PendingAugmentDrops.take(x, y, z);
        if (tag != null && tag.hasKey("Energy")) {
            NBTTagCompound itemTag = new NBTTagCompound();
            itemTag.setLong("Energy", tag.getLong("Energy"));
            drop.setTagCompound(itemTag);
        }
        ArrayList<ItemStack> drops = new ArrayList<ItemStack>();
        drops.add(drop);
        return drops;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileSingularityCell && stack.hasTagCompound() && stack.getTagCompound().hasKey("Energy")) {
            ((TileSingularityCell) te).setEnergyStoredLong(stack.getTagCompound().getLong("Energy"));
        }
    }
}
