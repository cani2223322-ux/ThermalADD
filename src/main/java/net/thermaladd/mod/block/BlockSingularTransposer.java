package net.thermaladd.mod.block;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.thermaladd.mod.ThermalADD;
import net.thermaladd.mod.tileentity.TileSingularTransposer;

/** Real TE's Fluid Transposer face ({@code Machine_Face_Transposer}) on the TE casing. */
public class BlockSingularTransposer extends BlockSingularityMachine {

    public BlockSingularTransposer() {
        super("singularTransposer", "Transposer", TileSingularTransposer.SIDE_BADGES, ThermalADD.GUI_ID_SINGULAR_TRANSPOSER);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileSingularTransposer();
    }

    @Override
    public int getItemCapacity() {
        return TileSingularTransposer.BASE_ENERGY_CAPACITY;
    }

    @Override
    public int getItemReceiveRate() {
        return TileSingularTransposer.ENERGY_RECEIVE_PER_TICK;
    }
}
