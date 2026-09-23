package net.thermaladd.mod.handler;

import cpw.mods.fml.common.network.IGuiHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.thermaladd.mod.ThermalADD;
import net.thermaladd.mod.client.gui.GuiAdvancedCharger;
import net.thermaladd.mod.client.gui.GuiAdvancedFurnace;
import net.thermaladd.mod.client.gui.GuiAdvancedPulverizer;
import net.thermaladd.mod.client.gui.GuiAdvancedSawmill;
import net.thermaladd.mod.client.gui.GuiImprovedAssembler;
import net.thermaladd.mod.client.gui.GuiSingularityCell;
import net.thermaladd.mod.inventory.ContainerAdvancedCharger;
import net.thermaladd.mod.inventory.ContainerAdvancedFurnace;
import net.thermaladd.mod.inventory.ContainerAdvancedPulverizer;
import net.thermaladd.mod.inventory.ContainerAdvancedSawmill;
import net.thermaladd.mod.inventory.ContainerImprovedAssembler;
import net.thermaladd.mod.inventory.ContainerSingularityCell;
import net.thermaladd.mod.tileentity.TileAdvancedCharger;
import net.thermaladd.mod.tileentity.TileAdvancedFurnace;
import net.thermaladd.mod.tileentity.TileAdvancedPulverizer;
import net.thermaladd.mod.tileentity.TileAdvancedSawmill;
import net.thermaladd.mod.tileentity.TileImprovedAssembler;
import net.thermaladd.mod.tileentity.TileSingularityCell;
import net.thermaladd.mod.tileentity.TileSingularCrucible;
import net.thermaladd.mod.tileentity.TileSingularSmelter;
import net.thermaladd.mod.tileentity.TileSingularTransposer;
import net.thermaladd.mod.client.gui.GuiSingularCrucible;
import net.thermaladd.mod.client.gui.GuiSingularSmelter;
import net.thermaladd.mod.client.gui.GuiSingularTransposer;
import net.thermaladd.mod.inventory.ContainerSingularCrucible;
import net.thermaladd.mod.inventory.ContainerSingularSmelter;
import net.thermaladd.mod.inventory.ContainerSingularTransposer;
import net.thermaladd.mod.inventory.ContainerSingularityStrongbox;
import net.thermaladd.mod.client.gui.GuiSingularityStrongbox;
import net.thermaladd.mod.tileentity.TileSingularityStrongbox;

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
        if (id == ThermalADD.GUI_ID_SINGULARITY_CELL && te instanceof TileSingularityCell) {
            return new ContainerSingularityCell(player.inventory, (TileSingularityCell) te);
        }
        if (id == ThermalADD.GUI_ID_ADVANCED_SAWMILL && te instanceof TileAdvancedSawmill) {
            return new ContainerAdvancedSawmill(player.inventory, (TileAdvancedSawmill) te);
        }
        if (id == ThermalADD.GUI_ID_ADVANCED_CHARGER && te instanceof TileAdvancedCharger) {
            return new ContainerAdvancedCharger(player.inventory, (TileAdvancedCharger) te);
        }
        if (id == ThermalADD.GUI_ID_SINGULAR_SMELTER && te instanceof TileSingularSmelter) {
            return new ContainerSingularSmelter(player.inventory, (TileSingularSmelter) te);
        }
        if (id == ThermalADD.GUI_ID_SINGULAR_CRUCIBLE && te instanceof TileSingularCrucible) {
            return new ContainerSingularCrucible(player.inventory, (TileSingularCrucible) te);
        }
        if (id == ThermalADD.GUI_ID_SINGULAR_TRANSPOSER && te instanceof TileSingularTransposer) {
            return new ContainerSingularTransposer(player.inventory, (TileSingularTransposer) te);
        }
        if (id == ThermalADD.GUI_ID_SINGULARITY_STRONGBOX && te instanceof TileSingularityStrongbox) {
            return new ContainerSingularityStrongbox(player.inventory, (TileSingularityStrongbox) te);
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
        if (id == ThermalADD.GUI_ID_SINGULARITY_CELL && te instanceof TileSingularityCell) {
            return new GuiSingularityCell(player.inventory, (TileSingularityCell) te);
        }
        if (id == ThermalADD.GUI_ID_ADVANCED_SAWMILL && te instanceof TileAdvancedSawmill) {
            return new GuiAdvancedSawmill(player.inventory, (TileAdvancedSawmill) te);
        }
        if (id == ThermalADD.GUI_ID_ADVANCED_CHARGER && te instanceof TileAdvancedCharger) {
            return new GuiAdvancedCharger(player.inventory, (TileAdvancedCharger) te);
        }
        if (id == ThermalADD.GUI_ID_SINGULAR_SMELTER && te instanceof TileSingularSmelter) {
            return new GuiSingularSmelter(player.inventory, (TileSingularSmelter) te);
        }
        if (id == ThermalADD.GUI_ID_SINGULAR_CRUCIBLE && te instanceof TileSingularCrucible) {
            return new GuiSingularCrucible(player.inventory, (TileSingularCrucible) te);
        }
        if (id == ThermalADD.GUI_ID_SINGULAR_TRANSPOSER && te instanceof TileSingularTransposer) {
            return new GuiSingularTransposer(player.inventory, (TileSingularTransposer) te);
        }
        if (id == ThermalADD.GUI_ID_SINGULARITY_STRONGBOX && te instanceof TileSingularityStrongbox) {
            return new GuiSingularityStrongbox(player.inventory, (TileSingularityStrongbox) te);
        }
        return null;
    }
}
