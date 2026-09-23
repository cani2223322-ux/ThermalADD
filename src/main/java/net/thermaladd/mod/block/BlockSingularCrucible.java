package net.thermaladd.mod.block;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.thermaladd.mod.ThermalADD;
import net.thermaladd.mod.tileentity.TileSingularCrucible;

/** Real TE's Magma Crucible face ({@code Machine_Face_Crucible}) on the TE casing. */
public class BlockSingularCrucible extends BlockSingularityMachine {

    public BlockSingularCrucible() {
        super("singularCrucible", "Crucible", TileSingularCrucible.SIDE_BADGES, ThermalADD.GUI_ID_SINGULAR_CRUCIBLE);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileSingularCrucible();
    }

    @Override
    public int getItemCapacity() {
        return TileSingularCrucible.BASE_ENERGY_CAPACITY;
    }

    @Override
    public int getItemReceiveRate() {
        return TileSingularCrucible.ENERGY_RECEIVE_PER_TICK;
    }
}
