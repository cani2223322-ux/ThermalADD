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

import cofh.api.item.IToolHammer;
import net.thermaladd.mod.ThermalADD;
import net.thermaladd.mod.init.ModCreativeTab;
import net.thermaladd.mod.tileentity.TileImprovedAssembler;
import net.thermaladd.mod.util.PendingAugmentDrops;

/**
 * Reuses Thermal Expansion's own real machine-casing and Assembler textures directly from
 * its resource domain, the same way {@code BlockAdvancedPulverizer} does - now that this mod
 * is merged into ThermalADD, ThermalExpansion is a hard dependency, so the bundled duplicate
 * PNGs the standalone ImprovedAssembler mod used to ship are no longer necessary.
 */
public class BlockImprovedAssembler extends BlockContainer {

    private IIcon iconFace;
    /** Indexed by SIDE_MODE_* (Auto/Input/Output/Disabled) - Auto is just the plain casing texture for that face. */
    private final IIcon[] iconsTop = new IIcon[4];
    private final IIcon[] iconsBottom = new IIcon[4];
    private final IIcon[] iconsSide = new IIcon[4];

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
        iconsTop[TileImprovedAssembler.SIDE_MODE_AUTO] = register.registerIcon("thermalexpansion:machine/Machine_Top");
        iconsBottom[TileImprovedAssembler.SIDE_MODE_AUTO] = register.registerIcon("thermalexpansion:machine/Machine_Bottom");
        iconsSide[TileImprovedAssembler.SIDE_MODE_AUTO] = register.registerIcon("thermalexpansion:machine/Machine_Side");
        iconFace = register.registerIcon("thermalexpansion:machine/Machine_Face_Assembler");
        iconsTop[TileImprovedAssembler.SIDE_MODE_INPUT] = register.registerIcon(ThermalADD.MODID + ":TopInput");
        iconsTop[TileImprovedAssembler.SIDE_MODE_OUTPUT] = register.registerIcon(ThermalADD.MODID + ":TopOutput");
        iconsTop[TileImprovedAssembler.SIDE_MODE_DISABLED] = register.registerIcon(ThermalADD.MODID + ":TopDisabled");
        iconsBottom[TileImprovedAssembler.SIDE_MODE_INPUT] = register.registerIcon(ThermalADD.MODID + ":BottomInput");
        iconsBottom[TileImprovedAssembler.SIDE_MODE_OUTPUT] = register.registerIcon(ThermalADD.MODID + ":BottomOutput");
        iconsBottom[TileImprovedAssembler.SIDE_MODE_DISABLED] = register.registerIcon(ThermalADD.MODID + ":BottomDisabled");
        iconsSide[TileImprovedAssembler.SIDE_MODE_INPUT] = register.registerIcon(ThermalADD.MODID + ":SideInput");
        iconsSide[TileImprovedAssembler.SIDE_MODE_OUTPUT] = register.registerIcon(ThermalADD.MODID + ":SideOutput");
        iconsSide[TileImprovedAssembler.SIDE_MODE_DISABLED] = register.registerIcon(ThermalADD.MODID + ":SideDisabled");
    }

    /** Inventory / item-form rendering: no world context, so always show the plain face. */
    @Override
    public IIcon getIcon(int side, int meta) {
        if (side == 0) {
            return iconsBottom[TileImprovedAssembler.SIDE_MODE_AUTO];
        }
        if (side == 1) {
            return iconsTop[TileImprovedAssembler.SIDE_MODE_AUTO];
        }
        return side == meta ? iconFace : iconsSide[TileImprovedAssembler.SIDE_MODE_AUTO];
    }

    /**
     * In-world rendering: swaps any face, including top/bottom, to a connection badge (blue/
     * orange/red) whenever that side's mode isn't Auto - same as
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
                : TileImprovedAssembler.SIDE_MODE_AUTO;
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
                if (stack.hasTagCompound() && stack.getTagCompound().hasKey("Augments")) {
                    tile.readAugmentsFromNBT(stack.getTagCompound());
                } else {
                    tile.installDefaultAugments();
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
                    int facing = world.getBlockMetadata(x, y, z);
                    world.setBlockMetadataWithNotify(x, y, z, nextFacing(facing), 3);
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

            NBTTagCompound augNbt = tile.writeAugmentsToNBT(new NBTTagCompound());
            PendingAugmentDrops.put(x, y, z, augNbt);

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
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
        ItemStack drop = new ItemStack(Item.getItemFromBlock(this), 1, damageDropped(metadata));
        NBTTagCompound augNbt = PendingAugmentDrops.take(x, y, z);
        if (augNbt != null && augNbt.hasKey("Augments")) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setTag("Augments", augNbt.getTag("Augments"));
            drop.setTagCompound(tag);
        }
        ArrayList<ItemStack> drops = new ArrayList<ItemStack>();
        drops.add(drop);
        return drops;
    }
}
