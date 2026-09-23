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
import net.thermaladd.mod.tileentity.TileImprovedAssembler;
import net.thermaladd.mod.util.MachineDismantle;

/**
 * Reuses Thermal Expansion's own real machine-casing and Assembler textures directly from
 * its resource domain, the same way {@code BlockAdvancedPulverizer} does - now that this mod
 * is merged into ThermalADD, ThermalExpansion is a hard dependency, so the bundled duplicate
 * PNGs the standalone ImprovedAssembler mod used to ship are no longer necessary.
 */
public class BlockImprovedAssembler extends BlockContainer implements IDismantleable {

    private IIcon iconFace;
    /**
     * Indexed by SIDE_MODE_* (Disabled/Input/Output/InputRow1/InputRow2/All) - Disabled is just
     * the plain casing texture for that face, matching real Thermal Expansion's own blank
     * Config_None badge.
     */
    private final IIcon[] iconsTop = new IIcon[TileImprovedAssembler.SIDE_MODE_COUNT];
    private final IIcon[] iconsBottom = new IIcon[TileImprovedAssembler.SIDE_MODE_COUNT];
    private final IIcon[] iconsSide = new IIcon[TileImprovedAssembler.SIDE_MODE_COUNT];

    public BlockImprovedAssembler() {
        super(Material.iron);
        setBlockName("improvedAssembler");
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(5.0F);
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
    }

    @Override
    public void registerBlockIcons(IIconRegister register) {
        iconsTop[TileImprovedAssembler.SIDE_MODE_DISABLED] = register.registerIcon("thermalexpansion:machine/Machine_Top");
        iconsBottom[TileImprovedAssembler.SIDE_MODE_DISABLED] = register.registerIcon("thermalexpansion:machine/Machine_Bottom");
        iconsSide[TileImprovedAssembler.SIDE_MODE_DISABLED] = register.registerIcon("thermalexpansion:machine/Machine_Side");
        iconFace = register.registerIcon("thermalexpansion:machine/Machine_Face_Assembler");
        // Same composited connection badges as the other 2 machines (see
        // BlockAdvancedPulverizer's javadoc): Blue=Input(whole buffer), Orange=Output,
        // Green=Input Row 1, Purple=Input Row 2, Open=All - colors verified against the
        // decompiled TileAssembler/BlockMachine. Real TE's Assembler is the only one of the
        // 3 machines with a material buffer split into individually addressable rows.
        iconsTop[TileImprovedAssembler.SIDE_MODE_INPUT] = register.registerIcon(ThermalADD.MODID + ":TopInput");
        iconsTop[TileImprovedAssembler.SIDE_MODE_OUTPUT] = register.registerIcon(ThermalADD.MODID + ":TopOutput");
        iconsTop[TileImprovedAssembler.SIDE_MODE_INPUT_ROW1] = register.registerIcon(ThermalADD.MODID + ":TopInputRow1");
        iconsTop[TileImprovedAssembler.SIDE_MODE_INPUT_ROW2] = register.registerIcon(ThermalADD.MODID + ":TopInputRow2");
        iconsTop[TileImprovedAssembler.SIDE_MODE_ALL] = register.registerIcon(ThermalADD.MODID + ":TopAll");
        iconsBottom[TileImprovedAssembler.SIDE_MODE_INPUT] = register.registerIcon(ThermalADD.MODID + ":BottomInput");
        iconsBottom[TileImprovedAssembler.SIDE_MODE_OUTPUT] = register.registerIcon(ThermalADD.MODID + ":BottomOutput");
        iconsBottom[TileImprovedAssembler.SIDE_MODE_INPUT_ROW1] = register.registerIcon(ThermalADD.MODID + ":BottomInputRow1");
        iconsBottom[TileImprovedAssembler.SIDE_MODE_INPUT_ROW2] = register.registerIcon(ThermalADD.MODID + ":BottomInputRow2");
        iconsBottom[TileImprovedAssembler.SIDE_MODE_ALL] = register.registerIcon(ThermalADD.MODID + ":BottomAll");
        iconsSide[TileImprovedAssembler.SIDE_MODE_INPUT] = register.registerIcon(ThermalADD.MODID + ":SideInput");
        iconsSide[TileImprovedAssembler.SIDE_MODE_OUTPUT] = register.registerIcon(ThermalADD.MODID + ":SideOutput");
        iconsSide[TileImprovedAssembler.SIDE_MODE_INPUT_ROW1] = register.registerIcon(ThermalADD.MODID + ":SideInputRow1");
        iconsSide[TileImprovedAssembler.SIDE_MODE_INPUT_ROW2] = register.registerIcon(ThermalADD.MODID + ":SideInputRow2");
        iconsSide[TileImprovedAssembler.SIDE_MODE_ALL] = register.registerIcon(ThermalADD.MODID + ":SideAll");
    }

    /** Inventory / item-form rendering: no world context, so always show the plain face. */
    @Override
    public IIcon getIcon(int side, int meta) {
        if (side == 0) {
            return iconsBottom[TileImprovedAssembler.SIDE_MODE_DISABLED];
        }
        if (side == 1) {
            return iconsTop[TileImprovedAssembler.SIDE_MODE_DISABLED];
        }
        // meta 0 is the item form (inventory, hand, dropped): show the front on side 3, as vanilla furnaces do.
        return side == meta || (meta == 0 && side == 3) ? iconFace : iconsSide[TileImprovedAssembler.SIDE_MODE_DISABLED];
    }

    /**
     * In-world rendering: swaps any face, including top/bottom, to a connection badge whenever
     * that side's mode isn't Disabled - same as
     * {@code BlockAdvancedPulverizer}/{@code BlockAdvancedFurnace}, wired to this block's own
     * side config (its facing lives in block metadata, not a separate tile field).
     */
    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        int facing = world.getBlockMetadata(x, y, z);
        if (side == facing) {
            return iconFace;
        }

        TileEntity te = world.getTileEntity(x, y, z);
        int mode = te instanceof TileImprovedAssembler
                ? ((TileImprovedAssembler) te).getSideMode(side)
                : TileImprovedAssembler.SIDE_MODE_DISABLED;
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
        int facing = MathHelper.floor_double(placer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        int meta;
        switch (facing) {
            case 0:
                meta = 2; // north
                break;
            case 1:
                meta = 5; // east
                break;
            case 2:
                meta = 3; // south
                break;
            default:
                meta = 4; // west
                break;
        }
        world.setBlockMetadataWithNotify(x, y, z, meta, 2);
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileImprovedAssembler) {
                TileImprovedAssembler tile = (TileImprovedAssembler) te;
                tile.setDefaultSides();
                if (stack.hasDisplayName()) {
                    tile.setCustomName(stack.getDisplayName());
                }
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
        return te instanceof TileImprovedAssembler ? ((TileImprovedAssembler) te).getComparatorSignal() : 0;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileImprovedAssembler();
    }

    private static final int[] FACING_META = {2, 5, 3, 4};

    /**
     * Crescent Hammer support - this block never had it before, even though its own
     * Configuration tab depends on facing (Left/Right/Front/Back rotate with it) and had no
     * way to change facing after placement. A click with one held always rotates, matching
     * real Thermal Expansion's TileReconfigurable#onWrench (unconditional, no sneak branch).
     */
    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side,
            float hitX, float hitY, float hitZ) {
        ItemStack held = player.getHeldItem();
        if (held != null && held.getItem() instanceof IToolHammer) {
            IToolHammer hammer = (IToolHammer) held.getItem();
            if (hammer.isUsable(held, player, x, y, z)) {
                if (!world.isRemote) {
                    // Sneak+wrench dismantles (real TE's own gesture); a plain click rotates.
                    if (player.isSneaking()) {
                        dismantleBlock(player, world, x, y, z, false);
                    } else {
                        int facing = world.getBlockMetadata(x, y, z);
                        int next = nextFacing(facing);
                        // Sides first, while the metadata still holds the old facing.
                        TileEntity te = world.getTileEntity(x, y, z);
                        if (te instanceof TileImprovedAssembler) {
                            ((TileImprovedAssembler) te).rotateSides(facing, next);
                        }
                        world.setBlockMetadataWithNotify(x, y, z, next, 3);
                    }
                    hammer.toolUsed(held, player, x, y, z);
                }
                return true;
            }
        }

        if (!world.isRemote) {
            player.openGui(ThermalADD.instance, ThermalADD.GUI_ID_IMPROVED_ASSEMBLER, world, x, y, z);
        }
        return true;
    }

    private static int nextFacing(int facing) {
        for (int i = 0; i < FACING_META.length; i++) {
            if (FACING_META[i] == facing) {
                return FACING_META[(i + 1) % FACING_META.length];
            }
        }
        return FACING_META[0];
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileImprovedAssembler) {
            TileImprovedAssembler tile = (TileImprovedAssembler) te;

            // Augments are NOT spilled here - they travel inside the dropped block item's own NBT
            // instead (see getDrops), together with the side configuration and the energy buffer.
            for (int i = 0; i < tile.getSizeInventory(); i++) {
                if (i >= TileImprovedAssembler.AUGMENT_START
                        && i < TileImprovedAssembler.AUGMENT_START + TileImprovedAssembler.AUGMENT_SLOTS) {
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

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
        ArrayList<ItemStack> drops = new ArrayList<ItemStack>();
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileImprovedAssembler) {
            drops.add(MachineDismantle.createDrop(this, damageDropped(metadata), (TileImprovedAssembler) te));
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
