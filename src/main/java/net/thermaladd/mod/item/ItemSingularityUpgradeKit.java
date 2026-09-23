package net.thermaladd.mod.item;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import net.thermaladd.mod.ThermalADD;
import net.thermaladd.mod.init.ModCreativeTab;
import net.thermaladd.mod.util.MachineUpgrade;

/**
 * Singularity Upgrade Kit - right-click a placed Thermal Expansion machine (Pulverizer, Furnace,
 * Sawmill, Induction Smelter, Magma Crucible, Fluid Transposer, Charger, Cyclic Assembler), a
 * portable tank or a strongbox to turn it into its singularity counterpart in place, keeping what
 * it held and how it was set up - see MachineUpgrade.
 *
 * Works through onItemUseFirst, which runs before the block's own right-click: a TE machine would
 * otherwise open its GUI and the kit would never be used.
 */
public class ItemSingularityUpgradeKit extends Item {

    private IIcon icon;

    public ItemSingularityUpgradeKit() {
        setUnlocalizedName("singularityUpgradeKit");
        setCreativeTab(ModCreativeTab.TAB);
        setMaxStackSize(16);
    }

    @Override
    public void registerIcons(IIconRegister register) {
        icon = register.registerIcon(ThermalADD.MODID + ":singularityUpgradeKit");
    }

    @Override
    public IIcon getIconFromDamage(int meta) {
        return icon;
    }

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
            float hitX, float hitY, float hitZ) {
        if (!MachineUpgrade.canUpgrade(world, x, y, z)) {
            return false;
        }
        if (world.isRemote) {
            // Claim the click on the client too, so the TE block's own GUI does not open.
            return true;
        }
        String result = MachineUpgrade.upgrade(world, x, y, z, player);
        if (result == null) {
            player.addChatMessage(new ChatComponentTranslation("chat.thermaladd.upgradeKit.denied"));
            return true;
        }
        if (!player.capabilities.isCreativeMode) {
            stack.stackSize--;
            // onItemUseFirst leaves an emptied stack in the slot - clear it like a normal use would.
            if (stack.stackSize <= 0) {
                player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
            }
        }
        world.playSoundEffect(x + 0.5, y + 0.5, z + 0.5, "random.anvil_use", 0.6F, 1.4F);
        player.addChatMessage(new ChatComponentTranslation("chat.thermaladd.upgradeKit.done",
                new ChatComponentTranslation(result)));
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("tooltip.thermaladd.upgradeKit.0"));
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("tooltip.thermaladd.upgradeKit.1"));
    }
}
