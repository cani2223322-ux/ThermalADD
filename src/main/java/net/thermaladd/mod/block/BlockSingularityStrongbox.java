package net.thermaladd.mod.block;

import java.util.ArrayList;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import cofh.api.block.IDismantleable;
import cofh.api.item.IToolHammer;
import net.thermaladd.mod.ThermalADD;
import net.thermaladd.mod.init.ModCreativeTab;
import net.thermaladd.mod.tileentity.TileSingularityStrongbox;
import net.thermaladd.mod.util.MachineDismantle;

/**
 * Drawn entirely by RenderSingularityStrongbox with real TE's own strongbox model; the block's
 * bounds match that model (TE's BlockStrongbox uses the same 1/16 inset and 14/16 height).
 */
public class BlockSingularityStrongbox extends BlockContainer implements IDismantleable {

    public BlockSingularityStrongbox() {
        super(Material.iron);
        setBlockName("singularityStrongbox");
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(20.0F);
        setResistance(120.0F);
        setStepSound(soundTypeMetal);
        setBlockBounds(0.0625F, 0F, 0.0625F, 0.9375F, 0.875F, 0.9375F);
    }

    /** Only the break particles use this. */
    @Override
    public void registerBlockIcons(IIconRegister register) {
        blockIcon = register.registerIcon(ThermalADD.MODID + ":singularityFrameSide");
    }

    @Override
    public int getRenderType() {
        return -1;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileSingularityStrongbox();
    }

    @Override
    public boolean hasComparatorInputOverride() {
        return true;
    }

    @Override
    public int getComparatorInputOverride(World world, int x, int y, int z, int side) {
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof TileSingularityStrongbox ? ((TileSingularityStrongbox) te).getComparatorSignal() : 0;
    }

    /** Faces the player, like a chest; a carried inventory is unpacked into the new box. */
    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileSingularityStrongbox)) {
            return;
        }
        TileSingularityStrongbox tile = (TileSingularityStrongbox) te;
        int rotation = MathHelper.floor_double(placer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        tile.setFacing(rotation == 0 ? 2 : rotation == 1 ? 5 : rotation == 2 ? 3 : 4);
        if (stack.hasDisplayName()) {
            tile.setCustomName(stack.getDisplayName());
        }
        if (!world.isRemote && stack.hasTagCompound()) {
            tile.readInventoryFromItem(stack.getTagCompound());
        }
        world.markBlockForUpdate(x, y, z);
    }

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
            player.openGui(ThermalADD.instance, ThermalADD.GUI_ID_SINGULARITY_STRONGBOX, world, x, y, z);
        }
        return true;
    }

    /**
     * The whole inventory goes into the one dropped item, as with TE's strongboxes - breaking it
     * by hand, with a wrench or by any other means never spills the contents.
     */
    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
        ArrayList<ItemStack> drops = new ArrayList<ItemStack>();
        ItemStack drop = new ItemStack(Item.getItemFromBlock(this), 1, 0);
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileSingularityStrongbox) {
            TileSingularityStrongbox tile = (TileSingularityStrongbox) te;
            NBTTagCompound tag = new NBTTagCompound();
            tile.writeInventoryToItem(tag);
            if (!tag.hasNoTags()) {
                drop.setTagCompound(tag);
            }
            if (tile.hasCustomInventoryName()) {
                drop.setStackDisplayName(tile.getInventoryName());
            }
            // breakBlock deliberately spills nothing, so the contents exist only in this item.
        }
        drops.add(drop);
        return drops;
    }

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
}
