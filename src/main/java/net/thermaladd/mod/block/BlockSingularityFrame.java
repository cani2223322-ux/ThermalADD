package net.thermaladd.mod.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import net.thermaladd.mod.ThermalADD;
import net.thermaladd.mod.init.ModCreativeTab;

/**
 * Pure crafting material - the mod-original counterpart to real Thermal Expansion's own
 * "Рамка механизма (резонирующая)" (cofh.thermalexpansion.block.simple.BlockFrame, Types.
 * MACHINE_RESONANT), used as the base ingredient for its own recipe. Real TE's own frame block
 * renders as a hollow frame around a tier-specific "center" material via a dedicated ISBRH/TESR
 * (see BlockFrame#hasCenter/hasFrame) - deliberately simplified here to a plain solid cube with
 * the same real Top/Bottom/Side face textures (composited fully opaque over the real Inner
 * layer, same fix as BlockSingularityCell's own texture needed - Machine_Frame_*.png is only
 * real TE's outer cage layer, ~25% transparent on its own), same "no separate tier/upgrade
 * item, no fancy renderer" simplification this mod's other blocks already use, recolored with
 * the same violet-magenta-gold-cyan "Singularity" gradient.
 */
public class BlockSingularityFrame extends Block {

    private IIcon iconTop;
    private IIcon iconBottom;
    private IIcon iconSide;

    public BlockSingularityFrame() {
        super(Material.iron);
        setBlockName("singularityFrame");
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(20.0F);
        setResistance(120.0F);
        setStepSound(soundTypeMetal);
    }

    @Override
    public void registerBlockIcons(IIconRegister register) {
        iconTop = register.registerIcon(ThermalADD.MODID + ":singularityFrameTop");
        iconBottom = register.registerIcon(ThermalADD.MODID + ":singularityFrameBottom");
        iconSide = register.registerIcon(ThermalADD.MODID + ":singularityFrameSide");
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        if (side == 0) {
            return iconBottom;
        }
        if (side == 1) {
            return iconTop;
        }
        return iconSide;
    }

    @Override
    public boolean isOpaqueCube() {
        return true;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return true;
    }
}
