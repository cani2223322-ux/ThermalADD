package net.thermaladd.mod.block;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.thermaladd.mod.ThermalADD;
import net.thermaladd.mod.tileentity.TileSingularSmelter;

/** Real TE's Induction Smelter face ({@code Machine_Face_Smelter}) on the TE casing. */
public class BlockSingularSmelter extends BlockSingularityMachine {

    public BlockSingularSmelter() {
        super("singularSmelter", "Smelter", TileSingularSmelter.SIDE_BADGES, ThermalADD.GUI_ID_SINGULAR_SMELTER);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileSingularSmelter();
    }

    @Override
    public int getItemCapacity() {
        return TileSingularSmelter.BASE_ENERGY_CAPACITY;
    }

    @Override
    public int getItemReceiveRate() {
        return TileSingularSmelter.ENERGY_RECEIVE_PER_TICK;
    }
}
