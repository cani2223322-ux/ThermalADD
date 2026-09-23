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
import net.thermaladd.mod.tileentity.TileAdvancedPulverizer;
import net.thermaladd.mod.util.MachineDismantle;

/**
 * Reuses Thermal Expansion's own real machine-casing and Pulverizer textures directly from
 * its resource domain (top/bottom/side casing + the Pulverizer's own idle/active face icon) -
 * since ThermalExpansion is a hard, mandatory dependency of this addon (see the mod's
 * {@code required-after} declaration), those textures are guaranteed to be present, and this
 * gives the block a pixel-identical Thermal Expansion look for free.
 */
public class BlockAdvancedPulverizer extends BlockContainer implements IDismantleable {

    private IIcon iconFaceIdle;
    private IIcon iconFaceActive;
    /**
     * Indexed by SIDE_MODE_* (Disabled/Input/OutputPrimary/OutputSecondary/OutputBoth/All) -
     * Disabled is just the plain casing texture for that face, exactly like real Thermal
     * Expansion's own Config_None badge (blank/transparent, so the plain casing shows through).
     */
    private final IIcon[] iconsTop = new IIcon[TileAdvancedPulverizer.SIDE_MODE_COUNT];
    private final IIcon[] iconsBottom = new IIcon[TileAdvancedPulverizer.SIDE_MODE_COUNT];
    private final IIcon[] iconsSide = new IIcon[TileAdvancedPulverizer.SIDE_MODE_COUNT];

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
        iconsTop[TileAdvancedPulverizer.SIDE_MODE_DISABLED] = register.registerIcon("thermalexpansion:machine/Machine_Top");
        iconsBottom[TileAdvancedPulverizer.SIDE_MODE_DISABLED] = register.registerIcon("thermalexpansion:machine/Machine_Bottom");
        iconsSide[TileAdvancedPulverizer.SIDE_MODE_DISABLED] = register.registerIcon("thermalexpansion:machine/Machine_Side");
        iconFaceIdle = register.registerIcon("thermalexpansion:machine/Machine_Face_Pulverizer");
        iconFaceActive = register.registerIcon("thermalexpansion:machine/Machine_Active_Pulverizer");
        // Composited once per face (Machine_Top/Bottom/Side.png + real TE's own Config_Blue/
        // Red/Yellow/Orange/Open.png connection badges - see thermaladd:blocks/{Top,Bottom,
        // Side}{Input,OutputPrimary,OutputSecondary,OutputBoth,All}.png) so the block shows,
        // on EVERY face including top/bottom, exactly what the Configuration tab says for it -
        // the same "read the face, not just the GUI" workflow real Thermal Expansion machines
        // use. Colors verified against the decompiled TilePulverizer/BlockMachine: Blue=Input,
        // Red=Output(Primary), Yellow=Output(Secondary), Orange=Output(Both), Open=All - Disabled
        // has no badge of its own (real TE's Config_None is blank), so it just falls back to the
        // plain casing texture registered above.
        iconsTop[TileAdvancedPulverizer.SIDE_MODE_INPUT] = register.registerIcon(ThermalADD.MODID + ":TopInput");
        iconsTop[TileAdvancedPulverizer.SIDE_MODE_OUTPUT_PRIMARY] = register.registerIcon(ThermalADD.MODID + ":TopOutputPrimary");
        iconsTop[TileAdvancedPulverizer.SIDE_MODE_OUTPUT_SECONDARY] = register.registerIcon(ThermalADD.MODID + ":TopOutputSecondary");
        iconsTop[TileAdvancedPulverizer.SIDE_MODE_OUTPUT_BOTH] = register.registerIcon(ThermalADD.MODID + ":TopOutputBoth");
        iconsTop[TileAdvancedPulverizer.SIDE_MODE_ALL] = register.registerIcon(ThermalADD.MODID + ":TopAll");
        iconsBottom[TileAdvancedPulverizer.SIDE_MODE_INPUT] = register.registerIcon(ThermalADD.MODID + ":BottomInput");
        iconsBottom[TileAdvancedPulverizer.SIDE_MODE_OUTPUT_PRIMARY] = register.registerIcon(ThermalADD.MODID + ":BottomOutputPrimary");
        iconsBottom[TileAdvancedPulverizer.SIDE_MODE_OUTPUT_SECONDARY] = register.registerIcon(ThermalADD.MODID + ":BottomOutputSecondary");
        iconsBottom[TileAdvancedPulverizer.SIDE_MODE_OUTPUT_BOTH] = register.registerIcon(ThermalADD.MODID + ":BottomOutputBoth");
        iconsBottom[TileAdvancedPulverizer.SIDE_MODE_ALL] = register.registerIcon(ThermalADD.MODID + ":BottomAll");
        iconsSide[TileAdvancedPulverizer.SIDE_MODE_INPUT] = register.registerIcon(ThermalADD.MODID + ":SideInput");
        iconsSide[TileAdvancedPulverizer.SIDE_MODE_OUTPUT_PRIMARY] = register.registerIcon(ThermalADD.MODID + ":SideOutputPrimary");
        iconsSide[TileAdvancedPulverizer.SIDE_MODE_OUTPUT_SECONDARY] = register.registerIcon(ThermalADD.MODID + ":SideOutputSecondary");
        iconsSide[TileAdvancedPulverizer.SIDE_MODE_OUTPUT_BOTH] = register.registerIcon(ThermalADD.MODID + ":SideOutputBoth");
        iconsSide[TileAdvancedPulverizer.SIDE_MODE_ALL] = register.registerIcon(ThermalADD.MODID + ":SideAll");
    }

    /** Inventory / item-form rendering: no world context, so always show the idle face and plain casing. */
    @Override
    public IIcon getIcon(int side, int meta) {
        if (side == 0) {
            return iconsBottom[TileAdvancedPulverizer.SIDE_MODE_DISABLED];
        }
        if (side == 1) {
            return iconsTop[TileAdvancedPulverizer.SIDE_MODE_DISABLED];
        }
        return side == meta ? iconFaceIdle : iconsSide[TileAdvancedPulverizer.SIDE_MODE_DISABLED];
    }

    /**
     * In-world rendering: swaps the front face to the "working" texture while the tile is
     * active, and - independently of that, on EVERY face including top/bottom - swaps in a
     * connection badge (blue/red/yellow/orange/open) whenever that side's mode isn't Disabled,
     * exactly mirroring what TileMachineBase#getTexture does in real Thermal Expansion (side
     * icon driven by sideCache for all 6 sides, not just the 4 "walls"). Disabled intentionally
     * shows the plain casing - only a side actually reconfigured away from Off gets decorated.
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
                : TileAdvancedPulverizer.SIDE_MODE_DISABLED;
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
            tile.setDefaultSides();
            if (stack.hasDisplayName()) {
                tile.setCustomName(stack.getDisplayName());
            }
            if (!world.isRemote) {
                // A block picked back up after being broken carries its previous augments (and
                // only those) in its own NBT - only a genuinely fresh item (creative menu, a
                // freshly crafted one) has none and gets the real-TE default set instead.
                if (stack.hasTagCompound() && stack.getTagCompound().hasKey("Augments")) {
                    tile.readAugmentsFromNBT(stack.getTagCompound());
                } else {
                    tile.installDefaultAugments();
                }
                // After setDefaultSides()/installAugments() above, so a machine that was picked up
                // already configured comes back exactly as it was rather than reset to defaults.
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

    /**
     * Comparator support. The signal itself is defined by the tile (occupied processing lines,
     * scaled 1-15); no explicit comparator notification is needed anywhere, because every
     * inventory mutation already goes through TileEntity#markDirty, which vanilla routes into
     * World#markTileEntityChunkModified and from there into the comparator update path.
     */
    @Override
    public boolean hasComparatorInputOverride() {
        return true;
    }

    @Override
    public int getComparatorInputOverride(World world, int x, int y, int z, int side) {
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof TileAdvancedPulverizer ? ((TileAdvancedPulverizer) te).getComparatorSignal() : 0;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileAdvancedPulverizer();
    }

    /**
     * Crescent Hammer support, matching real Thermal Expansion's own gestures: a plain click
     * rotates the machine's facing (TileReconfigurable#onWrench calls rotateBlock() regardless of
     * sneaking), and a sneaking click dismantles it into an item that keeps its augments, side
     * configuration and buffered RF. Side configuration itself stays a GUI Configuration-tab
     * feature, exactly as in real TE - there is no wrench gesture for it. Any other held item (or
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
                    if (player.isSneaking()) {
                        dismantleBlock(player, world, x, y, z, false);
                    } else {
                        TileEntity te = world.getTileEntity(x, y, z);
                        if (te instanceof TileAdvancedPulverizer) {
                            TileAdvancedPulverizer tile = (TileAdvancedPulverizer) te;
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

            // Augments are NOT spilled here - they travel inside the dropped block item's own NBT
            // instead (see getDrops), together with the side configuration and the energy buffer.
            for (int i = 0; i < tile.getSizeInventory(); i++) {
                if (i >= TileAdvancedPulverizer.AUGMENT_START
                        && i < TileAdvancedPulverizer.AUGMENT_START + TileAdvancedPulverizer.AUGMENT_SLOTS) {
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
            // The copies above are now in the world; the originals must go, or another player
            // with this GUI still open could shift-click them out in the same tick.
            tile.clearContentsOnBreak();
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    /**
     * The single dropped block item carries the machine's augments, side configuration and stored
     * RF, read straight off the live tile. Not called at all for a creative-mode instant-break,
     * which is exactly right: creative players shouldn't receive an item either way.
     */
    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
        ArrayList<ItemStack> drops = new ArrayList<ItemStack>();
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileAdvancedPulverizer) {
            drops.add(MachineDismantle.createDrop(this, damageDropped(metadata), (TileAdvancedPulverizer) te));
        } else {
            drops.add(new ItemStack(Item.getItemFromBlock(this), 1, damageDropped(metadata)));
        }
        return drops;
    }

    /**
     * On a player harvest vanilla calls removedByPlayer (which normally clears the block, taking
     * the tile with it) BEFORE harvestBlock/getDrops, so getDrops would find no tile to read.
     * Reporting the removal here without actually performing it, and doing the real clear after
     * harvestBlock has run, is the standard Forge workaround. Every non-player destruction path
     * (explosions, the Wither, mod block-breakers) already drops before clearing, so those read a
     * live tile without any help.
     */
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
