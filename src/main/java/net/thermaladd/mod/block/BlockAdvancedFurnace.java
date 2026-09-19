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
import net.thermaladd.mod.tileentity.TileAdvancedFurnace;
import net.thermaladd.mod.util.PendingAugmentDrops;

/** Same design as {@link BlockAdvancedPulverizer}: real Thermal Expansion casing/face textures, Crescent Hammer support, Active/Inactive face swap. */
public class BlockAdvancedFurnace extends BlockContainer {

    private IIcon iconFaceIdle;
    private IIcon iconFaceActive;
    /** Indexed by SIDE_MODE_* (Auto/Input/Output/Disabled) - Auto is just the plain casing texture for that face. */
    private final IIcon[] iconsTop = new IIcon[4];
    private final IIcon[] iconsBottom = new IIcon[4];
    private final IIcon[] iconsSide = new IIcon[4];

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
        iconsTop[TileAdvancedFurnace.SIDE_MODE_AUTO] = register.registerIcon("thermalexpansion:machine/Machine_Top");
        iconsBottom[TileAdvancedFurnace.SIDE_MODE_AUTO] = register.registerIcon("thermalexpansion:machine/Machine_Bottom");
        iconsSide[TileAdvancedFurnace.SIDE_MODE_AUTO] = register.registerIcon("thermalexpansion:machine/Machine_Side");
        iconFaceIdle = register.registerIcon("thermalexpansion:machine/Machine_Face_Furnace");
        iconFaceActive = register.registerIcon("thermalexpansion:machine/Machine_Active_Furnace");
        iconsTop[TileAdvancedFurnace.SIDE_MODE_INPUT] = register.registerIcon(ThermalADD.MODID + ":TopInput");
        iconsTop[TileAdvancedFurnace.SIDE_MODE_OUTPUT] = register.registerIcon(ThermalADD.MODID + ":TopOutput");
        iconsTop[TileAdvancedFurnace.SIDE_MODE_DISABLED] = register.registerIcon(ThermalADD.MODID + ":TopDisabled");
        iconsBottom[TileAdvancedFurnace.SIDE_MODE_INPUT] = register.registerIcon(ThermalADD.MODID + ":BottomInput");
        iconsBottom[TileAdvancedFurnace.SIDE_MODE_OUTPUT] = register.registerIcon(ThermalADD.MODID + ":BottomOutput");
        iconsBottom[TileAdvancedFurnace.SIDE_MODE_DISABLED] = register.registerIcon(ThermalADD.MODID + ":BottomDisabled");
        iconsSide[TileAdvancedFurnace.SIDE_MODE_INPUT] = register.registerIcon(ThermalADD.MODID + ":SideInput");
        iconsSide[TileAdvancedFurnace.SIDE_MODE_OUTPUT] = register.registerIcon(ThermalADD.MODID + ":SideOutput");
        iconsSide[TileAdvancedFurnace.SIDE_MODE_DISABLED] = register.registerIcon(ThermalADD.MODID + ":SideDisabled");
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        if (side == 0) {
            return iconsBottom[TileAdvancedFurnace.SIDE_MODE_AUTO];
        }
        if (side == 1) {
            return iconsTop[TileAdvancedFurnace.SIDE_MODE_AUTO];
        }
        return side == meta ? iconFaceIdle : iconsSide[TileAdvancedFurnace.SIDE_MODE_AUTO];
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
                : TileAdvancedFurnace.SIDE_MODE_AUTO;
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
            if (!world.isRemote) {
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
        return new TileAdvancedFurnace();
    }

    /**
     * Crescent Hammer support: a click with one held always rotates the machine's facing -
     * matching real Thermal Expansion's own TileReconfigurable#onWrench (unconditional
     * rotateBlock(), no sneak branching). Side configuration is GUI-only in real TE.
     */
    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side,
            float hitX, float hitY, float hitZ) {
        ItemStack held = player.getHeldItem();
        if (held != null && held.getItem() instanceof IToolHammer) {
            IToolHammer hammer = (IToolHammer) held.getItem();
            if (hammer.isUsable(held, player, x, y, z)) {
                if (!world.isRemote) {
                    TileEntity te = world.getTileEntity(x, y, z);
                    if (te instanceof TileAdvancedFurnace) {
                        TileAdvancedFurnace tile = (TileAdvancedFurnace) te;
                        int next = nextFacing(tile.getFacing());
                        world.setBlockMetadataWithNotify(x, y, z, next, 3);
                        tile.setFacing(next);
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

            NBTTagCompound augNbt = tile.writeAugmentsToNBT(new NBTTagCompound());
            PendingAugmentDrops.put(x, y, z, augNbt);

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
