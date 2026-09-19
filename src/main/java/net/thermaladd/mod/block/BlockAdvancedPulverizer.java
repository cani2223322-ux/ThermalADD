package net.thermaladd.mod.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import cofh.api.item.IToolHammer;
import net.thermaladd.mod.ThermalADD;
import net.thermaladd.mod.init.ModCreativeTab;
import net.thermaladd.mod.tileentity.TileAdvancedPulverizer;

/**
 * Reuses Thermal Expansion's own real machine-casing and Pulverizer textures directly from
 * its resource domain (top/bottom/side casing + the Pulverizer's own idle/active face icon) -
 * since ThermalExpansion is a hard, mandatory dependency of this addon (see the mod's
 * {@code required-after} declaration), those textures are guaranteed to be present, and this
 * gives the block a pixel-identical Thermal Expansion look for free.
 */
public class BlockAdvancedPulverizer extends BlockContainer {

    private IIcon iconFaceIdle;
    private IIcon iconFaceActive;
    /** Indexed by SIDE_MODE_* (Auto/Input/Output/Disabled) - Auto is just the plain casing texture for that face. */
    private final IIcon[] iconsTop = new IIcon[4];
    private final IIcon[] iconsBottom = new IIcon[4];
    private final IIcon[] iconsSide = new IIcon[4];

    public BlockAdvancedPulverizer() {
        super(Material.iron);
        setBlockName("advancedPulverizer");
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(5.0F);
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
    }

    @Override
    public void registerBlockIcons(IIconRegister register) {
        // assets/thermalexpansion/textures/blocks/machine/Machine_*.png - the "blocks/" part
        // of the path is implicit for a block IIconRegister, so it is left out of the string.
        iconsTop[TileAdvancedPulverizer.SIDE_MODE_AUTO] = register.registerIcon("thermalexpansion:machine/Machine_Top");
        iconsBottom[TileAdvancedPulverizer.SIDE_MODE_AUTO] = register.registerIcon("thermalexpansion:machine/Machine_Bottom");
        iconsSide[TileAdvancedPulverizer.SIDE_MODE_AUTO] = register.registerIcon("thermalexpansion:machine/Machine_Side");
        iconFaceIdle = register.registerIcon("thermalexpansion:machine/Machine_Face_Pulverizer");
        iconFaceActive = register.registerIcon("thermalexpansion:machine/Machine_Active_Pulverizer");
        // Composited once per face (Machine_Top/Bottom/Side.png + the real TE Config_Blue/
        // Orange/Red.png connection badges - see thermaladd:blocks/{Top,Bottom,Side}{Input,
        // Output,Disabled}.png) so the block shows, on EVERY face including top/bottom, exactly
        // what the Configuration tab says for it - the same "read the face, not just the GUI"
        // workflow real Thermal Expansion machines use.
        iconsTop[TileAdvancedPulverizer.SIDE_MODE_INPUT] = register.registerIcon(ThermalADD.MODID + ":TopInput");
        iconsTop[TileAdvancedPulverizer.SIDE_MODE_OUTPUT] = register.registerIcon(ThermalADD.MODID + ":TopOutput");
        iconsTop[TileAdvancedPulverizer.SIDE_MODE_DISABLED] = register.registerIcon(ThermalADD.MODID + ":TopDisabled");
        iconsBottom[TileAdvancedPulverizer.SIDE_MODE_INPUT] = register.registerIcon(ThermalADD.MODID + ":BottomInput");
        iconsBottom[TileAdvancedPulverizer.SIDE_MODE_OUTPUT] = register.registerIcon(ThermalADD.MODID + ":BottomOutput");
        iconsBottom[TileAdvancedPulverizer.SIDE_MODE_DISABLED] = register.registerIcon(ThermalADD.MODID + ":BottomDisabled");
        iconsSide[TileAdvancedPulverizer.SIDE_MODE_INPUT] = register.registerIcon(ThermalADD.MODID + ":SideInput");
        iconsSide[TileAdvancedPulverizer.SIDE_MODE_OUTPUT] = register.registerIcon(ThermalADD.MODID + ":SideOutput");
        iconsSide[TileAdvancedPulverizer.SIDE_MODE_DISABLED] = register.registerIcon(ThermalADD.MODID + ":SideDisabled");
    }

    /** Inventory / item-form rendering: no world context, so always show the idle face and plain casing. */
    @Override
    public IIcon getIcon(int side, int meta) {
        if (side == 0) {
            return iconsBottom[TileAdvancedPulverizer.SIDE_MODE_AUTO];
        }
        if (side == 1) {
            return iconsTop[TileAdvancedPulverizer.SIDE_MODE_AUTO];
        }
        return side == meta ? iconFaceIdle : iconsSide[TileAdvancedPulverizer.SIDE_MODE_AUTO];
    }

    /**
     * In-world rendering: swaps the front face to the "working" texture while the tile is
     * active, and - independently of that, on EVERY face including top/bottom - swaps in a
     * connection badge (blue/orange/red) whenever that side's mode isn't Auto, exactly
     * mirroring what TileMachineBase#getTexture does in real Thermal Expansion (side icon
     * driven by sideCache for all 6 sides, not just the 4 "walls"). Auto mode intentionally
     * shows the plain casing - only a side actually reconfigured away from the default gets
     * decorated.
     */
    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        TileEntity te = world.getTileEntity(x, y, z);
        int facing = te instanceof TileAdvancedPulverizer ? ((TileAdvancedPulverizer) te).getFacing() : -1;
        if (side == facing) {
            boolean active = te instanceof TileAdvancedPulverizer && ((TileAdvancedPulverizer) te).isActive();
            return active ? iconFaceActive : iconFaceIdle;
        }

        int mode = te instanceof TileAdvancedPulverizer
                ? ((TileAdvancedPulverizer) te).getSideMode(side)
                : TileAdvancedPulverizer.SIDE_MODE_AUTO;
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
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileAdvancedPulverizer) {
            TileAdvancedPulverizer tile = (TileAdvancedPulverizer) te;
            tile.setFacing(meta);
            if (!world.isRemote) {
                tile.installDefaultAugments();
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
        return new TileAdvancedPulverizer();
    }

    /**
     * Crescent Hammer support: a click with one held always rotates the machine's facing -
     * matching real Thermal Expansion's own TileReconfigurable#onWrench, which unconditionally
     * calls rotateBlock() regardless of sneaking. Side configuration (Auto/Input/Output/
     * Disabled) is a GUI Configuration-tab-only feature in real TE - there is no wrench
     * gesture for it - so this doesn't branch on sneak state at all. Any other held item (or
     * an empty hand) just opens the GUI, as usual.
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
                    if (te instanceof TileAdvancedPulverizer) {
                        TileAdvancedPulverizer tile = (TileAdvancedPulverizer) te;
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
            player.openGui(ThermalADD.instance, ThermalADD.GUI_ID_ADVANCED_PULVERIZER, world, x, y, z);
        }
        return true;
    }

    private static int nextFacing(int facing) {
        int[] order = TileAdvancedPulverizer.FACING_META;
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
        if (te instanceof TileAdvancedPulverizer) {
            TileAdvancedPulverizer tile = (TileAdvancedPulverizer) te;
            for (int i = 0; i < tile.getSizeInventory(); i++) {
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
}
