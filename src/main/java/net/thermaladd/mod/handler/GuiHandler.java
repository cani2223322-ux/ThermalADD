package net.thermaladd.mod.handler;

import cpw.mods.fml.common.network.IGuiHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.thermaladd.mod.ThermalADD;
import net.thermaladd.mod.client.gui.GuiAdvancedFurnace;
import net.thermaladd.mod.client.gui.GuiAdvancedPulverizer;
import net.thermaladd.mod.client.gui.GuiImprovedAssembler;
import net.thermaladd.mod.inventory.ContainerAdvancedFurnace;
import net.thermaladd.mod.inventory.ContainerAdvancedPulverizer;
import net.thermaladd.mod.inventory.ContainerImprovedAssembler;
import net.thermaladd.mod.tileentity.TileAdvancedFurnace;
import net.thermaladd.mod.tileentity.TileAdvancedPulverizer;
import net.thermaladd.mod.tileentity.TileImprovedAssembler;

public class GuiHandler implements IGuiHandler {

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (id == ThermalADD.GUI_ID_ADVANCED_PULVERIZER && te instanceof TileAdvancedPulverizer) {
            return new ContainerAdvancedPulverizer(player.inventory, (TileAdvancedPulverizer) te);
        }
        if (id == ThermalADD.GUI_ID_IMPROVED_ASSEMBLER && te instanceof TileImprovedAssembler) {
            return new ContainerImprovedAssembler(player.inventory, (TileImprovedAssembler) te);
        }
        if (id == ThermalADD.GUI_ID_ADVANCED_FURNACE && te instanceof TileAdvancedFurnace) {
            return new ContainerAdvancedFurnace(player.inventory, (TileAdvancedFurnace) te);
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (id == ThermalADD.GUI_ID_ADVANCED_PULVERIZER && te instanceof TileAdvancedPulverizer) {
            return new GuiAdvancedPulverizer(player.inventory, (TileAdvancedPulverizer) te);
        }
        if (id == ThermalADD.GUI_ID_IMPROVED_ASSEMBLER && te instanceof TileImprovedAssembler) {
            return new GuiImprovedAssembler(player.inventory, (TileImprovedAssembler) te);
        }
        if (id == ThermalADD.GUI_ID_ADVANCED_FURNACE && te instanceof TileAdvancedFurnace) {
            return new GuiAdvancedFurnace(player.inventory, (TileAdvancedFurnace) te);
        }
        return null;
    }
}
