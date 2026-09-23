package net.thermaladd.mod.client.render;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraftforge.fluids.FluidStack;
import net.thermaladd.mod.block.BlockSingularityTank;
import net.thermaladd.mod.tileentity.TileSingularityTank;

/**
 * What the ordinary block render cannot draw: the fluid column inside the glass (the same inset
 * box real TE's RenderTank fills - {@code 0.1865..0.8135} across, {@code 0.0615..0.9385} tall -
 * with a gas filling it all the way at an opacity by amount instead), and the inner faces of the
 * frame, which TE draws as back faces pushed 1/16 inward so the far walls show through the glass.
 */
public class RenderSingularityTank extends TileEntitySpecialRenderer {

    private static final double FLUID_MIN = 0.1865234375;
    private static final double FLUID_MAX = 0.8134765625;
    private static final double FLUID_BOTTOM = 0.0615234375;
    private static final double FLUID_TOP = 1.0 - FLUID_BOTTOM;

    private static final double FRAME_MIN = 0.125 + 0.0625;
    private static final double FRAME_MAX = 0.875 - 0.0625;

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        if (!(te instanceof TileSingularityTank)) {
            return;
        }
        TileSingularityTank tank = (TileSingularityTank) te;
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        bindTexture(TextureMap.locationBlocksTexture);

        renderInnerFrame(tank);
        renderFluid(tank);

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        GL11.glPopMatrix();
    }

    private void renderInnerFrame(TileSingularityTank tank) {
        if (!(tank.getBlockType() instanceof BlockSingularityTank)) {
            return;
        }
        BlockSingularityTank block = (BlockSingularityTank) tank.getBlockType();
        int mode = tank.getMode();
        GL11.glColor4f(0.8F, 0.8F, 0.8F, 1F);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        // Wound to face inward: seen only from inside the tank, i.e. through the glass.
        IIcon side = block.getFrameIcon(2, mode);
        double lo = FRAME_MIN;
        double hi = FRAME_MAX;
        // north wall (z = lo), facing +z
        quad(t, side, lo, 0, lo, hi, 1, lo);
        // south wall (z = hi), facing -z
        quad(t, side, hi, 0, hi, lo, 1, hi);
        // west wall (x = lo), facing +x
        quad(t, side, lo, 0, hi, lo, 1, lo);
        // east wall (x = hi), facing -x
        quad(t, side, hi, 0, lo, hi, 1, hi);
        IIcon bottom = block.getFrameIcon(0, mode);
        IIcon top = block.getFrameIcon(1, mode);
        horizontal(t, bottom, lo, hi, 0.0625, true);
        horizontal(t, top, lo, hi, 1 - 0.0625, false);
        t.draw();
    }

    /** A vertical quad from (x1, y1, z1) to (x2, y2, z2); UVs span the icon's middle 12 px like the outer face. */
    private static void quad(Tessellator t, IIcon icon, double x1, double y1, double z1, double x2, double y2, double z2) {
        double u1 = icon.getInterpolatedU(2);
        double u2 = icon.getInterpolatedU(14);
        double v1 = icon.getInterpolatedV(0);
        double v2 = icon.getInterpolatedV(16);
        t.addVertexWithUV(x1, y1, z1, u1, v2);
        t.addVertexWithUV(x2, y1, z2, u2, v2);
        t.addVertexWithUV(x2, y2, z2, u2, v1);
        t.addVertexWithUV(x1, y2, z1, u1, v1);
    }

    /** The floor (facing up) or the ceiling (facing down) of the frame, seen from inside. */
    private static void horizontal(Tessellator t, IIcon icon, double lo, double hi, double y, boolean facingUp) {
        double u1 = icon.getInterpolatedU(2);
        double u2 = icon.getInterpolatedU(14);
        double v1 = icon.getInterpolatedV(2);
        double v2 = icon.getInterpolatedV(14);
        if (facingUp) {
            t.addVertexWithUV(lo, y, lo, u1, v1);
            t.addVertexWithUV(lo, y, hi, u1, v2);
            t.addVertexWithUV(hi, y, hi, u2, v2);
            t.addVertexWithUV(hi, y, lo, u2, v1);
        } else {
            t.addVertexWithUV(lo, y, lo, u1, v1);
            t.addVertexWithUV(hi, y, lo, u2, v1);
            t.addVertexWithUV(hi, y, hi, u2, v2);
            t.addVertexWithUV(lo, y, hi, u1, v2);
        }
    }

    private void renderFluid(TileSingularityTank tank) {
        FluidStack fluid = tank.getTankFluid();
        if (fluid == null || fluid.amount <= 0 || fluid.getFluid() == null) {
            return;
        }
        IIcon icon = fluid.getFluid().getIcon(fluid);
        if (icon == null) {
            return;
        }
        int color = fluid.getFluid().getColor(fluid);
        float alpha = 1F;
        double top;
        double fraction = Math.min(1.0, (double) fluid.amount / tank.getTankCapacity());
        if (fluid.getFluid().isGaseous(fluid)) {
            top = FLUID_TOP;
            alpha = (32 + (float) (192 * fraction)) / 255F;
        } else {
            top = FLUID_BOTTOM + (FLUID_TOP - FLUID_BOTTOM) * Math.max(fraction, 1.0 / 128);
        }
        GL11.glColor4f((color >> 16 & 0xFF) / 255F, (color >> 8 & 0xFF) / 255F, (color & 0xFF) / 255F, alpha);

        double lo = FLUID_MIN;
        double hi = FLUID_MAX;
        double bottom = FLUID_BOTTOM;
        double u1 = icon.getInterpolatedU(lo * 16);
        double u2 = icon.getInterpolatedU(hi * 16);
        double vTop = icon.getInterpolatedV(16 - top * 16);
        double vBottom = icon.getInterpolatedV(16 - bottom * 16);

        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        // sides, facing outward
        t.addVertexWithUV(lo, bottom, lo, u2, vBottom);
        t.addVertexWithUV(lo, top, lo, u2, vTop);
        t.addVertexWithUV(hi, top, lo, u1, vTop);
        t.addVertexWithUV(hi, bottom, lo, u1, vBottom);

        t.addVertexWithUV(hi, bottom, hi, u2, vBottom);
        t.addVertexWithUV(hi, top, hi, u2, vTop);
        t.addVertexWithUV(lo, top, hi, u1, vTop);
        t.addVertexWithUV(lo, bottom, hi, u1, vBottom);

        t.addVertexWithUV(lo, bottom, hi, u2, vBottom);
        t.addVertexWithUV(lo, top, hi, u2, vTop);
        t.addVertexWithUV(lo, top, lo, u1, vTop);
        t.addVertexWithUV(lo, bottom, lo, u1, vBottom);

        t.addVertexWithUV(hi, bottom, lo, u2, vBottom);
        t.addVertexWithUV(hi, top, lo, u2, vTop);
        t.addVertexWithUV(hi, top, hi, u1, vTop);
        t.addVertexWithUV(hi, bottom, hi, u1, vBottom);

        // top, facing up
        double uLo = icon.getInterpolatedU(lo * 16);
        double uHi = icon.getInterpolatedU(hi * 16);
        double vLo = icon.getInterpolatedV(lo * 16);
        double vHi = icon.getInterpolatedV(hi * 16);
        t.addVertexWithUV(lo, top, lo, uLo, vLo);
        t.addVertexWithUV(lo, top, hi, uLo, vHi);
        t.addVertexWithUV(hi, top, hi, uHi, vHi);
        t.addVertexWithUV(hi, top, lo, uHi, vLo);

        // bottom, facing down
        t.addVertexWithUV(lo, bottom, lo, uLo, vLo);
        t.addVertexWithUV(hi, bottom, lo, uHi, vLo);
        t.addVertexWithUV(hi, bottom, hi, uHi, vHi);
        t.addVertexWithUV(lo, bottom, hi, uLo, vHi);
        t.draw();
    }
}
