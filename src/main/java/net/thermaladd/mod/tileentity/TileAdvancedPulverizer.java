package net.thermaladd.mod.tileentity;

import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import cofh.api.energy.EnergyStorage;
import cofh.api.energy.IEnergyContainerItem;
import cofh.api.energy.IEnergyReceiver;
import cofh.api.item.IAugmentItem;
import cofh.api.tileentity.IEnergyInfo;
import cofh.api.tileentity.IPortableData;
import cofh.api.tileentity.IRedstoneControl;
import cofh.thermalexpansion.item.TEAugments;
import cofh.thermalexpansion.util.crafting.PulverizerManager;
import cofh.thermalexpansion.util.crafting.PulverizerManager.RecipePulverizer;
import cpw.mods.fml.common.network.NetworkRegistry;
import net.thermaladd.mod.network.MessageTileRenderSync;
import net.thermaladd.mod.network.PacketHandler;
import net.thermaladd.mod.util.IPortableMachineState;
import net.thermaladd.mod.util.LineLocks;
import net.thermaladd.mod.util.SideRotation;

/**
 * Advanced Pulverizer tile entity.
 *
 * Deliberately does NOT extend Thermal Expansion's own {@code TileMachineBase} /
 * {@code TilePowered}: those classes are TE's internal machine framework (tightly coupled
 * to TE's own {@code BlockMachine.Types} registration, augment system and packet layer,
 * with several package-private fields) rather than a stable addon API, so subclassing them
 * from outside TE is fragile across versions. Instead this is a self-contained TileEntity -
 * same approach the sibling ImprovedAssembler mod in this workspace uses for its own
 * "improved TE machine" - that only reaches into TE for the real public pieces of API it
 * actually needs: {@link PulverizerManager} for recipes, and {@link IAugmentItem} to
 * recognize genuine Thermal Expansion augment items.
 *
 * The upgrade over a real Pulverizer: 3 independent input slots instead of 1, each running
 * its own recipe lookup/energy accumulation/craft cycle in parallel every tick, sharing one
 * RF buffer, one primary output pair and a secondary output pair (real TE's own Pulverizer
 * only ever has one of each) - and 9 augment slots instead of the 3-6 a real (tiered) TE
 * machine gets, all always available at once since this block has no separate tier/upgrade
 * item of its own.
 */
public class TileAdvancedPulverizer extends TileEntity
        implements ISidedInventory, IEnergyReceiver, IRedstoneControl, IEnergyInfo, IPortableData, IPortableMachineState {

    public static final int INPUT_SLOTS = 3;
    /** One primary-output slot per input line, as instructed - not a fixed 2 regardless of INPUT_SLOTS. */
    public static final int OUTPUT_PRIMARY_SLOTS = 3;
    public static final int OUTPUT_SECONDARY_SLOTS = 2;
    public static final int AUGMENT_SLOTS = 9;
    /** Real TE's own "charge slot" - see {@link #chargeFromItem}: accepts any RF-storing item (Capacitors, charged tools, etc.) and continuously drains it into this machine's own buffer. */
    public static final int CHARGE_SLOTS = 1;
    public static final int TOTAL_SLOTS = INPUT_SLOTS + OUTPUT_PRIMARY_SLOTS + OUTPUT_SECONDARY_SLOTS + AUGMENT_SLOTS + CHARGE_SLOTS;

    public static final int INPUT_START = 0;
    public static final int OUTPUT_PRIMARY_START = INPUT_SLOTS;
    public static final int OUTPUT_SECONDARY_START = INPUT_SLOTS + OUTPUT_PRIMARY_SLOTS;
    public static final int AUGMENT_START = INPUT_SLOTS + OUTPUT_PRIMARY_SLOTS + OUTPUT_SECONDARY_SLOTS;
    public static final int CHARGE_SLOT = AUGMENT_START + AUGMENT_SLOTS;

    /**
     * RF spent per tick per active processing line while its recipe is being worked on -
     * 2x a stock level-0 Pulverizer's 40 RF/t base power, i.e. roughly twice the work speed,
     * per the "faster than the base version" requirement. Three lines can run at once, so
     * peak draw is 3x this (before augments).
     */
    /** Overridable from config/ThermalADD.cfg - see {@link net.thermaladd.mod.config.ModConfig}. */
    public static int BASE_ENERGY_PER_TICK = 80;

    /**
     * This block's own fixed power tier - "UltimateResonant": named for sitting beyond even
     * Thermal Expansion's own historical top tier (Resonant, TE3's most powerful machine
     * variant). Unlike TE's real tiers this isn't a separate craftable block/item the player
     * upgrades into - it's simply this machine's one and only power level, shown in its
     * tooltip via {@link net.thermaladd.mod.item.ItemBlockAdvancedPulverizer}.
     *
     * Sized so 3 lines can genuinely run flat-out on Machine Speed augments without starving:
     * at the augment's max level (machineSpeed III, energy mod x20) a single line costs
     * 80 * 20 = 1600 RF/t, so 3 lines at once peak at 4800 RF/t - comfortably under the
     * receive rate below, with the RF buffer sized to absorb bursts on top of that.
     */
    public static final String TIER_NAME = "UltimateResonant";
    /** Base RF buffer - was 80,000, far too small once Machine Speed augments are involved. */
    public static int BASE_ENERGY_CAPACITY = 1000000;
    /** Base RF intake per tick - was 1,200, well below the ~4,800 RF/t a maxed-out speed setup can burn. */
    public static int ENERGY_RECEIVE_PER_TICK = 10000;

    /**
     * Real Thermal Expansion's own ambient "machine working" sound event (verified against the
     * vendored jar's own {@code assets/thermalexpansion/sounds.json}: {@code blockMachinePulverizer}
     * maps to {@code blocks/machine/pulverizer.ogg}) - reused directly rather than shipping a
     * copy, same as this mod's own machine face textures. See
     * {@link net.thermaladd.mod.network.MessageTileRenderSyncHandler} for where this actually
     * gets played - client-only code, deliberately kept out of this class (which loads on the
     * server too) to avoid ever linking a client-only sound type there.
     */
    public static final String SOUND_NAME = "thermalexpansion:blockMachinePulverizer";

    /**
     * Side config modes, verified against real Thermal Expansion's own Pulverizer (decompiled
     * {@code cofh.thermalexpansion.block.machine.TilePulverizer#initialize}: 6 modes, numbered
     * and colored exactly this way - {@code sideTex = {0,1,2,3,4,7}} indexing real TE's own
     * {@code Config_None/Blue/Red/Yellow/Orange/Open} badge textures). Mode 0 ("no badge, plain
     * casing") is Thermal Expansion's actual DISABLED state, not an "accept everything" default
     * the way this mod used to treat it - a real Pulverizer's front face is permanently forced
     * to mode 0 for exactly that reason (see {@code setDefaultSides()} below), and a side only
     * ever ends up here if a player explicitly disables it. "Output" is genuinely 3 separate
     * modes in real TE - Primary only, Secondary only, or Both - not one generic mode that
     * always exposes everything, which is what let a pipe pull secondary byproducts out of a
     * side meant only for the primary product.
     */
    public static final int SIDE_MODE_DISABLED = 0;
    public static final int SIDE_MODE_INPUT = 1;
    public static final int SIDE_MODE_OUTPUT_PRIMARY = 2;
    public static final int SIDE_MODE_OUTPUT_SECONDARY = 3;
    public static final int SIDE_MODE_OUTPUT_BOTH = 4;
    public static final int SIDE_MODE_ALL = 5;
    public static final int SIDE_MODE_COUNT = 6;

    /** North/South/West/East facing metas, same convention vanilla furnaces use. */
    public static final int[] FACING_META = {2, 5, 3, 4};

    // -------------------------------------------------- augments (real TE augment items)

    /**
     * Exact string keys real Thermal Expansion augment items report via
     * {@link IAugmentItem#getAugmentTypes(ItemStack)} / {@link IAugmentItem#getAugmentLevel}
     * (verified against the decompiled {@code cofh.thermalexpansion.item.TEAugments}), so
     * genuine TE augments - the same items real TE machines accept - work here too.
     */
    public static final String AUG_GENERAL_AUTO_OUTPUT = "generalAutoOutput";
    public static final String AUG_GENERAL_AUTO_INPUT = "generalAutoInput";
    public static final String AUG_GENERAL_RECONFIG_SIDES = "generalReconfigSides";
    public static final String AUG_GENERAL_REDSTONE_CONTROL = "generalRedstoneControl";
    public static final String AUG_MACHINE_SPEED = "machineSpeed";
    public static final String AUG_MACHINE_SECONDARY = "machineSecondary";
    public static final String AUG_MACHINE_NULL = "machineNull";
    public static final String AUG_ENERGY_STORAGE = "energyStorage";

    /**
     * Same per-level modifier tables as {@code TEAugments} (index 0 = no augment present = no
     * bonus) for levels 1-3, plus a 4th level - this mod's own "beyond spec" Machine Speed
     * augment (see {@code net.thermaladd.mod.init.ModAugments}, crafted from real TE's own
     * level-3 speed augment): x10 process speed for +200% RF/t over level 3's already-steep
     * 20x cost (20 * 3 = 60x base), matching what the augment's own tooltip says. Only
     * AUG_MACHINE_SPEED goes this high - AUG_ENERGY_STORAGE (no ThermalADD equivalent exists)
     * stays clamped to real TE's own MAX_AUGMENT_LEVEL of 3.
     */
    private static final int[] MACHINE_SPEED_PROCESS_MOD = {1, 2, 4, 8, 10};
    private static final int[] MACHINE_SPEED_ENERGY_MOD = {1, 3, 8, 20, 60};
    /**
     * Levels 0-3 match real TE's own MACHINE_SECONDARY_MOD exactly (subtracted straight from
     * the 100-based chance divisor - see placeOutput()). Level 4 is this mod's own "beyond
     * spec" tier (net.thermaladd.mod.init.ModAugments' secondarySieve4, crafted from real TE's
     * level-3 sieve): +200 pushes the divisor to {@code max(1, 100-200)=1}, meaning any recipe
     * with a nonzero secondary chance always hits - a guaranteed-secondary-output top tier.
     * Unlike the other tiers, this one also isn't free: MACHINE_SECONDARY_ENERGY_PCT adds +25%
     * RF/t on top of whatever the Machine Speed augment already costs, level 4 only.
     */
    private static final int[] MACHINE_SECONDARY_MOD = {0, 10, 15, 20, 200};
    private static final int[] MACHINE_SECONDARY_ENERGY_PCT = {100, 100, 100, 100, 125};
    private static final int[] ENERGY_STORAGE_MOD = {1, 2, 4, 8};
    private static final int MAX_AUGMENT_LEVEL = 3;
    private static final int MAX_SPEED_LEVEL = 4;
    private static final int MAX_SECONDARY_LEVEL = 4;

    private static final int AUTO_IO_INTERVAL = 8;

    public boolean augmentAutoInput = false;
    public boolean augmentAutoOutput = false;
    public boolean augmentReconfigSides = false;
    public boolean augmentRedstoneControl = false;
    /** Matches real Thermal Expansion's own default - a freshly installed augment starts on "Low" (paused while powered). */
    private ControlMode rsMode = ControlMode.LOW;
    private boolean rsPowered = false;
    public boolean augmentSecondaryNull = false;

    private int speedProcessMod = 1;
    private int speedEnergyMod = 1;
    /** 100 = no surcharge; only the level-4 Secondary Sieve augment raises this (see MACHINE_SECONDARY_ENERGY_PCT). */
    private int secondaryEnergyPct = 100;
    private int secondaryChanceDivisor = 100;
    private int autoIOTimer = 0;

    private final ItemStack[] inventory = new ItemStack[TOTAL_SLOTS];
    private final EnergyStorage energyStorage = new EnergyStorage(BASE_ENERGY_CAPACITY, ENERGY_RECEIVE_PER_TICK);

    /** RF already invested into whatever recipe is currently occupying input slot N. */
    private final int[] progress = new int[INPUT_SLOTS];
    /** Total RF the recipe currently in slot N requires; 0 while no recipe is in progress. */
    private final int[] progressMax = new int[INPUT_SLOTS];
    /** RF actually spent on the tick just finished - shown as "Energy Consumption" in the GUI. */
    private int energyPerTick = 0;
    /** RF/t all 3 lines together would draw at once, at the current Machine Speed augment level - recomputed in installAugments(). */
    private int maxEnergyPerTick = INPUT_SLOTS * BASE_ENERGY_PER_TICK;

    /** Player-given name from an anvil, or null - see getInventoryName(). */
    private String customName;

    private byte facing = 3; // south, matches BlockContainer's default before onBlockPlacedBy runs
    private byte[] sideCache = new byte[6];

    /** True while at least one input line is mid-recipe; drives the block's Active/Inactive face icon. */
    private boolean isActive = false;

    public boolean isActive() {
        return isActive;
    }

    // ---------------------------------------------------------------- facing / sides

    /**
     * Comparator signal: how many of the machine's processing lines currently hold an input item,
     * scaled to 1-15, with 0 meaning every line is empty. Deliberately counts occupied lines
     * rather than weighing stack sizes the way vanilla's own inventory comparator does - for a
     * parallel machine the useful thing to automate on is "every line is busy, stop feeding me",
     * and a line holding 1 item is exactly as busy as one holding 64.
     */
    public int getComparatorSignal() {
        int occupied = 0;
        for (int i = 0; i < INPUT_SLOTS; i++) {
            if (inventory[INPUT_START + i] != null) {
                occupied++;
            }
        }
        return occupied == 0 ? 0 : 1 + (occupied * 14) / INPUT_SLOTS;
    }

    // ---------------------------------------------------------------- IPortableData (TE Redprint)

    /**
     * Lets real Thermal Expansion's own Redprint copy this machine's side configuration and
     * redstone mode onto another one - no custom item or GUI button needed, the Redprint just
     * works on anything implementing cofh.api.tileentity.IPortableData.
     *
     * The data type is this mod's own per-machine string, so a Redprint filled from a Pulverizer
     * only pastes onto another Pulverizer: the side MODES are machine-specific (this one has 6,
     * the Furnace has 4), and TE's own ItemDiagram refuses the paste outright when the types don't
     * match rather than writing something meaningless.
     *
     * The configuration is pasted RELATIVE to the machine: the source's facing travels with the
     * data and the sides are rotated onto the target's own facing, so "input on the left" stays
     * input on the left however the target is turned. The target's facing itself is never
     * changed - pasting a configuration must not spin a machine the player already placed.
     */
    @Override
    public String getDataType() {
        return "tile.thermaladd.advancedPulverizer";
    }

    @Override
    public void writePortableData(EntityPlayer player, NBTTagCompound tag) {
        tag.setByteArray("SideCache", sideCache.clone());
        tag.setByte("Facing", facing);
        tag.setByte("RSControl", (byte) rsMode.ordinal());
    }

    @Override
    public void readPortableData(EntityPlayer player, NBTTagCompound tag) {
        // Same augment gates the GUI itself enforces - a Redprint must not be a way around a
        // machine that has no Reconfigurable Sides / Redstone Control augment installed.
        if (augmentReconfigSides && tag.hasKey("SideCache")) {
            applySideModes(tag.getByteArray("SideCache"), tag.hasKey("Facing") ? tag.getByte("Facing") : -1);
        }
        if (augmentRedstoneControl && tag.hasKey("RSControl")) {
            int ordinal = tag.getByte("RSControl") & 0xFF;
            if (ordinal < ControlMode.values().length) {
                rsMode = ControlMode.values()[ordinal];
                markDirty();
            }
        }
    }

    // ---------------------------------------------------------------- IPortableMachineState

    @Override
    public byte[] getSideModesCopy() {
        return sideCache.clone();
    }

    @Override
    public void applySideModes(byte[] modes, int sourceFacing) {
        // Same validation the NBT load path uses - a wrong-length or out-of-range array would
        // later index the block's per-mode icon arrays out of bounds while rendering.
        if (modes == null || modes.length != sideCache.length || !isValidSideArray(modes)) {
            return;
        }
        byte[] rotated = SideRotation.rotate(modes, sourceFacing, facing);
        // The front is never configurable - enforced here too, so data from an older save (or a
        // source whose facing is unknown) can never leave a hidden, working mode behind it.
        // Range-checked because facing is read from NBT unvalidated.
        if (facing >= 0 && facing < rotated.length) {
            rotated[facing] = SIDE_MODE_DISABLED;
        }
        sideCache = rotated;
        markDirty();
        syncRenderState();
    }

    /**
     * Wrench rotation: turns the machine AND its side configuration together, exactly like real
     * Thermal Expansion's TileReconfigurable#rotateBlock - see SideRotation for what went wrong
     * when only the facing changed.
     */
    public void rotateFacing(int newFacing) {
        byte[] rotated = SideRotation.rotate(sideCache, facing, newFacing);
        rotated[newFacing] = SIDE_MODE_DISABLED;
        sideCache = rotated;
        setFacing(newFacing);
    }

    /**
     * Empties every slot once breakBlock has spilled them. Without this the tile kept its
     * originals after the copies were dropped, and another player with the GUI still open could
     * shift-click them out in the same tick the machine was destroyed - a straight duplication.
     * Done on the raw array on purpose: setInventorySlotContents on an augment slot would
     * re-run installAugments on a block that is being removed.
     */
    public void clearContentsOnBreak() {
        for (int i = 0; i < inventory.length; i++) {
            inventory[i] = null;
        }
    }

    @Override
    public void setStoredEnergy(int energy) {
        int capped = Math.max(0, Math.min(energy, energyStorage.getMaxEnergyStored()));
        energyStorage.setEnergyStored(capped);
        markDirty();
    }

    public int getFacing() {
        return facing;
    }

    public void setFacing(int meta) {
        facing = (byte) meta;
        markDirty();
        syncRenderState();
    }

    public int getSideMode(int side) {
        return sideCache[side];
    }

    /**
     * Gated by the Reconfigurable Sides augment, exactly like real Thermal Expansion machines.
     * Also refuses to touch the front face, matching TileReconfigurable#incrSide/decrSide -
     * real TE never lets the front be individually configured, only reset along with
     * everything else via resetAllSideModes().
     */
    /**
     * {@code side} arrives straight from a client-sent network packet (MessageCycleSide's
     * {@code side} field is an unchecked byte, -128..127) - without this bounds check, an
     * out-of-range value indexes {@code sideCache} out of bounds and throws, which Forge's
     * packet handling turns into a disconnect for the sender.
     */
    private static boolean isValidSide(int side) {
        return side >= 0 && side < 6;
    }

    /** See readFromNBT's own use of this - guards against a mode value outside SIDE_MODE_COUNT reaching sideCache from tampered/foreign NBT. */
    private static boolean isValidSideArray(byte[] sides) {
        for (byte mode : sides) {
            if (mode < 0 || mode >= SIDE_MODE_COUNT) {
                return false;
            }
        }
        return true;
    }

    public boolean cycleSideMode(int side, int direction) {
        if (!isValidSide(side) || !augmentReconfigSides || side == facing) {
            return false;
        }
        sideCache[side] = (byte) (((sideCache[side] + direction) % SIDE_MODE_COUNT + SIDE_MODE_COUNT) % SIDE_MODE_COUNT);
        markDirty();
        syncRenderState();
        return true;
    }

    public boolean resetSideMode(int side) {
        if (!isValidSide(side) || !augmentReconfigSides || side == facing) {
            return false;
        }
        sideCache[side] = SIDE_MODE_DISABLED;
        markDirty();
        syncRenderState();
        return true;
    }

    public boolean resetAllSideModes() {
        if (!augmentReconfigSides) {
            return false;
        }
        setDefaultSides();
        return true;
    }

    /**
     * Every side starts (and resets back to) plain Disabled - no "smart" per-side defaults.
     * Real TE's own Pulverizer does auto-assign sides on placement, but per explicit
     * instruction this mod deliberately does NOT: a side only ever carries a role (and only
     * ever shows a slot highlight - see {@link #isAnyInputSide()} and friends) once the player
     * has actually chosen one via the Configuration tab. Applied once when the block is freshly
     * placed ({@code BlockAdvancedPulverizer#onBlockPlacedBy}) and again whenever every side is
     * reset via a shift-click on the Configuration tab's center button.
     */
    public void setDefaultSides() {
        for (int i = 0; i < sideCache.length; i++) {
            sideCache[i] = SIDE_MODE_DISABLED;
        }
        markDirty();
        syncRenderState();
    }

    /**
     * facing/sideCache drive the block's in-world icon (see BlockAdvancedPulverizer#getIcon),
     * but neither is stored in block metadata, so vanilla's block-change networking never
     * reaches them - World#markBlockForUpdate called here (server-side) would be a no-op,
     * since it only affects whichever World instance it's called on, and the server's World
     * is never the client's renderer. This explicitly pushes the new state to every nearby
     * client, which then repaints the block from its OWN World - see
     * MessageTileRenderSyncHandler.
     */
    private void syncRenderState() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        PacketHandler.INSTANCE.sendToAllAround(new MessageTileRenderSync(xCoord, yCoord, zCoord, facing, sideCache, isActive),
                new NetworkRegistry.TargetPoint(worldObj.provider.dimensionId, xCoord, yCoord, zCoord, 64.0));
    }

    public void setFacingClient(int meta) {
        facing = (byte) meta;
    }

    public void setSideModeClient(int side, int mode) {
        sideCache[side] = (byte) mode;
    }

    /** Client-side only: applied by MessageTileRenderSyncHandler, which also uses the false->true edge of this same value to start the ambient machine sound. */
    public void setActiveClient(boolean active) {
        isActive = active;
    }

    /** Sides in Input or All mode accept items pushed/pulled into the 3 input slots. */
    private static boolean modeAllowsInsertInput(int mode) {
        return mode == SIDE_MODE_INPUT || mode == SIDE_MODE_ALL;
    }

    /**
     * Real TE quirk, verified against the decompiled {@code TilePulverizer}
     * ({@code allowExtractionSide[1] = true} for its own Input mode): a side left on plain
     * Input can still have its input slots drained back out (e.g. by a pipe on the other end
     * correcting a wrong item), even though Input alone never exposes the output slots.
     */
    private static boolean modeAllowsExtractInput(int mode) {
        return mode == SIDE_MODE_INPUT;
    }

    private static boolean modeAllowsExtractPrimary(int mode) {
        return mode == SIDE_MODE_OUTPUT_PRIMARY || mode == SIDE_MODE_OUTPUT_BOTH || mode == SIDE_MODE_ALL;
    }

    private static boolean modeAllowsExtractSecondary(int mode) {
        return mode == SIDE_MODE_OUTPUT_SECONDARY || mode == SIDE_MODE_OUTPUT_BOTH || mode == SIDE_MODE_ALL;
    }

    /**
     * Live per-role slot-highlight queries for the GUI (see GuiAdvancedPulverizer): matching
     * real Thermal Expansion, the colored ring around a slot only appears once the player has
     * actually configured a side to reach it - these scan the current sideCache directly rather
     * than reading any fixed default, so the highlight tracks whatever's actually configured
     * right now and disappears again the moment no side grants that role anymore.
     */
    public boolean isAnyInputSide() {
        for (int side = 0; side < 6; side++) {
            if (modeAllowsInsertInput(sideCache[side])) {
                return true;
            }
        }
        return false;
    }

    public boolean isAnyOutputPrimarySide() {
        for (int side = 0; side < 6; side++) {
            if (modeAllowsExtractPrimary(sideCache[side])) {
                return true;
            }
        }
        return false;
    }

    public boolean isAnyOutputSecondarySide() {
        for (int side = 0; side < 6; side++) {
            if (modeAllowsExtractSecondary(sideCache[side])) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- augments

    /**
     * Mirrors Thermal Expansion's own {@code TileAugmentable#installAugments()}: re-derive
     * every augment-driven flag/modifier from whatever real augment items currently sit in
     * the 9 augment slots. Unlike TE's own tiered machines (which require having every lower
     * level of an augment installed before a higher one counts, since they can be upgraded
     * in place), this block has a fixed slot count and no tier concept, so it simply takes
     * the strongest level of each augment type present - a deliberate simplification.
     */
    public void installAugments() {
        boolean autoInput = false;
        boolean autoOutput = false;
        boolean reconfigSides = false;
        boolean redstoneControl = false;
        boolean secondaryNull = false;
        int speedLevel = 0;
        int secondaryLevel = 0;
        int energyLevel = 0;

        for (int i = 0; i < AUGMENT_SLOTS; i++) {
            ItemStack augment = inventory[AUGMENT_START + i];
            if (!isAugmentItem(augment)) {
                continue;
            }
            IAugmentItem item = (IAugmentItem) augment.getItem();
            if (item.getAugmentLevel(augment, AUG_GENERAL_AUTO_OUTPUT) > 0) {
                autoOutput = true;
            }
            if (item.getAugmentLevel(augment, AUG_GENERAL_AUTO_INPUT) > 0) {
                autoInput = true;
            }
            if (item.getAugmentLevel(augment, AUG_GENERAL_RECONFIG_SIDES) > 0) {
                reconfigSides = true;
            }
            if (item.getAugmentLevel(augment, AUG_GENERAL_REDSTONE_CONTROL) > 0) {
                redstoneControl = true;
            }
            if (item.getAugmentLevel(augment, AUG_MACHINE_NULL) > 0) {
                secondaryNull = true;
            }
            speedLevel = Math.max(speedLevel, clampLevel(item.getAugmentLevel(augment, AUG_MACHINE_SPEED), MAX_SPEED_LEVEL));
            secondaryLevel = Math.max(secondaryLevel, clampLevel(item.getAugmentLevel(augment, AUG_MACHINE_SECONDARY), MAX_SECONDARY_LEVEL));
            energyLevel = Math.max(energyLevel, clampLevel(item.getAugmentLevel(augment, AUG_ENERGY_STORAGE)));
        }

        if (augmentReconfigSides && !reconfigSides) {
            // the augment that unlocked side reconfiguration was removed - lock back to defaults
            setDefaultSides();
        }
        if (!redstoneControl) {
            // matches real TE's TileAugmentable#onInstalled() - no augment means no mode to show/apply
            rsMode = ControlMode.DISABLED;
        }

        augmentAutoInput = autoInput;
        augmentAutoOutput = autoOutput;
        augmentReconfigSides = reconfigSides;
        augmentRedstoneControl = redstoneControl;
        augmentSecondaryNull = secondaryNull;

        speedProcessMod = MACHINE_SPEED_PROCESS_MOD[speedLevel];
        speedEnergyMod = MACHINE_SPEED_ENERGY_MOD[speedLevel];
        secondaryEnergyPct = MACHINE_SECONDARY_ENERGY_PCT[secondaryLevel];
        maxEnergyPerTick = INPUT_SLOTS * BASE_ENERGY_PER_TICK * speedEnergyMod * secondaryEnergyPct / 100;
        secondaryChanceDivisor = Math.max(1, 100 - MACHINE_SECONDARY_MOD[secondaryLevel]);

        // Capacity AND intake rate scale together with the Energy Storage augment - a bigger
        // buffer that still fills at the base rate would just take proportionally longer to
        // top up, defeating the point of installing it alongside Machine Speed augments.
        energyStorage.setCapacity(BASE_ENERGY_CAPACITY * ENERGY_STORAGE_MOD[energyLevel]);
        energyStorage.setMaxTransfer(ENERGY_RECEIVE_PER_TICK * ENERGY_STORAGE_MOD[energyLevel]);
        markDirty();
    }

    /**
     * Mirrors real Thermal Expansion's own default-augment behavior (decompiled from
     * {@code cofh.thermalexpansion.block.machine.BlockMachine}: {@code defaultAugments[0/1/2]}
     * is seeded with {@code TEAugments.generalAutoOutput}/{@code generalRedstoneControl}/
     * {@code generalReconfigSides} whenever the corresponding config flag is on, which is
     * TE's shipped default for all three) - a freshly placed machine already has these 3
     * augments installed, not just an empty augment bay. Only called once, from
     * {@code onBlockPlacedBy}, so loading an existing saved tile never re-seeds it.
     */
    public void installDefaultAugments() {
        setInventorySlotContents(AUGMENT_START, TEAugments.generalAutoOutput.copy());
        setInventorySlotContents(AUGMENT_START + 1, TEAugments.generalRedstoneControl.copy());
        setInventorySlotContents(AUGMENT_START + 2, TEAugments.generalReconfigSides.copy());
    }

    /**
     * Serializes the 9 augment slots (relative index 0-8, not the absolute inventory index) so
     * they can travel inside the dropped block item's NBT - see {@code BlockAdvancedPulverizer
     * #breakBlock/getDrops}. Whatever is currently installed (defaults, player-added upgrades,
     * or none) round-trips exactly, instead of falling out as separate loose item entities that
     * would let a re-placed block get handed a second, brand new set of defaults.
     */
    public NBTTagCompound writeAugmentsToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < AUGMENT_SLOTS; i++) {
            ItemStack stack = inventory[AUGMENT_START + i];
            if (stack != null) {
                NBTTagCompound itemTag = new NBTTagCompound();
                itemTag.setByte("Slot", (byte) i);
                stack.writeToNBT(itemTag);
                list.appendTag(itemTag);
            }
        }
        tag.setTag("Augments", list);
        return tag;
    }

    public void readAugmentsFromNBT(NBTTagCompound tag) {
        NBTTagList list = tag.getTagList("Augments", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound itemTag = list.getCompoundTagAt(i);
            int slot = itemTag.getByte("Slot") & 255;
            if (slot >= 0 && slot < AUGMENT_SLOTS) {
                inventory[AUGMENT_START + slot] = ItemStack.loadItemStackFromNBT(itemTag);
            }
        }
        installAugments();
    }

    private static int clampLevel(int level) {
        return clampLevel(level, MAX_AUGMENT_LEVEL);
    }

    private static int clampLevel(int level, int max) {
        if (level < 0) {
            return 0;
        }
        return Math.min(level, max);
    }

    public static boolean isAugmentItem(ItemStack stack) {
        return stack != null && stack.getItem() instanceof IAugmentItem;
    }

    public static boolean isValidAugment(ItemStack stack) {
        if (!isAugmentItem(stack)) {
            return false;
        }
        IAugmentItem item = (IAugmentItem) stack.getItem();
        Set<String> types = item.getAugmentTypes(stack);
        if (types == null) {
            return false;
        }
        return types.contains(AUG_GENERAL_AUTO_OUTPUT) || types.contains(AUG_GENERAL_AUTO_INPUT)
                || types.contains(AUG_GENERAL_RECONFIG_SIDES) || types.contains(AUG_GENERAL_REDSTONE_CONTROL)
                || types.contains(AUG_MACHINE_SPEED) || types.contains(AUG_MACHINE_SECONDARY)
                || types.contains(AUG_MACHINE_NULL) || types.contains(AUG_ENERGY_STORAGE);
    }

    /**
     * Real TE lets a second augment of the same type sit in another slot and just silently
     * ignores it (see {@code TileMachineBase#installAugment}'s own hasDuplicateAugment check) -
     * this mod instead refuses the slot outright, so a duplicate never becomes a dead, wasted
     * slot in the first place. "Same type" means sharing at least one of the augment's own
     * type strings (a stack can in principle report more than one), checked against every
     * OTHER currently-filled augment slot.
     */
    public boolean hasDuplicateAugmentType(ItemStack candidate, int excludeSlot) {
        if (!isAugmentItem(candidate)) {
            return false;
        }
        Set<String> types = ((IAugmentItem) candidate.getItem()).getAugmentTypes(candidate);
        if (types == null || types.isEmpty()) {
            return false;
        }
        for (int i = 0; i < AUGMENT_SLOTS; i++) {
            int slot = AUGMENT_START + i;
            if (slot == excludeSlot) {
                continue;
            }
            ItemStack other = inventory[slot];
            if (!isAugmentItem(other)) {
                continue;
            }
            Set<String> otherTypes = ((IAugmentItem) other.getItem()).getAugmentTypes(other);
            if (otherTypes == null) {
                continue;
            }
            for (String type : types) {
                if (otherTypes.contains(type)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- energy (RF)

    @Override
    public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate) {
        return energyStorage.receiveEnergy(maxReceive, simulate);
    }

    @Override
    public int getEnergyStored(ForgeDirection from) {
        return energyStorage.getEnergyStored();
    }

    @Override
    public int getMaxEnergyStored(ForgeDirection from) {
        return energyStorage.getMaxEnergyStored();
    }

    @Override
    public boolean canConnectEnergy(ForgeDirection from) {
        return true;
    }

    public int getEnergy() {
        return energyStorage.getEnergyStored();
    }

    /** Capacity is dynamic (the Energy Storage augment raises it), so the GUI reads it live instead of a constant. */
    public int getMaxEnergy() {
        return energyStorage.getMaxEnergyStored();
    }

    /**
     * Client-side halves of the four RF numbers the GUI prints.
     *
     * Container#sendProgressBarUpdate serializes as a SIGNED 16-BIT SHORT, so anything past 32767
     * cannot go over in one piece. This used to be handled by dividing by ENERGY_SYNC_SCALE and
     * multiplying back, which silently rounded every number the GUI showed: a 1,000,000 RF buffer
     * came out as 999,936, because 1,000,000/256 is 3906.25 and the .25 was discarded. Waila reads
     * the real server-side value, so the two disagreed on screen.
     *
     * Sending the low and high 16 bits as two separate properties and reassembling them here is
     * exact for the whole int range instead. The incoming halves are masked with 0xFFFF because
     * the packet is read back as a SIGNED short - a low half of 0xFFFF arrives as -1.
     */
    private int clientEnergyLow;
    private int clientEnergyHigh;
    private int clientMaxEnergyLow;
    private int clientMaxEnergyHigh;

    public void setEnergyLowClient(int value) {
        clientEnergyLow = value & 0xFFFF;
        applyClientEnergy();
    }

    public void setEnergyHighClient(int value) {
        clientEnergyHigh = value & 0xFFFF;
        applyClientEnergy();
    }

    public void setMaxEnergyLowClient(int value) {
        clientMaxEnergyLow = value & 0xFFFF;
        applyClientEnergy();
    }

    public void setMaxEnergyHighClient(int value) {
        clientMaxEnergyHigh = value & 0xFFFF;
        applyClientEnergy();
    }

    /** Capacity is applied first: EnergyStorage clamps the stored value to it. */
    private void applyClientEnergy() {
        int capacity = clientMaxEnergyHigh << 16 | clientMaxEnergyLow;
        if (capacity > 0) {
            energyStorage.setCapacity(capacity);
        }
        energyStorage.setEnergyStored(clientEnergyHigh << 16 | clientEnergyLow);
    }

    public int getProgress(int line) {
        return progress[line];
    }

    public int getProgressMax(int line) {
        return progressMax[line];
    }

    public void setProgressClient(int line, int value) {
        progress[line] = value;
    }

    public void setProgressMaxClient(int line, int value) {
        progressMax[line] = value;
    }

    /** RF actually drawn on the last tick that ran server-side - what real TE's own "Energy Consumption" line shows. */
    public int getEnergyPerTick() {
        return energyPerTick;
    }

    /** RF/t this machine would draw if all 3 lines were processing at once, at the current Machine Speed augment level. */
    public int getMaxEnergyPerTick() {
        return maxEnergyPerTick;
    }

    /** Same exact-halves scheme as the energy buffer above - see applyClientEnergy's comment. */
    public void setEnergyPerTickLowClient(int value) {
        energyPerTick = (energyPerTick & 0xFFFF0000) | (value & 0xFFFF);
    }

    public void setEnergyPerTickHighClient(int value) {
        energyPerTick = ((value & 0xFFFF) << 16) | (energyPerTick & 0xFFFF);
    }

    public void setMaxEnergyPerTickLowClient(int value) {
        maxEnergyPerTick = (maxEnergyPerTick & 0xFFFF0000) | (value & 0xFFFF);
    }

    public void setMaxEnergyPerTickHighClient(int value) {
        maxEnergyPerTick = ((value & 0xFFFF) << 16) | (maxEnergyPerTick & 0xFFFF);
    }

    // ---------------------------------------------------------------- IEnergyInfo (real TE's own Energy tab)

    @Override
    public int getInfoEnergyPerTick() {
        return getEnergyPerTick();
    }

    @Override
    public int getInfoMaxEnergyPerTick() {
        return getMaxEnergyPerTick();
    }

    @Override
    public int getInfoEnergyStored() {
        return getEnergy();
    }

    @Override
    public int getInfoMaxEnergyStored() {
        return getMaxEnergy();
    }

    // ---------------------------------------------------------------- redstone control (TE-compatible)

    @Override
    public void setControl(ControlMode mode) {
        rsMode = mode;
        markDirty();
    }

    @Override
    public ControlMode getControl() {
        return rsMode;
    }

    @Override
    public void setPowered(boolean powered) {
        rsPowered = powered;
    }

    @Override
    public boolean isPowered() {
        return rsPowered;
    }

    /** Client-side only: applies a control-mode ordinal received via the container's progress bar sync. */
    public void setControlClient(int ordinal) {
        rsMode = ControlMode.values()[ordinal];
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }

        boolean dirty = false;
        energyPerTick = 0;

        if (chargeFromItem()) {
            dirty = true;
        }

        // Same 3-way control real TE uses (cofh.api.tileentity.IRedstoneControl.ControlMode):
        // Disabled ignores redstone entirely, Low only crafts while NOT powered, High only
        // crafts WHILE powered - selectable from the Redstone Control tab (see
        // client.gui.TabRedstoneControl). Energy can still be received and pipes can still
        // move items either way - only crafting itself is gated.
        setPowered(worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord));
        boolean redstoneAllows = !augmentRedstoneControl || rsMode.isDisabled() || rsMode.isHigh() == isPowered();
        if (redstoneAllows) {
            for (int line = 0; line < INPUT_SLOTS; line++) {
                if (tryProcess(line)) {
                    dirty = true;
                }
            }
        }

        if (augmentAutoInput || augmentAutoOutput) {
            if (++autoIOTimer >= AUTO_IO_INTERVAL) {
                autoIOTimer = 0;
                if (augmentAutoInput && autoPullInputs()) {
                    dirty = true;
                }
                if (augmentAutoOutput && autoPushOutputs()) {
                    dirty = true;
                }
            }
        }

        boolean nowActive = false;
        for (int line = 0; line < INPUT_SLOTS; line++) {
            if (progressMax[line] > 0) {
                nowActive = true;
                break;
            }
        }
        if (nowActive != isActive) {
            isActive = nowActive;
            // Only on the active/inactive transition edge (not every tick) - this is what
            // actually swaps the rendered face icon, unlike markDirty() which just flags the
            // chunk for saving. A full block update is more expensive than that, which is
            // exactly why it is gated behind this state-change check.
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            // Pushes the new isActive to every nearby client via the same packet the facing/
            // side-cache already uses - without this, a client who never opens this machine's
            // GUI would never actually learn isActive changed (windowProperty sync only reaches
            // a client with the GUI open), so its face icon would stay stuck and the ambient
            // "machine working" sound (see MessageTileRenderSyncHandler) would never start.
            syncRenderState();
            dirty = true;
        }

        if (dirty) {
            markDirty();
        }
    }

    /**
     * Mirrors real Thermal Expansion's own {@code TilePowered#chargeEnergy}: whatever's sitting
     * in the charge slot (a Redstone/Resonant/etc. Capacitor, or any other RF-storing item) is
     * drained into this machine's own buffer every tick, up to whichever is smaller - the item's
     * own discharge rate or the buffer's remaining room/receive rate - and discarded once empty.
     * Unlike crafting, this runs regardless of Redstone Control - real TE never gates charging
     * behind it either.
     */
    private boolean chargeFromItem() {
        ItemStack stack = inventory[CHARGE_SLOT];
        if (stack == null || !(stack.getItem() instanceof IEnergyContainerItem)) {
            return false;
        }
        int receive = Math.min(energyStorage.getMaxReceive(), energyStorage.getMaxEnergyStored() - energyStorage.getEnergyStored());
        if (receive <= 0) {
            return false;
        }
        IEnergyContainerItem energyItem = (IEnergyContainerItem) stack.getItem();
        int extracted = energyItem.extractEnergy(stack, receive, false);
        if (extracted <= 0) {
            return false;
        }
        energyStorage.receiveEnergy(extracted, false);
        if (stack.stackSize <= 0) {
            inventory[CHARGE_SLOT] = null;
        }
        return true;
    }

    /**
     * One input line's share of the tick: mirrors Thermal Expansion's own Pulverizer
     * process (accumulate RF into a progress counter, craft once it reaches the recipe's
     * energy cost) but simplified to a flat rate per line instead of TE's tiered
     * min/max/ramp power curve - each of the 3 lines is independent and cannot steal
     * energy reserved for the others mid-tick. The Machine Speed augment scales both the
     * per-tick progress gain and its RF cost together (faster, but not cheaper), the same
     * trade-off real TE machines make.
     */
    private boolean tryProcess(int line) {
        ItemStack input = inventory[INPUT_START + line];
        if (input == null) {
            if (progressMax[line] != 0) {
                progress[line] = 0;
                progressMax[line] = 0;
                return true;
            }
            return false;
        }

        RecipePulverizer recipe = PulverizerManager.getRecipe(input);
        if (recipe == null || input.stackSize < recipe.getInput().stackSize) {
            if (progressMax[line] != 0) {
                progress[line] = 0;
                progressMax[line] = 0;
                return true;
            }
            return false;
        }

        progressMax[line] = recipe.getEnergy();

        if (progress[line] < progressMax[line]) {
            int energyCost = BASE_ENERGY_PER_TICK * speedEnergyMod * secondaryEnergyPct / 100;
            if (energyStorage.getEnergyStored() < energyCost) {
                return false;
            }
            energyStorage.modifyEnergyStored(-energyCost);
            energyPerTick += energyCost;
            progress[line] += BASE_ENERGY_PER_TICK * speedProcessMod;
            return true;
        }

        // Progress is complete - wait here (without spending more energy) until there is
        // room to actually place the output, then craft.
        if (!canFitOutput(recipe)) {
            return false;
        }

        placeOutput(recipe);
        input.stackSize -= recipe.getInput().stackSize;
        if (input.stackSize <= 0) {
            inventory[INPUT_START + line] = null;
        }
        progress[line] = 0;
        progressMax[line] = 0;
        return true;
    }

    private boolean canFitOutput(RecipePulverizer recipe) {
        ItemStack primary = recipe.getPrimaryOutput();
        boolean primaryFits = false;
        for (int i = 0; i < OUTPUT_PRIMARY_SLOTS; i++) {
            if (canFitStack(OUTPUT_PRIMARY_START + i, primary)) {
                primaryFits = true;
                break;
            }
        }
        if (!primaryFits) {
            return false;
        }
        ItemStack secondary = recipe.getSecondaryOutput();
        // The Null augment mirrors real TE's augmentSecondaryNull: skip this gate entirely,
        // so jammed secondary slots never block the primary product from finishing.
        if (secondary != null && !augmentSecondaryNull) {
            boolean anyFits = false;
            for (int i = 0; i < OUTPUT_SECONDARY_SLOTS; i++) {
                if (canFitStack(OUTPUT_SECONDARY_START + i, secondary)) {
                    anyFits = true;
                    break;
                }
            }
            if (!anyFits) {
                return false;
            }
        }
        return true;
    }

    private boolean canFitStack(int slot, ItemStack stack) {
        ItemStack existing = inventory[slot];
        if (existing == null) {
            return true;
        }
        return existing.getItem() == stack.getItem()
                && existing.getItemDamage() == stack.getItemDamage()
                && ItemStack.areItemStackTagsEqual(existing, stack)
                && existing.stackSize + stack.stackSize <= existing.getMaxStackSize();
    }

    private void placeOutput(RecipePulverizer recipe) {
        addToFirstFitting(OUTPUT_PRIMARY_START, OUTPUT_PRIMARY_SLOTS, recipe.getPrimaryOutput());

        ItemStack secondary = recipe.getSecondaryOutput();
        if (secondary == null) {
            return;
        }
        int chance = recipe.getSecondaryOutputChance();
        // Same roll Thermal Expansion itself uses: shrinking the divisor (via the Secondary
        // Output augment) raises the odds of landing under the recipe's own chance value.
        if (chance >= 100 || worldObj.rand.nextInt(secondaryChanceDivisor) < chance) {
            addToFirstFitting(OUTPUT_SECONDARY_START, OUTPUT_SECONDARY_SLOTS, secondary);
        }
    }

    private void addToFirstFitting(int firstSlot, int slotCount, ItemStack stack) {
        for (int i = 0; i < slotCount; i++) {
            int slot = firstSlot + i;
            if (canFitStack(slot, stack)) {
                if (inventory[slot] == null) {
                    inventory[slot] = stack.copy();
                } else {
                    inventory[slot].stackSize += stack.stackSize;
                }
                return;
            }
        }
    }

    // -------------------------------------------------- auto input/output (Auto I/O augments)

    /**
     * Unlike plain pipe access (which works on any side regardless of augments), this self-
     * pulls/pushes from/to adjacent inventories without needing a pipe at all - exactly the
     * behavior the real Auto Input/Auto Output augments unlock on Thermal Expansion machines.
     */
    private boolean autoPullInputs() {
        boolean moved = false;
        for (int side = 0; side < 6; side++) {
            if (modeAllowsInsertInput(sideCache[side]) && pullFromSide(ForgeDirection.getOrientation(side))) {
                moved = true;
            }
        }
        return moved;
    }

    private boolean autoPushOutputs() {
        boolean moved = false;
        for (int side = 0; side < 6; side++) {
            int mode = sideCache[side];
            boolean primary = modeAllowsExtractPrimary(mode);
            boolean secondary = modeAllowsExtractSecondary(mode);
            if ((primary || secondary) && pushToSide(ForgeDirection.getOrientation(side), primary, secondary)) {
                moved = true;
            }
        }
        return moved;
    }

    private boolean pullFromSide(ForgeDirection dir) {
        TileEntity neighbor = worldObj.getTileEntity(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
        if (!(neighbor instanceof IInventory)) {
            return false;
        }
        IInventory neighborInv = (IInventory) neighbor;
        ForgeDirection fromSide = dir.getOpposite();
        int[] slots = neighborInv instanceof ISidedInventory
                ? ((ISidedInventory) neighborInv).getAccessibleSlotsFromSide(fromSide.ordinal())
                : allSlots(neighborInv);

        for (int slotIdx : slots) {
            ItemStack candidate = neighborInv.getStackInSlot(slotIdx);
            if (candidate == null || !PulverizerManager.recipeExists(candidate)) {
                continue;
            }
            if (neighborInv instanceof ISidedInventory
                    && !((ISidedInventory) neighborInv).canExtractItem(slotIdx, candidate, fromSide.ordinal())) {
                continue;
            }
            int inputSlot = findInputSlotFor(candidate);
            if (inputSlot < 0) {
                continue;
            }
            if (inventory[inputSlot] == null) {
                ItemStack moved = candidate.copy();
                moved.stackSize = 1;
                inventory[inputSlot] = moved;
            } else {
                inventory[inputSlot].stackSize++;
            }
            neighborInv.decrStackSize(slotIdx, 1);
            neighborInv.markDirty();
            return true;
        }
        return false;
    }

    private boolean pushToSide(ForgeDirection dir, boolean primary, boolean secondary) {
        TileEntity neighbor = worldObj.getTileEntity(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
        if (!(neighbor instanceof IInventory)) {
            return false;
        }
        IInventory neighborInv = (IInventory) neighbor;
        ForgeDirection toSide = dir.getOpposite();

        if (primary) {
            for (int i = 0; i < OUTPUT_PRIMARY_SLOTS; i++) {
                if (pushSlot(neighborInv, toSide, OUTPUT_PRIMARY_START + i)) {
                    return true;
                }
            }
        }
        if (secondary) {
            for (int i = 0; i < OUTPUT_SECONDARY_SLOTS; i++) {
                if (pushSlot(neighborInv, toSide, OUTPUT_SECONDARY_START + i)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean pushSlot(IInventory neighborInv, ForgeDirection toSide, int slot) {
        ItemStack stack = inventory[slot];
        if (stack == null) {
            return false;
        }
        if (insertIntoInventory(neighborInv, toSide, stack)) {
            stack.stackSize--;
            if (stack.stackSize <= 0) {
                inventory[slot] = null;
            }
            return true;
        }
        return false;
    }

    /**
     * Where auto-input should put {@code stack}: a line already holding it, else an empty line
     * LOCKED to it, else any empty unlocked line. Preferring the locked line matters - otherwise
     * a free line took the item and the line reserved for it stayed empty. Lines locked to
     * something else are skipped entirely.
     */
    private int findInputSlotFor(ItemStack stack) {
        int emptyLocked = -1;
        int emptyFree = -1;
        for (int i = 0; i < INPUT_SLOTS; i++) {
            if (!lineLocks.accepts(i, stack)) {
                continue;
            }
            ItemStack buf = inventory[INPUT_START + i];
            if (buf == null) {
                if (lineLocks.isLocked(i)) {
                    if (emptyLocked < 0) {
                        emptyLocked = INPUT_START + i;
                    }
                } else if (emptyFree < 0) {
                    emptyFree = INPUT_START + i;
                }
                continue;
            }
            if (buf.getItem() == stack.getItem() && buf.getItemDamage() == stack.getItemDamage()
                    && ItemStack.areItemStackTagsEqual(buf, stack) && buf.stackSize < buf.getMaxStackSize()) {
                return INPUT_START + i;
            }
        }
        return emptyLocked >= 0 ? emptyLocked : emptyFree;
    }

    // ---------------------------------------------------------------- line locks

    private final LineLocks lineLocks = new LineLocks(INPUT_SLOTS);

    public LineLocks getLineLocks() {
        return lineLocks;
    }

    /**
     * Shift + right-click on a line's input slot (see the container's slotClick): locks the line
     * to whatever it holds, or releases an existing lock. Server-side only; the description
     * packet then carries the lock to clients, which draw it as a ghost item in the empty slot.
     */
    public void toggleLineLock(int line) {
        if (line < 0 || line >= INPUT_SLOTS) {
            return;
        }
        if (lineLocks.toggle(line, inventory[INPUT_START + line])) {
            markDirty();
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    private boolean insertIntoInventory(IInventory inv, ForgeDirection side, ItemStack stack) {
        int[] slots = inv instanceof ISidedInventory
                ? ((ISidedInventory) inv).getAccessibleSlotsFromSide(side.ordinal())
                : allSlots(inv);

        for (int slotIdx : slots) {
            ItemStack single = stack.copy();
            single.stackSize = 1;

            if (inv instanceof ISidedInventory && !((ISidedInventory) inv).canInsertItem(slotIdx, single, side.ordinal())) {
                continue;
            }
            if (!inv.isItemValidForSlot(slotIdx, single)) {
                continue;
            }
            ItemStack existing = inv.getStackInSlot(slotIdx);
            if (existing == null) {
                inv.setInventorySlotContents(slotIdx, single);
                inv.markDirty();
                return true;
            }
            if (existing.getItem() == single.getItem() && existing.getItemDamage() == single.getItemDamage()
                    && ItemStack.areItemStackTagsEqual(existing, single) && existing.stackSize < existing.getMaxStackSize()) {
                existing.stackSize++;
                inv.markDirty();
                return true;
            }
        }
        return false;
    }

    private static int[] allSlots(IInventory inv) {
        int[] slots = new int[inv.getSizeInventory()];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = i;
        }
        return slots;
    }

    // ---------------------------------------------------------------- inventory

    @Override
    public int getSizeInventory() {
        return TOTAL_SLOTS;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return inventory[slot];
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (inventory[slot] == null) {
            return null;
        }
        ItemStack result;
        if (inventory[slot].stackSize <= amount) {
            result = inventory[slot];
            inventory[slot] = null;
        } else {
            result = inventory[slot].splitStack(amount);
            if (inventory[slot].stackSize == 0) {
                inventory[slot] = null;
            }
        }
        if (isAugmentSlot(slot)) {
            installAugments();
        }
        markDirty();
        return result;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        ItemStack stack = inventory[slot];
        inventory[slot] = null;
        if (isAugmentSlot(slot)) {
            installAugments();
        }
        return stack;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        inventory[slot] = stack;
        if (stack != null && stack.stackSize > getInventoryStackLimit()) {
            stack.stackSize = getInventoryStackLimit();
        }
        if (isAugmentSlot(slot)) {
            installAugments();
        }
        markDirty();
    }

    private static boolean isAugmentSlot(int slot) {
        return slot >= AUGMENT_START && slot < AUGMENT_START + AUGMENT_SLOTS;
    }

    /**
     * The machine's own lang KEY when unnamed, or the player's name for it once it has been
     * renamed in an anvil - the GUI only translates the former, which is what
     * hasCustomInventoryName() distinguishes. The name is set from the placed item's display name
     * (see the block's onBlockPlacedBy) and travels back out with the dropped item.
     */
    @Override
    public String getInventoryName() {
        return hasCustomInventoryName() ? customName : "tile.advancedPulverizer.name";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return customName != null && customName.length() > 0;
    }

    public void setCustomName(String name) {
        customName = name;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
                && player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64.0;
    }

    @Override
    public void openInventory() {
    }

    @Override
    public void closeInventory() {
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (slot == CHARGE_SLOT) {
            return stack.getItem() instanceof IEnergyContainerItem;
        }
        if (isAugmentSlot(slot)) {
            return isValidAugment(stack) && !hasDuplicateAugmentType(stack, slot);
        }
        if (slot < OUTPUT_PRIMARY_START) {
            return PulverizerManager.recipeExists(stack) && lineLocks.accepts(slot - INPUT_START, stack);
        }
        return false;
    }

    /**
     * Rotates which input slot is listed first based on world time (not a mutable field this
     * method itself advances - a "getter" with a side effect breaks the moment two neighbors,
     * or a pipe that probes this side more than once per tick, call it back to back and expect
     * the same answer both times), so a hopper or pipe feeding this side over multiple ticks
     * still fills all 3 inputs evenly rather than always topping off slot 0 first.
     */
    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        int mode = sideCache[side];
        boolean input = modeAllowsInsertInput(mode) || modeAllowsExtractInput(mode);
        boolean primary = modeAllowsExtractPrimary(mode);
        boolean secondary = modeAllowsExtractSecondary(mode);

        int[] slots = new int[(input ? INPUT_SLOTS : 0) + (primary ? OUTPUT_PRIMARY_SLOTS : 0) + (secondary ? OUTPUT_SECONDARY_SLOTS : 0)];
        int idx = 0;
        if (input) {
            int start = (int) ((worldObj != null ? worldObj.getTotalWorldTime() : 0) % INPUT_SLOTS);
            for (int i = 0; i < INPUT_SLOTS; i++) {
                slots[idx++] = INPUT_START + (start + i) % INPUT_SLOTS;
            }
        }
        if (primary) {
            for (int i = 0; i < OUTPUT_PRIMARY_SLOTS; i++) {
                slots[idx++] = OUTPUT_PRIMARY_START + i;
            }
        }
        if (secondary) {
            for (int i = 0; i < OUTPUT_SECONDARY_SLOTS; i++) {
                slots[idx++] = OUTPUT_SECONDARY_START + i;
            }
        }
        return slots;
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack stack, int side) {
        return slot < OUTPUT_PRIMARY_START && modeAllowsInsertInput(sideCache[side]) && isItemValidForSlot(slot, stack);
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack stack, int side) {
        int mode = sideCache[side];
        if (slot < OUTPUT_PRIMARY_START) {
            return modeAllowsExtractInput(mode);
        }
        if (slot < OUTPUT_SECONDARY_START) {
            return modeAllowsExtractPrimary(mode);
        }
        if (slot < AUGMENT_START) {
            return modeAllowsExtractSecondary(mode);
        }
        return false;
    }

    // ---------------------------------------------------------------- nbt

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        energyStorage.writeToNBT(tag);
        tag.setByte("Facing", facing);
        tag.setByteArray("Sides", sideCache);
        tag.setIntArray("Progress", progress);
        tag.setIntArray("ProgressMax", progressMax);
        tag.setByte("RSControl", (byte) rsMode.ordinal());
        if (hasCustomInventoryName()) {
            tag.setString("CustomName", customName);
        }
        lineLocks.writeToNBT(tag);

        NBTTagList items = new NBTTagList();
        for (int i = 0; i < inventory.length; i++) {
            if (inventory[i] != null) {
                NBTTagCompound itemTag = new NBTTagCompound();
                itemTag.setByte("Slot", (byte) i);
                inventory[i].writeToNBT(itemTag);
                items.appendTag(itemTag);
            }
        }
        tag.setTag("Items", items);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        facing = tag.getByte("Facing");
        if (tag.hasKey("CustomName")) {
            customName = tag.getString("CustomName");
        }
        lineLocks.readFromNBT(tag);
        if (tag.hasKey("RSControl")) {
            // Range-checked exactly like the Sides array below - an out-of-range ordinal from a
            // corrupt or hand-edited tag threw straight out of readFromNBT, killing the chunk load.
            int ordinal = tag.getByte("RSControl") & 0xFF;
            if (ordinal < ControlMode.values().length) {
                rsMode = ControlMode.values()[ordinal];
            }
        }

        if (tag.hasKey("Sides")) {
            byte[] sides = tag.getByteArray("Sides");
            // Tampered/foreign/downgraded-from-a-future-version NBT could carry a mode value
            // outside SIDE_MODE_COUNT, which would later index BlockAdvancedPulverizer's
            // per-mode icon arrays out of bounds during rendering - reject the whole array
            // rather than trust it element-by-element (sideCache's own zero-initialized default
            // is already all-Disabled, a safe fallback).
            if (sides.length == sideCache.length && isValidSideArray(sides)) {
                sideCache = sides;
            }
        }
        if (tag.hasKey("Progress")) {
            int[] p = tag.getIntArray("Progress");
            System.arraycopy(p, 0, progress, 0, Math.min(p.length, progress.length));
        }
        if (tag.hasKey("ProgressMax")) {
            int[] p = tag.getIntArray("ProgressMax");
            System.arraycopy(p, 0, progressMax, 0, Math.min(p.length, progressMax.length));
        }

        NBTTagList items = tag.getTagList("Items", 10);
        for (int i = 0; i < inventory.length; i++) {
            inventory[i] = null;
        }
        for (int i = 0; i < items.tagCount(); i++) {
            NBTTagCompound itemTag = items.getCompoundTagAt(i);
            int slot = itemTag.getByte("Slot") & 255;
            if (slot >= 0 && slot < inventory.length) {
                inventory[slot] = ItemStack.loadItemStackFromNBT(itemTag);
            }
        }

        installAugments();
        // Only AFTER installAugments(): CoFH's EnergyStorage#readFromNBT clamps the stored value to
        // the CURRENT capacity, and until the Energy Storage augment above has been applied that
        // is still the base capacity. Reading it first silently threw away everything past the
        // base buffer on every chunk load - an augmented machine holding 5,000,000 RF came back
        // with 1,000,000.
        energyStorage.readFromNBT(tag);

        isActive = false;
        for (int i = 0; i < INPUT_SLOTS; i++) {
            if (progressMax[i] > 0) {
                isActive = true;
                break;
            }
        }
    }

    /**
     * Without this, a freshly loaded chunk never tells a client what facing/sideCache/augments
     * this tile actually has - the default TileEntity#getDescriptionPacket() returns null, so
     * the client keeps whatever it was constructed with (facing = south, the createNewTileEntity
     * default) until something else happens to resync it. That's exactly the "block faces a
     * different way after rejoining the world" bug: the SERVER's saved data was always correct,
     * the CLIENT's own copy just never received it. Reuses writeToNBT/readFromNBT wholesale
     * (facing, sides, augments, energy, redstone mode, everything) rather than hand-picking
     * fields, since this only fires once per chunk load, not something to be stingy about.
     */
    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        // MCP never gave this one a friendly name - func_148857_g() is S35PacketUpdateTileEntity's NBT getter.
        readFromNBT(packet.func_148857_g());
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }
}
