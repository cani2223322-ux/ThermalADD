package net.thermaladd.mod.block;

import java.util.ArrayList;

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
import net.thermaladd.mod.tileentity.TileAdvancedFurnace;
import net.thermaladd.mod.util.MachineDismantle;

/** Same design as {@link BlockAdvancedPulverizer}: real Thermal Expansion casing/face textures, Crescent Hammer support, Active/Inactive face swap. */
public class BlockAdvancedFurnace extends BlockContainer implements IDismantleable {

    private IIcon iconFaceIdle;
    private IIcon iconFaceActive;
    /**
     * Indexed by SIDE_MODE_* (Disabled/Input/Output/All) - Disabled is just the plain casing
     * texture for that face, matching real Thermal Expansion's own blank Config_None badge.
     */
    private final IIcon[] iconsTop = new IIcon[TileAdvancedFurnace.SIDE_MODE_COUNT];
    private final IIcon[] iconsBottom = new IIcon[TileAdvancedFurnace.SIDE_MODE_COUNT];
    private final IIcon[] iconsSide = new IIcon[TileAdvancedFurnace.SIDE_MODE_COUNT];

    public BlockAdvancedFurnace() {
        super(Material.iron);
        setBlockName("advancedFurnace");
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(5.0F);
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
    }

    @Override
    public void registerBlockIcons(IIconRegister register) {
        iconsTop[TileAdvancedFurnace.SIDE_MODE_DISABLED] = register.registerIcon("thermalexpansion:machine/Machine_Top");
        iconsBottom[TileAdvancedFurnace.SIDE_MODE_DISABLED] = register.registerIcon("thermalexpansion:machine/Machine_Bottom");
        iconsSide[TileAdvancedFurnace.SIDE_MODE_DISABLED] = register.registerIcon("thermalexpansion:machine/Machine_Side");
        iconFaceIdle = register.registerIcon("thermalexpansion:machine/Machine_Face_Furnace");
        iconFaceActive = register.registerIcon("thermalexpansion:machine/Machine_Active_Furnace");
        // Same composited connection badges as the Singularity Pulverizer (see
        // BlockAdvancedPulverizer's own javadoc): Blue=Input, Orange=Output, Open=All - colors
        // verified against the decompiled TileFurnace/BlockMachine. Real TE's Furnace Output
        // badge is Orange (Config_4), not the Pulverizer's Red primary-output Config_2, since
        // the Furnace only ever has the one generic output. TopAll/BottomAll/SideAll are the
        // exact same files the Pulverizer uses - the "All" badge is just real TE's Config_Open
        // dot composited over the same shared Machine_Top/Bottom/Side.png casing either way.
        iconsTop[TileAdvancedFurnace.SIDE_MODE_INPUT] = register.registerIcon(ThermalADD.MODID + ":TopInput");
        iconsTop[TileAdvancedFurnace.SIDE_MODE_OUTPUT] = register.registerIcon(ThermalADD.MODID + ":TopOutput");
        iconsTop[TileAdvancedFurnace.SIDE_MODE_ALL] = register.registerIcon(ThermalADD.MODID + ":TopAll");
        iconsBottom[TileAdvancedFurnace.SIDE_MODE_INPUT] = register.registerIcon(ThermalADD.MODID + ":BottomInput");
        iconsBottom[TileAdvancedFurnace.SIDE_MODE_OUTPUT] = register.registerIcon(ThermalADD.MODID + ":BottomOutput");
        iconsBottom[TileAdvancedFurnace.SIDE_MODE_ALL] = register.registerIcon(ThermalADD.MODID + ":BottomAll");
        iconsSide[TileAdvancedFurnace.SIDE_MODE_INPUT] = register.registerIcon(ThermalADD.MODID + ":SideInput");
        iconsSide[TileAdvancedFurnace.SIDE_MODE_OUTPUT] = register.registerIcon(ThermalADD.MODID + ":SideOutput");
        iconsSide[TileAdvancedFurnace.SIDE_MODE_ALL] = register.registerIcon(ThermalADD.MODID + ":SideAll");
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        if (side == 0) {
            return iconsBottom[TileAdvancedFurnace.SIDE_MODE_DISABLED];
        }
        if (side == 1) {
            return iconsTop[TileAdvancedFurnace.SIDE_MODE_DISABLED];
        }
        return side == meta ? iconFaceIdle : iconsSide[TileAdvancedFurnace.SIDE_MODE_DISABLED];
    }

    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        TileEntity te = world.getTileEntity(x, y, z);
        int facing = te instanceof TileAdvancedFurnace ? ((TileAdvancedFurnace) te).getFacing() : -1;
        if (side == facing) {
            boolean active = te instanceof TileAdvancedFurnace && ((TileAdvancedFurnace) te).isActive();
            return active ? iconFaceActive : iconFaceIdle;
        }

        int mode = te instanceof TileAdvancedFurnace
                ? ((TileAdvancedFurnace) te).getSideMode(side)
                : TileAdvancedFurnace.SIDE_MODE_DISABLED;
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
        int meta;
        switch (rotation) {
            case 0:
                meta = 2;
                break;
            case 1:
                meta = 5;
                break;
            case 2:
                meta = 3;
                break;
            default:
                meta = 4;
                break;
        }
        world.setBlockMetadataWithNotify(x, y, z, meta, 2);
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileAdvancedFurnace) {
            TileAdvancedFurnace tile = (TileAdvancedFurnace) te;
            tile.setFacing(meta);
            tile.setDefaultSides();
            if (stack.hasDisplayName()) {
                tile.setCustomName(stack.getDisplayName());
            }
            if (!world.isRemote) {
                if (stack.hasTagCompound() && stack.getTagCompound().hasKey("Augments")) {
                    tile.readAugmentsFromNBT(stack.getTagCompound());
                } else {
                    tile.installDefaultAugments();
                }
                MachineDismantle.restoreSidesAndEnergy(stack, tile);
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

    /** Comparator support - see BlockAdvancedPulverizer for why no explicit notification is needed. */
    @Override
    public boolean hasComparatorInputOverride() {
        return true;
    }

    @Override
    public int getComparatorInputOverride(World world, int x, int y, int z, int side) {
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof TileAdvancedFurnace ? ((TileAdvancedFurnace) te).getComparatorSignal() : 0;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileAdvancedFurnace();
    }

    /**
     * Crescent Hammer support, matching real Thermal Expansion's own gestures: a plain click
     * rotates the machine's facing, a sneaking click dismantles it into an item that keeps its
     * augments, side configuration and buffered RF. Side configuration itself stays GUI-only,
     * exactly as in real TE.
     */
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
                        if (te instanceof TileAdvancedFurnace) {
                            TileAdvancedFurnace tile = (TileAdvancedFurnace) te;
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
            player.openGui(ThermalADD.instance, ThermalADD.GUI_ID_ADVANCED_FURNACE, world, x, y, z);
        }
        return true;
    }

    private static int nextFacing(int facing) {
        int[] order = TileAdvancedFurnace.FACING_META;
        for (int i = 0; i < order.length; i++) {
            if (order[i] == facing) {
                return order[(i + 1) % order.length];
            }
        }
        return order[0];
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileAdvancedFurnace) {
            TileAdvancedFurnace tile = (TileAdvancedFurnace) te;

            // Augments are NOT spilled here - they travel inside the dropped block item's own NBT
            // instead (see getDrops), together with the side configuration and the energy buffer.
            for (int i = 0; i < tile.getSizeInventory(); i++) {
                if (i >= TileAdvancedFurnace.AUGMENT_START
                        && i < TileAdvancedFurnace.AUGMENT_START + TileAdvancedFurnace.AUGMENT_SLOTS) {
                    continue;
                }
                ItemStack stack = tile.getStackInSlot(i);
                if (stack != null) {
                    float rx = world.rand.nextFloat() * 0.8F + 0.1F;
                    float ry = world.rand.nextFloat() * 0.8F + 0.1F;
                    float rz = world.rand.nextFloat() * 0.8F + 0.1F;
                    EntityItem entityItem = new EntityItem(world, x + rx, y + ry, z + rz, stack.copy());
                    world.spawnEntityInWorld(entityItem);
                }
            }
            tile.clearContentsOnBreak();
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    /**
     * The single dropped block item carries the machine's augments, side configuration and stored
     * RF, read straight off the live tile - see MachineDismantle for why it is read here rather
     * than handed over from breakBlock.
     */
    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
        ArrayList<ItemStack> drops = new ArrayList<ItemStack>();
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileAdvancedFurnace) {
            drops.add(MachineDismantle.createDrop(this, damageDropped(metadata), (TileAdvancedFurnace) te));
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
}
