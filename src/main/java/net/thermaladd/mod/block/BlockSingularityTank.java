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
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fluids.IFluidHandler;

import cofh.api.block.IDismantleable;
import cofh.api.item.IToolHammer;
import cofh.lib.util.helpers.FluidHelper;
import cofh.lib.util.helpers.ItemHelper;
import net.thermaladd.mod.ThermalADD;
import net.thermaladd.mod.init.ModCreativeTab;
import net.thermaladd.mod.tileentity.TileSingularityTank;
import net.thermaladd.mod.util.MachineDismantle;

/**
 * The tank's frame is drawn as an ordinary block inset like TE's own (2 px on every vertical side),
 * with this mod's recoloured tank textures - the glass is cut out, so the fluid and the inside of
 * the frame, drawn by RenderSingularityTank, show through. Blue frame = hold, orange = pour down.
 */
public class BlockSingularityTank extends BlockContainer implements IDismantleable {

    private static final float INSET = 0.125F;

    private final IIcon[] iconsSide = new IIcon[2];
    private final IIcon[] iconsTop = new IIcon[2];
    private final IIcon[] iconsBottom = new IIcon[2];

    public BlockSingularityTank() {
        super(Material.glass);
        setBlockName("singularityTank");
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(15.0F);
        setResistance(120.0F);
        setStepSound(soundTypeGlass);
        setBlockBounds(INSET, 0F, INSET, 1F - INSET, 1F, 1F - INSET);
    }

    @Override
    public void registerBlockIcons(IIconRegister register) {
        String[] colors = {"Blue", "Orange"};
        for (int i = 0; i < 2; i++) {
            iconsSide[i] = register.registerIcon(ThermalADD.MODID + ":tank/SingularityTank_Side_" + colors[i]);
            iconsTop[i] = register.registerIcon(ThermalADD.MODID + ":tank/SingularityTank_Top_" + colors[i]);
            iconsBottom[i] = register.registerIcon(ThermalADD.MODID + ":tank/SingularityTank_Bottom_" + colors[i]);
        }
    }

    public IIcon getFrameIcon(int side, int mode) {
        int m = mode == 1 ? 1 : 0;
        if (side == 0) {
            return iconsBottom[m];
        }
        return side == 1 ? iconsTop[m] : iconsSide[m];
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        return getFrameIcon(side, 0);
    }

    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        TileEntity te = world.getTileEntity(x, y, z);
        return getFrameIcon(side, te instanceof TileSingularityTank ? ((TileSingularityTank) te).getMode() : 0);
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    /** Stacked tanks still draw their touching top/bottom faces - they are frames, not solid walls. */
    @Override
    public boolean shouldSideBeRendered(IBlockAccess world, int x, int y, int z, int side) {
        return true;
    }

    @Override
    public int getLightValue(IBlockAccess world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof TileSingularityTank ? ((TileSingularityTank) te).getLightValue() : 0;
    }

    @Override
    public boolean hasComparatorInputOverride() {
        return true;
    }

    @Override
    public int getComparatorInputOverride(World world, int x, int y, int z, int side) {
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof TileSingularityTank ? ((TileSingularityTank) te).getComparatorSignal() : 0;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileSingularityTank();
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileSingularityTank && stack.hasTagCompound() && !world.isRemote) {
            ((TileSingularityTank) te).readFromItem(stack.getTagCompound());
            world.markBlockForUpdate(x, y, z);
        }
    }

    /**
     * Wrench: click toggles pour-down, sneak+click dismantles. Otherwise TE's own bucket handling:
     * empty a held container into the tank, or fill it from the tank.
     */
    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side,
            float hitX, float hitY, float hitZ) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileSingularityTank)) {
            return false;
        }
        TileSingularityTank tile = (TileSingularityTank) te;
        ItemStack held = player.getHeldItem();
        if (held != null && held.getItem() instanceof IToolHammer) {
            IToolHammer hammer = (IToolHammer) held.getItem();
            if (hammer.isUsable(held, player, x, y, z)) {
                if (!world.isRemote) {
                    if (player.isSneaking()) {
                        dismantleBlock(player, world, x, y, z, false);
                    } else {
                        tile.toggleMode();
                    }
                    hammer.toolUsed(held, player, x, y, z);
                }
                return true;
            }
        }
        if (FluidHelper.fillHandlerWithContainer(world, (IFluidHandler) tile, player)) {
            return true;
        }
        if (FluidHelper.fillContainerFromHandler(world, (IFluidHandler) tile, player, tile.getTankFluid())) {
            return true;
        }
        return ItemHelper.isPlayerHoldingFluidContainer(player);
    }

    /** One item that carries the fluid and the mode - see TileSingularityTank#writeToItem. */
    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
        ArrayList<ItemStack> drops = new ArrayList<ItemStack>();
        ItemStack drop = new ItemStack(Item.getItemFromBlock(this), 1, 0);
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileSingularityTank) {
            NBTTagCompound tag = new NBTTagCompound();
            ((TileSingularityTank) te).writeToItem(tag);
            if (!tag.hasNoTags()) {
                drop.setTagCompound(tag);
            }
        }
        drops.add(drop);
        return drops;
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
