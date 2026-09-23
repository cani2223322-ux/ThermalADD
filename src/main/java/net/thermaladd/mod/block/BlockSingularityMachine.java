package net.thermaladd.mod.block;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import cofh.api.block.IDismantleable;
import cofh.api.item.IToolHammer;
import net.thermaladd.mod.ThermalADD;
import net.thermaladd.mod.init.ModCreativeTab;
import net.thermaladd.mod.tileentity.TileSingularityMachine;
import net.thermaladd.mod.util.MachineDismantle;

/**
 * Block shared by the TileSingularityMachine family - the same behaviour as BlockAdvancedSawmill
 * (TE casing and face textures, side badges, wrench rotation and dismantling, drops that carry
 * augments, sides and energy, comparator output), parameterised by the machine's face texture,
 * its side-mode badges and its GUI id.
 */
public abstract class BlockSingularityMachine extends BlockContainer implements IDismantleable {

    private final String faceName;
    private final String[] badges;
    private final int guiId;

    private IIcon iconFaceIdle;
    private IIcon iconFaceActive;
    private IIcon[] iconsTop;
    private IIcon[] iconsBottom;
    private IIcon[] iconsSide;

    protected BlockSingularityMachine(String name, String faceName, String[] badges, int guiId) {
        super(Material.iron);
        this.faceName = faceName;
        this.badges = badges;
        this.guiId = guiId;
        setBlockName(name);
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(5.0F);
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
    }

    /** Tooltip numbers for the item form; read from the tile's config-backed statics. */
    public abstract int getItemCapacity();

    public abstract int getItemReceiveRate();

    @Override
    public void registerBlockIcons(IIconRegister register) {
        iconFaceIdle = register.registerIcon("thermalexpansion:machine/Machine_Face_" + faceName);
        iconFaceActive = register.registerIcon("thermalexpansion:machine/Machine_Active_" + faceName);
        iconsTop = new IIcon[badges.length];
        iconsBottom = new IIcon[badges.length];
        iconsSide = new IIcon[badges.length];
        for (int mode = 0; mode < badges.length; mode++) {
            if (badges[mode] == null) {
                iconsTop[mode] = register.registerIcon("thermalexpansion:machine/Machine_Top");
                iconsBottom[mode] = register.registerIcon("thermalexpansion:machine/Machine_Bottom");
                iconsSide[mode] = register.registerIcon("thermalexpansion:machine/Machine_Side");
            } else {
                iconsTop[mode] = register.registerIcon(ThermalADD.MODID + ":Top" + badges[mode]);
                iconsBottom[mode] = register.registerIcon(ThermalADD.MODID + ":Bottom" + badges[mode]);
                iconsSide[mode] = register.registerIcon(ThermalADD.MODID + ":Side" + badges[mode]);
            }
        }
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        if (side == 0) {
            return iconsBottom[0];
        }
        if (side == 1) {
            return iconsTop[0];
        }
        return side == meta || (meta == 0 && side == 3) ? iconFaceIdle : iconsSide[0];
    }

    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileSingularityMachine)) {
            return getIcon(side, world.getBlockMetadata(x, y, z));
        }
        TileSingularityMachine tile = (TileSingularityMachine) te;
        if (side == tile.getFacing()) {
            return tile.isActive() ? iconFaceActive : iconFaceIdle;
        }
        int mode = tile.getSideMode(side);
        if (mode < 0 || mode >= badges.length) {
            mode = 0;
        }
        if (side == 0) {
            return iconsBottom[mode];
        }
        if (side == 1) {
            return iconsTop[mode];
        }
        return iconsSide[mode];
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        int rotation = MathHelper.floor_double(placer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        int meta = rotation == 0 ? 2 : rotation == 1 ? 5 : rotation == 2 ? 3 : 4;
        world.setBlockMetadataWithNotify(x, y, z, meta, 2);
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileSingularityMachine) {
            TileSingularityMachine tile = (TileSingularityMachine) te;
            tile.setFacing(meta);
            tile.setDefaultSides();
            if (stack.hasDisplayName()) {
                tile.setCustomName(stack.getDisplayName());
            }
            if (!world.isRemote) {
                if (stack.hasTagCompound() && stack.getTagCompound().hasKey(MachineDismantle.TAG_AUGMENTS)) {
                    tile.readAugmentsFromNBT(stack.getTagCompound());
                } else {
                    tile.installDefaultAugments();
                }
                MachineDismantle.restoreSidesAndEnergy(stack, tile);
                if (stack.hasTagCompound()) {
                    tile.readTankFromItem(stack.getTagCompound());
                }
            }
        }
    }

    @Override
    public boolean isOpaqueCube() {
        return true;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return true;
    }

    @Override
    public boolean hasComparatorInputOverride() {
        return true;
    }

    @Override
    public int getComparatorInputOverride(World world, int x, int y, int z, int side) {
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof TileSingularityMachine ? ((TileSingularityMachine) te).getComparatorSignal() : 0;
    }

    /** Crescent Hammer: a click rotates, sneak+click dismantles - see BlockAdvancedPulverizer. */
    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side,
            float hitX, float hitY, float hitZ) {
        ItemStack held = player.getHeldItem();
        if (held != null && held.getItem() instanceof IToolHammer) {
            IToolHammer hammer = (IToolHammer) held.getItem();
            if (hammer.isUsable(held, player, x, y, z)) {
                if (!world.isRemote) {
                    if (player.isSneaking()) {
                        dismantleBlock(player, world, x, y, z, false);
                    } else {
                        TileEntity te = world.getTileEntity(x, y, z);
                        if (te instanceof TileSingularityMachine) {
                            TileSingularityMachine tile = (TileSingularityMachine) te;
                            int next = nextFacing(tile.getFacing());
                            world.setBlockMetadataWithNotify(x, y, z, next, 3);
                            tile.rotateFacing(next);
                        }
                    }
                    hammer.toolUsed(held, player, x, y, z);
                }
                return true;
            }
        }

        if (!world.isRemote) {
            player.openGui(ThermalADD.instance, guiId, world, x, y, z);
        }
        return true;
    }

    private static int nextFacing(int facing) {
        int[] order = TileSingularityMachine.FACING_META;
        for (int i = 0; i < order.length; i++) {
            if (order[i] == facing) {
                return order[(i + 1) % order.length];
            }
        }
        return order[0];
    }

    /** Spills everything but the augments, which travel inside the dropped item - see getDrops. */
    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileSingularityMachine) {
            TileSingularityMachine tile = (TileSingularityMachine) te;
            for (int i = 0; i < tile.getSizeInventory(); i++) {
                if (tile.isAugmentSlot(i)) {
                    continue;
                }
                spill(world, x, y, z, tile.getStackInSlot(i));
            }
            List<ItemStack> hidden = tile.takeHiddenContents();
            for (int i = 0; i < hidden.size(); i++) {
                spill(world, x, y, z, hidden.get(i));
            }
            tile.clearContentsOnBreak();
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    private static void spill(World world, int x, int y, int z, ItemStack stack) {
        if (stack != null) {
            float rx = world.rand.nextFloat() * 0.8F + 0.1F;
            float ry = world.rand.nextFloat() * 0.8F + 0.1F;
            float rz = world.rand.nextFloat() * 0.8F + 0.1F;
            world.spawnEntityInWorld(new EntityItem(world, x + rx, y + ry, z + rz, stack.copy()));
        }
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
        ArrayList<ItemStack> drops = new ArrayList<ItemStack>();
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileSingularityMachine) {
            ItemStack drop = MachineDismantle.createDrop(this, damageDropped(metadata), (TileSingularityMachine) te);
            // The tank travels with the machine too, so a Crucible full of lava can be moved.
            NBTTagCompound fluidTag = new NBTTagCompound();
            ((TileSingularityMachine) te).writeTankToItem(fluidTag);
            if (!fluidTag.hasNoTags()) {
                if (!drop.hasTagCompound()) {
                    drop.setTagCompound(new NBTTagCompound());
                }
                drop.getTagCompound().setTag(TileSingularityMachine.TAG_FLUID, fluidTag.getTag(TileSingularityMachine.TAG_FLUID));
            }
            drops.add(drop);
        } else {
            drops.add(new ItemStack(Item.getItemFromBlock(this), 1, damageDropped(metadata)));
        }
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

    /** Keeps the tile alive until getDrops has read it - see BlockAdvancedPulverizer#removedByPlayer. */
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
